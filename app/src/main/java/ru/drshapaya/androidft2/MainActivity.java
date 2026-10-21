package ru.drshapaya.androidft2;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    static final int REQ_IMPORT = 21;
    static final int REQ_EXPORT_JSON = 22;
    static final int REQ_EXPORT_PNG = 23;
    static final int REQ_EXPORT_GEDCOM = 24;
    static final int REQ_PHOTO = 25;
    static final int REQ_MEMORY_FILE = 26;
    static final int REQ_EXPORT_FTREE = 27;
    static final int REQ_EXPORT_PDF = 28;
    static final int REQ_EXPORT_TILES = 29;
    static final int REQ_EXPORT_HTML = 40;
    static final int REQ_EXPORT_XML = 41;
    static final int REQ_WRITE_PHOTO_PERMISSION = 30;
    static final int REQ_MEMORY_PHOTO = 31;
    static final int REQ_ALBUM_PHOTOS = 32;
    static final String VERSION_NAME = BuildConfig.VERSION_NAME;
    private static final String UI_PREFERENCES = "androidft-ui";
    private static final String TRAINING_OFFER_HANDLED = "training_offer_handled";
    private static final String PREF_TREE_HINT_DISMISSED = "tree_hint_dismissed";
    private static final String PREF_UI_SCALE = "ui_scale";
    private static final String PREF_FONT_SCALE = "font_scale";
    private static final long TREE_HINT_AUTO_HIDE_MS = 5L * 60L * 1000L;
    static final String[][] TRAINING_STEPS = new String[][]{
        {"", "Как управлять деревом", "Перемещайте полотно одним пальцем, меняйте масштаб щипком, а карточку выбирайте обычным нажатием.", "next"},
        {"add-person", "Добавление человека", "Кнопка + создаёт первую карточку или открывает выбор родственника. В обучении мы ничего добавлять не будем.", "next"},
        {"card-menu", "Карточка человека", "Нажмите «Карточка»: здесь редактируются ФИО, даты, фото, заметки и другие сведения выбранного человека.", "tap"},
        {"people-menu", "Люди и фотографии", "Нажмите «Люди»: здесь находятся общий каталог, поиск, фильтры, аватары, семейные и собственные альбомы.", "tap"},
        {"links-menu", "Семейные связи", "Нажмите «Связи»: отсюда создаются родительские, родственные и братские линии, а также определяется родство.", "tap"},
        {"tree-menu", "Инструменты дерева", "Нажмите «Дерево»: здесь находятся упорядочивание, выделение, ветки и линии поколений.", "tap"},
        {"more-menu", "Остальные возможности", "Нажмите «Ещё», чтобы открыть файлы, сохранённые версии, параметры, обучение и информацию о приложении.", "tap"},
        {"more-overview", "Всё важное под рукой", "Файлы и экспорт, резервные версии, параметры и повторный запуск обучения находятся в этом меню.", "finish"}
    };

    TreeStore store;
    TreeState state;
    TreeCanvasView treeView;
    FrameLayout stage;
    LinearLayout landscapeBody;
    FrameLayout landscapePanelHost;
    FrameLayout landscapeFullPanelHost;
    View landscapeTreePanelCloseButton;
    TextView stats;
    LinearLayout cardPanel;
    LinearLayout linksPanel;
    LinearLayout guidePanel;
    LinearLayout filesPanel;
    LinearLayout viewPanel;
    LinearLayout branchPanel;
    LinearLayout settingsPanel;
    View peoplePanel;
    View morePanel;
    LinearLayout settingsContent;
    LinearLayout historyPanel;
    LinearLayout historyList;
    LinearLayout branchStatusPanel;
    LinearLayout selectionToolbar;
    LinearLayout canvasModePanel;
    TextView historyHint;
    TextView branchStatusText;
    TextView selectionStatusText;
    TextView canvasModeTitle;
    TextView canvasModeDetail;
    Button canvasModeAction;
    Button selectionArrangeButton;
    Button selectionMoveButton;
    Button selectionStopButton;
    Button selectionAppendButton;
    EditText nameInput;
    EditText bornInput;
    EditText diedInput;
    EditText placeInput;
    EditText notesInput;
    EditText guideLabelInput;
    Button undoBtn;
    Button redoBtn;
    Button treeNav;
    Button cardNav;
    Button peopleNav;
    Button linksNav;
    Button moreNav;
    Button lockRailButton;
    Button treeQualityButton;
    View appHeader;
    View headerBrand;
    View headerIdentityIcon;
    View headerIdentityTexts;
    LinearLayout landscapeHeaderControls;
    LinearLayout landscapeFocusControlsHost;
    View landscapePeopleCloseHost;
    TextView zoomIndicator;
    TextView treeHint;
    View distantCardsWarning;
    View actionOverlay;
    boolean treeHintDismissed;
    boolean treeHintAutoHidden;
    View zoomRail;
    View bottomNavigation;
    Button addPersonButton;
    View trainingMoreMenuTarget;
    final ArrayDeque<TreeCommand> undoStack = new ArrayDeque<>();
    final ArrayDeque<TreeCommand> redoStack = new ArrayDeque<>();
    String activePanel = "";
    String pendingLinkType = "";
    String pendingLinkFrom = "";
    String selectedLinkId = "";
    String pendingBranchMode = "";
    String branchMode = "all";
    String branchAnchorId = "";
    int nearGenerations = 1;
    private LinearLayout nearGenerationControls;
    EditText nearGenerationInput;
    LinearLayout kinshipStatusPanel;
    boolean editLocked = false;
    boolean onlineReadOnly = false;
    boolean viewMode = false;
    boolean generationLines = true;
    boolean hideCardDetails = false;
    boolean compactCards = false;
    boolean focusTree = false;
    boolean kinshipTerminological = true;
    int workspaceWidth = (int) TreeLayoutEngine.SURFACE_W;
    int workspaceHeight = (int) TreeLayoutEngine.SURFACE_H;
    float uiScale = 1f;
    float fontScale = 1f;
    String parentLineMode = "smart";
    String familyLineMode = "centered";
    String activeGuideMode = "";
    String guideDraftLabel = "Поколение";
    String guideDraftColor = "#2f7d75";
    String theme = "light";
    String lastSelectionMode = "rect";
    boolean selectionAppendMode = false;
    boolean bindingEditor = false;
    String pendingPhotoPersonId = "";
    String pendingPreviewPhotoMediaId = "";
    String pendingPreviewPhotoDataUrl = "";
    String pendingMemoryPersonId = "";
    String pendingMemoryTitle = "";
    String pendingMemoryText = "";
    Typeface uiRegularTypeface;
    Typeface uiBoldTypeface;
    private MainActivityFiles filesModule;
    private MainActivityHistory historyModule;
    private MainActivityHeader headerModule;
    MainActivitySearch searchModule;
    private MainActivitySettings settingsModule;
    private MainActivityPanels panelsModule;
    private MainActivityRelations relationsModule;
    private MainActivityEditor editorModule;
    private MainActivityQuickStart quickStartModule;
    MainActivityPeople peopleModule;
    private MainUiKit uiKit;
    private MainActivityAbout aboutModule;
    private MainActivityWorkshop workshopModule;
    private MainActivityOnline onlineModule;
    private OnlineTreeManager onlineManager;
    private TreeSaveCoordinator saveCoordinator;
    private LayoutTrainingStore layoutTraining;
    MainActivityQuality qualityModule;
    private MainActivityPersonMenus personMenusModule;
    private MainActivityAddPersonMenu addPersonMenuModule;
    private MainActivityBiographyShare biographyShareModule;
    private MainActivityDistantCards distantCardsModule;
    private MainActivityPhotos photosModule;
    private final Handler toastHandler = new Handler(Looper.getMainLooper());
    private Toast currentToast;
    private int statusBarInsetPx;
    private final java.util.IdentityHashMap<View, View> landscapePanelContainers =
        new java.util.IdentityHashMap<>();
    private Dialog lockedEditPromptDialog;
    private volatile boolean arrangeInProgress;
    private final Runnable dismissLockedEditPromptRunnable = () -> {
        if (lockedEditPromptDialog != null && lockedEditPromptDialog.isShowing()) {
            lockedEditPromptDialog.dismiss();
        }
    };
    private final Runnable cancelToastRunnable = () -> {
        if (currentToast != null) currentToast.cancel();
    };
    private final Runnable hideZoomIndicatorRunnable = () -> {
        if (zoomIndicator == null) return;
        zoomIndicator.animate().alpha(0f).setDuration(180L).withEndAction(() -> {
            if (zoomIndicator != null) zoomIndicator.setVisibility(View.GONE);
        }).start();
    };
    private OnBackInvokedCallback backInvokedCallback;
    private boolean activityStarted;
    private StartupSplash startupSplash;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(wrapUiContext(newBase));
    }

    private static Context wrapUiContext(Context context) {
        return AppLanguage.wrap(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StartupSplash.applyLaunchTheme(this);
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backInvokedCallback = this::handlePredictiveBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                backInvokedCallback);
        }
        if (AppLanguage.isEnglish(this) && "Поколение".equals(guideDraftLabel)) {
            guideDraftLabel = "Generation";
        }
        store = new TreeStore(this);
        startupSplash = new StartupSplash(this, savedInstanceState);
        StartupSplash splash = startupSplash;
        new Thread(() -> {
            StartupSplashView.Artwork artwork = splash.prepareArtwork();
            runOnUiThread(() -> splash.setArtwork(artwork));
            TreeState loaded = store.load();
            runOnUiThread(() -> {
                splash.whenAssemblyFinished(() -> {
                    if (!isFinishing() && !isDestroyed()) initializeTree(loaded);
                });
            });
        }, "tree-startup-loader").start();
    }

    private void initializeTree(TreeState loaded) {
        layoutTraining = new LayoutTrainingStore(this);
        state = loaded;
        AnalyticsReporter.treeLoaded(state);
        applyAutoArrangePreference(state);
        treeHintDismissed = getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE)
            .getBoolean(PREF_TREE_HINT_DISMISSED, false);
        kinshipTerminological = getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE)
            .getBoolean("kinship_terminological", true);
        filesModule = new MainActivityFiles(this);
        historyModule = new MainActivityHistory(this);
        headerModule = new MainActivityHeader(this);
        settingsModule = new MainActivitySettings(this);
        panelsModule = new MainActivityPanels(this);
        relationsModule = new MainActivityRelations(this);
        editorModule = new MainActivityEditor(this);
        quickStartModule = new MainActivityQuickStart(this);
        peopleModule = new MainActivityPeople(this);
        uiKit = new MainUiKit(this, () -> uiScale);
        photosModule = new MainActivityPhotos(this);
        distantCardsModule = new MainActivityDistantCards(
            this,
            uiKit,
            () -> state,
            () -> activePanel,
            this::focusDistantCard,
            this::moveDistantCardsCloser);
        searchModule = new MainActivitySearch(
            this,
            uiKit,
            () -> state,
            () -> tr("Без имени"),
            query -> { if (treeView != null) treeView.setFilter(query); },
            this::selectTreeSearchPerson,
            () -> stage,
            this::showKeyboard,
            this::hideKeyboard);
        aboutModule = new MainActivityAbout(
            this,
            uiKit,
            this::toast,
            this::showDeveloperMenu);
        workshopModule = new MainActivityWorkshop(
            this,
            uiKit,
            () -> treeView,
            this::toast);
        qualityModule = new MainActivityQuality(
            this,
            uiKit,
            () -> state,
            () -> treeView,
            () -> treeQualityButton,
            this::selectQualityPerson,
            this::showPanel,
            this::toast,
            this::openPersonEditor);
        personMenusModule = new MainActivityPersonMenus(this);
        addPersonMenuModule = new MainActivityAddPersonMenu(this);
        biographyShareModule = new MainActivityBiographyShare(this);
        onlineManager = new OnlineTreeManager(this, store, new OnlineTreeManager.Listener() {
            @Override public void onRemoteTree(TreeState remote, String message) {
                applyOnlineTree(remote, message);
            }

            @Override public void onMediaChanged() {
                ThumbnailLoader.SHARED.clearFailures();
                if (treeView != null) treeView.invalidate();
                if (onlineModule != null) onlineModule.refreshOpenDashboard();
            }

            @Override public void onEditingPermissionChanged(boolean canEdit) {
                onlineReadOnly = !canEdit;
                resetTransientCanvasModes(false);
                if (treeView != null) treeView.setEditLocked(editingBlocked());
                refreshLockUi();
                if (editingBlocked()) hideKeyboard();
                refreshOpenPersonEditor();
                if (settingsModule != null) settingsModule.refreshSettingsIfVisible();
                updateCanvasModePanel();
            }

            @Override public void onStatusChanged() {
                if (onlineModule != null) onlineModule.refreshOpenDashboard();
            }

            @Override public void onMessage(String message) {
                toast(message);
            }
        });
        onlineReadOnly = !onlineManager.canEdit();
        onlineModule = new MainActivityOnline(this, onlineManager);
        saveCoordinator = new TreeSaveCoordinator(
            store,
            () -> state,
            () -> historyModule.commitPendingUndo(),
            new TreeSaveCoordinator.Listener() {
                @Override public void onSaveError() {
                    toast("Не удалось сохранить дерево");
                }

                @Override public void onSaved(TreeState snapshot) {
                    AnalyticsReporter.treeSaved(snapshot);
                    BirthdayWidgetProvider.updateAll(MainActivity.this, snapshot);
                    if (onlineManager != null) onlineManager.onLocalTreeSaved(snapshot);
                }
            });
        applyStateSettings();
        AppThemePalette.setDark("dark".equals(theme));
        super.setTheme(AppThemePalette.isDark() ? R.style.AppThemeDark : R.style.AppTheme);
        applySystemBars();
        TreeLayoutEngine.ensurePositions(state);
        buildUi();
        bindState();
        onlineManager.reconcileLocalTree(state);
        if (activityStarted) onlineManager.startForegroundChecks();
        String recoveryNotice = store.consumeRecoveryNotice();
        if (!recoveryNotice.isEmpty()) toast(recoveryNotice);
        handleIncomingIntent(getIntent());
        Runnable afterStartup = () -> {
            if (!isFinishing() && !isDestroyed() && stage != null) {
                stage.postDelayed(this::offerTrainingIfNeeded, 420);
            }
        };
        if (startupSplash != null) startupSplash.onTreeReady(headerIdentityIcon, afterStartup);
        else afterStartup.run();
        treeView.post(() -> {
            if (state == null || state.people.isEmpty()) treeView.focusWorkspaceCenter();
            else treeView.fit();
            stage.postDelayed(() -> {
                if (!editingBlocked()) hideTreeHintForSession();
            }, TREE_HINT_AUTO_HIDE_MS);
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (state != null) handleIncomingIntent(intent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (state != null && headerModule != null && panelsModule != null) {
            if (startupSplash != null) startupSplash.dismiss();
            rebuildUiKeepingPanel();
        }
    }

    @SuppressLint("GestureBackNavigation")
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (settingsModule != null && settingsModule.handleTrainingBack()) return;
        if (!activePanel.isEmpty()) {
            showPanel("");
            return;
        }
        super.onBackPressed();
    }

    private void handlePredictiveBack() {
        if (settingsModule != null && settingsModule.handleTrainingBack()) return;
        if (!activePanel.isEmpty()) {
            showPanel("");
            return;
        }
        finishAfterTransition();
    }

    @Override
    protected void onStop() {
        activityStarted = false;
        DiagnosticsLogger.breadcrumb(this, "activity.stop");
        if (saveCoordinator != null) saveCoordinator.flush();
        if (onlineManager != null) onlineManager.stopForegroundChecks();
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        activityStarted = true;
        if (onlineManager != null) onlineManager.startForegroundChecks();
    }

    @Override
    protected void onDestroy() {
        if (startupSplash != null) startupSplash.close();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && backInvokedCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backInvokedCallback);
            backInvokedCallback = null;
        }
        if (saveCoordinator != null) saveCoordinator.close();
        if (onlineManager != null) onlineManager.close();
        if (peopleModule != null) peopleModule.close();
        if (qualityModule != null) qualityModule.close();
        if (searchModule != null) searchModule.close();
        if (settingsModule != null) settingsModule.close();
        if (filesModule != null) filesModule.close();
        if (workshopModule != null) workshopModule.close();
        toastHandler.removeCallbacks(dismissLockedEditPromptRunnable);
        if (lockedEditPromptDialog != null && lockedEditPromptDialog.isShowing()) {
            lockedEditPromptDialog.dismiss();
        }
        super.onDestroy();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (treeView != null) treeView.trimBitmapCache(level);
        DiagnosticsLogger.breadcrumb(this, "memory.trim=" + level);
    }

    private void buildUi() {
        landscapePanelContainers.clear();
        landscapePanelHost = null;
        landscapeFullPanelHost = null;
        landscapeBody = null;
        landscapeTreePanelCloseButton = null;
        landscapeHeaderControls = null;
        landscapeFocusControlsHost = null;
        landscapePeopleCloseHost = null;
        distantCardsWarning = null;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppThemePalette.isDark()
            ? Color.rgb(16, 23, 27)
            : Color.rgb(243, 246, 248));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int bottomInset = 0;
            int topInset = 0;
            if (insets != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bottomInset = insets.getInsets(
                        android.view.WindowInsets.Type.navigationBars()).bottom;
                    topInset = insets.getInsets(
                        android.view.WindowInsets.Type.statusBars()).top;
                } else {
                    bottomInset = insets.getSystemWindowInsetBottom();
                    topInset = insets.getSystemWindowInsetTop();
                }
            }
            view.setPadding(0, 0, 0, Math.max(0, bottomInset));
            int safeTop = Math.max(0, topInset);
            if (statusBarInsetPx != safeTop) {
                statusBarInsetPx = safeTop;
                applyFullPanelTopInset();
            }
            return insets;
        });
        if (startupSplash == null || !startupSplash.installContent(root)) setContentView(root);
        filesModule.installFileDrop(root);
        root.post(root::requestApplyInsets);

        appHeader = buildHeader();
        root.addView(appHeader, new LinearLayout.LayoutParams(-1, -2));

        stage = new FrameLayout(this);
        stage.setFocusableInTouchMode(true);
        stage.setBackgroundColor(AppThemePalette.surface(Color.rgb(248, 251, 252)));
        if (isLandscapeLayout()) {
            landscapeBody = new LinearLayout(this);
            landscapeBody.setOrientation(LinearLayout.HORIZONTAL);
            landscapeBody.setBackgroundColor(AppThemePalette.surface(Color.rgb(248, 251, 252)));
            root.addView(landscapeBody, new LinearLayout.LayoutParams(-1, 0, 1));

            landscapePanelHost = new FrameLayout(this);
            landscapePanelHost.setBackgroundColor(AppThemePalette.surface(Color.rgb(248, 251, 252)));
            landscapePanelHost.setVisibility(View.GONE);
            landscapePanelHost.setElevation(dp(9));
            landscapeBody.addView(
                landscapePanelHost,
                new LinearLayout.LayoutParams(landscapePanelWidth(), -1));
            landscapeBody.addView(stage, new LinearLayout.LayoutParams(0, -1, 1));

            landscapeFullPanelHost = new FrameLayout(this);
            landscapeFullPanelHost.setBackgroundColor(AppThemePalette.surface(Color.rgb(243, 247, 248)));
            landscapeFullPanelHost.setVisibility(View.GONE);
            landscapeBody.addView(
                landscapeFullPanelHost,
                new LinearLayout.LayoutParams(0, -1, 1));
        } else {
            root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
        }

        treeView = new TreeCanvasView(this);
        treeView.setMediaStore(store.mediaStore());
        treeView.setListener(new TreeCanvasView.Listener() {
            @Override public void onPersonSelected(Person person) {
                if (handlePendingLink(person)) return;
                if (handlePendingBranch(person)) return;
                selectedLinkId = "";
                bindEditor(person);
            }
            @Override public void onPersonMenu(Person person, float screenX, float screenY) {
                state.selectedId = person.id;
                selectedLinkId = "";
                bindEditor(person);
                openPersonActions(person, screenX, screenY);
            }
            @Override public void onLinkSelected(Relation relation) {
                selectedLinkId = relation == null ? "" : relation.id;
                pendingLinkType = "";
                pendingLinkFrom = "";
                toast("Связь выбрана");
                bindState();
            }
            @Override public void onSelectionChanged(int count) {
                updateSelectionToolbar();
            }
            @Override public void onTreeEditStart(String label, String detail) {
                recordUndo(label, detail);
            }
            @Override public void onPeopleMoved(
                java.util.Map<String, PointF> before,
                java.util.Map<String, PointF> after,
                String detail
            ) {
                historyModule.recordMove(before, after, detail);
            }
            @Override public void onTreeChanged() {
                treeView.invalidateStructureCaches();
                saveOnly();
                updateDistantCardsWarning();
                if (!activeGuideMode.isEmpty()) finishGuideAction();
            }
            @Override public void onGuideActionMiss() {
                toast("Линия не найдена — режим удаления всё ещё включён");
                updateCanvasModePanel();
            }
            @Override public void onCanvasTouched() {
                clearSearchFocus();
            }
            @Override public void onEditBlocked() {
                showEditingLockedPrompt();
            }
            @Override public void onZoomChanged(float scale) {
                showZoomIndicator(scale);
            }
        });
        stage.addView(treeView, new FrameLayout.LayoutParams(-1, -1));

        if (isLandscapeLayout()) {
            landscapeFocusControlsHost = new LinearLayout(this);
            landscapeFocusControlsHost.setGravity(Gravity.CENTER_VERTICAL);
            landscapeFocusControlsHost.setOrientation(LinearLayout.HORIZONTAL);
            landscapeFocusControlsHost.setPadding(0, dp(3), dp(2), dp(3));
            landscapeFocusControlsHost.setBackgroundColor(Color.TRANSPARENT);
            landscapeFocusControlsHost.setVisibility(View.GONE);
            landscapeFocusControlsHost.setElevation(dp(13));
            FrameLayout.LayoutParams focusControlsParams = new FrameLayout.LayoutParams(
                -1,
                dp(58),
                Gravity.TOP);
            focusControlsParams.setMargins(dp(7), statusBarHeight(), dp(10), 0);
            stage.addView(landscapeFocusControlsHost, focusControlsParams);
        }

        if (isLandscapeLayout()) {
            TextView closeTreePanel = new LocalizedTextView(this);
            closeTreePanel.setGravity(Gravity.CENTER);
            closeTreePanel.setBackground(panelBg(
                Color.rgb(255, 247, 247),
                dp(10),
                Color.rgb(224, 86, 86)));
            closeTreePanel.setForeground(centeredIcon(
                R.drawable.ic_menu_close,
                Color.rgb(211, 55, 55)));
            closeTreePanel.setForegroundGravity(Gravity.CENTER);
            closeTreePanel.setContentDescription(tr("Закрыть инструменты дерева"));
            closeTreePanel.setOnClickListener(v -> showPanel(""));
            closeTreePanel.setElevation(dp(16));
            closeTreePanel.setVisibility(View.GONE);
            FrameLayout.LayoutParams closeTreeParams = new FrameLayout.LayoutParams(
                dp(42),
                dp(42),
                Gravity.LEFT | Gravity.TOP);
            closeTreeParams.setMargins(dp(8), dp(8), 0, 0);
            stage.addView(closeTreePanel, closeTreeParams);
            landscapeTreePanelCloseButton = closeTreePanel;
        }

        actionOverlay = headerModule.buildActionOverlay();
        if (actionOverlay != null) {
            actionOverlay.setElevation(dp(12));
            FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
            actionParams.setMargins(0, isLandscapeLayout() ? 0 : dp(8), 0, 0);
            stage.addView(actionOverlay, actionParams);
        }

        zoomIndicator = new LocalizedTextView(this);
        zoomIndicator.setTextColor(Color.WHITE);
        zoomIndicator.setTextSize(12);
        zoomIndicator.setTypeface(uiBold());
        zoomIndicator.setGravity(Gravity.CENTER);
        zoomIndicator.setBackground(panelBg(Color.argb(205, 36, 42, 48), dp(999), Color.argb(90, 255, 255, 255)));
        zoomIndicator.setVisibility(View.GONE);
        zoomIndicator.setAlpha(0f);
        FrameLayout.LayoutParams zoomIndicatorParams = new FrameLayout.LayoutParams(dp(76), dp(34), Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        zoomIndicatorParams.setMargins(0, 0, 0, dp(12));
        stage.addView(zoomIndicator, zoomIndicatorParams);

        TextView hint = new LocalizedTextView(this);
        hint.setText("Один палец - движение, два пальца - масштаб");
        hint.setTextColor(Color.rgb(101, 113, 122));
        hint.setTextSize(13);
        hint.setTypeface(ui());
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(12), 0, dp(12), 0);
        hint.setBackground(panelBg(Color.argb(218, 255, 255, 255), dp(8), Color.argb(42, 24, 169, 153)));
        final float[] hintDownX = {0f};
        hint.setOnTouchListener((view, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                hintDownX[0] = event.getX();
                return true;
            }
            if (event.getAction() == android.view.MotionEvent.ACTION_UP
                || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                float distance = event.getX() - hintDownX[0];
                if (editingBlocked()) {
                    if (Math.abs(distance) < dp(18)) showEditingLockedPrompt();
                } else if (Math.abs(distance) >= dp(54)) {
                    dismissTreeHint(distance >= 0f ? 1 : -1);
                }
                return true;
            }
            return true;
        });
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(-1, dp(40), Gravity.TOP);
        hintParams.setMargins(
            dp(isLandscapeLayout() ? 64 : 12),
            dp(isLandscapeLayout() ? 8 : 76),
            dp(isLandscapeLayout() ? 12 : 64),
            0);
        stage.addView(hint, hintParams);
        treeHint = hint;
        refreshLockUi();

        distantCardsWarning = distantCardsModule.buildWarning();
        FrameLayout.LayoutParams distantParams = new FrameLayout.LayoutParams(-1, dp(44), Gravity.BOTTOM);
        distantParams.setMargins(
            dp(12),
            0,
            dp(76),
            dp(isLandscapeLayout() ? 12 : focusTree ? 66 : 86));
        stage.addView(distantCardsWarning, distantParams);

        canvasModePanel = buildCanvasModePanel();
        stage.addView(canvasModePanel, canvasModeParams());
        zoomRail = buildZoomRail();
        stage.addView(zoomRail, railParams());
        historyPanel = buildHistoryPanel();
        stage.addView(historyPanel, historyParams());
        branchStatusPanel = buildBranchStatusPanel();
        stage.addView(branchStatusPanel, branchStatusParams());
        selectionToolbar = buildSelectionToolbar();
        stage.addView(selectionToolbar, selectionToolbarParams());

        Button add = iconButton(
            R.drawable.ic_menu_add_box,
            v -> showAddPersonMenu(),
            Color.WHITE);
        add.setBackground(gradientBg());
        add.setElevation(dp(7));
        FrameLayout.LayoutParams addParams = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.RIGHT | Gravity.BOTTOM);
        addParams.setMargins(0, 0, dp(12), dp(isLandscapeLayout() ? 12 : 86));
        stage.addView(add, addParams);
        addPersonButton = add;

        cardPanel = buildCardPanel();
        linksPanel = buildLinksPanel();
        guidePanel = buildGuidePanel();
        filesPanel = buildFilesPanel();
        viewPanel = buildViewPanel();
        branchPanel = buildBranchPanel();
        settingsPanel = buildSettingsPanel();
        peoplePanel = peopleModule.buildPanel();
        morePanel = buildMorePanel();
        addAdaptivePanel(cardPanel, bottomPanelWrapParams(), true);
        addAdaptivePanel(linksPanel, bottomPanelWrapParams(), true);
        addAdaptivePanel(guidePanel, bottomPanelWrapParams(), true);
        addAdaptivePanel(filesPanel, bottomPanelWrapParams(), true);
        addAdaptivePanel(viewPanel, bottomPanelWrapParams(), false);
        addAdaptivePanel(branchPanel, bottomPanelWrapParams(), true);
        addAdaptivePanel(settingsPanel, fullPanelParams(), false);
        addAdaptiveFullPanel(peoplePanel, fullPanelParams());
        addAdaptivePanel(morePanel, new FrameLayout.LayoutParams(-1, -1), false);

        bottomNavigation = buildBottomNav();
        if (isLandscapeLayout()) {
            landscapeBody.addView(
                bottomNavigation,
                new LinearLayout.LayoutParams(dp(88), -1));
        } else {
            stage.addView(bottomNavigation, bottomParams());
        }
        applyFocusTreeUi();
        showPanel(activePanel);

    }

    void updateDistantCardsWarning() {
        if (distantCardsWarning != null
            && distantCardsWarning.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) distantCardsWarning.getLayoutParams();
            int bottomMargin = dp(isLandscapeLayout() ? 12 : focusTree ? 66 : 86);
            if (params.bottomMargin != bottomMargin) {
                params.bottomMargin = bottomMargin;
                distantCardsWarning.setLayoutParams(params);
            }
        }
        distantCardsModule.update();
    }

    private void focusDistantCard(String personId) {
        state.selectedId = personId;
        bindState();
        treeView.focusPerson(personId);
    }

    private void moveDistantCardsCloser(java.util.List<String> distantIds) {
        if (distantIds.isEmpty() || state == null || !requireEditingEnabled()) {
            updateDistantCardsWarning();
            return;
        }
        recordUndo("Далёкие карточки возвращены ближе", distantIds.size() + " шт.");
        DistantCardFinder.Bounds main = DistantCardFinder.boundsExcluding(state, distantIds);
        float startX = main.right + TreeLayoutEngine.GRID * 2f;
        float startY = main.top;
        int index = 0;
        for (String id : distantIds) {
            Person person = state.people.get(id);
            if (person == null || person.pinned) continue;
            float[] spot = findOpenSpot(
                startX + (index % 2) * (TreeLayoutEngine.CARD_W + TreeLayoutEngine.GRID),
                startY + (index / 2) * (TreeLayoutEngine.CARD_H + TreeLayoutEngine.GRID));
            person.x = spot[0];
            person.y = spot[1];
            index++;
        }
        distantCardsModule.resetDismissed();
        saveToast("Карточки возвращены ближе");
        bindState();
        treeView.fit();
    }

    private View buildHeader() {
        return headerModule.buildHeader();
    }

    private LinearLayout buildZoomRail() {
        return headerModule.buildZoomRail();
    }

    private LinearLayout buildHistoryPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(9), dp(12), dp(10));
        panel.setBackground(panelBg(Color.argb(244, 252, 254, 254), dp(16), Color.argb(72, 63, 82, 94)));
        panel.setElevation(dp(9));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView title = new LocalizedTextView(this);
        title.setText("История действий");
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setTextSize(13);
        title.setTypeface(uiBold());
        title.setIncludeFontPadding(false);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(32), 1));

        Button hide = iconButton(R.drawable.ic_menu_eye_off, v -> {
            if (state == null) return;
            state.historyHidden = true;
            saveOnly();
            refreshSettingsIfVisible();
            updateHistoryPanel();
        });
        hide.setBackground(panelBg(Color.rgb(232, 248, 246), dp(10), Color.argb(72, 24, 169, 153)));
        header.addView(hide, new LinearLayout.LayoutParams(dp(32), dp(32)));
        panel.addView(header);

        historyHint = new LocalizedTextView(this);
        historyHint.setTextColor(Color.rgb(83, 94, 103));
        historyHint.setTextSize(9);
        historyHint.setTypeface(uiBold());
        historyHint.setSingleLine(true);
        historyHint.setEllipsize(android.text.TextUtils.TruncateAt.END);
        historyHint.setGravity(Gravity.CENTER_VERTICAL);
        historyHint.setIncludeFontPadding(false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, dp(24));
        hintParams.setMargins(0, 0, 0, dp(4));
        panel.addView(historyHint, hintParams);

        ScrollView scroll = new ScrollView(this);
        historyList = new LinearLayout(this);
        historyList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(historyList);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        return panel;
    }

    private LinearLayout buildBranchStatusPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(4), dp(4), dp(4));
        panel.setBackground(panelBg(Color.argb(245, 248, 251, 252), dp(10), Color.argb(52, 63, 82, 94)));
        panel.setElevation(dp(7));
        panel.setTag("canvas.branchStatus");
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        branchStatusText = new LocalizedTextView(this);
        branchStatusText.setTextColor(uiColor(Color.rgb(28, 34, 38)));
        branchStatusText.setTextSize(12); branchStatusText.setTypeface(uiBold());
        branchStatusText.setMaxLines(2); branchStatusText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.addView(branchStatusText, new LinearLayout.LayoutParams(0, -2, 1));
        Button reset = iconButton(R.drawable.ic_menu_target, v -> resetBranchAnchor());
        reset.setContentDescription(tr("Выбрать другую карточку"));
        heading.addView(reset, new LinearLayout.LayoutParams(dp(44), dp(44)));
        Button close = iconButton(R.drawable.ic_menu_close, v -> clearBranchFilter());
        close.setContentDescription(tr("Закрыть"));
        heading.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        panel.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        nearGenerationControls = new LinearLayout(this);
        nearGenerationControls.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new LocalizedTextView(this);
        label.setText("Поколения"); label.setTextSize(11); label.setTextColor(uiColor(Color.rgb(76, 87, 96)));
        nearGenerationControls.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        Button minus = actionButton("−", v -> {
            setNearGenerations(nearGenerations - 1);
            nearGenerationInput.setText(String.valueOf(nearGenerations));
        });
        minus.setTag("near.minus"); minus.setContentDescription(tr("Уменьшить число поколений"));
        nearGenerationControls.addView(minus, new LinearLayout.LayoutParams(dp(44), dp(44)));
        nearGenerationInput = field("");
        nearGenerationInput.setTag("near.generations");
        nearGenerationInput.setContentDescription(tr("Количество поколений близких"));
        nearGenerationInput.setSingleLine(true); nearGenerationInput.setGravity(Gravity.CENTER);
        nearGenerationInput.setPadding(dp(4), 0, dp(4), 0);
        nearGenerationInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        nearGenerationInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2)});
        nearGenerationInput.setText(String.valueOf(nearGenerations));
        nearGenerationInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        nearGenerationControls.addView(nearGenerationInput, new LinearLayout.LayoutParams(dp(44), dp(44)));
        nearGenerationInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try { setNearGenerations(Integer.parseInt(s.toString())); } catch (NumberFormatException ignored) {}
            }
            public void afterTextChanged(Editable s) {}
        });
        nearGenerationInput.setOnFocusChangeListener((v, focused) -> {
            if (!focused && !nearGenerationInput.getText().toString().equals(String.valueOf(nearGenerations)))
                nearGenerationInput.setText(String.valueOf(nearGenerations));
        });
        nearGenerationInput.setOnEditorActionListener((v, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_DONE) return false;
            nearGenerationInput.clearFocus();
            InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(v.getWindowToken(), 0);
            return true;
        });
        Button plus = actionButton("+", v -> {
            setNearGenerations(nearGenerations + 1);
            nearGenerationInput.setText(String.valueOf(nearGenerations));
        });
        plus.setTag("near.plus"); plus.setContentDescription(tr("Увеличить число поколений"));
        nearGenerationControls.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(44)));
        panel.addView(nearGenerationControls, new LinearLayout.LayoutParams(-1, -2));
        nearGenerationControls.setVisibility(View.GONE);
        panel.setVisibility(View.GONE);
        return panel;
    }

    void setNearGenerations(int value) {
        int next = Math.max(1, Math.min(99, value));
        if (nearGenerations == next) return;
        nearGenerations = next;
        treeView.setNearGenerations(next);
        updateBranchStatusPanel();
        if ("near".equals(branchMode) && !branchAnchorId.isEmpty()) treeView.post(treeView::fit);
    }

    private LinearLayout buildCanvasModePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setPadding(dp(12), dp(7), dp(7), dp(7));
        panel.setBackground(panelBg(Color.argb(250, 232, 248, 246), dp(10), Color.argb(108, 24, 169, 153)));
        panel.setElevation(dp(9));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_menu_target);
        icon.setColorFilter(uiColor(Color.rgb(8, 122, 115)));
        panel.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, dp(8), 0);
        canvasModeTitle = new LocalizedTextView(this);
        canvasModeTitle.setTextColor(Color.rgb(28, 34, 38));
        canvasModeTitle.setTextSize(12);
        canvasModeTitle.setTypeface(uiBold());
        canvasModeTitle.setSingleLine(true);
        canvasModeTitle.setIncludeFontPadding(false);
        canvasModeTitle.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(canvasModeTitle, new LinearLayout.LayoutParams(-1, dp(24)));
        canvasModeDetail = new LocalizedTextView(this);
        canvasModeDetail.setTextColor(Color.rgb(76, 87, 96));
        canvasModeDetail.setTextSize(8);
        canvasModeDetail.setSingleLine(false);
        canvasModeDetail.setMaxLines(2);
        canvasModeDetail.setIncludeFontPadding(false);
        canvasModeDetail.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(canvasModeDetail, new LinearLayout.LayoutParams(-1, dp(28)));
        panel.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));

        canvasModeAction = selectionActionButton(
            R.drawable.ic_menu_stop,
            "Стоп",
            v -> cancelActiveCanvasMode());
        panel.addView(canvasModeAction, new LinearLayout.LayoutParams(dp(82), dp(42)));
        panel.setVisibility(View.GONE);
        return panel;
    }

    private LinearLayout buildSelectionToolbar() {
        LinearLayout panel = new LinearLayout(this);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(8), dp(6), dp(8), dp(6));
        panel.setBackground(panelBg(
            Color.argb(252, 248, 252, 252),
            dp(16),
            Color.argb(72, 24, 169, 153)));
        panel.setElevation(dp(6));

        selectionStatusText = new LocalizedTextView(this);
        selectionStatusText.setTextColor(Color.rgb(101, 113, 122));
        selectionStatusText.setTextSize(11);
        selectionStatusText.setTypeface(uiBold());
        selectionStatusText.setSingleLine(true);
        selectionStatusText.setGravity(Gravity.CENTER_VERTICAL);
        selectionStatusText.setIncludeFontPadding(false);
        panel.addView(selectionStatusText, new LinearLayout.LayoutParams(-1, dp(22)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        selectionArrangeButton = selectionActionButton(
            R.drawable.ic_menu_layout,
            "Упорядочить",
            v -> startSelectedArrange());
        selectionArrangeButton.setContentDescription(tr("Упорядочить выделение"));
        actions.addView(selectionArrangeButton, selectionButtonParams(1.28f, false));

        selectionMoveButton = selectionActionButton(
            R.drawable.ic_menu_check,
            "Готово",
            v -> enableSelectionMove());
        actions.addView(selectionMoveButton, selectionButtonParams(1.18f, true));

        selectionStopButton = selectionActionButton(
            R.drawable.ic_menu_stop,
            "Сброс",
            v -> clearSelection());
        actions.addView(selectionStopButton, selectionButtonParams(0.82f, true));

        selectionAppendButton = selectionActionButton(
            R.drawable.ic_menu_frame,
            "Добор",
            v -> {
            selectionAppendMode = !selectionAppendMode;
            if (treeView != null) treeView.setSelectionAppendMode(selectionAppendMode);
            updateSelectionToolbar();
            toast(selectionAppendMode
                ? "Добор выделения включён"
                : "Новое выделение заменяет старое");
        });
        selectionAppendButton.setContentDescription(tr("Добавлять к текущему выделению"));
        actions.addView(selectionAppendButton, selectionButtonParams(0.90f, true));
        panel.addView(actions, new LinearLayout.LayoutParams(-1, dp(48)));
        panel.setVisibility(View.GONE);
        return panel;
    }

    boolean isLandscapeLayout() {
        android.graphics.Rect bounds;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bounds = getWindowManager().getCurrentWindowMetrics().getBounds();
        } else {
            android.graphics.Point size = new android.graphics.Point();
            getWindowManager().getDefaultDisplay().getSize(size);
            bounds = new android.graphics.Rect(0, 0, size.x, size.y);
        }
        return bounds.width() > bounds.height();
    }

    private int landscapePanelWidth() {
        float density = getResources().getDisplayMetrics().density;
        int widthPixels;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            widthPixels = getWindowManager().getCurrentWindowMetrics().getBounds().width();
        } else {
            android.graphics.Point size = new android.graphics.Point();
            getWindowManager().getDefaultDisplay().getSize(size);
            widthPixels = size.x;
        }
        int screenWidthDp = Math.round(widthPixels / Math.max(1f, density));
        int preferred = Math.round(screenWidthDp * (focusTree ? 0.22f : 0.25f));
        return dp(Math.min(focusTree ? 204 : 224, Math.max(focusTree ? 176 : 192, preferred)));
    }

    private void addAdaptivePanel(
        View panel,
        FrameLayout.LayoutParams portraitParams,
        boolean scrollInLandscape
    ) {
        if (!isLandscapeLayout() || landscapePanelHost == null) {
            stage.addView(panel, portraitParams);
            return;
        }
        View container = panel;
        if (scrollInLandscape) {
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setClipToPadding(false);
            scroll.setBackgroundColor(AppThemePalette.surface(Color.rgb(248, 251, 252)));
            scroll.addView(panel, new ScrollView.LayoutParams(-1, -2));
            container = scroll;
        }
        container.setVisibility(View.GONE);
        panel.setVisibility(View.GONE);
        landscapePanelHost.addView(container, new FrameLayout.LayoutParams(-1, -1));
        landscapePanelContainers.put(panel, container);
    }

    private void addAdaptiveFullPanel(View panel, FrameLayout.LayoutParams portraitParams) {
        if (!isLandscapeLayout() || landscapeFullPanelHost == null) {
            stage.addView(panel, portraitParams);
            return;
        }
        panel.setVisibility(View.GONE);
        landscapeFullPanelHost.addView(panel, new FrameLayout.LayoutParams(-1, -1));
    }

    void setAdaptivePanelVisibility(View panel, boolean visible) {
        if (panel == null) return;
        View container = landscapePanelContainers.get(panel);
        View target = container == null ? panel : container;
        boolean opening = visible && (panel.getVisibility() != View.VISIBLE
            || target.getVisibility() != View.VISIBLE);
        if (!visible) {
            if (panel == morePanel && panel instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) panel;
                if (group.getChildCount() > 0) PanelTransitions.reset(group.getChildAt(0));
            }
            PanelTransitions.hide(target, panel == morePanel ? moreMenuRows() : null);
            if (target != panel) PanelTransitions.hide(panel, null);
            return;
        }
        panel.setVisibility(View.VISIBLE);
        target.setVisibility(View.VISIBLE);
        if (opening && panel != morePanel) PanelTransitions.enter(target, isLandscapeLayout(), null);
    }

    void bringAdaptivePanelToFront(View panel) {
        if (panel == null) return;
        View container = landscapePanelContainers.get(panel);
        (container == null ? panel : container).bringToFront();
    }

    void updateLandscapePanelHostVisibility() {
        if (landscapePanelHost == null) return;
        int visibility = activePanel == null
            || activePanel.isEmpty()
            || "people".equals(activePanel)
            ? View.GONE
            : View.VISIBLE;
        if (landscapePanelHost.getVisibility() == visibility) return;
        landscapePanelHost.setVisibility(visibility);
        if (stage != null && treeView != null) {
            stage.post(() -> {
                if (treeView != null && state != null && !state.people.isEmpty()) treeView.fit();
            });
        }
    }

    private LinearLayout buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        boolean landscape = isLandscapeLayout();
        nav.setOrientation(landscape ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        nav.setPadding(
            dp(landscape ? 6 : 8),
            dp(landscape ? 8 : 7),
            dp(landscape ? 6 : 8),
            dp(landscape ? 8 : 6));
        nav.setClipChildren(false);
        nav.setClipToPadding(false);
        nav.setBackground(panelBg(Color.argb(242, 248, 251, 252), 0, Color.argb(32, 63, 82, 94)));
        nav.setElevation(dp(8));
        if (landscape) {
            LinearLayout closeHost = new LinearLayout(this);
            closeHost.setGravity(Gravity.CENTER);
            closeHost.setBackgroundColor(Color.TRANSPARENT);
            closeHost.setVisibility(View.GONE);
            Button closePeople = iconButton(
                R.drawable.ic_menu_close,
                v -> showPanel(""),
                Color.rgb(83, 94, 103));
            closePeople.setContentDescription(tr("Закрыть список людей"));
            closePeople.setBackground(panelBg(
                Color.WHITE,
                dp(10),
                Color.rgb(205, 214, 220)));
            closeHost.addView(closePeople, new LinearLayout.LayoutParams(dp(58), dp(52)));
            nav.addView(closeHost, new LinearLayout.LayoutParams(-1, 0));
            landscapePeopleCloseHost = closeHost;
        }
        treeNav = navButton("Дерево", R.drawable.ic_nav_tree, v -> {
            togglePanel("view");
            trainingTargetActivated("tree-menu");
        });
        nav.addView(treeNav, navigationItemParams(landscape));
        cardNav = navButton("Карточка", R.drawable.ic_nav_card, v -> {
            togglePanel("card");
            trainingTargetActivated("card-menu");
        });
        nav.addView(cardNav, navigationItemParams(landscape));
        peopleNav = navButton("Люди", R.drawable.ic_menu_people, v -> {
            togglePanel("people");
            trainingTargetActivated("people-menu");
        });
        peopleNav.setTextSize(11);
        peopleNav.setStateListAnimator(null);
        FrameLayout peopleSlot = new FrameLayout(this);
        peopleSlot.setClipChildren(false);
        peopleSlot.setClipToPadding(false);
        FrameLayout.LayoutParams peopleButtonParams = new FrameLayout.LayoutParams(-1, -1);
        peopleButtonParams.setMargins(
            landscape ? 0 : dp(3),
            landscape ? dp(2) : 0,
            landscape ? 0 : dp(3),
            landscape ? dp(2) : dp(6));
        peopleSlot.addView(peopleNav, peopleButtonParams);
        nav.addView(peopleSlot, navigationItemParams(landscape));
        linksNav = navButton("Связи", R.drawable.ic_nav_links, v -> {
            togglePanel("links");
            trainingTargetActivated("links-menu");
        });
        nav.addView(linksNav, navigationItemParams(landscape));
        moreNav = navButton("Ещё", R.drawable.ic_nav_more, v -> {
            if ("files".equals(activePanel)) {
                showPanel("");
                return;
            }
            togglePanel("more");
            trainingTargetActivated("more-menu");
        });
        nav.addView(moreNav, navigationItemParams(landscape));
        return nav;
    }

    private LinearLayout.LayoutParams navigationItemParams(boolean landscape) {
        return landscape
            ? new LinearLayout.LayoutParams(-1, 0, 1)
            : new LinearLayout.LayoutParams(0, -1, 1);
    }

    private View buildMorePanel() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(52, 15, 32, 38));
        // Keep the popup above the header/zoom side controls, while the bottom
        // navigation (elevation 8dp) stays available to close or switch sections.
        overlay.setElevation(dp(7));
        overlay.setOnClickListener(v -> showPanel(""));

        LinearLayout bubbles = new LinearLayout(this);
        bubbles.setOrientation(LinearLayout.VERTICAL);
        bubbles.setGravity(Gravity.RIGHT);
        bubbles.setPadding(dp(10), dp(10), dp(10), dp(10));
        bubbles.setBackground(panelBg(Color.argb(246, 248, 251, 252), dp(24), Color.argb(72, 24, 169, 153)));
        bubbles.setElevation(dp(14));
        bubbles.setOnClickListener(v -> { });
        trainingMoreMenuTarget = bubbles;
        bubbles.addView(onlineTreeBubble(), onlineTreeBubbleParams());
        bubbles.addView(moreBubble(R.drawable.ic_workshop_storefront, "Мастерская", "Жетоны, оформление и бонусы", () -> {
            showPanel("");
            openWorkshop();
        }), moreBubbleParams(false));
        bubbles.addView(moreBubble(R.drawable.ic_nav_files, "Файлы и экспорт", "Сохранение, импорт и отправка", () -> {
            showPanel("files");
        }), moreBubbleParams(false));
        bubbles.addView(moreBubble(R.drawable.ic_menu_statistics, "Статистика дерева",
            "Родство, возраст и дети", () -> {
                showPanel("");
                MainActivityStatistics.show(this, state.people.containsKey(state.rootId) ? state.rootId : state.selectedId);
            }), moreBubbleParams(false));
        bubbles.addView(moreBubble(R.drawable.ic_editor_archive, "Сохранённые версии", "Последние локальные копии", () -> {
            showPanel("");
            filesModule.openVersions();
        }), moreBubbleParams(false));
        bubbles.addView(moreBubble(R.drawable.ic_nav_more, "Параметры", "Вид, защита и оформление", () -> {
            showPanel("settings");
        }), moreBubbleParams(false));
        bubbles.addView(moreBubble(R.drawable.ic_menu_search, "Обучение", "Подсказки по возможностям", () -> {
            showPanel("");
            startTraining();
        }), moreBubbleParams(false));
        bubbles.addView(moreBubble(R.drawable.ic_menu_info, "О нас", "Команда, версия и что нового", () -> {
            showPanel("");
            showAbout();
        }), moreBubbleParams(true));

        if (isLandscapeLayout()) {
            overlay.setBackgroundColor(AppThemePalette.surface(Color.rgb(248, 251, 252)));
            bubbles.setGravity(Gravity.TOP);
            bubbles.setBackgroundColor(Color.TRANSPARENT);
            ScrollView scroll = new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setClipToPadding(false);
            scroll.addView(bubbles, new ScrollView.LayoutParams(-1, -2));
            overlay.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        } else {
            int menuWidth = Math.min(dp(320), getResources().getDisplayMetrics().widthPixels - dp(24));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(menuWidth, -2, Gravity.RIGHT | Gravity.BOTTOM);
            params.setMargins(dp(12), dp(12), dp(12), dp(86));
            ScrollView scroll = new ScrollView(this);
            scroll.setClipToPadding(false);
            scroll.addView(bubbles, new ScrollView.LayoutParams(-1, -2));
            overlay.addView(scroll, params);
        }
        overlay.setVisibility(View.GONE);
        return overlay;
    }

    private View onlineTreeBubble() {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setPadding(dp(10), dp(8), dp(12), dp(8));
        bubble.setBackground(tealGradientBg(dp(20)));
        bubble.setElevation(dp(8));
        bubble.setContentDescription(tr(
            "Онлайн-дерево. Синхронизация, резервные копии и доступ близких"));
        bubble.setOnClickListener(v -> {
            showPanel("");
            openOnlineMenu();
        });

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_menu_upload);
        icon.setColorFilter(Color.WHITE);
        icon.setPadding(dp(12), dp(12), dp(12), dp(12));
        icon.setBackground(panelBg(
            Color.argb(42, 255, 255, 255),
            dp(999),
            Color.argb(68, 255, 255, 255)));
        bubble.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(6), 0);
        TextView title = new LocalizedTextView(this);
        title.setText("Онлайн-дерево");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setTypeface(uiBold());
        title.setSingleLine(true);
        copy.addView(title, new LinearLayout.LayoutParams(-1, dp(27)));
        TextView detail = new LocalizedTextView(this);
        detail.setText("Синхронизация, резервные копии и доступ близких");
        detail.setTextColor(Color.argb(225, 255, 255, 255));
        detail.setTextSize(10);
        detail.setMaxLines(2);
        detail.setLineSpacing(dp(1), 1f);
        copy.addView(detail, new LinearLayout.LayoutParams(-1, -2));
        bubble.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView arrow = new LocalizedTextView(this);
        LocalizedViews.setRaw(arrow, "›");
        arrow.setTextColor(Color.WHITE);
        arrow.setTextSize(28);
        arrow.setGravity(Gravity.CENTER);
        bubble.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(48)));
        return bubble;
    }

    private LinearLayout.LayoutParams onlineTreeBubbleParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(78));
        params.setMargins(0, 0, 0, dp(12));
        return params;
    }

    void openWorkshop() {
        if (workshopModule != null) workshopModule.open();
    }

    private View moreBubble(int iconRes, String title, String detail, Runnable action) {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setGravity(Gravity.CENTER_VERTICAL);
        bubble.setPadding(dp(8), dp(6), dp(12), dp(6));
        bubble.setBackground(panelBg(Color.WHITE, dp(999), Color.argb(68, 24, 169, 153)));
        bubble.setElevation(dp(4));
        bubble.setOnClickListener(v -> action.run());

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.WHITE);
        icon.setPadding(dp(12), dp(12), dp(12), dp(12));
        icon.setBackground(tealGradientBg(dp(999)));
        bubble.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, 0, 0);
        TextView label = new LocalizedTextView(this);
        label.setText(title);
        label.setTextColor(Color.rgb(28, 34, 38));
        label.setTextSize(12);
        label.setTypeface(uiBold());
        label.setSingleLine(true);
        copy.addView(label, new LinearLayout.LayoutParams(-1, dp(24)));
        TextView hint = new LocalizedTextView(this);
        hint.setText(detail);
        hint.setTextColor(Color.rgb(101, 113, 122));
        hint.setTextSize(8);
        hint.setSingleLine(true);
        copy.addView(hint, new LinearLayout.LayoutParams(-1, dp(20)));
        bubble.addView(copy, new LinearLayout.LayoutParams(0, dp(48), 1));
        return bubble;
    }

    private LinearLayout.LayoutParams moreBubbleParams(boolean last) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(62));
        params.setMargins(0, 0, 0, last ? 0 : dp(10));
        return params;
    }

    private void showAbout() {
        if (aboutModule != null) aboutModule.open();
    }

    private void showDeveloperMenu() {
        if (layoutTraining == null) layoutTraining = new LayoutTrainingStore(this);
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = editorDialogShell();
        shell.addView(editorDialogHeader(
            dialog,
            R.drawable.ic_menu_data_object,
            "Меню разработчика",
            "Инструменты расстановки дерева"));

        TextView warning = aboutText(
            "Настройки действуют только на этом устройстве. Журналы не содержат ФИО, фото и заметок.",
            10,
            Color.rgb(83, 94, 103),
            false,
            Gravity.LEFT);
        warning.setPadding(dp(4), dp(8), dp(4), dp(10));
        shell.addView(warning, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.addView(menuRow(
            R.drawable.ic_menu_data_object,
            "ИИ-логи тестирования",
            layoutTraining.loggingEnabled() ? "Включены временно" : "Выключены",
            v -> {
                toggleLayoutLogging();
                dialog.dismiss();
            }));
        rows.addView(menuRow(
            R.drawable.ic_menu_copy,
            "Скопировать ИИ-логи",
            "Скопировать приватный журнал расстановки в буфер обмена",
            v -> {
                copyLayoutLogs();
                dialog.dismiss();
            }));
        if (BuildConfig.DEBUG) {
            rows.addView(menuRow(
                R.drawable.ic_workshop_tokens,
                "Выдать 100 жетонов",
                "Для тестирования Мастерской без просмотра рекламы",
                v -> {
                    if (workshopModule != null) workshopModule.grantDebugTokens();
                    toast("Начислено 100 жетонов");
                    dialog.dismiss();
                }));
        }
        shell.addView(rows, new LinearLayout.LayoutParams(-1, -2));

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) return;
        int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(20), dp(520));
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.width = width;
        attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
        attrs.dimAmount = 0.48f;
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setAttributes(attrs);
    }

    private TextView aboutText(String value, float size, int color, boolean bold, int gravity) {
        TextView text = new LocalizedTextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(uiColor(color));
        text.setTypeface(bold ? uiBold() : ui());
        text.setGravity(gravity);
        text.setLineSpacing(dp(2), 1f);
        return text;
    }


    private LinearLayout buildCardPanel() {
        LinearLayout panel = basePanel();
        panel.setPadding(dp(10), dp(8), dp(10), dp(2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(cardActionTile(
            R.drawable.ic_nav_card,
            "Открыть карточку",
            "Профиль, память и связи",
            v -> openPersonEditor(),
            true), new LinearLayout.LayoutParams(0, dp(112), 1));
        LinearLayout.LayoutParams quickParams = new LinearLayout.LayoutParams(0, dp(112), 1);
        quickParams.setMargins(dp(10), 0, 0, 0);
        actions.addView(cardActionTile(
            R.drawable.ic_menu_add_person,
            "Быстрый старт",
            "Создать основу дерева",
            v -> quickStart(),
            true), quickParams);
        panel.addView(actions, new LinearLayout.LayoutParams(-1, dp(112)));

        LinearLayout cardActions = new LinearLayout(this);
        cardActions.setOrientation(LinearLayout.VERTICAL);
        cardActions.setPadding(0, dp(8), 0, 0);
        cardActions.addView(menuRow(
            R.drawable.ic_menu_palette,
            "Цвет по ФИО",
            "Назначает выбранной карточке стабильный цвет по полному имени.",
            v -> recolorSelected()));
        cardActions.addView(menuRow(
            R.drawable.ic_menu_family_color,
            "Цвет по фамилии",
            "Окрашивает семейные ветви одинаково по фамилии.",
            v -> recolorByFamily()));
        cardActions.addView(menuRow(
            R.drawable.ic_menu_trash,
            "Удалить карточку",
            "Удаляет выбранного человека после подтверждения.",
            v -> confirmDelete(),
            true));
        panel.addView(cardActions, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout editorHost = new LinearLayout(this);
        editorHost.setOrientation(LinearLayout.VERTICAL);
        editorHost.setVisibility(View.GONE);
        nameInput = field("Имя");
        editorHost.addView(nameInput);
        LinearLayout years = new LinearLayout(this);
        years.setOrientation(LinearLayout.HORIZONTAL);
        bornInput = field("Год рождения");
        diedInput = field("Год ухода");
        years.addView(bornInput, new LinearLayout.LayoutParams(0, -2, 1));
        years.addView(diedInput, new LinearLayout.LayoutParams(0, -2, 1));
        editorHost.addView(years);
        placeInput = field("Место");
        notesInput = field("Заметки");
        notesInput.setMinLines(2);
        editorHost.addView(placeInput);
        editorHost.addView(notesInput);
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!bindingEditor) updateSelectedFromEditor();
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        nameInput.addTextChangedListener(watcher);
        bornInput.addTextChangedListener(watcher);
        diedInput.addTextChangedListener(watcher);
        placeInput.addTextChangedListener(watcher);
        notesInput.addTextChangedListener(watcher);

        panel.addView(editorHost, new LinearLayout.LayoutParams(-1, 0));
        return panel;
    }

    private View cardActionTile(int iconRes, String label, String detail, View.OnClickListener listener) {
        return cardActionTile(iconRes, label, detail, listener, false);
    }

    private View cardActionTile(
        int iconRes,
        String label,
        String detail,
        View.OnClickListener listener,
        boolean accent
    ) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        boolean landscape = isLandscapeLayout();
        tile.setPadding(
            dp(landscape ? 6 : 10),
            dp(landscape ? 6 : 12),
            dp(landscape ? 6 : 10),
            dp(landscape ? 6 : 10));
        tile.setBackground(accent
            ? panelBg(Color.WHITE, dp(10), AppThemePalette.alpha(AppThemePalette.secondaryBright(), 82))
            : panelBg(Color.WHITE, dp(10), Color.rgb(217, 224, 229)));
        tile.setOnClickListener(listener);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(uiColor(accent ? AppThemePalette.secondary() : AppThemePalette.primary()));
        tile.addView(icon, new LinearLayout.LayoutParams(dp(landscape ? 26 : 30), dp(landscape ? 26 : 30)));
        TextView text = cardActionTitle(label, false);
        if (accent) text.setTextColor(AppThemePalette.secondary());
        text.setGravity(Gravity.CENTER);
        if (landscape) {
            text.setSingleLine(false);
            text.setMaxLines(2);
            text.setTextSize(10);
        }
        tile.addView(text, new LinearLayout.LayoutParams(-1, dp(landscape ? 34 : 28)));
        TextView sub = cardActionDetail(detail, false);
        sub.setGravity(Gravity.CENTER);
        if (landscape) {
            sub.setSingleLine(false);
            sub.setMaxLines(2);
            sub.setTextSize(8);
        }
        tile.addView(sub, new LinearLayout.LayoutParams(-1, dp(landscape ? 30 : 22)));
        return tile;
    }

    TextView cardActionTitle(String value, boolean danger) {
        TextView text = new LocalizedTextView(this);
        text.setText(value);
        text.setTextColor(danger ? Color.rgb(197, 83, 75) : Color.rgb(28, 34, 38));
        text.setTextSize(13);
        text.setTypeface(uiBold());
        text.setSingleLine(true);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setIncludeFontPadding(false);
        return text;
    }

    TextView cardActionDetail(String value, boolean danger) {
        TextView text = new LocalizedTextView(this);
        text.setText(value);
        text.setTextColor(danger ? Color.rgb(173, 91, 84) : Color.rgb(101, 113, 122));
        text.setTextSize(9);
        text.setSingleLine(true);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setIncludeFontPadding(false);
        return text;
    }


    private LinearLayout buildFilesPanel() {
        LinearLayout panel = basePanel();
        panel.setPadding(dp(10), dp(8), dp(10), dp(2));
        LinearLayout primary = new LinearLayout(this);
        primary.setOrientation(LinearLayout.HORIZONTAL);
        primary.addView(cardActionTile(
            R.drawable.ic_menu_upload,
            "Поделиться деревом",
            "Один файл со всеми вложениями",
            v -> filesModule.shareTreePackage(),
            true), new LinearLayout.LayoutParams(0, dp(112), 1));
        LinearLayout.LayoutParams pngParams = new LinearLayout.LayoutParams(0, dp(112), 1);
        pngParams.setMargins(dp(10), 0, 0, 0);
        primary.addView(cardActionTile(
            R.drawable.ic_menu_image,
            "Сохранить PNG",
            "Предпросмотр и точные настройки",
            v -> filesModule.showPngSaveSettings(),
            true), pngParams);
        panel.addView(primary, new LinearLayout.LayoutParams(-1, dp(112)));
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(8), 0, 0);
        panel.addView(rows, new LinearLayout.LayoutParams(-1, -2));
        rows.addView(menuRow(R.drawable.ic_menu_save, "Сохранить", "Сохраняет файл дерева в выбранную папку на телефоне.", v -> filesModule.showSaveMenu()));
        rows.addView(menuRow(
            R.drawable.ic_editor_archive,
            "Сохранённые версии",
            "Показывает 10 последних локальных версий и позволяет восстановить любую из них.",
            v -> filesModule.openVersions()));
        rows.addView(menuRow(R.drawable.ic_menu_import, "Импорт", "Загружает .ftree, JSON, GEDCOM и ZIP с фотографиями.", v -> openImport()));
        rows.addView(menuRow(
            R.drawable.ic_menu_export,
            "Экспорт",
            "Отправляет файл дерева в мессенджер, облако или другое приложение.",
            v -> filesModule.showExportMenu()));
        return panel;
    }

    private LinearLayout buildLinksPanel() {
        LinearLayout panel = basePanel();
        panel.setPadding(dp(10), dp(8), dp(10), dp(2));
        LinearLayout primary = new LinearLayout(this);
        primary.setOrientation(LinearLayout.HORIZONTAL);
        primary.addView(cardActionTile(
            R.drawable.ic_menu_parent_line,
            "Родительская линия",
            "Первый выбранный станет родителем",
            v -> startLink("parent"),
            true), new LinearLayout.LayoutParams(0, dp(112), 1));
        LinearLayout.LayoutParams siblingParams = new LinearLayout.LayoutParams(0, dp(112), 1);
        siblingParams.setMargins(dp(10), 0, 0, 0);
        primary.addView(cardActionTile(
            R.drawable.ic_menu_people,
            "Братская линия",
            "Связь брат/сестра",
            v -> startLink("sibling"),
            true), siblingParams);
        panel.addView(primary, new LinearLayout.LayoutParams(-1, dp(112)));

        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, dp(8), 0, 0);
        panel.addView(rows, new LinearLayout.LayoutParams(-1, -2));
        rows.addView(menuRow(R.drawable.ic_menu_heart, "Партнёрская связь", "Нажмите пункт, затем выберите две карточки для связи партнёров.", v -> startLink("family")));
        rows.addView(menuRow(
            R.drawable.ic_menu_route,
            "Кто кому приходится",
            "Выберите две карточки — приложение определит родство в обоих направлениях.",
            v -> startLink("kinship")));
        rows.addView(menuRow(
            R.drawable.ic_menu_eraser,
            "Убрать связь",
            "Нажмите пункт, затем выберите две карточки: связь между ними будет удалена.",
            v -> startLink("erase"),
            true));
        return panel;
    }

    LinearLayout buildGuidePanel() {
        LinearLayout panel = basePanel();
        panel.setPadding(dp(10), dp(8), dp(10), dp(4));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(dp(2), 0, 0, dp(2));
        TextView title = new LocalizedTextView(this);
        title.setText("Линии поколений");
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setTextSize(16);
        title.setTypeface(uiBold());
        title.setIncludeFontPadding(false);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));
        top.addView(closeButton(v -> showPanel("view")), new LinearLayout.LayoutParams(dp(42), dp(42)));
        panel.addView(top);
        panel.addView(guideToggleRow(), formFieldParams());
        panel.addView(guideDraftRow());
        panel.addView(guideColorRow(), formFieldParams());
        panel.addView(guideCaption("ОДНОРАЗОВОЕ ДЕЙСТВИЕ НА ПОЛОТНЕ"), new LinearLayout.LayoutParams(-1, dp(24)));

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.addView(guideModeTile("h", R.drawable.ic_menu_horizontal_guide, "Гориз.", "Линия слева направо"), new LinearLayout.LayoutParams(0, dp(70), 1));
        modes.addView(guideModeTile("v", R.drawable.ic_guide_vertical, "Вертик.", "Линия сверху вниз"), spacedTileParams());
        modes.addView(guideModeTile("erase", R.drawable.ic_menu_eraser, "Удалять", "Тап рядом с линией"), spacedTileParams());
        panel.addView(modes, formFieldParams());

        Button clear = actionButton("Очистить все направляющие", v -> clearGuides());
        clear.setTextColor(Color.rgb(197, 83, 75));
        clear.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_trash, 0, 0, 0);
        clear.setCompoundDrawablePadding(dp(7));
        tintDrawables(clear, Color.rgb(197, 83, 75));
        panel.addView(clear, new LinearLayout.LayoutParams(-1, dp(46)));
        return panel;
    }

    private View guideToggleRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), 0, dp(8), 0);
        row.setBackground(panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        TextView text = new LocalizedTextView(this);
        text.setText(generationLines ? "Линии поколений видны" : "Линии поколений скрыты");
        text.setTextColor(Color.rgb(28, 34, 38));
        text.setTextSize(13);
        text.setTypeface(uiBold());
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setCompoundDrawablesWithIntrinsicBounds(generationLines ? R.drawable.ic_menu_grid_lines : R.drawable.ic_menu_generation_lines_off, 0, 0, 0);
        text.setCompoundDrawablePadding(dp(9));
        tintDrawables(text, Color.rgb(8, 122, 115));
        row.addView(text, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button toggle = actionButton(generationLines ? "Скрыть" : "Показать", v -> toggleGenerationLines());
        row.addView(toggle, new LinearLayout.LayoutParams(dp(104), dp(40)));
        return row;
    }

    private TextView guideCaption(String value) {
        TextView caption = new LocalizedTextView(this);
        caption.setText(value);
        caption.setTextColor(Color.rgb(101, 113, 122));
        caption.setTextSize(10);
        caption.setTypeface(uiBold());
        caption.setGravity(Gravity.CENTER_VERTICAL);
        caption.setIncludeFontPadding(false);
        return caption;
    }

    private View guideModeTile(String mode, int iconRes, String label, String detail) {
        boolean active = mode.equals(activeGuideMode);
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(4), dp(6), dp(4), dp(6));
        tile.setBackground(active
            ? panelBg(Color.rgb(232, 248, 246), dp(8), Color.argb(92, 24, 169, 153))
            : panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        View.OnClickListener listener = v -> startGuideMode(mode);
        tile.setOnClickListener(listener);
        tile.setClickable(true);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(uiColor(active ? Color.rgb(8, 122, 115) : Color.rgb(76, 87, 96)));
        icon.setOnClickListener(listener);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView title = new LocalizedTextView(this);
        title.setText(label);
        title.setTextColor(active ? Color.rgb(8, 122, 115) : Color.rgb(28, 34, 38));
        title.setTextSize(11);
        title.setTypeface(uiBold());
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setIncludeFontPadding(false);
        title.setOnClickListener(listener);
        tile.addView(title, new LinearLayout.LayoutParams(-1, dp(22)));

        TextView sub = new LocalizedTextView(this);
        sub.setText(detail);
        sub.setTextColor(Color.rgb(101, 113, 122));
        sub.setTextSize(8);
        sub.setGravity(Gravity.CENTER);
        sub.setMaxLines(2);
        sub.setIncludeFontPadding(false);
        sub.setOnClickListener(listener);
        tile.addView(sub, new LinearLayout.LayoutParams(-1, 0, 1));
        return tile;
    }

    private String guideModeTitle(String mode) {
        if ("h".equals(mode)) return "Поставить горизонтальную направляющую";
        if ("v".equals(mode)) return "Поставить вертикальную направляющую";
        if ("erase".equals(mode)) return "Удаление направляющих";
        return "Режим не включён";
    }

    private View guideDraftRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(params);
        guideLabelInput = field("Метка");
        guideLabelInput.setSingleLine(true);
        guideLabelInput.setText(guideDraftLabel);
        guideLabelInput.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_edit, 0, 0, 0);
        guideLabelInput.setCompoundDrawablePadding(dp(9));
        tintDrawables(guideLabelInput, Color.rgb(8, 122, 115));
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                guideDraftLabel = text(guideLabelInput).trim().isEmpty() ? "Поколение" : text(guideLabelInput).trim();
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        guideLabelInput.addTextChangedListener(watcher);
        row.addView(guideLabelInput, new LinearLayout.LayoutParams(-1, -1));
        return row;
    }

    private View guideColorRow() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(10), dp(7), dp(10), dp(7));
        block.setBackground(panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        TextView caption = guideCaption("ЦВЕТ ЛИНИИ");
        block.addView(caption, new LinearLayout.LayoutParams(-1, dp(20)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        ColorPickerView slider = new ColorPickerView(this);
        slider.setTag("guide.colorPicker");
        slider.setColor(TreeState.parseColor(guideDraftColor, Color.rgb(47, 125, 117)));
        TextView preview = new LocalizedTextView(this);
        preview.setBackground(colorSwatchBg(slider.color(), dp(999)));
        slider.setListener((color, fromUser) -> {
            guideDraftColor = TreeState.colorString(color);
            preview.setBackground(colorSwatchBg(color, dp(999)));
            if (treeView != null) treeView.setGuideDraft(guideDraftColor, guideDraftLabel);
        });
        row.addView(slider, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        previewParams.setMargins(dp(8), dp(20), 0, 0);
        row.addView(preview, previewParams);
        block.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return block;
    }

    private LinearLayout buildViewPanel() {
        LinearLayout panel = basePanel();
        panel.setPadding(dp(10), dp(8), dp(10), dp(2));
        ScrollView scroll = new ScrollView(this);
        panel.addView(scroll, new LinearLayout.LayoutParams(
            -1,
            isLandscapeLayout() ? 0 : -2,
            isLandscapeLayout() ? 1 : 0));
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        LinearLayout primary = new LinearLayout(this);
        primary.setOrientation(LinearLayout.HORIZONTAL);
        primary.addView(cardActionTile(
            R.drawable.ic_menu_layout,
            "Упорядочить",
            "Перестроить дерево по поколениям",
            v -> startManualArrange(),
            true), new LinearLayout.LayoutParams(0, dp(112), 1));
        LinearLayout.LayoutParams guidesParams = new LinearLayout.LayoutParams(0, dp(112), 1);
        guidesParams.setMargins(dp(10), 0, 0, 0);
        primary.addView(cardActionTile(
            R.drawable.ic_menu_grid_lines,
            "Линии поколений",
            "Направляющие и подписи уровней",
            v -> togglePanel("guides"),
            true), guidesParams);
        LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(-1, dp(112));
        primaryParams.setMargins(0, 0, 0, dp(8));
        rows.addView(primary, primaryParams);
        rows.addView(menuRow(R.drawable.ic_menu_frame, "Рамка", "Выделяет карточки прямоугольной рамкой.", v -> startSelectionMode("rect")));
        rows.addView(menuRow(R.drawable.ic_menu_lasso, "Лассо", "Выделяет карточки свободной линией.", v -> startSelectionMode("lasso")));
        rows.addView(menuRow(R.drawable.ic_menu_branch, "Отображение ветки", "Выберите, какую часть дерева показать.", v -> togglePanel("branch")));
        return panel;
    }

    private void startManualArrange() {
        startManualArrange(false);
    }

    private void startManualArrange(boolean automatic) {
        if (!requireEditingEnabled()) return;
        if (arrangeInProgress) {
            toast("Упорядочивание уже выполняется");
            return;
        }
        if (state == null || state.people.isEmpty()) {
            toast("В дереве пока нет карточек");
            return;
        }

        arrangeInProgress = true;
        TreeState working = TreeStateCopier.copy(state);
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(16), dp(14), dp(16), dp(16));
        shell.setBackground(panelBg(Color.rgb(250, 252, 253), dp(18), Color.argb(56, 63, 82, 94)));

        TextView title = cardActionTitle("Упорядочивание дерева", false);
        title.setTextSize(19);
        shell.addView(title, new LinearLayout.LayoutParams(-1, dp(30)));
        TextView detail = cardActionDetail("Готовим семейные ветки…", false);
        detail.setTextSize(12);
        detail.setMaxLines(2);
        shell.addView(detail, new LinearLayout.LayoutParams(-1, dp(42)));

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(Math.max(1, working.people.size()));
        progress.setProgress(0);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(22));
        progressParams.setMargins(0, dp(8), 0, dp(12));
        shell.addView(progress, progressParams);

        Button cancel = actionButton("Отмена", v -> {
            cancelled.set(true);
            detail.setText("Отменяем после текущего шага…");
        });
        cancel.setTextColor(Color.rgb(197, 83, 75));
        cancel.setBackground(panelBg(Color.rgb(255, 244, 241), dp(12), Color.argb(76, 197, 83, 75)));
        shell.addView(cancel, new LinearLayout.LayoutParams(-1, dp(46)));

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(ignored -> cancelled.set(true));
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(440));
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.dimAmount = 0.34f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }

        new Thread(() -> {
            final Throwable[] failure = {null};
            boolean completed = false;
            try {
                completed = TreeLayoutEngine.layoutFresh(working, new TreeLayoutEngine.RebuildProgress() {
                    @Override public boolean isCancelled() {
                        return cancelled.get();
                    }

                    @Override public void onProgress(int current, int total, String message) {
                        runOnUiThread(() -> {
                            progress.setMax(Math.max(1, total));
                            // Let ProgressBar interpolate between real engine
                            // checkpoints instead of jumping to the next one.
                            int bounded = Math.max(0, Math.min(current, total));
                            progress.setProgress(bounded, true);
                            detail.setText(message == null || message.trim().isEmpty()
                                ? "Размещаем карточки…"
                                : message + " · " + Math.round(100f * bounded / Math.max(1, total)) + "%");
                        });
                    }
                });
            } catch (Throwable error) {
                failure[0] = error;
            }
            boolean success = completed;
            runOnUiThread(() -> {
                Runnable complete = () -> finishManualArrange(
                    dialog, working, cancelled.get(), success, failure[0], automatic);
                if (success && failure[0] == null && !cancelled.get()) {
                    progress.setProgress(progress.getMax(), true);
                    progress.postDelayed(complete, 180L);
                } else {
                    complete.run();
                }
            });
        }, "tree-manual-arrange").start();
    }

    private void startSelectedArrange() {
        if (!requireEditingEnabled()) return;
        if (arrangeInProgress) {
            toast("Упорядочивание уже выполняется");
            return;
        }
        if (state == null || state.people.isEmpty()) {
            toast("В дереве пока нет карточек");
            return;
        }
        java.util.LinkedHashSet<String> scope = selectedArrangeIds();
        if (scope.size() < 2) {
            toast("Выберите минимум две карточки или активную ветку");
            return;
        }

        arrangeInProgress = true;
        final java.util.LinkedHashSet<String> arrangedIds = new java.util.LinkedHashSet<>(scope);
        final String anchorId = arrangeAnchorId(arrangedIds);
        final String titleText = arrangedIds.size() + " карточек";
        TreeState working = TreeStateCopier.copy(state);
        LayoutTrainingStore.Session trainingSession = layoutTraining == null
            ? null
            : layoutTraining.beginAutoArrange(working, arrangedIds, anchorId, "selected-arrange");
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(16), dp(14), dp(16), dp(16));
        shell.setBackground(panelBg(Color.rgb(250, 252, 253), dp(18), Color.argb(56, 63, 82, 94)));

        TextView title = cardActionTitle("Упорядочивание выбранного", false);
        title.setTextSize(19);
        shell.addView(title, new LinearLayout.LayoutParams(-1, dp(30)));
        TextView detail = cardActionDetail("Готовим " + titleText + "…", false);
        detail.setTextSize(12);
        detail.setMaxLines(2);
        shell.addView(detail, new LinearLayout.LayoutParams(-1, dp(42)));

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(Math.max(1, arrangedIds.size()));
        progress.setProgress(0);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(22));
        progressParams.setMargins(0, dp(8), 0, dp(12));
        shell.addView(progress, progressParams);

        Button cancel = actionButton("Отмена", v -> {
            cancelled.set(true);
            detail.setText("Отменяем после текущего шага…");
        });
        cancel.setTextColor(Color.rgb(197, 83, 75));
        cancel.setBackground(panelBg(Color.rgb(255, 244, 241), dp(12), Color.argb(76, 197, 83, 75)));
        shell.addView(cancel, new LinearLayout.LayoutParams(-1, dp(46)));

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(ignored -> cancelled.set(true));
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(440));
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.dimAmount = 0.34f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }

        new Thread(() -> {
            final Throwable[] failure = {null};
            boolean completed = false;
            try {
                completed = TreeLayoutEngine.rebuildSelected(working, arrangedIds, anchorId, new TreeLayoutEngine.RebuildProgress() {
                    @Override public boolean isCancelled() {
                        return cancelled.get();
                    }

                    @Override public void onProgress(int current, int total, String message) {
                        runOnUiThread(() -> {
                            progress.setMax(Math.max(1, total));
                            int bounded = Math.max(0, Math.min(current, total));
                            progress.setProgress(bounded, true);
                            detail.setText(message == null || message.trim().isEmpty()
                                ? "Размещаем выбранные карточки…"
                                : message + " · " + Math.round(100f * bounded / Math.max(1, total)) + "%");
                        });
                    }
                });
            } catch (Throwable error) {
                failure[0] = error;
            }
            boolean success = completed;
            runOnUiThread(() -> {
                Runnable complete = () -> finishSelectedArrange(
                    dialog,
                    working,
                    arrangedIds,
                    cancelled.get(),
                    success,
                    failure[0],
                    trainingSession);
                if (success && failure[0] == null && !cancelled.get()) {
                    progress.setProgress(progress.getMax(), true);
                    progress.postDelayed(complete, 180L);
                } else {
                    complete.run();
                }
            });
        }, "tree-selected-arrange").start();
    }

    private void finishManualArrange(
        Dialog dialog,
        TreeState arranged,
        boolean cancelled,
        boolean success,
        Throwable failure,
        boolean automatic
    ) {
        arrangeInProgress = false;
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        if (failure != null) {
            DiagnosticsLogger.handled(this, "manual-arrange", failure);
            toast("Не удалось упорядочить дерево");
            return;
        }
        if (cancelled || !success || arranged == null) {
            toast("Упорядочивание отменено");
            return;
        }
        if (!automatic) recordUndo("Упорядочено дерево");
        applyArrangedLayout(arranged);
        workspaceWidth = TreeLayoutEngine.normalizeSurfaceWidth(state.workspaceWidth);
        workspaceHeight = TreeLayoutEngine.normalizeSurfaceHeight(state.workspaceHeight);
        treeView.setWorkspaceSize(workspaceWidth, workspaceHeight);
        bindState();
        saveToast(automatic ? "Карточка добавлена, дерево упорядочено" : "Дерево упорядочено");
        treeView.invalidate();
    }

    private void finishSelectedArrange(
        Dialog dialog,
        TreeState arranged,
        java.util.Collection<String> arrangedIds,
        boolean cancelled,
        boolean success,
        Throwable failure,
        LayoutTrainingStore.Session trainingSession
    ) {
        arrangeInProgress = false;
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        if (failure != null) {
            DiagnosticsLogger.handled(this, "selected-arrange", failure);
            if (layoutTraining != null) layoutTraining.finishAutoArrange(trainingSession, arranged, false);
            toast("Не удалось упорядочить выбранное");
            return;
        }
        if (cancelled || !success || arranged == null) {
            if (layoutTraining != null) layoutTraining.finishAutoArrange(trainingSession, arranged, false);
            toast("Упорядочивание отменено");
            return;
        }
        recordUndo("Упорядочено выбранное", arrangedIds == null ? "" : arrangedIds.size() + " карточек");
        applyArrangedLayout(arranged, arrangedIds);
        workspaceWidth = TreeLayoutEngine.normalizeSurfaceWidth(state.workspaceWidth);
        workspaceHeight = TreeLayoutEngine.normalizeSurfaceHeight(state.workspaceHeight);
        treeView.setWorkspaceSize(workspaceWidth, workspaceHeight);
        bindState();
        if (layoutTraining != null) layoutTraining.finishAutoArrange(trainingSession, state, true);
        saveToast("Выбранное упорядочено");
        treeView.invalidate();
    }

    private void applyArrangedLayout(TreeState arranged) {
        if (state == null || arranged == null) return;
        for (Person person : state.people.values()) {
            Person next = arranged.people.get(person.id);
            if (next == null) continue;
            person.x = next.x;
            person.y = next.y;
        }
        state.workspaceWidth = arranged.workspaceWidth;
        state.workspaceHeight = arranged.workspaceHeight;
    }

    private void applyArrangedLayout(
        TreeState arranged,
        java.util.Collection<String> ids
    ) {
        if (state == null || arranged == null || ids == null) return;
        for (String id : ids) {
            Person person = state.people.get(id);
            Person next = arranged.people.get(id);
            if (person == null || next == null || person.pinned) continue;
            person.x = next.x;
            person.y = next.y;
        }
        state.workspaceWidth = arranged.workspaceWidth;
        state.workspaceHeight = arranged.workspaceHeight;
    }

    private java.util.LinkedHashSet<String> selectedArrangeIds() {
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        if (treeView != null) ids.addAll(treeView.selectedIds());
        ids.removeIf(id -> state == null || !state.people.containsKey(id));
        if (ids.size() >= 2) return ids;
        ids.clear();
        if (state == null) return ids;
        if (!"all".equals(branchMode) && branchAnchorId != null && !branchAnchorId.isEmpty()) {
            ids.addAll(branchIds(branchMode, branchAnchorId));
            ids.removeIf(id -> !state.people.containsKey(id));
            if (ids.size() >= 2) return ids;
            ids.clear();
        }
        Person selected = state.selectedPerson();
        if (selected == null) return ids;
        ids.addAll(state.descendantsOf(selected.id));
        if (ids.size() < 2) {
            ids.clear();
            ids.addAll(state.nearOf(selected.id));
        }
        ids.removeIf(id -> !state.people.containsKey(id));
        return ids;
    }

    private java.util.Set<String> branchIds(String mode, String anchorId) {
        if (state == null || anchorId == null || anchorId.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        if ("ancestors".equals(mode)) return state.ancestorsOf(anchorId);
        if ("descendants".equals(mode)) return state.descendantsOf(anchorId);
        if ("near".equals(mode)) return state.nearOf(anchorId, nearGenerations);
        return new java.util.LinkedHashSet<>(state.people.keySet());
    }

    private String arrangeAnchorId(java.util.Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return "";
        if (state != null && state.selectedId != null && ids.contains(state.selectedId)) {
            return state.selectedId;
        }
        if (branchAnchorId != null && ids.contains(branchAnchorId)) return branchAnchorId;
        return ids.iterator().next();
    }

    private LinearLayout buildBranchPanel() {
        LinearLayout panel = basePanel();
        panel.setPadding(dp(10), dp(8), dp(10), dp(4));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new LocalizedTextView(this);
        title.setText("Отображение ветки");
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setTextSize(14);
        title.setTypeface(uiBold());
        title.setIncludeFontPadding(false);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));
        top.addView(closeButton(v -> showPanel("view")), new LinearLayout.LayoutParams(dp(42), dp(42)));
        panel.addView(top);
        panel.addView(menuRow(R.drawable.ic_nav_tree, "Всё дерево", "Показывает все карточки и связи.", v -> setBranch("all")));
        panel.addView(menuRow(R.drawable.ic_menu_ancestors, "Предки", "Показывает выбранную карточку и её предков.", v -> setBranch("ancestors")));
        panel.addView(menuRow(R.drawable.ic_menu_descendants, "Потомки", "Показывает выбранную карточку и её потомков.", v -> setBranch("descendants")));
        panel.addView(menuRow(R.drawable.ic_menu_near, "Близкие", "Показывает ближайшие связи выбранной карточки.", v -> setBranch("near")));
        return panel;
    }

    private LinearLayout buildSettingsPanel() {
        LinearLayout panel = basePanel();
        panel.setPadding(dp(14), dp(10), dp(14), 0);
        panel.setElevation(0f);
        panel.setBackgroundColor(AppThemePalette.surface(Color.rgb(248, 251, 252)));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new LocalizedTextView(this);
        title.setText("Параметры");
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setTextSize(isLandscapeLayout() ? 18 : 20);
        title.setTypeface(uiBold());
        title.setIncludeFontPadding(false);
        title.setSingleLine(true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView close = closeButton(v -> showPanel(""));
        close.setContentDescription(tr("Закрыть параметры"));
        top.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        panel.addView(top, new LinearLayout.LayoutParams(-1, dp(44)));
        ScrollView scroll = new ScrollView(this);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        settingsContent = new LinearLayout(this);
        settingsContent.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(settingsContent);
        addSettingsContent(settingsContent);
        return panel;
    }

    void addSettingsContent(LinearLayout panel) {
        View mainSection = settingsSection("Основное");
        mainSection.setPadding(dp(4), dp(3), 0, dp(3));
        panel.addView(mainSection);
        panel.addView(settingActionRow(
            R.drawable.ic_menu_language,
            tr("Язык") + ": " + AppLanguage.modeLabel(this),
            tr("Автоматически использует язык телефона. Можно вручную выбрать русский или английский."),
            v -> showLanguageDialog()));
        panel.addView(settingActionRow(
            R.drawable.ic_menu_training,
            "Обучение",
            "Интерактивно подсвечивает нужные кнопки и проводит через создание карточки, связи, дерево, файлы и параметры.",
            v -> startTraining()));
        panel.addView(settingActionRow(
            R.drawable.ic_menu_route,
            "Степень родства",
            KinshipModeDialog.HELP,
            v -> showKinshipModeDialog()));
        panel.addView(settingSwitchRow(R.drawable.ic_menu_history, "История действий", state == null || !state.historyHidden, "Показывает или скрывает панель последних действий на поле дерева.", v -> toggleHistoryPanel()));
        boolean editingBlocked = editingBlocked();
        panel.addView(settingSwitchRow(
            editingBlocked ? R.drawable.ic_menu_lock : R.drawable.ic_menu_unlock,
            onlineReadOnly ? "Только просмотр" : "Защита правок",
            editingBlocked,
            onlineReadOnly
                ? "Глава дерева временно отключил редактирование для вашего аккаунта."
                : "Блокирует или разрешает редактирование, чтобы не изменить дерево случайно.",
            v -> toggleLock()));
        panel.addView(settingsSection("Визуал"));
        panel.addView(settingSwitchRow(R.drawable.ic_menu_eye, "Значок качества карточки", state.showQualityBadge,
            "Показывает на карточках небольшой значок ошибок, предупреждений или полноты данных. Проверка качества дерева продолжает работать и при скрытых значках.",
            v -> { state.showQualityBadge = !state.showQualityBadge; saveOnly(); treeView.invalidate(); refreshSettingsIfVisible(); }));
        panel.addView(settingSwitchRow(R.drawable.ic_life_heart_outline, "Значок статуса жизни", state.showLifeStatusBadge,
            "Значок располагается над качеством карточки или на его месте, если качество скрыто. Статус можно указать в профиле человека, а иконки и цвета — выбрать ниже.",
            v -> { state.showLifeStatusBadge = !state.showLifeStatusBadge; saveOnly(); treeView.invalidate(); refreshSettingsIfVisible(); }));
        View lifeAppearanceRow = settingActionRow(R.drawable.ic_menu_paint_can, "Иконки и цвета статуса жизни",
            "Выберите символы для живых, умерших и неизвестного статуса, их цвет и фон. Изменения сразу видны на карточках.",
            v -> LifeStatusAppearanceDialog.show(this));
        lifeAppearanceRow.setTag("settings.lifeStatusAppearance");
        panel.addView(lifeAppearanceRow);
        panel.addView(themeRow());
        panel.addView(scaleChoiceRow(
            R.drawable.ic_menu_sliders,
            "Размер шрифта",
            "Мелкий",
            "Обычный",
            "Крупный",
            0.92f,
            1f,
            1.1f,
            fontScale,
            value -> setFontScale(value)));
        panel.addView(scaleChoiceRow(
            R.drawable.ic_menu_expand,
            "Масштаб интерфейса",
            "Компактно",
            "Обычно",
            "Крупно",
            0.9f,
            1f,
            1.15f,
            uiScale,
            value -> setUiScale(value)));
        panel.addView(settingSwitchRow(generationLines ? R.drawable.ic_menu_grid_lines : R.drawable.ic_menu_generation_lines_off, "Линии поколений", generationLines, "Показывает или скрывает горизонтальные линии поколений.", v -> toggleGenerationLines()));
        panel.addView(settingSwitchRow(
            R.drawable.ic_menu_straight_links,
            "Ровные связи",
            "orthogonal".equals(parentLineMode),
            "По умолчанию связи родителей с детьми рисуются плавными кривыми. Этот режим включает ровные прямоугольные связи.",
            v -> toggleParentLineMode()));
        panel.addView(settingSwitchRow(
            R.drawable.ic_nav_links,
            "От середины пары",
            "centered".equals(familyLineMode),
            "Включено — родители используют общий семейный маршрут от середины пары. Выключено — линия идёт отдельно от каждого человека, как раньше.",
            v -> toggleFamilyLineMode()));
        panel.addView(settingSwitchRow(hideCardDetails ? R.drawable.ic_menu_eye_off : R.drawable.ic_menu_eye, "Скрыть детали карточек", hideCardDetails, "Скрывает вторичные детали карточек, оставляя основной текст чище.", v -> toggleHideDetails()));
        panel.addView(settingSwitchRow(compactCards ? R.drawable.ic_menu_compress : R.drawable.ic_menu_expand, "Компактные карточки", compactCards, "Переключает обычные карточки 4 на 7 на компактные 3 на 5. Линии автоматически подстраиваются под размер.", v -> toggleCompactCards()));
        panel.addView(settingSwitchRow(focusTree ? R.drawable.ic_menu_focus_off : R.drawable.ic_menu_focus, "Фокус на дереве", focusTree, "Оставляет больше места дереву и делает нижнюю панель компактнее.", v -> toggleFocusTree()));
        panel.addView(settingsSection("Дерево и данные"));
        panel.addView(menuRow(
            R.drawable.ic_menu_trash,
            "Удалить всё дерево",
            "Удаляет все карточки, связи и линии поколений. Действие можно отменить через историю.",
            v -> confirmDeleteWholeTree(),
            true));
    }

    private View settingsSection(String label) {
        TextView section = new LocalizedTextView(this);
        section.setText(label);
        section.setTextColor(AppThemePalette.secondary());
        section.setTextSize(10);
        section.setTypeface(uiBold());
        section.setGravity(Gravity.LEFT | Gravity.BOTTOM);
        section.setIncludeFontPadding(false);
        section.setPadding(dp(4), dp(8), 0, dp(6));
        return section;
    }

    private View scaleChoiceRow(
        int iconRes,
        String label,
        String smallLabel,
        String normalLabel,
        String largeLabel,
        float smallValue,
        float normalValue,
        float largeValue,
        float current,
        java.util.function.Consumer<Float> setter
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(10));
        row.setBackground(panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(92));
        rowParams.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rowParams);

        TextView title = new LocalizedTextView(this);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setText(label);
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setTextSize(13);
        title.setTypeface(uiBold());
        title.setIncludeFontPadding(false);
        title.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        title.setCompoundDrawablePadding(dp(9));
        tintDrawables(title, AppThemePalette.secondary());
        row.addView(title, new LinearLayout.LayoutParams(-1, dp(30)));

        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        choices.setPadding(dp(3), dp(3), dp(3), dp(3));
        choices.setBackground(panelBg(
            AppThemePalette.isDark() ? Color.rgb(22, 31, 36) : Color.rgb(235, 241, 244),
            dp(9),
            Color.TRANSPARENT));
        choices.addView(scaleChoice(label, smallLabel, smallValue, current, setter), new LinearLayout.LayoutParams(0, -1, 1));
        choices.addView(scaleChoice(label, normalLabel, normalValue, current, setter), new LinearLayout.LayoutParams(0, -1, 1));
        choices.addView(scaleChoice(label, largeLabel, largeValue, current, setter), new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams choicesParams = new LinearLayout.LayoutParams(-1, dp(38));
        choicesParams.setMargins(0, dp(6), 0, 0);
        row.addView(choices, choicesParams);
        return row;
    }

    private TextView scaleChoice(
        String group,
        String label,
        float value,
        float current,
        java.util.function.Consumer<Float> setter
    ) {
        boolean selected = Math.abs(current - value) < 0.06f;
        TextView choice = new LocalizedTextView(this);
        choice.setText(label);
        choice.setGravity(Gravity.CENTER);
        choice.setTextSize(11);
        choice.setTypeface(uiBold());
        choice.setIncludeFontPadding(false);
        choice.setTextColor(selected ? Color.WHITE : Color.rgb(28, 34, 38));
        choice.setBackground(selected
            ? panelBg(AppThemePalette.isDark() ? AppThemePalette.ctaSecondary() : AppThemePalette.secondary(), dp(7), Color.argb(70, 255, 255, 255))
            : panelBg(Color.TRANSPARENT, dp(7), Color.TRANSPARENT));
        choice.setContentDescription(tr(group + ": " + label));
        choice.setOnClickListener(v -> setter.accept(value));
        return choice;
    }

    private void showLanguageDialog() {
        boolean english = AppLanguage.isEnglish(this);
        String current = AppLanguage.mode(this);
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(18), dp(18), dp(16));
        shell.setBackground(panelBg(
            Color.rgb(252, 254, 254),
            dp(22),
            Color.argb(58, 63, 82, 94)));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = new LocalizedTextView(this);
        badge.setText("AЯ");
        badge.setTextSize(16);
        badge.setTypeface(uiBold());
        badge.setTextColor(Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setBackground(tealGradientBg(dp(14)));
        header.addView(badge, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(13), 0, 0, 0);
        TextView title = new LocalizedTextView(this);
        title.setText(english ? "Language" : "Язык");
        title.setTextSize(20);
        title.setTypeface(uiBold());
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setIncludeFontPadding(false);
        copy.addView(title);
        TextView detail = new LocalizedTextView(this);
        detail.setText(english
            ? "Choose once or follow your phone language"
            : "Выберите язык или используйте язык телефона");
        detail.setTextSize(11);
        detail.setTextColor(Color.rgb(83, 94, 103));
        detail.setPadding(0, dp(4), 0, 0);
        detail.setIncludeFontPadding(false);
        copy.addView(detail);
        header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(header);

        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams choicesParams = new LinearLayout.LayoutParams(-1, -2);
        choicesParams.setMargins(0, dp(17), 0, 0);
        shell.addView(choices, choicesParams);

        Runnable selectAuto = () -> applyLanguageChoice(dialog, AppLanguage.AUTO);
        Runnable selectRussian = () -> applyLanguageChoice(dialog, AppLanguage.RUSSIAN);
        Runnable selectEnglish = () -> applyLanguageChoice(dialog, AppLanguage.ENGLISH);
        choices.addView(languageChoiceRow(
            "A",
            english ? "Automatic" : "Автоматически",
            english ? "Use the phone language" : "Использовать язык телефона",
            AppLanguage.AUTO.equals(current),
            selectAuto));
        choices.addView(languageChoiceRow(
            "РУ",
            english ? "Russian" : "Русский",
            english ? "Russian interface" : "Интерфейс на русском языке",
            AppLanguage.RUSSIAN.equals(current),
            selectRussian));
        choices.addView(languageChoiceRow(
            "EN",
            "English",
            english ? "English interface" : "Интерфейс на английском языке",
            AppLanguage.ENGLISH.equals(current),
            selectEnglish));

        Button cancel = actionButton(english ? "Cancel" : "Отмена", v -> dialog.dismiss());
        cancel.setElevation(0f);
        cancel.setBackground(panelBg(Color.WHITE, dp(12), Color.rgb(205, 214, 220)));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, dp(46));
        cancelParams.setMargins(0, dp(8), 0, 0);
        shell.addView(cancel, cancelParams);

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(
                getResources().getDisplayMetrics().widthPixels - dp(28),
                dp(470));
            shell.setMinimumWidth(width);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.dimAmount = 0.34f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
        dialog.show();
    }

    private View languageChoiceRow(
        String mark,
        String title,
        String detail,
        boolean selected,
        Runnable action
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        row.setBackground(panelBg(
            selected ? Color.rgb(232, 248, 246) : Color.WHITE,
            dp(15),
            selected ? Color.argb(112, 24, 169, 153) : Color.rgb(217, 224, 229)));
        row.setClickable(true);
        row.setOnClickListener(v -> action.run());

        TextView icon = new LocalizedTextView(this);
        icon.setText(mark);
        icon.setTextSize(mark.length() > 1 ? 11 : 15);
        icon.setTypeface(uiBold());
        icon.setTextColor(selected ? Color.WHITE : Color.rgb(8, 122, 115));
        icon.setGravity(Gravity.CENTER);
        icon.setIncludeFontPadding(false);
        icon.setBackground(panelBg(
            selected ? Color.rgb(8, 122, 115) : Color.rgb(232, 248, 246),
            dp(999),
            Color.argb(74, 24, 169, 153)));
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(8), 0);
        TextView heading = new LocalizedTextView(this);
        heading.setText(title);
        heading.setTextSize(15);
        heading.setTypeface(uiBold());
        heading.setTextColor(Color.rgb(28, 34, 38));
        heading.setIncludeFontPadding(false);
        copy.addView(heading);
        TextView caption = new LocalizedTextView(this);
        caption.setText(detail);
        caption.setTextSize(11);
        caption.setTextColor(Color.rgb(83, 94, 103));
        caption.setPadding(0, dp(3), 0, 0);
        caption.setIncludeFontPadding(false);
        copy.addView(caption);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView check = new LocalizedTextView(this);
        check.setText(selected ? "✓" : "");
        check.setTextSize(15);
        check.setTypeface(uiBold());
        check.setTextColor(Color.WHITE);
        check.setGravity(Gravity.CENTER);
        check.setBackground(panelBg(
            selected ? Color.rgb(24, 169, 153) : Color.TRANSPARENT,
            dp(999),
            Color.TRANSPARENT));
        row.addView(check, new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(66));
        params.setMargins(0, 0, 0, dp(9));
        row.setLayoutParams(params);
        return row;
    }

    private void applyLanguageChoice(Dialog dialog, String next) {
        if (!next.equals(AppLanguage.mode(this))) {
            AppLanguage.setMode(this, next);
            dialog.dismiss();
            if (saveCoordinator != null) saveCoordinator.saveThen(saved -> { if (saved && !isDestroyed()) recreate(); });
            else recreate();
        } else {
            dialog.dismiss();
        }
    }

    void bindState() {
        syncSettingsToState();
        treeView.setState(state);
        if (qualityModule != null) qualityModule.refresh();
        treeView.setEditLocked(editingBlocked());
        treeView.setGenerationLines(generationLines);
        treeView.setHideDetails(hideCardDetails);
        treeView.setCompactCards(compactCards);
        treeView.setFontScale(fontScale);
        treeView.setWorkspaceSize(workspaceWidth, workspaceHeight);
        treeView.setParentLineMode(parentLineMode);
        treeView.setFamilyLineMode(familyLineMode);
        treeView.setTheme(theme);
        if (workshopModule != null) workshopModule.applyStyles();
        treeView.setNearGenerations(nearGenerations);
        treeView.setBranchMode(branchMode, branchAnchorId);
        treeView.setLinkState(pendingLinkFrom, selectedLinkId);
        bindEditor(state.selectedPerson());
        updateStats();
        updateCanvasModePanel();
        updateHistoryButtons();
        updateHistoryPanel();
        updateBranchStatusPanel();
        updateSelectionToolbar();
        updateDistantCardsWarning();
        refreshLockUi();
    }

    private void applyOnlineTree(TreeState remote, String message) {
        if (remote == null || isFinishing() || isDestroyed()) return;
        TreeState local = state;
        if (local != null) {
            remote.theme = local.theme;
            remote.printScale = local.printScale;
            remote.uiScale = local.uiScale;
            remote.fontScale = local.fontScale;
            remote.editLocked = local.editLocked;
            remote.historyHidden = local.historyHidden;
            remote.inspectorHidden = local.inspectorHidden;
            remote.adminCollapsed = local.adminCollapsed;
            remote.guidesVisible = local.guidesVisible;
            remote.hideCardDetails = local.hideCardDetails;
            remote.useMaidenNames = local.useMaidenNames;
            remote.showQualityBadge = local.showQualityBadge;
            remote.showLifeStatusBadge = local.showLifeStatusBadge;
            remote.lifeStatusAppearance = local.lifeStatusAppearance.copy();
            remote.compactCards = local.compactCards;
            remote.focusTree = local.focusTree;
            remote.autoArrangeOnAdd = local.autoArrangeOnAdd;
            remote.workspaceWidth = local.workspaceWidth;
            remote.workspaceHeight = local.workspaceHeight;
            remote.parentLineMode = local.parentLineMode;
            remote.familyLineMode = local.familyLineMode;
            if (remote.omittedAlbumFields.contains("photoAlbums")) {
                for (java.util.Map.Entry<String, java.util.List<String>> album : local.photoAlbums.entrySet()) {
                    remote.photoAlbums.put(album.getKey(), new java.util.ArrayList<>(album.getValue()));
                }
            }
            if (remote.omittedAlbumFields.contains("photoAlbumMedia")) copyAlbumMedia(local.photoAlbumMedia, remote.photoAlbumMedia);
            if (remote.omittedAlbumFields.contains("familyAlbumMedia")) copyAlbumMedia(local.familyAlbumMedia, remote.familyAlbumMedia);
            if (remote.omittedAlbumFields.contains("personAlbumMedia")) copyAlbumMedia(local.personAlbumMedia, remote.personAlbumMedia);
            if (remote.omittedAlbumFields.contains("photoAlbumFolders")) {
                for (java.util.Map.Entry<String, java.util.List<PhotoAlbumFolder>> entry : local.photoAlbumFolders.entrySet()) {
                    java.util.List<PhotoAlbumFolder> folders = new java.util.ArrayList<>();
                    for (PhotoAlbumFolder original : entry.getValue()) {
                        PhotoAlbumFolder folder = new PhotoAlbumFolder();
                        folder.id = original.id;
                        folder.name = original.name;
                        folder.personIds.addAll(original.personIds);
                        folder.photoMediaIds.addAll(original.photoMediaIds);
                        folders.add(folder);
                    }
                    remote.photoAlbumFolders.put(entry.getKey(), folders);
                }
            }
            if (remote.omittedAlbumFields.contains("familyAlbums")) remote.familyAlbums.addAll(local.familyAlbums);
            if (remote.people.containsKey(local.selectedId)) remote.selectedId = local.selectedId;
        }
        state = remote;
        if (historyModule != null) historyModule.cancelPendingUndo();
        undoStack.clear();
        redoStack.clear();
        resetTransientCanvasModes(false);
        applyStateSettings();
        TreeLayoutEngine.ensurePositions(state);
        bindState();
        if (saveCoordinator != null) saveCoordinator.requestImmediate();
        if (message != null && !message.isEmpty()) toast(message);
    }

    private static void copyAlbumMedia(
        java.util.Map<String, java.util.List<String>> source,
        java.util.Map<String, java.util.List<String>> target
    ) {
        for (java.util.Map.Entry<String, java.util.List<String>> entry : source.entrySet()) {
            target.put(entry.getKey(), new java.util.ArrayList<>(entry.getValue()));
        }
    }

    void openOnlineMenu() {
        if (onlineModule != null) onlineModule.openDashboard();
    }

    String personInitials(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) return "?";
        String[] parts = value.split("\\s+");
        String first = parts[0].isEmpty() ? "" : parts[0].substring(0, 1);
        String second = parts.length > 1 && !parts[1].isEmpty()
            ? parts[1].substring(0, 1)
            : "";
        return (first + second).toUpperCase(Locale.ROOT);
    }

    void applyStateSettings() {
        if (state == null) return;
        editLocked = state.editLocked;
        viewMode = false;
        generationLines = state.guidesVisible;
        hideCardDetails = state.hideCardDetails;
        compactCards = state.compactCards;
        focusTree = state.focusTree;
        uiScale = normalizeUiScale(state.uiScale);
        fontScale = normalizeFontScale(state.fontScale);
        rememberUiScalePreferences();
        workspaceWidth = TreeLayoutEngine.normalizeSurfaceWidth(state.workspaceWidth);
        workspaceHeight = TreeLayoutEngine.normalizeSurfaceHeight(state.workspaceHeight);
        parentLineMode = "orthogonal".equals(state.parentLineMode) ? "orthogonal" : "smart";
        familyLineMode = "individual".equals(state.familyLineMode) ? "individual" : "centered";
        theme = normalizeTheme(state.theme);
        StartupSplash.rememberTheme(this, theme);
    }

    private void syncSettingsToState() {
        if (state == null) return;
        state.editLocked = editLocked;
        state.readerMode = false;
        state.guidesVisible = generationLines;
        state.hideCardDetails = hideCardDetails;
        state.compactCards = compactCards;
        state.focusTree = focusTree;
        state.uiScale = normalizeUiScale(uiScale);
        state.fontScale = normalizeFontScale(fontScale);
        state.workspaceWidth = TreeLayoutEngine.normalizeSurfaceWidth(workspaceWidth);
        state.workspaceHeight = TreeLayoutEngine.normalizeSurfaceHeight(workspaceHeight);
        state.parentLineMode = "orthogonal".equals(parentLineMode) ? "orthogonal" : "smart";
        state.familyLineMode = "individual".equals(familyLineMode) ? "individual" : "centered";
        state.theme = normalizeTheme(theme);
    }

    void bindEditor(Person person) {
        editorModule.bindEditor(person);
    }

    boolean editingBlocked() {
        return editLocked || onlineReadOnly;
    }

    boolean requireEditingEnabled() {
        if (!editingBlocked()) return true;
        showEditingLockedPrompt();
        return false;
    }

    void toggleTreeLockFromRail() {
        toggleLock();
    }

    void refreshLockUi() {
        boolean locked = editingBlocked();
        if (lockRailButton != null) {
            int tint = locked ? Color.rgb(197, 83, 75) : Color.rgb(8, 122, 115);
            lockRailButton.setForeground(centeredIcon(
                locked ? R.drawable.ic_menu_lock : R.drawable.ic_menu_unlock,
                tint));
            lockRailButton.setBackground(panelBg(
                locked ? Color.rgb(255, 241, 239) : Color.WHITE,
                dp(8),
                locked ? Color.argb(112, 197, 83, 75) : Color.rgb(217, 224, 229)));
            lockRailButton.setContentDescription(tr(locked ? "Разблокировать дерево" : "Заблокировать дерево"));
        }
        if (treeHint == null) return;
        treeHint.animate().cancel();
        treeHint.setTranslationX(0f);
        treeHint.setAlpha(1f);
        treeHint.setText("Один палец - движение, два пальца - масштаб");
        treeHint.setTextColor(Color.rgb(101, 113, 122));
        treeHint.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        treeHint.setCompoundDrawablePadding(0);
        treeHint.setBackground(panelBg(
            Color.argb(218, 255, 255, 255),
            dp(8),
            Color.argb(42, 24, 169, 153)));
        if (treeHint.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) treeHint.getLayoutParams();
            params.gravity = Gravity.TOP;
            params.setMargins(
                dp(isLandscapeLayout() ? 64 : 12),
                dp(isLandscapeLayout() ? 8 : 72),
                dp(isLandscapeLayout() ? 12 : 64),
                0);
            treeHint.setLayoutParams(params);
        }
        boolean fullScreenPanel = "settings".equals(activePanel) || "people".equals(activePanel);
        boolean show = !fullScreenPanel && !locked && !focusTree
            && !treeHintDismissed && !treeHintAutoHidden;
        treeHint.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    void showEditingLockedPrompt() {
        if (!editingBlocked() || isFinishing() || isDestroyed()) return;
        toastHandler.removeCallbacks(dismissLockedEditPromptRunnable);
        if (lockedEditPromptDialog != null && lockedEditPromptDialog.isShowing()) {
            toastHandler.postDelayed(dismissLockedEditPromptRunnable, 3600L);
            return;
        }

        Dialog dialog = new Dialog(this);
        lockedEditPromptDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(9), dp(10), dp(9));
        card.setBackground(panelBg(
            Color.rgb(255, 249, 248),
            dp(18),
            Color.argb(94, 197, 83, 75)));
        card.setElevation(dp(12));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_menu_lock);
        icon.setColorFilter(AppThemePalette.text(Color.rgb(197, 83, 75)));
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(panelBg(Color.rgb(255, 235, 232), dp(13), Color.TRANSPARENT));
        card.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, dp(6), 0);
        TextView title = new LocalizedTextView(this);
        title.setText("Дерево заблокировано");
        title.setTextColor(Color.rgb(126, 49, 45));
        title.setTextSize(13);
        title.setTypeface(uiBold());
        title.setSingleLine(true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(title, new LinearLayout.LayoutParams(-1, dp(23)));
        TextView detail = new LocalizedTextView(this);
        detail.setText(onlineReadOnly
            ? "Доступ только для просмотра"
            : "Разблокируйте дерево, чтобы изменить данные");
        detail.setTextColor(Color.rgb(128, 88, 84));
        detail.setTextSize(9);
        detail.setSingleLine(true);
        detail.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(detail, new LinearLayout.LayoutParams(-1, dp(21)));
        card.addView(copy, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button unlock = actionButton(onlineReadOnly ? "Только просмотр" : "Разблокировать", v -> {
            if (onlineReadOnly) {
                toast("Глава дерева включил режим просмотра");
                return;
            }
            dialog.dismiss();
            toggleLock();
        });
        unlock.setTextSize(10);
        unlock.setSingleLine(true);
        unlock.setTextColor(onlineReadOnly ? Color.rgb(101, 113, 122) : Color.rgb(166, 55, 50));
        unlock.setBackground(panelBg(
            Color.WHITE,
            dp(12),
            onlineReadOnly ? Color.rgb(217, 224, 229) : Color.argb(90, 197, 83, 75)));
        card.addView(unlock, new LinearLayout.LayoutParams(dp(116), dp(42)));

        FrameLayout root = new FrameLayout(this);
        root.setPadding(dp(12), 0, dp(12), dp(14));
        root.addView(card, new FrameLayout.LayoutParams(-1, dp(64), Gravity.BOTTOM));
        dialog.setContentView(root);
        dialog.setOnDismissListener(ignored -> {
            toastHandler.removeCallbacks(dismissLockedEditPromptRunnable);
            if (lockedEditPromptDialog == dialog) lockedEditPromptDialog = null;
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        toastHandler.postDelayed(dismissLockedEditPromptRunnable, 3600L);
    }

    void openPersonEditor() {
        editorModule.openPersonEditor();
    }

    void refreshOpenPersonEditor() {
        editorModule.refreshOpenPersonEditor();
    }

    void refreshOpenMemorySection(String personId) {
        editorModule.refreshMemorySection(personId);
    }

    void addMemoryDraftAttachments(String personId, java.util.List<MemoryAttachment> attachments, int failedCount) {
        editorModule.addMemoryDraftAttachments(personId, attachments, failedCount);
    }

    private void updateSelectedFromEditor() {
        editorModule.updateSelectedFromEditor();
    }

    void addLoosePerson() {
        if (!requireEditingEnabled()) return;
        recordUndo("Добавлена пустая карточка");
        hideKeyboard();
        boolean firstCard = state.people.isEmpty();
        PointF center = firstCard
            ? new PointF(
                (workspaceWidth - TreeLayoutEngine.CARD_W) / 2f,
                (workspaceHeight - TreeLayoutEngine.CARD_H) / 2f)
            : treeView.viewportCenterWorld();
        float[] spot = findOpenSpot(center.x, center.y);
        Person person = state.addPerson(tr("Пустая карточка"), spot[0], spot[1]);
        state.selectedId = person.id;
        arrangeAfterPersonAddedIfEnabled();
        AnalyticsReporter.personAdded("loose", 1, firstCard, state);
        saveToast("Карточка добавлена");
        bindState();
        if (firstCard) treeView.post(() -> treeView.focusPerson(person.id));
        else treeView.invalidate();
    }

    private void showAddPersonMenu() {
        addPersonMenuModule.showAddPersonMenu();
    }

    private void openPersonActions(Person person, float screenX, float screenY) {
        personMenusModule.openPersonActions(person, screenX, screenY);
    }

    void showPersonBranch(Person person, String mode) {
        if (person == null || state == null) return;
        state.selectedId = person.id;
        branchMode = mode;
        if (kinshipStatusPanel != null) kinshipStatusPanel.setVisibility(View.GONE);
        if ("near".equals(mode)) { nearGenerations = 1; treeView.setNearGenerations(1); }
        branchAnchorId = person.id;
        pendingBranchMode = "";
        treeView.setBranchMode(mode, person.id);
        updateBranchStatusPanel();
        showPanel("");
        treeView.post(treeView::fit);
    }

    void showKinshipToRoot(Person person) {
        if (person == null || state == null) return;
        Person root = state.people.get(state.rootId);
        if (root == null) {
            toast("Ваша корневая карточка не найдена");
            return;
        }
        KinshipCalculator.Result result = calculateKinship(person.id, root.id);
        treeView.highlightKinshipPath(PersonPathFinder.shortestPath(state, person.id, root.id));
        showKinshipResult(person, root, result);
    }

    KinshipCalculator.Result calculateKinship(String firstId, String secondId) {
        return KinshipCalculator.calculate(state, firstId, secondId, kinshipTerminological);
    }

    Dialog showKinshipModeDialog() {
        return KinshipModeDialog.show(this, terminology -> {
            kinshipTerminological = terminology;
            getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE).edit().putBoolean("kinship_terminological", terminology).apply();
            rebuildUiKeepingPanel();
        });
    }

    void shareBiography(Person person) {
        biographyShareModule.shareBiography(person);
    }

    String personQualitySummary(TreeQualityAnalyzer.PersonReport report) {
        return qualityModule == null
            ? "Заполнено 0%"
            : qualityModule.personSummary(report);
    }

    int qualitySummaryColor(TreeQualityAnalyzer.PersonReport report) {
        return qualityModule == null
            ? Color.rgb(8, 122, 115)
            : qualityModule.summaryColor(report);
    }

    void refreshTreeQuality() {
        if (qualityModule != null) qualityModule.refresh();
    }

    void showTreeQualityDialog() {
        if (qualityModule != null) qualityModule.showTreeDialog();
    }

    void showPersonQualityDialog(Person person, String category) {
        if (qualityModule != null) qualityModule.showPersonDialog(person, category);
    }

    private void selectQualityPerson(String personId) {
        if (state == null || !state.people.containsKey(personId)) return;
        state.selectedId = personId;
        bindState();
        if (treeView != null) treeView.focusPerson(personId);
    }

    void togglePersonPin(Person person) {
        if (person == null || !requireEditingEnabled()) return;
        recordUndo(person.pinned ? "Откреплена карточка: " + person.name : "Закреплена карточка: " + person.name);
        person.pinned = !person.pinned;
        saveToast(person.pinned ? "Карточка закреплена" : "Карточка откреплена");
        bindState();
    }

    void addRelationAction(String action) {
        if (!requireEditingEnabled()) return;
        Person current = state.selectedPerson();
        if (current == null) return;
        // Editor quick actions and the add-person popup share this entry point.
        // Resolve relationship choices before recording undo or creating a card.
        if ("add-partner".equals(action)) {
            addPersonMenuModule.requestAddPartner(current);
            return;
        }
        if (action.startsWith("add-children-") && action.indexOf('|') < 0) {
            addPersonMenuModule.requestAddChildren(current, Math.min(4, countFromAction(action, 1)));
            return;
        }
        recordUndo();
        java.util.List<String> newIds = new java.util.ArrayList<>();

        if (action.startsWith("add-parents-")) {
            int count = Math.min(2, countFromAction(action, 1));
            java.util.List<String> existingParents = parentIdsOf(current.id);
            java.util.List<String> siblingFamily = state.siblingFamilyTargetsForNewParent(
                current.id,
                existingParents);
            float[] offsets = distribute(count, 340f);
            for (int i = 0; i < count; i++) {
                float[] spot = positionNear(current, offsets[i], -TreeLayoutEngine.LEVEL_GAP);
                Person created = state.addPerson(
                    tr("Новый родитель") + (count > 1 ? " " + (i + 1) : ""),
                    spot[0],
                    spot[1]);
                for (String childId : siblingFamily) {
                    state.addRelation("parent", created.id, childId);
                }
                newIds.add(created.id);
            }
            if (newIds.size() >= 2) {
                connectEveryPair(newIds, "partner");
            } else if (newIds.size() == 1 && !existingParents.isEmpty()) {
                String existingParent = existingParents.get(0);
                String newParent = newIds.get(0);
                state.addRelation("partner", existingParent, newParent, "left");
                state.copyParentLinks(existingParent, newParent);
            }
        } else if (action.startsWith("add-children-")) {
            int count = Math.min(4, countFromAction(action, 1));
            String partner = coParentFromAction(action, current.id);
            java.util.List<String> parentIds = new java.util.ArrayList<>();
            parentIds.add(current.id);
            if (!partner.isEmpty()) parentIds.add(partner);
            float[] offsets = distribute(count, 340f);
            for (int i = 0; i < count; i++) {
                float[] spot = positionNear(current, offsets[i], TreeLayoutEngine.LEVEL_GAP);
                Person created = state.addPerson(
                    tr("Новый ребёнок") + (count > 1 ? " " + (i + 1) : ""),
                    spot[0],
                    spot[1]);
                state.addRelation("parent", current.id, created.id);
                if (!partner.isEmpty()) state.addRelation("parent", partner, created.id);
                linkChildToSiblings(parentIds, created.id);
                newIds.add(created.id);
            }
        } else if (action.startsWith("add-partner")) {
            float[] spot = positionNear(current, -320f, 0f);
            Person created = state.addPerson(tr("Новый партнёр"), spot[0], spot[1]);
            state.addRelation("partner", current.id, created.id, "left");
            if ("add-partner-with-children".equals(action)
                || "add-partner".equals(action)) {
                state.copyParentLinks(current.id, created.id);
            }
            newIds.add(created.id);
        } else if (action.startsWith("add-siblings-") || "add-sibling".equals(action)) {
            int count = "add-sibling".equals(action) ? 1 : Math.min(3, countFromAction(action, 1));
            java.util.List<String> parents = parentIdsOf(current.id);
            java.util.List<String> existingSiblings = siblingIdsOf(current.id);
            for (int i = 0; i < count; i++) {
                float offset = ((i / 2) + 1) * 320f * (i % 2 == 0 ? 1f : -1f);
                float[] spot = positionNear(current, offset, 0f);
                Person created = state.addPerson(
                    tr("Брат или сестра") + (count > 1 ? " " + (i + 1) : ""),
                    spot[0],
                    spot[1]);
                if (!parents.isEmpty()) {
                    for (String parentId : parents) state.addRelation("parent", parentId, created.id);
                    linkChildToSiblings(parents, created.id);
                }
                newIds.add(created.id);
            }
            java.util.List<String> siblingSet = new java.util.ArrayList<>();
            siblingSet.add(current.id);
            siblingSet.addAll(existingSiblings);
            siblingSet.addAll(newIds);
            connectEveryPair(new java.util.ArrayList<>(new java.util.LinkedHashSet<>(siblingSet)), "sibling");
        }

        if (newIds.isEmpty()) {
            historyModule.cancelPendingUndo();
            return;
        }
        String newId = newIds.get(newIds.size() - 1);
        String label = newIds.size() > 1
            ? "Добавлены карточки: " + newIds.size()
            : "Добавлен: " + (state.people.get(newId) == null ? "новый человек" : state.people.get(newId).name);
        recordAction(label, current.name.isEmpty() ? "Без имени" : current.name);
        state.selectedId = newId;
        arrangeAfterPersonAddedIfEnabled();
        AnalyticsReporter.personAdded(action, newIds.size(), false, state);
        saveToast(newIds.size() > 1 ? "Карточки добавлены: " + newIds.size() : "Родственник добавлен");
        bindState();
        treeView.invalidate();
    }

    private int countFromAction(String action, int fallback) {
        try {
            String value = action.substring(action.lastIndexOf('-') + 1);
            int separator = value.indexOf('|');
            if (separator >= 0) value = value.substring(0, separator);
            return Math.max(1, Integer.parseInt(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String coParentFromAction(String action, String personId) {
        int separator = action == null ? -1 : action.indexOf('|');
        if (separator < 0) return firstPartnerOf(personId);
        String chosenId = action.substring(separator + 1);
        if ("none".equals(chosenId)) return "";
        return FamilyRelationPolicy.validChosenCoParent(state, personId, chosenId);
    }

    private float[] distribute(int count, float gap) {
        if (count <= 1) return new float[]{0f};
        float start = -((count - 1) * gap) / 2f;
        float[] result = new float[count];
        for (int i = 0; i < count; i++) result[i] = start + i * gap;
        return result;
    }

    private void connectEveryPair(java.util.List<String> personIds, String type) {
        for (int first = 0; first < personIds.size(); first++) {
            for (int second = first + 1; second < personIds.size(); second++) {
                state.addRelation(type, personIds.get(first), personIds.get(second), "right");
            }
        }
    }

    private void addRelative(String kind) {
        if (!requireEditingEnabled()) return;
        Person current = state.selectedPerson();
        if (current == null) return;
        recordUndo();
        Person created;
        if ("parent".equals(kind)) {
            java.util.List<String> existingParents = parentIdsOf(current.id);
            java.util.List<String> siblingFamily = state.siblingFamilyTargetsForNewParent(
                current.id,
                existingParents);
            float[] spot = positionNear(current, -160f, -TreeLayoutEngine.LEVEL_GAP);
            created = state.addPerson(tr("Новый родитель"), spot[0], spot[1]);
            for (String childId : siblingFamily) {
                state.addRelation("parent", created.id, childId);
            }
            if (!existingParents.isEmpty()) {
                String existingParent = existingParents.get(0);
                state.addRelation("partner", existingParent, created.id, "left");
                state.copyParentLinks(existingParent, created.id);
            }
        } else if ("child".equals(kind)) {
            float[] spot = positionNear(current, 120f, TreeLayoutEngine.LEVEL_GAP);
            created = state.addPerson(tr("Новый ребёнок"), spot[0], spot[1]);
            state.addRelation("parent", current.id, created.id);
            java.util.List<String> parentIds = new java.util.ArrayList<>();
            parentIds.add(current.id);
            String partner = firstPartnerOf(current.id);
            if (!partner.isEmpty()) {
                state.addRelation("parent", partner, created.id);
                parentIds.add(partner);
            }
            linkChildToSiblings(parentIds, created.id);
        } else if ("partner".equals(kind)) {
            float[] spot = positionNear(current, -320f, 0f);
            created = state.addPerson(tr("Новый партнёр"), spot[0], spot[1]);
            state.addRelation("partner", current.id, created.id, "left");
            state.copyParentLinks(current.id, created.id);
        } else {
            float[] spot = positionNear(current, 320f, 0f);
            created = state.addPerson(tr("Брат или сестра"), spot[0], spot[1]);
            java.util.List<String> parents = parentIdsOf(current.id);
            if (parents.isEmpty()) {
                state.addRelation("sibling", current.id, created.id, "right");
            } else {
                for (String parentId : parents) state.addRelation("parent", parentId, created.id);
                linkChildToSiblings(parents, created.id);
            }
        }
        state.selectedId = created.id;
        recordAction("Добавлен: " + created.name, current.name.isEmpty() ? "Без имени" : current.name);
        arrangeAfterPersonAddedIfEnabled();
        saveToast("Родственник добавлен");
        bindState();
    }

    void duplicateSelected() {
        if (!requireEditingEnabled()) return;
        Person current = state.selectedPerson();
        if (current == null) return;
        recordUndo("Дублирована карточка: " + (current.name.isEmpty() ? "Без имени" : current.name));
        float[] spot = findOpenSpot(current.x + 320f, current.y + 40f);
        Person copy = state.addPerson(
            (current.name.isEmpty() ? tr("Без имени") : current.name)
                + tr(" (копия)"),
            spot[0],
            spot[1]);
        PersonNames.copy(current, copy);
        if (copy.namePartsSet) PersonNames.set(copy, current.surname, current.givenName,
            PersonNames.clean(current.patronymic + tr(" (копия)")));
        copy.born = current.born;
        copy.died = current.died;
        copy.bornDay = current.bornDay;
        copy.bornMonth = current.bornMonth;
        copy.bornYear = current.bornYear;
        copy.diedDay = current.diedDay;
        copy.diedMonth = current.diedMonth;
        copy.diedYear = current.diedYear;
        copy.place = current.place;
        copy.notes = current.notes;
        copy.photoMediaId = "";
        copy.photo = "";
        copy.gender = PersonGender.resolve(current);
        copy.genderManual = current.genderManual;
        copy.colorMode = current.colorMode;
        copy.manualColor = current.manualColor;
        copy.color = TreeState.displayColor(copy, state.people.size());
        copy.pinned = false;
        for (Memory memory : current.memories) {
            Memory next = new Memory();
            next.id = "m_" + java.util.UUID.randomUUID().toString().replace("-", "");
            next.type = memory.type;
            next.title = memory.title;
            next.text = memory.text;
            next.filename = memory.filename;
            next.mimeType = memory.mimeType;
            next.data = memory.data;
            next.at = memory.at;
            for (MemoryAttachment attachment : memory.attachments) {
                MemoryAttachment copiedAttachment = new MemoryAttachment();
                copiedAttachment.id = "a_" + java.util.UUID.randomUUID().toString().replace("-", "");
                copiedAttachment.filename = attachment.filename;
                copiedAttachment.mimeType = attachment.mimeType;
                copiedAttachment.type = attachment.type;
                copiedAttachment.mediaId = attachment.mediaId;
                copiedAttachment.size = attachment.size;
                copiedAttachment.data = attachment.data;
                next.attachments.add(copiedAttachment);
            }
            copy.memories.add(next);
        }
        arrangeAfterPersonAddedIfEnabled();
        saveToast("Карточка дублирована");
        bindState();
    }

    private void arrangeAfterPersonAddedIfEnabled() {
        if (state == null || !state.autoArrangeOnAdd || state.people.isEmpty()) return;
        startManualArrange(true);
    }

    void applyAutoArrangePreference(TreeState target) {
        if (target == null) return;
        android.content.SharedPreferences preferences = getSharedPreferences(
            "androidft-ui",
            MODE_PRIVATE);
        if (preferences.contains("auto_arrange_on_add")) {
            target.autoArrangeOnAdd = preferences.getBoolean("auto_arrange_on_add", false);
        } else {
            preferences.edit()
                .putBoolean("auto_arrange_on_add", target.autoArrangeOnAdd)
                .apply();
        }
    }

    void rememberAutoArrangePreference(boolean enabled) {
        getSharedPreferences("androidft-ui", MODE_PRIVATE)
            .edit()
            .putBoolean("auto_arrange_on_add", enabled)
            .apply();
    }

    private void rememberUiScalePreferences() {
        getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE)
            .edit()
            .putFloat(PREF_UI_SCALE, normalizeUiScale(uiScale))
            .putFloat(PREF_FONT_SCALE, normalizeFontScale(fontScale))
            .apply();
    }

    private void setUiScale(float nextScale) {
        float normalized = normalizeUiScale(nextScale);
        if (Math.abs(uiScale - normalized) < 0.001f) return;
        uiScale = normalized;
        syncSettingsToState();
        rememberUiScalePreferences();
        saveOnly();
        rebuildUiKeepingPanel();
        toast("Масштаб интерфейса обновлён");
    }

    private void setFontScale(float nextScale) {
        float normalized = normalizeFontScale(nextScale);
        if (Math.abs(fontScale - normalized) < 0.001f) return;
        fontScale = normalized;
        syncSettingsToState();
        rememberUiScalePreferences();
        saveOnly();
        rebuildUiKeepingPanel();
        toast("Размер шрифта обновлён");
    }

    private void rebuildUiKeepingPanel() {
        String panel = activePanel == null ? "" : activePanel;
        String selectedId = state == null ? "" : state.selectedId;
        String linkId = selectedLinkId;
        buildUi();
        if (state != null && state.people.containsKey(selectedId)) state.selectedId = selectedId;
        selectedLinkId = linkId == null ? "" : linkId;
        bindState();
        showPanel(panel);
    }

    private void toggleLayoutLogging() {
        if (layoutTraining == null) layoutTraining = new LayoutTrainingStore(this);
        boolean enabled = layoutTraining.toggleLogging();
        toast(enabled
            ? "ИИ-логи включены временно"
            : "ИИ-логи выключены");
    }

    private void copyLayoutLogs() {
        if (layoutTraining == null) layoutTraining = new LayoutTrainingStore(this);
        String logs = layoutTraining.logText();
        if (logs.trim().isEmpty()) {
            toast("ИИ-лог пока пуст");
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            toast("Не удалось открыть буфер обмена");
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Family Tree DS layout AI logs", logs));
        toast("ИИ-логи скопированы");
    }

    void confirmDelete() {
        if (!requireEditingEnabled()) return;
        java.util.Set<String> selectedIds = treeView == null
            ? java.util.Collections.emptySet()
            : treeView.selectedIds();
        java.util.List<String> selectedInTreeOrder = new java.util.ArrayList<>();
        if (selectedIds.size() > 1) {
            for (String id : state.people.keySet()) {
                if (selectedIds.contains(id)) selectedInTreeOrder.add(id);
            }
        }
        if (selectedInTreeOrder.size() > 1) {
            treeView.clearSelection();
            updateSelectionToolbar();
            showMultiDeleteDialog(selectedInTreeOrder);
            return;
        }
        Person person = state.selectedPerson();
        if (person == null) return;
        showStyledConfirmation(
            R.drawable.ic_menu_trash,
            "Удалить человека?",
            person.name.isEmpty() ? "Без имени" : person.name,
            "Удалить",
            true,
            () -> {
                String name = person.name.isEmpty() ? "Без имени" : person.name;
                int peopleBefore = state.people.size();
                int linksBefore = state.links.size();
                recordUndo(state.people.size() <= 1 ? "Очищена последняя карточка" : "Удалён: " + name);
                boolean clearedLast = state.people.size() <= 1;
                if (state.people.size() <= 1) clearLastPerson(person);
                else state.deletePerson(person.id);
                AnalyticsReporter.personDeleted(1, false, peopleBefore, linksBefore);
                saveToast(clearedLast ? "Последняя карточка очищена" : "Человек удалён");
                bindState();
                treeView.invalidate();
            });
    }

    private void showMultiDeleteDialog(java.util.List<String> personIds) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(18), dp(18), dp(16));
        shell.setBackground(panelBg(Color.WHITE, dp(18), Color.argb(56, 63, 82, 94)));
        shell.setElevation(dp(12));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_menu_trash);
        icon.setColorFilter(uiColor(Color.rgb(197, 83, 75)));
        icon.setPadding(dp(13), dp(13), dp(13), dp(13));
        icon.setBackground(panelBg(Color.rgb(255, 244, 241), dp(999), Color.argb(60, 63, 82, 94)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(54), dp(54));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        shell.addView(icon, iconParams);

        TextView heading = new LocalizedTextView(this);
        heading.setText("Удаление выбранных");
        heading.setTextColor(Color.rgb(28, 34, 38));
        heading.setTextSize(20);
        heading.setTypeface(uiBold());
        heading.setGravity(Gravity.CENTER);
        heading.setIncludeFontPadding(false);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.setMargins(0, dp(14), 0, 0);
        shell.addView(heading, headingParams);

        TextView body = new TextView(this);
        body.setTextColor(AppThemePalette.text(Color.rgb(83, 94, 103)));
        body.setTextSize(14);
        body.setTypeface(ui());
        body.setGravity(Gravity.CENTER);
        body.setMinLines(2);
        body.setMaxLines(2);
        body.setEllipsize(android.text.TextUtils.TruncateAt.END);
        body.setIncludeFontPadding(false);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.setMargins(dp(4), dp(10), dp(4), 0);
        shell.addView(body, bodyParams);

        int[] index = {0};
        int[] deleted = {0};
        Runnable updateText = () -> {
            while (index[0] < personIds.size() && !state.people.containsKey(personIds.get(index[0]))) index[0]++;
            if (index[0] >= personIds.size()) return;
            Person person = state.people.get(personIds.get(index[0]));
            String name = person.name == null || person.name.trim().isEmpty() ? "Без имени" : person.name.trim();
            body.setText(AppLanguage.isEnglish(this)
                ? "Person " + (index[0] + 1) + " of " + personIds.size() + "\n" + name
                : "Человек " + (index[0] + 1) + " из " + personIds.size() + "\n" + name);
        };
        updateText.run();

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(48));
        actionsParams.setMargins(0, dp(18), 0, 0);
        Button cancel = actionButton("Отмена", v -> dialog.dismiss());
        cancel.setTextColor(Color.rgb(28, 34, 38));
        cancel.setBackground(panelBg(Color.WHITE, dp(10), Color.rgb(205, 214, 220)));
        actions.addView(cancel, new LinearLayout.LayoutParams(0, -1, 1));
        Button deleteOne = actionButton("Удалить", v -> {
            if (!requireEditingEnabled()) return;
            while (index[0] < personIds.size() && !state.people.containsKey(personIds.get(index[0]))) index[0]++;
            if (index[0] >= personIds.size()) {
                dialog.dismiss();
                return;
            }
            Person person = state.people.get(personIds.get(index[0]));
            String name = person.name == null || person.name.trim().isEmpty() ? "Без имени" : person.name.trim();
            int peopleBefore = state.people.size();
            int linksBefore = state.links.size();
            state.selectedId = person.id;
            recordUndo(peopleBefore <= 1 ? "Очищена последняя карточка" : "Удалён: " + name);
            if (peopleBefore <= 1) clearLastPerson(person);
            else state.deletePerson(person.id);
            AnalyticsReporter.personDeleted(1, false, peopleBefore, linksBefore);
            deleted[0]++;
            index[0]++;
            while (index[0] < personIds.size() && !state.people.containsKey(personIds.get(index[0]))) index[0]++;
            if (index[0] < personIds.size()) state.selectedId = personIds.get(index[0]);
            saveOnly();
            bindState();
            treeView.invalidate();
            if (index[0] >= personIds.size()) dialog.dismiss();
            else updateText.run();
        });
        deleteOne.setTextColor(Color.WHITE);
        deleteOne.setBackground(panelBg(Color.rgb(197, 83, 75), dp(10), Color.TRANSPARENT));
        LinearLayout.LayoutParams deleteOneParams = new LinearLayout.LayoutParams(0, -1, 1.35f);
        deleteOneParams.setMargins(dp(10), 0, 0, 0);
        actions.addView(deleteOne, deleteOneParams);
        shell.addView(actions, actionsParams);

        Button deleteAll = actionButton("Удалить всех", v -> {
            if (!requireEditingEnabled()) return;
            int remaining = 0;
            for (int i = index[0]; i < personIds.size(); i++) {
                if (state.people.containsKey(personIds.get(i))) remaining++;
            }
            if (remaining == 0) {
                dialog.dismiss();
                return;
            }
            int peopleBefore = state.people.size();
            int linksBefore = state.links.size();
            recordUndo("Удалены выбранные карточки: " + remaining);
            for (int i = index[0]; i < personIds.size(); i++) {
                Person person = state.people.get(personIds.get(i));
                if (person == null) continue;
                state.selectedId = person.id;
                if (state.people.size() <= 1) clearLastPerson(person);
                else state.deletePerson(person.id);
            }
            AnalyticsReporter.personDeleted(remaining, false, peopleBefore, linksBefore);
            deleted[0] += remaining;
            saveOnly();
            bindState();
            treeView.invalidate();
            dialog.dismiss();
        });
        deleteAll.setTextColor(Color.rgb(197, 83, 75));
        deleteAll.setBackground(panelBg(Color.rgb(255, 244, 241), dp(10), Color.argb(90, 197, 83, 75)));
        LinearLayout.LayoutParams deleteAllParams = new LinearLayout.LayoutParams(-1, dp(44));
        deleteAllParams.setMargins(0, dp(10), 0, 0);
        shell.addView(deleteAll, deleteAllParams);

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(ignored -> {
            if (deleted[0] == 0) return;
            historyModule.commitPendingUndo();
            saveToast("Удалено карточек: " + deleted[0]);
        });
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(470));
            shell.setMinimumWidth(width);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.dimAmount = 0.32f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
        dialog.show();
    }

    private void confirmDeleteWholeTree() {
        if (!requireEditingEnabled()) return;
        if (state == null
            || (state.people.isEmpty() && state.links.isEmpty() && state.guides.isEmpty())) {
            toast("Дерево уже пустое");
            return;
        }
        showStyledConfirmation(
            R.drawable.ic_menu_trash,
            "Удалить всё дерево?",
            "Все карточки, связи и линии поколений будут удалены. Это действие можно сразу отменить.",
            "Удалить дерево",
            true,
            this::deleteWholeTree);
    }

    private void deleteWholeTree() {
        if (state == null) return;
        int peopleCount = state.people.size();
        int linksCount = state.links.size();
        recordUndo("Удалено всё дерево", peopleCount + " карточек");
        state.people.clear();
        state.links.clear();
        state.guides.clear();
        state.rootId = "";
        state.selectedId = "";
        branchMode = "all";
        branchAnchorId = "";
        pendingBranchMode = "";
        resetTransientCanvasModes(false);
        saveOnly();
        AnalyticsReporter.personDeleted(peopleCount, true, peopleCount, linksCount);
        bindState();
        showPanel("");
        treeView.invalidate();
        toast("Дерево удалено");
    }

    private void clearLastPerson(Person person) {
        if (person == null) return;
        person.name = "Первый человек";
        person.born = "";
        person.died = "";
        person.bornDay = "";
        person.bornMonth = "";
        person.bornYear = "";
        person.diedDay = "";
        person.diedMonth = "";
        person.diedYear = "";
        person.place = "";
        person.notes = "";
        person.photoMediaId = "";
        person.photo = "";
        person.avatarScale = 1f;
        person.avatarOffsetX = 0f;
        person.avatarOffsetY = 0f;
        person.gender = PersonGender.infer(person.name);
        person.genderManual = false;
        person.memories.clear();
        person.pinned = false;
        person.colorMode = "auto-surname";
        person.manualColor = TreeState.colorString(TreeState.colorFor(person.name, 0));
        person.color = TreeState.displayColor(person, 0);
        state.links.clear();
        state.rootId = person.id;
        state.selectedId = person.id;
        pendingBranchMode = "";
        branchMode = "all";
        branchAnchorId = "";
        treeView.setBranchMode("all", "");
    }

    void setRootPerson(String personId) {
        if (personId == null || !state.people.containsKey(personId) || !requireEditingEnabled()) return;
        Person person = state.people.get(personId);
        recordUndo("Центр дерева: " + (person == null || person.name.isEmpty() ? "Без имени" : person.name));
        state.rootId = personId;
        state.selectedId = personId;
        saveToast("Центр дерева изменён");
        bindState();
        treeView.focusPerson(personId);
    }

    private float[] positionNear(Person anchor, float dx, float dy) {
        if (anchor == null || Float.isNaN(anchor.x) || Float.isNaN(anchor.y)) {
            PointF center = treeView.viewportCenterWorld();
            return findOpenSpot(center.x + dx, center.y + dy);
        }
        return findOpenSpot(anchor.x + dx, anchor.y + dy);
    }

    private float[] findOpenSpot(float preferredX, float preferredY) {
        java.util.List<Person> occupied = new java.util.ArrayList<>();
        for (Person person : state.people.values()) {
            if (Float.isFinite(person.x) && Float.isFinite(person.y)) occupied.add(person);
        }
        float[] start = snapPoint(preferredX, preferredY);
        if (spotIsOpen(start[0], start[1], occupied)) return start;
        float maxRadius = Math.max(workspaceWidth, workspaceHeight);
        for (float radius = TreeLayoutEngine.GRID; radius <= maxRadius; radius += TreeLayoutEngine.GRID) {
            float[][] candidates = new float[][]{
                {start[0] + radius, start[1]},
                {start[0] - radius, start[1]},
                {start[0], start[1] + radius},
                {start[0], start[1] - radius},
                {start[0] + radius, start[1] + radius},
                {start[0] - radius, start[1] + radius},
                {start[0] + radius, start[1] - radius},
                {start[0] - radius, start[1] - radius}
            };
            for (float[] candidate : candidates) {
                float[] spot = snapPoint(candidate[0], candidate[1]);
                if (spotIsOpen(spot[0], spot[1], occupied)) return spot;
            }
        }
        return start;
    }

    private boolean spotIsOpen(float x, float y, java.util.List<Person> occupied) {
        for (Person person : occupied) {
            boolean separated = x + TreeLayoutEngine.CARD_W + TreeLayoutEngine.GRID <= person.x
                || person.x + TreeLayoutEngine.CARD_W + TreeLayoutEngine.GRID <= x
                || y + TreeLayoutEngine.CARD_H + TreeLayoutEngine.GRID <= person.y
                || person.y + TreeLayoutEngine.CARD_H + TreeLayoutEngine.GRID <= y;
            if (!separated) return false;
        }
        return true;
    }

    private float[] snapPoint(float x, float y) {
        return new float[]{
            TreeLayoutEngine.snap(x),
            TreeLayoutEngine.snap(y)
        };
    }

    java.util.List<String> parentIdsOf(String childId) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (Relation link : state.links) {
            if ("parent".equals(link.type) && childId.equals(link.to) && state.people.containsKey(link.from)) ids.add(link.from);
        }
        return ids;
    }

    private java.util.List<String> siblingIdsOf(String personId) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (Relation link : state.links) {
            if (!"sibling".equals(link.type)) continue;
            if (personId.equals(link.from) && state.people.containsKey(link.to)) ids.add(link.to);
            else if (personId.equals(link.to) && state.people.containsKey(link.from)) ids.add(link.from);
        }
        return new java.util.ArrayList<>(ids);
    }

    private String firstPartnerOf(String personId) {
        java.util.List<String> partners = FamilyRelationPolicy.partnerIds(state, personId);
        return partners.isEmpty() ? "" : partners.get(0);
    }

    private java.util.List<String> childrenForParents(java.util.List<String> parentIds, String excludedId) {
        java.util.Set<String> parentSet = new java.util.HashSet<>(parentIds);
        java.util.Set<String> childIds = new java.util.LinkedHashSet<>();
        for (Relation link : state.links) {
            if (!"parent".equals(link.type) || !parentSet.contains(link.from) || link.to.equals(excludedId)) continue;
            childIds.add(link.to);
        }
        return new java.util.ArrayList<>(childIds);
    }

    private void linkChildToSiblings(java.util.List<String> parentIds, String childId) {
        for (String siblingId : childrenForParents(parentIds, childId)) {
            state.addRelation("sibling", siblingId, childId, "right");
        }
    }

    private void setBranch(String mode) {
        branchMode = mode == null ? "all" : mode;
        if (kinshipStatusPanel != null) kinshipStatusPanel.setVisibility(View.GONE);
        if ("near".equals(branchMode)) { nearGenerations = 1; treeView.setNearGenerations(1); }
        if ("all".equals(branchMode)) {
            pendingBranchMode = "";
            branchAnchorId = "";
            treeView.setBranchMode("all", "");
            updateBranchStatusPanel();
            showPanel("view");
            return;
        }
        pendingBranchMode = branchMode;
        branchAnchorId = "";
        treeView.setBranchMode(branchMode, "");
        updateBranchStatusPanel();
        showPanel("");
        toast("Нажмите карточку-якорь");
    }

    private void startSelectionMode(String mode) {
        if (!requireEditingEnabled()) return;
        resetTransientCanvasModes(false);
        lastSelectionMode = "lasso".equals(mode) ? "lasso" : "rect";
        treeView.setSelectionMode(mode);
        toast("Выделение: " + ("lasso".equals(lastSelectionMode) ? "лассо" : "рамка"));
        updateSelectionToolbar();
        showPanel("");
    }

    private void startGuideMode(String mode) {
        if (!requireEditingEnabled()) return;
        String nextMode = "h".equals(mode) || "v".equals(mode) || "erase".equals(mode) ? mode : "";
        if (!nextMode.isEmpty() && nextMode.equals(activeGuideMode)) {
            cancelGuideMode();
            return;
        }
        resetTransientCanvasModes(false);
        activeGuideMode = nextMode;
        if (guideLabelInput != null && !text(guideLabelInput).trim().isEmpty()) guideDraftLabel = text(guideLabelInput).trim();
        treeView.setGuideDraft(guideDraftColor, guideDraftLabel);
        treeView.setGuideMode(activeGuideMode);
        updateCanvasModePanel();
        showPanel("");
    }

    private void cancelGuideMode() {
        boolean active = !activeGuideMode.isEmpty();
        cancelGuideModeSilently();
        refreshGuidePanelIfVisible();
        if (active) toast("Режим направляющих выключен");
    }

    void cancelGuideModeSilently() {
        activeGuideMode = "";
        if (treeView != null) treeView.setGuideMode("");
        updateCanvasModePanel();
    }

    private void finishGuideAction() {
        String completedMode = activeGuideMode;
        if (completedMode.isEmpty()) return;
        cancelGuideModeSilently();
        refreshGuidePanelIfVisible();
        toast("erase".equals(completedMode)
            ? "Направляющая удалена — режим выключен"
            : "Направляющая добавлена — режим выключен");
    }

    private void clearGuides() {
        if (state == null || state.guides.isEmpty()) {
            toast("Направляющих нет");
            return;
        }
        if (!requireEditingEnabled()) return;
        recordUndo("Очищены направляющие", state.guides.size() + " шт.");
        state.guides.clear();
        cancelGuideModeSilently();
        saveOnly();
        treeView.invalidate();
        refreshGuidePanelIfVisible();
        toast("Направляющие очищены");
    }

    private void toggleLock() {
        settingsModule.toggleLock();
    }

    private void toggleViewMode() {
        settingsModule.toggleViewMode();
    }

    private void toggleGenerationLines() {
        settingsModule.toggleGenerationLines();
    }

    private void toggleParentLineMode() {
        settingsModule.toggleParentLineMode();
    }

    private void toggleFamilyLineMode() {
        settingsModule.toggleFamilyLineMode();
    }

    private void toggleHideDetails() {
        settingsModule.toggleHideDetails();
    }

    private void toggleCompactCards() {
        settingsModule.toggleCompactCards();
    }

    private void toggleFocusTree() {
        settingsModule.toggleFocusTree();
    }

    private void toggleHistoryPanel() {
        settingsModule.toggleHistoryPanel();
    }

    private void setTheme(String nextTheme) {
        settingsModule.setTheme(nextTheme);
    }

    void recreateWithTheme() {
        StartupSplash.rememberTheme(this, theme);
        syncSettingsToState();
        if (saveCoordinator != null) saveCoordinator.saveThen(saved -> {
            if (saved && !isDestroyed()) { AppThemePalette.setDark("dark".equals(theme)); recreate(); }
        });
    }

    private void dismissTreeHint(int direction) {
        if (treeHint == null || treeHintDismissed) return;
        treeHintDismissed = true;
        getSharedPreferences("androidft-ui", MODE_PRIVATE)
            .edit()
            .putBoolean("tree_hint_dismissed", true)
            .apply();
        treeHint.animate()
            .translationX(direction * Math.max(treeHint.getWidth(), dp(240)))
            .alpha(0f)
            .setDuration(180L)
            .withEndAction(() -> {
                if (treeHint != null) treeHint.setVisibility(View.GONE);
            })
            .start();
    }

    private void hideTreeHintForSession() {
        if (treeHint == null || treeHintDismissed || treeHint.getVisibility() != View.VISIBLE) return;
        treeHintAutoHidden = true;
        treeHint.animate()
            .alpha(0f)
            .setDuration(180L)
            .withEndAction(() -> {
                if (treeHint != null) treeHint.setVisibility(View.GONE);
            })
            .start();
    }

    private void applySystemBars() {
        boolean dark = AppThemePalette.isDark();
        int headerColor = AppThemePalette.surface(Color.rgb(248, 251, 252));
        getWindow().setBackgroundDrawable(new ColorDrawable(dark ? Color.rgb(16, 23, 27) : Color.rgb(250, 250, 250)));
        getWindow().setStatusBarColor(headerColor);
        getWindow().setNavigationBarColor(dark ? Color.rgb(16, 23, 27) : Color.rgb(248, 251, 252));
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (android.os.Build.VERSION.SDK_INT >= 26) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        if (!dark) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (android.os.Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
        applyFocusSystemBars();
    }

    /** Focus mode uses the whole canvas, including the status/notification bar area. */
    void applyFocusSystemBars() {
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (focusTree) {
                    controller.hide(android.view.WindowInsets.Type.statusBars());
                    controller.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    controller.show(android.view.WindowInsets.Type.statusBars());
                }
            }
            return;
        }
        int flags = decor.getSystemUiVisibility();
        if (focusTree) flags |= View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        else flags &= ~(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        decor.setSystemUiVisibility(flags);
    }

    private void startTraining() {
        settingsModule.startTraining();
    }

    private void offerTrainingIfNeeded() {
        if (state == null) return;
        android.content.SharedPreferences preferences = getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE);
        boolean handledForApplication = preferences.getBoolean(TRAINING_OFFER_HANDLED, false);
        boolean existingTree = !state.people.isEmpty();
        if (handledForApplication || existingTree || state.onboardingCompleted || state.onboardingOffered) {
            if (!handledForApplication) {
                preferences.edit().putBoolean(TRAINING_OFFER_HANDLED, true).apply();
            }
            return;
        }
        preferences.edit().putBoolean(TRAINING_OFFER_HANDLED, true).apply();
        state.onboardingOffered = true;
        saveOnly();
        showStyledConfirmation(
            R.drawable.ic_menu_training,
            "Пройти короткое обучение?",
            "За пару минут покажем, как создать карточку, добавить связь и упорядочить дерево. Полотно останется открытым, а нужные кнопки будут подсвечены.",
            "Начать",
            "Не сейчас",
            false,
            this::startTraining);
    }

    void trainingTargetActivated(String id) {
        if (settingsModule != null) settingsModule.onTrainingTargetActivated(id);
    }

    View trainingTarget(String id) {
        if ("add-person".equals(id)) return addPersonButton;
        if ("card-menu".equals(id)) return cardNav;
        if ("people-menu".equals(id)) return peopleNav;
        if ("links-menu".equals(id)) return linksNav;
        if ("tree-menu".equals(id)) return treeNav;
        if ("more-menu".equals(id)) return moreNav;
        if ("more-overview".equals(id)) return trainingMoreMenuTarget;
        return null;
    }

    View trainingOpenedTarget(String id) {
        if ("card-menu".equals(id)) return cardPanel;
        if ("people-menu".equals(id)) return peoplePanel;
        if ("links-menu".equals(id)) return linksPanel;
        if ("tree-menu".equals(id)) return viewPanel;
        if ("more-menu".equals(id)) return trainingMoreMenuTarget;
        return null;
    }

    private String normalizeTheme(String value) {
        return MainActivitySettings.normalizeTheme(value);
    }

    private void refreshSettingsIfVisible() {
        settingsModule.refreshSettingsIfVisible();
    }

    void refreshGuidePanelIfVisible() {
        settingsModule.refreshGuidePanelIfVisible();
    }

    private void startLink(String type) {
        relationsModule.startLink(type);
    }

    private boolean handlePendingLink(Person person) {
        return relationsModule.handlePendingLink(person);
    }

    private boolean handlePendingBranch(Person person) {
        if (person == null) return false;
        String nextMode = pendingBranchMode.isEmpty() ? ("all".equals(branchMode) ? "" : branchMode) : pendingBranchMode;
        if (nextMode.isEmpty()) return false;
        branchMode = nextMode;
        pendingBranchMode = nextMode;
        state.selectedId = person.id;
        branchAnchorId = person.id;
        treeView.setBranchMode(branchMode, branchAnchorId);
        saveOnly();
        updateBranchStatusPanel();
        toast("Ветка: " + (person.name.isEmpty() ? "Без имени" : person.name));
        return true;
    }

    private void resetBranchAnchor() {
        String mode = pendingBranchMode.isEmpty() ? branchMode : pendingBranchMode;
        if ("all".equals(mode) || mode.isEmpty()) return;
        branchMode = mode;
        pendingBranchMode = mode;
        branchAnchorId = "";
        treeView.setBranchMode(mode, "");
        saveOnly();
        updateBranchStatusPanel();
        toast("Показано всё дерево. Выберите карточку");
    }

    private void clearBranchFilter() {
        pendingBranchMode = "";
        branchMode = "all";
        branchAnchorId = "";
        treeView.setBranchMode("all", "");
        saveOnly();
        updateBranchStatusPanel();
        toast("Показано всё дерево");
    }

    private void cancelLinkMode() {
        relationsModule.cancelLinkMode();
    }

    void showPanel(String panel) {
        if (kinshipStatusPanel != null && panel != null && !panel.isEmpty()) kinshipStatusPanel.setVisibility(View.GONE);
        clearSearchFocus();
        panelsModule.showPanel(panel);
    }

    void clearSearchFocus() {
        searchModule.clearFocus();
    }

    private void showZoomIndicator(float scale) {
        if (zoomIndicator == null || !Float.isFinite(scale)) return;
        int percent = Math.max(5, Math.round(scale * 100f));
        String label=percent+"%";
        if(!android.text.TextUtils.equals(zoomIndicator.getText(),label))zoomIndicator.setText(label);
        zoomIndicator.animate().cancel();
        zoomIndicator.setVisibility(View.VISIBLE);
        zoomIndicator.setAlpha(1f);
        toastHandler.removeCallbacks(hideZoomIndicatorRunnable);
        toastHandler.postDelayed(hideZoomIndicatorRunnable, 850L);
    }

    void setHeaderActionsDimmed(boolean dimmed) {
        int surface = dimmed ? Color.rgb(206, 210, 211) : Color.WHITE;
        int stroke = dimmed ? Color.rgb(174, 184, 188) : Color.rgb(217, 224, 229);
        int icon = dimmed ? Color.rgb(101, 113, 122) : Color.rgb(8, 122, 115);

        searchModule.setDimmed(dimmed, surface, stroke, icon);
        for (Button action : new Button[]{undoBtn, redoBtn}) {
            if (action == null) continue;
            action.setAlpha(1f);
            action.setEnabled(!dimmed);
            action.setBackground(panelBg(surface, dp(8), stroke));
            tintHeaderAction(action, icon);
        }
    }

    private void tintHeaderAction(View action, int color) {
        if (action instanceof TextView) tintDrawables((TextView) action, color);
        Drawable foreground = action.getForeground();
        if (foreground != null) foreground.mutate().setTint(AppThemePalette.text(color));
    }

    private void selectTreeSearchPerson(Person person) {
        bindEditor(person);
        showPanel("");
        treeView.focusPerson(person.id);
    }

    private void togglePanel(String panel) {
        panelsModule.togglePanel(panel);
    }

    void recordUndo() {
        historyModule.recordUndo("", "");
    }

    void recordUndo(String label) {
        historyModule.recordUndo(label, "");
    }

    void recordUndo(String label, String detail) {
        historyModule.recordUndo(label, detail);
    }

    void recordAction(String label, String detail) {
        historyModule.recordAction(label, detail);
        DiagnosticsLogger.breadcrumb(this, "tree.action");
    }

    void undo() {
        historyModule.undo();
    }

    void redo() {
        historyModule.redo();
    }

    private void updateHistoryButtons() {
        historyModule.updateHistoryButtons();
    }

    void updateHistoryPanel() {
        if (historyModule != null) historyModule.updateHistoryPanel();
    }

    void updateBranchStatusPanel() {
        if (branchStatusPanel == null || branchStatusText == null) return;
        branchStatusPanel.setLayoutParams(branchStatusParams());
        if (kinshipStatusPanel != null && kinshipStatusPanel.getVisibility() == View.VISIBLE && activePanel.isEmpty()) {
            branchStatusPanel.setVisibility(View.GONE);
            if (historyPanel != null) historyPanel.setVisibility(View.GONE);
            return;
        }
        boolean active = !"all".equals(branchMode) || !pendingBranchMode.isEmpty();
        boolean visible = active && activePanel.isEmpty();
        branchStatusPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (historyPanel != null && visible) historyPanel.setVisibility(View.GONE);
        if (!active) {
            updateHistoryPanel();
            return;
        }
        String mode = pendingBranchMode.isEmpty() ? branchMode : pendingBranchMode;
        Person anchor = state == null ? null : state.people.get(branchAnchorId);
        String label = branchLabel(mode);
        nearGenerationControls.setVisibility("near".equals(mode) ? View.VISIBLE : View.GONE);
        if (!nearGenerationInput.hasFocus() && !nearGenerationInput.getText().toString().equals(String.valueOf(nearGenerations)))
            nearGenerationInput.setText(String.valueOf(nearGenerations));
        branchStatusText.setText(anchor == null ? label + ": нажмите карточку" : label + ": " + (anchor.name.isEmpty() ? "Без имени" : anchor.name));
    }

    void updateSelectionToolbar() {
        if (selectionToolbar == null || treeView == null) return;
        int count = treeView.selectedIds().size();
        boolean selecting = treeView.hasActiveSelectionMode();
        boolean visible = (selecting || count > 0) && activePanel.isEmpty();
        if (selectionStatusText != null) {
            String modeLabel = treeView.selectionModeLabel();
            selectionStatusText.setText(selecting
                ? modeLabel + (count > 0 ? " · " + count : "")
                : "Выбрано: " + count);
        }
        styleSelectionToolbarButtons(selecting);
        selectionToolbar.setVisibility(visible ? View.VISIBLE : View.GONE);
        updateAddPersonButtonVisibility();
    }

    private void styleSelectionToolbarButtons(boolean selecting) {
        int teal = Color.rgb(8, 122, 115);
        int tealSurface = Color.rgb(232, 248, 246);
        int red = Color.rgb(184, 68, 62);

        if (selectionMoveButton != null) {
            int icon = selecting
                ? R.drawable.ic_menu_check
                : "lasso".equals(lastSelectionMode)
                    ? R.drawable.ic_menu_lasso
                    : R.drawable.ic_menu_frame;
            selectionMoveButton.setText(selecting ? "Готово" : "Продолжить");
            selectionMoveButton.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
            tintDrawables(selectionMoveButton, teal);
            selectionMoveButton.setTextColor(teal);
            selectionMoveButton.setBackground(panelBg(
                tealSurface,
                dp(12),
                Color.argb(92, 24, 169, 153)));
        }
        if (selectionArrangeButton != null) {
            selectionArrangeButton.setText("Упорядочить");
            selectionArrangeButton.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_menu_layout, 0, 0, 0);
            tintDrawables(selectionArrangeButton, teal);
            selectionArrangeButton.setTextColor(teal);
            selectionArrangeButton.setBackground(panelBg(
                Color.WHITE,
                dp(12),
                Color.rgb(217, 224, 229)));
        }
        if (selectionStopButton != null) {
            selectionStopButton.setText("Сброс");
            selectionStopButton.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_menu_stop, 0, 0, 0);
            tintDrawables(selectionStopButton, red);
            selectionStopButton.setTextColor(red);
            selectionStopButton.setBackground(panelBg(
                Color.rgb(255, 244, 241),
                dp(12),
                Color.argb(90, 197, 83, 75)));
        }
        if (selectionAppendButton != null) {
            selectionAppendButton.setText("Добор");
            selectionAppendButton.setForeground(null);
            selectionAppendButton.setCompoundDrawablesWithIntrinsicBounds(
                selectionAppendMode ? R.drawable.ic_menu_check : R.drawable.ic_menu_plus,
                0,
                0,
                0);
            tintDrawables(selectionAppendButton, teal);
            selectionAppendButton.setCompoundDrawablePadding(dp(3));
            selectionAppendButton.setGravity(Gravity.CENTER);
            selectionAppendButton.setPadding(dp(3), 0, dp(3), 0);
            selectionAppendButton.setTextColor(teal);
            selectionAppendButton.setBackground(panelBg(
                selectionAppendMode ? tealSurface : Color.WHITE,
                dp(12),
                selectionAppendMode
                    ? Color.argb(110, 24, 169, 153)
                    : Color.rgb(217, 224, 229)));
            selectionAppendButton.setContentDescription(tr(selectionAppendMode
                ? "Добавление к выделению включено"
                : "Добавлять к текущему выделению"));
        }
    }

    private void enableSelectionMove() {
        if (treeView == null) return;
        if (treeView.hasActiveSelectionMode()) {
            treeView.setSelectionMode("");
            toast("Можно переносить выбранные карточки");
        } else {
            treeView.setSelectionMode(lastSelectionMode);
            toast("Можно выделять ещё");
        }
        updateSelectionToolbar();
    }

    private void clearSelection() {
        if (treeView == null) return;
        treeView.clearSelection();
        toast("Выделение сброшено");
        updateSelectionToolbar();
    }

    void resetTransientCanvasModes(boolean notify) {
        boolean hadMode = !activeGuideMode.isEmpty()
            || !pendingLinkType.isEmpty()
            || (treeView != null && treeView.hasActiveSelectionMode());
        activeGuideMode = "";
        pendingLinkType = "";
        pendingLinkFrom = "";
        selectedLinkId = "";
        if (treeView != null) {
            treeView.setGuideMode("");
            treeView.setLinkState("", "");
            treeView.clearSelection();
        }
        updateCanvasModePanel();
        updateSelectionToolbar();
        if (notify && hadMode) toast("Активный инструмент выключен");
    }

    private void cancelActiveCanvasMode() {
        if (!activeGuideMode.isEmpty()) {
            cancelGuideMode();
            return;
        }
        if (!pendingLinkType.isEmpty()) {
            cancelLinkMode();
            return;
        }
        if (onlineReadOnly) {
            toast("Глава дерева включил режим просмотра");
        } else if (editLocked) {
            toggleLock();
        }
    }

    void updateCanvasModePanel() {
        if (canvasModePanel == null || canvasModeTitle == null || canvasModeDetail == null || canvasModeAction == null) return;
        boolean guideActive = !activeGuideMode.isEmpty();
        boolean linkActive = !pendingLinkType.isEmpty();
        boolean visible = (guideActive || linkActive) && activePanel.isEmpty();
        canvasModePanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;
        if (guideActive) {
            canvasModeAction.setText("Стоп");
            styleCanvasModeAction(true);
            canvasModeTitle.setText(guideModeBannerTitle(activeGuideMode));
            canvasModeDetail.setText("erase".equals(activeGuideMode)
                ? "Коснитесь линии. После удаления режим выключится."
                : "Коснитесь полотна. После добавления режим выключится.");
            return;
        }
        if (linkActive) {
            canvasModeAction.setText("Стоп");
            styleCanvasModeAction(true);
            canvasModeTitle.setText(linkModeTitle(pendingLinkType));
            if (pendingLinkFrom.isEmpty()) {
                canvasModeDetail.setText("parent".equals(pendingLinkType)
                    ? "Шаг 1/2: выберите первого родителя"
                    : "kinship".equals(pendingLinkType)
                        ? "Шаг 1/2: выберите первого человека"
                        : "Шаг 1/2: выберите первую карточку");
            } else {
                Person first = state == null ? null : state.people.get(pendingLinkFrom);
                String name = first == null || first.name.isEmpty() ? "первая карточка" : first.name;
                canvasModeDetail.setText("parent".equals(pendingLinkType)
                    ? name + " — первый родитель. Шаг 2/2: выберите ребёнка"
                    : name + ("kinship".equals(pendingLinkType)
                        ? " → выберите второго человека"
                        : " → выберите вторую карточку"));
            }
            return;
        }
    }

    private void styleCanvasModeAction(boolean stop) {
        if (canvasModeAction == null) return;
        int color = stop ? Color.rgb(184, 68, 62) : Color.rgb(8, 122, 115);
        int icon = stop ? R.drawable.ic_menu_stop : R.drawable.ic_menu_unlock;
        canvasModeAction.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
        tintDrawables(canvasModeAction, color);
        canvasModeAction.setTextColor(color);
        canvasModeAction.setElevation(0f);
        canvasModeAction.setBackground(panelBg(
            stop ? Color.rgb(255, 244, 241) : Color.WHITE,
            dp(12),
            stop
                ? Color.argb(90, 197, 83, 75)
                : Color.argb(82, 24, 169, 153)));
    }

    private String linkModeTitle(String type) {
        if ("kinship".equals(type)) return "Кто кому приходится";
        if ("parent".equals(type)) return "Родительская связь";
        if ("family".equals(type)) return "Родственная связь";
        if ("sibling".equals(type)) return "Братская связь";
        if ("erase".equals(type)) return "Удаление связи";
        return "Работа со связями";
    }

    void showKinshipResult(Person first, Person second, KinshipCalculator.Result result) {
        showPanel("");
        if (kinshipStatusPanel == null) {
            kinshipStatusPanel = new LinearLayout(this);
            kinshipStatusPanel.setOrientation(LinearLayout.VERTICAL);
            kinshipStatusPanel.setTag("canvas.kinshipStatus");
            kinshipStatusPanel.setPadding(dp(10), dp(4), dp(6), dp(10));
            kinshipStatusPanel.setBackground(panelBg(Color.argb(245, 248, 251, 252), dp(10), Color.argb(52, 63, 82, 94)));
            kinshipStatusPanel.setElevation(dp(8));
            stage.addView(kinshipStatusPanel, branchStatusParams());
        }
        kinshipStatusPanel.removeAllViews();
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new LocalizedTextView(this);
        title.setText(result != null && result.found ? "Кто кому приходится" : "Родство не найдено");
        title.setTextSize(12); title.setTypeface(uiBold()); title.setTextColor(uiColor(Color.rgb(8, 122, 115)));
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = iconButton(R.drawable.ic_menu_close, v -> {
            kinshipStatusPanel.setVisibility(View.GONE);
            treeView.highlightKinshipPath(java.util.Collections.emptyList());
            updateBranchStatusPanel();
        });
        close.setContentDescription(tr("Закрыть"));
        heading.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        kinshipStatusPanel.addView(heading);
        String firstName = first == null || first.name.isEmpty() ? tr("Первый человек") : first.name;
        String secondName = second == null || second.name.isEmpty() ? tr("Второй человек") : second.name;
        if (result != null && result.found) {
            addKinshipStatusLine(firstName + " → " + secondName + ": " + tr(result.firstToSecond));
            addKinshipStatusLine(secondName + " → " + firstName + ": " + tr(result.secondToFirst));
        } else {
            addKinshipStatusLine(firstName + " ↔ " + secondName);
        }
        kinshipStatusPanel.setLayoutParams(branchStatusParams());
        kinshipStatusPanel.setVisibility(View.VISIBLE);
        kinshipStatusPanel.bringToFront();
        updateBranchStatusPanel();
    }

    private void addKinshipStatusLine(String value) {
        TextView line = new LocalizedTextView(this);
        LocalizedViews.setRaw(line, value);
        line.setTextSize(12); line.setTypeface(ui());
        line.setTextColor(uiColor(Color.rgb(28, 34, 38)));
        line.setPadding(0, 0, 0, dp(4));
        kinshipStatusPanel.addView(line, new LinearLayout.LayoutParams(-1, -2));
    }

    private View kinshipResultCard(String subject, String reference, String relation) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(11), dp(14), dp(11));
        card.setBackground(panelBg(Color.rgb(232, 248, 246), dp(12), Color.argb(82, 24, 169, 153)));

        TextView names = new LocalizedTextView(this);
        LocalizedViews.setRaw(names, subject);
        names.setTextColor(Color.rgb(28, 34, 38));
        names.setTextSize(14);
        names.setTypeface(uiBold());
        names.setSingleLine(false);
        names.setMaxLines(2);
        card.addView(names, new LinearLayout.LayoutParams(-1, -2));

        TextView context = new LocalizedTextView(this);
        LocalizedViews.setRaw(context, tr("для ") + reference);
        context.setTextColor(Color.rgb(101, 113, 122));
        context.setTextSize(11);
        context.setTypeface(ui());
        context.setPadding(0, dp(2), 0, 0);
        card.addView(context, new LinearLayout.LayoutParams(-1, -2));

        TextView answer = new LocalizedTextView(this);
        answer.setText(AppLanguage.translateFully(
            this,
            relation == null || relation.isEmpty()
                ? "родство не определено"
                : relation));
        answer.setTextColor(Color.rgb(8, 122, 115));
        answer.setTextSize(17);
        answer.setTypeface(uiBold());
        answer.setPadding(0, dp(8), 0, 0);
        card.addView(answer, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private LinearLayout.LayoutParams kinshipCardParams() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private String guideModeBannerTitle(String mode) {
        if ("h".equals(mode)) return "Линия: горизонтальная";
        if ("v".equals(mode)) return "Линия: вертикальная";
        if ("erase".equals(mode)) return "Удаление направляющей";
        return "Направляющие";
    }

    private String branchLabel(String mode) {
        if ("ancestors".equals(mode)) return "Предки";
        if ("descendants".equals(mode)) return "Потомки";
        if ("near".equals(mode)) return "Близкие";
        return "Всё дерево";
    }

    private View historyItem(HistoryEntry entry) {
        return historyModule.historyItem(entry);
    }

    private String currentActionLabel() {
        return historyModule.currentActionLabel();
    }

    private LinearLayout.LayoutParams historyItemParams() {
        return historyModule.historyItemParams();
    }

    private String actionLabelFromSnapshot(TreeCommand command) {
        return historyModule.actionLabelFromSnapshot(command);
    }

    private String historyTime(String at) {
        return MainActivityHistory.historyTime(at);
    }

    private String safeHistoryText(String value, int max) {
        return MainActivityHistory.safeHistoryText(value, max);
    }

    boolean saveOnly() {
        syncSettingsToState();
        if (onlineManager != null) state.localEditEpoch = onlineManager.markLocalTreeChanged();
        if (saveCoordinator != null) saveCoordinator.requestDebounced();
        updateStats();
        return true;
    }

    void saveToast() {
        saveToast("Дерево сохранено");
    }

    void saveToast(String message) {
        syncSettingsToState();
        if (onlineManager != null) state.localEditEpoch = onlineManager.markLocalTreeChanged();
        if (saveCoordinator != null) saveCoordinator.requestImmediate();
        updateStats();
        toast(message);
    }

    void updateStats() {
        if (stats != null) {
            stats.setText(AppLanguage.isEnglish(this)
                ? state.people.size() + " people, " + state.links.size() + " connections"
                : state.people.size() + " человек, " + state.links.size() + " связей");
        }
        DiagnosticsLogger.updateStateMetrics(state);
    }

    private void openImport() {
        filesModule.openImport();
    }

    private void openExport(int requestCode, String type, String filename) {
        filesModule.openExport(requestCode, type, filename);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (peopleModule != null) peopleModule.onActivityResult(requestCode, resultCode, data);
        filesModule.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(
        int requestCode,
        String[] permissions,
        int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_WRITE_PHOTO_PERMISSION) return;
        if (grantResults.length > 0
            && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            filesModule.savePreviewPhotoToGallery();
        } else {
            pendingPreviewPhotoMediaId = "";
            pendingPreviewPhotoDataUrl = "";
            toast("Без разрешения фото нельзя сохранить в галерею");
        }
    }

    void handleIncomingIntent(Intent intent) {
        String widgetPersonId = intent == null
            ? ""
            : intent.getStringExtra(BirthdayWidgetProvider.EXTRA_PERSON_ID);
        if (widgetPersonId != null
            && !widgetPersonId.isEmpty()
            && state != null
            && state.people.containsKey(widgetPersonId)) {
            intent.removeExtra(BirthdayWidgetProvider.EXTRA_PERSON_ID);
            Person person = state.people.get(widgetPersonId);
            state.selectedId = widgetPersonId;
            bindEditor(person);
            showPanel("");
            if (treeView != null) {
                treeView.postDelayed(() -> {
                    if (state != null && state.people.containsKey(widgetPersonId)) {
                        state.selectedId = widgetPersonId;
                        bindEditor(state.people.get(widgetPersonId));
                        treeView.focusPerson(widgetPersonId);
                    }
                }, 180L);
            }
            return;
        }
        Uri uri = intent == null ? null : intent.getData();
        if (uri != null
            && "androidft".equalsIgnoreCase(uri.getScheme())
            && "oauth".equalsIgnoreCase(uri.getHost())
            && "/github".equals(uri.getPath())) {
            intent.setData(null);
            if (onlineModule != null) onlineModule.handleOAuthCallback(uri);
            return;
        }
        boolean customJoin = uri != null
            && "androidft".equalsIgnoreCase(uri.getScheme())
            && "join".equalsIgnoreCase(uri.getHost());
        boolean webJoin = uri != null
            && "https".equalsIgnoreCase(uri.getScheme())
            && "drshapaya.ru".equalsIgnoreCase(uri.getHost())
            && "/androidft/join".equals(uri.getPath());
        if (customJoin || webJoin) {
            String key = uri.getQueryParameter("key");
            intent.setData(null);
            if (onlineModule != null) onlineModule.handleInvitationLink(key);
            return;
        }
        filesModule.handleIncomingIntent(intent);
    }

    private void importFromUri(Uri uri) {
        filesModule.importFromUri(uri);
    }

    void openPhotoPicker() {
        filesModule.openPhotoPicker();
    }

    void openMemoryFilePicker() {
        filesModule.openMemoryFilePicker();
    }

    void openMemoryPhotoPicker() {
        filesModule.openMemoryPhotoPicker();
    }

    private void importPhotoFromUri(Uri uri) {
        filesModule.importPhotoFromUri(uri);
    }

    private void importMemoryFileFromUri(Uri uri) {
        filesModule.importMemoryFileFromUri(uri);
    }

    private void exportTextToUri(Uri uri, String kind) {
        filesModule.exportTextToUri(uri, kind);
    }

    private void exportPngToUri(Uri uri) {
        filesModule.exportPngToUri(uri);
    }

    private String exportGedcomText() {
        return filesModule.exportGedcomText();
    }

    private String cleanGed(String value) {
        return MainActivityFiles.cleanGed(value);
    }

    private String gedDate(String day, String month, String year) {
        return MainActivityFiles.gedDate(day, month, year);
    }

    private void shareJsonText() {
        filesModule.shareJsonText();
    }

    Dialog showHelp(String title, String text) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(16), dp(14), dp(16), dp(14));
        shell.setBackground(panelBg(Color.rgb(255, 255, 255), dp(12), Color.argb(64, 24, 169, 153)));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);

        TextView mark = new LocalizedTextView(this);
        mark.setText("?");
        mark.setGravity(Gravity.CENTER);
        mark.setTextSize(18);
        mark.setTypeface(uiBold());
        mark.setIncludeFontPadding(false);
        mark.setTextColor(Color.WHITE);
        mark.setBackground(panelBg(Color.rgb(24, 169, 153), dp(999), Color.TRANSPARENT));
        top.addView(mark, new LinearLayout.LayoutParams(dp(34), dp(34)));

        TextView heading = new LocalizedTextView(this);
        heading.setText(title == null || title.trim().isEmpty() ? "Подсказка" : title.trim());
        heading.setTextColor(Color.rgb(28, 34, 38));
        heading.setTextSize(18);
        heading.setTypeface(uiBold());
        heading.setIncludeFontPadding(true);
        heading.setSingleLine(false);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, -2, 1);
        headingParams.setMargins(dp(10), 0, dp(8), 0);
        top.addView(heading, headingParams);

        top.addView(closeButton(v -> dialog.dismiss()), new LinearLayout.LayoutParams(dp(38), dp(38)));
        shell.addView(top);

        View divider = new View(this);
        divider.setBackgroundColor(AppThemePalette.stroke(Color.argb(44, 63, 82, 94)));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(0, dp(12), 0, dp(10));
        shell.addView(divider, dividerParams);

        ScrollView scroll = new ScrollView(this);
        TextView body = new LocalizedTextView(this);
        body.setText(text == null || text.trim().isEmpty() ? "Описание пока не добавлено." : text.trim());
        body.setTextColor(Color.rgb(76, 87, 96));
        body.setTextSize(14);
        body.setTypeface(ui());
        body.setLineSpacing(dp(2), 1.0f);
        body.setIncludeFontPadding(true);
        body.setPadding(dp(2), dp(2), dp(2), dp(8));
        scroll.addView(body);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        Button ok = actionButton("Понятно", v -> dialog.dismiss());
        ok.setTextColor(Color.WHITE);
        ok.setBackground(panelBg(Color.rgb(24, 169, 153), dp(8), Color.TRANSPARENT));
        LinearLayout.LayoutParams okParams = new LinearLayout.LayoutParams(-1, dp(44));
        okParams.setMargins(0, dp(14), 0, 0);
        shell.addView(ok, okParams);

        dialog.setContentView(shell);
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(36), dp(460));
            shell.setMinimumWidth(width);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.dimAmount = 0.32f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
        dialog.show();
        if (window != null) {
            int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(36), dp(460));
            int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.85f);
            shell.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int wanted = shell.getMeasuredHeight();
            if (wanted > maxHeight) scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
            window.setLayout(width, Math.min(wanted, maxHeight));
        }
        return dialog;
    }

    void showStyledConfirmation(
        int iconRes,
        String title,
        String message,
        String confirmLabel,
        boolean danger,
        Runnable action
    ) {
        showStyledConfirmation(
            iconRes,
            title,
            message,
            confirmLabel,
            "Отмена",
            danger,
            action);
    }

    void showStyledConfirmation(
        int iconRes,
        String title,
        String message,
        String confirmLabel,
        String cancelLabel,
        boolean danger,
        Runnable action
    ) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        int accent = danger ? Color.rgb(197, 83, 75) : Color.rgb(8, 122, 115);
        int accentSurface = danger ? Color.rgb(255, 244, 241) : Color.rgb(232, 248, 246);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(18), dp(18), dp(16));
        shell.setBackground(panelBg(Color.WHITE, dp(18), Color.argb(56, 63, 82, 94)));
        shell.setElevation(dp(12));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(uiColor(accent));
        icon.setPadding(dp(13), dp(13), dp(13), dp(13));
        icon.setBackground(panelBg(accentSurface, dp(999), Color.argb(60, 63, 82, 94)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(54), dp(54));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        shell.addView(icon, iconParams);

        TextView heading = new LocalizedTextView(this);
        heading.setText(title == null ? "" : title);
        heading.setTextColor(Color.rgb(28, 34, 38));
        heading.setTextSize(20);
        heading.setTypeface(uiBold());
        heading.setGravity(Gravity.CENTER);
        heading.setIncludeFontPadding(false);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.setMargins(0, dp(14), 0, 0);
        shell.addView(heading, headingParams);

        TextView body = new LocalizedTextView(this);
        body.setText(message == null ? "" : message);
        body.setTextColor(Color.rgb(83, 94, 103));
        body.setTextSize(14);
        body.setTypeface(ui());
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(dp(2), 1f);
        body.setIncludeFontPadding(false);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.setMargins(dp(4), dp(10), dp(4), 0);
        shell.addView(body, bodyParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(48));
        actionsParams.setMargins(0, dp(18), 0, 0);

        Button cancel = actionButton(cancelLabel, v -> dialog.dismiss());
        cancel.setTextColor(Color.rgb(28, 34, 38));
        cancel.setBackground(panelBg(Color.WHITE, dp(10), Color.rgb(205, 214, 220)));
        actions.addView(cancel, new LinearLayout.LayoutParams(0, -1, 1));

        Button confirm = actionButton(confirmLabel, v -> {
            dialog.dismiss();
            if (action != null) action.run();
        });
        confirm.setTextColor(Color.WHITE);
        confirm.setBackground(panelBg(accent, dp(10), Color.TRANSPARENT));
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, -1, 1.35f);
        confirmParams.setMargins(dp(10), 0, 0, 0);
        actions.addView(confirm, confirmParams);
        shell.addView(actions, actionsParams);

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(470));
            shell.setMinimumWidth(width);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.dimAmount = 0.32f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
        dialog.show();
    }

    void showPhotoPreview(String dataUrl) {
        photosModule.showPhotoPreview(dataUrl);
    }

    void showMediaPhotoPreview(String mediaId) {
        photosModule.showMediaPhotoPreview(mediaId);
    }

    void showPersonPhotoEditor(Person person) {
        photosModule.showPersonPhotoEditor(person);
    }

    LinearLayout editorDialogShell() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(12), dp(12), dp(12), dp(12));
        shell.setBackground(panelBg(Color.WHITE, dp(20), Color.argb(56, 63, 82, 94)));
        return shell;
    }

    View editorDialogHeader(Dialog dialog, int icon, String titleText, String subtitleText) {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(this);
        mark.setImageResource(icon);
        mark.setColorFilter(uiColor(Color.rgb(8, 122, 115)));
        mark.setPadding(dp(9), dp(9), dp(9), dp(9));
        mark.setBackground(panelBg(Color.rgb(232, 248, 246), dp(999), Color.TRANSPARENT));
        top.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(10), 0, dp(6), 0);
        TextView title = new LocalizedTextView(this);
        title.setText(titleText);
        title.setTextSize(17);
        title.setTextColor(uiColor(Color.rgb(28, 34, 38)));
        title.setTypeface(uiBold());
        heading.addView(title, new LinearLayout.LayoutParams(-1, dp(25)));
        TextView subtitle = new LocalizedTextView(this);
        subtitle.setText(subtitleText);
        subtitle.setTextSize(10);
        subtitle.setTextColor(uiColor(Color.rgb(101, 113, 122)));
        subtitle.setTypeface(ui());
        heading.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(19)));
        top.addView(heading, new LinearLayout.LayoutParams(0, dp(44), 1));
        top.addView(closeButton(v -> dialog.dismiss()), new LinearLayout.LayoutParams(dp(40), dp(40)));
        return top;
    }

    void showEditorDialog(Dialog dialog, View shell) {
        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) return;
        int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(20), dp(640));
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.width = width;
        attrs.height = Math.min(Math.round(getResources().getDisplayMetrics().heightPixels * 0.92f), dp(900));
        attrs.dimAmount = 0.48f;
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setAttributes(attrs);
    }

    void requestPhotoDownload(String mediaId, String dataUrl) {
        pendingPreviewPhotoMediaId = mediaId == null ? "" : mediaId;
        pendingPreviewPhotoDataUrl = dataUrl == null ? "" : dataUrl;
        if (pendingPreviewPhotoMediaId.isEmpty() && pendingPreviewPhotoDataUrl.isEmpty()) return;
        if (android.os.Build.VERSION.SDK_INT < 29
            && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[] {android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQ_WRITE_PHOTO_PERMISSION);
            return;
        }
        filesModule.savePreviewPhotoToGallery();
    }

    Bitmap bitmapFromDataUrl(String dataUrl) {
        if (dataUrl == null) return null;
        String value = dataUrl.trim();
        if (!value.toLowerCase(Locale.ROOT).startsWith("data:image/")) return null;
        int comma = value.indexOf(',');
        if (comma < 0 || comma + 1 >= value.length()) return null;
        try {
            byte[] bytes = Base64.decode(value.substring(comma + 1), Base64.DEFAULT);
            return decodeBitmapBytes(bytes, 1800);
        } catch (Exception ignored) {
            return null;
        }
    }

    static Bitmap decodeBitmapBytes(byte[] bytes, int targetSize) {
        if (bytes == null || bytes.length == 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = photoSampleSize(bounds.outWidth, bounds.outHeight, Math.max(64, targetSize));
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private static int photoSampleSize(int width, int height, int targetSize) {
        int sample = 1;
        while ((width / (sample * 2)) >= targetSize && (height / (sample * 2)) >= targetSize) sample *= 2;
        return Math.max(1, sample);
    }

    private String safeUriName(Uri uri) {
        return MainActivityFiles.safeUriName(uri);
    }

    private String memoryTypeFromMime(String mime) {
        return MainActivityFiles.memoryTypeFromMime(mime);
    }

    private String dateStamp() {
        return MainActivityFiles.dateStamp();
    }

    private void quickStart() {
        quickStartModule.open();
    }
    private void recolorSelected() {
        java.util.Set<String> ids = treeView.selectedIds();
        Person person = state.selectedPerson();
        if (ids.isEmpty() && person == null) return;
        if (!requireEditingEnabled()) return;
        recordUndo("Изменён цвет карточек: " + (ids.isEmpty() ? 1 : ids.size()));
        if (ids.isEmpty()) ids.add(person.id);
        for (String id : ids) {
            Person item = state.people.get(id);
            if (item == null) continue;
            item.colorMode = "auto-name";
            item.color = TreeState.displayColor(item, item.name.length());
        }
        saveToast("Цвет карточек обновлён");
        treeView.invalidate();
    }

    private void recolorByFamily() {
        if (!requireEditingEnabled()) return;
        java.util.Set<String> ids = treeView.selectedIds();
        if (ids.isEmpty()) ids.addAll(state.people.keySet());
        recordUndo("Изменён цвет карточек: " + ids.size());
        for (String id : ids) {
            Person person = state.people.get(id);
            if (person == null) continue;
            person.colorMode = "auto-surname";
            person.color = TreeState.displayColor(person, person.name.length());
        }
        saveToast("Цвета по фамилии обновлены");
        treeView.invalidate();
    }

    private LinearLayout basePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));
        panel.setBackground(panelBg(Color.rgb(248, 251, 252), dp(8), Color.argb(33, 63, 82, 94)));
        panel.setElevation(dp(9));
        return panel;
    }

    TextView closeButton(View.OnClickListener listener) {
        TextView button = new LocalizedTextView(this);
        button.setGravity(Gravity.CENTER);
        button.setBackground(panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        button.setForeground(centeredIcon(R.drawable.ic_menu_close, Color.rgb(28, 34, 38)));
        button.setForegroundGravity(Gravity.CENTER);
        button.setOnClickListener(listener);
        PressFeedback.install(button);
        return button;
    }

    private TextView branchStatusButton(String label, View.OnClickListener listener) {
        TextView button = new LocalizedTextView(this);
        button.setText(label);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(9);
        button.setTypeface(uiBold());
        button.setIncludeFontPadding(false);
        button.setTextColor(Color.rgb(8, 122, 115));
        button.setBackground(panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        button.setOnClickListener(listener);
        return button;
    }

    EditText field(String hint) {
        EditText edit = new LocalizedEditText(this);
        edit.setHint(AppLanguage.translate(this, hint));
        edit.setTextSize(15);
        edit.setTypeface(ui());
        edit.setSingleLine(false);
        edit.setIncludeFontPadding(false);
        edit.setTextColor(Color.rgb(28, 34, 38));
        edit.setHintTextColor(AppThemePalette.text(Color.rgb(128, 137, 144)));
        edit.setPadding(dp(10), 0, dp(10), 0);
        edit.setMinHeight(dp(44));
        edit.setBackground(panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        return edit;
    }

    Button actionButton(String text, View.OnClickListener listener) {
        Button button = new LocalizedButton(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(uiBold());
        button.setTextColor(Color.rgb(28, 34, 38));
        button.setOnClickListener(listener);
        button.setStateListAnimator(null);
        button.setElevation(0f);
        button.setIncludeFontPadding(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(44));
        button.setMinWidth(dp(44));
        button.setBackground(panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        button.setPadding(dp(10), 0, dp(10), 0);
        PressFeedback.install(button);
        return button;
    }

    Button iconButton(int iconRes, View.OnClickListener listener) {
        return iconButton(iconRes, listener, Color.rgb(8, 122, 115));
    }

    Button iconButton(int iconRes, View.OnClickListener listener, int tint) {
        Button button = actionButton("", listener);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setCompoundDrawables(null, null, null, null);
        button.setPadding(0, 0, 0, 0);
        button.setTextSize(0);
        button.setForeground(centeredIcon(iconRes, tint));
        button.setForegroundGravity(Gravity.CENTER);
        return button;
    }

    private Drawable centeredIcon(int iconRes, int tint) {
        Drawable icon = getDrawable(iconRes);
        if (icon == null) return null;
        icon = icon.mutate();
        icon.setTint(AppThemePalette.text(tint));
        return icon;
    }

    private Button selectionActionButton(int iconRes, String label, View.OnClickListener listener) {
        Button button = actionButton(label, listener);
        button.setTextSize(11);
        button.setSingleLine(true);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        button.setCompoundDrawablePadding(dp(3));
        tintDrawables(button, Color.rgb(8, 122, 115));
        button.setElevation(0f);
        button.setStateListAnimator(null);
        button.setBackground(panelBg(
            Color.WHITE,
            dp(12),
            Color.rgb(217, 224, 229)));
        return button;
    }

    private Button navButton(String label, int iconRes, View.OnClickListener listener) {
        Button button = actionButton(label, listener);
        // Navigation already has its own selected lift and scale.
        button.setStateListAnimator(null);
        button.setTextSize(isLandscapeLayout() ? 9 : 10);
        button.setTypeface(uiBold());
        button.setGravity(Gravity.CENTER);
        button.setCompoundDrawablesWithIntrinsicBounds(0, iconRes, 0, 0);
        button.setCompoundDrawablePadding(dp(4));
        button.setPadding(dp(3), dp(isLandscapeLayout() ? 3 : 5), dp(3), dp(isLandscapeLayout() ? 3 : 5));
        button.setMinHeight(dp(isLandscapeLayout() ? 48 : 58));
        button.setElevation(0f);
        return button;
    }

    void styleNav(Button button, boolean active) {
        if (button == null) return;
        int color = active
            ? AppThemePalette.secondary()
            : AppThemePalette.text(Color.rgb(28, 34, 38));
        button.setTextColor(color);
        Drawable top = button.getCompoundDrawables()[1];
        if (top != null) top.mutate().setTint(color);
        // Non-central tabs use a quiet teal selection; the people tab has its
        // own gradient treatment below. Keep the first-variant motion: the
        // selected item lifts and settles instead of snapping.
        button.setBackground(active
            ? panelBg(Color.argb(30, 36, 177, 165), dp(isLandscapeLayout() ? 12 : 10),
                Color.argb(76, 36, 177, 165))
            : panelBg(Color.TRANSPARENT, dp(8), Color.TRANSPARENT));
        if (button != peopleNav) animateNavButton(button, active, false);
    }

    void stylePeopleNav(boolean active) {
        if (peopleNav == null) return;
        int color = Color.WHITE;
        peopleNav.setTextColor(color);
        Drawable top = peopleNav.getCompoundDrawables()[1];
        if (top != null) top.mutate().setTint(color);
        GradientDrawable background = tealGradientBg(dp(14));
        background.setStroke(dp(1), active
            ? AppThemePalette.alpha(AppThemePalette.secondaryBright(), 120)
            : Color.argb(120, 255, 255, 255));
        peopleNav.setBackground(background);
        peopleNav.setElevation(dp(active ? 7 : 5));
        animateNavButton(peopleNav, active, true);
    }

    private void animateNavButton(Button button, boolean active, boolean central) {
        float targetScale = active ? (central ? 1.035f : 1.015f) : 1f;
        float targetY = active ? -dp(central ? 3 : 2) : 0f;
        if (!PanelTransitions.enabled(button)) {
            button.animate().cancel();
            button.setTranslationY(targetY);
            button.setScaleX(targetScale);
            button.setScaleY(targetScale);
            return;
        }
        if (Math.abs(button.getScaleX() - targetScale) < .001f
            && Math.abs(button.getTranslationY() - targetY) < .5f) return;
        button.animate().cancel();
        button.animate()
            .translationY(targetY)
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(260L)
            .setInterpolator(new DecelerateInterpolator(2f))
            .start();
    }

    void animateMorePanel(boolean visible) {
        if (morePanel == null) return;
        View sheet = ((ViewGroup) morePanel).getChildAt(0);
        PanelTransitions.overlay(morePanel, sheet, moreMenuRows(), visible,
            isLandscapeLayout(), this::updateAddPersonButtonVisibility);
    }

    private ViewGroup moreMenuRows() {
        return trainingMoreMenuTarget instanceof ViewGroup ? (ViewGroup) trainingMoreMenuTarget : null;
    }

    private View menuRow(int iconRes, String label, String help, View.OnClickListener listener) {
        return menuRow(iconRes, label, help, listener, false);
    }

    private View menuRow(int iconRes, String label, String help, View.OnClickListener listener, boolean danger) {
        int accent = danger ? Color.rgb(197, 83, 75) : AppThemePalette.primary();
        int accentSurface = danger ? Color.rgb(255, 247, 244) : AppThemePalette.primarySurface();
        int accentStroke = danger
            ? Color.argb(72, 197, 83, 75)
            : AppThemePalette.alpha(AppThemePalette.primaryBright(), 72);
        return menuRow(iconRes, label, help, listener, danger, accent, accentSurface, accentStroke);
    }

    private View menuRow(
        int iconRes,
        String label,
        String help,
        View.OnClickListener listener,
        boolean danger,
        int accent,
        int accentSurface,
        int accentStroke
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(48));
        rowParams.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rowParams);

        TextView main = new LocalizedTextView(this);
        main.setGravity(Gravity.CENTER_VERTICAL);
        main.setText(label);
        main.setTextColor(danger ? Color.rgb(197, 83, 75) : Color.rgb(28, 34, 38));
        main.setTextSize(13);
        main.setTypeface(uiBold());
        main.setPadding(dp(10), 0, dp(10), 0);
        main.setBackground(panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        main.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        main.setCompoundDrawablePadding(dp(9));
        tintDrawables(main, accent);
        main.setOnClickListener(listener);
        row.addView(main, new LinearLayout.LayoutParams(0, -1, 1));

        TextView helpButton = new LocalizedTextView(this);
        helpButton.setGravity(Gravity.CENTER);
        helpButton.setText("?");
        helpButton.setTextColor(accent);
        helpButton.setTextSize(17);
        helpButton.setTypeface(uiBold());
        helpButton.setBackground(panelBg(accentSurface, dp(8), accentStroke));
        helpButton.setOnClickListener(v -> showHelp(label, help));
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(dp(42), -1);
        helpParams.setMargins(dp(6), 0, 0, 0);
        row.addView(helpButton, helpParams);
        return row;
    }

    private View themeRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(72));
        params.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(params);
        row.addView(themeTile(
            R.drawable.ic_menu_sun,
            "Светлая",
            "Включает светлую тему.",
            "light".equals(theme),
            v -> setTheme("light"),
            AppThemePalette.primary(),
            AppThemePalette.primarySurface(),
            AppThemePalette.alpha(AppThemePalette.primaryBright(), 82)), new LinearLayout.LayoutParams(0, -1, 1));
        row.addView(themeTile(
            R.drawable.ic_menu_moon,
            "Тёмная",
            "Включает тёмную тему.",
            "dark".equals(theme),
            v -> setTheme("dark"),
            AppThemePalette.secondary(),
            AppThemePalette.secondarySurface(),
            AppThemePalette.alpha(AppThemePalette.secondaryBright(), 82)), spacedTileParams());
        row.addView(themeTile(
            R.drawable.ic_menu_sparkles,
            "Чистая",
            "Белый фон без сетки и лишних элементов.",
            "clean".equals(theme),
            v -> setTheme("clean"),
            AppThemePalette.blue(),
            AppThemePalette.blueSurface(),
            AppThemePalette.alpha(AppThemePalette.blue(), 76)), spacedTileParams());
        return row;
    }

    private LinearLayout.LayoutParams spacedTileParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
        params.setMargins(dp(8), 0, 0, 0);
        return params;
    }

    LinearLayout.LayoutParams formFieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(8));
        return params;
    }

    private LinearLayout.LayoutParams spacedInputParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(6), 0, 0, 0);
        return params;
    }

    LinearLayout.LayoutParams spacedButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
        params.setMargins(dp(8), 0, 0, 0);
        return params;
    }

    private View themeTile(
        int iconRes,
        String label,
        String helpText,
        boolean active,
        View.OnClickListener listener,
        int accent,
        int accentSurface,
        int accentStroke
    ) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(4), dp(5), dp(4), dp(5));
        tile.setBackground(active
            ? panelBg(accentSurface, dp(8), accentStroke)
            : panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        tile.setOnClickListener(listener);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        if (icon.getDrawable() != null) icon.getDrawable().mutate().setTint(
            AppThemePalette.text(active ? accent : Color.rgb(28, 34, 38)));
        tile.addView(icon, new LinearLayout.LayoutParams(dp(21), dp(21)));

        TextView text = new LocalizedTextView(this);
        text.setGravity(Gravity.CENTER);
        text.setText(label);
        text.setTextSize(11);
        text.setTypeface(uiBold());
        text.setIncludeFontPadding(false);
        text.setTextColor(active ? accent : Color.rgb(28, 34, 38));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-1, dp(16));
        textParams.setMargins(0, dp(1), 0, dp(1));
        tile.addView(text, textParams);

        TextView help = new LocalizedTextView(this);
        help.setGravity(Gravity.CENTER);
        help.setText("?");
        help.setTextSize(14);
        help.setTypeface(uiBold());
        help.setIncludeFontPadding(false);
        help.setTextColor(accent);
        help.setBackground(panelBg(accentSurface, dp(8), accentStroke));
        help.setOnClickListener(v -> showHelp(label, helpText));
        tile.addView(help, new LinearLayout.LayoutParams(dp(22), dp(22)));
        return tile;
    }

    private View settingActionRow(int iconRes, String label, String help, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(48));
        rowParams.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rowParams);

        LinearLayout mainBox = new LinearLayout(this);
        mainBox.setGravity(Gravity.CENTER_VERTICAL);
        mainBox.setOrientation(LinearLayout.HORIZONTAL);
        mainBox.setPadding(dp(10), 0, dp(8), 0);
        mainBox.setBackground(panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        mainBox.setOnClickListener(listener);

        TextView main = new LocalizedTextView(this);
        main.setGravity(Gravity.CENTER_VERTICAL);
        main.setText(label);
        main.setTextColor(Color.rgb(28, 34, 38));
        main.setTextSize(13);
        main.setTypeface(uiBold());
        main.setIncludeFontPadding(false);
        main.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        main.setCompoundDrawablePadding(dp(9));
        tintDrawables(main, AppThemePalette.primary());
        mainBox.addView(main, new LinearLayout.LayoutParams(0, -1, 1));
        row.addView(mainBox, new LinearLayout.LayoutParams(0, -1, 1));

        TextView helpButton = new LocalizedTextView(this);
        helpButton.setGravity(Gravity.CENTER);
        helpButton.setText("?");
        helpButton.setTextColor(AppThemePalette.primary());
        helpButton.setTextSize(14);
        helpButton.setTypeface(uiBold());
        helpButton.setIncludeFontPadding(false);
        helpButton.setBackground(panelBg(
            AppThemePalette.primarySurface(),
            dp(8),
            AppThemePalette.alpha(AppThemePalette.primaryBright(), 72)));
        helpButton.setOnClickListener(v -> showHelp(label, help));
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(dp(42), -1);
        helpParams.setMargins(dp(6), 0, 0, 0);
        row.addView(helpButton, helpParams);
        return row;
    }

    private View settingSwitchRow(int iconRes, String label, boolean enabled, String helpText, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(54));
        rowParams.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rowParams);

        LinearLayout mainBox = new LinearLayout(this);
        mainBox.setGravity(Gravity.CENTER_VERTICAL);
        mainBox.setOrientation(LinearLayout.HORIZONTAL);
        mainBox.setPadding(dp(10), 0, dp(8), 0);
        mainBox.setBackground(panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        mainBox.setOnClickListener(listener);

        TextView text = new LocalizedTextView(this);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setText(label);
        text.setTextColor(Color.rgb(28, 34, 38));
        text.setTextSize(13);
        text.setTypeface(uiBold());
        text.setIncludeFontPadding(false);
        text.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        text.setCompoundDrawablePadding(dp(9));
        tintDrawables(text, AppThemePalette.primary());
        mainBox.addView(text, new LinearLayout.LayoutParams(0, -1, 1));

        FrameLayout toggle = toggleControl(enabled);
        toggle.setBackground(toggleBg(enabled));
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(dp(44), dp(26));
        toggleParams.setMargins(dp(8), 0, 0, 0);
        mainBox.addView(toggle, toggleParams);
        row.addView(mainBox, new LinearLayout.LayoutParams(0, -1, 1));

        TextView help = new LocalizedTextView(this);
        help.setGravity(Gravity.CENTER);
        help.setText("?");
        help.setTextColor(AppThemePalette.primary());
        help.setTextSize(14);
        help.setTypeface(uiBold());
        help.setBackground(panelBg(
            AppThemePalette.primarySurface(),
            dp(8),
            AppThemePalette.alpha(AppThemePalette.primaryBright(), 72)));
        help.setOnClickListener(v -> showHelp(label, helpText));
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(dp(42), -1);
        helpParams.setMargins(dp(6), 0, 0, 0);
        row.addView(help, helpParams);
        return row;
    }

    GradientDrawable toggleBg(boolean enabled) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(enabled
            ? AppThemePalette.primaryBright()
            : AppThemePalette.isDark() ? Color.rgb(66, 82, 88) : Color.rgb(218, 224, 228));
        bg.setCornerRadius(dp(999));
        bg.setStroke(dp(1), enabled
            ? Color.argb(70, 8, 122, 115)
            : AppThemePalette.isDark() ? Color.rgb(83, 101, 108) : Color.rgb(210, 217, 222));
        return bg;
    }

    void tintDrawables(TextView view, int color) {
        color = AppThemePalette.text(color);
        for (Drawable drawable : view.getCompoundDrawables()) {
            if (drawable != null) drawable.mutate().setTint(color);
        }
    }

    void styleHistoryButton(Button button, boolean enabled) {
        if (button == null) return;
        button.setEnabled(enabled);
        tintDrawables(button, enabled ? Color.rgb(8, 122, 115) : Color.rgb(142, 150, 156));
        button.setBackground(panelBg(Color.WHITE, dp(8), enabled ? Color.rgb(217, 224, 229) : Color.argb(120, 217, 224, 229)));
    }

    Typeface ui() {
        if (uiRegularTypeface == null) uiRegularTypeface = loadTypeface("fonts/NotoSans-Regular.ttf", Typeface.NORMAL);
        return uiRegularTypeface;
    }

    Typeface uiBold() {
        if (uiBoldTypeface == null) uiBoldTypeface = loadTypeface("fonts/NotoSans-Bold.ttf", Typeface.BOLD);
        return uiBoldTypeface;
    }

    private Typeface loadTypeface(String assetPath, int fallbackStyle) {
        try {
            return Typeface.createFromAsset(getAssets(), assetPath);
        } catch (RuntimeException ignored) {
            return Typeface.create("sans-serif", fallbackStyle);
        }
    }

    GradientDrawable panelBg(int color, int radius, int stroke) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AppThemePalette.surface(color));
        bg.setCornerRadius(radius);
        bg.setStroke(dp(1), AppThemePalette.stroke(stroke));
        return bg;
    }

    GradientDrawable colorSwatchBg(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radius);
        bg.setStroke(dp(1), Color.argb(104, 255, 255, 255));
        return bg;
    }

    GradientDrawable tealGradientBg(int radius) {
        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{AppThemePalette.ctaPrimary(), AppThemePalette.ctaSecondary()});
        bg.setCornerRadius(radius);
        return bg;
    }

    GradientDrawable softAccentGradientBg(int radius) {
        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{
                AppThemePalette.primarySurface(),
                AppThemePalette.surface(Color.rgb(250, 248, 255)),
                AppThemePalette.secondarySurface()
            });
        bg.setCornerRadius(radius);
        bg.setStroke(dp(1), AppThemePalette.alpha(AppThemePalette.secondaryBright(), 54));
        return bg;
    }

    FrameLayout toggleControl(boolean enabled) {
        FrameLayout toggle = new FrameLayout(this);
        toggle.setBackground(toggleBg(enabled));
        View thumb = new View(this);
        thumb.setBackground(toggleThumbBg());
        toggle.addView(thumb, new FrameLayout.LayoutParams(dp(20), dp(20)));
        positionToggleThumb(toggle, enabled);
        return toggle;
    }

    void positionToggleThumb(FrameLayout toggle, boolean enabled) {
        if (toggle == null || toggle.getChildCount() == 0) return;
        View thumb = toggle.getChildAt(0);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(20), dp(20));
        params.leftMargin = dp(enabled ? 21 : 3);
        params.topMargin = dp(3);
        thumb.setLayoutParams(params);
    }

    private GradientDrawable toggleThumbBg() {
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(AppThemePalette.surface(Color.WHITE));
        thumb.setStroke(dp(1), AppThemePalette.alpha(Color.rgb(63, 82, 94), 38));
        return thumb;
    }

    GradientDrawable qualityGradientBg(int radius) {
        return uiKit.qualityGradientBg(radius);
    }

    private GradientDrawable gradientBg() {
        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[] {
                AppThemePalette.ctaPrimary(),
                AppThemePalette.ctaBlue(),
                AppThemePalette.ctaSecondary()
            });
        bg.setCornerRadius(dp(8));
        return bg;
    }

    LinearLayout.LayoutParams smallActionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(44));
        params.setMargins(dp(7), 0, 0, 0);
        return params;
    }

    LinearLayout.LayoutParams railButtonParams(boolean last) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(40), dp(40));
        params.setMargins(0, 0, 0, last ? 0 : dp(6));
        return params;
    }

    LinearLayout.LayoutParams railCardButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(52), dp(52));
        return params;
    }

    private FrameLayout.LayoutParams railParams() {
        boolean landscape = isLandscapeLayout();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            dp(52),
            -2,
            (landscape ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
        params.setMargins(
            landscape ? dp(8) : 0,
            dp(landscape ? 56 : 76),
            landscape ? 0 : dp(8),
            0);
        return params;
    }

    private FrameLayout.LayoutParams canvasModeParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, dp(68), Gravity.TOP);
        params.setMargins(
            dp(isLandscapeLayout() ? 64 : 12),
            dp(isLandscapeLayout() ? 56 : 76),
            dp(isLandscapeLayout() ? 12 : 64),
            0);
        return params;
    }

    private FrameLayout.LayoutParams historyParams() {
        float density = getResources().getDisplayMetrics().density;
        int screenWidthDp = Math.round(getResources().getDisplayMetrics().widthPixels / density);
        boolean phone = getResources().getConfiguration().smallestScreenWidthDp < 600;
        int widthDp = Math.min(phone ? 320 : 360, Math.max(240, screenWidthDp - 24));
        int heightDp = phone ? 176 : 242;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(widthDp), dp(heightDp), Gravity.LEFT | Gravity.BOTTOM);
        params.setMargins(dp(isLandscapeLayout() ? 64 : 12), 0, 0, dp(isLandscapeLayout() ? 12 : 88));
        return params;
    }

    private FrameLayout.LayoutParams branchStatusParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        params.setMargins(dp(isLandscapeLayout() ? 64 : 12), 0, dp(76), dp(isLandscapeLayout() ? 12 : focusTree ? 66 : 86));
        return params;
    }

    private FrameLayout.LayoutParams selectionToolbarParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, dp(86), Gravity.BOTTOM);
        params.setMargins(dp(10), 0, dp(10), dp(isLandscapeLayout() ? 10 : 84));
        return params;
    }

    private LinearLayout.LayoutParams selectionButtonParams(float weight, boolean withLeftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), weight);
        params.setMargins(withLeftMargin ? dp(4) : 0, 0, 0, 0);
        return params;
    }

    private FrameLayout.LayoutParams bottomParams() {
        return new FrameLayout.LayoutParams(-1, dp(74), Gravity.BOTTOM);
    }

    private FrameLayout.LayoutParams bottomPanelParams(int heightDp) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, dp(heightDp), Gravity.BOTTOM);
        params.setMargins(dp(10), 0, dp(10), dp(82));
        return params;
    }

    private FrameLayout.LayoutParams bottomPanelWrapParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        params.setMargins(dp(10), 0, dp(10), dp(82));
        return params;
    }

    private FrameLayout.LayoutParams fullPanelParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -1, Gravity.TOP);
        params.setMargins(0, statusBarHeight(), 0, dp(74));
        return params;
    }

    int statusBarHeight() {
        return statusBarInsetPx > 0 ? statusBarInsetPx : dp(24);
    }

    private void applyFullPanelTopInset() {
        if (isLandscapeLayout()) {
            if (landscapeFocusControlsHost != null
                && landscapeFocusControlsHost.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) landscapeFocusControlsHost.getLayoutParams();
                params.topMargin = statusBarHeight();
                landscapeFocusControlsHost.setLayoutParams(params);
            }
            if (landscapePeopleCloseHost != null
                && landscapePeopleCloseHost.getVisibility() == View.VISIBLE
                && landscapePeopleCloseHost.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams params =
                    (LinearLayout.LayoutParams) landscapePeopleCloseHost.getLayoutParams();
                params.height = statusBarHeight() + dp(58);
                landscapePeopleCloseHost.setLayoutParams(params);
            }
            return;
        }
        int top = statusBarHeight();
        View[] panels = {settingsPanel, peoplePanel};
        for (View panel : panels) {
            if (panel == null || !(panel.getLayoutParams() instanceof FrameLayout.LayoutParams)) continue;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panel.getLayoutParams();
            params.topMargin = top;
            panel.setLayoutParams(params);
        }
    }

    private void moveLandscapeHeaderControls(boolean toCanvasOverlay) {
        if (!isLandscapeLayout() || landscapeHeaderControls == null) return;
        ViewGroup destination = toCanvasOverlay
            ? landscapeFocusControlsHost
            : headerBrand instanceof ViewGroup ? (ViewGroup) headerBrand : null;
        if (destination == null) return;
        ViewGroup current = landscapeHeaderControls.getParent() instanceof ViewGroup
            ? (ViewGroup) landscapeHeaderControls.getParent()
            : null;
        if (current == destination) return;
        if (current != null) current.removeView(landscapeHeaderControls);
        if (toCanvasOverlay) {
            destination.addView(landscapeHeaderControls, new LinearLayout.LayoutParams(-1, -1));
            return;
        }
        int index = landscapePeopleCloseHost != null
            && landscapePeopleCloseHost.getParent() == destination
            ? destination.indexOfChild(landscapePeopleCloseHost)
            : destination.getChildCount();
        destination.addView(
            landscapeHeaderControls,
            Math.max(0, index),
            new LinearLayout.LayoutParams(0, -2, 1));
    }

    void applyFocusTreeUi() {
        if (branchStatusPanel != null) branchStatusPanel.setLayoutParams(branchStatusParams());
        if (kinshipStatusPanel != null) kinshipStatusPanel.setLayoutParams(branchStatusParams());
        if (isLandscapeLayout()) {
            boolean peopleTab = "people".equals(activePanel);
            boolean fullScreenPanel = "settings".equals(activePanel) || peopleTab;
            if (settingsPanel != null) {
                settingsPanel.setPadding(
                    dp(14),
                    focusTree ? statusBarHeight() + dp(8) : dp(10),
                    dp(14),
                    0);
            }
            if (landscapePanelHost != null
                && landscapePanelHost.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams hostParams =
                    (LinearLayout.LayoutParams) landscapePanelHost.getLayoutParams();
                hostParams.width = landscapePanelWidth();
                landscapePanelHost.setLayoutParams(hostParams);
            }
            moveLandscapeHeaderControls(focusTree);
            if (appHeader != null) {
                appHeader.setVisibility(focusTree || peopleTab ? View.GONE : View.VISIBLE);
            }
            if (actionOverlay != null) actionOverlay.setVisibility(peopleTab ? View.GONE : View.VISIBLE);
            if (headerBrand != null) headerBrand.setVisibility(View.VISIBLE);
            if (headerIdentityIcon != null) {
                headerIdentityIcon.setVisibility(!focusTree && !peopleTab ? View.VISIBLE : View.GONE);
            }
            if (headerIdentityTexts != null) {
                headerIdentityTexts.setVisibility(!focusTree && !peopleTab ? View.VISIBLE : View.GONE);
            }
            if (landscapeHeaderControls != null) {
                landscapeHeaderControls.setVisibility(peopleTab ? View.GONE : View.VISIBLE);
            }
            if (landscapeFocusControlsHost != null) {
                landscapeFocusControlsHost.setVisibility(
                    focusTree && !peopleTab ? View.VISIBLE : View.GONE);
            }
            if (landscapePeopleCloseHost != null) {
                landscapePeopleCloseHost.setVisibility(
                    !focusTree && peopleTab ? View.VISIBLE : View.GONE);
                if (landscapePeopleCloseHost.getLayoutParams() instanceof LinearLayout.LayoutParams) {
                    LinearLayout.LayoutParams closeParams =
                        (LinearLayout.LayoutParams) landscapePeopleCloseHost.getLayoutParams();
                    closeParams.height = !focusTree && peopleTab
                        ? statusBarHeight() + dp(58)
                        : 0;
                    closeParams.weight = 0f;
                    landscapePeopleCloseHost.setLayoutParams(closeParams);
                }
            }
            if (actionOverlay != null
                && actionOverlay.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams overlayParams =
                    (FrameLayout.LayoutParams) actionOverlay.getLayoutParams();
                overlayParams.topMargin = focusTree ? statusBarHeight() + dp(58) : 0;
                actionOverlay.setLayoutParams(overlayParams);
            }
            if (zoomRail != null) {
                zoomRail.setVisibility(fullScreenPanel ? View.GONE : View.VISIBLE);
                if (zoomRail.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                    FrameLayout.LayoutParams railLayout =
                        (FrameLayout.LayoutParams) zoomRail.getLayoutParams();
                    railLayout.topMargin = focusTree
                        ? statusBarHeight() + dp(62)
                        : dp(56);
                    zoomRail.setLayoutParams(railLayout);
                }
            }
            setHeaderActionsDimmed("more".equals(activePanel) && !focusTree);
            refreshLockUi();
            updateAddPersonButtonVisibility();
            applyFocusNavStyle(treeNav, "Дерево");
            applyFocusNavStyle(cardNav, "Карточка");
            applyFocusNavStyle(peopleNav, "Люди");
            applyFocusNavStyle(linksNav, "Связи");
            applyFocusNavStyle(moreNav, "Ещё");
            if (addPersonButton != null
                && addPersonButton.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) addPersonButton.getLayoutParams();
                params.setMargins(0, 0, dp(12), dp(12));
                addPersonButton.setLayoutParams(params);
            }
            updateHistoryPanel();
            return;
        }
        boolean settingsTab = "settings".equals(activePanel) || "people".equals(activePanel);
        if (appHeader != null) appHeader.setVisibility(settingsTab ? View.GONE : View.VISIBLE);
        if (actionOverlay != null) actionOverlay.setVisibility(settingsTab ? View.GONE : View.VISIBLE);
        if (headerBrand != null) headerBrand.setVisibility(focusTree ? View.GONE : View.VISIBLE);
        if (headerIdentityIcon != null) headerIdentityIcon.setVisibility(View.VISIBLE);
        if (headerIdentityTexts != null) headerIdentityTexts.setVisibility(View.VISIBLE);
        if (zoomRail != null) zoomRail.setVisibility(settingsTab ? View.GONE : View.VISIBLE);
        refreshLockUi();
        updateAddPersonButtonVisibility();
        applyFocusNavStyle(treeNav, "Дерево");
        applyFocusNavStyle(cardNav, "Карточка");
        applyFocusNavStyle(peopleNav, "Люди");
        applyFocusNavStyle(linksNav, "Связи");
        applyFocusNavStyle(moreNav, "Ещё");
        if (bottomNavigation != null) {
            ViewGroup.LayoutParams params = bottomNavigation.getLayoutParams();
            if (params != null) {
                params.height = dp(focusTree ? 54 : 74);
                bottomNavigation.setLayoutParams(params);
            }
        }
        if (addPersonButton != null && addPersonButton.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) addPersonButton.getLayoutParams();
            params.setMargins(0, 0, dp(12), dp(focusTree ? 66 : 86));
            addPersonButton.setLayoutParams(params);
        }
        int panelBottom = dp(focusTree ? 60 : 82);
        View[] panels = {cardPanel, linksPanel, guidePanel, filesPanel, viewPanel, branchPanel};
        for (View panel : panels) {
            if (panel == null || !(panel.getLayoutParams() instanceof FrameLayout.LayoutParams)) continue;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panel.getLayoutParams();
            params.setMargins(dp(10), 0, dp(10), panelBottom);
            panel.setLayoutParams(params);
        }
        if (settingsPanel != null && settingsPanel.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) settingsPanel.getLayoutParams();
            params.setMargins(0, statusBarHeight(), 0, dp(focusTree ? 54 : 74));
            settingsPanel.setLayoutParams(params);
        }
        updateHistoryPanel();
    }

    void updateAddPersonButtonVisibility() {
        if (addPersonButton == null) return;
        boolean menuVisible = activePanel != null && !activePanel.isEmpty();
        boolean menuClosing = morePanel != null && morePanel.getVisibility() == View.VISIBLE;
        boolean selectionVisible = selectionToolbar != null
            && selectionToolbar.getVisibility() == View.VISIBLE;
        addPersonButton.setVisibility(menuVisible || menuClosing || selectionVisible ? View.GONE : View.VISIBLE);
    }

    private void applyFocusNavStyle(Button button, String label) {
        if (button == null) return;
        button.setContentDescription(tr(label));
        boolean compact = focusTree && !isLandscapeLayout();
        button.setText(compact ? "" : label);
        button.setCompoundDrawablePadding(compact ? 0 : dp(4));
        button.setPadding(dp(3), compact ? dp(4) : dp(5), dp(3), compact ? dp(4) : dp(5));
        button.setMinHeight(dp(compact ? 46 : isLandscapeLayout() ? 48 : 58));
    }

    String text(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString();
    }

    String datePart(String value, int max) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return "";
        try {
            int number = Integer.parseInt(digits);
            if (number <= 0) return "";
            return String.format(java.util.Locale.US, "%02d", Math.min(max, number));
        } catch (Exception ignored) {
            return "";
        }
    }

    String yearPart(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.length() > 4) digits = digits.substring(digits.length() - 4);
        if (digits.isEmpty()) return "";
        try {
            int number = Integer.parseInt(digits);
            if (number <= 0) return "";
            return String.format(java.util.Locale.US, "%04d", Math.min(9999, number));
        } catch (Exception ignored) {
            return "";
        }
    }

    String humanDate(String day, String month, String year) {
        if (year == null || year.isEmpty()) return "";
        if (day != null && !day.isEmpty() && month != null && !month.isEmpty()) return day + "." + month + "." + year;
        if (month != null && !month.isEmpty()) return month + "." + year;
        return year;
    }

    private void showKeyboard(View target) {
        if (target == null) return;
        target.post(() -> {
            try {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
            } catch (Exception ignored) {
            }
        });
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            View view = getCurrentFocus();
            if (imm != null && view != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        } catch (Exception ignored) {
        }
    }

    void toast(String message) {
        if (message == null || message.trim().isEmpty()) return;
        toastHandler.removeCallbacks(cancelToastRunnable);
        if (currentToast != null) currentToast.cancel();
        currentToast = Toast.makeText(
            this,
            AppLanguage.translate(this, message),
            Toast.LENGTH_SHORT);
        currentToast.show();
        toastHandler.postDelayed(cancelToastRunnable, 900);
    }

    String tr(String value) {
        return AppLanguage.text(this, value);
    }

    int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density * normalizeUiScale(uiScale));
    }

    int uiColor(int color) {
        return AppThemePalette.text(color);
    }

    static float normalizeUiScale(float value) {
        if (!Float.isFinite(value)) return 1f;
        return Math.max(0.9f, Math.min(1.2f, value));
    }

    static float normalizeFontScale(float value) {
        if (!Float.isFinite(value)) return 1f;
        return Math.max(0.92f, Math.min(1.1f, value));
    }
}
