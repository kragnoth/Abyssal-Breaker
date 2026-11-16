package net.kragnoth.abyssal.desktop.render;

import net.kragnoth.abyssal.core.Defs;
import net.kragnoth.abyssal.core.Mth;
import net.kragnoth.abyssal.desktop.gl.Texture;

/**
 * Procedural 16x16 pixel textures (port of the browser build's canvas painters).
 * Alpha 0 in a block texel marks an ore fleck that glints when light hits it.
 */
public final class Textures {
    public static final int S = 16, ATLAS_COLS = Defs.BLOCK_TYPES, ATLAS_ROWS = 3;   // block atlas: type 1..9 across, crack level 0..2 down
    private static final int SEED = 1337;

    public final Texture metal, girder, hazard, wall, glow, boss;
    private final java.util.Map<Integer, Texture> atlases = new java.util.HashMap<>();

    public Textures() {
        metal = tex(this::metalPx);
        girder = tex(this::girderPx);
        hazard = tex((x, y) -> ((x + y) >> 2) % 2 == 1 ? new int[]{24, 20, 16} : new int[]{190, 150, 30});
        wall = tex(this::wallPx);
        boss = new Texture(S, S, toBytes(block(Defs.BASALT, 1, null)), true, true);
        glow = glowTex();
    }

    /** Block atlas for a level (-1 = Classic), built on first use with that level's palette. */
    public Texture blocks(int level) {
        return atlases.computeIfAbsent(level, lv -> {
            int[][] pal = lv >= 0 ? Defs.LEVELS.get(lv).pal() : null;
            byte[] atlas = new byte[S * ATLAS_COLS * S * ATLAS_ROWS * 4];
            int aw = S * ATLAS_COLS;
            for (int t = 1; t <= ATLAS_COLS; t++) for (int cr = 0; cr < 3; cr++) {
                int[] px = block(t, cr, pal != null ? pal[t] : null);
                for (int y = 0; y < S; y++) for (int x = 0; x < S; x++) {
                    int o = ((cr * S + y) * aw + (t - 1) * S + x) * 4, p = (y * S + x) * 4;
                    for (int i = 0; i < 4; i++) atlas[o + i] = (byte) px[p + i];
                }
            }
            return new Texture(aw, S * ATLAS_ROWS, atlas, true, false);
        });
    }

    interface Painter { int[] at(int x, int y); }

    private static int c(double v) { return (int) Mth.clamp(Math.round(v), 0, 255); }
    private static byte[] toBytes(int[] px) { byte[] b = new byte[px.length]; for (int i = 0; i < px.length; i++) b[i] = (byte) px[i]; return b; }

    private Texture tex(Painter p) {
        int[] px = new int[S * S * 4];
        for (int y = 0; y < S; y++) for (int x = 0; x < S; x++) {
            int[] v = p.at(x, y); int o = (y * S + x) * 4;
            px[o] = c(v[0]); px[o + 1] = c(v[1]); px[o + 2] = c(v[2]); px[o + 3] = v.length > 3 ? v[3] : 255;
        }
        return new Texture(S, S, toBytes(px), true, true);
    }

    private static boolean[] crackMask(int lines, int seed) {
        boolean[] m = new boolean[S * S];
        for (int l = 0; l < lines; l++) {
            double x = S / 2.0 + (Mth.hash(seed, l, SEED) - 0.5) * 4, y = S / 2.0 + (Mth.hash(l, seed, SEED) - 0.5) * 4;
            double a = Mth.hash(seed + l, 9, SEED) * Math.PI * 2;
            for (int k = 0; k < 2; k++) {
                double cx = x, cy = y, ca = a + k * Math.PI;
                for (int s = 0; s < 11; s++) {
                    int ix = (int) Math.round(cx), iy = (int) Math.round(cy);
                    if (ix > 0 && iy > 0 && ix < S - 1 && iy < S - 1) m[iy * S + ix] = true;
                    ca += (Mth.hash(s + l * 31, k + seed, SEED) - 0.5) * 1.3; cx += Math.cos(ca); cy += Math.sin(ca);
                }
            }
        }
        return m;
    }

    /** One block texture, RGBA ints. */
    private static int[] block(int type, int crack, int[] baseOverride) {
        int[] out = new int[S * S * 4];
        boolean[] cm = crack > 0 ? crackMask(crack, type * 13 + crack) : null;
        int ts = type * 1000;
        int[] base = baseOverride != null ? baseOverride : Defs.BASE[type], ore = Defs.ORE[type];
        for (int y = 0; y < S; y++) for (int x = 0; x < S; x++) {
            double n = Mth.vnoise(x * 0.45 + ts, y * 0.45, SEED) * 0.6 + Mth.hash(x + ts, y, SEED) * 0.4;
            double k = 1 + (n - 0.5) * Defs.VARIANCE[type] / 40.0;
            double r = base[0] * k, g = base[1] * k, b = base[2] * k; int a = 255;
            if (type == Defs.DIRT && Mth.hash(x * 7 + 3, y * 5, SEED) > 0.9) { r = r * 0.7 + 30; g = g * 0.7 + 26; b = b * 0.7 + 22; }
            if (type == Defs.BASALT && (x + y * 2) % 7 == 0) { r *= 1.35; g *= 1.35; b *= 1.35; }
            if (type == Defs.TAR && Mth.hash(x * 3, y * 9 + 1, SEED) > 0.93) { r += 40; g += 34; b += 30; }          // wet sheen
            if (type == Defs.MIRROR && (x - y + 32) % 5 == 0) { r = 235; g = 245; b = 255; }                         // glass streaks
            if (ore != null && Mth.vnoise(x * 0.9 + ts, y * 0.9 + 5, SEED) > 0.7) { r = ore[0] * 0.75; g = ore[1] * 0.75; b = ore[2] * 0.75; a = 0; }
            if (x == 0 || y == 0 || x == S - 1 || y == S - 1) { r *= 0.42; g *= 0.42; b *= 0.42; }
            else if (y == 1 || x == 1) { r *= 1.18; g *= 1.18; b *= 1.18; }
            if (cm != null && cm[y * S + x]) { r *= 0.18; g *= 0.16; b *= 0.15; a = 255; }
            int o = (y * S + x) * 4;
            out[o] = c(r); out[o + 1] = c(g); out[o + 2] = c(b); out[o + 3] = a;
        }
        return out;
    }

    private int[] metalPx(int x, int y) {
        double n = Mth.vnoise(x * 0.5, y * 0.5 + 40, SEED), rr = Mth.vnoise(x * 0.3 + 9, y * 0.3, SEED);
        double[] c = {78 + n * 24, 70 + n * 18, 64 + n * 14};
        if (rr > 0.62) c = new double[]{120 + n * 30, 62 + n * 14, 30};
        if (x == 0 || y == 0) c = new double[]{c[0] * 0.5, c[1] * 0.5, c[2] * 0.5};
        if ((x == 2 || x == 13) && (y == 2 || y == 13)) c = new double[]{150, 140, 120};
        return new int[]{c(c[0]), c(c[1]), c(c[2])};
    }
    private int[] girderPx(int x, int y) {
        int[] c = {60, 52, 46};
        if (y < 2 || y > 13 || x == y || x == 15 - y) c = new int[]{98, 74, 56};
        if (Mth.vnoise(x * 0.6 + 70, y * 0.6, SEED) > 0.66) c = new int[]{112, 58, 28};
        return c;
    }
    private int[] wallPx(int x, int y) {
        double n = Mth.fbm(x * 0.35 + 300, y * 0.35, SEED);
        double[] c = {46 + n * 30, 42 + n * 26, 44 + n * 26};
        if (Mth.vnoise(x * 0.8 + 12, y * 0.8, SEED) > 0.72) for (int i = 0; i < 3; i++) c[i] *= 0.55;
        return new int[]{c(c[0]), c(c[1]), c(c[2])};
    }
    private static Texture glowTex() {
        int G = 32; byte[] d = new byte[G * G * 4];
        for (int y = 0; y < G; y++) for (int x = 0; x < G; x++) {
            double dx = (x + 0.5) / G - 0.5, dy = (y + 0.5) / G - 0.5, r = Math.sqrt(dx * dx + dy * dy) * 2;
            double a = Math.pow(Mth.clamp(1 - r, 0, 1), 2.2);
            int o = (y * G + x) * 4;
            d[o] = d[o + 1] = d[o + 2] = (byte) 255; d[o + 3] = (byte) (a * 255);
        }
        return new Texture(G, G, d, false, false);
    }
}
