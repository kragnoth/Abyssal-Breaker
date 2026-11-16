package net.kragnoth.abyssal.desktop.gl;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Interleaved triangle mesh: position(3) normal(3) uv(2) glow(3) = 11 floats per vertex.
 * Attribute locations are fixed (0..3) so every shader binds them the same way.
 * Static meshes upload once; dynamic meshes re-upload each frame via {@link #upload}.
 */
public final class Mesh {
    public static final int STRIDE = 11;
    public final int vao, vbo;
    public int count;
    private final boolean dynamic;

    public Mesh(boolean dynamic) {
        this.dynamic = dynamic;
        vao = glGenVertexArrays(); vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        int s = STRIDE * 4;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, s, 0);  glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, s, 12); glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, s, 24); glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 3, GL_FLOAT, false, s, 32); glEnableVertexAttribArray(3);
        glBindVertexArray(0);
    }

    public static Mesh of(FloatList data) { Mesh m = new Mesh(false); m.upload(data); return m; }

    public void upload(FloatList data) {
        count = data.size() / STRIDE;
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        if (count == 0) return;
        FloatBuffer fb = MemoryUtil.memAllocFloat(data.size());
        fb.put(data.array(), 0, data.size()).flip();
        glBufferData(GL_ARRAY_BUFFER, fb, dynamic ? GL_STREAM_DRAW : GL_STATIC_DRAW);
        MemoryUtil.memFree(fb);
    }

    public void draw() { draw(GL_TRIANGLES); }
    public void draw(int mode) {
        if (count == 0) return;
        glBindVertexArray(vao);
        glDrawArrays(mode, 0, count);
    }

    public void delete() { glDeleteBuffers(vbo); glDeleteVertexArrays(vao); }
}
