package ru.drshapaya.androidft2;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class MainActivityOnline {
    private static final int INK = Color.rgb(24, 32, 38);
    private static final int MUTED = Color.rgb(91, 105, 114);
    private static final int TEAL = Color.rgb(8, 122, 115);
    private static final int MINT = Color.rgb(24, 169, 153);
    private static final int SURFACE = Color.rgb(248, 251, 252);
    private static final int BORDER = Color.rgb(215, 225, 229);
    private static final int DANGER = Color.rgb(190, 68, 67);
    private final MainActivity activity;
    private final OnlineTreeManager manager;
    private Dialog dashboard;
    private LinearLayout dashboardContent;
    private Dialog loginCodeDialog;
    private boolean discoveryOffered;
    private boolean discoveryRunning;
    private String pendingInvitationKey = "";

    MainActivityOnline(MainActivity activity, OnlineTreeManager manager) {
        this.activity = activity;
        this.manager = manager;
    }

    void openDashboard() {
        if (dashboard != null && dashboard.isShowing()) {
            renderDashboard();
            offerRecoveryIfNeeded();
            return;
        }
        dashboard = new Dialog(activity);
        dashboard.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(activity.dp(14), activity.dp(14), activity.dp(14), activity.dp(14));
        shell.setBackground(activity.panelBg(
            SURFACE,
            activity.dp(20),
            Color.argb(46, 63, 82, 94)));

        LinearLayout hero = new LinearLayout(activity);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(activity.dp(16), activity.dp(14), activity.dp(12), activity.dp(14));
        hero.setBackground(activity.tealGradientBg(activity.dp(17)));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = label("ANDROIDFT 2.6.0  ·  ПРИВАТНЫЙ РАЗДЕЛ", 9, Color.argb(210, 255, 255, 255), true);
        TextView title = label("Семейное облако", 23, Color.WHITE, true);
        titles.addView(eyebrow, new LinearLayout.LayoutParams(-1, activity.dp(18)));
        titles.addView(title, new LinearLayout.LayoutParams(-1, activity.dp(32)));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(activity.closeButton(v -> dashboard.dismiss()),
            new LinearLayout.LayoutParams(activity.dp(42), activity.dp(42)));
        hero.addView(header);

        TextView explanation = label(
            "Приватное дерево хранится в GitHub и остаётся доступным участникам независимо от телефона главы.",
            12,
            Color.argb(220, 255, 255, 255),
            false);
        explanation.setLineSpacing(activity.dp(2), 1f);
        LinearLayout.LayoutParams explanationParams = new LinearLayout.LayoutParams(-1, -2);
        explanationParams.setMargins(0, activity.dp(7), activity.dp(4), 0);
        hero.addView(explanation, explanationParams);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2);
        heroParams.setMargins(0, 0, 0, activity.dp(12));
        shell.addView(hero, heroParams);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        dashboardContent = new LinearLayout(activity);
        dashboardContent.setOrientation(LinearLayout.VERTICAL);
        dashboardContent.setPadding(0, 0, 0, activity.dp(8));
        scroll.addView(dashboardContent, new ScrollView.LayoutParams(-1, -2));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        dashboard.setContentView(shell);
        dashboard.setOnDismissListener(dialog -> {
            dashboard = null;
            dashboardContent = null;
        });
        configureDialog(dashboard, shell);
        renderDashboard();
        dashboard.show();
        offerRecoveryIfNeeded();
    }

    void refreshOpenDashboard() {
        if (dashboard != null && dashboard.isShowing()) renderDashboard();
    }

    private void renderDashboard() {
        if (dashboardContent == null) return;
        dashboardContent.removeAllViews();
        dashboardContent.addView(statusCard());

        if (!manager.signedIn()) {
            dashboardContent.addView(sectionTitle("Аккаунт GitHub"));
            dashboardContent.addView(infoCard(
                "Подключите GitHub один раз",
                "Нажмите кнопку, подтвердите доступ на GitHub и автоматически вернитесь в AndroidFT. Токен останется в защищённом хранилище телефона."));
            if (manager.browserSignInAvailable()) {
                dashboardContent.addView(primaryButton(
                    "Продолжить через GitHub",
                    v -> startBrowserSignIn()));
                dashboardContent.addView(secondaryButton(
                    "Резервный вход по коду",
                    v -> startCodeSignIn()));
            } else {
                dashboardContent.addView(primaryButton(
                    "Войти по коду",
                    v -> startCodeSignIn()));
            }
            dashboardContent.addView(note(
                "Потребуются разрешения на приватные репозитории и Gist: дерево хранится в репозитории, а зашифрованные запросы — в канале приглашений."));
            return;
        }

        dashboardContent.addView(accountCard());
        if (!manager.connected()) {
            dashboardContent.addView(sectionTitle("Онлайн-дерево"));
            dashboardContent.addView(actionCard(
                "Загрузить дерево из GitHub",
                "Найти доступные этому аккаунту деревья после переустановки или смены телефона.",
                v -> discoverOnlineTrees(false)));
            dashboardContent.addView(actionCard(
                "Создать онлайн-дерево",
                "Текущее дерево станет приватным репозиторием вашего аккаунта.",
                v -> confirmCreateTree()));
            dashboardContent.addView(actionCard(
                "Присоединиться по ключу",
                "Введите ключ, полученный от главы семейного дерева.",
                v -> openJoinDialog()));
            dashboardContent.addView(secondaryButton("Выйти из GitHub", v -> {
                manager.signOut();
                renderDashboard();
            }));
            return;
        }

        dashboardContent.addView(sectionTitle("Подключённое дерево"));
        dashboardContent.addView(treeCard());
        dashboardContent.addView(note(
            "Фото, документы, аудио и видео синхронизируются вместе с деревом через приватное хранилище GitHub."));
        dashboardContent.addView(primaryButton(
            "Синхронизировать сейчас",
            v -> retrySync()));

        if (manager.isOwner()) {
            dashboardContent.addView(sectionTitle("Приглашение близких"));
            dashboardContent.addView(invitationCard());
            dashboardContent.addView(actionCard(
                "Кто подключён",
                "Просмотр участников и отзыв доступа к дереву.",
                v -> openParticipants()));
        } else {
            dashboardContent.addView(note(
                "Вы подключены как участник. Глава может управлять доступом, но для синхронизации его телефон не требуется."));
        }

        dashboardContent.addView(sectionTitle("Управление"));
        if (!manager.isOwner()) {
            dashboardContent.addView(secondaryButton("Покинуть общее дерево", v ->
                confirm(
                    "Покинуть общее дерево?",
                    "Ваш GitHub-аккаунт потеряет доступ. Локальная копия останется на телефоне.",
                    "Покинуть",
                    () -> manager.leaveTree(new OnlineTreeManager.Callback<Void>() {
                        @Override public void onSuccess(Void result) {
                            activity.toast("Вы покинули общее дерево");
                            renderDashboard();
                        }

                        @Override public void onError(String message) {
                            activity.toast(message);
                        }
                    }))));
        }
        dashboardContent.addView(secondaryButton("Отключить дерево на этом телефоне", v ->
            confirm(
                "Отключить онлайн-дерево?",
                "Локальная копия останется на телефоне. Репозиторий и доступ других участников не изменятся.",
                "Отключить",
                () -> {
                    manager.disconnectTree();
                    renderDashboard();
                })));
        dashboardContent.addView(secondaryButton("Выйти из GitHub", v ->
            confirm(
                "Выйти из GitHub?",
                "Онлайн-дерево останется в GitHub. На этом телефоне синхронизация будет отключена.",
                "Выйти",
                () -> {
                    manager.signOut();
                    renderDashboard();
                })));
    }

    private View statusCard() {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        boolean problem = "Офлайн".equals(manager.status())
            || manager.status().startsWith("Ошибка");
        TextView stateIcon = label(problem ? "!" : "✓", 15, Color.WHITE, true);
        stateIcon.setGravity(Gravity.CENTER);
        stateIcon.setBackground(activity.panelBg(
            problem ? DANGER : MINT,
            activity.dp(999),
            Color.TRANSPARENT));
        card.addView(stateIcon, new LinearLayout.LayoutParams(activity.dp(38), activity.dp(38)));
        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(activity.dp(11), 0, 0, 0);
        text.addView(label(manager.status(), 14, INK, true));
        String detail = manager.lastError().isEmpty()
            ? statusDetail()
            : manager.lastError();
        TextView detailView = label(detail, 11, problem ? DANGER : MUTED, false);
        detailView.setPadding(0, activity.dp(3), 0, 0);
        text.addView(detailView);
        card.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        if (problem && manager.connected()) {
            Button retry = smallButton("Повторить", v -> retrySync());
            LinearLayout.LayoutParams retryParams =
                new LinearLayout.LayoutParams(activity.dp(92), activity.dp(40));
            retryParams.setMargins(activity.dp(8), 0, 0, 0);
            card.addView(retry, retryParams);
        }
        return card;
    }

    private void retrySync() {
        manager.syncNow(new OnlineTreeManager.Callback<Void>() {
            @Override public void onSuccess(Void result) {
                activity.toast("Синхронизация завершена");
                renderDashboard();
            }

            @Override public void onError(String message) {
                activity.toast(message);
                renderDashboard();
            }
        });
    }

    private String statusDetail() {
        if (!manager.connected()) return "Локальный режим продолжает работать без сети";
        if (manager.lastSyncAt() <= 0L) return manager.treeName();
        return manager.treeName() + " · " + new SimpleDateFormat(
            "dd.MM HH:mm",
            Locale.getDefault()).format(new Date(manager.lastSyncAt()));
    }

    private View accountCard() {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar = label(
            manager.login().isEmpty()
                ? "@"
                : manager.login().substring(0, 1).toUpperCase(Locale.ROOT),
            19,
            Color.WHITE,
            true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(activity.tealGradientBg(activity.dp(999)));
        card.addView(avatar, new LinearLayout.LayoutParams(activity.dp(46), activity.dp(46)));
        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(activity.dp(12), 0, 0, 0);
        text.addView(label("@" + manager.login(), 16, INK, true));
        text.addView(label("GitHub защищён и подключён", 11, MUTED, false));
        card.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        return card;
    }

    private View treeCard() {
        LinearLayout card = card();
        card.setBackground(activity.panelBg(
            Color.rgb(235, 248, 246),
            activity.dp(14),
            Color.argb(86, 24, 169, 153)));
        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView cloud = label("☁", 20, TEAL, true);
        cloud.setGravity(Gravity.CENTER);
        heading.addView(cloud, new LinearLayout.LayoutParams(activity.dp(34), activity.dp(34)));
        heading.addView(label(
                manager.isOwner() ? "Ваше онлайн-дерево" : "Общее семейное дерево",
                15,
                INK,
                true),
            new LinearLayout.LayoutParams(0, -2, 1));
        TextView role = pill(manager.isOwner() ? "ГЛАВА" : "УЧАСТНИК", TEAL);
        heading.addView(role);
        card.addView(heading);
        TextView name = label(manager.treeName(), 12, Color.rgb(8, 122, 115), true);
        name.setPadding(activity.dp(34), activity.dp(5), 0, activity.dp(4));
        card.addView(name);
        card.addView(label(
            manager.isOwner()
                ? "Вы — глава. Подключённые участники работают напрямую с GitHub."
                : "Изменения отправляются напрямую в приватный репозиторий.",
            11,
            MUTED,
            false));
        return card;
    }

    private View invitationCard() {
        LinearLayout card = card();
        card.addView(label("Ключ приглашения", 15, INK, true));
        card.addView(label(
            "Передавайте его только тем, кому доверяете редактирование дерева. Нажмите на ключ, чтобы скопировать.",
            11,
            MUTED,
            false));
        String key = manager.invitationKey();
        boolean keyMissing = key.isEmpty();
        TextView keyText = label(
            keyMissing
                ? "Создайте новый ключ после восстановления"
                : groupKey(key),
            keyMissing ? 11 : 12,
            Color.rgb(8, 122, 115),
            true);
        keyText.setClickable(!keyMissing);
        keyText.setFocusable(!keyMissing);
        keyText.setContentDescription(
            keyMissing ? "Ключ приглашения недоступен" : "Скопировать ключ приглашения");
        if (!keyMissing) keyText.setOnClickListener(v -> copyKey(key));
        keyText.setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10));
        keyText.setBackground(activity.panelBg(
            Color.rgb(240, 249, 248),
            activity.dp(10),
            Color.argb(74, 24, 169, 153)));
        LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(-1, -2);
        keyParams.setMargins(0, activity.dp(9), 0, activity.dp(10));
        card.addView(keyText, keyParams);

        if (!keyMissing) {
            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.addView(smallButton("Копировать", v -> copyKey(key)),
                new LinearLayout.LayoutParams(0, activity.dp(44), 1));
            Button share = smallButton("Поделиться", v -> shareKey(key));
            LinearLayout.LayoutParams shareParams =
                new LinearLayout.LayoutParams(0, activity.dp(44), 1);
            shareParams.setMargins(activity.dp(8), 0, 0, 0);
            actions.addView(share, shareParams);
            card.addView(actions);
        }

        Button rotate = smallButton(keyMissing ? "Создать новый ключ" : "Сменить ключ", v ->
            confirm(
                keyMissing ? "Создать ключ приглашения?" : "Сменить ключ приглашения?",
                keyMissing
                    ? "Подключённые участники останутся. Новый ключ понадобится только для новых телефонов."
                    : "Старый ключ перестанет принимать новые подключения. Уже подключённые участники останутся.",
                keyMissing ? "Создать" : "Сменить",
                () -> manager.rotateInvitation(new OnlineTreeManager.Callback<String>() {
                    @Override public void onSuccess(String result) {
                        activity.toast("Создан новый ключ");
                        renderDashboard();
                    }

                    @Override public void onError(String message) {
                        activity.toast(message);
                    }
                })));
        LinearLayout.LayoutParams rotateParams = new LinearLayout.LayoutParams(-1, activity.dp(44));
        rotateParams.setMargins(0, activity.dp(8), 0, 0);
        card.addView(rotate, rotateParams);
        return card;
    }

    private void startBrowserSignIn() {
        try {
            String url = manager.beginBrowserSignIn();
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception error) {
            activity.toast(error.getMessage() == null
                ? "Не удалось открыть вход GitHub"
                : error.getMessage());
            renderDashboard();
        }
    }

    void handleOAuthCallback(Uri callbackUri) {
        Dialog progress = progressDialog(
            "Вход в GitHub",
            "Проверяем подтверждение и сохраняем аккаунт…");
        progress.setCancelable(false);
        progress.show();
        manager.completeBrowserSignIn(callbackUri, new OnlineTreeManager.Callback<String>() {
            @Override public void onSuccess(String login) {
                progress.dismiss();
                activity.toast("GitHub подключён: @" + login);
                openDashboard();
                if (!openPendingInvitation()) discoverOnlineTrees(true);
            }

            @Override public void onError(String message) {
                progress.dismiss();
                activity.toast(message);
                openDashboard();
            }
        });
    }

    void handleInvitationLink(String key) {
        String normalized = key == null ? "" : key.trim();
        if (normalized.isEmpty() || !normalized.startsWith("AFT1-")) {
            activity.toast("Ссылка приглашения повреждена");
            return;
        }
        if (manager.connected()) {
            activity.toast("Сначала отключите текущее онлайн-дерево");
            openDashboard();
            return;
        }
        pendingInvitationKey = normalized;
        copyText("Ключ дерева AndroidFT", normalized);
        openDashboard();
        if (manager.signedIn()) {
            openPendingInvitation();
        } else {
            activity.toast("Ключ скопирован. Войдите в GitHub для подключения");
        }
    }

    private void startCodeSignIn() {
        Dialog progress = progressDialog("Вход в GitHub", "Запрашиваем одноразовый код…");
        progress.setOnCancelListener(dialog -> manager.cancelSignIn());
        progress.show();
        manager.signIn(new OnlineTreeManager.AuthListener() {
            @Override public void onCode(String code, String verificationUrl) {
                progress.dismiss();
                loginCodeDialog = openDeviceCodeDialog(code, verificationUrl);
            }

            @Override public void onSuccess(String login) {
                progress.dismiss();
                if (loginCodeDialog != null) loginCodeDialog.dismiss();
                loginCodeDialog = null;
                activity.toast("Выполнен вход: @" + login);
                renderDashboard();
                if (!openPendingInvitation()) discoverOnlineTrees(true);
            }

            @Override public void onError(String message) {
                progress.dismiss();
                if (loginCodeDialog != null) loginCodeDialog.dismiss();
                loginCodeDialog = null;
                activity.toast(message);
                renderDashboard();
            }
        });
    }

    private Dialog openDeviceCodeDialog(String code, String verificationUrl) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = dialogShell();
        dialogHeader(
            shell,
            "↗",
            "Вход в GitHub",
            "Код уже скопирован. Откройте GitHub, подтвердите AndroidFT и вернитесь в приложение.");
        TextView codeView = label(code, 25, Color.rgb(8, 122, 115), true);
        codeView.setGravity(Gravity.CENTER);
        codeView.setTextIsSelectable(true);
        codeView.setPadding(0, activity.dp(16), 0, activity.dp(16));
        codeView.setBackground(activity.panelBg(
            Color.rgb(232, 248, 246),
            activity.dp(12),
            Color.argb(72, 24, 169, 153)));
        shell.addView(codeView, new LinearLayout.LayoutParams(-1, activity.dp(76)));
        Button open = primaryButton("Открыть GitHub", v -> {
            copyText("Код GitHub", code);
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(verificationUrl)));
            } catch (Exception error) {
                activity.toast("Не удалось открыть браузер");
            }
        });
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(-1, activity.dp(48));
        openParams.setMargins(0, activity.dp(12), 0, 0);
        shell.addView(open, openParams);
        shell.addView(secondaryButton("Отменить вход", v -> {
            manager.cancelSignIn();
            dialog.dismiss();
        }));
        dialog.setContentView(shell);
        dialog.setOnCancelListener(cancelled -> manager.cancelSignIn());
        configureDialog(dialog, shell);
        dialog.show();
        return dialog;
    }

    private void confirmCreateTree() {
        confirm(
            "Сделать дерево онлайн?",
            "Будет создан новый приватный репозиторий в аккаунте @" + manager.login()
                + ". Локальная копия и сохранённые версии останутся на телефоне.",
            "Создать",
            () -> {
                Dialog progress = progressDialog(
                    "Создание онлайн-дерева",
                    "Создаём приватный репозиторий и ключ приглашения…");
                progress.show();
                manager.createOnlineTree(
                    activity.state,
                    new OnlineTreeManager.Callback<String>() {
                        @Override public void onSuccess(String key) {
                            progress.dismiss();
                            activity.toast("Онлайн-дерево создано");
                            renderDashboard();
                        }

                        @Override public void onError(String message) {
                            progress.dismiss();
                            activity.toast(message);
                            renderDashboard();
                        }
                    });
            });
    }

    private void openJoinDialog() {
        openJoinDialog("");
    }

    private void openJoinDialog(String initialKey) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = dialogShell();
        dialogHeader(
            shell,
            "⌁",
            "Присоединиться к дереву",
            "Вставьте полный ключ главы. Запрос шифруется на устройстве и передаётся через GitHub.");
        EditText input = activity.field("AFT1-…");
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setGravity(Gravity.TOP);
        input.setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10));
        if (initialKey != null && !initialKey.trim().isEmpty()) {
            input.setText(initialKey.trim());
            input.setSelection(input.length());
        }
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, activity.dp(100));
        inputParams.setMargins(0, activity.dp(10), 0, activity.dp(10));
        shell.addView(input, inputParams);
        Button join = primaryButton("Отправить запрос", v -> {
            String key = activity.text(input);
            if (key.trim().isEmpty()) {
                activity.toast("Введите ключ дерева");
                return;
            }
            confirm(
                "Загрузить общее дерево?",
                "После подключения текущее дерево на экране будет заменено общей версией. Локальные сохранённые версии останутся доступными для восстановления.",
                "Подключиться",
                () -> {
                    dialog.dismiss();
                    Dialog progress = progressDialog(
                        "Подключение",
                        "Ждём автоматического подтверждения от приложения главы…");
                    progress.show();
                    manager.joinTree(key, new OnlineTreeManager.Callback<Void>() {
                        @Override public void onSuccess(Void result) {
                            progress.dismiss();
                            activity.toast("Дерево подключено");
                            renderDashboard();
                        }

                        @Override public void onError(String message) {
                            progress.dismiss();
                            activity.toast(message);
                            renderDashboard();
                        }
                    });
                });
        });
        shell.addView(join, new LinearLayout.LayoutParams(-1, activity.dp(48)));
        shell.addView(secondaryButton("Отмена", v -> dialog.dismiss()));
        dialog.setContentView(shell);
        configureDialog(dialog, shell);
        dialog.show();
    }

    private boolean openPendingInvitation() {
        if (!manager.signedIn()
            || manager.connected()
            || pendingInvitationKey.isEmpty()) {
            return false;
        }
        String key = pendingInvitationKey;
        pendingInvitationKey = "";
        activity.toast("Ключ приглашения уже вставлен");
        openJoinDialog(key);
        return true;
    }

    private void discoverOnlineTrees(boolean automatic) {
        if (!manager.signedIn() || manager.connected() || discoveryRunning) return;
        discoveryOffered = true;
        discoveryRunning = true;
        Dialog progress = progressDialog(
            "Поиск дерева",
            "Проверяем доступные этому GitHub-аккаунту приватные репозитории…");
        progress.show();
        manager.discoverOnlineTrees(
            new OnlineTreeManager.Callback<List<OnlineTreeManager.AvailableTree>>() {
                @Override public void onSuccess(
                    List<OnlineTreeManager.AvailableTree> result
                ) {
                    discoveryRunning = false;
                    progress.dismiss();
                    if (manager.connected()) return;
                    if (result == null || result.isEmpty()) {
                        activity.toast(
                            automatic
                                ? "Сохранённые онлайн-деревья не найдены"
                                : "В этом аккаунте нет доступных деревьев AndroidFT");
                        renderDashboard();
                        return;
                    }
                    openTreeRecoveryDialog(result);
                }

                @Override public void onError(String message) {
                    discoveryRunning = false;
                    progress.dismiss();
                    activity.toast(message);
                    renderDashboard();
                }
            });
    }

    private void offerRecoveryIfNeeded() {
        if (manager.signedIn()
            && !manager.connected()
            && pendingInvitationKey.isEmpty()
            && !discoveryOffered
            && !discoveryRunning) {
            discoverOnlineTrees(true);
        }
    }

    private void openTreeRecoveryDialog(
        List<OnlineTreeManager.AvailableTree> trees
    ) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = dialogShell();
        dialogHeader(
            shell,
            "☁",
            trees.size() == 1 ? "Найдено онлайн-дерево" : "Найдены онлайн-деревья",
            "Выберите дерево, которое нужно загрузить на этот телефон.");

        LinearLayout choices = new LinearLayout(activity);
        choices.setOrientation(LinearLayout.VERTICAL);
        for (OnlineTreeManager.AvailableTree tree : trees) {
            String updated = tree.updatedAt <= 0L
                ? ""
                : " · " + new SimpleDateFormat(
                    "dd.MM.yyyy HH:mm",
                    Locale.getDefault()).format(new Date(tree.updatedAt));
            choices.addView(actionCard(
                tree.fullName(),
                (tree.ownerAccount ? "Ваше дерево" : "Вы подключены как участник")
                    + updated,
                v -> {
                    dialog.dismiss();
                    confirm(
                        "Загрузить дерево из GitHub?",
                        "Текущее локальное дерево на телефоне будет заменено выбранной онлайн-копией.",
                        "Загрузить",
                        () -> restoreOnlineTree(tree));
                }));
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(choices, new ScrollView.LayoutParams(-1, -2));
        int listHeight = Math.min(activity.dp(390), activity.dp(112 * trees.size()));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, listHeight));
        LinearLayout.LayoutParams closeParams =
            new LinearLayout.LayoutParams(-1, activity.dp(46));
        closeParams.setMargins(0, activity.dp(8), 0, 0);
        shell.addView(smallButton("Не сейчас", v -> dialog.dismiss()), closeParams);

        dialog.setContentView(shell);
        configureDialog(dialog, shell);
        dialog.show();
    }

    private void restoreOnlineTree(OnlineTreeManager.AvailableTree tree) {
        Dialog progress = progressDialog(
            "Восстановление дерева",
            "Загружаем структуру. Медиа продолжат загружаться в фоне…");
        progress.show();
        manager.restoreOnlineTree(tree, new OnlineTreeManager.Callback<Void>() {
            @Override public void onSuccess(Void result) {
                progress.dismiss();
                activity.toast("Онлайн-дерево загружено");
                renderDashboard();
            }

            @Override public void onError(String message) {
                progress.dismiss();
                activity.toast(message);
                renderDashboard();
            }
        });
    }

    private void openParticipants() {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = dialogShell();
        dialogHeader(
            shell,
            "◎",
            "Участники дерева",
            "Управление прямым доступом к приватному репозиторию.");
        TextView loading = note("Загружаем список из GitHub…");
        shell.addView(loading);
        dialog.setContentView(shell);
        configureDialog(dialog, shell);
        dialog.show();

        manager.loadParticipants(new OnlineTreeManager.Callback<List<OnlineTreeManager.Participant>>() {
            @Override public void onSuccess(List<OnlineTreeManager.Participant> participants) {
                shell.removeAllViews();
                dialogHeader(
                    shell,
                    "◎",
                    "Участники дерева",
                    participants.size() + " подключено через приватный репозиторий GitHub.");
                for (OnlineTreeManager.Participant participant : participants) {
                    shell.addView(participantRow(dialog, participant));
                }
                shell.addView(secondaryButton("Закрыть", v -> dialog.dismiss()));
            }

            @Override public void onError(String message) {
                loading.setText(message);
                shell.addView(secondaryButton("Закрыть", v -> dialog.dismiss()));
            }
        });
    }

    private View participantRow(Dialog dialog, OnlineTreeManager.Participant participant) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dp(12), activity.dp(8), activity.dp(8), activity.dp(8));
        row.setBackground(activity.panelBg(
            Color.WHITE,
            activity.dp(12),
            BORDER));
        String initial = participant.login == null || participant.login.isEmpty()
            ? "@"
            : participant.login.substring(0, 1).toUpperCase(Locale.ROOT);
        TextView avatar = label(initial, 14, Color.WHITE, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(activity.panelBg(
            participant.owner ? TEAL : MINT,
            activity.dp(999),
            Color.TRANSPARENT));
        row.addView(avatar, new LinearLayout.LayoutParams(activity.dp(38), activity.dp(38)));
        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(activity.dp(10), 0, 0, 0);
        text.addView(label("@" + participant.login, 14, INK, true));
        text.addView(label(
            participant.owner
                ? "Глава дерева"
                : participant.canEdit ? "Может редактировать" : "Только просмотр",
            11,
            participant.owner ? TEAL : MUTED,
            false));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        if (!participant.owner) {
            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.VERTICAL);
            Button permission = smallButton(
                participant.canEdit ? "Только просмотр" : "Разрешить правки",
                v -> {
                    boolean allowEditing = !participant.canEdit;
                    confirm(
                        allowEditing
                            ? "Разрешить правки @" + participant.login + "?"
                            : "Оставить @" + participant.login + " только просмотр?",
                        allowEditing
                            ? "Участник снова сможет менять и синхронизировать общее дерево."
                            : "Участник сможет видеть актуальное дерево, но GitHub не примет его изменения.",
                        allowEditing ? "Разрешить" : "Только просмотр",
                        () -> manager.setParticipantEditing(
                            participant.login,
                            allowEditing,
                            new OnlineTreeManager.Callback<Void>() {
                                @Override public void onSuccess(Void result) {
                                    activity.toast(
                                        allowEditing
                                            ? "Редактирование разрешено"
                                            : "Включён режим просмотра");
                                    dialog.dismiss();
                                    openParticipants();
                                }

                                @Override public void onError(String message) {
                                    activity.toast(message);
                                }
                            }));
                });
            actions.addView(
                permission,
                new LinearLayout.LayoutParams(activity.dp(142), activity.dp(38)));
            Button remove = smallButton("Отключить", v ->
                confirm(
                    "Отключить @" + participant.login + "?",
                    "Пользователь потеряет доступ к приватному дереву. Его локальная копия на устройстве не удаляется.",
                    "Отключить",
                    () -> manager.removeParticipant(
                        participant.login,
                        new OnlineTreeManager.Callback<Void>() {
                            @Override public void onSuccess(Void result) {
                                activity.toast("Участник отключён");
                                dialog.dismiss();
                                openParticipants();
                            }

                            @Override public void onError(String message) {
                                activity.toast(message);
                            }
                        })));
            remove.setTextColor(DANGER);
            LinearLayout.LayoutParams removeParams =
                new LinearLayout.LayoutParams(activity.dp(142), activity.dp(38));
            removeParams.setMargins(0, activity.dp(4), 0, 0);
            actions.addView(remove, removeParams);
            row.addView(actions, new LinearLayout.LayoutParams(activity.dp(142), -2));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, activity.dp(8));
        row.setLayoutParams(params);
        return row;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12));
        card.setBackground(activity.panelBg(
            Color.WHITE,
            activity.dp(14),
            Color.rgb(217, 224, 229)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, activity.dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private View infoCard(String title, String detail) {
        LinearLayout card = card();
        card.addView(label(title, 15, INK, true));
        TextView description = label(detail, 11, MUTED, false);
        description.setPadding(0, activity.dp(6), 0, 0);
        card.addView(description);
        return card;
    }

    private View actionCard(String title, String detail, View.OnClickListener listener) {
        LinearLayout card = card();
        card.setClickable(true);
        card.setOnClickListener(listener);
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = label(actionGlyph(title), 18, TEAL, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(activity.panelBg(
            Color.rgb(231, 247, 245),
            activity.dp(11),
            Color.TRANSPARENT));
        row.addView(icon, new LinearLayout.LayoutParams(activity.dp(42), activity.dp(42)));
        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(activity.dp(11), 0, activity.dp(6), 0);
        text.addView(label(title, 15, INK, true));
        TextView description = label(detail, 11, MUTED, false);
        description.setPadding(0, activity.dp(5), 0, 0);
        text.addView(description);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(label("›", 28, MINT, false),
            new LinearLayout.LayoutParams(activity.dp(26), -2));
        card.addView(row);
        return card;
    }

    private TextView sectionTitle(String value) {
        TextView title = label(value.toUpperCase(Locale.ROOT), 10, TEAL, true);
        title.setPadding(activity.dp(3), activity.dp(10), 0, activity.dp(8));
        return title;
    }

    private TextView note(String value) {
        TextView text = label(value, 11, MUTED, false);
        text.setLineSpacing(activity.dp(2), 1f);
        text.setPadding(activity.dp(2), activity.dp(9), activity.dp(2), activity.dp(9));
        return text;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setTypeface(bold ? activity.uiBold() : activity.ui());
        text.setIncludeFontPadding(false);
        return text;
    }

    private Button primaryButton(String value, View.OnClickListener listener) {
        Button button = activity.actionButton(value, listener);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setBackground(activity.tealGradientBg(activity.dp(12)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, activity.dp(50));
        params.setMargins(0, 0, 0, activity.dp(10));
        button.setLayoutParams(params);
        return button;
    }

    private Button secondaryButton(String value, View.OnClickListener listener) {
        Button button = activity.actionButton(value, listener);
        button.setTextColor(TEAL);
        button.setBackground(activity.panelBg(
            Color.WHITE,
            activity.dp(12),
            BORDER));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, activity.dp(48));
        params.setMargins(0, activity.dp(8), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private Button smallButton(String value, View.OnClickListener listener) {
        Button button = activity.actionButton(value, listener);
        button.setTextSize(12);
        button.setTextColor(TEAL);
        button.setBackground(activity.panelBg(
            Color.rgb(246, 251, 251),
            activity.dp(10),
            BORDER));
        return button;
    }

    private void dialogHeader(
        LinearLayout shell,
        String glyph,
        String title,
        String detail
    ) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = label(glyph, 18, Color.WHITE, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(activity.tealGradientBg(activity.dp(13)));
        row.addView(icon, new LinearLayout.LayoutParams(activity.dp(46), activity.dp(46)));
        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(activity.dp(12), 0, 0, 0);
        text.addView(label(title, 19, INK, true));
        TextView description = label(detail, 11, MUTED, false);
        description.setLineSpacing(activity.dp(2), 1f);
        description.setPadding(0, activity.dp(4), 0, 0);
        text.addView(description);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, activity.dp(14));
        shell.addView(row, params);
    }

    private TextView pill(String value, int color) {
        TextView text = label(value, 9, color, true);
        text.setGravity(Gravity.CENTER);
        text.setPadding(activity.dp(8), activity.dp(4), activity.dp(8), activity.dp(4));
        text.setBackground(activity.panelBg(
            Color.argb(28, Color.red(color), Color.green(color), Color.blue(color)),
            activity.dp(999),
            Color.argb(62, Color.red(color), Color.green(color), Color.blue(color))));
        return text;
    }

    private static String actionGlyph(String title) {
        String value = title == null ? "" : title.toLowerCase(Locale.ROOT);
        if (value.contains("создать")) return "+";
        if (value.contains("присоедин")) return "⌁";
        if (value.contains("кто") || value.contains("участ")) return "◎";
        return "→";
    }

    private LinearLayout dialogShell() {
        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(activity.dp(18), activity.dp(18), activity.dp(18), activity.dp(16));
        shell.setBackground(activity.panelBg(
            SURFACE,
            activity.dp(20),
            Color.argb(52, 63, 82, 94)));
        return shell;
    }

    private Dialog progressDialog(String title, String detail) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = dialogShell();
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ProgressBar progress = new ProgressBar(activity);
        progress.setIndeterminateTintList(ColorStateList.valueOf(MINT));
        row.addView(progress, new LinearLayout.LayoutParams(activity.dp(44), activity.dp(44)));
        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(activity.dp(12), 0, 0, 0);
        text.addView(label(title, 18, INK, true));
        TextView message = label(detail, 11, TEAL, false);
        message.setPadding(0, activity.dp(4), 0, 0);
        text.addView(message);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        shell.addView(row);
        dialog.setContentView(shell);
        dialog.setCancelable(false);
        configureDialog(dialog, shell);
        return dialog;
    }

    private void configureDialog(Dialog dialog, View shell) {
        dialog.setOnShowListener(shown -> applyDialogWindow(dialog, shell));
        if (dialog.isShowing()) applyDialogWindow(dialog, shell);
    }

    private void applyDialogWindow(Dialog dialog, View shell) {
        Window window = dialog.getWindow();
        if (window == null) return;
        int width = Math.min(
            activity.getResources().getDisplayMetrics().widthPixels - activity.dp(24),
            activity.dp(560));
        int height = Math.min(
            activity.getResources().getDisplayMetrics().heightPixels - activity.dp(70),
            activity.dp(dashboardHeightDp()));
        shell.setMinimumWidth(width);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.width = width;
        attributes.height = dialog == dashboard
            ? height
            : WindowManager.LayoutParams.WRAP_CONTENT;
        attributes.dimAmount = 0.34f;
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setAttributes(attributes);
    }

    private int dashboardHeightDp() {
        if (!manager.signedIn()) return 565;
        if (!manager.connected()) return 650;
        return 760;
    }

    private void confirm(
        String title,
        String message,
        String positive,
        Runnable action
    ) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = dialogShell();
        boolean dangerous = positive.toLowerCase(Locale.ROOT).contains("отключ")
            || positive.toLowerCase(Locale.ROOT).contains("покин")
            || positive.toLowerCase(Locale.ROOT).contains("выйти");
        dialogHeader(
            shell,
            dangerous ? "!" : "✓",
            title,
            message);
        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = smallButton("Отмена", v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, activity.dp(46), 1));
        Button accept = smallButton(positive, v -> {
            dialog.dismiss();
            if (action != null) action.run();
        });
        accept.setTextColor(Color.WHITE);
        accept.setBackground(activity.panelBg(
            dangerous ? DANGER : TEAL,
            activity.dp(11),
            Color.TRANSPARENT));
        LinearLayout.LayoutParams acceptParams =
            new LinearLayout.LayoutParams(0, activity.dp(46), 1);
        acceptParams.setMargins(activity.dp(9), 0, 0, 0);
        actions.addView(accept, acceptParams);
        shell.addView(actions);
        dialog.setContentView(shell);
        configureDialog(dialog, shell);
        dialog.show();
    }

    private void copyKey(String key) {
        if (key == null || key.isEmpty()) {
            activity.toast("Ключ пока недоступен");
            return;
        }
        copyText("Ключ дерева AndroidFT", key);
        activity.toast("Ключ скопирован");
    }

    private void shareKey(String key) {
        if (key == null || key.isEmpty()) {
            activity.toast("Ключ пока недоступен");
            return;
        }
        String inviteLink = new Uri.Builder()
            .scheme("https")
            .authority("drshapaya.ru")
            .appendPath("androidft")
            .appendPath("join")
            .appendQueryParameter("key", key)
            .build()
            .toString();
        SpannableStringBuilder text = new SpannableStringBuilder();
        int titleStart = text.length();
        text.append("Приглашение в семейное дерево AndroidFT");
        text.setSpan(
            new StyleSpan(android.graphics.Typeface.BOLD),
            titleStart,
            text.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append("\n\nКлюч подключения — нажмите и удерживайте, чтобы скопировать:\n");
        int keyStart = text.length();
        text.append(key);
        text.setSpan(
            new TypefaceSpan("monospace"),
            keyStart,
            text.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append("\n\nОткрыть в AndroidFT:\n");
        int linkStart = text.length();
        text.append(inviteLink);
        text.setSpan(
            new URLSpan(inviteLink),
            linkStart,
            text.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append("\n\nЕсли ссылка не открылась, вставьте ключ в разделе «Семейное облако».");
        String html = "<b>Приглашение в семейное дерево AndroidFT</b><br><br>"
            + "Ключ подключения — нажмите и удерживайте, чтобы скопировать:<br>"
            + "<code>" + TextUtils.htmlEncode(key) + "</code><br><br>"
            + "Открыть в AndroidFT:<br><a href=\""
            + TextUtils.htmlEncode(inviteLink)
            + "\">" + TextUtils.htmlEncode(inviteLink) + "</a><br><br>"
            + "Если ссылка не открылась, вставьте ключ в разделе «Семейное облако».";
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/html");
        share.putExtra(Intent.EXTRA_SUBJECT, "Приглашение AndroidFT");
        share.putExtra(Intent.EXTRA_TEXT, text);
        share.putExtra(Intent.EXTRA_HTML_TEXT, html);
        activity.startActivity(Intent.createChooser(share, "Отправить ключ дерева"));
    }

    private void copyText(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(
            Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
    }

    private static String groupKey(String key) {
        if (key == null || key.isEmpty()) return "Ключ недоступен";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            if (i > 0 && i % 28 == 0) result.append('\n');
            result.append(key.charAt(i));
        }
        return result.toString();
    }
}
