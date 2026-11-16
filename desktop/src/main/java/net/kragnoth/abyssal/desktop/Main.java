package net.kragnoth.abyssal.desktop;

import net.kragnoth.abyssal.core.Defs;
import net.kragnoth.abyssal.core.Fx;
import net.kragnoth.abyssal.core.Game;
import net.kragnoth.abyssal.core.Game.State;
import net.kragnoth.abyssal.core.SaveStore;
import net.kragnoth.abyssal.desktop.audio.Audio;
import net.kragnoth.abyssal.desktop.render.Particles;
import net.kragnoth.abyssal.desktop.render.Renderer;
import net.kragnoth.abyssal.desktop.ui.Screens;
import net.kragnoth.abyssal.desktop.ui.UiBatch;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 * Abyssal Breaker desktop launcher.
 * Options: --windowed WxH, --fullscreen, --demo (bot plays), --boss N (jump to wyrm N, 0-3),
 * --screen shop|junction|foundry|over|victory (open a menu), --shot FILE --shot-after SECONDS (screenshot then exit).
 */
public final class Main {
    private long window;
    private Game game;
    private Renderer renderer;
    private Particles particles;
    private Audio audio;
    private UiBatch ui;
    private Screens screens;
    private final int[] viewport = new int[4];
    private int winW = 1280, winH = 896;
    private boolean fullscreen, running = true;
    private int[] savedWin = {100, 100, 1280, 896};
    private double mouseX, mouseY;

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        new Main().run(args);
    }

    private static Path savePath() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return Paths.get(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "AbyssalBreaker", "save.json");
        if (os.contains("mac")) return Paths.get(System.getProperty("user.home"), "Library", "Application Support", "AbyssalBreaker", "save.json");
        String xdg = System.getenv("XDG_DATA_HOME");
        return (xdg != null && !xdg.isBlank() ? Paths.get(xdg) : Paths.get(System.getProperty("user.home"), ".local", "share")).resolve("abyssal-breaker").resolve("save.json");
    }

    private void run(String[] args) {
        boolean demo = false; int boss = -1; String shot = null, screen = null; double shotAfter = 3;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--fullscreen" -> fullscreen = true;
                case "--windowed" -> { String[] wh = args[++i].split("x"); winW = Integer.parseInt(wh[0]); winH = Integer.parseInt(wh[1]); }
                case "--demo" -> demo = true;
                case "--boss" -> boss = Integer.parseInt(args[++i]);
                case "--shot" -> shot = args[++i];
                case "--screen" -> screen = args[++i];
                case "--shot-after" -> shotAfter = Double.parseDouble(args[++i]);
                default -> System.err.println("unknown option " + args[i]);
            }
        }

        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("GLFW init failed");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        window = glfwCreateWindow(winW, winH, "Abyssal Breaker", 0, 0);
        if (window == 0) throw new IllegalStateException("could not create an OpenGL 3.3 core window");
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        GL.createCapabilities();
        System.out.println("[abyssal] GL " + glGetString(GL_VERSION) + " on " + glGetString(GL_RENDERER));
        if (fullscreen) { fullscreen = false; toggleFullscreen(); }

        particles = new Particles();
        audio = new Audio();
        renderer = new Renderer();
        ui = new UiBatch();
        Fx fx = new Fx() {
            public void sound(Sfx s, int pitch) { audio.play(s, pitch); }
            public void burst(float x, float y, int n, float r, float g, float b, float sp, float life) { particles.burst(x, y, n, r, g, b, sp, life); }
            public void spark(float x, float y, float z, float vx, float vy, float r, float g, float b, float life) { particles.spark(x, y, z, vx, vy, r, g, b, life); }
            public void debris(float x, float y, int color, int n) { particles.debris(x, y, color, n); }
            public void flash(float x, float y, float r, float g, float b, float rad, float life) { particles.flash(x, y, r, g, b, rad, life); }
            public void message(String t, int col, float s) { if (screens != null) screens.message(t, col, s); }
        };
        game = new Game(fx, SaveStore.file(savePath()), System.nanoTime());
        screens = new Screens(ui, game);
        screens.onQuit = () -> running = false;
        installInput();

        if (demo || boss >= 0) { game.start(); game.bot = new Game.Bot(0.7f); }
        if (boss >= 0) game.debugBossScene(boss);
        if (screen != null) game.debugScreen(screen);

        double last = glfwGetTime(), started = last, titleT = 0;
        while (running && !glfwWindowShouldClose(window)) {
            double now = glfwGetTime();
            float dt = (float) Math.min(now - last, 0.05);
            last = now;
            int[] w = new int[1], h = new int[1];
            glfwGetFramebufferSize(window, w, h);
            winW = Math.max(1, w[0]); winH = Math.max(1, h[0]);
            float scale = Math.min(winW / (float) Defs.VIEW_W, winH / (float) Defs.VIEW_H);
            viewport[2] = Math.round(Defs.VIEW_W * scale); viewport[3] = Math.round(Defs.VIEW_H * scale);
            viewport[0] = (winW - viewport[2]) / 2; viewport[1] = (winH - viewport[3]) / 2;

            if (game.state == State.TITLE) { titleT += dt; game.px = (float) Math.sin(titleT * 0.6) * 3; }
            if (demo) autoplayMenus();
            game.update(dt);
            game.updateVisuals(dt);
            particles.update(dt);
            audio.frame(dt, game.state == State.PLAY);
            glfwSetInputMode(window, GLFW_CURSOR, game.state == State.PLAY ? GLFW_CURSOR_HIDDEN : GLFW_CURSOR_NORMAL);

            renderer.render(game, particles, dt, winW, winH, viewport);
            ui.begin(viewport, winH);
            screens.draw(dt);
            ui.end();

            if (shot != null && now - started >= shotAfter) { screenshot(shot); running = false; }
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
        audio.close();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    /** --demo: let the bot drive menus too. */
    private void autoplayMenus() {
        switch (game.state) {
            case JUNCTION -> game.closeJunction(true);
            case SHOP -> game.closeShop();
            case VICTORY -> game.closeVictory(true);
            case OVER -> { if (game.save.pending != null) game.tarotChoice("pass"); else { game.start(); game.bot = new Game.Bot(0.7f); } }
            default -> {}
        }
    }

    private void screenshot(String file) {
        glFinish();
        ByteBuffer px = MemoryUtil.memAlloc(winW * winH * 4);
        glReadPixels(0, 0, winW, winH, GL_RGBA, GL_UNSIGNED_BYTE, px);
        BufferedImage img = new BufferedImage(winW, winH, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < winH; y++) for (int x = 0; x < winW; x++) {
            int o = ((winH - 1 - y) * winW + x) * 4;
            img.setRGB(x, y, ((px.get(o) & 255) << 16) | ((px.get(o + 1) & 255) << 8) | (px.get(o + 2) & 255));
        }
        MemoryUtil.memFree(px);
        try { ImageIO.write(img, "png", new File(file)); System.out.println("[abyssal] wrote " + file); }
        catch (Exception e) { System.err.println("[abyssal] screenshot failed: " + e); }
    }

    private void toggleFullscreen() {
        long mon = glfwGetPrimaryMonitor();
        GLFWVidMode mode = glfwGetVideoMode(mon);
        if (!fullscreen) {
            int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
            glfwGetWindowPos(window, x, y); glfwGetWindowSize(window, w, h);
            savedWin = new int[]{x[0], y[0], w[0], h[0]};
            glfwSetWindowMonitor(window, mon, 0, 0, mode.width(), mode.height(), mode.refreshRate());
        } else glfwSetWindowMonitor(window, 0, savedWin[0], savedWin[1], savedWin[2], savedWin[3], 0);
        fullscreen = !fullscreen;
    }

    // ================================================================ input
    private void installInput() {
        glfwSetCursorPosCallback(window, (win, x, y) -> {
            float sx = scaleX(), sy = scaleY();
            mouseX = x * sx; mouseY = y * sy;
            float fx = (float) ((mouseX - viewport[0]) / viewport[2]), fy = (float) ((mouseY - (winH - viewport[1] - viewport[3])) / viewport[3]);
            game.pointer(fx, fy);
        });
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (action != GLFW_PRESS) return;
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (screens.click(ui.toVX(mouseX), ui.toVY(mouseY))) return;
                if (game.state == State.PLAY) game.launch();
            } else if (button == GLFW_MOUSE_BUTTON_RIGHT && game.state == State.PLAY) game.startDrop();
        });
        glfwSetKeyCallback(window, (win, key, sc, action, mods) -> {
            String dir = switch (key) {
                case GLFW_KEY_A, GLFW_KEY_LEFT -> "left"; case GLFW_KEY_D, GLFW_KEY_RIGHT -> "right";
                case GLFW_KEY_W, GLFW_KEY_UP -> "up"; case GLFW_KEY_S, GLFW_KEY_DOWN -> "down";
                default -> null;
            };
            if (dir != null && action != GLFW_REPEAT) game.key(dir, action == GLFW_PRESS);
            if (action != GLFW_PRESS) return;
            if (key == GLFW_KEY_F11) { toggleFullscreen(); return; }
            if (key == GLFW_KEY_M) { audio.muted = !audio.muted; return; }
            switch (game.state) {
                case TITLE -> { if (key == GLFW_KEY_SPACE || key == GLFW_KEY_ENTER) game.start(); if (key == GLFW_KEY_F) game.openFoundry(); if (key == GLFW_KEY_ESCAPE) running = false; }
                case PLAY -> {
                    if (key == GLFW_KEY_SPACE) game.launch();
                    if (key == GLFW_KEY_E) game.startDrop();
                    if (key == GLFW_KEY_P || key == GLFW_KEY_ESCAPE) game.togglePause();
                    if (key == GLFW_KEY_C) renderer.crt = !renderer.crt;
                    if (key == GLFW_KEY_J) renderer.jitter = !renderer.jitter;
                }
                case PAUSED -> { if (key == GLFW_KEY_P || key == GLFW_KEY_ESCAPE) game.togglePause(); }
                case JUNCTION -> { if (key == GLFW_KEY_B) game.closeJunction(true); if (key == GLFW_KEY_G) game.closeJunction(false); }
                case SHOP -> {
                    if (key >= GLFW_KEY_1 && key <= GLFW_KEY_4) game.buyOffer(key - GLFW_KEY_1);
                    if (key == GLFW_KEY_5) game.repair();
                    if (key == GLFW_KEY_R) game.reroll();
                    if (key == GLFW_KEY_ENTER || key == GLFW_KEY_SPACE) game.closeShop();
                }
                case FOUNDRY -> { if (key == GLFW_KEY_ESCAPE || key == GLFW_KEY_F) game.closeFoundry(); }
                case OVER -> {
                    if (game.save.pending != null) {
                        if (key == GLFW_KEY_K) game.tarotChoice("keep");
                        if (key == GLFW_KEY_P) game.tarotChoice("pass");
                        if (key >= GLFW_KEY_1 && key <= GLFW_KEY_3 && game.save.hand.size() >= 3) game.tarotChoice("r" + (key - GLFW_KEY_1));
                    } else {
                        if (key == GLFW_KEY_R) game.start();
                        if (key == GLFW_KEY_F) game.openFoundry();
                    }
                }
                case VICTORY -> { if (key == GLFW_KEY_S) game.closeVictory(false); if (key == GLFW_KEY_E) game.closeVictory(true); }
            }
        });
    }
    /** Cursor callbacks are in window coordinates; the viewport is in framebuffer pixels (HiDPI). */
    private float scaleX() { int[] w = new int[1], h = new int[1]; glfwGetWindowSize(window, w, h); return w[0] > 0 ? winW / (float) w[0] : 1; }
    private float scaleY() { int[] w = new int[1], h = new int[1]; glfwGetWindowSize(window, w, h); return h[0] > 0 ? winH / (float) h[0] : 1; }
}
