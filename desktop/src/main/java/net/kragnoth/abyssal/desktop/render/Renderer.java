package net.kragnoth.abyssal.desktop.render;

import net.kragnoth.abyssal.core.Defs;
import net.kragnoth.abyssal.core.Entities.*;
import net.kragnoth.abyssal.core.Game;
import net.kragnoth.abyssal.core.Mth;
import net.kragnoth.abyssal.desktop.gl.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import static net.kragnoth.abyssal.core.Defs.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 * Draws the game into a 640x448 render target with per-vertex (Gouraud) point lights, vertex snapping and
 * abyss fog, then upscales it through a dither/scanline/chromatic-aberration post pass.
 */
public final class Renderer {
    private static final int NL = 8;

    private final Shader lit, unlit, sprite, points, post;
    private final Textures tex = new Textures();
    private final Texture podAtlas;
    private final Map<String, Integer> podIndex = new HashMap<>();
    private final int fbo, colorTex, depthRb, postVao;

    // static meshes
    private final Mesh[] blockVariants = new Mesh[6];
    private final float[][] blockVariantData = new float[6][];
    private final Mesh backWall, sideWall, rail, girder, post2, cube, chassis, bumper, vent, piston, wheel, winchM, coil, beam, clampBox,
        drillCone, panelBox, strip, warnBox, stalCone, bellMesh, seg, head, eye, ball, nugget;
    // dynamic meshes
    private final Mesh dynDebris = new Mesh(true), dynSparks = new Mesh(true), dynGlow = new Mesh(true), dynPods = new Mesh(true), dynBeams = new Mesh(true);
    private final FloatList scratch = new FloatList(1 << 16);
    private final Map<Integer, RowMesh> rowMeshes = new HashMap<>();

    private final Matrix4f proj = new Matrix4f(), view = new Matrix4f(), model = new Matrix4f();
    private final Vector3f camRight = new Vector3f(), camUp = new Vector3f();
    private final float[] lightPos = new float[NL * 4], lightCol = new float[NL * 3];
    public boolean crt = true, jitter = true;
    private float time;

    private static final class RowMesh { Mesh mesh; int version = -1; }

    public Renderer() {
        lit = new Shader(LIT_VS, LIT_FS);
        unlit = new Shader(UNLIT_VS, UNLIT_FS);
        sprite = new Shader(SPRITE_VS, SPRITE_FS);
        points = new Shader(POINTS_VS, POINTS_FS);
        post = new Shader(POST_VS, POST_FS);

        for (int i = 0; i < 6; i++) { FloatList d = Geo.block(new int[]{11, 23, 37, 51, 67, 83}[i]); blockVariants[i] = Mesh.of(d); blockVariantData[i] = java.util.Arrays.copyOf(d.array(), d.size()); }
        backWall = Mesh.of(Geo.plane(60, 60, 24));
        sideWall = Mesh.of(Geo.box(8, 60, 3, 4, 30, 1, null));
        rail = Mesh.of(Geo.box(0.25f, 60, 0.4f));
        girder = Mesh.of(Geo.box(COLS + 6, 0.5f, 0.5f));
        post2 = Mesh.of(Geo.box(0.45f, 12, 0.45f));
        cube = Mesh.of(Geo.box(1, 1, 1));
        chassis = Mesh.of(Geo.box(PW - 0.3f, 0.7f, 1.1f, 3, 1, 1, null));
        bumper = Mesh.of(Geo.box(PW, 0.18f, 1.3f));
        vent = Mesh.of(Geo.box(0.8f, 0.22f, 0.1f));
        piston = Mesh.of(Geo.cylinder(0.08f, 0.08f, 0.35f, 6));
        wheel = Mesh.of(Geo.box(0.4f, 0.22f, 0.6f));
        winchM = Mesh.of(Geo.cylinder(0.28f, 0.28f, 0.9f, 8));
        coil = Mesh.of(Geo.coil(0.3f, 0.045f, 7, 200, 5));
        beam = Mesh.of(Geo.box(COLS + 1, 0.34f, 1.0f));
        clampBox = Mesh.of(Geo.box(0.7f, 0.9f, 1.3f));
        drillCone = Mesh.of(Geo.cylinder(1.35f, 0, 2.4f, 8));
        panelBox = Mesh.of(Geo.box(0.55f, 1.3f, 1.1f));
        strip = Mesh.of(Geo.box(0.12f, 0.9f, 0.1f));
        warnBox = Mesh.of(Geo.box(1.1f, 0.12f, 1.3f));
        stalCone = Mesh.of(Geo.cylinder(0.42f, 0, 1.7f, 6));
        bellMesh = Mesh.of(Geo.cylinder(0.45f, 1.0f, 1.4f, 10));
        seg = Mesh.of(Geo.icosahedron(0.5f));
        head = Mesh.of(Geo.dodecahedron(0.72f));
        eye = Mesh.of(Geo.box(0.16f, 0.12f, 0.1f));
        ball = Mesh.of(Geo.icosahedron(BR));
        nugget = Mesh.of(Geo.octahedron(0.17f));
        podAtlas = buildPodAtlas();

        // off-screen target at the PS2-ish internal resolution
        fbo = glGenFramebuffers(); glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        colorTex = glGenTextures(); glBindTexture(GL_TEXTURE_2D, colorTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, VIEW_W, VIEW_H, 0, GL_RGBA, GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTex, 0);
        depthRb = glGenRenderbuffers(); glBindRenderbuffer(GL_RENDERBUFFER, depthRb);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, VIEW_W, VIEW_H);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthRb);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("framebuffer incomplete");
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        postVao = glGenVertexArrays();
        proj.setPerspective((float) Math.toRadians(35), VIEW_W / (float) VIEW_H, 0.5f, 200f);
    }

    /** Capsule sprites with the pod letter, painted with Java2D once at start-up. */
    private Texture buildPodAtlas() {
        int w = 26, h = 14, n = PODS.size();
        BufferedImage img = new BufferedImage(w * n, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        for (int i = 0; i < n; i++) {
            PowerUp p = PODS.get(i); podIndex.put(p.key(), i);
            int x0 = i * w;
            g.setColor(Color.BLACK); g.fillRect(x0 + 2, 0, 22, 14); g.fillRect(x0, 2, 26, 10);
            g.setColor(new Color(p.color())); g.fillRect(x0 + 3, 1, 20, 12); g.fillRect(x0 + 1, 3, 24, 8);
            g.setColor(new Color(255, 255, 255, 115)); g.fillRect(x0 + 4, 2, 18, 2);
            g.setColor(new Color(0, 0, 0, 90)); g.fillRect(x0 + 4, 10, 18, 2);
            g.setColor(Color.BLACK);
            String s = String.valueOf(p.letter()); int sw = g.getFontMetrics().stringWidth(s);
            g.drawString(s, x0 + 13 - sw / 2, 11);
        }
        g.dispose();
        byte[] rgba = new byte[w * n * h * 4];
        for (int y = 0; y < h; y++) for (int x = 0; x < w * n; x++) {
            int argb = img.getRGB(x, h - 1 - y), o = (y * w * n + x) * 4;   // flip: GL origin is bottom-left
            rgba[o] = (byte) (argb >> 16); rgba[o + 1] = (byte) (argb >> 8); rgba[o + 2] = (byte) argb; rgba[o + 3] = (byte) (argb >>> 24);
        }
        return new Texture(w * n, h, rgba, true, false);
    }

    // ================================================================ frame
    public void render(Game g, Particles fx, float dt, int winW, int winH, int[] viewport) {
        time += dt;
        // camera
        float sx = (float) ((Math.random() - 0.5) * g.shake * 0.5), sy = (float) ((Math.random() - 0.5) * g.shake * 0.5);
        float ty = g.rigY - CAM_TARGET_BELOW_RIG;
        view.setLookAt(sx, ty + 5.5f + sy, 26, sx * 0.5f, ty + sy * 0.5f, 0, 0, 1, 0);
        view.positiveX(camRight); view.positiveY(camUp);
        Atmosphere atm = g.atmosphere();
        buildLights(g, fx);

        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, VIEW_W, VIEW_H);
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glEnable(GL_DEPTH_TEST); glDepthFunc(GL_LEQUAL);
        glDisable(GL_CULL_FACE); glDisable(GL_BLEND);

        lit.use();
        lit.set("uView", view).set("uProj", proj).set4v("uLightPos", lightPos).set3v("uLightCol", lightCol)
            .set("uAmbient", atm.amb()).set("uFogColor", atm.fog()).set("uBiome", atm.tint())
            .set("uRigY", g.rigY).set("uFogStart", 8f).set("uFogRange", 9f).set("uSnap", jitter ? 1f : 0f)
            .set("uRes", VIEW_W / 2f, VIEW_H / 2f).set("uTime", time).set("uMap", 0);
        glVertexAttrib3f(3, 0, 0, 0);

        drawEnvironment(g, ty);
        drawRows(g);
        drawRig(g);
        drawMachines(g);
        drawBoss(g);
        drawCoresAndLoot(g);
        drawParticles(fx);

        // ---- post to the window (letterboxed to the 640:448 aspect)
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDisable(GL_DEPTH_TEST); glDisable(GL_BLEND);
        glViewport(0, 0, winW, winH);
        glClearColor(0.016f, 0.012f, 0.016f, 1); glClear(GL_COLOR_BUFFER_BIT);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        post.use().set("tDiffuse", 0).set("uRes", (float) VIEW_W, (float) VIEW_H).set("uDither", crt ? 1f : 0f).set("uCRT", crt ? 1f : 0f).set("uFlash", g.hullFlash * 0.5f);
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, colorTex);
        glBindVertexArray(postVao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
    }

    private void buildLights(Game g, Particles fx) {
        int li = 0;
        java.util.Arrays.fill(lightPos, 0); java.util.Arrays.fill(lightCol, 0);
        for (int i = 0; i < NL; i++) { lightPos[i * 4 + 1] = 1e4f; lightPos[i * 4 + 3] = 1; }
        if (g.boss != null) { Boss B = g.boss; boolean tele = B.phase.equals("tele") && ((int) (time * 14)) % 2 == 1; li = put(li, B.x, B.y, 1.5f, 6, tele ? 2.2f : 1.2f, 0.3f, 0.2f); }
        for (var v : g.voidLights) li = put(li, v.x, v.y, 1.5f, 6, 0.9f, 0.5f, 1.6f);
        if (!g.drills.isEmpty()) { Drill d = g.drills.get(0); li = put(li, g.cellX(d.c), d.y - 1, 1.5f, 9, 2.4f, 1.3f, 0.5f); }
        int nb = 0;
        for (Ball b : g.balls) {
            if (nb++ >= 5) break;
            float k = b.boost > 1.05f ? 0.5f : 0;
            li = put(li, b.x, b.y, 1.5f, 9 + b.total() * 0.3f, b.col[0] * 2.6f + 0.4f, b.col[1] * 2.6f + 0.2f + k, b.col[2] * 2.6f + 0.1f);
        }
        int nf = 0;
        for (float[] f : fx.flashes) { if (nf++ >= 2) break; float k = f[6] / f[7]; li = put(li, f[0], f[1], 2, f[5], f[2] * k, f[3] * k, f[4] * k); }
        li = put(li, g.px - 1.1f, g.paddleY - 1.2f, 1.6f, 7.5f, 1.2f, 0.95f, 0.6f);
        put(li, g.px + 1.1f, g.paddleY - 1.2f, 1.6f, 7.5f, 1.2f, 0.95f, 0.6f);
    }
    private int put(int li, float x, float y, float z, float rad, float r, float gg, float b) {
        if (li >= NL) return li;
        lightPos[li * 4] = x; lightPos[li * 4 + 1] = y; lightPos[li * 4 + 2] = z; lightPos[li * 4 + 3] = rad;
        lightCol[li * 3] = r; lightCol[li * 3 + 1] = gg; lightCol[li * 3 + 2] = b;
        return li + 1;
    }

    // ---- lit helpers
    private void litDraw(Mesh m, Texture t, Matrix4f mdl, float tint, float uvx, float uvy, float offx, float offy, float er, float eg, float eb) {
        t.bind(0);
        lit.set("uModel", mdl).set("uTint", tint, tint, tint).set("uUvScale", uvx, uvy).set("uUvOff", offx, offy).set("uEmis", er, eg, eb);
        m.draw();
    }
    private void litDraw(Mesh m, Texture t, Matrix4f mdl, float tint) { litDraw(m, t, mdl, tint, 1, 1, 0, 0, 0, 0, 0); }
    private void litTinted(Mesh m, Texture t, Matrix4f mdl, float[] tint, float er, float eg, float eb) {
        t.bind(0);
        lit.set("uModel", mdl).set("uTint", tint[0], tint[1], tint[2]).set("uUvScale", 1, 1).set("uUvOff", 0, 0).set("uEmis", er, eg, eb);
        m.draw();
    }

    private void drawEnvironment(Game g, float camY) {
        float off = (camY / 4) % 1;
        litDraw(backWall, tex.wall, model.identity().translate(0, camY, -3.5f), 1.6f, 15, 15, 0, off, 0, 0, 0);
        for (int s = -1; s <= 1; s += 2) {
            litDraw(sideWall, tex.wall, model.identity().translate(s * (HALF + 4), camY, 0), 1.9f, 2, 15, 0, off, 0, 0, 0);
            litDraw(rail, tex.metal, model.identity().translate(s * (HALF + 0.1f), camY, 1.2f), 2f, 1, 60, 0, camY % 1, 0, 0, 0);
        }
        for (int r = (g.R - 6) / 12 * 12; r <= g.R + 36; r += 12) {
            if (r < 0) continue;
            litDraw(girder, tex.girder, model.identity().translate(0, -r, -2.6f), 2f, 16, 1, 0, 0, 0, 0, 0);
            for (int s = -1; s <= 1; s += 2)
                litDraw(post2, tex.girder, model.identity().translate(s * (HALF - 1.5f + (float) Mth.hash(r, s + 2, 7) * 2), -r - 6, -2.9f), 1.8f, 1, 10, 0, 0, 0, 0, 0);
        }
    }

    // ---- shaft rows: one cached mesh per row, rebuilt when the row changes (like Minecraft chunk sections)
    private void drawRows(Game g) {
        tex.blocks(g.level).bind(0);
        lit.set("uModel", model.identity()).set("uTint", 2.2f, 2.2f, 2.2f).set("uUvScale", 1, 1).set("uUvOff", 0, 0).set("uEmis", 0, 0, 0);
        rowMeshes.entrySet().removeIf(e -> { if (!g.rows.containsKey(e.getKey())) { e.getValue().mesh.delete(); return true; } return false; });
        for (Row row : g.rows.values()) {
            if (row.r < g.R - 4 || row.r > g.R + 24) continue;
            RowMesh rm = rowMeshes.computeIfAbsent(row.r, k -> { RowMesh x = new RowMesh(); x.mesh = new Mesh(false); return x; });
            if (rm.version != row.version) { buildRow(g, row, rm.mesh); rm.version = row.version; }
            glBindVertexArray(rm.mesh.vao);
            rm.mesh.draw();
        }
    }
    private void buildRow(Game g, Row row, Mesh mesh) {
        FloatList o = scratch; o.clear();
        for (int c = 0; c < COLS; c++) {
            int t = row.t[c];
            if (t == 0) continue;
            int v = (int) (Mth.hash(c, row.r * 3, 5) * 6), rot = t == MIRROR ? 0 : (int) (Mth.hash(c * 5, row.r, 5) * 4);
            float ratio = row.maxHp[c] > 0 ? (float) row.hp[c] / row.maxHp[c] : 1;
            int crack = ratio >= 0.999f ? 0 : ratio <= 0.5f ? 2 : 1;
            float sc = crack > 0 ? 0.94f + ratio * 0.06f : 1;
            float cs = (float) Math.cos(rot * Math.PI / 2), sn = (float) Math.sin(rot * Math.PI / 2);
            float gx = 0, gy = 0, gz = 0;
            if (ORE[t] != null) { gx = ORE[t][0] / 255f * 1.2f; gy = ORE[t][1] / 255f * 1.2f; gz = ORE[t][2] / 255f * 1.2f; }
            float cx = g.cellX(c), cy = -row.r;
            float[] d = blockVariantData[v];
            for (int i = 0; i < d.length; i += Mesh.STRIDE) {
                float x = d[i] * sc, y = d[i + 1] * sc, z = d[i + 2] * sc, nx = d[i + 3], ny = d[i + 4];
                o.add(cx + x * cs - y * sn, cy + x * sn + y * cs, z, nx * cs - ny * sn, nx * sn + ny * cs, d[i + 5],
                    ((t - 1) + d[i + 6]) / Textures.ATLAS_COLS, (crack + d[i + 7]) / Textures.ATLAS_ROWS, gx, gy, gz);
            }
        }
        mesh.upload(o);
    }

    private void drawRig(Game g) {
        float sxScale = g.padW / PW;
        Matrix4f rigM = new Matrix4f().translate(g.px, g.paddleY + g.rigKick * 0.6f, 0).rotateZ(Mth.clamp(-g.rigVel * 0.004f, -0.08f, 0.08f)).scale(sxScale, 1, 1);
        litDraw(chassis, tex.metal, new Matrix4f(rigM).translate(0, 0.05f, 0), 2.1f);
        litDraw(bumper, tex.hazard, new Matrix4f(rigM).translate(0, -0.46f, 0), 2f, 4, 0.4f, 0, 0, 0, 0, 0);
        litDraw(vent, tex.hazard, new Matrix4f(rigM).translate(0, 0.12f, 0.58f), 0, 1, 1, 0, 0, 1.4f + (float) Math.sin(time * 9) * 0.4f, 0.5f, 0.1f);
        for (int s = -1; s <= 1; s += 2) {
            float wob = 1 + (float) Math.sin(time * 14 + s * 0.8f) * 0.15f * Math.min(1, Math.abs(g.px - g.pxTarget) + Math.abs(g.dropV) * 0.2f);
            litDraw(piston, tex.metal, new Matrix4f(rigM).translate(s * 0.8f, 0.52f, 0).scale(1, wob, 1), 2.1f);
        }
        // trolley, winch and coil spring
        for (int s = -1; s <= 1; s += 2) litDraw(wheel, tex.metal, model.identity().translate(g.px + s * 0.6f, g.rigY + 0.86f, 0), 2.1f);
        litDraw(winchM, tex.metal, model.identity().translate(g.px, g.rigY + 0.62f, 0).rotateZ((float) Math.PI / 2), 2.1f);
        float top = g.rigY + 0.45f, bot = g.paddleY + 0.75f, len = Math.max(0.15f, top - bot);
        litDraw(coil, tex.metal, model.identity().translate(g.px, (top + bot) / 2, 0).scale(1, len, 1), 2.1f);
        // bridge
        litDraw(beam, tex.girder, model.identity().translate(0, g.rigY + 0.86f, 0), 2.2f, COLS, 1, 0, 0, 0, 0, 0);
        for (int s = -1; s <= 1; s += 2) litDraw(clampBox, tex.metal, model.identity().translate(s * (HALF + 0.3f), g.rigY + 0.8f, 0), 2.1f);

        unlit.use().set("uView", view).set("uProj", proj).set("uVColor", 0);
        boolean ready = g.pressureT >= 0 || g.dropping != null;
        boolean blink = ((int) (time * 6)) % 2 == 1;
        for (int s = -1; s <= 1; s += 2) {
            unlitDraw(cube, model.identity().translate(s * (HALF + 0.3f), g.rigY + 0.8f, 0.7f).scale(0.22f, 0.22f, 0.1f), ready ? (blink ? 0x9cff6a : 0x2a5a1a) : 0xff3020);
            unlitDraw(cube, new Matrix4f(rigM).translate(s * 1.1f, -0.3f, 0.6f).scale(0.28f, 0.16f, 0.2f), 0xffe08a);
        }
        lit.use();
    }
    private void unlitDraw(Mesh m, Matrix4f mdl, int hex) {
        unlit.set("uModel", mdl).set("uColor", Mth.r(hex), Mth.g(hex), Mth.b(hex));
        m.draw();
    }
    private void unlitDraw(Mesh m, Matrix4f mdl, float r, float gg, float b) { unlit.set("uModel", mdl).set("uColor", r, gg, b); m.draw(); }

    private void drawMachines(Game g) {
        for (Drill d : g.drills) litDraw(drillCone, tex.metal, model.identity().translate(g.cellX(d.c), d.y, 0.3f).rotateY(time * 25), 2.1f);
        for (Panel p : g.panels) litDraw(panelBox, tex.metal, model.identity().translate(p.side * (HALF + 0.3f), p.y, 0.3f), 2.1f);
        for (Hazard h : g.hazards) {
            if (h.falling) litDraw(stalCone, tex.metal, model.identity().translate(h.x, h.y, 0), 2.1f);
            else if (h.rising && h.warn <= 0) litTinted(stalCone, tex.hazard, model.identity().translate(h.x, h.y, 0.2f).rotateZ((float) Math.PI), new float[]{0, 0, 0}, 2.2f, 0.9f, 0.2f);
        }
        unlit.use().set("uView", view).set("uProj", proj).set("uVColor", 0);
        for (Panel p : g.panels) {
            float x = p.side * (HALF + 0.3f);
            int col = p.kind.equals("laser") ? (p.cd < 0.4f && ((int) (time * 12)) % 2 == 1 ? 0xffffff : 0xff3a2a) : (p.pull > 0 ? 0xffffff : 0xc86aff);
            unlitDraw(strip, model.identity().translate(x - p.side * 0.29f, p.y, 0.4f), col);
        }
        for (Hazard h : g.hazards) if (h.warn > 0 && ((int) (time * 10)) % 2 == 0) unlitDraw(warnBox, model.identity().translate(h.x, g.rigY + 1.1f, 0.1f), 0xff3020);
        lit.use();
    }

    private void drawBoss(Game g) {
        Boss B = g.boss;
        if (B == null) return;
        String kind = B.def.kind();
        if (!kind.equals("wyrm")) { drawOtherBoss(g, B, kind); return; }
        float[] tint = {B.def.col()[0] * 2.3f, B.def.col()[1] * 2.3f, B.def.col()[2] * 2.3f};
        float ss = B.def.segR() / 0.62f, hs = B.def.headR() / 0.95f;
        for (Segment s : B.segs) {
            if (!s.alive) continue;
            Matrix4f m = model.identity().translate(s.x, s.y, 0).rotateXYZ(time + s.x, time * 0.7f, 0).scale((1 + s.hitT * 1.5f) * ss);
            litTinted(seg, tex.boss, m, tint, 0, 0, 0);
        }
        boolean tele = B.phase.equals("tele") && ((int) (time * 14)) % 2 == 1, armored = B.armored();
        Matrix4f hm = new Matrix4f().translate(B.x, B.y, 0.2f).rotateZ(B.dir + (float) Math.PI / 2).rotateY((float) Math.sin(time * 3) * 0.3f).scale(hs);
        float[] ht = {B.def.col()[0] * 2.4f, B.def.col()[1] * 2.4f, B.def.col()[2] * 2.4f};
        litTinted(head, tex.metal, hm, ht, B.hitT > 0 ? 1.5f : tele ? 0.8f : 0, B.hitT > 0 ? 0.6f : 0.1f, 0);
        unlit.use().set("uView", view).set("uProj", proj).set("uVColor", 0);
        for (float ex : new float[]{-0.28f, 0.28f}) unlitDraw(eye, new Matrix4f(hm).translate(ex, -0.25f, 0.62f), tele ? 0xffffff : armored ? 0xff3020 : 0xffb020);
        lit.use();
    }

    /** Gate, bell, foundry, lodgers and the copy. */
    private void drawOtherBoss(Game g, Boss B, String kind) {
        float[] col = B.def.col(), tint = {col[0] * 2.3f, col[1] * 2.3f, col[2] * 2.3f};
        boolean armored = B.armored();
        float hit = B.hitT > 0 ? 1.5f : 0;
        switch (kind) {
            case "gate" -> {
                for (Segment s : B.segs) if (s.alive) litTinted(cube, tex.boss, model.identity().translate(s.x, s.y, 0).rotateZ(0.1f * (float) Math.sin(time + s.x)).scale(1.1f + s.hitT, 1.1f + s.hitT, 1.2f), tint, 0, 0, 0);
                litTinted(head, tex.metal, model.identity().translate(B.x, B.y, 0).rotateXYZ(time * 0.7f, time, 0).scale(1.05f), new float[]{0.8f, 2.2f, 2.0f}, armored ? 0.3f + hit : 1.2f + hit, armored ? 1.0f : 1.6f, 1.0f);
            }
            case "bell" -> {
                float ax = 0, ay = B.y + (armored ? 0 : B.dropT * 2);
                unlit.use().set("uView", view).set("uProj", proj).set("uVColor", 0);
                for (Segment s : B.segs) if (s.alive) line(s.x, s.y, B.x, B.y + 0.8f, 0.05f, 0x3a3028);
                lit.use();
                for (Segment s : B.segs) if (s.alive) litTinted(cube, tex.metal, model.identity().translate(s.x, s.y, 0).scale(0.8f + s.hitT), new float[]{1.8f, 1.6f, 1.4f}, 0, 0, 0);
                litTinted(bellMesh, tex.metal, model.identity().translate(B.x, B.y, 0.1f).rotateZ((float) Math.sin(time * 0.9) * 0.18f * (armored ? 1 : 0)), new float[]{2.8f, 2.2f, 1.1f}, hit, hit * 0.6f, 0);
            }
            case "foundry" -> {
                for (Segment s : B.segs) if (s.alive) {
                    litTinted(cube, tex.boss, model.identity().translate(s.x, s.y, 0).scale(1.3f + s.hitT, 1.5f + s.hitT, 1.3f), tint, 0, 0, 0);
                    litTinted(vent, tex.hazard, model.identity().translate(s.x, s.y - 0.2f, 0.7f).scale(0.9f, 1.8f, 1), new float[]{0, 0, 0}, 1.6f + (float) Math.sin(time * 7 + s.x) * 0.5f, 0.45f, 0.08f);
                }
                litTinted(cube, tex.metal, model.identity().translate(B.x, B.y, -0.2f).scale(2.6f, 1.7f, 1.4f), new float[]{2.0f, 1.4f, 1.2f}, (armored ? 0.5f : 1.6f) + hit, armored ? 0.12f : 0.4f, 0.05f);
            }
            case "lodgers" -> {
                for (Segment s : B.segs) if (s.alive) litTinted(head, tex.boss, model.identity().translate(s.x, s.y, 0).rotateXYZ(time * 1.3f + s.t0, time, 0).scale(0.75f + s.hitT), new float[]{0.5f, 0.45f, 0.7f}, 0.25f + (s.lunging ? 0.8f : 0), 0.15f, 0.45f);
                if (!armored) litTinted(head, tex.boss, model.identity().translate(B.x, B.y, 0).rotateXYZ(time, time * 0.6f, 0).scale(1.2f), new float[]{0.6f, 0.5f, 0.9f}, 0.5f + hit, 0.25f, 0.8f);
            }
            case "copy" -> {                                            // a dark second rig, upside down
                Matrix4f cm = new Matrix4f().translate(B.x, B.y, 0).rotateZ((float) Math.PI).scale(g.padW / PW, 1, 1);
                float[] dark = {0.9f, 1.4f, 1.4f};
                litTinted(chassis, tex.metal, new Matrix4f(cm).translate(0, 0.05f, 0), dark, hit * 0.3f, hit, hit);
                litTinted(bumper, tex.hazard, new Matrix4f(cm).translate(0, -0.46f, 0), new float[]{0.7f, 1.6f, 1.5f}, 0.1f, 0.3f, 0.3f);
                for (Segment s : B.segs) if (s.alive) litTinted(cube, tex.metal, model.identity().translate(s.x, s.y, 0.2f).rotateZ(time * 2 + s.t0).scale(0.55f + s.hitT, 0.8f + s.hitT, 0.12f), new float[]{2.6f, 2.8f, 3.0f}, 0.4f, 0.55f, 0.6f);
            }
            default -> {}
        }
    }
    private void line(float x0, float y0, float x1, float y1, float w, int hex) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.hypot(dx, dy);
        unlitDraw(cube, model.identity().translate((x0 + x1) / 2, (y0 + y1) / 2, -0.1f).rotateZ((float) Math.atan2(dy, dx)).scale(len, w, w), hex);
    }

    private void drawCoresAndLoot(Game g) {
        unlit.use().set("uView", view).set("uProj", proj).set("uVColor", 0);
        for (Ball b : g.balls) {
            float sc = 1 + b.total() * 0.07f + b.evoCount() * 0.15f;
            if (b.stuck > 0) { unlitDraw(ball, model.identity().translate(b.x, b.y, 0.2f).scale(sc * 1.2f), 0.08f, 0.06f, 0.05f); continue; }
            float r = b.evoCount() > 0 ? 1 : 0.75f + b.col[0] * 0.35f, gg = b.evoCount() > 0 ? 1 : 0.75f + b.col[1] * 0.35f, bl = b.evoCount() > 0 ? 1 : 0.7f + b.col[2] * 0.35f;
            unlitDraw(ball, model.identity().translate(b.x, b.y, 0.2f).rotateZ(time * 9).scale(sc), r, gg, bl);
        }
        for (Loot l : g.loot) {
            if (l.kind.equals("pu")) continue;
            int col = l.kind.equals("gold") ? 0xffc43c : 0x5ae6ff;
            unlitDraw(nugget, model.identity().translate(l.x, l.y, 0.75f).rotateY(time * l.spin).rotateX(time * 2), col);
        }
        // additive glows
        FloatList o = scratch; o.clear();
        for (Ball b : g.balls) billboard(o, b.x, b.y, 0.45f, (2.4f + b.total() * 0.22f + b.evoCount() * 1.2f) / 2, b.col[0], b.col[1], b.col[2], 1, 0, 0, 1, 1);
        for (Loot l : g.loot) {
            if (l.kind.equals("pu")) { int c = pod(l.pu).color(); billboard(o, l.x, l.y, 0.7f, 0.95f, Mth.r(c) * 0.55f, Mth.g(c) * 0.55f, Mth.b(c) * 0.55f, 1, 0, 0, 1, 1); }
            else if (l.kind.equals("gold")) billboard(o, l.x, l.y, 0.7f, 0.55f, 0.6f, 0.42f, 0.06f, 1, 0, 0, 1, 1);
            else billboard(o, l.x, l.y, 0.7f, 0.55f, 0.12f, 0.48f, 0.59f, 1, 0, 0, 1, 1);
        }
        for (Drill d : g.drills) billboard(o, g.cellX(d.c), d.y - 1.1f, 0.8f, 2, 1, 0.6f, 0.25f, 1, 0, 0, 1, 1);
        for (var gh : g.ghosts) { float fl = 0.55f + 0.25f * (float) Math.sin(time * 9 + gh.x); billboard(o, gh.x, gh.y, 0.9f, 0.75f, 0.55f * fl, 0.7f * fl, 0.85f * fl, gh.alpha, 0, 0, 1, 1); }
        for (var v : g.voidLights) billboard(o, v.x, v.y, 0.9f, 1.4f, 0.55f, 0.3f, 0.95f, 1, 0, 0, 1, 1);
        for (Hazard h : g.hazards) if (h.rising && h.warn <= 0) billboard(o, h.x, h.y, 0.9f, 1.1f, 1, 0.45f, 0.1f, 1, 0, 0, 1, 1);
        if (g.boss != null && g.boss.def.kind().equals("gate")) billboard(o, g.boss.x, g.boss.y, 1.0f, 2.4f, 0.15f, 0.7f, 0.6f, 1, 0, 0, 1, 1);
        if (g.boss != null && g.boss.def.kind().equals("lodgers")) for (var sg : g.boss.segs) if (sg.alive) billboard(o, sg.x, sg.y, 0.8f, 1.2f, 0.35f, 0.25f, 0.55f, 1, 0, 0, 1, 1);
        dynGlow.upload(o);
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE); glDepthMask(false);
        sprite.use().set("uView", view).set("uProj", proj).set("uMap", 0);
        tex.glow.bind(0);
        dynGlow.draw();
        // pods: alpha-blended letter capsules
        o.clear();
        int n = PODS.size();
        for (Loot l : g.loot) if (l.kind.equals("pu")) {
            int i = podIndex.get(l.pu);
            billboardRect(o, l.x, l.y + (float) Math.sin(time * 6 + l.x) * 0.06f, 0.75f, 0.65f, 0.35f, (float) i / n, 0, (float) (i + 1) / n, 1);
        }
        dynPods.upload(o);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        podAtlas.bind(0);
        dynPods.draw();
        // laser beams
        o.clear();
        for (Panel p : g.panels) if (p.beamT > 0) {
            float x0 = Math.min(p.beamX0, p.beamX1), x1 = Math.max(p.beamX0, p.beamX1), hh = (0.6f + p.beamT * 4) * 0.07f;
            quad(o, x0, p.beamY - hh, x1, p.beamY + hh, 0.6f, 1, 0.16f, 0.1f, 1);
        }
        if (g.waterTop > -1e8f) {                                        // flooded rows
            float yTop = g.waterTop, yBot = g.rigY - 40;
            quad(o, -HALF, yBot, HALF, yTop, 0.75f, 0.05f, 0.2f, 0.26f, 1);
            quad(o, -HALF, yTop - 0.06f, HALF, yTop + 0.06f, 0.76f, 0.2f, 0.55f, 0.6f, 1);
        }
        dynBeams.upload(o);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        tex.glow.bind(0);
        sprite.set("uSolid", 1);
        dynBeams.draw();
        sprite.set("uSolid", 0);
        glDepthMask(true); glDisable(GL_BLEND);
        lit.use();
    }
    private void billboard(FloatList o, float x, float y, float z, float half, float r, float g, float b, float a, float u0, float v0, float u1, float v1) {
        billboardRect(o, x, y, z, half, half, u0, v0, u1, v1, r, g, b, a);
    }
    private void billboardRect(FloatList o, float x, float y, float z, float hw, float hh, float u0, float v0, float u1, float v1) {
        billboardRect(o, x, y, z, hw, hh, u0, v0, u1, v1, 1, 1, 1, 1);
    }
    private void billboardRect(FloatList o, float x, float y, float z, float hw, float hh, float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        float rx = camRight.x * hw, ry = camRight.y * hw, rz = camRight.z * hw, ux = camUp.x * hh, uy = camUp.y * hh, uz = camUp.z * hh;
        float[][] p = {{x - rx - ux, y - ry - uy, z - rz - uz, u0, v0}, {x + rx - ux, y + ry - uy, z + rz - uz, u1, v0},
                       {x + rx + ux, y + ry + uy, z + rz + uz, u1, v1}, {x - rx + ux, y - ry + uy, z - rz + uz, u0, v1}};
        for (int i : new int[]{0, 1, 2, 0, 2, 3}) o.add(p[i][0], p[i][1], p[i][2], a, 0, 0, p[i][3], p[i][4], r, g, b);
    }
    private void quad(FloatList o, float x0, float y0, float x1, float y1, float z, float r, float g, float b, float a) {
        float[][] p = {{x0, y0}, {x1, y0}, {x1, y1}, {x0, y1}};
        for (int i : new int[]{0, 1, 2, 0, 2, 3}) o.add(p[i][0], p[i][1], z, a, 0, 0, 0.5f, 0.5f, r, g, b);
    }

    private void drawParticles(Particles fx) {
        // debris: small coloured cubes batched into one mesh
        FloatList o = scratch; o.clear();
        float[] cubeData = CUBE;
        Matrix4f m = new Matrix4f(); Vector3f v = new Vector3f();
        for (float[] d : fx.deb) {
            if (d == null || d[8] <= 0) continue;
            float s = 0.2f * d[9] * Mth.clamp(d[8] * 2, 0, 1);
            m.identity().translate(d[0], d[1], d[2]).rotateXYZ(d[6], d[6] * 0.7f, 0).scale(s);
            for (int i = 0; i < cubeData.length; i += 3) { m.transformPosition(v.set(cubeData[i], cubeData[i + 1], cubeData[i + 2])); o.add(v.x, v.y, v.z, 0, 0, 1, 0, 0, d[10], d[11], d[12]); }
        }
        dynDebris.upload(o);
        unlit.use().set("uView", view).set("uProj", proj).set("uModel", model.identity()).set("uColor", 1, 1, 1).set("uVColor", 1);
        dynDebris.draw();
        // sparks: additive points
        o.clear();
        for (int i = 0; i < Particles.SP; i++) {
            if (fx.spLife[i] <= 0) continue;
            float f = Mth.clamp(fx.spLife[i] * 2.5f, 0, 1);
            o.add(fx.spPos[i * 3], fx.spPos[i * 3 + 1], fx.spPos[i * 3 + 2], 1, 0, 0, 0, 0, fx.spBase[i * 3] * f, fx.spBase[i * 3 + 1] * f, fx.spBase[i * 3 + 2] * f);
        }
        dynSparks.upload(o);
        glEnable(GL_BLEND); glBlendFunc(GL_ONE, GL_ONE); glDepthMask(false); glEnable(GL_PROGRAM_POINT_SIZE);
        points.use().set("uView", view).set("uProj", proj);
        dynSparks.draw(GL_POINTS);
        glDepthMask(true); glDisable(GL_BLEND);
    }
    private static final float[] CUBE;
    static {
        FloatList b = Geo.box(1, 1, 1);
        CUBE = new float[b.size() / Mesh.STRIDE * 3];
        for (int i = 0, j = 0; i < b.size(); i += Mesh.STRIDE) { CUBE[j++] = b.get(i); CUBE[j++] = b.get(i + 1); CUBE[j++] = b.get(i + 2); }
    }

    // ================================================================ shaders
    private static final String LIT_VS = """
        layout(location=0) in vec3 aPos; layout(location=1) in vec3 aNormal; layout(location=2) in vec2 aUV; layout(location=3) in vec3 aGlow;
        uniform mat4 uModel, uView, uProj;
        uniform vec4 uLightPos[8]; uniform vec3 uLightCol[8]; uniform vec3 uAmbient;
        uniform float uRigY, uSnap; uniform vec2 uRes, uUvScale, uUvOff;
        out vec3 vLight; out vec2 vUv; out float vBelow; out vec3 vGlow;
        void main(){
          vec4 wp = uModel * vec4(aPos, 1.0);
          vec3 n = normalize(mat3(uModel) * aNormal);
          vec3 L = uAmbient;
          for (int i = 0; i < 8; i++) {
            vec3 d = uLightPos[i].xyz - wp.xyz; float dist = length(d) + 1e-4;
            float a = clamp(1.0 - dist / uLightPos[i].w, 0.0, 1.0); a *= a;
            L += uLightCol[i] * a * (max(dot(n, d / dist), 0.0) * 0.75 + 0.25);
          }
          vLight = L; vUv = aUV * uUvScale + uUvOff; vBelow = uRigY - wp.y; vGlow = aGlow;
          vec4 cp = uProj * uView * wp;
          if (uSnap > 0.5) cp.xy = floor(cp.xy / cp.w * uRes + 0.5) / uRes * cp.w;   // PS1/PS2 vertex jitter
          gl_Position = cp;
        }""";
    private static final String LIT_FS = """
        uniform sampler2D uMap; uniform vec3 uTint, uEmis, uBiome, uFogColor; uniform float uFogStart, uFogRange, uTime;
        in vec3 vLight; in vec2 vUv; in float vBelow; in vec3 vGlow;
        out vec4 frag;
        void main(){
          vec4 t = texture(uMap, vUv);
          float lum = dot(vLight, vec3(0.333));
          vec3 c = t.rgb * uTint * uBiome * vLight;
          float m = 1.0 - t.a;                                  // ore fleck mask
          c += vGlow * m * (lum * 0.9 + 0.07 + 0.04 * sin(uTime * 3.0 + vUv.x * 19.0 + vUv.y * 7.0));
          c += uEmis * t.rgb;
          c = mix(c, uFogColor, smoothstep(uFogStart, uFogStart + uFogRange, vBelow));
          frag = vec4(c, 1.0);
        }""";
    private static final String UNLIT_VS = """
        layout(location=0) in vec3 aPos; layout(location=3) in vec3 aColor;
        uniform mat4 uModel, uView, uProj; out vec3 vColor;
        void main(){ vColor = aColor; gl_Position = uProj * uView * uModel * vec4(aPos, 1.0); }""";
    private static final String UNLIT_FS = """
        uniform vec3 uColor; uniform int uVColor; in vec3 vColor; out vec4 frag;
        void main(){ frag = vec4(uVColor == 1 ? vColor : uColor, 1.0); }""";
    private static final String SPRITE_VS = """
        layout(location=0) in vec3 aPos; layout(location=1) in vec3 aAlpha; layout(location=2) in vec2 aUV; layout(location=3) in vec3 aColor;
        uniform mat4 uView, uProj; out vec2 vUv; out vec4 vColor;
        void main(){ vUv = aUV; vColor = vec4(aColor, aAlpha.x); gl_Position = uProj * uView * vec4(aPos, 1.0); }""";
    private static final String SPRITE_FS = """
        uniform sampler2D uMap; uniform int uSolid; in vec2 vUv; in vec4 vColor; out vec4 frag;
        void main(){ vec4 t = uSolid == 1 ? vec4(1.0) : texture(uMap, vUv); frag = t * vColor; if (frag.a < 0.01) discard; }""";
    private static final String POINTS_VS = """
        layout(location=0) in vec3 aPos; layout(location=3) in vec3 aColor;
        uniform mat4 uView, uProj; out vec3 vColor;
        void main(){ vColor = aColor; gl_Position = uProj * uView * vec4(aPos, 1.0); gl_PointSize = 2.0; }""";
    private static final String POINTS_FS = """
        in vec3 vColor; out vec4 frag; void main(){ frag = vec4(vColor, 1.0); }""";
    private static final String POST_VS = """
        out vec2 vUv;
        void main(){ vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); vUv = p; gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0); }""";
    private static final String POST_FS = """
        uniform sampler2D tDiffuse; uniform vec2 uRes; uniform float uDither, uCRT, uFlash;
        in vec2 vUv; out vec4 frag;
        float bayer2(vec2 a){ a = floor(a); return fract(a.x / 2.0 + a.y * a.y * 0.75); }
        float bayer4(vec2 a){ return bayer2(0.5 * a) * 0.25 + bayer2(a); }
        void main(){
          vec3 c = texture(tDiffuse, vUv).rgb;
          if (uCRT > 0.5) {
            c.r = texture(tDiffuse, vUv + vec2(0.7 / uRes.x, 0.0)).r;
            c.b = texture(tDiffuse, vUv - vec2(0.7 / uRes.x, 0.0)).b;
          }
          vec2 p = floor(vUv * uRes);
          if (uDither > 0.5) c = floor(c * 31.0 + bayer4(p) * 0.999) / 31.0;   // 15-bit colour, ordered dither
          if (uCRT > 0.5) {
            c *= mod(p.y, 2.0) < 1.0 ? 1.0 : 0.8;
            vec2 v = vUv - 0.5; c *= 1.0 - 0.45 * pow(length(v) * 1.35, 2.6);
          }
          c += vec3(0.55, 0.06, 0.0) * uFlash;
          frag = vec4(c, 1.0);
        }""";
}
