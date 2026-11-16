package net.kragnoth.abyssal.desktop.audio;

import java.util.Random;

/**
 * Offline sound synthesis into float buffers: oscillators, RBJ-biquad filtered noise, inharmonic struck metal,
 * steam hiss. Port of the browser build's WebAudio graph; sounds are rendered once at start-up.
 */
final class Synth {
    static final int RATE = 44100;
    private final Random rng = new Random(9);
    final float[] buf;

    Synth(float seconds) { buf = new float[(int) (seconds * RATE)]; }

    private float rnd(float a, float b) { return a + rng.nextFloat() * (b - a); }

    /** Exponential attack (4 ms) to {@code v}, exponential decay to silence by {@code d}. */
    private static float env(float t, float d, float v) {
        if (t < 0 || t > d) return 0;
        if (t < 0.004f) return (float) (0.0001 * Math.pow(v / 0.0001, t / 0.004));
        return (float) (v * Math.pow(0.0006 / v, (t - 0.004) / Math.max(1e-4, d - 0.004)));
    }

    void osc(float f, float d, String type, float v, float f2, float at) {
        double ph = 0;
        int s0 = (int) (at * RATE), n = (int) ((d + 0.05f) * RATE);
        for (int i = 0; i < n && s0 + i < buf.length; i++) {
            float t = i / (float) RATE;
            double fr = f2 > 0 ? f * Math.pow(f2 / f, Math.min(1, t / d)) : f;
            ph += fr / RATE; ph -= Math.floor(ph);
            double w = switch (type) {
                case "sine" -> Math.sin(ph * Math.PI * 2);
                case "triangle" -> 1 - 4 * Math.abs(ph - 0.5);
                case "square" -> ph < 0.5 ? 1 : -1;
                default -> 2 * ph - 1;                                   // sawtooth
            };
            buf[s0 + i] += (float) (w * env(t, d, v));
        }
    }

    /** Filtered white noise. type: lowpass, highpass, bandpass. Optional exponential cutoff sweep to f2. */
    void nz(float d, float v, float freq, String type, float q, float at, float f2) {
        int s0 = (int) (at * RATE), n = (int) ((d + 0.05f) * RATE);
        double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
        for (int i = 0; i < n && s0 + i < buf.length; i++) {
            float t = i / (float) RATE;
            double fc = f2 > 0 ? freq * Math.pow(f2 / freq, Math.min(1, t / d)) : freq;
            fc = Math.min(fc, RATE * 0.45);
            double w0 = 2 * Math.PI * fc / RATE, cs = Math.cos(w0), al = Math.sin(w0) / (2 * Math.max(0.1, q));
            double b0, b1, b2, a0 = 1 + al, a1 = -2 * cs, a2 = 1 - al;
            switch (type) {
                case "highpass" -> { b0 = (1 + cs) / 2; b1 = -(1 + cs); b2 = (1 + cs) / 2; }
                case "bandpass" -> { b0 = al; b1 = 0; b2 = -al; }
                default -> { b0 = (1 - cs) / 2; b1 = 1 - cs; b2 = (1 - cs) / 2; }
            }
            double x = rng.nextDouble() * 2 - 1;
            double y = (b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0;
            x2 = x1; x1 = x; y2 = y1; y1 = y;
            buf[s0 + i] += (float) (y * env(t, d, v));
        }
    }

    /** Pipes, plates, anvils: four inharmonic partials plus a band-passed strike. */
    void metal(float f, float d, float v, float bright, float at) {
        float[][] P = {{1, 1}, {2.76f, 0.6f}, {5.4f, 0.4f * bright + 0.1f}, {8.93f, 0.3f * bright}};
        for (float[] p : P) if (p[1] > 0.01f)
            osc(f * p[0] * rnd(0.99f, 1.01f), d / (float) Math.sqrt(p[0]), p[0] < 2 ? "triangle" : "square", v * p[1] * (p[0] < 2 ? 1 : 0.35f), 0, at);
        nz(0.03f, v * 0.8f, f * 6, "bandpass", 2, at, 0);
    }

    void hiss(float d, float v, float at) { nz(d, v, 5200, "highpass", 0.7f, at, 2600); }

    /** Soft-clip drive (tanh) then peak-normalise, like the browser build's drive + compressor bus. */
    short[] finish(float gain) {
        float peak = 1e-6f;
        for (int i = 0; i < buf.length; i++) { buf[i] = (float) Math.tanh(buf[i] * 2.2); peak = Math.max(peak, Math.abs(buf[i])); }
        float k = Math.min(gain / peak, 3f) * 0.9f;
        short[] out = new short[buf.length];
        for (int i = 0; i < buf.length; i++) out[i] = (short) Math.max(-32767, Math.min(32767, buf[i] * k * 32767));
        return out;
    }
}
