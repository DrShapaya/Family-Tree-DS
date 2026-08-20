package ru.drshapaya.androidft2;

import android.app.Activity;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BirthdayWidgetConfigureActivity extends Activity {
    private static final int PAGE = Color.rgb(0, 0, 0);
    private static final int CARD = Color.rgb(28, 28, 30);
    private static final int PRIMARY = Color.rgb(246, 246, 248);
    private static final int SECONDARY = Color.rgb(166, 166, 174);
    private static final int ACCENT = Color.rgb(61, 133, 255);
    private static final int[] PRESET_COLORS = {
        0xff111113,
        0xffefba76,
        0xffffdf79,
        0xff50ca91,
        0xff88c3ef
    };

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private BirthdayWidgetSettings settings;
    private BirthdayCalculator.Result birthday;
    private TreeState treeState;
    private ImageView previewBackground;
    private ImageView previewPhoto;
    private TextView previewName;
    private TextView previewDate;
    private TextView previewNumber;
    private TextView previewDays;
    private TextView configuredPersonName;
    private TextView configuredPersonDate;
    private TextView transparencyValue;
    private LinearLayout backgroundControls;
    private View colorButton;
    private View autoColorIndicator;
    private View manualColorIndicator;
    private TextView personSelectionValue;
    private final List<TextView> colorSwatches = new ArrayList<>();

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(AppLanguage.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppThemeDark);
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        setResult(RESULT_CANCELED);
        appWidgetId = getIntent().getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        settings = BirthdayWidgetSettings.load(this, appWidgetId);
        treeState = new TreeStore(this).load();
        birthday = BirthdayCalculator.nearest(treeState, settings.excludedPersonIds);
        buildUi();
        updatePreview();
    }

    private void buildUi() {
        WallpaperPageLayout page = new WallpaperPageLayout(this);
        setContentView(page);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(92));
        scroll.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> page.invalidate());
        page.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(14), dp(20), dp(20));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = label(text("Настройки виджета", "Widget settings"), 30, true, PRIMARY);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.setMargins(dp(10), dp(4), dp(10), dp(12));
        root.addView(title, titleParams);

        FrameLayout previewCard = new FrameLayout(this);
        previewCard.setBackground(rounded(Color.TRANSPARENT, dp(30)));
        previewCard.setClipToOutline(true);
        page.setWallpaperHole(previewCard);
        LinearLayout.LayoutParams previewCardParams = new LinearLayout.LayoutParams(-1, dp(190));
        previewCardParams.setMargins(0, 0, 0, dp(10));
        root.addView(previewCard, previewCardParams);
        buildPreview(previewCard);

        LinearLayout personCard = card();
        configuredPersonName = label(
            BirthdayWidgetRenderer.personName(this, birthday),
            18,
            false,
            PRIMARY);
        personCard.addView(configuredPersonName, rowParams(0, 0, 0, 5));
        configuredPersonDate = label(BirthdayWidgetRenderer.date(this, birthday), 14, false, ACCENT);
        personCard.addView(configuredPersonDate, rowParams(0, 0, 0, 0));
        root.addView(personCard, cardParams());

        LinearLayout peopleCard = card();
        LinearLayout peopleRow = new LinearLayout(this);
        peopleRow.setOrientation(LinearLayout.HORIZONTAL);
        peopleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout peopleLabels = new LinearLayout(this);
        peopleLabels.setOrientation(LinearLayout.VERTICAL);
        peopleLabels.addView(label(text("Кого показывать", "People to show"), 17, false, PRIMARY));
        personSelectionValue = label(personSelectionSummary(), 13, false, SECONDARY);
        peopleLabels.addView(personSelectionValue);
        peopleRow.addView(peopleLabels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView changePeople = label(text("Изменить", "Change"), 15, true, ACCENT);
        changePeople.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        peopleRow.addView(changePeople, new LinearLayout.LayoutParams(dp(92), dp(42)));
        peopleRow.setOnClickListener(v -> showPersonFilterDialog());
        peopleCard.addView(peopleRow, new LinearLayout.LayoutParams(-1, -2));
        root.addView(peopleCard, cardParams());

        LinearLayout appearanceCard = card();
        appearanceCard.addView(label(text("Оформление", "Appearance"), 18, true, PRIMARY), rowParams(0, 0, 0, 2));
        appearanceCard.addView(
            label(text("Цвет, прозрачность и фотография", "Color, transparency and photo"), 13, false, SECONDARY),
            rowParams(0, 0, 0, 7));

        LinearLayout backgroundRow = new LinearLayout(this);
        backgroundRow.setOrientation(LinearLayout.HORIZONTAL);
        backgroundRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout backgroundLabels = new LinearLayout(this);
        backgroundLabels.setOrientation(LinearLayout.VERTICAL);
        backgroundLabels.addView(label(text("Фон", "Background"), 17, false, PRIMARY));
        backgroundLabels.addView(label(
            text("Выключите для полностью прозрачного виджета", "Turn off for a fully transparent widget"),
            13,
            false,
            SECONDARY));
        backgroundRow.addView(backgroundLabels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch backgroundSwitch = oneUiSwitch(settings.backgroundEnabled);
        backgroundSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            settings.backgroundEnabled = checked;
            updateBackgroundControls();
            updatePreview();
        });
        backgroundRow.addView(backgroundSwitch, new LinearLayout.LayoutParams(dp(56), dp(48)));
        appearanceCard.addView(backgroundRow, rowParams(0, 0, 0, 0));

        backgroundControls = new LinearLayout(this);
        backgroundControls.setOrientation(LinearLayout.VERTICAL);
        backgroundControls.addView(divider(), dividerParams(5));

        LinearLayout autoRow = new LinearLayout(this);
        autoRow.setOrientation(LinearLayout.HORIZONTAL);
        autoRow.setGravity(Gravity.CENTER_VERTICAL);
        autoRow.setPadding(0, dp(2), 0, dp(2));
        autoColorIndicator = selectionIndicator(settings.autoColor);
        autoRow.addView(autoColorIndicator, indicatorParams());
        LinearLayout autoLabels = new LinearLayout(this);
        autoLabels.setOrientation(LinearLayout.VERTICAL);
        autoLabels.addView(label(text("Автоцвет", "Automatic color"), 17, false, PRIMARY));
        autoLabels.addView(label(
            text("Использовать цвет карточки родственника", "Use the relative card color"),
            13,
            false,
            SECONDARY));
        autoRow.addView(autoLabels, new LinearLayout.LayoutParams(0, -2, 1));
        autoRow.setOnClickListener(v -> setAutoColor(true));
        backgroundControls.addView(autoRow, rowParams(0, 0, 0, 0));
        backgroundControls.addView(divider(), dividerParams(4));

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        colorRow.setPadding(0, dp(2), 0, dp(2));
        manualColorIndicator = selectionIndicator(!settings.autoColor);
        colorRow.addView(manualColorIndicator, indicatorParams());
        colorRow.addView(label(text("Цвет", "Color"), 17, false, PRIMARY), new LinearLayout.LayoutParams(0, -2, 1));
        colorRow.setOnClickListener(v -> setAutoColor(false));
        backgroundControls.addView(colorRow, rowParams(0, 0, 0, 2));

        LinearLayout paletteRow = new LinearLayout(this);
        paletteRow.setOrientation(LinearLayout.HORIZONTAL);
        paletteRow.setGravity(Gravity.CENTER_VERTICAL);
        paletteRow.setPadding(0, dp(2), 0, dp(2));
        for (int presetColor : PRESET_COLORS) {
            TextView swatch = colorSwatch(presetColor);
            colorSwatches.add(swatch);
            FrameLayout slot = new FrameLayout(this);
            slot.addView(swatch, new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER));
            paletteRow.addView(slot, new LinearLayout.LayoutParams(0, dp(44), 1));
        }
        colorButton = new PaletteButtonView(this);
        colorButton.setContentDescription(text("Выбрать цвет", "Choose color"));
        colorButton.setOnClickListener(v -> showColorDialog());
        FrameLayout paletteSlot = new FrameLayout(this);
        paletteSlot.addView(colorButton, new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER));
        paletteRow.addView(paletteSlot, new LinearLayout.LayoutParams(0, dp(44), 1));
        backgroundControls.addView(paletteRow, rowParams(0, 0, 0, 0));
        backgroundControls.addView(divider(), dividerParams(6));

        LinearLayout transparencyHeader = new LinearLayout(this);
        transparencyHeader.setOrientation(LinearLayout.HORIZONTAL);
        transparencyHeader.setGravity(Gravity.CENTER_VERTICAL);
        transparencyHeader.addView(
            label(text("Прозрачность", "Transparency"), 16, false, PRIMARY),
            new LinearLayout.LayoutParams(0, -2, 1));
        transparencyValue = label(percent(settings.transparency), 14, true, ACCENT);
        transparencyValue.setGravity(Gravity.END);
        transparencyHeader.addView(transparencyValue, new LinearLayout.LayoutParams(dp(60), -2));
        backgroundControls.addView(transparencyHeader, rowParams(0, 0, 0, 2));

        SeekBar transparency = new SeekBar(this);
        transparency.setMax(100);
        transparency.setProgress(settings.transparency);
        transparency.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        transparency.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        transparency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                settings.transparency = progress;
                transparencyValue.setText(percent(progress));
                updatePreview();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        LinearLayout transparencyRow = new LinearLayout(this);
        transparencyRow.setOrientation(LinearLayout.HORIZONTAL);
        transparencyRow.setGravity(Gravity.CENTER_VERTICAL);
        View transparencyIcon = new CheckerboardView(this);
        LinearLayout.LayoutParams transparencyIconParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        transparencyIconParams.setMargins(dp(3), 0, dp(12), 0);
        transparencyRow.addView(transparencyIcon, transparencyIconParams);
        transparencyRow.addView(transparency, new LinearLayout.LayoutParams(0, dp(48), 1));
        backgroundControls.addView(transparencyRow, new LinearLayout.LayoutParams(-1, dp(40)));
        appearanceCard.addView(backgroundControls, new LinearLayout.LayoutParams(-1, -2));
        appearanceCard.addView(divider(), dividerParams(3));

        LinearLayout photoRow = new LinearLayout(this);
        photoRow.setOrientation(LinearLayout.HORIZONTAL);
        photoRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout photoLabels = new LinearLayout(this);
        photoLabels.setOrientation(LinearLayout.VERTICAL);
        photoLabels.addView(label(text("Показывать фотографию", "Show photo"), 16, false, PRIMARY));
        photoLabels.addView(label(
            text("Фото размещается ниже строки ФИО", "Photo stays below the name line"),
            13,
            false,
            SECONDARY));
        photoRow.addView(photoLabels, new LinearLayout.LayoutParams(0, -2, 1));
        Switch showPhoto = oneUiSwitch(settings.showPhoto);
        showPhoto.setOnCheckedChangeListener((buttonView, checked) -> {
            settings.showPhoto = checked;
            updatePreview();
        });
        photoRow.addView(showPhoto, new LinearLayout.LayoutParams(dp(56), dp(48)));
        appearanceCard.addView(photoRow, rowParams(0, 1, 0, 0));
        root.addView(appearanceCard, cardParams());
        updateBackgroundControls();

        TextView hint = label(
            text(
                "Доступны четыре варианта в списке One UI: 2×1, 2×2, 3×2 и 4×2. Каждый можно дополнительно растягивать.",
                "Four One UI presets are available: 2×1, 2×2, 3×2 and 4×2. Each can still be resized."),
            13,
            false,
            SECONDARY);
        hint.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.setMargins(dp(10), 0, dp(10), dp(14));
        root.addView(hint, hintParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(dp(6), dp(6), dp(6), dp(6));
        actions.setBackground(rounded(Color.rgb(52, 52, 56), dp(32)));

        Button cancel = actionButton(text("Отмена", "Cancel"), false);
        cancel.setOnClickListener(v -> finish());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1));
        View actionDivider = new View(this);
        actionDivider.setBackgroundColor(Color.rgb(91, 91, 97));
        actions.addView(actionDivider, new LinearLayout.LayoutParams(dp(1), dp(26)));
        Button save = actionButton(text("Сохранить", "Save"), true);
        save.setOnClickListener(v -> saveWidget());
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(52), 1));

        FrameLayout.LayoutParams actionsParams = new FrameLayout.LayoutParams(dp(332), dp(64), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        actionsParams.setMargins(0, 0, 0, dp(18));
        page.addView(actions, actionsParams);
    }

    private void buildPreview(FrameLayout host) {
        FrameLayout widget = new FrameLayout(this);
        FrameLayout.LayoutParams widgetParams = new FrameLayout.LayoutParams(-1, dp(166), Gravity.CENTER);
        host.addView(widget, widgetParams);

        previewBackground = new ImageView(this);
        previewBackground.setScaleType(ImageView.ScaleType.FIT_XY);
        widget.addView(previewBackground, new FrameLayout.LayoutParams(-1, -1));

        previewName = label("", 18, true, Color.BLACK);
        previewName.setSingleLine(false);
        previewName.setMaxLines(2);
        FrameLayout.LayoutParams nameParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.START);
        nameParams.setMargins(dp(19), dp(17), dp(19), 0);
        widget.addView(previewName, nameParams);

        previewDate = label("", 13, false, Color.DKGRAY);
        FrameLayout.LayoutParams dateParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        dateParams.setMargins(dp(19), dp(66), dp(19), 0);
        widget.addView(previewDate, dateParams);

        LinearLayout count = new LinearLayout(this);
        count.setOrientation(LinearLayout.HORIZONTAL);
        count.setGravity(Gravity.BOTTOM);
        previewNumber = label("", 46, true, Color.BLACK);
        previewDays = label("", 15, true, Color.DKGRAY);
        count.addView(previewNumber);
        LinearLayout.LayoutParams daysParams = new LinearLayout.LayoutParams(-2, -2);
        daysParams.setMargins(dp(7), 0, 0, dp(5));
        count.addView(previewDays, daysParams);
        FrameLayout.LayoutParams countParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        countParams.setMargins(dp(19), 0, 0, dp(17));
        widget.addView(count, countParams);

        previewPhoto = new ImageView(this);
        previewPhoto.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams photoParams = new FrameLayout.LayoutParams(dp(92), dp(92), Gravity.BOTTOM | Gravity.END);
        photoParams.setMargins(0, 0, dp(13), dp(11));
        widget.addView(previewPhoto, photoParams);
    }

    private void updatePreview() {
        if (previewBackground == null) return;
        previewBackground.setImageBitmap(BirthdayWidgetRenderer.renderBackground(this, birthday, settings, 330, 166));
        int primary = BirthdayWidgetRenderer.primaryTextColor(birthday, settings);
        int secondary = BirthdayWidgetRenderer.secondaryTextColor(birthday, settings);
        previewName.setText(BirthdayWidgetRenderer.personName(this, birthday));
        previewDate.setText(BirthdayWidgetRenderer.date(this, birthday));
        previewNumber.setText(BirthdayWidgetRenderer.number(this, birthday));
        previewDays.setText(BirthdayWidgetRenderer.daysLabel(this, birthday));
        previewName.setTextColor(primary);
        previewNumber.setTextColor(primary);
        previewDate.setTextColor(secondary);
        previewDays.setTextColor(secondary);
        Bitmap photo = settings.showPhoto
            ? BirthdayWidgetRenderer.renderPhoto(this, birthday, 92)
            : null;
        previewPhoto.setVisibility(photo == null ? View.GONE : View.VISIBLE);
        if (photo != null) previewPhoto.setImageBitmap(photo);
        updateColorButton();
    }

    private void updateBackgroundControls() {
        if (backgroundControls != null) {
            backgroundControls.setVisibility(settings.backgroundEnabled ? View.VISIBLE : View.GONE);
        }
        updateColorButton();
    }

    private void updateColorButton() {
        if (colorButton == null) return;
        updateSelectionIndicator(autoColorIndicator, settings.autoColor);
        updateSelectionIndicator(manualColorIndicator, !settings.autoColor);
        for (TextView swatch : colorSwatches) {
            Object tag = swatch.getTag();
            int color = tag instanceof Integer ? (Integer) tag : Color.TRANSPARENT;
            swatch.setBackground(swatchDrawable(color, !settings.autoColor && sameColor(color, settings.customColor)));
        }
        colorButton.setAlpha(settings.autoColor ? 0.62f : 1f);
        colorButton.setEnabled(settings.backgroundEnabled);
    }

    private void setAutoColor(boolean auto) {
        settings.autoColor = auto;
        updateColorButton();
        updatePreview();
    }

    private View selectionIndicator(boolean selected) {
        return new RadioIndicatorView(this, selected);
    }

    private void updateSelectionIndicator(View indicator, boolean selected) {
        if (indicator == null) return;
        if (indicator instanceof RadioIndicatorView) {
            ((RadioIndicatorView) indicator).setChecked(selected);
        }
    }

    private TextView colorSwatch(int color) {
        TextView swatch = new TextView(this);
        swatch.setGravity(Gravity.CENTER);
        swatch.setTag(color);
        swatch.setContentDescription(text("Выбрать готовый цвет", "Choose preset color"));
        swatch.setBackground(swatchDrawable(color, !settings.autoColor && sameColor(color, settings.customColor)));
        swatch.setOnClickListener(v -> {
            settings.customColor = color;
            setAutoColor(false);
        });
        return swatch;
    }

    private GradientDrawable swatchDrawable(int color, boolean selected) {
        GradientDrawable drawable = rounded(color, dp(24));
        drawable.setStroke(dp(selected ? 4 : 2), selected ? ACCENT : Color.rgb(89, 89, 96));
        return drawable;
    }

    private boolean sameColor(int first, int second) {
        return (first & 0x00ffffff) == (second & 0x00ffffff);
    }

    private String personSelectionSummary() {
        int total = treeState == null ? 0 : treeState.people.size();
        if (total == 0) return text("В дереве нет людей", "No people in the tree");
        int excluded = 0;
        for (String id : settings.excludedPersonIds) {
            if (treeState.people.containsKey(id)) excluded++;
        }
        int selected = total - excluded;
        if (selected == total) return text("Все родственники", "All relatives");
        if (selected == 0) return text("Никого", "Nobody");
        return text("Выбрано ", "Selected ") + selected + " / " + total;
    }

    private void showPersonFilterDialog() {
        Dialog dialog = new Dialog(this);
        List<Person> people = new ArrayList<>();
        if (treeState != null) people.addAll(treeState.people.values());
        people.sort(Comparator.comparing(this::personDisplayName, String.CASE_INSENSITIVE_ORDER));

        Set<String> selectedIds = new HashSet<>();
        for (Person person : people) {
            if (!settings.excludedPersonIds.contains(person.id)) selectedIds.add(person.id);
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(17), dp(20), dp(14));
        content.setBackground(rounded(CARD, dp(28)));

        content.addView(
            label(text("Кого показывать", "People to show"), 22, true, PRIMARY),
            rowParams(0, 0, 0, 2));
        TextView count = label("", 13, false, SECONDARY);
        content.addView(count, rowParams(0, 0, 0, 9));

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint(text("Поиск по имени", "Search by name"));
        search.setTextColor(PRIMARY);
        search.setHintTextColor(SECONDARY);
        search.setTextSize(15);
        search.setPadding(dp(15), 0, dp(15), 0);
        search.setBackground(rounded(Color.rgb(44, 44, 48), dp(18)));
        content.addView(search, new LinearLayout.LayoutParams(-1, dp(44)));

        LinearLayout quickActions = new LinearLayout(this);
        quickActions.setOrientation(LinearLayout.HORIZONTAL);
        Button selectAll = compactDialogButton(text("Выбрать всех", "Select all"));
        Button selectNone = compactDialogButton(text("Никого", "Nobody"));
        quickActions.addView(selectAll, new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams noneParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        noneParams.setMargins(dp(8), 0, 0, 0);
        quickActions.addView(selectNone, noneParams);
        LinearLayout.LayoutParams quickParams = new LinearLayout.LayoutParams(-1, dp(42));
        quickParams.setMargins(0, dp(8), 0, dp(7));
        content.addView(quickActions, quickParams);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView listScroll = new ScrollView(this);
        listScroll.setFillViewport(false);
        listScroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        content.addView(listScroll, new LinearLayout.LayoutParams(-1, dp(330)));

        Runnable refreshList = () -> {
            updatePersonFilterCount(count, selectedIds.size(), people.size());
            populatePersonFilterList(list, people, selectedIds, search.getText().toString(), count);
        };
        selectAll.setOnClickListener(v -> {
            for (Person person : people) selectedIds.add(person.id);
            refreshList.run();
        });
        selectNone.setOnClickListener(v -> {
            selectedIds.clear();
            refreshList.run();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int countValue) {
                refreshList.run();
            }
            @Override public void afterTextChanged(Editable value) {}
        });
        refreshList.run();

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = actionButton(text("Отмена", "Cancel"), false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button done = actionButton(text("Готово", "Done"), true);
        done.setOnClickListener(v -> {
            settings.excludedPersonIds.clear();
            for (Person person : people) {
                if (!selectedIds.contains(person.id)) settings.excludedPersonIds.add(person.id);
            }
            birthday = BirthdayCalculator.nearest(treeState, settings.excludedPersonIds);
            configuredPersonName.setText(BirthdayWidgetRenderer.personName(this, birthday));
            configuredPersonDate.setText(BirthdayWidgetRenderer.date(this, birthday));
            personSelectionValue.setText(personSelectionSummary());
            updatePreview();
            dialog.dismiss();
        });
        actions.addView(done, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, dp(48));
        actionsParams.setMargins(0, dp(7), 0, 0);
        content.addView(actions, actionsParams);

        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.width = Math.min(
                getResources().getDisplayMetrics().widthPixels - dp(28),
                dp(440));
            attributes.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(attributes);
        }
        dialog.show();
    }

    private void populatePersonFilterList(
        LinearLayout list,
        List<Person> people,
        Set<String> selectedIds,
        String query,
        TextView count
    ) {
        list.removeAllViews();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Person> visiblePeople = new ArrayList<>();
        for (Person person : people) {
            String name = personDisplayName(person);
            if (!normalizedQuery.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(normalizedQuery)) continue;
            visiblePeople.add(person);
        }
        for (int index = 0; index < visiblePeople.size(); index++) {
            Person person = visiblePeople.get(index);
            String name = personDisplayName(person);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(2), dp(3), dp(2), dp(3));
            CheckBox check = new CheckBox(this);
            check.setChecked(selectedIds.contains(person.id));
            check.setButtonTintList(new android.content.res.ColorStateList(
                new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
                new int[] {ACCENT, Color.rgb(112, 112, 120)}));
            row.addView(check, new LinearLayout.LayoutParams(dp(48), dp(48)));
            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(label(name, 16, false, PRIMARY));
            labels.addView(label(personBirthSummary(person), 12, false, SECONDARY));
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            check.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selectedIds.add(person.id);
                else selectedIds.remove(person.id);
                updatePersonFilterCount(count, selectedIds.size(), people.size());
            });
            row.setOnClickListener(v -> check.setChecked(!check.isChecked()));
            list.addView(row, new LinearLayout.LayoutParams(-1, dp(54)));
            if (index + 1 < visiblePeople.size()) {
                list.addView(divider(), new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
        if (visiblePeople.isEmpty()) {
            TextView empty = label(text("Никого не найдено", "No matches"), 14, false, SECONDARY);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(80)));
        }
    }

    private void updatePersonFilterCount(TextView view, int selected, int total) {
        view.setText(text("Выбрано: ", "Selected: ") + selected + " / " + total);
    }

    private String personDisplayName(Person person) {
        if (person == null || person.name == null || person.name.trim().isEmpty()) {
            return text("Без имени", "Unnamed person");
        }
        return person.name.trim();
    }

    private String personBirthSummary(Person person) {
        String day = person == null || person.bornDay == null ? "" : person.bornDay.trim();
        String month = person == null || person.bornMonth == null ? "" : person.bornMonth.trim();
        String year = person == null || person.bornYear == null ? "" : person.bornYear.trim();
        if (day.isEmpty() || month.isEmpty()) {
            return text("День и месяц не указаны", "Day and month not set");
        }
        return day + "." + month + (year.isEmpty() ? "" : "." + year);
    }

    private Button compactDialogButton(String value) {
        Button button = actionButton(value, true);
        button.setTextSize(14);
        button.setBackground(rounded(Color.rgb(44, 44, 48), dp(16)));
        return button;
    }

    private void showColorDialog() {
        Dialog dialog = new Dialog(this);
        final int originalColor = settings.customColor;
        final int[] selectedColor = {originalColor};
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(18), dp(22), dp(14));
        content.setBackground(rounded(CARD, dp(28)));
        content.addView(label(text("Выберите цвет", "Choose color"), 22, true, PRIMARY), rowParams(0, 0, 0, 10));

        TextView selectedPreview = new TextView(this);
        selectedPreview.setGravity(Gravity.CENTER);
        selectedPreview.setText("✓");
        selectedPreview.setTextColor(Color.WHITE);
        selectedPreview.setTextSize(24);
        selectedPreview.setBackground(rounded(settings.customColor, dp(32)));
        LinearLayout.LayoutParams selectedParams = new LinearLayout.LayoutParams(dp(64), dp(64));
        selectedParams.gravity = Gravity.CENTER_HORIZONTAL;
        selectedParams.setMargins(0, 0, 0, dp(8));
        content.addView(selectedPreview, selectedParams);

        HueSliderView slider = new HueSliderView(this);
        slider.setColor(originalColor);
        slider.setListener((color, fromUser) -> {
            selectedColor[0] = color;
            selectedPreview.setBackground(rounded(color, dp(32)));
        });
        content.addView(slider, new LinearLayout.LayoutParams(-1, dp(50)));

        TextView paletteHint = label(
            text("Проведите по палитре для выбора оттенка", "Slide across the palette to choose a shade"),
            13,
            false,
            SECONDARY);
        paletteHint.setGravity(Gravity.CENTER);
        content.addView(paletteHint, rowParams(0, 0, 0, 8));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = actionButton(text("Отмена", "Cancel"), false);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button done = actionButton(text("Готово", "Done"), true);
        done.setOnClickListener(v -> {
            settings.autoColor = false;
            settings.customColor = selectedColor[0];
            updateColorButton();
            updatePreview();
            dialog.dismiss();
        });
        actions.addView(done, new LinearLayout.LayoutParams(0, dp(48), 1));
        content.addView(actions, new LinearLayout.LayoutParams(-1, dp(48)));

        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.width = getResources().getDisplayMetrics().widthPixels - dp(40);
            attributes.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(attributes);
        }
        dialog.show();
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(14), dp(18), dp(14));
        card.setBackground(rounded(CARD, dp(28)));
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        return params;
    }

    private LinearLayout.LayoutParams rowParams(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams indicatorParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(30), dp(30));
        params.gravity = Gravity.CENTER_VERTICAL;
        params.setMargins(dp(3), 0, dp(15), 0);
        return params;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(58, 58, 62));
        return divider;
    }

    private LinearLayout.LayoutParams dividerParams(int verticalMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.setMargins(0, dp(verticalMargin), 0, dp(verticalMargin));
        return params;
    }

    private Button actionButton(String value, boolean emphasized) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(value);
        button.setTextSize(17);
        button.setTextColor(emphasized ? Color.rgb(130, 178, 255) : PRIMARY);
        button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private void saveWidget() {
        settings.save(this, appWidgetId);
        BirthdayWidgetProvider.update(this, appWidgetId);
        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    private TextView label(String value, int size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private android.content.res.ColorStateList switchThumbColors() {
        return new android.content.res.ColorStateList(
            new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
            new int[] {Color.WHITE, Color.rgb(205, 205, 211)});
    }

    private android.content.res.ColorStateList switchTrackColors() {
        return new android.content.res.ColorStateList(
            new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
            new int[] {ACCENT, Color.rgb(92, 92, 99)});
    }

    private Switch oneUiSwitch(boolean checked) {
        Switch result = new Switch(this);
        result.setShowText(false);
        result.setChecked(checked);
        result.setThumbTintList(switchThumbColors());
        result.setTrackTintList(switchTrackColors());
        return result;
    }

    private final class WallpaperPageLayout extends FrameLayout {
        private View wallpaperHole;
        private final Rect holeBounds = new Rect();
        private final Path holePath = new Path();

        WallpaperPageLayout(android.content.Context context) {
            super(context);
            setWillNotDraw(false);
        }

        void setWallpaperHole(View wallpaperHole) {
            this.wallpaperHole = wallpaperHole;
            invalidate();
        }

        @SuppressWarnings("deprecation")
        @Override
        protected void onDraw(Canvas canvas) {
            if (wallpaperHole == null || !wallpaperHole.isShown()) {
                canvas.drawColor(PAGE);
                return;
            }
            wallpaperHole.getDrawingRect(holeBounds);
            offsetDescendantRectToMyCoords(wallpaperHole, holeBounds);
            holePath.reset();
            holePath.addRoundRect(
                holeBounds.left,
                holeBounds.top,
                holeBounds.right,
                holeBounds.bottom,
                dp(30),
                dp(30),
                Path.Direction.CW);
            canvas.save();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                canvas.clipOutPath(holePath);
            } else {
                canvas.clipPath(holePath, Region.Op.DIFFERENCE);
            }
            canvas.drawColor(PAGE);
            canvas.restore();
        }
    }

    private final class PaletteButtonView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PaletteButtonView(android.content.Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float radius = Math.min(getWidth(), getHeight()) * 0.43f;
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            paint.setShader(new SweepGradient(
                centerX,
                centerY,
                new int[] {Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN, Color.GREEN, Color.YELLOW, Color.RED},
                null));
            canvas.drawCircle(centerX, centerY, radius, paint);
            paint.setShader(null);
            paint.setColor(Color.WHITE);
            paint.setAlpha(210);
            canvas.drawCircle(centerX - radius * 0.22f, centerY - radius * 0.22f, radius * 0.42f, paint);
            paint.setAlpha(255);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.rgb(89, 89, 96));
            canvas.drawCircle(centerX, centerY, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private final class RadioIndicatorView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean checked;

        RadioIndicatorView(android.content.Context context, boolean checked) {
            super(context);
            this.checked = checked;
        }

        void setChecked(boolean checked) {
            if (this.checked == checked) return;
            this.checked = checked;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) / 2f - dp(2);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(checked ? 3 : 2));
            paint.setColor(checked ? ACCENT : Color.rgb(112, 112, 120));
            canvas.drawCircle(centerX, centerY, radius, paint);
            if (checked) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ACCENT);
                canvas.drawCircle(centerX, centerY, radius * 0.48f, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private final class CheckerboardView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CheckerboardView(android.content.Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float radius = Math.min(getWidth(), getHeight()) / 2f;
            canvas.save();
            canvas.clipPath(circlePath(getWidth() / 2f, getHeight() / 2f, radius));
            int cell = Math.max(1, getWidth() / 5);
            for (int y = 0; y < getHeight(); y += cell) {
                for (int x = 0; x < getWidth(); x += cell) {
                    paint.setColor(((x / cell) + (y / cell)) % 2 == 0
                        ? Color.rgb(92, 92, 98)
                        : Color.rgb(177, 177, 183));
                    canvas.drawRect(x, y, x + cell, y + cell, paint);
                }
            }
            canvas.restore();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.rgb(128, 128, 135));
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius - dp(1), paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private android.graphics.Path circlePath(float centerX, float centerY, float radius) {
        android.graphics.Path path = new android.graphics.Path();
        path.addCircle(centerX, centerY, radius, android.graphics.Path.Direction.CW);
        return path;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String text(String russian, String english) {
        return AppLanguage.isEnglish(this) ? english : russian;
    }

    private String percent(int value) {
        return String.format(Locale.ROOT, "%d%%", value);
    }
}
