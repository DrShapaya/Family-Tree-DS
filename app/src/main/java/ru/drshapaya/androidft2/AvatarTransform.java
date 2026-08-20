package ru.drshapaya.androidft2;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.widget.ImageView;

final class AvatarTransform {
    private AvatarTransform() {
    }

    static Rect sourceRect(Bitmap bitmap, Person person) {
        return sourceRect(
            bitmap,
            person == null ? 1f : person.avatarScale,
            person == null ? 0f : person.avatarOffsetX,
            person == null ? 0f : person.avatarOffsetY);
    }

    static Rect sourceRect(Bitmap bitmap, float scale, float offsetX, float offsetY) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return new Rect();
        }
        float safeScale = clamp(scale, 1f, 8f);
        float side = Math.max(1f, Math.min(bitmap.getWidth(), bitmap.getHeight()) / safeScale);
        float centerX = bitmap.getWidth() / 2f - offsetX * side;
        float centerY = bitmap.getHeight() / 2f - offsetY * side;
        centerX = clamp(centerX, side / 2f, bitmap.getWidth() - side / 2f);
        centerY = clamp(centerY, side / 2f, bitmap.getHeight() - side / 2f);
        int left = Math.max(0, Math.round(centerX - side / 2f));
        int top = Math.max(0, Math.round(centerY - side / 2f));
        int right = Math.min(bitmap.getWidth(), Math.round(centerX + side / 2f));
        int bottom = Math.min(bitmap.getHeight(), Math.round(centerY + side / 2f));
        int size = Math.min(right - left, bottom - top);
        return new Rect(left, top, left + Math.max(1, size), top + Math.max(1, size));
    }

    static void apply(ImageView image, Bitmap bitmap, Person person) {
        if (image == null || bitmap == null) return;
        image.setScaleType(ImageView.ScaleType.MATRIX);
        image.setImageBitmap(bitmap);
        image.post(() -> {
            if (image.getWidth() <= 0 || image.getHeight() <= 0) return;
            Rect source = sourceRect(bitmap, person);
            Matrix matrix = new Matrix();
            matrix.setRectToRect(
                new RectF(source),
                new RectF(0f, 0f, image.getWidth(), image.getHeight()),
                Matrix.ScaleToFit.FILL);
            image.setImageMatrix(matrix);
        });
    }

    static float maxOffsetX(Bitmap bitmap, float scale) {
        if (bitmap == null) return 0f;
        float side = Math.max(1f, Math.min(bitmap.getWidth(), bitmap.getHeight()) / clamp(scale, 1f, 8f));
        return Math.max(0f, (bitmap.getWidth() - side) / (2f * side));
    }

    static float maxOffsetY(Bitmap bitmap, float scale) {
        if (bitmap == null) return 0f;
        float side = Math.max(1f, Math.min(bitmap.getWidth(), bitmap.getHeight()) / clamp(scale, 1f, 8f));
        return Math.max(0f, (bitmap.getHeight() - side) / (2f * side));
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
