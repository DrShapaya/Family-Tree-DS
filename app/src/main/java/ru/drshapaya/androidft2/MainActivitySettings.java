package ru.drshapaya.androidft2;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

final class MainActivitySettings {
    private final MainActivity activity;
    private int trainingStep = -1;
    private View trainingHighlight;
    private LinearLayout trainingCard;
    private TextView trainingProgress;
    private TextView trainingTitle;
    private TextView trainingDetail;

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
        activity.treeView.setEditLocked(activity.editLocked);
        activity.toast(activity.editLocked ? "Редактирование заблокировано" : "Редактирование разрешено");
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
        activity.compactCards = !activity.compactCards;
        activity.treeView.setCompactCards(activity.compactCards);
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
        activity.theme = normalizeTheme(nextTheme);
        activity.treeView.setTheme(activity.theme);
        refreshSettingsIfVisible();
        activity.saveOnly();
        activity.toast("clean".equals(activity.theme) ? "Чистый режим" : "dark".equals(activity.theme) ? "Тёмная тема" : "Светлая тема");
    }

    void startTraining() {
        if (activity.editingBlocked()) {
            activity.toast("Сначала отключите защиту правок");
            return;
        }
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
        String[] step = MainActivity.TRAINING_STEPS[index];
        prepareTrainingStep(step[0]);
        ensureTrainingOverlay();
        trainingProgress.setText("ОБУЧЕНИЕ  ·  ШАГ " + (index + 1) + " ИЗ " + MainActivity.TRAINING_STEPS.length);
        trainingTitle.setText(step[1]);
        trainingDetail.setText(step[2]);
        activity.stage.post(() -> positionTrainingHighlight(step[0]));
    }

    void prepareTrainingStep(String id) {
        if ("add-person".equals(id)) {
            activity.showPanel("");
        } else if ("parent-link".equals(id) && !"links".equals(activity.activePanel)) {
            activity.showPanel("links");
        } else if ("layout-tree".equals(id) && !"view".equals(activity.activePanel)) {
            activity.showPanel("view");
        }
    }

    void onTrainingTargetActivated(String id) {
        if (trainingStep < 0 || trainingStep >= MainActivity.TRAINING_STEPS.length) return;
        String expected = MainActivity.TRAINING_STEPS[trainingStep][0];
        if (!expected.equals(id)) return;
        final int completedStep = trainingStep;
        if ("parent-link".equals(id)) {
            activity.stage.postDelayed(() -> {
                if (trainingStep != completedStep) return;
                activity.resetTransientCanvasModes(false);
                showTrainingStep(completedStep + 1);
            }, 650);
        } else {
            showTrainingStep(completedStep + 1);
        }
    }

    private void ensureTrainingOverlay() {
        if (activity.stage == null) return;
        if (trainingHighlight == null) {
            trainingHighlight = new View(activity);
            trainingHighlight.setClickable(false);
            trainingHighlight.setFocusable(false);
            trainingHighlight.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            GradientDrawable ring = new GradientDrawable();
            ring.setColor(Color.TRANSPARENT);
            ring.setCornerRadius(activity.dp(14));
            ring.setStroke(activity.dp(3), Color.rgb(24, 169, 153));
            trainingHighlight.setBackground(ring);
            trainingHighlight.setElevation(activity.dp(24));
            AlphaAnimation pulse = new AlphaAnimation(0.38f, 1f);
            pulse.setDuration(720);
            pulse.setRepeatCount(AlphaAnimation.INFINITE);
            pulse.setRepeatMode(AlphaAnimation.REVERSE);
            trainingHighlight.startAnimation(pulse);
            activity.stage.addView(trainingHighlight, new FrameLayout.LayoutParams(1, 1));
        }
        if (trainingCard != null) {
            trainingCard.bringToFront();
            return;
        }

        trainingCard = new LinearLayout(activity);
        trainingCard.setOrientation(LinearLayout.VERTICAL);
        trainingCard.setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(10));
        trainingCard.setBackground(activity.panelBg(Color.rgb(252, 254, 254), activity.dp(14), Color.argb(110, 24, 169, 153)));
        trainingCard.setElevation(activity.dp(26));

        trainingProgress = trainingText(10, Color.rgb(8, 122, 115), true);
        trainingCard.addView(trainingProgress, new LinearLayout.LayoutParams(-1, activity.dp(20)));
        trainingTitle = trainingText(17, Color.rgb(28, 34, 38), true);
        trainingCard.addView(trainingTitle, new LinearLayout.LayoutParams(-1, activity.dp(30)));
        trainingDetail = trainingText(12, Color.rgb(76, 87, 96), false);
        trainingDetail.setGravity(Gravity.TOP);
        trainingDetail.setMaxLines(3);
        trainingCard.addView(trainingDetail, new LinearLayout.LayoutParams(-1, activity.dp(50)));

        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView action = trainingText(11, Color.rgb(8, 122, 115), true);
        action.setText("↓  Нажмите подсвеченный элемент");
        footer.addView(action, new LinearLayout.LayoutParams(0, activity.dp(36), 1));
        Button close = activity.actionButton("Закрыть", v -> stopTraining(false));
        close.setTextSize(11);
        close.setElevation(0f);
        footer.addView(close, new LinearLayout.LayoutParams(activity.dp(82), activity.dp(34)));
        trainingCard.addView(footer, new LinearLayout.LayoutParams(-1, activity.dp(36)));

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        cardParams.setMargins(activity.dp(12), activity.dp(10), activity.dp(12), 0);
        activity.stage.addView(trainingCard, cardParams);
        trainingCard.bringToFront();
    }

    private TextView trainingText(int size, int color, boolean bold) {
        TextView text = new TextView(activity);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setTypeface(bold ? activity.uiBold() : activity.ui());
        text.setIncludeFontPadding(false);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }

    private void positionTrainingHighlight(String id) {
        if (trainingStep < 0 || activity.stage == null || trainingHighlight == null) return;
        View target = activity.trainingTarget(id);
        if (target == null || !target.isShown() || target.getWidth() <= 0 || target.getHeight() <= 0) {
            activity.stage.postDelayed(() -> {
                if (trainingStep >= 0 && MainActivity.TRAINING_STEPS[trainingStep][0].equals(id)) {
                    positionTrainingHighlight(id);
                }
            }, 120);
            return;
        }
        int[] stageLocation = new int[2];
        int[] targetLocation = new int[2];
        activity.stage.getLocationOnScreen(stageLocation);
        target.getLocationOnScreen(targetLocation);
        int inset = activity.dp(5);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            target.getWidth() + inset * 2,
            target.getHeight() + inset * 2);
        params.leftMargin = targetLocation[0] - stageLocation[0] - inset;
        params.topMargin = targetLocation[1] - stageLocation[1] - inset;
        trainingHighlight.setLayoutParams(params);
        trainingHighlight.setVisibility(View.VISIBLE);
        trainingHighlight.bringToFront();
        trainingCard.bringToFront();
    }

    private void stopTraining(boolean completed) {
        trainingStep = -1;
        activity.resetTransientCanvasModes(false);
        if (trainingHighlight != null) {
            trainingHighlight.clearAnimation();
            if (trainingHighlight.getParent() == activity.stage) activity.stage.removeView(trainingHighlight);
            trainingHighlight = null;
        }
        if (trainingCard != null) {
            if (trainingCard.getParent() == activity.stage) activity.stage.removeView(trainingCard);
            trainingCard = null;
        }
        trainingProgress = null;
        trainingTitle = null;
        trainingDetail = null;
        if (completed) {
            activity.state.onboardingCompleted = true;
            activity.saveOnly();
            activity.toast("Готово — вы освоили основные действия");
        }
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
