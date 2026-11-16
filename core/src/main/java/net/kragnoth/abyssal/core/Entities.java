package net.kragnoth.abyssal.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Plain state holders. The simulation mutates them; renderers only read them. */
public final class Entities {
    private Entities() {}

    /** One row of the shaft. {@code version} bumps whenever a cell changes so renderers can rebuild lazily. */
    public static final class Row {
        public final int r;
        public final int[] t = new int[Defs.COLS];
        public final int[] hp = new int[Defs.COLS];
        public final int[] maxHp = new int[Defs.COLS];
        public int solid0;
        public int version;
        public Row(int r) { this.r = r; }
    }

    public static final class Ball {
        public float x, y, vx, vy, boost = 1, holdX, holdT;
        public boolean docked, held, lost;
        public int pierceLeft, lanceLeft = 10, slam;
        public float lastSplit = -9;
        public final Map<String, Integer> up = new HashMap<>();
        public float[] col = {1, 0.55f, 0.2f};
        public int up(String k) { return up.getOrDefault(k, 0); }
        public boolean evo(String k) { return up.getOrDefault("evo_" + k, 0) > 0; }
        public int total() { int n = 0; for (String k : Defs.UP_KEYS) n += up(k); return n; }
        public int evoCount() { int n = 0; for (Defs.Evolution e : Defs.EVOS) if (evo(e.key())) n++; return n; }
    }

    public static final class Loot {
        public float x, y, vx, vy, spin;
        public final String kind;          // "gold", "aether" or "pu"
        public final String pu;            // power-up key when kind == "pu"
        public Loot(String kind, String pu) { this.kind = kind; this.pu = pu; }
    }

    public static final class Drill {
        public int c, row, left;
        public float y, v = 2;
    }

    public static final class Panel {
        public final String kind;          // "laser" or "skimmer"
        public final int side;             // -1 left wall, +1 right wall
        public float y, ty, wait, cd, beamT, beamY, beamX0, beamX1;
        public int pull;
        public Panel(String kind, int side) { this.kind = kind; this.side = side; }
    }

    public static final class Hazard {
        public float x, warn, age, y, vy;
        public boolean falling, passed;
    }

    public static final class Segment {
        public float x, y, hitT;
        public int hp, max;
        public boolean alive = true;
    }

    public static final class Boss {
        public Defs.BossDef def;
        public float sc, t, x, y, dir, tele, lungeT, lungeTime, hitT, age, lx, rubbleT = 2;
        public int hp, max;
        public String phase = "rise";
        public final List<Segment> segs = new ArrayList<>();
        public final ArrayDeque<float[]> trail = new ArrayDeque<>();
        public boolean armored() { for (Segment s : segs) if (s.alive) return true; return false; }
        public int aliveSegs() { int n = 0; for (Segment s : segs) if (s.alive) n++; return n; }
    }

    /** Pacing telemetry; the logic sims read these. */
    public static final class Stats {
        public float t, idle, readyWait, lastProg, firstPod = -1, firstDrop = -1;
        public int blocks, hullHits, chips, stalHits, drops, maxCores = 1, maxStack, laserShots, skimmed, evos, slams, bossKills, shopSeen;
        public double ore;
        public final float[] coreT = new float[4];
        public final Map<String, Integer> pods = new HashMap<>();
        public final List<Long> unspent = new ArrayList<>();
        public String death = "";
    }
}
