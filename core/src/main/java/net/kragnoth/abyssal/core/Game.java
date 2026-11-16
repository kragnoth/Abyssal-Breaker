package net.kragnoth.abyssal.core;

import net.kragnoth.abyssal.core.Defs.*;
import net.kragnoth.abyssal.core.Entities.*;
import net.kragnoth.abyssal.core.SaveData.Card;

import java.util.*;

import static net.kragnoth.abyssal.core.Defs.*;
import static net.kragnoth.abyssal.core.Mth.*;

/**
 * The whole game simulation: world, physics, upgrades, economy, bosses, meta-progression.
 * Front-ends call {@link #update(float)} once per frame, feed input through the pointer/key
 * methods, and read the public fields to draw. Nothing in here touches OpenGL or audio.
 */
public final class Game {
    public enum State { TITLE, PLAY, PAUSED, JUNCTION, SHOP, FOUNDRY, OVER, VICTORY }

    public final Fx fx;
    private final SaveStore store;
    public SaveData save;
    public final Random rng;

    public State state = State.TITLE;
    private State prevState = State.TITLE;
    private boolean overHidden;               // game-over screen hidden while the Foundry is open
    private int seed;

    // ---- world & entities
    public final Map<Integer, Row> rows = new HashMap<>();
    public final List<Ball> balls = new ArrayList<>();
    public final List<Loot> loot = new ArrayList<>();
    public final List<Drill> drills = new ArrayList<>();
    public final List<Panel> panels = new ArrayList<>();
    public final List<Hazard> hazards = new ArrayList<>();
    public Boss boss;

    // ---- rig
    public int R;
    public float rigY, px, pxTarget, drop, dropV, dropTarget, paddleY, padW = PW, rigKick, rigVel;
    private int firstRock = ZONE_OFF;
    private float mouseFy = -1;
    private boolean usingMouse = true;
    private final Set<String> keys = new HashSet<>();

    // ---- run economy
    public float hull;
    public long cargo, banked, bankedTotal, score;
    public float cargoMult = 1;
    public int combo, bestCombo;
    public float pressureT = -1;
    public Dropping dropping;
    public float shake, hullFlash, time;
    public final Map<String, Integer> shop = new HashMap<>();
    public float wideT, magT, catchT;
    private float zoneLast = -1, zoneIdle;
    public int banksRun;
    private boolean reviveUsed;
    public final List<String> offers = new ArrayList<>();
    private final Set<String> banished = new HashSet<>();
    public int rerollsFree, rerollPaid, banishLeft;
    private final Map<String, Double> arc = new HashMap<>();
    private int bossIdx, endlessNext;
    public boolean endless, won, refundArmed, newGameArmed;
    public int bossKills;
    private boolean rockAnnounced;
    private float hazT = 8;
    private final List<Timer> timers = new ArrayList<>();
    public Stats stats = new Stats();
    public LastRun lastRun;
    public Bot bot;

    public static final class Dropping { public float from, to, t; int crushed; Map<String, Integer> carry; }
    public record LastRun(boolean win, int depthM, long earned, List<String[]> parts, List<String> fresh, float seconds) {}
    private record Timer(Runnable fn, float[] t) {}
    public static final class Bot { public final float skill; Ball tgt; float err, aim, seen, lt; public Bot(float s) { skill = s; } }

    public Game(Fx fx, SaveStore store, long rngSeed) {
        this.fx = fx == null ? Fx.NONE : fx;
        this.store = store;
        this.rng = new Random(rngSeed);
        this.save = store.load().normalised();
        seed = rng.nextInt();
        ensureRows();
        dockNew(new HashMap<>());
    }

    // ================================================================ small helpers
    private float rnd(float a, float b) { return a + rng.nextFloat() * (b - a); }
    private float rnd() { return rng.nextFloat(); }
    public float cellX(int c) { return c - HALF + 0.5f; }
    public int lv(String k) { return shop.getOrDefault(k, 0); }
    public int mr(String k) { return save.ranks.getOrDefault(k, 0); }
    public double A(String k) { return arc.getOrDefault(k, 0.0); }
    private void msg(String t, int col, float dur) { fx.message(t, col, dur); }
    private void later(Runnable fn, float sec) { timers.add(new Timer(fn, new float[]{sec})); }

    public float curSpeed() { return (float) (Math.min(10.5f + R * 0.03f, 18f) * (1 - 0.05 * mr("gov")) * Math.max(0.6, 1 + A("speed"))); }
    public int maxHull() { return (int) Math.max(30, Math.round((100 + 20 * lv("armor") + 15 * mr("hull") + A("hull")) * (1 + A("hullPct")))); }
    public float basePadW() { return (float) Math.max(1.6, PW + 0.4 * lv("width") + 0.25 * mr("span") + A("padW")); }
    public float pressureMax() { return (float) Math.max(8, PRESSURE_TIME + 8 * lv("clamps") + A("clamp")); }
    public float reach() { return Math.min(4.6f, 1 + 0.9f * mr("winch")); }
    private float springK() { return 120f * (1 + 0.6f * lv("servo")); }
    public boolean catching() { return lv("catch") > 0 || catchT > 0; }
    private float holdLimit() { return lv("catch") >= 2 ? Float.POSITIVE_INFINITY : 4; }
    public int depthM() { return Math.round(-rigY * M_PER_ROW); }
    public int magnetLevel() { return (int) Math.max(0, lv("magnet") + mr("lode") + A("magnet") + (magT > 0 ? 3 : 0)); }

    public long shopCost(ShopItem it) { return Math.round(it.base() * Math.pow(it.mul(), lv(it.key())) * Math.max(0.5, 1 + A("shopCost"))); }
    public long metaCost(MetaItem m) { return Math.round(m.base() * (mr(m.key()) + 1) + 15 * (Math.pow(1.1, save.bought) - 1)); }
    public boolean metaUnlocked(MetaItem m) { return m.unlock() == null || UNLOCKS.get(m.unlock()).test().test(save.rec); }
    public long rerollCost() { return rerollsFree > 0 ? 0 : 100L * (1 + rerollPaid); }

    private Map<String, Integer> starterUp() {
        Map<String, Integer> u = new HashMap<>();
        int room = MAX_UP;
        for (String k : STARTER_KEYS) {
            int n = (int) Math.min(lv(k) + Math.max(0, A("up_" + k)), room);
            if (n > 0) { u.put(k, n); room -= n; }
        }
        return u;
    }

    // ================================================================ tarot
    public Map<String, Double> cardMods(Card c) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (var e : ARCANA.get(c.n).mods().entrySet()) {
            if (!c.rev) out.put(e.getKey(), e.getValue());
            else if (AFX.get(e.getKey()).flips()) out.put(e.getKey(), -e.getValue());
        }
        return out;
    }
    public long cardValue(Card c) {
        double v = 60;
        for (var e : cardMods(c).entrySet()) { Afx a = AFX.get(e.getKey()); v += e.getValue() * a.good() * a.weight(); }
        return Math.max(15, Math.round(v));
    }
    private Card drawCard() {
        Set<Integer> held = new HashSet<>();
        for (Card c : save.hand) held.add(c.n);
        int n;
        do { n = rng.nextInt(ARCANA.size()); } while (held.contains(n) || (ARCANA.get(n).rare() && rnd() < 0.6));
        return new Card(n, !ARCANA.get(n).rare() && rnd() < 0.3);
    }
    private void computeArc() {
        arc.clear();
        for (Card c : save.hand) for (var e : cardMods(c).entrySet()) arc.merge(e.getKey(), e.getValue(), Double::sum);
    }
    public void tarotChoice(String t) {
        Card c = save.pending;
        if (c == null) return;
        switch (t) {
            case "pass" -> { long v = cardValue(c); save.scrip += v; save.rec.totalScrip += v; msg("+" + v + " SCRIP", 0xffc43c, 1); }
            case "keep" -> { if (save.hand.size() >= 3) { fx.sound(Fx.Sfx.DENY, 0); return; } save.hand.add(c); }
            default -> {
                if (!t.startsWith("r")) return;
                int i = Integer.parseInt(t.substring(1));
                if (i < 0 || i >= save.hand.size()) return;
                save.hand.set(i, c);
            }
        }
        save.pending = null;
        fx.sound(Fx.Sfx.BUY, 0);
        persist();
    }

    // ================================================================ persistence
    public void persist() { save.t = System.currentTimeMillis(); store.store(save); }

    // ================================================================ world
    private void genRow(int r) {
        Row row = new Row(r);
        if (r >= firstRock) {
            for (int c = 0; c < COLS; c++) {
                double cave = fbm(c * 0.22 + 3.1, r * 0.16, seed);
                if (r > firstRock + 3 && cave > 0.64) continue;                       // cavern pockets
                double h = fbm(c * 0.3 + 40, r * 0.22 + 11, seed) + Math.min(r / 260.0, 0.32) - Math.max(0, 14 - r) * 0.02;
                int ty = h < 0.44 ? DIRT : h < 0.7 ? STONE : BASALT;
                if (fbm(c * 0.45 + 90, r * 0.45 + 70, seed) > 0.69) ty = GOLD;
                if (r > 12 && fbm(c * 0.6 + 200, r * 0.6 + 33, seed) > 0.74) ty = AETHER;
                if (r > 10 && hash(c * 3 + 1, r * 7 + 5, seed) < 0.016) ty = GAS;
                row.t[c] = ty; row.hp[c] = HP[ty]; row.maxHp[c] = HP[ty]; row.solid0++;
            }
        }
        rows.put(r, row);
    }
    public void ensureRows() {
        for (int r = R - 4; r <= R + 36; r++) if (!rows.containsKey(r)) genRow(r);
        rows.keySet().removeIf(r -> r < R - 7);
    }
    public int cell(int r, int c) { Row row = rows.get(r); return row == null || c < 0 || c >= COLS ? 0 : row.t[c]; }
    private void placeBlock(int r, int c, int ty) {
        Row row = rows.get(r);
        if (row == null || row.t[c] != 0 || (boss != null && Math.hypot(cellX(c) - boss.x, -r - boss.y) < 1.6)) return;
        row.t[c] = ty; row.hp[c] = HP[ty]; row.maxHp[c] = HP[ty]; row.version++;
        fx.debris(cellX(c), -r, DEBRIS[ty], 3);
    }

    /** @return true if the block was destroyed. {@code src} is a Ball, "drill", "laser" or null. */
    private boolean damageBlock(int r, int c, int amt, boolean reward, Object src) {
        Row row = rows.get(r);
        if (row == null || c < 0 || c >= COLS || row.t[c] == 0) return false;
        int ty = row.t[c];
        row.hp[c] -= amt;
        if (row.hp[c] <= 0) { destroyBlock(r, c, reward, src); return true; }
        row.version++;
        fx.burst(cellX(c), -r, 6, 1, 0.6f, 0.25f, 4, 0.3f);
        fx.debris(cellX(c), -r, DEBRIS[ty], 2);
        return false;
    }
    private void destroyBlock(int r, int c, boolean reward, Object src) {
        Row row = rows.get(r);
        int ty = row.t[c];
        row.t[c] = 0; row.version++;
        float x = cellX(c), y = -r;
        fx.debris(x, y, DEBRIS[ty], reward ? 7 : 4);
        fx.burst(x, y, reward ? 10 : 4, 0.9f, 0.55f, 0.25f, 5, 0.4f);
        if (!reward) return;
        score += Math.round(SCORE[ty] * (1 + combo * 0.1)); stats.blocks++; stats.lastProg = stats.t;
        Ball b = src instanceof Ball bb ? bb : null;
        int pro = b != null ? b.up("gold") : 0;
        if (b != null && b.evo("midas") && rnd() < 0.4) spawnLoot(x, y, "gold", null);
        if (ty == GOLD) { int n = 2 + (rnd() < 0.4 ? 1 : 0) + pro; for (int i = 0; i < n; i++) spawnLoot(x, y, "gold", null); }
        if (ty == AETHER) for (int i = 0; i <= (pro >> 1); i++) spawnLoot(x, y, "aether", null);
        if (ty != GOLD && ty != AETHER && pro > 0 && rnd() < 0.06 * pro) spawnLoot(x, y, "gold", null);
        double pod = ("drill".equals(src) || "laser".equals(src)) ? 0.006 : ty == AETHER ? 0.25 : ty == GOLD ? 0.05 : ty == GAS ? 0.1 : 0.014;
        if (rnd() < pod * Math.max(0.2, 1 + 0.25 * lv("luck") + 0.1 * mr("luck") + A("pods"))) spawnLoot(x, y, "pu", rollPU());
        if (ty == GAS) explode(r, c);
    }
    private void explode(int r0, int c0) {
        float x = cellX(c0), y = -r0;
        fx.sound(Fx.Sfx.BOOM, 0); shake = Math.max(shake, 0.9f);
        fx.flash(x, y, 1.6f, 2.2f, 0.8f, 12, 0.5f);
        fx.burst(x, y, 80, 0.7f, 1, 0.4f, 12, 0.7f); fx.burst(x, y, 40, 1, 0.6f, 0.2f, 8, 0.9f);
        double r2 = 5.3 * Math.max(0.3, 1 + A("gas"));
        int gr = (int) Math.ceil(Math.sqrt(r2));
        for (int r = r0 - gr; r <= r0 + gr; r++) for (int c = c0 - gr; c <= c0 + gr; c++) {
            if (c < 0 || c >= COLS || (r == r0 && c == c0)) continue;
            if ((r - r0) * (r - r0) + (c - c0) * (c - c0) <= r2) damageBlock(r, c, 3, true, null);
        }
        for (Ball b : balls) if (!b.docked && Math.hypot(b.x - x, b.y - y) < 4) b.boost = 1.7f;
        msg("METHANE POCKET", 0x9cff6a, 0.9f);
    }
    private String rollPU() {
        int total = 0;
        for (PowerUp p : PODS) total += p.weight();
        float r = rnd() * total;
        for (PowerUp p : PODS) { r -= p.weight(); if (r <= 0) return p.key(); }
        return "split";
    }

    // ================================================================ cores
    private Ball addBall(float x, float y, float vx, float vy, boolean docked, Map<String, Integer> up) {
        if (balls.size() >= MAX_BALLS) return null;
        Ball b = new Ball();
        b.x = x; b.y = y; b.vx = vx; b.vy = vy; b.docked = docked;
        b.up.putAll(up); b.pierceLeft = b.up("pierce");
        restyle(b); balls.add(b);
        if (state == State.PLAY) checkEvo(b);
        return b;
    }
    /** Core colour = blend of its upgrades, or of its evolutions once it has any. */
    public void restyle(Ball b) {
        float w = 1; float[] col = {1, 0.55f, 0.2f};
        for (String k : UP_KEYS) if (b.up(k) > 0) { int n = b.up(k); float[] u = UP_RGB.get(k); for (int i = 0; i < 3; i++) col[i] += u[i] * n; w += n; }
        for (int i = 0; i < 3; i++) col[i] /= w;
        int ev = 0; float[] ec = {0, 0, 0};
        for (Evolution e : EVOS) if (b.evo(e.key())) { ev++; for (int i = 0; i < 3; i++) ec[i] += e.col()[i]; }
        if (ev > 0) for (int i = 0; i < 3; i++) col[i] = ec[i] / ev;
        b.col = col;
    }
    private void dockNew(Map<String, Integer> up) { addBall(px, paddleY - 0.55f - BR - 0.02f, 0, 0, true, up); }
    private float minAngle(float a) {
        if (Math.abs(a) >= 0.12f) return a;
        return (a < 0 || (a == 0 && rnd() < 0.5) ? -1 : 1) * rnd(0.12f, 0.2f);
    }
    public void launch() {
        for (Ball b : balls) if (b.docked) {
            float a = minAngle(b.held ? clamp(b.holdX / (padW / 2), -1, 1) * 1.05f : rnd(-0.45f, 0.45f)), s = curSpeed() * b.boost;
            b.docked = false; b.held = false; b.holdX = 0;
            b.vx = s * (float) Math.sin(a); b.vy = -s * (float) Math.cos(a);
            fx.sound(Fx.Sfx.LAUNCH, 0);
        }
    }
    private void checkEvo(Ball b) {
        for (Evolution e : EVOS) {
            if (!b.evo(e.key()) && b.up(e.a()) >= 2 && b.up(e.b()) >= 2) {
                b.up.put("evo_" + e.key(), 1); restyle(b); stats.evos++;
                msg("EVOLUTION: " + e.name(), 0x7affd4, 1.6f); shake = Math.max(shake, 0.4f);
                fx.burst(b.x, b.y, 60, e.col()[0], e.col()[1], e.col()[2], 9, 0.7f);
                fx.flash(b.x, b.y, e.col()[0] * 2.5f, e.col()[1] * 2.5f, e.col()[2] * 2.5f, 10, 0.4f);
                fx.sound(Fx.Sfx.EVOLVE, 0);
            }
        }
    }
    private void fissionCheck(Ball b) {
        if (b.up("split") > 0 && time - b.lastSplit > 0.6f && rnd() < 0.08 * b.up("split")) splitOne(b);
    }
    private void split() {
        if (balls.stream().anyMatch(b -> b.docked)) launch();
        for (Ball b : new ArrayList<>(balls)) splitOne(b);
    }
    /** One core buds a child that inherits everything, including Fission — so splits keep splitting. */
    private void splitOne(Ball b) {
        if (balls.size() >= MAX_BALLS || b.docked) return;
        float a = rnd(0.3f, 0.5f) * (rnd() < 0.5 ? -1 : 1), c = (float) Math.cos(a), s = (float) Math.sin(a);
        Ball nb = addBall(b.x, b.y, b.vx * c - b.vy * s, b.vx * s + b.vy * c, false, b.up);
        if (nb == null) return;
        nb.boost = b.boost; nb.pierceLeft = b.pierceLeft; nb.lastSplit = b.lastSplit = time;
        fx.burst(b.x, b.y, 14, 1, 0.5f, 0.15f, 5, 0.35f); fx.sound(Fx.Sfx.POD, 0);
    }

    private void stepBall(Ball b, float dt) {
        b.x += b.vx * dt; b.y += b.vy * dt;
        if (b.x - BR < -HALF) { b.x = -HALF + BR; b.vx = Math.abs(b.vx); fx.sound(Fx.Sfx.WALL, 0); }
        if (b.x + BR > HALF) { b.x = HALF - BR; b.vx = -Math.abs(b.vx); fx.sound(Fx.Sfx.WALL, 0); }
        if (b.y < -(R + 34)) { b.vy = Math.abs(b.vy); b.vx += rnd(-2, 2); }
        // bumper (underside of the rig)
        float pBot = paddleY - 0.55f;
        if (b.vy > 0 && b.y + BR >= pBot && b.y < pBot + 0.45f && Math.abs(b.x - px) <= padW / 2 + BR * 0.8f) {
            if (dropV > 3 && dropping == null) {                      // spring slam
                b.boost = Math.max(b.boost, 1.35f); b.slam = 1; stats.slams++;
                msg("SLAM", 0xffb347, 0.5f); fx.burst(b.x, pBot, 26, 1, 0.75f, 0.3f, 8, 0.4f); shake = Math.max(shake, 0.3f);
                fx.sound(Fx.Sfx.SLAM, 0);
            }
            float off = clamp((b.x - px) / (padW / 2), -1, 1), a = minAngle(off * 1.05f), s = curSpeed() * b.boost;
            b.vx = s * (float) Math.sin(a); b.vy = -s * (float) Math.cos(a); b.y = pBot - BR; b.pierceLeft = b.up("pierce"); b.lanceLeft = 10;
            if (catching()) {
                b.docked = true; b.held = true; b.holdX = clamp(b.x - px, -padW / 2, padW / 2); b.holdT = 0;
                fx.sound(Fx.Sfx.CATCH, 0); combo = 0; return;
            }
            combo = 0; fx.sound(Fx.Sfx.PADDLE, 0); fx.burst(b.x, pBot, 8, 1, 0.8f, 0.4f, 4, 0.25f); rigKick = 0.12f; dropV -= 2.5f;
            return;
        }
        if (b.y - BR > rigY + 0.7f) { b.lost = true; return; }
        if (boss != null && hitBoss(b)) return;
        // voxel grid
        int c0 = Math.max(0, (int) Math.floor(b.x - BR + HALF)), c1 = Math.min(COLS - 1, (int) Math.floor(b.x + BR + HALF));
        int rA = (int) Math.floor(-(b.y + BR) + 0.5f), rB = (int) Math.floor(-(b.y - BR) + 0.5f);
        int br = 0, bc = 0; float bnx = 0, bny = 0, bdx = 0, bdy = 0, bd = 0, bpen = -1;
        for (int r = rA; r <= rB; r++) for (int c = c0; c <= c1; c++) {
            int t = cell(r, c);
            if (t == 0) continue;
            float cx = cellX(c), cy = -r, nx = clamp(b.x, cx - 0.5f, cx + 0.5f), ny = clamp(b.y, cy - 0.5f, cy + 0.5f);
            float dx = b.x - nx, dy = b.y - ny, d2 = dx * dx + dy * dy;
            if (d2 >= BR * BR) continue;
            if (PIERCE[t]) {                                           // dirt: plow straight through
                combo++; bestCombo = Math.max(bestCombo, combo); fx.sound(Fx.Sfx.HIT, combo);
                destroyBlock(r, c, true, b); fissionCheck(b); continue;
            }
            float d = (float) Math.sqrt(d2), pen = BR - d;
            if (pen > bpen) { bpen = pen; br = r; bc = c; bnx = nx; bny = ny; bdx = dx; bdy = dy; bd = d; }
        }
        if (bpen < 0) return;
        int ty = cell(br, bc);
        int dmg = 1 + b.up("str") + (rnd() < 0.1 * mr("might") ? 1 : 0) + (b.evo("lance") ? 2 : 0) + (b.slam > 0 ? 2 : 0);
        if (b.slam > 0) { b.slam = 0; fx.burst(b.x, b.y, 24, 1, 0.8f, 0.4f, 7, 0.4f); shake = Math.max(shake, 0.25f); }
        combo++; bestCombo = Math.max(bestCombo, combo);
        fx.sound(ty == BASALT ? Fx.Sfx.HIT_HARD : Fx.Sfx.HIT, combo);
        boolean killed = damageBlock(br, bc, dmg, true, b);
        if (killed) fissionCheck(b);
        coreFx(b, br, bc);
        if (killed && (b.pierceLeft > 0 || (b.evo("lance") && b.lanceLeft > 0))) {   // drill-point / lance
            if (b.pierceLeft > 0) b.pierceLeft--; else b.lanceLeft--;
            fx.burst(b.x, b.y, 6, 0.85f, 0.92f, 1, 5, 0.3f);
            return;
        }
        float n0, n1;
        if (bd > 1e-5f) { n0 = bdx / bd; n1 = bdy / bd; }
        else { float s = (float) Math.hypot(b.vx, b.vy); n0 = -b.vx / s; n1 = -b.vy / s; }
        float dot = b.vx * n0 + b.vy * n1;
        if (dot < 0) { b.vx -= 2 * dot * n0; b.vy -= 2 * dot * n1; }
        b.x = bnx + n0 * (BR + 0.002f); b.y = bny + n1 * (BR + 0.002f);
        float s = (float) Math.hypot(b.vx, b.vy); if (s == 0) s = 1;
        float minV = 0.28f * s;
        if (Math.abs(b.vy) < minV) { b.vy = (b.vy < 0 ? -1 : 1) * minV; float k = s / (float) Math.hypot(b.vx, b.vy); b.vx *= k; b.vy *= k; }
        if (Math.abs(b.vx) < 0.12f * s) { b.vx = (b.vx < 0 || (b.vx == 0 && rnd() < 0.5) ? -1 : 1) * 0.12f * s; float k = s / (float) Math.hypot(b.vx, b.vy); b.vx *= k; b.vy *= k; }
    }

    /** On-hit effects the core carries: Blast splash and Arc lightning (plus their evolutions). */
    private void coreFx(Ball b, int r0, int c0) {
        int bl = b.up("blast"), arcN = b.up("arc") + (b.evo("storm") ? 2 : 0);
        float x0 = cellX(c0), y0 = -r0;
        if (bl > 0) {
            float rad = (0.9f + bl * 0.45f) * (b.evo("nova") ? 1.6f : 1);
            int ri = (int) Math.ceil(rad), d = 1 + (bl >> 2) + (b.evo("nova") ? 1 : 0);
            for (int r = r0 - ri; r <= r0 + ri; r++) for (int c = c0 - ri; c <= c0 + ri; c++) {
                if (c < 0 || c >= COLS || (r == r0 && c == c0) || (r - r0) * (r - r0) + (c - c0) * (c - c0) > rad * rad) continue;
                if (cell(r, c) != 0) damageBlock(r, c, d, true, b);
            }
            fx.burst(x0, y0, 10 + bl * 6, 1, 0.6f, 0.15f, 4 + rad * 2, 0.35f);
            if (bl >= 3) { shake = Math.max(shake, 0.12f + bl * 0.03f); fx.flash(x0, y0, 1.6f, 0.9f, 0.3f, 3 + rad * 1.5f, 0.15f); }
            fx.sound(Fx.Sfx.CRUNCH, 0);
        }
        if (arcN > 0) {
            List<int[]> cand = new ArrayList<>();
            for (int r = r0 - 3; r <= r0 + 3; r++) for (int c = Math.max(0, c0 - 3); c <= Math.min(COLS - 1, c0 + 3); c++)
                if (cell(r, c) != 0 && !(r == r0 && c == c0)) cand.add(new int[]{r, c});
            for (int i = 0; i < arcN && !cand.isEmpty(); i++) {
                int[] rc = cand.remove(rng.nextInt(cand.size()));
                int r = rc[0], c = rc[1]; float x1 = cellX(c), y1 = -r;
                for (int s = 0; s <= 10; s++) { float t = s / 10f; fx.spark(lerp(x0, x1, t) + rnd(-0.15f, 0.15f), lerp(y0, y1, t) + rnd(-0.15f, 0.15f), 0.8f, 0, 0, 0.4f, 0.95f, 1, 0.18f); }
                boolean killedArc = damageBlock(r, c, 1, true, b);
                if (killedArc && b.evo("swarm") && rnd() < 0.15) splitOne(b);
                if (b.evo("storm")) {
                    for (int rr = r - 1; rr <= r + 1; rr++) for (int cc = Math.max(0, c - 1); cc <= Math.min(COLS - 1, c + 1); cc++)
                        if (cell(rr, cc) != 0) damageBlock(rr, cc, 1, true, b);
                    fx.burst(x1, y1, 14, 0.55f, 0.95f, 1, 5, 0.3f);
                }
            }
            fx.sound(Fx.Sfx.ARC, 0);
        }
    }

    // ================================================================ loot & pods
    private void spawnLoot(float x, float y, String kind, String pu) {
        Loot l = new Loot(kind, pu);
        l.x = x; l.y = y; l.vx = rnd(-1.6f, 1.6f); l.vy = rnd(-3, -1); l.spin = "pu".equals(kind) ? 0 : rnd(2, 6);
        loot.add(l);
    }
    private void collect(Loot l) {
        if ("pu".equals(l.kind)) { applyPU(l.pu); fx.burst(l.x, paddleY - 0.5f, 16, 1, 0.7f, 0.4f, 4, 0.4f); return; }
        double v = ("gold".equals(l.kind) ? 10 : 50) * (1 + 0.1 * mr("assay")) * Math.max(0.1, 1 + A("ore"));
        cargo += Math.round(v * cargoMult); stats.ore += v;
        fx.sound("aether".equals(l.kind) ? Fx.Sfx.ORE_HI : Fx.Sfx.ORE, 0);
        boolean ae = "aether".equals(l.kind);
        fx.burst(l.x, paddleY - 0.5f, 10, ae ? 0.4f : 1, ae ? 0.9f : 0.8f, ae ? 1 : 0.3f, 3, 0.3f);
    }
    private void applyPU(String k) {
        PowerUp P = pod(k);
        fx.sound(Fx.Sfx.POD, 0);
        stats.pods.merge(k, 1, Integer::sum);
        if (stats.firstPod < 0) stats.firstPod = stats.t;
        if (P.up()) {
            int took = 0;
            for (Ball b : balls) if (b.total() < MAX_UP) { b.up.merge(k, 1, Integer::sum); if (k.equals("pierce")) b.pierceLeft++; restyle(b); took++; }
            for (Ball b : new ArrayList<>(balls)) checkEvo(b);
            if (took == 0) { cargo += Math.round(25 * cargoMult); msg("CORES MAXED  +25", 0xffc43c, 0.8f); return; }
            int lvl = 0; for (Ball b : balls) lvl = Math.max(lvl, b.up(k));
            if (k.equals("split")) split();
            msg(P.name() + " " + lvl, P.color(), 0.8f);
            return;
        }
        float dur = 1 + 0.2f * mr("dur");
        switch (k) {
            case "drill" -> { launchDrill(px); msg("MEGA DRILL", P.color(), 0.9f); }
            case "wide" -> { wideT = Math.min(wideT + 18 * dur, 50); msg("WIDE RIG", P.color(), 0.8f); }
            case "magnet" -> { magT = Math.min(magT + 15 * dur, 50); msg("MAGNET SURGE", P.color(), 0.8f); }
            case "catch" -> { catchT = Math.min(catchT + 20 * dur, 60); msg("CATCH - CLICK TO RELEASE", P.color(), 1); }
            case "hull" -> { hull = Math.min(maxHull(), hull + 20); msg("HULL PATCH +20", P.color(), 0.8f); }
            default -> {}
        }
    }

    // ================================================================ mega drills
    private void launchDrill(float cx) {
        Drill d = new Drill();
        d.c = clamp(Math.round(cx + HALF - 0.5f), 1, COLS - 2);
        d.y = paddleY - 1.2f; d.row = R + 1; d.left = Math.round(16 * (1 + 0.2f * mr("dur")));
        drills.add(d); fx.sound(Fx.Sfx.DROP, 0);
    }
    private void updateDrills(float dt) {
        for (Iterator<Drill> it = drills.iterator(); it.hasNext(); ) {
            Drill d = it.next();
            d.v = Math.min(d.v + 30 * dt, 11); d.y -= d.v * dt;
            while (d.row <= Math.floor(-(d.y - 1.0f) + 0.5f) && d.left > 0) {
                for (int c = d.c - 1; c <= d.c + 1; c++) if (cell(d.row, c) != 0) destroyBlock(d.row, c, true, "drill");
                fx.sound(Fx.Sfx.CRUNCH, 0); d.row++; if (d.row > R + ZONE_OFF) d.left--;
            }
            shake = Math.max(shake, 0.2f);
            if (rnd() < 0.9) fx.burst(cellX(d.c), d.y - 1.1f, 3, 1, 0.6f, 0.2f, 7, 0.35f);
            if (d.left <= 0 || d.y < -(R + 34)) {
                it.remove();
                fx.burst(cellX(d.c), d.y, 40, 1, 0.6f, 0.2f, 9, 0.6f); fx.debris(cellX(d.c), d.y, 0x6a6058, 12); fx.sound(Fx.Sfx.BOOM, 0);
            }
        }
    }

    // ================================================================ wall panels
    public void syncPanels() {
        panels.clear();
        for (String kind : List.of("laser", "skimmer")) {
            int n = lv(kind) >= 2 ? 2 : lv(kind), home = kind.equals("laser") ? -1 : 1;
            for (int i = 0; i < n; i++) {
                Panel p = new Panel(kind, i == 0 ? home : -home);
                p.y = rigY - rnd(3, 12); p.ty = rigY - rnd(3, 12); p.wait = rnd(0, 1); p.cd = rnd(0.5f, 2);
                panels.add(p);
            }
        }
    }
    private void updatePanels(float dt) {
        for (Panel p : panels) {
            float x = p.side * (HALF + 0.3f); int L = lv(p.kind);
            if (p.wait > 0) p.wait -= dt;
            else {
                float d = p.ty - p.y, step = 3.5f * dt;
                if (Math.abs(d) <= step) { p.y = p.ty; p.wait = rnd(0.6f, 2.2f); p.ty = rigY - rnd(2.5f, 14); }
                else p.y += Math.signum(d) * step;
            }
            if (p.y > rigY - 2) p.y = rigY - 2;
            if (p.kind.equals("skimmer")) {
                float rad = 2.8f + (L >= 3 ? 2 : 0); p.pull = 0;
                for (Iterator<Loot> it = loot.iterator(); it.hasNext(); ) {
                    Loot l = it.next();
                    if (l.kind.equals("pu")) continue;                         // pods are yours to catch
                    float dx = x - l.x, dy = p.y - l.y, d = (float) Math.hypot(dx, dy);
                    if (d > rad) continue;
                    p.pull++;
                    float k = Math.min(1, 6 * dt); l.x += dx * k; l.y += dy * k; l.vy = 0; l.vx = 0;
                    if (rnd() < 0.5) fx.spark(lerp(l.x, x, rnd()), lerp(l.y, p.y, rnd()), 0.7f, 0, 0, 0.7f, 0.35f, 1, 0.2f);
                    if (d < 0.6f) { collect(l); stats.skimmed++; it.remove(); }
                }
            } else {
                p.cd -= dt * (L >= 3 ? 1.6f : 1);
                if (p.cd <= 0) {
                    p.cd = 2.4f; stats.laserShots++;
                    int row = Math.round(-p.y), maxHits = 3 + L, dmg = L >= 3 ? 2 : 1, hits = 0;
                    float endX = -x;
                    for (int i = 0; i < COLS; i++) {
                        int c = p.side < 0 ? i : COLS - 1 - i;
                        if (cell(row, c) == 0) continue;
                        damageBlock(row, c, dmg, true, "laser");
                        if (++hits >= maxHits) { endX = cellX(c); break; }
                    }
                    float y = -row; p.beamT = 0.18f; p.beamY = y; p.beamX0 = x; p.beamX1 = endX;
                    for (int s = 0; s < 16; s++) fx.spark(lerp(x, endX, rnd()), y + rnd(-0.1f, 0.1f), 0.6f, rnd(-1, 1), rnd(0, 2), 1, 0.3f, 0.15f, 0.3f);
                    fx.flash((x + endX) / 2, y, 2, 0.4f, 0.2f, 6, 0.18f);
                    fx.sound(Fx.Sfx.LASER, 0);
                }
            }
            p.beamT = Math.max(0, p.beamT - dt);
        }
    }

    // ================================================================ rockfall (score-gated)
    private void updateHazards(float dt) {
        for (Timer tm : new ArrayList<>(timers)) { tm.t[0] -= dt; if (tm.t[0] <= 0) { timers.remove(tm); tm.fn.run(); } }
        if (score >= ROCK_SCORE && dropping == null) {
            if (!rockAnnounced) { rockAnnounced = true; hazT = 4; msg("THE SHAFT GROANS - ROCKFALL", 0xff5a3c, 1.6f); }
            hazT -= dt;
            if (hazT <= 0) {
                hazT = (float) (Math.max(3, 15 - (score - ROCK_SCORE) / 8000.0) * rnd(0.7f, 1.3f) / Math.max(0.2, 1 + A("rock")));
                Hazard h = new Hazard();
                h.x = rnd(-HALF + 0.6f, HALF - 0.6f);
                h.warn = (float) (Math.max(0.75, 1.4 - R * 0.0025) * Math.max(0.4, 1 + A("warn")));
                hazards.add(h); fx.sound(Fx.Sfx.WARN, 0);
            }
        }
        for (Iterator<Hazard> it = hazards.iterator(); it.hasNext(); ) {
            Hazard h = it.next();
            h.age += dt;
            if (h.warn > 0) { h.warn -= dt; if (h.warn <= 0) { h.y = rigY + 9; h.falling = true; } continue; }
            h.vy += 45 * dt; h.y -= h.vy * dt;
            if (!h.passed && h.y <= paddleY + 0.6f) {
                h.passed = true;
                if (Math.abs(h.x - px) < padW / 2 + 0.3f) {
                    it.remove();
                    fx.burst(h.x, paddleY + 0.6f, 30, 0.8f, 0.7f, 0.6f, 7, 0.5f); fx.debris(h.x, paddleY + 0.5f, 0x5a5048, 8);
                    stats.stalHits++; shake = Math.max(shake, 0.7f); hullFlash = 0.7f; fx.sound(Fx.Sfx.HULL, 0); msg("ROCKFALL", 0xff5a3c, 0.7f);
                    if (damageHull(12, "boulder")) return;
                    continue;
                }
            }
            int r = Math.round(-h.y), c = (int) Math.floor(h.x + HALF);
            if (h.passed && c >= 0 && c < COLS && cell(r, c) != 0) {
                it.remove(); destroyBlock(r, c, true, "drill");
                fx.burst(h.x, h.y, 24, 0.8f, 0.7f, 0.6f, 6, 0.5f); fx.sound(Fx.Sfx.CRUNCH, 0); continue;
            }
            if (h.y < rigY - 30) it.remove();
        }
    }

    // ================================================================ wyrm bosses
    private int nextBossDepth() { return endless ? endlessNext : bossIdx < BOSSES.size() ? BOSSES.get(bossIdx).at() : Integer.MAX_VALUE; }
    private void spawnBoss() {
        int lap = endless ? (endlessNext - 360) / 120 : 0;
        BossDef def = endless ? BOSSES.get(Math.floorMod(lap - 1, BOSSES.size())) : BOSSES.get(bossIdx);
        float sc = 1 + lap * 0.5f;
        Boss B = new Boss();
        B.def = def; B.sc = sc; B.t = rnd(0, 6);
        for (int i = 0; i < def.seg(); i++) { Segment s = new Segment(); s.y = -(R + 20); s.hp = s.max = Math.round(def.segHp() * sc); B.segs.add(s); }
        B.x = rnd(-3, 3); B.y = -(R + 22);
        B.hp = B.max = Math.round(def.headHp() * sc);
        B.lungeT = def.lunge() * 0.7f;
        for (int i = 0; i < def.seg() * 8 + 10; i++) B.trail.addLast(new float[]{B.x, B.y - i * 0.1f});
        boss = B; pressureT = -1;
        msg(def.name() + " AWAKENS", 0xff5a3c, 2); fx.sound(Fx.Sfx.BOOM, 0); fx.sound(Fx.Sfx.GROWL, 0); shake = Math.max(shake, 1);
        for (int i = 0; i < 2; i++) { final int k = i; later(() -> spawnLoot(rnd(-4, 4), paddleY - 7, "pu", k == 0 ? "split" : rollPU()), 1 + i); }
    }
    private void updateBoss(float dt) {
        Boss B = boss;
        if (B == null) return;
        B.t += dt; B.age += dt; B.hitT = Math.max(0, B.hitT - dt);
        float base = -(R + 10), lim = HALF - 1.2f, tx, ty, sp = (4.2f + B.sc * 0.4f) * (B.armored() ? 1 : 0.65f);
        switch (B.phase) {
            case "rise" -> { tx = 0; ty = base; if (B.y > base - 1) B.phase = "roam"; sp = 7; }
            case "roam" -> {
                tx = (float) Math.sin(B.t * 0.7) * lim; ty = base + (float) Math.sin(B.t * 1.13) * 2.5f;
                Ball near = null; float nd = 1e9f;                       // hunt the nearest core
                for (Ball b : balls) if (!b.docked) { float d2 = (float) Math.hypot(b.x - B.x, b.y - B.y); if (d2 < nd) { nd = d2; near = b; } }
                if (near != null) { tx = lerp(tx, near.x, 0.6f); ty = Math.min(lerp(ty, near.y, 0.6f), paddleY - 4); }
                B.rubbleT -= dt;                                        // churn up rubble so the arena never empties
                if (B.rubbleT <= 0) { B.rubbleT = 1.6f; for (int i = 0; i < 3; i++) placeBlock(R + 6 + rng.nextInt(10), rng.nextInt(COLS), rnd() < 0.15 ? GOLD : STONE); }
                B.lungeT -= dt;
                if (B.lungeT <= 0) { B.phase = "tele"; B.tele = 1.5f; fx.sound(Fx.Sfx.GROWL, 0); }
            }
            case "tele" -> { tx = B.x; ty = B.y; sp = 1; B.tele -= dt; if (B.tele <= 0) { B.phase = "lunge"; B.lungeTime = 0; B.lx = px; } }
            case "lunge" -> {
                tx = B.lx; ty = paddleY - 0.4f; sp = 10; B.lungeTime += dt;
                if (Math.abs(B.x - px) < padW / 2 + 0.5f && B.y > paddleY - 1.1f) {      // the bite
                    shake = 1; hullFlash = 0.8f; fx.sound(Fx.Sfx.HULL, 0); fx.burst(B.x, B.y, 40, 1, 0.4f, 0.3f, 8, 0.6f); msg("THE WYRM BITES", 0xff5a3c, 0.9f);
                    B.phase = "retreat";
                    if (damageHull(12, "wyrm")) return;
                }
                if (B.lungeTime > 1.6f || B.y > paddleY - 0.5f) B.phase = "retreat";
            }
            default -> { tx = B.x; ty = base; sp = 6; if (B.y < base + 0.5f) { B.phase = "roam"; B.lungeT = B.def.lunge() * rnd(0.8f, 1.2f); } }
        }
        float dx = tx - B.x, dy = ty - B.y, d = (float) Math.hypot(dx, dy);
        if (d > 0.01f) { float st = Math.min(d, sp * dt); B.x += dx / d * st; B.y += dy / d * st; B.dir = (float) Math.atan2(dy, dx); }
        B.x = clamp(B.x, -lim - 0.6f, lim + 0.6f);
        float[] last = B.trail.peekFirst();
        if (Math.hypot(B.x - last[0], B.y - last[1]) > 0.1) { B.trail.addFirst(new float[]{B.x, B.y}); if (B.trail.size() > B.segs.size() * 8 + 12) B.trail.removeLast(); }
        float[][] tr = B.trail.toArray(new float[0][]);
        for (int i = 0; i < B.segs.size(); i++) {
            Segment s = B.segs.get(i); float[] p = tr[Math.min(tr.length - 1, (i + 1) * 8)];
            s.x = p[0]; s.y = p[1]; s.hitT = Math.max(0, s.hitT - dt);
        }
        int hr = Math.round(-B.y), hc = (int) Math.floor(B.x + HALF);           // the head bores through rock
        for (int r = hr - 1; r <= hr + 1; r++) for (int c = hc - 1; c <= hc + 1; c++)
            if (c >= 0 && c < COLS && cell(r, c) != 0 && Math.hypot(cellX(c) - B.x, -r - B.y) < 1.0) destroyBlock(r, c, false, null);
        for (Iterator<Loot> it = loot.iterator(); it.hasNext(); ) {             // it eats pods that drift past
            Loot l = it.next();
            if (l.kind.equals("pu") && Math.hypot(l.x - B.x, l.y - B.y) < 1.1) {
                it.remove();
                for (Segment s : B.segs) if (!s.alive) { s.alive = true; s.hp = s.max; break; }
                msg("THE WYRM DEVOURS YOUR POD", 0xff7a4a, 1); fx.sound(Fx.Sfx.DEVOUR, 0);
            }
        }
    }
    /** Core vs boss: returns true if the core bounced off a part of it. */
    private boolean hitBoss(Ball b) {
        Boss B = boss;
        List<Object[]> parts = new ArrayList<>();
        parts.add(new Object[]{B.x, B.y, 0.95f, null});
        for (Segment s : B.segs) if (s.alive) parts.add(new Object[]{s.x, s.y, 0.62f, s});
        for (Object[] p : parts) {
            float pxp = (float) p[0], pyp = (float) p[1], pr = (float) p[2];
            float dx = b.x - pxp, dy = b.y - pyp, d = (float) Math.hypot(dx, dy);
            if (d >= BR + pr || d < 1e-5f) continue;
            float n0 = dx / d, n1 = dy / d, dot = b.vx * n0 + b.vy * n1;
            if (dot < 0) { b.vx -= 2 * dot * n0; b.vy -= 2 * dot * n1; }
            b.x = pxp + n0 * (BR + pr + 0.01f); b.y = pyp + n1 * (BR + pr + 0.01f);
            int dmg = 1 + b.up("str") + (b.evo("lance") ? 2 : 0) + (b.slam > 0 ? 2 : 0); b.slam = 0;
            combo++; bestCombo = Math.max(bestCombo, combo); stats.lastProg = stats.t;
            if (p[3] == null) {
                if (B.armored()) { fx.burst(b.x, b.y, 8, 0.8f, 0.8f, 0.9f, 4, 0.25f); fx.sound(Fx.Sfx.CLANG, 0); return true; }
                B.hp -= dmg; B.hitT = 0.15f; fx.burst(b.x, b.y, 16, 1, 0.4f, 0.3f, 6, 0.35f); fx.sound(Fx.Sfx.HIT_HARD, combo);
                if (B.hp <= 0) killBoss();
            } else {
                Segment s = (Segment) p[3];
                s.hp -= dmg; s.hitT = 0.15f; fx.burst(b.x, b.y, 10, 1, 0.7f, 0.4f, 5, 0.3f); fx.sound(Fx.Sfx.HIT_HARD, combo);
                if (s.hp <= 0) {
                    s.alive = false; fx.debris(s.x, s.y, 0x5a4a40, 10); fx.sound(Fx.Sfx.CRUNCH, 0);
                    spawnLoot(s.x, s.y, "gold", null);
                    if (!B.armored()) msg("ARMOR BROKEN - STRIKE THE HEAD", 0xffc43c, 1.3f);
                }
            }
            return true;
        }
        return false;
    }
    private void killBoss() {
        Boss B = boss; boss = null; bossKills++; stats.bossKills++;
        fx.sound(Fx.Sfx.BOOM, 0); shake = 1.2f; fx.flash(B.x, B.y, 2.5f, 1.6f, 0.8f, 16, 0.8f);
        fx.burst(B.x, B.y, 160, 1, 0.6f, 0.25f, 14, 1.1f); fx.debris(B.x, B.y, 0x6a5a48, 30);
        for (int i = 0; i < 10; i++) spawnLoot(B.x + rnd(-1, 1), B.y + rnd(-1, 1), "gold", null);
        for (int i = 0; i < 3; i++) spawnLoot(B.x, B.y, "aether", null);
        for (int i = 0; i < 2; i++) spawnLoot(B.x, B.y, "pu", rollPU());
        if (endless) endlessNext += 120; else bossIdx++;
        if (B.def.last() && !endless) { won = true; later(this::openVictory, 2.2f); msg("THE HEART IS BROKEN", 0x7affd4, 2.2f); return; }
        msg(B.def.name() + " SLAIN - PATH CLEAR", 0x9cff6a, 1.6f);
        pressureT = pressureMax();
    }
    private void openVictory() {
        if (state != State.PLAY) return;
        state = State.VICTORY; save.rec.wins++; persist();
    }
    public void closeVictory(boolean goEndless) {
        if (state != State.VICTORY) return;
        if (goEndless) { endless = true; endlessNext = R * M_PER_ROW + 120; state = State.PLAY; pressureT = pressureMax(); msg("ENDLESS DESCENT", 0xff9a3c, 1.4f); }
        else { bankedTotal += cargo; banked += cargo; cargo = 0; state = State.PLAY; gameOver(true); }
    }

    // ================================================================ descent
    public float zoneCleared() {
        int solid = 0, rem = 0;
        for (int r = R + ZONE_OFF; r < R + ZONE_OFF + ZONE; r++) {
            Row row = rows.get(r);
            if (row == null) continue;
            solid += row.solid0;
            for (int c = 0; c < COLS; c++) if (row.t[c] != 0) rem++;
        }
        return solid > 0 ? 1 - (float) rem / solid : 1;
    }
    public void startDrop() {
        if (dropping != null || pressureT < 0 || state != State.PLAY) return;
        Dropping d = new Dropping();
        d.from = rigY; d.to = -(R + DROP);
        dropping = d; pressureT = -1; fx.sound(Fx.Sfx.DROP, 0); shake = Math.max(shake, 0.5f);
        List<Loot> surge = new ArrayList<>(loot); loot.clear();
        for (Loot l : surge) collect(l);                              // magnet surge grabs loose ore & pods
        Ball best = null;
        for (Ball b : balls) if (best == null || b.total() > best.total()) best = b;
        d.carry = best == null ? new HashMap<>() : new HashMap<>(best.up);   // best core's upgrades survive
        balls.clear();
        msg("CLAMPS RELEASED", 0xffb347, 0.9f);
    }
    private void updateDrop(float dt) {
        Dropping d = dropping;
        d.t = Math.min(1, d.t + dt / 1.3f);
        float e = d.t < 0.5f ? 2 * d.t * d.t : 1 - (float) Math.pow(-2 * d.t + 2, 2) / 2;
        rigY = lerp(d.from, d.to, e);
        shake = Math.max(shake, 0.15f);
        int want = (int) Math.floor(d.t * ZONE + 0.001f);
        while (d.crushed < want) {                                    // crush leftovers of the old zone
            int r = R + ZONE_OFF + d.crushed; Row row = rows.get(r);
            if (row != null) { for (int c = 0; c < COLS; c++) if (row.t[c] != 0) destroyBlock(r, c, false, null); fx.sound(Fx.Sfx.CRUNCH, 0); }
            d.crushed++;
        }
        if (d.t >= 1) {
            stats.drops++; if (stats.firstDrop < 0) stats.firstDrop = stats.t;
            R += DROP; rigY = -R;
            for (int i = 0; i < lv("drillbay"); i++) later(() -> launchDrill(rnd(-HALF + 1.5f, HALF - 1.5f)), 0.6f + i * 0.8f);
            Map<String, Integer> carry = d.carry; dropping = null;
            paddleY = rigY - drop;
            ensureRows(); dockNew(carry);
            if (boss == null && R * M_PER_ROW >= nextBossDepth()) spawnBoss();
            if (R % JUNCTION_EVERY == 0) openJunction();
        }
    }

    // ================================================================ hull / run end
    private boolean damageHull(float amt, String cause) {
        hull -= (float) (amt * (1 - 0.1 * mr("brace")) * Math.max(0.2, 1 + A("dmg")));
        if (hull > 0) return false;
        if ((mr("revive") > 0 || A("revive") > 0) && !reviveUsed) {
            reviveUsed = true; hull = maxHull() * (mr("revive") > 0 ? 0.5f : 0.3f);
            msg("EMERGENCY BALLAST", 0xff8ab0, 1.4f); fx.sound(Fx.Sfx.POD, 0); return false;
        }
        stats.death = cause; gameOver(false); return true;
    }
    private void hullHit() {
        stats.hullHits++; fx.sound(Fx.Sfx.HULL, 0); shake = 1.0f; hullFlash = 1;
        fx.burst(px, rigY + 0.8f, 60, 1, 0.5f, 0.2f, 9, 0.8f); fx.debris(px, rigY + 0.6f, 0x6a5040, 10);
        if (damageHull(25, "last core")) return;
        msg("HULL BREACH - CORE UPGRADES LOST", 0xff5a3c, 1.2f); dockNew(starterUp());
    }
    private void gameOver(boolean win) {
        state = State.OVER; if (!win) hull = 0;
        int depth = R * M_PER_ROW;
        List<MetaItem> before = META.stream().filter(this::metaUnlocked).toList();
        List<String[]> parts = List.of(
            new String[]{"banked x0.025", "" + Math.round(bankedTotal * 0.025)}, new String[]{"salvage x0.01", "" + Math.round(cargo * 0.01)},
            new String[]{"depth x0.5", "" + Math.round(depth * 0.5)}, new String[]{"wyrms x150", "" + bossKills * 150});
        double sum = 0; for (String[] p : parts) sum += Long.parseLong(p[1]);
        long earned = Math.round(sum * (1 + 0.1 * mr("greed")) * Math.max(0.1, 1 + A("scrip")) * (win || won ? 1.5 : 1));
        SaveData.Records rc = save.rec;
        save.scrip += earned; rc.runs++; rc.totalScrip += earned; rc.bestDepth = Math.max(rc.bestDepth, depth); rc.bestScore = Math.max(rc.bestScore, score + bankedTotal);
        rc.fission += stats.pods.getOrDefault("split", 0); rc.maxCores = Math.max(rc.maxCores, stats.maxCores); rc.maxBanksRun = Math.max(rc.maxBanksRun, banksRun);
        List<String> fresh = new ArrayList<>();
        for (MetaItem m : META) if (metaUnlocked(m) && !before.contains(m)) fresh.add(m.name());
        lastRun = new LastRun(win, depth, earned, parts, fresh, stats.t);
        if (save.pending == null) save.pending = drawCard();
        persist();
    }

    // ================================================================ junction & workshop
    private void openJunction() { state = State.JUNCTION; }
    public void closeJunction(boolean bank) {
        if (state != State.JUNCTION) return;
        if (bank) {
            banksRun++; banked += cargo; bankedTotal += cargo; cargo = 0; cargoMult = 1; hull = Math.min(maxHull(), hull + 25);
            msg("CARGO HOISTED", 0xffc43c, 1); fx.sound(Fx.Sfx.POD, 0);
        } else { cargoMult += 0.5f; msg(String.format("PUSHING ON  x%.1f", cargoMult), 0xff7a4a, 1.2f); fx.sound(Fx.Sfx.CLAMP, 0); }
        state = State.PLAY;
        if (bank) openShop();
    }
    private List<String> eligibleOffers() {
        List<String> out = new ArrayList<>();
        for (ShopItem it : SHOP) if (lv(it.key()) < it.max() && !banished.contains(it.key()) && !offers.contains(it.key())) out.add(it.key());
        return out;
    }
    private String drawOffer() { List<String> e = eligibleOffers(); return e.isEmpty() ? null : e.get(rng.nextInt(e.size())); }
    private void rollOffers() { offers.clear(); for (int i = 0; i < 4; i++) offers.add(drawOffer()); }
    private void openShop() { state = State.SHOP; rerollPaid = 0; rollOffers(); }
    private void applyStarter(String k) {
        later(() -> { for (Ball b : new ArrayList<>(balls)) checkEvo(b); }, 0);
        for (Ball b : balls) {
            int room = MAX_UP - b.total(), cur = b.up(k);
            if (room > 0 && cur < lv(k)) { b.up.put(k, Math.min(lv(k), cur + room)); if (k.equals("pierce")) b.pierceLeft = b.up("pierce"); restyle(b); }
        }
    }
    public void buyOffer(int i) {
        if (state != State.SHOP || i < 0 || i >= offers.size()) return;
        String k = offers.get(i);
        if (k == null) return;
        ShopItem it = shopItem(k); long c = shopCost(it);
        if (banked < c || lv(k) >= it.max()) { fx.sound(Fx.Sfx.DENY, 0); return; }
        banked -= c; shop.merge(k, 1, Integer::sum); fx.sound(Fx.Sfx.BUY, 0);
        if (k.equals("armor")) hull += 20;
        if (STARTER_KEYS.contains(k)) applyStarter(k);
        if (k.equals("skimmer") || k.equals("laser")) syncPanels();
        offers.set(i, null); offers.set(i, drawOffer());
    }
    public void repair() {
        if (state != State.SHOP) return;
        if (hull >= maxHull() || banked < REPAIR_COST) { fx.sound(Fx.Sfx.DENY, 0); return; }
        banked -= REPAIR_COST; hull = Math.min(maxHull(), hull + 40); fx.sound(Fx.Sfx.BUY, 0);
    }
    public void reroll() {
        if (state != State.SHOP) return;
        long c = rerollCost();
        if (banked < c) { fx.sound(Fx.Sfx.DENY, 0); return; }
        if (rerollsFree > 0) rerollsFree--; else { banked -= c; rerollPaid++; }
        rollOffers(); fx.sound(Fx.Sfx.TICK, 0);
    }
    public void banishOffer(int i) {
        if (state != State.SHOP || banishLeft <= 0 || i < 0 || i >= offers.size() || offers.get(i) == null) return;
        banished.add(offers.get(i)); banishLeft--; offers.set(i, null); offers.set(i, drawOffer()); fx.sound(Fx.Sfx.CRUNCH, 0);
    }
    public void closeShop() { if (state == State.SHOP) { stats.unspent.add(banked); stats.shopSeen++; state = State.PLAY; msg("DESCEND", 0xff9a3c, 0.8f); } }

    // ================================================================ foundry
    public void openFoundry() {
        if (state != State.TITLE && state != State.OVER) return;
        if (state == State.OVER && save.pending != null) return;      // resolve the card first
        prevState = state; state = State.FOUNDRY; refundArmed = false; newGameArmed = false;
    }
    public void closeFoundry() { if (state == State.FOUNDRY) state = prevState; }
    public void metaBuy(String k) {
        MetaItem m = metaItem(k);
        if (m == null || !metaUnlocked(m) || mr(k) >= m.max()) { fx.sound(Fx.Sfx.DENY, 0); return; }
        long c = metaCost(m);
        if (save.scrip < c) { fx.sound(Fx.Sfx.DENY, 0); return; }
        save.scrip -= c; save.spent += c; save.bought++; save.ranks.merge(k, 1, Integer::sum); save.rec.everBought = true;
        fx.sound(Fx.Sfx.BUY, 0); persist();
    }
    public void metaRefund() {
        if (!refundArmed) { refundArmed = true; return; }
        save.scrip += save.spent; save.spent = 0; save.bought = 0; save.ranks.clear(); refundArmed = false;
        fx.sound(Fx.Sfx.CRUNCH, 0); persist();
    }
    public void newGame() {
        if (!newGameArmed) { newGameArmed = true; return; }
        save = new SaveData(); newGameArmed = false; persist(); msg("NEW GAME", 0xff9a3c, 1);
    }

    // ================================================================ run lifecycle
    private void reset() {
        rows.clear(); balls.clear(); loot.clear(); drills.clear(); hazards.clear(); timers.clear(); boss = null;
        seed = rng.nextInt();
        R = 0; rigY = 0; drop = dropV = dropTarget = 0; paddleY = 0; px = pxTarget = 0; firstRock = ZONE_OFF;
        shop.clear(); bankedTotal = 0; magT = catchT = wideT = 0; zoneLast = -1; zoneIdle = 0;
        cargo = 0; cargoMult = 1; score = 0; combo = 0; bestCombo = 0; pressureT = -1; dropping = null;
        bossIdx = 0; endless = false; endlessNext = 0; bossKills = 0; won = false; rockAnnounced = false; hazT = 8;
        stats = new Stats(); banksRun = 0; reviveUsed = false; offers.clear(); banished.clear();
        rerollsFree = mr("reroll"); banishLeft = mr("banish"); rerollPaid = 0;
        syncPanels();
        computeArc();
        banked = Math.max(0, 100L * mr("stake") + Math.round(A("credits")));
        hull = maxHull(); padW = basePadW();
        Map<String, Integer> su = starterUp();
        if (mr("seed") > 0 && su.getOrDefault("split", 0) == 0) { int t = 0; for (int v : su.values()) t += v; if (t < MAX_UP) su.put("split", 1); }
        ensureRows(); dockNew(su);
        int extra = (mr("twin") > 0 ? 1 : 0) + (int) Math.max(0, A("cores"));
        for (int i = 0; i < extra; i++) dockNew(starterUp());
    }
    /** Start a descent. Refuses (returns false) while a tarot card is still waiting to be resolved. */
    public boolean start() {
        if (save.pending != null) { state = State.OVER; return false; }
        reset(); state = State.PLAY; msg("DESCEND", 0xff9a3c, 1);
        return true;
    }
    public void togglePause() {
        if (state == State.PLAY) state = State.PAUSED;
        else if (state == State.PAUSED) state = State.PLAY;
    }
    /** Ends the run right now (used by tests when a time limit is hit). */
    public void abandon() { if (state != State.OVER) gameOver(false); }
    public void giveShop(String k, int n) { shop.put(k, n); syncPanels(); }
    /** Open a menu directly (screenshots and testing): shop, junction, foundry, over, victory. */
    public void debugScreen(String which) {
        if (state == State.TITLE) start();
        switch (which) {
            case "shop" -> { banked = 2400; openShop(); }
            case "junction" -> { cargo = 840; openJunction(); }
            case "foundry" -> { save.scrip = 1500; state = State.TITLE; openFoundry(); }
            case "over" -> { if (save.hand.isEmpty()) { save.hand.add(new Card(15, false)); save.hand.add(new Card(3, true)); } gameOver(false); }
            case "victory" -> state = State.VICTORY;
            default -> {}
        }
    }
    /** Jump straight to wyrm {@code i} in its biome (screenshots and testing). */
    public void debugBossScene(int i) {
        rows.clear(); balls.clear(); loot.clear(); boss = null;
        R = Math.round(BOSSES.get(i).at() / (float) M_PER_ROW / DROP) * DROP; rigY = -R; firstRock = R + ZONE_OFF; bossIdx = i;
        ensureRows(); paddleY = rigY - drop; dockNew(new HashMap<>());
        spawnBoss();
        boss.y = -(R + 10);
        boss.trail.clear();
        for (int k = 0; k < boss.segs.size() * 8 + 10; k++) boss.trail.addLast(new float[]{boss.x - k * 0.06f, -(R + 10) - k * 0.05f});
    }

    // ================================================================ input
    /** Pointer position in normalised viewport coordinates (0..1, origin top-left). */
    public void pointer(float fx, float fy) {
        pxTarget = (fx - 0.5f) * POINTER_SPAN_X; usingMouse = true; mouseFy = fy;
    }
    public void key(String name, boolean down) { if (down) keys.add(name); else keys.remove(name); }

    // ================================================================ frame
    public void update(float dt) {
        if (state != State.PLAY) return;
        time += dt;
        // ---- rig: horizontal
        wideT = Math.max(0, wideT - dt); catchT = Math.max(0, catchT - dt); magT = Math.max(0, magT - dt);
        padW = lerp(padW, basePadW() + (wideT > 0 ? 1.8f : 0), 1 - (float) Math.exp(-dt * 8));
        float lim = HALF - padW / 2;
        if (keys.contains("left")) { pxTarget = px - 54 * dt; usingMouse = false; }
        if (keys.contains("right")) { pxTarget = px + 54 * dt; usingMouse = false; }
        if (bot != null) botStep(dt);
        stats.t += dt;
        if (stats.t - stats.lastProg > 4 && dropping == null) stats.idle += dt;
        if (pressureT >= 0) stats.readyWait += dt;
        stats.coreT[balls.size() <= 1 ? 0 : balls.size() <= 3 ? 1 : balls.size() <= 7 ? 2 : 3] += dt;
        pxTarget = clamp(pxTarget, -lim, lim);
        float prevPx = px;
        px = clamp(lerp(px, pxTarget, 1 - (float) Math.exp(-dt * (usingMouse ? 30 : 20))), -lim, lim);
        rigVel = (px - prevPx) / Math.max(dt, 1e-4f);
        // ---- rig: vertical spring
        if (keys.contains("up")) { dropTarget -= 6 * dt; usingMouse = false; }
        if (keys.contains("down")) { dropTarget += 6 * dt; usingMouse = false; }
        if (usingMouse && mouseFy >= 0) dropTarget = rigY - ((rigY - CAM_TARGET_BELOW_RIG) - (mouseFy - 0.5f) * 2 * CAM_HALF_HEIGHT) - 0.3f;
        dropTarget = clamp(dropTarget, 0, reach());
        float k = springK(), cDamp = 2 * (float) Math.sqrt(k) * 0.45f;   // underdamped: it bobs
        dropV += ((dropTarget - drop) * k - dropV * cDamp) * dt;
        drop = clamp(drop + dropV * dt, -0.25f, reach() + 0.35f);

        if (dropping != null) updateDrop(dt);
        paddleY = rigY - drop;
        updateDrills(dt);
        updateHazards(dt);
        if (state != State.PLAY) return;
        updatePanels(dt);
        updateBoss(dt);
        if (state != State.PLAY) return;

        // ---- cores (fixed substeps so they can't tunnel)
        int n = Math.max(1, (int) Math.ceil(dt / (1f / 240))); float h = dt / n;
        for (int s = 0; s < n; s++) for (int i = 0; i < balls.size(); i++) { Ball b = balls.get(i); if (!b.docked && !b.lost) stepBall(b, h); }
        for (Ball b : new ArrayList<>(balls)) {
            if (b.docked) {
                b.x = px + b.holdX; b.y = paddleY - 0.55f - BR - 0.02f;
                if (b.held && (b.holdT += dt) > holdLimit()) launch();
            } else {
                b.boost = Math.max(1, b.boost - dt * 0.4f);
                float sp = curSpeed() * b.boost;
                if (Math.abs(b.vy) < 0.3f * sp) b.vy = (b.vy < 0 ? -1 : 1) * 0.3f * sp;     // never near-horizontal
                float s = (float) Math.hypot(b.vx, b.vy); if (s == 0) s = 1;
                b.vx *= sp / s; b.vy *= sp / s;
            }
            if (b.lost) {
                balls.remove(b); fx.burst(b.x, rigY + 0.8f, 20, 1, 0.6f, 0.2f, 6, 0.4f);
                if (!balls.isEmpty() && dropping == null) {
                    hullFlash = 0.35f; shake = Math.max(shake, 0.3f); stats.chips++;
                    if (damageHull((float) (4 * Math.max(0, 1 + A("chip"))), "missed cores")) return;
                }
            }
        }
        if (dropping == null && balls.isEmpty()) hullHit();
        if (state != State.PLAY) return;
        stats.maxCores = Math.max(stats.maxCores, balls.size());
        for (Ball b : balls) stats.maxStack = Math.max(stats.maxStack, b.total());

        // ---- loot rises to the magnetic hopper
        int mg = magnetLevel();
        for (Iterator<Loot> it = loot.iterator(); it.hasNext(); ) {
            Loot l = it.next();
            l.vy = Math.min(l.vy + 9 * dt, 7.5f);
            if (paddleY - l.y < 5 + mg * 1.6f) l.vx += (px - l.x) * (1.1f + mg * 0.8f) * dt;
            if (mg > 0 && paddleY - l.y < 2.5f + mg) l.vy += mg * 1.5f * dt;
            l.vx *= 0.985f; l.x = clamp(l.x + l.vx * dt, -HALF + 0.2f, HALF - 0.2f); l.y += l.vy * dt;
            if (l.y >= paddleY - 0.6f && l.y < paddleY + 0.2f && Math.abs(l.x - px) <= padW / 2 + 0.35f + mg * 0.12f) { it.remove(); collect(l); }
            else if (l.y > paddleY + 1.2f) { it.remove(); fx.burst(l.x, l.y, 6, 0.5f, 0.4f, 0.3f, 2, 0.3f); }
        }

        // ---- structural clearance -> clamps (locked while a wyrm lives)
        float cl = zoneCleared();
        if (dropping == null && boss == null) {
            if (Math.abs(cl - zoneLast) > 1e-6f) { zoneLast = cl; zoneIdle = 0; } else zoneIdle += dt;
            if (pressureT < 0 && cl < CLEAR_REQ && cl >= 0.45f && zoneIdle > 20) { pressureT = pressureMax(); fx.sound(Fx.Sfx.CLAMP, 0); msg("STRUCTURE SETTLING - CLAMPS READY", 0x9cff6a, 1.4f); }
            if (cl >= CLEAR_REQ && pressureT < 0) { pressureT = pressureMax(); fx.sound(Fx.Sfx.CLAMP, 0); msg("CLAMPS READY", 0x9cff6a, 1.1f); }
            if (pressureT >= 0) {
                pressureT -= dt;
                if (pressureT < 6) shake = Math.max(shake, 0.12f);
                if (pressureT <= 0) startDrop();
            }
        }
        rigKick = Math.max(0, rigKick - dt);
    }

    /** Decay of purely visual values; call every frame even while paused/menus. */
    public void updateVisuals(float dt) {
        shake = Math.max(0, shake - dt * 2.2f);
        hullFlash = Math.max(0, hullFlash - dt * 2.5f);
    }

    // ================================================================ simulated player (logic tests)
    private void botStep(float dt) {
        Bot bt = bot; float s = bt.skill, pBot = paddleY - 0.55f;
        Ball tgt = null; float tBest = 1e9f;
        for (Ball b : balls) if (!b.docked && b.vy > 0) { float t = (pBot - BR - b.y) / b.vy; if (t >= 0 && t < tBest) { tBest = t; tgt = b; } }
        if (tgt != null && tgt != bt.tgt) { bt.tgt = tgt; bt.seen = 0; bt.err = (float) rng.nextGaussian() * (1 - s) * (1.2f + 0.4f * tBest); bt.aim = rnd(-0.6f, 0.6f) * padW / 2 * s; }
        bt.seen += dt;
        float react = 0.12f + 0.35f * (1 - s);
        Float goal = null;
        if (tgt != null && tBest < 1.6f && bt.seen >= react) {
            float L = HALF - BR, w = 2 * L;
            float u = (((tgt.x + tgt.vx * tBest + L) % (2 * w)) + 2 * w) % (2 * w); if (u > w) u = 2 * w - u;
            goal = u - L + bt.err - bt.aim;
        } else if (!(tgt != null && tBest < 1.6f)) {
            Loot lb = null; float ls = -1e9f;
            for (Loot l : loot) { float sc = (l.kind.equals("pu") ? 5 : 0) + l.y; if (l.y > paddleY - 6 && sc > ls) { ls = sc; lb = l; } }
            goal = lb != null ? Float.valueOf(lb.x) : tgt != null ? Float.valueOf(tgt.x) : null;
        }
        List<Float> threats = new ArrayList<>();
        for (Hazard hz : hazards) if (hz.age > react) threats.add(hz.x);
        if (boss != null && (boss.phase.equals("tele") || boss.phase.equals("lunge"))) threats.add(boss.phase.equals("tele") ? px : boss.lx);
        for (float hx : threats) {
            float g = goal != null ? goal : pxTarget, gap = padW / 2 + 0.7f;
            if (Math.abs(g - hx) < gap) goal = hx + (g >= hx ? gap : -gap) * (Math.abs(hx + gap) > HALF - padW / 2 ? -1 : 1);
        }
        if (goal != null) { float mv = (10 + 25 * s) * dt; pxTarget += clamp(goal - pxTarget, -mv, mv); }
        if (balls.stream().anyMatch(b -> b.docked)) { bt.lt += dt; if (bt.lt > 0.4f) { launch(); bt.lt = 0; } }
        if (pressureT >= 0 && dropping == null && loot.isEmpty() && pressureT < pressureMax() - 4) startDrop();
    }
}
