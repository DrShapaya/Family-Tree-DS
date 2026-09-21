package ru.drshapaya.androidft2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** Draggable crop edges/corners and a moveable interior, in world coordinates. */
final class ExportPreviewView extends View {
    final TreeImageExport export;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF image = new RectF();
    private final RectF selection = new RectF();
    private final RectF decorationArea = new RectF();
    private final RectF displayExtent = new RectF();
    private final Path contentClip = new Path();
    private Bitmap bitmap;
    private int drag;
    private float lastX, lastY;
    private final ScaleGestureDetector scaleDetector;
    private float previewScale = 1f;
    private float previewOffsetX;
    private float previewOffsetY;
    private float lastGestureFocusX;
    private float lastGestureFocusY;
    private Runnable changed;

    ExportPreviewView(Context context, TreeImageExport export) {
        super(context);
        this.export = export;
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(225, 233, 235));
        background.setCornerRadius(14f * density());
        setBackground(background);
        setClipToOutline(true);
        setContentDescription("Область PNG. Двумя пальцами можно масштабировать и перемещать предпросмотр. Рамка остаётся на месте.");
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                drag = 0;
                lastGestureFocusX = detector.getFocusX();
                lastGestureFocusY = detector.getFocusY();
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }

            @Override public boolean onScale(ScaleGestureDetector detector) {
                float oldScale = previewScale;
                float nextScale = clamp(oldScale * detector.getScaleFactor(), 1f, 5f);
                float focusX = detector.getFocusX();
                float focusY = detector.getFocusY();
                if (Math.abs(nextScale - oldScale) > 0.0001f) {
                    float localX = (focusX - image.centerX() - previewOffsetX) / oldScale;
                    float localY = (focusY - image.centerY() - previewOffsetY) / oldScale;
                    previewScale = nextScale;
                    previewOffsetX = focusX - image.centerX() - localX * nextScale;
                    previewOffsetY = focusY - image.centerY() - localY * nextScale;
                    constrainPreviewOffset();
                    invalidate();
                }
                return true;
            }
        });
    }

    void setOnCropChanged(Runnable listener) { changed = listener; }

    void refresh() {
        refresh(4f, 8_000_000L);
    }

    void refresh(float requestedScale, long maxPixels) {
        export.apply();
        updateDisplayExtent();
        Bitmap next = export.previewForDisplay(
            displayExtent, requestedScale, maxPixels, getWidth(), getHeight());
        if (bitmap != null) bitmap.recycle();
        bitmap = next;
        invalidate();
    }

    void release() {
        if (bitmap != null) bitmap.recycle();
        bitmap = null;
    }

    private float density() { return getResources().getDisplayMetrics().density; }

    private void updateDisplayExtent() {
        displayExtent.set(export.extent);
        float availableWidth = Math.max(1f, getWidth() - 8f * density());
        float availableHeight = Math.max(1f, getHeight() - 8f * density());
        float viewAspect = availableWidth / availableHeight;
        float worldAspect = displayExtent.width() / Math.max(1f, displayExtent.height());
        if (worldAspect > viewAspect) {
            float targetHeight = displayExtent.width() / viewAspect;
            displayExtent.inset(0f, -(targetHeight - displayExtent.height()) / 2f);
        } else {
            float targetWidth = displayExtent.height() * viewAspect;
            displayExtent.inset(-(targetWidth - displayExtent.width()) / 2f, 0f);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        if (displayExtent.isEmpty()) updateDisplayExtent();
        float padding = 4f * density();
        image.set(padding, padding, getWidth() - padding, getHeight() - padding);
        float ratioX = image.width() / Math.max(1f, displayExtent.width());
        float ratioY = image.height() / Math.max(1f, displayExtent.height());
        paint.setStyle(Paint.Style.FILL);
        int contentSave = canvas.save();
        contentClip.reset();
        contentClip.addRoundRect(image, 12f * density(), 12f * density(), Path.Direction.CW);
        canvas.clipPath(contentClip);
        if (bitmap != null) {
            float halfWidth = image.width() * previewScale / 2f;
            float halfHeight = image.height() * previewScale / 2f;
            RectF previewImage = new RectF(
                image.centerX() + previewOffsetX - halfWidth,
                image.centerY() + previewOffsetY - halfHeight,
                image.centerX() + previewOffsetX + halfWidth,
                image.centerY() + previewOffsetY + halfHeight);
            canvas.save();
            canvas.clipRect(image);
            canvas.drawBitmap(bitmap, null, previewImage, paint);
            canvas.restore();
        }
        selection.set(image.left + (export.crop.left - displayExtent.left) * ratioX,
            image.top + (export.crop.top - displayExtent.top) * ratioY,
            image.left + (export.crop.right - displayExtent.left) * ratioX,
            image.top + (export.crop.bottom - displayExtent.top) * ratioY);

        canvas.save();
        canvas.translate(selection.left, selection.top);
        canvas.scale(ratioX, ratioY);
        decorationArea.set(0, 0, export.crop.width(), export.crop.height());
        ExportDecoration.draw(canvas, decorationArea, export.frame, false);
        canvas.restore();

        paint.setColor(Color.argb(120, 18, 32, 39));
        canvas.drawRect(image.left, image.top, image.right, selection.top, paint);
        canvas.drawRect(image.left, selection.bottom, image.right, image.bottom, paint);
        canvas.drawRect(image.left, selection.top, selection.left, selection.bottom, paint);
        canvas.drawRect(selection.right, selection.top, image.right, selection.bottom, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * density());
        paint.setColor(Color.WHITE);
        canvas.drawRect(selection, paint);
        paint.setStrokeWidth(density());
        paint.setColor(Color.rgb(0, 126, 116));
        canvas.drawRect(selection, paint);
        for (int col = 1; col <= export.verticalCuts; col++) {
            float x = selection.left + selection.width() * col / (export.verticalCuts + 1);
            canvas.drawLine(x, selection.top, x, selection.bottom, paint);
        }
        for (int row = 1; row <= export.horizontalCuts; row++) {
            float y = selection.top + selection.height() * row / (export.horizontalCuts + 1);
            canvas.drawLine(selection.left, y, selection.right, y, paint);
        }
        canvas.restoreToCount(contentSave);
        paint.setStyle(Paint.Style.FILL);
        for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) {
            if (x == 1 && y == 1) continue;
            float cx = selection.left + selection.width() * x / 2;
            float cy = selection.top + selection.height() * y / 2;
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx, cy, 5f * density(), paint);
            paint.setColor(Color.rgb(0, 126, 116));
            canvas.drawCircle(cx, cy, 3f * density(), paint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        float x = event.getX(), y = event.getY();
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            float hit = 24f * density();
            drag = 0;
            if (x >= selection.left - hit && x <= selection.right + hit
                    && y >= selection.top - hit && y <= selection.bottom + hit) {
                if (Math.abs(x - selection.left) <= hit && x <= selection.centerX()) drag |= 1;
                else if (Math.abs(x - selection.right) <= hit) drag |= 2;
                if (Math.abs(y - selection.top) <= hit && y <= selection.centerY()) drag |= 4;
                else if (Math.abs(y - selection.bottom) <= hit) drag |= 8;
                if (drag == 0) drag = 16;
            }
            lastX = x; lastY = y;
            if (drag != 0) getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            drag = 0;
            lastGestureFocusX = pointerFocusX(event);
            lastGestureFocusY = pointerFocusY(event);
            getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && event.getPointerCount() >= 2) {
            float focusX = pointerFocusX(event);
            float focusY = pointerFocusY(event);
            previewOffsetX += focusX - lastGestureFocusX;
            previewOffsetY += focusY - lastGestureFocusY;
            lastGestureFocusX = focusX;
            lastGestureFocusY = focusY;
            constrainPreviewOffset();
            invalidate();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && drag != 0) {
            float dx = (x - lastX) * displayExtent.width() / image.width();
            float dy = (y - lastY) * displayExtent.height() / image.height();
            // displayExtent is the full visible canvas. It can be taller or wider than the
            // tree itself to match the preview aspect ratio, so crop handles must use it.
            RectF c = export.crop, e = displayExtent;
            float minW = Math.min(48f, e.width() * 0.1f), minH = Math.min(48f, e.height() * 0.1f);
            if (drag == 16) c.offset(clamp(dx, e.left - c.left, e.right - c.right),
                clamp(dy, e.top - c.top, e.bottom - c.bottom));
            else {
                if ((drag & 1) != 0) c.left = clamp(c.left + dx, e.left, c.right - minW);
                if ((drag & 2) != 0) c.right = clamp(c.right + dx, c.left + minW, e.right);
                if ((drag & 4) != 0) c.top = clamp(c.top + dy, e.top, c.bottom - minH);
                if ((drag & 8) != 0) c.bottom = clamp(c.bottom + dy, c.top + minH, e.bottom);
            }
            lastX = x; lastY = y;
            invalidate();
            if (changed != null) changed.run();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
            drag = 0;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            drag = 0;
            getParent().requestDisallowInterceptTouchEvent(false);
            performClick();
            return true;
        }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private void constrainPreviewOffset() {
        if (previewScale <= 1.001f) {
            previewOffsetX = 0f;
            previewOffsetY = 0f;
            return;
        }
        float maxX = image.width() * (previewScale - 1f) / 2f;
        float maxY = image.height() * (previewScale - 1f) / 2f;
        previewOffsetX = clamp(previewOffsetX, -maxX, maxX);
        previewOffsetY = clamp(previewOffsetY, -maxY, maxY);
    }

    private static float pointerFocusX(MotionEvent event) {
        float sum = 0f;
        for (int i = 0; i < event.getPointerCount(); i++) sum += event.getX(i);
        return sum / Math.max(1, event.getPointerCount());
    }

    private static float pointerFocusY(MotionEvent event) {
        float sum = 0f;
        for (int i = 0; i < event.getPointerCount(); i++) sum += event.getY(i);
        return sum / Math.max(1, event.getPointerCount());
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
