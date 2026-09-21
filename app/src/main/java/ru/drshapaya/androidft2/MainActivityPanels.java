package ru.drshapaya.androidft2;

import android.view.View;

final class MainActivityPanels {
    private final MainActivity activity;

    MainActivityPanels(MainActivity activity) {
        this.activity = activity;
    }

    void showPanel(String panel) {
        activity.activePanel = panel == null ? "" : panel;
        activity.setHeaderActionsDimmed(
            "more".equals(activity.activePanel)
                && !(activity.isLandscapeLayout() && activity.focusTree));
        boolean landscapePeople = activity.isLandscapeLayout()
            && "people".equals(activity.activePanel);
        boolean fullScreenTab = "people".equals(activity.activePanel)
            || (!activity.isLandscapeLayout() && "settings".equals(activity.activePanel));
        activity.updateLandscapePanelHostVisibility();
        if (activity.landscapeFullPanelHost != null) {
            activity.landscapeFullPanelHost.setVisibility(
                landscapePeople ? View.VISIBLE : View.GONE);
        }
        if (activity.stage != null) {
            activity.stage.setVisibility(landscapePeople ? View.GONE : View.VISIBLE);
        }
        if (activity.appHeader != null) {
            activity.appHeader.setVisibility(fullScreenTab ? View.GONE : View.VISIBLE);
        }
        if (activity.actionOverlay != null) {
            activity.actionOverlay.setVisibility(fullScreenTab ? View.GONE : View.VISIBLE);
        }
        if (activity.treeView != null) activity.treeView.setVisibility(fullScreenTab ? View.GONE : View.VISIBLE);
        if (activity.zoomRail != null) activity.zoomRail.setVisibility(fullScreenTab ? View.GONE : View.VISIBLE);
        activity.updateAddPersonButtonVisibility();
        activity.refreshLockUi();
        if ("guides".equals(activity.activePanel)) activity.refreshGuidePanelIfVisible();
        activity.setAdaptivePanelVisibility(activity.cardPanel, "card".equals(activity.activePanel));
        activity.setAdaptivePanelVisibility(activity.linksPanel, "links".equals(activity.activePanel));
        activity.setAdaptivePanelVisibility(activity.guidePanel, "guides".equals(activity.activePanel));
        activity.setAdaptivePanelVisibility(activity.filesPanel, "files".equals(activity.activePanel));
        activity.setAdaptivePanelVisibility(activity.viewPanel, "view".equals(activity.activePanel));
        if (activity.landscapeTreePanelCloseButton != null) {
            activity.landscapeTreePanelCloseButton.setVisibility(
                "view".equals(activity.activePanel) ? View.VISIBLE : View.GONE);
        }
        activity.setAdaptivePanelVisibility(activity.branchPanel, "branch".equals(activity.activePanel));
        activity.setAdaptivePanelVisibility(activity.settingsPanel, "settings".equals(activity.activePanel));
        if (activity.peoplePanel != null) {
            activity.setAdaptivePanelVisibility(activity.peoplePanel, "people".equals(activity.activePanel));
            if ("people".equals(activity.activePanel) && activity.peopleModule != null) activity.peopleModule.refresh();
        }
        if (activity.morePanel != null) {
            boolean moreVisible = "more".equals(activity.activePanel);
            activity.setAdaptivePanelVisibility(activity.morePanel, moreVisible);
            if (moreVisible) activity.bringAdaptivePanelToFront(activity.morePanel);
        }
        activity.styleNav(activity.treeNav, "view".equals(activity.activePanel)
            || "branch".equals(activity.activePanel)
            || "guides".equals(activity.activePanel));
        activity.styleNav(activity.cardNav, "card".equals(activity.activePanel));
        activity.styleNav(activity.peopleNav, "people".equals(activity.activePanel));
        activity.stylePeopleNav("people".equals(activity.activePanel));
        activity.styleNav(activity.linksNav, "links".equals(activity.activePanel));
        activity.styleNav(activity.moreNav, "more".equals(activity.activePanel)
            || "files".equals(activity.activePanel)
            || "settings".equals(activity.activePanel));
        activity.applyFocusTreeUi();
        activity.updateHistoryPanel();
        activity.updateBranchStatusPanel();
        activity.updateSelectionToolbar();
        activity.updateCanvasModePanel();
        activity.updateDistantCardsWarning();
    }

    void togglePanel(String panel) {
        if (panel != null && panel.equals(activity.activePanel)) {
            showPanel("");
        } else {
            showPanel(panel);
        }
    }
}
