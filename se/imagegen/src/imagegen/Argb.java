package imagegen;

/**
 * ARGB8888 packing and the handful of blend ops {@link Canvas} needs. Dependency-free and explicit so the pixel
 * math is byte-identical on every machine — no Java2D compositing/colour-management can leak nondeterminism in.
 */
public final class Argb {

    private Argb() {
    }

    public static int of(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    /** {@code 0xRRGGBB} → opaque ARGB. */
    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    public static int a(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    public static int r(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    public static int g(int argb) {
        return (argb >>> 8) & 0xFF;
    }

    public static int b(int argb) {
        return argb & 0xFF;
    }

    /** {@code src} alpha-composited OVER {@code dst} (straight alpha). */
    public static int over(int src, int dst) {
        int sa = a(src);
        if (sa == 0) {
            return dst;
        }
        if (sa == 255) {
            return src;
        }
        int da = a(dst);
        int outA = sa + da * (255 - sa) / 255;
        if (outA == 0) {
            return 0;
        }
        int outR = (r(src) * sa + r(dst) * da * (255 - sa) / 255) / outA;
        int outG = (g(src) * sa + g(dst) * da * (255 - sa) / 255) / outA;
        int outB = (b(src) * sa + b(dst) * da * (255 - sa) / 255) / outA;
        return of(outA, outR, outG, outB);
    }

    /** Linear interpolation between two ARGB colours, {@code t} in {@code [0,1]}. */
    public static int lerp(int x, int y, double t) {
        return of(
                (int) Math.round(a(x) + (a(y) - a(x)) * t),
                (int) Math.round(r(x) + (r(y) - r(x)) * t),
                (int) Math.round(g(x) + (g(y) - g(x)) * t),
                (int) Math.round(b(x) + (b(y) - b(x)) * t));
    }

    /** Multiply RGB by {@code factor} (the per-face shading of the isometric block render); alpha preserved. */
    public static int shade(int argb, double factor) {
        return of(a(argb), (int) (r(argb) * factor), (int) (g(argb) * factor), (int) (b(argb) * factor));
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }
}
