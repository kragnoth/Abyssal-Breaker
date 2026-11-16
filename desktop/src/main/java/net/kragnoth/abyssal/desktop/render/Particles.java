package net.kragnoth.abyssal.desktop.render;

import net.kragnoth.abyssal.core.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Sparks, tumbling debris and short point-light flashes, fed by the simulation's Fx events. */
public final class Particles {
    public static final int SP = 900, DB = 500;
    public final float[] spPos = new float[SP * 3], spVel = new float[SP * 3], spBase = new float[SP * 3], spLife = new float[SP];
    private int spHead;
    public final float[][] deb = new float[DB][];   // x y z vx vy vz rot vrot life scale r g b
    private int dbHead;
    public final List<float[]> flashes = new ArrayList<>();   // x y r g b rad life max
    private final Random rng = new Random();

    private float rnd(float a, float b) { return a + rng.nextFloat() * (b - a); }

    public void spark(float x, float y, float z, float vx, float vy, float r, float g, float b, float life) {
        int i = spHead; spHead = (spHead + 1) % SP;
        spPos[i * 3] = x; spPos[i * 3 + 1] = y; spPos[i * 3 + 2] = z;
        spVel[i * 3] = vx; spVel[i * 3 + 1] = vy; spVel[i * 3 + 2] = rnd(-1, 1);
        spBase[i * 3] = r; spBase[i * 3 + 1] = g; spBase[i * 3 + 2] = b; spLife[i] = life;
    }
    public void burst(float x, float y, int n, float r, float g, float b, float speed, float life) {
        for (int i = 0; i < n; i++) {
            float a = rnd(0, 6.283f), s = rnd(0.3f, 1) * speed;
            spark(x, y, rnd(0.4f, 0.9f), (float) Math.cos(a) * s, (float) Math.sin(a) * s, r, g, b, rnd(0.5f, 1) * life);
        }
    }
    public void debris(float x, float y, int hex, int n) {
        for (int k = 0; k < n; k++) {
            int i = dbHead; dbHead = (dbHead + 1) % DB;
            float m = rnd(0.6f, 1.2f);
            deb[i] = new float[]{x + rnd(-0.4f, 0.4f), y + rnd(-0.4f, 0.4f), rnd(0, 0.6f), rnd(-3, 3), rnd(-1, 4), rnd(0, 3), rnd(0, 6), rnd(-12, 12),
                rnd(0.9f, 1.6f), rnd(0.6f, 1.4f), Mth.r(hex) * m, Mth.g(hex) * m, Mth.b(hex) * m};
        }
    }
    public void flash(float x, float y, float r, float g, float b, float rad, float life) { flashes.add(new float[]{x, y, r, g, b, rad, life, life}); }

    public void update(float dt) {
        for (int i = 0; i < SP; i++) {
            if (spLife[i] <= 0) continue;
            spLife[i] -= dt; spVel[i * 3 + 1] -= 6 * dt;
            for (int k = 0; k < 3; k++) spPos[i * 3 + k] += spVel[i * 3 + k] * dt;
        }
        for (int i = 0; i < DB; i++) {
            float[] d = deb[i];
            if (d == null || d[8] <= 0) continue;
            d[8] -= dt; d[4] -= 16 * dt; d[0] += d[3] * dt; d[1] += d[4] * dt; d[2] += d[5] * dt; d[6] += d[7] * dt;
        }
        flashes.removeIf(f -> (f[6] -= dt) <= 0);
    }

    public void clear() {
        java.util.Arrays.fill(spLife, 0);
        java.util.Arrays.fill(deb, null);
        flashes.clear();
    }
}
