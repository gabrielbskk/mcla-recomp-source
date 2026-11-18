package org.mcla.recomp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import java.util.Arrays;

/**
 * Authentic Xbox 360 multitouch overlay + vertical analog Gas (RT) / Brake (LT) racing pedals
 * with live 0%–100% pressure gauges (zero heap allocations on 240 Hz touch & draw paths).
 */
public final class MclaTouchControls extends View {
    static final String PREFS_NAME = "touch_layout";
    static final String KEY_PEDALS = "pedals_enabled";
    static final String KEY_SIZE = "size_percent";
    static final String KEY_OPACITY = "opacity_percent";

    private static final long IDLE_MS = 6500;
    private static final int LEFT_STICK = 0x10000, RIGHT_STICK = 0x20000;
    private static final int LT = 0x40000, RT = 0x80000;
    private static final int LB = 0x0100, RB = 0x0200;
    private static final int START = 0x0010, BACK = 0x0020;
    private static final int LS_BTN = 0x0040, RS_BTN = 0x0080;
    private static final int DPAD_UP = 0x0001, DPAD_DOWN = 0x0002, DPAD_LEFT = 0x0004, DPAD_RIGHT = 0x0008;
    private static final int BTN_Y = 0x8000, BTN_A = 0x1000, BTN_X = 0x4000, BTN_B = 0x2000;

    private static final int MAX_FINGERS = 10;
    private static final int[] CONTROLS = {
        LEFT_STICK, RIGHT_STICK, LT, RT, LB, RB, START, BACK,
        LS_BTN, RS_BTN,
        DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, BTN_Y, BTN_A, BTN_X, BTN_B
    };
    private static final String[] LABELS = {
        "LS", "RS", "LT", "RT", "LB", "RB", "START", "BACK", "L3", "R3",
        "▲", "▼", "◀", "▶", "Y", "A", "X", "B"
    };
    // Authentic Xbox 360 jewel palette: Y=Amber, A=Emerald, X=Sapphire, B=Ruby
    private static final int[] COLORS = {
        0xFF94A3B8, 0xFF94A3B8, 0xFFFB7185, 0xFF34D399, 0xFF94A3B8, 0xFF94A3B8,
        0xFFCBD5E1, 0xFFCBD5E1, 0xFF64748B, 0xFF64748B,
        0xFF94A3B8, 0xFF94A3B8, 0xFF94A3B8, 0xFF94A3B8,
        0xFFF2B718, 0xFF35B636, 0xFF2278DC, 0xFFD9362E
    };

    private static final String[] PERCENT_LABELS = new String[101];
    static {
        for (int p = 0; p <= 100; p++) {
            PERCENT_LABELS[p] = p + "%";
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmpRect = new RectF();
    private final RectF fillRect = new RectF();
    private final SharedPreferences layout;
    private final boolean editing;

    // Precomputed layout geometry (rebuilt only when view size or layout prefs change).
    private final float[] normX = new float[CONTROLS.length];
    private final float[] normY = new float[CONTROLS.length];
    private final float[] cachedX = new float[CONTROLS.length];
    private final float[] cachedY = new float[CONTROLS.length];
    private int cachedSizePercent = 100;
    private int cachedOpacityPercent = 88;
    private boolean pedalsEnabled = true;
    private float cachedRadius = 1f;

    // Fixed primitive finger table (zero heap allocations during gameplay).
    private final int[] fingerIds = new int[MAX_FINGERS];
    private final int[] fingerControls = new int[MAX_FINGERS];
    private final float[] fingerX = new float[MAX_FINGERS];
    private final float[] fingerY = new float[MAX_FINGERS];
    private final float[] originX = new float[MAX_FINGERS];
    private final float[] originY = new float[MAX_FINGERS];
    private int activeFingerCount = 0;

    // Last sent JNI gamepad state + live visual feedback state.
    private int sentButtons = 0, sentLt = 0, sentRt = 0;
    private int sentLx = 0, sentLy = 0, sentRx = 0, sentRy = 0;
    private int visualLtPercent = 0, visualRtPercent = 0;

    private int dragPointer = -1;
    private int dragControlIndex = -1;
    private boolean showing = true;
    private boolean gamepadConnected;

    private final Runnable hideIdle = new Runnable() {
        @Override public void run() {
            if (!gamepadConnected && activeFingerCount == 0 && showing) {
                showing = false;
                invalidate();
            }
        }
    };

    private static native void nativeSetState(int buttons, int lt, int rt,
                                              int lx, int ly, int rx, int ry);

    public MclaTouchControls(Context context) {
        this(context, false);
    }

    public MclaTouchControls(Context context, boolean editing) {
        super(context);
        this.editing = editing;
        Arrays.fill(fingerIds, -1);
        layout = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        setFocusable(false);
        reloadLayoutFromPrefs();
        if (!editing) scheduleHide();
    }

    public static boolean isPedalsEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_PEDALS, true);
    }

    public static void setPedalsEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_PEDALS, enabled).apply();
    }

    private String keyX(int control) {
        return (pedalsEnabled ? "px64_" : "cx64_") + control;
    }

    private String keyY(int control) {
        return (pedalsEnabled ? "py64_" : "cy64_") + control;
    }

    private void reloadLayoutFromPrefs() {
        cachedSizePercent = Math.max(60, Math.min(150, layout.getInt(KEY_SIZE, 100)));
        cachedOpacityPercent = Math.max(35, Math.min(100, layout.getInt(KEY_OPACITY, 88)));
        pedalsEnabled = layout.getBoolean(KEY_PEDALS, true);
        for (int i = 0; i < CONTROLS.length; i++) {
            int control = CONTROLS[i];
            normX[i] = layout.getFloat(keyX(control), defaultX(control, pedalsEnabled));
            normY[i] = layout.getFloat(keyY(control), defaultY(control, pedalsEnabled));
        }
        recomputePixelGeometry(getWidth(), getHeight());
    }

    private void recomputePixelGeometry(int width, int height) {
        if (width <= 0 || height <= 0) return;
        cachedRadius = Math.min(width / 2.1f, height) * .076f * cachedSizePercent / 100f;
        for (int i = 0; i < CONTROLS.length; i++) {
            cachedX[i] = width * normX[i];
            cachedY[i] = height * normY[i];
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recomputePixelGeometry(w, h);
    }

    public void setSizePercent(int percent) {
        int clamped = Math.max(60, Math.min(150, percent));
        cachedSizePercent = clamped;
        layout.edit().putInt(KEY_SIZE, clamped).apply();
        recomputePixelGeometry(getWidth(), getHeight());
        invalidate();
    }

    public int getSizePercent() { return cachedSizePercent; }

    public void setOpacityPercent(int percent) {
        int clamped = Math.max(35, Math.min(100, percent));
        cachedOpacityPercent = clamped;
        layout.edit().putInt(KEY_OPACITY, clamped).apply();
        invalidate();
    }

    public int getOpacityPercent() { return cachedOpacityPercent; }

    public void setPedalsMode(boolean enabled) {
        pedalsEnabled = enabled;
        layout.edit().putBoolean(KEY_PEDALS, enabled).apply();
        reloadLayoutFromPrefs();
        invalidate();
    }

    public boolean getPedalsMode() { return pedalsEnabled; }

    public void resetLayout() {
        boolean keepPedals = pedalsEnabled;
        layout.edit().clear().putBoolean(KEY_PEDALS, keepPedals).apply();
        reloadLayoutFromPrefs();
        invalidate();
    }

    public void setGamepadConnected(boolean connected) {
        if (gamepadConnected == connected) return;
        gamepadConnected = connected;
        clearInput();
        setVisibility(connected ? GONE : VISIBLE);
        showing = !connected;
        if (!connected) scheduleHide();
        invalidate();
    }

    public void clearInput() {
        Arrays.fill(fingerIds, -1);
        activeFingerCount = 0;
        visualLtPercent = 0;
        visualRtPercent = 0;
        if (!editing) {
            dispatchNativeState(0, 0, 0, 0, 0, 0, 0, true);
        }
        handler.removeCallbacks(hideIdle);
        invalidate();
    }

    public void resumeControls() {
        reloadLayoutFromPrefs();
        if (!gamepadConnected) scheduleHide();
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideIdle);
        if (!editing && !gamepadConnected && activeFingerCount == 0) {
            handler.postDelayed(hideIdle, IDLE_MS);
        }
    }

    private static float defaultX(int control, boolean pedals) {
        switch (control) {
            case LEFT_STICK: return .145f;
            case RIGHT_STICK: return pedals ? .665f : .72f;
            case LT: return pedals ? .828f : .11f;
            case RT: return pedals ? .932f : .89f;
            case LB: return .145f;
            case RB: return .855f;
            case BACK: return .435f;
            case START: return .565f;
            case LS_BTN: return .055f;
            case RS_BTN: return pedals ? .575f : .62f;
            case DPAD_UP: case DPAD_DOWN: return .312f;
            case DPAD_LEFT: return .275f;
            case DPAD_RIGHT: return .349f;
            case BTN_Y: case BTN_A: return pedals ? .845f : .89f;
            case BTN_X: return pedals ? .798f : .845f;
            case BTN_B: return pedals ? .892f : .935f;
            default: return 0f;
        }
    }

    private static float defaultY(int control, boolean pedals) {
        switch (control) {
            case LEFT_STICK: return .74f;
            case RIGHT_STICK: return .77f;
            case LT: return pedals ? .765f : .12f;
            case RT: return pedals ? .730f : .12f;
            case LB: case RB: return .115f;
            case BACK: case START: return .115f;
            case LS_BTN: case RS_BTN: return .91f;
            case DPAD_UP: return .67f;
            case DPAD_DOWN: return .83f;
            case DPAD_LEFT: case DPAD_RIGHT: return .75f;
            case BTN_Y: return pedals ? .245f : .40f;
            case BTN_A: return pedals ? .455f : .62f;
            case BTN_X: case BTN_B: return pedals ? .350f : .51f;
            default: return 0f;
        }
    }

    private float pedalHalfWidth(int control) {
        return cachedRadius * (control == RT ? 0.56f : 0.50f);
    }

    private float pedalHalfHeight(int control) {
        return cachedRadius * (control == RT ? 1.52f : 1.24f);
    }

    /**
     * Vertical pedal pressure: touching higher up on the pedal = stronger pressure (up to 255 / 100%),
     * touching lower down = weaker pressure (down to 0%).
     */
    private int computePedalPressure(int control, float touchY, float centerY) {
        if (!pedalsEnabled) {
            float norm = Math.max(0f, Math.min(1f, 1f - (touchY - centerY) / (cachedRadius * 1.15f)));
            // Classic trigger button still gives full press if tapped near/above center, analog if pulled down.
            return (int) (255f * norm + 0.5f);
        }
        float halfH = pedalHalfHeight(control);
        float topY = centerY - halfH * 0.88f;
        float bottomY = centerY + halfH * 0.96f;
        float span = Math.max(1f, bottomY - topY);
        // Bottom of pedal starts gentle (~6%), rising smoothly to 100% near top of pedal.
        float ratio = (bottomY - touchY) / span;
        if (ratio <= 0f) return 0;
        if (ratio >= 1f) return 255;
        return Math.max(1, Math.min(255, (int) (ratio * 255f + 0.5f)));
    }

    private int hitIndex(float x, float y) {
        float baseR = cachedRadius;
        // Check pedals & face buttons before wide stick zones so adjacent touches are crisp.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < CONTROLS.length; i++) {
                boolean isStick = (i < 2);
                if ((pass == 0 && isStick) || (pass == 1 && !isStick)) continue;
                int control = CONTROLS[i];
                float cx = cachedX[i], cy = cachedY[i];
                if (pedalsEnabled && (control == LT || control == RT)) {
                    float hw = pedalHalfWidth(control) * 1.28f;
                    float hh = pedalHalfHeight(control) * 1.14f;
                    if (Math.abs(x - cx) <= hw && Math.abs(y - cy) <= hh) {
                        return i;
                    }
                    continue;
                }
                if (control == LB || control == RB) {
                    if (Math.abs(x - cx) <= baseR * 1.18f && Math.abs(y - cy) <= baseR * 0.62f) {
                        return i;
                    }
                    continue;
                }
                if (control == START || control == BACK || control == LS_BTN || control == RS_BTN) {
                    if (Math.abs(x - cx) <= baseR * 0.85f && Math.abs(y - cy) <= baseR * 0.52f) {
                        return i;
                    }
                    continue;
                }
                float dx = x - cx, dy = y - cy;
                float radius = baseR * (isStick ? 1.62f : (i >= 10 && i <= 13) ? 0.56f : 0.76f);
                if (dx * dx + dy * dy < radius * radius) return i;
            }
        }
        return -1;
    }

    private int findSlot(int pointerId) {
        for (int i = 0; i < MAX_FINGERS; i++) {
            if (fingerIds[i] == pointerId) return i;
        }
        return -1;
    }

    private int allocateSlot(int pointerId) {
        int existing = findSlot(pointerId);
        if (existing >= 0) return existing;
        for (int i = 0; i < MAX_FINGERS; i++) {
            if (fingerIds[i] == -1) {
                fingerIds[i] = pointerId;
                activeFingerCount++;
                return i;
            }
        }
        return -1;
    }

    private void releaseSlot(int pointerId) {
        for (int i = 0; i < MAX_FINGERS; i++) {
            if (fingerIds[i] == pointerId) {
                fingerIds[i] = -1;
                fingerControls[i] = 0;
                if (activeFingerCount > 0) activeFingerCount--;
                return;
            }
        }
    }

    private boolean editTouch(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN) {
            dragPointer = event.getPointerId(index);
            dragControlIndex = hitIndex(event.getX(index), event.getY(index));
        }
        if (dragControlIndex >= 0 && dragPointer >= 0 && getWidth() > 0 && getHeight() > 0) {
            int pointer = event.findPointerIndex(dragPointer);
            if (pointer >= 0) {
                float x = Math.max(.04f, Math.min(.96f, event.getX(pointer) / getWidth()));
                float y = Math.max(.06f, Math.min(.94f, event.getY(pointer) / getHeight()));
                normX[dragControlIndex] = x;
                normY[dragControlIndex] = y;
                cachedX[dragControlIndex] = getWidth() * x;
                cachedY[dragControlIndex] = getHeight() * y;
                invalidate();
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (dragControlIndex >= 0 && action == MotionEvent.ACTION_UP) {
                int control = CONTROLS[dragControlIndex];
                layout.edit()
                        .putFloat(keyX(control), normX[dragControlIndex])
                        .putFloat(keyY(control), normY[dragControlIndex])
                        .apply();
            } else if (action == MotionEvent.ACTION_CANCEL) {
                reloadLayoutFromPrefs();
            }
            dragPointer = -1;
            dragControlIndex = -1;
            invalidate();
        }
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (editing) return editTouch(event);
        if (gamepadConnected) return false;
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        boolean needsRedraw = false;

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (!showing) {
                showing = true;
                needsRedraw = true;
            }
            int id = event.getPointerId(index);
            float px = event.getX(index), py = event.getY(index);
            int controlIdx = hitIndex(px, py);
            if (controlIdx >= 0) {
                int slot = allocateSlot(id);
                if (slot >= 0) {
                    int control = CONTROLS[controlIdx];
                    fingerControls[slot] = control;
                    fingerX[slot] = px;
                    fingerY[slot] = py;
                    originX[slot] = px;
                    originY[slot] = py;
                    needsRedraw = true;
                }
            }
            handler.removeCallbacks(hideIdle);
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            clearInput();
            scheduleHide();
            return true;
        }

        int pointerCount = event.getPointerCount();
        for (int i = 0; i < pointerCount; i++) {
            int slot = findSlot(event.getPointerId(i));
            if (slot >= 0) {
                fingerX[slot] = event.getX(i);
                fingerY[slot] = event.getY(i);
                int control = fingerControls[slot];
                if (action == MotionEvent.ACTION_MOVE &&
                        (control == LEFT_STICK || control == RIGHT_STICK ||
                         control == LT || control == RT)) {
                    needsRedraw = true;
                }
            }
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int slot = findSlot(event.getPointerId(index));
            if (slot >= 0) {
                needsRedraw = true;
            }
            releaseSlot(event.getPointerId(index));
        }

        if (sendState()) {
            needsRedraw = true;
        }
        scheduleHide();
        if (needsRedraw) {
            invalidate();
        }
        return true;
    }

    private boolean dispatchNativeState(int buttons, int lt, int rt,
                                        int lx, int ly, int rx, int ry, boolean force) {
        int newLtPct = (lt * 100 + 127) / 255;
        int newRtPct = (rt * 100 + 127) / 255;
        boolean pctChanged = (newLtPct != visualLtPercent) || (newRtPct != visualRtPercent)
                || (buttons != sentButtons);
        visualLtPercent = newLtPct;
        visualRtPercent = newRtPct;
        if (!force && buttons == sentButtons && lt == sentLt && rt == sentRt &&
                lx == sentLx && ly == sentLy && rx == sentRx && ry == sentRy) {
            return pctChanged;
        }
        sentButtons = buttons;
        sentLt = lt;
        sentRt = rt;
        sentLx = lx;
        sentLy = ly;
        sentRx = rx;
        sentRy = ry;
        nativeSetState(buttons, lt, rt, lx, ly, rx, ry);
        return pctChanged;
    }

    private boolean sendState() {
        int buttons = 0, lt = 0, rt = 0, lx = 0, ly = 0, rx = 0, ry = 0;
        float radius = cachedRadius;
        for (int i = 0; i < MAX_FINGERS; i++) {
            if (fingerIds[i] == -1) continue;
            int control = fingerControls[i];
            if (control == LT || control == RT) {
                float triggerCy = (control == LT) ? cachedY[2] : cachedY[3];
                int pressure = computePedalPressure(control, fingerY[i], triggerCy);
                if (control == LT) {
                    if (pressure > lt) lt = pressure;
                } else {
                    if (pressure > rt) rt = pressure;
                }
            } else if (control == LEFT_STICK || control == RIGHT_STICK) {
                float scale = radius * 1.12f;
                float dx = (fingerX[i] - originX[i]) / scale;
                float dy = (originY[i] - fingerY[i]) / scale;
                float length = (float) Math.hypot(dx, dy);
                if (length < .08f) {
                    dx = 0f;
                    dy = 0f;
                } else {
                    float magnitude = Math.min(1f, (length - .08f) / .92f);
                    dx = dx / length * magnitude;
                    dy = dy / length * magnitude;
                }
                if (control == LEFT_STICK) {
                    lx = (int) (dx * 32767f);
                    ly = (int) (dy * 32767f);
                } else {
                    rx = (int) (dx * 32767f);
                    ry = (int) (dy * 32767f);
                }
            } else {
                buttons |= control;
            }
        }
        return dispatchNativeState(buttons, lt, rt, lx, ly, rx, ry, false);
    }

    private static int scaleAlpha(int argb, float factor) {
        int a = Math.max(0, Math.min(255, (int) (((argb >>> 24) & 0xFF) * factor + 0.5f)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!showing || gamepadConnected) return;

        final float alphaScale = editing ? 1.0f : (cachedOpacityPercent / 100f);
        final float baseR = cachedRadius;

        if (editing) {
            drawEditorGrid(canvas);
        }

        // Draw Xbox 360 D-pad circular dish backdrop centered around the 4 D-pad directions
        drawXboxDpadBackdrop(canvas, baseR, alphaScale);

        for (int i = 0; i < CONTROLS.length; i++) {
            int control = CONTROLS[i];
            float cx = cachedX[i], cy = cachedY[i];
            boolean selectedInEditor = editing && (dragControlIndex == i);

            if (i < 2) {
                drawXboxThumbstick(canvas, control, LABELS[i], cx, cy, baseR, alphaScale, selectedInEditor);
            } else if (pedalsEnabled && (control == LT || control == RT)) {
                int pct = editing ? (control == RT ? 72 : 40)
                        : (control == RT ? visualRtPercent : visualLtPercent);
                drawRacingPedal(canvas, control, cx, cy, baseR, pct, alphaScale, selectedInEditor);
            } else if (control == LB || control == RB || (!pedalsEnabled && (control == LT || control == RT))) {
                boolean pressed = (control == LT) ? (visualLtPercent > 0)
                        : (control == RT) ? (visualRtPercent > 0)
                        : ((sentButtons & control) != 0);
                drawXboxBumper(canvas, LABELS[i], COLORS[i], cx, cy, baseR, pressed, alphaScale, selectedInEditor);
            } else if (control == START || control == BACK || control == LS_BTN || control == RS_BTN) {
                boolean pressed = (sentButtons & control) != 0;
                drawXboxPillButton(canvas, LABELS[i], cx, cy, baseR, pressed, alphaScale, selectedInEditor);
            } else if (i >= 10 && i <= 13) {
                boolean pressed = (sentButtons & control) != 0;
                drawXboxDpadArm(canvas, LABELS[i], cx, cy, baseR, pressed, alphaScale, selectedInEditor);
            } else {
                boolean pressed = (sentButtons & control) != 0;
                drawXboxJewelButton(canvas, LABELS[i], COLORS[i], cx, cy, baseR, pressed, alphaScale, selectedInEditor);
            }
        }
    }

    private void drawEditorGrid(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(0x2238BDF8);
        for (int gx = 1; gx < 10; gx++) {
            float x = w * gx / 10f;
            canvas.drawLine(x, 0, x, h, paint);
        }
        for (int gy = 1; gy < 6; gy++) {
            float y = h * gy / 6f;
            canvas.drawLine(0, y, w, y, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawXboxDpadBackdrop(Canvas canvas, float baseR, float alphaScale) {
        float cx = (cachedX[10] + cachedX[11] + cachedX[12] + cachedX[13]) * 0.25f;
        float cy = (cachedY[10] + cachedY[11] + cachedY[12] + cachedY[13]) * 0.25f;
        // Only draw unified dish if D-pad buttons are still grouped near each other.
        if (Math.hypot(cachedX[10] - cx, cachedY[10] - cy) > baseR * 2.2f) return;
        float dishR = baseR * 1.42f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(scaleAlpha(0xA60B1017, alphaScale));
        canvas.drawCircle(cx, cy, dishR, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, baseR * 0.045f));
        paint.setColor(scaleAlpha(0x88334155, alphaScale));
        canvas.drawCircle(cx, cy, dishR, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawXboxDpadArm(Canvas canvas, String arrow, float cx, float cy,
                                 float baseR, boolean pressed, float alphaScale, boolean selected) {
        float r = baseR * 0.46f;
        tmpRect.set(cx - r, cy - r, cx + r, cy + r);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(scaleAlpha(pressed ? 0xEE38BDF8 : 0xCC1E293B, alphaScale));
        canvas.drawRoundRect(tmpRect, baseR * 0.16f, baseR * 0.16f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.8f, baseR * 0.04f));
        paint.setColor(scaleAlpha(selected ? 0xFF38BDF8 : (pressed ? 0xFFFFFFFF : 0x9964748B), alphaScale));
        canvas.drawRoundRect(tmpRect, baseR * 0.16f, baseR * 0.16f, paint);
        paint.setStyle(Paint.Style.FILL);

        textPaint.setColor(scaleAlpha(pressed ? 0xFF041019 : 0xFFE2E8F0, alphaScale));
        textPaint.setTextSize(baseR * 0.34f);
        canvas.drawText(arrow, cx, cy - (textPaint.ascent() + textPaint.descent()) * 0.5f, textPaint);
    }

    private void drawXboxThumbstick(Canvas canvas, int control, String label,
                                    float baseX, float baseY, float baseR,
                                    float alphaScale, boolean selected) {
        float thumbX = baseX, thumbY = baseY;
        boolean active = false;
        if (!editing) {
            for (int slot = 0; slot < MAX_FINGERS; slot++) {
                if (fingerIds[slot] == -1 || fingerControls[slot] != control) continue;
                active = true;
                baseX = originX[slot];
                baseY = originY[slot];
                float dx = fingerX[slot] - baseX, dy = fingerY[slot] - baseY;
                float distance = (float) Math.hypot(dx, dy);
                float travel = baseR * 0.86f;
                if (distance > travel) {
                    dx *= travel / distance;
                    dy *= travel / distance;
                }
                thumbX = baseX + dx;
                thumbY = baseY + dy;
                break;
            }
        }

        float gateR = baseR * 1.30f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(scaleAlpha(0x88080D14, alphaScale));
        canvas.drawCircle(baseX, baseY, gateR, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, baseR * 0.055f));
        paint.setColor(scaleAlpha(selected || active ? 0xDD38BDF8 : 0x88475569, alphaScale));
        canvas.drawCircle(baseX, baseY, gateR, paint);

        // Concave Xbox 360 dark graphite rubber cap + 4 cardinal grip nubs
        float capR = baseR * 0.62f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(scaleAlpha(active ? 0xEE283548 : 0xD81E2631, alphaScale));
        canvas.drawCircle(thumbX, thumbY, capR, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, baseR * 0.05f));
        paint.setColor(scaleAlpha(active ? 0xFF38BDF8 : 0xBB64748B, alphaScale));
        canvas.drawCircle(thumbX, thumbY, capR, paint);

        // Inner concave dish ring
        paint.setStrokeWidth(Math.max(1.5f, baseR * 0.03f));
        paint.setColor(scaleAlpha(0x770F172A, alphaScale));
        canvas.drawCircle(thumbX, thumbY, capR * 0.68f, paint);

        // 4 signature Xbox 360 tactile rubber dots on the stick rim
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(scaleAlpha(0xBB94A3B8, alphaScale));
        float nubOffset = capR * 0.80f;
        float nubR = Math.max(2f, baseR * 0.045f);
        canvas.drawCircle(thumbX, thumbY - nubOffset, nubR, paint);
        canvas.drawCircle(thumbX, thumbY + nubOffset, nubR, paint);
        canvas.drawCircle(thumbX - nubOffset, thumbY, nubR, paint);
        canvas.drawCircle(thumbX + nubOffset, thumbY, nubR, paint);

        textPaint.setColor(scaleAlpha(0xCCF1F5F9, alphaScale));
        textPaint.setTextSize(baseR * 0.34f);
        canvas.drawText(label, thumbX, thumbY - (textPaint.ascent() + textPaint.descent()) * 0.5f, textPaint);
    }

    private void drawXboxJewelButton(Canvas canvas, String label, int jewelColor,
                                     float cx, float cy, float baseR,
                                     boolean pressed, float alphaScale, boolean selected) {
        float r = baseR * (pressed ? 0.70f : 0.65f);
        if (pressed) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(scaleAlpha((jewelColor & 0x00FFFFFF) | 0x55000000, alphaScale));
            canvas.drawCircle(cx, cy, r * 1.22f, paint);
        }

        // Dark Xbox 360 outer socket bezel
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(scaleAlpha(0xCC090D14, alphaScale));
        canvas.drawCircle(cx, cy, r * 1.06f, paint);

        // Colored Xbox 360 glossy jewel body
        int bodyAlpha = pressed ? 0xF5000000 : 0xCC000000;
        paint.setColor(scaleAlpha((jewelColor & 0x00FFFFFF) | bodyAlpha, alphaScale));
        canvas.drawCircle(cx, cy, r * 0.92f, paint);

        // Top glossy dome specular highlight
        paint.setColor(scaleAlpha(pressed ? 0x66FFFFFF : 0x3DFFFFFF, alphaScale));
        tmpRect.set(cx - r * 0.62f, cy - r * 0.78f, cx + r * 0.62f, cy - r * 0.06f);
        canvas.drawOval(tmpRect, paint);

        // Rim stroke
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, baseR * 0.05f));
        paint.setColor(scaleAlpha(selected ? 0xFF38BDF8 : (pressed ? 0xFFFFFFFF : jewelColor), alphaScale));
        canvas.drawCircle(cx, cy, r * 0.95f, paint);
        paint.setStyle(Paint.Style.FILL);

        // Crisp Xbox 360 letter with subtle dark shadow
        textPaint.setTextSize(baseR * 0.54f);
        float textY = cy - (textPaint.ascent() + textPaint.descent()) * 0.5f;
        textPaint.setColor(scaleAlpha(0xAA000000, alphaScale));
        canvas.drawText(label, cx + 1.5f, textY + 1.5f, textPaint);
        textPaint.setColor(scaleAlpha(0xFFFFFFFF, alphaScale));
        canvas.drawText(label, cx, textY, textPaint);
    }

    private void drawXboxBumper(Canvas canvas, String label, int accentColor,
                                float cx, float cy, float baseR,
                                boolean pressed, float alphaScale, boolean selected) {
        float hw = baseR * 1.05f;
        float hh = baseR * 0.46f;
        tmpRect.set(cx - hw, cy - hh, cx + hw, cy + hh);
        float corner = hh * 0.85f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(scaleAlpha(pressed ? 0xE6334155 : 0xB8111824, alphaScale));
        canvas.drawRoundRect(tmpRect, corner, corner, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, baseR * 0.048f));
        paint.setColor(scaleAlpha(selected ? 0xFF38BDF8 : (pressed ? 0xFFFFFFFF : accentColor), alphaScale));
        canvas.drawRoundRect(tmpRect, corner, corner, paint);
        paint.setStyle(Paint.Style.FILL);

        textPaint.setColor(scaleAlpha(0xFFF8FAFC, alphaScale));
        textPaint.setTextSize(baseR * 0.38f);
        canvas.drawText(label, cx, cy - (textPaint.ascent() + textPaint.descent()) * 0.5f, textPaint);
    }

    private void drawXboxPillButton(Canvas canvas, String label, float cx, float cy,
                                    float baseR, boolean pressed, float alphaScale, boolean selected) {
        float hw = baseR * 0.72f;
        float hh = baseR * 0.34f;
        tmpRect.set(cx - hw, cy - hh, cx + hw, cy + hh);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(scaleAlpha(pressed ? 0xE638BDF8 : 0xAE0F172A, alphaScale));
        canvas.drawRoundRect(tmpRect, hh, hh, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.6f, baseR * 0.038f));
        paint.setColor(scaleAlpha(selected ? 0xFF38BDF8 : 0x9964748B, alphaScale));
        canvas.drawRoundRect(tmpRect, hh, hh, paint);
        paint.setStyle(Paint.Style.FILL);

        textPaint.setColor(scaleAlpha(pressed ? 0xFF041019 : 0xFFE2E8F0, alphaScale));
        textPaint.setTextSize(baseR * 0.28f);
        canvas.drawText(label, cx, cy - (textPaint.ascent() + textPaint.descent()) * 0.5f, textPaint);
    }

    private void drawRacingPedal(Canvas canvas, int control, float cx, float cy,
                                 float baseR, int percent, float alphaScale, boolean selected) {
        final boolean isGas = (control == RT);
        final float hw = pedalHalfWidth(control);
        final float hh = pedalHalfHeight(control);
        final float corner = hw * 0.34f;
        final int clampedPct = Math.max(0, Math.min(100, percent));
        final int accent = isGas ? 0xFF22C55E : 0xFFF43F5E;
        final int fillTint = isGas ? 0xCC10B981 : 0xCCF43F5E;

        // Pedal metallic outer housing
        tmpRect.set(cx - hw, cy - hh, cx + hw, cy + hh);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(scaleAlpha(0xC80B121B, alphaScale));
        canvas.drawRoundRect(tmpRect, corner, corner, paint);

        // Live vertical pressure fill rising from bottom (0%) to top (100%)
        if (clampedPct > 0) {
            float innerPad = Math.max(2f, baseR * 0.04f);
            float innerTop = tmpRect.top + innerPad;
            float innerBottom = tmpRect.bottom - innerPad;
            float fillTop = innerBottom - (innerBottom - innerTop) * (clampedPct / 100f);
            fillRect.set(tmpRect.left + innerPad, fillTop, tmpRect.right - innerPad, innerBottom);
            paint.setColor(scaleAlpha(fillTint, alphaScale));
            canvas.drawRoundRect(fillRect, corner * 0.8f, corner * 0.8f, paint);
        }

        // Brushed metallic pedal grip treads (horizontal grooves)
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, baseR * 0.038f));
        paint.setColor(scaleAlpha(0x5594A3B8, alphaScale));
        int treads = isGas ? 6 : 5;
        for (int t = 1; t <= treads; t++) {
            float ty = tmpRect.top + (tmpRect.height() * t) / (treads + 1f);
            canvas.drawLine(cx - hw * 0.64f, ty, cx + hw * 0.64f, ty, paint);
        }

        // Pedal rim stroke
        paint.setStrokeWidth(Math.max(2.2f, baseR * 0.052f));
        paint.setColor(scaleAlpha(selected ? 0xFF38BDF8 : (clampedPct > 0 ? accent : 0xAA475569), alphaScale));
        canvas.drawRoundRect(tmpRect, corner, corner, paint);
        paint.setStyle(Paint.Style.FILL);

        // Live percentage pill above the pedal ("0%" .. "100%")
        float pillHw = hw * 0.96f;
        float pillHh = baseR * 0.25f;
        float pillCy = tmpRect.top - pillHh - baseR * 0.08f;
        fillRect.set(cx - pillHw, pillCy - pillHh, cx + pillHw, pillCy + pillHh);
        paint.setColor(scaleAlpha(clampedPct > 0 ? 0xE6070D14 : 0xB0070D14, alphaScale));
        canvas.drawRoundRect(fillRect, pillHh, pillHh, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, baseR * 0.03f));
        paint.setColor(scaleAlpha(clampedPct > 0 ? accent : 0x66475569, alphaScale));
        canvas.drawRoundRect(fillRect, pillHh, pillHh, paint);
        paint.setStyle(Paint.Style.FILL);

        textPaint.setTextSize(baseR * 0.31f);
        textPaint.setColor(scaleAlpha(clampedPct > 0 ? 0xFFFFFFFF : 0xFFCBD5E1, alphaScale));
        canvas.drawText(PERCENT_LABELS[clampedPct], cx,
                pillCy - (textPaint.ascent() + textPaint.descent()) * 0.5f, textPaint);

        // Pedal role micro-label near bottom ("GAS" / "BRK")
        textPaint.setTextSize(baseR * 0.26f);
        textPaint.setColor(scaleAlpha(0xEEFFFFFF, alphaScale));
        String tag = isGas ? "GAS" : "BRK";
        float tagY = tmpRect.bottom - baseR * 0.22f;
        canvas.drawText(tag, cx, tagY, textPaint);
    }
}
