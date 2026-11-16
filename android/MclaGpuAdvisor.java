package org.mcla.recomp;

import android.app.Activity;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/** Advisory only: never downloads, loads or selects a third-party library. */
final class MclaGpuAdvisor {
    private MclaGpuAdvisor() {}

    static void addTo(Activity activity, LinearLayout card) {
        TextView info = new TextView(activity);
        info.setTextColor(0xFFA8B9C4);
        info.setText("Detecting system GPU...");
        card.addView(info);
        new Thread(() -> {
            String renderer = systemRenderer();
            String text = recommendation(renderer);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                info.setText("System GPU (OpenGL renderer): " + renderer + "\n" + text);
            });
        }, "MCLA system GPU detection").start();
    }

    static String recommendation(String renderer) {
        String gpu = renderer.toLowerCase(Locale.ROOT);
        if (gpu.matches(".*adreno[^0-9]*[67][0-9]{2}.*")) {
            return "Start with the system driver. Optional: an ARM64 Mesa Turnip ZIP explicitly " +
                    "listing your GPU and Android requirements. Read the release notes; newer is not " +
                    "always better. This importer needs libvulkan_freedreno.so, not a proprietary " +
                    "Qualcomm package. " +
                    "No custom driver has been validated here for every device. Import trusted ZIPs only.";
        }
        if (gpu.contains("adreno")) {
            return "Use the system driver as the baseline. Do not assume Turnip supports this GPU; " +
                    "verify its exact model in the release notes. Experimental builds may crash or render incorrectly.";
        }
        if (gpu.contains("mali") || gpu.contains("immortalis")) {
            return "Unsupported GPU: Mali/Immortalis support has been discontinued. " +
                    "This Android project targets Snapdragon/Adreno. Importing a PanVK or " +
                    "Turnip ZIP does not make this build compatible with Mali.";
        }
        return "Use the system driver. No verified custom-driver recommendation for this GPU. " +
                "Detection uses system OpenGL, not the game's selected Vulkan driver.";
    }

    private static String systemRenderer() {
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) return "Unknown";
        EGLContext context = EGL14.EGL_NO_CONTEXT;
        EGLSurface surface = EGL14.EGL_NO_SURFACE;
        boolean initialized = false;
        try {
            int[] version = new int[2];
            initialized = EGL14.eglInitialize(display, version, 0, version, 1);
            if (!initialized) return "Unknown";
            int[] attributes = {EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE};
            EGLConfig[] configs = new EGLConfig[1];
            int[] count = new int[1];
            if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) ||
                    count[0] == 0) return "Unknown";
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
            surface = EGL14.eglCreatePbufferSurface(display, configs[0],
                    new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
            if (context == EGL14.EGL_NO_CONTEXT || surface == EGL14.EGL_NO_SURFACE ||
                    !EGL14.eglMakeCurrent(display, surface, surface, context)) return "Unknown";
            String renderer = GLES20.glGetString(GLES20.GL_RENDERER);
            return renderer == null ? "Unknown" : renderer;
        } catch (RuntimeException e) {
            return "Unknown";
        } finally {
            if (initialized) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT);
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context);
                EGL14.eglTerminate(display);
            }
            EGL14.eglReleaseThread();
        }
    }
}
