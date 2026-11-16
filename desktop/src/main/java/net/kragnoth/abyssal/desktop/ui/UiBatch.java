package net.kragnoth.abyssal.desktop.ui;

import net.kragnoth.abyssal.desktop.gl.Shader;
import net.kragnoth.abyssal.desktop.gl.Texture;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Immediate-mode 2D drawing in a virtual 640x448 space (top-left origin), scaled to the letterboxed viewport.
 * Text comes from a glyph atlas rasterised once with Java2D (no antialiasing) and magnified nearest-neighbour.
 */
public final class UiBatch {
    public static final float VW = 640, VH = 448;
    private static final int CELL = 16, ATLAS = 1024;
    private static final String EXTRA = "■□·×—↓←→✦∞☽♀♜⚷♡⚔♌⚲☸⚖⚓☠⧖⛧♖★☾☀♆⊕";

    private final Shader shader;
    private final Texture atlas;
    private final Map<Character, float[]> glyphs = new HashMap<>();   // u0 v0 u1 v1 advance(px at CELL size)
    private final float[] white = new float[4];
    private final int vao, vbo;
    private final FloatBuffer buf = MemoryUtil.memAllocFloat(8 * 6 * 20000);
    private int count;

    public UiBatch() {
        shader = new Shader("""
            layout(location=0) in vec2 aPos; layout(location=1) in vec2 aUV; layout(location=2) in vec4 aCol;
            uniform mat4 uProj; out vec2 vUv; out vec4 vCol;
            void main(){ vUv = aUV; vCol = aCol; gl_Position = uProj * vec4(aPos, 0.0, 1.0); }""", """
            uniform sampler2D uMap; in vec2 vUv; in vec4 vCol; out vec4 frag;
            void main(){ frag = texture(uMap, vUv) * vCol; if (frag.a < 0.01) discard; }""");
        vao = glGenVertexArrays(); vbo = glGenBuffers();
        glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 32, 0); glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 32, 8); glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, 32, 16); glEnableVertexAttribArray(2);
        glBindVertexArray(0);
        atlas = buildAtlas();
    }

    private Texture buildAtlas() {
        BufferedImage img = new BufferedImage(ATLAS, ATLAS, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 4, 4);                                           // solid texel for rectangles
        white[0] = 1f / ATLAS; white[1] = 1f / ATLAS; white[2] = 2f / ATLAS; white[3] = 2f / ATLAS;
        String[] families = {"DejaVu Sans Mono", "Liberation Mono", "Monospaced", "DejaVu Sans", "Noto Sans Symbols2", "Noto Sans Symbols", "Symbola", "Dialog"};
        Font[] fonts = new Font[families.length];
        for (int i = 0; i < families.length; i++) fonts[i] = new Font(families[i], Font.BOLD, 14);
        StringBuilder chars = new StringBuilder();
        for (char c = 32; c < 127; c++) chars.append(c);
        chars.append(EXTRA);
        int x = CELL, y = 0, per = ATLAS / CELL;
        int idx = 1;
        for (char c : chars.toString().toCharArray()) {
            Font f = fonts[fonts.length - 1];
            for (Font cand : fonts) if (cand.canDisplay(c)) { f = cand; break; }
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            int cx = (idx % per) * CELL, cy = (idx / per) * CELL; idx++;
            g.drawString(String.valueOf(c), cx + 1, cy + 13);
            int adv = Math.min(CELL - 1, fm.charWidth(c) + 1);
            glyphs.put(c, new float[]{(float) cx / ATLAS, (float) cy / ATLAS, (float) (cx + CELL) / ATLAS, (float) (cy + CELL) / ATLAS, adv});
        }
        g.dispose();
        byte[] rgba = new byte[ATLAS * ATLAS * 4];
        for (int yy = 0; yy < ATLAS; yy++) for (int xx = 0; xx < ATLAS; xx++) {
            int argb = img.getRGB(xx, yy), o = (yy * ATLAS + xx) * 4;      // keep image orientation; UVs are top-left based
            rgba[o] = (byte) 255; rgba[o + 1] = (byte) 255; rgba[o + 2] = (byte) 255; rgba[o + 3] = (byte) (argb >>> 24);
        }
        return new Texture(ATLAS, ATLAS, rgba, true, false);
    }

    // ---------------------------------------------------------------- frame
    private float vx, vy, vs;   // viewport origin (window px, bottom-left) and scale (window px per virtual px)
    public void begin(int[] viewport, int winH) {
        vx = viewport[0]; vy = winH - viewport[1] - viewport[3]; vs = viewport[2] / VW;
        count = 0; buf.clear();
        Matrix4f proj = new Matrix4f().setOrtho(0, viewport[2] / vs, viewport[3] / vs, 0, -1, 1);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        shader.use().set("uProj", proj).set("uMap", 0);
    }
    public void end() {
        flush();
    }
    private void flush() {
        if (count == 0) return;
        buf.flip();
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); glDisable(GL_DEPTH_TEST);
        atlas.bind(0);
        glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, buf, GL_STREAM_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, count);
        buf.clear(); count = 0;
        glDisable(GL_BLEND);
    }
    private void v(float x, float y, float u, float w, float r, float g, float b, float a) {
        if (buf.remaining() < 8) flush();
        buf.put(x).put(y).put(u).put(w).put(r).put(g).put(b).put(a); count++;
    }
    private void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        v(x0, y0, u0, v0, r, g, b, a); v(x1, y0, u1, v0, r, g, b, a); v(x1, y1, u1, v1, r, g, b, a);
        v(x0, y0, u0, v0, r, g, b, a); v(x1, y1, u1, v1, r, g, b, a); v(x0, y1, u0, v1, r, g, b, a);
    }

    // ---------------------------------------------------------------- primitives (virtual coordinates)
    public void rect(float x, float y, float w, float h, int rgb, float a) {
        quad(x, y, x + w, y + h, white[0], white[1], white[2], white[3], ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, a);
    }
    public void frame(float x, float y, float w, float h, float t, int rgb) {
        rect(x, y, w, t, rgb, 1); rect(x, y + h - t, w, t, rgb, 1); rect(x, y, t, h, rgb, 1); rect(x + w - t, y, t, h, rgb, 1);
    }
    public float width(String s, float size) {
        float k = size / CELL, w = 0;
        for (char c : s.toCharArray()) { float[] gl = glyphs.getOrDefault(c, glyphs.get('?')); w += gl[4] * k; }
        return w;
    }
    /** Draws text with a 1-px drop shadow; returns the advance. */
    public float text(String s, float x, float y, float size, int rgb) { return text(s, x, y, size, rgb, 1, true); }
    public float text(String s, float x, float y, float size, int rgb, float a, boolean shadow) {
        float k = size / CELL;
        if (shadow) drawText(s, x + Math.max(1, size / 12), y + Math.max(1, size / 12), k, 0, a);
        return drawText(s, x, y, k, rgb, a);
    }
    private float drawText(String s, float x, float y, float k, int rgb, float a) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f, cx = x;
        for (char c : s.toCharArray()) {
            float[] gl = glyphs.getOrDefault(c, glyphs.get('?'));
            if (c != ' ') quad(cx, y, cx + CELL * k, y + CELL * k, gl[0], gl[1], gl[2], gl[3], r, g, b, a);
            cx += gl[4] * k;
        }
        return cx - x;
    }
    public void textCentered(String s, float cx, float y, float size, int rgb) { text(s, cx - width(s, size) / 2, y, size, rgb); }
    public void textRight(String s, float rx, float y, float size, int rgb) { text(s, rx - width(s, size), y, size, rgb); }

    /** Greedy word wrap. */
    public java.util.List<String> wrap(String s, float size, float maxW) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : s.split(" ")) {
            String cand = line.isEmpty() ? w : line + " " + w;
            if (width(cand, size) > maxW && !line.isEmpty()) { out.add(line.toString()); line = new StringBuilder(w); }
            else line = new StringBuilder(cand);
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    /** Window pixel -> virtual coordinate. */
    public float toVX(double winX) { return (float) ((winX - vx) / vs); }
    public float toVY(double winY) { return (float) ((winY - vy) / vs); }
}
