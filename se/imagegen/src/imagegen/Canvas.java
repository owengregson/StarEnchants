package imagegen;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * A tiny ARGB raster — the only drawing primitive the generator needs. Java2D is used solely for PNG encode and
 * (inside the font) glyph rasterisation; all compositing here is explicit pixel math so a committed PNG is
 * byte-deterministic across machines. Blits are alpha-over; upscaling is integer NEAREST (no resampling), which is
 * what makes a 16px sprite or an 8px-tall bitmap glyph stay crisp when enlarged.
 */
public final class Canvas {

    public final int width;
    public final int height;
    private final int[] px; // ARGB8888, row-major

    public Canvas(int width, int height) {
        this.width = width;
        this.height = height;
        this.px = new int[Math.max(0, width * height)];
    }

    public int get(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return 0;
        }
        return px[y * width + x];
    }

    /** Overwrite a pixel (no blending). */
    public void put(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        px[y * width + x] = argb;
    }

    /** Alpha-composite {@code argb} OVER the existing pixel. */
    public void blend(int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        int i = y * width + x;
        px[i] = Argb.over(argb, px[i]);
    }

    public void fill(int argb) {
        Arrays.fill(px, argb);
    }

    public void fillRect(int x, int y, int w, int h, int argb) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                blend(xx, yy, argb);
            }
        }
    }

    /** A 1px-thick outline rectangle. */
    public void outline(int x, int y, int w, int h, int argb) {
        for (int xx = x; xx < x + w; xx++) {
            blend(xx, y, argb);
            blend(xx, y + h - 1, argb);
        }
        for (int yy = y; yy < y + h; yy++) {
            blend(x, yy, argb);
            blend(x + w - 1, yy, argb);
        }
    }

    /** A vertical gradient fill, {@code top}→{@code bottom} ARGB (the tooltip's purple inner border). */
    public void verticalGradient(int x, int y, int w, int h, int top, int bottom) {
        for (int row = 0; row < h; row++) {
            double t = h <= 1 ? 0 : (double) row / (h - 1);
            int c = Argb.lerp(top, bottom, t);
            for (int xx = x; xx < x + w; xx++) {
                blend(xx, y + row, c);
            }
        }
    }

    /** Alpha-over blit at 1:1. */
    public void blit(BufferedImage src, int dx, int dy) {
        if (src == null) {
            return;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                blend(dx + xx, dy + yy, src.getRGB(xx, yy));
            }
        }
    }

    /** Alpha-over blit of another canvas at 1:1. */
    public void blit(Canvas src, int dx, int dy) {
        if (src == null) {
            return;
        }
        for (int yy = 0; yy < src.height; yy++) {
            for (int xx = 0; xx < src.width; xx++) {
                blend(dx + xx, dy + yy, src.get(xx, yy));
            }
        }
    }

    /** Alpha-over blit with integer NEAREST upscale (each source pixel becomes a {@code scale}×{@code scale} block). */
    public void blitScaled(BufferedImage src, int dx, int dy, int scale) {
        if (src == null) {
            return;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                int c = src.getRGB(xx, yy);
                for (int sy = 0; sy < scale; sy++) {
                    for (int sx = 0; sx < scale; sx++) {
                        blend(dx + xx * scale + sx, dy + yy * scale + sy, c);
                    }
                }
            }
        }
    }

    /** A new canvas NEAREST-upscaled by {@code n}. */
    public Canvas scaled(int n) {
        Canvas out = new Canvas(width * n, height * n);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int c = px[y * width + x];
                for (int sy = 0; sy < n; sy++) {
                    for (int sx = 0; sx < n; sx++) {
                        out.px[(y * n + sy) * out.width + (x * n + sx)] = c;
                    }
                }
            }
        }
        return out;
    }

    public BufferedImage toImage() {
        BufferedImage img = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
        if (width > 0 && height > 0) {
            img.setRGB(0, 0, width, height, px, 0, width);
        }
        return img;
    }

    public static Canvas fromImage(BufferedImage img) {
        Canvas c = new Canvas(img.getWidth(), img.getHeight());
        img.getRGB(0, 0, c.width, c.height, c.px, 0, c.width);
        return c;
    }

    public void writePng(Path path) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            ImageIO.write(toImage(), "png", path.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("write " + path, e);
        }
    }
}
