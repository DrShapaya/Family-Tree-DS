package ru.drshapaya.androidft2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
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
        void onEditBlocked();
        void onZoomChanged(float scale);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics fontMetricsScratch = new Paint.FontMetrics();
    private final Map<String,Float> metaTextWidths=new HashMap<>();
    private float measuredMetaSize=-1f;
    private final RectF tmp = new RectF();
    private final RectF visibleScratch = new RectF();
    private final RectF linkBoundsScratch = new RectF();
    private final Rect tmpSrc = new Rect();
    private final Path clipPath = new Path();
    private final LifeStatusBadge lifeStatusBadge = new LifeStatusBadge();
    private final Path linkPathScratch = new Path();
    private final PaperTexture paperTexture = new PaperTexture();
    private final BotanicalCanvasBackground botanicalCanvasBackground;
    private final FireCardEffect fireCardEffect = new FireCardEffect();
    private final GpuFireCardEffect gpuFireCardEffect;
    private boolean gpuFireForFrame;
    private long gpuEffectFrames;
    private final AnimatedCardAtlas animatedCardAtlas = new AnimatedCardAtlas(this::postInvalidateOnAnimation);
    private final List<Person> effectPeopleScratch = new ArrayList<>();
    private final CardRasterCache cardRasterCache=new CardRasterCache();
    private final CardRasterCache.Renderer cardRasterRenderer=this::renderCardRaster;
    private long cardRasterStyleSignature;
    private boolean rasterCardsEnabled;
    private boolean rasterizingStaticCard;
    private boolean recordingCardArtwork;
    private float cardViewportScale;
    private final Paint photoPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final SmokeCardEffect smokeCardEffect = new SmokeCardEffect();
    private final BotanicalEffect botanicalEffect = new BotanicalEffect();
    private float fireFrameSeconds;
    private final long effectClockOrigin=SystemClock.uptimeMillis();
    private volatile long[] debugFrameStages;
    private boolean debugDisableCardRaster;
    private final Path ribbonPathA = new Path();
    private final Path ribbonPathB = new Path();
    private final Path fireFlowPath = new Path();
    private final Matrix worldToScreen = new Matrix();
    private final Matrix gradientMatrix = new Matrix();
    private final android.util.SparseArray<Shader> fillGradients = new android.util.SparseArray<>();
    private final android.util.SparseArray<Shader> overlayGradients = new android.util.SparseArray<>();
    private Shader canvasFillGradient;
    private String gradientCanvasStyle,gradientCanvasTheme;
    private int gradientCanvasWidth,gradientCanvasHeight;
    private final Rect gridClip = new Rect();
    private Path lastLinkWorldPath;
    private final float[] worldToScreenValues = new float[9];
    private final Map<String, LinkGeometry> linkGeometryCache = new HashMap<>();
    private FamilyLineRouter.Plan familyLinePlan = FamilyLineRouter.Plan.empty();
    private final Map<FamilyLineRouter.FamilyRoute, Path> familyRoutePaths = new java.util.IdentityHashMap<>();
    private final Map<FamilyLineRouter.FamilyRoute, List<ColoredFamilyPath>> coloredFamilyPaths = new java.util.IdentityHashMap<>();
    private final Map<Path, List<Path[]>> ribbonGeometry = new java.util.WeakHashMap<>();
    private final Map<Relation, LinkGradient> linkGradients = new java.util.WeakHashMap<>();
    private final float[] ribbonPoint = new float[2], ribbonTangent = new float[2];
    private long familyLinePlanSignature = Long.MIN_VALUE;
    private boolean familyLinePlanDirty = true;
    private long familyObservedCoordinateRevision=Long.MIN_VALUE;
    private SceneBoundsIndex<FamilyLineRouter.FamilyRoute> routeBounds;
    private SceneBoundsIndex<Relation> relationBounds;
    private final List<FamilyLineRouter.FamilyRoute> routeCandidates=new ArrayList<>();
    private final List<Relation> relationCandidates=new ArrayList<>();
    private FamilyLineRouter.Plan indexedPlan;
    private Set<String> indexedMatches;
    private long coordinateRevision, indexedCoordinates = -1;
    private float indexedCardWidth, indexedCardHeight;
    private final Set<Integer> generationRows = new HashSet<>();
    private final DashPathEffect guideDash = new DashPathEffect(new float[] {10f, 8f}, 0f);
    private DashPathEffect siblingDash;
    private float siblingDashScale = -1f;
    private final LruCache<String, Bitmap> photoCache;
    private final Map<String, CachedName<String[]>> nameLinesCache = new HashMap<>();
    private final Map<String, CachedName<String>> nameEllipsisCache = new HashMap<>();
    private final Map<String, CardMeta> cardMetaCache = new HashMap<>();
    private final Map<Person,ThumbnailLoader.Request> thumbnailRequests=new java.util.WeakHashMap<>();
    private final Map<Long, List<Person>> spatialGrid = new HashMap<>();
    private final List<Person> visiblePeopleScratch = new ArrayList<>();
    private boolean visibleCellsCached;
    private int visibleMinX,visibleMaxX,visibleMinY,visibleMaxY;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable thumbnailReady = this::postInvalidateOnAnimation;
    private final Typeface uiRegular;
    private final Typeface uiBold;
    private final ScaleGestureDetector scaleDetector;
    private TreeState state;
    private TreeMediaStore mediaStore;
    private Listener listener;
    private String filter = "";
    private SmartPeopleSearch.Query smartFilter;
    private final ExecutorService filterWorker = Executors.newSingleThreadExecutor();
    private volatile long filterRevision;
    private boolean detached;
    private Set<String> filterMatchIds;
    private Calendar smartFilterNow = Calendar.getInstance();
    private Set<String> matchingPersonIds;
    private String branchMode = "all";
    private String branchAnchorId = "";
    private int nearGenerations = 1;
    private String selectionMode = "";
    private String guideMode = "";
    private String parentLineMode = "smart";
    private String familyLineMode = "centered";
    private String pendingLineFromId = "";
    private String selectedLinkId = "";
    private String guideColor = "#2f7d75";
    private String guideLabel = "Поколение";
    private String theme = "light";
    private String cardEdgeStyle = RewardCatalog.STANDARD;
    private String cardStyle = RewardCatalog.STANDARD;
    private int activeCardColor = Color.WHITE;
    private String canvasStyle = RewardCatalog.STANDARD;
    private String linkStyle = RewardCatalog.STANDARD;
    private boolean exportRendering;
    private boolean exportGrid;
    private float exportPhotoScale = 1f;
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
    private float fontScale = 1f;
    private float workspaceWidth = TreeLayoutEngine.SURFACE_W;
    private float workspaceHeight = TreeLayoutEngine.SURFACE_H;
    private boolean appendSelection = false;
    private float scale = 0.55f;
    private float zoomTargetScale = 0.55f;
    private float zoomTargetOffsetX = 80f;
    private float zoomTargetOffsetY = 160f;
    private boolean zoomAnimationRunning;
    private final Runnable zoomAnimation = this::advanceZoomAnimation;
    private long zoomFrameTime;
    private android.animation.ValueAnimator cameraAnimator;
    private int continuousZoomDirection;
    private long continuousZoomTime;
    private final Runnable continuousZoom = new Runnable() {
        @Override public void run() {
            if (continuousZoomDirection == 0 || !isShown()) { stopContinuousZoom(); return; }
            long now = SystemClock.uptimeMillis();
            float seconds = Math.min(.05f, (now - continuousZoomTime) / 1000f);
            continuousZoomTime = now;
            zoomBy((float) Math.exp(continuousZoomDirection * .85f * seconds));
            postOnAnimation(this);
        }
    };
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
    private boolean blockedEditNotified = false;
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
        botanicalCanvasBackground = new BotanicalCanvasBackground(context);
        gpuFireCardEffect = new GpuFireCardEffect(context);
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
            @Override public void onScaleEnd(ScaleGestureDetector detector) { postInvalidateOnAnimation(); }
        });
    }

    void setState(TreeState state) {
        // A state can be rebound after an in-place layout/edit.  Bump the
        // coordinate revision even when the object identity is unchanged so
        // retained routing does not need to rescan every person every frame.
        coordinateRevision++;
        if (this.state != state) {
            cardRasterCache.clear();
            thumbnailRequests.clear();
            photoCache.evictAll();
            ThumbnailLoader.SHARED.cancel(this);
            nameLinesCache.clear();
            nameEllipsisCache.clear();
            cardMetaCache.clear();
            linkGeometryCache.clear();
            familyLinePlan = FamilyLineRouter.Plan.empty();
            familyLinePlanSignature = Long.MIN_VALUE;
            ThumbnailLoader.SHARED.clearFailures();
        }
        this.state = state;
        setFilter(this.filter);
        smartFilterNow = Calendar.getInstance();
        matchingPersonIds = null;
        familyLinePlanDirty = true;
        visibleBranchIds = null;
        spatialDirty = true;
        familyLinePlanDirty = true;
        invalidate();
    }

    void setMediaStore(TreeMediaStore mediaStore) {
        if (this.mediaStore != mediaStore) {
            photoCache.evictAll();
            ThumbnailLoader.SHARED.clearFailures();
            matchingPersonIds = null;
        familyLinePlanDirty = true;
        }
        this.mediaStore = mediaStore;
    }

    void trimBitmapCache(int level) {
        if(level>=20) { animatedCardAtlas.release();botanicalEffect.trim();smokeCardEffect.trim();cardRasterCache.clear();gpuFireCardEffect.trim(); }
        if (level >= 80) {
            animatedCardAtlas.release();
            ThumbnailLoader.SHARED.trim(true);
            photoCache.evictAll();
            nameLinesCache.clear();
            nameEllipsisCache.clear();
            cardMetaCache.clear();
        } else if (level >= 10 && level < 20) {
            ThumbnailLoader.SHARED.trim(false);
            photoCache.trimToSize(Math.max(1, photoCache.maxSize() / 2));
        }
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setFilter(String filter) {
        this.filter = filter == null ? "" : filter.trim();
        long revision = ++filterRevision;
        filterMatchIds = null; matchingPersonIds = null; familyLinePlanDirty = true;
        if (this.filter.isEmpty() || state == null || detached) { smartFilter = null; invalidate(); return; }
        TreeState source = state;
        TreeState snapshot = TreeStateCopier.copy(source);
        String query = this.filter;
        filterWorker.execute(() -> {
            if (revision != filterRevision) return;
            SmartPeopleSearch.Query parsed = SmartPeopleSearch.parse(snapshot, query, snapshot.selectedId);
            Set<String> ids = new HashSet<>(); Calendar now = Calendar.getInstance();
            for (Person person : snapshot.people.values()) {
                if (revision != filterRevision || Thread.currentThread().isInterrupted()) return;
                if (!parsed.kinshipFirstId.isEmpty() && !parsed.kinshipSecondId.isEmpty()) {
                    if (person.id.equals(parsed.kinshipFirstId) || person.id.equals(parsed.kinshipSecondId)) ids.add(person.id);
                } else if (SmartPeopleSearch.matches(snapshot,parsed,person,this::hasSearchPhoto,now)) ids.add(person.id);
            }
            mainHandler.post(() -> {
                if (detached || revision != filterRevision || source != state) return;
                smartFilter=parsed; filterMatchIds=ids; matchingPersonIds=null; familyLinePlanDirty=true; invalidate();
            });
        });
        invalidate();
    }

    void setNearGenerations(int value) {
        nearGenerations = Math.max(1, Math.min(99, value));
        visibleBranchIds = null; matchingPersonIds = null; familyLinePlanDirty = true; invalidate();
    }

    void setBranchMode(String mode, String anchorId) {
        branchMode = mode == null || mode.isEmpty() ? "all" : mode;
        branchAnchorId = anchorId == null ? "" : anchorId;
        visibleBranchIds = null;
        matchingPersonIds = null;
        familyLinePlanDirty = true;
        invalidate();
    }

    void invalidateStructureCaches() {
        coordinateRevision++;
        visibleBranchIds = null;
        matchingPersonIds = null;
        familyLinePlanDirty = true;
        spatialDirty = true;
        familyLinePlanDirty = true;
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
        if (compactCards == enabled) return;
        compactCards = enabled;
        familyLinePlanDirty = true;
        nameLinesCache.clear();
        nameEllipsisCache.clear();
        invalidate();
    }

    void setFontScale(float value) {
        float normalized = MainActivity.normalizeFontScale(value);
        if (Math.abs(fontScale - normalized) < 0.001f) return;
        fontScale = normalized;
        nameLinesCache.clear();
        nameEllipsisCache.clear();
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
        familyRoutePaths.clear();
        coloredFamilyPaths.clear();
        parentLineMode = "orthogonal".equals(mode) ? "orthogonal" : "smart";
        invalidate();
    }

    void setFamilyLineMode(String mode) {
        familyLineMode = "individual".equals(mode) ? "individual" : "centered";
        invalidate();
    }

    void setCardEdgeStyle(String value) {
        String next = normalizeWorkshopStyle(value);
        if (cardEdgeStyle.equals(next)) return;
        cardEdgeStyle = next;
        invalidate();
    }

    void setCardStyle(String value) {
        String next = normalizeWorkshopStyle(value);
        if (cardStyle.equals(next)) return;
        cardStyle = next;
        fillGradients.clear();overlayGradients.clear();
        invalidate();
    }

    void setCanvasStyle(String value) {
        String next = normalizeWorkshopStyle(value);
        if (canvasStyle.equals(next)) return;
        canvasStyle = next;
        invalidate();
    }

    void setLinkStyle(String value) {
        String next = normalizeWorkshopStyle(value);
        if (linkStyle.equals(next)) return;
        linkStyle = next;
        invalidate();
    }

    /** A private export view uses the same drawing code at a fixed logical zoom.
     * Resolution and preview size are applied by Canvas, so details never change
     * between preview, single PNG and individual tiles. The detached export renderer is owned exclusively by its render task. */
    void drawExport(Canvas canvas, RectF bounds, boolean grid, float pixelsPerWorld) {
        float oldScale = scale, oldX = offsetX, oldY = offsetY;
        exportRendering = true;
        exportGrid = grid;
        exportPhotoScale = Math.max(1f, Math.min(16f, pixelsPerWorld));
        try {
            scale = 1f;
            offsetX = -bounds.left;
            offsetY = -bounds.top;
            drawScene(canvas, (int) Math.ceil(bounds.width()), (int) Math.ceil(bounds.height()), true);
        } finally {
            scale = oldScale;
            offsetX = oldX;
            offsetY = oldY;
            exportRendering = false;
        }
    }

    RectF imageExportBounds() {
        RectF bounds = exportBounds();
        bounds.inset(-64f, -64f);
        return bounds;
    }

    void releaseExportResources() {
        animatedCardAtlas.release();
        cardRasterCache.clear();gpuFireCardEffect.trim();
        filterRevision++; filterWorker.shutdownNow();
        photoCache.evictAll();
        ThumbnailLoader.SHARED.cancel(this);
    }

    void copyExportStylesTo(TreeCanvasView target) {
        target.setState(state); target.setMediaStore(mediaStore);
        target.setCardStyle(cardStyle); target.setCardEdgeStyle(cardEdgeStyle); target.setLinkStyle(linkStyle);
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
        fillGradients.clear();overlayGradients.clear();
        theme = "print".equals(value) ? "clean" : "dark".equals(value) || "clean".equals(value) ? value : "light";
        invalidate();
    }

    void setWorkspaceSize(int width, int height) {
        workspaceWidth = TreeLayoutEngine.normalizeSurfaceWidth(width);
        workspaceHeight = TreeLayoutEngine.normalizeSurfaceHeight(height);
        invalidate();
    }

    void zoomBy(float factor) {
        zoom(factor, getWidth() / 2f, getHeight() / 2f);
    }

    void startContinuousZoom(boolean in) {
        stopContinuousZoom();
        continuousZoomDirection = in ? 1 : -1;
        continuousZoomTime = SystemClock.uptimeMillis();
        postOnAnimation(continuousZoom);
    }

    void stopContinuousZoom() {
        continuousZoomDirection = 0;
        removeCallbacks(continuousZoom);
    }

    void fit() {
        if (state == null || state.people.isEmpty() || getWidth() == 0 || getHeight() == 0) return;
        RectF bounds = treeBounds();
        float sx = (getWidth() - 80f) / Math.max(cardWidthWorld(), bounds.width());
        float sy = (getHeight() - 180f) / Math.max(cardHeightWorld(), bounds.height());
        float target = clamp(Math.min(sx, sy), 0.08f, 2.2f);
        animateCamera(target, getWidth() / 2f - bounds.centerX() * target,
            getHeight() / 2f - bounds.centerY() * target);
    }

    void focusWorkspaceCenter() {
        if (getWidth() == 0 || getHeight() == 0) return;
        animateCamera(scale, getWidth() / 2f - workspaceWidth / 2f * scale,
            getHeight() / 2f - workspaceHeight / 2f * scale);
    }

    void focusPerson(String personId) {
        if (state == null || getWidth() == 0 || getHeight() == 0) return;
        Person person = state.people.get(personId);
        if (person == null) return;
        float target = clamp(Math.max(scale, 0.95f), 0.08f, 2.2f);
        animateCamera(target, getWidth() / 2f - (person.x + cardWidthWorld() / 2f) * target,
            getHeight() / 2f - (person.y + cardHeightWorld() / 2f) * target);
        focusHighlightId = personId;
        focusHighlightUntil = SystemClock.uptimeMillis() + 1800L;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        ThumbnailLoader.SHARED.begin(this);
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
        long profileStart=BuildConfig.DEBUG?System.nanoTime():0;
        fireFrameSeconds = exportMode ? 2.4f : (SystemClock.uptimeMillis()-effectClockOrigin) / 1000f;
        canvas.drawColor(canvasBackground());
        drawCanvasStyle(canvas, width, height);
        if ((!exportMode || exportGrid) && shouldDrawDefaultGrid()) drawGrid(canvas, width, height);
        if (state == null) return;
        refreshToday();
        prepareBranchCache();
        float visualPad = Math.max(80f, 20f / scale);
        if (canvas.getClipBounds(tmpSrc)) {
            visibleScratch.set(wx(tmpSrc.left) - visualPad, wy(tmpSrc.top) - visualPad,
                wx(tmpSrc.right) + visualPad, wy(tmpSrc.bottom) + visualPad);
        } else visibleScratch.set(wx(0) - visualPad, wy(0) - visualPad,
            wx(width) + visualPad, wy(height) + visualPad);
        RectF visible = visibleScratch;
        if (generationLines) drawGenerationLines(canvas, visible, width, height);
        if (state.guidesVisible && (!exportMode || generationLines)) drawGuides(canvas, visible);
        long profileLinks=BuildConfig.DEBUG?System.nanoTime():0;
        drawLinks(canvas, visible);
        long profileCards=BuildConfig.DEBUG?System.nanoTime():0;
        cardRasterCache.beginFrame();
        drawCards(canvas, visible, exportMode);
        if(gpuFireForFrame)gpuEffectFrames++;
        if(BuildConfig.DEBUG && !exportMode)debugFrameStages=new long[]{profileLinks-profileStart,
            profileCards-profileLinks,System.nanoTime()-profileCards,animatedCardAtlas.presentedFrames(),gpuEffectFrames,
            cardRasterCache.buildsForTests(),cardRasterCache.frameBuildsForTests()};
        if (!exportMode) drawSelectionOverlay(canvas);
    }

    private void drawGrid(Canvas canvas, int width, int height) {
        float grid = Math.max(1f, TreeLayoutEngine.GRID * scale);
        float startX = positiveModulo(offsetX, grid);
        float startY = positiveModulo(offsetY, grid);
        canvas.getClipBounds(gridClip);
        startX += (float) Math.floor((gridClip.left - startX) / grid) * grid;
        startY += (float) Math.floor((gridClip.top - startY) / grid) * grid;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        if ("canvas_background_blueprint".equals(canvasStyle) && !"dark".equals(theme)) {
            paint.setColor(Color.argb(scale < 0.2f ? 26 : 46, 50, 109, 148));
        } else if ("canvas_background_paper".equals(canvasStyle) && !"dark".equals(theme)) {
            paint.setColor(Color.argb(scale < 0.2f ? 16 : 28, 128, 104, 72));
        } else if ("canvas_background_garden".equals(canvasStyle) && !"dark".equals(theme)) {
            paint.setColor(Color.argb(scale < 0.2f ? 16 : 30, 69, 132, 91));
        } else if ("canvas_background_botanical".equals(canvasStyle) && !"dark".equals(theme)) {
            paint.setColor(Color.argb(scale < 0.2f ? 13 : 23, 118, 91, 54));
        } else if ("canvas_background_paper".equals(canvasStyle)) {
            paint.setColor(Color.argb(scale < 0.2f ? 20 : 36, 105, 119, 124));
        } else if ("canvas_background_garden".equals(canvasStyle)) {
            paint.setColor(Color.argb(scale < 0.2f ? 18 : 34, 91, 139, 111));
        } else {
            paint.setColor(Color.argb(scale < 0.2f ? 20 : 34, 45, 112, 125));
        }
        for (float x = startX; x < Math.min(width,gridClip.right)+grid; x += grid) canvas.drawLine(x, 0, x, height, paint);
        for (float y = startY; y < Math.min(height,gridClip.bottom)+grid; y += grid) canvas.drawLine(0, y, width, y, paint);
    }

    private int canvasBackground() {
        if ("dark".equals(theme)) {
            if ("canvas_background_paper".equals(canvasStyle)) return Color.rgb(33, 36, 37);
            if ("canvas_background_blueprint".equals(canvasStyle)) return Color.rgb(20, 34, 44);
            if ("canvas_background_deep_blueprint".equals(canvasStyle)) return Color.rgb(5, 34, 88);
            if ("canvas_background_garden".equals(canvasStyle)) return Color.rgb(23, 39, 32);
            if ("canvas_background_botanical".equals(canvasStyle)) return Color.rgb(31, 29, 21);
            return Color.rgb(16, 23, 27);
        }
        if ("clean".equals(theme)) {
            return "canvas_background_paper".equals(canvasStyle)
                ? Color.rgb(245, 241, 229)
                : Color.WHITE;
        }
        if ("canvas_background_paper".equals(canvasStyle)) return Color.rgb(245, 241, 229);
        if ("canvas_background_blueprint".equals(canvasStyle)) return Color.rgb(226, 238, 247);
        if ("canvas_background_deep_blueprint".equals(canvasStyle)) return Color.rgb(7, 55, 126);
        if ("canvas_background_garden".equals(canvasStyle)) return Color.rgb(238, 247, 235);
        if ("canvas_background_botanical".equals(canvasStyle)) return Color.rgb(241, 222, 181);
        return Color.rgb(243, 246, 248);
    }

    private boolean shouldDrawDefaultGrid() {
        if ("canvas_background_botanical".equals(canvasStyle)) {
            return exportRendering && exportGrid;
        }
        return !"clean".equals(theme)
            && !"canvas_background_blueprint".equals(canvasStyle)
            && !"canvas_background_deep_blueprint".equals(canvasStyle);
    }

    private void drawCanvasStyle(Canvas canvas, int width, int height) {
        if ("clean".equals(theme) && !"canvas_background_paper".equals(canvasStyle)) return;
        paint.setPathEffect(null);
        if ("canvas_background_paper".equals(canvasStyle)) {
            drawPaperRelief(canvas, width, height);
            return;
        } else if ("canvas_background_botanical".equals(canvasStyle)) {
            botanicalCanvasBackground.draw(
                canvas, width, height, scale, offsetX, offsetY, "dark".equals(theme), exportRendering);
            return;
        } else if ("canvas_background_blueprint".equals(canvasStyle)) {
            drawAnchoredBlueprintGrid(
                canvas,
                width,
                height,
                TreeLayoutEngine.GRID * 2f,
                "dark".equals(theme) ? Color.argb(50, 88, 141, 170) : Color.argb(40, 45, 106, 148),
                "dark".equals(theme) ? Color.argb(68, 159, 201, 220) : Color.argb(56, 255, 255, 255));
        } else if ("canvas_background_deep_blueprint".equals(canvasStyle)) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            paint.setShader(canvasGradient(width,height));
            canvas.drawRect(0f, 0f, width, height, paint);
            paint.setShader(null);
            drawAnchoredBlueprintGrid(
                canvas,
                width,
                height,
                TreeLayoutEngine.GRID * 2f,
                Color.argb(58, 152, 210, 242),
                Color.argb(90, 219, 244, 255));
        } else if ("canvas_background_garden".equals(canvasStyle)) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            paint.setShader(canvasGradient(width,height));
            canvas.drawRect(0f, 0f, width, height, paint);
            paint.setShader(null);
        }
    }

    private Shader canvasGradient(int width,int height) {
        if(canvasFillGradient==null || gradientCanvasWidth!=width || gradientCanvasHeight!=height
            || !canvasStyle.equals(gradientCanvasStyle) || !theme.equals(gradientCanvasTheme)) {
            boolean dark="dark".equals(theme),blue="canvas_background_deep_blueprint".equals(canvasStyle);
            int from=blue?(dark?Color.rgb(7,47,115):Color.rgb(10,72,150))
                :(dark?Color.argb(54,57,91,70):Color.argb(76,255,249,223));
            int to=blue?(dark?Color.rgb(3,24,68):Color.rgb(4,34,91))
                :(dark?Color.argb(62,18,62,49):Color.argb(66,177,217,196));
            canvasFillGradient=new LinearGradient(0,0,width,height,from,to,Shader.TileMode.CLAMP);
            gradientCanvasWidth=width;gradientCanvasHeight=height;gradientCanvasStyle=canvasStyle;gradientCanvasTheme=theme;
        }
        return canvasFillGradient;
    }

    private void drawPaperRelief(Canvas canvas, int width, int height) {
        paperTexture.draw(canvas, width, height, scale, offsetX, offsetY, "dark".equals(theme));
    }

    private void drawAnchoredBlueprintGrid(
        Canvas canvas,
        int width,
        int height,
        float worldStep,
        int minorColor,
        int majorColor
    ) {
        if (exportRendering && !exportGrid) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setPathEffect(null);
        paint.setStrokeWidth(1f);
        if (worldStep * scale >= 10f) {
            paint.setColor(minorColor);
            drawWorldGridLines(canvas, width, height, worldStep);
        }
        paint.setStrokeWidth(Math.max(1f, 1.5f * scale));
        paint.setColor(majorColor);
        drawWorldGridLines(canvas, width, height, worldStep * 4f);
    }

    private void drawWorldGridLines(Canvas canvas, int width, int height, float worldStep) {
        float startX = (float) Math.floor(wx(0f) / worldStep) * worldStep;
        float endX = wx(width);
        for (float worldX = startX; worldX <= endX + worldStep; worldX += worldStep) {
            float x = sx(worldX);
            canvas.drawLine(x, 0f, x, height, paint);
        }
        float startY = (float) Math.floor(wy(0f) / worldStep) * worldStep;
        float endY = wy(height);
        for (float worldY = startY; worldY <= endY + worldStep; worldY += worldStep) {
            float y = sy(worldY);
            canvas.drawLine(0f, y, width, y, paint);
        }
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
        Set<String> routedVisibleIds = matchingPersonIds();
        FamilyLineRouter.Plan familyPlan = "centered".equals(familyLineMode)
            ? familyLinePlan(routedVisibleIds)
            : FamilyLineRouter.Plan.empty();
        prepareLinkBounds(familyPlan,routedVisibleIds);
        routeBounds.queryInto(visible.left,visible.top,visible.right,visible.bottom,routeCandidates);
        for (int routeIndex=0;routeIndex<routeCandidates.size();routeIndex++) {
            FamilyLineRouter.FamilyRoute route=routeCandidates.get(routeIndex);
            Relation representative = familyPlan.representative(route);
            if (representative == null || !familyRouteIntersects(route, visible)) continue;
            boolean selected = route.containsRelation(selectedLinkId);
            boolean onPath = pathActive && familyRouteOnPath(route);
            if (RewardCatalog.LINK_STYLE_CARD_COLORS.equals(linkStyle) && !selected && !onPath) {
                drawCardColorFamily(canvas, route);
                continue;
            }
            Path path = familyRoutePath(route);
            drawStyledLink(canvas, path, representative, selected, onPath);
        }
        relationBounds.queryInto(visible.left,visible.top,visible.right,visible.bottom,relationCandidates);
        for (int linkIndex=0;linkIndex<relationCandidates.size();linkIndex++) {
            Relation link=relationCandidates.get(linkIndex);
            if (familyPlan.consumes(link)) continue;
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
            Path path = linkPath(link, from, to);
            drawStyledLink(canvas, path, link, selected, onPath);
        }
        paint.setPathEffect(null);
        if (pathActive) postInvalidateOnAnimation();
    }

    private boolean familyRouteIntersects(
        FamilyLineRouter.FamilyRoute route,
        RectF visible
    ) {
        for (int i=0;i<route.segments.size();i++) {
            FamilyLineRouter.Segment segment=route.segments.get(i);
            linkBoundsScratch.set(
                Math.min(segment.x1, segment.x2) - 16f,
                Math.min(segment.y1, segment.y2) - 16f,
                Math.max(segment.x1, segment.x2) + 16f,
                Math.max(segment.y1, segment.y2) + 16f);
            if (RectF.intersects(visible, linkBoundsScratch)) return true;
        }
        return false;
    }

    private void prepareLinkBounds(FamilyLineRouter.Plan plan, Set<String> matches) {
        if (indexedPlan==plan && indexedMatches==matches && indexedCoordinates==coordinateRevision
                && indexedCardWidth==cardWidthWorld() && indexedCardHeight==cardHeightWorld()) return;
        routeBounds=new SceneBoundsIndex<>(); relationBounds=new SceneBoundsIndex<>();
        for (FamilyLineRouter.FamilyRoute route:plan.routes) {
            float left=Float.POSITIVE_INFINITY,top=left,right=Float.NEGATIVE_INFINITY,bottom=right;
            for (FamilyLineRouter.Segment segment:route.segments) {
                left=Math.min(left,Math.min(segment.x1,segment.x2)-16f); top=Math.min(top,Math.min(segment.y1,segment.y2)-16f);
                right=Math.max(right,Math.max(segment.x1,segment.x2)+16f); bottom=Math.max(bottom,Math.max(segment.y1,segment.y2)+16f);
            }
            routeBounds.add(route,left,top,right,bottom);
        }
        for (Relation link:state.links) {
            if (plan.consumes(link)) continue;
            Person a=state.people.get(link.from),b=state.people.get(link.to);
            if (a==null || b==null || !matches.contains(a.id) || !matches.contains(b.id)) continue;
            relationBounds.add(link,Math.min(a.x,b.x)-280f,Math.min(a.y,b.y)-280f,
                Math.max(a.x,b.x)+cardWidthWorld()+280f,Math.max(a.y,b.y)+cardHeightWorld()+280f);
        }
        indexedPlan=plan; indexedMatches=matches; indexedCoordinates=coordinateRevision;
        indexedCardWidth=cardWidthWorld(); indexedCardHeight=cardHeightWorld();
    }

    /**
     * Family routing is substantially more expensive than transforming its
     * finished segments for the current viewport.  Keep it stable while the
     * user pans or zooms, and rebuild only when route-relevant model data or
     * visibility changes.
     */
    private FamilyLineRouter.Plan familyLinePlan(Set<String> visibleIds) {
        // Coordinates are revised by state binding, structural invalidation,
        // layout edits, and card dragging.  This keeps pan/zoom frames O(1)
        // while still invalidating retained routes after in-place edits.
        if (!familyLinePlanDirty && familyObservedCoordinateRevision==coordinateRevision) {
            return familyLinePlan;
        }
        familyObservedCoordinateRevision=coordinateRevision;
        familyLinePlanDirty = false;
        long signature = 0xcbf29ce484222325L;
        signature = mixFamilySignature(signature, Float.floatToIntBits(cardWidthWorld()));
        signature = mixFamilySignature(signature, Float.floatToIntBits(cardHeightWorld()));
        signature = mixFamilySignature(signature, state.people.size());
        signature = mixFamilySignature(signature, state.links.size());
        for (Person person : state.people.values()) {
            if (person == null) {
                signature = mixFamilySignature(signature, 0);
                continue;
            }
            signature = mixFamilySignature(signature, hash(person.id));
            signature = mixFamilySignature(signature, Float.floatToIntBits(person.x));
            signature = mixFamilySignature(signature, Float.floatToIntBits(person.y));
            signature = mixFamilySignature(signature, hash(PersonGender.resolve(person)));
            signature = mixFamilySignature(
                signature,
                visibleIds != null && visibleIds.contains(person.id) ? 1 : 0);
        }
        for (Relation relation : state.links) {
            if (relation == null) {
                signature = mixFamilySignature(signature, 0);
                continue;
            }
            signature = mixFamilySignature(signature, hash(relation.id));
            signature = mixFamilySignature(signature, hash(relation.type));
            signature = mixFamilySignature(signature, hash(relation.from));
            signature = mixFamilySignature(signature, hash(relation.to));
        }
        if (signature != familyLinePlanSignature) {
            familyRoutePaths.clear();
            coloredFamilyPaths.clear();
            familyLinePlan = FamilyLineRouter.build(
                state,
                cardWidthWorld(),
                cardHeightWorld(),
                TreeLayoutEngine.GRID,
                visibleIds);
            familyLinePlanSignature = signature;
        }
        return familyLinePlan;
    }

    private static long mixFamilySignature(long signature, int value) {
        return (signature ^ (value & 0xffffffffL)) * 0x100000001b3L;
    }

    private static int hash(String value) {
        return value == null ? 0 : value.hashCode();
    }

    private boolean familyRouteOnPath(FamilyLineRouter.FamilyRoute route) {
        for (String parentId : route.parentIds) {
            for (String childId : route.childIds) {
                if (highlightedPathEdges.contains(edgeKey(parentId, childId))) return true;
            }
        }
        return false;
    }

    private Path familyRoutePath(FamilyLineRouter.FamilyRoute route) {
        Path worldPath = familyRoutePaths.get(route);
        if (worldPath == null) {
            worldPath = new Path();
            appendFamilyRoute(worldPath, route, null);
            familyRoutePaths.put(route, worldPath);
        }
        lastLinkWorldPath = worldPath;
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
        if ("link_style_botanical".equals(linkStyle)) return worldPath;
        linkPathScratch.reset();
        worldPath.transform(worldToScreen, linkPathScratch);
        return linkPathScratch;
    }

    private void appendFamilyRoute(Path path, FamilyLineRouter.FamilyRoute route, String ownerId) {
        boolean orthogonal = "orthogonal".equals(parentLineMode);
        if (!orthogonal) {
            appendCurvedFamilyRoute(path, route, ownerId);
        }
        List<PointF> points = new ArrayList<>();
        String previousOwner = null;
        for (FamilyLineRouter.Segment segment : route.segments) {
            String effectiveOwner = segment.personId == null ? route.childIds.get(0) : segment.personId;
            if (ownerId != null && !ownerId.equals(effectiveOwner)) continue;
            if (orthogonal) {
                path.moveTo(segment.x1, segment.y1);
                path.lineTo(segment.x2, segment.y2);
                continue;
            }
            if (!route.routedChildIds.contains(segment.personId)) continue;
            PointF end = points.isEmpty() ? null : points.get(points.size() - 1);
            // Shared trunk/bus junctions must stay exact: round only within
            // one person's continuous attachment, never across a family fork.
            boolean continues = end != null && segment.personId != null
                && segment.personId.equals(previousOwner)
                && Math.abs(end.x - segment.x1) < 0.01f
                && Math.abs(end.y - segment.y1) < 0.01f;
            if (!continues) {
                appendSmoothPolyline(path, points);
                points.clear();
                points.add(new PointF(segment.x1, segment.y1));
            }
            if (points.size() >= 2) {
                PointF before = points.get(points.size() - 2);
                PointF last = points.get(points.size() - 1);
                float ax = last.x - before.x, ay = last.y - before.y;
                float bx = segment.x2 - last.x, by = segment.y2 - last.y;
                if (Math.abs(ax * by - ay * bx) < 0.01f && ax * bx + ay * by > 0f) {
                    points.remove(points.size() - 1);
                }
            }
            points.add(new PointF(segment.x2, segment.y2));
            previousOwner = segment.personId;
        }
        appendSmoothPolyline(path, points);
    }

    private static void appendSmoothPolyline(Path path, List<PointF> points) {
        if (points.size() < 2) return;
        PointF first = points.get(0);
        path.moveTo(first.x, first.y);
        for (int i = 1; i + 1 < points.size(); i++) {
            PointF before = points.get(i - 1), corner = points.get(i), after = points.get(i + 1);
            float incoming = (float) Math.hypot(corner.x - before.x, corner.y - before.y);
            float outgoing = (float) Math.hypot(after.x - corner.x, after.y - corner.y);
            // Stay within the clearance reserved by the obstacle router.
            float radius = Math.min(TreeLayoutEngine.GRID * 0.45f, Math.min(incoming, outgoing) / 2f);
            if (radius <= 0.01f) {
                path.lineTo(corner.x, corner.y);
                continue;
            }
            path.lineTo(corner.x + (before.x - corner.x) * radius / incoming,
                corner.y + (before.y - corner.y) * radius / incoming);
            path.quadTo(corner.x, corner.y,
                corner.x + (after.x - corner.x) * radius / outgoing,
                corner.y + (after.y - corner.y) * radius / outgoing);
        }
        PointF last = points.get(points.size() - 1);
        path.lineTo(last.x, last.y);
    }

    private void appendCurvedFamilyRoute(Path worldPath, FamilyLineRouter.FamilyRoute route, String ownerId) {
        if (state == null || route == null) return;
        float cardW = cardWidthWorld();
        float cardH = cardHeightWorld();
        boolean paired = route.parentIds.size() > 1;
        for (String parentId : route.parentIds) {
            if (ownerId != null && !ownerId.equals(parentId)) continue;
            Person parent = state.people.get(parentId);
            if (parent == null) continue;
            float centerX = parent.x + cardW / 2f;
            boolean unionBesideCard = paired
                && route.unionY >= parent.y - 1f
                && route.unionY <= parent.y + cardH + 1f;
            float startX = unionBesideCard
                ? (route.unionX >= centerX ? parent.x + cardW : parent.x)
                : centerX;
            float startY = unionBesideCard ? parent.y + cardH / 2f : parent.y + cardH;
            if (Math.abs(startX - route.unionX) < 0.01f
                && Math.abs(startY - route.unionY) < 0.01f) continue;
            worldPath.moveTo(startX, startY);
            float dx = route.unionX - startX;
            float dy = route.unionY - startY;
            if (Math.abs(dy) < 0.01f) {
                worldPath.cubicTo(
                    startX + dx * 0.38f,
                    startY,
                    startX + dx * 0.78f,
                    route.unionY,
                    route.unionX,
                    route.unionY);
            } else {
                worldPath.cubicTo(
                    startX,
                    startY + dy * 0.52f,
                    route.unionX,
                    route.unionY - dy * 0.18f,
                    route.unionX,
                    route.unionY);
            }
        }

        if ((ownerId == null || ownerId.equals(route.childIds.get(0)))
            && Math.abs(route.busY - route.unionY) > 0.01f) {
            worldPath.moveTo(route.unionX, route.unionY);
            worldPath.lineTo(route.unionX, route.busY);
        }
        for (String childId : route.childIds) {
            if (ownerId != null && !ownerId.equals(childId)) continue;
            if (route.routedChildIds.contains(childId)) continue;
            Person child = state.people.get(childId);
            if (child == null) continue;
            float childX = child.x + cardW / 2f;
            float childY = child.y;
            worldPath.moveTo(route.unionX, route.busY);
            float dy = childY - route.busY;
            if (Math.abs(childX - route.unionX) < 0.01f) {
                worldPath.lineTo(childX, childY);
            } else {
                worldPath.cubicTo(
                    route.unionX,
                    route.busY + dy * 0.34f,
                    childX,
                    childY - dy * 0.34f,
                    childX,
                    childY);
            }
        }
    }

    private void drawCardColorFamily(Canvas canvas, FamilyLineRouter.FamilyRoute route) {
        // Initialise the same viewport transform used by ordinary family paths.
        familyRoutePath(route);
        List<ColoredFamilyPath> paths = coloredFamilyPaths.get(route);
        if (paths == null) {
            paths = new ArrayList<>();
            java.util.LinkedHashSet<String> owners = new java.util.LinkedHashSet<>(route.parentIds);
            owners.addAll(route.childIds);
            for (String owner : owners) {
                Path world = new Path();
                appendFamilyRoute(world, route, owner);
                paths.add(new ColoredFamilyPath(world, new Relation(route.id, "parent", owner, owner)));
            }
            coloredFamilyPaths.put(route, paths);
        }
        for (ColoredFamilyPath part : paths) {
            linkPathScratch.reset();
            part.world.transform(worldToScreen, linkPathScratch);
            drawStyledLink(canvas, linkPathScratch, part.relation, false, false);
        }
    }

    private static final class ColoredFamilyPath {
        final Path world;
        final Relation relation;
        ColoredFamilyPath(Path world, Relation relation) { this.world=world; this.relation=relation; }
    }

    private void drawStyledLink(Canvas canvas, Path path, Relation link, boolean selected, boolean onPath) {
        boolean botanical = "link_style_botanical".equals(linkStyle);
        Path screenPath = path;
        if (botanical && (selected || onPath)) {
            // Selection and path highlights are always painted in screen
            // coordinates. Ordinary botanical links remain in world space so
            // their cached decoration keeps the pinch/pan fast path.
            linkPathScratch.reset();
            path.transform(worldToScreen, linkPathScratch);
            screenPath = linkPathScratch;
        }
        int baseColor = onPath
            ? AppThemePalette.blue()
            : selected
                ? AppThemePalette.text(Color.rgb(197, 83, 75))
                : AppThemePalette.relationColor(link.type);
        int color = styledLinkColor(link, baseColor, selected, onPath);
        float coreWidth = linkStrokeWidth(selected, onPath);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (selected) {
            // A broad translucent halo keeps selection unmistakable for every
            // decorative style as well as for all four routing combinations.
            paint.setPathEffect(null);
            paint.setStrokeWidth(coreWidth + Math.max(5f, 7f * scale));
            paint.setColor(withAlpha(AppThemePalette.text(Color.rgb(197, 83, 75)), 76));
            canvas.drawPath(screenPath, paint);
        }
        if (botanical && !onPath) {
            canvas.save();
            canvas.concat(worldToScreen);
            botanicalEffect.link(
                canvas,
                lastLinkWorldPath,
                "sibling".equals(link.type),
                selected,
                scale);
            canvas.restore();
            return;
        } else if ("link_style_ribbon".equals(linkStyle) && !onPath) {
            drawRibbonLink(canvas, path, link, color, selected);
            return;
        } else if ("link_style_fire".equals(linkStyle) && !onPath) {
            drawFireLink(canvas, path, link, color, selected);
            return;
        } else if ("link_style_ink".equals(linkStyle) && !onPath) {
            {
                paint.setPathEffect(null);
                paint.setStrokeWidth(coreWidth + Math.max(0.6f, 1.2f * scale));
                paint.setColor(withAlpha(Color.rgb(18, 24, 27), AppThemePalette.isDark() ? 70 : 34));
                canvas.drawPath(path, paint);
            }
        } else if ("link_style_blueprint".equals(linkStyle) && !onPath) {
            paint.setPathEffect(null);
            paint.setStrokeWidth(coreWidth + Math.max(1f, 2.8f * scale));
            paint.setColor(withAlpha(blend(color, Color.WHITE, 0.34f), AppThemePalette.isDark() ? 66 : 46));
            canvas.drawPath(path, paint);
        }
        paint.setStrokeWidth(coreWidth);
        paint.setColor(color);
        if (RewardCatalog.LINK_STYLE_CARD_COLORS.equals(linkStyle) && !selected && !onPath) {
            Person from = state == null ? null : state.people.get(link.from);
            Person to = state == null ? null : state.people.get(link.to);
            if (from != null && to != null && !from.id.equals(to.id)) {
                float x1=from.x+cardWidthWorld()/2f,y1=from.y+cardHeightWorld()/2f;
                float x2=to.x+cardWidthWorld()/2f,y2=to.y+cardHeightWorld()/2f;
                LinkGradient gradient=linkGradients.get(link);
                if(gradient==null || !gradient.matches(x1,y1,x2,y2,from.color,to.color)) {
                    gradient=new LinkGradient(x1,y1,x2,y2,from.color,to.color);linkGradients.put(link,gradient);
                }
                gradient.shader.setLocalMatrix(worldToScreen);paint.setShader(gradient.shader);
            }
        }
        paint.setPathEffect(linkPathEffect(link, onPath));
        canvas.drawPath(screenPath, paint);
        paint.setShader(null);
        paint.setPathEffect(null);
        drawLinkStyleEndpoints(canvas, screenPath, color, onPath);
    }

    private float linkStrokeWidth(boolean selected, boolean onPath) {
        if (onPath) return Math.max(5f, 8f * scale);
        if (selected) return Math.max(3.4f, 5f * scale);
        if ("link_style_ink".equals(linkStyle)) return Math.max(1.35f, 4.1f * scale);
        if ("link_style_ribbon".equals(linkStyle)) return Math.max(1.25f, 2.45f * scale);
        if ("link_style_blueprint".equals(linkStyle)) return Math.max(2.1f, 3.4f * scale);
        if ("link_style_fire".equals(linkStyle)) return Math.max(0.85f, 2.55f * scale);
        return Math.max(2.4f, 3.6f * scale);
    }

    private static final class LinkGradient {
        final float x1,y1,x2,y2;final int from,to;final Shader shader;
        LinkGradient(float x1,float y1,float x2,float y2,int from,int to) {
            this.x1=x1;this.y1=y1;this.x2=x2;this.y2=y2;this.from=from;this.to=to;
            shader=new LinearGradient(x1,y1,x2,y2,from,to,Shader.TileMode.CLAMP);
        }
        boolean matches(float a,float b,float c,float d,int e,int f) {
            return x1==a&&y1==b&&x2==c&&y2==d&&from==e&&to==f;
        }
    }

    private DashPathEffect linkPathEffect(Relation link, boolean onPath) {
        if (onPath) return null;
        return "sibling".equals(link.type) ? siblingDashEffect() : null;
    }

    private int styledLinkColor(Relation link, int color, boolean selected, boolean onPath) {
        if (selected || onPath) return color;
        if (RewardCatalog.LINK_STYLE_CARD_COLORS.equals(linkStyle)) {
            Person person = state == null ? null : state.people.get(link.to);
            return person == null ? color : person.color;
        }
        int background = canvasBackground();
        boolean darkCanvas = Color.red(background) * 0.299f + Color.green(background) * 0.587f
            + Color.blue(background) * 0.114f < 128f;
        if ("link_style_ink".equals(linkStyle)) {
            if (darkCanvas) {
                if ("partner".equals(link.type)) return Color.rgb(206, 172, 126);
                if ("sibling".equals(link.type)) return Color.rgb(167, 180, 193);
                return Color.rgb(188, 207, 217);
            }
            if ("partner".equals(link.type)) return Color.rgb(112, 77, 48);
            if ("sibling".equals(link.type)) return Color.rgb(82, 92, 102);
            return Color.rgb(37, 50, 57);
        }
        if ("link_style_ribbon".equals(linkStyle)) {
            return blend(color, darkCanvas ? Color.WHITE : Color.rgb(18, 74, 91), darkCanvas ? 0.55f : 0.18f);
        }
        if ("link_style_blueprint".equals(linkStyle)) {
            if ("canvas_background_deep_blueprint".equals(canvasStyle)) return Color.rgb(190, 235, 255);
            if (darkCanvas) return Color.rgb(142, 196, 232);
            if ("partner".equals(link.type)) return Color.rgb(52, 121, 176);
            if ("sibling".equals(link.type)) return Color.rgb(68, 133, 154);
            return Color.rgb(27, 98, 158);
        }
        if ("link_style_fire".equals(linkStyle)) {
            if ("partner".equals(link.type)) return Color.rgb(255, 153, 48);
            if ("sibling".equals(link.type)) return Color.rgb(255, 190, 73);
            return Color.rgb(245, 87, 33);
        }
        return darkCanvas ? blend(color, Color.WHITE, 0.4f) : color;
    }

    private void drawLinkStyleEndpoints(Canvas canvas, Path path, int color, boolean onPath) {
        if (onPath) return;
        if (!"link_style_blueprint".equals(linkStyle)) return;
        PathMeasure measure = new PathMeasure(path, false);
        float length = measure.getLength();
        if (length <= 0f) return;
        float[] point = new float[2];
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(color, "link_style_ribbon".equals(linkStyle) ? 138 : 168));
        float radius = "link_style_ribbon".equals(linkStyle)
            ? Math.max(2f, 3.4f * scale)
            : Math.max(1.6f, 2.5f * scale);
        if (measure.getPosTan(0f, point, null)) canvas.drawCircle(point[0], point[1], radius, paint);
        if (measure.getPosTan(length, point, null)) canvas.drawCircle(point[0], point[1], radius, paint);
    }

    private void drawRibbonLink(Canvas canvas, Path path, Relation link, int color, boolean selected) {
        if (lastLinkWorldPath != null) {
            List<Path[]> contours = ribbonGeometry.get(lastLinkWorldPath);
            if (contours == null) {
                contours = new ArrayList<>();
                PathMeasure worldMeasure = new PathMeasure(lastLinkWorldPath, false);
                float screenScale = scale;
                try {
                    scale = 1f;
                    do {
                        float length = worldMeasure.getLength();
                        if (length <= 0f) continue;
                        Path a = new Path(), b = new Path();
                        if ("sibling".equals(link.type)) {
                            buildDashedRibbon(a,worldMeasure,length,2.1f,0f);
                            buildDashedRibbon(b,worldMeasure,length,2.1f,(float)Math.PI);
                        } else {
                            buildRibbonStrand(a,worldMeasure,length,2.1f,38f,0f);
                            buildRibbonStrand(b,worldMeasure,length,2.1f,38f,(float)Math.PI);
                        }
                        contours.add(new Path[]{a,b});
                    } while (worldMeasure.nextContour());
                } finally { scale = screenScale; }
                ribbonGeometry.put(lastLinkWorldPath, contours);
            }
            canvas.save();
            canvas.concat(worldToScreen);
            for (Path[] contour : contours) drawRibbonStrands(canvas,contour[0],contour[1],color,selected,1f);
            canvas.restore();
            return;
        }
        PathMeasure measure = new PathMeasure(path, false);
        do {
        float length = measure.getLength();
        if (length <= 0f) continue;
        float amplitude = 2.1f * scale;
        float wavelength = 38f * scale;
        if ("sibling".equals(link.type)) {
            buildDashedRibbon(ribbonPathA, measure, length, amplitude, 0f);
            buildDashedRibbon(ribbonPathB, measure, length, amplitude, (float) Math.PI);
        } else {
            buildRibbonStrand(ribbonPathA, measure, length, amplitude, wavelength, 0f);
            buildRibbonStrand(ribbonPathB, measure, length, amplitude, wavelength, (float) Math.PI);
        }

        drawRibbonStrands(canvas,ribbonPathA,ribbonPathB,color,selected,scale);
        } while (measure.nextContour());
    }

    private void drawRibbonStrands(Canvas canvas,Path a,Path b,int color,boolean selected,float strokeScale) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setPathEffect(null);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(2.2f * strokeScale);
        paint.setColor(withAlpha(blend(color, Color.BLACK, 0.18f), AppThemePalette.isDark() ? 92 : 78));
        canvas.drawPath(a, paint);
        canvas.drawPath(b, paint);

        paint.setStrokeWidth(1.25f * strokeScale);
        paint.setColor(withAlpha(blend(color, Color.WHITE, 0.06f), selected ? 238 : 218));
        canvas.drawPath(a, paint);
        paint.setColor(withAlpha(blend(color, Color.WHITE, AppThemePalette.isDark() ? 0.44f : 0.32f), selected ? 230 : 202));
        canvas.drawPath(b, paint);

        paint.setPathEffect(null);
    }

    private void drawFireLink(Canvas canvas, Path path, Relation link, int color, boolean selected) {
        float coreWidth = linkStrokeWidth(selected, false);
        DashPathEffect effect = linkPathEffect(link, false);
        float zoomWidth = Math.max(0.18f, Math.min(1f, scale));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setPathEffect(effect);

        paint.setStrokeWidth(coreWidth + Math.max(0.9f, 7.4f * zoomWidth));
        paint.setColor(Color.argb(AppThemePalette.isDark() ? 86 : 54, 245, 55, 18));
        canvas.drawPath(path, paint);

        paint.setStrokeWidth(coreWidth + Math.max(0.55f, 3.7f * zoomWidth));
        paint.setColor(Color.argb(AppThemePalette.isDark() ? 126 : 86, 255, 144, 31));
        canvas.drawPath(path, paint);

        paint.setStrokeWidth(coreWidth);
        paint.setColor(withAlpha(color, selected ? 245 : 222));
        canvas.drawPath(path, paint);

        paint.setStrokeWidth(Math.max(0.8f, 1.25f * scale));
        paint.setColor(Color.argb(selected ? 238 : 205, 255, 229, 132));
        canvas.drawPath(path, paint);
        paint.setPathEffect(null);

        PathMeasure measure = new PathMeasure(path, false);
        // Keep the glow on the connection itself. Detached flame slivers looked
        // like broken strokes, and animated flow must not fill sibling gaps.
        do {
            float length = measure.getLength();
            if (length > 0f && effect == null) drawFireFlow(canvas, measure, length, selected);
        } while (measure.nextContour());
        if (!exportRendering && effect == null) postInvalidateOnAnimation();
    }

    private void drawFireFlow(Canvas canvas, PathMeasure measure, float length, boolean selected) {
        float progress = exportRendering ? 0.35f : (SystemClock.uptimeMillis() % 1800L) / 1800f;
        float segment = Math.min(length * 0.26f, Math.max(30f, 92f * Math.max(0.28f, scale)));
        float cycle = length + segment;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setPathEffect(null);
        for (int i = 0; i < 2; i++) {
            float start = ((progress + i * 0.5f) % 1f) * cycle - segment;
            drawFireFlowSegment(canvas, measure, length, start, Math.min(length, start + segment), selected, i);
            if (start < 0f) {
                drawFireFlowSegment(canvas, measure, length, length + start, length, selected, i);
            } else if (start + segment > length) {
                drawFireFlowSegment(canvas, measure, length, 0f, start + segment - length, selected, i);
            }
        }
    }

    private void drawFireFlowSegment(
        Canvas canvas,
        PathMeasure measure,
        float length,
        float start,
        float end,
        boolean selected,
        int index
    ) {
        if (end <= start || start >= length || end <= 0f) return;
        fireFlowPath.reset();
        if (!measure.getSegment(Math.max(0f, start), Math.min(length, end), fireFlowPath, true)) return;
        paint.setStrokeWidth(Math.max(0.85f, 1.55f * scale));
        paint.setColor(Color.argb(selected ? 226 : 188, 255, index == 0 ? 239 : 183, 104));
        canvas.drawPath(fireFlowPath, paint);
    }

    private void buildRibbonStrand(
        Path target,
        PathMeasure measure,
        float length,
        float amplitude,
        float wavelength,
        float phase
    ) {
        target.reset();
        float[] point = ribbonPoint;
        float[] tangent = ribbonTangent;
        float step = Math.max(3.5f, wavelength / 9f);
        boolean started = false;
        for (float distance = 0f; distance <= length; distance += step) {
            if (addRibbonPoint(target, measure, point, tangent, distance, amplitude, wavelength, phase, started)) {
                started = true;
            }
        }
        addRibbonPoint(target, measure, point, tangent, length, amplitude, wavelength, phase, started);
    }

    private void buildDashedRibbon(Path target, PathMeasure measure, float length, float amplitude, float phase) {
        target.reset();
        float[] point = ribbonPoint, tangent = ribbonTangent;
        float dash = 18f * scale, gap = 10f * scale;
        for (float start = 0; start < length; start += dash + gap) {
            float end = Math.min(length, start + dash);
            float wavelength = 2f * (end - start);
            float localPhase = phase - start / wavelength * (float) Math.PI * 2f;
            float height = amplitude * Math.min(1f, (end - start) / dash);
            boolean started = false;
            for (float d = start; d < end; d += 1.5f * scale) {
                started |= addRibbonPoint(target, measure, point, tangent, d, height, wavelength, localPhase, started);
            }
            addRibbonPoint(target, measure, point, tangent, end, height, wavelength, localPhase, started);
        }
    }

    private boolean addRibbonPoint(
        Path target,
        PathMeasure measure,
        float[] point,
        float[] tangent,
        float distance,
        float amplitude,
        float wavelength,
        float phase,
        boolean started
    ) {
        if (!measure.getPosTan(distance, point, tangent)) return false;
        float len = (float) Math.hypot(tangent[0], tangent[1]);
        if (len <= 0f) return false;
        float wave = (float) Math.sin(distance / wavelength * Math.PI * 2f + phase);
        float nx = -tangent[1] / len;
        float ny = tangent[0] / len;
        float x = point[0] + nx * amplitude * wave;
        float y = point[1] + ny * amplitude * wave;
        if (started) target.lineTo(x, y);
        else target.moveTo(x, y);
        return true;
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
            int alpha = Math.max(28, Math.min(72, (int) (58 * scale)));
            int color = AppThemePalette.isDark()
                ? Color.rgb(88, 103, 110)
                : Color.rgb(177, 191, 198);
            paint.setColor(withAlpha(color, alpha));
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
        boolean drewFire = false;
        Iterable<Person> people = dragPersonIds.isEmpty()
            ? visiblePeople(visible)
            : state.people.values();
        effectPeopleScratch.clear();
        long effectPeopleSignature=0xcbf29ce484222325L;
        int effectPeopleCount=0;
        for (Person person : people) {
            if (!matches(person)) continue;
            tmp.set(person.x - 64f, person.y - 64f, person.x + cardWidthWorld() + 64f, person.y + cardHeightWorld() + 64f);
            if (!RectF.intersects(visible, tmp)) continue;
            effectPeopleScratch.add(person);
            int seed=person.id==null?0:person.id.hashCode();
            effectPeopleSignature += ((long)seed*0x9e3779b97f4a7c15L);
            effectPeopleSignature ^= Long.rotateLeft(seed*0x85ebca6bL, seed&31);
            effectPeopleCount++;
        }
        effectPeopleSignature += ((long)effectPeopleCount*0xc2b2ae3d27d4eb4fL);
        rasterCardsEnabled=!exportMode &&canvas.isHardwareAccelerated() &&effectPeopleScratch.size()>=24
            &&!(BuildConfig.DEBUG &&debugDisableCardRaster);
        cardRasterStyleSignature=cardRasterStyleSignature();
        boolean fireShaderCandidate=!exportMode
            &&("card_edge_fire".equals(cardEdgeStyle)||"card_theme_fire".equals(cardStyle));
        // A shader pass is efficient for a few cards, but three full card
        // passes across a 500-person tree overload the RenderThread. The
        // complete animated atlas keeps every particle and path while making
        // the large-scene frame cost independent of the card count.
        boolean largeFireScene=effectPeopleScratch.size()>=96;
        gpuFireForFrame=fireShaderCandidate&&!largeFireScene
            &&gpuFireCardEffect.prepare(canvas,cardWidthWorld(),cardHeightWorld());
        animatedCardAtlas.begin(canvas,effectPeopleScratch,cardWidthWorld(),cardHeightWorld(),scale,fireFrameSeconds,
            "card_edge_fire".equals(cardEdgeStyle)&&!gpuFireForFrame,"card_edge_smoke".equals(cardEdgeStyle),
            "card_theme_fire".equals(cardStyle)&&!gpuFireForFrame,effectPeopleSignature);
        for(Person person:effectPeopleScratch) {
            drawCard(canvas, person, exportMode);
            drewFire |= "card_edge_fire".equals(cardEdgeStyle)
                || "card_edge_smoke".equals(cardEdgeStyle)
                || "card_theme_fire".equals(cardStyle);
        }
        if(!exportMode &&cardRasterCache.needsAnotherFrame() &&isShown() &&getWindowVisibility()==VISIBLE)postInvalidateOnAnimation();
        if (drewFire && !exportMode && isShown() && getWindowVisibility() == VISIBLE
                && (gpuFireForFrame || !animatedCardAtlas.hasActiveFrame()))postInvalidateOnAnimation();
    }

    private void drawCard(Canvas canvas, Person person, boolean exportMode) {
        boolean animated="card_edge_fire".equals(cardEdgeStyle)||"card_edge_smoke".equals(cardEdgeStyle)
            ||"card_theme_fire".equals(cardStyle);
        if(android.os.Build.VERSION.SDK_INT<29||exportMode||recordingCardArtwork||animated
            ||!canvas.isHardwareAccelerated()) {
            drawCardAtScale(canvas,person,exportMode);return;
        }
        // Record and replay vector commands in card/world coordinates. RenderNode
        // has no bitmap layer: GPU rasterizes text and paths at the live matrix.
        // The same commands are used at rest and during pinch, with no LOD switch.
        float oldScale=scale,oldX=offsetX,oldY=offsetY,oldViewport=cardViewportScale;
        float left=sx(person.x),top=sy(person.y);
        int save=canvas.save();canvas.translate(left,top);canvas.scale(oldScale,oldScale);
        try {
            cardViewportScale=oldScale;scale=1;offsetX=-person.x;offsetY=-person.y;
            drawCardAtScale(canvas,person,exportMode);
        } finally {
            scale=oldScale;offsetX=oldX;offsetY=oldY;cardViewportScale=oldViewport;canvas.restoreToCount(save);
        }
    }

    private void drawCardAtScale(Canvas canvas, Person person, boolean exportMode) {
        CardMeta cardMeta=cardMeta(person);
        boolean animated=!rasterizingStaticCard && ("card_edge_fire".equals(cardEdgeStyle)||"card_edge_smoke".equals(cardEdgeStyle)||"card_theme_fire".equals(cardStyle));
        long now=SystemClock.uptimeMillis();
        boolean transientHighlight=(now<highlightedPathUntil &&highlightedPathIds.contains(person.id))
            ||(now<focusHighlightUntil &&person.id.equals(focusHighlightId));
        if(!rasterizingStaticCard&&!animated&&!transientHighlight&&drawCardRaster(canvas,person,cardMeta,0,exportMode))return;
        float left = sx(person.x);
        float top = sy(person.y);
        float width = cardWidthWorld() * scale;
        float height = cardHeightWorld() * scale;
        boolean fire = "card_edge_fire".equals(cardEdgeStyle);
        boolean smoke = "card_edge_smoke".equals(cardEdgeStyle);
        boolean botanical = "card_edge_botanical".equals(cardEdgeStyle);
        boolean decorated = botanical || (!rasterizingStaticCard && (fire || smoke));
        float radius = (decorated || "card_theme_smoke".equals(cardStyle) ? 10f : 8f) * scale;
        int fireSeed = person.id == null ? 0 : person.id.hashCode();
        if(decorated)animatedCardAtlas.prepare(fireSeed,fireFrameSeconds);
        boolean atlas = !rasterizingStaticCard && animatedCardAtlas.contains(fireSeed);
        if (decorated) {
            canvas.save(); canvas.translate(left, top); canvas.scale(scale, scale);
            if (atlas) animatedCardAtlas.behind(canvas,fireSeed);
            if (fire && !atlas) {
                if(gpuFireForFrame)gpuFireCardEffect.behind(canvas,cardWidthWorld(),cardHeightWorld(),fireSeed,fireFrameSeconds,scale);
                else fireCardEffect.behind(canvas, cardWidthWorld(), cardHeightWorld(), fireSeed, fireFrameSeconds);
            }
            if (smoke && !atlas) smokeCardEffect.behind(canvas, cardWidthWorld(), cardHeightWorld(), fireSeed, fireFrameSeconds);
            if (botanical) {
                botanicalEffect.behind(
                    canvas,
                    cardWidthWorld(),
                    cardHeightWorld(),
                    fireSeed,
                    scale);
            }
            canvas.restore();
        }

        // Animated edges still need the moving effect behind and in front of
        // the card, while the complete static card can be reused as one tile.
        if(animated&&!transientHighlight&&drawCardRaster(canvas,person,cardMeta,0,exportMode)) {
            canvas.save(); canvas.translate(left, top); canvas.scale(scale, scale);
            if (atlas) animatedCardAtlas.front(canvas,fireSeed);
            if (fire && !atlas) {
                if(gpuFireForFrame)gpuFireCardEffect.rim(canvas,cardWidthWorld(),cardHeightWorld(),fireSeed,fireFrameSeconds,scale);
                else fireCardEffect.rim(canvas,cardWidthWorld(),cardHeightWorld(),fireSeed,fireFrameSeconds);
            }
            if (smoke && !atlas) smokeCardEffect.rim(canvas,cardWidthWorld(),cardHeightWorld());
            if (botanical) botanicalEffect.rim(canvas,cardWidthWorld(),cardHeightWorld());
            canvas.restore();
            return;
        }

        paint.setStyle(Paint.Style.FILL);
        {
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
        int cardColor = styledCardColor(adjustForSelection(person), person);
        activeCardColor = cardColor;
        paint.setShader(cardFillGradient(left, top, left + width, top + height, cardColor));
        paint.setColor(cardColor);
        canvas.drawRoundRect(left, top, left + width, top + height, radius, radius, paint);
        paint.setShader(null);
        drawCardStyleOverlay(canvas, left, top, width, height, radius, person.color,
            person.id == null ? 0 : person.id.hashCode());
        if (decorated || atlas) {
            canvas.save(); canvas.translate(left, top); canvas.scale(scale, scale);
            if (atlas) animatedCardAtlas.front(canvas,fireSeed);
            if (fire && !atlas) {
                if(gpuFireForFrame)gpuFireCardEffect.rim(canvas,cardWidthWorld(),cardHeightWorld(),fireSeed,fireFrameSeconds,scale);
                else fireCardEffect.rim(canvas, cardWidthWorld(), cardHeightWorld(), fireSeed, fireFrameSeconds);
            }
            if (smoke && !atlas) smokeCardEffect.rim(canvas, cardWidthWorld(), cardHeightWorld());
            if (botanical) botanicalEffect.rim(canvas, cardWidthWorld(), cardHeightWorld());
            canvas.restore();
        }
        boolean selected = person.id.equals(state.selectedId);
        boolean multiSelected = selectedIds.contains(person.id);
        boolean lineStart = person.id.equals(pendingLineFromId);
        boolean onHighlightedPath = highlightedPathUntil > SystemClock.uptimeMillis()
            && highlightedPathIds.contains(person.id);
        if (onHighlightedPath) {
            float pathHalo = Math.max(4f, 7f * scale);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(3.2f, 6.5f * scale));
            paint.setColor(withAlpha(AppThemePalette.blue(), 225));
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
            paint.setColor(withAlpha(AppThemePalette.primaryBright(), 215));
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
            paint.setColor(withAlpha(AppThemePalette.secondaryBright(), 188));
            canvas.drawRoundRect(left - 4f * scale, top - 4f * scale, left + width + 4f * scale, top + height + 4f * scale, radius + 5f * scale, radius + 5f * scale, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(selected || multiSelected
            ? Math.max(2.6f, 3.8f * scale)
            : (lineStart || person.pinned ? 3.2f : 2f) * scale);
        paint.setColor(lineStart
            ? AppThemePalette.text(Color.rgb(197, 83, 75))
            : multiSelected
                ? AppThemePalette.secondaryBright()
                : selected
                    ? AppThemePalette.primary()
                    : person.pinned
                        ? AppThemePalette.text(Color.rgb(31, 94, 89))
                        : cardNeonStroke(person.color, 220));
        if (!decorated || selected || multiSelected || lineStart || person.pinned)
            canvas.drawRoundRect(left, top, left + width, top + height, radius, radius, paint);
        if (person.id.equals(focusHighlightId)) {
            long remaining = focusHighlightUntil - SystemClock.uptimeMillis();
            if (remaining > 0L) {
                float phase = remaining / 1800f;
                int alpha = 90 + Math.round(110f * (1f - phase));
                float halo = (7f + 10f * (1f - phase)) * scale;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(3.5f, 5.5f * scale));
                paint.setColor(withAlpha(AppThemePalette.primaryBright(), Math.min(220, alpha)));
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
        if (multiSelected) {
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
            Paint.FontMetrics checkFm = fontMetricsScratch;textPaint.getFontMetrics(checkFm);
            canvas.drawText("✓", markerLeft + marker / 2f, markerTop + marker / 2f - (checkFm.ascent + checkFm.descent) / 2f, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
        }

        if(drawCardRaster(canvas,person,cardMeta,1,exportMode))return;
        drawCardContents(canvas,person,cardMeta,left,top,width,height,exportMode);
    }

    private void drawCardContents(Canvas canvas,Person person,CardMeta cardMeta,float left,float top,float width,float height,boolean exportMode) {
        float pad = (compactCards ? 10f : 14f) * scale;
        float textLeft = left + pad;
        float textMaxWidth = width - pad * 2;

        float textScale = cardTextScale();

        {
            float avatar = (compactCards ? 56f : 80f) * scale;
            float cx = left + pad + avatar / 2f;
            float cy = top + pad + avatar / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(cardNeonStroke(person.color, 58));
            canvas.drawCircle(cx, cy, avatar / 2f, paint);
            Bitmap photo = photoBitmap(person, exportMode);
            if (photo != null) {
                RectF avatarRect = new RectF(cx - avatar / 2f, cy - avatar / 2f, cx + avatar / 2f, cy + avatar / 2f);
                clipPath.reset();
                clipPath.addCircle(cx, cy, avatar / 2f, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(clipPath);
                drawPhotoCenterCrop(canvas, photo, avatarRect, person);
                canvas.restore();
                // The export cache owns this bitmap across tiles.
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f * scale);
            paint.setColor(photo == null
                ? blend(cardInitialsTextColor(), person.color, 0.25f)
                : cardNeonStroke(person.color, 224));
            canvas.drawCircle(cx, cy, avatar / 2f, paint);
            if (photo == null) {
                paint.setStrokeWidth(1.1f * scale);
                paint.setColor(withAlpha(cardInitialsTextColor(), 100));
                canvas.drawCircle(cx, cy, avatar / 2f - 4.5f * scale, paint);
            }
            Paint.FontMetrics fm;
            if (photo == null) {
                paint.setStyle(Paint.Style.FILL);
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setColor(cardInitialsTextColor());
                textPaint.setFakeBoldText(true);
                textPaint.setTypeface(uiBold);
                textPaint.setTextSize(18f * textScale);
                fm = fontMetricsScratch;textPaint.getFontMetrics(fm);
                canvas.drawText(
                    cardMeta.initials,
                    cx,
                    cy - (fm.ascent + fm.descent) / 2f,
                    textPaint);
            }
            textPaint.setTextAlign(Paint.Align.LEFT);
            textLeft = left + pad + avatar + (compactCards ? 9f : 12f) * scale;
            textMaxWidth = Math.max(1f, width - (textLeft - left) - pad);

            if (!exportMode) {
                float menu = (compactCards ? 23f : 28f) * scale;
                float menuLeft = left + width - 7f * scale - menu;
                float menuTop = top + 7f * scale;
                paint.setColor(Color.argb(76, 255, 255, 255));
                canvas.drawRoundRect(menuLeft, menuTop, menuLeft + menu, menuTop + menu, 5f * scale, 5f * scale, paint);
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setColor(cardMenuTextColor());
                textPaint.setTextSize(17f * textScale);
                fm = fontMetricsScratch;textPaint.getFontMetrics(fm);
                canvas.drawText("⋮", menuLeft + menu / 2f, menuTop + menu / 2f - (fm.ascent + fm.descent) / 2f, textPaint);
                textPaint.setTextAlign(Paint.Align.LEFT);
            }
        }

        textPaint.setColor(cardPrimaryTextColor());
        textPaint.setFakeBoldText(true);
        textPaint.setTypeface(uiBold);
        textPaint.setTextSize((compactCards ? 15.5f : 18.5f) * textScale);
        drawNameLines(
            canvas,
            cardMeta.displayName.isEmpty()
                ? AppLanguage.text(getContext(), "Без имени")
                : cardMeta.displayName,
            textLeft,
            top + pad + textPaint.getTextSize(),
            textMaxWidth,
            textScale);

        if (!hideDetails) {
            textPaint.setFakeBoldText(false);
            textPaint.setTypeface(uiRegular);
            textPaint.setColor(cardSecondaryTextColor());
            textPaint.setTextSize((compactCards ? 13f : 15f) * textScale);
            CardMeta meta = cardMeta;
            String date = meta.date;
            String place = meta.place;
            float metaLeft = left + pad;
            float qualityReserve = !exportMode && (state.showLifeStatusBadge
                || (state.showQualityBadge && qualityReports.containsKey(person.id)))
                ? 28f * scale
                : 0f;
            float metaWidth = Math.max(1f, width - pad * 2f - qualityReserve);
            float bottomBaseline = top + height - pad - 2f * scale;
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
        if (!exportMode) {
            drawQualityIndicator(canvas, person, left, top, width, height);
            drawLifeStatusIndicator(canvas, person, left, top, width, height);
        }
    }

    private boolean drawCardRaster(Canvas canvas,Person person,CardMeta meta,int layer,boolean exportMode) {
        if(recordingCardArtwork ||!rasterCardsEnabled ||exportMode ||!canvas.isHardwareAccelerated())return false;
        // Animated artwork must remain live; cache only its static contents.
        if(layer==0 &&("card_edge_fire".equals(cardEdgeStyle)||"card_edge_smoke".equals(cardEdgeStyle)
            ||"card_theme_fire".equals(cardStyle)))return false;
        long signature=mixFamilySignature(cardRasterStyleSignature,meta.signature);
        signature=mixFamilySignature(signature,System.identityHashCode(meta));
        signature=mixFamilySignature(signature,person.color);
        signature=mixFamilySignature(signature,(person.pinned?1:0)|(person.id.equals(state.selectedId)?2:0)
            |(selectedIds.contains(person.id)?4:0)|(person.id.equals(pendingLineFromId)?8:0));
        signature=mixFamilySignature(signature,Float.floatToIntBits(person.avatarScale));
        signature=mixFamilySignature(signature,Float.floatToIntBits(person.avatarOffsetX));
        signature=mixFamilySignature(signature,Float.floatToIntBits(person.avatarOffsetY));
        signature=mixFamilySignature(signature,System.identityHashCode(photoBitmap(person,false)));
        signature=mixFamilySignature(signature,stringHash(person.lifeStatus));
        signature=mixFamilySignature(signature,stringHash(person.born));signature=mixFamilySignature(signature,stringHash(person.died));
        TreeQualityAnalyzer.PersonReport report=qualityReports.get(person.id);
        signature=mixFamilySignature(signature,report==null?-1:report.topSeverity());
        return cardRasterCache.draw(canvas,person,layer,signature,cardWidthWorld(),cardHeightWorld(),scale,sx(person.x),sy(person.y),
            !zoomAnimationRunning && (cameraAnimator == null || !cameraAnimator.isRunning())
                && !scaleDetector.isInProgress(),cardRasterRenderer);
    }

    private long cardRasterStyleSignature() {
        long signature=0xcbf29ce484222325L;
        signature=mixFamilySignature(signature,stringHash(theme));signature=mixFamilySignature(signature,stringHash(cardStyle));
        signature=mixFamilySignature(signature,stringHash(cardEdgeStyle));signature=mixFamilySignature(signature,Float.floatToIntBits(fontScale));
        signature=mixFamilySignature(signature,(compactCards?1:0)|(hideDetails?2:0)|(state.showQualityBadge?4:0)|(state.showLifeStatusBadge?8:0));
        if(state.showLifeStatusBadge &&state.lifeStatusAppearance!=null) {
            LifeStatusAppearance appearance=state.lifeStatusAppearance;
            signature=mixFamilySignature(signature,stringHash(appearance.livingIcon));signature=mixFamilySignature(signature,stringHash(appearance.deceasedIcon));
            signature=mixFamilySignature(signature,stringHash(appearance.unknownIcon));signature=mixFamilySignature(signature,stringHash(appearance.livingColor));
            signature=mixFamilySignature(signature,stringHash(appearance.deceasedColor));signature=mixFamilySignature(signature,stringHash(appearance.unknownColor));
            signature=mixFamilySignature(signature,stringHash(appearance.style));
        }
        return signature;
    }

    private void renderCardRaster(Canvas canvas,Person person,int layer) {
        float oldX=offsetX,oldY=offsetY;
        offsetX=-person.x*scale;offsetY=-person.y*scale;
        boolean oldStatic=rasterizingStaticCard,oldRecording=recordingCardArtwork;
        try {
            recordingCardArtwork=true;
            rasterizingStaticCard=layer==0;
            if(layer==0)drawCard(canvas,person,false);
            else drawCardContents(canvas,person,cardMeta(person),0,0,cardWidthWorld()*scale,cardHeightWorld()*scale,false);
        } finally {offsetX=oldX;offsetY=oldY;rasterizingStaticCard=oldStatic;recordingCardArtwork=oldRecording;}
    }

    private void drawLifeStatusIndicator(Canvas canvas, Person person, float left, float top, float width, float height) {
        if (!state.showLifeStatusBadge) return;
        float diameter = Math.max(1f, 22f * scale);
        float cx = left + width - 10f * scale - diameter / 2f;
        boolean qualityVisible = state.showQualityBadge && qualityReports.containsKey(person.id);
        float cy = LifeStatusBadge.centerY(top, height, scale, qualityVisible);
        lifeStatusBadge.draw(canvas, PersonLifeStatus.resolve(person), state.lifeStatusAppearance, cx, cy, diameter, scale);
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
        if (report == null || !state.showQualityBadge) return;
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
        paint.setStrokeWidth(size * 0.12f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(textPaint.getColor());
        canvas.drawRoundRect(left,top+size*.16f,left+size,top+size,size*.15f,size*.15f,paint);
        canvas.drawLine(left + size * 0.24f, top, left + size * 0.24f, top + size * 0.34f, paint);
        canvas.drawLine(left + size * 0.76f, top, left + size * 0.76f, top + size * 0.34f, paint);
        canvas.drawLine(left, top + size * 0.43f, left + size, top + size * 0.43f, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawLocationMetaIcon(Canvas canvas, float left, float baseline, float size) {
        float cx = left + size / 2f;
        float top = baseline - size * 0.96f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * 0.12f);
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
        if (selectedIds.contains(person.id)) return blend(person.color, AppThemePalette.secondaryBright(), 0.16f);
        if (!person.id.equals(state.selectedId)) return person.color;
        return blend(blend(person.color, Color.WHITE, 0.16f), AppThemePalette.primaryBright(), 0.18f);
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
        signature = 31 * signature + stringHash(person.surname);
        signature = 31 * signature + stringHash(person.givenName);
        signature = 31 * signature + stringHash(person.patronymic);
        signature = 31 * signature + stringHash(person.maidenName);
        signature = 31 * signature + stringHash(person.surnameDisplay);
        signature = 31 * signature + (person.namePartsSet ? 1 : 0);
        signature = 31 * signature + (person.maidenNameEnabled ? 1 : 0);
        signature = 31 * signature + (state.useMaidenNames ? 1 : 0);
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
        String search = (PersonNames.search(person)
            + " " + safe(person.bornYear)
            + " " + safe(person.diedYear)
            + " " + place
            + " " + safe(person.notes)).toLowerCase(Locale.ROOT);
        CardMeta meta = new CardMeta(
            signature,
            PersonNames.display(person, state.useMaidenNames),
            initials(PersonNames.display(person, state.useMaidenNames)),
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
            cardMetaCache.clear();
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
                    Math.round((compactCards ? 56f : 80f) * Math.max(1f,
                        exportRendering ? exportPhotoScale : scale))));
            if (!mediaId.isEmpty() && mediaStore != null) {
                String exportKey = "export:" + mediaId + ":" + targetSize;
                Bitmap decoded = photoCache.get(exportKey);
                if (decoded == null) {
                    decoded = mediaStore.decodeBitmap(mediaId, targetSize);
                    if (decoded != null) photoCache.put(exportKey, decoded);
                }
                return decoded;
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
        int targetSize = 64;
        int requestedSize = Math.round((compactCards ? 56f : 80f) * (cardViewportScale>0?cardViewportScale:scale));
        while (targetSize < requestedSize && targetSize < 512) targetSize *= 2;
        ThumbnailLoader.Request request=thumbnailRequests.get(person);
        if(request==null) {request=new ThumbnailLoader.Request();thumbnailRequests.put(person,request);}
        return ThumbnailLoader.SHARED.get(this, thumbnailReady, mediaStore, mediaId, inlinePhoto, targetSize, 1,request);
    }

    @Override
    protected void onDetachedFromWindow() {
        stopContinuousZoom();
        syncZoomTarget();
        animatedCardAtlas.release();
        cardRasterCache.clear();gpuFireCardEffect.trim();
        thumbnailRequests.clear();
        detached = true; filterRevision++; filterWorker.shutdownNow();
        ThumbnailLoader.SHARED.cancel(this);
        mainHandler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    private void drawPhotoCenterCrop(Canvas canvas, Bitmap photo, RectF target, Person person) {
        tmpSrc.set(AvatarTransform.sourceRect(photo, person));
        canvas.drawBitmap(photo, tmpSrc, target, photoPaint);
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
        int fontKey = Math.round(fontScale * 100f);
        CachedName<String> cached = nameEllipsisCache.get(text);
        if (cached != null && cached.matches(widthKey,fontKey,compactCards)) return cached.value;
        float originalTextSize = textPaint.getTextSize();
        textPaint.setTextSize((compactCards ? 15.5f : 18.5f) * fontScale);
        String result = ellipsize(text, maxWidth / stableScale);
        textPaint.setTextSize(originalTextSize);
        if (nameEllipsisCache.size() > Math.max(256, state.people.size() * 4)) nameEllipsisCache.clear();
        nameEllipsisCache.put(text, new CachedName<>(widthKey,fontKey,compactCards,result));
        return result;
    }

    private void drawFittedSingleLine(Canvas canvas, String text, float x, float baseline, float maxWidth) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return;
        float original = textPaint.getTextSize();
        float worldTextSize=(compactCards?13f:15f)*fontScale;
        if(measuredMetaSize!=worldTextSize) { measuredMetaSize=worldTextSize;metaTextWidths.clear(); }
        Float cachedWidth=metaTextWidths.get(value);
        if(cachedWidth==null) {
            textPaint.setTextSize(worldTextSize);
            cachedWidth=Math.max(1f,textPaint.measureText(value));
            textPaint.setTextSize(original);
            if(metaTextWidths.size()>Math.max(256,state.people.size()*2))metaTextWidths.clear();
            metaTextWidths.put(value,cachedWidth);
        }
        float measured = cachedWidth*scale;
        if (measured > maxWidth && maxWidth > 0f) {
            textPaint.setTextSize(Math.max(1f, original * maxWidth / measured));
        }
        drawSingleLine(canvas, value, x, baseline, maxWidth);
        textPaint.setTextSize(original);
    }

    private float cardTextScale() {
        return scale * fontScale;
    }

    private String[] nameLines(String value, float maxWidth) {
        int widthKey = Math.round(maxWidth / Math.max(0.001f, scale));
        int fontKey = Math.round(fontScale * 100f);
        CachedName<String[]> cached = nameLinesCache.get(value);
        if (cached != null && cached.matches(widthKey,fontKey,compactCards)) return cached.value;
        float originalTextSize = textPaint.getTextSize();
        textPaint.setTextSize((compactCards ? 15.5f : 18.5f) * fontScale);
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
        if (nameLinesCache.size() > Math.max(256, state.people.size() * 4)) nameLinesCache.clear();
        nameLinesCache.put(value,new CachedName<>(widthKey,fontKey,compactCards,result));
        return result;
    }

    private static final class CachedName<T> {
        final int width,font;final boolean compact;final T value;
        CachedName(int width,int font,boolean compact,T value){this.width=width;this.font=font;this.compact=compact;this.value=value;}
        boolean matches(int width,int font,boolean compact){return this.width==width&&this.font==font&&this.compact==compact;}
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
        lastLinkWorldPath = geometry.worldPath;
        // Botanical links draw their cached decoration in world coordinates
        // under one shared canvas transform.  Do not transform the same path
        // into screen coordinates first; that matrix work used to happen on
        // every visible botanical link during pinch and pan.
        if ("link_style_botanical".equals(linkStyle)) return geometry.worldPath;
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
                // A new pan or pinch starts from the currently displayed
                // transform.  Cancel a previous settle animation so it cannot
                // fight the user's finger during the next gesture.
                syncZoomTarget();
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                gestureStartOffsetX = offsetX;
                gestureStartOffsetY = offsetY;
                moved = false;
                blockedEditNotified = false;
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
                } else if (editLocked && moved && !dragPersonId.isEmpty() && !blockedEditNotified) {
                    blockedEditNotified = true;
                    if (listener != null) listener.onEditBlocked();
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
            paint.setColor(withAlpha(AppThemePalette.secondary(), 34));
            paint.setPathEffect(null);
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.6f);
            paint.setColor(AppThemePalette.secondary());
            paint.setPathEffect(new DashPathEffect(new float[] {12f, 8f}, 0f));
            canvas.drawPath(path, paint);
        } else if (!selectionRect.isEmpty()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(withAlpha(AppThemePalette.secondary(), 28));
            paint.setPathEffect(null);
            canvas.drawRect(sx(selectionRect.left), sy(selectionRect.top), sx(selectionRect.right), sy(selectionRect.bottom), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.6f);
            paint.setColor(AppThemePalette.secondary());
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
        Set<String> routedVisibleIds = matchingPersonIds();
        FamilyLineRouter.Plan familyPlan = "centered".equals(familyLineMode)
            ? familyLinePlan(routedVisibleIds)
            : FamilyLineRouter.Plan.empty();
        for (FamilyLineRouter.FamilyRoute route : familyPlan.routes) {
            Relation representative = familyPlan.representative(route);
            if (representative == null) continue;
            float distance = distanceToPath(
                screenPathForHitTest(familyRoutePath(route)),
                screenX,
                screenY);
            if (distance < bestDistance && distance <= threshold) {
                bestDistance = distance;
                best = representative;
            }
        }
        for (Relation link : state.links) {
            if (familyPlan.consumes(link)) continue;
            Person from = state.people.get(link.from);
            Person to = state.people.get(link.to);
            if (from == null || to == null || !matches(from) || !matches(to)) continue;
            Path path = screenPathForHitTest(linkPath(link, from, to));
            float distance = distanceToPath(path, screenX, screenY);
            if (distance < bestDistance && distance <= threshold) {
                bestDistance = distance;
                best = link;
            }
        }
        return best;
    }

    private Path screenPathForHitTest(Path path) {
        if (!"link_style_botanical".equals(linkStyle)) return path;
        Path screenPath = new Path();
        path.transform(worldToScreen, screenPath);
        return screenPath;
    }

    private static float distanceToPath(Path path, float x, float y) {
        PathMeasure measure = new PathMeasure(path, false);
        float[] point = new float[2];
        float best = Float.MAX_VALUE;
        // Centered family routes contain separate contours for the parents,
        // trunk and child branches. Every visible contour must be tappable.
        do {
            float length = measure.getLength();
            if (length <= 0f) continue;
            float step = Math.max(6f, Math.min(28f, length / 40f));
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
        } while (measure.nextContour());
        return best == Float.MAX_VALUE ? Float.MAX_VALUE : (float) Math.sqrt(best);
    }

    private void startCardDrag(Person hit) {
        if (state == null || hit == null || editLocked) return;
        spatialDirty = true;
        familyLinePlanDirty = true;
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
        coordinateRevision++;
        familyLinePlanDirty = true;
        if (state == null || dragPersonIds.isEmpty()) return;
        for (String id : dragPersonIds) {
            PointF start = dragStartPositions.get(id);
            Person person = state.people.get(id);
            if (start == null || person == null || person.pinned) continue;
            person.x = start.x + dx;
            person.y = start.y + dy;
        }
    }

    private void finishActiveCardDrag() {
        finishCardDrag(true);
    }

    private void finishCardDrag(boolean commit) {
        coordinateRevision++;
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
        familyLinePlanDirty = true;
        invalidate();
    }

    private boolean matches(Person person) {
        return person != null && matchingPersonIds().contains(person.id);
    }

    private Set<String> matchingPersonIds() {
        if (matchingPersonIds != null) return matchingPersonIds;
        Set<String> result = new HashSet<>();
        if (state != null) {
            for (Person person : state.people.values()) {
                if (person != null && matchesCurrentFilter(person)) result.add(person.id);
            }
        }
        matchingPersonIds = result;
        return result;
    }

    private boolean matchesCurrentFilter(Person person) {
        if (!branchAllows(person.id)) return false;
        if (filter.isEmpty()) return true;
        return filterMatchIds == null || filterMatchIds.contains(person.id);
    }

    private boolean hasSearchPhoto(Person person) {
        if (person == null) return false;
        if (person.photo != null && !person.photo.trim().isEmpty()) return true;
        return person.photoMediaId != null
            && !person.photoMediaId.trim().isEmpty()
            && mediaStore != null
            && mediaStore.exists(person.photoMediaId);
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
        else if ("near".equals(branchMode)) visibleBranchIds = state.nearOf(anchor, nearGenerations);
    }

    private List<Person> visiblePeople(RectF visible) {
        rebuildSpatialIndex();
        int minCellX = cell(visible.left - cardWidthWorld() - 64f);
        int maxCellX = cell(visible.right + 64f);
        int minCellY = cell(visible.top - cardHeightWorld() - 64f);
        int maxCellY = cell(visible.bottom + 64f);
        if(visibleCellsCached &&visibleMinX==minCellX &&visibleMaxX==maxCellX &&visibleMinY==minCellY &&visibleMaxY==maxCellY)return visiblePeopleScratch;
        visibleCellsCached=true;visibleMinX=minCellX;visibleMaxX=maxCellX;visibleMinY=minCellY;visibleMaxY=maxCellY;
        visiblePeopleScratch.clear();
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
        visibleCellsCached=false;
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
        if (!Float.isFinite(factor) || factor <= 0f) return;
        if (cameraAnimator != null) { cameraAnimator.cancel(); cameraAnimator = null; }
        float beforeX = wx(focusX);
        float beforeY = wy(focusY);
        if(scaleDetector.isInProgress() || !PanelTransitions.enabled(this)) {
            scale=clamp(scale*factor,.05f,3f);
            offsetX=focusX-beforeX*scale;offsetY=focusY-beforeY*scale;
            syncZoomTarget();notifyZoomChanged();postInvalidateOnAnimation();return;
        }
        // Button zoom keeps its short transition and accumulates repeated clicks.
        float baseScale = zoomAnimationRunning ? zoomTargetScale : scale;
        zoomTargetScale = clamp(baseScale * factor, 0.05f, 3.0f);
        zoomTargetOffsetX = focusX - beforeX * zoomTargetScale;
        zoomTargetOffsetY = focusY - beforeY * zoomTargetScale;
        if (!zoomAnimationRunning) {
            zoomAnimationRunning = true;
            zoomFrameTime = SystemClock.uptimeMillis();
            postOnAnimation(zoomAnimation);
        }
    }

    private void advanceZoomAnimation() {
        long now = SystemClock.uptimeMillis();
        float interpolation = 1f - (float) Math.exp(-Math.min(64L, now - zoomFrameTime) / 65f);
        zoomFrameTime = now;
        scale += (zoomTargetScale - scale) * interpolation;
        offsetX += (zoomTargetOffsetX - offsetX) * interpolation;
        offsetY += (zoomTargetOffsetY - offsetY) * interpolation;
        boolean settled = Math.abs(zoomTargetScale - scale) < .0006f
            && Math.abs(zoomTargetOffsetX - offsetX) < .08f
            && Math.abs(zoomTargetOffsetY - offsetY) < .08f;
        if (settled) {
            scale = zoomTargetScale;
            offsetX = zoomTargetOffsetX;
            offsetY = zoomTargetOffsetY;
            zoomAnimationRunning = false;
        } else {
            postOnAnimation(zoomAnimation);
        }
        notifyZoomChanged();
        invalidate();
    }

    private void syncZoomTarget() {
        if (cameraAnimator != null) { cameraAnimator.cancel(); cameraAnimator = null; }
        zoomTargetScale = scale;
        zoomTargetOffsetX = offsetX;
        zoomTargetOffsetY = offsetY;
        zoomAnimationRunning = false;
        removeCallbacks(zoomAnimation);
    }

    private void animateCamera(float targetScale, float targetX, float targetY) {
        stopContinuousZoom();
        syncZoomTarget();
        if (!isLaidOut() || !isShown() || !PanelTransitions.enabled(this)) {
            scale = targetScale; offsetX = targetX; offsetY = targetY;
            syncZoomTarget(); notifyZoomChanged(); invalidate();
            return;
        }
        final float fromScale = scale, fromX = offsetX, fromY = offsetY;
        cameraAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        cameraAnimator.setDuration(460L);
        cameraAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator(2f));
        cameraAnimator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            scale = fromScale + (targetScale - fromScale) * t;
            offsetX = fromX + (targetX - fromX) * t;
            offsetY = fromY + (targetY - fromY) * t;
            notifyZoomChanged(); invalidate();
        });
        cameraAnimator.start();
    }

    private void notifyZoomChanged() {
        notifyZoomChanged(scale);
    }

    private void notifyZoomChanged(float value) {
        if (listener != null && Float.isFinite(value)) listener.onZoomChanged(value);
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
        return TreeLayoutEngine.snap(value);
    }

    private float snapPersonY(float value) {
        return TreeLayoutEngine.snap(value);
    }

    private int cardNeonStroke(int color, int alpha) {
        if ("card_theme_smoke".equals(cardStyle)) return Color.argb(alpha, 174, 194, 207);
        if ("card_theme_botanical".equals(cardStyle))
            return Color.argb(Math.min(alpha, 220), 72, 126, 49);
        if ("card_theme_archive".equals(cardStyle)) {
            return withAlpha(Color.rgb(116, 91, 57), Math.min(alpha, 188));
        }
        if ("card_theme_midnight".equals(cardStyle)) {
            int mixed = blend(color, Color.rgb(105, 232, 218), 0.42f);
            return withAlpha(mixed, Math.min(alpha, 230));
        }
        if ("card_theme_glass".equals(cardStyle)) {
            int mixed = blend(color, Color.WHITE, 0.38f);
            return withAlpha(mixed, Math.min(alpha, 198));
        }
        if ("card_theme_fire".equals(cardStyle)) {
            int mixed = blend(color, Color.rgb(255, 127, 32), 0.62f);
            return withAlpha(mixed, Math.min(alpha, 235));
        }
        int mixed = blend(color, Color.WHITE, AppThemePalette.isDark() ? 0.18f : 0.06f);
        int cappedAlpha = AppThemePalette.isDark()
            ? Math.min(alpha, 214)
            : Math.min(alpha, 178);
        return withAlpha(mixed, cappedAlpha);
    }

    private Shader cardFillGradient(float left, float top, float right, float bottom, int color) {
        int key="card_theme_fire".equals(cardStyle)||"card_theme_smoke".equals(cardStyle)
            ||"card_theme_botanical".equals(cardStyle)?0:color;
        Shader shader=fillGradients.get(key);
        if (shader==null) {
            shader=createCardFillGradient(0,0,1,1,color);
            if (fillGradients.size()>=Math.max(512,state==null?512:state.people.size()*2+32)) fillGradients.clear();
            fillGradients.put(key,shader);
        }
        gradientMatrix.setScale(right-left,bottom-top);
        gradientMatrix.postTranslate(left,top);
        shader.setLocalMatrix(gradientMatrix);
        return shader;
    }

    private Shader createCardFillGradient(float left, float top, float right, float bottom, int color) {
        if ("card_theme_smoke".equals(cardStyle)) return new LinearGradient(left, top, right, bottom,
            new int[] {0xff45525b, 0xff20282f, 0xff343f48}, new float[] {0, .52f, 1}, Shader.TileMode.CLAMP);
        if ("card_theme_botanical".equals(cardStyle)) return new LinearGradient(
            left, top, right, bottom,
            new int[] {0xffeff8d7, 0xffcce9b1, 0xfff7f2cf},
            new float[] {0f, .56f, 1f}, Shader.TileMode.CLAMP);
        if ("card_theme_archive".equals(cardStyle)) {
            return new LinearGradient(
                left,
                top,
                right,
                bottom,
                blend(color, Color.WHITE, 0.18f),
                blend(color, Color.rgb(126, 88, 48), 0.15f),
                Shader.TileMode.CLAMP);
        }
        if ("card_theme_midnight".equals(cardStyle)) {
            return new LinearGradient(
                left,
                top,
                right,
                bottom,
                blend(color, Color.rgb(25, 39, 50), 0.42f),
                blend(color, Color.rgb(7, 16, 23), 0.64f),
                Shader.TileMode.CLAMP);
        }
        if ("card_theme_glass".equals(cardStyle)) {
            return new LinearGradient(
                left,
                top,
                right,
                bottom,
                withAlpha(blend(color, Color.WHITE, 0.58f), 188),
                withAlpha(blend(color, Color.rgb(210, 235, 241), 0.42f), 138),
                Shader.TileMode.CLAMP);
        }
        if ("card_theme_fire".equals(cardStyle)) {
            return new LinearGradient(
                left,
                top,
                right,
                bottom,
                new int[] {
                    Color.rgb(213, 80, 11),
                    Color.rgb(166, 53, 8),
                    Color.rgb(238, 98, 10)
                },
                new float[] {0f, 0.46f, 1f},
                Shader.TileMode.CLAMP);
        }
        int topColor = color;
        int bottomColor = blend(color, Color.BLACK, AppThemePalette.isDark() ? 0.18f : 0.12f);
        return new LinearGradient(left, top, right, bottom, topColor, bottomColor, Shader.TileMode.CLAMP);
    }

    private int styledCardColor(int color, Person person) {
        if ("card_theme_smoke".equals(cardStyle)) return Color.rgb(42, 52, 60);
        if ("card_theme_botanical".equals(cardStyle))
            return blend(color, Color.rgb(225, 241, 193), .78f);
        if ("card_theme_archive".equals(cardStyle)) {
            return blend(blend(color, Color.rgb(250, 238, 207), 0.72f), Color.WHITE, 0.08f);
        }
        if ("card_theme_midnight".equals(cardStyle)) {
            return blend(color, Color.rgb(11, 23, 31), AppThemePalette.isDark() ? 0.48f : 0.56f);
        }
        if ("card_theme_glass".equals(cardStyle)) {
            return blend(color, Color.WHITE, person.pinned ? 0.44f : 0.34f);
        }
        if ("card_theme_fire".equals(cardStyle)) {
            return blend(color, Color.rgb(118, 40, 10), AppThemePalette.isDark() ? 0.38f : 0.43f);
        }
        return color;
    }

    private Shader overlayGradient(int key,int kind,int accent,float left,float top,float width,float height) {
        Shader result=overlayGradients.get(key);
        if(result==null) {
            if(kind==0) result=new LinearGradient(0,0,.32f,0,
                withAlpha(blend(accent,Color.rgb(98,228,216),.16f),132),Color.TRANSPARENT,Shader.TileMode.CLAMP);
            else if(kind==1)result=new LinearGradient(0,0,0,.55f,
                Color.argb(122,255,255,255),Color.argb(28,255,255,255),Shader.TileMode.CLAMP);
            else result=new LinearGradient(0,.18f,1,.82f,Color.TRANSPARENT,Color.argb(76,255,255,255),Shader.TileMode.CLAMP);
            if(overlayGradients.size()>=Math.max(1024,state==null?1024:state.people.size()*2+32))overlayGradients.clear();
            overlayGradients.put(key,result);
        }
        gradientMatrix.setScale(width,height);gradientMatrix.postTranslate(left,top);result.setLocalMatrix(gradientMatrix);
        return result;
    }

    private void drawCardStyleOverlay(Canvas canvas, float left, float top, float width, float height, float radius, int accent, int seed) {
        if ("card_theme_archive".equals(cardStyle)) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, 1.2f * scale));
            paint.setColor(Color.argb(26, 118, 91, 54));
            float step = Math.max(5f, 15f * scale);
            for (float y = top + step; y < top + height - step / 2f; y += step) {
                canvas.drawLine(left + 10f * scale, y, left + width - 10f * scale, y, paint);
            }
        } else if ("card_theme_midnight".equals(cardStyle)) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(overlayGradient(accent,0,accent,left,top,width,height));
            canvas.drawRoundRect(left, top, left + width, top + height, radius, radius, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, 1.2f * scale));
            paint.setColor(Color.argb(48, 145, 240, 226));
            canvas.drawRoundRect(
                left + 3f * scale,
                top + 3f * scale,
                left + width - 3f * scale,
                top + height - 3f * scale,
                Math.max(1f, radius - 2f * scale),
                Math.max(1f, radius - 2f * scale),
                paint);
            paint.setStrokeWidth(Math.max(2f, 3.2f * scale));
            paint.setColor(withAlpha(blend(accent, Color.WHITE, 0.18f), 188));
            canvas.drawLine(left + 7f * scale, top + 12f * scale, left + 7f * scale, top + height - 12f * scale, paint);
        } else if ("card_theme_glass".equals(cardStyle)) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(overlayGradient(0,1,accent,left,top,width,height));
            canvas.drawRoundRect(left, top, left + width, top + height, radius, radius, paint);
            paint.setShader(null);
            paint.setShader(overlayGradient(1,2,accent,left,top,width,height));
            canvas.drawRoundRect(
                left + 8f * scale,
                top + 8f * scale,
                left + width - 8f * scale,
                top + height - 8f * scale,
                Math.max(1f, radius - 3f * scale),
                Math.max(1f, radius - 3f * scale),
                paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, 1.4f * scale));
            paint.setColor(Color.argb(148, 255, 255, 255));
            canvas.drawRoundRect(left + scale, top + scale, left + width - scale, top + height - scale, radius, radius, paint);
        } else if ("card_theme_fire".equals(cardStyle)) {
            canvas.save();
            canvas.translate(left, top);
            canvas.scale(scale, scale);
            if(gpuFireForFrame)gpuFireCardEffect.surface(canvas,cardWidthWorld(),cardHeightWorld(),seed,fireFrameSeconds,scale);
            else if (!animatedCardAtlas.contains(seed)) fireCardEffect.surface(canvas, width / scale, height / scale, seed, fireFrameSeconds);
            canvas.restore();
        } else if ("card_theme_smoke".equals(cardStyle)) {
            canvas.save(); canvas.translate(left, top); canvas.scale(scale, scale);
            smokeCardEffect.surface(canvas, width / scale, height / scale, seed,exportRendering?1f:scale);
            canvas.restore();
        } else if ("card_theme_botanical".equals(cardStyle)) {
            canvas.save(); canvas.translate(left, top); canvas.scale(scale, scale);
            botanicalEffect.surface(canvas, width / scale, height / scale, seed,exportRendering?1f:scale);
            canvas.restore();
        }
    }

    private int cardPrimaryTextColor() {
        if ("card_theme_smoke".equals(cardStyle)) return Color.rgb(245, 249, 251);
        if ("card_theme_midnight".equals(cardStyle)) return Color.rgb(236, 244, 246);
        if ("card_theme_archive".equals(cardStyle)) return Color.rgb(58, 44, 30);
        if ("card_theme_fire".equals(cardStyle)) return Color.rgb(255, 246, 222);
        return contrastTextColor(activeCardColor, 255, Color.rgb(34, 37, 39));
    }

    private int cardSecondaryTextColor() {
        if ("card_theme_smoke".equals(cardStyle)) return Color.rgb(215, 227, 234);
        if ("card_theme_midnight".equals(cardStyle)) return Color.argb(205, 206, 224, 226);
        if ("card_theme_archive".equals(cardStyle)) return Color.argb(208, 101, 75, 46);
        if ("card_theme_fire".equals(cardStyle)) return Color.rgb(255, 220, 103);
        return contrastTextColor(activeCardColor, 184, Color.rgb(34, 37, 39));
    }

    private int cardInitialsTextColor() {
        if ("card_theme_smoke".equals(cardStyle)) return Color.rgb(239, 245, 249);
        if ("card_theme_midnight".equals(cardStyle)) return Color.rgb(238, 248, 248);
        if ("card_theme_archive".equals(cardStyle)) return Color.argb(210, 70, 51, 31);
        if ("card_theme_fire".equals(cardStyle)) return Color.rgb(255, 238, 185);
        return contrastTextColor(activeCardColor, 220, Color.rgb(34, 37, 39));
    }

    private int cardMenuTextColor() {
        if ("card_theme_smoke".equals(cardStyle)) return Color.rgb(219, 233, 243);
        if ("card_theme_botanical".equals(cardStyle)) return Color.rgb(53, 91, 39);
        if ("card_theme_midnight".equals(cardStyle)) return Color.rgb(218, 235, 238);
        if ("card_theme_archive".equals(cardStyle)) return Color.rgb(98, 72, 43);
        if ("card_theme_fire".equals(cardStyle)) return Color.rgb(255, 218, 148);
        return contrastTextColor(activeCardColor, 220, Color.rgb(83, 94, 88));
    }

    private int contrastTextColor(int background, int alpha, int darkFallback) {
        // White only when it actually reads better than the dark text: a fixed
        // luminance cutoff flipped mid-tone palette colors (blue, pink) to white.
        double luminance = relativeLuminance(background);
        double whiteContrast = 1.05 / (luminance + .05);
        double darkContrast = (luminance + .05) / (relativeLuminance(darkFallback) + .05);
        return whiteContrast > darkContrast ? Color.argb(alpha, 255, 255, 255) : darkFallback;
    }

    private static double relativeLuminance(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;
        r = r <= .03928 ? r / 12.92 : Math.pow((r + .055) / 1.055, 2.4);
        g = g <= .03928 ? g / 12.92 : Math.pow((g + .055) / 1.055, 2.4);
        b = b <= .03928 ? b / 12.92 : Math.pow((b + .055) / 1.055, 2.4);
        return .2126 * r + .7152 * g + .0722 * b;
    }

    private static String normalizeWorkshopStyle(String value) {
        String id = value == null ? "" : value.trim();
        return id.isEmpty() ? RewardCatalog.STANDARD : id;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
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
        final String displayName;
        final String initials;
        final String date;
        final String place;
        final String search;

        CardMeta(int signature, String displayName, String initials, String date, String place, String search) {
            this.signature = signature;
            this.displayName = displayName;
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
