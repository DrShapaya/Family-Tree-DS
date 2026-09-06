package ru.drshapaya.androidft2;

import android.graphics.Color;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

final class MainActivitySettings {
    private final MainActivity activity;
    private int trainingStep = -1;
    private int trainingGeneration;
    private int targetPositionAttempts;
    private boolean trainingStepConfirmed;
    private TrainingSpotlightView trainingSpotlight;
    private LinearLayout trainingCard;
    private TextView trainingProgress;
    private TextView trainingTitle;
    private TextView trainingDetail;
    private TextView trainingHint;
    private Button trainingBack;
    private Button trainingPrimary;

    MainActivitySettings(MainActivity activity) {
        this.activity = activity;
    }

    void toggleLock() {
        if (activity.onlineReadOnly) {
            activity.toast("Глава дерева включил режим просмотра");
            return;
        }
        if (!activity.editLocked) activity.resetTransientCanvasModes(false);
        activity.editLocked = !activity.editLocked;
        activity.viewMode = false;
        activity.treeView.setEditLocked(activity.editingBlocked());
        activity.refreshLockUi();
        activity.refreshOpenPersonEditor();
        refreshSettingsIfVisible();
        activity.saveOnly();
        activity.showPanel(activity.activePanel);
    }

    void toggleViewMode() {
        if (activity.onlineReadOnly) {
            activity.toast("Режим просмотра управляется главой дерева");
            return;
        }
        if (!activity.viewMode) activity.resetTransientCanvasModes(false);
        activity.viewMode = !activity.viewMode;
        activity.editLocked = activity.viewMode;
        activity.treeView.setEditLocked(activity.editingBlocked());
        activity.refreshLockUi();
        activity.refreshOpenPersonEditor();
        refreshSettingsIfVisible();
        activity.saveOnly();
        activity.toast(activity.viewMode ? "Режим просмотра включён" : "Режим просмотра выключен");
    }

    void toggleGenerationLines() {
        activity.generationLines = !activity.generationLines;
        activity.treeView.setGenerationLines(activity.generationLines);
        refreshSettingsIfVisible();
        activity.saveOnly();
        refreshGuidePanelIfVisible();
    }

    void toggleHideDetails() {
        activity.hideCardDetails = !activity.hideCardDetails;
        activity.treeView.setHideDetails(activity.hideCardDetails);
        refreshSettingsIfVisible();
        activity.saveOnly();
    }

    void toggleCompactCards() {
        boolean makeCompact = !activity.compactCards;
        float oldHeight = activity.compactCards
            ? TreeLayoutEngine.GRID * 3f
            : TreeLayoutEngine.CARD_H;
        float newHeight = makeCompact
            ? TreeLayoutEngine.GRID * 3f
            : TreeLayoutEngine.CARD_H;
        float shift = oldHeight - newHeight;
        for (Person person : activity.state.people.values()) {
            person.y = person.y + shift;
        }
        activity.compactCards = makeCompact;
        activity.treeView.setCompactCards(activity.compactCards);
        activity.treeView.invalidateStructureCaches();
        refreshSettingsIfVisible();
        activity.saveOnly();
    }

    void toggleParentLineMode() {
        activity.parentLineMode = "orthogonal".equals(activity.parentLineMode) ? "smart" : "orthogonal";
        activity.treeView.setParentLineMode(activity.parentLineMode);
        refreshSettingsIfVisible();
        activity.saveOnly();
        activity.toast("orthogonal".equals(activity.parentLineMode)
            ? "Ровные связи включены"
            : "Кривые родительские линии включены");
    }

    void toggleFocusTree() {
        activity.focusTree = !activity.focusTree;
        activity.applyFocusTreeUi();
        refreshSettingsIfVisible();
        activity.saveOnly();
        if (activity.focusTree) activity.showPanel("");
        activity.toast(activity.focusTree ? "Фокус на дереве включён" : "Обычный интерфейс восстановлен");
    }

    void toggleHistoryPanel() {
        if (activity.state == null) return;
        activity.state.historyHidden = !activity.state.historyHidden;
        refreshSettingsIfVisible();
        activity.saveOnly();
        activity.toast(activity.state.historyHidden ? "История скрыта" : "История показана");
    }

    void setTheme(String nextTheme) {
        String normalized = normalizeTheme(nextTheme);
        if (normalized.equals(activity.theme)) return;
        activity.theme = normalized;
        activity.saveOnly();
        activity.toast("clean".equals(activity.theme) ? "Чистый режим" : "dark".equals(activity.theme) ? "Тёмная тема" : "Светлая тема");
        activity.recreateWithTheme();
    }

    void startTraining() {
        activity.getSharedPreferences("androidft-ui", MainActivity.MODE_PRIVATE)
            .edit()
            .putBoolean("training_offer_handled", true)
            .apply();
        if (!activity.state.onboardingOffered) {
            activity.state.onboardingOffered = true;
            activity.saveOnly();
        }
        stopTraining(false);
        showTrainingStep(0);
    }

    void showTrainingStep(int index) {
        if (index >= MainActivity.TRAINING_STEPS.length) {
            stopTraining(true);
            return;
        }
        if (index < 0) index = 0;
        trainingStep = index;
        trainingGeneration++;
        targetPositionAttempts = 0;
        trainingStepConfirmed = false;
        String[] step = MainActivity.TRAINING_STEPS[index];
        prepareTrainingStep(step[0]);
        ensureTrainingOverlay();
        String progress = AppLanguage.isEnglish(activity)
            ? "TRAINING  ·  STEP " + (index + 1) + " OF " + MainActivity.TRAINING_STEPS.length
            : "ОБУЧЕНИЕ  ·  ШАГ " + (index + 1) + " ИЗ " + MainActivity.TRAINING_STEPS.length;
        LocalizedViews.setRaw(trainingProgress, progress);
        trainingTitle.setText(step[1]);
        trainingDetail.setText(step[2]);
        configureTrainingActions(step[3]);
        final int generation = trainingGeneration;
        activity.stage.post(() -> positionTrainingTarget(step[0], step[3], generation));
    }

    void prepareTrainingStep(String id) {
        activity.resetTransientCanvasModes(false);
        if ("more-overview".equals(id)) {
            activity.showPanel("more");
        } else {
            activity.showPanel("");
        }
    }

    void onTrainingTargetActivated(String id) {
        if (trainingStep < 0 || trainingStep >= MainActivity.TRAINING_STEPS.length) return;
        String[] current = MainActivity.TRAINING_STEPS[trainingStep];
        String expected = current[0];
        if (!"tap".equals(current[3])) return;
        if (!expected.equals(id)) return;
        if (trainingStepConfirmed) return;
        final int completedStep = trainingStep;
        final int generation = trainingGeneration;
        trainingStepConfirmed = true;
        targetPositionAttempts = 0;
        trainingSpotlight.disableTargetTap();
        activity.stage.postDelayed(() -> {
            if (trainingStep != completedStep || generation != trainingGeneration) return;
            positionOpenedTrainingSection(id, generation);
        }, 140L);
    }

    private void ensureTrainingOverlay() {
        if (activity.stage == null) return;
        if (trainingSpotlight == null) {
            trainingSpotlight = new TrainingSpotlightView(activity);
            trainingSpotlight.setElevation(activity.dp(22));
            activity.stage.addView(trainingSpotlight, new FrameLayout.LayoutParams(-1, -1));
        }
        if (trainingCard != null) {
            trainingSpotlight.bringToFront();
            trainingCard.bringToFront();
            return;
        }

        trainingCard = new LinearLayout(activity);
        trainingCard.setOrientation(LinearLayout.VERTICAL);
        trainingCard.setPadding(activity.dp(16), activity.dp(12), activity.dp(16), activity.dp(12));
        trainingCard.setBackground(activity.panelBg(Color.rgb(252, 254, 254), activity.dp(18), Color.argb(155, 24, 169, 153)));
        trainingCard.setElevation(activity.dp(26));

        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        trainingProgress = trainingText(10, Color.rgb(8, 122, 115), true);
        heading.addView(trainingProgress, new LinearLayout.LayoutParams(0, activity.dp(32), 1));
        Button close = activity.actionButton("Закрыть", v -> stopTraining(false));
        close.setTextSize(10);
        close.setElevation(0f);
        close.setContentDescription(AppLanguage.text(activity, "Закрыть обучение"));
        heading.addView(close, new LinearLayout.LayoutParams(activity.dp(82), activity.dp(32)));
        trainingCard.addView(heading, new LinearLayout.LayoutParams(-1, activity.dp(32)));

        trainingTitle = trainingText(17, Color.rgb(28, 34, 38), true);
        trainingCard.addView(trainingTitle, new LinearLayout.LayoutParams(-1, activity.dp(32)));
        trainingDetail = trainingText(12, Color.rgb(76, 87, 96), false);
        trainingDetail.setGravity(Gravity.TOP);
        trainingDetail.setMaxLines(4);
        trainingDetail.setLineSpacing(activity.dp(2), 1f);
        trainingCard.addView(trainingDetail, new LinearLayout.LayoutParams(-1, activity.dp(66)));

        trainingHint = trainingText(11, Color.rgb(8, 122, 115), true);
        trainingHint.setGravity(Gravity.CENTER_VERTICAL);
        trainingHint.setPadding(activity.dp(10), 0, activity.dp(10), 0);
        trainingHint.setBackground(activity.panelBg(Color.rgb(235, 248, 246), activity.dp(12), Color.argb(60, 24, 169, 153)));
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, activity.dp(34));
        hintParams.setMargins(0, activity.dp(2), 0, activity.dp(8));
        trainingCard.addView(trainingHint, hintParams);

        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        trainingBack = activity.actionButton("Назад", v -> showTrainingStep(trainingStep - 1));
        trainingBack.setTextSize(11);
        trainingBack.setElevation(0f);
        footer.addView(trainingBack, new LinearLayout.LayoutParams(activity.dp(90), activity.dp(40)));
        View spacer = new View(activity);
        footer.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        trainingPrimary = activity.actionButton("Далее", v -> advanceTraining());
        trainingPrimary.setTextSize(11);
        trainingPrimary.setTextColor(Color.WHITE);
        trainingPrimary.setBackground(activity.tealGradientBg(activity.dp(12)));
        footer.addView(trainingPrimary, new LinearLayout.LayoutParams(activity.dp(112), activity.dp(40)));
        trainingCard.addView(footer, new LinearLayout.LayoutParams(-1, activity.dp(40)));

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        cardParams.setMargins(activity.dp(12), activity.dp(10), activity.dp(12), 0);
        activity.stage.addView(trainingCard, cardParams);
        trainingSpotlight.bringToFront();
        trainingCard.bringToFront();
    }

    private void configureTrainingActions(String mode) {
        trainingBack.setVisibility(trainingStep == 0 ? View.INVISIBLE : View.VISIBLE);
        if ("tap".equals(mode)) {
            trainingHint.setText("Нажмите подсвеченную кнопку");
            trainingPrimary.setText("Пропустить");
        } else if ("finish".equals(mode)) {
            trainingHint.setText("Обучение завершено — его всегда можно открыть снова через «Ещё»");
            trainingPrimary.setText("Готово");
        } else {
            trainingHint.setText(trainingStep == 0 ? "Начнём с основных жестов" : "Посмотрите на подсвеченный элемент");
            trainingPrimary.setText("Далее");
        }
    }

    private void advanceTraining() {
        if (trainingStep < 0 || trainingStep >= MainActivity.TRAINING_STEPS.length) return;
        String mode = MainActivity.TRAINING_STEPS[trainingStep][3];
        if ("finish".equals(mode)) stopTraining(true);
        else showTrainingStep(trainingStep + 1);
    }

    private void positionOpenedTrainingSection(String id, int generation) {
        if (trainingStep < 0 || generation != trainingGeneration || trainingSpotlight == null) return;
        View target = activity.trainingOpenedTarget(id);
        if (target == null || !target.isShown() || target.getWidth() <= 0 || target.getHeight() <= 0) {
            targetPositionAttempts++;
            if (targetPositionAttempts <= 15) {
                activity.stage.postDelayed(() -> positionOpenedTrainingSection(id, generation), 100L);
            } else {
                trainingSpotlight.clearTarget();
                trainingHint.setText("Раздел открыт. Осмотритесь и нажмите «Далее»");
                trainingPrimary.setText("Далее");
                positionTrainingCard(null);
            }
            return;
        }
        RectF bounds = targetBounds(target, activity.dp(4));
        trainingSpotlight.showTarget(
            bounds,
            false,
            AppLanguage.text(activity, "Раздел открыт"),
            null);
        trainingHint.setText("Раздел открыт. Осмотритесь и нажмите «Далее»");
        trainingPrimary.setText("Далее");
        positionTrainingCard(bounds);
        trainingSpotlight.bringToFront();
        trainingCard.bringToFront();
    }

    private TextView trainingText(int size, int color, boolean bold) {
        TextView text = new LocalizedTextView(activity);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setTypeface(bold ? activity.uiBold() : activity.ui());
        text.setIncludeFontPadding(false);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }

    private void positionTrainingTarget(String id, String mode, int generation) {
        if (trainingStep < 0 || generation != trainingGeneration || activity.stage == null || trainingSpotlight == null) return;
        if (id == null || id.isEmpty()) {
            trainingSpotlight.clearTarget();
            positionTrainingCard(null);
            return;
        }
        View target = activity.trainingTarget(id);
        if (target == null || !target.isShown() || target.getWidth() <= 0 || target.getHeight() <= 0) {
            targetPositionAttempts++;
            if (targetPositionAttempts <= 15) {
                activity.stage.postDelayed(() -> positionTrainingTarget(id, mode, generation), 100L);
            } else {
                trainingSpotlight.clearTarget();
                trainingHint.setText("Элемент сейчас недоступен — перейдите к следующему шагу");
                trainingPrimary.setText("Далее");
                positionTrainingCard(null);
            }
            return;
        }
        RectF bounds = targetBounds(target, activity.dp(6));
        boolean interactive = "tap".equals(mode);
        String targetLabel = AppLanguage.text(
            activity,
            interactive ? "Нажмите здесь" : "finish".equals(mode) ? "Это меню" : "Обратите внимание");
        trainingSpotlight.showTarget(bounds, interactive, targetLabel, target::performClick);
        positionTrainingCard(bounds);
        trainingSpotlight.bringToFront();
        trainingCard.bringToFront();
    }

    private RectF targetBounds(View target, int inset) {
        int[] stageLocation = new int[2];
        int[] targetLocation = new int[2];
        activity.stage.getLocationOnScreen(stageLocation);
        target.getLocationOnScreen(targetLocation);
        return new RectF(
            targetLocation[0] - stageLocation[0] - inset,
            targetLocation[1] - stageLocation[1] - inset,
            targetLocation[0] - stageLocation[0] + target.getWidth() + inset,
            targetLocation[1] - stageLocation[1] + target.getHeight() + inset);
    }

    private void positionTrainingCard(RectF target) {
        if (trainingCard == null || activity.stage == null) return;
        int width = Math.max(activity.dp(280), activity.stage.getWidth() - activity.dp(24));
        trainingCard.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int cardHeight = trainingCard.getMeasuredHeight();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        params.leftMargin = activity.dp(12);
        params.rightMargin = activity.dp(12);
        if (target == null || target.isEmpty()) {
            params.topMargin = Math.max(activity.dp(12), (activity.stage.getHeight() - cardHeight) / 2);
        } else if (target.centerY() > activity.stage.getHeight() / 2f) {
            params.topMargin = activity.dp(12);
        } else {
            params.topMargin = Math.max(activity.dp(12), activity.stage.getHeight() - cardHeight - activity.dp(86));
        }
        trainingCard.setLayoutParams(params);
    }

    private void stopTraining(boolean completed) {
        if (trainingStep < 0 && trainingSpotlight == null && trainingCard == null) return;
        trainingStep = -1;
        trainingGeneration++;
        trainingStepConfirmed = false;
        activity.resetTransientCanvasModes(false);
        if (trainingSpotlight != null) {
            if (trainingSpotlight.getParent() == activity.stage) activity.stage.removeView(trainingSpotlight);
            trainingSpotlight = null;
        }
        if (trainingCard != null) {
            if (trainingCard.getParent() == activity.stage) activity.stage.removeView(trainingCard);
            trainingCard = null;
        }
        trainingProgress = null;
        trainingTitle = null;
        trainingDetail = null;
        trainingHint = null;
        trainingBack = null;
        trainingPrimary = null;
        activity.showPanel("");
        if (completed) {
            activity.state.onboardingCompleted = true;
            activity.saveOnly();
            activity.toast("Готово — вы освоили основные действия");
        }
    }

    boolean handleTrainingBack() {
        if (trainingStep < 0) return false;
        if (trainingStep > 0) showTrainingStep(trainingStep - 1);
        else stopTraining(false);
        return true;
    }

    void close() {
        stopTraining(false);
    }

    static String normalizeTheme(String value) {
        if ("print".equals(value)) return "clean";
        if ("dark".equals(value) || "clean".equals(value)) return value;
        return "light";
    }

    void refreshSettingsIfVisible() {
        if (activity.settingsContent == null) return;
        activity.settingsContent.removeAllViews();
        activity.addSettingsContent(activity.settingsContent);
    }

    void refreshGuidePanelIfVisible() {
        if (activity.guidePanel == null || !"guides".equals(activity.activePanel)) return;
        activity.guidePanel.removeAllViews();
        LinearLayout fresh = activity.buildGuidePanel();
        while (fresh.getChildCount() > 0) {
            View child = fresh.getChildAt(0);
            fresh.removeViewAt(0);
            activity.guidePanel.addView(child);
        }
    }
}
