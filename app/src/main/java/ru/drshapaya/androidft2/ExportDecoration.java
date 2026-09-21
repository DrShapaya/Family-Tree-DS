package ru.drshapaya.androidft2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

final class ExportDecoration {
    private static final ThreadLocal<BotanicalEffect> BOTANICAL =
        ThreadLocal.withInitial(BotanicalEffect::new);

    static void draw(Canvas canvas, RectF area, String style, boolean monochrome) {
        if ("export_frame_botanical".equals(style)) {
            drawBotanical(canvas, area, monochrome);
            return;
        }
        if ("export_frame_fire".equals(style)) {
            drawFire(canvas, area, monochrome);
            return;
        }
        if (!"export_frame_classic".equals(style)) return;
        float inset = Math.min(12f, Math.min(area.width(), area.height()) / 12f);
        RectF frame = new RectF(area);
        frame.inset(inset, inset);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(monochrome ? Color.DKGRAY : Color.rgb(157, 122, 63));
        paint.setStrokeWidth(1.6f);
        canvas.drawRect(frame, paint);
        frame.inset(4f, 4f);
        paint.setStrokeWidth(0.65f);
        canvas.drawRect(frame, paint);
    }

    private static void drawFire(Canvas canvas, RectF area, boolean monochrome) {
        float inset = Math.min(14f, Math.min(area.width(), area.height()) / 10f);
        RectF frame = new RectF(area);
        frame.inset(inset, inset);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(monochrome ? Color.argb(46, 32, 32, 32) : Color.argb(55, 255, 70, 0));
        for (int i = 8; i >= 2; i -= 2) {
            paint.setStrokeWidth(i * 1.7f);
            canvas.drawRoundRect(frame, inset * .45f, inset * .45f, paint);
        }
        paint.setStrokeWidth(5.2f);
        paint.setColor(monochrome ? Color.DKGRAY : Color.rgb(238, 83, 4));
        canvas.drawRoundRect(frame, inset * .42f, inset * .42f, paint);
        paint.setStrokeWidth(2.4f);
        paint.setColor(monochrome ? Color.GRAY : Color.rgb(255, 188, 13));
        canvas.drawRoundRect(frame, inset * .42f, inset * .42f, paint);
        paint.setStrokeWidth(.85f);
        paint.setColor(monochrome ? Color.LTGRAY : Color.rgb(255, 250, 174));
        canvas.drawRoundRect(frame, inset * .42f, inset * .42f, paint);

        paint.setStyle(Paint.Style.FILL);
        Path flame = new Path();
        float step = Math.max(16f, Math.min(25f, frame.width() / 24f));
        int index = 0;
        for (float x = frame.left + 8f; x < frame.right - 6f; x += step) {
            float height = 7f + 5f * wave(index * 2.17f);
            tongue(canvas, flame, paint, x, frame.top + 1f, 4.8f, height, false, monochrome);
            if ((index & 1) == 0)
                tongue(canvas, flame, paint, x + step * .42f, frame.bottom - 1f,
                    4f, height * .7f, true, monochrome);
            index++;
        }
        step = Math.max(18f, Math.min(27f, frame.height() / 17f));
        index = 0;
        for (float y = frame.top + 10f; y < frame.bottom - 6f; y += step) {
            float height = 6f + 4f * wave(index * 1.73f + .8f);
            tongueSide(canvas, flame, paint, frame.left + 1f, y, height, true, monochrome);
            tongueSide(canvas, flame, paint, frame.right - 1f, y + step * .28f,
                height, false, monochrome);
            index++;
        }
    }

    private static void drawBotanical(Canvas canvas, RectF area, boolean monochrome) {
        float inset = Math.min(27f, Math.min(area.width(), area.height()) / 7f);
        RectF frame = new RectF(area);
        frame.inset(inset, inset);
        canvas.save();
        canvas.translate(frame.left, frame.top);
        if (monochrome) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.2f);
            paint.setColor(Color.DKGRAY);
            canvas.drawRoundRect(0, 0, frame.width(), frame.height(), 10, 10, paint);
        } else {
            BOTANICAL.get().frame(canvas, frame.width(), frame.height());
        }
        canvas.restore();
    }

    private static void tongue(Canvas canvas, Path path, Paint paint, float x, float y,
                               float halfWidth, float height, boolean down, boolean monochrome) {
        path.reset();
        float sign = down ? 1f : -1f;
        path.moveTo(x - halfWidth, y);
        path.cubicTo(x - halfWidth * .7f, y + sign * height * .38f,
            x + halfWidth * .4f, y + sign * height * .62f, x, y + sign * height);
        path.cubicTo(x + halfWidth * 1.15f, y + sign * height * .62f,
            x + halfWidth * .75f, y + sign * height * .22f, x + halfWidth, y);
        path.close();
        paint.setColor(monochrome ? Color.GRAY : Color.rgb(244, 85, 3));
        canvas.drawPath(path, paint);
        paint.setColor(monochrome ? Color.LTGRAY : Color.rgb(255, 205, 35));
        canvas.save(); canvas.scale(.56f, .67f, x, y); canvas.drawPath(path, paint); canvas.restore();
    }

    private static void tongueSide(Canvas canvas, Path path, Paint paint, float x, float y,
                                   float height, boolean right, boolean monochrome) {
        canvas.save();
        canvas.rotate(right ? 90f : -90f, x, y);
        tongue(canvas, path, paint, x, y, 3.7f, height, false, monochrome);
        canvas.restore();
    }

    private static float wave(float value) {
        return .5f + .5f * (float) Math.sin(value);
    }
}
