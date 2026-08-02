package ru.drshapaya.androidft2;

import android.view.View;

final class MainActivityPanels {
    private final MainActivity activity;

    MainActivityPanels(MainActivity activity) {
        this.activity = activity;
    }

    void showPanel(String panel) {
        activity.activePanel = panel == null ? "" : panel;
        boolean settingsTab = "settings".equals(activity.activePanel);
        if (activity.appHeader != null) {
            activity.appHeader.setVisibility(settingsTab ? View.GONE : View.VISIBLE);
        }
        if (activity.treeView != null) activity.treeView.setVisibility(settingsTab ? View.GONE : View.VISIBLE);
        if (activity.zoomRail != null) activity.zoomRail.setVisibility(settingsTab ? View.GONE : View.VISIBLE);
        activity.updateAddPersonButtonVisibility();
        if (activity.treeHint != null) {
            activity.treeHint.setVisibility(
                settingsTab || activity.focusTree || activity.treeHintDismissed
                    ? View.GONE
                    : View.VISIBLE);
        }
        if ("guides".equals(activity.activePanel)) activity.refreshGuidePanelIfVisible();
        if (activity.cardPanel != null) activity.cardPanel.setVisibility("card".equals(activity.activePanel) ? View.VISIBLE : View.GONE);
        if (activity.linksPanel != null) activity.linksPanel.setVisibility("links".equals(activity.activePanel) ? View.VISIBLE : View.GONE);
        if (activity.guidePanel != null) activity.guidePanel.setVisibility("guides".equals(activity.activePanel) ? View.VISIBLE : View.GONE);
        if (activity.filesPanel != null) activity.filesPanel.setVisibility("files".equals(activity.activePanel) ? View.VISIBLE : View.GONE);
        if (activity.viewPanel != null) activity.viewPanel.setVisibility("view".equals(activity.activePanel) ? View.VISIBLE : View.GONE);
        if (activity.branchPanel != null) activity.branchPanel.setVisibility("branch".equals(activity.activePanel) ? View.VISIBLE : View.GONE);
        if (activity.settingsPanel != null) activity.settingsPanel.setVisibility("settings".equals(activity.activePanel) ? View.VISIBLE : View.GONE);
        activity.styleNav(activity.treeNav, "view".equals(activity.activePanel)
            || "branch".equals(activity.activePanel)
            || "guides".equals(activity.activePanel));
        activity.styleNav(activity.cardNav, "card".equals(activity.activePanel));
        activity.styleNav(activity.linksNav, "links".equals(activity.activePanel));
        activity.styleNav(activity.filesNav, "files".equals(activity.activePanel));
        activity.styleNav(activity.settingsNav, "settings".equals(activity.activePanel));
        activity.updateHistoryPanel();
        activity.updateBranchStatusPanel();
        activity.updateSelectionToolbar();
        activity.updateCanvasModePanel();
    }

    void togglePanel(String panel) {
        if (panel != null && panel.equals(activity.activePanel)) {
            showPanel("");
        } else {
            showPanel(panel);
        }
    }
}
