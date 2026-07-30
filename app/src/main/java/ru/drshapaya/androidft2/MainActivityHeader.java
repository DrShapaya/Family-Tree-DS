package ru.drshapaya.androidft2;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class MainActivityHeader {
    private final MainActivity activity;

    MainActivityHeader(MainActivity activity) {
        this.activity = activity;
    }

    View buildHeader() {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10));
        header.setBackgroundColor(Color.rgb(248, 251, 252));
        header.setElevation(activity.dp(5));
        header.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = insets == null ? 0 : insets.getSystemWindowInsetTop();
            view.setPadding(activity.dp(12), Math.max(activity.dp(30), activity.dp(10) + topInset), activity.dp(12), activity.dp(10));
            return insets;
        });
        header.post(header::requestApplyInsets);

        LinearLayout brand = new LinearLayout(activity);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(brand, new LinearLayout.LayoutParams(-1, -2));
        activity.headerBrand = brand;

        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.app_icon);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setBackground(activity.panelBg(Color.rgb(17, 169, 213), activity.dp(10), Color.TRANSPARENT));
        icon.setClipToOutline(true);
        brand.addView(icon, new LinearLayout.LayoutParams(activity.dp(34), activity.dp(34)));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(activity.dp(9), 0, 0, 0);
        brand.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

        TextView title = new TextView(activity);
        title.setText("Семейное древо");
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setTextSize(20);
        title.setTypeface(activity.uiBold());
        title.setSingleLine(true);
        title.setIncludeFontPadding(false);
        texts.addView(title);

        activity.stats = new TextView(activity);
        activity.stats.setTextColor(Color.rgb(101, 113, 122));
        activity.stats.setTextSize(13);
        activity.stats.setTypeface(activity.ui());
        activity.stats.setIncludeFontPadding(false);
        texts.addView(activity.stats);

        activity.badge = new TextView(activity);
        activity.badge.setText(MainActivity.VERSION_BADGE);
        activity.badge.setTextColor(Color.rgb(8, 122, 115));
        activity.badge.setTextSize(12);
        activity.badge.setTypeface(activity.uiBold());
        activity.badge.setIncludeFontPadding(false);
        activity.badge.setPadding(activity.dp(8), activity.dp(3), activity.dp(8), activity.dp(3));
        activity.badge.setBackground(activity.panelBg(Color.argb(120, 255, 255, 255), activity.dp(999), Color.argb(60, 24, 169, 153)));
        final int[] versionTaps = {0};
        final long[] lastVersionTap = {0L};
        activity.badge.setOnClickListener(view -> {
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastVersionTap[0] > 1800L) versionTaps[0] = 0;
            lastVersionTap[0] = now;
            versionTaps[0]++;
            if (versionTaps[0] >= 5) {
                versionTaps[0] = 0;
                activity.openOnlineMenu();
            }
        });
        texts.addView(activity.badge, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, activity.dp(10), 0, 0);
        header.addView(row, new LinearLayout.LayoutParams(-1, -2));

        activity.search = new EditText(activity);
        activity.search.setSingleLine(true);
        activity.search.setHint("Поиск");
        activity.search.setTextSize(16);
        activity.search.setTypeface(activity.ui());
        activity.search.setIncludeFontPadding(false);
        activity.search.setPadding(activity.dp(12), 0, activity.dp(12), 0);
        activity.search.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_search, 0, 0, 0);
        activity.search.setCompoundDrawablePadding(activity.dp(10));
        activity.tintDrawables(activity.search, Color.rgb(8, 122, 115));
        activity.search.setBackground(activity.panelBg(Color.WHITE, activity.dp(8), Color.rgb(217, 224, 229)));
        activity.search.setOnTouchListener((view, event) -> {
            if (event.getAction() != android.view.MotionEvent.ACTION_UP) return false;
            android.graphics.drawable.Drawable clear = activity.search.getCompoundDrawables()[2];
            if (clear == null) return false;
            float clearStart = activity.search.getWidth() - activity.search.getPaddingRight() - clear.getBounds().width() - activity.dp(12);
            if (event.getX() < clearStart) return false;
            activity.search.setText("");
            activity.clearSearchFocus();
            return true;
        });
        row.addView(activity.search, new LinearLayout.LayoutParams(0, activity.dp(44), 1));

        activity.undoBtn = activity.iconButton(R.drawable.ic_menu_undo, v -> activity.undo());
        activity.redoBtn = activity.iconButton(R.drawable.ic_menu_redo, v -> activity.redo());
        row.addView(activity.undoBtn, activity.smallActionParams());
        row.addView(activity.redoBtn, activity.smallActionParams());
        Button saveButton = activity.iconButton(R.drawable.ic_menu_save, v -> activity.saveToast("Дерево сохранено"), Color.WHITE);
        saveButton.setBackground(activity.panelBg(Color.rgb(24, 169, 153), activity.dp(8), Color.TRANSPARENT));
        row.addView(saveButton, activity.smallActionParams());
        activity.headerSaveButton = saveButton;

        activity.searchSuggestionsScroll = new HorizontalScrollView(activity);
        activity.searchSuggestionsScroll.setHorizontalScrollBarEnabled(false);
        activity.searchSuggestionsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        activity.searchSuggestionsScroll.setVisibility(View.GONE);
        activity.searchSuggestions = new LinearLayout(activity);
        activity.searchSuggestions.setOrientation(LinearLayout.HORIZONTAL);
        activity.searchSuggestions.setGravity(Gravity.CENTER_VERTICAL);
        activity.searchSuggestions.setPadding(0, activity.dp(7), 0, 0);
        activity.searchSuggestionsScroll.addView(
            activity.searchSuggestions,
            new HorizontalScrollView.LayoutParams(-2, activity.dp(42)));
        header.addView(activity.searchSuggestionsScroll, new LinearLayout.LayoutParams(-1, activity.dp(42)));
        return header;
    }

    LinearLayout buildZoomRail() {
        LinearLayout rail = new LinearLayout(activity);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setPadding(activity.dp(5), activity.dp(5), activity.dp(5), activity.dp(5));
        rail.setBackground(activity.panelBg(Color.argb(235, 248, 251, 252), activity.dp(8), Color.argb(31, 63, 82, 94)));
        rail.setElevation(activity.dp(6));
        rail.addView(activity.iconButton(R.drawable.ic_nav_card, v -> activity.openPersonEditor()), activity.railButtonParams(false));
        rail.addView(activity.iconButton(R.drawable.ic_menu_fit, v -> activity.treeView.fit()), activity.railButtonParams(false));
        rail.addView(activity.iconButton(R.drawable.ic_menu_zoom_out, v -> activity.treeView.zoomBy(0.86f)), activity.railButtonParams(false));
        rail.addView(activity.iconButton(R.drawable.ic_menu_zoom_in, v -> activity.treeView.zoomBy(1.16f)), activity.railButtonParams(true));
        return rail;
    }
}
