package ru.drshapaya.androidft2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;

/** Owns a snapshot and a detached renderer; never changes the user's canvas. */
final class TreeImageExport {
    final TreeState state;
    final TreeCanvasView renderer;
    final RectF extent;
    final RectF crop;
    String background, frame;
    boolean grid = false, generations, compact, details, straight;
    float fontScale;
    int verticalCuts, horizontalCuts;

    TreeImageExport(MainActivity activity) {
        state = TreeStateCopier.copy(activity.state);
        state.selectedId = "";
        generations = activity.generationLines;
        compact = activity.compactCards;
        details = !activity.hideCardDetails;
        straight = "orthogonal".equals(activity.parentLineMode);
        fontScale = activity.fontScale;
        RewardWallet wallet = new RewardWallet(activity);
        background = wallet.selected(RewardCatalog.CANVAS_BACKGROUNDS);
        frame = wallet.selected(RewardCatalog.EXPORT_FRAMES);
        renderer = new TreeCanvasView(activity);
        renderer.setState(state);
        renderer.setMediaStore(activity.store.mediaStore());
        renderer.setCardStyle(wallet.selected(RewardCatalog.CARD_THEMES));
        renderer.setCardEdgeStyle(wallet.selected(RewardCatalog.CARD_EDGES));
        renderer.setLinkStyle(wallet.selected(RewardCatalog.LINK_STYLES));
        apply();
        crop = renderer.imageExportBounds();
        renderer.setCompactCards(false);
        extent = renderer.imageExportBounds();
        renderer.setCompactCards(compact);
        // Room around the initial crop allows users to expand as well as shrink it.
        extent.inset(-extent.width() * 0.12f, -extent.height() * 0.12f);
    }

    void apply() {
        renderer.setCanvasStyle("white".equals(background) ? RewardCatalog.STANDARD : background);
        renderer.setTheme("white".equals(background) ? "clean" : "light");
        renderer.setGenerationLines(generations);
        renderer.setCompactCards(compact);
        renderer.setHideDetails(!details);
        renderer.setParentLineMode(straight ? "orthogonal" : "smart");
        renderer.setFontScale(fontScale);
    }

    void resetCrop() { crop.set(renderer.imageExportBounds()); }

    ExportLayout layout(float scale, long pixels) {
        return new ExportLayout(crop.width(), crop.height(), scale,
            Math.max(1_000_000L, pixels), verticalCuts, horizontalCuts);
    }

    Bitmap preview() {
        return preview(extent, 4f, 8_000_000L);
    }

    Bitmap preview(RectF previewExtent, float requestedScale, long maxPixels) {
        return preview(previewExtent, requestedScale, maxPixels, 24_000_000L);
    }

    Bitmap previewForDisplay(RectF previewExtent, float requestedScale, long maxPixels,
                             int viewWidth, int viewHeight) {
        long displayPixels = Math.max(1L, (long) Math.max(1, viewWidth) * Math.max(1, viewHeight));
        long cap = Math.max(1_000_000L, Math.min(3_000_000L, displayPixels * 2L));
        return preview(previewExtent, requestedScale, maxPixels, cap);
    }

    private Bitmap preview(RectF previewExtent, float requestedScale, long maxPixels,
                           long previewBudgetCap) {
        RectF bounds = previewExtent == null || previewExtent.isEmpty()
            ? extent
            : previewExtent;
        ExportLayout finalLayout = layout(requestedScale, maxPixels);
        float exportRatio = finalLayout.width / Math.max(1f, crop.width());
        // Preserve the selected export pixel density in the preview. The old maxPixels / 8
        // budget made every option look soft even though the saved PNG was sharper.
        double pixelsAtExportDensity = (double) bounds.width() * bounds.height()
            * exportRatio * exportRatio;
        // The preview is displayed in a relatively small view. Rendering the same
        // 8-24 MP bitmap as the final file only blocks the UI thread and provides no
        // visible benefit on screen. The saved PNG still uses finalLayout unchanged.
        long previewBudget = Math.min(previewBudgetCap,
            Math.max(1_000_000L, (long) Math.ceil(pixelsAtExportDensity)));
        float budgetRatio = (float) Math.sqrt(previewBudget
            / Math.max(1d, (double) bounds.width() * bounds.height()));
        float sideRatio = Math.min(8192f / Math.max(1f, bounds.width()),
            8192f / Math.max(1f, bounds.height()));
        float ratio = Math.max(0.05f, Math.min(exportRatio, Math.min(budgetRatio, sideRatio)));
        int width = Math.max(1, Math.round(bounds.width() * ratio));
        int height = Math.max(1, Math.round(bounds.height() * ratio));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(width / bounds.width(), height / bounds.height());
            renderer.drawExport(canvas, bounds, grid, ratio);
            return bitmap;
        } catch (RuntimeException | OutOfMemoryError error) {
            bitmap.recycle();
            throw error;
        }
    }

    Bitmap tile(ExportLayout layout, int column, int row) {
        int left = layout.x(column), top = layout.y(row);
        int width = layout.x(column + 1) - left;
        int height = layout.y(row + 1) - top;
        // A small bleed keeps antialiased leaves, smoke and flames identical at tile seams.
        int bleed = 8;
        Bitmap bitmap = Bitmap.createBitmap(width + bleed * 2,
            height + bleed * 2, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.translate(bleed - left, bleed - top);
            canvas.scale(layout.width / crop.width(), layout.height / crop.height());
            drawCrop(canvas, Math.max(layout.width / crop.width(), layout.height / crop.height()));
            Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            new Canvas(result).drawBitmap(bitmap, -bleed, -bleed, null);
            bitmap.recycle();
            return result;
        } catch (RuntimeException | OutOfMemoryError error) {
            if (!bitmap.isRecycled()) bitmap.recycle();
            throw error;
        }
    }

    private void drawCrop(Canvas canvas, float pixelsPerWorld) {
        canvas.save();
        // drawExport already maps bounds.left/top to the bitmap origin. Applying the
        // crop translation here as well moved every card and link outside the final
        // PNG while the viewport-sized background still remained visible.
        renderer.drawExport(canvas, crop, grid, pixelsPerWorld);
        canvas.restore();
        ExportDecoration.draw(canvas, new RectF(0, 0, crop.width(), crop.height()), frame, false);
    }

    void close() { renderer.releaseExportResources(); }
}
