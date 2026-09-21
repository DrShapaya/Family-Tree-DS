package ru.drshapaya.androidft2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Picture;
import android.graphics.RectF;
import android.util.LongSparseArray;

import java.util.IdentityHashMap;

/** Detailed vines drawn as cached compound paths rather than hundreds of Canvas operations. */
final class BotanicalEffect {
    private static final class Decoration {
        final Path stem = new Path();
        final Path leaves = new Path();
        final Path highlights = new Path();
        final Path veins = new Path();
        final Path petals = new Path();
        final Path centres = new Path();
        final RectF bounds = new RectF();
        final Bitmap[] normalBitmaps = new Bitmap[3];
        final Bitmap[] selectedBitmaps = new Bitmap[3];
        final boolean[] normalBitmapRejected = new boolean[3];
        final boolean[] selectedBitmapRejected = new boolean[3];
        // Very long links can exceed the bitmap cap.  Keep a recorded vector
        // fallback so panning/zooming never redraws every leaf on the UI thread.
        final Picture[] normalPictures = new Picture[1];
        final Picture[] selectedPictures = new Picture[1];
    }
    private static final long MAX_BITMAP_PIXELS = 900_000L;
    private static final long MAX_BITMAP_CACHE_BYTES = 32L * 1024L * 1024L;
    private static final int MAX_CARD_DECORATIONS = 1024;
    private static final int MAX_SURFACE_DECORATIONS = 512;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path leaf = new Path();
    private final Path leafHighlight = new Path();
    private final Path leafVeins = new Path();
    private final Path petal = new Path();
    private final Path centre = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final float[] position = new float[2];
    private final float[] tangent = new float[2];
    private final Matrix transform = new Matrix();
    private final float[] values = new float[9];
    private final RectF bitmapDestination = new RectF();
    private final LongSparseArray<Decoration> cardCache = new LongSparseArray<>();
    private final LongSparseArray<Decoration> surfaceCache = new LongSparseArray<>();
    private final IdentityHashMap<Path, Decoration> linkCache = new IdentityHashMap<>();
    private long bitmapCacheBytes;
    private int linkBuildCount;
    private int cardBuildCount;

    BotanicalEffect() {
        leaf.moveTo(0, 0);
        leaf.cubicTo(4, -7, 13, -8, 22, -2);
        leaf.cubicTo(17, 0, 10, 10, 0, 0);
        leaf.close();
        leafHighlight.moveTo(2, -1);
        leafHighlight.cubicTo(8, -6, 15, -6, 20, -2);
        leafHighlight.cubicTo(14, -2, 8, 1, 2, -1);
        leafHighlight.close();
        leafVeins.moveTo(0, 0); leafVeins.lineTo(20, -2);
        for (int i = 5; i < 17; i += 4) {
            leafVeins.moveTo(i, -i / 11f); leafVeins.lineTo(i - 2, -4.5f);
            leafVeins.moveTo(i, -i / 11f); leafVeins.lineTo(i - 2, 3.4f);
        }
        petal.addOval(-3, -10, 3, 0, Path.Direction.CW);
        centre.addCircle(0, 0, 2.8f, Path.Direction.CW);
    }

    void behind(Canvas canvas, float width, float height, int seed) {
        behind(canvas, width, height, seed, 1f);
    }

    void behind(Canvas canvas, float width, float height, int seed, float detailScale) {
        long key = decorationKey(width, height, seed);
        Decoration decoration = cardCache.get(key);
        if (decoration == null) {
            decoration = buildBorder(width, height, seed);
            cardBuildCount++;
            evictCardDecorationIfNeeded();
            cardCache.put(key, decoration);
        }
        drawCached(canvas, decoration, detailBucket(detailScale), false);
    }

    void surface(Canvas canvas, float width, float height, int seed) {
        long key = decorationKey(width, height, seed);
        Decoration decoration = surfaceCache.get(key);
        if (decoration == null) {
            decoration = new Decoration();
            addLeaf(decoration, width * .72f, height * .9f, -28, 2.8f);
            addLeaf(decoration, width * .8f, height * .82f, -68, 2.1f);
            addLeaf(decoration, width * .13f, height * .14f, 150, 2.25f);
            if (surfaceCache.size() >= MAX_SURFACE_DECORATIONS) surfaceCache.clear();
            surfaceCache.put(key, decoration);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(19, 42, 105, 36));
        canvas.drawPath(decoration.leaves, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(.55f);
        paint.setColor(Color.argb(27, 32, 80, 28));
        canvas.drawPath(decoration.veins, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    void rim(Canvas canvas, float width, float height) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xff4e791f);
        paint.setStrokeWidth(2);
        canvas.drawRoundRect(0, 0, width, height, 10, 10, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /** Path identity is stable in TreeCanvasView, so leaf geometry survives pan and zoom unchanged. */
    void link(Canvas canvas, Path worldPath, boolean dashed, boolean selected) {
        link(canvas, worldPath, dashed, selected, 1f);
    }

    void link(Canvas canvas, Path worldPath, boolean dashed, boolean selected, float detailScale) {
        Decoration decoration = linkCache.get(worldPath);
        if (decoration == null) {
            decoration = buildLink(worldPath, dashed);
            if (linkCache.size() >= 1024) {
                linkCache.clear();
                clearBitmapCaches();
            }
            linkCache.put(worldPath, decoration);
            linkBuildCount++;
        }
        drawCached(canvas, decoration, detailBucket(detailScale), selected);
    }

    void frame(Canvas canvas, float width, float height) {
        behind(canvas, width, height, 7919);
        rim(canvas, width, height);
    }

    int linkBuildCountForTests() { return linkBuildCount; }
    int cardBuildCountForTests() { return cardBuildCount; }

    private Decoration buildBorder(float width, float height, int seed) {
        Decoration result = new Decoration();
        result.stem.addRoundRect(-2, -2, width + 2, height + 2, 12, 12, Path.Direction.CW);
        measure.setPath(result.stem, false);
        float length = measure.getLength();
        int count = Math.max(1, (int) (length / 17));
        for (int i = 0; i < count; i++) {
            measure.getPosTan((i + .4f) * length / count, position, tangent);
            float angle = degrees(tangent);
            float variation = (float) Math.sin(i * 2.399 + (seed & 255));
            addLeaf(result, position[0], position[1], angle - 53 - variation * 18,
                .72f + .2f * variation);
            if (i % 3 == 0) addLeaf(result, position[0], position[1], angle - 128, .65f);
        }
        addFlower(result, -5, height * .76f, .85f);
        addFlower(result, width * .77f, -7, .7f);
        addFlower(result, width + 5, height * .85f, .75f);
        updateBounds(result);
        return result;
    }

    private Decoration buildLink(Path worldPath, boolean dashed) {
        Decoration result = new Decoration();
        measure.setPath(worldPath, false);
        float length = measure.getLength();
        if (dashed) {
            for (float distance = 0; distance < length; distance += 32f)
                measure.getSegment(distance, Math.min(length, distance + 20f), result.stem, true);
        } else result.stem.addPath(worldPath);
        for (int i = 0; ; i++) {
            float distance = dashed ? 9f + i * 64f : 24f + i * 45f;
            if (distance > length - 12f) break;
            measure.getPosTan(distance, position, tangent);
            addLeaf(result, position[0], position[1],
                degrees(tangent) + (i % 2 == 0 ? -58 : 58), .85f);
        }
        updateBounds(result);
        return result;
    }

    private void updateBounds(Decoration decoration) {
        decoration.bounds.setEmpty();
        unionBounds(decoration.bounds, decoration.stem);
        unionBounds(decoration.bounds, decoration.leaves);
        unionBounds(decoration.bounds, decoration.highlights);
        unionBounds(decoration.bounds, decoration.veins);
        unionBounds(decoration.bounds, decoration.petals);
        unionBounds(decoration.bounds, decoration.centres);
        if (decoration.bounds.isEmpty()) decoration.bounds.set(0, 0, 1, 1);
    }

    private static void unionBounds(RectF target, Path path) {
        if (path == null || path.isEmpty()) return;
        RectF value = new RectF();
        path.computeBounds(value, true);
        if (target.isEmpty()) target.set(value); else target.union(value);
    }

    private void drawCached(Canvas canvas, Decoration decoration, int bucket, boolean selected) {
        float pad = 24f;
        bitmapDestination.set(
            decoration.bounds.left - pad,
            decoration.bounds.top - pad,
            decoration.bounds.right + pad,
            decoration.bounds.bottom + pad);

        // At close zoom use a recorded vector display list.  It keeps every
        // vein and petal at native precision while avoiding a large bitmap
        // allocation exactly when the user is actively pinching.
        if (bucket > 1) {
            Picture[] pictures = selected ? decoration.selectedPictures : decoration.normalPictures;
            Picture picture = pictures[0];
            if (picture == null) {
                picture = createPicture(decoration, selected);
                pictures[0] = picture;
            }
            if (picture != null) {
                canvas.drawPicture(picture, bitmapDestination);
                return;
            }
        }
        Bitmap[] cache = selected ? decoration.selectedBitmaps : decoration.normalBitmaps;
        boolean[] rejected = selected ? decoration.selectedBitmapRejected : decoration.normalBitmapRejected;
        Bitmap bitmap = cache[bucket - 1];
        if ((bitmap == null || bitmap.isRecycled()) && !rejected[bucket - 1]) {
            bitmap = createBitmap(decoration, bucket, selected);
            if (bitmap != null) {
                long bytes = bitmap.getAllocationByteCount();
                if (bitmapCacheBytes + bytes > MAX_BITMAP_CACHE_BYTES) {
                    // A large botanical tree can contain dozens of long links.
                    // Clearing every cached bitmap here made the next frame build
                    // all of them again, causing a permanent allocation/render loop.
                    // Keep the existing hot cache and pin this decoration to its
                    // recorded Picture fallback instead.
                    bitmap.recycle();
                    bitmap = null;
                    rejected[bucket - 1] = true;
                } else {
                    cache[bucket - 1] = bitmap;
                    bitmapCacheBytes += bytes;
                }
            } else {
                rejected[bucket - 1] = true;
            }
        }
        if (bitmap == null) {
            Picture[] pictures = selected ? decoration.selectedPictures : decoration.normalPictures;
            Picture picture = pictures[0];
            if (picture == null) {
                picture = createPicture(decoration, selected);
                pictures[0] = picture;
            }
            if (picture != null) {
                canvas.drawPicture(picture, bitmapDestination);
            } else {
                // Only reached if the platform cannot allocate a display list.
                drawDecoration(canvas, decoration, 1f, selected);
            }
            return;
        }
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setFilterBitmap(true);
        canvas.drawBitmap(bitmap, null, bitmapDestination, paint);
    }

    private Bitmap createBitmap(Decoration decoration, int bucket, boolean selected) {
        final float pad = 24f;
        float left = decoration.bounds.left - pad;
        float top = decoration.bounds.top - pad;
        float right = decoration.bounds.right + pad;
        float bottom = decoration.bounds.bottom + pad;
        int width = Math.max(1, Math.min(4096, (int) Math.ceil((right - left) * bucket)));
        int height = Math.max(1, Math.min(4096, (int) Math.ceil((bottom - top) * bucket)));
        if ((long) width * height > MAX_BITMAP_PIXELS) return null;
        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError ignored) {
            return null;
        }
        Canvas bitmapCanvas = new Canvas(bitmap);
        bitmapCanvas.translate(-left * bucket, -top * bucket);
        bitmapCanvas.scale(bucket, bucket);
        drawDecoration(bitmapCanvas, decoration, 1f, selected);
        return bitmap;
    }

    private Picture createPicture(Decoration decoration, boolean selected) {
        final float pad = 24f;
        float left = decoration.bounds.left - pad;
        float top = decoration.bounds.top - pad;
        float right = decoration.bounds.right + pad;
        float bottom = decoration.bounds.bottom + pad;
        int width = Math.max(1, (int) Math.min(Integer.MAX_VALUE - 1L, (long) Math.ceil(right - left)));
        int height = Math.max(1, (int) Math.min(Integer.MAX_VALUE - 1L, (long) Math.ceil(bottom - top)));
        try {
            Picture picture = new Picture();
            Canvas pictureCanvas = picture.beginRecording(width, height);
            pictureCanvas.translate(-left, -top);
            drawDecoration(pictureCanvas, decoration, 1f, selected);
            picture.endRecording();
            return picture;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void clearBitmapCaches() {
        for (int i = 0; i < cardCache.size(); i++) clearBitmaps(cardCache.valueAt(i));
        for (Decoration decoration : linkCache.values()) clearBitmaps(decoration);
        bitmapCacheBytes = 0L;
    }

    private void evictCardDecorationIfNeeded() {
        if (cardCache.size() < MAX_CARD_DECORATIONS) return;
        Decoration evicted = cardCache.valueAt(0);
        cardCache.removeAt(0);
        bitmapCacheBytes = Math.max(0L, bitmapCacheBytes - bitmapBytes(evicted));
        clearBitmaps(evicted);
    }

    private static long bitmapBytes(Decoration decoration) {
        long bytes = 0L;
        for (Bitmap bitmap : decoration.normalBitmaps) {
            if (bitmap != null && !bitmap.isRecycled()) bytes += bitmap.getAllocationByteCount();
        }
        for (Bitmap bitmap : decoration.selectedBitmaps) {
            if (bitmap != null && !bitmap.isRecycled()) bytes += bitmap.getAllocationByteCount();
        }
        return bytes;
    }

    private static long decorationKey(float width, float height, int seed) {
        long w = Math.round(width) & 0xffffffffL;
        long h = Math.round(height) & 0xffffffffL;
        return (w << 32) ^ (h << 3) ^ (seed & 7L);
    }

    private static void clearBitmaps(Decoration decoration) {
        for (int i = 0; i < decoration.normalBitmaps.length; i++) {
            decoration.normalBitmaps[i] = null;
            decoration.selectedBitmaps[i] = null;
            decoration.normalBitmapRejected[i] = false;
            decoration.selectedBitmapRejected[i] = false;
        }
        decoration.normalPictures[0] = null;
        decoration.selectedPictures[0] = null;
    }

    private static int detailBucket(float scale) {
        if (!Float.isFinite(scale) || scale <= 1f) return 1;
        return Math.min(3, Math.max(1, (int) Math.ceil(scale)));
    }

    private void drawDecoration(Canvas canvas, Decoration decoration, float scale, boolean selected) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(selected ? 0xffb95042 : 0xff315d1f);
        paint.setStrokeWidth(4.3f * scale);
        canvas.drawPath(decoration.stem, paint);
        paint.setColor(selected ? 0xffffb28d : 0xff9cbe50);
        paint.setStrokeWidth(1.5f * scale);
        canvas.drawPath(decoration.stem, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(selected ? 0xffb96842 : 0xff5f9824);
        canvas.drawPath(decoration.leaves, paint);
        paint.setColor(selected ? 0xffffbc84 : 0xffabd449);
        canvas.drawPath(decoration.highlights, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(.58f * scale);
        paint.setColor(selected ? 0xff793628 : 0xff315e1e);
        canvas.drawPath(decoration.veins, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xfffaf8de);
        canvas.drawPath(decoration.petals, paint);
        paint.setColor(0xffd9ad31);
        canvas.drawPath(decoration.centres, paint);
    }

    private void addLeaf(Decoration target, float x, float y, float angle, float scale) {
        setTransform(x, y, angle, scale);
        target.leaves.addPath(leaf, transform);
        target.highlights.addPath(leafHighlight, transform);
        target.veins.addPath(leafVeins, transform);
    }

    private void addFlower(Decoration target, float x, float y, float scale) {
        for (int i = 0; i < 5; i++) {
            setTransform(x, y, i * 72f, scale);
            target.petals.addPath(petal, transform);
        }
        setTransform(x, y, 0, scale);
        target.centres.addPath(centre, transform);
    }

    private void setTransform(float x, float y, float angle, float scale) {
        float radians = (float) Math.toRadians(angle);
        float cosine = (float) Math.cos(radians) * scale;
        float sine = (float) Math.sin(radians) * scale;
        values[0] = cosine; values[1] = -sine; values[2] = x;
        values[3] = sine; values[4] = cosine; values[5] = y;
        values[6] = 0; values[7] = 0; values[8] = 1;
        transform.setValues(values);
    }

    private static float degrees(float[] tangent) {
        return (float) Math.toDegrees(Math.atan2(tangent[1], tangent[0]));
    }
}
