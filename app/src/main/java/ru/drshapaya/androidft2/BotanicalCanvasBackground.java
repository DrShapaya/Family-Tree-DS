package ru.drshapaya.androidft2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;

/** A world-anchored botanical paper supplied as a workshop canvas background. */
final class BotanicalCanvasBackground {
    // Keep the artwork anchored to a large world-sized repeat.  At normal
    // zoom a viewport sees one quiet paper field instead of the same corner
    // motif several times; the second layer is even larger and only adds
    // barely visible tonal variation.
    private static final float TILE_WORLD_WIDTH = 24_000f;
    private static final float SECONDARY_TILE_WORLD_WIDTH = 38_000f;
    private static final int CROP_LEFT = 238;
    private static final int CROP_TOP = 168;
    private static final int CROP_RIGHT = 1434;
    private static final int CROP_BOTTOM = 773;
    private final Bitmap bitmap;
    private final BitmapShader shader;
    private final BitmapShader secondaryShader;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();

    BotanicalCanvasBackground(Context context) {
        Bitmap source = BitmapFactory.decodeResource(
            context.getResources(), R.drawable.canvas_background_botanical);
        bitmap = cropTexture(source);
        shader = bitmap == null ? null : new BitmapShader(
            bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        secondaryShader = bitmap == null ? null : new BitmapShader(
            bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    }

    void draw(Canvas canvas, int width, int height, float scale, float offsetX, float offsetY,
              boolean dark, boolean fitArea) {
        if (shader == null || secondaryShader == null || bitmap == null) return;
        float imageScale = fitArea
            ? Math.max(width / (float) bitmap.getWidth(), height / (float) bitmap.getHeight())
            : TILE_WORLD_WIDTH / bitmap.getWidth() * Math.max(.01f, scale);
        matrix.reset();
        matrix.setScale(imageScale, imageScale);
        matrix.postTranslate(
            fitArea ? (width - bitmap.getWidth() * imageScale) / 2f : offsetX,
            fitArea ? (height - bitmap.getHeight() * imageScale) / 2f : offsetY);
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);
        paint.setColor(Color.WHITE);
        paint.setAlpha(dark ? 112 : 240);
        canvas.drawRect(0, 0, width, height, paint);

        // A second, much larger phase breaks any residual motif repetition while
        // staying anchored to the same world coordinates.
        float secondaryScale = fitArea
            ? imageScale * .83f
            : SECONDARY_TILE_WORLD_WIDTH / bitmap.getWidth() * Math.max(.01f, scale);
        matrix.reset();
        matrix.setScale(secondaryScale, secondaryScale);
        matrix.postTranslate(
            fitArea
                ? (width - bitmap.getWidth() * secondaryScale) / 2f + 137f
                : offsetX + 137f * Math.max(.01f, scale),
            fitArea
                ? (height - bitmap.getHeight() * secondaryScale) / 2f - 89f
                : offsetY - 89f * Math.max(.01f, scale));
        secondaryShader.setLocalMatrix(matrix);
        paint.setShader(secondaryShader);
        paint.setColor(Color.WHITE);
        paint.setAlpha(dark ? 22 : 30);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
        paint.setAlpha(255);
        if (dark) {
            paint.setColor(Color.argb(155, 12, 22, 18));
            canvas.drawRect(0, 0, width, height, paint);
        }
    }

    private static Bitmap cropTexture(Bitmap source) {
        if (source == null) return null;
        int left = Math.max(0, Math.min(CROP_LEFT, source.getWidth() - 2));
        int top = Math.max(0, Math.min(CROP_TOP, source.getHeight() - 2));
        int right = Math.max(left + 1, Math.min(CROP_RIGHT, source.getWidth()));
        int bottom = Math.max(top + 1, Math.min(CROP_BOTTOM, source.getHeight()));
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top);
    }
}
