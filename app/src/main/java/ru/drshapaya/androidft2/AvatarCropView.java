package ru.drshapaya.androidft2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

final class AvatarPositionView extends View {
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF square = new RectF();
    private final Matrix matrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private Bitmap bitmap;
    private float scale = 1f;
    private float offsetX;
    private float offsetY;
    private float lastX;
    private float lastY;

    AvatarPositionView(Context context) {
        super(context);
        shadePaint.setColor(Color.argb(174, 8, 16, 20));
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(2));
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scale = AvatarTransform.clamp(scale * detector.getScaleFactor(), 1f, 8f);
                constrainOffsets();
                invalidate();
                return true;
            }
        });
        setBackgroundColor(Color.rgb(226, 234, 237));
    }

    void setBitmap(Bitmap value) {
        bitmap = value;
        constrainOffsets();
        invalidate();
    }

    void setPosition(float valueScale, float valueX, float valueY) {
        scale = AvatarTransform.clamp(valueScale, 1f, 8f);
        offsetX = valueX;
        offsetY = valueY;
        constrainOffsets();
        invalidate();
    }

    float positionScale() { return scale; }
    float positionX() { return offsetX; }
    float positionY() { return offsetY; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || getWidth() <= 0 || getHeight() <= 0) return;
        float side = Math.min(getWidth(), getHeight()) - dp(34);
        float left = (getWidth() - side) / 2f;
        float top = (getHeight() - side) / 2f;
        square.set(left, top, left + side, top + side);
        Rect source = AvatarTransform.sourceRect(bitmap, scale, offsetX, offsetY);
        matrix.setRectToRect(new RectF(source), square, Matrix.ScaleToFit.FILL);
        canvas.drawBitmap(bitmap, matrix, bitmapPaint);
        canvas.drawRect(0, 0, getWidth(), square.top, shadePaint);
        canvas.drawRect(0, square.bottom, getWidth(), getHeight(), shadePaint);
        canvas.drawRect(0, square.top, square.left, square.bottom, shadePaint);
        canvas.drawRect(square.right, square.top, getWidth(), square.bottom, shadePaint);
        canvas.drawRoundRect(square, dp(12), dp(12), borderPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && event.getPointerCount() == 1 && square.width() > 0f) {
                    offsetX += (event.getX() - lastX) / square.width();
                    offsetY += (event.getY() - lastY) / square.height();
                    constrainOffsets();
                    invalidate();
                }
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                performClick();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void constrainOffsets() {
        offsetX = AvatarTransform.clamp(offsetX, -AvatarTransform.maxOffsetX(bitmap, scale), AvatarTransform.maxOffsetX(bitmap, scale));
        offsetY = AvatarTransform.clamp(offsetY, -AvatarTransform.maxOffsetY(bitmap, scale), AvatarTransform.maxOffsetY(bitmap, scale));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

final class FreeAvatarCropView extends View {
    private static final int MOVE = 1;
    private static final int TOP_LEFT = 2;
    private static final int TOP_RIGHT = 3;
    private static final int BOTTOM_LEFT = 4;
    private static final int BOTTOM_RIGHT = 5;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageRect = new RectF();
    private final RectF cropRect = new RectF();
    private Bitmap bitmap;
    private int dragMode;
    private float lastX;
    private float lastY;
    private boolean initialized;

    FreeAvatarCropView(Context context) {
        super(context);
        shadePaint.setColor(Color.argb(170, 7, 14, 18));
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(2));
        handlePaint.setColor(Color.WHITE);
        setBackgroundColor(Color.rgb(32, 40, 44));
    }

    void setBitmap(Bitmap value) {
        bitmap = value;
        initialized = false;
        invalidate();
    }

    Bitmap croppedBitmap() {
        if (bitmap == null || imageRect.width() <= 0f || cropRect.width() <= 1f || cropRect.height() <= 1f) return null;
        float sx = bitmap.getWidth() / imageRect.width();
        float sy = bitmap.getHeight() / imageRect.height();
        int left = Math.max(0, Math.round((cropRect.left - imageRect.left) * sx));
        int top = Math.max(0, Math.round((cropRect.top - imageRect.top) * sy));
        int right = Math.min(bitmap.getWidth(), Math.round((cropRect.right - imageRect.left) * sx));
        int bottom = Math.min(bitmap.getHeight(), Math.round((cropRect.bottom - imageRect.top) * sy));
        if (right - left < 2 || bottom - top < 2) return null;
        try {
            return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || getWidth() <= 0 || getHeight() <= 0) return;
        float availableWidth = getWidth() - dp(24);
        float availableHeight = getHeight() - dp(24);
        float fit = Math.min(availableWidth / bitmap.getWidth(), availableHeight / bitmap.getHeight());
        float width = bitmap.getWidth() * fit;
        float height = bitmap.getHeight() * fit;
        imageRect.set((getWidth() - width) / 2f, (getHeight() - height) / 2f, (getWidth() + width) / 2f, (getHeight() + height) / 2f);
        if (!initialized) {
            float insetX = imageRect.width() * 0.12f;
            float insetY = imageRect.height() * 0.12f;
            cropRect.set(imageRect.left + insetX, imageRect.top + insetY, imageRect.right - insetX, imageRect.bottom - insetY);
            initialized = true;
        }
        canvas.drawBitmap(bitmap, null, imageRect, bitmapPaint);
        canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, cropRect.top, shadePaint);
        canvas.drawRect(imageRect.left, cropRect.bottom, imageRect.right, imageRect.bottom, shadePaint);
        canvas.drawRect(imageRect.left, cropRect.top, cropRect.left, cropRect.bottom, shadePaint);
        canvas.drawRect(cropRect.right, cropRect.top, imageRect.right, cropRect.bottom, shadePaint);
        canvas.drawRect(cropRect, borderPaint);
        drawHandle(canvas, cropRect.left, cropRect.top);
        drawHandle(canvas, cropRect.right, cropRect.top);
        drawHandle(canvas, cropRect.left, cropRect.bottom);
        drawHandle(canvas, cropRect.right, cropRect.bottom);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragMode = hitMode(event.getX(), event.getY());
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                updateCrop(event.getX() - lastX, event.getY() - lastY);
                lastX = event.getX();
                lastY = event.getY();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragMode = 0;
                performClick();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int hitMode(float x, float y) {
        float radius = dp(34);
        if (distance(x, y, cropRect.left, cropRect.top) <= radius) return TOP_LEFT;
        if (distance(x, y, cropRect.right, cropRect.top) <= radius) return TOP_RIGHT;
        if (distance(x, y, cropRect.left, cropRect.bottom) <= radius) return BOTTOM_LEFT;
        if (distance(x, y, cropRect.right, cropRect.bottom) <= radius) return BOTTOM_RIGHT;
        return cropRect.contains(x, y) ? MOVE : 0;
    }

    private void updateCrop(float dx, float dy) {
        float min = dp(54);
        if (dragMode == MOVE) {
            float safeDx = Math.max(imageRect.left - cropRect.left, Math.min(imageRect.right - cropRect.right, dx));
            float safeDy = Math.max(imageRect.top - cropRect.top, Math.min(imageRect.bottom - cropRect.bottom, dy));
            cropRect.offset(safeDx, safeDy);
        } else if (dragMode == TOP_LEFT) {
            cropRect.left = clamp(cropRect.left + dx, imageRect.left, cropRect.right - min);
            cropRect.top = clamp(cropRect.top + dy, imageRect.top, cropRect.bottom - min);
        } else if (dragMode == TOP_RIGHT) {
            cropRect.right = clamp(cropRect.right + dx, cropRect.left + min, imageRect.right);
            cropRect.top = clamp(cropRect.top + dy, imageRect.top, cropRect.bottom - min);
        } else if (dragMode == BOTTOM_LEFT) {
            cropRect.left = clamp(cropRect.left + dx, imageRect.left, cropRect.right - min);
            cropRect.bottom = clamp(cropRect.bottom + dy, cropRect.top + min, imageRect.bottom);
        } else if (dragMode == BOTTOM_RIGHT) {
            cropRect.right = clamp(cropRect.right + dx, cropRect.left + min, imageRect.right);
            cropRect.bottom = clamp(cropRect.bottom + dy, cropRect.top + min, imageRect.bottom);
        }
    }

    private void drawHandle(Canvas canvas, float x, float y) {
        canvas.drawCircle(x, y, dp(6), handlePaint);
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
