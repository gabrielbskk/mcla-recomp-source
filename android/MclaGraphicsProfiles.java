package org.mcla.recomp;

import android.content.Context;
import android.system.Os;
import android.util.Log;

/** Explicit launch overrides only; never edits mcla.toml, drivers or caches. */
final class MclaGraphicsProfiles {
    static final String PREFS = "graphics_profiles";
    static final String KEY = "resolution";
    static final String CONFIGURED = "configured";
    static final String HD_16_9 = "720p_16_9";
    static final String HD_SCREEN = "720p_screen";
    private static final String[] VARIABLES = {
        "REX_MCLA_ASPECT_RATIO", "REX_MCLA_RENDER_HEIGHT", "REX_RESOLUTION_SCALE",
        "REX_DRAW_RESOLUTION_SCALE_X", "REX_DRAW_RESOLUTION_SCALE_Y"
    };

    private MclaGraphicsProfiles() {}

    static String normalize(String value) {
        if (HD_16_9.equals(value)) return HD_16_9;
        return HD_SCREEN.equals(value) ? HD_SCREEN : CONFIGURED;
    }

    static String selected(Context context) {
        return normalize(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, CONFIGURED));
    }

    static String[] values(String profile) {
        String normalized = normalize(profile);
        if (HD_16_9.equals(normalized)) return new String[]{"16:9", "720", "1", "1", "1"};
        if (HD_SCREEN.equals(normalized)) return new String[]{"auto", "720", "1", "1", "1"};
        return new String[0];
    }

    static void apply(Context context) {
        String profile = selected(context);
        try {
            // Clear this feature's overrides first, including when an Activity
            // is relaunched in the same process after selecting Configured.
            for (String variable : VARIABLES) Os.unsetenv(variable);
            String[] values = values(profile);
            for (int i = 0; i < values.length; i++) Os.setenv(VARIABLES[i], values[i], true);
            Log.i("MCLA", "Resolution profile=" + profile +
                    (values.length == 0 ? "; using existing game settings" :
                    "; requested height=720, aspect=" + values[0] +
                    ", native scale; restart required"));
            MclaAdrenoDiagnostics.event(context, "resolution_profile=" + profile);
        } catch (Exception e) {
            // Do not leave a partly applied aspect/height/scale combination.
            for (String variable : VARIABLES) {
                try { Os.unsetenv(variable); } catch (Exception ignored) { }
            }
            Log.e("MCLA", "Resolution profile could not be applied; using game settings", e);
        }
    }
}
