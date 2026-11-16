package org.mcla.recomp;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.system.Os;
import android.util.Log;

/** Opt-in launch-only tradeoffs; never changes clocks, drivers or audio. */
final class MclaPerformanceProfile {
    static final String PREFS = "s23_performance";
    static final String KEY = "enabled";
    private MclaPerformanceProfile() {}

    static boolean enabled(Context context) {
        boolean defaultValue = false;
        try {
            Bundle metadata = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), PackageManager.GET_META_DATA).metaData;
            defaultValue = metadata != null && metadata.getBoolean(
                    "org.mcla.recomp.S23_PERFORMANCE_DEFAULT", false);
        } catch (PackageManager.NameNotFoundException ignored) { }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, defaultValue);
    }

    static String[][] overrides() {
        return new String[][]{
            {"REX_MCLA_PRESET", "custom"},
            {"REX_MCLA_ASPECT_RATIO", "16:9"}, {"REX_MCLA_RENDER_HEIGHT", "720"},
            {"REX_RESOLUTION_SCALE", "1"}, {"REX_DRAW_RESOLUTION_SCALE_X", "1"},
            {"REX_DRAW_RESOLUTION_SCALE_Y", "1"},
            {"REX_MCLA_SHADOWS", "false"}, {"REX_MCLA_RACE_SHADOWS", "false"},
            {"REX_MCLA_FOLIAGE_SHADOWS", "false"}, {"REX_MCLA_FOLIAGE_IMPOSTORS", "false"},
            {"REX_MCLA_FULLSCREEN_BLUR", "false"}, {"REX_MCLA_MOTION_BLUR", "false"},
            {"REX_MCLA_DEPTH_OF_FIELD", "false"}, {"REX_MCLA_ANTI_ALIASING", "off"},
            {"REX_MCLA_CITY_LOD", "0.35"}, {"REX_MCLA_TRAFFIC_LOD", "0.35"},
            {"REX_MCLA_TRAFFIC_DISTANCE", "150"}, {"REX_MCLA_PEDESTRIANS", "0"},
            {"REX_MCLA_PARKED_CARS", "0"},
            {"REX_MCLA_ENV_MAP_INTERVAL", "4"}, {"REX_MCLA_REFLECTION_INTERVAL", "4"}
        };
    }

    static void apply(Context context) {
        try {
            // Resolution overrides are also owned by the resolution selector.
            // Restore that selector after clearing this profile on a relaunch.
            for (String[] entry : overrides()) Os.unsetenv(entry[0]);
            MclaGraphicsProfiles.apply(context);
            // Normal candidate: retire the S23 preset without erasing its saved
            // preference, so installing an older experimental APK remains reversible.
            boolean enabled = false;
            if (enabled) {
                for (String[] entry : overrides()) Os.setenv(entry[0], entry[1], true);
            }
            String message = "S23 performance profile=" + enabled +
                    "; experimental visual tradeoffs, no clock/thermal/audio changes";
            Log.i("MCLA", message);
            MclaAdrenoDiagnostics.event(context, message);
        } catch (Exception e) {
            for (String[] entry : overrides()) {
                try { Os.unsetenv(entry[0]); } catch (Exception ignored) { }
            }
            MclaGraphicsProfiles.apply(context);
            Log.e("MCLA", "Could not apply S23 performance profile", e);
        }
    }
}
