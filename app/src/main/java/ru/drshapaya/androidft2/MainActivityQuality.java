package ru.drshapaya.androidft2;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns quality-report state, dialogs and issue navigation. */
final class MainActivityQuality {
    private final Activity activity;
    private final MainUiKit ui;
    private final Supplier<TreeState> stateProvider;
    private final Supplier<TreeCanvasView> treeViewProvider;
    private final Supplier<Button> qualityButtonProvider;
    private final Consumer<String> selectPerson;
    private final Consumer<String> panels;
    private final Consumer<String> messages;
    private final Runnable editPerson;

    private TreeQualityAnalyzer.TreeReport report =
        new TreeQualityAnalyzer.TreeReport();

    MainActivityQuality(
        Activity activity,
        MainUiKit ui,
        Supplier<TreeState> stateProvider,
        Supplier<TreeCanvasView> treeViewProvider,
        Supplier<Button> qualityButtonProvider,
        Consumer<String> selectPerson,
        Consumer<String> panels,
        Consumer<String> messages,
        Runnable editPerson
    ) {
        this.activity = activity;
        this.ui = ui;
        this.stateProvider = stateProvider;
        this.treeViewProvider = treeViewProvider;
        this.qualityButtonProvider = qualityButtonProvider;
        this.selectPerson = selectPerson;
        this.panels = panels;
        this.messages = messages;
        this.editPerson = editPerson;
    }

    TreeQualityAnalyzer.PersonReport person(String personId) {
        return report.person(personId);
    }

    private TreeState state() {
        return stateProvider.get();
    }

    private TreeCanvasView treeView() {
        return treeViewProvider.get();
    }

    private Button qualityButton() {
        return qualityButtonProvider.get();
    }

    String personSummary(TreeQualityAnalyzer.PersonReport report) {
        if (report == null) return "Заполнено 0%";
        if (report.errors() > 0) {
            String value = "Заполнено " + report.completeness + "% · " + report.errors()
                + " " + RussianWordForms.forCount(
                    report.errors(), "ошибка", "ошибки", "ошибок");
            if (report.warnings() > 0) value += " · " + report.warnings() + " предупр.";
            return value;
        }
        if (report.warnings() > 0) {
            return "Заполнено " + report.completeness + "% · " + report.warnings()
                + " " + RussianWordForms.forCount(
                    report.warnings(),
                    "предупреждение",
                    "предупреждения",
                    "предупреждений");
        }
        if (report.recommendations() > 0) {
            return "Заполнено " + report.completeness + "% · " + report.recommendations()
                + " " + RussianWordForms.forCount(
                    report.recommendations(),
                    "рекомендация",
                    "рекомендации",
                    "рекомендаций");
        }
        return "Заполнено " + report.completeness + "% · всё хорошо";
    }

    int summaryColor(TreeQualityAnalyzer.PersonReport report) {
        if (report != null && report.errors() > 0) return Color.rgb(197, 83, 75);
        if (report != null && report.warnings() > 0) return Color.rgb(184, 128, 24);
        if (report != null && report.recommendations() > 0) return Color.rgb(101, 113, 122);
        return Color.rgb(8, 122, 115);
    }

    private void updateTreeQualityButton() {
        if (qualityButton() == null) return;
        int score = report == null ? 0 : report.score;
        qualityButton().setText(score + "%");
        int color = score >= 80
            ? AppThemePalette.secondary()
            : score >= 55 ? Color.rgb(184, 128, 24) : Color.rgb(197, 83, 75);
        qualityButton().setTextColor(color);
        ui.tintDrawables(qualityButton(), color);
        qualityButton().setBackground(ui.softAccentGradientBg(ui.dp(10)));
    }

    void refresh() {
        report = TreeQualityAnalyzer.analyze(state());
        if (treeView() != null) treeView().setQualityReports(report.people);
        updateTreeQualityButton();
    }

    void showTreeDialog() {
        report = TreeQualityAnalyzer.analyze(state());
        showQualityDialog(
            "Оценка здоровья дерева",
            state().people.size() + " карточек · " + state().links.size() + " связей",
            report.score,
            report.issues,
            "");
    }

    void showPersonDialog(Person person, String category) {
        if (person == null) return;
        report = TreeQualityAnalyzer.analyze(state());
        TreeQualityAnalyzer.PersonReport personReport = report.person(person.id);
        java.util.List<TreeQualityAnalyzer.Issue> filtered = new java.util.ArrayList<>();
        for (TreeQualityAnalyzer.Issue issue : personReport.issues) {
            if (category == null || category.isEmpty() || category.equals(issue.category)) filtered.add(issue);
        }
        showQualityDialog(
            "Проверка карточки",
            person.name == null || person.name.trim().isEmpty() ? "Без имени" : person.name.trim(),
            personReport.completeness,
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
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        boolean treeReport = personId == null || personId.isEmpty();
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(16));
        shell.setBackground(treeReport
            ? ui.softAccentGradientBg(ui.dp(18))
            : ui.panelBg(Color.rgb(250, 252, 253), ui.dp(18), Color.argb(56, 63, 82, 94)));
        scroll.addView(shell, new ScrollView.LayoutParams(-1, -2));

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heading = new LinearLayout(activity);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView title = ui.actionTitle(titleValue, false);
        title.setTextSize(19);
        heading.addView(title, new LinearLayout.LayoutParams(-1, ui.dp(30)));
        TextView subtitle = ui.actionDetail(subtitleValue, false);
        subtitle.setTextSize(11);
        heading.addView(subtitle, new LinearLayout.LayoutParams(-1, ui.dp(22)));
        top.addView(heading, new LinearLayout.LayoutParams(0, ui.dp(52), 1));
        top.addView(ui.closeButton(v -> dialog.dismiss()), new LinearLayout.LayoutParams(ui.dp(42), ui.dp(42)));
        shell.addView(top);

        if (treeReport) shell.addView(qualityGenderSummary(), qualityGenderParams());
        shell.addView(qualityScoreCard(score, treeReport), qualityBlockParams());
        LinearLayout metrics = new LinearLayout(activity);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        int errors = 0;
        int warnings = 0;
        int recommendations = 0;
        for (TreeQualityAnalyzer.Issue issue : issues) {
            if (issue.severity == TreeQualityAnalyzer.ERROR) errors++;
            else if (issue.severity == TreeQualityAnalyzer.WARNING) warnings++;
            else recommendations++;
        }
        metrics.addView(qualityMetric("Ошибки", errors, Color.rgb(197, 83, 75)), new LinearLayout.LayoutParams(0, ui.dp(58), 1));
        LinearLayout.LayoutParams warningParams = new LinearLayout.LayoutParams(0, ui.dp(58), 1);
        warningParams.setMargins(ui.dp(7), 0, 0, 0);
        metrics.addView(qualityMetric("Предупреждения", warnings, Color.rgb(184, 128, 24)), warningParams);
        LinearLayout.LayoutParams recommendationParams = new LinearLayout.LayoutParams(0, ui.dp(58), 1);
        recommendationParams.setMargins(ui.dp(7), 0, 0, 0);
        metrics.addView(qualityMetric("Советы", recommendations, Color.rgb(101, 113, 122)), recommendationParams);
        shell.addView(metrics, qualityBlockParams());

        TextView section = ui.sectionLabel(issues.isEmpty() ? "РЕЗУЛЬТАТ" : "ЧТО НУЖНО ПРОВЕРИТЬ");
        shell.addView(section, new LinearLayout.LayoutParams(-1, ui.dp(34)));
        if (issues.isEmpty()) {
            TextView empty = ui.actionDetail("Замечаний не найдено. Данные выглядят согласованными.", false);
            empty.setTextSize(12);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(ui.dp(12), ui.dp(14), ui.dp(12), ui.dp(14));
            empty.setBackground(ui.panelBg(Color.rgb(232, 248, 246), ui.dp(12), Color.argb(72, 24, 169, 153)));
            shell.addView(empty, new LinearLayout.LayoutParams(-1, ui.dp(72)));
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
            int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels - ui.dp(24), ui.dp(540));
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = Math.min(activity.getResources().getDisplayMetrics().heightPixels - ui.dp(54), ui.dp(760));
            attrs.dimAmount = 0.34f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
        dialog.show();
    }

    private View qualityScoreCard(int score, boolean treeReport) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int color = score >= 80
            ? AppThemePalette.secondary()
            : score >= 55 ? Color.rgb(184, 128, 24) : Color.rgb(197, 83, 75);
        card.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10));
        card.setBackground(treeReport || score >= 80
            ? ui.softAccentGradientBg(ui.dp(12))
            : ui.panelBg(Color.WHITE, ui.dp(12), Color.argb(86, Color.red(color), Color.green(color), Color.blue(color))));
        TextView scoreView = new LocalizedTextView(activity);
        LocalizedViews.setRaw(scoreView, score + "%");
        scoreView.setTextColor(color);
        scoreView.setTextSize(26);
        scoreView.setTypeface(ui.uiBold());
        scoreView.setGravity(Gravity.CENTER);
        card.addView(scoreView, new LinearLayout.LayoutParams(ui.dp(82), -1));
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = ui.actionTitle(
            score >= 80 ? "Хорошее состояние" : score >= 55 ? "Нужно проверить" : "Требует внимания",
            false);
        if (score >= 80) title.setTextColor(AppThemePalette.secondary());
        copy.addView(title, new LinearLayout.LayoutParams(-1, ui.dp(25)));
        TextView detail = ui.actionDetail("Оценка учитывает заполненность, даты и семейные связи.", false);
        detail.setTextSize(9);
        detail.setMaxLines(2);
        copy.addView(detail, new LinearLayout.LayoutParams(-1, ui.dp(34)));
        card.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        return card;
    }

    private View qualityMetric(String label, int value, int color) {
        LinearLayout metric = new LinearLayout(activity);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setGravity(Gravity.CENTER);
        metric.setBackground(ui.panelBg(Color.WHITE, ui.dp(10), Color.argb(64, Color.red(color), Color.green(color), Color.blue(color))));
        TextView number = new LocalizedTextView(activity);
        LocalizedViews.setRaw(number, String.valueOf(value));
        number.setTextColor(color);
        number.setTextSize(18);
        number.setTypeface(ui.uiBold());
        number.setGravity(Gravity.CENTER);
        metric.addView(number, new LinearLayout.LayoutParams(-1, ui.dp(30)));
        TextView caption = ui.actionDetail(label, false);
        caption.setTextSize(8);
        caption.setGravity(Gravity.CENTER);
        metric.addView(caption, new LinearLayout.LayoutParams(-1, ui.dp(20)));
        return metric;
    }

    private View qualityGenderSummary() {
        int male = 0;
        int female = 0;
        int unknown = 0;
        if (state() != null) {
            for (Person person : state().people.values()) {
                String gender = PersonGender.resolve(person);
                if (PersonGender.MALE.equals(gender)) male++;
                else if (PersonGender.FEMALE.equals(gender)) female++;
                else unknown++;
            }
        }
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(8), ui.dp(7), ui.dp(8), ui.dp(7));
        row.setBackground(ui.panelBg(Color.WHITE, ui.dp(12), Color.rgb(217, 224, 229)));
        row.addView(qualityGenderMetric("М", "Мужчины", male, Color.rgb(47, 125, 185)), new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams femaleParams = new LinearLayout.LayoutParams(0, -1, 1);
        femaleParams.setMargins(ui.dp(7), 0, 0, 0);
        row.addView(qualityGenderMetric("Ж", "Женщины", female, Color.rgb(185, 83, 130)), femaleParams);
        if (unknown > 0) {
            LinearLayout.LayoutParams unknownParams = new LinearLayout.LayoutParams(0, -1, 1);
            unknownParams.setMargins(ui.dp(7), 0, 0, 0);
            row.addView(qualityGenderMetric("?", "Не указан", unknown, Color.rgb(101, 113, 122)), unknownParams);
        }
        return row;
    }

    private View qualityGenderMetric(String mark, String label, int value, int color) {
        LinearLayout metric = new LinearLayout(activity);
        metric.setOrientation(LinearLayout.HORIZONTAL);
        metric.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = new LocalizedTextView(activity);
        LocalizedViews.setRaw(badge, mark);
        badge.setTextSize(12);
        badge.setTypeface(ui.uiBold());
        badge.setTextColor(Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(ui.panelBg(color, ui.dp(999), Color.TRANSPARENT));
        metric.addView(badge, new LinearLayout.LayoutParams(ui.dp(30), ui.dp(30)));
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(ui.dp(7), 0, 0, 0);
        TextView count = new LocalizedTextView(activity);
        LocalizedViews.setRaw(count, String.valueOf(value));
        count.setTextSize(14);
        count.setTypeface(ui.uiBold());
        count.setTextColor(color);
        count.setIncludeFontPadding(false);
        copy.addView(count, new LinearLayout.LayoutParams(-1, ui.dp(21)));
        TextView caption = ui.actionDetail(label, false);
        caption.setTextSize(8);
        copy.addView(caption, new LinearLayout.LayoutParams(-1, ui.dp(17)));
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
        LinearLayout group = new LinearLayout(activity);
        group.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(ui.dp(10), 0, ui.dp(10), 0);
        String countLabel = AppLanguage.isEnglish(activity)
            ? cardCount + (cardCount == 1 ? " card" : " cards")
            : cardCount + " " + RussianWordForms.forCount(
                cardCount, "карточка", "карточки", "карточек");
        int color = severity == TreeQualityAnalyzer.ERROR
            ? Color.rgb(197, 83, 75)
            : severity == TreeQualityAnalyzer.WARNING
                ? Color.rgb(184, 128, 24)
                : Color.rgb(101, 113, 122);
        header.setBackground(ui.panelBg(
            severity == TreeQualityAnalyzer.ERROR
                ? Color.rgb(255, 244, 241)
                : severity == TreeQualityAnalyzer.WARNING
                    ? Color.rgb(255, 248, 226)
                    : Color.rgb(243, 246, 248),
            ui.dp(9),
            Color.argb(76, Color.red(color), Color.green(color), Color.blue(color))));

        TextView arrow = new LocalizedTextView(activity);
        LocalizedViews.setRaw(arrow, "›");
        arrow.setTextColor(color);
        arrow.setTextSize(22);
        arrow.setTypeface(ui.uiBold());
        arrow.setGravity(Gravity.CENTER);
        arrow.setIncludeFontPadding(false);
        header.addView(arrow, new LinearLayout.LayoutParams(ui.dp(28), -1));

        TextView title = new LocalizedTextView(activity);
        LocalizedViews.setRaw(title, AppLanguage.translate(activity, problem));
        title.setTextColor(color);
        title.setTextSize(12);
        title.setTypeface(ui.uiBold());
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        TextView count = new LocalizedTextView(activity);
        LocalizedViews.setRaw(count, countLabel);
        count.setTextColor(color);
        count.setTextSize(9);
        count.setTypeface(ui.uiBold());
        count.setGravity(Gravity.CENTER);
        count.setPadding(ui.dp(9), 0, ui.dp(9), 0);
        count.setBackground(ui.panelBg(Color.WHITE, ui.dp(999), Color.argb(72, Color.red(color), Color.green(color), Color.blue(color))));
        header.addView(count, new LinearLayout.LayoutParams(-2, ui.dp(30)));
        group.addView(header, new LinearLayout.LayoutParams(-1, ui.dp(48)));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(ui.dp(8), ui.dp(8), 0, 0);
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
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int color = issue.severity == TreeQualityAnalyzer.ERROR
            ? Color.rgb(197, 83, 75)
            : issue.severity == TreeQualityAnalyzer.WARNING
                ? Color.rgb(184, 128, 24)
                : Color.rgb(101, 113, 122);
        row.setPadding(ui.dp(10), ui.dp(7), ui.dp(8), ui.dp(7));
        row.setBackground(ui.panelBg(Color.WHITE, ui.dp(10), Color.argb(70, Color.red(color), Color.green(color), Color.blue(color))));
        TextView marker = new LocalizedTextView(activity);
        marker.setText(issue.severity == TreeQualityAnalyzer.RECOMMENDATION ? "i" : "!");
        marker.setTextColor(Color.WHITE);
        marker.setTextSize(13);
        marker.setTypeface(ui.uiBold());
        marker.setGravity(Gravity.CENTER);
        marker.setBackground(ui.panelBg(color, ui.dp(999), Color.TRANSPARENT));
        row.addView(marker, new LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)));
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(ui.dp(9), 0, ui.dp(7), 0);
        Person issuePerson = state() == null ? null : state().people.get(issue.personId);
        String titleValue = showPersonName
            ? issuePerson == null || issuePerson.name == null || issuePerson.name.trim().isEmpty()
                ? "Без имени"
                : issuePerson.name.trim()
            : issue.title;
        TextView title = ui.actionTitle(titleValue, false);
        title.setTextSize(11);
        copy.addView(title, new LinearLayout.LayoutParams(-1, ui.dp(23)));
        TextView detail = ui.actionDetail(issue.detail, false);
        detail.setTextSize(8);
        detail.setMaxLines(2);
        copy.addView(detail, new LinearLayout.LayoutParams(-1, ui.dp(34)));
        row.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        Button fix = ui.actionButton("Исправить", v -> {
            dialog.dismiss();
            quickFixQualityIssue(issue);
        });
        fix.setTextSize(9);
        fix.setTextColor(color);
        fix.setBackground(ui.panelBg(Color.WHITE, ui.dp(8), Color.argb(90, Color.red(color), Color.green(color), Color.blue(color))));
        row.addView(fix, new LinearLayout.LayoutParams(ui.dp(78), ui.dp(38)));
        return row;
    }

    private void quickFixQualityIssue(TreeQualityAnalyzer.Issue issue) {
        if (issue == null || state() == null || !state().people.containsKey(issue.personId)) return;
        selectPerson.accept(issue.personId);
        if (TreeQualityAnalyzer.CATEGORY_RELATIONS.equals(issue.category)
            || TreeQualityAnalyzer.CATEGORY_STRUCTURE.equals(issue.category)) {
            panels.accept("links");
            messages.accept("Выберите инструмент для исправления связи");
        } else if (TreeQualityAnalyzer.CATEGORY_DUPLICATES.equals(issue.category)) {
            panels.accept("people");
            messages.accept("Проверьте похожие карточки в каталоге");
        } else {
            editPerson.run();
        }
    }

    private LinearLayout.LayoutParams qualityBlockParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, ui.dp(80));
        params.setMargins(0, ui.dp(12), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams qualityGenderParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, ui.dp(62));
        params.setMargins(0, ui.dp(10), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams qualityIssueParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, ui.dp(76));
        params.setMargins(0, 0, 0, ui.dp(8));
        return params;
    }

    private LinearLayout.LayoutParams qualityProblemHeaderParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, ui.dp(7), 0, ui.dp(7));
        return params;
    }
}
