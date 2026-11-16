package net.kragnoth.abyssal.desktop.audio;

import net.kragnoth.abyssal.core.Fx.Sfx;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.*;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;

/** OpenAL playback of the synthesised sound set, a looping machinery drone and random distant clanks. Silent if no device. */
public final class Audio {
    private long device, context;
    private boolean ok;
    private final Map<Sfx, int[]> buffers = new EnumMap<>(Sfx.class);
    private final int[] hit = new int[25], hitHard = new int[25];
    private final int[] ambient = new int[4];
    private int droneBuf, droneSrc;
    private final int[] pool = new int[32];
    private final Random rng = new Random();
    private int budget;
    private float ambientT = 3.7f;
    public boolean muted;
    public float volume = 0.9f;

    public Audio() {
        try {
            device = alcOpenDevice((ByteBuffer) null);
            if (device == 0) { System.err.println("[abyssal] no audio device; running silent"); return; }
            ALCCapabilities caps = ALC.createCapabilities(device);
            context = alcCreateContext(device, (IntBuffer) null);
            alcMakeContextCurrent(context);
            AL.createCapabilities(caps);
            for (int i = 0; i < pool.length; i++) pool[i] = alGenSources();
            build();
            droneSrc = alGenSources();
            alSourcei(droneSrc, AL_BUFFER, droneBuf); alSourcei(droneSrc, AL_LOOPING, AL_TRUE); alSourcef(droneSrc, AL_GAIN, 0.35f);
            alSourcePlay(droneSrc);
            ok = true;
        } catch (Throwable t) {
            System.err.println("[abyssal] audio disabled: " + t);
            ok = false;
        }
    }

    private int upload(short[] pcm) {
        int b = alGenBuffers();
        ShortBuffer sb = MemoryUtil.memAllocShort(pcm.length);
        sb.put(pcm).flip();
        alBufferData(b, AL_FORMAT_MONO16, sb, Synth.RATE);
        MemoryUtil.memFree(sb);
        return b;
    }

    private interface Recipe { void make(Synth s); }
    private int make(float secs, float gain, Recipe r) { Synth s = new Synth(secs); r.make(s); return upload(s.finish(gain)); }
    private void put(Sfx k, float secs, float gain, Recipe r) { buffers.put(k, new int[]{make(secs, gain, r), make(secs, gain, r)}); }

    private void build() {
        for (int i = 0; i < 25; i++) {
            float f = 150 * (float) Math.pow(2, i / 12.0);
            hit[i] = make(0.4f, 0.55f, s -> { s.metal(f, 0.28f, 0.15f, 0.5f, 0); s.nz(0.12f, 0.3f, 800, "lowpass", 0.8f, 0, 0); });
            hitHard[i] = make(0.6f, 0.7f, s -> { s.metal(f, 0.5f, 0.22f, 0.9f, 0); s.nz(0.12f, 0.4f, 1400, "lowpass", 0.8f, 0, 0); });
        }
        put(Sfx.CRUNCH, 0.4f, 0.6f, s -> { s.nz(0.3f, 0.45f, 500, "lowpass", 0.8f, 0, 0); s.osc(70, 0.18f, "sine", 0.35f, 40, 0); });
        put(Sfx.PADDLE, 0.6f, 0.8f, s -> { s.metal(92, 0.45f, 0.32f, 0.35f, 0); s.osc(58, 0.2f, "sine", 0.5f, 38, 0); s.hiss(0.18f, 0.08f, 0); });
        put(Sfx.WALL, 0.3f, 0.35f, s -> s.metal(128, 0.2f, 0.12f, 0.2f, 0));
        put(Sfx.ORE, 0.6f, 0.45f, s -> s.metal(820, 0.5f, 0.11f, 1, 0));
        put(Sfx.ORE_HI, 0.6f, 0.5f, s -> s.metal(1180, 0.5f, 0.11f, 1, 0));
        put(Sfx.POD, 0.8f, 0.6f, s -> { for (int i = 0; i < 4; i++) s.nz(0.03f, 0.3f, 3000, "bandpass", 4, i * 0.045f, 0); s.metal(330, 0.5f, 0.15f, 0.8f, 0.18f); });
        put(Sfx.BOOM, 1.8f, 1.0f, s -> { s.nz(1.4f, 0.8f, 420, "lowpass", 0.7f, 0, 90); s.osc(46, 0.9f, "sawtooth", 0.45f, 24, 0); for (int i = 0; i < 4; i++) s.metal(160 + i * 70, 0.4f, 0.08f, 0.6f, 0.1f + i * 0.14f); });
        put(Sfx.HULL, 1.2f, 1.0f, s -> { s.metal(70, 0.9f, 0.4f, 1, 0); s.nz(0.7f, 0.6f, 300, "lowpass", 0.8f, 0, 0); s.osc(620, 0.14f, "square", 0.12f, 0, 0); s.osc(470, 0.14f, "square", 0.12f, 0, 0.16f); });
        put(Sfx.DROP, 2.6f, 0.9f, s -> { s.hiss(1.4f, 0.3f, 0); s.osc(52, 1.6f, "sawtooth", 0.35f, 28, 0); for (int i = 0; i < 14; i++) s.nz(0.025f, 0.22f, 2200, "bandpass", 5, 0.15f + i * 0.07f, 0); s.metal(84, 1.2f, 0.25f, 0.4f, 1.3f); });
        put(Sfx.CLAMP, 0.6f, 0.6f, s -> { s.metal(300, 0.25f, 0.18f, 0.6f, 0); s.metal(450, 0.3f, 0.18f, 0.6f, 0.11f); s.hiss(0.35f, 0.12f, 0.05f); });
        put(Sfx.LAUNCH, 0.4f, 0.55f, s -> { s.osc(170, 0.22f, "sawtooth", 0.18f, 540, 0); s.metal(640, 0.3f, 0.1f, 0.8f, 0); });
        put(Sfx.ARC, 0.15f, 0.3f, s -> s.osc(2000, 0.05f, "sawtooth", 0.06f, 900, 0));
        put(Sfx.LASER, 0.2f, 0.4f, s -> s.osc(1400, 0.1f, "sawtooth", 0.08f, 300, 0));
        put(Sfx.SLAM, 0.4f, 0.8f, s -> { s.nz(0.15f, 0.48f, 1200, "lowpass", 0.8f, 0, 0); s.osc(140, 0.18f, "square", 0.32f, 70, 0); s.metal(110, 0.3f, 0.2f, 0.5f, 0); });
        put(Sfx.CLANG, 0.3f, 0.4f, s -> s.metal(1200, 0.2f, 0.08f, 1, 0));
        put(Sfx.CATCH, 0.3f, 0.45f, s -> s.osc(300, 0.08f, "triangle", 0.24f, 600, 0));
        put(Sfx.WARN, 0.3f, 0.35f, s -> { s.osc(880, 0.06f, "square", 0.08f, 0, 0); s.osc(660, 0.06f, "square", 0.08f, 0, 0.08f); });
        put(Sfx.GROWL, 1.2f, 0.7f, s -> { s.osc(110, 0.9f, "sawtooth", 0.2f, 220, 0); s.nz(1.0f, 0.3f, 200, "lowpass", 2, 0, 90); });
        put(Sfx.DEVOUR, 0.6f, 0.6f, s -> { s.osc(90, 0.4f, "sawtooth", 0.25f, 50, 0); s.nz(0.4f, 0.4f, 400, "lowpass", 1, 0, 0); });
        put(Sfx.BUY, 0.3f, 0.45f, s -> s.osc(660, 0.08f, "square", 0.16f, 990, 0));
        put(Sfx.DENY, 0.2f, 0.35f, s -> s.osc(90, 0.12f, "square", 0.16f, 0, 0));
        put(Sfx.TICK, 0.3f, 0.35f, s -> s.osc(400, 0.1f, "square", 0.13f, 800, 0));
        put(Sfx.EVOLVE, 0.9f, 0.7f, s -> { s.osc(330, 0.3f, "square", 0.19f, 990, 0); s.osc(495, 0.4f, "square", 0.16f, 1485, 0); s.metal(660, 0.6f, 0.12f, 1, 0.1f); });
        for (int i = 0; i < 4; i++) { final int k = i; ambient[i] = make(1.6f, 0.25f, s -> { if (k < 2) s.metal(70 + k * 50, 1.4f, 0.06f, 0, 0); else s.hiss(0.6f + k * 0.4f, 0.04f, 0); }); }
        // machinery bed: integer cycles over 4 s so the loop is seamless; LFO chug at 2.25 Hz
        Synth d = new Synth(8);
        double[] fs = {41.5, 41.75, 82.25};
        float[] raw = new float[d.buf.length];
        for (int i = 0; i < raw.length; i++) {
            double t = i / (double) Synth.RATE, v = 0;
            v += 2 * ((t * fs[0]) % 1) - 1; v += 2 * ((t * fs[1]) % 1) - 1; v += ((t * fs[2]) % 1) < 0.5 ? 0.6 : -0.6;
            raw[i] = (float) (v * (0.07 + 0.035 * Math.sin(t * Math.PI * 2 * 2.25)));
        }
        double y1 = 0, y2 = 0, x1 = 0, x2 = 0, w0 = 2 * Math.PI * 190 / Synth.RATE, cs = Math.cos(w0), al = Math.sin(w0) / 6;
        double b0 = (1 - cs) / 2, b1 = 1 - cs, b2 = (1 - cs) / 2, a0 = 1 + al, a1 = -2 * cs, a2 = 1 - al;
        int half = raw.length / 2;
        short[] loop = new short[half];
        for (int i = 0; i < raw.length; i++) {
            double y = (b0 * raw[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0; x2 = x1; x1 = raw[i]; y2 = y1; y1 = y;
            if (i >= half) loop[i - half] = (short) Math.max(-32767, Math.min(32767, Math.tanh(y * 3) * 0.5 * 32767));
        }
        droneBuf = upload(loop);
    }

    /** Call once per frame before any {@link #play}. */
    public void frame(float dt, boolean playing) {
        budget = 8;
        if (!ok) return;
        alSourcef(droneSrc, AL_GAIN, muted ? 0 : 0.35f * volume);
        if (playing && !muted && (ambientT -= dt) <= 0) {
            ambientT = 3.7f;
            playBuf(ambient[rng.nextInt(4)], 0.8f, 0.85f + rng.nextFloat() * 0.3f);
        }
    }

    public void play(Sfx s, int pitch) {
        if (!ok || muted || budget-- <= 0) return;
        int b = switch (s) {
            case HIT -> hit[Math.min(24, Math.max(0, pitch))];
            case HIT_HARD -> hitHard[Math.min(24, Math.max(0, pitch))];
            default -> { int[] v = buffers.get(s); yield v[rng.nextInt(v.length)]; }
        };
        playBuf(b, 1, 0.97f + rng.nextFloat() * 0.06f);
    }

    private void playBuf(int buffer, float gain, float pitch) {
        for (int src : pool) {
            if (alGetSourcei(src, AL_SOURCE_STATE) == AL_PLAYING) continue;
            alSourcei(src, AL_BUFFER, buffer);
            alSourcef(src, AL_GAIN, gain * volume);
            alSourcef(src, AL_PITCH, pitch);
            alSourcePlay(src);
            return;
        }
    }

    public void close() {
        if (device == 0) return;
        alcMakeContextCurrent(0);
        if (context != 0) alcDestroyContext(context);
        alcCloseDevice(device);
    }
}
