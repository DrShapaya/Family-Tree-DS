package ru.drshapaya.androidft2;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Owns the workshop dialog, reward wallet and rewarded-ad lifecycle.
 *
 * <p>The feature depends on an Android context, a shared UI kit and two narrow
 * callbacks. It does not reach into {@link MainActivity} state.</p>
 */
final class MainActivityWorkshop {
    private static final int ACCENT_PURPLE = Color.rgb(106, 74, 177);
    private static final int LEADERBOARD_ENTRY_COST = 50;

    private final Activity activity;
    private final MainUiKit ui;
    private final Supplier<TreeCanvasView> treeViewProvider;
    private final Consumer<String> messages;
    private final RewardWallet rewardWallet;
    private final YandexRewardedAdProvider rewardedAds;

    private Dialog workshopDialog;
    private LinearLayout workshopCatalogList;
    private LinearLayout workshopCategoryHeader;
    private TextView workshopBalanceText;
    private TextView workshopQuickChoiceLabel;
    private Button workshopRewardAdButton;
    private String workshopCategoryId = "";

    MainActivityWorkshop(
        Activity activity,
        MainUiKit ui,
        Supplier<TreeCanvasView> treeViewProvider,
        Consumer<String> messages
    ) {
        this.activity = activity;
        this.ui = ui;
        this.treeViewProvider = treeViewProvider;
        this.messages = messages;
        rewardWallet = new RewardWallet(activity);
        rewardedAds = new YandexRewardedAdProvider(
            activity,
            BuildConfig.YANDEX_REWARDED_AD_UNIT_ID,
            new YandexRewardedAdProvider.Listener() {
                @Override public void onRewardedAdReadyChanged() {
                    updateWorkshopUi();
                }

                @Override public void onRewardedAdEarned() {
                    rewardWallet.recordRewardedAd(System.currentTimeMillis());
                    AnalyticsReporter.rewardedAdEarned(
                        RewardWallet.REWARDED_AD_TOKENS,
                        rewardWallet.balance());
                    messages.accept("+10 жетонов");
                    updateWorkshopUi();
                }

                @Override public void onRewardedAdMessage(String message) {
                    messages.accept(message);
                }
            });
        rewardedAds.load();
    }

    private TreeCanvasView treeView() {
        return treeViewProvider.get();
    }

    void close() {
        rewardedAds.close();
        if (workshopDialog != null && workshopDialog.isShowing()) {
            workshopDialog.dismiss();
        }
        workshopDialog = null;
    }

    void grantDebugTokens() {
        rewardWallet.grantTokens(100);
        updateWorkshopUi();
    }

    void open() {
        AnalyticsReporter.event("workshop_open");
        rewardedAds.load();
        Dialog dialog = new Dialog(activity);
        workshopDialog = dialog;
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setOnDismissListener(d -> {
            if (workshopDialog == dialog) workshopDialog = null;
            workshopBalanceText = null;
            workshopQuickChoiceLabel = null;
            workshopRewardAdButton = null;
            workshopCatalogList = null;
            workshopCategoryHeader = null;
            workshopCategoryId = "";
        });

        LinearLayout shell = ui.editorDialogShell();
        shell.addView(ui.editorDialogHeader(dialog, R.drawable.ic_workshop_storefront, "Мастерская", "Жетоны, оформление и бонусы"));

        LinearLayout balance = new LinearLayout(activity);
        balance.setOrientation(LinearLayout.VERTICAL);
        balance.setGravity(Gravity.CENTER_VERTICAL);
        balance.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        balance.setBackground(ui.panelBg(
            AppThemePalette.primarySurface(),
            ui.dp(12),
            AppThemePalette.alpha(AppThemePalette.primaryBright(), 92)));
        TextView balanceLabel = new LocalizedTextView(activity);
        balanceLabel.setText("Баланс жетонов");
        balanceLabel.setTextColor(Color.rgb(28, 34, 38));
        balanceLabel.setTextSize(12);
        balanceLabel.setTypeface(ui.uiBold());
        balanceLabel.setIncludeFontPadding(false);
        balance.addView(balanceLabel, new LinearLayout.LayoutParams(-1, ui.dp(22)));
        LinearLayout balanceLine = new LinearLayout(activity);
        balanceLine.setGravity(Gravity.CENTER_VERTICAL);
        workshopBalanceText = new LocalizedTextView(activity);
        workshopBalanceText.setTextColor(AppThemePalette.primary());
        workshopBalanceText.setTextSize(24);
        workshopBalanceText.setTypeface(ui.uiBold());
        workshopBalanceText.setIncludeFontPadding(false);
        balanceLine.addView(workshopBalanceText, new LinearLayout.LayoutParams(0, ui.dp(38), 1));
        Button topButton = ui.actionButton("Топ", v -> messages.accept("Скоро"));
        topButton.setTextSize(10);
        topButton.setTextColor(AppThemePalette.primary());
        topButton.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        topButton.setBackground(ui.panelBg(
            Color.WHITE, ui.dp(999), AppThemePalette.alpha(AppThemePalette.primaryBright(), 100)));
        balanceLine.addView(topButton, new LinearLayout.LayoutParams(ui.dp(60), ui.dp(34)));
        balance.addView(balanceLine, new LinearLayout.LayoutParams(-1, ui.dp(38)));
        LinearLayout.LayoutParams balanceParams = new LinearLayout.LayoutParams(-1, ui.dp(82));
        balanceParams.setMargins(0, ui.dp(12), 0, ui.dp(10));
        shell.addView(balance, balanceParams);

        workshopRewardAdButton = ui.actionButton("Смотреть рекламу +10 жетонов", v -> watchRewardedAd());
        workshopRewardAdButton.setTextColor(Color.WHITE);
        workshopRewardAdButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_sparkles, 0, 0, 0);
        workshopRewardAdButton.setCompoundDrawablePadding(ui.dp(8));
        ui.tintDrawables(workshopRewardAdButton, Color.WHITE);
        workshopRewardAdButton.setBackground(ui.tealGradientBg(ui.dp(10)));
        shell.addView(workshopRewardAdButton, new LinearLayout.LayoutParams(-1, ui.dp(48)));

        workshopQuickChoiceLabel = new LocalizedTextView(activity);
        workshopQuickChoiceLabel.setText("Быстрый выбор");
        workshopQuickChoiceLabel.setTextColor(AppThemePalette.secondary());
        workshopQuickChoiceLabel.setTextSize(10);
        workshopQuickChoiceLabel.setTypeface(ui.uiBold());
        workshopQuickChoiceLabel.setGravity(Gravity.LEFT | Gravity.BOTTOM);
        workshopQuickChoiceLabel.setPadding(ui.dp(4), ui.dp(12), 0, ui.dp(6));
        shell.addView(workshopQuickChoiceLabel, new LinearLayout.LayoutParams(-1, ui.dp(42)));

        workshopCategoryHeader = new LinearLayout(activity);
        workshopCategoryHeader.setOrientation(LinearLayout.VERTICAL);
        workshopCategoryHeader.setVisibility(View.GONE);
        shell.addView(workshopCategoryHeader, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(activity);
        workshopCatalogList = new LinearLayout(activity);
        workshopCatalogList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(workshopCatalogList);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        workshopCategoryId = "";
        bindWorkshopCatalog();
        updateWorkshopUi();
        ui.showEditorDialog(dialog, shell);
    }

    private void showWorkshopLeaderboard() {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = ui.editorDialogShell();
        shell.addView(ui.editorDialogHeader(
            dialog, R.drawable.ic_workshop_tokens, "Топ по жетонам", "10 лучших участников"));
        Button join = ui.actionButton("Попасть в топ", v -> showJoinLeaderboardDialog());
        join.setTextColor(Color.WHITE);
        join.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_workshop_tokens, 0, 0, 0);
        join.setCompoundDrawablePadding(ui.dp(7));
        ui.tintDrawables(join, Color.WHITE);
        join.setBackground(ui.tealGradientBg(ui.dp(10)));
        LinearLayout.LayoutParams joinParams = new LinearLayout.LayoutParams(-1, ui.dp(44));
        joinParams.setMargins(0, ui.dp(10), 0, 0);
        shell.addView(join, joinParams);
        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, ui.dp(10), 0, ui.dp(8));
        String[][] rows = {
            {"1", "Елена", "412"},
            {"2", "Алексей", "356"},
            {"3", "Мария", "301"},
            {"4", "Ирина", "268"},
            {"5", "Виктор", "241"},
            {"6", "Анна", "208"},
            {"7", "Михаил", "142"},
            {"8", "Вы", String.valueOf(rewardWallet.balance())},
            {"9", "Ольга", "117"},
            {"10", "Сергей", "104"}
        };
        for (String[] row : rows) {
            LinearLayout item = new LinearLayout(activity);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
            item.setBackground(ui.panelBg(Color.WHITE, ui.dp(11), Color.rgb(217, 224, 229)));
            TextView rank = new LocalizedTextView(activity);
            rank.setText(row[0]);
            rank.setTextColor(AppThemePalette.primary());
            rank.setTextSize(16);
            rank.setTypeface(ui.uiBold());
            rank.setGravity(Gravity.CENTER);
            item.addView(rank, new LinearLayout.LayoutParams(ui.dp(34), ui.dp(32)));
            TextView name = new LocalizedTextView(activity);
            name.setText(row[1]);
            name.setTextColor(Color.rgb(28, 34, 38));
            name.setTextSize(12);
            name.setTypeface(ui.uiBold());
            item.addView(name, new LinearLayout.LayoutParams(0, ui.dp(32), 1));
            TextView score = new LocalizedTextView(activity);
            score.setText(row[2] + " жет.");
            score.setTextColor(Color.rgb(161, 111, 19));
            score.setTextSize(11);
            score.setTypeface(ui.uiBold());
            score.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            item.addView(score, new LinearLayout.LayoutParams(ui.dp(74), ui.dp(32)));
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, ui.dp(50));
            itemParams.setMargins(0, 0, 0, ui.dp(7));
            list.addView(item, itemParams);
        }
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        ui.showEditorDialog(dialog, shell);
    }

    private void showJoinLeaderboardDialog() {
        int balance = rewardWallet.balance();
        boolean english = AppLanguage.isEnglish(activity);
        if (balance < LEADERBOARD_ENTRY_COST) {
            new android.app.AlertDialog.Builder(activity)
                .setTitle(english ? "Not enough tokens yet" : "Жетонов пока мало")
                .setMessage(english
                    ? "You need " + LEADERBOARD_ENTRY_COST + " tokens to join the leaderboard. Available now: " + balance + "."
                    : "Для участия в топе нужно " + LEADERBOARD_ENTRY_COST
                        + " жетонов. Сейчас доступно: " + balance + ".")
                .setPositiveButton(english ? "Got it" : "Понятно", null)
                .show();
            return;
        }
        EditText nickname = new LocalizedEditText(activity);
        nickname.setSingleLine(true);
        nickname.setHint(AppLanguage.text(activity, "Ваш ник в топе"));
        nickname.setTextSize(15);
        nickname.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        nickname.setBackground(ui.panelBg(Color.WHITE, ui.dp(10), Color.rgb(217, 224, 229)));
        LinearLayout box = new LinearLayout(activity);
        box.setPadding(ui.dp(8), ui.dp(4), ui.dp(8), 0);
        box.addView(nickname, new LinearLayout.LayoutParams(-1, ui.dp(48)));
        new android.app.AlertDialog.Builder(activity)
            .setTitle(english ? "Join the leaderboard" : "Попасть в топ")
            .setMessage(english
                ? "Enter a nickname. The placement fee is " + LEADERBOARD_ENTRY_COST + " tokens."
                : "Введите ник. С размещения будет списано "
                    + LEADERBOARD_ENTRY_COST + " жетонов.")
            .setView(box)
            .setNegativeButton(english ? "Cancel" : "Отмена", null)
            .setPositiveButton(english ? "Place" : "Разместить", (dialog, which) -> {
                String value = nickname.getText() == null ? "" : nickname.getText().toString().trim();
                if (value.isEmpty()) {
                    messages.accept(english ? "Enter a nickname" : "Введите ник");
                    return;
                }
                if (rewardWallet.balance() < LEADERBOARD_ENTRY_COST) {
                    messages.accept(english ? "Not enough tokens" : "Жетонов стало недостаточно");
                    return;
                }
                if (rewardWallet.spendTokens(LEADERBOARD_ENTRY_COST)) {
                    messages.accept(english
                        ? "Request for “" + value + "” was sent to the leaderboard"
                        : "Заявка «" + value + "» отправлена в топ");
                    updateWorkshopUi();
                } else {
                    messages.accept(english ? "Not enough tokens" : "Жетонов стало недостаточно");
                }
            })
            .show();
    }

    private void watchRewardedAd() {
        long now = System.currentTimeMillis();
        AnalyticsReporter.rewardedAdRequested(rewardedAds.isReady(), rewardedAds.isLoading());
        if (!rewardWallet.canEarnRewardedAd(now)) {
            AnalyticsReporter.event("rewarded_ad_cooldown_blocked");
            messages.accept(rewardWallet.rewardedAdBlockedMessage(now));
            updateWorkshopUi();
            return;
        }
        rewardedAds.show();
        updateWorkshopUi();
    }

    private void updateWorkshopUi() {
        long now = System.currentTimeMillis();
        if (workshopBalanceText != null) {
            workshopBalanceText.setText(rewardWallet.balance() + " жетонов");
        }
        if (workshopRewardAdButton != null) {
            boolean allowed = rewardWallet.canEarnRewardedAd(now);
            String blocked = rewardWallet.rewardedAdBlockedMessage(now);
            boolean ready = rewardedAds.isReady();
            boolean loading = rewardedAds.isLoading();
            workshopRewardAdButton.setEnabled(allowed);
            if (!allowed && !blocked.isEmpty()) {
                workshopRewardAdButton.setText(blocked);
            } else if (ready) {
                workshopRewardAdButton.setText("Смотреть рекламу +10 жетонов");
            } else if (loading) {
                workshopRewardAdButton.setText("Загрузка рекламы...");
            } else {
                workshopRewardAdButton.setText("Загрузить рекламу +10 жетонов");
            }
            workshopRewardAdButton.setAlpha(allowed ? 1f : 0.62f);
        }
        bindWorkshopCatalog();
    }

    private void bindWorkshopCatalog() {
        if (workshopCatalogList == null || workshopCategoryHeader == null) return;
        workshopCatalogList.removeAllViews();
        workshopCategoryHeader.removeAllViews();
        if (workshopQuickChoiceLabel != null) {
            workshopQuickChoiceLabel.setVisibility(workshopCategoryId.isEmpty() ? View.VISIBLE : View.GONE);
        }
        workshopCategoryHeader.setVisibility(workshopCategoryId.isEmpty() ? View.GONE : View.VISIBLE);
        if (workshopCategoryId.isEmpty()) {
            bindWorkshopHome();
            return;
        }
        bindWorkshopCategory(workshopCategoryId);
    }

    private void bindWorkshopHome() {
        LinearLayout grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        List<RewardCatalog.Category> categories = RewardCatalog.categories();
        for (int row = 0; row < 3; row++) {
            LinearLayout line = new LinearLayout(activity);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < 2; column++) {
                int index = row * 2 + column;
                RewardCatalog.Category category = categories.get(index);
                View tile = workshopCategoryTile(category);
                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0, ui.dp(112), 1);
                if (column > 0) tileParams.setMargins(ui.dp(8), 0, 0, 0);
                line.addView(tile, tileParams);
            }
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, ui.dp(112));
            lineParams.setMargins(0, 0, 0, ui.dp(8));
            grid.addView(line, lineParams);
        }
        workshopCatalogList.addView(grid);
    }

    private View workshopCategoryTile(RewardCatalog.Category category) {
        LinearLayout tile = new LinearLayout(activity);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(ui.dp(12), ui.dp(10), ui.dp(10), ui.dp(8));
        tile.setBackground(workshopCategoryTileBackground(category.id));
        tile.setElevation(ui.dp(2));
        tile.setClipToOutline(true);

        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(workshopCategoryIcon(category.id));
        icon.setColorFilter(ACCENT_PURPLE);
        icon.setPadding(ui.dp(7), ui.dp(7), ui.dp(7), ui.dp(7));
        icon.setBackground(ui.panelBg(
            Color.rgb(244, 236, 255), ui.dp(12),
            Color.rgb(211, 190, 239)));
        head.addView(icon, new LinearLayout.LayoutParams(ui.dp(42), ui.dp(42)));
        View headSpacer = new View(activity);
        head.addView(headSpacer, new LinearLayout.LayoutParams(0, 1, 1));
        TextView arrow = new LocalizedTextView(activity);
        arrow.setText("›");
        arrow.setTextColor(AppThemePalette.primary());
        arrow.setTextSize(22);
        arrow.setGravity(Gravity.CENTER);
        arrow.setIncludeFontPadding(false);
        head.addView(arrow, new LinearLayout.LayoutParams(ui.dp(24), ui.dp(42)));
        tile.addView(head, new LinearLayout.LayoutParams(-1, ui.dp(42)));

        TextView title = new LocalizedTextView(activity);
        title.setText(category.title);
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setTextSize(13);
        title.setTypeface(ui.uiBold());
        title.setMaxLines(2);
        title.setGravity(Gravity.CENTER_VERTICAL);
        tile.addView(title, new LinearLayout.LayoutParams(-1, ui.dp(27)));
        TextView detail = new LocalizedTextView(activity);
        detail.setText(RewardCatalog.itemsFor(category.id).size() + " вариантов оформления");
        detail.setTextColor(Color.rgb(101, 113, 122));
        detail.setTextSize(9);
        detail.setSingleLine(true);
        detail.setGravity(Gravity.CENTER_VERTICAL);
        tile.addView(detail, new LinearLayout.LayoutParams(-1, ui.dp(18)));
        tile.setOnClickListener(v -> {
            workshopCategoryId = category.id;
            bindWorkshopCatalog();
        });
        tile.setContentDescription(category.title);
        return tile;
    }

    private GradientDrawable workshopCategoryTileBackground(String categoryId) {
        int accent = workshopCategoryAccent(RewardCatalog.CANVAS_BACKGROUNDS);
        GradientDrawable background = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{Color.WHITE, AppThemePalette.surface(accent)});
        background.setCornerRadius(ui.dp(15));
        background.setStroke(ui.dp(1), AppThemePalette.alpha(accent, 115));
        return background;
    }

    private int workshopCategoryAccent(String categoryId) {
        if (RewardCatalog.CARD_THEMES.equals(categoryId)) return Color.rgb(255, 239, 216);
        if (RewardCatalog.CARD_EDGES.equals(categoryId)) return Color.rgb(255, 232, 216);
        if (RewardCatalog.CANVAS_BACKGROUNDS.equals(categoryId)) return Color.rgb(226, 246, 241);
        if (RewardCatalog.LINK_STYLES.equals(categoryId)) return Color.rgb(229, 240, 255);
        if (RewardCatalog.WIDGET_FRAMES.equals(categoryId)) return Color.rgb(242, 233, 255);
        return Color.rgb(255, 235, 243);
    }

    private int workshopCategoryIcon(String categoryId) {
        if (RewardCatalog.CARD_THEMES.equals(categoryId)) return R.drawable.ic_menu_palette;
        if (RewardCatalog.CARD_EDGES.equals(categoryId)) return R.drawable.ic_menu_sparkles;
        if (RewardCatalog.CANVAS_BACKGROUNDS.equals(categoryId)) return R.drawable.ic_menu_image;
        if (RewardCatalog.LINK_STYLES.equals(categoryId)) return R.drawable.ic_menu_straight_links;
        if (RewardCatalog.WIDGET_FRAMES.equals(categoryId)) return R.drawable.ic_field_calendar;
        return R.drawable.ic_menu_export;
    }

    private void bindWorkshopCategory(String categoryId) {
        RewardCatalog.Category category = null;
        for (RewardCatalog.Category value : RewardCatalog.categories()) {
            if (value.id.equals(categoryId)) { category = value; break; }
        }
        if (category == null) { workshopCategoryId = ""; bindWorkshopCatalog(); return; }
        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new LocalizedTextView(activity);
        title.setText(category.title);
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setTextSize(18);
        title.setTypeface(ui.uiBold());
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(ui.dp(4), 0, ui.dp(8), 0);
        heading.addView(title, new LinearLayout.LayoutParams(0, ui.dp(48), 1));

        Button back = ui.actionButton("‹ Все разделы", v -> {
            workshopCategoryId = "";
            bindWorkshopCatalog();
        });
        back.setTextSize(12);
        back.setTextColor(AppThemePalette.secondary());
        back.setGravity(Gravity.CENTER);
        back.setPadding(ui.dp(14), 0, ui.dp(14), 0);
        back.setBackground(ui.panelBg(
            AppThemePalette.secondarySurface(),
            ui.dp(10),
            AppThemePalette.alpha(AppThemePalette.secondaryBright(), 72)));
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(ui.dp(154), ui.dp(42));
        heading.addView(back, backParams);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, ui.dp(58));
        headingParams.setMargins(0, ui.dp(5), 0, ui.dp(5));
        workshopCategoryHeader.addView(heading, headingParams);
        workshopCatalogList.addView(workshopItemRow(
            category.id, RewardCatalog.STANDARD, category.standardTitle,
            category.standardDetail, 0, ""));
        for (RewardCatalog.Item item : RewardCatalog.itemsFor(category.id)) {
            workshopCatalogList.addView(workshopItemRow(
                item.categoryId, item.id, item.title, item.detail, item.price, item.rarity));
        }
    }

    private View workshopItemRow(String categoryId, String itemId, String itemTitle, String itemDetail, int price, String rarity) {
        boolean selected = itemId.equals(rewardWallet.selected(categoryId));
        boolean owned = rewardWallet.owns(itemId);
        boolean standard = RewardCatalog.STANDARD.equals(itemId);
        boolean affordable = rewardWallet.balance() >= price;
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));
        row.setBackground(ui.panelBg(
            selected ? AppThemePalette.primarySurface() : Color.WHITE,
            ui.dp(8),
            selected ? AppThemePalette.alpha(AppThemePalette.primaryBright(), 110) : Color.rgb(217, 224, 229)));

        ImageView itemIcon = new ImageView(activity);
        itemIcon.setImageResource(workshopItemIcon(itemId));
        itemIcon.setColorFilter(workshopRarityTextColor(
            standard ? RewardCatalog.RARITY_COMMON : rarity));
        itemIcon.setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
        String iconRarity = standard ? RewardCatalog.RARITY_COMMON : rarity;
        itemIcon.setBackground(ui.panelBg(
            workshopRarityFillColor(iconRarity), ui.dp(10), workshopRarityStrokeColor(iconRarity)));
        LinearLayout.LayoutParams itemIconParams = new LinearLayout.LayoutParams(ui.dp(42), ui.dp(42));
        itemIconParams.setMargins(0, 0, ui.dp(10), 0);
        row.addView(itemIcon, itemIconParams);

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new LocalizedTextView(activity);
        title.setText(itemTitle);
        title.setTextColor(Color.rgb(28, 34, 38));
        title.setTextSize(13);
        title.setTypeface(ui.uiBold());
        title.setIncludeFontPadding(false);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ui.dp(24), 1));
        if (!standard) {
            TextView badge = new LocalizedTextView(activity);
            badge.setText(RewardCatalog.rarityLabel(rarity));
            badge.setTextColor(workshopRarityTextColor(rarity));
            badge.setTextSize(8);
            badge.setTypeface(ui.uiBold());
            badge.setGravity(Gravity.CENTER);
            badge.setSingleLine(true);
            badge.setIncludeFontPadding(false);
            badge.setBackground(ui.panelBg(
                workshopRarityFillColor(rarity),
                ui.dp(999),
                workshopRarityStrokeColor(rarity)));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(ui.dp(74), ui.dp(22));
            badgeParams.setMargins(ui.dp(8), 0, 0, 0);
            titleRow.addView(badge, badgeParams);
        }
        copy.addView(titleRow, new LinearLayout.LayoutParams(-1, ui.dp(24)));
        TextView detail = new LocalizedTextView(activity);
        detail.setText(itemDetail);
        detail.setTextColor(Color.rgb(101, 113, 122));
        detail.setTextSize(9);
        detail.setMaxLines(2);
        detail.setLineSpacing(ui.dp(1), 1f);
        copy.addView(detail, new LinearLayout.LayoutParams(-1, -2));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        String buttonText = selected
            ? "Выбрано"
            : owned
                ? "Выбрать"
                : price + " жет.";
        Button buy = ui.actionButton(buttonText, v -> chooseWorkshopItem(categoryId, itemId));
        buy.setTextSize(10);
        buy.setEnabled(!selected && (owned || affordable));
        buy.setAlpha(!selected && (owned || affordable) ? 1f : 0.62f);
        buy.setTextColor(selected || owned || standard ? AppThemePalette.primary() : affordable ? Color.WHITE : Color.rgb(76, 87, 96));
        buy.setBackground(selected || owned || standard
            ? ui.panelBg(AppThemePalette.primarySurface(), ui.dp(8), AppThemePalette.alpha(AppThemePalette.primaryBright(), 72))
            : affordable
                ? ui.tealGradientBg(ui.dp(8))
                : ui.panelBg(Color.rgb(244, 247, 248), ui.dp(8), Color.rgb(217, 224, 229)));
        LinearLayout.LayoutParams buyParams = new LinearLayout.LayoutParams(ui.dp(82), ui.dp(40));
        buyParams.setMargins(ui.dp(10), 0, 0, 0);
        row.addView(buy, buyParams);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, ui.dp(70));
        rowParams.setMargins(0, 0, 0, ui.dp(8));
        row.setLayoutParams(rowParams);
        return row;
    }

    private int workshopItemIcon(String itemId) {
        if ("standard".equals(itemId)) return R.drawable.ic_menu_minus;
        if ("card_theme_archive".equals(itemId)) return R.drawable.ic_menu_file;
        if ("card_theme_midnight".equals(itemId)) return R.drawable.ic_menu_moon;
        if ("card_theme_glass".equals(itemId)) return R.drawable.ic_menu_window;
        if ("card_theme_fire".equals(itemId)
            || "card_edge_fire".equals(itemId)
            || "link_style_fire".equals(itemId)
            || "widget_frame_fire".equals(itemId)
            || "export_frame_fire".equals(itemId)) return R.drawable.ic_menu_fire;
        if ("card_theme_smoke".equals(itemId)
            || "card_edge_smoke".equals(itemId)) return R.drawable.ic_menu_smoke;
        if ("card_theme_botanical".equals(itemId)
            || "card_edge_botanical".equals(itemId)
            || "link_style_botanical".equals(itemId)
            || "canvas_background_botanical".equals(itemId)
            || "widget_frame_botanical".equals(itemId)
            || "export_frame_botanical".equals(itemId)) return R.drawable.ic_menu_leaf;
        if ("canvas_background_blueprint".equals(itemId)
            || "link_style_ink".equals(itemId)) return R.drawable.ic_menu_pen;
        if ("canvas_background_deep_blueprint".equals(itemId)) return R.drawable.ic_menu_gears;
        if ("link_style_blueprint".equals(itemId)) return R.drawable.ic_menu_paint_can;
        if ("canvas_background_paper".equals(itemId)) return R.drawable.ic_menu_paper;
        if ("export_frame_classic".equals(itemId)) return R.drawable.ic_menu_pen;
        if ("canvas_background_garden".equals(itemId)) return R.drawable.ic_menu_leaf;
        if ("link_style_ribbon".equals(itemId)) return R.drawable.ic_menu_ribbon;
        if ("widget_frame_family".equals(itemId)) return R.drawable.ic_menu_people;
        return R.drawable.ic_menu_frame;
    }

    private int workshopRarityTextColor(String rarity) {
        if (RewardCatalog.RARITY_EPIC.equals(rarity)) return Color.rgb(122, 66, 158);
        if (RewardCatalog.RARITY_RARE.equals(rarity)) return Color.rgb(22, 104, 156);
        return Color.rgb(53, 119, 88);
    }

    private int workshopRarityFillColor(String rarity) {
        if (RewardCatalog.RARITY_EPIC.equals(rarity)) return Color.rgb(249, 242, 255);
        if (RewardCatalog.RARITY_RARE.equals(rarity)) return Color.rgb(239, 248, 255);
        return Color.rgb(241, 250, 245);
    }

    private int workshopRarityStrokeColor(String rarity) {
        if (RewardCatalog.RARITY_EPIC.equals(rarity)) return Color.rgb(218, 191, 238);
        if (RewardCatalog.RARITY_RARE.equals(rarity)) return Color.rgb(181, 218, 239);
        return Color.rgb(187, 224, 202);
    }

    private void chooseWorkshopItem(String categoryId, String itemId) {
        boolean owned = rewardWallet.owns(itemId);
        RewardCatalog.Item item = findWorkshopItem(itemId);
        if (!owned) {
            if (item == null || !rewardWallet.purchase(item)) {
                AnalyticsReporter.event("workshop_item_purchase_failed", "item_id", itemId);
                messages.accept("Не хватает жетонов");
                updateWorkshopUi();
                return;
            }
            AnalyticsReporter.workshopItemPurchased(item, rewardWallet.balance());
            messages.accept("Покупка добавлена в Мастерскую");
        }
        if (rewardWallet.select(categoryId, itemId)) {
            AnalyticsReporter.workshopItemSelected(categoryId, itemId);
            applyStyles();
            if (RewardCatalog.WIDGET_FRAMES.equals(categoryId)) BirthdayWidgetProvider.updateAll(activity);
            if (owned) messages.accept("Оформление выбрано");
        }
        updateWorkshopUi();
    }

    private RewardCatalog.Item findWorkshopItem(String itemId) {
        for (RewardCatalog.Item item : RewardCatalog.items()) {
            if (item.id.equals(itemId)) return item;
        }
        return null;
    }

    void applyStyles() {
        if (treeView() == null) return;
        treeView().setCardStyle(rewardWallet.selected(RewardCatalog.CARD_THEMES));
        treeView().setCardEdgeStyle(rewardWallet.selected(RewardCatalog.CARD_EDGES));
        treeView().setCanvasStyle(rewardWallet.selected(RewardCatalog.CANVAS_BACKGROUNDS));
        treeView().setLinkStyle(rewardWallet.selected(RewardCatalog.LINK_STYLES));
    }
}
