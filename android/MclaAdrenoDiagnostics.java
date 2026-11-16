package org.mcla.recomp;

import android.content.Context;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class MclaAdrenoDiagnostics {
    private static final Object LOCK = new Object();
    private static ScheduledExecutorService worker;
    private MclaAdrenoDiagnostics() {}

    private static String memorySnapshot(Context context) {
        String rss = "unavailable", swap = "unavailable";
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("VmRSS:")) rss = line.substring(6).trim();
                if (line.startsWith("VmSwap:")) swap = line.substring(7).trim();
            }
        } catch (Exception ignored) { }
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return "rss=" + rss + " swap=" + swap + " system_memory=unavailable";
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(memory);
        return "rss=" + rss + " swap=" + swap + " ram_total_mb=" +
                memory.totalMem / (1024 * 1024) + " ram_available_mb=" +
                memory.availMem / (1024 * 1024) + " system_low_memory=" + memory.lowMemory +
                " art_gc_count=" + android.os.Debug.getRuntimeStat("art.gc.gc-count") +
                " art_gc_time_ms=" + android.os.Debug.getRuntimeStat("art.gc.gc-time") +
                " art_blocking_gc_time_ms=" + android.os.Debug.getRuntimeStat("art.gc.blocking-gc-time") +
                " art_bytes_allocated=" + android.os.Debug.getRuntimeStat("art.gc.bytes-allocated") +
                " native_heap_allocated=" + android.os.Debug.getNativeHeapAllocatedSize();
    }

    static void event(Context context, String message) {
        if (!MclaBuildVariant.isAdrenoDiagnostic(context)) return;
        synchronized (LOCK) {
            try {
                File folder = new File(context.getFilesDir(), "logs");
                if (!folder.isDirectory() && !folder.mkdirs()) return;
                File log = new File(folder, "mcla_a730.log");
                // Independent of the UI and bounded even if the test runs overnight.
                if (log.length() > 256 * 1024) {
                    File old = new File(folder, "mcla_a730.1.log");
                    if (old.exists() && !old.delete()) return;
                    if (!log.renameTo(old)) return;
                }
                String line = "wall_time=" + MclaLogExporter.timestamp() +
                        " process_session=" + MclaLogExporter.sessionId() + " " +
                        SystemClock.elapsedRealtime() + "ms uptime_ms=" +
                        SystemClock.uptimeMillis() + " " + message + "\n";
                try (FileOutputStream out = new FileOutputStream(log, true)) {
                    out.write(line.getBytes(StandardCharsets.UTF_8));
                    // Diagnostic text is not save data. Do not force a storage
                    // barrier on the UI thread for every startup event/heartbeat.
                }
            } catch (Exception e) {
                Log.w("MCLA A730", "Could not write diagnostic event", e);
            }
        }
    }

    static void start(Context context) {
        if (!MclaBuildVariant.isAdrenoDiagnostic(context)) return;
        synchronized (LOCK) {
            if (worker != null) return;
            Context app = context.getApplicationContext();
            worker = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "MCLA A730 Android diagnostics");
                t.setDaemon(true);
                return t;
            });
            worker.scheduleAtFixedRate(() -> {
                try {
                    PowerManager power = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                    String thermal = Build.VERSION.SDK_INT >= 29 && power != null ?
                            Integer.toString(power.getCurrentThermalStatus()) : "unavailable";
                    Intent battery = app.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int status = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    event(app, "heartbeat thermal_status=" + thermal + " battery_level=" + level +
                            " battery_status=" + status + " " + memorySnapshot(app));
                    // The separate exporter schedules bounded incremental batches;
                    // do not queue additional exports from every heartbeat.
                } catch (Exception e) {
                    Log.w("MCLA A730", "Android diagnostic heartbeat failed", e);
                }
            }, 0, 5, TimeUnit.SECONDS);
        }
    }

    static void stop() {
        synchronized (LOCK) {
            if (worker != null) worker.shutdownNow();
            worker = null;
        }
    }
}
