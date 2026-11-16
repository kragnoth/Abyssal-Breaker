package net.kragnoth.abyssal.desktop.gl;

import java.util.Arrays;

/** Growable float array without boxing. */
public final class FloatList {
    private float[] a;
    private int n;
    public FloatList() { this(1024); }
    public FloatList(int cap) { a = new float[cap]; }
    public void add(float v) { if (n == a.length) a = Arrays.copyOf(a, n * 2); a[n++] = v; }
    public void add(float... vs) { for (float v : vs) add(v); }
    public void clear() { n = 0; }
    public int size() { return n; }
    public float[] array() { return a; }
    public float get(int i) { return a[i]; }
    public void set(int i, float v) { a[i] = v; }
}
