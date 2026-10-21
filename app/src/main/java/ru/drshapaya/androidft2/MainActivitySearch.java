package ru.drshapaya.androidft2;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Header search behavior; the header owns construction and this controller owns interaction. */
final class MainActivitySearch {
    private final Activity activity;
    private final MainUiKit ui;
    private final Supplier<TreeState> stateProvider;
    private final Supplier<String> unnamedPerson;
    private final Consumer<String> filter;
    private final Consumer<Person> selectPerson;
    private final Supplier<View> focusTarget;
    private final Consumer<View> showKeyboard;
    private final Runnable hideKeyboard;

    private final java.util.concurrent.ExecutorService searchWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private volatile long suggestionRevision;
    private boolean closed;
    private boolean expanded;
    private EditText field;
    private Button toggleButton;
    private HorizontalScrollView suggestionsScroll;
    private LinearLayout suggestions;

    MainActivitySearch(
        Activity activity,
        MainUiKit ui,
        Supplier<TreeState> stateProvider,
        Supplier<String> unnamedPerson,
        Consumer<String> filter,
        Consumer<Person> selectPerson,
        Supplier<View> focusTarget,
        Consumer<View> showKeyboard,
        Runnable hideKeyboard
    ) {
        this.activity = activity;
        this.ui = ui;
        this.stateProvider = stateProvider;
        this.unnamedPerson = unnamedPerson;
        this.filter = filter;
        this.selectPerson = selectPerson;
        this.focusTarget = focusTarget;
        this.showKeyboard = showKeyboard;
        this.hideKeyboard = hideKeyboard;
    }

    void attach(
        EditText field,
        Button toggleButton,
        HorizontalScrollView suggestionsScroll,
        LinearLayout suggestions
    ) {
        suggestionRevision++;
        this.field = field;
        expanded = false;
        this.toggleButton = toggleButton;
        this.suggestionsScroll = suggestionsScroll;
        this.suggestions = suggestions;
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = String.valueOf(s);
                filter.accept(query);
                updateSuggestions(query);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    void clearFocus() {
        if (field != null) field.clearFocus();
        View target = focusTarget.get();
        if (target != null) target.requestFocus();
        hideKeyboard.run();
        if (field != null && field.getText().length() == 0) collapse();
    }

    void toggle() {
        if (field == null) return;
        if (expanded) {
            field.requestFocus();
            showKeyboard.accept(field);
            return;
        }
        expanded = true;
        PanelTransitions.reset(field);
        if (toggleButton != null) toggleButton.setVisibility(View.GONE);
        field.setVisibility(View.VISIBLE);
        if (PanelTransitions.enabled(field)) {
            field.setAlpha(0f);
            field.setTranslationX(-ui.dp(24));
            field.animate().alpha(1f).translationX(0f).setDuration(280L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
        }
        field.requestFocus();
        showKeyboard.accept(field);
    }

    void clearTextAndFocus() {
        if (field == null) return;
        field.setText("");
        clearFocus();
    }

    void setDimmed(boolean dimmed, int surface, int stroke, int icon) {
        if (toggleButton != null) {
            toggleButton.setAlpha(1f);
            toggleButton.setEnabled(!dimmed);
            toggleButton.setBackground(ui.panelBg(surface, ui.dp(11), stroke));
            ui.tintDrawables(toggleButton, icon);
            Drawable foreground = toggleButton.getForeground();
            if (foreground != null) foreground.mutate().setTint(AppThemePalette.text(icon));
        }
        if (field != null) {
            field.setEnabled(!dimmed);
            field.setBackground(ui.panelBg(surface, ui.dp(8), stroke));
            field.setTextColor(AppThemePalette.text(dimmed
                ? Color.rgb(70, 83, 92) : Color.rgb(28, 34, 38)));
            field.setHintTextColor(AppThemePalette.text(dimmed
                ? Color.rgb(101, 113, 122) : Color.rgb(128, 137, 144)));
            ui.tintDrawables(field, icon);
        }
    }

    private void collapse() {
        if (field == null || field.getText().length() > 0) return;
        if (!expanded) return;
        expanded = false;
        field.animate().cancel();
        Runnable finish = () -> {
            if (expanded) return;
            field.setVisibility(View.INVISIBLE);
            PanelTransitions.reset(field);
            if (toggleButton != null) toggleButton.setVisibility(View.VISIBLE);
        };
        if (!PanelTransitions.enabled(field)) finish.run();
        else field.animate().alpha(0f).translationX(-ui.dp(16)).setDuration(160L)
            .withEndAction(finish).start();
        if (suggestionsScroll != null) suggestionsScroll.setVisibility(View.GONE);
    }

    private void updateSuggestions(String query) {
        if (suggestions == null || suggestionsScroll == null || field == null) return;
        suggestionRevision++;
        String value = query == null ? "" : query.trim();
        field.setCompoundDrawablesWithIntrinsicBounds(
            R.drawable.ic_menu_search, 0,
            value.isEmpty() ? 0 : R.drawable.ic_menu_close, 0);
        ui.tintDrawables(field, Color.rgb(8, 122, 115));
        suggestions.removeAllViews();
        TreeState state = stateProvider.get();
        if (value.isEmpty() || state == null) {
            suggestionsScroll.setVisibility(View.GONE);
            return;
        }
        long revision=++suggestionRevision;
        TreeState snapshot=TreeStateCopier.copy(state);
        String unnamed=unnamedPerson.get();
        searchWorker.execute(() -> {
            if (revision!=suggestionRevision) return;
            List<Person> people=new ArrayList<>(snapshot.people.values());
            List<SearchSuggestions.Suggestion> result=SearchSuggestions.build(query,8,people,
                () -> SearchSuggestions.treePlaces(people), () -> SearchSuggestions.birthYears(people),unnamed);
            main.post(() -> {
                if (!closed && revision==suggestionRevision && stateProvider.get()==state && !activity.isDestroyed())
                    renderSuggestions(result);
            });
        });
    }

    private void renderSuggestions(List<SearchSuggestions.Suggestion> result) {
        suggestions.removeAllViews();
        int count=0;
        for (SearchSuggestions.Suggestion suggestion : result) {
            TextView pill = new LocalizedTextView(activity);
            LocalizedViews.setRaw(pill, suggestion.label);
            pill.setTextColor(Color.rgb(8, 122, 115));
            pill.setTextSize(11);
            pill.setTypeface(ui.uiBold());
            pill.setSingleLine(true);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(ui.dp(13), 0, ui.dp(13), 0);
            pill.setBackground(ui.panelBg(Color.rgb(232, 248, 246), ui.dp(999), Color.argb(72, 24, 169, 153)));
            pill.setOnClickListener(v -> selectSuggestion(suggestion));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, ui.dp(34));
            params.setMargins(0, 0, ui.dp(7), 0);
            suggestions.addView(pill, params);
            count++;
        }
        if (count == 0) {
            TextView empty = new LocalizedTextView(activity);
            empty.setText("Совпадений нет — попробуйте фамилию, год или место");
            empty.setTextColor(Color.rgb(101, 113, 122));
            empty.setTextSize(11);
            empty.setTypeface(ui.ui());
            empty.setGravity(Gravity.CENTER_VERTICAL);
            empty.setPadding(ui.dp(4), 0, ui.dp(12), 0);
            suggestions.addView(empty, new LinearLayout.LayoutParams(-2, ui.dp(34)));
        }
        suggestionsScroll.setVisibility(View.VISIBLE);
    }

    void close() { closed=true; suggestionRevision++; searchWorker.shutdownNow(); main.removeCallbacksAndMessages(null); }

    private void selectSuggestion(SearchSuggestions.Suggestion suggestion) {
        TreeState state = stateProvider.get();
        if (suggestion == null || state == null || field == null) return;
        Person person = suggestion.personId.isEmpty() ? null : state.people.get(suggestion.personId);
        if (person != null) {
            state.selectedId = person.id;
            clearTextAndFocus();
            selectPerson.accept(person);
            return;
        }
        field.setText(suggestion.applyText);
        field.setSelection(field.getText().length());
    }
}
