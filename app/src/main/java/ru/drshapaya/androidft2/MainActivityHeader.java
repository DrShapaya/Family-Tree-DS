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
    private LinearLayout actionOverlay;

    MainActivityHeader(MainActivity activity) {
        this.activity = activity;
    }

    View buildHeader() {
        boolean landscape = activity.isLandscapeLayout();
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, 0, 0, 0);
        header.setBackgroundColor(Color.TRANSPARENT);
        // The canvas must continue directly under the white brand block;
        // action controls are overlaid on the canvas below and must not cast
        // an opaque separator band.
        header.setElevation(0f);

        LinearLayout brand = new LinearLayout(activity);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(
            activity.dp(12),
            activity.dp(landscape ? 1 : 6),
            activity.dp(12),
            activity.dp(landscape ? 4 : 8));
        brand.setBackgroundColor(Color.WHITE);
        header.addView(brand, new LinearLayout.LayoutParams(-1, -2));
        activity.headerBrand = brand;

        header.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = insets == null ? 0 : insets.getSystemWindowInsetTop();
            int topPadding = activity.dp(landscape ? 1 : 6);
            int bottomPadding = activity.dp(landscape ? 4 : 8);
            brand.setPadding(
                activity.dp(12),
                Math.max(topPadding, topPadding + topInset),
                activity.dp(12),
                bottomPadding);
            return insets;
        });
        header.post(header::requestApplyInsets);

        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.app_icon);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setBackground(activity.panelBg(Color.rgb(17, 169, 213), activity.dp(10), Color.TRANSPARENT));
        icon.setClipToOutline(true);
        activity.headerIdentityIcon = icon;
        brand.addView(icon, new LinearLayout.LayoutParams(
            activity.dp(landscape ? 38 : 34),
            activity.dp(landscape ? 38 : 34)));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(activity.dp(9), 0, 0, 0);
        activity.headerIdentityTexts = texts;
        brand.addView(texts, new LinearLayout.LayoutParams(
            landscape ? activity.dp(214) : 0,
            -2,
            landscape ? 0 : 1));

        TextView title = new LocalizedTextView(activity);
        title.setText("Семейное древо");
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setTextSize(landscape ? 17 : 20);
        title.setTypeface(activity.uiBold());
        title.setSingleLine(true);
        title.setIncludeFontPadding(false);
        texts.addView(title);

        activity.stats = new LocalizedTextView(activity);
        activity.stats.setTextColor(Color.rgb(101, 113, 122));
        activity.stats.setTextSize(landscape ? 10 : 13);
        activity.stats.setTypeface(activity.ui());
        activity.stats.setIncludeFontPadding(false);
        texts.addView(activity.stats);

        activity.treeQualityButton = activity.actionButton("0%", v -> activity.showTreeQualityDialog());
        activity.treeQualityButton.setTextSize(9);
        activity.treeQualityButton.setSingleLine(true);
        activity.treeQualityButton.setGravity(Gravity.CENTER_VERTICAL);
        activity.treeQualityButton.setPadding(activity.dp(7), 0, activity.dp(7), 0);
        activity.treeQualityButton.setCompoundDrawablesWithIntrinsicBounds(
            R.drawable.ic_menu_shield,
            0,
            0,
            0);
        activity.treeQualityButton.setCompoundDrawablePadding(activity.dp(4));
        activity.treeQualityButton.setTextColor(AppThemePalette.secondary());
        activity.tintDrawables(activity.treeQualityButton, AppThemePalette.secondary());
        activity.treeQualityButton.setBackground(activity.qualityGradientBg(activity.dp(10)));
        LinearLayout.LayoutParams qualityParams = new LinearLayout.LayoutParams(
            activity.dp(landscape ? 78 : 82),
            activity.dp(landscape ? 42 : 44));
        qualityParams.setMargins(activity.dp(8), 0, 0, 0);

        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // Match the trailing edge of the 52dp side rail (8dp from the screen).
        row.setPadding(activity.dp(12), 0, activity.dp(8), 0);
        row.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, activity.dp(60));
        rowParams.setMargins(0, 0, 0, 0);

        activity.search = new LocalizedEditText(activity);
        activity.search.setSingleLine(true);
        activity.search.setHint(activity.tr("Поиск"));
        activity.search.setTextSize(16);
        activity.search.setTypeface(activity.ui());
        activity.search.setTextColor(AppThemePalette.text(Color.rgb(28, 34, 38)));
        activity.search.setHintTextColor(AppThemePalette.text(Color.rgb(128, 137, 144)));
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
        // Keep the weighted slot in the action row while the field is hidden;
        // this leaves undo/redo pinned to the right like the reference layout.
        activity.search.setVisibility(View.INVISIBLE);

        activity.searchToggleButton = activity.iconButton(
            R.drawable.ic_menu_search,
            v -> activity.toggleSearchField(),
            Color.rgb(8, 122, 115));
        activity.searchToggleButton.setContentDescription(activity.tr("Поиск"));
        activity.searchToggleButton.setBackground(activity.panelBg(Color.WHITE, activity.dp(11), Color.rgb(217, 224, 229)));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(0, activity.dp(52), 1);
        searchParams.setMargins(activity.dp(7), 0, 0, 0);

        activity.undoBtn = activity.iconButton(R.drawable.ic_menu_undo, v -> activity.undo());
        activity.redoBtn = activity.iconButton(R.drawable.ic_menu_redo, v -> activity.redo());
        LinearLayout.LayoutParams undoParams = new LinearLayout.LayoutParams(activity.dp(52), activity.dp(52));
        undoParams.setMargins(activity.dp(7), 0, 0, 0);
        LinearLayout.LayoutParams redoParams = new LinearLayout.LayoutParams(activity.dp(52), activity.dp(52));
        redoParams.setMargins(activity.dp(7), 0, 0, 0);
        if (landscape) {
            LinearLayout controls = new LinearLayout(activity);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            controls.setGravity(Gravity.CENTER_VERTICAL);
            controls.setBackgroundColor(Color.TRANSPARENT);
            activity.landscapeHeaderControls = controls;
            activity.searchToggleButton.setVisibility(View.VISIBLE);
            controls.addView(
                activity.searchToggleButton,
                new LinearLayout.LayoutParams(activity.dp(52), activity.dp(52)));
            controls.addView(activity.search, searchParams);
            controls.addView(activity.undoBtn, undoParams);
            controls.addView(activity.redoBtn, redoParams);
            controls.addView(activity.treeQualityButton, qualityParams);
            brand.addView(controls, new LinearLayout.LayoutParams(0, -2, 1));
        } else {
            // Keep the tree score in the brand row, aligned with the title,
            // while undo/redo stay at the far right of the action rail below.
            brand.addView(activity.treeQualityButton, qualityParams);
            row.addView(activity.searchToggleButton, new LinearLayout.LayoutParams(activity.dp(52), activity.dp(52)));
            row.addView(activity.search, searchParams);
            row.addView(activity.undoBtn, undoParams);
            row.addView(activity.redoBtn, redoParams);
        }

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
        actionOverlay = new LinearLayout(activity);
        actionOverlay.setOrientation(LinearLayout.VERTICAL);
        actionOverlay.setBackgroundColor(Color.TRANSPARENT);
        actionOverlay.setClipChildren(false);
        actionOverlay.setClipToPadding(false);
        if (!landscape) actionOverlay.addView(row, rowParams);
        actionOverlay.addView(activity.searchSuggestionsScroll, new LinearLayout.LayoutParams(-1, activity.dp(42)));
        return header;
    }

    View buildActionOverlay() {
        return actionOverlay;
    }

    LinearLayout buildZoomRail() {
        LinearLayout rail = new LinearLayout(activity);
        rail.setOrientation(LinearLayout.VERTICAL);
        // A one-dp optical correction compensates for the rail outline: measured button
        // gaps on the emulator become equal instead of 13 px on the left and 19 px right.
        rail.setPadding(activity.dp(6), activity.dp(5), activity.dp(4), activity.dp(5));
        rail.setBackground(activity.panelBg(
            Color.argb(235, 248, 251, 252), activity.dp(8), Color.argb(31, 63, 82, 94)));
        rail.setElevation(activity.dp(6));

        Button card = activity.iconButton(
            R.drawable.ic_nav_card, v -> activity.openPersonEditor(), Color.rgb(106, 74, 177));
        rail.addView(card, activity.railButtonParams(false));
        rail.addView(activity.iconButton(R.drawable.ic_menu_fit, v -> activity.treeView.fit()), activity.railButtonParams(false));
        activity.lockRailButton = activity.iconButton(
            activity.editingBlocked() ? R.drawable.ic_menu_lock : R.drawable.ic_menu_unlock,
            v -> activity.toggleTreeLockFromRail());
        activity.lockRailButton.setContentDescription(activity.tr("Защита правок"));
        rail.addView(activity.lockRailButton, activity.railButtonParams(false));
        rail.addView(activity.iconButton(R.drawable.ic_menu_zoom_out, v -> activity.treeView.zoomBy(0.86f)), activity.railButtonParams(false));
        rail.addView(activity.iconButton(R.drawable.ic_menu_zoom_in, v -> activity.treeView.zoomBy(1.16f)), activity.railButtonParams(true));
        activity.refreshLockUi();
        return rail;
    }
}
