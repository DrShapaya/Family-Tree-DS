package ru.drshapaya.androidft2;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Add-person choices and relative-count dialog. */
final class MainActivityAddPersonMenu {
    private final MainActivity activity;
    private final PersonMenuViews views;
    private PopupWindow activePopup;

    MainActivityAddPersonMenu(MainActivity activity) {
        this.activity = activity;
        this.views = new PersonMenuViews(activity);
    }

    void showAddPersonMenu() {
        if (activity.state == null || activity.addPersonButton == null || !activity.requireEditingEnabled()) return;
        if (activePopup != null && activePopup.isShowing()) return;
        Person selected = activity.state.selectedPerson();
        if (selected == null) {
            activity.addLoosePerson();
            return;
        }

        final int popupWidth = Math.min(
            activity.dp(300),
            activity.getResources().getDisplayMetrics().widthPixels - activity.dp(24));
        LinearLayout menu = new LinearLayout(activity);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(activity.dp(9), activity.dp(9), activity.dp(9), activity.dp(9));
        menu.setBackground(activity.panelBg(
            Color.rgb(248, 251, 252),
            activity.dp(14),
            Color.argb(64, 24, 169, 153)));
        menu.setElevation(activity.dp(12));

        PopupWindow popup = new PopupWindow(menu, popupWidth, -2, true);
        activePopup = popup;
        popup.setOnDismissListener(() -> activePopup = null);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(activity.dp(12));
        popup.setAnimationStyle(0);

        menu.addView(
            views.personMenuHeader(selected, "Выбрана карточка"),
            new LinearLayout.LayoutParams(-1, activity.dp(64)));
        menu.addView(
            views.personMenuSection("ДОБАВИТЬ К ВЫБРАННОЙ КАРТОЧКЕ"),
            new LinearLayout.LayoutParams(-1, activity.dp(30)));
        menu.addView(views.personMenuAction(
            R.drawable.ic_menu_add_person,
            "Добавить родителей",
            false,
            popup,
            () -> showRelativeCountDialog(selected, "parents", 2)));
        menu.addView(views.personMenuAction(
            R.drawable.ic_menu_child,
            "Добавить детей",
            false,
            popup,
            () -> showRelativeCountDialog(selected, "children", 4)));
        menu.addView(views.personMenuAction(
            R.drawable.ic_menu_heart,
            "Добавить партнёра",
            false,
            popup,
            () -> requestAddPartner(selected)));
        menu.addView(views.personMenuAction(
            R.drawable.ic_menu_people,
            "Добавить брата/сестру",
            false,
            popup,
            () -> showRelativeCountDialog(selected, "siblings", 3)));
        menu.addView(
            views.personMenuSection("ОТДЕЛЬНАЯ КАРТОЧКА"),
            new LinearLayout.LayoutParams(-1, activity.dp(30)));
        menu.addView(views.personMenuAction(
            R.drawable.ic_menu_add_box,
            "Создать пустую карточку",
            false,
            popup,
            activity::addLoosePerson));
        menu.addView(
            views.personMenuSection("АВТОМАТИЧЕСКОЕ РАЗМЕЩЕНИЕ"),
            new LinearLayout.LayoutParams(-1, activity.dp(30)));
        menu.addView(views.personMenuToggleAction(
            R.drawable.ic_menu_layout,
            "Упорядочивать после добавления",
            activity.state.autoArrangeOnAdd,
            enabled -> {
                activity.state.autoArrangeOnAdd = enabled;
                activity.rememberAutoArrangePreference(enabled);
                activity.saveOnly();
                activity.toast(enabled
                    ? "Полное упорядочивание после добавления включено"
                    : "Автоупорядочивание выключено");
            }));

        menu.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int[] anchor = new int[2];
        activity.addPersonButton.getLocationOnScreen(anchor);
        int popupHeight = menu.getMeasuredHeight();
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int x = Math.max(
            activity.dp(8),
            Math.min(screenWidth - popupWidth - activity.dp(8), anchor[0] + activity.addPersonButton.getWidth() - popupWidth));
        int y = Math.max(activity.dp(12), anchor[1] - popupHeight - activity.dp(8));
        PanelTransitions.popup(menu, true);
        popup.showAtLocation(activity.treeView, Gravity.TOP | Gravity.LEFT, x, y);
    }

    private void showRelativeCountDialog(Person selected, String kind, int maximum) {
        if (selected == null || !activity.state.people.containsKey(selected.id)) return;
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(activity.dp(18), activity.dp(17), activity.dp(18), activity.dp(16));
        shell.setBackground(activity.panelBg(Color.WHITE, activity.dp(20), Color.argb(60, 63, 82, 94)));

        TextView eyebrow = new LocalizedTextView(activity);
        eyebrow.setText("ВЫБРАНА КАРТОЧКА");
        eyebrow.setTextSize(9);
        eyebrow.setTypeface(activity.uiBold());
        eyebrow.setTextColor(Color.rgb(8, 122, 115));
        eyebrow.setIncludeFontPadding(false);
        shell.addView(eyebrow, new LinearLayout.LayoutParams(-1, activity.dp(20)));

        TextView personName = activity.cardActionTitle(
            selected.name == null || selected.name.trim().isEmpty() ? "Без имени" : selected.name.trim(),
            false);
        personName.setTextSize(15);
        personName.setSingleLine(true);
        personName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        shell.addView(personName, new LinearLayout.LayoutParams(-1, activity.dp(30)));

        String relation = "parents".equals(kind)
            ? "родителей"
            : "children".equals(kind) ? "детей" : "братьев или сестёр";
        TextView title = activity.cardActionTitle("Сколько добавить?", false);
        title.setTextSize(18);
        title.setPadding(0, activity.dp(7), 0, 0);
        shell.addView(title, new LinearLayout.LayoutParams(-1, activity.dp(40)));
        TextView detail = activity.cardActionDetail("Выберите количество " + relation, false);
        detail.setTextSize(11);
        shell.addView(detail, new LinearLayout.LayoutParams(-1, activity.dp(26)));

        LinearLayout choices = new LinearLayout(activity);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        choices.setGravity(Gravity.CENTER);
        for (int count = 1; count <= maximum; count++) {
            final int selectedCount = count;
            Button choice = activity.actionButton(String.valueOf(count), v -> {
                dialog.dismiss();
                if (!activity.state.people.containsKey(selected.id)) return;
                activity.state.selectedId = selected.id;
                if ("children".equals(kind)) {
                    requestAddChildren(selected, selectedCount);
                } else {
                    activity.addRelationAction("add-" + kind + "-" + selectedCount);
                }
            });
            choice.setTextSize(18);
            choice.setTextColor(Color.rgb(8, 122, 115));
            choice.setBackground(activity.panelBg(Color.rgb(232, 248, 246), activity.dp(13), Color.argb(88, 24, 169, 153)));
            LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(0, activity.dp(52), 1);
            if (count > 1) choiceParams.setMargins(activity.dp(8), 0, 0, 0);
            choices.addView(choice, choiceParams);
        }
        LinearLayout.LayoutParams choicesParams = new LinearLayout.LayoutParams(-1, activity.dp(52));
        choicesParams.setMargins(0, activity.dp(10), 0, 0);
        shell.addView(choices, choicesParams);

        Button cancel = activity.actionButton("Отмена", v -> dialog.dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, activity.dp(46));
        cancelParams.setMargins(0, activity.dp(10), 0, 0);
        shell.addView(cancel, cancelParams);

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels - activity.dp(32), activity.dp(390));
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.dimAmount = 0.34f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
    }

    void requestAddPartner(Person selected) {
        if (selected == null || !activity.state.people.containsKey(selected.id)) return;
        if (!FamilyRelationPolicy.askWhetherNewPartnerParentsExistingChildren(
            activity.state,
            selected.id)) {
            activity.addRelationAction("add-partner-only");
            return;
        }
        int children = FamilyRelationPolicy.childIds(activity.state, selected.id).size();
        List<ChoiceOption> options = new ArrayList<>();
        options.add(new ChoiceOption(
            R.drawable.ic_menu_child,
            activity.tr("Связать с детьми"),
            activity.tr("Новый партнёр станет родителем существующих детей"),
            () -> {
                if (!activity.state.people.containsKey(selected.id)) return;
                activity.state.selectedId = selected.id;
                activity.addRelationAction("add-partner-with-children");
            }));
        options.add(new ChoiceOption(
            R.drawable.ic_menu_heart,
            activity.tr("Не связывать"),
            activity.tr("Добавится только связь партнёров"),
            () -> {
                if (!activity.state.people.containsKey(selected.id)) return;
                activity.state.selectedId = selected.id;
                activity.addRelationAction("add-partner-only");
            }));
        showChoiceDialog(
            selected,
            activity.tr("ДОБАВЛЕНИЕ ПАРТНЁРА"),
            activity.tr("Связать партнёра с детьми?"),
            activity.tr("У выбранной персоны уже есть дети: ") + children,
            options);
    }

    void requestAddChildren(Person selected, int count) {
        if (selected == null || !activity.state.people.containsKey(selected.id)) return;
        java.util.List<String> partners = FamilyRelationPolicy.partnerIds(
            activity.state,
            selected.id);
        if (partners.size() <= 1) {
            String partnerId = FamilyRelationPolicy.automaticCoParent(
                activity.state,
                selected.id);
            addChildren(selected, count, partnerId);
            return;
        }

        List<ChoiceOption> options = new ArrayList<>();
        for (int index = 0; index < partners.size(); index++) {
            String partnerId = partners.get(index);
            Person partner = activity.state.people.get(partnerId);
            String name = partner == null || partner.name == null || partner.name.trim().isEmpty()
                ? activity.tr("Без имени")
                : partner.name.trim();
            options.add(new ChoiceOption(
                R.drawable.ic_menu_people,
                name,
                activity.tr("Второй родитель для новых детей"),
                () -> addChildren(selected, count, partnerId)));
        }
        options.add(new ChoiceOption(
            R.drawable.ic_menu_child,
            activity.tr("Без второго родителя"),
            activity.tr("Связать детей только с выбранной персоной"),
            () -> addChildren(selected, count, "")));
        showChoiceDialog(
            selected,
            activity.tr("ДОБАВЛЕНИЕ ДЕТЕЙ"),
            activity.tr("Выберите второго родителя"),
            activity.tr("У выбранной персоны несколько партнёров. С кем связать новых детей?"),
            options);
    }

    private void showChoiceDialog(
        Person selected,
        String eyebrowText,
        String titleText,
        String detailText,
        List<ChoiceOption> options
    ) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(activity.dp(18), activity.dp(17), activity.dp(18), activity.dp(16));
        shell.setBackground(activity.panelBg(
            Color.WHITE, activity.dp(20), Color.argb(60, 63, 82, 94)));

        TextView eyebrow = new LocalizedTextView(activity);
        eyebrow.setText(eyebrowText);
        eyebrow.setTextSize(9);
        eyebrow.setTypeface(activity.uiBold());
        eyebrow.setTextColor(Color.rgb(8, 122, 115));
        eyebrow.setIncludeFontPadding(false);
        shell.addView(eyebrow, new LinearLayout.LayoutParams(-1, activity.dp(20)));

        TextView personName = activity.cardActionTitle(
            selected.name == null || selected.name.trim().isEmpty()
                ? activity.tr("Без имени") : selected.name.trim(), false);
        personName.setTextSize(15);
        personName.setSingleLine(true);
        personName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        shell.addView(personName, new LinearLayout.LayoutParams(-1, activity.dp(28)));

        TextView title = activity.cardActionTitle(titleText, false);
        title.setTextSize(18);
        title.setPadding(0, activity.dp(8), 0, 0);
        shell.addView(title, new LinearLayout.LayoutParams(-1, activity.dp(40)));
        TextView detail = activity.cardActionDetail(detailText, false);
        detail.setTextSize(11);
        detail.setMaxLines(3);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.setMargins(0, 0, 0, activity.dp(10));
        shell.addView(detail, detailParams);

        ScrollView scroller = new ScrollView(activity);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        LinearLayout choiceList = new LinearLayout(activity);
        choiceList.setOrientation(LinearLayout.VERTICAL);
        for (ChoiceOption option : options) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(activity.dp(70));
            row.setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10));
            row.setBackground(activity.panelBg(
                Color.rgb(246, 251, 250),
                activity.dp(14),
                Color.argb(65, 24, 169, 153)));
            row.setClickable(true);
            row.setFocusable(true);
            row.setContentDescription(option.title + ". " + option.detail);
            row.setOnClickListener(view -> {
                dialog.dismiss();
                if (!activity.state.people.containsKey(selected.id)) return;
                activity.state.selectedId = selected.id;
                option.action.run();
            });

            ImageView icon = new ImageView(activity);
            icon.setImageResource(option.icon);
            icon.setColorFilter(AppThemePalette.primary());
            icon.setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10));
            icon.setBackground(activity.panelBg(
                Color.rgb(226, 246, 242), activity.dp(12), Color.TRANSPARENT));
            row.addView(icon, new LinearLayout.LayoutParams(activity.dp(42), activity.dp(42)));

            LinearLayout labels = new LinearLayout(activity);
            labels.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
            labelParams.setMargins(activity.dp(12), 0, activity.dp(6), 0);
            row.addView(labels, labelParams);
            TextView optionTitle = activity.cardActionTitle(option.title, false);
            optionTitle.setTextSize(14);
            optionTitle.setSingleLine(true);
            optionTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            labels.addView(optionTitle, new LinearLayout.LayoutParams(-1, -2));
            TextView optionDetail = activity.cardActionDetail(option.detail, false);
            optionDetail.setTextSize(10);
            optionDetail.setMaxLines(2);
            labels.addView(optionDetail, new LinearLayout.LayoutParams(-1, -2));

            TextView chevron = new LocalizedTextView(activity);
            chevron.setText("›");
            chevron.setTextSize(26);
            chevron.setTextColor(Color.rgb(8, 122, 115));
            row.addView(chevron, new LinearLayout.LayoutParams(activity.dp(18), -2));

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.setMargins(0, 0, 0, activity.dp(8));
            choiceList.addView(row, rowParams);
        }
        scroller.addView(choiceList);
        int listHeight = Math.min(
            options.size() * activity.dp(80),
            Math.max(activity.dp(120),
                activity.getResources().getDisplayMetrics().heightPixels - activity.dp(300)));
        shell.addView(scroller, new LinearLayout.LayoutParams(-1, listHeight));

        Button cancel = activity.actionButton(activity.tr("Отмена"), view -> dialog.dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(-1, activity.dp(46));
        cancelParams.setMargins(0, activity.dp(2), 0, 0);
        shell.addView(cancel, cancelParams);

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(
                activity.getResources().getDisplayMetrics().widthPixels - activity.dp(32),
                activity.dp(390));
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.dimAmount = 0.34f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
    }

    private static final class ChoiceOption {
        final int icon;
        final String title;
        final String detail;
        final Runnable action;

        ChoiceOption(int icon, String title, String detail, Runnable action) {
            this.icon = icon;
            this.title = title;
            this.detail = detail;
            this.action = action;
        }
    }

    private void addChildren(Person selected, int count, String partnerId) {
        activity.state.selectedId = selected.id;
        String suffix = partnerId == null || partnerId.isEmpty() ? "none" : partnerId;
        activity.addRelationAction("add-children-" + count + "|" + suffix);
    }

}
