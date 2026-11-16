package org.mcla.recomp;

import android.content.Context;
import android.system.Os;
import android.util.Log;
import java.io.File;

/** Optional, per-launch SDR2x request. User DLL stays private and outside the APK. */
final class MclaFrameGeneration {
    static final String PREFS = "frame-generation";
    static final String KEY = "lsfg_2x";
    private MclaFrameGeneration() {}

    static File dll(Context context) {
        return new File(context.getFilesDir(), "lsfg/Lossless.dll");
    }

    static boolean available(Context context) {
        File file = dll(context);
        return file.isFile() && file.length() > 1024 && file.length() <= 32L * 1024 * 1024;
    }

    static void apply(Context context) {
        boolean requested = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false);
        boolean enabled = requested && available(context);
        try {
            Os.setenv("REX_MCLA_LSFG", enabled ? "1" : "0", true);
            Os.setenv("REX_MCLA_LSFG_DLL", enabled ? dll(context).getAbsolutePath() : "", true);
            MclaAdrenoDiagnostics.event(context, "LSFG requested=" + requested +
                    " launch_enabled=" + enabled + " mode=SDR2x user_dll=" + available(context));
        } catch (Exception error) {
            Log.e("MCLA LSFG", "Could not configure optional frame generation", error);
        }
    }
}
