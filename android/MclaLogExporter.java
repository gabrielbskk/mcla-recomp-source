package org.mcla.recomp;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.system.Os;
import android.system.StructStat;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class MclaLogExporter {
    private static final String TAG = "MCLA Logs";
    private static final String PATH = Environment.DIRECTORY_DOCUMENTS + "/MCLA/logs/";
    private static final String SESSION = UUID.randomUUID().toString();
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int CHUNK_BYTES = 64 * 1024;
    private static ScheduledExecutorService worker;
    // All export I/O and cursor updates are serialized by this worker.
    private MclaLogExporter() {}

    static String sessionId() { return SESSION; }

    static String timestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date());
    }

    static synchronized void start(Context context) {
        if (worker != null || Build.VERSION.SDK_INT < 29) return;
        Context app = context.getApplicationContext();
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "MCLA incremental log export");
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.setDaemon(true);
            return thread;
        });
        worker.scheduleWithFixedDelay(() -> safeExport(app), 5, 30, TimeUnit.SECONDS);
    }

    static synchronized void exportAsync(Context context) {
        start(context);
        if (worker != null) {
            Context app = context.getApplicationContext();
            worker.execute(() -> safeExport(app));
        }
    }

    private static void safeExport(Context app) {
        try {
            export(app);
            app.getSharedPreferences("auto-log-status", Context.MODE_PRIVATE).edit()
                    .putString("status", "Last automatic check: " + timestamp()).apply();
        } catch (Exception e) {
            Log.w(TAG, "Automatic log export failed; retrying next cycle", e);
            app.getSharedPreferences("auto-log-status", Context.MODE_PRIVATE).edit()
                    .putString("status", "Automatic export needs attention (" +
                            e.getClass().getSimpleName() + "). Retrying in the background.").apply();
        }
    }

    static String status(Context context) {
        if (Build.VERSION.SDK_INT < 29) return "Automatic Documents export requires Android 10 or newer.";
        return context.getSharedPreferences("auto-log-status", Context.MODE_PRIVATE)
                .getString("status", "Automatic export is starting. No manual action required.");
    }

    private static String header(Context context) throws Exception {
        return "\n=== MCLA SESSION/PROCESS " + SESSION + " observed=" + timestamp() +
                " elapsed_ms=" + SystemClock.elapsedRealtime() + " ===\n" +
                "version=" + context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName +
                " android=" + Build.VERSION.SDK_INT + " device=" + Build.MANUFACTURER + " " + Build.MODEL +
                "\ngraphics_profile=" + MclaGraphicsProfiles.selected(context) +
                " keyboard_mouse_mode=" + MclaKeyboardMouse.mode(context) +
                " empty_streets=" + context.getSharedPreferences(MclaWorldOptions.PREFS, Context.MODE_PRIVATE)
                        .getBoolean(MclaWorldOptions.KEY, false) +
                " sustained_performance=" + context.getSharedPreferences("performance", Context.MODE_PRIVATE)
                        .getBoolean("sustained", false) +
                "\nSources are grouped by read batch, NOT globally time-sorted. " +
                "Source timestamps retain their original clock; imported backlog may predate this process. " +
                "Bounded source rotation can leave gaps if collection falls behind. " +
                "Abrupt process death may have no END marker.\n";
    }

    private static Uri target(Context context, SharedPreferences state) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        String saved = state.getString("uri", null);
        if (saved != null) {
            Uri uri = Uri.parse(saved);
            try (android.database.Cursor cursor = resolver.query(uri,
                    new String[]{MediaStore.MediaColumns._ID}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) return uri;
            } catch (SecurityException e) {
                Log.w(TAG, "Export document unavailable; creating a replacement", e);
            }
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "MCLA-diagnostic.txt");
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, PATH);
        // Publish immediately: a process killed mid-export must not leave a hidden document.
        Uri uri = resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
        if (uri == null) throw new java.io.IOException("MediaStore refused diagnostic document");
        state.edit().clear().putString("uri", uri.toString()).commit();
        return uri;
    }

    private static void export(Context context) throws Exception {
        SharedPreferences state = context.getSharedPreferences("unified-log-v1", Context.MODE_PRIVATE);
        ContentResolver resolver = context.getContentResolver();
        Uri uri = target(context, state);
        SharedPreferences.Editor progress = state.edit();
        ByteArrayOutputStream batch = new ByteArrayOutputStream();
        if (!SESSION.equals(state.getString("session", ""))) {
            batch.write(header(context).getBytes(StandardCharsets.UTF_8));
        }
        File folder = new File(context.getFilesDir(), "logs");
        java.util.Set<String> activeKeys = new java.util.HashSet<>();
        // Oldest first within each source. Inode identity follows rename-based rotation.
        for (String name : new String[]{"mcla.1.log", "mcla.log", "mcla_a730.1.log", "mcla_a730.log"}) {
            File source = new File(folder, name);
            if (!source.isFile()) continue;
            StructStat stat = Os.stat(source.getPath());
            String key = "source." + stat.st_dev + "." + stat.st_ino;
            activeKeys.add(key);
            activeKeys.add(key + ".tail");
            MclaLogChunk chunk = MclaLogChunk.read(source, state.getLong(key, 0),
                    state.getString(key + ".tail", ""), CHUNK_BYTES);
            if (chunk.bytes.length == 0) continue;
            String marker = "\n--- source=" + name + " export_process=" + SESSION + " observed=" + timestamp() +
                    " elapsed_ms=" + SystemClock.elapsedRealtime() + " end_offset=" + chunk.nextOffset +
                    " reset=" + chunk.reset + " partial_line=" + chunk.partialLine + " ---\n";
            batch.write(marker.getBytes(StandardCharsets.UTF_8));
            batch.write(chunk.bytes);
            progress.putLong(key, chunk.nextOffset).putString(key + ".tail", chunk.tail);
        }
        if (batch.size() == 0) return;
        if (batch.size() > MAX_BYTES / 2 - 16384)
            throw new java.io.IOException("Export batch exceeds safe retention budget");
        for (String key : state.getAll().keySet()) {
            if (key.startsWith("source.") && !activeKeys.contains(key)) progress.remove(key);
        }
        long size;
        try (android.os.ParcelFileDescriptor fd = resolver.openFileDescriptor(uri, "r")) {
            if (fd == null || (size = fd.getStatSize()) < 0)
                throw new java.io.IOException("Cannot establish export size");
        }
        byte[] retained = null;
        if (size > MAX_BYTES - batch.size()) {
            // Keep the newest half, beginning at a complete line, without an unbounded archive.
            try (InputStream input = resolver.openInputStream(uri)) {
                if (input == null) throw new java.io.IOException("Cannot read export for compaction");
                retained = MclaLogChunk.recentTail(input, size, MAX_BYTES / 2);
            }
        }
        byte[] trimNotice = ("=== HISTORY TRIMMED at " + timestamp() +
                "; limit=8MiB; recent tail retained ===\n").getBytes(StandardCharsets.UTF_8);
        byte[] restartHeader = retained == null ? new byte[0] : header(context).getBytes(StandardCharsets.UTF_8);
        if (retained != null && (long) retained.length + trimNotice.length + restartHeader.length +
                batch.size() > MAX_BYTES) throw new java.io.IOException("Compaction exceeds 8 MiB");
        try (OutputStream output = resolver.openOutputStream(uri, retained == null ? "wa" : "wt")) {
            if (output == null) throw new java.io.IOException("Cannot open export output");
            if (retained != null) {
                output.write(trimNotice);
                output.write(retained);
                output.write(restartHeader);
            }
            batch.writeTo(output);
            output.flush();
        }
        // A kill between write and commit may replay a batch; offsets make that identifiable.
        if (!progress.putString("session", SESSION).commit())
            Log.w(TAG, "Cursor commit failed; next cycle may repeat a batch");
    }
}
