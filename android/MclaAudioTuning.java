package org.mcla.recomp;

import android.content.Context;
import android.system.Os;
import android.util.Log;

/** Reversible audio-queue experiments shared by all Snapdragon devices. */
final class MclaAudioTuning {
    static final String PREFS = "audio_tuning";
    static final String KEY = "queue_profile";
    private static final String[] OWNED = {
        "REX_AUDIO_MAXQFRAMES", "REX_AUDIO_ADAPTIVE_QUEUE",
        "REX_AUDIO_ADAPTIVE_QUEUE_MAX", "REX_AUDIO_ADAPTIVE_QUEUE_STEP",
        "REX_AUDIO_STATS", "REX_AUDIO_STATS_INTERVAL"
    };

    private MclaAudioTuning() {}
    static int normalize(int value) { return value >= 0 && value <= 2 ? value : 0; }
    static int profile(Context context) {
        return normalize(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0));
    }
    static String[][] overrides(int profile) {
        switch (normalize(profile)) {
            case 1: return new String[][]{
                {"REX_AUDIO_MAXQFRAMES", "16"}, {"REX_AUDIO_ADAPTIVE_QUEUE", "false"},
                {"REX_AUDIO_STATS", "true"}, {"REX_AUDIO_STATS_INTERVAL", "5"}
            };
            case 2: return new String[][]{
                {"REX_AUDIO_MAXQFRAMES", "8"}, {"REX_AUDIO_ADAPTIVE_QUEUE", "true"},
                {"REX_AUDIO_ADAPTIVE_QUEUE_MAX", "24"}, {"REX_AUDIO_ADAPTIVE_QUEUE_STEP", "4"},
                {"REX_AUDIO_STATS", "true"}, {"REX_AUDIO_STATS_INTERVAL", "5"}
            };
            default: return new String[0][];
        }
    }
    static void apply(Context context) {
        try {
            for (String name : OWNED) Os.unsetenv(name);
            int profile = profile(context);
            // Preserve the historical diagnostic preference only in its original
            // variant. Explicit common queue profiles always take precedence.
            if (profile == 0 && MclaBuildVariant.isRedMagicAudioTest(context)) {
                boolean legacy = context.getSharedPreferences("redmagic_diagnostics", Context.MODE_PRIVATE)
                        .getBoolean("adaptive_queue", false);
                Os.setenv("REX_AUDIO_ADAPTIVE_QUEUE", legacy ? "true" : "false", true);
            }
            for (String[] entry : overrides(profile)) Os.setenv(entry[0], entry[1], true);
            // Base Activity setup has already cleared/set the channel override.
            // Only override it here when the user explicitly chose this control.
            android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (prefs.contains("force_stereo")) {
                Os.setenv("REX_AUDIO_ANDROID_FORCE_STEREO",
                        prefs.getBoolean("force_stereo", false) ? "true" : "false", true);
            }
            String message = "Audio queue profile=" + profile +
                    " (0=saved/default, 1=fixed16, 2=adaptive8to24); stereo_override=" +
                    (prefs.contains("force_stereo") ?
                            Boolean.toString(prefs.getBoolean("force_stereo", false)) : "unchanged");
            Log.i("MCLA", message);
            MclaAdrenoDiagnostics.event(context, message);
        } catch (Exception e) {
            for (String name : OWNED) {
                try { Os.unsetenv(name); } catch (Exception ignored) { }
            }
            Log.e("MCLA", "Could not apply audio queue tuning", e);
        }
    }
}
