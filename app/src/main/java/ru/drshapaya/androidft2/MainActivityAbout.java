package ru.drshapaya.androidft2;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Owns the about, supporters and release-notes UI. */
final class MainActivityAbout {
    private static final String SUPPORT_URL =
        "https://www.donationalerts.com/r/drshapaya";
    private static final String SUPPORTERS_URL =
        "https://raw.githubusercontent.com/DrShapaya/Family-Tree-DS/main/AndroidFT%202.0/app/src/main/assets/supporters.json";
    private static final String SUPPORTERS_CACHE = "supporters_cache";
    private static final String SUPPORTERS_CACHE_JSON = "json";
    private static final long SUPPORTERS_REQUEST_BUCKET_MS = 30000L;
    private static final long SUPPORTERS_MEMORY_FRESH_MS = 15000L;
    private static final int ACCENT_PURPLE = Color.rgb(106, 74, 177);

    private final Activity activity;
    private final MainUiKit ui;
    private final Consumer<String> messages;
    private final Runnable developerMenu;
    private final Object supporterRequestLock = new Object();
    private final ArrayList<Consumer<SupporterCatalog.RemoteData>> supporterCallbacks = new ArrayList<>();
    private boolean supporterRequestRunning;
    private long lastSupporterFetchAt;
    private SupporterCatalog.RemoteData latestSupporterData;

    MainActivityAbout(
        Activity activity,
        MainUiKit ui,
        Consumer<String> messages,
        Runnable developerMenu
    ) {
        this.activity = activity;
        this.ui = ui;
        this.messages = messages;
        this.developerMenu = developerMenu;
        requestSupporterData(null);
    }

    private TextView aboutText(
        String value,
        float size,
        int color,
        boolean bold,
        int gravity
    ) {
        TextView text = new LocalizedTextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(AppThemePalette.text(color));
        text.setTypeface(bold ? ui.uiBold() : ui.ui());
        text.setGravity(gravity);
        text.setLineSpacing(ui.dp(2), 1f);
        return text;
    }

    void open() {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = ui.editorDialogShell();
        shell.addView(ui.editorDialogHeader(dialog, R.drawable.ic_menu_info, "О нас", "Family Tree DS"));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(ui.dp(2), ui.dp(6), ui.dp(2), ui.dp(2));

        LinearLayout identity = new LinearLayout(activity);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        identity.setOrientation(LinearLayout.HORIZONTAL);

        ImageView appIcon = new ImageView(activity);
        appIcon.setImageResource(R.drawable.app_icon);
        appIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        appIcon.setBackground(ui.panelBg(Color.WHITE, ui.dp(14), Color.argb(60, 24, 169, 153)));
        appIcon.setClipToOutline(true);
        appIcon.setElevation(ui.dp(5));
        final int[] developerTaps = {0};
        final long[] lastDeveloperTap = {0L};
        appIcon.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            if (now - lastDeveloperTap[0] > 1400L) developerTaps[0] = 0;
            lastDeveloperTap[0] = now;
            developerTaps[0]++;
            if (developerTaps[0] < 5) return;
            developerTaps[0] = 0;
            dialog.dismiss();
            developerMenu.run();
        });
        identity.addView(appIcon, new LinearLayout.LayoutParams(ui.dp(76), ui.dp(76)));

        LinearLayout identityText = new LinearLayout(activity);
        identityText.setGravity(Gravity.CENTER_VERTICAL);
        identityText.setOrientation(LinearLayout.VERTICAL);
        identityText.setPadding(ui.dp(10), 0, ui.dp(5), 0);
        TextView appName = aboutText("Family Tree DS", 20, Color.rgb(28, 34, 38), true, Gravity.LEFT);
        identityText.addView(appName, new LinearLayout.LayoutParams(-1, ui.dp(29)));
        TextView createdBy = aboutText("Приложение создано TeamDS", 10, Color.rgb(83, 94, 103), false, Gravity.LEFT);
        identityText.addView(createdBy, new LinearLayout.LayoutParams(-1, ui.dp(21)));
        TextView version = aboutText(
            "Версия " + BuildConfig.VERSION_NAME,
            10,
            Color.rgb(8, 122, 115),
            true,
            Gravity.LEFT | Gravity.CENTER_VERTICAL);
        version.setSingleLine(true);
        identityText.addView(version, new LinearLayout.LayoutParams(-1, ui.dp(20)));
        identity.addView(identityText, new LinearLayout.LayoutParams(0, ui.dp(76), 1f));
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(-1, ui.dp(76));
        identityParams.setMargins(0, 0, 0, ui.dp(6));
        body.addView(identity, identityParams);

        Button supportDeveloper = ui.actionButton("Поддержать разработчика", v ->
            openExternalUrl(SUPPORT_URL));
        supportDeveloper.setTextSize(10);
        supportDeveloper.setSingleLine(true);
        supportDeveloper.setTextColor(Color.rgb(8, 122, 115));
        supportDeveloper.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_heart, 0, 0, 0);
        supportDeveloper.setCompoundDrawablePadding(ui.dp(6));
        ui.tintDrawables(supportDeveloper, Color.rgb(8, 122, 115));
        supportDeveloper.setBackground(ui.panelBg(Color.rgb(235, 248, 246), ui.dp(999), Color.argb(64, 24, 169, 153)));
        LinearLayout.LayoutParams supportParams = new LinearLayout.LayoutParams(-1, ui.dp(42));
        supportParams.setMargins(0, 0, 0, ui.dp(8));
        body.addView(supportDeveloper, supportParams);

        LinearLayout socials = new LinearLayout(activity);
        socials.setGravity(Gravity.CENTER);
        socials.addView(socialButton(R.drawable.ic_social_telegram, "Telegram-канал", "https://t.me/FamilyTreeDS"), socialParams(0));
        socials.addView(socialButton(R.drawable.ic_social_chat, "Telegram-чат", "https://t.me/ChatFamilyTree"), socialParams(ui.dp(8)));
        socials.addView(socialButton(R.drawable.ic_social_github, "GitHub", "https://github.com/DrShapaya/Family-Tree-DS"), socialParams(ui.dp(8)));
        socials.addView(socialButton(R.drawable.ic_rustore, "RuStore", "https://www.rustore.ru/catalog/app/ru.drshapaya.familytree"), socialParams(ui.dp(8)));
        LinearLayout.LayoutParams socialsParams = new LinearLayout.LayoutParams(-1, ui.dp(46));
        socialsParams.setMargins(0, 0, 0, ui.dp(6));
        body.addView(socials, socialsParams);

        LinearLayout notesCard = aboutCard();
        String notesTitle = AppLanguage.isEnglish(activity)
            ? "Update " + releaseNotesVersion() + " since " + releaseNotesBaseVersion()
            : "Обновление " + releaseNotesVersion() + " по сравнению с " + releaseNotesBaseVersion();
        notesCard.addView(aboutText(notesTitle, 14, Color.rgb(28, 34, 38), true, Gravity.LEFT));
        LinearLayout notesList = new LinearLayout(activity);
        notesList.setOrientation(LinearLayout.VERTICAL);
        notesCard.addView(notesList, new LinearLayout.LayoutParams(-1, -2));
        bindReleaseNotes(notesList, releaseNotes(), false);
        body.addView(notesCard, aboutCardParams());

        LinearLayout teamCard = aboutCard();
        teamCard.addView(aboutText("Команда", 14, Color.rgb(28, 34, 38), true, Gravity.LEFT));
        TextView developer = aboutText("DrShapaya", 13, Color.rgb(8, 122, 115), true, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        developer.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_people, 0, 0, 0);
        developer.setCompoundDrawablePadding(ui.dp(9));
        ui.tintDrawables(developer, Color.rgb(8, 122, 115));
        installHoldTooltip(developer, "Оригинальный создатель, разработчик и дизайнер приложения");
        developer.setBackground(ui.panelBg(Color.rgb(235, 248, 246), ui.dp(13), Color.argb(52, 24, 169, 153)));
        developer.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        LinearLayout.LayoutParams developerParams = new LinearLayout.LayoutParams(-1, ui.dp(42));
        developerParams.setMargins(0, ui.dp(6), 0, 0);
        teamCard.addView(developer, developerParams);
        body.addView(teamCard, aboutCardParams());

        SupporterCatalog.RemoteData initialData = loadSupporterData();

        LinearLayout fundraiserCard = aboutCard();
        bindFundraiser(fundraiserCard, initialData.fundraiser);
        body.addView(fundraiserCard, aboutCardParams());

        LinearLayout donorsCard = aboutCard();
        donorsCard.addView(aboutText("Топ донатеры", 14, Color.rgb(28, 34, 38), true, Gravity.LEFT));
        bindSupporters(donorsCard, initialData.supporters, initialData.displayLimit);
        body.addView(donorsCard, aboutCardParams());

        refreshSupporterData(dialog, fundraiserCard, donorsCard, initialData);

        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        ui.showEditorDialog(dialog, shell);
    }

    private void bindSupporters(
        LinearLayout card,
        List<SupporterCatalog.Entry> supporters,
        int displayLimit
    ) {
        if (card.getChildCount() > 1) card.removeViews(1, card.getChildCount() - 1);
        if (supporters.isEmpty()) {
            LinearLayout empty = new LinearLayout(activity);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            TextView nobody = aboutText(
                "Пока здесь никого нет",
                11,
                Color.rgb(83, 94, 103),
                false,
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
            empty.addView(nobody, new LinearLayout.LayoutParams(0, ui.dp(40), 1));
            Button first = ui.actionButton("Стать первым", v -> openExternalUrl(SUPPORT_URL));
            first.setTextSize(10);
            first.setTextColor(ACCENT_PURPLE);
            first.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_heart, 0, 0, 0);
            first.setCompoundDrawablePadding(ui.dp(5));
            ui.tintDrawables(first, ACCENT_PURPLE);
            first.setBackground(ui.panelBg(Color.TRANSPARENT, ui.dp(13), Color.argb(150, 106, 74, 177)));
            LinearLayout.LayoutParams firstParams = new LinearLayout.LayoutParams(ui.dp(124), ui.dp(40));
            firstParams.setMargins(ui.dp(10), 0, 0, 0);
            empty.addView(first, firstParams);
            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(-1, ui.dp(40));
            emptyParams.setMargins(0, ui.dp(5), 0, 0);
            card.addView(empty, emptyParams);
            return;
        }

        int visible = Math.min(displayLimit, supporters.size());
        for (int index = 0; index < visible; index++) {
            SupporterCatalog.Entry supporter = supporters.get(index);
            boolean leader = index == 0;
            int accent = supporterColor(supporter);
            int accentText = readableAccent(accent);
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(ui.dp(10), 0, ui.dp(10), 0);
            row.setBackground(ui.panelBg(
                blendColor(accent, Color.WHITE, leader ? 0.86f : 0.93f),
                ui.dp(13),
                blendColor(accent, Color.WHITE, leader ? 0.34f : 0.58f)));

            TextView crown = aboutText(
                leader ? "♛" : String.valueOf(index + 1),
                leader ? 24 : 11,
                accentText,
                leader,
                Gravity.CENTER);
            crown.setTypeface(Typeface.DEFAULT_BOLD);
            LocalizedViews.setRaw(crown, leader ? "♛" : String.valueOf(index + 1));
            row.addView(crown, new LinearLayout.LayoutParams(ui.dp(38), -1));

            TextView name = aboutText(
                supporter.name,
                leader ? 14 : 11,
                accentText,
                leader,
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
            LocalizedViews.setRaw(name, supporter.name);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(name, new LinearLayout.LayoutParams(0, -1, 1));

            if (supporter.showHeart) {
                TextView heart = aboutText("", 12, accentText, false, Gravity.CENTER);
                heart.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_heart, 0, 0, 0);
                ui.tintDrawables(heart, accentText);
                heart.setContentDescription(AppLanguage.isEnglish(activity)
                    ? "Featured supporter"
                    : "Особый донатер");
                row.addView(heart, new LinearLayout.LayoutParams(ui.dp(25), -1));
            }

            String amount = formatDonationAmount(supporter.amount, supporter.currency);
            TextView amountView = aboutText(
                amount,
                leader ? 13 : 10,
                accentText,
                true,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            LocalizedViews.setRaw(amountView, amount);
            row.addView(amountView, new LinearLayout.LayoutParams(-2, -1));

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                -1,
                ui.dp(leader ? 52 : 44));
            rowParams.setMargins(0, ui.dp(index == 0 ? 7 : 5), 0, 0);
            card.addView(row, rowParams);
        }

        SupporterCatalog.Entry leader = supporters.get(0);
        long recordAmount = SupporterCatalog.amountToBeat(leader);
        String suggested = formatDonationAmount(recordAmount, leader.currency);
        String buttonLabel = AppLanguage.isEnglish(activity)
            ? "Beat the record · " + suggested
            : "Побить рекорд · " + suggested;
        Button beatRecord = ui.actionButton(buttonLabel, v -> openRecordDonation(recordAmount, leader.currency));
        LocalizedViews.setRaw(beatRecord, buttonLabel);
        beatRecord.setTextSize(10);
        beatRecord.setSingleLine(true);
        beatRecord.setTextColor(Color.WHITE);
        beatRecord.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_heart, 0, 0, 0);
        beatRecord.setCompoundDrawablePadding(ui.dp(7));
        ui.tintDrawables(beatRecord, Color.WHITE);
        beatRecord.setBackground(ui.tealGradientBg(ui.dp(13)));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, ui.dp(42));
        buttonParams.setMargins(0, ui.dp(9), 0, 0);
        card.addView(beatRecord, buttonParams);
    }

    private int supporterColor(SupporterCatalog.Entry supporter) {
        try {
            return Color.parseColor(supporter == null ? "#087A73" : supporter.color);
        } catch (Exception ignored) {
            return Color.rgb(8, 122, 115);
        }
    }

    private int readableAccent(int color) {
        double luminance = 0.299d * Color.red(color)
            + 0.587d * Color.green(color)
            + 0.114d * Color.blue(color);
        return luminance > 178d ? blendColor(color, Color.BLACK, 0.38f) : color;
    }

    private int blendColor(int from, int to, float ratio) {
        float safeRatio = Math.max(0f, Math.min(1f, ratio));
        float keep = 1f - safeRatio;
        return Color.rgb(
            Math.round(Color.red(from) * keep + Color.red(to) * safeRatio),
            Math.round(Color.green(from) * keep + Color.green(to) * safeRatio),
            Math.round(Color.blue(from) * keep + Color.blue(to) * safeRatio));
    }

    private SupporterCatalog.RemoteData loadSupporterData() {
        String cached = activity.getSharedPreferences(SUPPORTERS_CACHE, Context.MODE_PRIVATE)
            .getString(SUPPORTERS_CACHE_JSON, "");
        if (cached != null && !cached.trim().isEmpty()) {
            return SupporterCatalog.parseData(cached);
        }
        try (InputStream input = activity.getAssets().open("supporters.json")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return SupporterCatalog.parseData(output.toString(StandardCharsets.UTF_8.name()));
        } catch (Exception ignored) {
            return SupporterCatalog.parseData(null);
        }
    }

    private void refreshSupporterData(
        Dialog dialog,
        LinearLayout fundraiserCard,
        LinearLayout donorsCard,
        SupporterCatalog.RemoteData initialData
    ) {
        String initialSignature = supporterDataSignature(initialData);
        requestSupporterData(remote -> {
            if (!dialog.isShowing() || initialSignature.equals(supporterDataSignature(remote))) return;
            fundraiserCard.animate().cancel();
            donorsCard.animate().cancel();
            fundraiserCard.animate().alpha(0f).setDuration(70L).start();
            donorsCard.animate().alpha(0f).setDuration(70L).withEndAction(() -> {
                if (!dialog.isShowing()) return;
                bindFundraiser(fundraiserCard, remote.fundraiser);
                bindSupporters(donorsCard, remote.supporters, remote.displayLimit);
                fundraiserCard.animate().alpha(1f).setDuration(140L).start();
                donorsCard.animate().alpha(1f).setDuration(140L).start();
            }).start();
        });
    }

    private void requestSupporterData(Consumer<SupporterCatalog.RemoteData> callback) {
        SupporterCatalog.RemoteData freshData = null;
        boolean startRequest = false;
        synchronized (supporterRequestLock) {
            long age = System.currentTimeMillis() - lastSupporterFetchAt;
            if (callback != null && latestSupporterData != null && age < SUPPORTERS_MEMORY_FRESH_MS) {
                freshData = latestSupporterData;
            } else {
                if (callback != null) supporterCallbacks.add(callback);
                if (!supporterRequestRunning) {
                    supporterRequestRunning = true;
                    startRequest = true;
                }
            }
        }
        if (freshData != null) {
            callback.accept(freshData);
            return;
        }
        if (!startRequest) return;
        new Thread(() -> {
            SupporterCatalog.RemoteData remote = loadRemoteSupporterData();
            ArrayList<Consumer<SupporterCatalog.RemoteData>> callbacks;
            synchronized (supporterRequestLock) {
                supporterRequestRunning = false;
                if (remote != null) {
                    latestSupporterData = remote;
                    lastSupporterFetchAt = System.currentTimeMillis();
                }
                callbacks = new ArrayList<>(supporterCallbacks);
                supporterCallbacks.clear();
            }
            if (remote == null || callbacks.isEmpty()) return;
            activity.runOnUiThread(() -> {
                for (Consumer<SupporterCatalog.RemoteData> consumer : callbacks) {
                    consumer.accept(remote);
                }
            });
        }, "supporters-prefetch").start();
    }

    private SupporterCatalog.RemoteData loadRemoteSupporterData() {
        HttpURLConnection connection = null;
        try {
            // A short shared bucket keeps GitHub's CDN useful while still publishing
            // admin-panel updates to the app within roughly half a minute.
            connection = (HttpURLConnection) new URL(
                SUPPORTERS_URL + "?v=" + (System.currentTimeMillis() / SUPPORTERS_REQUEST_BUCKET_MS))
                .openConnection();
            connection.setConnectTimeout(3500);
            connection.setReadTimeout(3500);
            connection.setUseCaches(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "FamilyTreeDS/" + BuildConfig.VERSION_NAME);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                String json = output.toString(StandardCharsets.UTF_8.name());
                JSONObject root = new JSONObject(json);
                if (!root.has("supporters") && !root.has("fundraiser")) return null;
                SupporterCatalog.RemoteData parsed = SupporterCatalog.parseData(json);
                activity.getSharedPreferences(SUPPORTERS_CACHE, Context.MODE_PRIVATE)
                    .edit()
                    .putString(SUPPORTERS_CACHE_JSON, json)
                    .apply();
                return parsed;
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String supporterDataSignature(SupporterCatalog.RemoteData data) {
        if (data == null || data.fundraiser == null) return "";
        StringBuilder value = new StringBuilder()
            .append(data.displayLimit).append('|')
            .append(data.fundraiser.title).append('|')
            .append(data.fundraiser.description).append('|')
            .append(data.fundraiser.goal).append('|')
            .append(data.fundraiser.raised).append('|')
            .append(data.fundraiser.currency);
        for (SupporterCatalog.Entry supporter : data.supporters) {
            value.append('|').append(supporter.name)
                .append(':').append(supporter.amount)
                .append(':').append(supporter.currency)
                .append(':').append(supporter.color)
                .append(':').append(supporter.showHeart)
                .append(':').append(supporter.order);
        }
        return value.toString();
    }

    private void bindFundraiser(LinearLayout card, SupporterCatalog.Fundraiser fundraiser) {
        card.removeAllViews();
        SupporterCatalog.Fundraiser data = fundraiser == null
            ? new SupporterCatalog.Fundraiser("Сбор на сервер", "", 30000d, 0d, "RUB")
            : fundraiser;

        TextView title = aboutText(data.title, 14, Color.rgb(28, 34, 38), true, Gravity.LEFT);
        card.addView(title, new LinearLayout.LayoutParams(-1, ui.dp(26)));

        if (!data.description.isEmpty()) {
            TextView description = aboutText(
                data.description,
                10,
                Color.rgb(83, 94, 103),
                false,
                Gravity.LEFT);
            description.setPadding(0, ui.dp(2), 0, 0);
            card.addView(description, new LinearLayout.LayoutParams(-1, -2));
        }

        double ratio = data.goal <= 0d ? 0d : Math.max(0d, Math.min(1d, data.raised / data.goal));
        FrameLayout track = new FrameLayout(activity);
        track.setBackground(roundFill(Color.rgb(224, 232, 235), ui.dp(999)));
        View fill = new View(activity);
        fill.setBackground(roundFill(Color.rgb(8, 122, 115), ui.dp(999)));
        track.addView(fill, new FrameLayout.LayoutParams(0, -1));
        track.post(() -> {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) fill.getLayoutParams();
            params.width = Math.max(ratio > 0d ? ui.dp(8) : 0, Math.round(track.getWidth() * (float) ratio));
            fill.setLayoutParams(params);
        });
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(-1, ui.dp(11));
        trackParams.setMargins(0, ui.dp(9), 0, ui.dp(7));
        card.addView(track, trackParams);

        LinearLayout amountRow = new LinearLayout(activity);
        amountRow.setGravity(Gravity.CENTER_VERTICAL);
        String raised = formatDonationAmount(data.raised, data.currency);
        String goal = formatDonationAmount(data.goal, data.currency);
        TextView amount = aboutText(
            raised + " из " + goal,
            11,
            Color.rgb(8, 122, 115),
            true,
            Gravity.LEFT);
        amountRow.addView(amount, new LinearLayout.LayoutParams(0, ui.dp(22), 1f));
        TextView percent = aboutText(
            Math.round(ratio * 100d) + "%",
            11,
            Color.rgb(106, 74, 177),
            true,
            Gravity.RIGHT);
        amountRow.addView(percent, new LinearLayout.LayoutParams(-2, ui.dp(22)));
        card.addView(amountRow, new LinearLayout.LayoutParams(-1, ui.dp(22)));
    }

    private GradientDrawable roundFill(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private String formatDonationAmount(double amount, String currency) {
        java.text.NumberFormat format = java.text.NumberFormat.getNumberInstance(
            AppLanguage.isEnglish(activity) ? Locale.US : new Locale("ru", "RU"));
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(Math.abs(amount - Math.rint(amount)) < 0.001d ? 0 : 2);
        String code = currency == null ? "RUB" : currency.toUpperCase(Locale.ROOT);
        String mark;
        if ("RUB".equals(code)) mark = "₽";
        else if ("USD".equals(code)) mark = "$";
        else if ("EUR".equals(code)) mark = "€";
        else if ("KZT".equals(code)) mark = "₸";
        else mark = code;
        return format.format(amount) + " " + mark;
    }

    private void openRecordDonation(long amount, String currency) {
        String amountText = String.valueOf(amount);
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("DonationAlerts amount", amountText));
            String display = formatDonationAmount(amount, currency);
            messages.accept(AppLanguage.isEnglish(activity)
                ? display + " copied — paste it into DonationAlerts"
                : display + " скопировано — вставьте сумму в DonationAlerts");
        }
        openExternalUrl(SUPPORT_URL);
    }

    private void bindReleaseNotes(LinearLayout host, List<String> notes, boolean expanded) {
        host.removeAllViews();
        int visible = expanded ? notes.size() : 0;
        for (int index = 0; index < visible; index++) {
            String translatedNote = AppLanguage.translateFully(activity, notes.get(index));
            String value = "•  " + translatedNote;
            TextView noteView = aboutText(value, 10, Color.rgb(70, 83, 92), false, Gravity.LEFT);
            LocalizedViews.setRaw(noteView, value);
            noteView.setPadding(0, ui.dp(5), 0, 0);
            if (!expanded) {
                noteView.setMaxLines(2);
                noteView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            }
            host.addView(noteView, new LinearLayout.LayoutParams(-1, -2));
        }
        if (notes.isEmpty()) return;
        String label = expanded
            ? AppLanguage.text(activity, "Свернуть")
            : (AppLanguage.isEnglish(activity) ? "View list" : "Посмотреть список")
                + " · " + notes.size();
        Button toggle = ui.actionButton(label, v -> bindReleaseNotes(host, notes, !expanded));
        toggle.setTextColor(Color.rgb(8, 122, 115));
        toggle.setBackground(ui.panelBg(Color.rgb(235, 248, 246), ui.dp(13), Color.argb(52, 24, 169, 153)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, ui.dp(36));
        params.setMargins(0, ui.dp(expanded ? 7 : 3), 0, 0);
        host.addView(toggle, params);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void installHoldTooltip(TextView anchor, String message) {
        Handler handler = new Handler(Looper.getMainLooper());
        final PopupWindow[] tooltip = {null};
        Runnable show = () -> {
            if (!anchor.isPressed() || tooltip[0] != null) return;
            TextView content = aboutText(message, 11, Color.WHITE, false, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            LocalizedViews.setRaw(content, AppLanguage.translateFully(activity, message));
            content.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10));
            content.setBackground(ui.panelBg(Color.rgb(35, 55, 62), ui.dp(14), Color.argb(70, 255, 255, 255)));
            int width = Math.min(ui.dp(330), activity.getResources().getDisplayMetrics().widthPixels - ui.dp(36));
            content.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            PopupWindow popup = new PopupWindow(content, width, content.getMeasuredHeight(), false);
            popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popup.setTouchable(false);
            popup.setOutsideTouchable(false);
            popup.setElevation(ui.dp(8));
            tooltip[0] = popup;
            popup.showAsDropDown(anchor, anchor.getWidth() - width, -anchor.getHeight() - content.getMeasuredHeight() - ui.dp(8));
        };
        anchor.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                view.setPressed(true);
                handler.postDelayed(show, ViewConfiguration.getLongPressTimeout());
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                handler.removeCallbacks(show);
                view.setPressed(false);
                if (tooltip[0] != null) {
                    tooltip[0].dismiss();
                    tooltip[0] = null;
                }
                return true;
            }
            return true;
        });
    }

    private LinearLayout aboutCard() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(ui.dp(13), ui.dp(10), ui.dp(13), ui.dp(10));
        card.setBackground(ui.panelBg(Color.rgb(248, 251, 252), ui.dp(18), Color.rgb(217, 224, 229)));
        return card;
    }

    private LinearLayout.LayoutParams aboutCardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, ui.dp(6));
        return params;
    }

    private ImageView socialButton(int icon, String description, String url) {
        ImageView button = new ImageView(activity);
        button.setImageResource(icon);
        button.setContentDescription(description);
        button.setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9));
        button.setColorFilter(ACCENT_PURPLE);
        button.setBackground(ui.panelBg(Color.TRANSPARENT, ui.dp(999), Color.argb(150, 106, 74, 177)));
        button.setElevation(0f);
        button.setOnClickListener(v -> openExternalUrl(url));
        return button;
    }

    private LinearLayout.LayoutParams socialParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40));
        params.setMargins(leftMargin, 0, 0, 0);
        return params;
    }

    private void openExternalUrl(String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
            messages.accept("Не удалось открыть ссылку");
        }
    }

    private String releaseNotesVersion() {
        try {
            return releaseNotesJson().optString("version", BuildConfig.VERSION_NAME);
        } catch (Exception ignored) {
            return BuildConfig.VERSION_NAME;
        }
    }

    private String releaseNotesBaseVersion() {
        try {
            return releaseNotesJson().optString("baseVersion", "2.7.4");
        } catch (Exception ignored) {
            return "2.7.4";
        }
    }

    private List<String> releaseNotes() {
        ArrayList<String> notes = new ArrayList<>();
        try {
            JSONArray values = releaseNotesJson().optJSONArray("notes");
            if (values != null) {
                for (int index = 0; index < values.length(); index++) {
                    String note = values.optString(index, "").trim();
                    if (!note.isEmpty()) notes.add(note);
                }
            }
        } catch (Exception ignored) {
        }
        if (notes.isEmpty()) notes.add("Улучшения стабильности и интерфейса.");
        return notes;
    }

    private JSONObject releaseNotesJson() throws Exception {
        try (InputStream input = activity.getAssets().open("release_notes.json")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
        }
    }
}
