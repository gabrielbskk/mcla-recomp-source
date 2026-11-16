package org.mcla.recomp;

/** Pure geometry: resolve visual/hit-button collisions without rewriting preferences. */
final class MclaTouchLayout {
    private MclaTouchLayout() {}

    static void separate(float[] x, float[] y, float[] radii, int width, int height) {
        if (width <= 0 || height <= 0) return;
        for (int i = 0; i < x.length; i++) {
            if (Float.isNaN(x[i]) || Float.isInfinite(x[i])) x[i] = .5f;
            if (Float.isNaN(y[i]) || Float.isInfinite(y[i])) y[i] = .5f;
        }
        for (int pass = 0; pass < 96; pass++) {
            boolean moved = false;
            for (int i = 0; i < x.length; i++) {
                float margin = radii[i] + 2;
                x[i] = Math.max(margin / width, Math.min(1 - margin / width, x[i]));
                y[i] = Math.max(margin / height, Math.min(1 - margin / height, y[i]));
                for (int j = 0; j < i; j++) {
                    float dx = (x[i] - x[j]) * width, dy = (y[i] - y[j]) * height;
                    float distance = (float) Math.hypot(dx, dy);
                    float minimum = radii[i] + radii[j] + 4;
                    if (distance >= minimum - .1f) continue;
                    if (distance < .01f) { dx = 1; dy = 0; distance = 1; }
                    float push = (minimum - distance) * .51f;
                    float px = dx / distance * push / width, py = dy / distance * push / height;
                    x[i] += px; y[i] += py;
                    x[j] -= px; y[j] -= py;
                    moved = true;
                }
            }
            if (!moved) break;
        }
    }
}
