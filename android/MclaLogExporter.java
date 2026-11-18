package org.mcla.recomp;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Self-healing diagnostic log exporter for Android 10+ MediaStore (Documents/MCLA/logs/)
 * plus an app-external fallback mirror (Android/data/org.mcla.recomp/files/logs/).
 *
 * Exports both:
 *  - MCLA-diagnostic.txt: rolling multi-session diagnostic stream with device/settings header
 *    (8 MiB cap, trimmed to newest 4 MiB on overflow).
 *  - mcla-current.txt: complete snapshot of the active run's mcla.1.log + mcla.log.
 */
final class MclaLogExporter {
    private static final String TAG = "MCLA Logs";
    private static final String RELATIVE_PATH = Environment.DIRECTORY_DOCUMENTS + "/MCLA/logs/";
    private static final String RELATIVE_PATH_NO_SLASH = Environment.DIRECTORY_DOCUMENTS + "/MCLA/logs";
    private static final String DIAGNOSTIC_FILE = "MCLA-diagnostic.txt";
    private static final String CURRENT_FILE = "mcla-current.txt";
    private static final String PREFS_STATE = "unified-log-v2";
    private static final String PREFS_STATUS = "auto-log-status";
    private static final int MAX_DIAGNOSTIC_BYTES = 8 * 1024 * 1024;
    private static final int KEEP_DIAGNOSTIC_BYTES = 4 * 1024 * 1024;
    private static final int MAX_BATCH_BYTES = 2 * 1024 * 1024;
    private static final Object LOCK = new Object();
    private static final String PROCESS_SESSION = UUID.randomUUID().toString().substring(0, 8);

    private static ScheduledExecutorService periodicWorker;
    private static boolean sessionHeaderWritten = false;

    private MclaLogExporter() {}

    static void startPeriodic(Context context) {
        final Context app = context.getApplicationContext();
        synchronized (LOCK) {
            if (periodicWorker != null && !periodicWorker.isShutdown()) {
                return;
            }
            periodicWorker = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "MCLA log exporter");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });
            periodicWorker.scheduleWithFixedDelay(
                    () -> exportInternal(app, false), 4, 15, TimeUnit.SECONDS);
        }
    }

    static void stopPeriodic() {
        synchronized (LOCK) {
            if (periodicWorker != null) {
                periodicWorker.shutdownNow();
                periodicWorker = null;
            }
        }
    }

    static void exportAsync(Context context) {
        exportNowAsync(context, false, null);
    }

    static void exportNowAsync(Context context, boolean forceFull, Runnable onComplete) {
        final Context app = context.getApplicationContext();
        Thread thread = new Thread(() -> {
            try {
                exportInternal(app, forceFull);
            } finally {
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        }, "MCLA log export now");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }

    static String statusSummary(Context context) {
        if (Build.VERSION.SDK_INT < 29) {
            File ext = externalMirrorDir(context);
            return ext != null
                    ? "Logs saved to " + ext.getAbsolutePath()
                    : "Private logs in files/logs/mcla.log (Android < 10)";
        }
        return context.getSharedPreferences(PREFS_STATUS, Context.MODE_PRIVATE)
                .getString("status", "Ready · auto-saves to Documents/MCLA/logs/" + DIAGNOSTIC_FILE);
    }

    private static void saveStatus(Context context, String text) {
        context.getSharedPreferences(PREFS_STATUS, Context.MODE_PRIVATE)
                .edit().putString("status", text).apply();
    }

    private static File externalMirrorDir(Context context) {
        try {
            File base = context.getExternalFilesDir(null);
            if (base == null) return null;
            File logs = new File(base, "logs");
            if (!logs.isDirectory() && !logs.mkdirs()) return null;
            return logs;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String isoTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date());
    }

    private static String shortTime() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
    }

    private static String buildSessionHeader(Context context) {
        String version = "unknown";
        long versionCode = 0;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            version = info.versionName != null ? info.versionName : "unknown";
            versionCode = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (Exception ignored) { }

        long totalRamMb = -1;
        long availRamMb = -1;
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mem);
                totalRamMb = mem.totalMem / (1024L * 1024L);
                availRamMb = mem.availMem / (1024L * 1024L);
            }
        } catch (Exception ignored) { }

        boolean customDriver = new File(context.getFilesDir(), "gpu-drivers/selected.txt").isFile();
        String tuDebug = context.getSharedPreferences("driver_debug", Context.MODE_PRIVATE)
                .getString("tu_debug_s23", "syncdraw");
        String perfPreset = MclaRuntimeProfiles.perfPreset(context);
        String audioProfile = MclaRuntimeProfiles.audioProfile(context);
        boolean emptyStreets = MclaRuntimeProfiles.emptyStreets(context);
        boolean sustained = MclaRuntimeProfiles.sustainedPerformance(context);

        return "\n========================================================================\n"
                + "=== MCLA DIAGNOSTIC SESSION " + PROCESS_SESSION
                + " | " + isoTimestamp()
                + " | uptime_ms=" + SystemClock.elapsedRealtime() + " ===\n"
                + "apk_version=" + version + " (" + versionCode + ")"
                + " | android=" + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n"
                + "device=" + Build.MANUFACTURER + " " + Build.MODEL
                + " (" + Build.DEVICE + " / " + Build.HARDWARE + " / " + Build.SOC_MODEL_SAFE() + ")\n"
                + "ram_mb=" + availRamMb + "/" + totalRamMb
                + " | driver=" + (customDriver ? "turnip(tu_debug=" + tuDebug + ")" : "system")
                + " | perf_preset=" + perfPreset
                + " | audio_profile=" + audioProfile
                + " | empty_streets=" + emptyStreets
                + " | sustained_thermal=" + sustained + "\n"
                + "========================================================================\n";
    }


    // Helper static wrapper that avoids direct Build.SOC_MODEL verify issues on older API levels.
    private static final class Build {
        static final int VERSION_SDK = android.os.Build.VERSION.SDK_INT;
        static final String MANUFACTURER = android.os.Build.MANUFACTURER;
        static final String MODEL = android.os.Build.MODEL;
        static final String DEVICE = android.os.Build.DEVICE;
        static final String HARDWARE = android.os.Build.HARDWARE;
        static final String BOARD = android.os.Build.BOARD;
        static final class VERSION {
            static final int SDK_INT = android.os.Build.VERSION.SDK_INT;
            static final String RELEASE = android.os.Build.VERSION.RELEASE;
        }
        static String SOC_MODEL_SAFE() {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                try {
                    return android.os.Build.SOC_MODEL;
                } catch (Throwable ignored) { }
            }
            return android.os.Build.BOARD;
        }
    }

    private static void exportInternal(Context context, boolean forceFull) {
        synchronized (LOCK) {
            File logsDir = new File(context.getFilesDir(), "logs");
            if (!logsDir.isDirectory() && !logsDir.mkdirs()) {
                return;
            }
            File currentLog = new File(logsDir, "mcla.log");
            File rotatedLog = new File(logsDir, "mcla.1.log");
            SharedPreferences state = context.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE);

            try {
                boolean wroteAnything = false;
                ByteArrayOutputStream delta = new ByteArrayOutputStream();

                if (!sessionHeaderWritten || forceFull) {
                    byte[] hdr = buildSessionHeader(context).getBytes(StandardCharsets.UTF_8);
                    delta.write(hdr);
                    sessionHeaderWritten = true;
                }

                if (rotatedLog.isFile() && rotatedLog.length() > 0) {
                    String rotSig = rotatedLog.length() + ":" + rotatedLog.lastModified();
                    if (forceFull || !rotSig.equals(state.getString("rot.sig", ""))) {
                        appendTailChunk(rotatedLog, 0, MAX_BATCH_BYTES / 2, "mcla.1.log", delta);
                        state.edit().putString("rot.sig", rotSig).apply();
                    }
                }

                long curLen = currentLog.isFile() ? currentLog.length() : 0L;
                long savedOffset = forceFull ? 0L : state.getLong("cur.offset", 0L);
                long savedMtime = state.getLong("cur.mtime", 0L);
                if (curLen < savedOffset) {
                    savedOffset = 0L; // Log truncated or rotated on new game launch.
                }
                if (curLen > savedOffset || (forceFull && curLen > 0)) {
                    long startAt = curLen - savedOffset > MAX_BATCH_BYTES
                            ? curLen - MAX_BATCH_BYTES
                            : savedOffset;
                    appendTailChunk(currentLog, startAt, MAX_BATCH_BYTES, "mcla.log", delta);
                    state.edit()
                            .putLong("cur.offset", curLen)
                            .putLong("cur.mtime", currentLog.lastModified())
                            .apply();
                } else if (!currentLog.isFile() && forceFull) {
                    String note = "[launcher] No native gameplay log generated yet in this install; "
                            + "waiting for Play session.\n";
                    delta.write(note.getBytes(StandardCharsets.UTF_8));
                }

                byte[] payload = delta.toByteArray();
                if (payload.length > 0) {
                    writeMirrorDiagnostic(context, payload);
                    if (Build.VERSION.SDK_INT >= 29) {
                        appendMediaStoreDiagnostic(context.getContentResolver(), state, payload);
                    }
                    wroteAnything = true;
                }

                // Also keep mcla-current.txt updated whenever mcla.log changes or forceFull is requested.
                if (currentLog.isFile() && currentLog.length() > 0) {
                    String snapStamp = (rotatedLog.isFile() ? rotatedLog.length() + ":" + rotatedLog.lastModified() : "0")
                            + "|" + curLen + ":" + currentLog.lastModified();
                    if (forceFull || !snapStamp.equals(state.getString("snap.stamp", ""))) {
                        File[] parts = rotatedLog.isFile()
                                ? new File[]{rotatedLog, currentLog}
                                : new File[]{currentLog};
                        writeMirrorSnapshot(context, parts);
                        if (Build.VERSION.SDK_INT >= 29) {
                            writeMediaStoreSnapshot(context.getContentResolver(), state, CURRENT_FILE, parts);
                        }
                        state.edit().putString("snap.stamp", snapStamp).apply();
                        wroteAnything = true;
                    }
                }

                if (wroteAnything || forceFull) {
                    long kb = Math.max(1L, (curLen + payload.length) / 1024L);
                    saveStatus(context, "Saved Documents/MCLA/logs/" + DIAGNOSTIC_FILE
                            + " · " + shortTime() + " (" + kb + " KiB)");
                }
            } catch (Exception e) {
                Log.e(TAG, "Diagnostic log export error", e);
                saveStatus(context, "Log export retry scheduled (" + e.getClass().getSimpleName() + ")");
            }
        }
    }

    private static void appendTailChunk(File file, long startOffset, int maxBytes,
            String label, ByteArrayOutputStream out) throws Exception {
        long length = file.length();
        if (length <= startOffset) return;
        long readFrom = Math.max(startOffset, length - maxBytes);
        String marker = "--- [" + label + " offset=" + readFrom + ".." + length
                + " at " + isoTimestamp() + "] ---\n";
        out.write(marker.getBytes(StandardCharsets.UTF_8));
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(readFrom);
            byte[] buf = new byte[32 * 1024];
            long remaining = length - readFrom;
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                int n = raf.read(buf, 0, toRead);
                if (n <= 0) break;
                out.write(buf, 0, n);
                remaining -= n;
            }
        }
        out.write('\n');
    }

    private static void writeMirrorDiagnostic(Context context, byte[] payload) {
        File dir = externalMirrorDir(context);
        if (dir == null) return;
        File diag = new File(dir, DIAGNOSTIC_FILE);
        try {
            if (diag.isFile() && diag.length() + payload.length > MAX_DIAGNOSTIC_BYTES) {
                byte[] kept = readRecentBytes(diag, KEEP_DIAGNOSTIC_BYTES);
                try (FileOutputStream out = new FileOutputStream(diag, false)) {
                    out.write(kept);
                    out.write(payload);
                }
            } else {
                try (FileOutputStream out = new FileOutputStream(diag, true)) {
                    out.write(payload);
                }
            }
        } catch (Exception ignored) { }
    }

    private static void writeMirrorSnapshot(Context context, File[] parts) {
        File dir = externalMirrorDir(context);
        if (dir == null) return;
        File target = new File(dir, CURRENT_FILE);
        try (FileOutputStream out = new FileOutputStream(target, false)) {
            byte[] buf = new byte[64 * 1024];
            for (File part : parts) {
                if (!part.isFile()) continue;
                try (InputStream in = new FileInputStream(part)) {
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }
            }
        } catch (Exception ignored) { }
    }

    private static byte[] readRecentBytes(File file, int maxKeep) throws Exception {
        long len = file.length();
        int keep = (int) Math.min(len, maxKeep);
        byte[] buf = new byte[keep];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(len - keep);
            raf.readFully(buf);
        }
        return buf;
    }

    private static Uri resolveOrCreateDocument(ContentResolver resolver, SharedPreferences state,
            String displayName) throws Exception {
        String key = "uri." + displayName;
        String cached = state.getString(key, null);
        if (cached != null) {
            Uri candidate = Uri.parse(cached);
            if (isAliveDocument(resolver, candidate)) {
                return candidate;
            }
            state.edit().remove(key).apply();
        }

        Uri collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        String[] projection = {MediaStore.MediaColumns._ID};
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND ("
                + MediaStore.MediaColumns.RELATIVE_PATH + "=? OR "
                + MediaStore.MediaColumns.RELATIVE_PATH + "=?)";
        String[] args = {displayName, RELATIVE_PATH, RELATIVE_PATH_NO_SLASH};
        try (Cursor cursor = resolver.query(collection, projection, selection, args,
                MediaStore.MediaColumns._ID + " DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                Uri existing = ContentUris.withAppendedId(collection, cursor.getLong(0));
                if (isAliveDocument(resolver, existing)) {
                    state.edit().putString(key, existing.toString()).apply();
                    return existing;
                }
            }
        } catch (Exception ignored) { }

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH);
        Uri created = resolver.insert(collection, values);
        if (created == null) {
            throw new Exception("MediaStore refused " + RELATIVE_PATH + displayName);
        }
        state.edit().putString(key, created.toString()).apply();
        return created;
    }

    private static boolean isAliveDocument(ContentResolver resolver, Uri uri) {
        String[] projection = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.IS_TRASHED};
        try (Cursor c = resolver.query(uri, projection, null, null, null)) {
            if (c == null || !c.moveToFirst()) return false;
            return c.getInt(1) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static long queryDocumentSize(ContentResolver resolver, Uri uri) {
        String[] projection = {MediaStore.MediaColumns.SIZE};
        try (Cursor c = resolver.query(uri, projection, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getLong(0);
        } catch (Exception ignored) { }
        return 0L;
    }

    private static void appendMediaStoreDiagnostic(ContentResolver resolver, SharedPreferences state,
            byte[] payload) throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            Uri uri = resolveOrCreateDocument(resolver, state, DIAGNOSTIC_FILE);
            try {
                long existingSize = queryDocumentSize(resolver, uri);
                if (existingSize + payload.length > MAX_DIAGNOSTIC_BYTES) {
                    byte[] recent = readRecentUriBytes(resolver, uri, KEEP_DIAGNOSTIC_BYTES);
                    try (OutputStream out = resolver.openOutputStream(uri, "wt")) {
                        if (out == null) throw new Exception("Null output stream on trim");
                        String trimNote = "=== HISTORY TRIMMED at " + isoTimestamp()
                                + " (8 MiB cap) ===\n";
                        out.write(trimNote.getBytes(StandardCharsets.UTF_8));
                        if (recent.length > 0) out.write(recent);
                        out.write(payload);
                        out.flush();
                    }
                } else {
                    try (OutputStream out = resolver.openOutputStream(uri, "wa")) {
                        if (out == null) throw new Exception("Null output stream on append");
                        out.write(payload);
                        out.flush();
                    }
                }
                return;
            } catch (Exception e) {
                state.edit().remove("uri." + DIAGNOSTIC_FILE).apply();
                if (attempt == 1) throw e;
            }
        }
    }

    private static byte[] readRecentUriBytes(ContentResolver resolver, Uri uri, int maxBytes) {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return new byte[0];
            ByteArrayOutputStream ring = new ByteArrayOutputStream();
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                ring.write(buf, 0, n);
                if (ring.size() > maxBytes * 2) {
                    byte[] cur = ring.toByteArray();
                    ring.reset();
                    ring.write(cur, cur.length - maxBytes, maxBytes);
                }
            }
            byte[] all = ring.toByteArray();
            if (all.length <= maxBytes) return all;
            byte[] tail = new byte[maxBytes];
            System.arraycopy(all, all.length - maxBytes, tail, 0, maxBytes);
            return tail;
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static void writeMediaStoreSnapshot(ContentResolver resolver, SharedPreferences state,
            String displayName, File[] parts) throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            Uri uri = resolveOrCreateDocument(resolver, state, displayName);
            try (OutputStream out = resolver.openOutputStream(uri, "wt")) {
                if (out == null) throw new Exception("Cannot open " + displayName);
                byte[] buf = new byte[64 * 1024];
                for (File part : parts) {
                    if (!part.isFile()) continue;
                    try (InputStream in = new FileInputStream(part)) {
                        int n;
                        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    }
                }
                out.flush();
                return;
            } catch (Exception e) {
                state.edit().remove("uri." + displayName).apply();
                if (attempt == 1) throw e;
            }
        }
    }
}
