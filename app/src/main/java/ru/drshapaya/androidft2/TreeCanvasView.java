package ru.drshapaya.androidft2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Base64;
import android.util.LruCache;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class TreeCanvasView extends View {
    interface Listener {
        void onPersonSelected(Person person);
        void onPersonMenu(Person person, float screenX, float screenY);
        void onLinkSelected(Relation relation);
        void onSelectionChanged(int count);
        void onTreeEditStart(String label, String detail);
        void onPeopleMoved(
            Map<String, PointF> before,
            Map<String, PointF> after,
            String detail);
        void onTreeChanged();
        void onGuideActionMiss();
        void onCanvasTouched();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();
    private final RectF visibleScratch = new RectF();
    private final RectF linkBoundsScratch = new RectF();
    private final Rect tmpSrc = new Rect();
    private final Path clipPath = new Path();
    private final Path linkPathScratch = new Path();
    private final Matrix worldToScreen = new Matrix();
    private final float[] worldToScreenValues = new float[9];
    private final Map<String, LinkGeometry> linkGeometryCache = new HashMap<>();
    private final Set<Integer> generationRows = new HashSet<>();
    private final DashPathEffect guideDash = new DashPathEffect(new float[] {10f, 8f}, 0f);
    private DashPathEffect siblingDash;
    private float siblingDashScale = -1f;
    private final LruCache<String, Bitmap> photoCache;
    private final LruCache<String, String[]> nameLinesCache = new LruCache<>(640);
    private final LruCache<String, String> nameEllipsisCache = new LruCache<>(960);
    private final LruCache<String, CardMeta> cardMetaCache = new LruCache<>(1200);
    private final Map<Long, List<Person>> spatialGrid = new HashMap<>();
    private final List<Person> visiblePeopleScratch = new ArrayList<>();
    private final Set<String> photoLoads = new HashSet<>();
    private final Set<String> failedPhotoLoads = new HashSet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService photoDecoder = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tree-photo-decoder");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final Typeface uiRegular;
    private final Typeface uiBold;
    private final ScaleGestureDetector scaleDetector;
    private TreeState state;
    private TreeMediaStore mediaStore;
    private Listener listener;
    private String filter = "";
    private String branchMode = "all";
    private String branchAnchorId = "";
    private String selectionMode = "";
    private String guideMode = "";
    private String parentLineMode = "smart";
    private String pendingLineFromId = "";
    private String selectedLinkId = "";
    private String guideColor = "#2f7d75";
    private String guideLabel = "Поколение";
    private String theme = "light";
    private Set<String> visibleBranchIds;
    private boolean spatialDirty = true;
    private int todayYear;
    private int todayMonth;
    private int todayDay;
    private long todayRefreshedAt;
    private static final float SPATIAL_CELL = 640f;
    private final Set<String> selectedIds = new HashSet<>();
    private final java.util.List<PointF> selectionPoints = new ArrayList<>();
    private final RectF selectionRect = new RectF();
    private boolean editLocked = false;
    private boolean generationLines = true;
    private boolean hideDetails = false;
    private boolean compactCards = false;
    private boolean workspaceBoundsVisible = true;
    private String workspaceBoundsStyle = "soft";
    private float workspaceWidth = TreeLayoutEngine.SURFACE_W;
    private float workspaceHeight = TreeLayoutEngine.SURFACE_H;
    private boolean appendSelection = false;
    private float scale = 0.55f;
    private float offsetX = 80f;
    private float offsetY = 160f;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private String dragPersonId = "";
    private boolean draggingCanvas = false;
    private boolean selecting = false;
    private boolean moved = false;
    private final int touchSlop;
    private float gestureStartOffsetX;
    private float gestureStartOffsetY;
    private final Map<String, PointF> dragStartPositions = new HashMap<>();
    private final List<String> dragPersonIds = new ArrayList<>();
    private String focusHighlightId = "";
    private long focusHighlightUntil = 0L;
    private final Map<String, TreeQualityAnalyzer.PersonReport> qualityReports = new HashMap<>();
    private final Set<String> highlightedPathIds = new HashSet<>();
    private final Set<String> highlightedPathEdges = new HashSet<>();
    private long highlightedPathUntil = 0L;
    TreeCanvasView(Context context) {
        super(context);
        setFocusable(true);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        int maxCacheKb = (int) Math.max(
            8L * 1024L,
            Math.min(32L * 1024L, Runtime.getRuntime().maxMemory() / 1024L / 8L));
        photoCache = new LruCache<String, Bitmap>(maxCacheKb) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return Math.max(1, bitmap.getAllocationByteCount() / 1024);
            }
        };
        uiRegular = loadTypeface(context, "fonts/NotoSans-Regular.ttf", Typeface.NORMAL);
        uiBold = loadTypeface(context, "fonts/NotoSans-Bold.ttf", Typeface.BOLD);
        textPaint.setColor(Color.rgb(34, 37, 39));
        textPaint.setTextSize(28f);
        textPaint.setTypeface(uiRegular);
        textPaint.setLinearText(true);
        textPaint.setSubpixelText(true);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                finishActiveCardDrag();
                draggingCanvas = false;
                selecting = false;
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                zoom(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
    }

    void setState(TreeState state) {
        if (this.state != state) {
            photoCache.evictAll();
            nameLinesCache.evictAll();
            nameEllipsisCache.evictAll();
            cardMetaCache.evictAll();
            linkGeometryCache.clear();
            failedPhotoLoads.clear();
        }
        this.state = state;
        visibleBranchIds = null;
        spatialDirty = true;
        invalidate();
    }

    void setMediaStore(TreeMediaStore mediaStore) {
        if (this.mediaStore != mediaStore) {
            photoCache.evictAll();
            failedPhotoLoads.clear();
        }
        this.mediaStore = mediaStore;
    }

    void trimBitmapCache(int level) {
        if (level >= 80) {
            photoCache.evictAll();
            nameLinesCache.evictAll();
            nameEllipsisCache.evictAll();
            cardMetaCache.evictAll();
        } else if (level >= 10 && level < 20) {
            photoCache.trimToSize(Math.max(1, photoCache.maxSize() / 2));
        }
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setFilter(String filter) {
        this.filter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        invalidate();
    }

    void setBranchMode(String mode, String anchorId) {
        branchMode = mode == null || mode.isEmpty() ? "all" : mode;
        branchAnchorId = anchorId == null ? "" : anchorId;
        visibleBranchIds = null;
        invalidate();
    }

    void invalidateStructureCaches() {
        visibleBranchIds = null;
        spatialDirty = true;
        if (state == null || linkGeometryCache.size() > state.links.size() * 2 + 32) {
            linkGeometryCache.clear();
        }
        invalidate();
    }

    void setEditLocked(boolean locked) {
        editLocked = locked;
    }

    void setGenerationLines(boolean enabled) {
        generationLines = enabled;
        invalidate();
    }

    void setQualityReports(Map<String, TreeQualityAnalyzer.PersonReport> reports) {
        qualityReports.clear();
        if (reports != null) qualityReports.putAll(reports);
        invalidate();
    }

    void highlightKinshipPath(List<String> personIds) {
        highlightedPathIds.clear();
        highlightedPathEdges.clear();
        if (personIds != null) {
            highlightedPathIds.addAll(personIds);
            for (int index = 1; index < personIds.size(); index++) {
                highlightedPathEdges.add(edgeKey(personIds.get(index - 1), personIds.get(index)));
            }
        }
        highlightedPathUntil = highlightedPathIds.isEmpty()
            ? 0L
            : SystemClock.uptimeMillis() + 6000L;
        invalidate();
    }

    void setHideDetails(boolean enabled) {
        hideDetails = enabled;
        invalidate();
    }

    void setCompactCards(boolean enabled) {
        compactCards = enabled;
        invalidate();
    }

    void setSelectionMode(String mode) {
        selectionMode = "lasso".equals(mode) || "rect".equals(mode) ? mode : "";
        selecting = false;
        selectionPoints.clear();
        selectionRect.setEmpty();
        notifySelectionChanged();
        invalidate();
    }

    void setSelectionAppendMode(boolean enabled) {
        appendSelection = enabled;
    }

    Set<String> selectedIds() {
        return new HashSet<>(selectedIds);
    }

    boolean hasActiveSelectionMode() {
        return !selectionMode.isEmpty();
    }

    String selectionModeLabel() {
        if ("lasso".equals(selectionMode)) {
            return AppLanguage.text(getContext(), "Лассо");
        }
        if ("rect".equals(selectionMode)) {
            return AppLanguage.text(getContext(), "Рамка");
        }
        return "";
    }

    void clearSelection() {
        selectionMode = "";
        selectedIds.clear();
        selectionPoints.clear();
        selectionRect.setEmpty();
        selecting = false;
        notifySelectionChanged();
        invalidate();
    }

    void setGuideMode(String mode) {
        guideMode = "h".equals(mode) || "v".equals(mode) || "erase".equals(mode) ? mode : "";
        selectionMode = "";
        selecting = false;
        selectionPoints.clear();
        selectionRect.setEmpty();
        notifySelectionChanged();
        invalidate();
    }

    void setGuideDraft(String color, String label) {
        String value = color == null ? "" : color.trim();
        guideColor = value.matches("#[0-9a-fA-F]{6}") ? value : "#2f7d75";
        guideLabel = label == null ? "" : label.trim();
        if (guideLabel.length() > 32) guideLabel = guideLabel.substring(0, 32);
    }

    void setParentLineMode(String mode) {
        parentLineMode = "orthogonal".equals(mode) ? "orthogonal" : "smart";
        invalidate();
    }

    void setLinkState(String pendingFromId, String selectedId) {
        pendingLineFromId = pendingFromId == null ? "" : pendingFromId;
        selectedLinkId = selectedId == null ? "" : selectedId;
        invalidate();
    }

    PointF viewportCenterWorld() {
        return new PointF((getWidth() / 2f - offsetX) / scale - cardWidthWorld() / 2f, (getHeight() / 2f - offsetY) / scale - cardHeightWorld() / 2f);
    }

    void setTheme(String value) {
        theme = "print".equals(value) ? "clean" : "dark".equals(value) || "clean".equals(value) ? value : "light";
        invalidate();
    }

    void setWorkspaceBounds(boolean visible, String style, int width, int height) {
        workspaceBoundsVisible = visible;
        workspaceBoundsStyle = "contrast".equals(style) || "outline".equals(style)
            ? style
            : "soft";
        workspaceWidth = TreeLayoutEngine.normalizeSurfaceWidth(width);
        workspaceHeight = TreeLayoutEngine.normalizeSurfaceHeight(height);
        invalidate();
    }

    void zoomBy(float factor) {
        zoom(factor, getWidth() / 2f, getHeight() / 2f);
    }

    void fit() {
        if (state == null || state.people.isEmpty() || getWidth() == 0 || getHeight() == 0) return;
        RectF bounds = treeBounds();
        float sx = (getWidth() - 80f) / Math.max(cardWidthWorld(), bounds.width());
        float sy = (getHeight() - 180f) / Math.max(cardHeightWorld(), bounds.height());
        scale = clamp(Math.min(sx, sy), 0.08f, 2.2f);
        offsetX = getWidth() / 2f - bounds.centerX() * scale;
        offsetY = getHeight() / 2f - bounds.centerY() * scale;
        invalidate();
    }

    void focusWorkspaceCenter() {
        if (getWidth() == 0 || getHeight() == 0) return;
        offsetX = getWidth() / 2f - workspaceWidth / 2f * scale;
        offsetY = getHeight() / 2f - workspaceHeight / 2f * scale;
        invalidate();
    }

    void focusPerson(String personId) {
        if (state == null || getWidth() == 0 || getHeight() == 0) return;
        Person person = state.people.get(personId);
        if (person == null) return;
        scale = clamp(Math.max(scale, 0.95f), 0.08f, 2.2f);
        offsetX = getWidth() / 2f - (person.x + cardWidthWorld() / 2f) * scale;
        offsetY = getHeight() / 2f - (person.y + cardHeightWorld() / 2f) * scale;
        focusHighlightId = personId;
        focusHighlightUntil = SystemClock.uptimeMillis() + 1800L;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawScene(canvas, getWidth(), getHeight(), false);
    }

    Bitmap renderBitmap() {
        return renderBitmap(8f, 16_000_000L);
    }

    int[] estimateRenderBitmapSize(float requestedScale, long maxPixels) {
        ExportBitmapSpec spec = exportBitmapSpec(requestedScale, maxPixels);
        return new int[] {spec.width, spec.height};
    }

    Bitmap renderBitmap(float requestedScale, long maxPixels) {
        if (state == null || state.people.isEmpty()) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        RectF bounds = exportBounds();
        ExportBitmapSpec spec = exportBitmapSpec(bounds, requestedScale, maxPixels);
        float exportScale = spec.scale;
        float padding = spec.padding;
        int width = spec.width;
        int height = spec.height;
        float oldScale = scale;
        float oldX = offsetX;
        float oldY = offsetY;
        try {
            scale = exportScale;
            offsetX = padding - bounds.left * scale;
            offsetY = padding - bounds.top * scale;
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawScene(canvas, width, height, true);
            return bitmap;
        } finally {
            scale = oldScale;
            offsetX = oldX;
            offsetY = oldY;
        }
    }

    private ExportBitmapSpec exportBitmapSpec(float requestedScale, long maxPixels) {
        if (state == null || state.people.isEmpty()) {
            return new ExportBitmapSpec(1f, 0f, 1, 1);
        }
        return exportBitmapSpec(exportBounds(), requestedScale, maxPixels);
    }

    private ExportBitmapSpec exportBitmapSpec(
        RectF bounds,
        float requestedScale,
        long maxPixels
    ) {
        float padding = 72f;
        float exportScale = clamp(requestedScale, 1f, 16f);
        float maxSideScale = Math.min(
            (28000f - padding * 2f) / Math.max(1f, bounds.width()),
            (28000f - padding * 2f) / Math.max(1f, bounds.height()));
        exportScale = Math.max(0.1f, Math.min(exportScale, maxSideScale));
        long safeMaxPixels = Math.max(2_000_000L, Math.min(64_000_000L, maxPixels));
        int width = exportBitmapSide(bounds.width(), exportScale, padding);
        int height = exportBitmapSide(bounds.height(), exportScale, padding);
        for (int attempt = 0; attempt < 3; attempt++) {
            long pixels = (long) width * height;
            if (pixels <= safeMaxPixels) break;
            exportScale *= (float) Math.sqrt((double) safeMaxPixels / pixels);
            width = exportBitmapSide(bounds.width(), exportScale, padding);
            height = exportBitmapSide(bounds.height(), exportScale, padding);
        }
        return new ExportBitmapSpec(exportScale, padding, width, height);
    }

    private static int exportBitmapSide(float worldSide, float exportScale, float padding) {
        return Math.max(1, Math.round(Math.max(1f, worldSide) * exportScale + padding * 2f));
    }

    private static final class ExportBitmapSpec {
        final float scale;
        final float padding;
        final int width;
        final int height;

        ExportBitmapSpec(float scale, float padding, int width, int height) {
            this.scale = scale;
            this.padding = padding;
            this.width = width;
            this.height = height;
        }
    }

    private void drawScene(Canvas canvas, int width, int height, boolean exportMode) {
        canvas.drawColor(canvasBackground());
        if (!exportMode && !"clean".equals(theme)) drawGrid(canvas, width, height);
        if (!exportMode) drawWorkspaceBounds(canvas, width, height);
        if (state == null) return;
        refreshToday();
        prepareBranchCache();
        visibleScratch.set(
            wx(0) - 520f / scale,
            wy(0) - 520f / scale,
            wx(width) + 520f / scale,
            wy(height) + 520f / scale);
        RectF visible = visibleScratch;
        if (generationLines) drawGenerationLines(canvas, visible, width, height);
        if (exportMode || state.guidesVisible) drawGuides(canvas, visible);
        drawLinks(canvas, visible);
        drawCards(canvas, visible, exportMode);
        if (!exportMode) drawSelectionOverlay(canvas);
    }

    private void drawGrid(Canvas canvas, int width, int height) {
        float grid = Math.max(1f, TreeLayoutEngine.GRID * scale);
        float startX = positiveModulo(offsetX, grid);
        float startY = positiveModulo(offsetY, grid);
        paint.setStrokeWidth(1f);
        paint.setColor(Color.argb(scale < 0.2f ? 20 : 34, 45, 112, 125));
        for (float x = startX; x < width; x += grid) canvas.drawLine(x, 0, x, height, paint);
        for (float y = startY; y < height; y += grid) canvas.drawLine(0, y, width, y, paint);
    }

    private void drawWorkspaceBounds(Canvas canvas, int width, int height) {
        if (!workspaceBoundsVisible) return;
        float left = sx(0f);
        float top = sy(0f);
        float right = sx(workspaceWidth);
        float bottom = sy(workspaceHeight);

        // Slightly shade only the part outside the editable surface. This keeps the
        // boundary understandable even when just one of its sides is on screen.
        paint.setStyle(Paint.Style.FILL);
        paint.setPathEffect(null);
        boolean outlineOnly = "outline".equals(workspaceBoundsStyle);
        boolean contrast = "contrast".equals(workspaceBoundsStyle);
        paint.setColor("dark".equals(theme)
            ? Color.argb(contrast ? 96 : 64, 0, 0, 0)
            : Color.argb(contrast ? 54 : 28, 46, 64, 72));
        if (!outlineOnly) {
            if (left > 0f) canvas.drawRect(0f, 0f, Math.min(width, left), height, paint);
            if (right < width) canvas.drawRect(Math.max(0f, right), 0f, width, height, paint);
            float innerLeft = Math.max(0f, left);
            float innerRight = Math.min(width, right);
            if (innerRight > innerLeft) {
                if (top > 0f) canvas.drawRect(innerLeft, 0f, innerRight, Math.min(height, top), paint);
                if (bottom < height) canvas.drawRect(innerLeft, Math.max(0f, bottom), innerRight, height, paint);
            }
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(contrast ? 3.2f : outlineOnly ? 1.6f : 2.2f);
        paint.setColor("dark".equals(theme)
            ? Color.argb(contrast ? 245 : 210, 83, 211, 197)
            : Color.argb(contrast ? 240 : 190, 8, 122, 115));
        paint.setPathEffect(outlineOnly ? null : new DashPathEffect(
            contrast ? new float[] {18f, 7f} : new float[] {14f, 9f},
            0f));
        canvas.drawRect(left, top, right, bottom, paint);
        paint.setPathEffect(null);
    }

    private int canvasBackground() {
        if ("dark".equals(theme)) return Color.rgb(22, 24, 22);
        if ("clean".equals(theme)) return Color.WHITE;
        return Color.rgb(243, 246, 248);
    }

    private void drawLinks(Canvas canvas, RectF visible) {
        boolean pathActive = highlightedPathUntil > SystemClock.uptimeMillis();
        if (!pathActive && !highlightedPathIds.isEmpty()) {
            highlightedPathIds.clear();
            highlightedPathEdges.clear();
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(2.4f, 3.6f * scale));
        for (Relation link : state.links) {
            Person from = state.people.get(link.from);
            Person to = state.people.get(link.to);
            if (from == null || to == null || !matches(from) || !matches(to)) continue;
            linkBoundsScratch.set(
                Math.min(from.x, to.x) - 280f,
                Math.min(from.y, to.y) - 280f,
                Math.max(from.x, to.x) + cardWidthWorld() + 280f,
                Math.max(from.y, to.y) + cardHeightWorld() + 280f);
            if (!RectF.intersects(visible, linkBoundsScratch)) continue;
            boolean selected = link.id != null && link.id.equals(selectedLinkId);
            boolean onPath = pathActive && highlightedPathEdges.contains(edgeKey(link.from, link.to));
            paint.setColor(onPath
                ? Color.rgb(47, 140, 255)
                : selected
                    ? Color.rgb(197, 83, 75)
                    : "parent".equals(link.type)
                        ? Color.rgb(47, 125, 117)
                        : Color.argb(190, 83, 94, 88));
            paint.setStrokeWidth(onPath
                ? Math.max(5f, 8f * scale)
                : selected ? Math.max(3.4f, 5f * scale) : Math.max(2.4f, 3.6f * scale));
            paint.setPathEffect(onPath ? null : "sibling".equals(link.type) ? siblingDashEffect() : null);
            Path path = linkPath(link, from, to);
            canvas.drawPath(path, paint);
        }
        paint.setPathEffect(null);
        if (pathActive) postInvalidateOnAnimation();
    }

    private void drawGenerationLines(Canvas canvas, RectF visible, int width, int height) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, 1.5f * scale));
        paint.setPathEffect(null);
        generationRows.clear();
        for (Person person : state.people.values()) {
            if (!matches(person)) continue;
            int row = Math.round(TreeLayoutEngine.snap(person.y + cardHeightWorld()));
            if (!generationRows.add(row)) continue;
            float y = sy(row);
            if (y < -40f || y > height + 40f) continue;
            int alpha = Math.max(46, Math.min(120, (int) (95 * scale)));
            paint.setColor(Color.argb(alpha, 24, 169, 82));
            canvas.drawLine(0, y, width, y, paint);
        }
    }

    private void drawGuides(Canvas canvas, RectF visible) {
        if (state.guides.isEmpty()) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.8f, 2.2f * scale));
        paint.setPathEffect(guideDash);
        textPaint.setTypeface(uiBold);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(clamp(13f * scale, 8f, 13f));
        for (Guide guide : state.guides) {
            int color = TreeState.parseColor(guide.color, Color.rgb(47, 125, 117));
            paint.setColor(color);
            textPaint.setColor(color);
            if ("v".equals(guide.axis)) {
                if (guide.position < visible.left || guide.position > visible.right) continue;
                float x = sx(guide.position);
                canvas.drawLine(x, sy(visible.top), x, sy(visible.bottom), paint);
                if (!guide.label.isEmpty()) {
                    canvas.drawText(
                        AppLanguage.text(getContext(), guide.label),
                        x + 8f,
                        Math.max(18f, sy(visible.top) + 22f),
                        textPaint);
                }
            } else {
                if (guide.position < visible.top || guide.position > visible.bottom) continue;
                float y = sy(guide.position);
                canvas.drawLine(sx(visible.left), y, sx(visible.right), y, paint);
                if (!guide.label.isEmpty()) {
                    canvas.drawText(
                        AppLanguage.text(getContext(), guide.label),
                        Math.max(8f, sx(visible.left) + 12f),
                        y - 8f,
                        textPaint);
                }
            }
        }
        paint.setPathEffect(null);
        textPaint.setFakeBoldText(false);
    }

    private void drawCards(Canvas canvas, RectF visible, boolean exportMode) {
        Iterable<Person> people = dragPersonIds.isEmpty()
            ? visiblePeople(visible)
            : state.people.values();
        for (Person person : people) {
            if (!matches(person)) continue;
            tmp.set(person.x, person.y, person.x + cardWidthWorld(), person.y + cardHeightWorld());
            if (!RectF.intersects(visible, tmp)) continue;
            drawCard(canvas, person, exportMode);
        }
    }

    private void drawCard(Canvas canvas, Person person, boolean exportMode) {
        float left = sx(person.x);
        float top = sy(person.y);
        float width = cardWidthWorld() * scale;
        float height = cardHeightWorld() * scale;
        float radius = 8f * scale;

        paint.setStyle(Paint.Style.FILL);
        if (scale > 0.24f) {
            paint.setColor(Color.argb(28, 30, 42, 52));
            float shadowY = Math.max(1f, 5f * scale);
            canvas.drawRoundRect(
                left,
                top + shadowY,
                left + width,
                top + height + shadowY,
                radius,
                radius,
                paint);
        }
        paint.setColor(adjustForSelection(person));
        canvas.drawRoundRect(left, top, left + width, top + height, radius, radius, paint);
        boolean selected = person.id.equals(state.selectedId);
        boolean multiSelected = selectedIds.contains(person.id);
        boolean lineStart = person.id.equals(pendingLineFromId);
        boolean onHighlightedPath = highlightedPathUntil > SystemClock.uptimeMillis()
            && highlightedPathIds.contains(person.id);
        if (onHighlightedPath) {
            float pathHalo = Math.max(4f, 7f * scale);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(3.2f, 6.5f * scale));
            paint.setColor(Color.argb(225, 47, 140, 255));
            canvas.drawRoundRect(
                left - pathHalo,
                top - pathHalo,
                left + width + pathHalo,
                top + height + pathHalo,
                radius + pathHalo,
                radius + pathHalo,
                paint);
            postInvalidateOnAnimation();
        }
        if (selected && !multiSelected) {
            float halo = Math.max(2.5f, 4.5f * scale);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2.4f, 5f * scale));
            paint.setColor(Color.argb(215, 24, 169, 153));
            canvas.drawRoundRect(
                left - halo,
                top - halo,
                left + width + halo,
                top + height + halo,
                radius + halo,
                radius + halo,
                paint);
        }
        if (multiSelected) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(3.6f, 7.5f * scale));
            paint.setColor(Color.argb(188, 27, 117, 255));
            canvas.drawRoundRect(left - 4f * scale, top - 4f * scale, left + width + 4f * scale, top + height + 4f * scale, radius + 5f * scale, radius + 5f * scale, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(selected || multiSelected
            ? Math.max(2.6f, 3.8f * scale)
            : (lineStart || person.pinned ? 3.2f : 2f) * scale);
        paint.setColor(lineStart ? Color.rgb(197, 83, 75) : multiSelected ? Color.rgb(27, 117, 255) : selected ? Color.rgb(8, 122, 115) : person.pinned ? Color.rgb(31, 94, 89) : Color.argb(180, 255, 255, 255));
        canvas.drawRoundRect(left, top, left + width, top + height, radius, radius, paint);
        if (person.id.equals(focusHighlightId)) {
            long remaining = focusHighlightUntil - SystemClock.uptimeMillis();
            if (remaining > 0L) {
                float phase = remaining / 1800f;
                int alpha = 90 + Math.round(110f * (1f - phase));
                float halo = (7f + 10f * (1f - phase)) * scale;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(3.5f, 5.5f * scale));
                paint.setColor(Color.argb(Math.min(220, alpha), 24, 169, 153));
                canvas.drawRoundRect(
                    left - halo,
                    top - halo,
                    left + width + halo,
                    top + height + halo,
                    radius + halo,
                    radius + halo,
                    paint);
                postInvalidateOnAnimation();
            } else {
                focusHighlightId = "";
            }
        }
        if (multiSelected && scale > 0.16f) {
            float marker = 23f * scale;
            float markerLeft = left - 5f * scale;
            float markerTop = top - 5f * scale;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(27, 117, 255));
            canvas.drawCircle(markerLeft + marker / 2f, markerTop + marker / 2f, marker / 2f, paint);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(uiBold);
            textPaint.setFakeBoldText(true);
            textPaint.setTextSize(15f * scale);
            Paint.FontMetrics checkFm = textPaint.getFontMetrics();
            canvas.drawText("✓", markerLeft + marker / 2f, markerTop + marker / 2f - (checkFm.ascent + checkFm.descent) / 2f, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
        }

        float pad = (compactCards ? 10f : 14f) * scale;
        float textLeft = left + pad;
        float textMaxWidth = width - pad * 2;

        float textScale = cardTextScale();

        {
            float avatar = (compactCards ? 56f : 80f) * scale;
            float cx = left + pad + avatar / 2f;
            float cy = top + pad + avatar / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(128, 255, 255, 255));
            canvas.drawCircle(cx, cy, avatar / 2f, paint);
            Bitmap photo = photoBitmap(person, exportMode);
            if (photo != null) {
                RectF avatarRect = new RectF(cx - avatar / 2f, cy - avatar / 2f, cx + avatar / 2f, cy + avatar / 2f);
                clipPath.reset();
                clipPath.addCircle(cx, cy, avatar / 2f, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(clipPath);
                drawPhotoCenterCrop(canvas, photo, avatarRect);
                canvas.restore();
                if (exportMode && !photo.isRecycled()) photo.recycle();
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f * scale);
            paint.setColor(Color.argb(184, 255, 255, 255));
            canvas.drawCircle(cx, cy, avatar / 2f, paint);
            Paint.FontMetrics fm;
            if (photo == null) {
                paint.setStyle(Paint.Style.FILL);
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setColor(Color.argb(190, 34, 37, 39));
                textPaint.setFakeBoldText(true);
                textPaint.setTypeface(uiBold);
                textPaint.setTextSize(18f * textScale);
                fm = textPaint.getFontMetrics();
                canvas.drawText(
                    cardMeta(person).initials,
                    cx,
                    cy - (fm.ascent + fm.descent) / 2f,
                    textPaint);
            }
            textPaint.setTextAlign(Paint.Align.LEFT);
            textLeft = left + pad + avatar + (compactCards ? 9f : 12f) * scale;
            textMaxWidth = Math.max(1f, width - (textLeft - left) - pad);

            if (scale > 0.11f) {
                float menu = (compactCards ? 23f : 28f) * scale;
                float menuLeft = left + width - 7f * scale - menu;
                float menuTop = top + 7f * scale;
                paint.setColor(Color.argb(76, 255, 255, 255));
                canvas.drawRoundRect(menuLeft, menuTop, menuLeft + menu, menuTop + menu, 5f * scale, 5f * scale, paint);
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setColor(Color.rgb(83, 94, 88));
                textPaint.setTextSize(17f * textScale);
                fm = textPaint.getFontMetrics();
                canvas.drawText("⋮", menuLeft + menu / 2f, menuTop + menu / 2f - (fm.ascent + fm.descent) / 2f, textPaint);
                textPaint.setTextAlign(Paint.Align.LEFT);
            }
        }

        textPaint.setColor(Color.rgb(34, 37, 39));
        textPaint.setFakeBoldText(true);
        textPaint.setTypeface(uiBold);
        textPaint.setTextSize((compactCards ? 15.5f : 18.5f) * textScale);
        drawNameLines(
            canvas,
            person.name.isEmpty()
                ? AppLanguage.text(getContext(), "Без имени")
                : person.name,
            textLeft,
            top + pad + textPaint.getTextSize(),
            textMaxWidth,
            textScale);

        if (!hideDetails && scale > 0.18f) {
            textPaint.setFakeBoldText(false);
            textPaint.setTypeface(uiRegular);
            textPaint.setColor(Color.argb(184, 34, 37, 39));
            textPaint.setTextSize((compactCards ? 13f : 15f) * textScale);
            CardMeta meta = cardMeta(person);
            String date = meta.date;
            String place = meta.place;
            float metaLeft = left + pad;
            float qualityReserve = qualityReports.containsKey(person.id)
                ? 28f * scale
                : 0f;
            float metaWidth = Math.max(1f, width - pad * 2f - qualityReserve);
            float bottomBaseline = top + height - pad - 2f;
            float iconSize = (compactCards ? 10.5f : 12.5f) * textScale;
            float iconGap = 5f * textScale;
            float metaTextLeft = metaLeft + iconSize + iconGap;
            float metaTextWidth = Math.max(1f, metaWidth - iconSize - iconGap);
            if (!date.isEmpty() && !place.isEmpty()) {
                float dateBaseline = bottomBaseline - 19f * textScale;
                drawCalendarMetaIcon(canvas, metaLeft, dateBaseline, iconSize);
                drawFittedSingleLine(canvas, date, metaTextLeft, dateBaseline, metaTextWidth);
                drawLocationMetaIcon(canvas, metaLeft, bottomBaseline, iconSize);
                drawFittedSingleLine(canvas, place, metaTextLeft, bottomBaseline, metaTextWidth);
            } else if (!date.isEmpty()) {
                drawCalendarMetaIcon(canvas, metaLeft, bottomBaseline, iconSize);
                drawFittedSingleLine(canvas, date, metaTextLeft, bottomBaseline, metaTextWidth);
            } else if (!place.isEmpty()) {
                drawLocationMetaIcon(canvas, metaLeft, bottomBaseline, iconSize);
                drawFittedSingleLine(canvas, place, metaTextLeft, bottomBaseline, metaTextWidth);
            }
        }
        if (!exportMode) drawQualityIndicator(canvas, person, left, top, width, height);
    }

    private void drawQualityIndicator(
        Canvas canvas,
        Person person,
        float left,
        float top,
        float width,
        float height
    ) {
        TreeQualityAnalyzer.PersonReport report = qualityReports.get(person.id);
        if (report == null) return;
        int severity = report.topSeverity();
        int color = severity == TreeQualityAnalyzer.ERROR
            ? Color.rgb(211, 73, 67)
            : severity == TreeQualityAnalyzer.WARNING
                ? Color.rgb(224, 162, 38)
                : severity == TreeQualityAnalyzer.RECOMMENDATION
                    ? Color.rgb(126, 137, 145)
                    : Color.rgb(24, 169, 153);
        // Keep the badge proportional to the card. A screen-fixed badge overwhelms
        // small cards when the complete tree is fitted on screen.
        float diameter = Math.max(1f, 22f * scale);
        float inset = 10f * scale;
        float cx = left + width - inset - diameter / 2f;
        float cy = top + height - inset - diameter / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(232, 255, 255, 255));
        canvas.drawCircle(cx, cy, diameter / 2f + Math.max(0.35f, 1.8f * scale), paint);
        paint.setColor(color);
        canvas.drawCircle(cx, cy, diameter / 2f, paint);
        if (diameter < 7f) return;
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(uiBold);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(diameter * 0.62f);
        textPaint.setColor(Color.WHITE);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        String marker = severity == 0 ? "✓" : severity == TreeQualityAnalyzer.RECOMMENDATION ? "i" : "!";
        canvas.drawText(marker, cx, cy - (metrics.ascent + metrics.descent) / 2f, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private static String edgeKey(String first, String second) {
        String a = first == null ? "" : first;
        String b = second == null ? "" : second;
        return a.compareTo(b) <= 0 ? a + "\u0000" + b : b + "\u0000" + a;
    }

    private void drawCalendarMetaIcon(Canvas canvas, float left, float baseline, float size) {
        float top = baseline - size * 0.86f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, size * 0.12f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(textPaint.getColor());
        RectF body = new RectF(left, top + size * 0.16f, left + size, top + size);
        canvas.drawRoundRect(body, size * 0.15f, size * 0.15f, paint);
        canvas.drawLine(left + size * 0.24f, top, left + size * 0.24f, top + size * 0.34f, paint);
        canvas.drawLine(left + size * 0.76f, top, left + size * 0.76f, top + size * 0.34f, paint);
        canvas.drawLine(left, top + size * 0.43f, left + size, top + size * 0.43f, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawLocationMetaIcon(Canvas canvas, float left, float baseline, float size) {
        float cx = left + size / 2f;
        float top = baseline - size * 0.96f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, size * 0.12f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(textPaint.getColor());
        linkPathScratch.reset();
        linkPathScratch.moveTo(cx, top + size);
        linkPathScratch.cubicTo(
            left + size * 0.12f, top + size * 0.58f,
            left + size * 0.16f, top + size * 0.08f,
            cx, top + size * 0.08f);
        linkPathScratch.cubicTo(
            left + size * 0.84f, top + size * 0.08f,
            left + size * 0.88f, top + size * 0.58f,
            cx, top + size);
        canvas.drawPath(linkPathScratch, paint);
        canvas.drawCircle(cx, top + size * 0.39f, size * 0.13f, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private int adjustForSelection(Person person) {
        if (selectedIds.contains(person.id)) return blend(person.color, Color.rgb(27, 117, 255), 0.16f);
        if (!person.id.equals(state.selectedId)) return person.color;
        return blend(blend(person.color, Color.WHITE, 0.16f), Color.rgb(24, 169, 153), 0.18f);
    }

    private String dateMeta(Person person) {
        String born = displayDate(person.bornDay, person.bornMonth, person.bornYear);
        String died = displayDate(person.diedDay, person.diedMonth, person.diedYear);
        int age = age(person);
        String ageText = age < 0 ? "" : " (" + ageLabel(age) + ")";
        if (!born.isEmpty() && !died.isEmpty()) return born + " - " + died + ageText;
        if (!born.isEmpty()) {
            return (AppLanguage.isEnglish(getContext()) ? "Born " : "Род. ")
                + born + ageText;
        }
        if (!died.isEmpty()) {
            return (AppLanguage.isEnglish(getContext()) ? "Died " : "Ум. ") + died;
        }
        return "";
    }

    private int age(Person person) {
        int bornYear = intPart(person.bornYear);
        if (bornYear <= 0) return -1;
        int diedYear = intPart(person.diedYear);
        int endYear = diedYear > 0 ? diedYear : todayYear;
        int endMonth = diedYear > 0
            ? Math.max(1, intPart(person.diedMonth))
            : todayMonth;
        int endDay = diedYear > 0
            ? Math.max(1, intPart(person.diedDay))
            : todayDay;
        int value = endYear - bornYear;
        int bornMonth = intPart(person.bornMonth);
        int bornDay = intPart(person.bornDay);
        if (bornMonth > 0 && bornDay > 0) {
            if (endMonth < bornMonth || (endMonth == bornMonth && endDay < bornDay)) value--;
        }
        return value < 0 ? -1 : value;
    }

    private int intPart(String value) {
        try {
            return value == null || value.trim().isEmpty() ? 0 : Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String ageLabel(int age) {
        if (AppLanguage.isEnglish(getContext())) {
            return age + (age == 1 ? " year" : " years");
        }
        int mod100 = age % 100;
        int mod10 = age % 10;
        if (mod100 >= 11 && mod100 <= 14) return age + " лет";
        if (mod10 == 1) return age + " год";
        if (mod10 >= 2 && mod10 <= 4) return age + " года";
        return age + " лет";
    }

    private CardMeta cardMeta(Person person) {
        int signature = 17;
        signature = 31 * signature + stringHash(person.name);
        signature = 31 * signature + stringHash(person.bornDay);
        signature = 31 * signature + stringHash(person.bornMonth);
        signature = 31 * signature + stringHash(person.bornYear);
        signature = 31 * signature + stringHash(person.diedDay);
        signature = 31 * signature + stringHash(person.diedMonth);
        signature = 31 * signature + stringHash(person.diedYear);
        signature = 31 * signature + stringHash(person.place);
        signature = 31 * signature + stringHash(person.notes);
        signature = 31 * signature + todayYear;
        signature = 31 * signature + todayMonth;
        signature = 31 * signature + todayDay;
        CardMeta cached = cardMetaCache.get(person.id);
        if (cached != null && cached.signature == signature) return cached;
        String place = person.place == null ? "" : person.place.trim();
        String search = ((person.name == null ? "" : person.name)
            + " " + safe(person.bornYear)
            + " " + safe(person.diedYear)
            + " " + place
            + " " + safe(person.notes)).toLowerCase(Locale.ROOT);
        CardMeta meta = new CardMeta(
            signature,
            initials(person.name),
            dateMeta(person),
            place,
            search);
        cardMetaCache.put(person.id, meta);
        return meta;
    }

    private void refreshToday() {
        long now = System.currentTimeMillis();
        if (todayYear > 0 && now - todayRefreshedAt < 60L * 60L * 1000L) return;
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        if (todayYear != year || todayMonth != month || todayDay != day) {
            cardMetaCache.evictAll();
        }
        todayYear = year;
        todayMonth = month;
        todayDay = day;
        todayRefreshedAt = now;
    }

    private static int stringHash(String value) {
        return value == null ? 0 : value.hashCode();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private Bitmap photoBitmap(Person person, boolean exportMode) {
        if (person == null) return null;
        String mediaId = person.photoMediaId == null ? "" : person.photoMediaId;
        String inlinePhoto = person.photo == null ? "" : person.photo;
        if (mediaId.isEmpty() && inlinePhoto.isEmpty()) return null;
        if (exportMode) {
            int targetSize = Math.max(
                256,
                Math.min(
                    1536,
                    Math.round((compactCards ? 48f : 64f) * Math.max(1f, scale))));
            if (!mediaId.isEmpty() && mediaStore != null) {
                return mediaStore.decodeBitmap(mediaId, targetSize);
            }
            int separator = inlinePhoto.indexOf(',');
            try {
                if (inlinePhoto.toLowerCase(Locale.ROOT).startsWith("data:image")
                    && separator > 0) {
                    byte[] bytes = Base64.decode(
                        inlinePhoto.substring(separator + 1),
                        Base64.DEFAULT);
                    return MainActivity.decodeBitmapBytes(bytes, targetSize);
                }
                if (inlinePhoto.length() > 128 && !inlinePhoto.contains("://")) {
                    byte[] bytes = Base64.decode(inlinePhoto, Base64.DEFAULT);
                    return MainActivity.decodeBitmapBytes(bytes, targetSize);
                }
            } catch (Exception ignored) {
                return null;
            }
            return null;
        }
        String key = person.id + ":" + (mediaId.isEmpty()
            ? "inline-" + inlinePhoto.hashCode()
            : mediaId);
        Bitmap cached = photoCache.get(key);
        if (cached != null) return cached;
        Bitmap bitmap = null;
        int comma = inlinePhoto.indexOf(',');
        if (!mediaId.isEmpty() && mediaStore != null) {
            schedulePhotoDecode(key, mediaId);
            return null;
        } else if (inlinePhoto.toLowerCase(Locale.ROOT).startsWith("data:image") && comma > 0) {
            try {
                byte[] bytes = Base64.decode(inlinePhoto.substring(comma + 1), Base64.DEFAULT);
                bitmap = MainActivity.decodeBitmapBytes(bytes, 512);
            } catch (Exception ignored) {
                bitmap = null;
            }
        } else if (inlinePhoto.length() > 128 && !inlinePhoto.contains("://")) {
            try {
                byte[] bytes = Base64.decode(inlinePhoto, Base64.DEFAULT);
                bitmap = MainActivity.decodeBitmapBytes(bytes, 512);
            } catch (Exception ignored) {
                bitmap = null;
            }
        }
        if (bitmap != null) photoCache.put(key, bitmap);
        return bitmap;
    }

    private void schedulePhotoDecode(String key, String mediaId) {
        if (failedPhotoLoads.contains(key) || !photoLoads.add(key)) return;
        TreeMediaStore selectedStore = mediaStore;
        photoDecoder.execute(() -> {
            Bitmap decoded = selectedStore == null ? null : selectedStore.decodeBitmap(mediaId, 192);
            mainHandler.post(() -> {
                photoLoads.remove(key);
                if (decoded != null) {
                    failedPhotoLoads.remove(key);
                    photoCache.put(key, decoded);
                } else {
                    failedPhotoLoads.add(key);
                }
                invalidate();
            });
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        photoDecoder.shutdown();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    private void drawPhotoCenterCrop(Canvas canvas, Bitmap photo, RectF target) {
        int side = Math.min(photo.getWidth(), photo.getHeight());
        int left = Math.max(0, (photo.getWidth() - side) / 2);
        int top = Math.max(0, (photo.getHeight() - side) / 2);
        tmpSrc.set(left, top, left + side, top + side);
        canvas.drawBitmap(photo, tmpSrc, target, null);
    }

    private String displayDate(String day, String month, String year) {
        if (year == null || year.isEmpty()) return "";
        if (day != null && !day.isEmpty() && month != null && !month.isEmpty()) return day + "." + month + "." + year;
        if (month != null && !month.isEmpty()) return month + "." + year;
        return year;
    }

    private void drawSingleLine(Canvas canvas, String text, float x, float baseline, float maxWidth) {
        String value = text == null ? "" : text;
        canvas.drawText(value, x, baseline, textPaint);
    }

    private void drawNameLines(Canvas canvas, String text, float x, float baseline, float maxWidth, float textScale) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return;
        String[] lines = nameLines(value, maxWidth);
        float lineHeight = Math.max(13f * textScale, textPaint.getTextSize() * 1.14f);
        for (int i = 0; i < lines.length; i++) {
            drawSingleLine(
                canvas,
                ellipsizeName(lines[i], maxWidth),
                x,
                baseline + i * lineHeight,
                maxWidth);
        }
    }

    private String ellipsize(String value, float maxWidth) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || maxWidth <= 0f || textPaint.measureText(text) <= maxWidth) return text;
        String suffix = "…";
        float suffixWidth = textPaint.measureText(suffix);
        if (suffixWidth >= maxWidth) return suffix;
        int low = 0;
        int high = text.length();
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (textPaint.measureText(text, 0, middle) + suffixWidth <= maxWidth) low = middle;
            else high = middle - 1;
        }
        while (low > 0 && Character.isHighSurrogate(text.charAt(low - 1))) low--;
        return text.substring(0, low).trim() + suffix;
    }

    private String ellipsizeName(String value, float maxWidth) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return text;
        float stableScale = Math.max(0.001f, scale);
        int widthKey = Math.round(maxWidth / stableScale);
        String cacheKey = (compactCards ? "c" : "n") + '\u0000' + text + '\u0000' + widthKey;
        String cached = nameEllipsisCache.get(cacheKey);
        if (cached != null) return cached;
        float originalTextSize = textPaint.getTextSize();
        textPaint.setTextSize(compactCards ? 15.5f : 18.5f);
        String result = ellipsize(text, maxWidth / stableScale);
        textPaint.setTextSize(originalTextSize);
        nameEllipsisCache.put(cacheKey, result);
        return result;
    }

    private void drawFittedSingleLine(Canvas canvas, String text, float x, float baseline, float maxWidth) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return;
        float original = textPaint.getTextSize();
        float measured = Math.max(1f, textPaint.measureText(value));
        if (measured > maxWidth && maxWidth > 0f) {
            textPaint.setTextSize(Math.max(1f, original * maxWidth / measured));
        }
        drawSingleLine(canvas, value, x, baseline, maxWidth);
        textPaint.setTextSize(original);
    }

    private float cardTextScale() {
        return scale;
    }

    private String[] nameLines(String value, float maxWidth) {
        int widthKey = Math.round(maxWidth / Math.max(0.001f, scale));
        String cacheKey = (compactCards ? "c" : "n") + '\u0000' + value + '\u0000' + widthKey;
        String[] cached = nameLinesCache.get(cacheKey);
        if (cached != null) return cached;
        float originalTextSize = textPaint.getTextSize();
        textPaint.setTextSize(compactCards ? 15.5f : 18.5f);
        float stableMaxWidth = maxWidth / Math.max(0.001f, scale);
        List<String> lines = new ArrayList<>();
        if (!compactCards) {
            lines.addAll(nameParts(value));
        } else {
            String[] words = value.trim().split("\\s+");
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                String candidate = current.length() == 0 ? word : current + " " + word;
                if (current.length() == 0 || textPaint.measureText(candidate) <= stableMaxWidth || lines.size() >= 2) {
                    if (current.length() > 0) current.append(' ');
                    current.append(word);
                } else {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                }
            }
            if (current.length() > 0) lines.add(current.toString());
        }
        if (lines.isEmpty()) lines.add(value);
        textPaint.setTextSize(originalTextSize);
        int limit = compactCards ? 3 : 4;
        String[] result = lines.subList(0, Math.min(limit, lines.size())).toArray(new String[0]);
        nameLinesCache.put(cacheKey, result);
        return result;
    }

    private List<String> nameParts(String value) {
        List<String> parts = new ArrayList<>();
        String[] words = value.trim().split("\\s+");
        StringBuilder current = new StringBuilder();
        int parenthesesDepth = 0;
        for (String word : words) {
            boolean startsParentheses = word.indexOf('(') >= 0;
            if (current.length() == 0) {
                current.append(word);
            } else if (parenthesesDepth > 0 || startsParentheses) {
                current.append(' ').append(word);
            } else {
                parts.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
            for (int index = 0; index < word.length(); index++) {
                char character = word.charAt(index);
                if (character == '(') parenthesesDepth++;
                else if (character == ')' && parenthesesDepth > 0) parenthesesDepth--;
            }
        }
        if (current.length() > 0) parts.add(current.toString());
        return parts;
    }

    private String joinWords(String[] words, int from, int to) {
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(words[i]);
        }
        return builder.toString();
    }

    private String initials(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) return "?";
        String[] parts = value.split("\\s+");
        String first = parts[0].isEmpty() ? "" : parts[0].substring(0, 1);
        String second = parts.length > 1 && !parts[1].isEmpty() ? parts[1].substring(0, 1) : "";
        return (first + second).toUpperCase(Locale.ROOT);
    }

    private Path linkPath(Relation relation, Person from, Person to) {
        float cardW = cardWidthWorld();
        float cardH = cardHeightWorld();
        String type = relation.type;
        int signature = 17;
        signature = 31 * signature + Float.floatToIntBits(from.x);
        signature = 31 * signature + Float.floatToIntBits(from.y);
        signature = 31 * signature + Float.floatToIntBits(to.x);
        signature = 31 * signature + Float.floatToIntBits(to.y);
        signature = 31 * signature + Float.floatToIntBits(cardW);
        signature = 31 * signature + Float.floatToIntBits(cardH);
        signature = 31 * signature + stringHash(type);
        signature = 31 * signature + stringHash(parentLineMode);
        LinkGeometry geometry = linkGeometryCache.get(relation.id);
        if (geometry == null || geometry.signature != signature) {
            Path worldPath = new Path();
            float ax = from.x + cardW / 2f;
            float ay = from.y + cardH / 2f;
            float bx = to.x + cardW / 2f;
            float by = to.y + cardH / 2f;
            if ("parent".equals(type)) {
                float startY = from.y + cardH;
                float endY = to.y;
            float midY = startY + (endY - startY) / 2f;
                worldPath.moveTo(ax, startY);
                if ("orthogonal".equals(parentLineMode)) {
                    worldPath.lineTo(ax, midY);
                    worldPath.lineTo(bx, midY);
                    worldPath.lineTo(bx, endY);
                } else {
                    worldPath.cubicTo(ax, midY, bx, midY, bx, endY);
                }
            } else {
                float startX = ax < bx ? from.x + cardW : from.x;
                float endX = ax < bx ? to.x : to.x + cardW;
                float midX = startX + (endX - startX) / 2f;
                worldPath.moveTo(startX, ay);
                worldPath.cubicTo(midX, ay, midX, by, endX, by);
            }
            geometry = new LinkGeometry(signature, worldPath);
            linkGeometryCache.put(relation.id, geometry);
            if (linkGeometryCache.size() > Math.max(128, state.links.size() * 2)) {
                linkGeometryCache.clear();
                linkGeometryCache.put(relation.id, geometry);
            }
        }
        worldToScreenValues[0] = scale;
        worldToScreenValues[1] = 0f;
        worldToScreenValues[2] = offsetX;
        worldToScreenValues[3] = 0f;
        worldToScreenValues[4] = scale;
        worldToScreenValues[5] = offsetY;
        worldToScreenValues[6] = 0f;
        worldToScreenValues[7] = 0f;
        worldToScreenValues[8] = 1f;
        worldToScreen.setValues(worldToScreenValues);
        linkPathScratch.reset();
        geometry.worldPath.transform(worldToScreen, linkPathScratch);
        return linkPathScratch;
    }

    private DashPathEffect siblingDashEffect() {
        if (siblingDash == null || Math.abs(siblingDashScale - scale) > 0.015f) {
            siblingDashScale = scale;
            siblingDash = new DashPathEffect(
                new float[] {Math.max(1f, 16f * scale), Math.max(1f, 10f * scale)},
                0f);
        }
        return siblingDash;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (scaleDetector.isInProgress()) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (listener != null) listener.onCanvasTouched();
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                gestureStartOffsetX = offsetX;
                gestureStartOffsetY = offsetY;
                moved = false;
                dragStartPositions.clear();
                dragPersonIds.clear();
                if (!guideMode.isEmpty() && !editLocked) {
                    applyGuideTouch(event.getX(), event.getY());
                    return true;
                }
                if (!selectionMode.isEmpty() && !editLocked) {
                    startSelection(event.getX(), event.getY());
                    return true;
                }
                Person hit = hitPerson(event.getX(), event.getY());
                if (hit != null && state != null && hitMenuButton(hit, event.getX(), event.getY())) {
                    if (!selectedIds.isEmpty() && !selectedIds.contains(hit.id)) {
                        selectedIds.clear();
                        notifySelectionChanged();
                    }
                    state.selectedId = hit.id;
                    if (listener != null) listener.onPersonMenu(hit, event.getRawX(), event.getRawY());
                    invalidate();
                    return true;
                }
                dragPersonId = hit == null ? "" : hit.id;
                draggingCanvas = hit == null;
                if (hit != null && state != null) {
                    if (!selectedIds.isEmpty() && !selectedIds.contains(hit.id)) {
                        selectedIds.clear();
                        notifySelectionChanged();
                    }
                    state.selectedId = hit.id;
                    startCardDrag(hit);
                    if (listener != null) listener.onPersonSelected(hit);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (selecting) {
                    updateSelection(event.getX(), event.getY());
                    return true;
                }
                if (!moved) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    moved = dx * dx + dy * dy > touchSlop * touchSlop;
                }
                if (draggingCanvas) {
                    offsetX = gestureStartOffsetX + event.getX() - downX;
                    offsetY = gestureStartOffsetY + event.getY() - downY;
                } else if (state != null && !dragPersonId.isEmpty() && !editLocked) {
                    Person person = state.people.get(dragPersonId);
                    if (person != null && !person.pinned) {
                        if (moved) {
                            moveDraggedCards(
                                (event.getX() - downX) / scale,
                                (event.getY() - downY) / scale);
                        }
                    }
                }
                lastX = event.getX();
                lastY = event.getY();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (selecting) {
                    finishSelection();
                    return true;
                }
                if (!moved && draggingCanvas) {
                    Relation link = hitLink(event.getX(), event.getY());
                    if (link != null) {
                        selectedLinkId = link.id;
                        if (listener != null) listener.onLinkSelected(link);
                        invalidate();
                    }
                }
                finishCardDrag(true);
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (selecting) {
                    selecting = false;
                    selectionPoints.clear();
                    selectionRect.setEmpty();
                }
                finishCardDrag(false);
                return true;
            default:
                return true;
        }
    }

    private void startSelection(float screenX, float screenY) {
        selecting = true;
        selectionPoints.clear();
        float x = wx(screenX);
        float y = wy(screenY);
        selectionRect.set(x, y, x, y);
        selectionPoints.add(new PointF(x, y));
        invalidate();
    }

    private void updateSelection(float screenX, float screenY) {
        float x = wx(screenX);
        float y = wy(screenY);
        PointF start = selectionPoints.isEmpty() ? new PointF(x, y) : selectionPoints.get(0);
        if ("lasso".equals(selectionMode)) {
            selectionRect.left = Math.min(selectionRect.left, x);
            selectionRect.top = Math.min(selectionRect.top, y);
            selectionRect.right = Math.max(selectionRect.right, x);
            selectionRect.bottom = Math.max(selectionRect.bottom, y);
            selectionPoints.add(new PointF(x, y));
        } else {
            selectionRect.set(Math.min(start.x, x), Math.min(start.y, y), Math.max(start.x, x), Math.max(start.y, y));
        }
        invalidate();
    }

    private void finishSelection() {
        selecting = false;
        if (state != null) {
            Set<String> hits = new HashSet<>();
            for (Person person : state.people.values()) {
                if (!matches(person)) continue;
                RectF card = new RectF(person.x, person.y, person.x + cardWidthWorld(), person.y + cardHeightWorld());
                if ("lasso".equals(selectionMode)) {
                    if (selectionPoints.size() > 2 && lassoHitsCard(card)) hits.add(person.id);
                } else {
                    if (RectF.intersects(selectionRect, card)) hits.add(person.id);
                }
            }
            if (!appendSelection) selectedIds.clear();
            selectedIds.addAll(hits);
            if (!selectedIds.isEmpty()) state.selectedId = selectedIds.iterator().next();
        }
        notifySelectionChanged();
        selectionPoints.clear();
        selectionRect.setEmpty();
        invalidate();
    }

    private boolean lassoHitsCard(RectF card) {
        if (!RectF.intersects(selectionRect, card)) return false;
        float cx = card.centerX();
        float cy = card.centerY();
        if (pointInPolygon(cx, cy, selectionPoints)) return true;
        if (pointInPolygon(card.left, card.top, selectionPoints)) return true;
        if (pointInPolygon(card.right, card.top, selectionPoints)) return true;
        if (pointInPolygon(card.right, card.bottom, selectionPoints)) return true;
        if (pointInPolygon(card.left, card.bottom, selectionPoints)) return true;
        for (PointF point : selectionPoints) {
            if (card.contains(point.x, point.y)) return true;
        }
        for (int i = 0; i < selectionPoints.size(); i++) {
            PointF from = selectionPoints.get(i);
            PointF to = selectionPoints.get((i + 1) % selectionPoints.size());
            if (segmentIntersectsRect(from, to, card)) return true;
        }
        return false;
    }

    private boolean segmentIntersectsRect(PointF from, PointF to, RectF rect) {
        if (rect.contains(from.x, from.y) || rect.contains(to.x, to.y)) return true;
        return segmentsIntersect(from.x, from.y, to.x, to.y, rect.left, rect.top, rect.right, rect.top)
            || segmentsIntersect(from.x, from.y, to.x, to.y, rect.right, rect.top, rect.right, rect.bottom)
            || segmentsIntersect(from.x, from.y, to.x, to.y, rect.right, rect.bottom, rect.left, rect.bottom)
            || segmentsIntersect(from.x, from.y, to.x, to.y, rect.left, rect.bottom, rect.left, rect.top);
    }

    private boolean segmentsIntersect(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy) {
        float d1 = direction(ax, ay, bx, by, cx, cy);
        float d2 = direction(ax, ay, bx, by, dx, dy);
        float d3 = direction(cx, cy, dx, dy, ax, ay);
        float d4 = direction(cx, cy, dx, dy, bx, by);
        if (((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) && ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f))) return true;
        return nearlyZero(d1) && onSegment(ax, ay, bx, by, cx, cy)
            || nearlyZero(d2) && onSegment(ax, ay, bx, by, dx, dy)
            || nearlyZero(d3) && onSegment(cx, cy, dx, dy, ax, ay)
            || nearlyZero(d4) && onSegment(cx, cy, dx, dy, bx, by);
    }

    private float direction(float ax, float ay, float bx, float by, float px, float py) {
        return (px - ax) * (by - ay) - (py - ay) * (bx - ax);
    }

    private boolean onSegment(float ax, float ay, float bx, float by, float px, float py) {
        return px >= Math.min(ax, bx) - 0.001f && px <= Math.max(ax, bx) + 0.001f
            && py >= Math.min(ay, by) - 0.001f && py <= Math.max(ay, by) + 0.001f;
    }

    private boolean nearlyZero(float value) {
        return Math.abs(value) <= 0.001f;
    }

    private void notifySelectionChanged() {
        if (listener != null) listener.onSelectionChanged(selectedIds.size());
    }

    private void applyGuideTouch(float screenX, float screenY) {
        if (state == null) return;
        if ("erase".equals(guideMode)) {
            if (!hasGuideNear(screenX, screenY)) {
                if (listener != null) listener.onGuideActionMiss();
                return;
            }
            if (listener != null) listener.onTreeEditStart("Удалена направляющая", "");
            eraseGuideNear(screenX, screenY);
        } else {
            if (listener != null) listener.onTreeEditStart("Добавлена направляющая", guideLabel);
            Guide guide = new Guide();
            guide.id = "g_" + java.util.UUID.randomUUID().toString().replace("-", "");
            guide.axis = guideMode;
            guide.position = TreeLayoutEngine.snap("v".equals(guideMode) ? wx(screenX) : wy(screenY));
            guide.color = guideColor;
            guide.label = guideLabel;
            state.guides.add(guide);
            state.guidesVisible = true;
        }
        if (listener != null) listener.onTreeChanged();
        invalidate();
    }

    private boolean hasGuideNear(float screenX, float screenY) {
        return guideIndexNear(screenX, screenY) >= 0;
    }

    private void eraseGuideNear(float screenX, float screenY) {
        int bestIndex = guideIndexNear(screenX, screenY);
        if (bestIndex >= 0) state.guides.remove(bestIndex);
    }

    private int guideIndexNear(float screenX, float screenY) {
        if (state == null || state.guides.isEmpty()) return -1;
        float x = wx(screenX);
        float y = wy(screenY);
        float threshold = 28f / Math.max(0.1f, scale);
        int bestIndex = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < state.guides.size(); i++) {
            Guide guide = state.guides.get(i);
            float distance = "v".equals(guide.axis) ? Math.abs(guide.position - x) : Math.abs(guide.position - y);
            if (distance < bestDistance && distance <= threshold) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private void drawSelectionOverlay(Canvas canvas) {
        if (!selecting) return;
        if ("lasso".equals(selectionMode) && selectionPoints.size() > 1) {
            Path path = new Path();
            PointF first = selectionPoints.get(0);
            path.moveTo(sx(first.x), sy(first.y));
            for (int i = 1; i < selectionPoints.size(); i++) {
                PointF point = selectionPoints.get(i);
                path.lineTo(sx(point.x), sy(point.y));
            }
            path.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(34, 8, 122, 115));
            paint.setPathEffect(null);
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.6f);
            paint.setColor(Color.rgb(8, 122, 115));
            paint.setPathEffect(new DashPathEffect(new float[] {12f, 8f}, 0f));
            canvas.drawPath(path, paint);
        } else if (!selectionRect.isEmpty()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(28, 8, 122, 115));
            paint.setPathEffect(null);
            canvas.drawRect(sx(selectionRect.left), sy(selectionRect.top), sx(selectionRect.right), sy(selectionRect.bottom), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.6f);
            paint.setColor(Color.rgb(8, 122, 115));
            paint.setPathEffect(new DashPathEffect(new float[] {12f, 8f}, 0f));
            canvas.drawRect(sx(selectionRect.left), sy(selectionRect.top), sx(selectionRect.right), sy(selectionRect.bottom), paint);
        }
        paint.setPathEffect(null);
    }

    private boolean pointInPolygon(float x, float y, java.util.List<PointF> polygon) {
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            PointF pi = polygon.get(i);
            PointF pj = polygon.get(j);
            // The crossing condition guarantees a non-zero denominator. Keeping its
            // sign is essential: replacing a negative delta with a positive epsilon
            // makes descending lasso edges classify most enclosed cards as outside.
            boolean intersects = ((pi.y > y) != (pj.y > y))
                && (x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y) + pi.x);
            if (intersects) inside = !inside;
        }
        return inside;
    }

    private Person hitPerson(float screenX, float screenY) {
        if (state == null) return null;
        float x = wx(screenX);
        float y = wy(screenY);
        rebuildSpatialIndex();
        int minCellX = cell(x - cardWidthWorld());
        int maxCellX = cell(x);
        int minCellY = cell(y - cardHeightWorld());
        int maxCellY = cell(y);
        Person hit = null;
        for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                List<Person> candidates = spatialGrid.get(cellKey(cellX, cellY));
                if (candidates == null) continue;
                for (Person person : candidates) {
                    if (!matches(person)) continue;
                    if (x >= person.x
                        && x <= person.x + cardWidthWorld()
                        && y >= person.y
                        && y <= person.y + cardHeightWorld()) hit = person;
                }
            }
        }
        return hit;
    }

    private boolean hitMenuButton(Person person, float screenX, float screenY) {
        if (person == null || scale <= 0.2f) return false;
        float left = sx(person.x);
        float top = sy(person.y);
        float width = cardWidthWorld() * scale;
        float menu = 28f * scale;
        float menuLeft = left + width - 7f * scale - menu;
        float menuTop = top + 7f * scale;
        float hitPad = Math.max(8f, 10f * scale);
        return screenX >= menuLeft - hitPad && screenX <= menuLeft + menu + hitPad && screenY >= menuTop - hitPad && screenY <= menuTop + menu + hitPad;
    }

    private Relation hitLink(float screenX, float screenY) {
        if (state == null || state.links.isEmpty()) return null;
        float threshold = Math.max(16f, 24f * scale);
        Relation best = null;
        float bestDistance = Float.MAX_VALUE;
        for (Relation link : state.links) {
            Person from = state.people.get(link.from);
            Person to = state.people.get(link.to);
            if (from == null || to == null || !matches(from) || !matches(to)) continue;
            Path path = linkPath(link, from, to);
            float distance = distanceToPath(path, screenX, screenY);
            if (distance < bestDistance && distance <= threshold) {
                bestDistance = distance;
                best = link;
            }
        }
        return best;
    }

    private static float distanceToPath(Path path, float x, float y) {
        PathMeasure measure = new PathMeasure(path, false);
        float length = measure.getLength();
        if (length <= 0f) return Float.MAX_VALUE;
        float step = Math.max(6f, Math.min(28f, length / 40f));
        float[] point = new float[2];
        float best = Float.MAX_VALUE;
        for (float distance = 0f; distance <= length; distance += step) {
            measure.getPosTan(distance, point, null);
            float dx = point[0] - x;
            float dy = point[1] - y;
            best = Math.min(best, dx * dx + dy * dy);
        }
        measure.getPosTan(length, point, null);
        float dx = point[0] - x;
        float dy = point[1] - y;
        best = Math.min(best, dx * dx + dy * dy);
        return (float) Math.sqrt(best);
    }

    private void startCardDrag(Person hit) {
        if (state == null || hit == null || editLocked) return;
        spatialDirty = true;
        dragPersonIds.clear();
        dragStartPositions.clear();
        if (selectedIds.contains(hit.id)) {
            for (String id : selectedIds) {
                Person selected = state.people.get(id);
                if (selected != null && !selected.pinned) dragPersonIds.add(id);
            }
        }
        if (dragPersonIds.isEmpty() && !hit.pinned) dragPersonIds.add(hit.id);
        for (String id : dragPersonIds) {
            Person person = state.people.get(id);
            if (person != null) dragStartPositions.put(id, new PointF(person.x, person.y));
        }
    }

    private void moveDraggedCards(float dx, float dy) {
        if (state == null || dragPersonIds.isEmpty()) return;
        for (String id : dragPersonIds) {
            PointF start = dragStartPositions.get(id);
            Person person = state.people.get(id);
            if (start == null || person == null || person.pinned) continue;
            person.x = clamp(start.x + dx, 0f, workspaceWidth - cardWidthWorld());
            person.y = clamp(start.y + dy, 0f, workspaceHeight - cardHeightWorld());
        }
    }

    private void finishActiveCardDrag() {
        finishCardDrag(true);
    }

    private void finishCardDrag(boolean commit) {
        if (state != null && !dragPersonIds.isEmpty()) {
            Map<String, PointF> after = new HashMap<>();
            for (String id : dragPersonIds) {
                Person person = state.people.get(id);
                PointF start = dragStartPositions.get(id);
                if (person == null || start == null) continue;
                if (commit && moved) {
                    person.x = snapPersonX(person.x);
                    person.y = snapPersonY(person.y);
                    after.put(id, new PointF(person.x, person.y));
                } else {
                    person.x = start.x;
                    person.y = start.y;
                }
            }
            if (commit && moved && listener != null && !after.isEmpty()) {
                Person primary = state.people.get(dragPersonId);
                String detail = primary == null || primary.name == null || primary.name.isEmpty()
                    ? (after.size() == 1
                        ? AppLanguage.text(getContext(), "Без имени")
                        : after.size() + " " + AppLanguage.text(getContext(), "карточек"))
                    : primary.name;
                listener.onPeopleMoved(new HashMap<>(dragStartPositions), after, detail);
                listener.onTreeChanged();
            }
        }
        dragPersonId = "";
        dragPersonIds.clear();
        dragStartPositions.clear();
        draggingCanvas = false;
        moved = false;
        spatialDirty = true;
        invalidate();
    }

    private boolean matches(Person person) {
        if (!branchAllows(person.id)) return false;
        if (filter.isEmpty()) return true;
        return cardMeta(person).search.contains(filter);
    }

    private boolean branchAllows(String id) {
        if (state == null || "all".equals(branchMode)) return true;
        String anchor = branchAnchorId;
        if (anchor == null || anchor.isEmpty()) return true;
        prepareBranchCache();
        return visibleBranchIds == null || visibleBranchIds.contains(id);
    }

    private void prepareBranchCache() {
        if (visibleBranchIds != null || state == null || "all".equals(branchMode)) return;
        String anchor = branchAnchorId;
        if (anchor == null || anchor.isEmpty()) return;
        if ("ancestors".equals(branchMode)) visibleBranchIds = state.ancestorsOf(anchor);
        else if ("descendants".equals(branchMode)) visibleBranchIds = state.descendantsOf(anchor);
        else if ("near".equals(branchMode)) visibleBranchIds = state.nearOf(anchor);
    }

    private List<Person> visiblePeople(RectF visible) {
        rebuildSpatialIndex();
        visiblePeopleScratch.clear();
        int minCellX = cell(visible.left - cardWidthWorld());
        int maxCellX = cell(visible.right);
        int minCellY = cell(visible.top - cardHeightWorld());
        int maxCellY = cell(visible.bottom);
        long cellCount = (long) (maxCellX - minCellX + 1)
            * (long) (maxCellY - minCellY + 1);
        if (cellCount <= 0L || cellCount > 2048L) {
            visiblePeopleScratch.addAll(state.people.values());
            return visiblePeopleScratch;
        }
        for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                List<Person> people = spatialGrid.get(cellKey(cellX, cellY));
                if (people != null) visiblePeopleScratch.addAll(people);
            }
        }
        return visiblePeopleScratch;
    }

    private void rebuildSpatialIndex() {
        if (!spatialDirty || state == null) return;
        spatialGrid.clear();
        for (Person person : state.people.values()) {
            long key = cellKey(cell(person.x), cell(person.y));
            List<Person> bucket = spatialGrid.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                spatialGrid.put(key, bucket);
            }
            bucket.add(person);
        }
        spatialDirty = false;
    }

    private static int cell(float coordinate) {
        return (int) Math.floor(coordinate / SPATIAL_CELL);
    }

    private static long cellKey(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    private RectF visibleWorld(float extra) {
        return visibleWorld(getWidth(), getHeight(), extra);
    }

    private RectF visibleWorld(int width, int height, float extra) {
        return new RectF(wx(0) - extra, wy(0) - extra, wx(width) + extra, wy(height) + extra);
    }

    private RectF treeBounds() {
        RectF bounds = new RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        for (Person person : state.people.values()) {
            if (!matches(person)) continue;
            bounds.left = Math.min(bounds.left, person.x);
            bounds.top = Math.min(bounds.top, person.y);
            bounds.right = Math.max(bounds.right, person.x + cardWidthWorld());
            bounds.bottom = Math.max(bounds.bottom, person.y + cardHeightWorld());
        }
        if (bounds.left == Float.MAX_VALUE) bounds.set(0, 0, cardWidthWorld(), cardHeightWorld());
        return bounds;
    }

    private RectF exportBounds() {
        RectF bounds = treeBounds();
        if (state == null || !state.guidesVisible) return bounds;
        for (Guide guide : state.guides) {
            if (guide == null || !Float.isFinite(guide.position)) continue;
            if ("v".equals(guide.axis)) {
                bounds.left = Math.min(bounds.left, guide.position);
                bounds.right = Math.max(bounds.right, guide.position);
            } else {
                bounds.top = Math.min(bounds.top, guide.position);
                bounds.bottom = Math.max(bounds.bottom, guide.position);
            }
        }
        return bounds;
    }

    private void zoom(float factor, float focusX, float focusY) {
        float beforeX = wx(focusX);
        float beforeY = wy(focusY);
        scale = clamp(scale * factor, 0.05f, 3.0f);
        offsetX = focusX - beforeX * scale;
        offsetY = focusY - beforeY * scale;
        invalidate();
    }

    private float sx(float worldX) {
        return worldX * scale + offsetX;
    }

    private float sy(float worldY) {
        return worldY * scale + offsetY;
    }

    private float wx(float screenX) {
        return (screenX - offsetX) / scale;
    }

    private float wy(float screenY) {
        return (screenY - offsetY) / scale;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float positiveModulo(float value, float divisor) {
        float result = value % divisor;
        return result < 0f ? result + divisor : result;
    }

    private float cardWidthWorld() {
        return compactCards ? TreeLayoutEngine.GRID * 5f : TreeLayoutEngine.CARD_W;
    }

    private float cardHeightWorld() {
        return compactCards ? TreeLayoutEngine.GRID * 3f : TreeLayoutEngine.CARD_H;
    }

    private float snapPersonX(float value) {
        return clamp(
            TreeLayoutEngine.snap(value),
            0f,
            workspaceWidth - cardWidthWorld());
    }

    private float snapPersonY(float value) {
        return clamp(
            TreeLayoutEngine.snap(value),
            0f,
            workspaceHeight - cardHeightWorld());
    }

    private static int blend(int a, int b, float amount) {
        int ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
        int br = Color.red(b), bg = Color.green(b), bb = Color.blue(b);
        return Color.rgb((int) (ar + (br - ar) * amount), (int) (ag + (bg - ag) * amount), (int) (ab + (bb - ab) * amount));
    }

    private static Typeface loadTypeface(Context context, String assetPath, int fallbackStyle) {
        try {
            return Typeface.createFromAsset(context.getAssets(), assetPath);
        } catch (RuntimeException ignored) {
            return Typeface.create("sans-serif", fallbackStyle);
        }
    }

    private static final class CardMeta {
        final int signature;
        final String initials;
        final String date;
        final String place;
        final String search;

        CardMeta(int signature, String initials, String date, String place, String search) {
            this.signature = signature;
            this.initials = initials;
            this.date = date;
            this.place = place;
            this.search = search;
        }
    }

    private static final class LinkGeometry {
        final int signature;
        final Path worldPath;

        LinkGeometry(int signature, Path worldPath) {
            this.signature = signature;
            this.worldPath = worldPath;
        }
    }
}
