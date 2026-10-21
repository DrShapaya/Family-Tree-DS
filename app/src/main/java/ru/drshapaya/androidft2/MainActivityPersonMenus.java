package ru.drshapaya.androidft2;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

/** Selected-person context menu and its quality disclosure. */
final class MainActivityPersonMenus {
    private final MainActivity activity;
    private final PersonMenuViews views;
    private PopupWindow activePopup;

    MainActivityPersonMenus(MainActivity activity) {
        this.activity = activity;
        this.views = new PersonMenuViews(activity);
    }

    void openPersonActions(Person person, float screenX, float screenY) {
        if (person == null) return;
        if (activePopup != null) activePopup.dismiss();
        activity.refreshTreeQuality();
        final int popupWidth = Math.min(activity.dp(324), activity.getResources().getDisplayMetrics().widthPixels - activity.dp(24));
        LinearLayout menu = new LinearLayout(activity);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(activity.dp(9), activity.dp(9), activity.dp(9), activity.dp(9));
        menu.setBackground(activity.panelBg(Color.rgb(248, 251, 252), activity.dp(12), Color.argb(51, 93, 85, 72)));
        menu.setElevation(activity.dp(12));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(menu, new ScrollView.LayoutParams(-1, -2));
        PopupWindow popup = new PopupWindow(scroll, popupWidth, -2, true);
        activePopup = popup;
        popup.setOnDismissListener(() -> activePopup = null);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(activity.dp(12));
        popup.setAnimationStyle(0);

        TreeQualityAnalyzer.PersonReport report = activity.qualityModule == null
            ? new TreeQualityAnalyzer.PersonReport(person.id)
            : activity.qualityModule.person(person.id);
        menu.addView(selectedPersonMenuHeader(person, report), new LinearLayout.LayoutParams(-1, activity.dp(86)));
        menu.addView(views.personMenuSpaced(
            views.personMenuAction(
                R.drawable.ic_nav_card,
                "Открыть карточку",
                false,
                popup,
                activity::openPersonEditor),
            activity.dp(7),
            activity.dp(7)));

        menu.addView(views.personMenuSection("ПОКАЗАТЬ ВЕТКУ"), new LinearLayout.LayoutParams(-1, activity.dp(30)));
        Person rootPerson = activity.state.people.get(activity.state.rootId);
        KinshipCalculator.Result kinship = rootPerson == null
            ? null
            : activity.calculateKinship(person.id, rootPerson.id);
        String kinshipDetail = person.id.equals(activity.state.rootId)
            ? "Это ваша карточка"
            : kinship != null && kinship.found
                ? "Для вас: " + kinship.firstToSecond
                : "Родство пока не найдено";
        menu.addView(views.personMenuGridRow(
            views.personMenuGridTile(R.drawable.ic_menu_ancestors, "Предки", "", false, popup, () -> activity.showPersonBranch(person, "ancestors")),
            views.personMenuGridTile(R.drawable.ic_menu_descendants, "Потомки", "", false, popup, () -> activity.showPersonBranch(person, "descendants"))));
        menu.addView(views.personMenuGridRow(
            views.personMenuGridTile(R.drawable.ic_menu_near, "Близкие", "", false, popup, () -> activity.showPersonBranch(person, "near")),
            views.personMenuGridTile(R.drawable.ic_menu_route, "Связь со мной", kinshipDetail, false, popup, () -> activity.showKinshipToRoot(person))));

        menu.addView(personQualityDisclosure(person, report, popup));

        menu.addView(views.personMenuSection("ДЕЙСТВИЯ"), new LinearLayout.LayoutParams(-1, activity.dp(30)));
        menu.addView(views.personMenuGridRow(
            views.personMenuGridTile(
                R.drawable.ic_menu_pin,
                person.pinned ? "Открепить" : "Закрепить",
                "",
                false,
                popup,
                () -> activity.togglePersonPin(person)),
            views.personMenuGridTile(R.drawable.ic_menu_copy, "Дублировать", "", false, popup, activity::duplicateSelected)));
        menu.addView(views.personMenuAction(R.drawable.ic_menu_share, "Поделиться биографией", false, popup, () -> activity.shareBiography(person)));
        menu.addView(views.personMenuAction(R.drawable.ic_menu_trash, "Удалить человека", true, popup, activity::confirmDelete));

        menu.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int popupHeight = Math.min(menu.getMeasuredHeight(), screenHeight - activity.dp(32));
        popup.setHeight(popupHeight);
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int x = Math.max(activity.dp(8), Math.min(screenWidth - popupWidth - activity.dp(8), Math.round(screenX) - popupWidth + activity.dp(18)));
        int y = Math.round(screenY) + activity.dp(8);
        if (y + popupHeight > screenHeight - activity.dp(16)) y = Math.max(activity.dp(16), Math.round(screenY) - popupHeight - activity.dp(8));
        PanelTransitions.popup(menu, y < screenY);
        popup.showAtLocation(activity.treeView, Gravity.TOP | Gravity.LEFT, x, y);
    }

    private View selectedPersonMenuHeader(Person person, TreeQualityAnalyzer.PersonReport report) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8));
        header.setBackground(activity.softAccentGradientBg(activity.dp(10)));
        TextView eyebrow = new LocalizedTextView(activity);
        eyebrow.setText("Выбрана карточка");
        eyebrow.setTextColor(AppThemePalette.secondary());
        eyebrow.setTextSize(9);
        eyebrow.setTypeface(activity.uiBold());
        eyebrow.setIncludeFontPadding(false);
        header.addView(eyebrow, new LinearLayout.LayoutParams(-1, activity.dp(18)));
        TextView name = activity.cardActionTitle(
            person.name == null || person.name.trim().isEmpty() ? "Без имени" : person.name.trim(),
            false);
        name.setTextSize(15);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(name, new LinearLayout.LayoutParams(-1, activity.dp(27)));
        TextView quality = activity.cardActionDetail(activity.personQualitySummary(report), false);
        quality.setTextSize(10);
        quality.setTextColor(activity.qualitySummaryColor(report));
        header.addView(quality, new LinearLayout.LayoutParams(-1, activity.dp(22)));
        return header;
    }

    private View personQualityDisclosure(Person person, TreeQualityAnalyzer.PersonReport report, PopupWindow popup) {
        LinearLayout group = new LinearLayout(activity);
        group.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(-1, -2);
        groupParams.setMargins(0, 0, 0, activity.dp(7));
        group.setLayoutParams(groupParams);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(activity.dp(10), activity.dp(6), activity.dp(8), activity.dp(6));
        header.setBackground(activity.softAccentGradientBg(activity.dp(9)));

        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_menu_shield);
        icon.setColorFilter(activity.uiColor(AppThemePalette.secondary()));
        header.addView(icon, new LinearLayout.LayoutParams(activity.dp(24), activity.dp(24)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(activity.dp(9), 0, 0, 0);
        TextView title = activity.cardActionTitle("Проверить данные", false);
        title.setTextColor(AppThemePalette.secondary());
        title.setTextSize(12);
        copy.addView(title, new LinearLayout.LayoutParams(-1, activity.dp(23)));
        TextView detail = activity.cardActionDetail(activity.personQualitySummary(report), false);
        detail.setSingleLine(true);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(detail, new LinearLayout.LayoutParams(-1, activity.dp(18)));
        header.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));

        DisclosureChevron arrow = new DisclosureChevron(activity, AppThemePalette.secondary());
        header.addView(arrow, new LinearLayout.LayoutParams(activity.dp(30), -1));

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, activity.dp(7), 0, 0);
        content.setVisibility(View.GONE);
        content.addView(views.personMenuGridRow(
            personQualityGridTile(person, report, TreeQualityAnalyzer.CATEGORY_PARENT_AGE, "Возраст родителей", R.drawable.ic_menu_people, popup),
            personQualityGridTile(person, report, TreeQualityAnalyzer.CATEGORY_DATES, "Даты", R.drawable.ic_field_calendar, popup)));
        content.addView(views.personMenuGridRow(
            personQualityGridTile(person, report, TreeQualityAnalyzer.CATEGORY_RELATIONS, "Связи", R.drawable.ic_nav_links, popup),
            personQualityGridTile(person, report, TreeQualityAnalyzer.CATEGORY_DUPLICATES, "Дубли", R.drawable.ic_menu_copy, popup)));
        content.addView(views.personMenuGridRow(
            personQualityGridTile(person, report, TreeQualityAnalyzer.CATEGORY_STRUCTURE, "Ветки", R.drawable.ic_menu_branch, popup),
            personQualityGridTile(person, report, TreeQualityAnalyzer.CATEGORY_MEMORY, "Архив", R.drawable.ic_editor_archive, popup)));
        content.addView(views.personMenuGridRow(
            personQualityGridTile(person, report, TreeQualityAnalyzer.CATEGORY_MISSING, "Сведения", R.drawable.ic_menu_note_add, popup),
            views.personMenuGridSpacer()));

        final boolean[] expanded = {false};
        header.setOnClickListener(v -> {
            expanded[0] = !expanded[0];
            content.setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
            arrow.expanded(expanded[0]);
            if (expanded[0]) PanelTransitions.enter(content, false, null);
            views.updatePersonPopupHeight(popup);
        });

        group.addView(header, new LinearLayout.LayoutParams(-1, activity.dp(54)));
        group.addView(content, new LinearLayout.LayoutParams(-1, -2));
        return group;
    }

    private View personQualityGridTile(
        Person person,
        TreeQualityAnalyzer.PersonReport report,
        String category,
        String label,
        int iconRes,
        PopupWindow popup
    ) {
        int count = report == null ? 0 : report.countCategory(category);
        String detail = count == 0
            ? "Чисто"
            : count + " " + RussianWordForms.forCount(
                count, "пункт", "пункта", "пунктов");
        return views.personMenuGridTile(iconRes, label, detail, false, popup, () -> activity.showPersonQualityDialog(person, category));
    }

}
