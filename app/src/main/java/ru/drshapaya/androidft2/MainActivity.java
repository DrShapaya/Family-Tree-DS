package ru.drshapaya.androidft2;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
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
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
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
    static final String VERSION_NAME = "2.5.1";
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
    CheckBox selectionAppendCheck;
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
    View appHeader;
    View headerBrand;
    Button headerSaveButton;
    View treeHint;
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
    boolean viewMode = false;
    boolean generationLines = true;
    boolean hideCardDetails = false;
    boolean compactCards = false;
    boolean focusTree = false;
    String parentLineMode = "smart";
    String activeGuideMode = "";
    String guideDraftLabel = "Поколение";
    String guideDraftColor = "#2f7d75";
    String theme = "light";
    String lastSelectionMode = "rect";
    boolean selectionAppendMode = false;
    boolean bindingEditor = false;
    String pendingPhotoPersonId = "";
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
    private TreeSaveCoordinator saveCoordinator;
    private final Handler toastHandler = new Handler(Looper.getMainLooper());
    private Toast currentToast;
    private final Runnable cancelToastRunnable = () -> {
        if (currentToast != null) currentToast.cancel();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(245, 248, 250));
        getWindow().setNavigationBarColor(Color.rgb(248, 251, 252));
        store = new TreeStore(this);
        state = store.load();
        filesModule = new MainActivityFiles(this);
        historyModule = new MainActivityHistory(this);
        headerModule = new MainActivityHeader(this);
        settingsModule = new MainActivitySettings(this);
        panelsModule = new MainActivityPanels(this);
        relationsModule = new MainActivityRelations(this);
        editorModule = new MainActivityEditor(this);
        saveCoordinator = new TreeSaveCoordinator(
            store,
            () -> state,
            () -> historyModule.commitPendingUndo(),
            () -> toast("Не удалось сохранить дерево"));
        applyStateSettings();
        TreeLayoutEngine.ensurePositions(state);
        buildUi();
        bindState();
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
        handleIncomingIntent(intent);
    }

    @Override
    protected void onStop() {
        DiagnosticsLogger.breadcrumb(this, "activity.stop");
        if (saveCoordinator != null) saveCoordinator.flush();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (saveCoordinator != null) saveCoordinator.close();
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
        root.setBackgroundColor(Color.rgb(243, 246, 248));
        setContentView(root);

        appHeader = buildHeader();
        root.addView(appHeader, new LinearLayout.LayoutParams(-1, -2));

        stage = new FrameLayout(this);
        stage.setFocusableInTouchMode(true);
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

        TextView hint = new TextView(this);
        hint.setText("Один палец - движение, два пальца - масштаб");
        hint.setTextColor(Color.rgb(101, 113, 122));
        hint.setTextSize(13);
        hint.setTypeface(ui());
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(12), 0, dp(12), 0);
        hint.setBackground(panelBg(Color.argb(218, 255, 255, 255), dp(8), Color.argb(42, 24, 169, 153)));
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(-1, dp(40), Gravity.TOP);
        hintParams.setMargins(dp(12), dp(10), dp(64), 0);
        stage.addView(hint, hintParams);
        treeHint = hint;

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

        Button add = iconButton(R.drawable.ic_menu_add_box, v -> {
            addLoosePerson();
            trainingTargetActivated("add-person");
        }, Color.WHITE);
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
        panel.setPadding(dp(10), dp(8), dp(10), dp(10));
        panel.setBackground(panelBg(Color.argb(224, 255, 255, 255), dp(8), Color.argb(52, 63, 82, 94)));
        panel.setElevation(dp(7));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("История");
        title.setTextColor(Color.rgb(101, 113, 122));
        title.setTextSize(10);
        title.setTypeface(uiBold());
        title.setAllCaps(true);
        title.setIncludeFontPadding(false);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(30), 1));

        historyHint = new TextView(this);
        historyHint.setTextColor(Color.rgb(101, 113, 122));
        historyHint.setTextSize(9);
        historyHint.setTypeface(uiBold());
        historyHint.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        historyHint.setIncludeFontPadding(false);
        header.addView(historyHint, new LinearLayout.LayoutParams(dp(132), dp(30)));

        Button hide = iconButton(R.drawable.ic_menu_eye_off, v -> {
            if (state == null) return;
            state.historyHidden = true;
            saveOnly();
            refreshSettingsIfVisible();
        });
        header.addView(hide, new LinearLayout.LayoutParams(dp(30), dp(30)));
        panel.addView(header);

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

        branchStatusText = new TextView(this);
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
        icon.setColorFilter(Color.rgb(8, 122, 115));
        panel.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, dp(8), 0);
        canvasModeTitle = new TextView(this);
        canvasModeTitle.setTextColor(Color.rgb(28, 34, 38));
        canvasModeTitle.setTextSize(12);
        canvasModeTitle.setTypeface(uiBold());
        canvasModeTitle.setSingleLine(true);
        canvasModeTitle.setIncludeFontPadding(false);
        canvasModeTitle.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(canvasModeTitle, new LinearLayout.LayoutParams(-1, dp(24)));
        canvasModeDetail = new TextView(this);
        canvasModeDetail.setTextColor(Color.rgb(76, 87, 96));
        canvasModeDetail.setTextSize(8);
        canvasModeDetail.setSingleLine(false);
        canvasModeDetail.setMaxLines(2);
        canvasModeDetail.setIncludeFontPadding(false);
        canvasModeDetail.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(canvasModeDetail, new LinearLayout.LayoutParams(-1, dp(28)));
        panel.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));

        canvasModeAction = actionButton("Стоп", v -> cancelActiveCanvasMode());
        canvasModeAction.setTextColor(Color.rgb(8, 122, 115));
        panel.addView(canvasModeAction, new LinearLayout.LayoutParams(dp(82), dp(42)));
        panel.setVisibility(View.GONE);
        return panel;
    }

    private LinearLayout buildSelectionToolbar() {
        LinearLayout panel = new LinearLayout(this);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        panel.setBackground(panelBg(Color.argb(250, 248, 251, 252), dp(8), Color.argb(58, 24, 169, 153)));
        panel.setElevation(dp(9));

        selectionStatusText = new TextView(this);
        selectionStatusText.setTextColor(Color.rgb(101, 113, 122));
        selectionStatusText.setTextSize(11);
        selectionStatusText.setTypeface(uiBold());
        selectionStatusText.setSingleLine(true);
        selectionStatusText.setGravity(Gravity.CENTER_VERTICAL);
        selectionStatusText.setIncludeFontPadding(false);
        panel.addView(selectionStatusText, new LinearLayout.LayoutParams(0, -1, 1));

        panel.addView(selectionActionButton(R.drawable.ic_menu_drag, "Двигать", v -> enableSelectionMove()), selectionButtonParams());
        panel.addView(selectionActionButton(R.drawable.ic_menu_selection_off, "Стоп", v -> clearSelection()), selectionResetButtonParams());
        selectionAppendCheck = new CheckBox(this);
        selectionAppendCheck.setText("Добор");
        selectionAppendCheck.setTextSize(9);
        selectionAppendCheck.setTypeface(uiBold());
        selectionAppendCheck.setTextColor(Color.rgb(8, 122, 115));
        selectionAppendCheck.setGravity(Gravity.CENTER);
        selectionAppendCheck.setIncludeFontPadding(false);
        selectionAppendCheck.setPadding(dp(4), 0, dp(4), 0);
        selectionAppendCheck.setBackground(panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        selectionAppendCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            selectionAppendMode = isChecked;
            if (treeView != null) treeView.setSelectionAppendMode(selectionAppendMode);
            toast(isChecked ? "Добор выделения включён" : "Новое выделение заменяет старое");
        });
        panel.addView(selectionAppendCheck, selectionAppendParams());
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
            R.drawable.ic_menu_tag,
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
        icon.setColorFilter(Color.rgb(8, 122, 115));
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
        TextView text = new TextView(this);
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
        TextView text = new TextView(this);
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
            R.drawable.ic_menu_file,
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
        TextView title = new TextView(this);
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
        modes.addView(guideModeTile("erase", R.drawable.ic_guide_delete, "Удалять", "Тап рядом с линией"), spacedTileParams());
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
        TextView text = new TextView(this);
        text.setText(generationLines ? "Линии поколений видны" : "Линии поколений скрыты");
        text.setTextColor(Color.rgb(28, 34, 38));
        text.setTextSize(13);
        text.setTypeface(uiBold());
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setCompoundDrawablesWithIntrinsicBounds(generationLines ? R.drawable.ic_menu_grid_lines : R.drawable.ic_menu_grid_off, 0, 0, 0);
        text.setCompoundDrawablePadding(dp(9));
        tintDrawables(text, Color.rgb(8, 122, 115));
        row.addView(text, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button toggle = actionButton(generationLines ? "Скрыть" : "Показать", v -> toggleGenerationLines());
        row.addView(toggle, new LinearLayout.LayoutParams(dp(104), dp(40)));
        return row;
    }

    private TextView guideCaption(String value) {
        TextView caption = new TextView(this);
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
        icon.setColorFilter(active ? Color.rgb(8, 122, 115) : Color.rgb(76, 87, 96));
        icon.setOnClickListener(listener);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(active ? Color.rgb(8, 122, 115) : Color.rgb(28, 34, 38));
        title.setTextSize(11);
        title.setTypeface(uiBold());
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setIncludeFontPadding(false);
        title.setOnClickListener(listener);
        tile.addView(title, new LinearLayout.LayoutParams(-1, dp(22)));

        TextView sub = new TextView(this);
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
        TextView preview = new TextView(this);
        preview.setBackground(panelBg(slider.color(), dp(999), Color.argb(72, 28, 34, 38)));
        slider.setListener((color, fromUser) -> {
            guideDraftColor = TreeState.colorString(color);
            preview.setBackground(panelBg(color, dp(999), Color.argb(72, 28, 34, 38)));
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
        rows.addView(menuRow(R.drawable.ic_nav_tree, "Отображение ветки", "Выберите, какую часть дерева показать.", v -> togglePanel("branch")));
        rows.addView(menuRow(R.drawable.ic_menu_grid_lines, "Линии поколений", "Открывает направляющие и линии поколений.", v -> togglePanel("guides")));
        return panel;
    }

    private LinearLayout buildBranchPanel() {
        LinearLayout panel = basePanel();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new TextView(this);
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
        panel.setBackgroundColor(Color.rgb(248, 251, 252));
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
        TextView title = new TextView(this);
        title.setText("Параметры");
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setTextSize(14);
        title.setTypeface(uiBold());
        top.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));
        panel.addView(top);
        panel.addView(settingActionRow(
            R.drawable.ic_menu_sparkles,
            "Обучение",
            "Интерактивно подсвечивает нужные кнопки и проводит через создание карточки, связи, дерево, файлы и параметры.",
            v -> startTraining()));
        panel.addView(settingSwitchRow(R.drawable.ic_editor_archive, "История действий", state == null || !state.historyHidden, "Показывает или скрывает панель последних действий на поле дерева.", v -> toggleHistoryPanel()));
        panel.addView(themeRow());
        panel.addView(settingSwitchRow(editLocked ? R.drawable.ic_menu_lock : R.drawable.ic_menu_unlock, "Защита правок", editLocked, "Блокирует или разрешает редактирование, чтобы не изменить дерево случайно.", v -> toggleLock()));
        panel.addView(settingSwitchRow(generationLines ? R.drawable.ic_menu_grid_lines : R.drawable.ic_menu_grid_off, "Линии поколений", generationLines, "Показывает или скрывает горизонтальные линии поколений.", v -> toggleGenerationLines()));
        panel.addView(settingSwitchRow(
            R.drawable.ic_menu_straight_links,
            "Ровные связи",
            "orthogonal".equals(parentLineMode),
            "По умолчанию связи родителей с детьми рисуются плавными кривыми. Этот режим включает ровные прямоугольные связи.",
            v -> toggleParentLineMode()));
        panel.addView(settingSwitchRow(hideCardDetails ? R.drawable.ic_menu_eye_off : R.drawable.ic_menu_eye, "Скрыть детали карточек", hideCardDetails, "Скрывает вторичные детали карточек, оставляя основной текст чище.", v -> toggleHideDetails()));
        panel.addView(settingSwitchRow(compactCards ? R.drawable.ic_menu_compress : R.drawable.ic_menu_expand, "Компактные карточки", compactCards, "Переключает компактные карточки 2 на 5: меньше высота, линии подстраиваются под новый размер.", v -> toggleCompactCards()));
        panel.addView(settingSwitchRow(focusTree ? R.drawable.ic_menu_focus_off : R.drawable.ic_menu_focus, "Фокус на дереве", focusTree, "Оставляет больше места дереву и делает нижнюю панель компактнее.", v -> toggleFocusTree()));
        panel.addView(menuRow(
            R.drawable.ic_menu_trash,
            "Удалить всё дерево",
            "Удаляет все карточки, связи и линии поколений. Действие можно отменить через историю.",
            v -> confirmDeleteWholeTree(),
            true));
    }

    void bindState() {
        syncSettingsToState();
        treeView.setState(state);
        treeView.setEditLocked(editLocked);
        treeView.setGenerationLines(generationLines);
        treeView.setHideDetails(hideCardDetails);
        treeView.setCompactCards(compactCards);
        treeView.setParentLineMode(parentLineMode);
        treeView.setTheme(theme);
        treeView.setBranchMode(branchMode, branchAnchorId);
        treeView.setLinkState(pendingLinkFrom, selectedLinkId);
        bindEditor(state.selectedPerson());
        updateStats();
        updateCanvasModePanel();
        updateHistoryButtons();
        updateHistoryPanel();
        updateBranchStatusPanel();
        updateSelectionToolbar();
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
        state.parentLineMode = "orthogonal".equals(parentLineMode) ? "orthogonal" : "smart";
        state.theme = normalizeTheme(theme);
    }

    void bindEditor(Person person) {
        editorModule.bindEditor(person);
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
        if (editLocked) return;
        recordUndo("Добавлена пустая карточка");
        hideKeyboard();
        PointF center = treeView.viewportCenterWorld();
        float[] spot = findOpenSpot(center.x, center.y);
        Person person = state.addPerson("Пустая карточка", spot[0], spot[1]);
        state.selectedId = person.id;
        saveToast("Карточка добавлена");
        bindState();
        treeView.invalidate();
    }

    private void openPersonActions(Person person, float screenX, float screenY) {
        if (person == null) return;
        final int popupWidth = Math.min(dp(292), getResources().getDisplayMetrics().widthPixels - dp(24));
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(9), dp(9), dp(9), dp(9));
        menu.setBackground(panelBg(Color.rgb(248, 251, 252), dp(12), Color.argb(51, 93, 85, 72)));
        menu.setElevation(dp(12));

        PopupWindow popup = new PopupWindow(menu, popupWidth, -2, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(12));

        menu.addView(personMenuHeader(person), new LinearLayout.LayoutParams(-1, dp(64)));
        menu.addView(personMenuAction(
            R.drawable.ic_nav_card,
            "Открыть редактор",
            false,
            popup,
            this::openPersonEditor));
        menu.addView(personMenuSection("ДОБАВИТЬ РОДСТВЕННИКА"), new LinearLayout.LayoutParams(-1, dp(30)));
        menu.addView(personMenuGroup(
            R.drawable.ic_menu_add_person,
            "Добавить родителей",
            new String[]{"1 родитель", "2 родителя"},
            new String[]{"add-parents-1", "add-parents-2"},
            popup));
        menu.addView(personMenuGroup(
            R.drawable.ic_menu_child,
            "Добавить детей",
            new String[]{"1 ребёнок", "2 детей", "3 детей"},
            new String[]{"add-children-1", "add-children-2", "add-children-3"},
            popup));
        menu.addView(personMenuAction(R.drawable.ic_menu_heart, "Добавить партнёра", false, popup, () -> addRelationAction("add-partner")));
        menu.addView(personMenuAction(R.drawable.ic_menu_people, "Добавить брата/сестру", false, popup, () -> addRelationAction("add-sibling")));
        menu.addView(personMenuSection("КАРТОЧКА"), new LinearLayout.LayoutParams(-1, dp(30)));
        menu.addView(personMenuAction(R.drawable.ic_menu_copy, "Дублировать карточку", false, popup, this::duplicateSelected));
        menu.addView(personMenuAction(
            R.drawable.ic_menu_lock,
            person.pinned ? "Открепить" : "Закрепить",
            false,
            popup,
            () -> togglePersonPin(person)));
        menu.addView(personMenuAction(R.drawable.ic_menu_trash, "Удалить карточку", true, popup, this::confirmDelete));

        menu.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupHeight = menu.getMeasuredHeight();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int x = Math.max(dp(8), Math.min(screenWidth - popupWidth - dp(8), Math.round(screenX) - popupWidth + dp(18)));
        int y = Math.round(screenY) + dp(8);
        if (y + popupHeight > screenHeight - dp(16)) y = Math.max(dp(16), Math.round(screenY) - popupHeight - dp(8));
        popup.showAtLocation(treeView, Gravity.TOP | Gravity.LEFT, x, y);
    }

    private View personMenuHeader(Person person) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(7), dp(10), dp(7));
        header.setBackground(panelBg(Color.rgb(232, 248, 246), dp(10), Color.argb(72, 24, 169, 153)));
        TextView avatar = new TextView(this);
        avatar.setText(personInitials(person.name));
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
        TextView detail = cardActionDetail(person.pinned ? "Положение закреплено" : "Меню выбранной карточки", false);
        detail.setTextSize(10);
        copy.addView(detail, new LinearLayout.LayoutParams(-1, dp(20)));
        header.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        return header;
    }

    private TextView personMenuSection(String value) {
        TextView section = new TextView(this);
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

    private TextView personMenuText(int iconRes, String label, boolean danger) {
        TextView row = new TextView(this);
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

    private void togglePersonPin(Person person) {
        if (person == null || editLocked) return;
        recordUndo(person.pinned ? "Откреплена карточка: " + person.name : "Закреплена карточка: " + person.name);
        person.pinned = !person.pinned;
        saveToast(person.pinned ? "Карточка закреплена" : "Карточка откреплена");
        bindState();
    }

    void addRelationAction(String action) {
        if (editLocked) return;
        Person current = state.selectedPerson();
        if (current == null) return;
        recordUndo();
        java.util.List<String> newIds = new java.util.ArrayList<>();

        if (action.startsWith("add-parents-")) {
            int count = countFromAction(action, 1);
            float[] offsets = distribute(count, 340f);
            for (int i = 0; i < count; i++) {
                float[] spot = positionNear(current, offsets[i], -300f);
                Person created = state.addPerson(count > 1 ? "Новый родитель " + (i + 1) : "Новый родитель", spot[0], spot[1]);
                state.addRelation("parent", created.id, current.id);
                newIds.add(created.id);
            }
            if (newIds.size() >= 2) state.addRelation("partner", newIds.get(0), newIds.get(1), "right");
        } else if (action.startsWith("add-children-")) {
            int count = countFromAction(action, 1);
            String partner = firstPartnerOf(current.id);
            java.util.List<String> parentIds = new java.util.ArrayList<>();
            parentIds.add(current.id);
            if (!partner.isEmpty()) parentIds.add(partner);
            float[] offsets = distribute(count, 340f);
            for (int i = 0; i < count; i++) {
                float[] spot = positionNear(current, offsets[i], 300f);
                Person created = state.addPerson(count > 1 ? "Новый ребёнок " + (i + 1) : "Новый ребёнок", spot[0], spot[1]);
                state.addRelation("parent", current.id, created.id);
                if (!partner.isEmpty()) state.addRelation("parent", partner, created.id);
                linkChildToSiblings(parentIds, created.id);
                newIds.add(created.id);
            }
        } else if ("add-partner".equals(action)) {
            float[] spot = positionNear(current, -320f, 0f);
            Person created = state.addPerson("Новый партнёр", spot[0], spot[1]);
            state.addRelation("partner", current.id, created.id, "left");
            newIds.add(created.id);
        } else if ("add-sibling".equals(action)) {
            float[] spot = positionNear(current, 320f, 0f);
            Person created = state.addPerson("Брат или сестра", spot[0], spot[1]);
            java.util.List<String> parents = parentIdsOf(current.id);
            if (parents.isEmpty()) {
                state.addRelation("sibling", current.id, created.id, "right");
            } else {
                for (String parentId : parents) state.addRelation("parent", parentId, created.id);
                linkChildToSiblings(parents, created.id);
            }
            newIds.add(created.id);
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

    private void addRelative(String kind) {
        if (editLocked) return;
        Person current = state.selectedPerson();
        if (current == null) return;
        recordUndo();
        Person created;
        if ("parent".equals(kind)) {
            float[] spot = positionNear(current, -160f, -300f);
            created = state.addPerson("Новый родитель", spot[0], spot[1]);
            state.addRelation("parent", created.id, current.id);
        } else if ("child".equals(kind)) {
            float[] spot = positionNear(current, 120f, 300f);
            created = state.addPerson("Новый ребёнок", spot[0], spot[1]);
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
            created = state.addPerson("Новый партнёр", spot[0], spot[1]);
            state.addRelation("partner", current.id, created.id, "left");
        } else {
            float[] spot = positionNear(current, 320f, 0f);
            created = state.addPerson("Брат или сестра", spot[0], spot[1]);
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
        if (editLocked) return;
        Person current = state.selectedPerson();
        if (current == null) return;
        recordUndo("Дублирована карточка: " + (current.name.isEmpty() ? "Без имени" : current.name));
        float[] spot = findOpenSpot(current.x + 320f, current.y + 40f);
        Person copy = state.addPerson((current.name.isEmpty() ? "Без имени" : current.name) + " (копия)", spot[0], spot[1]);
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
        if (editLocked) return;
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

    private void confirmDeleteWholeTree() {
        if (editLocked) {
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
        person.colorMode = "auto-name";
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
        java.util.Set<String> occupied = new java.util.HashSet<>();
        for (Person person : state.people.values()) {
            if (Float.isFinite(person.x) && Float.isFinite(person.y)) occupied.add(TreeLayoutEngine.snap(person.x) + ":" + TreeLayoutEngine.snap(person.y));
        }
        float[] start = snapPoint(preferredX, preferredY);
        if (!occupied.contains(start[0] + ":" + start[1])) return start;
        for (float radius = TreeLayoutEngine.GRID; radius <= TreeLayoutEngine.GRID * 14f; radius += TreeLayoutEngine.GRID) {
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
                if (!occupied.contains(spot[0] + ":" + spot[1])) return spot;
            }
        }
        return start;
    }

    private float[] snapPoint(float x, float y) {
        return new float[]{
            Math.min(TreeLayoutEngine.SURFACE_W - TreeLayoutEngine.CARD_W, Math.max(0f, TreeLayoutEngine.snap(x))),
            Math.min(TreeLayoutEngine.SURFACE_H - TreeLayoutEngine.CARD_H, Math.max(0f, TreeLayoutEngine.snap(y)))
        };
    }

    java.util.List<String> parentIdsOf(String childId) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (Relation link : state.links) {
            if ("parent".equals(link.type) && childId.equals(link.to) && state.people.containsKey(link.from)) ids.add(link.from);
        }
        return ids;
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
        if (editLocked) return;
        resetTransientCanvasModes(false);
        lastSelectionMode = "lasso".equals(mode) ? "lasso" : "rect";
        treeView.setSelectionMode(mode);
        toast("Выделение: " + ("lasso".equals(lastSelectionMode) ? "лассо" : "рамка"));
        updateSelectionToolbar();
        showPanel("");
    }

    private void startGuideMode(String mode) {
        if (editLocked) return;
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
        if (state == null || state.guides.isEmpty() || editLocked) {
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

    private void startTraining() {
        settingsModule.startTraining();
    }

    private void offerTrainingIfNeeded() {
        if (state == null || state.onboardingCompleted || state.onboardingOffered) return;
        state.onboardingOffered = true;
        saveOnly();
        showStyledConfirmation(
            R.drawable.ic_menu_sparkles,
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
            TextView pill = new TextView(this);
            String year = person.bornYear == null || person.bornYear.isEmpty() ? "" : " · " + person.bornYear;
            pill.setText((person.name == null || person.name.trim().isEmpty() ? "Без имени" : person.name.trim()) + year);
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
            TextView empty = new TextView(this);
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
        if (selectionAppendCheck != null && selectionAppendCheck.isChecked() != selectionAppendMode) {
            selectionAppendCheck.setChecked(selectionAppendMode);
        }
        selectionToolbar.setVisibility(visible ? View.VISIBLE : View.GONE);
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
        if (editLocked) toggleLock();
    }

    void updateCanvasModePanel() {
        if (canvasModePanel == null || canvasModeTitle == null || canvasModeDetail == null || canvasModeAction == null) return;
        boolean guideActive = !activeGuideMode.isEmpty();
        boolean linkActive = !pendingLinkType.isEmpty();
        boolean lockActive = editLocked;
        boolean visible = (guideActive || linkActive || lockActive) && activePanel.isEmpty();
        canvasModePanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;
        if (guideActive) {
            canvasModeAction.setText("Стоп");
            canvasModeTitle.setText(guideModeBannerTitle(activeGuideMode));
            canvasModeDetail.setText("erase".equals(activeGuideMode)
                ? "Коснитесь линии. После удаления режим выключится."
                : "Коснитесь полотна. После добавления режим выключится.");
            return;
        }
        if (linkActive) {
            canvasModeAction.setText("Стоп");
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
        canvasModeTitle.setText(viewMode ? "Режим просмотра" : "Правки заблокированы");
        canvasModeDetail.setText("Изменения дерева отключены");
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
        icon.setColorFilter(Color.rgb(8, 122, 115));
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(panelBg(Color.rgb(232, 248, 246), dp(999), Color.argb(62, 24, 169, 153)));
        top.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, 0, 0);
        TextView eyebrow = new TextView(this);
        eyebrow.setText("СЕМЕЙНАЯ СВЯЗЬ");
        eyebrow.setTextColor(Color.rgb(8, 122, 115));
        eyebrow.setTextSize(10);
        eyebrow.setTypeface(uiBold());
        eyebrow.setIncludeFontPadding(false);
        TextView title = new TextView(this);
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
        divider.setBackgroundColor(Color.argb(44, 63, 82, 94));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(0, dp(14), 0, dp(12));
        shell.addView(divider, dividerParams);

        if (result != null && result.found) {
            shell.addView(kinshipResultCard(firstName, secondName, result.firstToSecond), kinshipCardParams());
            TextView arrow = new TextView(this);
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
            TextView pair = new TextView(this);
            pair.setText(firstName + "  ↔  " + secondName);
            pair.setTextColor(Color.rgb(28, 34, 38));
            pair.setTextSize(14);
            pair.setTypeface(uiBold());
            pair.setGravity(Gravity.CENTER);
            empty.addView(pair, new LinearLayout.LayoutParams(-1, dp(30)));
            TextView explanation = new TextView(this);
            explanation.setText("Проверьте родительские, братские и партнёрские связи между карточками.");
            explanation.setTextColor(Color.rgb(101, 113, 122));
            explanation.setTextSize(12);
            explanation.setTypeface(ui());
            explanation.setGravity(Gravity.CENTER);
            empty.addView(explanation, new LinearLayout.LayoutParams(-1, -2));
            shell.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        }

        if (result != null && result.detail != null && !result.detail.isEmpty()) {
            TextView path = new TextView(this);
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

        TextView names = new TextView(this);
        names.setText(subject);
        names.setTextColor(Color.rgb(28, 34, 38));
        names.setTextSize(14);
        names.setTypeface(uiBold());
        names.setSingleLine(false);
        names.setMaxLines(2);
        card.addView(names, new LinearLayout.LayoutParams(-1, -2));

        TextView context = new TextView(this);
        context.setText("для " + reference);
        context.setTextColor(Color.rgb(101, 113, 122));
        context.setTextSize(11);
        context.setTypeface(ui());
        context.setPadding(0, dp(2), 0, 0);
        card.addView(context, new LinearLayout.LayoutParams(-1, -2));

        TextView answer = new TextView(this);
        answer.setText(relation == null || relation.isEmpty() ? "родство не определено" : relation);
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
        if (saveCoordinator != null) saveCoordinator.requestDebounced();
        updateStats();
        return true;
    }

    void saveToast() {
        saveToast("Дерево сохранено");
    }

    void saveToast(String message) {
        syncSettingsToState();
        if (saveCoordinator != null) saveCoordinator.requestImmediate();
        updateStats();
        toast(message);
    }

    void updateStats() {
        if (stats != null) stats.setText(state.people.size() + " человек, " + state.links.size() + " связей");
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

    void handleIncomingIntent(Intent intent) {
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

        TextView mark = new TextView(this);
        mark.setText("?");
        mark.setGravity(Gravity.CENTER);
        mark.setTextSize(18);
        mark.setTypeface(uiBold());
        mark.setIncludeFontPadding(false);
        mark.setTextColor(Color.WHITE);
        mark.setBackground(panelBg(Color.rgb(24, 169, 153), dp(999), Color.TRANSPARENT));
        top.addView(mark, new LinearLayout.LayoutParams(dp(34), dp(34)));

        TextView heading = new TextView(this);
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
        divider.setBackgroundColor(Color.argb(44, 63, 82, 94));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(0, dp(12), 0, dp(10));
        shell.addView(divider, dividerParams);

        ScrollView scroll = new ScrollView(this);
        TextView body = new TextView(this);
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
        icon.setColorFilter(accent);
        icon.setPadding(dp(13), dp(13), dp(13), dp(13));
        icon.setBackground(panelBg(accentSurface, dp(999), Color.argb(60, 63, 82, 94)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(54), dp(54));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        shell.addView(icon, iconParams);

        TextView heading = new TextView(this);
        heading.setText(title == null ? "" : title);
        heading.setTextColor(Color.rgb(28, 34, 38));
        heading.setTextSize(20);
        heading.setTypeface(uiBold());
        heading.setGravity(Gravity.CENTER);
        heading.setIncludeFontPadding(false);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.setMargins(0, dp(14), 0, 0);
        shell.addView(heading, headingParams);

        TextView body = new TextView(this);
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
        showPhotoPreviewBitmap(bitmapFromDataUrl(dataUrl));
    }

    void showMediaPhotoPreview(String mediaId) {
        showPhotoPreviewBitmap(store.mediaStore().decodeBitmap(mediaId, 1800));
    }

    private void showPhotoPreviewBitmap(Bitmap bitmap) {
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
        mark.setColorFilter(Color.rgb(8, 122, 115));
        mark.setPadding(dp(9), dp(9), dp(9), dp(9));
        mark.setBackground(panelBg(Color.rgb(232, 248, 246), dp(999), Color.TRANSPARENT));
        top.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView title = new TextView(this);
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

        Button close = actionButton("Закрыть", v -> dialog.dismiss());
        close.setTextColor(Color.WHITE);
        close.setBackground(panelBg(Color.rgb(8, 122, 115), dp(10), Color.TRANSPARENT));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-1, dp(46));
        closeParams.setMargins(0, dp(12), 0, 0);
        shell.addView(close, closeParams);

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
        if (editLocked) return;
        if (state != null && !state.people.isEmpty()) {
            showStyledConfirmation(
                R.drawable.ic_menu_sparkles,
                "Создать новое дерево?",
                "Текущее дерево будет удалено. Продолжить?",
                "Продолжить",
                false,
                this::openQuickStart);
            return;
        }
        openQuickStart();
    }

    private void openQuickStart() {
        if (editLocked) return;
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(18), dp(12), dp(18), dp(18));
        shell.setBackground(panelBg(Color.rgb(250, 252, 253), dp(20), Color.argb(44, 63, 82, 94)));

        View handle = new View(this);
        handle.setBackground(panelBg(Color.rgb(194, 207, 214), dp(999), Color.TRANSPARENT));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(44), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.setMargins(0, 0, 0, dp(10));
        shell.addView(handle, handleParams);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = quickCaption("НОВОЕ СЕМЕЙНОЕ ДЕРЕВО");
        TextView title = new TextView(this);
        title.setText("Быстрый старт");
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setTextSize(24);
        title.setTypeface(uiBold());
        title.setIncludeFontPadding(false);
        heading.addView(eyebrow, new LinearLayout.LayoutParams(-1, dp(18)));
        heading.addView(title, new LinearLayout.LayoutParams(-1, dp(34)));
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(54), 1));
        header.addView(closeButton(v -> dialog.dismiss()), new LinearLayout.LayoutParams(dp(42), dp(42)));
        shell.addView(header);

        LinearLayout progress = new LinearLayout(this);
        progress.setOrientation(LinearLayout.HORIZONTAL);
        progress.setGravity(Gravity.CENTER_VERTICAL);
        TextView personStep = quickStep("1", "Вы");
        TextView familyStep = quickStep("2", "Семья");
        View progressLine = new View(this);
        progress.addView(personStep, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(dp(34), dp(2));
        lineParams.setMargins(dp(8), 0, dp(8), 0);
        progress.addView(progressLine, lineParams);
        progress.addView(familyStep, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(54));
        progressParams.setMargins(0, dp(8), 0, dp(10));
        shell.addView(progress, progressParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout formHost = new LinearLayout(this);
        formHost.setOrientation(LinearLayout.VERTICAL);
        formHost.setPadding(0, dp(2), 0, dp(6));
        scroll.addView(formHost);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        EditText selfName = field("Например, Иван Иванов");
        selfName.setSingleLine(true);
        EditText selfYear = field("Например, 1987");
        selfYear.setSingleLine(true);
        selfYear.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText selfPlace = field("Город или место, необязательно");
        selfPlace.setSingleLine(true);
        EditText fatherName = field("Имя папы, необязательно");
        fatherName.setSingleLine(true);
        EditText motherName = field("Имя мамы, необязательно");
        motherName.setSingleLine(true);
        EditText story = field("Что важно сохранить для семьи?");
        story.setMinLines(4);
        story.setGravity(Gravity.CENTER_VERTICAL);
        story.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout personPage = new LinearLayout(this);
        personPage.setOrientation(LinearLayout.VERTICAL);
        personPage.addView(quickIntro(
            R.drawable.ic_nav_card,
            "Начнём с вашей карточки",
            "Она станет центром дерева. Остальные сведения можно добавить позже."),
            quickSectionParams());
        LinearLayout selfCard = quickSection("Основные сведения", "Обязательное поле только одно — имя");
        selfCard.addView(quickField("Имя", selfName, R.drawable.ic_field_person), formFieldParams());
        LinearLayout selfDetails = new LinearLayout(this);
        selfDetails.setOrientation(LinearLayout.HORIZONTAL);
        selfDetails.addView(quickField("Год рождения", selfYear, R.drawable.ic_field_calendar), new LinearLayout.LayoutParams(0, -2, 0.8f));
        selfDetails.addView(quickField("Место", selfPlace, R.drawable.ic_field_location), spacedInputParams());
        selfCard.addView(selfDetails, formFieldParams());
        personPage.addView(selfCard, quickSectionParams());

        TextView personHint = quickNote(state != null && state.people.size() > 1
            ? "Будет создано новое дерево. Текущее можно вернуть командой «Отменить» в истории действий."
            : "После создания откроется обычное дерево: карточки можно дополнять, перемещать и связывать.");
        personPage.addView(personHint, quickSectionParams());

        LinearLayout familyPage = new LinearLayout(this);
        familyPage.setOrientation(LinearLayout.VERTICAL);
        familyPage.setVisibility(View.GONE);
        familyPage.addView(quickIntro(
            R.drawable.ic_menu_people,
            "Добавьте ближайшую семью",
            "Оставьте поле пустым, если пока не хотите создавать эту карточку."),
            quickSectionParams());
        LinearLayout parentsCard = quickSection("Родители", "Папа будет слева, мама — справа");
        parentsCard.addView(quickField("Папа", fatherName, R.drawable.ic_field_person), formFieldParams());
        parentsCard.addView(quickField("Мама", motherName, R.drawable.ic_field_person), formFieldParams());
        familyPage.addView(parentsCard, quickSectionParams());
        LinearLayout storyCard = quickSection("Первая история", "Необязательно — сохранится в вашей карточке");
        storyCard.addView(quickField("Семейная заметка", story, R.drawable.ic_field_note), formFieldParams());
        familyPage.addView(storyCard, quickSectionParams());

        TextView preview = new TextView(this);
        preview.setTextColor(Color.rgb(8, 122, 115));
        preview.setTextSize(11);
        preview.setTypeface(uiBold());
        preview.setGravity(Gravity.CENTER_VERTICAL);
        preview.setPadding(dp(12), dp(8), dp(12), dp(8));
        preview.setBackground(panelBg(Color.rgb(232, 248, 246), dp(10), Color.argb(72, 24, 169, 153)));
        familyPage.addView(preview, quickSectionParams());

        formHost.addView(personPage, new LinearLayout.LayoutParams(-1, -2));
        formHost.addView(familyPage, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(12), 0, 0);
        final int[] step = new int[]{0};
        Button back = actionButton("Отмена", null);
        back.setTextSize(14);
        Button primary = actionButton("Продолжить", null);
        primary.setTextSize(14);
        primary.setTextColor(Color.WHITE);
        primary.setBackground(tealGradientBg(dp(11)));
        actions.addView(back, new LinearLayout.LayoutParams(0, dp(56), 0.82f));
        LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(0, dp(56), 1.18f);
        primaryParams.setMargins(dp(10), 0, 0, 0);
        actions.addView(primary, primaryParams);
        shell.addView(actions);

        final Runnable[] renderStep = new Runnable[1];
        renderStep[0] = () -> {
            boolean family = step[0] == 1;
            personPage.setVisibility(family ? View.GONE : View.VISIBLE);
            familyPage.setVisibility(family ? View.VISIBLE : View.GONE);
            personStep.setBackground(quickStepBg(!family));
            familyStep.setBackground(quickStepBg(family));
            personStep.setTextColor(Color.rgb(8, 122, 115));
            familyStep.setTextColor(Color.rgb(8, 122, 115));
            progressLine.setBackgroundColor(family ? Color.rgb(24, 169, 153) : Color.rgb(217, 224, 229));
            back.setText(family ? "Назад" : "Отмена");
            primary.setText(family ? "Создать дерево" : "Продолжить");
            primary.setCompoundDrawablesWithIntrinsicBounds(
                0,
                0,
                family ? R.drawable.ic_menu_check : 0,
                0);
            tintDrawables(primary, Color.WHITE);
            preview.setText(quickPreview(
                text(selfName),
                text(fatherName),
                text(motherName),
                text(story)));
            scroll.scrollTo(0, 0);
        };
        back.setOnClickListener(v -> {
            if (step[0] == 0) dialog.dismiss();
            else {
                step[0] = 0;
                renderStep[0].run();
            }
        });
        primary.setOnClickListener(v -> {
            if (step[0] == 0) {
                String name = text(selfName).trim();
                if (name.isEmpty()) {
                    selfName.setError("Введите имя");
                    selfName.requestFocus();
                    return;
                }
                step[0] = 1;
                renderStep[0].run();
                return;
            }
            dialog.dismiss();
            createQuickStartTree(
                text(selfName),
                text(selfYear),
                text(selfPlace),
                text(fatherName),
                text(motherName),
                text(story));
        });
        TextWatcher previewWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                preview.setText(quickPreview(
                    text(selfName),
                    text(fatherName),
                    text(motherName),
                    text(story)));
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        selfName.addTextChangedListener(previewWatcher);
        fatherName.addTextChangedListener(previewWatcher);
        motherName.addTextChangedListener(previewWatcher);
        story.addTextChangedListener(previewWatcher);
        renderStep[0].run();

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = WindowManager.LayoutParams.MATCH_PARENT;
            attrs.height = Math.min(Math.round(getResources().getDisplayMetrics().heightPixels * 0.92f), dp(900));
            attrs.dimAmount = 0.28f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
    }

    private LinearLayout quickSection(String title, String subtitle) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(14), dp(14), dp(14), dp(12));
        section.setBackground(panelBg(Color.WHITE, dp(14), Color.rgb(217, 224, 229)));
        section.setElevation(dp(1));
        TextView heading = new TextView(this);
        heading.setText(title.toUpperCase(Locale.ROOT));
        heading.setTextColor(Color.rgb(28, 34, 38));
        heading.setTextSize(11);
        heading.setTypeface(uiBold());
        heading.setIncludeFontPadding(false);
        section.addView(heading, new LinearLayout.LayoutParams(-1, dp(23)));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView detail = new TextView(this);
            detail.setText(subtitle);
            detail.setTextColor(Color.rgb(101, 113, 122));
            detail.setTextSize(10);
            detail.setIncludeFontPadding(false);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, dp(24));
            detailParams.setMargins(0, 0, 0, dp(6));
            section.addView(detail, detailParams);
        }
        return section;
    }

    private LinearLayout quickField(String label, EditText edit, int iconRes) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(quickCaption(label), new LinearLayout.LayoutParams(-1, dp(22)));
        styleQuickField(edit, iconRes);
        block.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        return block;
    }

    private void styleQuickField(EditText edit, int iconRes) {
        edit.setMinHeight(dp(52));
        edit.setTextSize(14);
        edit.setPadding(dp(14), edit.getMinLines() > 1 ? dp(12) : 0, dp(14), edit.getMinLines() > 1 ? dp(12) : 0);
        edit.setBackground(panelBg(Color.WHITE, dp(10), Color.rgb(217, 224, 229)));
        if (iconRes != 0) {
            edit.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
            edit.setCompoundDrawablePadding(dp(10));
            tintDrawables(edit, Color.rgb(24, 169, 153));
        }
    }

    private TextView quickCaption(String value) {
        TextView caption = new TextView(this);
        caption.setText(value);
        caption.setTextColor(Color.rgb(76, 83, 88));
        caption.setTextSize(11);
        caption.setTypeface(uiBold());
        caption.setGravity(Gravity.CENTER_VERTICAL);
        caption.setIncludeFontPadding(false);
        return caption;
    }

    private TextView quickStep(String number, String label) {
        TextView step = new TextView(this);
        step.setText(number + "  " + label);
        step.setTextSize(12);
        step.setTypeface(uiBold());
        step.setGravity(Gravity.CENTER);
        step.setIncludeFontPadding(false);
        return step;
    }

    private GradientDrawable quickStepBg(boolean active) {
        return active
            ? panelBg(Color.WHITE, dp(999), Color.rgb(24, 169, 153))
            : panelBg(Color.rgb(232, 248, 246), dp(999), Color.TRANSPARENT);
    }

    private View quickIntro(int iconRes, String title, String detail) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(panelBg(Color.rgb(232, 248, 246), dp(14), Color.argb(62, 24, 169, 153)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.rgb(8, 122, 115));
        card.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, 0, 0);
        TextView introTitle = cardActionTitle(title, false);
        introTitle.setTextSize(14);
        copy.addView(introTitle, new LinearLayout.LayoutParams(-1, dp(25)));
        TextView sub = new TextView(this);
        sub.setText(detail);
        sub.setTextColor(Color.rgb(76, 87, 96));
        sub.setTextSize(11);
        sub.setMaxLines(2);
        sub.setIncludeFontPadding(false);
        copy.addView(sub, new LinearLayout.LayoutParams(-1, dp(34)));
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        return card;
    }

    private TextView quickNote(String value) {
        TextView note = new TextView(this);
        note.setText(value);
        note.setTextColor(Color.rgb(76, 87, 96));
        note.setTextSize(11);
        note.setGravity(Gravity.CENTER_VERTICAL);
        note.setPadding(dp(14), dp(12), dp(14), dp(12));
        note.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_file, 0, 0, 0);
        note.setCompoundDrawablePadding(dp(10));
        tintDrawables(note, Color.rgb(105, 100, 184));
        note.setBackground(panelBg(Color.rgb(247, 249, 252), dp(12), Color.rgb(217, 224, 229)));
        return note;
    }

    private String quickPreview(String self, String father, String mother, String story) {
        int cards = 1;
        if (father != null && !father.trim().isEmpty()) cards++;
        if (mother != null && !mother.trim().isEmpty()) cards++;
        int links = Math.max(0, cards - 1);
        if (cards == 3) links++;
        String name = self == null || self.trim().isEmpty() ? "ваша карточка" : self.trim();
        return "Будет создано: " + name + " · карточек: " + cards + " · связей: " + links
            + (story == null || story.trim().isEmpty() ? "" : " · 1 история");
    }

    private LinearLayout.LayoutParams quickSectionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(14));
        return params;
    }

    private void createQuickStartTree(String selfName, String selfYear, String selfPlace, String parentOne, String parentTwo, String story) {
        recordUndo("Создано дерево через быстрый старт");
        state.people.clear();
        state.links.clear();
        state.guides.clear();
        Person child = state.addPerson(selfName.trim().isEmpty() ? "Новый человек" : selfName.trim(), 4000, 3000);
        child.bornYear = selfYear.trim();
        child.place = selfPlace.trim();
        if (!story.trim().isEmpty()) {
            Memory memory = new Memory();
            memory.id = "m_" + java.util.UUID.randomUUID().toString().replace("-", "");
            memory.title = "Первая история";
            memory.text = story.trim();
            memory.at = String.valueOf(System.currentTimeMillis());
            child.memories.add(memory);
        }
        Person father = null;
        Person mother = null;
        if (!parentOne.trim().isEmpty()) {
            father = state.addPerson(parentOne.trim(), 3740, 2780);
            state.addRelation("parent", father.id, child.id);
        }
        if (!parentTwo.trim().isEmpty()) {
            mother = state.addPerson(parentTwo.trim(), 4260, 2780);
            state.addRelation("parent", mother.id, child.id);
        }
        if (father != null && mother != null) {
            state.addRelation("partner", father.id, mother.id, "right");
        }
        state.rootId = child.id;
        state.selectedId = child.id;
        TreeLayoutEngine.layout(state);
        saveToast("Быстрый старт создан");
        bindState();
        treeView.invalidate();
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

    private TextView closeButton(View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setGravity(Gravity.CENTER);
        button.setBackground(panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        button.setForeground(centeredIcon(R.drawable.ic_menu_close, Color.rgb(28, 34, 38)));
        button.setForegroundGravity(Gravity.CENTER);
        button.setOnClickListener(listener);
        return button;
    }

    private TextView branchStatusButton(String label, View.OnClickListener listener) {
        TextView button = new TextView(this);
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
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextSize(15);
        edit.setTypeface(ui());
        edit.setSingleLine(false);
        edit.setIncludeFontPadding(false);
        edit.setTextColor(Color.rgb(28, 34, 38));
        edit.setHintTextColor(Color.rgb(128, 137, 144));
        edit.setPadding(dp(10), 0, dp(10), 0);
        edit.setMinHeight(dp(44));
        edit.setBackground(panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        return edit;
    }

    Button actionButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(uiBold());
        button.setTextColor(Color.rgb(28, 34, 38));
        button.setOnClickListener(listener);
        button.setStateListAnimator(null);
        button.setElevation(dp(2));
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
        icon.setTint(tint);
        return icon;
    }

    private Button selectionActionButton(int iconRes, String label, View.OnClickListener listener) {
        Button button = actionButton(label, listener);
        button.setTextSize(11);
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(4), 0, dp(4), 0);
        button.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        button.setCompoundDrawablePadding(dp(3));
        tintDrawables(button, Color.rgb(8, 122, 115));
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
        int color = active ? Color.rgb(8, 122, 115) : Color.rgb(28, 34, 38);
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

        TextView main = new TextView(this);
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

        TextView helpButton = new TextView(this);
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
        if (icon.getDrawable() != null) icon.getDrawable().mutate().setTint(active ? Color.rgb(8, 122, 115) : Color.rgb(28, 34, 38));
        tile.addView(icon, new LinearLayout.LayoutParams(dp(21), dp(21)));

        TextView text = new TextView(this);
        text.setGravity(Gravity.CENTER);
        text.setText(label);
        text.setTextSize(11);
        text.setTypeface(uiBold());
        text.setIncludeFontPadding(false);
        text.setTextColor(active ? Color.rgb(8, 122, 115) : Color.rgb(28, 34, 38));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-1, dp(16));
        textParams.setMargins(0, dp(1), 0, dp(1));
        tile.addView(text, textParams);

        TextView help = new TextView(this);
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

        TextView main = new TextView(this);
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

        TextView helpButton = new TextView(this);
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

        TextView text = new TextView(this);
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

        TextView toggle = new TextView(this);
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

        TextView help = new TextView(this);
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
        bg.setColor(enabled ? Color.rgb(24, 169, 153) : Color.rgb(218, 224, 228));
        bg.setCornerRadius(dp(999));
        bg.setStroke(dp(1), enabled ? Color.argb(70, 8, 122, 115) : Color.rgb(210, 217, 222));
        return bg;
    }

    void tintDrawables(TextView view, int color) {
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
        bg.setColor(color);
        bg.setCornerRadius(radius);
        bg.setStroke(dp(1), stroke);
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
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(280), dp(230), Gravity.LEFT | Gravity.BOTTOM);
        params.setMargins(dp(16), 0, 0, dp(90));
        return params;
    }

    private FrameLayout.LayoutParams branchStatusParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(330), dp(48), Gravity.RIGHT | Gravity.BOTTOM);
        params.setMargins(0, 0, dp(16), dp(154));
        return params;
    }

    private FrameLayout.LayoutParams selectionToolbarParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, dp(60), Gravity.BOTTOM);
        params.setMargins(dp(10), 0, dp(10), dp(84));
        return params;
    }

    private LinearLayout.LayoutParams selectionButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(82), -1);
        params.setMargins(dp(7), 0, 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams selectionResetButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(92), -1);
        params.setMargins(dp(7), 0, 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams selectionAppendParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(72), -1);
        params.setMargins(dp(7), 0, 0, 0);
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
        params.setMargins(0, statusBarHeight() + dp(14), 0, dp(74));
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
        if (treeHint != null) treeHint.setVisibility(settingsTab || focusTree ? View.GONE : View.VISIBLE);
        if (zoomRail != null) zoomRail.setVisibility(settingsTab ? View.GONE : View.VISIBLE);
        if (addPersonButton != null) addPersonButton.setVisibility(settingsTab ? View.GONE : View.VISIBLE);
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
            params.setMargins(0, statusBarHeight() + dp(14), 0, dp(focusTree ? 54 : 74));
            settingsPanel.setLayoutParams(params);
        }
        updateHistoryPanel();
    }

    private void applyFocusNavStyle(Button button, String label) {
        if (button == null) return;
        button.setContentDescription(label);
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
        currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        currentToast.show();
        toastHandler.postDelayed(cancelToastRunnable, 900);
    }

    int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
