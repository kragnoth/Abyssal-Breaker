package net.kragnoth.abyssal.desktop.ui;

import net.kragnoth.abyssal.core.Defs;
import net.kragnoth.abyssal.core.Defs.*;
import net.kragnoth.abyssal.core.Entities.Ball;
import net.kragnoth.abyssal.core.Game;
import net.kragnoth.abyssal.core.Game.State;
import net.kragnoth.abyssal.core.SaveData.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.kragnoth.abyssal.core.Defs.*;
import static net.kragnoth.abyssal.desktop.ui.UiBatch.VH;
import static net.kragnoth.abyssal.desktop.ui.UiBatch.VW;

/** HUD and every menu screen, drawn in virtual 640x448 space with clickable buttons. */
public final class Screens {
    static final int TXT = 0xe9d3a4, DIM = 0x8a7a62, GOLD = 0xffc43c, AETH = 0x5ae6ff, RED = 0xff5a3c, GRN = 0x9cff6a, ORANGE = 0xff9a3c,
        BORDER = 0x6b5a44, PANEL = 0x0d0907, CARD_GOLD = 0xc9a14a;

    private final UiBatch u;
    private final Game g;
    private record Hit(float x, float y, float w, float h, Runnable action) {}
    private List<Hit> hits = new ArrayList<>(), live = new ArrayList<>();
    private String msg = ""; private int msgCol = TXT; private float msgT, msgDur = 1;
    private float time;
    public Runnable onToggleCrt = () -> {}, onToggleJitter = () -> {}, onToggleMute = () -> {}, onQuit = () -> {};

    public Screens(UiBatch u, Game g) { this.u = u; this.g = g; }

    public void message(String text, int col, float dur) { msg = text; msgCol = col; msgT = dur; msgDur = dur; }

    /** Route a click (virtual coords). Returns true if a button took it. */
    public boolean click(float x, float y) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (x >= h.x && x <= h.x + h.w && y >= h.y && y <= h.y + h.h) { h.action.run(); return true; }
        }
        return false;
    }

    private void button(String label, float x, float y, float w, float h, float size, int col, boolean enabled, Runnable action) {
        u.rect(x, y, w, h, enabled ? 0x1a0f08 : 0x0d0907, 1);
        u.frame(x, y, w, h, 1.5f, enabled ? col : 0x4a3c2c);
        u.textCentered(label, x + w / 2, y + (h - size) / 2, size, enabled ? col : 0x5d5040);
        if (enabled) live.add(new Hit(x, y, w, h, action));
    }
    private void panel(float x, float y, float w, float h) {
        u.rect(x, y, w, h, PANEL, 1); u.frame(x, y, w, h, 2, BORDER);
    }
    private void dim() { u.rect(0, 0, VW, VH, 0x040304, 0.72f); }
    private float para(String s, float x, float y, float maxW, float size, int col, boolean center) {
        for (String line : u.wrap(s, size, maxW)) {
            if (center) u.textCentered(line, x + maxW / 2, y, size, col); else u.text(line, x, y, size, col);
            y += size * 1.12f;
        }
        return y;
    }

    // ================================================================ frame
    public void draw(float dt) {
        time += dt; msgT = Math.max(0, msgT - dt);
        live = new ArrayList<>();
        if (g.state != State.TITLE && g.state != State.FOUNDRY && g.state != State.LEVELS) hud();
        switch (g.state) {
            case TITLE -> title();
            case LEVELS -> levels();
            case JUNCTION -> junction();
            case CHEST -> chest();
            case SHOP -> shop();
            case FOUNDRY -> foundry();
            case OVER -> over();
            case VICTORY -> victory();
            case PAUSED -> pause();
            default -> {}
        }
        hits = live;
    }

    // ================================================================ HUD
    private void hud() {
        u.text("DEPTH " + g.depthM() + "m", 10, 8, 24, TXT);
        u.text(g.atmosphere().name() + (g.lvl != null && g.endless ? " · ENDLESS" : ""), 10, 34, 13, g.lvl != null ? ORANGE : DIM);
        if (g.lvl != null && g.lvl.hazard() == LevelHazard.NOISE) {
            float nf = Math.min(1, g.noise / 100f);
            u.text("NOISE", 10, 78, 12, nf > 0.7f ? RED : 0x40e6d2);
            u.rect(52, 80, 80, 8, 0x120c08, 1); u.frame(52, 80, 80, 8, 1.5f, BORDER);
            u.rect(53.5f, 81.5f, 77 * nf, 5, nf > 0.7f ? RED : 0x40e6d2, 1);
        }
        StringBuilder arc = new StringBuilder();
        for (Card c : g.save.hand) { if (!arc.isEmpty()) arc.append(" · "); arc.append(ARCANA.get(c.n).name().replace("THE ", "")).append(c.rev ? "↓" : ""); }
        if (!arc.isEmpty()) u.text("ARCANA: " + arc, 10, 49, 10, DIM);
        u.text("SCORE " + g.score, 10, 62, 14, TXT);

        float hf = g.hull / g.maxHull();
        float bx = VW - 10 - 90, by = 10;
        u.textRight("HULL", bx - 6, 8, 14, TXT);
        u.rect(bx, by, 90, 9, 0x120c08, 1); u.frame(bx, by, 90, 9, 1.5f, BORDER);
        u.rect(bx + 1.5f, by + 1.5f, Math.max(0, 87 * hf), 6, hf > 0.5f ? GRN : hf > 0.25f ? GOLD : RED, 1);
        String mult = String.format(Locale.ROOT, " x%.1f", g.cargoMult);
        float w1 = u.width("CARGO ", 14) + u.width(g.cargo + "", 14) + u.width(mult, 14);
        float x = VW - 10 - w1;
        x += u.text("CARGO ", x, 24, 14, TXT); x += u.text(g.cargo + "", x, 24, 14, GOLD); u.text(mult, x, 24, 14, DIM);
        u.textRight("CREDITS " + g.banked, VW - 10, 39, 14, TXT);
        coreLine(VW - 10, 55);

        // structure / clamps
        float cl = g.zoneCleared();
        String right;
        int rightCol = DIM;
        if (g.dropping != null) { right = "DESCENDING…"; rightCol = GRN; }
        else if (g.boss != null) { right = "CLAMPS LOCKED - " + (g.boss.def.kind().equals("wyrm") && g.lvl == null ? "SLAY THE WYRM" : "DEFEAT " + g.boss.def.name()); rightCol = RED; }
        else if (g.pressureT >= 0) { right = "CLAMPS READY - RMB / E  auto " + (int) Math.ceil(g.pressureT) + "s"; rightCol = g.pressureT < 6 ? RED : GRN; }
        else right = Math.round(cl * 100) + "%";
        float total = u.width("STRUCTURE ", 14) + 112 + 8 + u.width(right, 14);
        float sx = VW / 2 - total / 2, sy = VH - 22;
        sx += u.text("STRUCTURE ", sx, sy, 14, TXT);
        u.rect(sx, sy + 3, 110, 9, 0x120c08, 1); u.frame(sx, sy + 3, 110, 9, 1.5f, BORDER);
        u.rect(sx + 1.5f, sy + 4.5f, Math.min(1, cl / Defs.CLEAR_REQ * 0.65f) * 107, 6, cl >= Defs.CLEAR_REQ ? GRN : 0xd4552a, 1);
        u.rect(sx + 110 * 0.65f, sy + 1, 2, 13, TXT, 1);
        u.text(right, sx + 118, sy, 14, rightCol);

        if (g.boss != null) {
            var B = g.boss;
            int segHp = 0, segMax = 0; for (var s : B.segs) { segHp += s.alive ? s.hp : 0; segMax += s.max; }
            float frac = (segHp + Math.max(0, B.hp)) / (float) (segMax + B.max);
            String part = switch (B.def.kind()) { case "gate" -> " frame stones"; case "bell" -> " supports"; case "foundry" -> " kilns"; case "lodgers" -> " lodgers"; case "copy" -> " mirrors"; default -> " armor plates"; };
            String note = B.armored() ? B.aliveSegs() + part : switch (B.def.kind()) { case "bell" -> "THE BELL HAS FALLEN"; case "lodgers", "copy" -> "EXPOSED"; default -> "HEAD EXPOSED"; };
            float bw = u.width(B.def.name() + " ", 14) + 162 + u.width(note, 13);
            float bx2 = VW / 2 - bw / 2, by2 = VH - 42;
            bx2 += u.text(B.def.name() + " ", bx2, by2, 14, RED);
            u.rect(bx2, by2 + 3, 160, 9, 0x120c08, 1); u.frame(bx2, by2 + 3, 160, 9, 1.5f, BORDER); u.rect(bx2 + 1.5f, by2 + 4.5f, 157 * frac, 6, RED, 1);
            u.text(note, bx2 + 166, by2 + 1, 13, DIM);
        }
        if (g.gadget != null) {
            Gadget G = Defs.gadget(g.gadget); boolean ready = g.gadgetCd <= 0;
            float gx = 10, gy = VH - 30;
            u.text(G.name() + " " + g.gadgetLv + (ready ? "  [Q] READY" : ""), gx, gy - 14, 12, ready ? G.color() : DIM);
            u.rect(gx, gy, 120, 8, 0x120c08, 1); u.frame(gx, gy, 120, 8, 1.5f, BORDER);
            u.rect(gx + 1.5f, gy + 1.5f, 117 * (1 - g.gadgetCd / g.gadgetMaxCd()), 5, ready ? G.color() : 0x6a5a48, 1);
        }
        float xf = Math.min(1, g.xp / g.xpNext());
        u.rect(0, 0, VW, 4, 0x120c08, 1); u.rect(0, 0, VW * xf, 4, 0xc86aff, 1);
        u.textCentered("LV " + g.xpLevel + (g.perks.isEmpty() ? "" : "  ·  " + g.perks.size() + " perks"), VW / 2, 7, 11, 0xc86aff);
        if (g.overdriveT > 0) u.textCentered("OVERDRIVE " + (int) Math.ceil(g.overdriveT) + "s", VW / 2, 52, 16, 0xff3a3a);
        if (g.combo >= 5) u.textCentered(g.combo + " CHAIN", VW / 2, 30, 20, 0xffb347);
        if (msgT > 0) {
            float a = Math.min(1, msgT / 0.25f);
            float w = u.width(msg, 28);
            float sz = w > VW - 40 ? 28 * (VW - 40) / w : 28;
            u.text(msg, VW / 2 - u.width(msg, sz) / 2, VH * 0.38f, sz, msgCol, a, true);
        }
    }
    private void coreLine(float right, float y) {
        Ball lead = null;
        for (Ball b : g.balls) if (lead == null || b.total() > lead.total()) lead = b;
        List<Object[]> parts = new ArrayList<>();
        if (lead != null) {
            for (Evolution e : EVOS) if (lead.evo(e.key())) parts.add(new Object[]{e.name() + " ", 0x7affd4});
            for (String k : UP_KEYS) if (lead.up(k) > 0) parts.add(new Object[]{"" + pod(k).letter() + lead.up(k) + " ", pod(k).color()});
        }
        parts.add(new Object[]{"CORES " + g.balls.size() + "/" + MAX_BALLS, DIM});
        if (g.wideT > 0) parts.add(new Object[]{" W" + (int) Math.ceil(g.wideT) + "s", GRN});
        if (g.magT > 0) parts.add(new Object[]{" U" + (int) Math.ceil(g.magT) + "s", 0xc86aff});
        if (g.panelT > 0) parts.add(new Object[]{" L" + (int) Math.ceil(g.panelT) + "s", 0x6affb0});
        if (g.catchT > 0) parts.add(new Object[]{" C" + (int) Math.ceil(g.catchT) + "s", 0x7affd4});
        else if (g.lv("catch") > 0) parts.add(new Object[]{" GRAPPLE", 0x7affd4});
        float w = 0; for (Object[] p : parts) w += u.width((String) p[0], 13);
        float x = right - w;
        for (Object[] p : parts) x += u.text((String) p[0], x, y, 13, (int) p[1]);
    }

    // ================================================================ title
    private void title() {
        dim();
        float pw = 470, px = VW / 2 - pw / 2, py = 30;
        panel(px, py, pw, g.save.hand.isEmpty() ? 268 : 360);
        u.textCentered("ABYSSAL BREAKER", VW / 2, py + 8, 36, ORANGE);
        float y = py + 50, iw = pw - 40, ix = px + 20;
        y = para("Your boring rig hangs over a lightless shaft. The Wrecking Core is the only light down here.", ix, y, iw, 12, DIM, true) + 4;
        y = para("Break the rock below you. When the structure under the rig is 65% cleared, the clamps release and you can drop deeper.", ix, y, iw, 12, TXT, true) + 4;
        y = para("Catch pods: core upgrades stack to 8 per core and every split inherits them. Two upgrades at 2+ evolve a core. Wyrms guard 80, 160 and 260m; the Eldritch Heart waits at 360m. Bank at junctions to shop; when the rig is lost you earn scrip for the Foundry.", ix, y, iw, 12, TXT, true) + 6;
        String[] keys = {"MOUSE / A D  move rig      mouse height / W S  lower it on its spring", "CLICK / SPACE  launch core      RIGHT-CLICK / E  release clamps",
                         "C crt/dither · J vertex jitter · M mute · P pause · F11 fullscreen"};
        for (String k : keys) { u.textCentered(k, VW / 2, y, 10, 0xb9a37e); y += 12; }
        u.textCentered(quote(), VW / 2, py - 18, 11, 0x9a8a6a);
        y += 8;
        button("DESCEND", VW / 2 - 175, y, 150, 24, 15, ORANGE, true, g::openLevels);
        button("FOUNDRY [F] · " + g.save.scrip + " scrip", VW / 2 - 15, y, 190, 24, 13, ORANGE, true, g::openFoundry);
        y += 34;
        if (!g.save.hand.isEmpty()) {
            u.textCentered("YOUR HAND", VW / 2, y, 11, DIM); y += 14;
            float cw = 66, cx = VW / 2 - (g.save.hand.size() * (cw + 6) - 6) / 2;
            for (Card c : g.save.hand) { card(c, cx, y, cw, 74, true); cx += cw + 6; }
        }
    }

    private String quote() { return QUOTES.get((int) (time / 7) % QUOTES.size()); }

    // ================================================================ level select
    private void levels() {
        dim();
        float pw = 620, ph = 432, px = VW / 2 - pw / 2, py = 8;
        panel(px, py, pw, ph);
        u.textCentered("CHOOSE A SHAFT", VW / 2, py + 8, 22, 0xffb347);
        u.textCentered("Follow the Guild's records down. Clearing a level opens the next.", VW / 2, py + 34, 11, DIM);
        float cw = 148, ch = 158, gx = VW / 2 - (4 * cw + 3 * 6) / 2, gy = py + 50;
        for (int i = -1; i < LEVELS.size(); i++) {
            int slot = i < 0 ? 7 : i;
            float x = gx + (slot % 4) * (cw + 6), y = gy + (slot / 4) * (ch + 6);
            boolean unlocked = i < 0 || g.levelUnlocked(i), cleared = i >= 0 && g.levelCleared(i);
            u.rect(x, y, cw, ch, 0x120c08, 1); u.frame(x, y, cw, ch, 1.5f, cleared ? 0x4a6a2c : unlocked ? 0x6b5a44 : 0x2a2018);
            int tc = unlocked ? 0xffcf8a : 0x5d5040, dc = unlocked ? 0xb9a37e : 0x4a3c2c;
            final int lv = i;
            if (i < 0) {
                u.textCentered("CLASSIC DESCENT", x + cw / 2, y + 8, 12, tc);
                float ty = para("The original run: four wyrms down to the Eldritch Heart at 360m.", x + 6, y + 28, cw - 12, 10, dc, true);
                para("Hazard: rockfall past 30k score", x + 6, ty + 6, cw - 12, 9, dc, true);
                button("DESCEND [C]", x + cw / 2 - 52, y + ch - 28, 104, 20, 11, ORANGE, true, () -> g.start(-1));
                continue;
            }
            LevelDef L = LEVELS.get(i);
            u.text(String.valueOf(i + 1), x + 6, y + 6, 12, CARD_GOLD);
            if (cleared) u.textRight("CLEARED", x + cw - 6, y + 6, 10, GRN);
            float ty = y + 22;
            for (String line : u.wrap(L.name(), 13, cw - 10)) { u.textCentered(line, x + cw / 2, ty, 13, tc); ty += 14; }
            ty = para(L.place(), x + 6, ty + 2, cw - 12, 9, dc, true);
            ty = para("Hazard: " + L.hazardText(), x + 6, ty + 5, cw - 12, 9, unlocked ? 0x9cc6c0 : dc, true);
            para("Boss: " + L.bossText(), x + 6, ty + 4, cw - 12, 9, unlocked ? 0xd09a8a : dc, true);
            if (unlocked) button("DESCEND [" + (i + 1) + "]", x + cw / 2 - 52, y + ch - 28, 104, 20, 11, ORANGE, true, () -> g.start(lv));
            else u.textCentered("LOCKED - clear level " + i + " first", x + cw / 2, y + ch - 24, 9, 0x5d5040);
        }
        u.textCentered(quote(), VW / 2, py + ph - 50, 11, 0x9a8a6a);
        button("BACK [ESC]", VW / 2 - 70, py + ph - 32, 140, 22, 12, ORANGE, true, g::closeLevels);
    }

    // ================================================================ junction
    private void junction() {
        dim();
        float pw = 440, ph = 170, px = VW / 2 - pw / 2, py = VH / 2 - ph / 2;
        panel(px, py, pw, ph);
        u.textCentered("ELEVATOR JUNCTION - " + g.R * M_PER_ROW + "m", VW / 2, py + 10, 22, 0xffb347);
        u.textCentered("Cargo aboard: " + g.cargo + String.format(Locale.ROOT, "  (x%.1f multiplier)", g.cargoMult), VW / 2, py + 40, 13, GOLD);
        float y = para("[B] BANK - hoist cargo to the surface as credits, patch hull +25, then shop in the workshop. Multiplier resets.", px + 20, py + 60, pw - 40, 12, TXT, true);
        para("[G] PUSH ON - keep the cargo, multiplier +0.5. Lose the rig, lose the hold.", px + 20, y + 4, pw - 40, 12, TXT, true);
        button("BANK & PATCH [B]", VW / 2 - 165, py + ph - 36, 160, 24, 13, ORANGE, true, () -> g.closeJunction(true));
        button("PUSH ON [G]", VW / 2 + 5, py + ph - 36, 160, 24, 13, ORANGE, true, () -> g.closeJunction(false));
    }

    // ================================================================ jackpot chest
    private void chest() {
        dim();
        int tier = g.chestTier;
        String title = tier == 0 ? "LEVEL " + g.xpLevel + " - CHOOSE A PERK" : tier == 5 ? "JACKPOT!" : tier == 3 ? "GILDED CHEST" : "CHEST";
        int tc = tier == 0 ? 0xc86aff : tier == 5 ? 0xffe066 : tier == 3 ? 0xffc43c : 0xffb347;
        float pw = 520, ph = 268, px = VW / 2 - pw / 2, py = VH / 2 - ph / 2;
        panel(px, py, pw, ph);
        if (tier == 5) { float f = 0.5f + 0.5f * (float) Math.sin(time * 10); u.frame(px - 3, py - 3, pw + 6, ph + 6, 2, f > 0.5f ? 0xffe066 : 0xff7a2a); }
        u.textCentered(title, VW / 2, py + 8, 26, tc);
        String sub = tier == 0 ? "combo perks unlock as your build grows" : g.chestPicks + (g.chestPicks == 1 ? " pick left" : " picks left");
        if (g.chestBonus > 0) sub += "   ·   +" + g.chestBonus + " cargo";
        u.textCentered(sub, VW / 2, py + 38, 12, TXT);
        float cw = 160, ch = 168, cx = VW / 2 - (3 * cw + 2 * 8) / 2, cy = py + 58;
        boolean spinning = g.chestT < 0.6f;
        for (int i = 0; i < 3 && i < g.chestChoices.size(); i++) {
            float x = cx + i * (cw + 8);
            u.rect(x, cy, cw, ch, 0x120c08, 1);
            if (spinning) {                                                         // the reel
                String[] reel = {"FISSION", "OVERDRIVE", "STRENGTH", "AUGER", "BLAST CORE", "RECALL", "ARC CORE", "SEISMIC CHARGE", "WIDE BUMPER"};
                int k = (int) (time * 22 + i * 3) % reel.length;
                u.frame(x, cy, cw, ch, 1.5f, 0x4a3c2c);
                u.textCentered(reel[k], x + cw / 2, cy + ch / 2 - 18, 13, 0x7a6a50);
                u.textCentered(reel[(k + 1) % reel.length], x + cw / 2, cy + ch / 2, 14, 0xc9a14a);
                u.textCentered(reel[(k + 2) % reel.length], x + cw / 2, cy + ch / 2 + 18, 13, 0x7a6a50);
                continue;
            }
            Game.ChestInfo ci = g.chestInfo(g.chestChoices.get(i));
            boolean evo = ci.tag().startsWith("EVOLVES") || ci.tag().startsWith("COMBO");
            int ec = ci.tag().startsWith("COMBO") ? 0xff7ae0 : 0x7affd4;
            u.frame(x, cy, cw, ch, evo ? 2.5f : 1.5f, evo ? ec : ci.color());
            u.textCentered("[" + (i + 1) + "]", x + cw / 2, cy + 6, 11, DIM);
            for (String tl : u.wrap(ci.tag(), 10, cw - 10)) { u.textCentered(tl, x + cw / 2, cy + 22, 10, evo ? ec : DIM); break; }
            float ty = cy + 40;
            for (String line : u.wrap(ci.name(), 15, cw - 12)) { u.textCentered(line, x + cw / 2, ty, 15, ci.color()); ty += 16; }
            para(ci.desc(), x + 8, ty + 8, cw - 16, 11, 0xb9a37e, true);
            final int idx = i;
            button("TAKE", x + cw / 2 - 40, cy + ch - 30, 80, 22, 13, ORANGE, true, () -> g.pickChest(idx));
        }
        u.textCentered(spinning ? "…" : "click a card or press 1-3", VW / 2, py + ph - 22, 11, DIM);
    }

    // ================================================================ workshop
    private void shop() {
        dim();
        float pw = 600, ph = 300, px = VW / 2 - pw / 2, py = VH / 2 - ph / 2;
        panel(px, py, pw, ph);
        u.textCentered("SURFACE WORKSHOP", VW / 2, py + 8, 22, 0xffb347);
        u.textCentered("Credits " + g.banked + "   Hull " + Math.round(g.hull) + "/" + g.maxHull() + "   · upgrades last until the rig is lost", VW / 2, py + 34, 12, TXT);
        float cw = 136, ch = 170, cx = VW / 2 - (4 * cw + 3 * 8) / 2, cy = py + 54;
        for (int i = 0; i < 4; i++) {
            float x = cx + i * (cw + 8);
            u.rect(x, cy, cw, ch, 0x120c08, 1); u.frame(x, cy, cw, ch, 1.5f, 0x4a3c2c);
            String k = i < g.offers.size() ? g.offers.get(i) : null;
            if (k == null) { u.textCentered("— sold out —", x + cw / 2, cy + ch / 2 - 6, 11, DIM); continue; }
            ShopItem it = shopItem(k); int l = g.lv(k); long c = g.shopCost(it);
            u.textCentered("[" + (i + 1) + "]", x + cw / 2, cy + 6, 11, DIM);
            float ty = cy + 20;
            for (String line : u.wrap(it.name(), 12, cw - 10)) { u.textCentered(line, x + cw / 2, ty, 12, 0xffcf8a); ty += 13; }
            u.textCentered("■".repeat(l) + "□".repeat(it.max() - l), x + cw / 2, ty + 2, 11, DIM);
            para(it.desc(), x + 6, ty + 18, cw - 12, 10, 0xb9a37e, true);
            final int idx = i;
            button(String.valueOf(c), x + cw / 2 - 34, cy + ch - 46, 68, 20, 12, GOLD, g.banked >= c, () -> g.buyOffer(idx));
            if (g.banishLeft > 0) button("banish", x + cw / 2 - 26, cy + ch - 22, 52, 16, 10, 0x8a5a4a, true, () -> g.banishOffer(idx));
        }
        float ry = cy + ch + 8;
        boolean full = g.hull >= g.maxHull();
        long rc = g.rerollCost();
        button("[5] REPAIR +40 · " + (full ? "FULL" : Defs.REPAIR_COST), VW / 2 - 205, ry, 190, 22, 12, GOLD, !full && g.banked >= Defs.REPAIR_COST, g::repair);
        button("[R] REROLL · " + (g.rerollsFree > 0 ? "FREE x" + g.rerollsFree : rc), VW / 2 + 15, ry, 190, 22, 12, GOLD, g.banked >= rc, g::reroll);
        if (g.banishLeft > 0) u.textCentered("banishes left: " + g.banishLeft, VW / 2, ry + 26, 10, DIM);
        button("CONTINUE DESCENT [ENTER]", VW / 2 - 110, py + ph - 30, 220, 22, 13, ORANGE, true, g::closeShop);
    }

    // ================================================================ foundry
    private void foundry() {
        dim();
        float pw = 620, ph = 432, px = VW / 2 - pw / 2, py = 8;
        panel(px, py, pw, ph);
        u.textCentered("THE FOUNDRY  - upgrades for every future descent", VW / 2, py + 6, 18, 0xffb347);
        u.textCentered("Scrip " + g.save.scrip + "  · every rank bought raises all prices 10% · refund any time · saved to disk", VW / 2, py + 28, 10, DIM);
        float cw = 148, ch = 66, gx = VW / 2 - (4 * cw + 3 * 4) / 2, gy = py + 44;
        int i = 0;
        for (MetaItem m : META) {
            float x = gx + (i % 4) * (cw + 4), y = gy + (i / 4) * (ch + 4); i++;
            boolean un = g.metaUnlocked(m);
            u.rect(x, y, cw, ch, 0x120c08, 1); u.frame(x, y, cw, ch, 1.5f, un ? 0x4a3c2c : 0x2a2018);
            if (!un) { u.textCentered("??? LOCKED", x + cw / 2, y + 8, 11, 0x5d5040); para(UNLOCKS.get(m.unlock()).desc(), x + 4, y + 26, cw - 8, 10, 0x5d5040, true); continue; }
            int r = g.mr(m.key()); boolean maxed = r >= m.max(); long c = g.metaCost(m);
            u.textCentered(m.name(), x + cw / 2, y + 4, 11, 0xffcf8a);
            u.textCentered("■".repeat(r) + "□".repeat(m.max() - r), x + cw / 2, y + 17, 9, DIM);
            para(m.desc(), x + 4, y + 28, cw - 8, 9, 0xb9a37e, true);
            button(maxed ? "MAX" : String.valueOf(c), x + cw / 2 - 30, y + ch - 17, 60, 14, 10, GOLD, !maxed && g.save.scrip >= c, () -> g.metaBuy(m.key()));
        }
        float by = py + ph - 30;
        button(g.refundArmed ? "CONFIRM REFUND (" + g.save.spent + ")" : "REFUND ALL", VW / 2 - 250, by, 160, 22, 12, ORANGE, true, g::metaRefund);
        button(g.newGameArmed ? "CONFIRM: WIPE EVERYTHING" : "NEW GAME", VW / 2 - 80, by, 180, 22, 12, g.newGameArmed ? RED : ORANGE, true, g::newGame);
        button("BACK [ESC]", VW / 2 + 110, by, 140, 22, 12, ORANGE, true, g::closeFoundry);
    }

    // ================================================================ game over + tarot
    private void over() {
        dim();
        var lr = g.lastRun;
        float pw = 560, ph = 420, px = VW / 2 - pw / 2, py = 14;
        panel(px, py, pw, ph);
        boolean win = lr != null && lr.win();
        u.textCentered(win ? (g.lvl != null ? g.lvl.name() + " CLEARED" : "YOU REACHED THE CORE") : "RIG LOST", VW / 2, py + 8, 24, win ? GRN : RED);
        float y = py + 40;
        if (lr != null) {
            int sec = (int) lr.seconds();
            u.textCentered((win ? "Surfaced from " : "Crushed at ") + lr.depthM() + "m after " + sec / 60 + ":" + String.format("%02d", sec % 60), VW / 2, y, 13, TXT); y += 16;
            u.textCentered("Banked " + g.bankedTotal + " · Cargo lost " + g.cargo + " · Score " + (g.score + g.bankedTotal) + " · Best chain " + g.bestCombo, VW / 2, y, 11, TXT); y += 14;
            StringBuilder pt = new StringBuilder();
            for (String[] p : lr.parts()) { if (!pt.isEmpty()) pt.append(" + "); pt.append(p[0]).append(" ").append(p[1]); }
            u.textCentered(pt.toString(), VW / 2, y, 10, DIM); y += 14;
            u.textCentered("+" + lr.earned() + " SCRIP  (" + g.save.scrip + " total)", VW / 2, y, 18, GOLD); y += 22;
            if (!lr.fresh().isEmpty()) { u.textCentered("UNLOCKED IN THE FOUNDRY: " + String.join(", ", lr.fresh()), VW / 2, y, 11, GRN); y += 14; }
        } else { u.textCentered("The cards remember your last rig.", VW / 2, y, 12, DIM); y += 18; }

        Card pend = g.save.pending;
        if (pend != null) {
            u.textCentered("THE SHAFT DEALS YOU A CARD", VW / 2, y + 2, 11, DIM); y += 16;
            card(pend, VW / 2 - 170, y, 130, 176, false);
            float bx = VW / 2 - 20, by = y + 20;
            long v = g.cardValue(pend);
            if (g.save.hand.size() >= 3) {
                u.text("Hand full: replace a card", bx, by, 11, DIM); by += 16;
                for (int i = 0; i < g.save.hand.size(); i++) {
                    Card h = g.save.hand.get(i); final int idx = i;
                    button("[" + (i + 1) + "] REPLACE " + ARCANA.get(h.n).name() + (h.rev ? " (R)" : ""), bx, by, 220, 20, 11, ORANGE, true, () -> g.tarotChoice("r" + idx));
                    by += 24;
                }
            } else { button("[K] KEEP (" + g.save.hand.size() + "/3)", bx, by, 200, 24, 13, ORANGE, true, () -> g.tarotChoice("keep")); by += 30; }
            button("[P] PASS +" + v + " SCRIP", bx, by, 200, 24, 13, GOLD, true, () -> g.tarotChoice("pass"));
            y += 184;
            if (!g.save.hand.isEmpty()) {
                u.textCentered("YOUR HAND", VW / 2, y, 10, DIM); y += 12;
                float cw = 60, cx = VW / 2 - (g.save.hand.size() * (cw + 6) - 6) / 2;
                for (Card c : g.save.hand) { card(c, cx, y, cw, 66, true); cx += cw + 6; }
            }
        } else {
            if (!g.save.hand.isEmpty()) {
                u.textCentered("YOUR HAND", VW / 2, y, 11, DIM); y += 14;
                float cw = 100, cx = VW / 2 - (g.save.hand.size() * (cw + 8) - 8) / 2;
                for (Card c : g.save.hand) { card(c, cx, y, cw, 150, false); cx += cw + 8; }
                y += 158;
            }
            button("AGAIN [R]", VW / 2 - 220, py + ph - 52, 140, 24, 13, ORANGE, true, () -> g.start());
            button("LEVELS [L]", VW / 2 - 70, py + ph - 52, 140, 24, 13, ORANGE, true, g::openLevels);
            button("FOUNDRY [F]", VW / 2 + 80, py + ph - 52, 140, 24, 13, ORANGE, true, g::openFoundry);
            u.textCentered("Workshop upgrades die with the rig. Foundry upgrades carry into every future descent.", VW / 2, py + ph - 20, 10, DIM);
        }
    }

    private void card(Card c, float x, float y, float w, float h, boolean small) {
        Arcana a = ARCANA.get(c.n);
        u.rect(x, y, w, h, 0x140c06, 1); u.frame(x, y, w, h, 2, CARD_GOLD); u.frame(x + 4, y + 4, w - 8, h - 8, 1, 0x6b5a2c);
        float s = small ? 0.6f : 1;
        u.textCentered(ROMAN[c.n], x + w / 2, y + 7 * s, 13 * s, CARD_GOLD);
        u.textCentered(a.glyph(), x + w / 2, y + 22 * s, 38 * s, c.rev ? 0xc08aff : 0xffd88a);
        float ty = y + 64 * s;
        for (String line : u.wrap(a.name(), 11 * s, w - 10)) { u.textCentered(line, x + w / 2, ty, 11 * s, 0xffcf8a); ty += 12 * s; }
        if (c.rev) { u.textCentered("REVERSED", x + w / 2, ty, 10 * s, RED); ty += 12 * s; }
        if (small) return;
        ty += 4;
        Map<String, Double> mods = g.cardMods(c);
        if (mods.isEmpty()) u.textCentered("no effect", x + w / 2, ty, 9, DIM);
        for (var e : mods.entrySet()) {
            Afx fx = AFX.get(e.getKey()); double v = e.getValue();
            String num = switch (fx.unit()) {
                case '%' -> (v > 0 ? "+" : "") + Math.round(v * 100) + "%";
                case 'x' -> "";
                case 's' -> (v > 0 ? "+" : "") + Math.round(v) + "s";
                default -> (v > 0 ? "+" : "") + (v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v));
            };
            for (String line : u.wrap((num + " " + fx.label()).trim(), 9, w - 10)) { u.textCentered(line, x + w / 2, ty, 9, v * fx.good() > 0 ? GRN : RED); ty += 10; }
        }
    }

    private void victory() {
        dim();
        float pw = 460, ph = 160, px = VW / 2 - pw / 2, py = VH / 2 - ph / 2;
        panel(px, py, pw, ph);
        boolean lv = g.lvl != null;
        u.textCentered(lv ? g.lvl.name() + " CLEARED" : "THE ELDRITCH HEART IS BROKEN", VW / 2, py + 10, 20, GRN);
        String next = lv && g.level + 1 < LEVELS.size() ? " " + LEVELS.get(g.level + 1).name() + " is open." : "";
        float y = para((lv ? "The Guild's records lead on." + next : "You reached the bottom of the shaft.") + " Scrip for this run is multiplied x1.5.", px + 20, py + 40, pw - 40, 12, TXT, true);
        para("[S] SURFACE - hoist all cargo and end the run as a victory.  [E] ENDLESS - keep descending; the " + (lv ? "boss returns every 60m" : "wyrms return every 120m") + ", stronger each time.", px + 20, y + 4, pw - 40, 11, TXT, true);
        button("SURFACE [S]", VW / 2 - 160, py + ph - 34, 150, 24, 13, ORANGE, true, () -> g.closeVictory(false));
        button("ENDLESS [E]", VW / 2 + 10, py + ph - 34, 150, 24, 13, ORANGE, true, () -> g.closeVictory(true));
    }

    private void pause() {
        dim();
        float pw = 260, ph = 120, px = VW / 2 - pw / 2, py = VH / 2 - ph / 2;
        panel(px, py, pw, ph);
        u.textCentered("PAUSED", VW / 2, py + 12, 24, 0xffb347);
        button("RESUME [P]", VW / 2 - 70, py + 50, 140, 24, 13, ORANGE, true, g::togglePause);
        button("QUIT TO DESKTOP", VW / 2 - 70, py + 80, 140, 22, 11, DIM, true, onQuit);
    }
}
