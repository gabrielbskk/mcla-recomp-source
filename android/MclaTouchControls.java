package org.mcla.recomp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import java.util.HashMap;
import java.util.Map;

/** Xbox-style multitouch pad over the SDL surface. This is an Android-only prototype. */
public final class MclaTouchControls extends View {
    private static final long IDLE_MS = 6000;
    private static final int LEFT_STICK = 0x10000, RIGHT_STICK = 0x20000;
    private static final int LT = 0x40000, RT = 0x80000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<Integer, Integer> fingers = new HashMap<>();
    private final Map<Integer, float[]> positions = new HashMap<>();
    private final MclaTouchStick leftStick = new MclaTouchStick();
    private final MclaTouchStick rightStick = new MclaTouchStick();
    private final Map<Integer, float[]> editedPositions = new HashMap<>();
    private final SharedPreferences layout;
    private final boolean editing;
    private int dragPointer = -1;
    private int dragControl;
    private boolean showing = true;
    private boolean gamepadConnected;
    private int leftPressure, rightPressure;
    private int sizePercent;
    private boolean pedals;
    private final float[] savedX = new float[18], savedY = new float[18];

    private void loadLayout() {
        sizePercent = Math.max(60, Math.min(150, layout.getInt("size_percent", 100)));
        pedals = layout.getBoolean("analog_pedals", false);
        for (int i = 0; i < CONTROLS.length; i++) {
            savedX[i] = layout.getFloat(positionKey("x", CONTROLS[i]), defaultX(CONTROLS[i]));
            savedY[i] = layout.getFloat(positionKey("y", CONTROLS[i]), defaultY(CONTROLS[i]));
        }
        float[] radii = new float[CONTROLS.length];
        for (int i = 0; i < radii.length; i++) {
            radii[i] = r() * (isPedal(CONTROLS[i]) ? 1.46f :
                    i < 2 ? 1.28f : i >= 10 && i <= 13 ? .57f : .8f);
        }
        MclaTouchLayout.separate(savedX, savedY, radii, getWidth(), getHeight());
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        loadLayout();
    }
    private final Runnable hideIdle = new Runnable() {
        @Override public void run() {
            if (!gamepadConnected && fingers.isEmpty()) {
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
        layout = context.getSharedPreferences("touch_layout", Context.MODE_PRIVATE);
        loadLayout();
        setFocusable(false);
        // Retain the normal hardware-accelerated display list, without an extra
        // full-screen texture/layer. Never change SDL's SurfaceView Z-order or
        // recreate its Vulkan surface when touch feedback changes.
        if (!editing) scheduleHide();
    }

    public void setSizePercent(int percent) {
        layout.edit().putInt("size_percent", Math.max(60, Math.min(150, percent))).apply();
        loadLayout();
        invalidate();
    }

    public int getSizePercent() { return sizePercent; }

    public boolean hasPedals() { return pedals; }

    public void setPedals(boolean enabled) {
        clearInput();
        editedPositions.clear();
        layout.edit().putBoolean("analog_pedals", enabled).apply();
        loadLayout();
        showing = true;
        invalidate();
    }

    private boolean isPedal(int control) { return hasPedals() && (control == LT || control == RT); }

    private String positionKey(String axis, int control) {
        return (isPedal(control) ? "pedal_" : "") + axis + "_" + control;
    }

    // Absolute vertical slider: bottom = 0%, top = 100%. Release always resets.
    static int pedalValue(float y, float center, float halfTravel) {
        if (halfTravel <= 0) return 0;
        return Math.round(255 * Math.max(0, Math.min(1,
                (center + halfTravel - y) / (2 * halfTravel))));
    }

    private int triggerValue(int control) {
        int value = 0;
        for (Map.Entry<Integer, Integer> finger : fingers.entrySet()) {
            if (finger.getValue() != control) continue;
            float[] pos = positions.get(finger.getKey());
            if (pos == null) continue;
            int pressure = isPedal(control) ? pedalValue(pos[1], cy(control), r() * 1.05f) :
                    (int)(255 * Math.max(0, Math.min(1, 1 - (pos[1] - cy(control)) / r())));
            value = Math.max(value, pressure);
        }
        return value;
    }

    public void resetLayout() {
        layout.edit().clear().apply();
        loadLayout();
        editedPositions.clear();
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
        fingers.clear();
        positions.clear();
        leftStick.clear();
        rightStick.clear();
        leftPressure = rightPressure = 0;
        if (!editing) nativeSetState(0, 0, 0, 0, 0, 0, 0);
        handler.removeCallbacks(hideIdle);
        invalidate();
    }

    public void resumeControls() {
        loadLayout();
        invalidate();
        if (!gamepadConnected) scheduleHide();
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideIdle);
        if (!editing && !gamepadConnected && fingers.isEmpty())
            handler.postDelayed(hideIdle, IDLE_MS);
    }

    private float r() {
        return Math.min(getWidth() / 2.1f, getHeight()) * .076f * getSizePercent() / 100f;
    }
    private float defaultX(int control) {
        if (isPedal(control)) return control == LT ? .78f : .92f;
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
    private float defaultY(int control) {
        if (isPedal(control)) return .83f;
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
    private float cx(int control) {
        float[] edit = editedPositions.get(control);
        return getWidth() * (edit == null
            ? savedX[controlIndex(control)] : edit[0]);
    }
    private float cy(int control) {
        float[] edit = editedPositions.get(control);
        return getHeight() * (edit == null
            ? savedY[controlIndex(control)] : edit[1]);
    }
    private static int controlIndex(int control) {
        for (int i = 0; i < CONTROLS.length; i++) if (CONTROLS[i] == control) return i;
        throw new IllegalArgumentException("Unknown touch control");
    }
    private static final int[] CONTROLS = {
        LEFT_STICK, RIGHT_STICK, LT, RT, 0x0100, 0x0200, 0x0010, 0x0020,
        0x0040, 0x0080,
        0x0001, 0x0002, 0x0004, 0x0008, 0x8000, 0x1000, 0x4000, 0x2000
    };
    private static final String[] LABELS = {
        "L", "R", "LT", "RT", "LB", "RB", "START", "BACK", "LS", "RS",
        "↑", "↓", "←", "→", "Y", "A", "X", "B"
    };
    private static final int[] COLORS = {
        0xFF888888, 0xFF888888, 0xFF777777, 0xFF777777, 0xFF777777, 0xFF777777,
        0xFF888888, 0xFF888888, 0xFF777777, 0xFF777777,
        0xFF777777, 0xFF777777, 0xFF777777, 0xFF777777,
        0xFFF4C137, 0xFF50B743, 0xFF428BD6, 0xFFDE4F4B
    };

    private int hit(float x, float y) {
        // Pedals take precedence over the wider stick capture area.
        if (hasPedals()) {
            for (int control : new int[]{LT, RT}) {
                if (Math.abs(x - cx(control)) <= r() * .72f &&
                        Math.abs(y - cy(control)) <= r() * 1.35f) return control;
            }
        }
        // Explicit buttons win over the sticks' enlarged capture regions.
        for (int step = 0; step < CONTROLS.length; step++) {
            int i = (step + 2) % CONTROLS.length;
            int control = CONTROLS[i];
            if (isPedal(control)) continue;
            float dx = x - cx(control), dy = y - cy(control);
            float radius = r() * (i < 2 ? 1.28f : i >= 10 && i <= 13 ? .57f : .8f);
            if (dx * dx + dy * dy < radius * radius) return control;
        }
        return 0;
    }

    private boolean editTouch(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN) {
            dragPointer = event.getPointerId(index);
            dragControl = hit(event.getX(index), event.getY(index));
        }
        if (dragControl != 0 && dragPointer >= 0) {
            int pointer = event.findPointerIndex(dragPointer);
            if (pointer >= 0) {
                float x = Math.max(.05f, Math.min(.95f, event.getX(pointer) / getWidth()));
                float y = Math.max(.07f, Math.min(.93f, event.getY(pointer) / getHeight()));
                editedPositions.put(dragControl, new float[] {x, y});
                invalidate();
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            float[] pos = editedPositions.get(dragControl);
            if (pos != null && action == MotionEvent.ACTION_UP) {
                layout.edit().putFloat(positionKey("x", dragControl), pos[0])
                    .putFloat(positionKey("y", dragControl), pos[1]).apply();
                loadLayout();
            }
            editedPositions.clear();
            dragPointer = -1;
            dragControl = 0;
            invalidate();
        }
        return true;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (editing) return editTouch(event);
        if (gamepadConnected) return false;
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        boolean reveal = !showing;
        int oldLx = leftStick.x, oldLy = leftStick.y, oldRx = rightStick.x, oldRy = rightStick.y;
        boolean oldLeftActive = leftStick.active(), oldRightActive = rightStick.active();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            showing = true;
            int id = event.getPointerId(index);
            int control = hit(event.getX(index), event.getY(index));
            if (control == LEFT_STICK && !leftStick.press(id)) control = 0;
            if (control == RIGHT_STICK && !rightStick.press(id)) control = 0;
            if (control != 0) {
                fingers.put(id, control);
            }
            handler.removeCallbacks(hideIdle);
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            clearInput();
            scheduleHide();
        } else {
            for (int i = 0; i < event.getPointerCount(); i++) {
                int id = event.getPointerId(i);
                if (fingers.containsKey(id)) {
                    float[] position = positions.get(id);
                    if (position == null) {
                        position = new float[2];
                        positions.put(id, position);
                    }
                    position[0] = event.getX(i);
                    position[1] = event.getY(i);
                    int control = fingers.get(id);
                    if (control == LEFT_STICK || control == RIGHT_STICK) {
                        MclaTouchStick stick = control == LEFT_STICK ? leftStick : rightStick;
                        stick.move(id, position[0], position[1], cx(control), cy(control), r() * .8f);
                    }
                }
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                int id = event.getPointerId(index);
                fingers.remove(id);
                positions.remove(id);
                leftStick.release(id);
                rightStick.release(id);
            }
            int oldLeft = leftPressure, oldRight = rightPressure;
            sendState();
            // Pedal state is functional feedback, not optional stick animation.
            // Use exactly the same pressure sent to JNI; release redraws zero.
            if (hasPedals() && (oldLeft != leftPressure || oldRight != rightPressure)) {
                invalidatePedals();
            }
            scheduleHide();
        }
        if (reveal) postInvalidateOnAnimation();
        else if (oldLx != leftStick.x || oldLy != leftStick.y || oldRx != rightStick.x ||
                oldRy != rightStick.y || oldLeftActive != leftStick.active() ||
                oldRightActive != rightStick.active()) invalidateSticks();
        return true;
    }

    private void sendState() {
        int buttons = 0, lt = triggerValue(LT), rt = triggerValue(RT);
        leftPressure = lt;
        rightPressure = rt;
        for (Map.Entry<Integer, Integer> finger : fingers.entrySet()) {
            int control = finger.getValue();
            if (control == LT || control == RT) {
                // Triggers are aggregated above, including multiple fingers.
            }
            else if (control != LEFT_STICK && control != RIGHT_STICK) buttons |= control;
        }
        nativeSetState(buttons, lt, rt, leftStick.x, leftStick.y, rightStick.x, rightStick.y);
    }

    private void invalidateSticks() {
        float padding = r() * 1.4f;
        postInvalidateOnAnimation((int)(Math.min(cx(LEFT_STICK), cx(RIGHT_STICK)) - padding),
                (int)(Math.min(cy(LEFT_STICK), cy(RIGHT_STICK)) - padding),
                (int)(Math.max(cx(LEFT_STICK), cx(RIGHT_STICK)) + padding + 1),
                (int)(Math.max(cy(LEFT_STICK), cy(RIGHT_STICK)) + padding + 1));
    }

    private void invalidatePedals() {
        float padding = r() * 1.4f;
        postInvalidateOnAnimation((int)(Math.min(cx(LT), cx(RT)) - padding),
                (int)(Math.min(cy(LT), cy(RT)) - padding),
                (int)(Math.max(cx(LT), cx(RT)) + padding + 1),
                (int)(Math.max(cy(LT), cy(RT)) + padding + 1));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!showing || gamepadConnected) return;
        for (int i = 0; i < CONTROLS.length; i++) {
            int control = CONTROLS[i];
            if (isPedal(control)) {
                drawPedal(canvas, control);
                continue;
            }
            float radius = r() * (i < 2 ? 1.28f : i >= 10 && i <= 13 ? .52f : .66f);
            float baseX = cx(control), baseY = cy(control);
            float thumbX = baseX, thumbY = baseY;
            MclaTouchStick stick = i == 0 ? leftStick : rightStick;
            if (i < 2 && !editing) {
                // The rendered knob and JNI use the exact same normalized axis.
                thumbX += stick.x / 32767f * r() * .8f;
                thumbY -= stick.y / 32767f * r() * .8f;
            }
            paint.setColor(0x9A080B10);
            canvas.drawCircle(baseX, baseY, radius, paint);
            paint.setColor(i < 2 && stick.active() && !editing ? 0xFF70DFCA : COLORS[i]);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, r() * .055f));
            canvas.drawCircle(baseX, baseY, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            if (i < 2) {
                paint.setColor(stick.active() && !editing ? 0xDB387D76 : 0xB048545C);
                canvas.drawCircle(thumbX, thumbY, r() * .48f, paint);
            }
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(r() * (i >= 10 && i <= 13 ? .38f : .48f));
            canvas.drawText(LABELS[i], i < 2 ? thumbX : baseX,
                (i < 2 ? thumbY : baseY) - (paint.ascent() + paint.descent()) / 2, paint);
        }
    }

    private void drawPedal(Canvas canvas, int control) {
        float x = cx(control), y = cy(control), w = r() * .65f, h = r() * 1.3f;
        int value = editing ? 0 : control == LT ? leftPressure : rightPressure;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xC0080B10);
        canvas.drawRoundRect(x - w, y - h, x + w, y + h, r() * .2f, r() * .2f, paint);
        paint.setColor(control == RT ? 0xB050B743 : 0xB0DE4F4B);
        if (value > 0) canvas.drawRoundRect(x - w, y + h - 2 * h * value / 255f,
                x + w, y + h, r() * .2f, r() * .2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, r() * .05f));
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(x - w, y - h, x + w, y + h, r() * .2f, r() * .2f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(r() * .35f);
        canvas.drawText(control == RT ? "GAS / RT" : "BRAKE / LT", x, y - r() * .25f, paint);
        canvas.drawText(editing ? "SLIDE ↑" : Math.round(value * 100f / 255) + "%",
                x, y + r() * .35f, paint);
    }
}
