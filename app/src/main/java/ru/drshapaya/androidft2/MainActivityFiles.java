package ru.drshapaya.androidft2;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.json.JSONObject;

final class MainActivityFiles {
    private static final float[] PNG_QUALITY_SCALES = {2f, 4f, 6f, 10f, 16f};
    private static final long[] PNG_QUALITY_MAX_PIXELS = {
        4_000_000L,
        8_000_000L,
        16_000_000L,
        32_000_000L,
        64_000_000L
    };
    private static final String[] PNG_QUALITY_NAMES = {
        "Компактное",
        "Обычное",
        "Высокое",
        "Очень высокое",
        "Максимальное"
    };
    private static final String[] PNG_QUALITY_HINTS = {
        "Для быстрого просмотра и отправки",
        "Хороший баланс размера и чёткости",
        "Для увеличения и просмотра деталей",
        "Для больших экранов и печати",
        "Предельная детализация, файл будет большим"
    };

    private final MainActivity activity;
    private String pendingPdfPage = "A4";
    private boolean pendingPdfLandscape = true;
    private int pendingPdfScale = 100;
    private boolean pendingPdfMonochrome = false;
    private int pendingPngQuality = 2;
    private int pendingTileDetail = 4;
    private int pendingTileSize = 2048;

    MainActivityFiles(MainActivity activity) {
        this.activity = activity;
    }

    void openImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        activity.startActivityForResult(intent, MainActivity.REQ_IMPORT);
    }

    void openExport(int requestCode, String type, String filename) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(type);
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        activity.startActivityForResult(intent, requestCode);
    }

    void showExportMenu() {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(16));
        shell.setBackground(activity.panelBg(
            Color.rgb(250, 252, 253),
            activity.dp(18),
            Color.argb(56, 63, 82, 94)));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = versionText("Экспорт дерева", 20, Color.rgb(28, 34, 38), true);
        header.addView(title, new LinearLayout.LayoutParams(0, activity.dp(48), 1));
        Button close = activity.actionButton("Закрыть", v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(activity.dp(90), activity.dp(42)));
        shell.addView(header);

        shell.addView(exportCaption("ДЛЯ ПЕРЕНОСА"));
        shell.addView(exportOption(
            "FamilyTree (.ftree)",
            "Полная копия с фото и вложениями",
            () -> {
                dialog.dismiss();
                openExport(
                    MainActivity.REQ_EXPORT_FTREE,
                    TreePackageIO.MIME_TYPE,
                    "family-tree-" + dateStamp() + ".ftree");
            }));

        shell.addView(exportCaption("ОБМЕН ДАННЫМИ"));
        shell.addView(exportOption(
            "JSON",
            "Структура дерева без бинарных файлов",
            () -> {
                dialog.dismiss();
                openExport(
                    MainActivity.REQ_EXPORT_JSON,
                    "application/json",
                    "family-tree-" + dateStamp() + ".json");
            }));
        shell.addView(exportOption(
            "GEDCOM",
            "Для других генеалогических программ",
            () -> {
                dialog.dismiss();
                openExport(
                    MainActivity.REQ_EXPORT_GEDCOM,
                    "text/plain",
                    "family-tree-" + dateStamp() + ".ged");
            }));

        shell.addView(exportCaption("ИЗОБРАЖЕНИЕ И ПЕЧАТЬ"));
        shell.addView(exportOption(
            "PNG для просмотра",
            "Одно изображение безопасного размера",
            () -> {
                dialog.dismiss();
                showPngSettings();
            }));
        shell.addView(exportOption(
            "PDF для печати",
            "Постраничный A4/A3 с векторным текстом и линиями",
            () -> {
                dialog.dismiss();
                showPdfSettings();
            }));
        shell.addView(exportOption(
            "PNG по частям",
            "Высокое разрешение, безопасные тайлы внутри ZIP",
            () -> {
                dialog.dismiss();
                showTileSettings();
            }));

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = Math.min(
                activity.getResources().getDisplayMetrics().widthPixels - activity.dp(24),
                activity.dp(520));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.dimAmount = 0.32f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
    }

    private TextView exportCaption(String value) {
        TextView caption = versionText(value, 10, Color.rgb(8, 122, 115), true);
        caption.setPadding(activity.dp(4), activity.dp(14), activity.dp(4), activity.dp(6));
        return caption;
    }

    private View exportOption(String title, String detail, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(activity.dp(13), activity.dp(10), activity.dp(13), activity.dp(10));
        row.setBackground(activity.panelBg(
            Color.WHITE,
            activity.dp(10),
            Color.rgb(217, 224, 229)));
        TextView name = versionText(title + "  ›", 14, Color.rgb(28, 34, 38), true);
        TextView description = versionText(detail, 11, Color.rgb(83, 94, 103), false);
        row.addView(name, new LinearLayout.LayoutParams(-1, activity.dp(25)));
        row.addView(description, new LinearLayout.LayoutParams(-1, activity.dp(22)));
        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, activity.dp(7));
        row.setLayoutParams(params);
        return row;
    }

    private void showPdfSettings() {
        Dialog dialog = settingsDialog("PDF для печати");
        LinearLayout shell = settingsShell(dialog);
        if (shell == null) return;
        Button page = settingButton("Формат: " + pendingPdfPage);
        page.setOnClickListener(v -> {
            pendingPdfPage = "A4".equals(pendingPdfPage) ? "A3" : "A4";
            page.setText("Формат: " + pendingPdfPage);
        });
        Button orientation = settingButton(
            "Ориентация: " + (pendingPdfLandscape ? "альбомная" : "книжная"));
        orientation.setOnClickListener(v -> {
            pendingPdfLandscape = !pendingPdfLandscape;
            orientation.setText(
                "Ориентация: " + (pendingPdfLandscape ? "альбомная" : "книжная"));
        });
        Button scale = settingButton("Масштаб: " + pendingPdfScale + "%");
        scale.setOnClickListener(v -> {
            pendingPdfScale = pendingPdfScale == 75 ? 100 : pendingPdfScale == 100 ? 125 : 75;
            scale.setText("Масштаб: " + pendingPdfScale + "%");
        });
        Button color = settingButton(
            "Режим: " + (pendingPdfMonochrome ? "экономичный" : "цветной"));
        color.setOnClickListener(v -> {
            pendingPdfMonochrome = !pendingPdfMonochrome;
            color.setText("Режим: " + (pendingPdfMonochrome ? "экономичный" : "цветной"));
        });
        shell.addView(page);
        shell.addView(orientation);
        shell.addView(scale);
        shell.addView(color);
        Button save = activity.actionButton("Сохранить PDF", v -> {
            dialog.dismiss();
            openExport(
                MainActivity.REQ_EXPORT_PDF,
                "application/pdf",
                "family-tree-" + dateStamp() + ".pdf");
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, activity.dp(48));
        saveParams.setMargins(0, activity.dp(12), 0, 0);
        shell.addView(save, saveParams);
        dialog.show();
        configureSettingsDialog(dialog);
    }

    private void showPngSettings() {
        Dialog dialog = settingsDialog("PNG для просмотра");
        LinearLayout shell = settingsShell(dialog);
        if (shell == null) return;

        LinearLayout qualityCard = new LinearLayout(activity);
        qualityCard.setOrientation(LinearLayout.VERTICAL);
        qualityCard.setPadding(
            activity.dp(14),
            activity.dp(12),
            activity.dp(14),
            activity.dp(12));
        qualityCard.setBackground(activity.panelBg(
            Color.rgb(238, 249, 247),
            activity.dp(14),
            Color.argb(72, 24, 169, 153)));

        TextView level = versionText("", 11, Color.rgb(45, 112, 105), true);
        TextView qualityName = versionText("", 18, Color.rgb(28, 34, 38), true);
        qualityName.setPadding(0, activity.dp(2), 0, activity.dp(8));
        TextView resolutionCaption = versionText(
            "Итоговое разрешение",
            11,
            Color.rgb(83, 94, 103),
            false);
        TextView resolution = versionText("", 22, Color.rgb(8, 122, 115), true);
        resolution.setPadding(0, activity.dp(1), 0, activity.dp(2));
        TextView hint = versionText("", 11, Color.rgb(83, 94, 103), false);

        qualityCard.addView(level);
        qualityCard.addView(qualityName);
        qualityCard.addView(resolutionCaption);
        qualityCard.addView(resolution);
        qualityCard.addView(hint);

        SeekBar slider = new SeekBar(activity);
        slider.setMax(PNG_QUALITY_NAMES.length - 1);
        slider.setProgress(Math.max(0, Math.min(PNG_QUALITY_NAMES.length - 1, pendingPngQuality)));
        slider.setSplitTrack(false);
        slider.setProgressTintList(ColorStateList.valueOf(Color.rgb(24, 169, 153)));
        slider.setThumbTintList(ColorStateList.valueOf(Color.rgb(8, 122, 115)));
        LinearLayout.LayoutParams sliderParams =
            new LinearLayout.LayoutParams(-1, activity.dp(44));
        sliderParams.setMargins(0, activity.dp(8), 0, 0);
        qualityCard.addView(slider, sliderParams);

        LinearLayout marks = new LinearLayout(activity);
        marks.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < PNG_QUALITY_NAMES.length; index++) {
            TextView mark = versionText(
                String.valueOf(index + 1),
                10,
                Color.rgb(83, 94, 103),
                true);
            mark.setGravity(Gravity.CENTER);
            marks.addView(mark, new LinearLayout.LayoutParams(0, activity.dp(20), 1));
        }
        qualityCard.addView(marks);
        shell.addView(qualityCard);

        updatePngQualityPreview(level, qualityName, resolution, hint);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                pendingPngQuality = progress;
                updatePngQualityPreview(level, qualityName, resolution, hint);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        Button save = activity.actionButton("Сохранить PNG", v -> {
            dialog.dismiss();
            openExport(
                MainActivity.REQ_EXPORT_PNG,
                "image/png",
                "family-tree-" + dateStamp() + ".png");
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, activity.dp(48));
        saveParams.setMargins(0, activity.dp(12), 0, 0);
        shell.addView(save, saveParams);
        dialog.show();
        configureSettingsDialog(dialog);
    }

    private void updatePngQualityPreview(
        TextView level,
        TextView qualityName,
        TextView resolution,
        TextView hint
    ) {
        int index = Math.max(0, Math.min(PNG_QUALITY_NAMES.length - 1, pendingPngQuality));
        int[] size = activity.treeView.estimateRenderBitmapSize(
            PNG_QUALITY_SCALES[index],
            PNG_QUALITY_MAX_PIXELS[index]);
        long pixels = (long) size[0] * size[1];
        level.setText("КАЧЕСТВО " + (index + 1) + " ИЗ " + PNG_QUALITY_NAMES.length);
        qualityName.setText(PNG_QUALITY_NAMES[index]);
        resolution.setText(formatPixels(size[0]) + " × " + formatPixels(size[1]) + " px");
        hint.setText(
            PNG_QUALITY_HINTS[index]
                + " · "
                + String.format(Locale.US, "%.1f", pixels / 1_000_000d).replace('.', ',')
                + " Мп");
    }

    private static String formatPixels(int value) {
        return String.format(Locale.US, "%,d", Math.max(0, value)).replace(',', ' ');
    }

    private void showTileSettings() {
        Dialog dialog = settingsDialog("PNG по частям");
        LinearLayout shell = settingsShell(dialog);
        if (shell == null) return;
        Button detail = settingButton("Детализация: " + pendingTileDetail + "×");
        detail.setOnClickListener(v -> {
            pendingTileDetail = pendingTileDetail == 4 ? 6 : pendingTileDetail == 6 ? 8 : 4;
            detail.setText("Детализация: " + pendingTileDetail + "×");
        });
        Button tile = settingButton("Тайл: " + pendingTileSize + " px");
        tile.setOnClickListener(v -> {
            pendingTileSize = pendingTileSize == 2048 ? 3072 : 2048;
            tile.setText("Тайл: " + pendingTileSize + " px");
        });
        shell.addView(detail);
        shell.addView(tile);
        TextView hint = versionText(
            "Результат — один ZIP с PNG-фрагментами, обзорной картой и manifest.json.",
            11,
            Color.rgb(83, 94, 103),
            false);
        hint.setPadding(activity.dp(4), activity.dp(10), activity.dp(4), 0);
        shell.addView(hint);
        Button save = activity.actionButton("Сохранить ZIP", v -> {
            dialog.dismiss();
            openExport(
                MainActivity.REQ_EXPORT_TILES,
                "application/zip",
                "family-tree-tiles-" + dateStamp() + ".zip");
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, activity.dp(48));
        saveParams.setMargins(0, activity.dp(12), 0, 0);
        shell.addView(save, saveParams);
        dialog.show();
        configureSettingsDialog(dialog);
    }

    private Dialog settingsDialog(String titleValue) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(16));
        shell.setBackground(activity.panelBg(
            Color.rgb(250, 252, 253),
            activity.dp(18),
            Color.argb(56, 63, 82, 94)));
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(
            versionText(titleValue, 19, Color.rgb(28, 34, 38), true),
            new LinearLayout.LayoutParams(0, activity.dp(46), 1));
        header.addView(
            activity.actionButton("Закрыть", v -> dialog.dismiss()),
            new LinearLayout.LayoutParams(activity.dp(90), activity.dp(42)));
        shell.addView(header);
        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    private LinearLayout settingsShell(Dialog dialog) {
        View content = dialog.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) content;
        return group.getChildCount() > 0 && group.getChildAt(0) instanceof LinearLayout
            ? (LinearLayout) group.getChildAt(0)
            : null;
    }

    private Button settingButton(String value) {
        Button button = activity.actionButton(value, null);
        button.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, activity.dp(46));
        params.setMargins(0, 0, 0, activity.dp(7));
        button.setLayoutParams(params);
        return button;
    }

    private void configureSettingsDialog(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        int width = Math.min(
            activity.getResources().getDisplayMetrics().widthPixels - activity.dp(24),
            activity.dp(500));
        WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.width = width;
        attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
        attrs.dimAmount = 0.32f;
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setAttributes(attrs);
    }

    void openVersions() {
        List<TreeStore.StoredVersion> versions = activity.store.listVersions();
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(16));
        shell.setBackground(activity.panelBg(Color.rgb(250, 252, 253), activity.dp(18), Color.argb(56, 63, 82, 94)));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout heading = new LinearLayout(activity);
        heading.setOrientation(LinearLayout.VERTICAL);
        TextView title = versionText("Сохранённые версии", 20, Color.rgb(28, 34, 38), true);
        TextView subtitle = versionText("Последние 10 локальных снимков дерева", 11, Color.rgb(101, 113, 122), false);
        heading.addView(title);
        heading.addView(subtitle);
        header.addView(heading, new LinearLayout.LayoutParams(0, activity.dp(52), 1));

        Button close = activity.actionButton("Закрыть", v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(activity.dp(90), activity.dp(42)));
        shell.addView(header);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, activity.dp(10), 0, 0);
        scroll.addView(list);

        if (versions.isEmpty()) {
            TextView empty = versionText(
                "Сохранённых версий пока нет.\nОни создаются автоматически при изменениях дерева.",
                13,
                Color.rgb(83, 94, 103),
                false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(activity.dp(12), activity.dp(28), activity.dp(12), activity.dp(28));
            empty.setBackground(activity.panelBg(Color.WHITE, activity.dp(10), Color.rgb(217, 224, 229)));
            list.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        } else {
            for (TreeStore.StoredVersion version : versions) {
                View row = versionRow(version, () -> confirmRestore(dialog, version));
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                rowParams.setMargins(0, 0, 0, activity.dp(8));
                list.addView(row, rowParams);
            }
        }

        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView hint = versionText(
            "Восстановление заменит текущее состояние. Сразу после него действие можно отменить.",
            10,
            Color.rgb(101, 113, 122),
            false);
        hint.setPadding(activity.dp(2), activity.dp(10), activity.dp(2), 0);
        shell.addView(hint);

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels - activity.dp(24), activity.dp(520));
            int height = Math.min(activity.getResources().getDisplayMetrics().heightPixels - activity.dp(72), activity.dp(620));
            shell.setMinimumWidth(width);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = width;
            attrs.height = height;
            attrs.dimAmount = 0.32f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
        dialog.show();
    }

    private View versionRow(TreeStore.StoredVersion version, Runnable restore) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dp(12), activity.dp(10), activity.dp(8), activity.dp(10));
        row.setBackground(activity.panelBg(Color.WHITE, activity.dp(10), Color.rgb(217, 224, 229)));

        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView date = versionText(versionDate(version.savedAt), 14, Color.rgb(28, 34, 38), true);
        String counts = version.peopleCount < 0 || version.linkCount < 0
            ? "Локальная резервная копия"
            : version.peopleCount + " человек, " + version.linkCount + " связей";
        TextView details = versionText(counts, 11, Color.rgb(83, 94, 103), false);
        text.addView(date);
        text.addView(details);
        if (!version.actionLabel.isEmpty()) {
            TextView action = versionText(version.actionLabel, 10, Color.rgb(8, 122, 115), false);
            action.setSingleLine(true);
            text.addView(action);
        }
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));

        Button restoreButton = activity.actionButton("Загрузить", v -> restore.run());
        restoreButton.setTextColor(Color.rgb(8, 122, 115));
        restoreButton.setBackground(activity.panelBg(Color.rgb(232, 248, 246), activity.dp(8), Color.argb(72, 24, 169, 153)));
        row.addView(restoreButton, new LinearLayout.LayoutParams(activity.dp(104), activity.dp(42)));
        row.setOnClickListener(v -> restore.run());
        row.setClickable(true);
        return row;
    }

    private void confirmRestore(Dialog versionsDialog, TreeStore.StoredVersion version) {
        activity.showStyledConfirmation(
            R.drawable.ic_menu_undo,
            "Восстановить версию?",
            versionDate(version.savedAt) + "\n"
                + (version.peopleCount < 0 || version.linkCount < 0
                    ? "Локальная резервная копия"
                    : version.peopleCount + " человек, " + version.linkCount + " связей"),
            "Восстановить",
            false,
            () -> restoreVersion(versionsDialog, version));
    }

    private void restoreVersion(Dialog versionsDialog, TreeStore.StoredVersion version) {
        versionsDialog.dismiss();
        activity.toast("Загрузка версии…");
        new Thread(() -> {
            try {
                TreeState restored = activity.store.loadVersion(version);
                activity.runOnUiThread(() -> applyRestoredVersion(restored, version));
            } catch (Exception error) {
                activity.runOnUiThread(() -> activity.toast("Не удалось восстановить версию"));
            }
        }, "tree-version-loader").start();
    }

    private void applyRestoredVersion(TreeState restored, TreeStore.StoredVersion version) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        activity.recordUndo("Восстановлена версия", versionDate(version.savedAt));
        activity.state = restored;
        activity.recordAction("Восстановлена версия", versionDate(version.savedAt));
        activity.resetTransientCanvasModes(false);
        activity.applyStateSettings();
        TreeLayoutEngine.ensurePositions(activity.state);
        if (!activity.saveOnly()) {
            activity.toast("Версия загружена, но не сохранена");
            return;
        }
        activity.bindState();
        activity.treeView.post(() -> activity.treeView.fit());
        activity.toast("Версия восстановлена");
    }

    private TextView versionText(String value, int size, int color, boolean bold) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setTypeface(bold ? activity.uiBold() : activity.ui());
        text.setIncludeFontPadding(false);
        return text;
    }

    private static String versionDate(long timestamp) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }

    void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) return;
        if (requestCode == MainActivity.REQ_MEMORY_FILE) {
            List<Uri> uris = new ArrayList<>();
            ClipData clipData = data.getClipData();
            if (clipData != null) {
                for (int index = 0; index < clipData.getItemCount() && uris.size() < 24; index++) {
                    Uri itemUri = clipData.getItemAt(index).getUri();
                    if (itemUri != null && !uris.contains(itemUri)) uris.add(itemUri);
                }
            }
            if (data.getData() != null && !uris.contains(data.getData())) uris.add(data.getData());
            if (!uris.isEmpty()) importMemoryFilesFromUris(uris);
            return;
        }
        if (data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == MainActivity.REQ_IMPORT) importFromUri(uri);
        if (requestCode == MainActivity.REQ_EXPORT_FTREE) exportTreePackageToUri(uri);
        if (requestCode == MainActivity.REQ_EXPORT_JSON) exportTextToUri(uri, "json");
        if (requestCode == MainActivity.REQ_EXPORT_GEDCOM) exportTextToUri(uri, "gedcom");
        if (requestCode == MainActivity.REQ_EXPORT_PNG) exportPngToUri(uri);
        if (requestCode == MainActivity.REQ_EXPORT_PDF) exportPdfToUri(uri);
        if (requestCode == MainActivity.REQ_EXPORT_TILES) exportTilesToUri(uri);
        if (requestCode == MainActivity.REQ_PHOTO) importPhotoFromUri(uri);
    }

    void handleIncomingIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) importFromUri(intent.getData());
    }

    void importFromUri(Uri uri) {
        if (uri == null) return;
        DiagnosticsLogger.breadcrumb(activity, "import.start");
        activity.toast("Проверка файла…");
        new Thread(() -> {
            try (InputStream raw = activity.getContentResolver().openInputStream(uri);
                 BufferedInputStream input = raw == null ? null : new BufferedInputStream(raw, 64 * 1024)) {
                if (input == null) throw new IllegalStateException("Файл не открыт");
                input.mark(8);
                byte[] header = new byte[4];
                int count = input.read(header);
                input.reset();
                TreeState imported = TreePackageIO.hasZipSignature(header, count)
                    ? TreePackageIO.read(input, activity.store)
                    : activity.store.parse(readUtf8Limited(input, 25L * 1024L * 1024L));
                activity.runOnUiThread(() -> applyImportedTree(imported));
            } catch (Exception error) {
                activity.runOnUiThread(() ->
                    activity.toast("Импорт не выполнен: " + safeError(error)));
                DiagnosticsLogger.handled(activity, "import", error);
            }
        }, "tree-import").start();
    }

    private void applyImportedTree(TreeState imported) {
        if (activity.isFinishing() || activity.isDestroyed() || imported == null) return;
        activity.recordUndo("Импортировано дерево", "");
        activity.state = imported;
        activity.recordAction("Импортировано дерево", "");
        activity.resetTransientCanvasModes(false);
        activity.applyStateSettings();
        TreeLayoutEngine.ensurePositions(activity.state);
        if (!activity.saveOnly()) {
            activity.toast("Дерево открыто, но не сохранено");
            return;
        }
        activity.bindState();
        activity.treeView.post(() -> activity.treeView.fit());
        DiagnosticsLogger.breadcrumb(activity, "import.finish");
        activity.toast("Дерево импортировано");
    }

    void openPhotoPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        activity.startActivityForResult(intent, MainActivity.REQ_PHOTO);
    }

    void openMemoryFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        activity.startActivityForResult(intent, MainActivity.REQ_MEMORY_FILE);
    }

    void importPhotoFromUri(Uri uri) {
        Person person = activity.state.people.get(activity.pendingPhotoPersonId);
        activity.pendingPhotoPersonId = "";
        if (person == null || activity.editLocked) return;
        try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("Фото не открыто");
            String mime = activity.getContentResolver().getType(uri);
            if (mime == null || mime.trim().isEmpty()) mime = "image/jpeg";
            TreeMediaStore.StoredMedia stored = activity.store.mediaStore().importPhoto(
                input,
                displayName(uri),
                mime);
            activity.recordUndo("Добавлено фото", person.name.isEmpty() ? "Без имени" : person.name);
            person.photoMediaId = stored.id;
            person.photo = "";
            activity.saveToast("Фото загружено");
            activity.bindState();
            activity.treeView.invalidate();
            activity.refreshOpenPersonEditor();
        } catch (Exception error) {
            activity.toast("Фото не загружено: " + error.getMessage());
        }
    }

    void importMemoryFileFromUri(Uri uri) {
        if (uri == null) return;
        importMemoryFilesFromUris(Collections.singletonList(uri));
    }

    private void importMemoryFilesFromUris(List<Uri> uris) {
        Person person = activity.state.people.get(activity.pendingMemoryPersonId);
        activity.pendingMemoryPersonId = "";
        activity.pendingMemoryTitle = "";
        activity.pendingMemoryText = "";
        if (person == null || activity.editLocked || uris == null || uris.isEmpty()) return;
        List<MemoryAttachment> attachments = new ArrayList<>();
        int failed = 0;
        for (Uri uri : uris) {
            if (uri == null || attachments.size() >= 24) continue;
            try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("Файл не открыт");
                String mime = activity.getContentResolver().getType(uri);
                if (mime == null || mime.trim().isEmpty()) mime = "application/octet-stream";
                String filename = displayName(uri);
                TreeMediaStore.StoredMedia stored = activity.store.mediaStore().importAttachment(
                    input,
                    filename,
                    mime);
                MemoryAttachment attachment = new MemoryAttachment();
                attachment.id = "a_" + java.util.UUID.randomUUID().toString().replace("-", "");
                attachment.filename = filename;
                attachment.mimeType = stored.mimeType;
                attachment.type = memoryTypeFromMime(mime);
                attachment.mediaId = stored.id;
                attachment.size = stored.size;
                attachment.data = "";
                attachments.add(attachment);
            } catch (Exception ignored) {
                failed++;
            }
        }
        if (attachments.isEmpty()) {
            activity.toast("Файлы не добавлены");
            return;
        }
        activity.addMemoryDraftAttachments(person.id, attachments, failed);
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = activity.getContentResolver().query(
            uri,
            new String[]{OpenableColumns.DISPLAY_NAME},
            null,
            null,
            null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) {
                    name = name.trim();
                    return name.length() > 180 ? name.substring(0, 180) : name;
                }
            }
        } catch (Exception ignored) {
        }
        return safeUriName(uri);
    }

    void exportTextToUri(Uri uri, String kind) {
        if (uri == null) return;
        TreeState exportState = TreeStateCopier.copy(activity.state);
        activity.toast("Подготовка файла…");
        new Thread(() -> {
            try (OutputStream output = activity.getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IllegalStateException("Файл не создан");
                String text = "gedcom".equals(kind)
                    ? exportGedcomText(exportState)
                    : activity.store.exportText(exportState);
                output.write(text.getBytes(StandardCharsets.UTF_8));
                activity.runOnUiThread(() -> activity.toast("Файл экспортирован"));
            } catch (Exception error) {
                DiagnosticsLogger.handled(activity, "export.text", error);
                activity.runOnUiThread(() ->
                    activity.toast("Экспорт не выполнен: " + safeError(error)));
            }
        }, "tree-text-export").start();
    }

    void exportTreePackageToUri(Uri uri) {
        if (uri == null) return;
        DiagnosticsLogger.breadcrumb(activity, "export.ftree.start");
        activity.toast("Создание .ftree…");
        TreeState exportState = TreeStateCopier.copy(activity.state);
        new Thread(() -> {
            try (OutputStream output = activity.getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IllegalStateException("Файл не создан");
                TreePackageIO.write(
                    exportState,
                    activity.store,
                    output,
                    exportState.readerMode ? "view" : "copy");
                activity.runOnUiThread(() -> activity.toast(".ftree экспортирован"));
                DiagnosticsLogger.breadcrumb(activity, "export.ftree.finish");
            } catch (Exception error) {
                DiagnosticsLogger.handled(activity, "export.ftree", error);
                activity.runOnUiThread(() ->
                    activity.toast("Экспорт не выполнен: " + safeError(error)));
            }
        }, "tree-package-export").start();
    }

    void exportPngToUri(Uri uri) {
        if (uri == null) return;
        final Bitmap bitmap;
        try {
            DiagnosticsLogger.breadcrumb(activity, "export.png.render");
            int quality = Math.max(
                0,
                Math.min(PNG_QUALITY_NAMES.length - 1, pendingPngQuality));
            bitmap = activity.treeView.renderBitmap(
                PNG_QUALITY_SCALES[quality],
                PNG_QUALITY_MAX_PIXELS[quality]);
            exportPngBitmapInBackground(uri, bitmap);
        } catch (OutOfMemoryError error) {
            DiagnosticsLogger.handled(activity, "export.png.oom", error);
            activity.toast("PNG не создан: дереву не хватает доступной памяти");
        } catch (Exception error) {
            activity.toast("PNG не создан: " + error.getMessage());
        }
    }

    private void exportPngBitmapInBackground(Uri uri, Bitmap bitmap) {
        activity.toast("Сжатие PNG…");
        new Thread(() -> {
            try (OutputStream output = activity.getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IllegalStateException("Файл не создан");
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 96, output)) {
                    throw new IOException("кодировщик PNG завершился с ошибкой");
                }
                DiagnosticsLogger.breadcrumb(activity, "export.png.finish");
                activity.runOnUiThread(() -> activity.toast("PNG экспортирован"));
            } catch (Exception error) {
                DiagnosticsLogger.handled(activity, "export.png", error);
                activity.runOnUiThread(() ->
                    activity.toast("PNG не создан: " + safeError(error)));
            } finally {
                bitmap.recycle();
            }
        }, "tree-png-encoder").start();
    }

    void exportPdfToUri(Uri uri) {
        if (uri == null) return;
        TreeState exportState = TreeStateCopier.copy(activity.state);
        String pageSize = pendingPdfPage;
        boolean landscape = pendingPdfLandscape;
        int requestedScale = pendingPdfScale;
        boolean monochrome = pendingPdfMonochrome;
        activity.toast("Создание PDF…");
        DiagnosticsLogger.breadcrumb(activity, "export.pdf.start");
        new Thread(() -> {
            try (OutputStream output = activity.getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IOException("Файл не создан");
                writePdf(
                    exportState,
                    output,
                    pageSize,
                    landscape,
                    requestedScale,
                    monochrome);
                DiagnosticsLogger.breadcrumb(activity, "export.pdf.finish");
                activity.runOnUiThread(() -> activity.toast("PDF экспортирован"));
            } catch (Exception error) {
                DiagnosticsLogger.handled(activity, "export.pdf", error);
                activity.runOnUiThread(() ->
                    activity.toast("PDF не создан: " + safeError(error)));
            } catch (OutOfMemoryError error) {
                DiagnosticsLogger.handled(activity, "export.pdf.oom", error);
                activity.runOnUiThread(() ->
                    activity.toast("PDF не создан: недостаточно памяти"));
            }
        }, "tree-pdf-export").start();
    }

    private void writePdf(
        TreeState exportState,
        OutputStream output,
        String pageSize,
        boolean landscape,
        int requestedScale,
        boolean monochrome
    ) throws Exception {
        int portraitWidth = "A3".equals(pageSize) ? 842 : 595;
        int portraitHeight = "A3".equals(pageSize) ? 1191 : 842;
        int pageWidth = landscape ? portraitHeight : portraitWidth;
        int pageHeight = landscape ? portraitWidth : portraitHeight;
        float margin = 28f;
        float footer = 18f;
        float renderScale = 0.55f * Math.max(50, Math.min(150, requestedScale)) / 100f;
        float contentWidth = pageWidth - margin * 2f;
        float contentHeight = pageHeight - margin * 2f - footer;
        float pageWorldWidth = contentWidth / renderScale;
        float pageWorldHeight = contentHeight / renderScale;

        TreeDocumentRenderer renderer = new TreeDocumentRenderer(
            exportState,
            activity.store.mediaStore());
        RectF bounds = renderer.bounds();
        int columns = Math.max(1, (int) Math.ceil(bounds.width() / pageWorldWidth));
        int rows = Math.max(1, (int) Math.ceil(bounds.height() / pageWorldHeight));
        long pageCount = (long) columns * rows;
        if (pageCount > 120L) {
            renderer.clear();
            throw new IOException(
                "Получается " + pageCount + " страниц. Уменьшите масштаб PDF");
        }

        PdfDocument document = new PdfDocument();
        Paint footerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        footerPaint.setColor(Color.DKGRAY);
        footerPaint.setTextSize(9f);
        try {
            int number = 1;
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(
                        pageWidth,
                        pageHeight,
                        number).create();
                    PdfDocument.Page page = document.startPage(info);
                    RectF world = new RectF(
                        bounds.left + column * pageWorldWidth,
                        bounds.top + row * pageWorldHeight,
                        bounds.left + (column + 1) * pageWorldWidth,
                        bounds.top + (row + 1) * pageWorldHeight);
                    RectF target = new RectF(
                        margin,
                        margin,
                        pageWidth - margin,
                        pageHeight - margin - footer);
                    renderer.render(page.getCanvas(), world, target, renderScale, monochrome);
                    String caption = "AndroidFT " + MainActivity.VERSION_NAME
                        + " · " + number + "/" + pageCount
                        + " · ряд " + (row + 1) + ", колонка " + (column + 1);
                    page.getCanvas().drawText(
                        caption,
                        margin,
                        pageHeight - margin + 2f,
                        footerPaint);
                    document.finishPage(page);
                    number++;
                }
            }
            document.writeTo(output);
        } finally {
            document.close();
            renderer.clear();
        }
    }

    void exportTilesToUri(Uri uri) {
        if (uri == null) return;
        TreeState exportState = TreeStateCopier.copy(activity.state);
        int detail = pendingTileDetail;
        int tileSize = pendingTileSize;
        activity.toast("Создание PNG-тайлов…");
        DiagnosticsLogger.breadcrumb(activity, "export.tiles.start");
        new Thread(() -> {
            try (OutputStream output = activity.getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IOException("Файл не создан");
                writeTileArchive(exportState, output, detail, tileSize);
                DiagnosticsLogger.breadcrumb(activity, "export.tiles.finish");
                activity.runOnUiThread(() -> activity.toast("Архив PNG-тайлов экспортирован"));
            } catch (Exception error) {
                DiagnosticsLogger.handled(activity, "export.tiles", error);
                activity.runOnUiThread(() ->
                    activity.toast("Тайлы не созданы: " + safeError(error)));
            } catch (OutOfMemoryError error) {
                DiagnosticsLogger.handled(activity, "export.tiles.oom", error);
                activity.runOnUiThread(() ->
                    activity.toast("Тайлы не созданы: недостаточно памяти"));
            }
        }, "tree-tile-export").start();
    }

    private void writeTileArchive(
        TreeState exportState,
        OutputStream output,
        int detail,
        int tileSize
    ) throws Exception {
        float renderScale = Math.max(2f, Math.min(8f, detail));
        int safeTileSize = tileSize >= 3072 ? 3072 : 2048;
        float tileWorld = safeTileSize / renderScale;
        TreeDocumentRenderer renderer = new TreeDocumentRenderer(
            exportState,
            activity.store.mediaStore());
        RectF bounds = renderer.bounds();
        int columns = Math.max(1, (int) Math.ceil(bounds.width() / tileWorld));
        int rows = Math.max(1, (int) Math.ceil(bounds.height() / tileWorld));
        long tileCount = (long) columns * rows;
        if (tileCount > 256L) {
            renderer.clear();
            throw new IOException(
                "Получается " + tileCount + " тайлов. Уменьшите детализацию");
        }

        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.setLevel(0);
            JSONObject manifest = new JSONObject()
                .put("format", "ru.drshapaya.familytree.tiles")
                .put("version", 1)
                .put("appVersion", MainActivity.VERSION_NAME)
                .put("tileSize", safeTileSize)
                .put("pixelsPerWorld", renderScale)
                .put("rows", rows)
                .put("columns", columns)
                .put("left", bounds.left)
                .put("top", bounds.top)
                .put("right", bounds.right)
                .put("bottom", bounds.bottom);
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifest.toString(2).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    Bitmap bitmap = Bitmap.createBitmap(
                        safeTileSize,
                        safeTileSize,
                        Bitmap.Config.ARGB_8888);
                    try {
                        RectF world = new RectF(
                            bounds.left + column * tileWorld,
                            bounds.top + row * tileWorld,
                            bounds.left + (column + 1) * tileWorld,
                            bounds.top + (row + 1) * tileWorld);
                        renderer.render(
                            new android.graphics.Canvas(bitmap),
                            world,
                            new RectF(0f, 0f, safeTileSize, safeTileSize),
                            renderScale,
                            false);
                        String name = String.format(
                            Locale.US,
                            "tiles/tile-r%03d-c%03d.png",
                            row + 1,
                            column + 1);
                        zip.putNextEntry(new ZipEntry(name));
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)) {
                            throw new IOException("Не удалось закодировать " + name);
                        }
                        zip.closeEntry();
                    } finally {
                        bitmap.recycle();
                    }
                }
            }
            writeTileOverview(zip, renderer, bounds);
            zip.finish();
        } finally {
            renderer.clear();
        }
    }

    private void writeTileOverview(
        ZipOutputStream zip,
        TreeDocumentRenderer renderer,
        RectF bounds
    ) throws Exception {
        float overviewScale = Math.min(
            1600f / Math.max(1f, bounds.width()),
            1600f / Math.max(1f, bounds.height()));
        overviewScale = Math.max(0.02f, overviewScale);
        int width = Math.max(1, Math.round(bounds.width() * overviewScale));
        int height = Math.max(1, Math.round(bounds.height() * overviewScale));
        Bitmap overview = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            renderer.render(
                new android.graphics.Canvas(overview),
                bounds,
                new RectF(0f, 0f, width, height),
                overviewScale,
                false);
            zip.putNextEntry(new ZipEntry("overview.png"));
            if (!overview.compress(Bitmap.CompressFormat.PNG, 100, zip)) {
                throw new IOException("Не удалось создать обзорную карту");
            }
            zip.closeEntry();
        } finally {
            overview.recycle();
        }
    }

    String exportGedcomText() {
        return exportGedcomText(activity.state);
    }

    private String exportGedcomText(TreeState sourceState) {
        StringBuilder out = new StringBuilder();
        out.append("0 HEAD\n1 SOUR AndroidFT\n1 CHAR UTF-8\n");
        for (Person person : sourceState.people.values()) {
            out.append("0 @").append(person.id).append("@ INDI\n");
            out.append("1 NAME ").append(cleanGed(person.name)).append("\n");
            String gender = PersonGender.resolve(person);
            if (PersonGender.MALE.equals(gender)) out.append("1 SEX M\n");
            if (PersonGender.FEMALE.equals(gender)) out.append("1 SEX F\n");
            String born = gedDate(person.bornDay, person.bornMonth, person.bornYear);
            String died = gedDate(person.diedDay, person.diedMonth, person.diedYear);
            if (!born.isEmpty()) {
                out.append("1 BIRT\n2 DATE ").append(born).append("\n");
                if (!person.place.isEmpty()) out.append("2 PLAC ").append(cleanGed(person.place)).append("\n");
            }
            if (!died.isEmpty()) out.append("1 DEAT\n2 DATE ").append(died).append("\n");
            if (!person.notes.isEmpty()) out.append("1 NOTE ").append(cleanGed(person.notes)).append("\n");
        }
        java.util.Map<String, java.util.List<String>> childrenByParentPair = new java.util.LinkedHashMap<>();
        for (Relation link : sourceState.links) {
            if (!"parent".equals(link.type)) continue;
            String partner = "";
            for (Relation candidate : sourceState.links) {
                if (!"parent".equals(candidate.type) || !link.to.equals(candidate.to) || link.from.equals(candidate.from)) continue;
                partner = candidate.from;
                break;
            }
            String a = link.from;
            String b = partner.isEmpty() ? "" : partner;
            String key = b.isEmpty() || a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
            java.util.List<String> children = childrenByParentPair.get(key);
            if (children == null) {
                children = new java.util.ArrayList<>();
                childrenByParentPair.put(key, children);
            }
            if (!children.contains(link.to)) children.add(link.to);
        }
        int fam = 1;
        for (java.util.Map.Entry<String, java.util.List<String>> entry : childrenByParentPair.entrySet()) {
            String[] parents = entry.getKey().split("\\|", -1);
            out.append("0 @F").append(fam++).append("@ FAM\n");
            if (!parents[0].isEmpty()) out.append("1 HUSB @").append(parents[0]).append("@\n");
            if (parents.length > 1 && !parents[1].isEmpty()) out.append("1 WIFE @").append(parents[1]).append("@\n");
            for (String child : entry.getValue()) out.append("1 CHIL @").append(child).append("@\n");
        }
        out.append("0 TRLR\n");
        return out.toString();
    }

    void shareJsonText() {
        try {
            String filename = "family-tree-" + dateStamp() + ".json";
            File directory = new File(activity.getCacheDir(), "shared");
            if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Не создана папка обмена");
            File file = new File(directory, filename);
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(activity.store.exportText(activity.state).getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = TreeShareProvider.uriFor(filename);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/json");
            send.putExtra(Intent.EXTRA_SUBJECT, "Семейное древо (JSON)");
            send.putExtra(Intent.EXTRA_TITLE, filename);
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.setClipData(ClipData.newRawUri(filename, uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(send, "Поделиться деревом (JSON)"));
        } catch (Exception error) {
            activity.toast("Не удалось поделиться: " + error.getMessage());
        }
    }

    void shareTreePackage() {
        activity.toast("Подготовка дерева…");
        TreeState exportState = TreeStateCopier.copy(activity.state);
        new Thread(() -> {
            String filename = "family-tree-" + dateStamp() + ".ftree";
            try {
                File directory = new File(activity.getCacheDir(), "shared");
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new IllegalStateException("Не создана папка обмена");
                }
                File file = new File(directory, filename);
                try (FileOutputStream output = new FileOutputStream(file)) {
                    TreePackageIO.write(
                        exportState,
                        activity.store,
                        output,
                        exportState.readerMode ? "view" : "copy");
                }
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    Uri uri = TreeShareProvider.uriFor(filename);
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType(TreePackageIO.MIME_TYPE);
                    send.putExtra(Intent.EXTRA_SUBJECT, "Семейное древо FamilyTree");
                    send.putExtra(Intent.EXTRA_TITLE, filename);
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.setClipData(ClipData.newRawUri(filename, uri));
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    activity.startActivity(Intent.createChooser(send, "Поделиться деревом"));
                });
            } catch (Exception error) {
                activity.runOnUiThread(() ->
                    activity.toast("Не удалось поделиться: " + safeError(error)));
            }
        }, "tree-package-share").start();
    }

    private static String readUtf8Limited(InputStream input, long maxBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Файл дерева превышает "
                    + TreeMediaStore.humanSize(maxBytes));
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static String safeError(Exception error) {
        String message = error == null ? "" : error.getMessage();
        return message == null || message.trim().isEmpty()
            ? "неизвестная ошибка"
            : message.trim();
    }

    static String cleanGed(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    static String gedDate(String day, String month, String year) {
        if (year == null || year.trim().isEmpty()) return "";
        String[] months = {"", "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
        int monthIndex = 0;
        try {
            monthIndex = month == null || month.isEmpty() ? 0 : Integer.parseInt(month);
        } catch (Exception ignored) {
            monthIndex = 0;
        }
        if (day != null && !day.isEmpty() && monthIndex >= 1 && monthIndex <= 12) return day + " " + months[monthIndex] + " " + year;
        if (monthIndex >= 1 && monthIndex <= 12) return months[monthIndex] + " " + year;
        return year;
    }

    static String safeUriName(Uri uri) {
        String name = uri == null ? "" : uri.getLastPathSegment();
        if (name == null || name.trim().isEmpty()) return "Файл";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < name.length()) name = name.substring(slash + 1);
        return name.length() > 180 ? name.substring(0, 180) : name;
    }

    static String memoryTypeFromMime(String mime) {
        if (mime == null) return "document";
        if (mime.startsWith("image/")) return "photo";
        if (mime.startsWith("audio/")) return "audio";
        if (mime.startsWith("video/")) return "video";
        if (mime.startsWith("text/")) return "source";
        return "document";
    }

    static String dateStamp() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

}
