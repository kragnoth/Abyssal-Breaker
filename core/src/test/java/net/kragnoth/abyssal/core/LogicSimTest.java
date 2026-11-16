package net.kragnoth.abyssal.core;

import net.kragnoth.abyssal.core.Defs.MetaItem;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Logic-only playthroughs: a bot with human-ish reaction time and aim error plays the real simulation,
 * spending scrip in the Foundry and resolving tarot cards between runs. Fails the build on any exception,
 * soft-lock (60 s without breaking a block or hitting a boss), or broken invariant.
 */
class LogicSimTest {
    static final float DT = 1 / 60f;

    record RunResult(String end, float minutes, int depth, boolean won, int bossKills, int evos, int maxStack, long scrip) {}

    static RunResult playOne(Game g, float skill, float maxMinutes) {
        assertTrue(g.start(), "a pending tarot card blocked the run");
        g.bot = new Game.Bot(skill);
        float t = 0;
        String end = "timeout";
        while (t < maxMinutes * 60) {
            g.update(DT); g.updateVisuals(DT); t += DT;
            switch (g.state) {
                case OVER -> { end = g.won ? "won" : "died: " + g.stats.death; t = Float.MAX_VALUE; }
                case JUNCTION -> g.closeJunction(true);
                case VICTORY -> g.closeVictory(false);
                case SHOP -> {
                    for (int guard = 0; guard < 40; guard++) {
                        if (g.hull < g.maxHull() * 0.5f && g.banked >= Defs.REPAIR_COST) { g.repair(); continue; }
                        int pick = -1; long pc = Long.MAX_VALUE;
                        for (int i = 0; i < g.offers.size(); i++) {
                            String k = g.offers.get(i);
                            if (k == null) continue;
                            long c = g.shopCost(Defs.shopItem(k));
                            if (c <= g.banked && c < pc) { pc = c; pick = i; }
                        }
                        if (pick < 0) break;
                        g.buyOffer(pick);
                    }
                    g.closeShop();
                }
                default -> {}
            }
            if (g.state == Game.State.PLAY && g.stats.t - g.stats.lastProg > 60) { end = "STUCK"; break; }
            for (var b : g.balls) assertTrue(b.total() <= Defs.MAX_UP, "core exceeded the upgrade cap");
            assertTrue(g.balls.size() <= Defs.MAX_BALLS, "too many cores");
        }
        if (!end.equals("STUCK") && g.state != Game.State.OVER) g.abandon();
        RunResult rr = new RunResult(end, g.stats.t / 60f, g.lastRun == null ? 0 : g.lastRun.depthM(), g.won, g.bossKills, g.stats.evos, g.stats.maxStack,
            g.lastRun == null ? 0 : g.lastRun.earned());
        // between runs: tarot (keep strong cards, else pass for scrip) and Foundry (cheapest unlocked rank first)
        if (g.save.pending != null) {
            long v = g.cardValue(g.save.pending);
            g.tarotChoice(g.save.hand.size() < 3 && v >= 140 ? "keep" : "pass");
        }
        for (int guard = 0; guard < 60; guard++) {
            MetaItem m = Defs.META.stream().filter(g::metaUnlocked).filter(x -> g.mr(x.key()) < x.max())
                .min(Comparator.comparingLong(g::metaCost)).orElse(null);
            if (m == null || g.metaCost(m) > g.save.scrip) break;
            g.metaBuy(m.key());
        }
        return rr;
    }

    @Test
    void noiseMatchesBrowserBuild() {
        assertEquals(0.3148280455066888, Mth.hash(1, 2, 12345), 1e-12);
        assertEquals(0.23209149814958022, Mth.hash(-7, 33, 12345), 1e-12);
        assertEquals(0.47789453502132934, Mth.fbm(3.3, 17.6, 12345), 1e-12);
        assertEquals(0.2849511385378229, Mth.fbm(-2.2, 140.9, 12345), 1e-12);
        assertEquals(0.7421734756189803, Mth.hash(5, 9, -987654321), 1e-12);
        assertEquals(0.24981118193057922, Mth.fbm(12.5, 3.25, -987654321), 1e-12);
    }

    @Test
    void chainedSessionPlaysCleanly() {
        Game g = new Game(Fx.NONE, SaveStore.memory(), 42);
        int runs = 30, wins = 0, stuck = 0, evoRuns = 0, kills = 0;
        float minutes = 0;
        for (int r = 0; r < runs; r++) {
            float skill = 0.3f + 0.25f * r / (runs - 1);                   // the player improves over the session
            RunResult rr = playOne(g, skill, 30);
            System.out.printf(Locale.ROOT, "run %2d skill %.2f  %-18s %5.1f min %4d m  wyrms %d  evolutions %d  scrip +%d%n",
                r, skill, rr.end, rr.minutes, rr.depth, rr.bossKills, rr.evos, rr.scrip);
            if (rr.won) wins++;
            if (rr.end.equals("STUCK")) stuck++;
            if (rr.evos > 0) evoRuns++;
            kills += rr.bossKills; minutes += rr.minutes;
        }
        System.out.printf(Locale.ROOT, "SESSION: %d runs, %d wins, %d wyrms slain, %d runs with an evolution, avg %.1f min, Foundry ranks %d%n",
            runs, wins, kills, evoRuns, minutes / runs, g.save.ranks.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(0, stuck, "soft-locked runs");
        assertTrue(g.save.rec.runs == runs, "every run should have been recorded");
    }

    @Test
    void forcedUpgradesAndPanelsDoNotBreak() {
        Game g = new Game(Fx.NONE, SaveStore.memory(), 7);
        for (int r = 0; r < 6; r++) {
            assertTrue(g.start());
            g.bot = new Game.Bot(0.6f);
            for (String k : new String[]{"laser", "skimmer", "catch", "drillbay", "blast", "arc", "pierce", "str"}) g.giveShop(k, 3);
            for (int i = 0; i < 60 * 60 * 8 && g.state != Game.State.OVER; i++) {
                g.update(DT);
                if (g.state == Game.State.JUNCTION) g.closeJunction(r % 2 == 0);
                if (g.state == Game.State.SHOP) g.closeShop();
                if (g.state == Game.State.VICTORY) g.closeVictory(true);
            }
            if (g.state != Game.State.OVER) g.abandon();
            g.tarotChoice("pass");
        }
    }

    @Test
    void saveRoundTrips() throws Exception {
        var dir = java.nio.file.Files.createTempDirectory("abyssal");
        SaveStore fs = SaveStore.file(dir.resolve("save.json"));
        SaveData d = new SaveData();
        d.scrip = 1234; d.ranks.put("hull", 2); d.hand.add(new SaveData.Card(15, true)); d.rec.bestDepth = 360;
        fs.store(d);
        SaveData back = fs.load();
        assertEquals(1234, back.scrip);
        assertEquals(2, back.ranks.get("hull"));
        assertEquals(15, back.hand.get(0).n);
        assertTrue(back.hand.get(0).rev);
        assertEquals(360, back.rec.bestDepth);
    }
}
