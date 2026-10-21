package ru.drshapaya.androidft2;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/** Shared view primitives for person context menus. */
final class PersonMenuViews {
    private final MainActivity activity;

    PersonMenuViews(MainActivity activity) {
        this.activity = activity;
    }

    View personMenuHeader(Person person, String detailText) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(activity.dp(10), activity.dp(7), activity.dp(10), activity.dp(7));
        header.setBackground(activity.softAccentGradientBg(activity.dp(10)));
        TextView avatar = new LocalizedTextView(activity);
        LocalizedViews.setRaw(avatar, activity.personInitials(person.name));
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(12);
        avatar.setTypeface(activity.uiBold());
        avatar.setGravity(Gravity.CENTER);
        avatar.setIncludeFontPadding(false);
        avatar.setBackground(activity.panelBg(person.color, activity.dp(999), Color.argb(72, 255, 255, 255)));
        header.addView(avatar, new LinearLayout.LayoutParams(activity.dp(40), activity.dp(40)));
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(activity.dp(10), 0, 0, 0);
        TextView name = activity.cardActionTitle(
            person.name == null || person.name.trim().isEmpty() ? "Без имени" : person.name.trim(),
            false);
        name.setTextSize(14);
        copy.addView(name, new LinearLayout.LayoutParams(-1, activity.dp(24)));
        TextView detail = activity.cardActionDetail(detailText, false);
        detail.setTextSize(10);
        copy.addView(detail, new LinearLayout.LayoutParams(-1, activity.dp(20)));
        header.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        return header;
    }

    TextView personMenuSection(String value) {
        TextView section = new LocalizedTextView(activity);
        section.setText(value);
        section.setTextColor(Color.rgb(101, 113, 122));
        section.setTextSize(9);
        section.setTypeface(activity.uiBold());
        section.setGravity(Gravity.LEFT | Gravity.BOTTOM);
        section.setPadding(activity.dp(8), 0, 0, activity.dp(5));
        section.setIncludeFontPadding(false);
        return section;
    }

    View personMenuAction(int iconRes, String label, boolean danger, PopupWindow popup, Runnable action) {
        TextView row = personMenuText(iconRes, label, danger);
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        PressFeedback.install(row);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, activity.dp(50));
        params.setMargins(0, 0, 0, activity.dp(7));
        row.setLayoutParams(params);
        return row;
    }

    View personMenuToggleAction(
        int iconRes,
        String label,
        boolean enabled,
        java.util.function.Consumer<Boolean> action
    ) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dp(10), 0, activity.dp(10), 0);
        row.setBackground(activity.panelBg(Color.WHITE, activity.dp(8), Color.rgb(225, 231, 235)));

        TextView title = personMenuText(iconRes, label, false);
        title.setBackgroundColor(Color.TRANSPARENT);
        row.addView(title, new LinearLayout.LayoutParams(0, activity.dp(48), 1f));

        FrameLayout toggle = activity.toggleControl(enabled);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(activity.dp(44), activity.dp(26));
        toggleParams.setMargins(activity.dp(8), 0, 0, 0);
        row.addView(toggle, toggleParams);

        final boolean[] value = {enabled};
        Runnable refresh = () -> {
            toggle.setBackground(activity.toggleBg(value[0]));
            activity.positionToggleThumb(toggle, value[0]);
        };
        refresh.run();
        row.setOnClickListener(v -> {
            value[0] = !value[0];
            refresh.run();
            action.accept(value[0]);
        });
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, activity.dp(48)));
        return row;
    }

    View personMenuGridRow(View first, View second) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(first, new LinearLayout.LayoutParams(0, activity.dp(54), 1));
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(0, activity.dp(54), 1);
        secondParams.setMargins(activity.dp(7), 0, 0, 0);
        row.addView(second, secondParams);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, activity.dp(61));
        params.setMargins(0, 0, 0, activity.dp(7));
        row.setLayoutParams(params);
        return row;
    }

    View personMenuSpaced(View view, int top, int bottom) {
        ViewGroup.LayoutParams current = view.getLayoutParams();
        int height = current == null ? ViewGroup.LayoutParams.WRAP_CONTENT : current.height;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, height);
        params.setMargins(0, top, 0, bottom);
        view.setLayoutParams(params);
        return view;
    }

    View personMenuGridTile(
        int iconRes,
        String label,
        String detail,
        boolean danger,
        PopupWindow popup,
        Runnable action
    ) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(activity.dp(9), activity.dp(4), activity.dp(7), activity.dp(4));
        tile.setBackground(activity.panelBg(
            danger ? Color.rgb(255, 244, 241) : Color.WHITE,
            activity.dp(9),
            danger ? Color.argb(88, 197, 83, 75) : Color.rgb(225, 231, 235)));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setColorFilter(activity.uiColor(danger ? Color.rgb(197, 83, 75) : Color.rgb(8, 122, 115)));
        tile.addView(icon, new LinearLayout.LayoutParams(activity.dp(24), activity.dp(24)));
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(activity.dp(7), 0, 0, 0);
        TextView title = activity.cardActionTitle(label, danger);
        title.setTextSize(12);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        copy.addView(title, new LinearLayout.LayoutParams(-1, detail == null || detail.isEmpty() ? activity.dp(42) : activity.dp(25)));
        if (detail != null && !detail.isEmpty()) {
            TextView caption = activity.cardActionDetail(detail, danger);
            caption.setTextSize(8);
            caption.setSingleLine(true);
            caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
            copy.addView(caption, new LinearLayout.LayoutParams(-1, activity.dp(16)));
        }
        tile.addView(copy, new LinearLayout.LayoutParams(0, -1, 1));
        tile.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        PressFeedback.install(tile);
        return tile;
    }

    View personMenuGridSpacer() {
        TextView spacer = new LocalizedTextView(activity);
        spacer.setVisibility(View.INVISIBLE);
        return spacer;
    }

    void updatePersonPopupHeight(PopupWindow popup) {
        if (popup == null || popup.getContentView() == null) return;
        View content = popup.getContentView();
        content.measure(
            View.MeasureSpec.makeMeasureSpec(popup.getWidth(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        popup.update(popup.getWidth(), Math.min(content.getMeasuredHeight(), screenHeight - activity.dp(32)));
    }

    TextView personMenuText(int iconRes, String label, boolean danger) {
        TextView row = new LocalizedTextView(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setText(label);
        row.setTextSize(13);
        row.setTypeface(activity.uiBold());
        row.setIncludeFontPadding(false);
        row.setTextColor(danger ? Color.rgb(197, 83, 75) : Color.rgb(28, 34, 38));
        row.setPadding(activity.dp(10), 0, activity.dp(10), 0);
        row.setBackground(activity.panelBg(Color.WHITE, activity.dp(8), Color.rgb(225, 231, 235)));
        if (iconRes != 0) {
            row.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
            row.setCompoundDrawablePadding(activity.dp(9));
            activity.tintDrawables(row, danger ? Color.rgb(197, 83, 75) : Color.rgb(8, 122, 115));
        }
        return row;
    }
}
