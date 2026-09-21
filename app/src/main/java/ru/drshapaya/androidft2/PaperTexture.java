package ru.drshapaya.androidft2;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import java.util.Random;

/** Seamless paper grain and fine fibres. The shader follows the tree transform. */
final class PaperTexture {
    private static final int SIZE = 512;
    private static Bitmap lightTile, darkTile;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private BitmapShader light, dark;

    void draw(Canvas canvas, int width, int height, float zoom, float x, float y, boolean night) {
        BitmapShader shader = night ? dark : light;
        if (shader == null) {
            shader = new BitmapShader(tile(night), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            if (night) dark = shader; else light = shader;
        }
        matrix.setScale(zoom, zoom);
        matrix.postTranslate(x, y);
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
    }

    private static synchronized Bitmap tile(boolean dark) {
        Bitmap cached = dark ? darkTile : lightTile;
        if (cached != null) return cached;
        int[] pixels = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            // Periodic, smoothly interpolated pulp density plus fine grain.
            float pulp = noise(x, y, 64) * 3.5f + noise(x, y, 16) * 2.3f;
            float grain = noise(x, y, 2) * 2.7f + hash(x, y) * 2f;
            int shade = Math.round(pulp + grain);
            pixels[y * SIZE + x] = dark
                ? Color.rgb(35 + shade, 36 + shade, 34 + shade)
                : Color.rgb(Math.min(255, 245 + shade), 241 + shade, 229 + shade);
        }
        Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
        Canvas canvas = new Canvas(bitmap);
        Paint fibre = new Paint(Paint.ANTI_ALIAS_FLAG);
        fibre.setStyle(Paint.Style.STROKE);
        fibre.setStrokeCap(Paint.Cap.ROUND);
        Path path = new Path();
        Random random = new Random(734091L);
        for (int i = 0; i < 3600; i++) {
            float x = random.nextFloat() * SIZE, y = random.nextFloat() * SIZE;
            double angle = random.nextDouble() * Math.PI * 2;
            float length = 2f + random.nextFloat() * 9f;
            float dx = (float) Math.cos(angle) * length, dy = (float) Math.sin(angle) * length;
            fibre.setStrokeWidth(0.3f + random.nextFloat() * 0.55f);
            fibre.setColor(i % 3 == 0 ? Color.argb(dark ? 13 : 36, 255, 255, 245)
                : Color.argb(dark ? 24 : 16, 70, 53, 29));
            // Wrap fibres at the tile edges instead of clipping their ends.
            for (int oy = -1; oy <= 1; oy++) for (int ox = -1; ox <= 1; ox++) {
                float px = x + ox * SIZE, py = y + oy * SIZE;
                if (px < -12 || px > SIZE + 12 || py < -12 || py > SIZE + 12) continue;
                path.reset(); path.moveTo(px, py);
                path.quadTo(px + dx * 0.45f - dy * 0.12f, py + dy * 0.45f + dx * 0.12f,
                    px + dx, py + dy);
                canvas.drawPath(path, fibre);
            }
        }
        bitmap.setHasMipMap(true);
        if (dark) darkTile = bitmap; else lightTile = bitmap;
        return bitmap;
    }

    private static float noise(int x, int y, int cell) {
        int count = SIZE / cell;
        int gx = x / cell, gy = y / cell;
        float fx = (x % cell) / (float) cell, fy = (y % cell) / (float) cell;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        float a = hash(gx, gy), b = hash((gx + 1) % count, gy);
        float c = hash(gx, (gy + 1) % count), d = hash((gx + 1) % count, (gy + 1) % count);
        return a + (b - a) * fx + (c + (d - c) * fx - a - (b - a) * fx) * fy;
    }

    private static float hash(int x, int y) {
        int n = x * 374761393 + y * 668265263 + 734091;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) & 65535) / 32767.5f - 1f;
    }
}
