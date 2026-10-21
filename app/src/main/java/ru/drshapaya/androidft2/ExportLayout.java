package ru.drshapaya.androidft2;

/** Pixel partitioning shared by the preview, encoder and ZIP manifest. */
final class ExportLayout {
    static final int MAX_BITMAP_SIDE = 8192;
    final int width, height, columns, rows;

    ExportLayout(float worldWidth, float worldHeight, float requestedScale,
                 long pixelsPerTile, int verticalCuts, int horizontalCuts) {
        columns = Math.max(1, Math.min(6, verticalCuts + 1));
        rows = Math.max(1, Math.min(6, horizontalCuts + 1));
        double w = Math.max(1d, worldWidth), h = Math.max(1d, worldHeight);
        double scale = Math.min(Math.max(0.000001d, requestedScale),
            Math.min(28000d / w, 28000d / h));
        // Keep every individual bitmap within a dimension supported reliably
        // by Android software canvases and PNG encoders. Cuts may still raise
        // the total exported width/height up to the existing 28k limit.
        scale = Math.min(scale, Math.min(
            MAX_BITMAP_SIDE * (double) columns / w,
            MAX_BITMAP_SIDE * (double) rows / h));
        long budget = Math.max(4096L, pixelsPerTile);
        scale = Math.min(scale, Math.sqrt(budget * (double) columns * rows / (w * h)));
        int candidateWidth = Math.max(columns, (int) Math.floor(w * scale));
        int candidateHeight = Math.max(rows, (int) Math.floor(h * scale));
        // Rounding up a tile edge must not exceed the per-bitmap memory budget.
        while ((long) ((candidateWidth + columns - 1) / columns)
                * ((candidateHeight + rows - 1) / rows) > budget) {
            if (candidateWidth > columns) candidateWidth--;
            if (candidateHeight > rows) candidateHeight--;
        }
        width = candidateWidth;
        height = candidateHeight;
    }

    static long safePixelsForHeap(long maxHeapBytes) {
        // ARGB_8888 uses four bytes per pixel. Reserving no more than one eighth
        // of the app heap for the bitmap leaves room for the tree model, decoded
        // photos, drawing caches and the PNG compressor.
        long byHeap = Math.max(1L, maxHeapBytes) / 32L;
        return Math.max(1_000_000L, Math.min(12_000_000L, byHeap));
    }

    int x(int column) { return (int) ((long) width * column / columns); }
    int y(int row) { return (int) ((long) height * row / rows); }

    static float fitPdfScale(float width, float height, float targetWidth, float targetHeight) {
        return Math.min(1f, Math.min(targetWidth / Math.max(1f, width),
            targetHeight / Math.max(1f, height)));
    }
}
