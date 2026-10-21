package ru.drshapaya.androidft2;

import android.app.Activity;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Warning UI and user actions for cards far from the main tree component. */
final class MainActivityDistantCards {
    private final Activity activity;
    private final MainUiKit ui;
    private final Supplier<TreeState> stateProvider;
    private final Supplier<String> activePanel;
    private final Consumer<String> focusPerson;
    private final Consumer<List<String>> moveCards;
    private LinearLayout warning;
    private TextView warningText;
    private boolean dismissed;

    MainActivityDistantCards(
        Activity activity,
        MainUiKit ui,
        Supplier<TreeState> stateProvider,
        Supplier<String> activePanel,
        Consumer<String> focusPerson,
        Consumer<List<String>> moveCards
    ) {
        this.activity = activity;
        this.ui = ui;
        this.stateProvider = stateProvider;
        this.activePanel = activePanel;
        this.focusPerson = focusPerson;
        this.moveCards = moveCards;
    }

    LinearLayout buildWarning() {
        warning = new LinearLayout(activity);
        warning.setGravity(Gravity.CENTER_VERTICAL);
        warning.setOrientation(LinearLayout.HORIZONTAL);
        warning.setPadding(ui.dp(10), 0, ui.dp(6), 0);
        warning.setBackground(ui.panelBg(Color.rgb(255, 251, 232), ui.dp(8), Color.argb(120, 224, 162, 38)));
        warning.setElevation(ui.dp(9));
        warning.setVisibility(View.GONE);

        TextView icon = new LocalizedTextView(activity);
        icon.setText("!");
        icon.setGravity(Gravity.CENTER);
        icon.setTextSize(13);
        icon.setTypeface(ui.uiBold());
        icon.setTextColor(Color.WHITE);
        icon.setIncludeFontPadding(false);
        icon.setBackground(ui.panelBg(Color.rgb(224, 162, 38), ui.dp(999), Color.TRANSPARENT));
        warning.addView(icon, new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)));

        warningText = new LocalizedTextView(activity);
        warningText.setTextColor(Color.rgb(74, 73, 55));
        warningText.setTextSize(11);
        warningText.setTypeface(ui.uiBold());
        warningText.setSingleLine(true);
        warningText.setEllipsize(TextUtils.TruncateAt.END);
        warningText.setIncludeFontPadding(false);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -1, 1);
        textParams.setMargins(ui.dp(8), 0, ui.dp(6), 0);
        warning.addView(warningText, textParams);

        Button show = compactButton("Показать", v -> focusFirst());
        warning.addView(show, new LinearLayout.LayoutParams(ui.dp(78), ui.dp(32)));
        Button move = compactButton("Вернуть", v -> move());
        LinearLayout.LayoutParams moveParams = new LinearLayout.LayoutParams(ui.dp(78), ui.dp(32));
        moveParams.setMargins(ui.dp(6), 0, 0, 0);
        warning.addView(move, moveParams);
        Button close = compactButton("×", v -> {
            dismissed = true;
            update();
        });
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(ui.dp(34), ui.dp(32));
        closeParams.setMargins(ui.dp(6), 0, 0, 0);
        warning.addView(close, closeParams);
        return warning;
    }

    void update() {
        if (warning == null) return;
        List<String> distantIds = DistantCardFinder.findIds(stateProvider.get());
        String panel = activePanel.get();
        boolean fullScreenPanel = "settings".equals(panel)
            || "people".equals(panel)
            || "more".equals(panel);
        if (distantIds.isEmpty()) dismissed = false;
        boolean show = !fullScreenPanel && !dismissed && !distantIds.isEmpty();
        warning.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        String label = distantIds.size()
            + " "
            + RussianWordForms.forCount(
                distantIds.size(), "карточка", "карточки", "карточек")
            + " далеко от основного дерева";
        warningText.setText(label);
    }

    void resetDismissed() {
        dismissed = false;
    }

    private Button compactButton(String label, View.OnClickListener listener) {
        Button button = ui.actionButton(label, listener);
        button.setTextSize(label.length() <= 1 ? 15 : 9);
        button.setSingleLine(true);
        button.setTextColor(Color.rgb(126, 83, 10));
        button.setBackground(ui.panelBg(Color.rgb(255, 248, 220), ui.dp(8), Color.argb(92, 224, 162, 38)));
        return button;
    }

    private void focusFirst() {
        List<String> distantIds = DistantCardFinder.findIds(stateProvider.get());
        if (distantIds.isEmpty()) {
            update();
            return;
        }
        focusPerson.accept(distantIds.get(0));
    }

    private void move() {
        List<String> distantIds = DistantCardFinder.findIds(stateProvider.get());
        if (distantIds.isEmpty()) {
            update();
            return;
        }
        moveCards.accept(distantIds);
    }
}
