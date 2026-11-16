package org.mcla.recomp;

import android.content.Context;
import android.system.Os;
import android.util.Log;

/** Ambient only: no changes to opponent AI, player cars, saves or collectibles. */
final class MclaWorldOptions {
    static final String PREFS = "world_options";
    static final String KEY = "empty_streets";
    private MclaWorldOptions() {}

    static void apply(Context context) {
        boolean enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false);
        try {
            // Explicit false also overrides any stale native cvar in a saved config.
            Os.setenv("REX_MCLA_EMPTY_STREETS", enabled ? "true" : "false", true);
            MclaAdrenoDiagnostics.event(context, "ambient empty_streets=" + enabled
                    + "; experimental; opponent AI untouched; restart required");
        } catch (Exception e) {
            Log.e("MCLA", "Could not configure ambient traffic", e);
        }
    }
}
