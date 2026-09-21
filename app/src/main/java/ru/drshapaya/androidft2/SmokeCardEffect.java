package ru.drshapaya.androidft2;

import android.graphics.*;

/** Cached translucent turbulent wisps; all positions are local to the card. */
final class SmokeCardEffect {
    private static Bitmap cloud;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF target = new RectF();
    private final Path clip = new Path();

    void behind(Canvas canvas, float width, float height, int seed, float seconds) {
        ensureCloud();
        float phase = (seed & 1023) * .017f;
        int count = (int) ((width + height) * 2 / 32);
        for (int i = 0; i < count; i++) {
            float distance = (i + .35f) / count * (width + height) * 2;
            float x, y;
            if (distance < width) { x = distance; y = 0; }
            else if ((distance -= width) < height) { x = width; y = distance; }
            else if ((distance -= height) < width) { x = width - distance; y = height; }
            else { x = 0; y = height - (distance - width); }
            float t = seconds * .55f + phase + i * 2.399f;
            x += (float) Math.sin(t) * 4;
            y += (float) Math.cos(t * .83f) * 5 - 3;
            float size = 68 + 10 * (float) Math.sin(t * .71f + i);
            puff(canvas, x, y, size, t * 16, 215);
            puff(canvas, x + 4, y - 6, size * .78f, -t * 13 + 73, 140);
        }
    }

    void surface(Canvas canvas, float width, float height, int seed) {
        ensureCloud();
        canvas.save();
        clip.reset(); clip.addRoundRect(0, 0, width, height, 10, 10, Path.Direction.CW);
        canvas.clipPath(clip);
        // Keep the centre quiet so the name and dates remain legible.
        puff(canvas, width * .78f, height, width * .85f, seed & 255, 100);
        puff(canvas, width * .18f, 0, width * .6f, 55, 95);
        canvas.restore();
    }

    void rim(Canvas canvas, float width, float height) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.argb(65, 213, 226, 234)); paint.setStrokeWidth(5);
        canvas.drawRoundRect(0, 0, width, height, 10, 10, paint);
        paint.setColor(Color.rgb(199, 216, 226)); paint.setStrokeWidth(1.5f);
        canvas.drawRoundRect(0, 0, width, height, 10, 10, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void puff(Canvas canvas, float x, float y, float size, float angle, int alpha) {
        canvas.save(); canvas.translate(x, y); canvas.rotate(angle);
        target.set(-size / 2, -size / 2, size / 2, size / 2);
        paint.setStyle(Paint.Style.FILL); paint.setAlpha(alpha);
        canvas.drawBitmap(cloud, null, target, paint);
        canvas.restore(); paint.setAlpha(255);
    }

    private static synchronized void ensureCloud() {
        if (cloud != null) return;
        int size = 192;
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            float u = x / (float) size, v = y / (float) size;
            float dx = (u - .5f) * 2, dy = (v - .5f) * 2;
            float envelope = Math.max(0, 1 - dx * dx - dy * dy);
            float warp = noise(u * 3, v * 3) * 1.8f;
            float n = noise(u * 4 + warp, v * 4 - warp) * .65f
                + noise(u * 9 - warp, v * 9 + warp) * .25f + noise(u * 18, v * 18) * .1f;
            float wisps = (float) Math.pow(Math.max(0, 1 - Math.abs(n - .48f) * 6), 2);
            int alpha = Math.round((float) Math.pow(envelope, 1.6) * (22 + 65 * n + 100 * wisps));
            int tone = Math.round(48 + n * 95);
            pixels[y * size + x] = Color.argb(alpha, tone, tone + 7, tone + 12);
        }
        cloud = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
    }

    private static float noise(float x, float y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        return mix(mix(hash(ix, iy), hash(ix + 1, iy), fx),
            mix(hash(ix, iy + 1), hash(ix + 1, iy + 1), fx), fy);
    }
    private static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 65535) / 65535f;
    }
    private static float mix(float a, float b, float f) { return a + (b - a) * f; }
}
