package org.mcla.recomp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import java.util.Arrays;

/** Xbox-style multitouch pad over the SDL surface (zero-allocation 240 Hz touch path). */
public final class MclaTouchControls extends View {
    private static final long IDLE_MS = 6000;
    private static final int LEFT_STICK = 0x10000, RIGHT_STICK = 0x20000;
    private static final int LT = 0x40000, RT = 0x80000;
    private static final int MAX_FINGERS = 10;
    private static final int[] CONTROLS = {
        LEFT_STICK, RIGHT_STICK, LT, RT, 0x0100, 0x0200, 0x0010, 0x0020,
        0x0040, 0x0080,
        0x0001, 0x0002, 0x0004, 0x0008, 0x8000, 0x1000, 0x4000, 0x2000
    };
    private static final String[] LABELS = {
        "L", "R", "LT", "RT", "LB", "RB", "BACK", "START", "LS", "RS",
        "↑", "↓", "←", "→", "Y", "A", "X", "B"
    };
    private static final int[] COLORS = {
        0xFF888888, 0xFF888888, 0xFF777777, 0xFF777777, 0xFF777777, 0xFF777777,
        0xFF888888, 0xFF888888, 0xFF777777, 0xFF777777,
        0xFF777777, 0xFF777777, 0xFF777777, 0xFF777777,
        0xFFF4C137, 0xFF50B743, 0xFF428BD6, 0xFFDE4F4B
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SharedPreferences layout;
    private final boolean editing;

    // Precomputed layout geometry (rebuilt only when view size or layout prefs change).
    private final float[] normX = new float[CONTROLS.length];
    private final float[] normY = new float[CONTROLS.length];
    private final float[] cachedX = new float[CONTROLS.length];
    private final float[] cachedY = new float[CONTROLS.length];
    private int cachedSizePercent = 100;
    private float cachedRadius = 1f;

    // Fixed primitive finger table (zero heap allocations during gameplay).
    private final int[] fingerIds = new int[MAX_FINGERS];
    private final int[] fingerControls = new int[MAX_FINGERS];
    private final float[] fingerX = new float[MAX_FINGERS];
    private final float[] fingerY = new float[MAX_FINGERS];
    private final float[] originX = new float[MAX_FINGERS];
    private final float[] originY = new float[MAX_FINGERS];
    private int activeFingerCount = 0;

    // Last sent JNI gamepad state to suppress redundant JNI + mutex traffic at 240 Hz.
    private int sentButtons = 0, sentLt = 0, sentRt = 0;
    private int sentLx = 0, sentLy = 0, sentRx = 0, sentRy = 0;

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
        layout = context.getSharedPreferences("touch_layout", Context.MODE_PRIVATE);
        setFocusable(false);
        reloadLayoutFromPrefs();
        if (!editing) scheduleHide();
    }

    private void reloadLayoutFromPrefs() {
        cachedSizePercent = layout.getInt("size_percent", 100);
        for (int i = 0; i < CONTROLS.length; i++) {
            int control = CONTROLS[i];
            normX[i] = layout.getFloat("x_" + control, defaultX(control));
            normY[i] = layout.getFloat("y_" + control, defaultY(control));
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
        layout.edit().putInt("size_percent", clamped).apply();
        recomputePixelGeometry(getWidth(), getHeight());
        invalidate();
    }

    public int getSizePercent() { return cachedSizePercent; }

    public void resetLayout() {
        layout.edit().clear().apply();
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

    private static float defaultX(int control) {
        switch (control) {
            case LEFT_STICK: return .15f;
            case RIGHT_STICK: return .72f;
            case LT: return .12f;
            case RT: return .89f;
            case 0x0100: return .26f; // LB
            case 0x0200: return .74f; // RB
            case 0x0010: return .55f; // Start
            case 0x0020: return .45f; // Back
            case 0x0040: return .245f; // LS
            case 0x0080: return .62f; // RS
            case 0x0001: case 0x0002: return .36f;
            case 0x0004: return .325f;
            case 0x0008: return .395f;
            case 0x8000: case 0x1000: return .89f; // Y, A
            case 0x4000: return .85f; // X
            case 0x2000: return .93f; // B
            default: return 0f;
        }
    }

    private static float defaultY(int control) {
        switch (control) {
            case LEFT_STICK: case RIGHT_STICK: return .76f;
            case LT: case RT: return .12f;
            case 0x0100: case 0x0200: return .10f;
            case 0x0010: case 0x0020: return .18f;
            case 0x0040: case 0x0080: return .92f;
            case 0x0001: return .66f;
            case 0x0002: return .82f;
            case 0x0004: case 0x0008: return .74f;
            case 0x8000: return .42f;
            case 0x1000: return .62f;
            case 0x4000: case 0x2000: return .52f;
            default: return 0f;
        }
    }

    private int hitIndex(float x, float y) {
        // The two sticks have a wider capture area for sliding while driving.
        float baseR = cachedRadius;
        for (int i = 0; i < CONTROLS.length; i++) {
            float dx = x - cachedX[i], dy = y - cachedY[i];
            float radius = baseR * (i < 2 ? 1.7f : i >= 10 && i <= 13 ? .57f : .8f);
            if (dx * dx + dy * dy < radius * radius) return i;
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
                float x = Math.max(.05f, Math.min(.95f, event.getX(pointer) / getWidth()));
                float y = Math.max(.07f, Math.min(.93f, event.getY(pointer) / getHeight()));
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
                        .putFloat("x_" + control, normX[dragControlIndex])
                        .putFloat("y_" + control, normY[dragControlIndex])
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
                    if (control == LEFT_STICK || control == RIGHT_STICK) {
                        needsRedraw = true;
                    }
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
                        (control == LEFT_STICK || control == RIGHT_STICK)) {
                    needsRedraw = true;
                }
            }
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            int slot = findSlot(event.getPointerId(index));
            if (slot >= 0 &&
                    (fingerControls[slot] == LEFT_STICK || fingerControls[slot] == RIGHT_STICK)) {
                needsRedraw = true;
            }
            releaseSlot(event.getPointerId(index));
        }

        sendState();
        scheduleHide();
        if (needsRedraw) {
            invalidate();
        }
        return true;
    }

    private void dispatchNativeState(int buttons, int lt, int rt,
                                     int lx, int ly, int rx, int ry, boolean force) {
        if (!force && buttons == sentButtons && lt == sentLt && rt == sentRt &&
                lx == sentLx && ly == sentLy && rx == sentRx && ry == sentRy) {
            return;
        }
        sentButtons = buttons;
        sentLt = lt;
        sentRt = rt;
        sentLx = lx;
        sentLy = ly;
        sentRx = rx;
        sentRy = ry;
        nativeSetState(buttons, lt, rt, lx, ly, rx, ry);
    }

    private void sendState() {
        int buttons = 0, lt = 0, rt = 0, lx = 0, ly = 0, rx = 0, ry = 0;
        float radius = cachedRadius;
        for (int i = 0; i < MAX_FINGERS; i++) {
            if (fingerIds[i] == -1) continue;
            int control = fingerControls[i];
            if (control == LT || control == RT) {
                float triggerCy = (control == LT) ? cachedY[2] : cachedY[3];
                int pressure = (int) (255f * Math.max(0f, Math.min(1f,
                        1f - (fingerY[i] - triggerCy) / radius)));
                if (control == LT) lt = pressure;
                else rt = pressure;
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
        dispatchNativeState(buttons, lt, rt, lx, ly, rx, ry, false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!showing || gamepadConnected) return;
        float baseR = cachedRadius;
        float strokeWidth = Math.max(2f, baseR * .055f);
        for (int i = 0; i < CONTROLS.length; i++) {
            int control = CONTROLS[i];
            float radius = baseR * (i < 2 ? 1.28f : i >= 10 && i <= 13 ? .52f : .66f);
            float baseX = cachedX[i], baseY = cachedY[i];
            float thumbX = baseX, thumbY = baseY;
            if (i < 2 && !editing) {
                for (int slot = 0; slot < MAX_FINGERS; slot++) {
                    if (fingerIds[slot] == -1 || fingerControls[slot] != control) continue;
                    baseX = originX[slot];
                    baseY = originY[slot];
                    float dx = fingerX[slot] - baseX, dy = fingerY[slot] - baseY;
                    float distance = (float) Math.hypot(dx, dy);
                    float travel = baseR * .82f;
                    if (distance > travel) {
                        dx *= travel / distance;
                        dy *= travel / distance;
                    }
                    thumbX = baseX + dx;
                    thumbY = baseY + dy;
                    break;
                }
            }
            paint.setColor(0x9A080B10);
            canvas.drawCircle(baseX, baseY, radius, paint);
            paint.setColor(COLORS[i]);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidth);
            canvas.drawCircle(baseX, baseY, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            if (i < 2) {
                paint.setColor(0xB048545C);
                canvas.drawCircle(thumbX, thumbY, baseR * .48f, paint);
            }
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(baseR * (i >= 10 && i <= 13 ? .38f : .48f));
            canvas.drawText(LABELS[i], i < 2 ? thumbX : baseX,
                    (i < 2 ? thumbY : baseY) - (paint.ascent() + paint.descent()) / 2f, paint);
        }
    }
}
