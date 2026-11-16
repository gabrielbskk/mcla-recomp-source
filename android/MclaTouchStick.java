package org.mcla.recomp;

/** Fixed-base joystick. One pointer owns a stick until UP/CANCEL; no frame filtering. */
final class MclaTouchStick {
    static final float DEAD_ZONE = .08f;
    private int owner = -1;
    int x, y;

    boolean press(int pointer) {
        if (owner != -1) return false;
        owner = pointer;
        return true;
    }

    boolean active() { return owner != -1; }

    void move(int pointer, float touchX, float touchY, float centerX, float centerY, float travel) {
        if (pointer != owner || owner == -1) return;
        if (!(travel > 0) || !finite(touchX) || !finite(touchY)) { x = y = 0; return; }
        float dx = (touchX - centerX) / travel;
        float dy = (centerY - touchY) / travel; // Xbox up is positive.
        float length = (float)Math.hypot(dx, dy);
        if (length <= DEAD_ZONE) { x = y = 0; return; }
        float magnitude = Math.min(1, (length - DEAD_ZONE) / (1 - DEAD_ZONE));
        x = Math.round(dx / length * magnitude * 32767);
        y = Math.round(dy / length * magnitude * 32767);
    }

    void release(int pointer) {
        if (pointer == owner) clear();
    }

    void clear() { owner = -1; x = y = 0; }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
