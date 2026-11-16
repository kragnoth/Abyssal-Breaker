package net.kragnoth.abyssal.desktop.gl;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33C.*;

/** RGBA8 texture uploaded from a byte array; nearest filtering by default (the crunchy PS2 look). */
public final class Texture {
    public final int id, w, h;

    public Texture(int w, int h, byte[] rgba, boolean nearest, boolean repeat) {
        this.w = w; this.h = h;
        id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        ByteBuffer buf = MemoryUtil.memAlloc(rgba.length);
        buf.put(rgba).flip();
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        MemoryUtil.memFree(buf);
        int f = nearest ? GL_NEAREST : GL_LINEAR, wrap = repeat ? GL_REPEAT : GL_CLAMP_TO_EDGE;
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, f);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, f);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap);
    }

    public void bind(int unit) { glActiveTexture(GL_TEXTURE0 + unit); glBindTexture(GL_TEXTURE_2D, id); }
}
