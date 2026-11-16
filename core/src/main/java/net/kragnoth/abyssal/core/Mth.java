package net.kragnoth.abyssal.core;

/** Small math helpers plus the seeded value noise used for world generation (bit-exact port of the JS hash). */
public final class Mth {
    private Mth() {}

    public static float clamp(float v, float a, float b) { return v < a ? a : v > b ? b : v; }
    public static double clamp(double v, double a, double b) { return v < a ? a : v > b ? b : v; }
    public static int clamp(int v, int a, int b) { return v < a ? a : v > b ? b : v; }
    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    public static float[] mix(float[] a, float[] b, float t) {
        float[] o = new float[a.length];
        for (int i = 0; i < a.length; i++) o[i] = lerp(a[i], b[i], t);
        return o;
    }

    /** Integer hash -> [0,1]. Java int multiply wraps exactly like JS Math.imul. */
    public static double hash(int x, int y, int seed) {
        int h = (x * 374761393) ^ (y * 668265263) ^ (seed * 1442695041);
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xffffffffL) / 4294967295.0;
    }

    public static double vnoise(double x, double y, int seed) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        double xf = x - xi, yf = y - yi;
        double u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
        double a = hash(xi, yi, seed), b = hash(xi + 1, yi, seed), c = hash(xi, yi + 1, seed), d = hash(xi + 1, yi + 1, seed);
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }

    public static double fbm(double x, double y, int seed) {
        return vnoise(x, y, seed) * 0.6 + vnoise(x * 2.1 + 7.3, y * 2.1 + 3.1, seed) * 0.3 + vnoise(x * 4.3 + 1.7, y * 4.3 + 9.2, seed) * 0.1;
    }

    public static float r(int hex) { return ((hex >> 16) & 255) / 255f; }
    public static float g(int hex) { return ((hex >> 8) & 255) / 255f; }
    public static float b(int hex) { return (hex & 255) / 255f; }
}
