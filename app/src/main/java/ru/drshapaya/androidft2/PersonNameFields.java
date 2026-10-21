package ru.drshapaya.androidft2;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One visibility control; hiding a maiden name never discards its value. */
final class PersonNameFields extends LinearLayout {
    final EditText surname, given, patronymic, maiden;
    final Button maidenEye;
    private final MainActivity activity;
    private boolean maidenVisible;

    PersonNameFields(MainActivity activity, Person person) {
        super(activity); this.activity = activity;
        setOrientation(VERTICAL);
        PersonNames.migrateLegacy(person);
        String[] parts = PersonNames.parts(person);
        surname = field("Фамилия", parts[0], "person.surname");
        given = field("Имя", parts[1], "person.givenName");
        patronymic = field("Отчество", parts[2], "person.patronymic");
        maidenVisible = person.maidenNameEnabled && ("maiden".equals(person.surnameDisplay)
            || ("inherit".equals(person.surnameDisplay) && activity.state.useMaidenNames));
        caption("Девичья фамилия");
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        maiden = makeField("Девичья фамилия", person.maidenName, "person.maidenName");
        row.addView(maiden, new LinearLayout.LayoutParams(0, activity.dp(48), 1));
        maidenEye = activity.iconButton(R.drawable.ic_menu_eye_off, v -> {});
        maidenEye.setTag("person.maidenVisibility");
        LinearLayout.LayoutParams eyeParams = new LinearLayout.LayoutParams(activity.dp(44), activity.dp(48));
        eyeParams.setMargins(activity.dp(6), 0, 0, 0);
        row.addView(maidenEye, eyeParams);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, activity.dp(12));
        addView(row, params);
        refreshVisibility();
    }

    void listen(Runnable changed) {
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { changed.run(); }
            public void afterTextChanged(Editable e) {}
        };
        for (EditText field : new EditText[]{surname, given, patronymic, maiden}) field.addTextChangedListener(watcher);
        maidenEye.setOnClickListener(v -> {
            if (!activity.requireEditingEnabled()) return;
            maidenVisible = !maidenVisible;
            refreshVisibility();
            changed.run();
        });
    }

    void apply(Person person) {
        PersonNames.set(person, surname.getText().toString(), given.getText().toString(), patronymic.getText().toString());
        person.maidenName = PersonNames.clean(maiden.getText().toString());
        person.maidenNameEnabled = maidenVisible;
        person.surnameDisplay = maidenVisible ? "maiden" : "current";
    }

    private void refreshVisibility() {
        maiden.setBackground(gradient(true, !maidenVisible));
        maiden.setTextColor(activity.uiColor(maidenVisible ? Color.rgb(28, 64, 49) : Color.rgb(104, 112, 121)));
        android.graphics.drawable.Drawable icon = activity.getDrawable(maidenVisible ? R.drawable.ic_menu_eye : R.drawable.ic_menu_eye_off).mutate();
        icon.setTint(activity.uiColor(maidenVisible ? Color.rgb(38, 131, 108) : Color.rgb(128, 138, 151)));
        maidenEye.setForeground(icon);
        maidenEye.setContentDescription(activity.tr(maidenVisible ? "Скрыть девичью фамилию в карточке" : "Показать девичью фамилию в карточке"));
        maidenEye.setSelected(maidenVisible);
        maidenEye.setEnabled(!activity.editingBlocked());
    }

    private void caption(String label) {
        TextView caption = new LocalizedTextView(activity);
        caption.setText(label); caption.setTextSize(12); caption.setTypeface(activity.uiBold());
        caption.setTextColor(activity.uiColor(Color.rgb(76, 87, 96)));
        caption.setPadding(0, 0, 0, activity.dp(5)); addView(caption);
    }

    private EditText field(String label, String value, String tag) {
        caption(label);
        EditText field = makeField(label, value, tag);
        field.setBackground(gradient(false, false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, activity.dp(48));
        params.setMargins(0, 0, 0, activity.dp(12)); addView(field, params);
        return field;
    }

    private EditText makeField(String label, String value, String tag) {
        EditText field = activity.field(label);
        LocalizedViews.setRaw(field, value); field.setTag(tag); field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS | android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(160)});
        field.setEnabled(!activity.editingBlocked());
        return field;
    }

    private GradientDrawable gradient(boolean green, boolean hidden) {
        boolean dark = AppThemePalette.isDark();
        int start = hidden ? (dark ? Color.rgb(51, 57, 65) : Color.rgb(239, 241, 244))
            : green ? (dark ? Color.rgb(32, 66, 55) : Color.rgb(235, 250, 240))
            : dark ? Color.rgb(58, 46, 77) : Color.rgb(247, 240, 255);
        int end = hidden ? (dark ? Color.rgb(44, 50, 57) : Color.rgb(227, 231, 236))
            : green ? (dark ? Color.rgb(38, 77, 62) : Color.rgb(218, 244, 229))
            : dark ? Color.rgb(69, 55, 92) : Color.rgb(233, 222, 250);
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        background.setCornerRadius(activity.dp(10));
        background.setStroke(activity.dp(1), AppThemePalette.stroke(hidden ? Color.rgb(205, 214, 220)
            : green ? Color.rgb(177, 215, 194) : Color.rgb(208, 192, 231)));
        return background;
    }
}
