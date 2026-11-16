package net.kragnoth.abyssal.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Every tuning constant and data table in the game. Pure data: no rendering, no state. */
public final class Defs {
    private Defs() {}

    // ---------------------------------------------------------------- playfield
    public static final int COLS = 15;
    public static final float HALF = COLS / 2f;           // playfield x in [-7.5, 7.5]
    public static final int ZONE_OFF = 6, ZONE = 6, DROP = 6;
    public static final float CLEAR_REQ = 0.65f;
    public static final float PRESSURE_TIME = 25f;
    public static final int JUNCTION_EVERY = 30;           // rows (2 m per row => 60 m)
    public static final int M_PER_ROW = 2;
    public static final float BR = 0.24f;                  // core radius
    public static final float PW = 3.0f;                   // bumper width
    public static final int MAX_BALLS = 8;
    public static final int MAX_UP = 8;
    public static final int REPAIR_COST = 150;
    public static final int ROCK_SCORE = 30000;            // boulders start above this run score

    // camera mapping shared by the renderer and pointer input (keep them in sync)
    public static final float CAM_TARGET_BELOW_RIG = 5.6f;
    public static final float CAM_HALF_HEIGHT = 8.4f;
    public static final float POINTER_SPAN_X = 23.4f;
    public static final int VIEW_W = 640, VIEW_H = 448;    // PS2 NTSC-ish internal resolution

    // ---------------------------------------------------------------- blocks
    public static final int EMPTY = 0, DIRT = 1, STONE = 2, BASALT = 3, GOLD = 4, AETHER = 5, GAS = 6, SCULK = 7, TAR = 8, MIRROR = 9;
    public static final int BLOCK_TYPES = 9;
    public static final int[] HP = {0, 1, 2, 4, 2, 3, 1, 1, 2, 5};
    public static final boolean[] PIERCE = {false, true, false, false, false, false, false, false, false, false};
    public static final int[] SCORE = {0, 5, 10, 25, 15, 30, 20, 15, 15, 40};
    public static final int[][] BASE = {null, {96, 66, 42}, {92, 90, 88}, {44, 42, 54}, {84, 78, 70}, {52, 54, 70}, {58, 72, 46},
                                        {16, 46, 56}, {24, 19, 17}, {150, 160, 176}};
    public static final int[] VARIANCE = {0, 26, 20, 12, 18, 14, 18, 16, 8, 10};
    public static final int[] DEBRIS = {0, 0x6e4e32, 0x6a6866, 0x34323e, 0x8a7a50, 0x3c6a7a, 0x4c6a30, 0x1c4a52, 0x161210, 0x9aa4b8};
    public static final int[][] ORE = {null, null, null, null, {255, 196, 60}, {90, 230, 255}, {150, 255, 90}, {40, 230, 210}, null, {220, 235, 255}};

    // ---------------------------------------------------------------- power-up pods
    public record PowerUp(String key, char letter, int color, String name, int weight, boolean up) {}
    public static final List<PowerUp> PODS = List.of(
        new PowerUp("split", 'X', 0xff7a2a, "FISSION", 20, true),
        new PowerUp("str", 'S', 0xff3a3a, "STRENGTH", 17, true),
        new PowerUp("pierce", 'P', 0xdfe9ff, "DRILL-POINT", 14, true),
        new PowerUp("blast", 'B', 0xffae2a, "BLAST CORE", 12, true),
        new PowerUp("arc", 'Z', 0x5ae6ff, "ARC CORE", 12, true),
        new PowerUp("gold", '$', 0xffd84a, "PROSPECTOR", 10, true),
        new PowerUp("drill", 'M', 0xc8c8c8, "MEGA DRILL", 9, false),
        new PowerUp("wide", 'W', 0x9cff6a, "WIDE RIG", 8, false),
        new PowerUp("magnet", 'U', 0xc86aff, "MAGNET SURGE", 6, false),
        new PowerUp("catch", 'C', 0x7affd4, "CATCH", 6, false),
        new PowerUp("hull", '+', 0xff8ab0, "HULL PATCH", 4, false));
    public static final List<String> UP_KEYS = List.of("split", "str", "pierce", "blast", "arc", "gold");
    public static final Map<String, float[]> UP_RGB = Map.of(
        "split", new float[]{1, 0.45f, 0.1f}, "str", new float[]{1, 0.25f, 0.2f}, "pierce", new float[]{0.85f, 0.92f, 1},
        "blast", new float[]{1, 0.6f, 0.15f}, "arc", new float[]{0.35f, 0.9f, 1}, "gold", new float[]{1, 0.85f, 0.3f});
    public static PowerUp pod(String key) { for (PowerUp p : PODS) if (p.key.equals(key)) return p; throw new IllegalArgumentException(key); }

    // ---------------------------------------------------------------- evolutions
    public record Evolution(String key, String name, String a, String b, float[] col, String desc) {}
    public static final List<Evolution> EVOS = List.of(
        new Evolution("storm", "STORM CORE", "blast", "arc", new float[]{0.55f, 0.95f, 1.0f}, "every arc strike detonates"),
        new Evolution("lance", "DRILL LANCE", "pierce", "str", new float[]{0.95f, 0.97f, 1.0f}, "plows through 10 blocks per bounce, +2 damage"),
        new Evolution("midas", "MIDAS CORE", "gold", "split", new float[]{1.0f, 0.82f, 0.25f}, "any broken block may turn to gold"),
        new Evolution("nova", "NOVA CORE", "str", "blast", new float[]{1.0f, 0.45f, 0.2f}, "blasts are 60% wider and hit harder"),
        new Evolution("swarm", "SWARM CORE", "split", "arc", new float[]{0.75f, 0.55f, 1.0f}, "arc kills can bud new cores"));

    // ---------------------------------------------------------------- surface workshop (in-run, credits)
    public record ShopItem(String key, String name, String desc, int max, int base, double mul) {}
    public static final List<String> STARTER_KEYS = List.of("str", "split", "pierce", "blast", "arc", "gold");
    public static final List<ShopItem> SHOP = List.of(
        new ShopItem("width", "WIDE BUMPER", "+0.4 paddle width", 5, 300, 1.6),
        new ShopItem("magnet", "MAGNET HOPPER", "longer, stronger ore pull", 5, 250, 1.6),
        new ShopItem("armor", "ARMOR PLATING", "+20 max hull", 5, 350, 1.7),
        new ShopItem("str", "TUNGSTEN CORE", "fresh cores: +1 Strength", 3, 600, 1.9),
        new ShopItem("split", "FISSION PRIMER", "fresh cores: +1 Fission", 3, 700, 2.0),
        new ShopItem("pierce", "DRILL-POINT TIP", "fresh cores: +1 Pierce", 3, 500, 1.9),
        new ShopItem("blast", "DETONATOR CAP", "fresh cores: +1 Blast", 3, 550, 1.9),
        new ShopItem("arc", "TESLA COIL", "fresh cores: +1 Arc", 3, 550, 1.9),
        new ShopItem("gold", "ASSAY KIT", "fresh cores: +1 Prospector", 3, 450, 1.8),
        new ShopItem("luck", "SEISMIC SCANNER", "+25% power-up drop rate", 4, 400, 1.7),
        new ShopItem("drillbay", "DRILL BAY", "free Mega Drill after every drop", 2, 900, 2.0),
        new ShopItem("clamps", "HEAVY CLAMPS", "+8s before the clamps auto-release", 3, 250, 1.6),
        new ShopItem("skimmer", "SKIMMER PANEL", "wall panel magnets loose ore into the hold", 3, 500, 1.8),
        new ShopItem("servo", "SERVO WINCH", "stiffer spring: the rig follows your aim faster", 2, 350, 1.8),
        new ShopItem("catch", "GRAPPLE BUMPER", "cores stick to the bumper until you click", 2, 450, 2.0),
        new ShopItem("laser", "LASER PANEL", "wall panel lasers through the rock", 3, 650, 1.9));
    public static ShopItem shopItem(String key) { for (ShopItem s : SHOP) if (s.key.equals(key)) return s; return null; }

    // ---------------------------------------------------------------- foundry (permanent, scrip)
    public record MetaItem(String key, String name, String desc, int max, int base, String unlock) {}
    public static final List<MetaItem> META = List.of(
        new MetaItem("hull", "REINFORCED HULL", "+15 max hull", 5, 120, null),
        new MetaItem("brace", "BRACING", "-10% hull damage", 3, 220, null),
        new MetaItem("span", "BUMPER SPAN", "+0.25 paddle width", 3, 220, null),
        new MetaItem("lode", "LODESTONE", "+1 magnet level", 3, 180, null),
        new MetaItem("might", "MIGHT", "+10% chance of +1 hit damage", 5, 200, null),
        new MetaItem("winch", "WINCH CABLE", "+0.9 cells the rig can drop", 4, 160, null),
        new MetaItem("gov", "CORE GOVERNOR", "-5% core speed", 2, 350, null),
        new MetaItem("luck", "LUCK", "+10% power-up drops", 3, 300, null),
        new MetaItem("assay", "ASSAYER", "+10% ore value", 5, 180, null),
        new MetaItem("greed", "GREED", "+10% scrip earned", 5, 220, null),
        new MetaItem("dur", "DURATION", "+20% pod timers & drill depth", 2, 300, null),
        new MetaItem("stake", "GRUBSTAKE", "start runs with +100 credits", 3, 180, null),
        new MetaItem("reroll", "REROLL", "+1 free workshop reroll per run", 3, 350, "banks3"),
        new MetaItem("banish", "BANISH", "+1 workshop banish per run", 2, 450, "depth120"),
        new MetaItem("seed", "FISSION SEED", "first core starts with Fission 1", 1, 1500, "fission25"),
        new MetaItem("twin", "TWIN CORE", "start every run with 2 cores", 1, 2200, "cores8"),
        new MetaItem("revive", "EMERGENCY BALLAST", "once per run, survive at 50% hull", 1, 3500, "depth240"));
    public static MetaItem metaItem(String key) { for (MetaItem m : META) if (m.key.equals(key)) return m; return null; }

    public record Unlock(String desc, Predicate<SaveData.Records> test) {}
    public static final Map<String, Unlock> UNLOCKS = Map.of(
        "banks3", new Unlock("Bank 5 times in one run", r -> r.maxBanksRun >= 5),
        "depth120", new Unlock("Reach 120m", r -> r.bestDepth >= 120),
        "fission25", new Unlock("Catch 60 Fission pods (total)", r -> r.fission >= 60),
        "cores8", new Unlock("Run 8 cores at once", r -> r.maxCores >= 8),
        "depth240", new Unlock("Reach 600m", r -> r.bestDepth >= 600));

    // ---------------------------------------------------------------- tarot
    public record Arcana(int n, String name, String glyph, Map<String, Double> mods, boolean rare) {}
    private static Map<String, Double> m(Object... kv) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) out.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
        return out;
    }
    public static final List<Arcana> ARCANA = List.of(
        new Arcana(0, "THE FOOL", "✦", m("up_split", 1, "hullPct", -0.2), false),
        new Arcana(1, "THE MAGICIAN", "∞", m("up_arc", 1, "up_blast", 1, "speed", 0.1), false),
        new Arcana(2, "THE HIGH PRIESTESS", "☽", m("clamp", 10, "ore", -0.1), false),
        new Arcana(3, "THE EMPRESS", "♀", m("ore", 0.25), false),
        new Arcana(4, "THE EMPEROR", "♜", m("hull", 30, "padW", -0.3), false),
        new Arcana(5, "THE HIEROPHANT", "⚷", m("credits", 300, "shopCost", 0.15), false),
        new Arcana(6, "THE LOVERS", "♡", m("cores", 1, "chip", 1), false),
        new Arcana(7, "THE CHARIOT", "⚔", m("padW", 0.6, "speed", 0.1), false),
        new Arcana(8, "STRENGTH", "♌", m("up_str", 1, "pods", -0.1), false),
        new Arcana(9, "THE HERMIT", "⚲", m("magnet", 2, "pods", -0.2), false),
        new Arcana(10, "WHEEL OF FORTUNE", "☸", m("pods", 0.4, "ore", -0.15), false),
        new Arcana(11, "JUSTICE", "⚖", m("dmg", -0.2, "scrip", -0.15), false),
        new Arcana(12, "THE HANGED MAN", "⚓", m("clamp", 15, "speed", -0.1), false),
        new Arcana(13, "DEATH", "☠", m("scrip", 0.5, "hullPct", -0.4), false),
        new Arcana(14, "TEMPERANCE", "⧖", m("speed", -0.15, "ore", -0.2), false),
        new Arcana(15, "THE DEVIL", "⛧", m("ore", 0.6, "dmg", 0.4), false),
        new Arcana(16, "THE TOWER", "♖", m("gas", 1, "dmg", 0.2), false),
        new Arcana(17, "THE STAR", "★", m("pods", 0.25, "hull", 15), false),
        new Arcana(18, "THE MOON", "☾", m("magnet", 1, "chip", 0.5), false),
        new Arcana(19, "THE SUN", "☀", m("ore", 0.2, "pods", 0.2, "speed", 0.15), false),
        new Arcana(20, "JUDGEMENT", "♆", m("revive", 1, "scrip", -0.2), false),
        new Arcana(21, "THE WORLD", "⊕", m("ore", 0.1, "pods", 0.1, "scrip", 0.1, "hullPct", 0.1), true));
    public static final String[] ROMAN = {"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX", "XXI"};

    /** How a modifier key reads and what it's worth: label, unit ('%','s','x' or ''), good direction, scrip weight, flips when reversed. */
    public record Afx(String label, char unit, int good, double weight, boolean flips) {}
    public static final Map<String, Afx> AFX = Map.ofEntries(
        Map.entry("hull", new Afx("max hull", ' ', 1, 3, true)), Map.entry("hullPct", new Afx("max hull", '%', 1, 400, true)),
        Map.entry("dmg", new Afx("hull damage taken", '%', -1, 400, true)), Map.entry("padW", new Afx("paddle width", ' ', 1, 250, true)),
        Map.entry("speed", new Afx("core speed", '%', -1, 300, true)), Map.entry("ore", new Afx("ore value", '%', 1, 500, true)),
        Map.entry("pods", new Afx("power-up drops", '%', 1, 400, true)), Map.entry("scrip", new Afx("scrip earned", '%', 1, 500, true)),
        Map.entry("magnet", new Afx("magnet level", ' ', 1, 50, true)), Map.entry("credits", new Afx("starting credits", ' ', 1, 0.25, true)),
        Map.entry("rock", new Afx("rockfall frequency", '%', -1, 120, true)), Map.entry("warn", new Afx("rockfall warning time", '%', 1, 100, true)),
        Map.entry("clamp", new Afx("s on the clamp timer", 's', 1, 4, true)), Map.entry("chip", new Afx("missed-core damage", '%', -1, 80, true)),
        Map.entry("shopCost", new Afx("workshop prices", '%', -1, 400, true)), Map.entry("gas", new Afx("methane blast radius", '%', 1, 60, true)),
        Map.entry("cores", new Afx("extra starting core", 'x', 1, 160, false)), Map.entry("up_split", new Afx("Fission on fresh cores", 'x', 1, 130, false)),
        Map.entry("up_str", new Afx("Strength on fresh cores", 'x', 1, 130, false)), Map.entry("up_arc", new Afx("Arc on fresh cores", 'x', 1, 110, false)),
        Map.entry("up_blast", new Afx("Blast on fresh cores", 'x', 1, 110, false)), Map.entry("revive", new Afx("revive once per run at 30% hull", 'x', 1, 220, false)));

    // ---------------------------------------------------------------- bosses
    /**
     * A boss. {@code kind}: wyrm (bores and lunges), gate (frame that spawns blocks), bell (rings if struck while hung),
     * foundry (kilns vent fire at the rig), lodgers (independent presences that dive), copy (a mirrored rig).
     * {@code lunge} is the attack interval; segR/headR are hit radii; speed scales movement.
     */
    public record BossDef(int at, String name, int seg, int segHp, int headHp, float[] col, float lunge, boolean last,
                          String kind, float segR, float headR, float speed) {
        public BossDef(int at, String name, int seg, int segHp, int headHp, float[] col, float lunge, boolean last) {
            this(at, name, seg, segHp, headHp, col, lunge, last, "wyrm", 0.62f, 0.95f, 1);
        }
    }
    public static final List<BossDef> BOSSES = List.of(
        new BossDef(80, "ROCK-WYRM", 6, 3, 8, new float[]{1.0f, 0.85f, 0.7f}, 13, false),
        new BossDef(160, "FUNGAL WYRM", 8, 4, 12, new float[]{0.9f, 0.7f, 1.1f}, 12, false),
        new BossDef(260, "CLOCKWORK WYRM", 10, 5, 16, new float[]{1.1f, 0.85f, 0.5f}, 11, false),
        new BossDef(360, "THE ELDRITCH HEART", 12, 6, 24, new float[]{1.1f, 0.6f, 0.7f}, 10, true));

    // ---------------------------------------------------------------- levels (Gearwright's Hollow pack bible, depth bands 1-7)
    public enum LevelHazard { DEBRIS, GHOSTS, FLOOD, NOISE, TAR, VOID_LIGHTS, MIRRORS }
    /** pal[type] overrides the block base colour for that level (null = default). goal: depth to clear when there is no boss. */
    public record LevelDef(String name, String place, String hazardText, String bossText, String quote, int goal, BossDef boss,
                           LevelHazard hazard, float speedBonus, float hardBias, float[] fog, float[] amb, float[] tint, int[][] pal) {}
    private static int[][] pal(int[]... p) { int[][] o = new int[BLOCK_TYPES + 1][]; for (int i = 0; i < p.length; i++) o[i + 1] = p[i]; return o; }
    public static final List<LevelDef> LEVELS = List.of(
        new LevelDef("THE SHAFT", "A Guild mining shaft, lit by nothing", "Falling debris", "None: reach the shaft floor at 60m",
            "If you are reading this, you are the relief. - Sgt. R. Coyle", 60, null, LevelHazard.DEBRIS, 0, -0.05f,
            new float[]{0.030f, 0.024f, 0.020f}, new float[]{0.10f, 0.09f, 0.08f}, new float[]{1.0f, 0.95f, 0.88f},
            pal(new int[]{90, 62, 38}, new int[]{96, 94, 90}, new int[]{62, 58, 54}, null, new int[]{92, 74, 52})),
        new LevelDef("THE GATEHOUSE", "A research gate, built like an old stronghold", "Clerks' ghosts repair broken blocks", "The open gate: a frame that keeps spawning blocks",
            "The gate does not close as fast as it opens. - A. Venn, Clerk", 72,
            new BossDef(72, "THE OPEN GATE", 8, 3, 10, new float[]{1.1f, 0.75f, 0.6f}, 4.5f, false, "gate", 0.62f, 1.0f, 1),
            LevelHazard.GHOSTS, 0.3f, 0.05f, new float[]{0.030f, 0.020f, 0.035f}, new float[]{0.09f, 0.08f, 0.10f}, new float[]{1.0f, 0.92f, 0.88f},
            pal(new int[]{118, 70, 52}, new int[]{126, 72, 58}, new int[]{58, 60, 66}, new int[]{90, 64, 40}, new int[]{60, 70, 80})),
        new LevelDef("THE TIDEWELL", "A sea-cooled lab its keeper flooded", "Flooding rows slow the core", "The great one in the flooded halls",
            "Every gate was built to fix the last one.", 84,
            new BossDef(84, "THE GREAT ONE", 10, 3, 12, new float[]{0.6f, 1.0f, 1.0f}, 12, false, "wyrm", 0.8f, 1.2f, 0.7f),
            LevelHazard.FLOOD, 0.5f, 0, new float[]{0.010f, 0.040f, 0.050f}, new float[]{0.05f, 0.09f, 0.11f}, new float[]{0.85f, 1.0f, 1.05f},
            pal(new int[]{62, 112, 102}, new int[]{72, 122, 114}, new int[]{30, 54, 62}, new int[]{70, 96, 90}, new int[]{52, 70, 92})),
        new LevelDef("THE LISTENING POST", "A sculk research camp in an open cavern", "Hitting sculk wakes something", "Brandt's bell, which mustn't be rung",
            "do not ring the bell - N. Brandt", 84,
            new BossDef(84, "BRANDT'S BELL", 4, 4, 14, new float[]{1.2f, 0.95f, 0.55f}, 0, false, "bell", 0.6f, 1.1f, 1),
            LevelHazard.NOISE, 0.6f, 0.05f, new float[]{0.010f, 0.020f, 0.030f}, new float[]{0.05f, 0.06f, 0.08f}, new float[]{0.9f, 0.95f, 1.05f},
            pal(new int[]{170, 140, 72}, new int[]{64, 64, 72}, new int[]{40, 40, 48}, new int[]{70, 66, 64}, new int[]{50, 56, 70})),
        new LevelDef("BELOW", "The Nether station and its foundry", "Tar sticks the core", "Gale's banked foundry",
            "Order refused. Kilns banked. - Foreman T. Gale", 96,
            new BossDef(96, "GALE'S BANKED FOUNDRY", 5, 5, 16, new float[]{1.2f, 0.6f, 0.45f}, 7, false, "foundry", 0.75f, 1.1f, 1),
            LevelHazard.TAR, 0.8f, 0.08f, new float[]{0.070f, 0.020f, 0.010f}, new float[]{0.12f, 0.05f, 0.04f}, new float[]{1.12f, 0.85f, 0.75f},
            pal(new int[]{110, 40, 36}, new int[]{92, 44, 40}, new int[]{34, 30, 34}, new int[]{100, 50, 40}, new int[]{70, 48, 60})),
        new LevelDef("BEYOND", "The End station: frosted frames in the void", "Lights in the void steer the core", "Marsh's lodgers",
            "We are not drawing power. We are being drawn on. - Dr. I. Marsh", 96,
            new BossDef(96, "MARSH'S LODGERS", 6, 4, 14, new float[]{0.9f, 0.85f, 1.2f}, 9, false, "lodgers", 0.62f, 0.9f, 1),
            LevelHazard.VOID_LIGHTS, 0.9f, 0.05f, new float[]{0.030f, 0.010f, 0.050f}, new float[]{0.08f, 0.06f, 0.11f}, new float[]{1.0f, 0.95f, 1.1f},
            pal(new int[]{200, 196, 140}, new int[]{186, 182, 132}, new int[]{72, 54, 92}, new int[]{180, 176, 130}, new int[]{160, 150, 200})),
        new LevelDef("THE HOLLOW", "The first gate, in the Guild's namesake valley", "Mirror blocks bounce the core straight back", "Your copy: a second rig that mirrors your moves",
            "It has been waiting in your shape.", 108,
            new BossDef(108, "YOUR COPY", 4, 5, 24, new float[]{0.55f, 0.9f, 0.85f}, 0, true, "copy", 0.5f, 0.45f, 1),
            LevelHazard.MIRRORS, 1.0f, 0.1f, new float[]{0.020f, 0.030f, 0.030f}, new float[]{0.06f, 0.08f, 0.08f}, new float[]{0.95f, 1.0f, 1.0f},
            pal(new int[]{72, 64, 60}, new int[]{56, 58, 64}, new int[]{30, 32, 36}, new int[]{150, 128, 88}, new int[]{60, 70, 72})));

    /** Loading-screen lines, from the pack's records. */
    public static final List<String> QUOTES = List.of(
        "The gate does not close as fast as it opens. - A. Venn, Clerk", "We are not drawing power. We are being drawn on. - Dr. I. Marsh",
        "Speak quietly. Wear wax.", "Do not drop tools. - Listening Post orders", "do not ring the bell - N. Brandt",
        "If you are reading this, you are the relief. - Sgt. R. Coyle", "It is not our smoke. - Foreman T. Gale", "Order refused. Kilns banked. - Foreman T. Gale",
        "The black gets into ink. - P. Sallow, Archivist", "I keep a light on in case anyone comes asking. - the Penitent", "I will not build a frame. - W. Tamsin",
        "The staff call them lodgers. I have asked them to stop. - M. Ashdown, Clerk", "The furnaces burn hotter when the crew is angry. - Master E. Harrow",
        "The well water tastes of pennies since. - Edda, unsent", "Each night the bell in the Hollow tolls twelve, and nobody rings it. - unsigned sermon",
        "It has been waiting in your shape.", "What the Foundry leaves, only the Foundry's own can gather.",
        "An escapement lets the world run without asking anything of it.", "Every gate was built to fix the last one.",
        "Nobody below a master was allowed to see a whole frame.", "The draught is not heat, though it comes with heat.");

    // ---------------------------------------------------------------- biomes (atmosphere only)
    public record Biome(int at, String name, float[] fog, float[] amb, float[] tint) {}
    public static final List<Biome> BIOMES = List.of(
        new Biome(0, "UPPER SEAMS", new float[]{0.020f, 0.018f, 0.022f}, new float[]{0.090f, 0.083f, 0.086f}, new float[]{1.00f, 0.97f, 0.92f}),
        new Biome(80, "FUNGAL CAVERNS", new float[]{0.045f, 0.018f, 0.060f}, new float[]{0.090f, 0.063f, 0.126f}, new float[]{0.92f, 0.86f, 1.06f}),
        new Biome(160, "SUNKEN CLOCKWORK FOUNDRY", new float[]{0.070f, 0.035f, 0.010f}, new float[]{0.126f, 0.081f, 0.045f}, new float[]{1.08f, 0.92f, 0.78f}),
        new Biome(260, "THE ELDRITCH CORE", new float[]{0.050f, 0.005f, 0.012f}, new float[]{0.117f, 0.036f, 0.054f}, new float[]{1.06f, 0.80f, 0.86f}));

    /** Blended atmosphere at a depth in metres (30 m crossfade before each boundary). */
    public record Atmosphere(String name, float[] fog, float[] amb, float[] tint) {}
    public static Atmosphere atmosphereAt(float depthM) {
        int i = 0;
        while (i < BIOMES.size() - 1 && depthM >= BIOMES.get(i + 1).at) i++;
        Biome a = BIOMES.get(i), b = BIOMES.get(Math.min(i + 1, BIOMES.size() - 1));
        float t = a == b ? 0 : Mth.clamp((depthM - (b.at - 30)) / 30f, 0, 1);
        return new Atmosphere(t > 0.5f ? b.name : a.name, Mth.mix(a.fog, b.fog, t), Mth.mix(a.amb, b.amb, t), Mth.mix(a.tint, b.tint, t));
    }
}
