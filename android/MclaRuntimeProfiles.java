package org.mcla.recomp;

import android.content.Context;
import android.content.SharedPreferences;
import android.system.Os;
import android.util.Log;

/**
 * Unified Android runtime profiles for Snapdragon performance presets, empty streets,
 * aspect ratio, and anti-cut audio buffering (S23 & RedMagic 11 Air / Snapdragon 8 Elite).
 *
 * Preserves compatibility with SharedPreferences keys from earlier v0.1.61–v0.1.63 installs.
 */
final class MclaRuntimeProfiles {
    private static final String TAG = "MCLA";

    static final String PREFS_GRAPHICS = "graphics";
    static final String PREFS_AUDIO = "audio_tuning";
    static final String PREFS_WORLD = "world_options";

    // Performance presets
    static final String PERF_S23_BALANCED = "s23_balanced";
    static final String PERF_MAX_FPS = "max_fps";
    static final String PERF_CONSOLE = "console";

    // Audio profiles
    static final String AUDIO_S23_ADAPTIVE = "s23_adaptive";
    static final String AUDIO_REDMAGIC_SAFE = "redmagic_safe";
    static final String AUDIO_LOW_LATENCY = "low_latency";

    private static final String[] OWNED_GRAPHICS_ENV = {
        "REX_MCLA_PRESET",
        "REX_MCLA_ANTI_ALIASING",
        "REX_MCLA_SHADOWS",
        "REX_MCLA_RACE_SHADOWS",
        "REX_MCLA_FAST_CAR_SHADOWS",
        "REX_MCLA_FOLIAGE_SHADOWS",
        "REX_MCLA_FOLIAGE_IMPOSTORS",
        "REX_MCLA_FULLSCREEN_BLUR",
        "REX_MCLA_MOTION_BLUR",
        "REX_MCLA_DEPTH_OF_FIELD",
        "REX_MCLA_CITY_LOD",
        "REX_MCLA_TRAFFIC_LOD",
        "REX_MCLA_TRAFFIC_DISTANCE",
        "REX_MCLA_PEDESTRIANS",
        "REX_MCLA_PARKED_CARS",
        "REX_MCLA_REFLECTION_INTERVAL",
        "REX_MCLA_ENV_MAP_INTERVAL",
        "REX_MCLA_FAR_SHADOW_INTERVAL",
        "REX_MCLA_EMPTY_STREETS",
        "REX_MCLA_RENDER_HEIGHT",
        "REX_MCLA_ASPECT_RATIO",
        "REX_RESOLUTION_SCALE"
    };

    private static final String[] OWNED_AUDIO_ENV = {
        "REX_AUDIO_ANDROID_FORCE_STEREO",
        "REX_AUDIO_MAXQFRAMES",
        "REX_AUDIO_ADAPTIVE_QUEUE",
        "REX_AUDIO_ADAPTIVE_QUEUE_MAX",
        "REX_AUDIO_ADAPTIVE_QUEUE_STEP",
        "REX_AUDIO_THREAD_PRIORITY",
        "REX_AUDIO_MIXER_PRIORITY",
        "REX_AUDIO_STATS",
        "REX_AUDIO_STATS_INTERVAL",
        "REX_MCLA_AUDIO_LOW_LATENCY",
        "SDL_AUDIO_DEVICE_SAMPLE_FRAMES"
    };

    private MclaRuntimeProfiles() {}

    static String perfPreset(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_GRAPHICS, Context.MODE_PRIVATE);
        String saved = prefs.getString("perf_preset_v64", null);
        if (saved != null) {
            return normalizePerfPreset(saved);
        }
        // Migrate earlier v0.1.61 s23_performance preference if present, otherwise default to S23 Balanced.
        SharedPreferences legacy = context.getSharedPreferences("s23_performance", Context.MODE_PRIVATE);
        if (legacy.contains("enabled") && !legacy.getBoolean("enabled", true)) {
            return PERF_CONSOLE;
        }
        return PERF_S23_BALANCED;
    }

    static void setPerfPreset(Context context, String preset) {
        context.getSharedPreferences(PREFS_GRAPHICS, Context.MODE_PRIVATE)
                .edit().putString("perf_preset_v64", normalizePerfPreset(preset)).apply();
    }

    static String normalizePerfPreset(String value) {
        if (PERF_MAX_FPS.equals(value) || PERF_CONSOLE.equals(value) || PERF_S23_BALANCED.equals(value)) {
            return value;
        }
        return PERF_S23_BALANCED;
    }

    static String audioProfile(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_AUDIO, Context.MODE_PRIVATE);
        String saved = prefs.getString("audio_profile_v64", null);
        if (saved != null) {
            return normalizeAudioProfile(saved);
        }
        // Auto-select Ultra-Safe RedMagic profile on RedMagic / nubia / Snapdragon 8 Elite devices!
        String brand = (android.os.Build.MANUFACTURER + " " + android.os.Build.BRAND + " "
                + android.os.Build.MODEL).toLowerCase(java.util.Locale.US);
        if (brand.contains("nubia") || brand.contains("redmagic") || brand.contains("nx7")) {
            return AUDIO_REDMAGIC_SAFE;
        }
        if (context.getSharedPreferences("audio", Context.MODE_PRIVATE).getBoolean("low_latency", false)) {
            return AUDIO_LOW_LATENCY;
        }
        return AUDIO_S23_ADAPTIVE;
    }

    static void setAudioProfile(Context context, String profile) {
        context.getSharedPreferences(PREFS_AUDIO, Context.MODE_PRIVATE)
                .edit().putString("audio_profile_v64", normalizeAudioProfile(profile)).apply();
    }

    static String normalizeAudioProfile(String value) {
        if (AUDIO_REDMAGIC_SAFE.equals(value) || AUDIO_LOW_LATENCY.equals(value)
                || AUDIO_S23_ADAPTIVE.equals(value)) {
            return value;
        }
        return AUDIO_S23_ADAPTIVE;
    }

    static boolean emptyStreets(Context context) {
        return context.getSharedPreferences(PREFS_WORLD, Context.MODE_PRIVATE)
                .getBoolean("empty_streets", false);
    }

    static void setEmptyStreets(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_WORLD, Context.MODE_PRIVATE)
                .edit().putBoolean("empty_streets", enabled).apply();
    }

    static boolean widescreenFill(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("graphics_profiles", Context.MODE_PRIVATE);
        String res = prefs.getString("resolution", "720p_screen");
        return !"720p_16_9".equals(res);
    }

    static void setWidescreenFill(Context context, boolean fillScreen) {
        context.getSharedPreferences("graphics_profiles", Context.MODE_PRIVATE)
                .edit().putString("resolution", fillScreen ? "720p_screen" : "720p_16_9").apply();
    }

    static boolean fpsOverlay(Context context) {
        return context.getSharedPreferences(PREFS_GRAPHICS, Context.MODE_PRIVATE)
                .getBoolean("fps_overlay", true);
    }

    static void setFpsOverlay(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_GRAPHICS, Context.MODE_PRIVATE)
                .edit().putBoolean("fps_overlay", enabled).apply();
    }

    static boolean sustainedPerformance(Context context) {
        return context.getSharedPreferences(PREFS_GRAPHICS, Context.MODE_PRIVATE)
                .getBoolean("sustained_perf_s23", true);
    }

    static void setSustainedPerformance(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_GRAPHICS, Context.MODE_PRIVATE)
                .edit().putBoolean("sustained_perf_s23", enabled).apply();
    }

    static void applyAll(Context context) {
        applyGraphicsAndWorld(context);
        applyAudio(context);
        applySdlAndroidHints();
    }

    private static void applyGraphicsAndWorld(Context context) {
        try {
            for (String key : OWNED_GRAPHICS_ENV) {
                Os.unsetenv(key);
            }
            Os.setenv("REX_MCLA_FPS_OVERLAY", fpsOverlay(context) ? "true" : "false", true);
            Os.setenv("REX_MCLA_RENDER_HEIGHT", "720", true);
            Os.setenv("REX_RESOLUTION_SCALE", "1", true);
            Os.setenv("REX_MCLA_ASPECT_RATIO", widescreenFill(context) ? "auto" : "16:9", true);

            String preset = perfPreset(context);
            if (PERF_S23_BALANCED.equals(preset)) {
                // Tuned for Snapdragon 8 Gen 2 (S23) + Turnip syncdraw: cuts costly off-screen
                // cascades, post-process blurs, and distant geometry draw calls while keeping
                // crisp car models, reflections, and city atmosphere.
                Os.setenv("REX_MCLA_ANTI_ALIASING", "off", true);
                Os.setenv("REX_MCLA_SHADOWS", "false", true);
                Os.setenv("REX_MCLA_RACE_SHADOWS", "false", true);
                Os.setenv("REX_MCLA_FAST_CAR_SHADOWS", "true", true);
                Os.setenv("REX_MCLA_FOLIAGE_SHADOWS", "false", true);
                Os.setenv("REX_MCLA_FULLSCREEN_BLUR", "false", true);
                Os.setenv("REX_MCLA_MOTION_BLUR", "false", true);
                Os.setenv("REX_MCLA_DEPTH_OF_FIELD", "false", true);
                Os.setenv("REX_MCLA_CITY_LOD", "0.65", true);
                Os.setenv("REX_MCLA_TRAFFIC_LOD", "0.60", true);
                Os.setenv("REX_MCLA_TRAFFIC_DISTANCE", "260", true);
                Os.setenv("REX_MCLA_PEDESTRIANS", "0.35", true);
                Os.setenv("REX_MCLA_PARKED_CARS", "0.45", true);
                Os.setenv("REX_MCLA_REFLECTION_INTERVAL", "2", true);
            } else if (PERF_MAX_FPS.equals(preset)) {
                // Ultra-low draw-call profile for heavy Turnip syncdraw scenes & 6-8 GB devices.
                Os.setenv("REX_MCLA_ANTI_ALIASING", "off", true);
                Os.setenv("REX_MCLA_SHADOWS", "false", true);
                Os.setenv("REX_MCLA_RACE_SHADOWS", "false", true);
                Os.setenv("REX_MCLA_FAST_CAR_SHADOWS", "true", true);
                Os.setenv("REX_MCLA_FOLIAGE_SHADOWS", "false", true);
                Os.setenv("REX_MCLA_FOLIAGE_IMPOSTORS", "false", true);
                Os.setenv("REX_MCLA_FULLSCREEN_BLUR", "false", true);
                Os.setenv("REX_MCLA_MOTION_BLUR", "false", true);
                Os.setenv("REX_MCLA_DEPTH_OF_FIELD", "false", true);
                Os.setenv("REX_MCLA_CITY_LOD", "0.45", true);
                Os.setenv("REX_MCLA_TRAFFIC_LOD", "0.45", true);
                Os.setenv("REX_MCLA_TRAFFIC_DISTANCE", "180", true);
                Os.setenv("REX_MCLA_PEDESTRIANS", "0.0", true);
                Os.setenv("REX_MCLA_PARKED_CARS", "0.15", true);
                Os.setenv("REX_MCLA_REFLECTION_INTERVAL", "3", true);
            }
            // PERF_CONSOLE leaves mcla.toml / in-game pause menu settings untouched!

            boolean empty = emptyStreets(context);
            Os.setenv("REX_MCLA_EMPTY_STREETS", empty ? "true" : "false", true);
            Log.i(TAG, "Applied graphics preset=" + preset + ", empty_streets=" + empty
                    + ", widescreen_fill=" + widescreenFill(context));
        } catch (Exception e) {
            Log.e(TAG, "Could not apply graphics/world profile", e);
        }
    }

    private static void applyAudio(Context context) {
        try {
            for (String key : OWNED_AUDIO_ENV) {
                Os.unsetenv(key);
            }
            // Always downmix 6ch -> 2ch stereo on Android (eliminates 5.1 endpoint overhead seen
            // in RedMagic 11 Air logs and keeps Android AAudio / spatializer stable).
            Os.setenv("REX_AUDIO_ANDROID_FORCE_STEREO", "true", true);
            Os.setenv("REX_AUDIO_THREAD_PRIORITY", "1", true);
            Os.setenv("REX_AUDIO_MIXER_PRIORITY", "1", true);
            Os.setenv("REX_AUDIO_STATS", "true", true);
            Os.setenv("REX_AUDIO_STATS_INTERVAL", "5", true);

            String profile = audioProfile(context);
            if (AUDIO_REDMAGIC_SAFE.equals(profile)) {
                // Deep anti-underrun queue (20 frames = 106 ms base, adaptive up to 32 = 170 ms)
                // + 1024-frame SDL AAudio callback quantum for RedMagic 11 Air / Snapdragon 8 Elite
                // or heavy shader compilation stalls.
                Os.setenv("REX_AUDIO_MAXQFRAMES", "20", true);
                Os.setenv("REX_AUDIO_ADAPTIVE_QUEUE", "true", true);
                Os.setenv("REX_AUDIO_ADAPTIVE_QUEUE_MAX", "32", true);
                Os.setenv("REX_AUDIO_ADAPTIVE_QUEUE_STEP", "4", true);
                Os.setenv("SDL_AUDIO_DEVICE_SAMPLE_FRAMES", "1024", true);
            } else if (AUDIO_LOW_LATENCY.equals(profile)) {
                Os.setenv("REX_AUDIO_MAXQFRAMES", "12", true);
                Os.setenv("REX_AUDIO_ADAPTIVE_QUEUE", "true", true);
                Os.setenv("REX_AUDIO_ADAPTIVE_QUEUE_MAX", "24", true);
                Os.setenv("REX_AUDIO_ADAPTIVE_QUEUE_STEP", "4", true);
                Os.setenv("REX_MCLA_AUDIO_LOW_LATENCY", "true", true);
                Os.setenv("SDL_AUDIO_DEVICE_SAMPLE_FRAMES", "512", true);
            } else {
                // Default S23 Adaptive Anti-Cut: 16 frames (85 ms) base, auto-grows to 28 (149 ms)
                // if frame drops occur, with 512-sample steady AAudio buffer.
                Os.setenv("REX_AUDIO_MAXQFRAMES", "16", true);
                Os.setenv("REX_AUDIO_ADAPTIVE_QUEUE", "true", true);
                Os.setenv("REX_AUDIO_ADAPTIVE_QUEUE_MAX", "28", true);
                Os.setenv("REX_AUDIO_ADAPTIVE_QUEUE_STEP", "4", true);
                Os.setenv("SDL_AUDIO_DEVICE_SAMPLE_FRAMES", "512", true);
            }
            Log.i(TAG, "Applied audio profile=" + profile + " (2ch stereo, priority=1)");
        } catch (Exception e) {
            Log.e(TAG, "Could not apply audio profile", e);
        }
    }

    private static void applySdlAndroidHints() {
        try {
            // Prevent SDL3 on Android from polling phone accelerometer/gyroscope as a joystick
            // at high frequency or synthesizing mouse events for every 240 Hz multitouch move!
            Os.setenv("SDL_ACCELEROMETER_AS_JOYSTICK", "0", true);
            Os.setenv("SDL_TOUCH_MOUSE_EVENTS", "0", true);
            Os.setenv("SDL_MOUSE_TOUCH_EVENTS", "0", true);
            Os.setenv("SDL_JOYSTICK_ALLOW_BACKGROUND_EVENTS", "0", true);
        } catch (Exception e) {
            Log.w(TAG, "Could not set SDL Android hints", e);
        }
    }
}
