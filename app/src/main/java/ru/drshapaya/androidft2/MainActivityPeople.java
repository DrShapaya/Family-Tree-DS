package ru.drshapaya.androidft2;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.LruCache;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class MainActivityPeople {
    private final MainActivity activity;
    private LinearLayout panel;
    private LinearLayout content;
    private EditText query;
    private Button listTab;
    private Button photoTab;
    private Button filterButton;
    private Button sortButton;
    private TextView peopleSubtitle;
    private String tab = "list";
    private String genderFilter = "all";
    private String lifeFilter = "all";
    private String photoFilter = "all";
    private String sort = "surname";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Collator peopleCollator = createPeopleCollator();
    private final LruCache<String, Bitmap> avatarCache = new LruCache<>(48);
    private final ExecutorService avatarDecoder = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "people-avatar-decoder");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private Runnable pendingFilter;
    private String pendingPhotoTarget = "";
    private String pendingPhotoAlbum = "";
    private String pendingPhotoItem = "";
    private Dialog openPhotoDialog;
    private int galleryColumns;

    MainActivityPeople(MainActivity activity) {
        this.activity = activity;
        galleryColumns = Math.max(1, Math.min(5,
            activity.getSharedPreferences("androidft_ui", Activity.MODE_PRIVATE).getInt("gallery_columns", 2)));
    }

    View buildPanel() {
        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(8));
        panel.setBackgroundColor(AppThemePalette.surface(Color.rgb(243, 247, 248)));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(8), dp(10), dp(8));
        header.setBackground(activity.panelBg(Color.WHITE, dp(20), Color.argb(54, 63, 82, 94)));
        header.setElevation(dp(3));

        ImageView mark = new ImageView(activity);
        mark.setImageResource(R.drawable.ic_menu_people);
        mark.setColorFilter(Color.WHITE);
        mark.setPadding(dp(13), dp(13), dp(13), dp(13));
        mark.setBackground(activity.tealGradientBg(dp(16)));
        header.addView(mark, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout heading = new LinearLayout(activity);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, dp(6), 0);
        TextView title = text("Люди", 20, Color.rgb(28, 34, 38), true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(title, new LinearLayout.LayoutParams(-1, dp(30)));
        int count = activity.state == null ? 0 : activity.state.people.size();
        peopleSubtitle = text(peopleCountText(count), 10, Color.rgb(101, 113, 122), false);
        peopleSubtitle.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(peopleSubtitle, new LinearLayout.LayoutParams(-1, dp(22)));
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(54), 1));
        panel.addView(header, new LinearLayout.LayoutParams(-1, dp(68)));

        LinearLayout tabs = new LinearLayout(activity);
        tabs.setPadding(dp(5), dp(5), dp(5), dp(5));
        tabs.setBackground(activity.panelBg(Color.rgb(235, 241, 243), dp(18), Color.argb(58, 63, 82, 94)));
        listTab = tabButton("Список", R.drawable.ic_menu_people, "list");
        photoTab = tabButton("Фото", R.drawable.ic_menu_image, "photo");
        LinearLayout.LayoutParams listTabParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        listTabParams.setMargins(0, 0, dp(4), 0);
        tabs.addView(listTab, listTabParams);
        LinearLayout.LayoutParams photoTabParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        photoTabParams.setMargins(dp(4), 0, 0, 0);
        tabs.addView(photoTab, photoTabParams);
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(-1, dp(62));
        tabsParams.setMargins(0, dp(10), 0, dp(12));
        panel.addView(tabs, tabsParams);

        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        panel.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        panel.setVisibility(View.GONE);
        return panel;
    }

    void close() {
        mainHandler.removeCallbacksAndMessages(null);
        avatarDecoder.shutdownNow();
        avatarCache.evictAll();
    }

    void refresh() {
        if (content == null) return;
        if (peopleSubtitle != null) {
            int count = activity.state == null ? 0 : activity.state.people.size();
            LocalizedViews.setRaw(peopleSubtitle, peopleCountText(count));
        }
        styleTabs();
        if ("photo".equals(tab)) renderPhotoHome();
        else renderList();
    }

    private Button tabButton(String label, int icon, String value) {
        Button button = activity.actionButton(label, v -> {
            tab = value;
            refresh();
        });
        button.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
        button.setCompoundDrawablePadding(dp(9));
        button.setTextSize(13);
        button.setElevation(0f);
        return button;
    }

    private void styleTabs() {
        styleTab(listTab, "list".equals(tab));
        styleTab(photoTab, "photo".equals(tab));
    }

    private void styleTab(Button button, boolean selected) {
        if (button == null) return;
        int color = selected ? Color.WHITE : Color.rgb(83, 94, 103);
        button.setTextColor(color);
        activity.tintDrawables(button, color);
        button.setBackground(selected
            ? activity.tealGradientBg(dp(14))
            : activity.panelBg(Color.WHITE, dp(14), Color.argb(42, 63, 82, 94)));
        button.setElevation(selected ? dp(3) : 0f);
    }

    private void renderList() {
        if (pendingFilter != null) mainHandler.removeCallbacks(pendingFilter);
        content.removeAllViews();
        LinearLayout controls = new LinearLayout(activity);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(8), dp(8), dp(8), dp(8));
        controls.setBackground(activity.panelBg(Color.WHITE, dp(18), Color.argb(48, 63, 82, 94)));
        query = activity.field("Поиск по ФИО, году или месту");
        query.setSingleLine(true);
        query.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_search, 0, 0, 0);
        query.setCompoundDrawablePadding(dp(8));
        activity.tintDrawables(query, Color.rgb(8, 122, 115));
        query.setBackground(activity.panelBg(Color.rgb(248, 251, 252), dp(13), Color.rgb(217, 224, 229)));
        controls.addView(query, new LinearLayout.LayoutParams(-1, dp(50)));
        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        filterButton = compactButton("Фильтры", R.drawable.ic_menu_filter, v -> showFilters());
        sortButton = compactButton("А–Я", R.drawable.ic_menu_sort_alpha, v -> showSorting());
        LinearLayout.LayoutParams action = new LinearLayout.LayoutParams(0, dp(50), 1);
        action.setMargins(0, dp(7), dp(4), dp(3));
        actions.addView(filterButton, action);
        LinearLayout.LayoutParams sortParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        sortParams.setMargins(dp(4), dp(7), 0, dp(3));
        actions.addView(sortButton, sortParams);
        controls.addView(actions, new LinearLayout.LayoutParams(-1, dp(62)));
        content.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout chips = new LinearLayout(activity);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(dp(4), dp(7), 0, dp(3));
        addActiveChips(chips);
        content.addView(chips, new LinearLayout.LayoutParams(-1, dp(42)));

        ScrollView scroll = new ScrollView(activity);
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        query.addTextChangedListener(new SimpleTextWatcher(() -> {
            if (pendingFilter != null) mainHandler.removeCallbacks(pendingFilter);
            pendingFilter = () -> {
                pendingFilter = null;
                fillPeopleRows(rows);
            };
            mainHandler.postDelayed(pendingFilter, 110L);
        }));
        fillPeopleRows(rows);
    }

    private Button compactButton(String label, int icon, View.OnClickListener click) {
        Button button = activity.actionButton(label, click);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(12), 0, dp(12), dp(3));
        button.setIncludeFontPadding(false);
        button.setTextSize(12);
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER);
        button.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
        button.setCompoundDrawablePadding(dp(9));
        button.setTextColor(Color.rgb(8, 122, 115));
        button.setBackground(activity.panelBg(Color.rgb(238, 249, 247), dp(13), Color.argb(76, 24, 169, 153)));
        activity.tintDrawables(button, Color.rgb(8, 122, 115));
        return button;
    }

    private void addActiveChips(LinearLayout chips) {
        if (!"all".equals(genderFilter)) chips.addView(chip("male".equals(genderFilter) ? "Мужчины" : "Женщины"));
        if (!"all".equals(lifeFilter)) chips.addView(chip("living".equals(lifeFilter) ? "Живые" : "Умершие"));
        if (!"all".equals(photoFilter)) chips.addView(chip("with".equals(photoFilter) ? "С фото" : "Без фото"));
        if (chips.getChildCount() == 0) {
            TextView hint = text("Все люди · нажмите «Фильтры», чтобы сузить список", 10, Color.rgb(101, 113, 122), false);
            chips.addView(hint, new LinearLayout.LayoutParams(-1, dp(34)));
        }
    }

    private TextView chip(String label) {
        TextView chip = text(label, 10, Color.rgb(8, 122, 115), true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), 0, dp(12), 0);
        chip.setBackground(activity.panelBg(Color.rgb(232, 248, 246), dp(999), Color.argb(64, 24, 169, 153)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(32));
        params.setMargins(0, 0, dp(7), 0);
        chip.setLayoutParams(params);
        return chip;
    }

    private void fillPeopleRows(LinearLayout rows) {
        rows.removeAllViews();
        if (activity.state == null) return;
        String needle = query == null ? "" : query.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<Person> people = new ArrayList<>();
        for (Person person : activity.state.people.values()) {
            if (!matches(person, needle)) continue;
            people.add(person);
        }
        Collections.sort(people, personComparator());
        String section = "";
        for (Person person : people) {
            String next = sectionFor(person);
            if (!next.equals(section)) {
                section = next;
                TextView label = text(section, 12, Color.rgb(8, 122, 115), true);
                label.setGravity(Gravity.CENTER_VERTICAL);
                label.setPadding(dp(8), dp(8), 0, 0);
                rows.addView(label, new LinearLayout.LayoutParams(-1, dp(38)));
            }
            rows.addView(personRow(person));
        }
        if (people.isEmpty()) rows.addView(emptyState("Люди не найдены", "Измените запрос или сбросьте фильтры"));
    }

    private boolean matches(Person person, String needle) {
        if ("male".equals(genderFilter) && !PersonGender.MALE.equals(person.gender)) return false;
        if ("female".equals(genderFilter) && !PersonGender.FEMALE.equals(person.gender)) return false;
        boolean dead = value(person.diedYear).length() > 0 || value(person.died).length() > 0;
        if ("living".equals(lifeFilter) && dead) return false;
        if ("dead".equals(lifeFilter) && !dead) return false;
        boolean hasPhoto = hasPhoto(person);
        if ("with".equals(photoFilter) && !hasPhoto) return false;
        if ("without".equals(photoFilter) && hasPhoto) return false;
        if (needle.isEmpty()) return true;
        return (value(person.name) + " " + value(person.bornYear) + " " + value(person.diedYear) + " " + value(person.place))
            .toLowerCase(Locale.ROOT).contains(needle);
    }

    private Comparator<Person> personComparator() {
        Collator collator = peopleCollator;
        return (a, b) -> {
            if ("birth".equals(sort)) return compareYears(a.bornYear, b.bornYear, collator.compare(value(a.name), value(b.name)));
            if ("name".equals(sort)) return collator.compare(value(a.name), value(b.name));
            int family = collator.compare(TreeState.surnameOf(a.name), TreeState.surnameOf(b.name));
            return family == 0 ? collator.compare(value(a.name), value(b.name)) : family;
        };
    }

    private int compareYears(String first, String second, int fallback) {
        int a = year(first);
        int b = year(second);
        if (a == b) return fallback;
        if (a == 0) return 1;
        if (b == 0) return -1;
        return Integer.compare(a, b);
    }

    private String sectionFor(Person person) {
        String value = "name".equals(sort) ? value(person.name) : TreeState.surnameOf(person.name);
        if ("birth".equals(sort)) value = value(person.bornYear);
        if (value.isEmpty()) return "#";
        return value.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private View personRow(Person person) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(9), dp(7), dp(7), dp(7));
        row.setBackground(activity.panelBg(Color.WHITE, dp(14), Color.rgb(217, 224, 229)));
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, dp(72));
        outer.setMargins(0, 0, 0, dp(7));
        row.setLayoutParams(outer);
        row.setOnClickListener(v -> openPerson(person, false));
        row.addView(avatar(person, 52), new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(11), 0, dp(6), 0);
        TextView name = text(displayName(person), 13, Color.rgb(28, 34, 38), true);
        name.setSingleLine(true);
        copy.addView(name, new LinearLayout.LayoutParams(-1, dp(28)));
        String years = years(person);
        String detail = years + (value(person.place).isEmpty() ? "" : (years.isEmpty() ? "" : " · ") + person.place);
        TextView sub = text(detail.isEmpty() ? "Карточка человека" : detail, 10, Color.rgb(101, 113, 122), false);
        sub.setSingleLine(true);
        copy.addView(sub, new LinearLayout.LayoutParams(-1, dp(24)));
        row.addView(copy, new LinearLayout.LayoutParams(0, dp(54), 1));

        Button tree = activity.iconButton(R.drawable.ic_nav_tree, v -> openPerson(person, true));
        tree.setBackground(activity.panelBg(Color.rgb(232, 248, 246), dp(12), Color.argb(58, 24, 169, 153)));
        row.addView(tree, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return row;
    }

    private void openPerson(Person person, boolean onTree) {
        activity.state.selectedId = person.id;
        activity.bindEditor(person);
        if (onTree) {
            activity.showPanel("");
            activity.treeView.post(() -> activity.treeView.focusPerson(person.id));
        } else {
            activity.openPersonEditor();
        }
    }

    private void showFilters() {
        Dialog dialog = styledChoiceDialog("Фильтры", "Выберите, кого показывать в списке", R.drawable.ic_menu_filter);
        LinearLayout host = dialogHost(dialog);
        String[] pending = {genderFilter, lifeFilter, photoFilter};
        host.addView(choiceSection("Пол", new String[]{"Все", "Мужчины", "Женщины"}, pending[0],
            new String[]{"all", "male", "female"}, value -> pending[0] = value));
        host.addView(choiceSection("Статус", new String[]{"Все", "Живые", "Умершие"}, pending[1],
            new String[]{"all", "living", "dead"}, value -> pending[1] = value));
        host.addView(choiceSection("Фотография", new String[]{"Любые", "С фото", "Без фото"}, pending[2],
            new String[]{"all", "with", "without"}, value -> pending[2] = value));
        host.addView(dialogActions("Сбросить", () -> {
            genderFilter = lifeFilter = photoFilter = "all";
            refresh();
            dialog.dismiss();
        }, "Применить", () -> {
            genderFilter = pending[0];
            lifeFilter = pending[1];
            photoFilter = pending[2];
            refresh();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showSorting() {
        Dialog dialog = styledChoiceDialog("Сортировка А–Я", "Выберите порядок людей в списке", R.drawable.ic_menu_sort_alpha);
        LinearLayout host = dialogHost(dialog);
        String[] pending = {sort};
        host.addView(choiceSection("Порядок", new String[]{"По фамилии и имени", "По имени", "По году рождения"}, pending[0],
            new String[]{"surname", "name", "birth"}, value -> pending[0] = value));
        host.addView(dialogActions("Отмена", dialog::dismiss, "Применить", () -> {
            sort = pending[0];
            refresh();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void renderPhotoHome() {
        content.removeAllViews();
        TextView intro = text("Фотографии дерева", 13, Color.rgb(28, 34, 38), true);
        intro.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(intro, new LinearLayout.LayoutParams(-1, dp(34)));
        TextView hint = text("Автоматические коллекции и ваши семейные альбомы", 10, Color.rgb(101, 113, 122), false);
        content.addView(hint, new LinearLayout.LayoutParams(-1, dp(30)));

        ScrollView scroll = new ScrollView(activity);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        body.addView(collectionCard("Все аватары", avatarPeople().size() + " фото", R.drawable.ic_menu_people,
            Color.rgb(24, 169, 153), this::showAvatarAlbum));
        body.addView(collectionCard("По семьям", familyGroups(false).size() + " семей с фотографиями", R.drawable.ic_menu_family_color,
            Color.rgb(47, 140, 255), this::showFamilies));
        body.addView(collectionCard("Создать альбом", "Соберите собственную подборку", R.drawable.ic_menu_add_box,
            Color.rgb(142, 105, 192), this::createAlbum));

        Map<String, List<String>> albums = loadAlbums();
        if (!albums.isEmpty()) {
            TextView label = section("МОИ АЛЬБОМЫ");
            label.setPadding(dp(4), dp(12), 0, 0);
            body.addView(label, new LinearLayout.LayoutParams(-1, dp(44)));
            for (Map.Entry<String, List<String>> entry : albums.entrySet()) {
                List<Person> people = peopleForIds(entry.getValue());
                body.addView(albumCard(entry.getKey(), people));
            }
        }
    }

    private View collectionCard(String title, String detail, int iconRes, int color, Runnable action) {
        LinearLayout card = new LinearLayout(activity);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(activity.panelBg(Color.WHITE, dp(18), Color.rgb(217, 224, 229)));
        card.setOnClickListener(v -> action.run());
        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.WHITE);
        icon.setPadding(dp(14), dp(14), dp(14), dp(14));
        icon.setBackground(activity.colorSwatchBg(color, dp(18)));
        card.addView(icon, new LinearLayout.LayoutParams(dp(62), dp(62)));
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(13), 0, 0, 0);
        copy.addView(text(title, 14, Color.rgb(28, 34, 38), true), new LinearLayout.LayoutParams(-1, dp(30)));
        copy.addView(text(detail, 10, Color.rgb(101, 113, 122), false), new LinearLayout.LayoutParams(-1, dp(26)));
        card.addView(copy, new LinearLayout.LayoutParams(0, dp(60), 1));
        TextView arrow = text("›", 28, Color.rgb(8, 122, 115), false);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(34), dp(52)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(86));
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private View albumCard(String name, List<Person> people) {
        int photos = people.size() + albumMedia(name).size();
        int folders = albumFolders(name).size();
        String detail = photos + " фото" + (folders == 0 ? "" : " · " + folders + " папок");
        LinearLayout card = (LinearLayout) collectionCard(name, detail, R.drawable.ic_menu_image,
            Color.rgb(240, 168, 95), () -> showCustomAlbum(name));
        card.setOnLongClickListener(v -> {
            showAlbumMenu(name);
            return true;
        });
        return card;
    }

    private void showFamilies() {
        Dialog dialog = fullDialog("Семейные альбомы");
        LinearLayout host = dialogHost(dialog);
        Map<String, List<Person>> groups = familyGroups(true);
        TextView hint = text("Семьи с фотографиями добавляются автоматически", 10, Color.rgb(101, 113, 122), false);
        host.addView(hint, new LinearLayout.LayoutParams(-1, dp(34)));
        GridLayout grid = photoGrid();
        for (Map.Entry<String, List<Person>> entry : groups.entrySet()) {
            View tile = familyTile(entry.getKey(), entry.getValue(), () -> showFamilyAlbum(entry.getKey(), entry.getValue()));
            tile.setOnLongClickListener(v -> {
                showFamilyMenu(entry.getKey(), entry.getValue(), dialog);
                return true;
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(154);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(0, 0, dp(8), dp(8));
            grid.addView(tile, params);
        }
        host.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        Button addFamily = activity.actionButton("+  Добавить семью из существующих", v -> chooseFamily(dialog));
        addFamily.setTextColor(Color.rgb(8, 122, 115));
        addFamily.setBackground(activity.panelBg(Color.rgb(232, 248, 246), dp(14), Color.argb(72, 24, 169, 153)));
        host.addView(addFamily, new LinearLayout.LayoutParams(-1, dp(52)));
        dialog.show();
    }

    private View familyTile(String surname, List<Person> people, Runnable action) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(8), dp(8), dp(8), dp(8));
        tile.setBackground(activity.panelBg(Color.WHITE, dp(16), Color.rgb(217, 224, 229)));
        tile.setOnClickListener(v -> action.run());
        LinearLayout collage = new LinearLayout(activity);
        collage.setGravity(Gravity.CENTER);
        for (int i = 0; i < Math.min(2, Math.max(1, people.size())); i++) {
            Person person = people.isEmpty() ? null : people.get(i);
            LinearLayout.LayoutParams preview = new LinearLayout.LayoutParams(dp(68), dp(68));
            preview.setMargins(dp(3), 0, dp(3), 0);
            collage.addView(person == null ? placeholderAvatar(surname) : avatar(person, 72), preview);
        }
        tile.addView(collage, new LinearLayout.LayoutParams(-1, dp(82)));
        TextView title = text(capitalize(surname), 12, Color.rgb(28, 34, 38), true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        tile.addView(title, new LinearLayout.LayoutParams(-1, dp(28)));
        int extra = activity.state.familyAlbumMedia.getOrDefault(surname, Collections.emptyList()).size();
        tile.addView(text((people.size() + extra) + " фото", 9, Color.rgb(101, 113, 122), false), new LinearLayout.LayoutParams(-1, dp(22)));
        return tile;
    }

    private void showAvatarAlbum() {
        Dialog dialog = fullDialog("Все аватары");
        openPhotoDialog = dialog;
        LinearLayout host = dialogHost(dialog);
        host.addView(albumToolbar(new String[]{"Добавить человеку фото"}, new int[]{R.drawable.ic_menu_add_box},
            new Runnable[]{() -> choosePersonForPhotos(dialog)}));
        List<Person> people = avatarPeople();
        if (people.isEmpty()) host.addView(emptyState("Фотографий пока нет", "Добавьте аватар в карточку человека"));
        else host.addView(peopleGrid(people, null, dialog));
        dialog.show();
    }

    private void showFamilyAlbum(String surname, List<Person> people) {
        Dialog dialog = fullDialog(capitalize(surname));
        openPhotoDialog = dialog;
        LinearLayout host = dialogHost(dialog);
        host.addView(albumToolbar(new String[]{"Добавить фото"}, new int[]{R.drawable.ic_menu_add_box},
            new Runnable[]{() -> openAlbumPhotoPicker("family", surname, "")}));
        GridLayout grid = photoGrid();
        for (Person person : people) addGridTile(grid, personTile(person, null, null, dialog));
        for (String mediaId : activity.state.familyAlbumMedia.getOrDefault(surname, Collections.emptyList())) {
            addGridTile(grid, mediaTile(mediaId, () -> removeMedia("family", surname, "", mediaId)));
        }
        if (grid.getChildCount() == 0) host.addView(emptyState("Альбом пуст", "Добавьте фотографии семьи"));
        else host.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        dialog.show();
    }

    private void showCustomAlbum(String name) {
        Dialog dialog = fullDialog(name);
        openPhotoDialog = dialog;
        LinearLayout host = dialogHost(dialog);
        host.addView(albumToolbar(
            new String[]{"Люди", "Фото", "Папка"},
            new int[]{R.drawable.ic_menu_people, R.drawable.ic_menu_image, R.drawable.ic_menu_add_box},
            new Runnable[]{
                () -> chooseAlbumPeople(name, null, dialog),
                () -> openAlbumPhotoPicker("album", name, ""),
                () -> createFolder(name, dialog)
            }
        ));
        GridLayout grid = photoGrid();
        for (PhotoAlbumFolder folder : albumFolders(name)) addGridTile(grid, folderTile(name, folder, dialog));
        for (Person person : peopleForIds(activity.state.photoAlbums.getOrDefault(name, Collections.emptyList()))) {
            addGridTile(grid, personTile(person, name, null, dialog));
        }
        for (String mediaId : albumMedia(name)) {
            addGridTile(grid, mediaTile(mediaId, () -> removeMedia("album", name, "", mediaId)));
        }
        if (grid.getChildCount() == 0) host.addView(emptyState("Альбом пока пуст", "Добавьте людей, любые фото или создайте папку"));
        else host.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        dialog.show();
    }

    private void showFolder(String albumName, String folderId) {
        PhotoAlbumFolder folder = findFolder(albumName, folderId);
        if (folder == null) return;
        Dialog dialog = fullDialog(folder.name);
        openPhotoDialog = dialog;
        LinearLayout host = dialogHost(dialog);
        host.addView(albumToolbar(
            new String[]{"Люди", "Фото"},
            new int[]{R.drawable.ic_menu_people, R.drawable.ic_menu_image},
            new Runnable[]{
                () -> chooseAlbumPeople(albumName, folder.id, dialog),
                () -> openAlbumPhotoPicker("folder", albumName, folder.id)
            }
        ));
        GridLayout grid = photoGrid();
        for (Person person : peopleForIds(folder.personIds)) addGridTile(grid, personTile(person, albumName, folder.id, dialog));
        for (String mediaId : folder.photoMediaIds) {
            addGridTile(grid, mediaTile(mediaId, () -> removeMedia("folder", albumName, folder.id, mediaId)));
        }
        if (grid.getChildCount() == 0) host.addView(emptyState("Папка пока пуста", "Выберите аватары или добавьте фотографии"));
        else host.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        dialog.show();
    }

    private void showPersonGallery(Person person) {
        if (person == null) return;
        Dialog dialog = fullDialog(displayName(person));
        openPhotoDialog = dialog;
        LinearLayout host = dialogHost(dialog);
        host.addView(albumToolbar(new String[]{"Добавить фото"}, new int[]{R.drawable.ic_menu_add_box},
            new Runnable[]{() -> openAlbumPhotoPicker("person", "", person.id)}));
        GridLayout grid = photoGrid();
        if (hasPhoto(person)) addGridTile(grid, personAvatarPhotoTile(person, dialog));
        for (String mediaId : activity.state.personAlbumMedia.getOrDefault(person.id, Collections.emptyList())) {
            addGridTile(grid, mediaTile(mediaId, () -> removeMedia("person", "", person.id, mediaId)));
        }
        if (grid.getChildCount() == 0) host.addView(emptyState("Фотографий пока нет", "Добавьте снимки этого человека"));
        else host.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        dialog.show();
    }

    private GridLayout peopleGrid(List<Person> people, String albumName, Dialog parent) {
        GridLayout grid = photoGrid();
        for (Person person : people) addGridTile(grid, personTile(person, albumName, null, parent));
        return grid;
    }

    private View personTile(Person person, String albumName, String folderId, Dialog parent) {
        LinearLayout tile = photoTileShell();
        FrameLayout square = new SquareFrame(activity);
        square.addView(avatar(person, 160), new FrameLayout.LayoutParams(-1, -1));
        if (!activity.state.personAlbumMedia.getOrDefault(person.id, Collections.emptyList()).isEmpty()) {
            TextView badge = text("+" + activity.state.personAlbumMedia.get(person.id).size(), 10, Color.WHITE, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(activity.colorSwatchBg(Color.rgb(8, 122, 115), dp(12)));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dp(38), dp(26), Gravity.BOTTOM | Gravity.RIGHT);
            badgeParams.setMargins(0, 0, dp(6), dp(6));
            square.addView(badge, badgeParams);
        }
        tile.addView(square, new LinearLayout.LayoutParams(-1, -2));
        TextView name = text(displayName(person), 10, Color.rgb(28, 34, 38), true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        tile.addView(name, new LinearLayout.LayoutParams(-1, dp(44)));
        tile.setOnClickListener(v -> showPersonGallery(person));
        tile.setOnLongClickListener(v -> {
            showPersonMenu(person, albumName, folderId, parent);
            return true;
        });
        return tile;
    }

    private View personAvatarPhotoTile(Person person, Dialog parent) {
        LinearLayout tile = photoTileShell();
        FrameLayout square = new SquareFrame(activity);
        square.addView(avatar(person, 180), new FrameLayout.LayoutParams(-1, -1));
        tile.addView(square, new LinearLayout.LayoutParams(-1, -2));
        TextView label = text("Основной аватар", 10, Color.rgb(28, 34, 38), true);
        label.setGravity(Gravity.CENTER);
        tile.addView(label, new LinearLayout.LayoutParams(-1, dp(44)));
        tile.setOnClickListener(v -> {
            if (parent != null) parent.dismiss();
            activity.showPersonPhotoEditor(person);
        });
        return tile;
    }

    private View folderTile(String albumName, PhotoAlbumFolder folder, Dialog parent) {
        LinearLayout tile = photoTileShell();
        FrameLayout square = new SquareFrame(activity);
        square.addView(folderPreview(folder), new FrameLayout.LayoutParams(-1, -1));
        TextView count = text((folder.personIds.size() + folder.photoMediaIds.size()) + "", 10, Color.WHITE, true);
        count.setGravity(Gravity.CENTER);
        count.setBackground(activity.colorSwatchBg(Color.rgb(8, 122, 115), dp(12)));
        FrameLayout.LayoutParams countParams = new FrameLayout.LayoutParams(dp(34), dp(26), Gravity.BOTTOM | Gravity.RIGHT);
        countParams.setMargins(0, 0, dp(6), dp(6));
        square.addView(count, countParams);
        tile.addView(square, new LinearLayout.LayoutParams(-1, -2));
        TextView name = text(folder.name, 10, Color.rgb(28, 34, 38), true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        tile.addView(name, new LinearLayout.LayoutParams(-1, dp(44)));
        tile.setOnClickListener(v -> showFolder(albumName, folder.id));
        tile.setOnLongClickListener(v -> {
            showFolderMenu(albumName, folder, parent);
            return true;
        });
        return tile;
    }

    private View folderPreview(PhotoAlbumFolder folder) {
        List<String> mediaIds = new ArrayList<>(folder.photoMediaIds);
        List<Person> people = peopleForIds(folder.personIds);
        int count = Math.min(4, mediaIds.size() + people.size());
        if (count == 0) {
            TextView mark = text("▰", 48, Color.WHITE, true);
            mark.setGravity(Gravity.CENTER);
            mark.setBackground(activity.colorSwatchBg(Color.rgb(142, 105, 192), dp(14)));
            return mark;
        }
        LinearLayout collage = new LinearLayout(activity);
        collage.setOrientation(LinearLayout.VERTICAL);
        collage.setBackground(activity.colorSwatchBg(Color.rgb(224, 231, 234), dp(14)));
        int rows = count <= 2 ? 1 : 2;
        int index = 0;
        for (int rowIndex = 0; rowIndex < rows; rowIndex++) {
            LinearLayout row = new LinearLayout(activity);
            int columns = rows == 1 ? count : 2;
            for (int column = 0; column < columns && index < count; column++, index++) {
                View preview = index < mediaIds.size()
                    ? mediaPreview(mediaIds.get(index), 120)
                    : avatar(people.get(index - mediaIds.size()), 120);
                LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, -1, 1);
                cell.setMargins(column == 0 ? 0 : dp(1), rowIndex == 0 ? 0 : dp(1), 0, 0);
                row.addView(preview, cell);
            }
            collage.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        return collage;
    }

    private View mediaTile(String mediaId, Runnable remove) {
        LinearLayout tile = photoTileShell();
        FrameLayout square = new SquareFrame(activity);
        square.addView(mediaPreview(mediaId, 180), new FrameLayout.LayoutParams(-1, -1));
        tile.addView(square, new LinearLayout.LayoutParams(-1, -2));
        TextView label = text("Фотография", 10, Color.rgb(28, 34, 38), true);
        label.setGravity(Gravity.CENTER);
        tile.addView(label, new LinearLayout.LayoutParams(-1, dp(44)));
        tile.setOnClickListener(v -> activity.showMediaPhotoPreview(mediaId));
        tile.setOnLongClickListener(v -> {
            confirmRemovePhoto(remove);
            return true;
        });
        return tile;
    }

    private LinearLayout photoTileShell() {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(7), dp(7), dp(7), dp(7));
        tile.setBackground(activity.panelBg(Color.WHITE, dp(14), Color.rgb(217, 224, 229)));
        return tile;
    }

    private View mediaPreview(String mediaId, int sizeDp) {
        FrameLayout frame = new FrameLayout(activity);
        frame.setBackground(activity.colorSwatchBg(Color.rgb(224, 231, 234), dp(14)));
        avatarDecoder.execute(() -> {
            Bitmap bitmap = activity.store.mediaStore().decodeBitmap(mediaId, dp(sizeDp));
            if (bitmap == null) return;
            mainHandler.post(() -> {
                ImageView image = new ImageView(activity);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImageBitmap(bitmap);
                frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
            });
        });
        return frame;
    }

    private void addGridTile(GridLayout grid, View tile) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = -2;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(0, 0, dp(8), dp(8));
        grid.addView(tile, params);
    }

    private GridLayout photoGrid() {
        PinchGridLayout grid = new PinchGridLayout();
        grid.setColumnCount(galleryColumns);
        return grid;
    }

    private final class PinchGridLayout extends GridLayout {
        private final ScaleGestureDetector scaleDetector;
        private float accumulatedScale = 1f;
        private boolean pinching;
        private boolean gestureOwned;
        private long lastDetectorEventTime = Long.MIN_VALUE;
        private int lastDetectorAction = -1;

        PinchGridLayout() {
            super(activity);
            scaleDetector = new ScaleGestureDetector(activity, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScaleBegin(ScaleGestureDetector detector) {
                    pinching = true;
                    accumulatedScale = 1f;
                    requestDisallowInterceptTouchEvent(true);
                    return true;
                }

                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    accumulatedScale *= detector.getScaleFactor();
                    if (accumulatedScale > 1.16f && galleryColumns > 1) {
                        updateGalleryColumns(galleryColumns - 1);
                        accumulatedScale = 1f;
                    } else if (accumulatedScale < 0.86f && galleryColumns < 5) {
                        updateGalleryColumns(galleryColumns + 1);
                        accumulatedScale = 1f;
                    }
                    return true;
                }

                @Override
                public void onScaleEnd(ScaleGestureDetector detector) {
                    pinching = false;
                }
            });
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            feedScaleDetector(event);
            if (event.getPointerCount() > 1 || pinching) {
                gestureOwned = true;
                requestDisallowInterceptTouchEvent(true);
                return true;
            }
            return gestureOwned || super.onInterceptTouchEvent(event);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            feedScaleDetector(event);
            boolean handled = gestureOwned || pinching || event.getPointerCount() > 1;
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                gestureOwned = false;
                requestDisallowInterceptTouchEvent(false);
            }
            return handled || super.onTouchEvent(event);
        }

        private void feedScaleDetector(MotionEvent event) {
            int action = event.getAction();
            if (event.getEventTime() == lastDetectorEventTime && action == lastDetectorAction) return;
            lastDetectorEventTime = event.getEventTime();
            lastDetectorAction = action;
            scaleDetector.onTouchEvent(event);
        }

        private void updateGalleryColumns(int columns) {
            galleryColumns = Math.max(1, Math.min(5, columns));
            setColumnCount(galleryColumns);
            activity.getSharedPreferences("androidft_ui", Activity.MODE_PRIVATE)
                .edit().putInt("gallery_columns", galleryColumns).apply();
            requestLayout();
        }
    }

    private View albumToolbar(String[] labels, int[] icons, Runnable[] actions) {
        LinearLayout card = new LinearLayout(activity);
        card.setPadding(dp(6), dp(6), dp(6), dp(6));
        card.setBackground(activity.panelBg(Color.WHITE, dp(17), Color.rgb(217, 224, 229)));
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            Button button = activity.actionButton(labels[i], v -> {
                if (activity.requireEditingEnabled()) actions[index].run();
            });
            button.setTextSize(10);
            button.setCompoundDrawablesWithIntrinsicBounds(icons[i], 0, 0, 0);
            button.setCompoundDrawablePadding(dp(5));
            button.setTextColor(Color.rgb(8, 122, 115));
            activity.tintDrawables(button, Color.rgb(8, 122, 115));
            button.setBackground(activity.panelBg(Color.rgb(239, 249, 248), dp(13), Color.argb(45, 24, 169, 153)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1);
            params.setMargins(i == 0 ? 0 : dp(3), 0, i == labels.length - 1 ? 0 : dp(3), 0);
            card.addView(button, params);
        }
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, dp(62));
        outer.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(outer);
        return card;
    }

    private void createAlbum() {
        if (!activity.requireEditingEnabled()) return;
        Dialog dialog = styledChoiceDialog("Новый альбом", "Название можно изменить позже", R.drawable.ic_menu_image);
        LinearLayout host = dialogHost(dialog);
        EditText input = albumNameField("Название альбома", "");
        host.addView(input, fieldParams());
        TextView hint = text("После создания внутри можно добавить людей, любые фото и папки.", 10, Color.rgb(101, 113, 122), false);
        hint.setPadding(dp(4), 0, dp(4), dp(10));
        host.addView(hint, new LinearLayout.LayoutParams(-1, dp(42)));
        host.addView(dialogActions("Отмена", dialog::dismiss, "Создать", () -> {
            String name = uniqueAlbumName(input.getText().toString());
            activity.state.photoAlbums.put(name, new ArrayList<>());
            activity.saveOnly();
            dialog.dismiss();
            refresh();
            showCustomAlbum(name);
        }));
        dialog.show();
    }

    private void chooseAlbumPeople(String albumName, String folderId, Dialog parent) {
        if (!activity.requireEditingEnabled()) return;
        PhotoAlbumFolder selectedFolder = folderId == null ? null : findFolder(albumName, folderId);
        if (folderId != null && selectedFolder == null) {
            activity.toast("Папка больше не существует");
            return;
        }
        List<String> source = folderId == null
            ? activity.state.photoAlbums.computeIfAbsent(albumName, key -> new ArrayList<>())
            : selectedFolder.personIds;
        Set<String> selected = new LinkedHashSet<>(source);
        Dialog dialog = fullDialog("Выбор людей");
        LinearLayout host = dialogHost(dialog);
        EditText search = albumNameField("Поиск по имени", "");
        search.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_search, 0, 0, 0);
        host.addView(search, fieldParams());
        TextView summary = text("", 10, Color.rgb(101, 113, 122), false);
        summary.setPadding(dp(4), 0, 0, dp(6));
        host.addView(summary, new LinearLayout.LayoutParams(-1, dp(30)));
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        host.addView(rows, new LinearLayout.LayoutParams(-1, -2));
        Runnable render = () -> renderPeoplePickerRows(rows, summary, search.getText().toString(), selected);
        search.addTextChangedListener(new SimpleTextWatcher(render));
        render.run();
        host.addView(dialogActions("Отмена", dialog::dismiss, "Сохранить", () -> {
            source.clear();
            source.addAll(selected);
            activity.saveOnly();
            dialog.dismiss();
            if (parent != null) parent.dismiss();
            if (folderId == null) showCustomAlbum(albumName);
            else showFolder(albumName, folderId);
        }));
        dialog.show();
    }

    private void renderPeoplePickerRows(LinearLayout rows, TextView summary, String search, Set<String> selected) {
        rows.removeAllViews();
        String needle = value(search).toLowerCase(Locale.ROOT);
        int visible = 0;
        for (Person person : avatarPeople()) {
            if (!needle.isEmpty() && !displayName(person).toLowerCase(Locale.ROOT).contains(needle)) continue;
            visible++;
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(6), dp(8), dp(6));
            row.setBackground(activity.panelBg(Color.WHITE, dp(14), Color.rgb(217, 224, 229)));
            row.addView(avatar(person, 52), new LinearLayout.LayoutParams(dp(48), dp(48)));
            TextView label = text(displayName(person), 12, Color.rgb(28, 34, 38), true);
            label.setPadding(dp(10), 0, dp(4), 0);
            row.addView(label, new LinearLayout.LayoutParams(0, dp(48), 1));
            CheckBox check = new CheckBox(activity);
            check.setChecked(selected.contains(person.id));
            check.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selected.add(person.id); else selected.remove(person.id);
                summary.setText(selected.size() + " выбрано");
            });
            row.addView(check, new LinearLayout.LayoutParams(dp(48), dp(48)));
            row.setOnClickListener(v -> check.setChecked(!check.isChecked()));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(62));
            params.setMargins(0, 0, 0, dp(7));
            rows.addView(row, params);
        }
        summary.setText(selected.size() + " выбрано · найдено " + visible);
        if (visible == 0) rows.addView(emptyState("Ничего не найдено", "Попробуйте изменить запрос"));
    }

    private void deleteAlbum(String name) {
        showConfirmation("Удалить альбом?",
            "Фото людей останутся в карточках. Добавленные в альбом файлы перестанут отображаться.",
            "Удалить", () -> {
                if (!activity.requireEditingEnabled()) return;
                activity.state.photoAlbums.remove(name);
                activity.state.photoAlbumMedia.remove(name);
                activity.state.photoAlbumFolders.remove(name);
                activity.saveOnly();
                refresh();
            });
    }

    private void showAlbumMenu(String name) {
        Dialog dialog = styledChoiceDialog(name, "Действия с альбомом", R.drawable.ic_menu_image);
        LinearLayout host = dialogHost(dialog);
        host.addView(menuAction("Открыть альбом", "Фото, люди и папки", R.drawable.ic_menu_image, () -> {
            dialog.dismiss();
            showCustomAlbum(name);
        }));
        host.addView(menuAction("Выбрать людей", "Поиск по аватарам дерева", R.drawable.ic_menu_people, () -> {
            dialog.dismiss();
            chooseAlbumPeople(name, null, null);
        }));
        host.addView(menuAction("Добавить фотографии", "Любые изображения с устройства", R.drawable.ic_menu_add_box, () -> {
            dialog.dismiss();
            if (activity.requireEditingEnabled()) openAlbumPhotoPicker("album", name, "");
        }));
        host.addView(menuAction("Создать папку", "Соберите отдельную подборку", R.drawable.ic_menu_add_box, () -> {
            dialog.dismiss();
            createFolder(name, null);
        }));
        host.addView(menuAction("Переименовать", "Изменить название альбома", R.drawable.ic_menu_edit, () -> {
            dialog.dismiss();
            renameAlbum(name);
        }));
        host.addView(menuAction("Удалить", "Фото людей останутся в дереве", R.drawable.ic_guide_delete, () -> {
            dialog.dismiss();
            deleteAlbum(name);
        }));
        dialog.show();
    }

    private void showFamilyMenu(String surname, List<Person> people, Dialog parent) {
        Dialog dialog = styledChoiceDialog(capitalize(surname), "Действия с семейной папкой", R.drawable.ic_menu_family_color);
        LinearLayout host = dialogHost(dialog);
        host.addView(menuAction("Открыть папку", "Аватары и семейные фотографии", R.drawable.ic_menu_image, () -> {
            dialog.dismiss();
            if (parent != null) parent.dismiss();
            showFamilyAlbum(surname, people);
        }));
        host.addView(menuAction("Добавить фото семьи", "Выбрать изображения с устройства", R.drawable.ic_menu_add_box, () -> {
            dialog.dismiss();
            if (parent != null) parent.dismiss();
            if (activity.requireEditingEnabled()) openAlbumPhotoPicker("family", surname, "");
        }));
        host.addView(menuAction("Добавить фото человеку", "Найти человека в дереве", R.drawable.ic_menu_people, () -> {
            dialog.dismiss();
            choosePersonForPhotos(parent);
        }));
        List<String> added = activity.state.familyAlbumMedia.get(surname);
        if (added != null && !added.isEmpty()) {
            host.addView(menuAction("Очистить добавленные фото", "Аватары людей останутся", R.drawable.ic_guide_delete, () -> {
                dialog.dismiss();
                showConfirmation("Убрать добавленные фото?",
                    "Аватары членов семьи останутся в папке.", "Убрать", () -> {
                        if (!activity.requireEditingEnabled()) return;
                        activity.state.familyAlbumMedia.remove(surname);
                        activity.saveOnly();
                        if (parent != null) parent.dismiss();
                        showFamilyAlbum(surname, people);
                    });
            }));
        }
        dialog.show();
    }

    private View menuAction(String title, String detail, int iconRes, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(9), dp(6), dp(9), dp(6));
        row.setBackground(activity.panelBg(Color.WHITE, dp(15), Color.rgb(217, 224, 229)));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.rgb(8, 122, 115));
        icon.setPadding(dp(10), dp(10), dp(10), dp(10));
        icon.setBackground(activity.panelBg(Color.rgb(235, 248, 246), dp(12), Color.argb(52, 24, 169, 153)));
        row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, 0, 0);
        copy.addView(text(title, 12, Color.rgb(28, 34, 38), true), new LinearLayout.LayoutParams(-1, dp(24)));
        copy.addView(text(detail, 9, Color.rgb(101, 113, 122), false), new LinearLayout.LayoutParams(-1, dp(20)));
        row.addView(copy, new LinearLayout.LayoutParams(0, dp(44), 1));
        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(58));
        params.setMargins(0, 0, 0, dp(7));
        row.setLayoutParams(params);
        return row;
    }

    private void showFolderMenu(String albumName, PhotoAlbumFolder folder, Dialog parent) {
        Dialog dialog = styledChoiceDialog(folder.name, "Действия с папкой", R.drawable.ic_menu_image);
        LinearLayout host = dialogHost(dialog);
        host.addView(menuAction("Добавить фото", "Выбрать изображения с устройства", R.drawable.ic_menu_add_box, () -> {
            dialog.dismiss();
            if (activity.requireEditingEnabled()) openAlbumPhotoPicker("folder", albumName, folder.id);
        }));
        host.addView(menuAction("Переименовать", "Изменить название папки", R.drawable.ic_menu_edit, () -> {
            dialog.dismiss();
            renameFolder(albumName, folder, parent);
        }));
        host.addView(menuAction("Удалить", "Удалить папку из альбома", R.drawable.ic_guide_delete, () -> {
            dialog.dismiss();
            confirmDeleteFolder(albumName, folder, parent);
        }));
        dialog.show();
    }

    private void showPersonMenu(Person person, String albumName, String folderId, Dialog parent) {
        Dialog dialog = styledChoiceDialog(displayName(person), "Действия с человеком", R.drawable.ic_menu_people);
        LinearLayout host = dialogHost(dialog);
        host.addView(menuAction("Добавить фото", "Человек станет папкой с фотографиями", R.drawable.ic_menu_add_box, () -> {
            dialog.dismiss();
            if (activity.requireEditingEnabled()) openAlbumPhotoPicker("person", "", person.id);
        }));
        host.addView(menuAction("Переименовать", "Изменить ФИО в карточке дерева", R.drawable.ic_menu_edit, () -> {
            dialog.dismiss();
            renamePerson(person, parent);
        }));
        List<String> personPhotos = activity.state.personAlbumMedia.get(person.id);
        if (personPhotos != null && !personPhotos.isEmpty()) {
            host.addView(menuAction("Удалить папку с фото", "Основной аватар останется", R.drawable.ic_guide_delete, () -> {
                dialog.dismiss();
                showConfirmation("Удалить дополнительные фото?",
                    "Основной аватар и карточка человека останутся в дереве.", "Удалить", () -> {
                        if (!activity.requireEditingEnabled()) return;
                        activity.state.personAlbumMedia.remove(person.id);
                        activity.saveOnly();
                        if (parent != null) parent.dismiss();
                        showPersonGallery(person);
                    });
            }));
        }
        if (albumName != null) {
            String removeTitle = folderId == null ? "Убрать из альбома" : "Убрать из папки";
            host.addView(menuAction(removeTitle, "Человек останется в дереве", R.drawable.ic_guide_delete, () -> {
                dialog.dismiss();
                if (!activity.requireEditingEnabled()) return;
                List<String> ids = folderId == null
                    ? activity.state.photoAlbums.get(albumName)
                    : findFolder(albumName, folderId).personIds;
                if (ids != null) ids.remove(person.id);
                activity.saveOnly();
                if (parent != null) parent.dismiss();
                if (folderId == null) showCustomAlbum(albumName); else showFolder(albumName, folderId);
            }));
        }
        dialog.show();
    }

    private void createFolder(String albumName, Dialog parent) {
        if (!activity.requireEditingEnabled()) return;
        Dialog dialog = styledChoiceDialog("Новая папка", "Название можно изменить позже", R.drawable.ic_menu_add_box);
        LinearLayout host = dialogHost(dialog);
        EditText input = albumNameField("Название папки", "");
        host.addView(input, fieldParams());
        host.addView(dialogActions("Отмена", dialog::dismiss, "Создать", () -> {
            String name = value(input.getText().toString());
            if (name.isEmpty()) name = "Новая папка";
            albumFolders(albumName).add(new PhotoAlbumFolder(name));
            activity.saveOnly();
            dialog.dismiss();
            if (parent != null) parent.dismiss();
            showCustomAlbum(albumName);
        }));
        dialog.show();
    }

    private void renameAlbum(String oldName) {
        if (!activity.requireEditingEnabled()) return;
        Dialog dialog = styledChoiceDialog("Переименовать альбом", "Новое название сохранит всё содержимое", R.drawable.ic_menu_edit);
        LinearLayout host = dialogHost(dialog);
        EditText input = albumNameField("Название альбома", oldName);
        host.addView(input, fieldParams());
        host.addView(dialogActions("Отмена", dialog::dismiss, "Сохранить", () -> {
            String newName = value(input.getText().toString());
            if (newName.isEmpty() || newName.equals(oldName)) {
                dialog.dismiss();
                return;
            }
            if (activity.state.photoAlbums.containsKey(newName)) {
                activity.toast("Альбом с таким названием уже существует");
                return;
            }
            activity.state.photoAlbums.put(newName, activity.state.photoAlbums.remove(oldName));
            moveMapEntry(activity.state.photoAlbumMedia, oldName, newName);
            List<PhotoAlbumFolder> folders = activity.state.photoAlbumFolders.remove(oldName);
            if (folders != null) activity.state.photoAlbumFolders.put(newName, folders);
            activity.saveOnly();
            dialog.dismiss();
            refresh();
        }));
        dialog.show();
    }

    private void renameFolder(String albumName, PhotoAlbumFolder folder, Dialog parent) {
        if (!activity.requireEditingEnabled()) return;
        Dialog dialog = styledChoiceDialog("Переименовать папку", "Содержимое останется на месте", R.drawable.ic_menu_edit);
        LinearLayout host = dialogHost(dialog);
        EditText input = albumNameField("Название папки", folder.name);
        host.addView(input, fieldParams());
        host.addView(dialogActions("Отмена", dialog::dismiss, "Сохранить", () -> {
            String name = value(input.getText().toString());
            if (!name.isEmpty()) folder.name = name;
            activity.saveOnly();
            dialog.dismiss();
            if (parent != null) parent.dismiss();
            showCustomAlbum(albumName);
        }));
        dialog.show();
    }

    private void renamePerson(Person person, Dialog parent) {
        if (!activity.requireEditingEnabled()) return;
        Dialog dialog = styledChoiceDialog("Изменить ФИО", "Имя обновится во всём дереве", R.drawable.ic_menu_edit);
        LinearLayout host = dialogHost(dialog);
        EditText input = albumNameField("Фамилия Имя Отчество", person.name);
        host.addView(input, fieldParams());
        host.addView(dialogActions("Отмена", dialog::dismiss, "Сохранить", () -> {
            String name = value(input.getText().toString());
            if (!name.isEmpty()) person.name = name;
            activity.saveOnly();
            activity.bindState();
            dialog.dismiss();
            if (parent != null) parent.dismiss();
            showPersonGallery(person);
        }));
        dialog.show();
    }

    private void confirmDeleteFolder(String albumName, PhotoAlbumFolder folder, Dialog parent) {
        showConfirmation("Удалить папку?", "Папка и добавленные в неё фото исчезнут из альбома.", "Удалить", () -> {
                if (!activity.requireEditingEnabled()) return;
                albumFolders(albumName).remove(folder);
                activity.saveOnly();
                if (parent != null) parent.dismiss();
                showCustomAlbum(albumName);
            });
    }

    private void confirmRemovePhoto(Runnable remove) {
        showConfirmation("Убрать фотографию?", "Она перестанет отображаться в этой подборке.", "Убрать", () -> {
                if (activity.requireEditingEnabled()) remove.run();
            });
    }

    private void removeMedia(String type, String albumName, String itemId, String mediaId) {
        List<String> media = mediaTarget(type, albumName, itemId);
        if (media != null) media.remove(mediaId);
        activity.saveOnly();
        if (openPhotoDialog != null) openPhotoDialog.dismiss();
        reopenPhotoTarget(type, albumName, itemId);
    }

    private EditText albumNameField(String hint, String value) {
        EditText input = activity.field(hint);
        input.setSingleLine(true);
        input.setText(value);
        input.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_image, 0, 0, 0);
        input.setCompoundDrawablePadding(dp(10));
        activity.tintDrawables(input, Color.rgb(8, 122, 115));
        input.setBackground(activity.panelBg(Color.rgb(248, 251, 252), dp(14), Color.rgb(217, 224, 229)));
        return input;
    }

    private LinearLayout.LayoutParams fieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(56));
        params.setMargins(0, dp(4), 0, dp(12));
        return params;
    }

    private String uniqueAlbumName(String requested) {
        String base = value(requested);
        if (base.isEmpty()) base = "Новый альбом";
        String candidate = base;
        int suffix = 2;
        while (activity.state.photoAlbums.containsKey(candidate)) candidate = base + " " + suffix++;
        return candidate;
    }

    private List<String> albumMedia(String name) {
        return activity.state.photoAlbumMedia.computeIfAbsent(name, key -> new ArrayList<>());
    }

    private List<PhotoAlbumFolder> albumFolders(String name) {
        return activity.state.photoAlbumFolders.computeIfAbsent(name, key -> new ArrayList<>());
    }

    private PhotoAlbumFolder findFolder(String albumName, String folderId) {
        for (PhotoAlbumFolder folder : albumFolders(albumName)) if (folder.id.equals(folderId)) return folder;
        return null;
    }

    private static void moveMapEntry(Map<String, List<String>> map, String oldKey, String newKey) {
        List<String> value = map.remove(oldKey);
        if (value != null) map.put(newKey, value);
    }

    private void choosePersonForPhotos(Dialog parent) {
        Dialog dialog = fullDialog("Кому добавить фото");
        LinearLayout host = dialogHost(dialog);
        EditText search = albumNameField("Поиск по имени", "");
        search.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_search, 0, 0, 0);
        host.addView(search, fieldParams());
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        host.addView(rows, new LinearLayout.LayoutParams(-1, -2));
        Runnable render = () -> {
            rows.removeAllViews();
            String needle = value(search.getText().toString()).toLowerCase(Locale.ROOT);
            int count = 0;
            for (Person person : activity.state.people.values()) {
                if (!needle.isEmpty() && !displayName(person).toLowerCase(Locale.ROOT).contains(needle)) continue;
                count++;
                LinearLayout row = new LinearLayout(activity);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8), dp(6), dp(8), dp(6));
                row.setBackground(activity.panelBg(Color.WHITE, dp(14), Color.rgb(217, 224, 229)));
                row.addView(avatar(person, 52), new LinearLayout.LayoutParams(dp(48), dp(48)));
                TextView name = text(displayName(person), 12, Color.rgb(28, 34, 38), true);
                name.setPadding(dp(10), 0, 0, 0);
                row.addView(name, new LinearLayout.LayoutParams(0, dp(48), 1));
                TextView arrow = text("›", 24, Color.rgb(8, 122, 115), false);
                arrow.setGravity(Gravity.CENTER);
                row.addView(arrow, new LinearLayout.LayoutParams(dp(40), dp(48)));
                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (parent != null) parent.dismiss();
                    openAlbumPhotoPicker("person", "", person.id);
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(62));
                params.setMargins(0, 0, 0, dp(7));
                rows.addView(row, params);
            }
            if (count == 0) rows.addView(emptyState("Ничего не найдено", "Попробуйте изменить запрос"));
        };
        search.addTextChangedListener(new SimpleTextWatcher(render));
        render.run();
        dialog.show();
    }

    private void openAlbumPhotoPicker(String target, String albumName, String itemId) {
        if (!activity.requireEditingEnabled()) return;
        pendingPhotoTarget = target;
        pendingPhotoAlbum = value(albumName);
        pendingPhotoItem = value(itemId);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        activity.startActivityForResult(intent, MainActivity.REQ_ALBUM_PHOTOS);
    }

    void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != MainActivity.REQ_ALBUM_PHOTOS || resultCode != Activity.RESULT_OK || data == null) return;
        if (!activity.requireEditingEnabled()) return;
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) for (int i = 0; i < clip.getItemCount() && uris.size() < 60; i++) {
            Uri uri = clip.getItemAt(i).getUri();
            if (uri != null && !uris.contains(uri)) uris.add(uri);
        }
        if (data.getData() != null && !uris.contains(data.getData())) uris.add(data.getData());
        if (uris.isEmpty()) return;
        final String target = pendingPhotoTarget;
        final String albumName = pendingPhotoAlbum;
        final String itemId = pendingPhotoItem;
        activity.toast(uris.size() == 1 ? "Добавление фотографии…" : "Добавление фотографий…");
        avatarDecoder.execute(() -> importAlbumPhotos(uris, target, albumName, itemId));
    }

    private void importAlbumPhotos(List<Uri> uris, String target, String albumName, String itemId) {
        List<String> imported = new ArrayList<>();
        int failed = 0;
        for (Uri uri : uris) {
            try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("Фото не открыто");
                String mime = activity.getContentResolver().getType(uri);
                TreeMediaStore.StoredMedia media = activity.store.mediaStore().importPhoto(input, displayName(uri), mime);
                if (!imported.contains(media.id)) imported.add(media.id);
            } catch (Exception ignored) {
                failed++;
            }
        }
        final int failedCount = failed;
        mainHandler.post(() -> {
            if (imported.isEmpty()) {
                activity.toast("Не удалось добавить фотографии");
                return;
            }
            List<String> destination = mediaTarget(target, albumName, itemId);
            if (destination == null) {
                activity.toast("Папка больше не существует");
                return;
            }
            for (String mediaId : imported) if (!destination.contains(mediaId)) destination.add(mediaId);
            activity.saveOnly();
            if (openPhotoDialog != null) openPhotoDialog.dismiss();
            reopenPhotoTarget(target, albumName, itemId);
            String message = imported.size() + " фото добавлено";
            if (failedCount > 0) message += " · " + failedCount + " пропущено";
            activity.toast(message);
        });
    }

    private List<String> mediaTarget(String target, String albumName, String itemId) {
        if (activity.state == null) return null;
        if ("album".equals(target)) return activity.state.photoAlbumMedia.computeIfAbsent(albumName, key -> new ArrayList<>());
        if ("family".equals(target)) return activity.state.familyAlbumMedia.computeIfAbsent(albumName, key -> new ArrayList<>());
        if ("person".equals(target)) return activity.state.personAlbumMedia.computeIfAbsent(itemId, key -> new ArrayList<>());
        if ("folder".equals(target)) {
            PhotoAlbumFolder folder = findFolder(albumName, itemId);
            return folder == null ? null : folder.photoMediaIds;
        }
        return null;
    }

    private void reopenPhotoTarget(String target, String albumName, String itemId) {
        if ("album".equals(target)) showCustomAlbum(albumName);
        else if ("folder".equals(target)) showFolder(albumName, itemId);
        else if ("person".equals(target)) showPersonGallery(activity.state.people.get(itemId));
        else if ("family".equals(target)) {
            List<Person> people = familyGroups(true).getOrDefault(albumName, Collections.emptyList());
            showFamilyAlbum(albumName, people);
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = activity.getContentResolver().query(
            uri,
            new String[]{OpenableColumns.DISPLAY_NAME},
            null,
            null,
            null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (!value(name).isEmpty()) return name.length() > 180 ? name.substring(0, 180) : name;
            }
        } catch (Exception ignored) {
        }
        String name = uri == null ? "photo.jpg" : value(uri.getLastPathSegment());
        return name.isEmpty() ? "photo.jpg" : name;
    }

    private void chooseFamily(Dialog current) {
        Set<String> all = new LinkedHashSet<>();
        for (Person person : activity.state.people.values()) {
            String surname = TreeState.surnameOf(person.name);
            if (!surname.isEmpty()) all.add(surname);
        }
        Set<String> manual = loadManualFamilies();
        List<String> choices = new ArrayList<>();
        for (String surname : all) if (!familyGroups(true).containsKey(surname)) choices.add(surname);
        if (choices.isEmpty()) {
            activity.toast("Все существующие семьи уже добавлены");
            return;
        }
        Dialog dialog = fullDialog("Добавить семью");
        LinearLayout host = dialogHost(dialog);
        host.addView(text("Выберите фамилию из дерева", 10, Color.rgb(101, 113, 122), false),
            new LinearLayout.LayoutParams(-1, dp(36)));
        for (String surname : choices) {
            host.addView(menuAction(capitalize(surname), "Создать семейную папку", R.drawable.ic_menu_family_color, () -> {
                manual.add(surname);
                saveManualFamilies(manual);
                current.dismiss();
                dialog.dismiss();
                showFamilies();
            }));
        }
        dialog.show();
    }

    private Map<String, List<Person>> familyGroups(boolean includeManual) {
        Map<String, List<Person>> result = new LinkedHashMap<>();
        for (Person person : activity.state.people.values()) {
            if (!hasGalleryPhoto(person)) continue;
            String surname = TreeState.surnameOf(person.name);
            if (surname.isEmpty()) surname = "Без фамилии";
            result.computeIfAbsent(surname, key -> new ArrayList<>()).add(person);
        }
        if (includeManual) {
            for (String surname : loadManualFamilies()) {
                List<Person> people = result.computeIfAbsent(surname, key -> new ArrayList<>());
                for (Person person : activity.state.people.values()) {
                    if (surname.equals(TreeState.surnameOf(person.name)) && hasGalleryPhoto(person) && !people.contains(person)) people.add(person);
                }
            }
        }
        return result;
    }

    private List<Person> avatarPeople() {
        List<Person> result = new ArrayList<>();
        if (activity.state != null) for (Person person : activity.state.people.values()) if (hasGalleryPhoto(person)) result.add(person);
        result.sort(personComparator());
        return result;
    }

    private List<Person> peopleForIds(List<String> ids) {
        List<Person> result = new ArrayList<>();
        if (activity.state == null) return result;
        for (String id : ids) {
            Person person = activity.state.people.get(id);
            if (person != null && hasGalleryPhoto(person)) result.add(person);
        }
        return result;
    }

    private View avatar(Person person, int sizeDp) {
        FrameLayout frame = new FrameLayout(activity);
        frame.setClipToOutline(true);
        frame.setBackground(activity.colorSwatchBg(person.color, dp(16)));
        TextView initials = text(activity.personInitials(person.name), 13, Color.WHITE, true);
        initials.setGravity(Gravity.CENTER);
        frame.addView(initials, new FrameLayout.LayoutParams(-1, -1));
        String mediaId = value(person.photoMediaId);
        String legacyPhoto = value(person.photo);
        if (mediaId.isEmpty() && legacyPhoto.isEmpty()) return frame;
        String key = (mediaId.isEmpty() ? "legacy:" + legacyPhoto.hashCode() : mediaId)
            + ":" + sizeDp + ":" + person.avatarScale + ":" + person.avatarOffsetX + ":" + person.avatarOffsetY;
        Bitmap cached = avatarCache.get(key);
        if (cached != null) {
            addAvatarBitmap(frame, cached, person);
            return frame;
        }
        avatarDecoder.execute(() -> {
            Bitmap bitmap = mediaId.isEmpty()
                ? activity.bitmapFromDataUrl(legacyPhoto)
                : activity.store.mediaStore().decodeBitmap(mediaId, dp(sizeDp));
            if (bitmap == null) return;
            avatarCache.put(key, bitmap);
            mainHandler.post(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) addAvatarBitmap(frame, bitmap, person);
            });
        });
        return frame;
    }

    private void addAvatarBitmap(FrameLayout frame, Bitmap bitmap, Person person) {
        ImageView image = new ImageView(activity);
        AvatarTransform.apply(image, bitmap, person);
        frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
    }

    private static Collator createPeopleCollator() {
        Collator collator = Collator.getInstance(new Locale("ru"));
        collator.setStrength(Collator.PRIMARY);
        return collator;
    }

    private View placeholderAvatar(String value) {
        TextView view = text(value.isEmpty() ? "?" : value.substring(0, 1).toUpperCase(Locale.ROOT), 24, Color.WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(activity.colorSwatchBg(TreeState.colorFor(value, 0), dp(14)));
        return view;
    }

    private Dialog fullDialog(String title) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(14), dp(12), dp(14), dp(12));
        shell.setBackgroundColor(AppThemePalette.surface(Color.rgb(243, 247, 248)));
        LinearLayout top = new LinearLayout(activity);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(title, 18, Color.rgb(28, 34, 38), true), new LinearLayout.LayoutParams(0, dp(48), 1));
        Button close = activity.iconButton(R.drawable.ic_menu_close, v -> dialog.dismiss());
        top.addView(close, new LinearLayout.LayoutParams(dp(46), dp(46)));
        shell.addView(top);
        ScrollView scroll = new ScrollView(activity);
        LinearLayout host = new LinearLayout(activity);
        host.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(host);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        FrameLayout root = new FrameLayout(activity);
        root.setTag(host);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
        return dialog;
    }

    private LinearLayout dialogHost(Dialog dialog) {
        View contentRoot = dialog.findViewById(android.R.id.content);
        if (contentRoot instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) contentRoot;
            if (group.getChildCount() > 0 && group.getChildAt(0).getTag() instanceof LinearLayout) {
                return (LinearLayout) group.getChildAt(0).getTag();
            }
        }
        throw new IllegalStateException("Photo dialog host is missing");
    }

    private LinearLayout dialogBody() {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(10), dp(8), dp(10), dp(8));
        return body;
    }

    private Dialog styledChoiceDialog(String title, String subtitle, int iconRes) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(14), dp(14), dp(14), dp(14));
        shell.setBackground(activity.panelBg(Color.rgb(248, 251, 252), dp(24), Color.argb(70, 24, 169, 153)));

        LinearLayout top = new LinearLayout(activity);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setColorFilter(Color.WHITE);
        icon.setPadding(dp(11), dp(11), dp(11), dp(11));
        icon.setBackground(activity.tealGradientBg(dp(14)));
        top.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(12), 0, dp(4), 0);
        copy.addView(text(title, 17, Color.rgb(28, 34, 38), true), new LinearLayout.LayoutParams(-1, dp(27)));
        copy.addView(text(subtitle, 9, Color.rgb(101, 113, 122), false), new LinearLayout.LayoutParams(-1, dp(21)));
        top.addView(copy, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button close = activity.iconButton(R.drawable.ic_menu_close, v -> dialog.dismiss());
        close.setBackground(activity.panelBg(Color.WHITE, dp(12), Color.rgb(217, 224, 229)));
        top.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        shell.addView(top, new LinearLayout.LayoutParams(-1, dp(50)));

        LinearLayout host = new LinearLayout(activity);
        host.setOrientation(LinearLayout.VERTICAL);
        host.setPadding(0, dp(12), 0, 0);
        shell.addView(host, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout root = new FrameLayout(activity);
        root.setTag(host);
        root.addView(shell, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.32f);
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            int width = Math.min(dp(420), activity.getResources().getDisplayMetrics().widthPixels - dp(20));
            dialog.getWindow().setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(Gravity.BOTTOM);
        }
        return dialog;
    }

    private View choiceSection(String title, String[] labels, String selected, String[] values, java.util.function.Consumer<String> change) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        card.setBackground(activity.panelBg(Color.WHITE, dp(16), Color.rgb(217, 224, 229)));
        TextView label = text(title, 10, Color.rgb(8, 122, 115), true);
        label.setPadding(dp(4), 0, 0, dp(4));
        card.addView(label, new LinearLayout.LayoutParams(-1, dp(28)));
        LinearLayout row = new LinearLayout(activity);
        final TextView[] buttons = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView option = choicePill(labels[i], values[i].equals(selected));
            buttons[i] = option;
            option.setOnClickListener(v -> {
                change.accept(values[index]);
                for (int j = 0; j < buttons.length; j++) styleChoicePill(buttons[j], j == index);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1);
            params.setMargins(i == 0 ? 0 : dp(3), 0, i == labels.length - 1 ? 0 : dp(3), 0);
            row.addView(option, params);
        }
        card.addView(row, new LinearLayout.LayoutParams(-1, dp(42)));
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, dp(90));
        outer.setMargins(0, 0, 0, dp(9));
        card.setLayoutParams(outer);
        return card;
    }

    private TextView choicePill(String label, boolean selected) {
        TextView view = text(label, 10, Color.rgb(83, 94, 103), true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        styleChoicePill(view, selected);
        return view;
    }

    private void styleChoicePill(TextView view, boolean selected) {
        view.setTextColor(selected ? Color.WHITE : AppThemePalette.text(Color.rgb(83, 94, 103)));
        view.setBackground(selected
            ? activity.tealGradientBg(dp(12))
            : activity.panelBg(Color.rgb(245, 248, 249), dp(12), Color.argb(48, 63, 82, 94)));
    }

    private View dialogActions(String secondary, Runnable secondaryAction, String primary, Runnable primaryAction) {
        LinearLayout actions = new LinearLayout(activity);
        Button left = activity.actionButton(secondary, v -> secondaryAction.run());
        left.setTextColor(Color.rgb(83, 94, 103));
        left.setBackground(activity.panelBg(Color.WHITE, dp(14), Color.rgb(217, 224, 229)));
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        leftParams.setMargins(0, dp(3), dp(5), 0);
        actions.addView(left, leftParams);
        Button right = activity.actionButton(primary, v -> primaryAction.run());
        right.setTextColor(Color.WHITE);
        right.setBackground(activity.tealGradientBg(dp(14)));
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        rightParams.setMargins(dp(5), dp(3), 0, 0);
        actions.addView(right, rightParams);
        return actions;
    }

    private void showConfirmation(String title, String message, String positive, Runnable action) {
        Dialog dialog = styledChoiceDialog(title, "Подтверждение действия", R.drawable.ic_guide_delete);
        LinearLayout host = dialogHost(dialog);
        TextView copy = text(message, 11, Color.rgb(70, 83, 92), false);
        copy.setPadding(dp(14), dp(12), dp(14), dp(12));
        copy.setBackground(activity.panelBg(Color.WHITE, dp(15), Color.rgb(217, 224, 229)));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(-1, -2);
        copyParams.setMargins(0, 0, 0, dp(10));
        host.addView(copy, copyParams);
        host.addView(dialogActions("Отмена", dialog::dismiss, positive, () -> {
            dialog.dismiss();
            action.run();
        }));
        dialog.show();
    }

    private View choiceRow(String label, boolean checked, View.OnClickListener click) {
        CheckBox box = new CheckBox(activity);
        box.setText(label);
        box.setChecked(checked);
        box.setTextSize(13);
        box.setTextColor(Color.rgb(28, 34, 38));
        box.setTypeface(activity.ui());
        box.setPadding(dp(8), 0, dp(8), 0);
        box.setOnClickListener(click);
        return box;
    }

    private TextView section(String label) {
        TextView view = text(label, 9, Color.rgb(8, 122, 115), true);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private View emptyState(String title, String detail) {
        LinearLayout empty = new LinearLayout(activity);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(16), dp(30), dp(16), dp(30));
        empty.setBackground(activity.panelBg(Color.WHITE, dp(18), Color.rgb(217, 224, 229)));
        empty.addView(text(title, 15, Color.rgb(28, 34, 38), true));
        TextView sub = text(detail, 10, Color.rgb(101, 113, 122), false);
        sub.setPadding(0, dp(8), 0, 0);
        empty.addView(sub);
        return empty;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new LocalizedTextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(AppThemePalette.text(color));
        view.setTypeface(bold ? activity.uiBold() : activity.ui());
        view.setIncludeFontPadding(false);
        return view;
    }

    private boolean hasPhoto(Person person) {
        return person != null && ((!value(person.photoMediaId).isEmpty() && activity.store.mediaStore().exists(person.photoMediaId))
            || !value(person.photo).isEmpty());
    }

    private boolean hasGalleryPhoto(Person person) {
        return person != null && (hasPhoto(person)
            || !activity.state.personAlbumMedia.getOrDefault(person.id, Collections.emptyList()).isEmpty());
    }

    private String displayName(Person person) {
        return value(person.name).isEmpty() ? "Без имени" : person.name.trim();
    }

    private String years(Person person) {
        String born = value(person.bornYear).isEmpty() ? value(person.born) : value(person.bornYear);
        String died = value(person.diedYear).isEmpty() ? value(person.died) : value(person.diedYear);
        if (born.isEmpty() && died.isEmpty()) return "";
        return born + "–" + died;
    }

    private static String value(String value) { return value == null ? "" : value.trim(); }
    private static int year(String value) { try { return Integer.parseInt(value(value).replaceAll("[^0-9]", "")); } catch (Exception ignored) { return 0; } }
    private static String capitalize(String value) { return value.isEmpty() ? value : value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1); }
    private String peopleCountText(int count) {
        return AppLanguage.isEnglish(activity)
            ? count + " people in the family tree"
            : count + " человек в семейном дереве";
    }
    private int dp(int value) { return activity.dp(value); }

    private Set<String> loadManualFamilies() {
        return activity.state == null
            ? new LinkedHashSet<>()
            : new LinkedHashSet<>(activity.state.familyAlbums);
    }

    private void saveManualFamilies(Set<String> values) {
        if (activity.state == null || !activity.requireEditingEnabled()) return;
        activity.state.familyAlbums.clear();
        activity.state.familyAlbums.addAll(values);
        activity.saveOnly();
    }

    private Map<String, List<String>> loadAlbums() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (activity.state != null) for (Map.Entry<String, List<String>> album : activity.state.photoAlbums.entrySet()) {
            result.put(album.getKey(), new ArrayList<>(album.getValue()));
        }
        return result;
    }

    private void saveAlbums(Map<String, List<String>> albums) {
        if (activity.state == null) return;
        activity.state.photoAlbums.clear();
        for (Map.Entry<String, List<String>> album : albums.entrySet()) {
            activity.state.photoAlbums.put(album.getKey(), new ArrayList<>(album.getValue()));
        }
        activity.saveOnly();
    }

    private static final class SimpleTextWatcher implements android.text.TextWatcher {
        private final Runnable changed;
        SimpleTextWatcher(Runnable changed) { this.changed = changed; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { changed.run(); }
        @Override public void afterTextChanged(android.text.Editable s) { }
    }

    private static final class SquareFrame extends FrameLayout {
        SquareFrame(android.content.Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int size = MeasureSpec.getSize(widthMeasureSpec);
            int exact = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
            super.onMeasure(exact, exact);
        }
    }
}
