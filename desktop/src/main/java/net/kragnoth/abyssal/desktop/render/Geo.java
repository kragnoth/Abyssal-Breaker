package net.kragnoth.abyssal.desktop.render;

import net.kragnoth.abyssal.core.Mth;
import net.kragnoth.abyssal.desktop.gl.FloatList;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry builders. Every builder appends interleaved vertices
 * (pos3 normal3 uv2 glow3 — see Mesh.STRIDE) to a FloatList in local space.
 */
public final class Geo {
    private Geo() {}

    public static void vert(FloatList o, float x, float y, float z, float nx, float ny, float nz, float u, float v) {
        o.add(x, y, z, nx, ny, nz, u, v, 0, 0, 0);
    }

    /** Jitter applied to a box vertex; positions in, positions out. */
    public interface Warp { void apply(float[] p); }

    /**
     * Box with a segmented grid on every face. With a warp the face normals are re-smoothed per face,
     * like three.js' BoxGeometry + computeVertexNormals.
     */
    public static FloatList box(float w, float h, float d, int sx, int sy, int sz, Warp warp) {
        FloatList o = new FloatList(4096);
        // face: axis order (u, v, normal), sign
        face(o, w, h, d, sx, sy, 0, 1, 2, +1, warp); face(o, w, h, d, sx, sy, 0, 1, 2, -1, warp);   // +z / -z
        face(o, d, h, w, sz, sy, 2, 1, 0, +1, warp); face(o, d, h, w, sz, sy, 2, 1, 0, -1, warp);   // +x / -x
        face(o, w, d, h, sx, sz, 0, 2, 1, +1, warp); face(o, w, d, h, sx, sz, 0, 2, 1, -1, warp);   // +y / -y
        return o;
    }
    public static FloatList box(float w, float h, float d) { return box(w, h, d, 1, 1, 1, null); }

    private static void face(FloatList o, float du, float dv, float dn, int su, int sv, int au, int av, int an, int sign, Warp warp) {
        int nu = su + 1, nv = sv + 1;
        float[][] pos = new float[nu * nv][];
        float[][] uv = new float[nu * nv][];
        for (int j = 0; j < nv; j++) for (int i = 0; i < nu; i++) {
            float u = (float) i / su, v = (float) j / sv;
            float[] p = new float[3];
            p[au] = (u - 0.5f) * du; p[av] = (v - 0.5f) * dv; p[an] = sign * dn / 2;
            if (warp != null) warp.apply(p);
            pos[j * nu + i] = p; uv[j * nu + i] = new float[]{u, v};
        }
        // smooth normals within the face
        float[][] nrm = new float[nu * nv][3];
        List<int[]> tris = new ArrayList<>();
        for (int j = 0; j < sv; j++) for (int i = 0; i < su; i++) {
            int a = j * nu + i, b = a + 1, c = a + nu, e = c + 1;
            if (sign > 0 ^ (an == 1)) { tris.add(new int[]{a, b, e}); tris.add(new int[]{a, e, c}); }
            else { tris.add(new int[]{a, e, b}); tris.add(new int[]{a, c, e}); }
        }
        for (int[] t : tris) {
            Vector3f p0 = new Vector3f(pos[t[0]][0], pos[t[0]][1], pos[t[0]][2]);
            Vector3f e1 = new Vector3f(pos[t[1]][0], pos[t[1]][1], pos[t[1]][2]).sub(p0);
            Vector3f e2 = new Vector3f(pos[t[2]][0], pos[t[2]][1], pos[t[2]][2]).sub(p0);
            Vector3f n = e1.cross(e2);
            for (int k : t) { nrm[k][0] += n.x; nrm[k][1] += n.y; nrm[k][2] += n.z; }
        }
        for (int[] t : tris) for (int k : t) {
            Vector3f n = new Vector3f(nrm[k][0], nrm[k][1], nrm[k][2]);
            if (n.lengthSquared() < 1e-12f) n.set(an == 0 ? sign : 0, an == 1 ? sign : 0, an == 2 ? sign : 0); else n.normalize();
            float out = an == 0 ? n.x : an == 1 ? n.y : n.z;
            if (out * sign < 0) n.negate();                          // always point away from the box
            vert(o, pos[k][0], pos[k][1], pos[k][2], n.x, n.y, n.z, uv[k][0], uv[k][1]);
        }
    }

    /** Beveled, lumpy voxel: chamfered corners and a jittered front face (deterministic per seed). */
    public static FloatList block(int seed) {
        return box(0.97f, 0.97f, 1.0f, 3, 3, 1, p -> {
            int kx = Math.round(p[0] * 100), ky = Math.round(p[1] * 100), kz = Math.round(p[2] * 100);
            float x = p[0], y = p[1], z = p[2];
            if (Math.abs(x) > 0.4f && Math.abs(y) > 0.4f) { x *= 0.86f; y *= 0.86f; }
            x += (float) (Mth.hash(kx + seed, ky * 3 + kz, 99) - 0.5) * 0.05f;
            y += (float) (Mth.hash(ky + seed * 7, kx * 5 + kz, 99) - 0.5) * 0.05f;
            if (z > 0) z += (float) (Mth.hash(kx * 11 + seed, ky * 13, 99) - 0.5) * 0.16f;
            p[0] = x; p[1] = y; p[2] = z;
        });
    }

    /** Cylinder / cone along Y, centred, flat-shaded caps. */
    public static FloatList cylinder(float rTop, float rBot, float h, int radial) {
        FloatList o = new FloatList();
        float slope = (rBot - rTop) / h;
        for (int i = 0; i < radial; i++) {
            float a0 = (float) (i * Math.PI * 2 / radial), a1 = (float) ((i + 1) * Math.PI * 2 / radial);
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            Vector3f n0 = new Vector3f(c0, slope, s0).normalize(), n1 = new Vector3f(c1, slope, s1).normalize();
            float u0 = (float) i / radial, u1 = (float) (i + 1) / radial;
            vert(o, c0 * rBot, -h / 2, s0 * rBot, n0.x, n0.y, n0.z, u0, 0);
            vert(o, c1 * rTop, h / 2, s1 * rTop, n1.x, n1.y, n1.z, u1, 1);
            vert(o, c1 * rBot, -h / 2, s1 * rBot, n1.x, n1.y, n1.z, u1, 0);
            vert(o, c0 * rBot, -h / 2, s0 * rBot, n0.x, n0.y, n0.z, u0, 0);
            vert(o, c0 * rTop, h / 2, s0 * rTop, n0.x, n0.y, n0.z, u0, 1);
            vert(o, c1 * rTop, h / 2, s1 * rTop, n1.x, n1.y, n1.z, u1, 1);
            if (rTop > 0) { vert(o, 0, h / 2, 0, 0, 1, 0, 0.5f, 0.5f); vert(o, c1 * rTop, h / 2, s1 * rTop, 0, 1, 0, 0, 0); vert(o, c0 * rTop, h / 2, s0 * rTop, 0, 1, 0, 1, 0); }
            if (rBot > 0) { vert(o, 0, -h / 2, 0, 0, -1, 0, 0.5f, 0.5f); vert(o, c0 * rBot, -h / 2, s0 * rBot, 0, -1, 0, 0, 0); vert(o, c1 * rBot, -h / 2, s1 * rBot, 0, -1, 0, 1, 0); }
        }
        return o;
    }

    private static final float PHI = (float) ((1 + Math.sqrt(5)) / 2);
    private static float[][] icoVerts() {
        float[][] v = {{-1, PHI, 0}, {1, PHI, 0}, {-1, -PHI, 0}, {1, -PHI, 0}, {0, -1, PHI}, {0, 1, PHI}, {0, -1, -PHI}, {0, 1, -PHI},
                       {PHI, 0, -1}, {PHI, 0, 1}, {-PHI, 0, -1}, {-PHI, 0, 1}};
        for (float[] p : v) { float l = (float) Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]); p[0] /= l; p[1] /= l; p[2] /= l; }
        return v;
    }
    private static final int[][] ICO_F = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
                                          {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};

    private static void flatTri(FloatList o, float[] a, float[] b, float[] c) {
        Vector3f n = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]).cross(c[0] - a[0], c[1] - a[1], c[2] - a[2]).normalize();
        if (n.dot(a[0] + b[0] + c[0], a[1] + b[1] + c[1], a[2] + b[2] + c[2]) < 0) n.negate();   // convex solids: face outward
        for (float[] p : new float[][]{a, b, c}) {
            float u = (float) (Math.atan2(p[2], p[0]) / (Math.PI * 2) + 0.5), v = (float) (Math.asin(Mth.clamp(p[1] / (float) Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]), -1, 1)) / Math.PI + 0.5);
            vert(o, p[0], p[1], p[2], n.x, n.y, n.z, u * 2, v * 2);
        }
    }

    /** Flat-shaded icosahedron (cores, wyrm plates). */
    public static FloatList icosahedron(float r) {
        float[][] v = icoVerts(); FloatList o = new FloatList();
        for (int[] f : ICO_F) flatTri(o, scale(v[f[0]], r), scale(v[f[1]], r), scale(v[f[2]], r));
        return o;
    }

    /** Flat-shaded dodecahedron built as the dual of the icosahedron (the wyrm's head). */
    public static FloatList dodecahedron(float r) {
        float[][] v = icoVerts(); FloatList o = new FloatList();
        float[][] cent = new float[ICO_F.length][];
        for (int i = 0; i < ICO_F.length; i++) {
            int[] f = ICO_F[i]; float[] c = new float[3];
            for (int k : f) for (int j = 0; j < 3; j++) c[j] += v[k][j] / 3;
            float l = (float) Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]);
            cent[i] = new float[]{c[0] / l * r, c[1] / l * r, c[2] / l * r};
        }
        for (int vi = 0; vi < v.length; vi++) {
            List<float[]> ring = new ArrayList<>();
            for (int i = 0; i < ICO_F.length; i++) for (int k : ICO_F[i]) if (k == vi) ring.add(cent[i]);
            Vector3f axis = new Vector3f(v[vi][0], v[vi][1], v[vi][2]);
            Vector3f ref = new Vector3f(ring.get(0)[0], ring.get(0)[1], ring.get(0)[2]).sub(new Vector3f(axis).mul(r * 0.8f)).normalize();
            Vector3f ref2 = new Vector3f(axis).cross(ref);
            ring.sort((p, q) -> Double.compare(Math.atan2(dot(p, ref2), dot(p, ref)), Math.atan2(dot(q, ref2), dot(q, ref))));
            for (int i = 1; i < ring.size() - 1; i++) flatTri(o, ring.get(0), ring.get(i), ring.get(i + 1));
        }
        return o;
    }
    private static float dot(float[] p, Vector3f a) { return p[0] * a.x + p[1] * a.y + p[2] * a.z; }
    private static float[] scale(float[] p, float r) { return new float[]{p[0] * r, p[1] * r, p[2] * r}; }

    public static FloatList octahedron(float r) {
        float[][] v = {{r, 0, 0}, {-r, 0, 0}, {0, r, 0}, {0, -r, 0}, {0, 0, r}, {0, 0, -r}};
        int[][] f = {{0, 2, 4}, {2, 1, 4}, {1, 3, 4}, {3, 0, 4}, {2, 0, 5}, {1, 2, 5}, {3, 1, 5}, {0, 3, 5}};
        FloatList o = new FloatList();
        for (int[] t : f) flatTri(o, v[t[0]], v[t[1]], v[t[2]]);
        return o;
    }

    /** Unit-height coil spring along Y (scale Y to stretch it). */
    public static FloatList coil(float radius, float tube, int turns, int steps, int radial) {
        FloatList o = new FloatList(steps * radial * 66);
        Vector3f[] pts = new Vector3f[steps + 1];
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps, a = t * Math.PI * 2 * turns;
            pts[i] = new Vector3f((float) Math.cos(a) * radius, (float) t - 0.5f, (float) Math.sin(a) * radius);
        }
        for (int i = 0; i < steps; i++) {
            Vector3f p0 = pts[i], p1 = pts[i + 1];
            Vector3f t = new Vector3f(p1).sub(p0).normalize();
            Vector3f n = new Vector3f(p0.x, 0, p0.z).normalize(), b = new Vector3f(t).cross(n).normalize();
            Vector3f n1 = new Vector3f(p1.x, 0, p1.z).normalize(), b1 = new Vector3f(t).cross(n1).normalize();
            for (int k = 0; k < radial; k++) {
                double a0 = k * Math.PI * 2 / radial, a1 = (k + 1) * Math.PI * 2 / radial;
                Vector3f d00 = ring(n, b, a0), d01 = ring(n, b, a1), d10 = ring(n1, b1, a0), d11 = ring(n1, b1, a1);
                quad(o, new Vector3f(p0).fma(tube, d00), d00, new Vector3f(p0).fma(tube, d01), d01, new Vector3f(p1).fma(tube, d11), d11, new Vector3f(p1).fma(tube, d10), d10);
            }
        }
        return o;
    }
    private static Vector3f ring(Vector3f n, Vector3f b, double a) { return new Vector3f(n).mul((float) Math.cos(a)).add(new Vector3f(b).mul((float) Math.sin(a))); }
    private static void quad(FloatList o, Vector3f a, Vector3f na, Vector3f b, Vector3f nb, Vector3f c, Vector3f nc, Vector3f d, Vector3f nd) {
        vert(o, a.x, a.y, a.z, na.x, na.y, na.z, 0, 0); vert(o, b.x, b.y, b.z, nb.x, nb.y, nb.z, 1, 0); vert(o, c.x, c.y, c.z, nc.x, nc.y, nc.z, 1, 1);
        vert(o, a.x, a.y, a.z, na.x, na.y, na.z, 0, 0); vert(o, c.x, c.y, c.z, nc.x, nc.y, nc.z, 1, 1); vert(o, d.x, d.y, d.z, nd.x, nd.y, nd.z, 0, 1);
    }

    /** Segmented plane facing +Z. */
    public static FloatList plane(float w, float h, int seg) {
        return box(w, h, 0.0001f, seg, seg, 1, null);
    }
}
