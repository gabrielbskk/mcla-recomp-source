package org.mcla.recomp;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.input.InputManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.system.Os;
import android.util.Log;
import android.util.TypedValue;
import android.view.InputDevice;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import org.libsdl.app.SDLActivity;

public final class MclaActivity extends SDLActivity {
    private static final String TAG = "MCLA";
    private static native String nativePollPerfOverlay();

    private MclaTouchControls touchControls;
    private TextView fpsOverlayView;
    private String lastPerfText = "";
    private boolean fpsOverlayEnabled = true;
    private boolean activityResumed = false;
    private InputManager inputManager;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private final AudioManager.OnAudioFocusChangeListener audioFocusListener =
            focusChange -> { };
    private final InputManager.InputDeviceListener gamepadListener =
            new InputManager.InputDeviceListener() {
                @Override public void onInputDeviceAdded(int id) { updateGamepad(); }
                @Override public void onInputDeviceRemoved(int id) { updateGamepad(); }
                @Override public void onInputDeviceChanged(int id) { updateGamepad(); }
            };

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void updateGamepad() {
        if (touchControls == null) return;
        boolean connected = false;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null || device.isVirtual()) continue;
            int sources = device.getSources();
            if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
                    (MclaKeyboardMouse.mode(this) != 0 &&
                     (device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC ||
                      (MclaKeyboardMouse.mode(this) == 2 &&
                       (sources & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE)))) {
                connected = true;
                break;
            }
        }
        touchControls.setGamepadConnected(connected);
    }

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable perfOverlayTick = new Runnable() {
        @Override public void run() {
            if (!activityResumed || !fpsOverlayEnabled || fpsOverlayView == null) return;
            String sample = null;
            try {
                sample = nativePollPerfOverlay();
            } catch (UnsatisfiedLinkError ignored) {
                // Native library may still be finishing startup.
            }
            if (sample != null && !sample.isEmpty()) {
                if (fpsOverlayView.getVisibility() != View.VISIBLE) {
                    fpsOverlayView.setVisibility(View.VISIBLE);
                }
                if (!sample.equals(lastPerfText)) {
                    lastPerfText = sample;
                    fpsOverlayView.setText(sample);
                }
            }
            uiHandler.postDelayed(this, 500);
        }
    };

    @Override
    public void setRequestedOrientation(int orientation) {
        // SDL can request FULL_USER for a resizable window, which returns this
        // game to portrait when system auto-rotation is locked.
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    @Override
    protected void onCreate(Bundle state) {
        fpsOverlayEnabled = MclaRuntimeProfiles.fpsOverlay(this);
        MclaRuntimeProfiles.applyAll(this);
        MclaKeyboardMouse.apply(this);
        applyRedMagicShaderEnvironment();
        applyLogEnvironment();
        applyDriverDebugEnvironment();
        MclaLogExporter.startPeriodic(this);

        File source = new File(getApplicationInfo().nativeLibraryDir, "librexgpu-xenos.so");
        File target = new File(getFilesDir(), "librexgpu-xenos.so");
        boolean needsPluginCopy = !target.isFile()
                || target.length() != source.length()
                || target.lastModified() < source.lastModified();
        if (needsPluginCopy) {
            File part = new File(getFilesDir(), "librexgpu-xenos.so.partial");
            try {
                try (FileInputStream input = new FileInputStream(source);
                     FileOutputStream output = new FileOutputStream(part)) {
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                    output.getFD().sync();
                }
                if (!part.renameTo(target)) {
                    throw new java.io.IOException("Could not rename staged Xenos plugin");
                }
                target.setReadable(false, false);
                target.setWritable(false, false);
                target.setExecutable(false, false);
                target.setReadable(true, true);
                target.setWritable(true, true);
                target.setExecutable(true, true);
            } catch (Exception e) {
                part.delete();
                Log.e(TAG, "Could not stage the Xenos Vulkan plugin", e);
            }
        }
        super.onCreate(state);
        if (mLayout != null) {
            touchControls = new MclaTouchControls(this);
            mLayout.addView(touchControls, new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT));
            if (fpsOverlayEnabled) {
                fpsOverlayView = createFpsOverlayView();
                RelativeLayout.LayoutParams hudParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                hudParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                hudParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                hudParams.setMargins(dp(14), dp(10), 0, 0);
                mLayout.addView(fpsOverlayView, hudParams);
            }
            inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
            if (inputManager != null) inputManager.registerInputDeviceListener(gamepadListener, uiHandler);
            updateGamepad();
        }
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        if (Build.VERSION.SDK_INT >= 24) {
            boolean sustained = MclaRuntimeProfiles.sustainedPerformance(this);
            PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (power != null && power.isSustainedPerformanceModeSupported()) {
                getWindow().setSustainedPerformanceMode(sustained);
                Log.i(TAG, sustained
                        ? "Sustained performance mode enabled"
                        : "Sustained performance mode disabled");
            } else {
                Log.i(TAG, "Sustained performance mode unavailable");
            }
        }
        applyImmersiveMode();
    }

    private TextView createFpsOverlayView() {
        TextView view = new TextView(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC070D14);
        bg.setCornerRadius(dp(9));
        bg.setStroke(Math.max(1, dp(1)), 0x6638BDF8);
        view.setBackground(bg);
        view.setPadding(dp(9), dp(4), dp(9), dp(4));
        view.setTextColor(0xFFEAF6FF);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        view.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        view.setIncludeFontPadding(false);
        view.setClickable(false);
        view.setFocusable(false);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        view.setVisibility(View.GONE);
        return view;
    }

    private void applyLogEnvironment() {
        // One stable log basename across launches; bounded rotation keeps
        // warnings from consuming unlimited private storage while MclaLogExporter
        // mirrors live chunks to Documents/MCLA/logs/MCLA-diagnostic.txt.
        File directory = new File(getFilesDir(), "logs");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            Log.e(TAG, "Could not create log directory");
            return;
        }
        try {
            Os.setenv("REX_LOG_FILE", new File(directory, "mcla.log").getAbsolutePath(), true);
            Os.setenv("REX_LOG_MAX_FILES", "1", true);
            Os.setenv("REX_LOG_MAX_FILE_SIZE_MB", "10", true);
        } catch (Exception e) {
            Log.e(TAG, "Could not configure the single log", e);
        }
    }

    private void applyRedMagicShaderEnvironment() {
        try {
            if (MclaBuildVariant.isRedMagicAudioTest(this)) {
                boolean enabled = getSharedPreferences("redmagic_diagnostics", MODE_PRIVATE)
                        .getBoolean("shader_pack_auto", true);
                Os.setenv("REX_MCLA_SHADER_PACK_AUTO", enabled ? "true" : "false", true);
                Log.i(TAG, "RedMagic shader pack auto-import=" + enabled
                        + "; existing shader storage is retained");
            } else {
                Os.unsetenv("REX_MCLA_SHADER_PACK_AUTO");
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not apply the shader pack import mode", e);
        }
    }

    private void applyDriverDebugEnvironment() {
        // The Vulkan driver is loaded during SDLActivity.onCreate. Keep this
        // private, opt-in diagnostic setting outside mcla.toml and apply it first.
        File config = new File(getFilesDir(), "driver.env");
        String debug = null;
        try {
            if (config.exists()) {
                if (!config.isFile() || config.length() > 512) {
                    throw new IllegalArgumentException("driver.env must be a file of at most 512 bytes");
                }
                try (BufferedReader reader = new BufferedReader(new FileReader(config))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        if (debug != null || !line.startsWith("TU_DEBUG=")) {
                            throw new IllegalArgumentException("driver.env accepts one TU_DEBUG entry");
                        }
                        debug = line.substring("TU_DEBUG=".length());
                        if (!debug.equals("noubwc") && !debug.equals("syncdraw") &&
                                !debug.equals("flushall") && !debug.equals("nolrz") &&
                                !debug.equals("sysmem")) {
                            throw new IllegalArgumentException("unsupported TU_DEBUG value");
                        }
                    }
                }
            }
            boolean turnipSelected = false;
            File selection = new File(getFilesDir(), "gpu-drivers/selected.txt");
            if (selection.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(selection))) {
                    turnipSelected = "libvulkan_freedreno.so".equals(reader.readLine());
                }
            }
            if (!turnipSelected) {
                // Turnip debug flags must never leak to system Vulkan or PanVK.
                debug = null;
            } else if (debug == null) {
                // Default to syncdraw on Turnip: fixes wheel & headlight flickering
                // on Snapdragon/Adreno Turnip drivers.
                debug = getSharedPreferences("driver_debug", MODE_PRIVATE)
                        .getString("tu_debug_s23", "syncdraw");
                if ("none".equals(debug) || (!debug.equals("syncdraw") && !debug.equals("noubwc") &&
                        !debug.equals("flushall") && !debug.equals("nolrz") &&
                        !debug.equals("sysmem"))) {
                    debug = null;
                }
            }
            if (debug != null) {
                Os.setenv("TU_DEBUG", debug, true);
                Log.i(TAG, "Driver debug: TU_DEBUG=" + Os.getenv("TU_DEBUG"));
            } else if (Os.getenv("TU_DEBUG") != null) {
                Os.unsetenv("TU_DEBUG");
                Log.i(TAG, "Driver debug: TU_DEBUG cleared");
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not apply driver.env; no driver debug test is active", e);
            try {
                Os.unsetenv("TU_DEBUG");
            } catch (Exception clearError) {
                Log.e(TAG, "Could not clear TU_DEBUG", clearError);
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
        } else if (touchControls != null) {
            touchControls.clearInput();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        MclaLogExporter.startPeriodic(this);
        requestAudioFocus();
        updateGamepad();
        if (touchControls != null) touchControls.resumeControls();
        uiHandler.removeCallbacks(perfOverlayTick);
        if (fpsOverlayEnabled && fpsOverlayView != null) {
            uiHandler.postDelayed(perfOverlayTick, 500);
        }
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        uiHandler.removeCallbacks(perfOverlayTick);
        if (touchControls != null) touchControls.clearInput();
        abandonAudioFocus();
        super.onPause();
        MclaLogExporter.exportAsync(this);
    }

    @Override
    protected void onDestroy() {
        activityResumed = false;
        uiHandler.removeCallbacks(perfOverlayTick);
        if (inputManager != null) inputManager.unregisterInputDeviceListener(gamepadListener);
        if (touchControls != null) touchControls.clearInput();
        MclaLogExporter.stopPeriodic();
        MclaLogExporter.exportAsync(this);
        super.onDestroy();
    }

    private void requestAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            if (audioFocusRequest == null) {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attributes)
                        .setOnAudioFocusChangeListener(audioFocusListener)
                        .setWillPauseWhenDucked(false)
                        .build();
            }
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            audioManager.requestAudioFocus(audioFocusListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            if (audioFocusRequest != null) audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(audioFocusListener);
        }
    }

    private void applyImmersiveMode() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }
}
