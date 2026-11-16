package net.kragnoth.abyssal.desktop.gl;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33C.*;

/** A linked GLSL program with cached uniform locations. */
public final class Shader {
    public final int id;
    private final Map<String, Integer> locs = new HashMap<>();

    public Shader(String vs, String fs) {
        int v = compile(GL_VERTEX_SHADER, vs), f = compile(GL_FRAGMENT_SHADER, fs);
        id = glCreateProgram();
        glAttachShader(id, v); glAttachShader(id, f);
        glLinkProgram(id);
        if (glGetProgrami(id, GL_LINK_STATUS) == GL_FALSE) throw new IllegalStateException("link failed:\n" + glGetProgramInfoLog(id));
        glDeleteShader(v); glDeleteShader(f);
    }

    private static int compile(int type, String src) {
        int s = glCreateShader(type);
        glShaderSource(s, "#version 330 core\n" + src);
        glCompileShader(s);
        if (glGetShaderi(s, GL_COMPILE_STATUS) == GL_FALSE) throw new IllegalStateException("shader compile failed:\n" + glGetShaderInfoLog(s) + "\n" + src);
        return s;
    }

    public Shader use() { glUseProgram(id); return this; }
    public int loc(String name) { return locs.computeIfAbsent(name, n -> glGetUniformLocation(id, n)); }
    public int attrib(String name) { return glGetAttribLocation(id, name); }
    public Shader set(String n, float v) { glUniform1f(loc(n), v); return this; }
    public Shader set(String n, int v) { glUniform1i(loc(n), v); return this; }
    public Shader set(String n, float a, float b) { glUniform2f(loc(n), a, b); return this; }
    public Shader set(String n, float a, float b, float c) { glUniform3f(loc(n), a, b, c); return this; }
    public Shader set(String n, float a, float b, float c, float d) { glUniform4f(loc(n), a, b, c, d); return this; }
    public Shader set(String n, float[] v3) { glUniform3f(loc(n), v3[0], v3[1], v3[2]); return this; }
    public Shader set(String n, Matrix4f m) {
        try (MemoryStack st = MemoryStack.stackPush()) { glUniformMatrix4fv(loc(n), false, m.get(st.mallocFloat(16))); }
        return this;
    }
    public Shader set3v(String n, float[] data) { glUniform3fv(loc(n), data); return this; }
    public Shader set4v(String n, float[] data) { glUniform4fv(loc(n), data); return this; }
}
