package ru.drshapaya.androidft2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

/** Local card coordinates keep every flame attached while panning or zooming.
 * The caller supplies one time for the whole frame, or a fixed time for export. */
final class FireCardEffect {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path flame = new Path();
    private final Shader outer = new LinearGradient(0, 0, 0, -1,
        new int[] {0xffffb000, 0xfffa5a05, 0xb8ff3300, 0x00ff3000},
        new float[] {0, 0.32f, 0.7f, 1}, Shader.TileMode.CLAMP);
    private final Shader inner = new LinearGradient(0, 0, 0, -1,
        new int[] {0xfffff7a0, 0xffffd324, 0xdfff9400, 0x00ff6400},
        new float[] {0, 0.25f, 0.68f, 1}, Shader.TileMode.CLAMP);

    void behind(Canvas canvas, float width, float height, int seed, float seconds) {
        float phase = phase(seed);
        float pulse = 0.9f + 0.1f * wave(seconds * 2.4f + phase);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        // Concentric translucent strokes approximate soft light on both GPU/PDF Canvas.
        for (int i = 9; i >= 1; i--) {
            paint.setStrokeWidth(i * 3.7f);
            paint.setColor(Color.argb(Math.round((9 + (9 - i) * 2.4f) * pulse), 255, 82, 0));
            canvas.drawRoundRect(-1, -1, width + 1, height + 1, 11, 11, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        int count = Math.round(width / 14f);
        for (int i = 0; i < count; i++) {
            float p = phase + i * 2.39996f;
            float x = 8 + (width - 16) * (i + 0.5f) / count;
            float heightFlame = 10 + 26 * (0.5f + 0.5f * wave(p * 1.73f));
            heightFlame *= 0.76f + 0.24f * wave(seconds * 3.1f + p);
            tongue(canvas, x, 2, 6.2f + 2.5f * (0.5f + 0.5f * wave(p)), heightFlame,
                seconds, p, wave(seconds * 2 + p) * 9);
        }
        int sides = Math.round(height / 18);
        for (int i = 0; i < sides; i++) {
            float y = 13 + (height - 22) * (i + 0.5f) / sides;
            float p = phase + i * 3.31f;
            tongue(canvas, 1, y, 5, 13 + 11 * (0.5f + 0.5f * wave(seconds * 3 + p)), seconds, p, -28);
            tongue(canvas, width - 1, y, 5, 13 + 11 * (0.5f + 0.5f * wave(seconds * 2.7f + p + 2)), seconds, p + 2, 28);
        }
        drawSparks(canvas, width, height, phase, seconds);
    }

    private void drawSparks(Canvas canvas, float width, float height, float phase, float seconds) {
        // Long-lived embers travel slowly around all four sides instead of
        // popping at fixed screen coordinates. The deterministic phase keeps
        // every card unique while zoom and pan leave the animation stable.
        paint.setShader(null);
        for (int i = 0; i < 24; i++) {
            float speed = .075f + (i % 5) * .014f;
            float life = fractional(seconds * speed + phase * .07f + i * .173f);
            float along = fractional(i * .6180339f + phase * .013f + life * .12f);
            float x;
            float y;
            int side = i & 3;
            if (side == 0) {
                x = 8f + (width - 16f) * along;
                y = -5f - life * 34f;
            } else if (side == 1) {
                x = width + 5f + life * 34f;
                y = 8f + (height - 16f) * along;
            } else if (side == 2) {
                x = width - 8f - (width - 16f) * along;
                y = height + 5f + life * 30f;
            } else {
                x = -5f - life * 30f;
                y = height - 8f - (height - 16f) * along;
            }
            float drift = wave(seconds * .9f + i * 1.73f) * (1.5f + life * 2.5f);
            if ((side & 1) == 0) x += drift; else y += drift;
            float fade = (float) Math.sin(life * Math.PI);
            int alpha = Math.round(205f * fade);
            float radius = .65f + .6f * (1f - life);
            paint.setColor(Color.argb(Math.max(0, alpha / 5), 255, 74, 0));
            canvas.drawCircle(x, y, radius * 3.4f, paint);
            paint.setColor(Color.argb(alpha, 255, 196 + (i % 3) * 12, 48));
            canvas.drawCircle(x, y, radius, paint);
            if (i % 3 == 0 && alpha > 24) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(.7f);
                paint.setColor(Color.argb(Math.min(230, alpha + 25), 255, 237, 143));
                canvas.drawLine(x - radius * 2.6f, y, x + radius * 2.6f, y, paint);
                canvas.drawLine(x, y - radius * 2.6f, x, y + radius * 2.6f, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
    }

    void surface(Canvas canvas, float width, float height, int seed, float seconds) {
        float phase = phase(seed);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 18; i++) {
            float life = fractional(seconds * (.08f + (i % 4) * .015f) + phase + i * .41f);
            float along = fractional(i * .7548776f + phase * .02f);
            boolean top = (i & 1) == 0;
            float x = 10f + (width - 20f) * along;
            float y = top
                ? 7f + life * 13f
                : height - 7f - life * 13f;
            float fade = (float) Math.sin(life * Math.PI);
            int alpha = Math.round(72f * fade);
            paint.setColor(Color.argb(alpha / 3, 255, 73, 0));
            canvas.drawCircle(x, y, 5.5f, paint);
            paint.setColor(Color.argb(alpha, 255, 184 + (i % 2) * 20, 45));
            canvas.drawCircle(x, y, .85f + .35f * (1f - life), paint);
        }
    }

    private void tongue(Canvas canvas, float x, float y, float halfWidth, float height,
                        float seconds, float phase, float rotation) {
        float bend = wave(seconds * 3.7f + phase) * 0.8f;
        float curl = wave(seconds * 5.1f + phase * 1.3f) * 0.45f;
        // Curved narrowing tip, with independent sway of its base and middle.
        flame.reset(); flame.moveTo(-1, 0);
        flame.cubicTo(-1.35f, -0.24f, curl - 0.2f, -0.38f, bend - 0.3f, -0.62f);
        flame.cubicTo(bend - 0.55f, -0.79f, bend + 0.5f, -0.83f, bend + 0.25f, -1);
        flame.cubicTo(bend + 1.25f, -0.73f, curl + 0.05f, -0.51f, 0.8f, -0.28f);
        flame.quadTo(1.35f, -0.12f, 1, 0);
        flame.close();
        canvas.save(); canvas.translate(x, y); canvas.rotate(rotation);
        canvas.scale(halfWidth, height);
        canvas.save(); canvas.scale(1.35f, 1.12f);
        paint.setColor(Color.argb(40, 255, 255, 255));
        paint.setShader(outer); canvas.drawPath(flame, paint);
        canvas.restore();
        paint.setColor(Color.argb(225, 255, 255, 255));
        paint.setShader(outer); canvas.drawPath(flame, paint);
        canvas.scale(0.56f, 0.72f);
        paint.setShader(inner); canvas.drawPath(flame, paint);
        paint.setShader(null); canvas.restore();
    }

    void rim(Canvas canvas, float width, float height, int seed, float seconds) {
        float shimmer = 0.92f + 0.08f * wave(seconds * 2.4f + phase(seed));
        paint.setShader(null); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(12);
        paint.setColor(Color.argb(65, 255, 59, 0));
        canvas.drawRoundRect(1, 1, width - 1, height - 1, 10, 10, paint);
        paint.setStrokeWidth(6);
        paint.setColor(Color.argb(220, 255, 107, 0));
        canvas.drawRoundRect(0, 0, width, height, 10, 10, paint);
        paint.setStrokeWidth(2.9f);
        paint.setColor(Color.argb(Math.round(255 * shimmer), 255, 214, 33));
        canvas.drawRoundRect(0, 0, width, height, 10, 10, paint);
        paint.setStrokeWidth(1.1f);
        paint.setColor(Color.rgb(255, 251, 166));
        canvas.drawRoundRect(0.8f, 0.8f, width - 0.8f, height - 0.8f, 9, 9, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private static float phase(int seed) {
        int mixed = (seed ^ (seed >>> 16)) * 0x45d9f3b;
        return ((mixed ^ (mixed >>> 16)) & 65535) * 0.0031f;
    }
    private static float wave(float value) { return (float) Math.sin(value); }
    private static float fractional(float value) { return value - (float) Math.floor(value); }
}
