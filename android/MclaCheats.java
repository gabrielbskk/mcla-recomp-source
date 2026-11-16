package org.mcla.recomp;

import android.content.Context;
import android.system.Os;
import android.util.Log;

/** One-launch requests; no TOML, collectible or save-file writes. */
final class MclaCheats {
    static final String PREFS = "cheats_next_launch";
    static final String[] KEYS = {"nitro", "no_damage", "special"};
    static final String[] LABELS = {"Infinite nitro", "No damage", "Infinite special abilities"};
    static final String[] ENVIRONMENT = {
        "MCLA_CHEAT_NITRO", "MCLA_CHEAT_NO_DAMAGE", "MCLA_CHEAT_SPECIAL"
    };
    private MclaCheats() {}

    static String value(boolean enabled) { return enabled ? "1" : "0"; }

    static void apply(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            for (int i = 0; i < KEYS.length; i++) {
                boolean requested = prefs.getBoolean(KEYS[i], false);
                Os.setenv(ENVIRONMENT[i], value(requested), true);
                String message = "Cheat next-launch request " + KEYS[i] + "=" + requested +
                        "; experimental native startup option; progression restrictions may apply";
                Log.i("MCLA", message);
                MclaAdrenoDiagnostics.event(context, message);
            }
            // Selection must be made again for a later launch. This does not
            // undo any gameplay effects or pretend that a saved change is reversible.
            android.content.SharedPreferences.Editor editor = prefs.edit();
            for (String key : KEYS) editor.remove(key);
            editor.apply();
        } catch (Exception e) {
            for (String name : ENVIRONMENT) {
                try { Os.unsetenv(name); } catch (Exception ignored) { }
            }
            Log.e("MCLA", "Could not prepare cheats; requests disabled for this launch", e);
        }
    }
}
