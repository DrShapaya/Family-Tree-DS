package ru.drshapaya.androidft2;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
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
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Date;
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
    static final int REQ_WRITE_PHOTO_PERMISSION = 30;
    static final int REQ_MEMORY_PHOTO = 31;
    static final String VERSION_NAME = "2.6.4";
    static final String VERSION_BADGE = "AndroidFT " + VERSION_NAME;
    static final String[][] TRAINING_STEPS = new String[][]{
        {"add-person", "Создайте карточку", "Нажмите подсвеченную кнопку +. На дереве появится новый человек, которого можно сразу заполнить."},
        {"card-menu", "Меню карточки", "Нажмите «Карточка». Здесь находятся редактор человека, быстрый старт, цвета и удаление."},
        {"links-menu", "Семейные связи", "Нажмите «Связи», чтобы открыть инструменты родительских, родственных и братских линий."},
        {"parent-link", "Родительская линия", "Нажмите «Родительская линия». В обычной работе после этого выбираются родитель и ребёнок."},
        {"tree-menu", "Инструменты дерева", "Нажмите «Дерево». Здесь находятся упорядочивание, рамка, лассо и отображение веток."},
        {"layout-tree", "Упорядочивание", "Нажмите «Упорядочить». Карточки автоматически выстроятся по поколениям и семейным веткам."},
        {"files-menu", "Файлы и экспорт", "Нажмите «Файлы». Здесь сохраняют, импортируют, отправляют и экспортируют дерево."},
        {"settings-menu", "Параметры", "Нажмите «Параметры». Здесь включаются темы, защита правок, линии, фокус и повторное обучение."}
    };

    TreeStore store;
    TreeState state;
    TreeCanvasView treeView;
    FrameLayout stage;
    TextView stats;
    TextView badge;
    EditText search;
    HorizontalScrollView searchSuggestionsScroll;
    LinearLayout searchSuggestions;
    LinearLayout cardPanel;
    LinearLayout linksPanel;
    LinearLayout guidePanel;
    LinearLayout filesPanel;
    LinearLayout viewPanel;
    LinearLayout branchPanel;
    LinearLayout settingsPanel;
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
    Button linksNav;
    Button filesNav;
    Button settingsNav;
    Button treeQualityButton;
    View appHeader;
    View headerBrand;
    Button headerSaveButton;
    View treeHint;
    boolean treeHintDismissed;
    View zoomRail;
    View bottomNavigation;
    Button addPersonButton;
    View trainingParentLinkTarget;
    View trainingLayoutTarget;
    final ArrayDeque<TreeCommand> undoStack = new ArrayDeque<>();
    final ArrayDeque<TreeCommand> redoStack = new ArrayDeque<>();
    String activePanel = "";
    String pendingLinkType = "";
    String pendingLinkFrom = "";
    String selectedLinkId = "";
    String pendingBranchMode = "";
    String branchMode = "all";
    String branchAnchorId = "";
    boolean editLocked = false;
    boolean onlineReadOnly = false;
    boolean viewMode = false;
    boolean generationLines = true;
    boolean hideCardDetails = false;
    boolean compactCards = false;
    boolean focusTree = false;
    boolean workspaceBoundsVisible = true;
    String workspaceBoundsStyle = "soft";
    int workspaceWidth = (int) TreeLayoutEngine.SURFACE_W;
    int workspaceHeight = (int) TreeLayoutEngine.SURFACE_H;
    String parentLineMode = "smart";
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
    private MainActivitySettings settingsModule;
    private MainActivityPanels panelsModule;
    private MainActivityRelations relationsModule;
    private MainActivityEditor editorModule;
    private MainActivityQuickStart quickStartModule;
    private MainActivityOnline onlineModule;
    private OnlineTreeManager onlineManager;
    private TreeSaveCoordinator saveCoordinator;
    private TreeQualityAnalyzer.TreeReport qualityReport = new TreeQualityAnalyzer.TreeReport();
    private final Handler toastHandler = new Handler(Looper.getMainLooper());
    private Toast currentToast;
    private final Runnable cancelToastRunnable = () -> {
        if (currentToast != null) currentToast.cancel();
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLanguage.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (AppLanguage.isEnglish(this) && "Поколение".equals(guideDraftLabel)) {
            guideDraftLabel = "Generation";
        }
        store = new TreeStore(this);
        state = store.load();
        treeHintDismissed = getSharedPreferences("androidft-ui", MODE_PRIVATE)
            .getBoolean("tree_hint_dismissed", false);
        filesModule = new MainActivityFiles(this);
        historyModule = new MainActivityHistory(this);
        headerModule = new MainActivityHeader(this);
        settingsModule = new MainActivitySettings(this);
        panelsModule = new MainActivityPanels(this);
        relationsModule = new MainActivityRelations(this);
        editorModule = new MainActivityEditor(this);
        quickStartModule = new MainActivityQuickStart(this);
        onlineManager = new OnlineTreeManager(this, store, new OnlineTreeManager.Listener() {
            @Override public void onRemoteTree(TreeState remote, String message) {
                applyOnlineTree(remote, message);
            }

            @Override public void onMediaChanged() {
                if (treeView != null) treeView.invalidate();
                if (onlineModule != null) onlineModule.refreshOpenDashboard();
            }

            @Override public void onEditingPermissionChanged(boolean canEdit) {
                onlineReadOnly = !canEdit;
                resetTransientCanvasModes(false);
                if (treeView != null) treeView.setEditLocked(editingBlocked());
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
        String recoveryNotice = store.consumeRecoveryNotice();
        if (!recoveryNotice.isEmpty()) toast(recoveryNotice);
        handleIncomingIntent(getIntent());
        treeView.post(() -> {
            treeView.fit();
            stage.postDelayed(this::offerTrainingIfNeeded, 420);
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    protected void onStop() {
        DiagnosticsLogger.breadcrumb(this, "activity.stop");
        if (saveCoordinator != null) saveCoordinator.flush();
        if (onlineManager != null) onlineManager.stopForegroundChecks();
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (onlineManager != null) onlineManager.startForegroundChecks();
    }

    @Override
    protected void onDestroy() {
        if (saveCoordinator != null) saveCoordinator.close();
        if (onlineManager != null) onlineManager.close();
        super.onDestroy();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (treeView != null) treeView.trimBitmapCache(level);
        DiagnosticsLogger.breadcrumb(this, "memory.trim=" + level);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppThemePalette.isDark()
            ? Color.rgb(16, 23, 27)
            : Color.rgb(243, 246, 248));
        setContentView(root);

        appHeader = buildHeader();
        root.addView(appHeader, new LinearLayout.LayoutParams(-1, -2));

        stage = new FrameLayout(this);
        stage.setFocusableInTouchMode(true);
        stage.setBackgroundColor(AppThemePalette.surface(Color.rgb(248, 251, 252)));
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

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
                if (!activeGuideMode.isEmpty()) finishGuideAction();
            }
            @Override public void onGuideActionMiss() {
                toast("Линия не найдена — режим удаления всё ещё включён");
                updateCanvasModePanel();
            }
            @Override public void onCanvasTouched() {
                clearSearchFocus();
            }
        });
        stage.addView(treeView, new FrameLayout.LayoutParams(-1, -1));

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
                if (Math.abs(distance) >= dp(54)) dismissTreeHint(distance >= 0f ? 1 : -1);
                return true;
            }
            return true;
        });
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(-1, dp(40), Gravity.TOP);
        hintParams.setMargins(dp(12), dp(10), dp(64), 0);
        stage.addView(hint, hintParams);
        treeHint = hint;
        if (treeHintDismissed) hint.setVisibility(View.GONE);

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
        addParams.setMargins(0, 0, dp(12), dp(86));
        stage.addView(add, addParams);
        addPersonButton = add;

        cardPanel = buildCardPanel();
        linksPanel = buildLinksPanel();
        guidePanel = buildGuidePanel();
        filesPanel = buildFilesPanel();
        viewPanel = buildViewPanel();
        branchPanel = buildBranchPanel();
        settingsPanel = buildSettingsPanel();
        stage.addView(cardPanel, bottomPanelWrapParams());
        stage.addView(linksPanel, bottomPanelWrapParams());
        stage.addView(guidePanel, bottomPanelWrapParams());
        stage.addView(filesPanel, bottomPanelWrapParams());
        stage.addView(viewPanel, bottomPanelWrapParams());
        stage.addView(branchPanel, bottomPanelParams(300));
        stage.addView(settingsPanel, fullPanelParams());

        bottomNavigation = buildBottomNav();
        stage.addView(bottomNavigation, bottomParams());
        applyFocusTreeUi();
        showPanel("");

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = String.valueOf(s);
                treeView.setFilter(query);
                updateSearchSuggestions(query);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
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
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setPadding(dp(10), 0, dp(6), 0);
        panel.setBackground(panelBg(Color.argb(235, 248, 251, 252), dp(8), Color.argb(52, 63, 82, 94)));
        panel.setElevation(dp(7));

        branchStatusText = new LocalizedTextView(this);
        branchStatusText.setTextColor(Color.rgb(28, 34, 38));
        branchStatusText.setTextSize(12);
        branchStatusText.setTypeface(uiBold());
        branchStatusText.setGravity(Gravity.CENTER_VERTICAL);
        branchStatusText.setIncludeFontPadding(false);
        panel.addView(branchStatusText, new LinearLayout.LayoutParams(0, -1, 1));

        TextView reset = branchStatusButton("Сброс", v -> resetBranchAnchor());
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(dp(72), dp(34));
        resetParams.setMargins(dp(6), 0, 0, 0);
        panel.addView(reset, resetParams);

        TextView close = closeButton(v -> clearBranchFilter());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        closeParams.setMargins(dp(6), 0, 0, 0);
        panel.addView(close, closeParams);
        panel.setVisibility(View.GONE);
        return panel;
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
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(8));
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
        panel.addView(selectionStatusText, new LinearLayout.LayoutParams(0, -1, 1));

        selectionMoveButton = selectionActionButton(
            R.drawable.ic_menu_check,
            "Готово",
            v -> enableSelectionMove());
        panel.addView(selectionMoveButton, selectionButtonParams());

        selectionStopButton = selectionActionButton(
            R.drawable.ic_menu_stop,
            "Стоп",
            v -> clearSelection());
        panel.addView(selectionStopButton, selectionResetButtonParams());

        selectionAppendButton = selectionActionButton(
            R.drawable.ic_menu_frame,
            "+",
            v -> {
            selectionAppendMode = !selectionAppendMode;
            if (treeView != null) treeView.setSelectionAppendMode(selectionAppendMode);
            updateSelectionToolbar();
            toast(selectionAppendMode
                ? "Добор выделения включён"
                : "Новое выделение заменяет старое");
        });
        selectionAppendButton.setContentDescription(tr("Добавлять к текущему выделению"));
        panel.addView(selectionAppendButton, selectionAppendParams());
        panel.setVisibility(View.GONE);
        return panel;
    }

    private LinearLayout buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(8), dp(7), dp(8), dp(8));
        nav.setBackground(panelBg(Color.argb(242, 248, 251, 252), 0, Color.argb(32, 63, 82, 94)));
        nav.setElevation(dp(8));
        treeNav = navButton("Дерево", R.drawable.ic_nav_tree, v -> {
            togglePanel("view");
            trainingTargetActivated("tree-menu");
        });
        nav.addView(treeNav, new LinearLayout.LayoutParams(0, -1, 1));
        cardNav = navButton("Карточка", R.drawable.ic_nav_card, v -> {
            togglePanel("card");
            trainingTargetActivated("card-menu");
        });
        nav.addView(cardNav, new LinearLayout.LayoutParams(0, -1, 1));
        linksNav = navButton("Связи", R.drawable.ic_nav_links, v -> {
            togglePanel("links");
            trainingTargetActivated("links-menu");
        });
        nav.addView(linksNav, new LinearLayout.LayoutParams(0, -1, 1));
        filesNav = navButton("Файлы", R.drawable.ic_nav_files, v -> {
            togglePanel("files");
            trainingTargetActivated("files-menu");
        });
        nav.addView(filesNav, new LinearLayout.LayoutParams(0, -1, 1));
        settingsNav = navButton("Параметры", R.drawable.ic_nav_more, v -> {
            togglePanel("settings");
            trainingTargetActivated("settings-menu");
        });
        nav.addView(settingsNav, new LinearLayout.LayoutParams(0, -1, 1));
        return nav;
    }

    private LinearLayout buildCardPanel() {
        LinearLayout panel = basePanel();
        panel.setPadding(dp(10), dp(8), dp(10), dp(4));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(cardActionTile(
            R.drawable.ic_nav_card,
            "Открыть карточку",
            "Профиль, память и связи",
            v -> openPersonEditor()), new LinearLayout.LayoutParams(0, dp(112), 1));
        LinearLayout.LayoutParams quickParams = new LinearLayout.LayoutParams(0, dp(112), 1);
        quickParams.setMargins(dp(10), 0, 0, 0);
        actions.addView(cardActionTile(
            R.drawable.ic_menu_add_person,
            "Быстрый старт",
            "Создать основу дерева",
            v -> quickStart()), quickParams);
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
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(10), dp(12), dp(10), dp(10));
        tile.setBackground(panelBg(Color.WHITE, dp(10), Color.rgb(217, 224, 229)));
        tile.setOnClickListener(listener);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(uiColor(Color.rgb(8, 122, 115)));
        tile.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView text = cardActionTitle(label, false);
        text.setGravity(Gravity.CENTER);
        tile.addView(text, new LinearLayout.LayoutParams(-1, dp(28)));
        TextView sub = cardActionDetail(detail, false);
        sub.setGravity(Gravity.CENTER);
        tile.addView(sub, new LinearLayout.LayoutParams(-1, dp(22)));
        return tile;
    }

    private TextView cardActionTitle(String value, boolean danger) {
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

    private TextView cardActionDetail(String value, boolean danger) {
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
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        panel.addView(rows, new LinearLayout.LayoutParams(-1, -2));
        rows.addView(menuRow(R.drawable.ic_menu_save, "Сохранить", "Сохраняет дерево локально на устройстве.", v -> saveToast("Дерево сохранено")));
        rows.addView(menuRow(
            R.drawable.ic_editor_archive,
            "Сохранённые версии",
            "Показывает 10 последних локальных версий и позволяет восстановить любую из них.",
            v -> filesModule.openVersions()));
        rows.addView(menuRow(
            R.drawable.ic_menu_upload,
            "Поделиться деревом",
            "Отправляет один .ftree файл вместе с фото и вложениями.",
            v -> filesModule.shareTreePackage()));
        rows.addView(menuRow(R.drawable.ic_menu_import, "Импорт", "Загружает .ftree, JSON или совместимый текстовый файл.", v -> openImport()));
        rows.addView(menuRow(
            R.drawable.ic_menu_export,
            "Экспорт",
            "Открывает подменю: FamilyTree, JSON, GEDCOM, PNG, PDF и тайлы.",
            v -> filesModule.showExportMenu()));
        return panel;
    }

    private LinearLayout buildLinksPanel() {
        LinearLayout panel = basePanel();
        panel.addView(menuRow(
            R.drawable.ic_menu_route,
            "Кто кому приходится",
            "Выберите две карточки — приложение определит родство в обоих направлениях.",
            v -> startLink("kinship")));
        View parentLinkRow = menuRow(
            R.drawable.ic_menu_parent_line,
            "Родительская линия",
            "Нажмите пункт, затем первую и вторую карточку: первая станет родителем.",
            v -> {
                startLink("parent");
                trainingTargetActivated("parent-link");
            });
        panel.addView(parentLinkRow);
        trainingParentLinkTarget = parentLinkRow instanceof ViewGroup
            ? ((ViewGroup) parentLinkRow).getChildAt(0)
            : parentLinkRow;
        panel.addView(menuRow(R.drawable.ic_menu_heart, "Родственная линия", "Нажмите пункт, затем выберите две карточки для родственной связи.", v -> startLink("family")));
        panel.addView(menuRow(R.drawable.ic_menu_people, "Братская линия", "Нажмите пункт, затем выберите две карточки для связи брат/сестра.", v -> startLink("sibling")));
        panel.addView(menuRow(
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
        row.setGravity(Gravity.CENTER_VERTICAL);
        HueSliderView slider = new HueSliderView(this);
        slider.setColor(TreeState.parseColor(guideDraftColor, Color.rgb(47, 125, 117)));
        TextView preview = new LocalizedTextView(this);
        preview.setBackground(colorSwatchBg(slider.color(), dp(999)));
        slider.setListener((color, fromUser) -> {
            guideDraftColor = TreeState.colorString(color);
            preview.setBackground(colorSwatchBg(color, dp(999)));
            if (treeView != null) treeView.setGuideDraft(guideDraftColor, guideDraftLabel);
        });
        row.addView(slider, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        previewParams.setMargins(dp(8), 0, 0, 0);
        row.addView(preview, previewParams);
        block.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
        return block;
    }

    private LinearLayout buildViewPanel() {
        LinearLayout panel = basePanel();
        panel.setPadding(dp(10), dp(8), dp(10), dp(2));
        ScrollView scroll = new ScrollView(this);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        View layoutRow = menuRow(R.drawable.ic_menu_layout, "Упорядочить", "Перестраивает карточки нативным layout-алгоритмом.", v -> {
            recordUndo("Упорядочено дерево");
            TreeLayoutEngine.layout(state);
            workspaceWidth = TreeLayoutEngine.normalizeSurfaceWidth(state.workspaceWidth);
            workspaceHeight = TreeLayoutEngine.normalizeSurfaceHeight(state.workspaceHeight);
            treeView.setWorkspaceBounds(
                workspaceBoundsVisible,
                workspaceBoundsStyle,
                workspaceWidth,
                workspaceHeight);
            saveToast("Дерево упорядочено");
            treeView.invalidate();
            trainingTargetActivated("layout-tree");
        });
        rows.addView(layoutRow);
        trainingLayoutTarget = layoutRow instanceof ViewGroup
            ? ((ViewGroup) layoutRow).getChildAt(0)
            : layoutRow;
        rows.addView(menuRow(R.drawable.ic_menu_frame, "Рамка", "Выделяет карточки прямоугольной рамкой.", v -> startSelectionMode("rect")));
        rows.addView(menuRow(R.drawable.ic_menu_lasso, "Лассо", "Выделяет карточки свободной линией.", v -> startSelectionMode("lasso")));
        rows.addView(menuRow(R.drawable.ic_menu_branch, "Отображение ветки", "Выберите, какую часть дерева показать.", v -> togglePanel("branch")));
        rows.addView(menuRow(R.drawable.ic_menu_grid_lines, "Линии поколений", "Открывает направляющие и линии поколений.", v -> togglePanel("guides")));
        return panel;
    }

    private LinearLayout buildBranchPanel() {
        LinearLayout panel = basePanel();
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
        ScrollView scroll = new ScrollView(this);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        settingsContent = new LinearLayout(this);
        settingsContent.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(settingsContent);
        addSettingsContent(settingsContent);
        return panel;
    }

    void addSettingsContent(LinearLayout panel) {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new LocalizedTextView(this);
        title.setText("Параметры");
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setTextSize(14);
        title.setTypeface(uiBold());
        top.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));
        panel.addView(top);
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
        panel.addView(settingSwitchRow(R.drawable.ic_menu_history, "История действий", state == null || !state.historyHidden, "Показывает или скрывает панель последних действий на поле дерева.", v -> toggleHistoryPanel()));
        panel.addView(settingActionRow(
            R.drawable.ic_menu_frame,
            tr("Границы поля") + " · " + workspaceWidth + " × " + workspaceHeight,
            workspaceBoundsVisible
                ? "Настройте размер, вид границы и затемнение за пределами рабочего поля."
                : "Границы и затемнение рабочего поля выключены.",
            v -> showWorkspaceBoundsDialog()));
        panel.addView(themeRow());
        boolean editingBlocked = editingBlocked();
        panel.addView(settingSwitchRow(
            editingBlocked ? R.drawable.ic_menu_lock : R.drawable.ic_menu_unlock,
            onlineReadOnly ? "Только просмотр" : "Защита правок",
            editingBlocked,
            onlineReadOnly
                ? "Глава дерева временно отключил редактирование для вашего аккаунта."
                : "Блокирует или разрешает редактирование, чтобы не изменить дерево случайно.",
            v -> toggleLock()));
        panel.addView(settingSwitchRow(generationLines ? R.drawable.ic_menu_grid_lines : R.drawable.ic_menu_generation_lines_off, "Линии поколений", generationLines, "Показывает или скрывает горизонтальные линии поколений.", v -> toggleGenerationLines()));
        panel.addView(settingSwitchRow(
            R.drawable.ic_menu_straight_links,
            "Ровные связи",
            "orthogonal".equals(parentLineMode),
            "По умолчанию связи родителей с детьми рисуются плавными кривыми. Этот режим включает ровные прямоугольные связи.",
            v -> toggleParentLineMode()));
        panel.addView(settingSwitchRow(hideCardDetails ? R.drawable.ic_menu_eye_off : R.drawable.ic_menu_eye, "Скрыть детали карточек", hideCardDetails, "Скрывает вторичные детали карточек, оставляя основной текст чище.", v -> toggleHideDetails()));
        panel.addView(settingSwitchRow(compactCards ? R.drawable.ic_menu_compress : R.drawable.ic_menu_expand, "Компактные карточки", compactCards, "Переключает обычные карточки 4 на 7 на компактные 3 на 5. Линии автоматически подстраиваются под размер.", v -> toggleCompactCards()));
        panel.addView(settingSwitchRow(focusTree ? R.drawable.ic_menu_focus_off : R.drawable.ic_menu_focus, "Фокус на дереве", focusTree, "Оставляет больше места дереву и делает нижнюю панель компактнее.", v -> toggleFocusTree()));
        panel.addView(menuRow(
            R.drawable.ic_menu_trash,
            "Удалить всё дерево",
            "Удаляет все карточки, связи и линии поколений. Действие можно отменить через историю.",
            v -> confirmDeleteWholeTree(),
            true));
    }

    private void showWorkspaceBoundsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(16), dp(18), dp(16));
        shell.setBackground(panelBg(
            Color.rgb(252, 254, 254),
            dp(22),
            Color.argb(64, 63, 82, 94)));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_menu_frame);
        icon.setColorFilter(Color.WHITE);
        icon.setPadding(dp(12), dp(12), dp(12), dp(12));
        icon.setBackground(tealGradientBg(dp(14)));
        header.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(13), 0, 0, 0);
        TextView title = new LocalizedTextView(this);
        title.setText("Границы поля");
        title.setTextSize(20);
        title.setTypeface(uiBold());
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setIncludeFontPadding(false);
        copy.addView(title);
        TextView detail = new LocalizedTextView(this);
        detail.setText("Выберите, как обозначать рабочую область дерева");
        detail.setTextSize(11);
        detail.setTextColor(Color.rgb(83, 94, 103));
        detail.setPadding(0, dp(4), 0, 0);
        detail.setIncludeFontPadding(false);
        copy.addView(detail);
        header.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(header);

        LinearLayout styles = new LinearLayout(this);
        styles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams stylesParams = new LinearLayout.LayoutParams(-1, -2);
        stylesParams.setMargins(0, dp(16), 0, 0);
        shell.addView(styles, stylesParams);

        TextView sizeCaption = new LocalizedTextView(this);
        sizeCaption.setText("РАЗМЕР РАБОЧЕЙ ЗОНЫ");
        sizeCaption.setTextSize(10);
        sizeCaption.setTypeface(uiBold());
        sizeCaption.setTextColor(Color.rgb(8, 122, 115));
        sizeCaption.setGravity(Gravity.CENTER_VERTICAL);
        styles.addView(sizeCaption, new LinearLayout.LayoutParams(-1, dp(26)));

        TextView sizeHint = new LocalizedTextView(this);
        sizeHint.setText("Ширина × высота в единицах полотна");
        sizeHint.setTextSize(10);
        sizeHint.setTextColor(Color.rgb(83, 94, 103));
        sizeHint.setIncludeFontPadding(false);
        LinearLayout.LayoutParams sizeHintParams = new LinearLayout.LayoutParams(-1, -2);
        sizeHintParams.setMargins(0, 0, 0, dp(7));
        styles.addView(sizeHint, sizeHintParams);

        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText widthInput = workspaceSizeField("Ширина", workspaceWidth);
        EditText heightInput = workspaceSizeField("Высота", workspaceHeight);
        sizeRow.addView(widthInput, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView multiply = new LocalizedTextView(this);
        multiply.setText("×");
        multiply.setTextSize(18);
        multiply.setTextColor(Color.rgb(83, 94, 103));
        multiply.setGravity(Gravity.CENTER);
        sizeRow.addView(multiply, new LinearLayout.LayoutParams(dp(28), dp(48)));
        sizeRow.addView(heightInput, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button visibilityToggle = actionButton(workspaceBoundsVisible ? "Вкл" : "Выкл", null);
        visibilityToggle.setTextSize(10);
        visibilityToggle.setTextColor(workspaceBoundsVisible ? Color.WHITE : Color.rgb(83, 94, 103));
        visibilityToggle.setBackground(workspaceBoundsVisible
            ? tealGradientBg(dp(12))
            : panelBg(Color.WHITE, dp(12), Color.rgb(217, 224, 229)));
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(dp(62), dp(48));
        toggleParams.setMargins(dp(8), 0, 0, 0);
        sizeRow.addView(visibilityToggle, toggleParams);
        visibilityToggle.setOnClickListener(v -> {
            workspaceBoundsVisible = !workspaceBoundsVisible;
            visibilityToggle.setText(workspaceBoundsVisible ? "Вкл" : "Выкл");
            visibilityToggle.setTextColor(workspaceBoundsVisible ? Color.WHITE : Color.rgb(83, 94, 103));
            visibilityToggle.setBackground(workspaceBoundsVisible
                ? tealGradientBg(dp(12))
                : panelBg(Color.WHITE, dp(12), Color.rgb(217, 224, 229)));
            applyWorkspaceBoundsSettings();
        });
        styles.addView(sizeRow, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout sizeActions = new LinearLayout(this);
        Button applySize = actionButton("Применить размер", v -> requestWorkspaceSizeChange(
            widthInput,
            heightInput,
            parseWorkspaceSize(widthInput, workspaceWidth, true),
            parseWorkspaceSize(heightInput, workspaceHeight, false)));
        applySize.setTextSize(11);
        applySize.setTextColor(Color.rgb(8, 122, 115));
        sizeActions.addView(applySize, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button resetSize = actionButton("Сбросить 24 000 × 16 000", v -> requestWorkspaceSizeChange(
            widthInput,
            heightInput,
            (int) TreeLayoutEngine.SURFACE_W,
            (int) TreeLayoutEngine.SURFACE_H));
        resetSize.setTextSize(10);
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(0, dp(44), 1.35f);
        resetParams.setMargins(dp(8), 0, 0, 0);
        sizeActions.addView(resetSize, resetParams);
        LinearLayout.LayoutParams sizeActionsParams = new LinearLayout.LayoutParams(-1, dp(44));
        sizeActionsParams.setMargins(0, dp(8), 0, 0);
        styles.addView(sizeActions, sizeActionsParams);

        TextView caption = new LocalizedTextView(this);
        caption.setText("ОФОРМЛЕНИЕ");
        caption.setTextSize(10);
        caption.setTypeface(uiBold());
        caption.setTextColor(Color.rgb(8, 122, 115));
        caption.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-1, dp(34));
        captionParams.setMargins(dp(2), dp(8), 0, 0);
        styles.addView(caption, captionParams);
        styles.addView(workspaceBoundsChoiceRow(
            "≈", "Мягкие", "Спокойный пунктир и лёгкое затемнение снаружи", "soft", dialog));
        styles.addView(workspaceBoundsChoiceRow(
            "!", "Контрастные", "Более заметная линия и усиленное затемнение", "contrast", dialog));
        styles.addView(workspaceBoundsChoiceRow(
            "□", "Только контур", "Тонкая сплошная линия без затемнения", "outline", dialog));

        Button done = actionButton("Готово", v -> dialog.dismiss());
        done.setTextColor(Color.WHITE);
        done.setBackground(tealGradientBg(dp(13)));
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(-1, dp(48));
        doneParams.setMargins(0, dp(7), 0, 0);
        shell.addView(done, doneParams);

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(
                getResources().getDisplayMetrics().widthPixels - dp(24),
                dp(470));
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.dimAmount = 0.34f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
    }

    private View workspaceBoundsChoiceRow(
        String mark,
        String title,
        String detail,
        String value,
        Dialog dialog
    ) {
        boolean selected = value.equals(workspaceBoundsStyle);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(7), dp(12), dp(7));
        row.setBackground(panelBg(
            selected ? Color.rgb(232, 248, 246) : Color.WHITE,
            dp(14),
            selected ? Color.argb(112, 24, 169, 153) : Color.rgb(217, 224, 229)));
        TextView badge = new LocalizedTextView(this);
        badge.setText(mark);
        badge.setTextSize(16);
        badge.setTypeface(uiBold());
        badge.setTextColor(selected ? Color.WHITE : Color.rgb(8, 122, 115));
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(panelBg(
            selected ? Color.rgb(8, 122, 115) : Color.rgb(232, 248, 246),
            dp(999),
            Color.argb(74, 24, 169, 153)));
        row.addView(badge, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(11), 0, dp(8), 0);
        TextView heading = new LocalizedTextView(this);
        heading.setText(title);
        heading.setTextSize(14);
        heading.setTypeface(uiBold());
        heading.setTextColor(Color.rgb(28, 34, 38));
        heading.setIncludeFontPadding(false);
        labels.addView(heading);
        TextView description = new LocalizedTextView(this);
        description.setText(detail);
        description.setTextSize(10);
        description.setTextColor(Color.rgb(83, 94, 103));
        description.setIncludeFontPadding(false);
        labels.addView(description);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView check = new LocalizedTextView(this);
        check.setText(selected ? "✓" : "");
        check.setTextSize(14);
        check.setTypeface(uiBold());
        check.setTextColor(Color.WHITE);
        check.setGravity(Gravity.CENTER);
        check.setBackground(panelBg(
            selected ? Color.rgb(24, 169, 153) : Color.TRANSPARENT,
            dp(999),
            Color.TRANSPARENT));
        row.addView(check, new LinearLayout.LayoutParams(dp(26), dp(26)));
        row.setOnClickListener(v -> {
            workspaceBoundsStyle = value;
            workspaceBoundsVisible = true;
            applyWorkspaceBoundsSettings();
            dialog.dismiss();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(62));
        params.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(params);
        return row;
    }

    private EditText workspaceSizeField(String hint, int value) {
        EditText input = field(hint);
        input.setSingleLine(true);
        input.setGravity(Gravity.CENTER);
        input.setTextSize(13);
        input.setSelectAllOnFocus(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
        LocalizedViews.setRaw(input, String.valueOf(value));
        return input;
    }

    private int parseWorkspaceSize(EditText input, int fallback, boolean width) {
        try {
            int value = Integer.parseInt(text(input).trim());
            return width
                ? TreeLayoutEngine.normalizeSurfaceWidth(value)
                : TreeLayoutEngine.normalizeSurfaceHeight(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void requestWorkspaceSizeChange(
        EditText widthInput,
        EditText heightInput,
        int requestedWidth,
        int requestedHeight
    ) {
        int nextWidth = TreeLayoutEngine.normalizeSurfaceWidth(requestedWidth);
        int nextHeight = TreeLayoutEngine.normalizeSurfaceHeight(requestedHeight);
        if (nextWidth < workspaceWidth || nextHeight < workspaceHeight) {
            showWorkspaceShrinkWarning(widthInput, heightInput, nextWidth, nextHeight);
            return;
        }
        applyWorkspaceSize(widthInput, heightInput, nextWidth, nextHeight);
    }

    private void showWorkspaceShrinkWarning(
        EditText widthInput,
        EditText heightInput,
        int nextWidth,
        int nextHeight
    ) {
        Dialog warning = new Dialog(this);
        warning.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(18), dp(18), dp(16));
        shell.setBackground(panelBg(
            Color.rgb(255, 253, 247),
            dp(22),
            Color.argb(88, 224, 162, 38)));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_menu_shield);
        icon.setColorFilter(Color.WHITE);
        icon.setPadding(dp(12), dp(12), dp(12), dp(12));
        icon.setBackground(panelBg(
            Color.rgb(224, 162, 38),
            dp(14),
            Color.argb(90, 255, 255, 255)));
        header.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(13), 0, 0, 0);
        TextView title = new LocalizedTextView(this);
        title.setText("Дерево может сместиться");
        title.setTextSize(18);
        title.setTypeface(uiBold());
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setIncludeFontPadding(false);
        labels.addView(title);
        TextView subtitle = new LocalizedTextView(this);
        subtitle.setText("Вы уменьшаете рабочую область");
        subtitle.setTextSize(11);
        subtitle.setTextColor(Color.rgb(156, 105, 18));
        subtitle.setPadding(0, dp(4), 0, 0);
        subtitle.setIncludeFontPadding(false);
        labels.addView(subtitle);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(header);

        TextView explanation = new LocalizedTextView(this);
        explanation.setText(
            "Карточки за новыми границами будут перенесены внутрь поля. "
                + "Из-за этого расположение веток может измениться.");
        explanation.setTextSize(12);
        explanation.setTextColor(Color.rgb(76, 87, 96));
        explanation.setPadding(dp(13), dp(11), dp(13), dp(11));
        explanation.setBackground(panelBg(
            Color.rgb(255, 248, 226),
            dp(14),
            Color.argb(80, 224, 162, 38)));
        LinearLayout.LayoutParams explanationParams = new LinearLayout.LayoutParams(-1, -2);
        explanationParams.setMargins(0, dp(16), 0, 0);
        shell.addView(explanation, explanationParams);

        TextView dimensions = new LocalizedTextView(this);
        LocalizedViews.setRaw(
            dimensions,
            workspaceWidth + " × " + workspaceHeight + "  →  " + nextWidth + " × " + nextHeight);
        dimensions.setTextSize(13);
        dimensions.setTypeface(uiBold());
        dimensions.setTextColor(Color.rgb(156, 105, 18));
        dimensions.setGravity(Gravity.CENTER);
        dimensions.setBackground(panelBg(
            Color.rgb(255, 252, 242),
            dp(12),
            Color.argb(72, 224, 162, 38)));
        LinearLayout.LayoutParams dimensionsParams = new LinearLayout.LayoutParams(-1, dp(42));
        dimensionsParams.setMargins(0, dp(10), 0, 0);
        shell.addView(dimensions, dimensionsParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = actionButton("Отмена", v -> warning.dismiss());
        cancel.setTextSize(12);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button confirm = actionButton("Всё равно уменьшить", v -> {
            warning.dismiss();
            applyWorkspaceSize(widthInput, heightInput, nextWidth, nextHeight);
        });
        confirm.setTextSize(11);
        confirm.setTextColor(Color.WHITE);
        confirm.setBackground(panelBg(
            Color.rgb(205, 137, 20),
            dp(13),
            Color.argb(90, 255, 255, 255)));
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, dp(48), 1.35f);
        confirmParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(confirm, confirmParams);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(48));
        actionParams.setMargins(0, dp(14), 0, 0);
        shell.addView(actions, actionParams);

        warning.setContentView(shell);
        warning.setCanceledOnTouchOutside(true);
        warning.show();
        Window window = warning.getWindow();
        if (window != null) {
            int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(440));
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.dimAmount = 0.42f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
    }

    private void applyWorkspaceSize(
        EditText widthInput,
        EditText heightInput,
        int requestedWidth,
        int requestedHeight
    ) {
        int nextWidth = TreeLayoutEngine.normalizeSurfaceWidth(requestedWidth);
        int nextHeight = TreeLayoutEngine.normalizeSurfaceHeight(requestedHeight);
        LocalizedViews.setRaw(widthInput, String.valueOf(nextWidth));
        LocalizedViews.setRaw(heightInput, String.valueOf(nextHeight));
        if (workspaceWidth == nextWidth && workspaceHeight == nextHeight) {
            toast("Размер рабочей зоны уже установлен");
            return;
        }
        recordUndo(
            "Изменён размер рабочей зоны",
            nextWidth + " × " + nextHeight);
        workspaceWidth = nextWidth;
        workspaceHeight = nextHeight;
        float cardWidth = compactCards ? TreeLayoutEngine.GRID * 5f : TreeLayoutEngine.CARD_W;
        float cardHeight = compactCards ? TreeLayoutEngine.GRID * 3f : TreeLayoutEngine.CARD_H;
        float maxX = Math.max(0f, workspaceWidth - cardWidth);
        float maxY = Math.max(0f, workspaceHeight - cardHeight);
        if (state != null) {
            for (Person person : state.people.values()) {
                person.x = Math.max(0f, Math.min(maxX, TreeLayoutEngine.snap(person.x)));
                person.y = Math.max(0f, Math.min(maxY, TreeLayoutEngine.snap(person.y)));
            }
        }
        treeView.invalidateStructureCaches();
        applyWorkspaceBoundsSettings();
        toast("Размер рабочей зоны обновлён");
    }

    private void applyWorkspaceBoundsSettings() {
        if (state != null) {
            state.workspaceBoundsVisible = workspaceBoundsVisible;
            state.workspaceBoundsStyle = workspaceBoundsStyle;
            state.workspaceWidth = workspaceWidth;
            state.workspaceHeight = workspaceHeight;
        }
        treeView.setWorkspaceBounds(
            workspaceBoundsVisible,
            workspaceBoundsStyle,
            workspaceWidth,
            workspaceHeight);
        saveOnly();
        refreshSettingsIfVisible();
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
            if (saveCoordinator != null) saveCoordinator.flush();
            dialog.dismiss();
            recreate();
        } else {
            dialog.dismiss();
        }
    }

    void bindState() {
        syncSettingsToState();
        qualityReport = TreeQualityAnalyzer.analyze(state);
        treeView.setState(state);
        treeView.setQualityReports(qualityReport.people);
        treeView.setEditLocked(editingBlocked());
        treeView.setGenerationLines(generationLines);
        treeView.setHideDetails(hideCardDetails);
        treeView.setCompactCards(compactCards);
        treeView.setWorkspaceBounds(
            workspaceBoundsVisible,
            workspaceBoundsStyle,
            workspaceWidth,
            workspaceHeight);
        treeView.setParentLineMode(parentLineMode);
        treeView.setTheme(theme);
        treeView.setBranchMode(branchMode, branchAnchorId);
        treeView.setLinkState(pendingLinkFrom, selectedLinkId);
        bindEditor(state.selectedPerson());
        updateStats();
        updateTreeQualityButton();
        updateCanvasModePanel();
        updateHistoryButtons();
        updateHistoryPanel();
        updateBranchStatusPanel();
        updateSelectionToolbar();
    }

    private void applyOnlineTree(TreeState remote, String message) {
        if (remote == null || isFinishing() || isDestroyed()) return;
        TreeState local = state;
        if (local != null) {
            remote.theme = local.theme;
            remote.printScale = local.printScale;
            remote.editLocked = local.editLocked;
            remote.historyHidden = local.historyHidden;
            remote.inspectorHidden = local.inspectorHidden;
            remote.adminCollapsed = local.adminCollapsed;
            remote.guidesVisible = local.guidesVisible;
            remote.hideCardDetails = local.hideCardDetails;
            remote.compactCards = local.compactCards;
            remote.focusTree = local.focusTree;
            remote.workspaceBoundsVisible = local.workspaceBoundsVisible;
            remote.workspaceBoundsStyle = local.workspaceBoundsStyle;
            remote.workspaceWidth = local.workspaceWidth;
            remote.workspaceHeight = local.workspaceHeight;
            remote.parentLineMode = local.parentLineMode;
            if (remote.people.containsKey(local.selectedId)) remote.selectedId = local.selectedId;
        }
        state = remote;
        undoStack.clear();
        redoStack.clear();
        resetTransientCanvasModes(false);
        applyStateSettings();
        TreeLayoutEngine.ensurePositions(state);
        bindState();
        if (saveCoordinator != null) saveCoordinator.requestImmediate();
        if (message != null && !message.isEmpty()) toast(message);
    }

    void openOnlineMenu() {
        if (onlineModule != null) onlineModule.openDashboard();
    }

    private String personInitials(String name) {
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
        workspaceBoundsVisible = state.workspaceBoundsVisible;
        workspaceBoundsStyle = state.workspaceBoundsStyle;
        workspaceWidth = TreeLayoutEngine.normalizeSurfaceWidth(state.workspaceWidth);
        workspaceHeight = TreeLayoutEngine.normalizeSurfaceHeight(state.workspaceHeight);
        parentLineMode = "orthogonal".equals(state.parentLineMode) ? "orthogonal" : "smart";
        theme = normalizeTheme(state.theme);
    }

    private void syncSettingsToState() {
        if (state == null) return;
        state.editLocked = editLocked;
        state.readerMode = false;
        state.guidesVisible = generationLines;
        state.hideCardDetails = hideCardDetails;
        state.compactCards = compactCards;
        state.focusTree = focusTree;
        state.workspaceBoundsVisible = workspaceBoundsVisible;
        state.workspaceBoundsStyle = workspaceBoundsStyle;
        state.workspaceWidth = TreeLayoutEngine.normalizeSurfaceWidth(workspaceWidth);
        state.workspaceHeight = TreeLayoutEngine.normalizeSurfaceHeight(workspaceHeight);
        state.parentLineMode = "orthogonal".equals(parentLineMode) ? "orthogonal" : "smart";
        state.theme = normalizeTheme(theme);
    }

    void bindEditor(Person person) {
        editorModule.bindEditor(person);
    }

    boolean editingBlocked() {
        return editLocked || onlineReadOnly;
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

    private void addLoosePerson() {
        if (editingBlocked()) return;
        recordUndo("Добавлена пустая карточка");
        hideKeyboard();
        PointF center = treeView.viewportCenterWorld();
        float[] spot = findOpenSpot(center.x, center.y);
        Person person = state.addPerson(tr("Пустая карточка"), spot[0], spot[1]);
        state.selectedId = person.id;
        saveToast("Карточка добавлена");
        bindState();
        treeView.invalidate();
        trainingTargetActivated("add-person");
    }

    private void showAddPersonMenu() {
        if (editingBlocked() || state == null || addPersonButton == null) return;
        Person selected = state.selectedPerson();
        if (selected == null) {
            addLoosePerson();
            return;
        }

        final int popupWidth = Math.min(
            dp(300),
            getResources().getDisplayMetrics().widthPixels - dp(24));
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(9), dp(9), dp(9), dp(9));
        menu.setBackground(panelBg(
            Color.rgb(248, 251, 252),
            dp(14),
            Color.argb(64, 24, 169, 153)));
        menu.setElevation(dp(12));

        PopupWindow popup = new PopupWindow(menu, popupWidth, -2, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(12));

        menu.addView(
            personMenuHeader(selected, "Выбрана карточка"),
            new LinearLayout.LayoutParams(-1, dp(64)));
        menu.addView(
            personMenuSection("ДОБАВИТЬ К ВЫБРАННОЙ КАРТОЧКЕ"),
            new LinearLayout.LayoutParams(-1, dp(30)));
        menu.addView(personMenuAction(
            R.drawable.ic_menu_add_person,
            "Добавить родителей",
            false,
            popup,
            () -> showRelativeCountDialog(selected, "parents", 2)));
        menu.addView(personMenuAction(
            R.drawable.ic_menu_child,
            "Добавить детей",
            false,
            popup,
            () -> showRelativeCountDialog(selected, "children", 4)));
        menu.addView(personMenuAction(
            R.drawable.ic_menu_heart,
            "Добавить партнёра",
            false,
            popup,
            () -> addRelationAction("add-partner")));
        menu.addView(personMenuAction(
            R.drawable.ic_menu_people,
            "Добавить брата/сестру",
            false,
            popup,
            () -> showRelativeCountDialog(selected, "siblings", 3)));
        menu.addView(
            personMenuSection("ОТДЕЛЬНАЯ КАРТОЧКА"),
            new LinearLayout.LayoutParams(-1, dp(30)));
        menu.addView(personMenuAction(
            R.drawable.ic_menu_add_box,
            "Создать пустую карточку",
            false,
            popup,
            this::addLoosePerson));

        menu.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int[] anchor = new int[2];
        addPersonButton.getLocationOnScreen(anchor);
        int popupHeight = menu.getMeasuredHeight();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int x = Math.max(
            dp(8),
            Math.min(screenWidth - popupWidth - dp(8), anchor[0] + addPersonButton.getWidth() - popupWidth));
        int y = Math.max(dp(12), anchor[1] - popupHeight - dp(8));
        popup.showAtLocation(treeView, Gravity.TOP | Gravity.LEFT, x, y);
    }

    private void showRelativeCountDialog(Person selected, String kind, int maximum) {
        if (selected == null || !state.people.containsKey(selected.id)) return;
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(17), dp(18), dp(16));
        shell.setBackground(panelBg(Color.WHITE, dp(20), Color.argb(60, 63, 82, 94)));

        TextView eyebrow = new LocalizedTextView(this);
        eyebrow.setText("ВЫБРАНА КАРТОЧКА");
        eyebrow.setTextSize(9);
        eyebrow.setTypeface(uiBold());
        eyebrow.setTextColor(Color.rgb(8, 122, 115));
        eyebrow.setIncludeFontPadding(false);
        shell.addView(eyebrow, new LinearLayout.LayoutParams(-1, dp(20)));

        TextView personName = cardActionTitle(
            selected.name == null || selected.name.trim().isEmpty() ? "Без имени" : selected.name.trim(),
            false);
        personName.setTextSize(15);
        personName.setSingleLine(true);
        personName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        shell.addView(personName, new LinearLayout.LayoutParams(-1, dp(30)));

        String relation = "parents".equals(kind)
            ? "родителей"
            : "children".equals(kind) ? "детей" : "братьев или сестёр";
        TextView title = cardActionTitle("Сколько добавить?", false);
        title.setTextSize(18);
        title.setPadding(0, dp(7), 0, 0);
        shell.addView(title, new LinearLayout.LayoutParams(-1, dp(40)));
        TextView detail = cardActionDetail("Выберите количество " + relation, false);
        detail.setTextSize(11);
        shell.addView(detail, new LinearLayout.LayoutParams(-1, dp(26)));

        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        choices.setGravity(Gravity.CENTER);
        for (int count = 1; count <= maximum; count++) {
            final int selectedCount = count;
            Button choice = actionButton(String.valueOf(count), v -> {
                dialog.dismiss();
                if (!state.people.containsKey(selected.id)) return;
                state.selectedId = selected.id;
                addRelationAction("add-" + kind + "-" + selectedCount);
            });
            choice.setTextSize(18);
            choice.setTextColor(Color.rgb(8, 122, 115));
            choice.setBackground(panelBg(Color.rgb(232, 248, 246), dp(13), Color.argb(88, 24, 169, 153)));
            LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(0, dp(52), 1);
            if (count > 1) choiceParams.setMargins(dp(8), 0, 0, 0);
            choices.addView(choice, choiceParams);
        }
        LinearLayout.LayoutParams choicesParams = new LinearLayout.LayoutParams(-1, dp(52));
        choicesParams.setMargins(0, dp(10), 0, 0);
        shell.addView(choices, choicesParams);

        Button cancel = actionButton("Отмена", v -> dialog.dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, dp(46));
        cancelParams.setMargins(0, dp(10), 0, 0);
        shell.addView(cancel, cancelParams);

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(390));
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.dimAmount = 0.34f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
    }

    private void openPersonActions(Person person, float screenX, float screenY) {
        if (person == null) return;
        refreshTreeQuality();
        final int popupWidth = Math.min(dp(324), getResources().getDisplayMetrics().widthPixels - dp(24));
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(9), dp(9), dp(9), dp(9));
        menu.setBackground(panelBg(Color.rgb(248, 251, 252), dp(12), Color.argb(51, 93, 85, 72)));
        menu.setElevation(dp(12));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(menu, new ScrollView.LayoutParams(-1, -2));
        PopupWindow popup = new PopupWindow(scroll, popupWidth, -2, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(12));

        TreeQualityAnalyzer.PersonReport report = qualityReport.person(person.id);
        menu.addView(selectedPersonMenuHeader(person, report), new LinearLayout.LayoutParams(-1, dp(86)));
        menu.addView(personMenuSpaced(
            personMenuAction(
                R.drawable.ic_nav_card,
                "Открыть карточку",
                false,
                popup,
                this::openPersonEditor),
            dp(7),
            0));

        menu.addView(personMenuSection("ПОКАЗАТЬ ВЕТКУ"), new LinearLayout.LayoutParams(-1, dp(30)));
        Person rootPerson = state.people.get(state.rootId);
        KinshipCalculator.Result kinship = rootPerson == null
            ? null
            : KinshipCalculator.calculate(state, person.id, rootPerson.id);
        String kinshipDetail = person.id.equals(state.rootId)
            ? "Это ваша карточка"
            : kinship != null && kinship.found
                ? "Для вас: " + kinship.firstToSecond
                : "Родство пока не найдено";
        menu.addView(personMenuGridRow(
            personMenuGridTile(R.drawable.ic_menu_ancestors, "Предки", "", false, popup, () -> showPersonBranch(person, "ancestors")),
            personMenuGridTile(R.drawable.ic_menu_descendants, "Потомки", "", false, popup, () -> showPersonBranch(person, "descendants"))));
        menu.addView(personMenuGridRow(
            personMenuGridTile(R.drawable.ic_menu_near, "Близкие", "", false, popup, () -> showPersonBranch(person, "near")),
            personMenuGridTile(R.drawable.ic_menu_route, "Связь со мной", kinshipDetail, false, popup, () -> showKinshipToRoot(person))));

        menu.addView(personMenuSection("ПРОВЕРИТЬ ДАННЫЕ"), new LinearLayout.LayoutParams(-1, dp(30)));
        menu.addView(personMenuSpaced(
            personQualityAction(person, report, TreeQualityAnalyzer.CATEGORY_PARENT_AGE, "Возраст родителей", R.drawable.ic_menu_people, popup),
            0,
            dp(5)));
        menu.addView(personMenuSpaced(
            personQualityAction(person, report, TreeQualityAnalyzer.CATEGORY_DATES, "Несовместимые даты", R.drawable.ic_field_calendar, popup),
            0,
            dp(5)));
        menu.addView(personMenuSpaced(
            personQualityAction(person, report, TreeQualityAnalyzer.CATEGORY_RELATIONS, "Подозрительные связи", R.drawable.ic_nav_links, popup),
            0,
            dp(5)));
        menu.addView(personQualityAction(person, report, TreeQualityAnalyzer.CATEGORY_MISSING, "Недостающие сведения", R.drawable.ic_menu_note_add, popup));

        menu.addView(personMenuSection("ДЕЙСТВИЯ"), new LinearLayout.LayoutParams(-1, dp(30)));
        menu.addView(personMenuGridRow(
            personMenuGridTile(
                R.drawable.ic_menu_pin,
                person.pinned ? "Открепить" : "Закрепить",
                "",
                false,
                popup,
                () -> togglePersonPin(person)),
            personMenuGridTile(R.drawable.ic_menu_copy, "Дублировать", "", false, popup, this::duplicateSelected)));
        menu.addView(personMenuAction(R.drawable.ic_menu_share, "Поделиться биографией", false, popup, () -> shareBiography(person)));
        menu.addView(personMenuAction(R.drawable.ic_menu_trash, "Удалить карточку", true, popup, this::confirmDelete));

        menu.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int popupHeight = Math.min(menu.getMeasuredHeight(), screenHeight - dp(32));
        popup.setHeight(popupHeight);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int x = Math.max(dp(8), Math.min(screenWidth - popupWidth - dp(8), Math.round(screenX) - popupWidth + dp(18)));
        int y = Math.round(screenY) + dp(8);
        if (y + popupHeight > screenHeight - dp(16)) y = Math.max(dp(16), Math.round(screenY) - popupHeight - dp(8));
        popup.showAtLocation(treeView, Gravity.TOP | Gravity.LEFT, x, y);
    }

    private View selectedPersonMenuHeader(Person person, TreeQualityAnalyzer.PersonReport report) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
        header.setBackground(panelBg(Color.rgb(232, 248, 246), dp(10), Color.argb(82, 24, 169, 153)));
        TextView eyebrow = new LocalizedTextView(this);
        eyebrow.setText("Выбрана карточка");
        eyebrow.setTextColor(Color.rgb(8, 122, 115));
        eyebrow.setTextSize(9);
        eyebrow.setTypeface(uiBold());
        eyebrow.setIncludeFontPadding(false);
        header.addView(eyebrow, new LinearLayout.LayoutParams(-1, dp(18)));
        TextView name = cardActionTitle(
            person.name == null || person.name.trim().isEmpty() ? "Без имени" : person.name.trim(),
            false);
        name.setTextSize(15);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(name, new LinearLayout.LayoutParams(-1, dp(27)));
        TextView quality = cardActionDetail(personQualitySummary(report), false);
        quality.setTextSize(10);
        quality.setTextColor(qualitySummaryColor(report));
        header.addView(quality, new LinearLayout.LayoutParams(-1, dp(22)));
        return header;
    }

    private View personMenuHeader(Person person) {
        return personMenuHeader(
            person,
            person.pinned ? "Положение закреплено" : "Меню выбранной карточки");
    }

    private View personMenuHeader(Person person, String detailText) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(7), dp(10), dp(7));
        header.setBackground(panelBg(Color.rgb(232, 248, 246), dp(10), Color.argb(72, 24, 169, 153)));
        TextView avatar = new LocalizedTextView(this);
        LocalizedViews.setRaw(avatar, personInitials(person.name));
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(12);
        avatar.setTypeface(uiBold());
        avatar.setGravity(Gravity.CENTER);
        avatar.setIncludeFontPadding(false);
        avatar.setBackground(panelBg(person.color, dp(999), Color.argb(72, 255, 255, 255)));
        header.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, 0, 0);
        TextView name = cardActionTitle(
            person.name == null || person.name.trim().isEmpty() ? "Без имени" : person.name.trim(),
            false);
        name.setTextSize(14);
        copy.addView(name, new LinearLayout.LayoutParams(-1, dp(24)));
        TextView detail = cardActionDetail(detailText, false);
        detail.setTextSize(10);
        copy.addView(detail, new LinearLayout.LayoutParams(-1, dp(20)));
        header.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        return header;
    }

    private TextView personMenuSection(String value) {
        TextView section = new LocalizedTextView(this);
        section.setText(value);
        section.setTextColor(Color.rgb(101, 113, 122));
        section.setTextSize(9);
        section.setTypeface(uiBold());
        section.setGravity(Gravity.LEFT | Gravity.BOTTOM);
        section.setPadding(dp(8), 0, 0, dp(5));
        section.setIncludeFontPadding(false);
        return section;
    }

    private View personMenuGroup(int iconRes, String label, String[] optionLabels, String[] actions, PopupWindow popup) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        TextView summary = personMenuText(iconRes, label, false);
        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setPadding(dp(8), dp(2), dp(8), dp(5));
        options.setBackground(panelBg(Color.rgb(232, 248, 246), dp(8), Color.argb(52, 24, 169, 153)));
        options.setVisibility(View.GONE);
        summary.setOnClickListener(v -> {
            options.setVisibility(options.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            View content = popup.getContentView();
            content.measure(
                View.MeasureSpec.makeMeasureSpec(popup.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            popup.update(popup.getWidth(), content.getMeasuredHeight());
        });
        group.addView(summary, new LinearLayout.LayoutParams(-1, dp(48)));
        for (int i = 0; i < optionLabels.length; i++) {
            final String action = actions[i];
            TextView option = personMenuText(0, optionLabels[i], false);
            option.setPadding(dp(42), 0, dp(10), 0);
            option.setOnClickListener(v -> {
                popup.dismiss();
                addRelationAction(action);
            });
            options.addView(option, new LinearLayout.LayoutParams(-1, dp(42)));
        }
        group.addView(options);
        return group;
    }

    private View personMenuAction(int iconRes, String label, boolean danger, PopupWindow popup, Runnable action) {
        TextView row = personMenuText(iconRes, label, danger);
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(48)));
        return row;
    }

    private View personMenuDetailedAction(
        int iconRes,
        String label,
        String detail,
        boolean danger,
        PopupWindow popup,
        Runnable action
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(5), dp(10), dp(5));
        row.setBackground(panelBg(Color.WHITE, dp(8), Color.rgb(225, 231, 235)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(uiColor(danger ? Color.rgb(197, 83, 75) : Color.rgb(8, 122, 115)));
        row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(9), 0, 0, 0);
        TextView title = cardActionTitle(label, danger);
        title.setTextSize(12);
        copy.addView(title, new LinearLayout.LayoutParams(-1, dp(23)));
        TextView caption = cardActionDetail(detail, danger);
        caption.setTextSize(9);
        caption.setSingleLine(true);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(caption, new LinearLayout.LayoutParams(-1, dp(19)));
        row.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(54)));
        return row;
    }

    private View personMenuGridRow(View first, View second) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(first, new LinearLayout.LayoutParams(0, dp(54), 1));
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(0, dp(54), 1);
        secondParams.setMargins(dp(7), 0, 0, 0);
        row.addView(second, secondParams);
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(61)));
        return row;
    }

    private View personMenuSpaced(View view, int top, int bottom) {
        ViewGroup.LayoutParams current = view.getLayoutParams();
        int height = current == null ? ViewGroup.LayoutParams.WRAP_CONTENT : current.height;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, height);
        params.setMargins(0, top, 0, bottom);
        view.setLayoutParams(params);
        return view;
    }

    private View personMenuGridTile(
        int iconRes,
        String label,
        String detail,
        boolean danger,
        PopupWindow popup,
        Runnable action
    ) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(9), dp(4), dp(7), dp(4));
        tile.setBackground(panelBg(
            danger ? Color.rgb(255, 244, 241) : Color.WHITE,
            dp(9),
            danger ? Color.argb(88, 197, 83, 75) : Color.rgb(225, 231, 235)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(uiColor(danger ? Color.rgb(197, 83, 75) : Color.rgb(8, 122, 115)));
        tile.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(7), 0, 0, 0);
        TextView title = cardActionTitle(label, danger);
        title.setTextSize(12);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(title, new LinearLayout.LayoutParams(-1, detail == null || detail.isEmpty() ? dp(42) : dp(25)));
        if (detail != null && !detail.isEmpty()) {
            TextView caption = cardActionDetail(detail, danger);
            caption.setTextSize(8);
            caption.setSingleLine(true);
            caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
            copy.addView(caption, new LinearLayout.LayoutParams(-1, dp(16)));
        }
        tile.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        tile.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        return tile;
    }

    private View personQualityAction(
        Person person,
        TreeQualityAnalyzer.PersonReport report,
        String category,
        String label,
        int iconRes,
        PopupWindow popup
    ) {
        int count = report.countCategory(category);
        String detail = count == 0
            ? "Замечаний нет"
            : count + " " + countWord(count, "замечание", "замечания", "замечаний");
        return personMenuDetailedAction(
            iconRes,
            label,
            detail,
            false,
            popup,
            () -> showPersonQualityDialog(person, category));
    }

    private TextView personMenuText(int iconRes, String label, boolean danger) {
        TextView row = new LocalizedTextView(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setText(label);
        row.setTextSize(13);
        row.setTypeface(uiBold());
        row.setIncludeFontPadding(false);
        row.setTextColor(danger ? Color.rgb(197, 83, 75) : Color.rgb(28, 34, 38));
        row.setPadding(dp(10), 0, dp(10), 0);
        row.setBackground(panelBg(Color.WHITE, dp(8), Color.rgb(225, 231, 235)));
        if (iconRes != 0) {
            row.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
            row.setCompoundDrawablePadding(dp(9));
            tintDrawables(row, danger ? Color.rgb(197, 83, 75) : Color.rgb(8, 122, 115));
        }
        return row;
    }

    private void showPersonBranch(Person person, String mode) {
        if (person == null || state == null) return;
        state.selectedId = person.id;
        branchMode = mode;
        branchAnchorId = person.id;
        pendingBranchMode = "";
        treeView.setBranchMode(mode, person.id);
        updateBranchStatusPanel();
        showPanel("");
        treeView.post(treeView::fit);
        toast(branchLabel(mode) + ": " + (person.name.isEmpty() ? "Без имени" : person.name));
    }

    private void showKinshipToRoot(Person person) {
        if (person == null || state == null) return;
        Person root = state.people.get(state.rootId);
        if (root == null) {
            toast("Ваша корневая карточка не найдена");
            return;
        }
        KinshipCalculator.Result result = KinshipCalculator.calculate(state, person.id, root.id);
        treeView.highlightKinshipPath(shortestPersonPath(person.id, root.id));
        showKinshipResult(person, root, result);
    }

    private java.util.List<String> shortestPersonPath(String startId, String endId) {
        java.util.List<String> empty = new java.util.ArrayList<>();
        if (state == null || startId == null || endId == null
            || !state.people.containsKey(startId) || !state.people.containsKey(endId)) return empty;
        if (startId.equals(endId)) {
            empty.add(startId);
            return empty;
        }
        java.util.Map<String, java.util.List<String>> graph = new java.util.HashMap<>();
        for (String id : state.people.keySet()) graph.put(id, new java.util.ArrayList<>());
        for (Relation link : state.links) {
            if (!graph.containsKey(link.from) || !graph.containsKey(link.to)) continue;
            graph.get(link.from).add(link.to);
            graph.get(link.to).add(link.from);
        }
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        java.util.Map<String, String> previous = new java.util.HashMap<>();
        queue.add(startId);
        previous.put(startId, "");
        while (!queue.isEmpty() && !previous.containsKey(endId)) {
            String current = queue.removeFirst();
            for (String next : graph.getOrDefault(current, java.util.Collections.emptyList())) {
                if (previous.containsKey(next)) continue;
                previous.put(next, current);
                queue.addLast(next);
            }
        }
        if (!previous.containsKey(endId)) return empty;
        String current = endId;
        while (!current.isEmpty()) {
            empty.add(0, current);
            current = previous.getOrDefault(current, "");
        }
        return empty;
    }

    private void shareBiography(Person person) {
        if (person == null) return;
        String subject = person.name == null ? "" : person.name.trim();
        String biography = biographyText(person);
        String mediaId = person.photoMediaId == null ? "" : person.photoMediaId.trim();
        String dataUrl = person.photo == null ? "" : person.photo.trim();
        if (mediaId.isEmpty() && dataUrl.isEmpty()) {
            startBiographyShare(subject, biography, null, "text/plain");
            return;
        }
        new Thread(() -> {
            try {
                String extension = biographyPhotoExtension(mediaId, dataUrl);
                String mime = "png".equals(extension)
                    ? "image/png"
                    : "webp".equals(extension) ? "image/webp" : "image/jpeg";
                File shared = new File(getCacheDir(), "shared");
                if (!shared.isDirectory() && !shared.mkdirs()) {
                    throw new java.io.IOException("Не удалось подготовить фотографию");
                }
                File photo = new File(shared, "biography-photo-" + System.currentTimeMillis() + "." + extension);
                try (OutputStream output = new FileOutputStream(photo)) {
                    if (!mediaId.isEmpty() && store.mediaStore().exists(mediaId)) {
                        store.mediaStore().copyTo(mediaId, output);
                    } else {
                        int comma = dataUrl.indexOf(',');
                        if (comma < 0 || comma + 1 >= dataUrl.length()) {
                            throw new java.io.IOException("Фотография повреждена");
                        }
                        output.write(Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT));
                    }
                    output.flush();
                }
                Uri photoUri = TreeShareProvider.uriFor(photo.getName());
                runOnUiThread(() -> startBiographyShare(subject, biography, photoUri, mime));
            } catch (Exception error) {
                DiagnosticsLogger.handled(this, "biography.share.photo", error);
                runOnUiThread(() -> {
                    toast("Фото не удалось приложить, отправляю текст");
                    startBiographyShare(subject, biography, null, "text/plain");
                });
            }
        }, "biography-photo-share").start();
    }

    private String biographyText(Person person) {
        StringBuilder biography = new StringBuilder();
        biography.append(person.name == null || person.name.trim().isEmpty() ? tr("Без имени") : person.name.trim());
        String gender = PersonGender.resolve(person);
        if (PersonGender.MALE.equals(gender)) biography.append("\nПол: мужской");
        else if (PersonGender.FEMALE.equals(gender)) biography.append("\nПол: женский");
        int age = personAge(person);
        if (age >= 0) {
            biography.append("\nВозраст: ").append(age).append(" ")
                .append(countWord(age, "год", "года", "лет"));
        }
        String born = humanDate(person.bornDay, person.bornMonth, person.bornYear);
        String died = humanDate(person.diedDay, person.diedMonth, person.diedYear);
        if (!born.isEmpty() && !died.isEmpty()) biography.append("\n").append(born).append(" — ").append(died);
        else if (!born.isEmpty()) biography.append("\nДата рождения: ").append(born);
        else if (!died.isEmpty()) biography.append("\nДата смерти: ").append(died);
        if (person.place != null && !person.place.trim().isEmpty()) biography.append("\n").append(person.place.trim());
        if (person.notes != null && !person.notes.trim().isEmpty()) biography.append("\n\n").append(person.notes.trim());
        if (!person.memories.isEmpty()) {
            biography.append("\n\n").append(tr("Память")).append(":");
            int limit = Math.min(8, person.memories.size());
            for (int index = 0; index < limit; index++) {
                Memory memory = person.memories.get(index);
                String title = memory.title == null || memory.title.trim().isEmpty()
                    ? tr("Воспоминание")
                    : memory.title.trim();
                biography.append("\n• ").append(title);
                if (memory.text != null && !memory.text.trim().isEmpty()) {
                    biography.append(": ").append(memory.text.trim());
                }
            }
        }
        return biography.toString();
    }

    private int personAge(Person person) {
        int bornYear = positiveYear(person == null ? "" : person.bornYear);
        if (bornYear <= 0) return -1;
        Calendar endpoint = Calendar.getInstance();
        int diedYear = positiveYear(person.diedYear);
        boolean deceased = diedYear > 0;
        int endYear = deceased ? diedYear : endpoint.get(Calendar.YEAR);
        int age = endYear - bornYear;
        if (age < 0) return -1;
        int bornMonth = positiveNumber(person.bornMonth, 1, 12);
        int bornDay = positiveNumber(person.bornDay, 1, 31);
        int endMonth = deceased ? positiveNumber(person.diedMonth, 1, 12) : endpoint.get(Calendar.MONTH) + 1;
        int endDay = deceased ? positiveNumber(person.diedDay, 1, 31) : endpoint.get(Calendar.DAY_OF_MONTH);
        if (bornMonth > 0 && endMonth > 0
            && (endMonth < bornMonth || endMonth == bornMonth && bornDay > 0 && endDay > 0 && endDay < bornDay)) {
            age--;
        }
        return Math.max(-1, age);
    }

    private int positiveYear(String value) {
        return positiveNumber(value, 1, 9999);
    }

    private int positiveNumber(String value, int minimum, int maximum) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return parsed >= minimum && parsed <= maximum ? parsed : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String biographyPhotoExtension(String mediaId, String dataUrl) {
        String source = mediaId == null ? "" : mediaId.toLowerCase(Locale.ROOT);
        int dot = source.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < source.length()) {
            String extension = source.substring(dot + 1);
            if ("png".equals(extension) || "webp".equals(extension) || "jpg".equals(extension)) return extension;
            if ("jpeg".equals(extension)) return "jpg";
        }
        String legacy = dataUrl == null ? "" : dataUrl.toLowerCase(Locale.ROOT);
        if (legacy.startsWith("data:image/png")) return "png";
        if (legacy.startsWith("data:image/webp")) return "webp";
        return "jpg";
    }

    private void startBiographyShare(String subject, String biography, Uri photoUri, String mime) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mime);
        send.putExtra(Intent.EXTRA_SUBJECT, subject);
        send.putExtra(Intent.EXTRA_TEXT, biography);
        if (photoUri != null) {
            send.putExtra(Intent.EXTRA_STREAM, photoUri);
            send.setClipData(ClipData.newRawUri("Фото", photoUri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        startActivity(Intent.createChooser(send, tr("Поделиться биографией")));
    }

    private String personQualitySummary(TreeQualityAnalyzer.PersonReport report) {
        if (report == null) return "Заполнено 0%";
        if (report.errors() > 0) {
            String value = "Заполнено " + report.completeness + "% · " + report.errors()
                + " " + countWord(report.errors(), "ошибка", "ошибки", "ошибок");
            if (report.warnings() > 0) value += " · " + report.warnings() + " предупр.";
            return value;
        }
        if (report.warnings() > 0) {
            return "Заполнено " + report.completeness + "% · " + report.warnings()
                + " " + countWord(report.warnings(), "предупреждение", "предупреждения", "предупреждений");
        }
        if (report.recommendations() > 0) {
            return "Заполнено " + report.completeness + "% · " + report.recommendations()
                + " " + countWord(report.recommendations(), "рекомендация", "рекомендации", "рекомендаций");
        }
        return "Заполнено " + report.completeness + "% · всё хорошо";
    }

    private int qualitySummaryColor(TreeQualityAnalyzer.PersonReport report) {
        if (report != null && report.errors() > 0) return Color.rgb(197, 83, 75);
        if (report != null && report.warnings() > 0) return Color.rgb(184, 128, 24);
        if (report != null && report.recommendations() > 0) return Color.rgb(101, 113, 122);
        return Color.rgb(8, 122, 115);
    }

    private String countWord(int count, String one, String few, String many) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        if (mod100 >= 11 && mod100 <= 14) return many;
        if (mod10 == 1) return one;
        if (mod10 >= 2 && mod10 <= 4) return few;
        return many;
    }

    private void updateTreeQualityButton() {
        if (treeQualityButton == null) return;
        int score = qualityReport == null ? 0 : qualityReport.score;
        treeQualityButton.setText("Оценка дерева\n" + score + "%");
        int color = score >= 80
            ? Color.rgb(8, 122, 115)
            : score >= 55 ? Color.rgb(184, 128, 24) : Color.rgb(197, 83, 75);
        treeQualityButton.setTextColor(color);
        tintDrawables(treeQualityButton, color);
        treeQualityButton.setBackground(panelBg(
            score >= 80
                ? Color.rgb(232, 248, 246)
                : score >= 55 ? Color.rgb(255, 248, 226) : Color.rgb(255, 241, 239),
            dp(10),
            Color.argb(105, Color.red(color), Color.green(color), Color.blue(color))));
    }

    void refreshTreeQuality() {
        qualityReport = TreeQualityAnalyzer.analyze(state);
        if (treeView != null) treeView.setQualityReports(qualityReport.people);
        updateTreeQualityButton();
    }

    void showTreeQualityDialog() {
        qualityReport = TreeQualityAnalyzer.analyze(state);
        showQualityDialog(
            "Оценка здоровья дерева",
            state.people.size() + " карточек · " + state.links.size() + " связей",
            qualityReport.score,
            qualityReport.issues,
            "");
    }

    private void showPersonQualityDialog(Person person, String category) {
        if (person == null) return;
        qualityReport = TreeQualityAnalyzer.analyze(state);
        TreeQualityAnalyzer.PersonReport report = qualityReport.person(person.id);
        java.util.List<TreeQualityAnalyzer.Issue> filtered = new java.util.ArrayList<>();
        for (TreeQualityAnalyzer.Issue issue : report.issues) {
            if (category == null || category.isEmpty() || category.equals(issue.category)) filtered.add(issue);
        }
        showQualityDialog(
            "Проверка карточки",
            person.name == null || person.name.trim().isEmpty() ? "Без имени" : person.name.trim(),
            report.completeness,
            filtered,
            person.id);
    }

    private void showQualityDialog(
        String titleValue,
        String subtitleValue,
        int score,
        java.util.List<TreeQualityAnalyzer.Issue> issues,
        String personId
    ) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(16), dp(14), dp(16), dp(16));
        shell.setBackground(panelBg(Color.rgb(250, 252, 253), dp(18), Color.argb(56, 63, 82, 94)));
        scroll.addView(shell, new ScrollView.LayoutParams(-1, -2));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView title = cardActionTitle(titleValue, false);
        title.setTextSize(19);
        heading.addView(title, new LinearLayout.LayoutParams(-1, dp(30)));
        TextView subtitle = cardActionDetail(subtitleValue, false);
        subtitle.setTextSize(11);
        heading.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(22)));
        top.addView(heading, new LinearLayout.LayoutParams(0, dp(52), 1));
        top.addView(closeButton(v -> dialog.dismiss()), new LinearLayout.LayoutParams(dp(42), dp(42)));
        shell.addView(top);

        boolean treeReport = personId == null || personId.isEmpty();
        if (treeReport) shell.addView(qualityGenderSummary(), qualityGenderParams());
        shell.addView(qualityScoreCard(score), qualityBlockParams());
        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        int errors = 0;
        int warnings = 0;
        int recommendations = 0;
        for (TreeQualityAnalyzer.Issue issue : issues) {
            if (issue.severity == TreeQualityAnalyzer.ERROR) errors++;
            else if (issue.severity == TreeQualityAnalyzer.WARNING) warnings++;
            else recommendations++;
        }
        metrics.addView(qualityMetric("Ошибки", errors, Color.rgb(197, 83, 75)), new LinearLayout.LayoutParams(0, dp(58), 1));
        LinearLayout.LayoutParams warningParams = new LinearLayout.LayoutParams(0, dp(58), 1);
        warningParams.setMargins(dp(7), 0, 0, 0);
        metrics.addView(qualityMetric("Предупреждения", warnings, Color.rgb(184, 128, 24)), warningParams);
        LinearLayout.LayoutParams recommendationParams = new LinearLayout.LayoutParams(0, dp(58), 1);
        recommendationParams.setMargins(dp(7), 0, 0, 0);
        metrics.addView(qualityMetric("Советы", recommendations, Color.rgb(101, 113, 122)), recommendationParams);
        shell.addView(metrics, qualityBlockParams());

        TextView section = personMenuSection(issues.isEmpty() ? "РЕЗУЛЬТАТ" : "ЧТО НУЖНО ПРОВЕРИТЬ");
        shell.addView(section, new LinearLayout.LayoutParams(-1, dp(34)));
        if (issues.isEmpty()) {
            TextView empty = cardActionDetail("Замечаний не найдено. Данные выглядят согласованными.", false);
            empty.setTextSize(12);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(12), dp(14), dp(12), dp(14));
            empty.setBackground(panelBg(Color.rgb(232, 248, 246), dp(12), Color.argb(72, 24, 169, 153)));
            shell.addView(empty, new LinearLayout.LayoutParams(-1, dp(72)));
        } else if (treeReport) {
            addGroupedQualityIssues(shell, issues, dialog);
        } else {
            int limit = Math.min(60, issues.size());
            for (int index = 0; index < limit; index++) {
                shell.addView(qualityIssueRow(issues.get(index), dialog, false), qualityIssueParams());
            }
        }

        dialog.setContentView(scroll);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(24), dp(540));
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = Math.min(getResources().getDisplayMetrics().heightPixels - dp(54), dp(760));
            attrs.dimAmount = 0.34f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
        dialog.show();
    }

    private View qualityScoreCard(int score) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int color = score >= 80
            ? Color.rgb(8, 122, 115)
            : score >= 55 ? Color.rgb(184, 128, 24) : Color.rgb(197, 83, 75);
        card.setPadding(dp(14), dp(10), dp(14), dp(10));
        card.setBackground(panelBg(Color.WHITE, dp(12), Color.argb(86, Color.red(color), Color.green(color), Color.blue(color))));
        TextView scoreView = new LocalizedTextView(this);
        LocalizedViews.setRaw(scoreView, score + "%");
        scoreView.setTextColor(color);
        scoreView.setTextSize(26);
        scoreView.setTypeface(uiBold());
        scoreView.setGravity(Gravity.CENTER);
        card.addView(scoreView, new LinearLayout.LayoutParams(dp(82), -1));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = cardActionTitle(
            score >= 80 ? "Хорошее состояние" : score >= 55 ? "Нужно проверить" : "Требует внимания",
            false);
        copy.addView(title, new LinearLayout.LayoutParams(-1, dp(25)));
        TextView detail = cardActionDetail("Оценка учитывает заполненность, даты и семейные связи.", false);
        detail.setTextSize(9);
        detail.setMaxLines(2);
        copy.addView(detail, new LinearLayout.LayoutParams(-1, dp(34)));
        card.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        return card;
    }

    private View qualityMetric(String label, int value, int color) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setGravity(Gravity.CENTER);
        metric.setBackground(panelBg(Color.WHITE, dp(10), Color.argb(64, Color.red(color), Color.green(color), Color.blue(color))));
        TextView number = new LocalizedTextView(this);
        LocalizedViews.setRaw(number, String.valueOf(value));
        number.setTextColor(color);
        number.setTextSize(18);
        number.setTypeface(uiBold());
        number.setGravity(Gravity.CENTER);
        metric.addView(number, new LinearLayout.LayoutParams(-1, dp(30)));
        TextView caption = cardActionDetail(label, false);
        caption.setTextSize(8);
        caption.setGravity(Gravity.CENTER);
        metric.addView(caption, new LinearLayout.LayoutParams(-1, dp(20)));
        return metric;
    }

    private View qualityGenderSummary() {
        int male = 0;
        int female = 0;
        int unknown = 0;
        if (state != null) {
            for (Person person : state.people.values()) {
                String gender = PersonGender.resolve(person);
                if (PersonGender.MALE.equals(gender)) male++;
                else if (PersonGender.FEMALE.equals(gender)) female++;
                else unknown++;
            }
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(7), dp(8), dp(7));
        row.setBackground(panelBg(Color.WHITE, dp(12), Color.rgb(217, 224, 229)));
        row.addView(qualityGenderMetric("М", "Мужчины", male, Color.rgb(47, 125, 185)), new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams femaleParams = new LinearLayout.LayoutParams(0, -1, 1);
        femaleParams.setMargins(dp(7), 0, 0, 0);
        row.addView(qualityGenderMetric("Ж", "Женщины", female, Color.rgb(185, 83, 130)), femaleParams);
        if (unknown > 0) {
            LinearLayout.LayoutParams unknownParams = new LinearLayout.LayoutParams(0, -1, 1);
            unknownParams.setMargins(dp(7), 0, 0, 0);
            row.addView(qualityGenderMetric("?", "Не указан", unknown, Color.rgb(101, 113, 122)), unknownParams);
        }
        return row;
    }

    private View qualityGenderMetric(String mark, String label, int value, int color) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.HORIZONTAL);
        metric.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = new LocalizedTextView(this);
        LocalizedViews.setRaw(badge, mark);
        badge.setTextSize(12);
        badge.setTypeface(uiBold());
        badge.setTextColor(Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(panelBg(color, dp(999), Color.TRANSPARENT));
        metric.addView(badge, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(7), 0, 0, 0);
        TextView count = new LocalizedTextView(this);
        LocalizedViews.setRaw(count, String.valueOf(value));
        count.setTextSize(14);
        count.setTypeface(uiBold());
        count.setTextColor(color);
        count.setIncludeFontPadding(false);
        copy.addView(count, new LinearLayout.LayoutParams(-1, dp(21)));
        TextView caption = cardActionDetail(label, false);
        caption.setTextSize(8);
        copy.addView(caption, new LinearLayout.LayoutParams(-1, dp(17)));
        metric.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        return metric;
    }

    private void addGroupedQualityIssues(
        LinearLayout shell,
        java.util.List<TreeQualityAnalyzer.Issue> issues,
        Dialog dialog
    ) {
        java.util.Map<String, java.util.List<TreeQualityAnalyzer.Issue>> groups = new java.util.LinkedHashMap<>();
        for (TreeQualityAnalyzer.Issue issue : issues) {
            groups.computeIfAbsent(issue.title, key -> new java.util.ArrayList<>()).add(issue);
        }
        for (java.util.Map.Entry<String, java.util.List<TreeQualityAnalyzer.Issue>> group : groups.entrySet()) {
            java.util.Set<String> cards = new java.util.LinkedHashSet<>();
            int severity = 0;
            for (TreeQualityAnalyzer.Issue issue : group.getValue()) {
                cards.add(issue.personId);
                severity = Math.max(severity, issue.severity);
            }
            shell.addView(
                qualityProblemAccordion(group.getKey(), cards.size(), severity, group.getValue(), dialog),
                qualityProblemHeaderParams());
        }
    }

    private View qualityProblemAccordion(
        String problem,
        int cardCount,
        int severity,
        java.util.List<TreeQualityAnalyzer.Issue> issues,
        Dialog dialog
    ) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), 0, dp(10), 0);
        String countLabel = AppLanguage.isEnglish(this)
            ? cardCount + (cardCount == 1 ? " card" : " cards")
            : cardCount + " " + countWord(cardCount, "карточка", "карточки", "карточек");
        int color = severity == TreeQualityAnalyzer.ERROR
            ? Color.rgb(197, 83, 75)
            : severity == TreeQualityAnalyzer.WARNING
                ? Color.rgb(184, 128, 24)
                : Color.rgb(101, 113, 122);
        header.setBackground(panelBg(
            severity == TreeQualityAnalyzer.ERROR
                ? Color.rgb(255, 244, 241)
                : severity == TreeQualityAnalyzer.WARNING
                    ? Color.rgb(255, 248, 226)
                    : Color.rgb(243, 246, 248),
            dp(9),
            Color.argb(76, Color.red(color), Color.green(color), Color.blue(color))));

        TextView arrow = new LocalizedTextView(this);
        LocalizedViews.setRaw(arrow, "›");
        arrow.setTextColor(color);
        arrow.setTextSize(22);
        arrow.setTypeface(uiBold());
        arrow.setGravity(Gravity.CENTER);
        arrow.setIncludeFontPadding(false);
        header.addView(arrow, new LinearLayout.LayoutParams(dp(28), -1));

        TextView title = new LocalizedTextView(this);
        LocalizedViews.setRaw(title, AppLanguage.translate(this, problem));
        title.setTextColor(color);
        title.setTextSize(12);
        title.setTypeface(uiBold());
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        TextView count = new LocalizedTextView(this);
        LocalizedViews.setRaw(count, countLabel);
        count.setTextColor(color);
        count.setTextSize(9);
        count.setTypeface(uiBold());
        count.setGravity(Gravity.CENTER);
        count.setPadding(dp(9), 0, dp(9), 0);
        count.setBackground(panelBg(Color.WHITE, dp(999), Color.argb(72, Color.red(color), Color.green(color), Color.blue(color))));
        header.addView(count, new LinearLayout.LayoutParams(-2, dp(30)));
        group.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(8), dp(8), 0, 0);
        content.setVisibility(View.GONE);
        group.addView(content, new LinearLayout.LayoutParams(-1, -2));
        boolean[] populated = {false};
        header.setOnClickListener(v -> {
            boolean open = content.getVisibility() == View.VISIBLE;
            if (!open && !populated[0]) {
                for (TreeQualityAnalyzer.Issue issue : issues) {
                    content.addView(qualityIssueRow(issue, dialog, true), qualityIssueParams());
                }
                populated[0] = true;
            }
            content.setVisibility(open ? View.GONE : View.VISIBLE);
            LocalizedViews.setRaw(arrow, open ? "›" : "⌄");
        });
        return group;
    }

    private View qualityIssueRow(TreeQualityAnalyzer.Issue issue, Dialog dialog, boolean showPersonName) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int color = issue.severity == TreeQualityAnalyzer.ERROR
            ? Color.rgb(197, 83, 75)
            : issue.severity == TreeQualityAnalyzer.WARNING
                ? Color.rgb(184, 128, 24)
                : Color.rgb(101, 113, 122);
        row.setPadding(dp(10), dp(7), dp(8), dp(7));
        row.setBackground(panelBg(Color.WHITE, dp(10), Color.argb(70, Color.red(color), Color.green(color), Color.blue(color))));
        TextView marker = new LocalizedTextView(this);
        marker.setText(issue.severity == TreeQualityAnalyzer.RECOMMENDATION ? "i" : "!");
        marker.setTextColor(Color.WHITE);
        marker.setTextSize(13);
        marker.setTypeface(uiBold());
        marker.setGravity(Gravity.CENTER);
        marker.setBackground(panelBg(color, dp(999), Color.TRANSPARENT));
        row.addView(marker, new LinearLayout.LayoutParams(dp(28), dp(28)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(9), 0, dp(7), 0);
        Person issuePerson = state == null ? null : state.people.get(issue.personId);
        String titleValue = showPersonName
            ? issuePerson == null || issuePerson.name == null || issuePerson.name.trim().isEmpty()
                ? "Без имени"
                : issuePerson.name.trim()
            : issue.title;
        TextView title = cardActionTitle(titleValue, false);
        title.setTextSize(11);
        copy.addView(title, new LinearLayout.LayoutParams(-1, dp(23)));
        TextView detail = cardActionDetail(issue.detail, false);
        detail.setTextSize(8);
        detail.setMaxLines(2);
        copy.addView(detail, new LinearLayout.LayoutParams(-1, dp(34)));
        row.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        Button fix = actionButton("Исправить", v -> {
            dialog.dismiss();
            quickFixQualityIssue(issue);
        });
        fix.setTextSize(9);
        fix.setTextColor(color);
        fix.setBackground(panelBg(Color.WHITE, dp(8), Color.argb(90, Color.red(color), Color.green(color), Color.blue(color))));
        row.addView(fix, new LinearLayout.LayoutParams(dp(78), dp(38)));
        return row;
    }

    private void quickFixQualityIssue(TreeQualityAnalyzer.Issue issue) {
        if (issue == null || state == null || !state.people.containsKey(issue.personId)) return;
        state.selectedId = issue.personId;
        bindState();
        treeView.focusPerson(issue.personId);
        if (TreeQualityAnalyzer.CATEGORY_RELATIONS.equals(issue.category)) {
            showPanel("links");
            toast("Выберите инструмент для исправления связи");
        } else {
            openPersonEditor();
        }
    }

    private LinearLayout.LayoutParams qualityBlockParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(80));
        params.setMargins(0, dp(12), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams qualityGenderParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(62));
        params.setMargins(0, dp(10), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams qualityIssueParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(76));
        params.setMargins(0, 0, 0, dp(8));
        return params;
    }

    private LinearLayout.LayoutParams qualityProblemHeaderParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(7), 0, dp(7));
        return params;
    }

    private void togglePersonPin(Person person) {
        if (person == null || editingBlocked()) return;
        recordUndo(person.pinned ? "Откреплена карточка: " + person.name : "Закреплена карточка: " + person.name);
        person.pinned = !person.pinned;
        saveToast(person.pinned ? "Карточка закреплена" : "Карточка откреплена");
        bindState();
    }

    void addRelationAction(String action) {
        if (editingBlocked()) return;
        Person current = state.selectedPerson();
        if (current == null) return;
        recordUndo();
        java.util.List<String> newIds = new java.util.ArrayList<>();

        if (action.startsWith("add-parents-")) {
            int count = Math.min(2, countFromAction(action, 1));
            java.util.List<String> existingParents = parentIdsOf(current.id);
            float[] offsets = distribute(count, 340f);
            for (int i = 0; i < count; i++) {
                float[] spot = positionNear(current, offsets[i], -TreeLayoutEngine.LEVEL_GAP);
                Person created = state.addPerson(
                    tr("Новый родитель") + (count > 1 ? " " + (i + 1) : ""),
                    spot[0],
                    spot[1]);
                state.addRelation("parent", created.id, current.id);
                newIds.add(created.id);
            }
            if (newIds.size() >= 2) {
                connectEveryPair(newIds, "partner");
            } else if (newIds.size() == 1 && !existingParents.isEmpty()) {
                state.addRelation("partner", existingParents.get(0), newIds.get(0), "right");
            }
        } else if (action.startsWith("add-children-")) {
            int count = Math.min(4, countFromAction(action, 1));
            String partner = firstPartnerOf(current.id);
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
        } else if ("add-partner".equals(action)) {
            float[] spot = positionNear(current, -320f, 0f);
            Person created = state.addPerson(tr("Новый партнёр"), spot[0], spot[1]);
            state.addRelation("partner", current.id, created.id, "left");
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
        saveToast(newIds.size() > 1 ? "Карточки добавлены: " + newIds.size() : "Родственник добавлен");
        bindState();
        treeView.invalidate();
        trainingTargetActivated("add-person");
    }

    private int countFromAction(String action, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(action.substring(action.lastIndexOf('-') + 1)));
        } catch (Exception ignored) {
            return fallback;
        }
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
        if (editingBlocked()) return;
        Person current = state.selectedPerson();
        if (current == null) return;
        recordUndo();
        Person created;
        if ("parent".equals(kind)) {
            float[] spot = positionNear(current, -160f, -TreeLayoutEngine.LEVEL_GAP);
            created = state.addPerson(tr("Новый родитель"), spot[0], spot[1]);
            state.addRelation("parent", created.id, current.id);
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
        saveToast("Родственник добавлен");
        bindState();
    }

    private void duplicateSelected() {
        if (editingBlocked()) return;
        Person current = state.selectedPerson();
        if (current == null) return;
        recordUndo("Дублирована карточка: " + (current.name.isEmpty() ? "Без имени" : current.name));
        float[] spot = findOpenSpot(current.x + 320f, current.y + 40f);
        Person copy = state.addPerson(
            (current.name.isEmpty() ? tr("Без имени") : current.name)
                + tr(" (копия)"),
            spot[0],
            spot[1]);
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
        saveToast("Карточка дублирована");
        bindState();
    }

    void confirmDelete() {
        if (editingBlocked()) return;
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
            clearSelection();
            confirmDeleteSequence(selectedInTreeOrder, 0, 0);
            return;
        }
        Person person = state.selectedPerson();
        if (person == null) return;
        showStyledConfirmation(
            R.drawable.ic_menu_trash,
            "Удалить карточку?",
            person.name.isEmpty() ? "Без имени" : person.name,
            "Удалить",
            true,
            () -> {
                String name = person.name.isEmpty() ? "Без имени" : person.name;
                recordUndo(state.people.size() <= 1 ? "Очищена последняя карточка" : "Удалён: " + name);
                boolean clearedLast = state.people.size() <= 1;
                if (state.people.size() <= 1) clearLastPerson(person);
                else state.deletePerson(person.id);
                saveToast(clearedLast ? "Последняя карточка очищена" : "Карточка удалена");
                bindState();
                treeView.invalidate();
            });
    }

    private void confirmDeleteSequence(java.util.List<String> personIds, int index, int deleted) {
        int nextIndex = index;
        while (nextIndex < personIds.size() && !state.people.containsKey(personIds.get(nextIndex))) {
            nextIndex++;
        }
        if (nextIndex >= personIds.size()) {
            historyModule.commitPendingUndo();
            bindState();
            treeView.invalidate();
            saveToast("Удалено карточек: " + deleted);
            return;
        }

        final int currentIndex = nextIndex;
        Person person = state.people.get(personIds.get(currentIndex));
        if (person == null) {
            confirmDeleteSequence(personIds, currentIndex + 1, deleted);
            return;
        }
        state.selectedId = person.id;
        bindState();
        String name = person.name == null || person.name.trim().isEmpty() ? "Без имени" : person.name.trim();
        showStyledConfirmation(
            R.drawable.ic_menu_trash,
            "Удалить карточку " + (currentIndex + 1) + " из " + personIds.size() + "?",
            name,
            "Удалить",
            true,
            () -> {
                recordUndo(state.people.size() <= 1 ? "Очищена последняя карточка" : "Удалён: " + name);
                if (state.people.size() <= 1) clearLastPerson(person);
                else state.deletePerson(person.id);
                saveOnly();
                bindState();
                treeView.invalidate();
                treeView.post(() -> confirmDeleteSequence(personIds, currentIndex + 1, deleted + 1));
            });
    }

    private void confirmDeleteWholeTree() {
        if (editingBlocked()) {
            toast("Сначала выключите защиту правок");
            return;
        }
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
        if (personId == null || !state.people.containsKey(personId)) return;
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
            Math.min(workspaceWidth - TreeLayoutEngine.CARD_W, Math.max(0f, TreeLayoutEngine.snap(x))),
            Math.min(workspaceHeight - TreeLayoutEngine.CARD_H, Math.max(0f, TreeLayoutEngine.snap(y)))
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
        for (Relation link : state.links) {
            if (!"partner".equals(link.type) && !"family".equals(link.type)) continue;
            if (personId.equals(link.from) && state.people.containsKey(link.to)) return link.to;
            if (personId.equals(link.to) && state.people.containsKey(link.from)) return link.from;
        }
        return "";
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
        if (editingBlocked()) return;
        resetTransientCanvasModes(false);
        lastSelectionMode = "lasso".equals(mode) ? "lasso" : "rect";
        treeView.setSelectionMode(mode);
        toast("Выделение: " + ("lasso".equals(lastSelectionMode) ? "лассо" : "рамка"));
        updateSelectionToolbar();
        showPanel("");
    }

    private void startGuideMode(String mode) {
        if (editingBlocked()) return;
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
        if (state == null || state.guides.isEmpty() || editingBlocked()) {
            toast("Направляющих нет");
            return;
        }
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
        syncSettingsToState();
        if (store != null && state != null) store.save(TreeStateCopier.copy(state));
        AppThemePalette.setDark("dark".equals(theme));
        recreate();
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

    private void applySystemBars() {
        boolean dark = AppThemePalette.isDark();
        int headerColor = AppThemePalette.surface(Color.rgb(248, 251, 252));
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
    }

    private void startTraining() {
        settingsModule.startTraining();
    }

    private void offerTrainingIfNeeded() {
        if (state == null || state.onboardingCompleted || state.onboardingOffered) return;
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

    private void showTrainingStep(int index) {
        settingsModule.showTrainingStep(index);
    }

    private void prepareTrainingStep(String id) {
        settingsModule.prepareTrainingStep(id);
    }

    void trainingTargetActivated(String id) {
        if (settingsModule != null) settingsModule.onTrainingTargetActivated(id);
    }

    View trainingTarget(String id) {
        if ("add-person".equals(id)) return addPersonButton;
        if ("card-menu".equals(id)) return cardNav;
        if ("links-menu".equals(id)) return linksNav;
        if ("parent-link".equals(id)) return trainingParentLinkTarget;
        if ("tree-menu".equals(id)) return treeNav;
        if ("layout-tree".equals(id)) return trainingLayoutTarget;
        if ("files-menu".equals(id)) return filesNav;
        if ("settings-menu".equals(id)) return settingsNav;
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
        clearSearchFocus();
        panelsModule.showPanel(panel);
    }

    void clearSearchFocus() {
        if (search != null) search.clearFocus();
        if (stage != null) stage.requestFocus();
        hideKeyboard();
    }

    private void updateSearchSuggestions(String query) {
        if (searchSuggestions == null || searchSuggestionsScroll == null || search == null) return;
        String value = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        search.setCompoundDrawablesWithIntrinsicBounds(
            R.drawable.ic_menu_search,
            0,
            value.isEmpty() ? 0 : R.drawable.ic_menu_close,
            0);
        tintDrawables(search, Color.rgb(8, 122, 115));
        searchSuggestions.removeAllViews();
        if (value.isEmpty() || state == null) {
            searchSuggestionsScroll.setVisibility(View.GONE);
            return;
        }
        int count = 0;
        for (Person person : state.people.values()) {
            String haystack = (person.name + " " + person.bornYear + " " + person.diedYear + " " + person.place)
                .toLowerCase(Locale.ROOT);
            if (!haystack.contains(value)) continue;
            TextView pill = new LocalizedTextView(this);
            String year = person.bornYear == null || person.bornYear.isEmpty() ? "" : " · " + person.bornYear;
            LocalizedViews.setRaw(
                pill,
                (person.name == null || person.name.trim().isEmpty()
                    ? tr("Без имени")
                    : person.name.trim()) + year);
            pill.setTextColor(Color.rgb(8, 122, 115));
            pill.setTextSize(11);
            pill.setTypeface(uiBold());
            pill.setSingleLine(true);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(dp(13), 0, dp(13), 0);
            pill.setBackground(panelBg(Color.rgb(232, 248, 246), dp(999), Color.argb(72, 24, 169, 153)));
            pill.setOnClickListener(v -> selectSearchSuggestion(person));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(34));
            params.setMargins(0, 0, dp(7), 0);
            searchSuggestions.addView(pill, params);
            if (++count >= 6) break;
        }
        if (count == 0) {
            TextView empty = new LocalizedTextView(this);
            empty.setText("Совпадений нет — попробуйте фамилию, год или место");
            empty.setTextColor(Color.rgb(101, 113, 122));
            empty.setTextSize(11);
            empty.setTypeface(ui());
            empty.setGravity(Gravity.CENTER_VERTICAL);
            empty.setPadding(dp(4), 0, dp(12), 0);
            searchSuggestions.addView(empty, new LinearLayout.LayoutParams(-2, dp(34)));
        }
        searchSuggestionsScroll.setVisibility(View.VISIBLE);
    }

    private void selectSearchSuggestion(Person person) {
        if (person == null || state == null) return;
        state.selectedId = person.id;
        search.setText("");
        clearSearchFocus();
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
        if (selectionStopButton != null) {
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
            selectionAppendButton.setText("");
            selectionAppendButton.setCompoundDrawables(null, null, null, null);
            selectionAppendButton.setForeground(centeredIcon(
                selectionAppendMode ? R.drawable.ic_menu_check : R.drawable.ic_menu_plus,
                teal));
            selectionAppendButton.setForegroundGravity(Gravity.CENTER);
            selectionAppendButton.setCompoundDrawablePadding(0);
            selectionAppendButton.setGravity(Gravity.CENTER);
            selectionAppendButton.setPadding(0, 0, 0, 0);
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
        boolean lockActive = editingBlocked();
        boolean visible = (guideActive || linkActive || lockActive) && activePanel.isEmpty();
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
                canvasModeDetail.setText("kinship".equals(pendingLinkType)
                    ? "Шаг 1/2: выберите первого человека"
                    : "Шаг 1/2: выберите первую карточку");
            } else {
                Person first = state == null ? null : state.people.get(pendingLinkFrom);
                String name = first == null || first.name.isEmpty() ? "первая карточка" : first.name;
                canvasModeDetail.setText(name + ("kinship".equals(pendingLinkType)
                    ? " → выберите второго человека"
                    : " → выберите вторую карточку"));
            }
            return;
        }
        canvasModeAction.setText("Разблок.");
        styleCanvasModeAction(false);
        canvasModeTitle.setText(viewMode ? "Режим просмотра" : "Правки заблокированы");
        canvasModeDetail.setText("Изменения дерева отключены");
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
        String firstName = first == null || first.name == null || first.name.trim().isEmpty()
            ? "Первый человек"
            : first.name.trim();
        String secondName = second == null || second.name == null || second.name.trim().isEmpty()
            ? "Второй человек"
            : second.name.trim();
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(16), dp(18), dp(16));
        shell.setBackground(panelBg(Color.WHITE, dp(18), Color.argb(56, 63, 82, 94)));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_menu_route);
        icon.setColorFilter(uiColor(Color.rgb(8, 122, 115)));
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(panelBg(Color.rgb(232, 248, 246), dp(999), Color.argb(62, 24, 169, 153)));
        top.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, 0, 0);
        TextView eyebrow = new LocalizedTextView(this);
        eyebrow.setText("СЕМЕЙНАЯ СВЯЗЬ");
        eyebrow.setTextColor(Color.rgb(8, 122, 115));
        eyebrow.setTextSize(10);
        eyebrow.setTypeface(uiBold());
        eyebrow.setIncludeFontPadding(false);
        TextView title = new LocalizedTextView(this);
        title.setText(result != null && result.found ? "Кто кому приходится" : "Родство не найдено");
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setTextSize(19);
        title.setTypeface(uiBold());
        title.setIncludeFontPadding(false);
        heading.addView(eyebrow, new LinearLayout.LayoutParams(-1, dp(18)));
        heading.addView(title, new LinearLayout.LayoutParams(-1, dp(28)));
        top.addView(heading, new LinearLayout.LayoutParams(0, dp(48), 1));
        top.addView(closeButton(v -> dialog.dismiss()), new LinearLayout.LayoutParams(dp(42), dp(42)));
        shell.addView(top);

        View divider = new View(this);
        divider.setBackgroundColor(AppThemePalette.stroke(Color.argb(44, 63, 82, 94)));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(0, dp(14), 0, dp(12));
        shell.addView(divider, dividerParams);

        if (result != null && result.found) {
            shell.addView(kinshipResultCard(firstName, secondName, result.firstToSecond), kinshipCardParams());
            TextView arrow = new LocalizedTextView(this);
            arrow.setText("⇅");
            arrow.setTextColor(Color.rgb(24, 169, 153));
            arrow.setTextSize(20);
            arrow.setTypeface(uiBold());
            arrow.setGravity(Gravity.CENTER);
            shell.addView(arrow, new LinearLayout.LayoutParams(-1, dp(30)));
            shell.addView(kinshipResultCard(secondName, firstName, result.secondToFirst), kinshipCardParams());
        } else {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(18), dp(16), dp(18));
            empty.setBackground(panelBg(Color.rgb(248, 251, 252), dp(12), Color.rgb(217, 224, 229)));
            TextView pair = new LocalizedTextView(this);
            LocalizedViews.setRaw(pair, firstName + "  ↔  " + secondName);
            pair.setTextColor(Color.rgb(28, 34, 38));
            pair.setTextSize(14);
            pair.setTypeface(uiBold());
            pair.setGravity(Gravity.CENTER);
            empty.addView(pair, new LinearLayout.LayoutParams(-1, dp(30)));
            TextView explanation = new LocalizedTextView(this);
            explanation.setText("Проверьте родительские, братские и партнёрские связи между карточками.");
            explanation.setTextColor(Color.rgb(101, 113, 122));
            explanation.setTextSize(12);
            explanation.setTypeface(ui());
            explanation.setGravity(Gravity.CENTER);
            empty.addView(explanation, new LinearLayout.LayoutParams(-1, -2));
            shell.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        }

        if (result != null && result.detail != null && !result.detail.isEmpty()) {
            TextView path = new LocalizedTextView(this);
            path.setText(result.detail);
            path.setTextColor(Color.rgb(76, 87, 96));
            path.setTextSize(11);
            path.setTypeface(ui());
            path.setLineSpacing(dp(2), 1f);
            path.setPadding(dp(12), dp(10), dp(12), dp(10));
            path.setBackground(panelBg(Color.rgb(243, 248, 249), dp(10), Color.argb(52, 24, 169, 153)));
            LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(-1, -2);
            pathParams.setMargins(0, dp(12), 0, 0);
            shell.addView(path, pathParams);
        }

        Button done = actionButton("Понятно", v -> dialog.dismiss());
        done.setTextColor(Color.WHITE);
        done.setBackground(tealGradientBg(dp(10)));
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(-1, dp(46));
        doneParams.setMargins(0, dp(14), 0, 0);
        shell.addView(done, doneParams);

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(500));
            shell.setMinimumWidth(width);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.dimAmount = 0.3f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
        dialog.show();
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
        if (onlineManager != null) onlineManager.markLocalTreeChanged();
        if (saveCoordinator != null) saveCoordinator.requestDebounced();
        updateStats();
        return true;
    }

    void saveToast() {
        saveToast("Дерево сохранено");
    }

    void saveToast(String message) {
        syncSettingsToState();
        if (onlineManager != null) onlineManager.markLocalTreeChanged();
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
        if (badge != null) badge.setText(VERSION_BADGE);
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

    void showHelp(String title, String text) {
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
        heading.setIncludeFontPadding(false);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, dp(40), 1);
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
        body.setPadding(dp(2), 0, dp(2), 0);
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
        showPhotoPreviewBitmap(bitmapFromDataUrl(dataUrl), "", dataUrl);
    }

    void showMediaPhotoPreview(String mediaId) {
        showPhotoPreviewBitmap(store.mediaStore().decodeBitmap(mediaId, 1800), mediaId, "");
    }

    private void showPhotoPreviewBitmap(Bitmap bitmap, String mediaId, String dataUrl) {
        if (bitmap == null) {
            showHelp("Фото", "Фото не удалось открыть.");
            return;
        }
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(12), dp(12), dp(12), dp(12));
        shell.setBackground(panelBg(Color.WHITE, dp(18), Color.argb(56, 63, 82, 94)));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_menu_image);
        mark.setColorFilter(uiColor(Color.rgb(8, 122, 115)));
        mark.setPadding(dp(9), dp(9), dp(9), dp(9));
        mark.setBackground(panelBg(Color.rgb(232, 248, 246), dp(999), Color.TRANSPARENT));
        top.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView title = new LocalizedTextView(this);
        title.setText("Фотография");
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setTextSize(18);
        title.setTypeface(uiBold());
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        titleParams.setMargins(dp(10), 0, dp(8), 0);
        top.addView(title, titleParams);
        top.addView(closeButton(v -> dialog.dismiss()), new LinearLayout.LayoutParams(dp(40), dp(40)));
        shell.addView(top);

        FrameLayout imagePlate = new FrameLayout(this);
        imagePlate.setPadding(dp(4), dp(4), dp(4), dp(4));
        imagePlate.setBackground(panelBg(Color.rgb(239, 244, 246), dp(14), Color.rgb(213, 223, 228)));
        ImageView image = new ImageView(this);
        image.setImageBitmap(bitmap);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imagePlate.addView(image, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, 0, 1);
        imageParams.setMargins(0, dp(12), 0, 0);
        shell.addView(imagePlate, imageParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button download = actionButton("Скачать фото", v -> requestPhotoDownload(mediaId, dataUrl));
        download.setTextColor(Color.WHITE);
        download.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_download, 0, 0, 0);
        download.setCompoundDrawablePadding(dp(8));
        tintDrawables(download, Color.WHITE);
        download.setBackground(tealGradientBg(dp(10)));
        actions.addView(download, new LinearLayout.LayoutParams(0, dp(48), 1.35f));

        Button close = actionButton("Закрыть", v -> dialog.dismiss());
        LinearLayout.LayoutParams closeButtonParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        closeButtonParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(close, closeButtonParams);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(48));
        actionsParams.setMargins(0, dp(12), 0, 0);
        shell.addView(actions, actionsParams);

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(24), dp(620));
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = Math.min(
                Math.round(getResources().getDisplayMetrics().heightPixels * 0.84f),
                dp(820));
            attrs.dimAmount = 0.42f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
    }

    private void requestPhotoDownload(String mediaId, String dataUrl) {
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
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(7), 0, dp(7), 0);
        button.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        button.setCompoundDrawablePadding(dp(4));
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
        button.setTextSize(10);
        button.setTypeface(uiBold());
        button.setGravity(Gravity.CENTER);
        button.setCompoundDrawablesWithIntrinsicBounds(0, iconRes, 0, 0);
        button.setCompoundDrawablePadding(dp(4));
        button.setPadding(dp(3), dp(5), dp(3), dp(5));
        button.setMinHeight(dp(58));
        button.setElevation(0f);
        return button;
    }

    void styleNav(Button button, boolean active) {
        if (button == null) return;
        int color = AppThemePalette.text(active ? Color.rgb(8, 122, 115) : Color.rgb(28, 34, 38));
        button.setTextColor(color);
        Drawable top = button.getCompoundDrawables()[1];
        if (top != null) top.mutate().setTint(color);
        button.setBackground(active
            ? panelBg(Color.rgb(232, 248, 246), dp(8), Color.argb(82, 24, 169, 153))
            : panelBg(Color.TRANSPARENT, dp(8), Color.TRANSPARENT));
    }

    private View menuRow(int iconRes, String label, String help, View.OnClickListener listener) {
        return menuRow(iconRes, label, help, listener, false);
    }

    private View menuRow(int iconRes, String label, String help, View.OnClickListener listener, boolean danger) {
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
        tintDrawables(main, danger ? Color.rgb(197, 83, 75) : Color.rgb(8, 122, 115));
        main.setOnClickListener(listener);
        row.addView(main, new LinearLayout.LayoutParams(0, -1, 1));

        TextView helpButton = new LocalizedTextView(this);
        helpButton.setGravity(Gravity.CENTER);
        helpButton.setText("?");
        helpButton.setTextColor(danger ? Color.rgb(197, 83, 75) : Color.rgb(8, 122, 115));
        helpButton.setTextSize(17);
        helpButton.setTypeface(uiBold());
        helpButton.setBackground(danger
            ? panelBg(Color.rgb(255, 247, 244), dp(8), Color.argb(72, 197, 83, 75))
            : panelBg(Color.rgb(232, 248, 246), dp(8), Color.argb(72, 24, 169, 153)));
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
        row.addView(themeTile(R.drawable.ic_menu_sun, "Светлая", "Включает светлую тему.", "light".equals(theme), v -> setTheme("light")), new LinearLayout.LayoutParams(0, -1, 1));
        row.addView(themeTile(R.drawable.ic_menu_moon, "Тёмная", "Включает тёмную тему.", "dark".equals(theme), v -> setTheme("dark")), spacedTileParams());
        row.addView(themeTile(R.drawable.ic_menu_sparkles, "Чистая", "Белый фон без сетки и лишних элементов.", "clean".equals(theme), v -> setTheme("clean")), spacedTileParams());
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

    private View themeTile(int iconRes, String label, String helpText, boolean active, View.OnClickListener listener) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(4), dp(5), dp(4), dp(5));
        tile.setBackground(active
            ? panelBg(Color.rgb(232, 248, 246), dp(8), Color.argb(82, 24, 169, 153))
            : panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        tile.setOnClickListener(listener);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        if (icon.getDrawable() != null) icon.getDrawable().mutate().setTint(
            AppThemePalette.text(active ? Color.rgb(8, 122, 115) : Color.rgb(28, 34, 38)));
        tile.addView(icon, new LinearLayout.LayoutParams(dp(21), dp(21)));

        TextView text = new LocalizedTextView(this);
        text.setGravity(Gravity.CENTER);
        text.setText(label);
        text.setTextSize(11);
        text.setTypeface(uiBold());
        text.setIncludeFontPadding(false);
        text.setTextColor(active ? Color.rgb(8, 122, 115) : Color.rgb(28, 34, 38));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-1, dp(16));
        textParams.setMargins(0, dp(1), 0, dp(1));
        tile.addView(text, textParams);

        TextView help = new LocalizedTextView(this);
        help.setGravity(Gravity.CENTER);
        help.setText("?");
        help.setTextSize(14);
        help.setTypeface(uiBold());
        help.setIncludeFontPadding(false);
        help.setTextColor(Color.rgb(8, 122, 115));
        help.setBackground(panelBg(Color.rgb(232, 248, 246), dp(8), Color.argb(72, 24, 169, 153)));
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
        tintDrawables(main, Color.rgb(8, 122, 115));
        mainBox.addView(main, new LinearLayout.LayoutParams(0, -1, 1));
        row.addView(mainBox, new LinearLayout.LayoutParams(0, -1, 1));

        TextView helpButton = new LocalizedTextView(this);
        helpButton.setGravity(Gravity.CENTER);
        helpButton.setText("?");
        helpButton.setTextColor(Color.rgb(8, 122, 115));
        helpButton.setTextSize(14);
        helpButton.setTypeface(uiBold());
        helpButton.setIncludeFontPadding(false);
        helpButton.setBackground(panelBg(Color.rgb(232, 248, 246), dp(8), Color.argb(72, 24, 169, 153)));
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
        tintDrawables(text, Color.rgb(8, 122, 115));
        mainBox.addView(text, new LinearLayout.LayoutParams(0, -1, 1));

        TextView toggle = new LocalizedTextView(this);
        toggle.setText(enabled ? "●" : "●");
        toggle.setGravity(enabled ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.LEFT | Gravity.CENTER_VERTICAL);
        toggle.setTextColor(Color.WHITE);
        toggle.setTextSize(20);
        toggle.setPadding(dp(3), 0, dp(3), 0);
        toggle.setBackground(toggleBg(enabled));
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(dp(44), dp(26));
        toggleParams.setMargins(dp(8), 0, 0, 0);
        mainBox.addView(toggle, toggleParams);
        row.addView(mainBox, new LinearLayout.LayoutParams(0, -1, 1));

        TextView help = new LocalizedTextView(this);
        help.setGravity(Gravity.CENTER);
        help.setText("?");
        help.setTextColor(Color.rgb(8, 122, 115));
        help.setTextSize(14);
        help.setTypeface(uiBold());
        help.setBackground(panelBg(Color.rgb(232, 248, 246), dp(8), Color.argb(72, 24, 169, 153)));
        help.setOnClickListener(v -> showHelp(label, helpText));
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(dp(42), -1);
        helpParams.setMargins(dp(6), 0, 0, 0);
        row.addView(help, helpParams);
        return row;
    }

    private GradientDrawable toggleBg(boolean enabled) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(enabled
            ? Color.rgb(24, 169, 153)
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
            new int[]{Color.rgb(8, 122, 115), Color.rgb(24, 169, 153)});
        bg.setCornerRadius(radius);
        return bg;
    }

    private GradientDrawable gradientBg() {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] { Color.rgb(24, 169, 153), Color.rgb(47, 140, 255) });
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

    private FrameLayout.LayoutParams railParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(52), -2, Gravity.RIGHT | Gravity.TOP);
        params.setMargins(0, dp(18), dp(8), 0);
        return params;
    }

    private FrameLayout.LayoutParams canvasModeParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, dp(68), Gravity.TOP);
        params.setMargins(dp(12), dp(58), dp(64), 0);
        return params;
    }

    private FrameLayout.LayoutParams historyParams() {
        float density = getResources().getDisplayMetrics().density;
        int screenWidthDp = Math.round(getResources().getDisplayMetrics().widthPixels / density);
        boolean phone = getResources().getConfiguration().smallestScreenWidthDp < 600;
        int widthDp = Math.min(phone ? 320 : 360, Math.max(240, screenWidthDp - 24));
        int heightDp = phone ? 176 : 242;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(widthDp), dp(heightDp), Gravity.LEFT | Gravity.BOTTOM);
        params.setMargins(dp(12), 0, 0, dp(88));
        return params;
    }

    private FrameLayout.LayoutParams branchStatusParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(330), dp(48), Gravity.RIGHT | Gravity.BOTTOM);
        params.setMargins(0, 0, dp(16), dp(154));
        return params;
    }

    private FrameLayout.LayoutParams selectionToolbarParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, dp(64), Gravity.BOTTOM);
        params.setMargins(dp(10), 0, dp(10), dp(84));
        return params;
    }

    private LinearLayout.LayoutParams selectionButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(88), dp(46));
        params.setMargins(dp(6), 0, 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams selectionResetButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(80), dp(46));
        params.setMargins(dp(6), 0, 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams selectionAppendParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(46), dp(46));
        params.setMargins(dp(6), 0, 0, 0);
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

    private int statusBarHeight() {
        int resource = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resource > 0 ? getResources().getDimensionPixelSize(resource) : dp(24);
    }

    void applyFocusTreeUi() {
        boolean settingsTab = "settings".equals(activePanel);
        if (appHeader != null) appHeader.setVisibility("settings".equals(activePanel) ? View.GONE : View.VISIBLE);
        if (headerBrand != null) headerBrand.setVisibility(focusTree ? View.GONE : View.VISIBLE);
        if (headerSaveButton != null) headerSaveButton.setVisibility(focusTree ? View.GONE : View.VISIBLE);
        if (treeHint != null) treeHint.setVisibility(
            settingsTab || focusTree || treeHintDismissed ? View.GONE : View.VISIBLE);
        if (zoomRail != null) zoomRail.setVisibility(settingsTab ? View.GONE : View.VISIBLE);
        updateAddPersonButtonVisibility();
        applyFocusNavStyle(treeNav, "Дерево");
        applyFocusNavStyle(cardNav, "Карточка");
        applyFocusNavStyle(linksNav, "Связи");
        applyFocusNavStyle(filesNav, "Файлы");
        applyFocusNavStyle(settingsNav, "Параметры");
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
        boolean settingsTab = "settings".equals(activePanel);
        boolean selectionVisible = selectionToolbar != null
            && selectionToolbar.getVisibility() == View.VISIBLE;
        addPersonButton.setVisibility(settingsTab || selectionVisible ? View.GONE : View.VISIBLE);
    }

    private void applyFocusNavStyle(Button button, String label) {
        if (button == null) return;
        button.setContentDescription(tr(label));
        button.setText(focusTree ? "" : label);
        button.setCompoundDrawablePadding(focusTree ? 0 : dp(4));
        button.setPadding(dp(3), focusTree ? dp(4) : dp(5), dp(3), focusTree ? dp(4) : dp(5));
        button.setMinHeight(dp(focusTree ? 46 : 58));
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
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    int uiColor(int color) {
        return AppThemePalette.text(color);
    }
}
