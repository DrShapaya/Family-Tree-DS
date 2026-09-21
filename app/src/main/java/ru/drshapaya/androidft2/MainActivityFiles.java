package ru.drshapaya.androidft2;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
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
    private static final float[] PNG_FONT_SCALES = {0.92f, 0.96f, 1.00f, 1.06f, 1.10f};

    private final MainActivity activity;
    private String pendingPdfPage = "A4";
    private boolean pendingPdfLandscape = true;
    private int pendingPdfScale = 100;
    private boolean pendingPdfMonochrome = false;
    private String pendingPdfFrame = RewardCatalog.STANDARD;
    private int pendingPngQuality = 2;
    private TreeImageExport pendingImageExport;
    private Dialog pngDialog;
    private Dialog exportMenuDialog;
    private Dialog pdfDialog;
    private boolean pendingPdfFit = true;

    private static final class ExportStyleChoice {
        final String id;
        final String title;
        final int price;
        final boolean owned;

        ExportStyleChoice(String id, String title, int price, boolean owned) {
            this.id = id;
            this.title = title;
            this.price = price;
            this.owned = owned;
        }
    }

    MainActivityFiles(MainActivity activity) {
        this.activity = activity;
    }

    void close() {
        if (exportMenuDialog != null) exportMenuDialog.dismiss();
        if (pdfDialog != null) pdfDialog.dismiss();
        if (pngDialog != null) pngDialog.dismiss();
        if (pendingImageExport != null) {
            pendingImageExport.close();
            pendingImageExport = null;
        }
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
        exportMenuDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (exportMenuDialog == dialog) exportMenuDialog = null;
        });
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
        TextView title = versionText("Экспорт дерева", 21, Color.rgb(28, 34, 38), true);
        header.addView(title, new LinearLayout.LayoutParams(0, activity.dp(48), 1));
        Button close = activity.actionButton("Закрыть", v -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(activity.dp(90), activity.dp(42)));
        shell.addView(header);

        TextView intro = versionText(
            "Выберите формат файла или откройте точные настройки печати.",
            11, Color.rgb(83, 94, 103), false);
        intro.setPadding(activity.dp(4), 0, activity.dp(4), activity.dp(6));
        shell.addView(intro);

        shell.addView(exportCaption("Резервная копия"));
        shell.addView(exportOption(
            "Family Tree DS (.ftree)",
            "Полная копия с фото и вложениями",
            () -> {
                dialog.dismiss();
                openExport(
                    MainActivity.REQ_EXPORT_FTREE,
                    TreePackageIO.MIME_TYPE,
                    "family-tree-" + dateStamp() + ".ftree");
            }));

        shell.addView(exportCaption("Форматы экспорта"));
        LinearLayout advanced = new LinearLayout(activity);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(3));
        advanced.setBackground(activity.panelBg(
            Color.rgb(243, 248, 248), activity.dp(12), Color.rgb(205, 226, 224)));
        advanced.addView(exportOption("JSON", "Структура дерева без фото и вложений", () -> {
            dialog.dismiss();
            openExport(MainActivity.REQ_EXPORT_JSON, "application/json", "family-tree-" + dateStamp() + ".json");
        }));
        advanced.addView(exportOption("GEDCOM", "Для других генеалогических программ", () -> {
            dialog.dismiss();
            openExport(MainActivity.REQ_EXPORT_GEDCOM, "text/plain", "family-tree-" + dateStamp() + ".ged");
        }));
        advanced.addView(exportOption("PDF", "Дерево целиком на странице или печать по листам", () -> {
            dialog.dismiss();
            showPdfSettings();
        }));
        shell.addView(advanced);

        ScrollView menuScroll = new ScrollView(activity);
        menuScroll.setFillViewport(true);
        menuScroll.addView(shell);
        dialog.setContentView(menuScroll);
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
        TextView caption = versionText(value, 11, Color.rgb(8, 122, 115), true);
        caption.setPadding(activity.dp(4), activity.dp(13), activity.dp(4), activity.dp(7));
        return caption;
    }

    private View exportOption(String title, String detail, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dp(10), activity.dp(9), activity.dp(10), activity.dp(9));
        row.setBackground(activity.panelBg(
            Color.WHITE,
            activity.dp(12),
            Color.rgb(217, 224, 229)));

        String badgeText = title.startsWith("Family") ? "FT" : title.replaceAll("[^A-Za-z]", "");
        if (badgeText.length() > 4) badgeText = badgeText.substring(0, 4);
        TextView badge = versionText(badgeText, badgeText.length() > 3 ? 9 : 11, Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(activity.tealGradientBg(activity.dp(11)));
        row.addView(badge, new LinearLayout.LayoutParams(activity.dp(46), activity.dp(46)));

        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(activity.dp(11), 0, activity.dp(8), 0);
        TextView name = versionText(title, 14, Color.rgb(28, 34, 38), true);
        TextView description = versionText(detail, 11, Color.rgb(83, 94, 103), false);
        copy.addView(name, new LinearLayout.LayoutParams(-1, -2));
        copy.addView(description, new LinearLayout.LayoutParams(-1, -2));
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = versionText("›", 28, Color.rgb(8, 122, 115), false);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(activity.dp(28), activity.dp(46)));
        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, activity.dp(7));
        row.setLayoutParams(params);
        return row;
    }

    private void showPdfSettings() {
        Dialog dialog = settingsDialog("PDF для печати");
        pdfDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (pdfDialog == dialog) pdfDialog = null;
        });
        LinearLayout shell = settingsShell(dialog);
        if (shell == null) return;
        RewardWallet wallet = new RewardWallet(activity);
        pendingPdfFrame = wallet.selected(RewardCatalog.EXPORT_FRAMES);
        TextView summary = versionText("", 12, Color.rgb(55, 77, 82), false);
        summary.setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10));
        summary.setBackground(activity.panelBg(
            Color.rgb(235, 248, 246), activity.dp(10), Color.rgb(183, 224, 217)));
        Runnable updatePdf = () -> {
            if (pendingPdfFit) summary.setText("1 страница · дерево целиком, без обрезки. Для большого дерева текст можно увеличить в PDF.");
            else {
                TreeDocumentRenderer renderer = new TreeDocumentRenderer(activity, activity.state, activity.store.mediaStore());
                RectF bounds = renderer.bounds();
                renderer.clear();
                float w = "A3".equals(pendingPdfPage) ? 842 : 595;
                float h = "A3".equals(pendingPdfPage) ? 1191 : 842;
                float scaleValue = 0.55f * pendingPdfScale / 100f;
                int cols = Math.max(1, (int) Math.ceil(bounds.width() * scaleValue / ((pendingPdfLandscape ? h : w) - 56)));
                int rows = Math.max(1, (int) Math.ceil(bounds.height() * scaleValue / ((pendingPdfLandscape ? w : h) - 74)));
                summary.setText(cols + " × " + rows + " листов · " + ((long) cols * rows) + " страниц");
            }
        };
        Button page = settingButton("Формат: " + pendingPdfPage);
        page.setOnClickListener(v -> {
            pendingPdfPage = "A4".equals(pendingPdfPage) ? "A3" : "A4";
            page.setText("Формат: " + pendingPdfPage);
            updatePdf.run();
        });
        Button orientation = settingButton(
            "Ориентация: " + (pendingPdfLandscape ? "альбомная" : "книжная"));
        orientation.setOnClickListener(v -> {
            pendingPdfLandscape = !pendingPdfLandscape;
            orientation.setText(
                "Ориентация: " + (pendingPdfLandscape ? "альбомная" : "книжная"));
            updatePdf.run();
        });
        Button scale = settingButton("Масштаб: " + pendingPdfScale + "%");
        scale.setOnClickListener(v -> {
            pendingPdfScale = pendingPdfScale >= 100 ? 25 : pendingPdfScale + 25;
            scale.setText("Масштаб: " + pendingPdfScale + "%");
            updatePdf.run();
        });
        scale.setVisibility(pendingPdfFit ? View.GONE : View.VISIBLE);
        Button fit = settingButton(pendingPdfFit ? "Размещение: целиком на странице" : "Размещение: по листам");
        fit.setOnClickListener(v -> {
            pendingPdfFit = !pendingPdfFit;
            fit.setText(pendingPdfFit ? "Размещение: целиком на странице" : "Размещение: по листам");
            scale.setVisibility(pendingPdfFit ? View.GONE : View.VISIBLE);
            updatePdf.run();
        });
        Button color = settingButton(
            "Режим: " + (pendingPdfMonochrome ? "экономичный" : "цветной"));
        color.setOnClickListener(v -> {
            pendingPdfMonochrome = !pendingPdfMonochrome;
            color.setText("Режим: " + (pendingPdfMonochrome ? "экономичный" : "цветной"));
        });
        shell.addView(exportCaption("Страница"));
        shell.addView(page);
        shell.addView(orientation);
        shell.addView(exportCaption("Размещение"));
        shell.addView(fit);
        shell.addView(scale);
        shell.addView(exportCaption("Оформление"));
        shell.addView(color);
        shell.addView(exportFrameButton(wallet, () -> pendingPdfFrame, value -> pendingPdfFrame = value, null));
        shell.addView(summary);
        updatePdf.run();
        Button save = activity.actionButton("Сохранить PDF", v -> {
            dialog.dismiss();
            openExport(
                MainActivity.REQ_EXPORT_PDF,
                "application/pdf",
                "family-tree-" + dateStamp() + ".pdf");
        });
        save.setTextColor(Color.WHITE);
        save.setBackground(activity.tealGradientBg(activity.dp(11)));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, activity.dp(48));
        saveParams.setMargins(0, activity.dp(12), 0, 0);
        shell.addView(save, saveParams);
        dialog.show();
        configureSettingsDialog(dialog);
    }

    void showPngSettings() {
        if (pngDialog != null) pngDialog.dismiss();
        if (activity.state.people.isEmpty()) {
            activity.toast("Добавьте людей в дерево перед экспортом");
            return;
        }
        TreeImageExport export = new TreeImageExport(activity);
        Dialog dialog = settingsDialog("Экспорт PNG");
        LinearLayout shell = settingsShell(dialog);
        if (shell == null) { export.close(); return; }
        pngDialog = dialog;
        ExportPreviewView preview = new ExportPreviewView(activity, export);
        int previewHeight = Math.min(
            activity.dp(315),
            Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.3375f));
        shell.addView(preview, new LinearLayout.LayoutParams(-1, previewHeight));
        ScrollView scroll = new ScrollView(activity);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, activity.dp(7), 0, 0);
        LinearLayout controls = new LinearLayout(activity);
        controls.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(controls);
        TextView hint = versionText("Двумя пальцами — масштаб и перемещение предпросмотра.",
            11, Color.rgb(83, 94, 103), false);
        hint.setPadding(activity.dp(4), activity.dp(7), activity.dp(4), activity.dp(7));
        controls.addView(hint, new LinearLayout.LayoutParams(-1, -2));
        TextView resolution = versionText("", 11, Color.rgb(8, 122, 115), true);
        resolution.setPadding(
            activity.dp(12), activity.dp(8), activity.dp(12), activity.dp(8));
        resolution.setMaxLines(3);
        resolution.setGravity(Gravity.CENTER_VERTICAL);
        resolution.setBackground(activity.panelBg(
            Color.rgb(239, 249, 248), activity.dp(9), Color.rgb(194, 226, 222)));
        Button reset = settingButton("Область изображения\nВсё дерево");
        reset.setTextSize(11);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(activity.dp(8), 0, activity.dp(8), 0);
        LinearLayout overview = new LinearLayout(activity);
        overview.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams resolutionParams = new LinearLayout.LayoutParams(0, activity.dp(72), 1.45f);
        resolutionParams.setMargins(0, activity.dp(8), activity.dp(6), activity.dp(8));
        overview.addView(resolution, resolutionParams);
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(0, activity.dp(72), 1f);
        resetParams.setMargins(activity.dp(6), activity.dp(8), 0, activity.dp(8));
        overview.addView(reset, resetParams);
        controls.addView(overview, new LinearLayout.LayoutParams(-1, activity.dp(88)));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Button save = activity.actionButton("Сохранить PNG", null);
        save.setTextColor(Color.WHITE);
        save.setBackground(activity.tealGradientBg(activity.dp(11)));
        shell.addView(save, new LinearLayout.LayoutParams(-1, activity.dp(48)));
        TextView cuts = versionText("", 12, Color.rgb(55, 77, 82), false);
        Runnable update = () -> {
            int q = pendingPngQuality;
            ExportLayout layout = export.layout(PNG_QUALITY_SCALES[q], PNG_QUALITY_MAX_PIXELS[q]);
            boolean split = layout.columns * layout.rows > 1;
            int partCount = layout.columns * layout.rows;
            resolution.setText("Разрешение: " + formatPixels(layout.width) + " × "
                + formatPixels(layout.height) + " px\n"
                + "Частей: " + partCount + "\n"
                + "≈ " + formatBytes(estimatePngBytes(layout, export, split)));
            reset.setText("Область изображения\n" + (sameRect(export.crop, export.extent)
                ? "Всё дерево"
                : "Выбранная область"));
            cuts.setText("Разрезы:\nПо вертикали — " + export.verticalCuts
                + "\nПо горизонтали — " + export.horizontalCuts
                + "\n" + layout.columns + " × " + layout.rows + " частей"
                + (split ? "\nСохранение: ZIP" : "\nСохранение: один PNG"));
            save.setText(split ? "Сохранить части PNG в ZIP" : "Сохранить PNG");
            preview.invalidate();
        };
        Runnable refresh = () -> {
            int q = pendingPngQuality;
            try {
                preview.refresh(PNG_QUALITY_SCALES[q], PNG_QUALITY_MAX_PIXELS[q]);
                save.setEnabled(true);
                update.run();
            }
            catch (RuntimeException | OutOfMemoryError error) {
                save.setEnabled(false);
                activity.toast("Не удалось обновить предпросмотр. Уменьшите объём дерева.");
                DiagnosticsLogger.handled(activity, "export.preview", error);
            }
        };
        preview.setOnCropChanged(update);
        reset.setOnClickListener(v -> { export.resetCrop(); update.run(); });
        controls.addView(exportCaption("Качество"));
        TextView quality = versionText(
            PNG_QUALITY_NAMES[pendingPngQuality] + " · " + PNG_QUALITY_HINTS[pendingPngQuality],
            13, Color.rgb(28, 34, 38), true);
        quality.setPadding(activity.dp(4), activity.dp(4), activity.dp(4), 0);
        controls.addView(quality);
        SeekBar qualitySlider = new SeekBar(activity);
        qualitySlider.setMax(PNG_QUALITY_NAMES.length - 1);
        qualitySlider.setProgress(pendingPngQuality);
        qualitySlider.setContentDescription("Качество PNG");
        qualitySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                pendingPngQuality = progress;
                quality.setText(PNG_QUALITY_NAMES[progress] + " · " + PNG_QUALITY_HINTS[progress]);
                update.run();
                preview.removeCallbacks(refresh);
                preview.postDelayed(refresh, 120);
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        styleExportSlider(qualitySlider);
        controls.addView(magnetSlider(qualitySlider), new LinearLayout.LayoutParams(-1, activity.dp(44)));

        TextView font = versionText("Размер шрифта: " + Math.round(export.fontScale * 100) + "%", 13, Color.rgb(28, 34, 38), false);
        font.setPadding(activity.dp(4), activity.dp(4), activity.dp(4), 0);
        controls.addView(font);
        SeekBar fontSlider = new SeekBar(activity);
        fontSlider.setMax(PNG_FONT_SCALES.length - 1);
        fontSlider.setProgress(nearestFontScaleIndex(export.fontScale));
        fontSlider.setContentDescription("Размер шрифта от 92 до 110 процентов");
        Runnable fontRefresh = refresh;
        fontSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                export.fontScale = PNG_FONT_SCALES[progress];
                font.setText("Размер шрифта: " + Math.round(export.fontScale * 100) + "%");
                preview.removeCallbacks(fontRefresh);
                preview.postDelayed(fontRefresh, 120);
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        styleExportSlider(fontSlider);
        controls.addView(magnetSlider(fontSlider), new LinearLayout.LayoutParams(-1, activity.dp(44)));

        controls.addView(exportCaption("Разрезы"));
        LinearLayout cutRow = new LinearLayout(activity);
        cutRow.setGravity(Gravity.CENTER_VERTICAL);
        ExportCutGrid cutGrid = new ExportCutGrid(activity, export, update);
        cutRow.addView(cutGrid, new LinearLayout.LayoutParams(activity.dp(174), activity.dp(116)));
        cuts.setPadding(activity.dp(16), 0, 0, 0);
        cuts.setTextSize(12);
        cuts.setTextColor(Color.rgb(55, 77, 82));
        cutRow.addView(cuts, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(cutRow);
        RewardWallet wallet = new RewardWallet(activity);
        controls.addView(exportStyleStrip(
            "Фон PNG", backgroundChoices(wallet),
            () -> export.background, value -> export.background = value, refresh));
        LinearLayout toggleGrid = new LinearLayout(activity);
        toggleGrid.setOrientation(LinearLayout.VERTICAL);
        addExportToggleRow(toggleGrid,
            exportToggle("Сетка", export.grid, value -> { export.grid = value; refresh.run(); }),
            exportToggle("Линии поколений", export.generations, value -> { export.generations = value; refresh.run(); }));
        addExportToggleRow(toggleGrid,
            exportToggle("Компактные карточки", export.compact, value -> { export.compact = value; refresh.run(); }),
            exportToggle("Ровные линии", export.straight, value -> { export.straight = value; refresh.run(); }));
        addExportToggleRow(toggleGrid,
            exportToggle("Детали карточек", export.details, value -> { export.details = value; refresh.run(); }),
            null);
        controls.addView(toggleGrid, new LinearLayout.LayoutParams(-1, -2));
        controls.addView(exportStyleStrip(
            "Рамка PNG/PDF", frameChoices(wallet),
            () -> export.frame, value -> export.frame = value, refresh));
        boolean[] saving = {false};
        save.setOnClickListener(v -> {
            preview.removeCallbacks(fontRefresh);
            export.apply();
            if (pendingImageExport != null) pendingImageExport.close();
            pendingImageExport = export;
            boolean split = export.verticalCuts + export.horizontalCuts > 0;
            try {
                openExport(split ? MainActivity.REQ_EXPORT_TILES : MainActivity.REQ_EXPORT_PNG,
                    split ? "application/zip" : "image/png", "family-tree-" + dateStamp() + (split ? ".zip" : ".png"));
                saving[0] = true;
                dialog.dismiss();
            } catch (RuntimeException error) {
                pendingImageExport = null;
                activity.toast("Не удалось открыть выбор файла");
            }
        });
        dialog.setOnDismissListener(ignored -> {
            if (pngDialog == dialog) pngDialog = null;
            preview.removeCallbacks(fontRefresh);
            preview.release();
            if (!saving[0]) export.close();
        });
        dialog.show();
        configureSettingsDialog(dialog);
        android.graphics.Rect available = new android.graphics.Rect();
        activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(available);
        if (dialog.getWindow() != null) dialog.getWindow().setLayout(
            Math.min(activity.getResources().getDisplayMetrics().widthPixels - activity.dp(12), activity.dp(720)),
            Math.max(1, available.height() - activity.dp(32)));
        preview.post(refresh);
    }

    private List<ExportStyleChoice> backgroundChoices(RewardWallet wallet) {
        List<ExportStyleChoice> choices = new ArrayList<>();
        choices.add(new ExportStyleChoice("white", "Белый", 0, true));
        choices.add(new ExportStyleChoice(
            RewardCatalog.STANDARD, "Стандартное полотно", 0, true));
        for (RewardCatalog.Item item : RewardCatalog.itemsFor(RewardCatalog.CANVAS_BACKGROUNDS)) {
            choices.add(new ExportStyleChoice(item.id, item.title, item.price, wallet.owns(item.id)));
        }
        return choices;
    }

    private List<ExportStyleChoice> frameChoices(RewardWallet wallet) {
        List<ExportStyleChoice> choices = new ArrayList<>();
        choices.add(new ExportStyleChoice(RewardCatalog.STANDARD, "Без рамки", 0, true));
        for (RewardCatalog.Item item : RewardCatalog.itemsFor(RewardCatalog.EXPORT_FRAMES)) {
            choices.add(new ExportStyleChoice(item.id, item.title, item.price, wallet.owns(item.id)));
        }
        return choices;
    }

    private View exportStyleStrip(
        String title,
        List<ExportStyleChoice> choices,
        java.util.function.Supplier<String> selected,
        java.util.function.Consumer<String> changed,
        Runnable refreshed
    ) {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        TextView caption = exportCaption(title);
        section.addView(caption, new LinearLayout.LayoutParams(-1, activity.dp(38)));

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout cards = new LinearLayout(activity);
        cards.setPadding(activity.dp(2), 0, activity.dp(2), activity.dp(5));
        List<View> cardViews = new ArrayList<>();
        for (ExportStyleChoice choice : choices) {
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(activity.dp(10), activity.dp(8), activity.dp(10), activity.dp(7));
            card.setContentDescription(choice.title);
            TextView name = versionText(choice.title, 11, Color.rgb(28, 34, 38), true);
            name.setMaxLines(2);
            name.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(name, new LinearLayout.LayoutParams(-1, 0, 1));
            TextView state = versionText("", 9, Color.rgb(83, 94, 103), false);
            state.setMaxLines(1);
            state.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(state, new LinearLayout.LayoutParams(-1, activity.dp(20)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(activity.dp(148), activity.dp(78));
            params.setMargins(0, 0, activity.dp(8), 0);
            cards.addView(card, params);
            cardViews.add(card);
            card.setOnClickListener(v -> {
                if (!choice.owned) {
                    showWorkshopUnlockPrompt(choice.title, choice.price);
                    return;
                }
                changed.accept(choice.id);
                if (refreshed != null) refreshed.run();
                for (int i = 0; i < choices.size(); i++) {
                    styleExportChoice(cardViews.get(i), choices.get(i), selected.get());
                }
            });
            card.setTag(state);
        }
        scroll.addView(cards, new HorizontalScrollView.LayoutParams(-2, activity.dp(83)));
        section.addView(scroll, new LinearLayout.LayoutParams(-1, activity.dp(88)));
        for (int i = 0; i < choices.size(); i++) {
            styleExportChoice(cardViews.get(i), choices.get(i), selected.get());
        }
        return section;
    }

    private void styleExportChoice(View view, ExportStyleChoice choice, String selectedId) {
        if (!(view instanceof ViewGroup)) return;
        boolean current = choice.id.equals(selectedId);
        int fill;
        int stroke;
        if (current) {
            fill = Color.rgb(232, 248, 246);
            stroke = Color.rgb(24, 169, 153);
        } else if (choice.owned) {
            fill = Color.WHITE;
            stroke = Color.rgb(211, 224, 227);
        } else {
            fill = Color.rgb(248, 244, 252);
            stroke = Color.rgb(215, 191, 232);
        }
        view.setBackground(activity.panelBg(fill, activity.dp(12), stroke));
        View stateView = view.getTag() instanceof View ? (View) view.getTag() : null;
        if (stateView instanceof TextView) {
            TextView state = (TextView) stateView;
            state.setText(current
                ? "✓ Выбрано"
                : choice.owned ? "Открыто" : "🔒 " + choice.price + " жет.");
            state.setTextColor(current
                ? Color.rgb(8, 122, 115)
                : choice.owned ? Color.rgb(83, 94, 103) : Color.rgb(122, 66, 158));
        }
    }

    private void showWorkshopUnlockPrompt(String title, int price) {
        Dialog dialog = settingsDialog("Оформление закрыто");
        LinearLayout shell = settingsShell(dialog);
        if (shell == null) return;
        LinearLayout message = new LinearLayout(activity);
        message.setOrientation(LinearLayout.VERTICAL);
        message.setGravity(Gravity.CENTER);
        message.setPadding(activity.dp(18), activity.dp(16), activity.dp(18), activity.dp(16));
        message.setBackground(activity.softAccentGradientBg(activity.dp(16)));
        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER);
        ImageView lock = new ImageView(activity);
        lock.setImageResource(R.drawable.ic_menu_lock);
        lock.setColorFilter(Color.rgb(122, 66, 158));
        lock.setPadding(activity.dp(6), activity.dp(6), activity.dp(6), activity.dp(6));
        titleRow.addView(lock, new LinearLayout.LayoutParams(activity.dp(34), activity.dp(34)));
        TextView name = versionText("«" + title + "»", 16, Color.rgb(48, 35, 70), true);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-2, activity.dp(34));
        nameParams.setMargins(activity.dp(6), 0, 0, 0);
        titleRow.addView(name, nameParams);
        message.addView(titleRow, new LinearLayout.LayoutParams(-1, activity.dp(38)));
        TextView explanation = versionText(
            "Эта рамка доступна в Мастерской за " + price + " жетонов.",
            12, Color.rgb(83, 72, 101), false);
        explanation.setGravity(Gravity.CENTER);
        explanation.setPadding(activity.dp(4), 0, activity.dp(4), 0);
        message.addView(explanation, new LinearLayout.LayoutParams(-1, activity.dp(42)));
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-1, activity.dp(104));
        messageParams.setMargins(0, activity.dp(6), 0, activity.dp(12));
        shell.addView(message, messageParams);

        LinearLayout actions = new LinearLayout(activity);
        Button later = activity.actionButton("Позже", v -> dialog.dismiss());
        LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(0, activity.dp(48), 1);
        laterParams.setMargins(0, 0, activity.dp(5), 0);
        actions.addView(later, laterParams);
        Button workshop = activity.actionButton("В мастерскую", v -> {
            dialog.dismiss();
            if (pngDialog != null) pngDialog.dismiss();
            activity.openWorkshop();
        });
        workshop.setTextColor(Color.WHITE);
        workshop.setBackground(activity.tealGradientBg(activity.dp(12)));
        LinearLayout.LayoutParams workshopParams = new LinearLayout.LayoutParams(0, activity.dp(48), 1.25f);
        workshopParams.setMargins(activity.dp(5), 0, 0, 0);
        actions.addView(workshop, workshopParams);
        shell.addView(actions, new LinearLayout.LayoutParams(-1, activity.dp(48)));
        dialog.show();
        configureSettingsDialog(dialog);
    }

    private void styleExportSlider(SeekBar slider) {
        slider.setPadding(activity.dp(18), activity.dp(7), activity.dp(18), activity.dp(7));
        slider.setSplitTrack(false);
        GradientDrawable track = new GradientDrawable();
        track.setColor(Color.rgb(221, 235, 234));
        track.setCornerRadius(activity.dp(99));
        track.setSize(-1, activity.dp(18));
        GradientDrawable progress = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{Color.rgb(8, 132, 122), Color.rgb(91, 70, 194)});
        progress.setCornerRadius(activity.dp(99));
        progress.setSize(-1, activity.dp(18));
        LayerDrawable drawable = new LayerDrawable(new Drawable[]{
            track,
            new ClipDrawable(progress, Gravity.LEFT, ClipDrawable.HORIZONTAL),
            new MagnetDotsDrawable()
        });
        drawable.setId(0, android.R.id.background);
        drawable.setId(1, android.R.id.progress);
        drawable.setLayerGravity(0, Gravity.CENTER_VERTICAL);
        drawable.setLayerGravity(1, Gravity.CENTER_VERTICAL);
        drawable.setLayerGravity(2, Gravity.CENTER_VERTICAL);
        drawable.setLayerHeight(0, activity.dp(18));
        drawable.setLayerHeight(1, activity.dp(18));
        drawable.setLayerHeight(2, activity.dp(18));
        slider.setProgressDrawable(drawable);
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(Color.WHITE);
        thumb.setStroke(activity.dp(2), Color.rgb(8, 132, 122));
        thumb.setSize(activity.dp(22), activity.dp(22));
        slider.setThumb(thumb);
        slider.setThumbOffset(activity.dp(11));
    }

    private FrameLayout magnetSlider(SeekBar slider) {
        FrameLayout frame = new FrameLayout(activity);
        frame.addView(slider, new FrameLayout.LayoutParams(-1, -1));
        return frame;
    }

    private final class MagnetDotsDrawable extends Drawable {
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override public void draw(Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            float radius = activity.dp(3);
            float left = bounds.left + radius;
            float width = Math.max(1f, bounds.width() - radius * 2f);
            float y = bounds.exactCenterY();
            for (int i = 0; i < 5; i++) {
                float x = left + width * i / 4f;
                dotPaint.setStyle(Paint.Style.FILL);
                dotPaint.setColor(Color.rgb(250, 252, 253));
                canvas.drawCircle(x, y, radius, dotPaint);
                dotPaint.setStyle(Paint.Style.STROKE);
                dotPaint.setStrokeWidth(activity.dp(1));
                dotPaint.setColor(Color.rgb(91, 70, 194));
                canvas.drawCircle(x, y, radius, dotPaint);
            }
        }

        @Override public void setAlpha(int alpha) { dotPaint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            dotPaint.setColorFilter(colorFilter);
        }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private static int nearestFontScaleIndex(float scale) {
        int nearest = 0;
        float distance = Float.MAX_VALUE;
        for (int i = 0; i < PNG_FONT_SCALES.length; i++) {
            float next = Math.abs(PNG_FONT_SCALES[i] - scale);
            if (next < distance) { nearest = i; distance = next; }
        }
        return nearest;
    }

    private static boolean sameRect(RectF first, RectF second) {
        return Math.abs(first.left - second.left) < 0.5f
            && Math.abs(first.top - second.top) < 0.5f
            && Math.abs(first.right - second.right) < 0.5f
            && Math.abs(first.bottom - second.bottom) < 0.5f;
    }

    private static long estimatePngBytes(ExportLayout layout, TreeImageExport export, boolean split) {
        long pixels = Math.max(1L, (long) layout.width * layout.height);
        double bytesPerPixel = 0.42d;
        if (!"white".equals(export.background)) bytesPerPixel += 0.22d;
        if (export.grid) bytesPerPixel += 0.10d;
        if (!RewardCatalog.STANDARD.equals(export.frame)) bytesPerPixel += 0.12d;
        if (export.details) bytesPerPixel += 0.08d;
        long estimate = Math.max(16_384L, (long) (pixels * bytesPerPixel));
        return split ? Math.round(estimate * 1.04d) : estimate;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L * 1024L) return Math.max(1L, bytes / 1024L) + " КБ";
        return String.format(Locale.US, "%.1f МБ", bytes / (1024d * 1024d));
    }

    private View exportToggle(String title, boolean checked,
                              java.util.function.Consumer<Boolean> changed) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dp(10), activity.dp(3), activity.dp(5), activity.dp(3));
        row.setBackground(activity.panelBg(
            checked ? Color.rgb(239, 249, 248) : Color.WHITE,
            activity.dp(13),
            checked ? Color.rgb(176, 221, 216) : Color.rgb(220, 230, 231)));
        TextView label = versionText(
            AppLanguage.text(activity, title), 11, Color.rgb(28, 34, 38), true);
        label.setMaxLines(2);
        label.setGravity(Gravity.CENTER);
        label.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        row.addView(label, new LinearLayout.LayoutParams(0, -1, 1));
        android.widget.Switch toggle = new android.widget.Switch(activity);
        toggle.setContentDescription(AppLanguage.text(activity, title));
        toggle.setChecked(checked);
        toggle.setShowText(false);
        toggle.setOnCheckedChangeListener((button, value) -> {
            row.setBackground(activity.panelBg(
                value ? Color.rgb(239, 249, 248) : Color.WHITE,
                activity.dp(13),
                value ? Color.rgb(176, 221, 216) : Color.rgb(220, 230, 231)));
            changed.accept(value);
        });
        row.addView(toggle, new LinearLayout.LayoutParams(activity.dp(50), activity.dp(48)));
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        return row;
    }

    private void addExportToggleRow(LinearLayout grid, View first, View second) {
        LinearLayout row = new LinearLayout(activity);
        LinearLayout.LayoutParams firstParams = new LinearLayout.LayoutParams(0, activity.dp(56), 1);
        firstParams.setMargins(0, 0, activity.dp(4), activity.dp(7));
        row.addView(first, firstParams);
        if (second != null) {
            LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(0, activity.dp(56), 1);
            secondParams.setMargins(activity.dp(4), 0, 0, activity.dp(7));
            row.addView(second, secondParams);
        } else {
            View spacer = new View(activity);
            LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, activity.dp(56), 1);
            spacerParams.setMargins(activity.dp(4), 0, 0, activity.dp(7));
            row.addView(spacer, spacerParams);
        }
        grid.addView(row, new LinearLayout.LayoutParams(-1, activity.dp(63)));
    }

    private Button exportFrameButton(
        RewardWallet wallet,
        java.util.function.Supplier<String> selected,
        java.util.function.Consumer<String> changed,
        Runnable refreshed
    ) {
        List<ExportStyleChoice> choices = frameChoices(wallet);
        String currentName = "Без рамки";
        for (ExportStyleChoice choice : choices) {
            if (choice.id.equals(selected.get())) { currentName = choice.title; break; }
        }
        Button button = settingButton("Рамка: " + currentName);
        button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_frame, 0, 0, 0);
        button.setCompoundDrawablePadding(activity.dp(8));
        button.setOnClickListener(v -> showPdfFramePicker(choices, selected, changed, refreshed, button));
        return button;
    }

    private void showPdfFramePicker(
        List<ExportStyleChoice> choices,
        java.util.function.Supplier<String> selected,
        java.util.function.Consumer<String> changed,
        Runnable refreshed,
        Button sourceButton
    ) {
        Dialog dialog = settingsDialog("Рамка PDF");
        LinearLayout shell = settingsShell(dialog);
        if (shell == null) return;

        TextView intro = versionText(
            "Выберите оформление границ страницы. Закрытые варианты можно открыть в Мастерской.",
            12, Color.rgb(83, 72, 101), false);
        intro.setGravity(Gravity.CENTER_VERTICAL);
        intro.setPadding(activity.dp(14), activity.dp(10), activity.dp(14), activity.dp(10));
        intro.setBackground(activity.softAccentGradientBg(activity.dp(14)));
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(-1, activity.dp(64));
        introParams.setMargins(0, activity.dp(4), 0, activity.dp(10));
        shell.addView(intro, introParams);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, activity.dp(4));
        for (ExportStyleChoice choice : choices) {
            boolean current = choice.id.equals(selected.get());
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(activity.dp(10), activity.dp(7), activity.dp(10), activity.dp(7));
            row.setBackground(activity.panelBg(
                current ? Color.rgb(241, 234, 250) : Color.WHITE,
                activity.dp(14),
                current ? Color.rgb(154, 112, 190) : Color.rgb(219, 225, 230)));

            ImageView icon = new ImageView(activity);
            icon.setImageResource(exportFrameIcon(choice.id));
            icon.setColorFilter(current ? Color.rgb(102, 55, 142) : Color.rgb(91, 70, 194));
            icon.setPadding(activity.dp(9), activity.dp(9), activity.dp(9), activity.dp(9));
            icon.setBackground(activity.panelBg(
                current ? Color.rgb(226, 211, 241) : Color.rgb(244, 239, 250),
                activity.dp(12), Color.TRANSPARENT));
            row.addView(icon, new LinearLayout.LayoutParams(activity.dp(46), activity.dp(46)));

            LinearLayout copy = new LinearLayout(activity);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setGravity(Gravity.CENTER_VERTICAL);
            copy.setPadding(activity.dp(12), 0, activity.dp(8), 0);
            TextView name = versionText(choice.title, 13, Color.rgb(37, 39, 47), true);
            copy.addView(name, new LinearLayout.LayoutParams(-1, activity.dp(24)));
            TextView state = versionText(
                current ? "Выбрано" : choice.owned ? "Нажмите, чтобы выбрать" : "Закрыто · " + choice.price + " жет.",
                10,
                current ? Color.rgb(102, 55, 142) : choice.owned
                    ? Color.rgb(83, 94, 103) : Color.rgb(143, 78, 126),
                current);
            copy.addView(state, new LinearLayout.LayoutParams(-1, activity.dp(21)));
            row.addView(copy, new LinearLayout.LayoutParams(0, activity.dp(48), 1));

            TextView mark = versionText(current ? "✓" : choice.owned ? "›" : "🔒", 18,
                current ? Color.rgb(102, 55, 142) : Color.rgb(112, 122, 129), true);
            mark.setGravity(Gravity.CENTER);
            row.addView(mark, new LinearLayout.LayoutParams(activity.dp(34), activity.dp(46)));
            row.setContentDescription(choice.title + ". " + state.getText());
            row.setOnClickListener(v -> {
                if (!choice.owned) {
                    showWorkshopUnlockPrompt(choice.title, choice.price);
                    return;
                }
                changed.accept(choice.id);
                sourceButton.setText("Рамка: " + choice.title);
                dialog.dismiss();
                if (refreshed != null) refreshed.run();
            });
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, activity.dp(68));
            rowParams.setMargins(0, 0, 0, activity.dp(8));
            list.addView(row, rowParams);
        }
        scroll.addView(list);
        int listHeight = Math.max(
            activity.dp(190),
            Math.min(activity.dp(320),
                activity.getResources().getDisplayMetrics().heightPixels - activity.dp(250)));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, listHeight));
        dialog.show();
        configureSettingsDialog(dialog);
    }

    private int exportFrameIcon(String id) {
        if (id.contains("fire")) return R.drawable.ic_menu_fire;
        if (id.contains("botanical")) return R.drawable.ic_menu_leaf;
        return R.drawable.ic_menu_frame;
    }

    private static String formatPixels(int value) {
        return String.format(Locale.US, "%,d", Math.max(0, value)).replace(',', ' ');
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
        button.setTextColor(Color.rgb(28, 92, 88));
        button.setBackground(activity.panelBg(
            Color.WHITE, activity.dp(10), Color.rgb(199, 220, 222)));
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
        TextView text = new LocalizedTextView(activity);
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
        if (resultCode != Activity.RESULT_OK || data == null) {
            releasePendingImageExport(requestCode);
            return;
        }
        if (requestCode == MainActivity.REQ_MEMORY_FILE
            || requestCode == MainActivity.REQ_MEMORY_PHOTO) {
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
        if (data.getData() == null) {
            releasePendingImageExport(requestCode);
            return;
        }
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
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            intent.setData(null);
            importFromUri(uri);
        }
    }

    private void releasePendingImageExport(int requestCode) {
        if (requestCode != MainActivity.REQ_EXPORT_PNG
            && requestCode != MainActivity.REQ_EXPORT_TILES) return;
        if (pendingImageExport != null) pendingImageExport.close();
        pendingImageExport = null;
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
                String sourceName = displayName(uri);
                activity.runOnUiThread(() -> confirmImportedTree(imported, sourceName));
            } catch (Exception error) {
                activity.runOnUiThread(() ->
                    activity.toast("Импорт не выполнен: " + safeError(error)));
                DiagnosticsLogger.handled(activity, "import", error);
            }
        }, "tree-import").start();
    }

    private void confirmImportedTree(TreeState imported, String sourceName) {
        if (activity.isFinishing() || activity.isDestroyed() || imported == null) return;
        if (!shouldWarnBeforeReplacing(activity.state, imported)) {
            applyImportedTree(imported);
            return;
        }

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(activity.dp(18), activity.dp(16), activity.dp(18), activity.dp(16));
        shell.setBackground(activity.panelBg(
            Color.rgb(250, 252, 253),
            activity.dp(18),
            Color.argb(56, 63, 82, 94)));

        TextView title = versionText(
            AppLanguage.isEnglish(activity) ? "Open another tree?" : "Открыть другое дерево?",
            20,
            Color.rgb(28, 34, 38),
            true);
        shell.addView(title, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            activity.dp(44)));

        String safeName = sourceName == null ? "" : sourceName.trim();
        String message = AppLanguage.isEnglish(activity)
            ? "The current tree will be replaced. To keep a separate copy, cancel and export it first."
            : "Текущее дерево будет заменено. Чтобы оставить отдельную копию, нажмите «Отмена» и сначала экспортируйте его.";
        if (!safeName.isEmpty()) {
            message += AppLanguage.isEnglish(activity)
                ? "\n\nFile: " + safeName
                : "\n\nФайл: " + safeName;
        }
        TextView body = versionText(message, 14, Color.rgb(71, 82, 89), false);
        body.setLineSpacing(0f, 1.12f);
        shell.addView(body, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, activity.dp(16), 0, 0);
        Button cancel = activity.actionButton(
            AppLanguage.isEnglish(activity) ? "Cancel" : "Отмена",
            view -> dialog.dismiss());
        Button open = activity.actionButton(
            AppLanguage.isEnglish(activity) ? "Open tree" : "Открыть дерево",
            view -> {
                dialog.dismiss();
                applyImportedTree(imported);
            });
        actions.addView(cancel, new LinearLayout.LayoutParams(activity.dp(112), activity.dp(44)));
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(
            activity.dp(148),
            activity.dp(44));
        openParams.leftMargin = activity.dp(8);
        actions.addView(open, openParams);
        shell.addView(actions);

        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.width = Math.min(
                activity.getResources().getDisplayMetrics().widthPixels - activity.dp(24),
                activity.dp(520));
            attrs.height = WindowManager.LayoutParams.WRAP_CONTENT;
            attrs.dimAmount = 0.32f;
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setAttributes(attrs);
        }
    }

    static boolean shouldWarnBeforeReplacing(TreeState current, TreeState imported) {
        return current != null
            && imported != null
            && !current.people.isEmpty()
            && current != imported;
    }

    private void applyImportedTree(TreeState imported) {
        if (activity.isFinishing() || activity.isDestroyed() || imported == null) return;
        activity.recordUndo("Импортировано дерево", "");
        activity.applyAutoArrangePreference(imported);
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
        AnalyticsReporter.treeImported(activity.state);
        activity.toast("Дерево импортировано");
    }

    void openPhotoPicker() {
        Intent intent;
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent = new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES);
            intent.setType("image/*");
        } else {
            intent = new Intent(
                Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setDataAndType(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "image/*");
        }
        activity.startActivityForResult(intent, MainActivity.REQ_PHOTO);
    }

    void savePreviewPhotoToGallery() {
        String mediaId = activity.pendingPreviewPhotoMediaId;
        String dataUrl = activity.pendingPreviewPhotoDataUrl;
        activity.pendingPreviewPhotoMediaId = "";
        activity.pendingPreviewPhotoDataUrl = "";
        if (mediaId.isEmpty() && dataUrl.isEmpty()) return;
        String extension = photoExtension(mediaId, dataUrl);
        String mime = "png".equals(extension)
            ? "image/png"
            : "webp".equals(extension) ? "image/webp" : "image/jpeg";
        String filename = "FamilyTreeDS_photo_" + dateStamp() + "." + extension;
        new Thread(() -> {
            Uri destination = null;
            try {
                ContentValues values = new ContentValues();
                values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename);
                values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, mime);
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    values.put(
                        android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_PICTURES + "/Family Tree DS");
                    values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 1);
                } else {
                    File directory = new File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_PICTURES),
                        "Family Tree DS");
                    if (!directory.isDirectory() && !directory.mkdirs()) {
                        throw new IOException("Не удалось создать папку Family Tree DS");
                    }
                    values.put(
                        android.provider.MediaStore.Images.Media.DATA,
                        new File(directory, filename).getAbsolutePath());
                }
                destination = activity.getContentResolver().insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values);
                if (destination == null) throw new IOException("Галерея недоступна");
                try (OutputStream output = activity.getContentResolver().openOutputStream(destination)) {
                    if (output == null) throw new IOException("Не удалось открыть фото");
                    if (!mediaId.isEmpty()) {
                        activity.store.mediaStore().copyTo(mediaId, output);
                    } else {
                        int comma = dataUrl.indexOf(',');
                        if (comma < 0 || comma + 1 >= dataUrl.length()) throw new IOException("Фото повреждено");
                        byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
                        output.write(bytes);
                    }
                    output.flush();
                }
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    ContentValues ready = new ContentValues();
                    ready.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0);
                    activity.getContentResolver().update(destination, ready, null, null);
                }
                activity.runOnUiThread(() -> activity.toast("Фото сохранено в галерею без потери качества"));
            } catch (Exception error) {
                if (destination != null) {
                    try {
                        activity.getContentResolver().delete(destination, null, null);
                    } catch (RuntimeException ignored) {}
                }
                DiagnosticsLogger.handled(activity, "photo.gallery", error);
                activity.runOnUiThread(() -> activity.toast("Фото не сохранено: " + error.getMessage()));
            }
        }, "photo-gallery-export").start();
    }

    private static String photoExtension(String mediaId, String dataUrl) {
        String source = mediaId == null ? "" : mediaId.trim().toLowerCase(Locale.ROOT);
        int dot = source.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < source.length()) {
            String candidate = source.substring(dot + 1);
            if ("png".equals(candidate) || "webp".equals(candidate)
                || "jpg".equals(candidate) || "jpeg".equals(candidate)) {
                return "jpeg".equals(candidate) ? "jpg" : candidate;
            }
        }
        String legacy = dataUrl == null ? "" : dataUrl.trim().toLowerCase(Locale.ROOT);
        if (legacy.startsWith("data:image/png")) return "png";
        if (legacy.startsWith("data:image/webp")) return "webp";
        return "jpg";
    }

    void openMemoryFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        activity.startActivityForResult(intent, MainActivity.REQ_MEMORY_FILE);
    }

    void openMemoryPhotoPicker() {
        Intent intent;
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent = new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES);
            intent.setType("image/*");
        } else {
            intent = new Intent(
                Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setDataAndType(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "image/*");
        }
        activity.startActivityForResult(intent, MainActivity.REQ_MEMORY_PHOTO);
    }

    void importPhotoFromUri(Uri uri) {
        Person person = activity.state.people.get(activity.pendingPhotoPersonId);
        activity.pendingPhotoPersonId = "";
        if (person == null || !activity.requireEditingEnabled()) return;
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
            person.avatarScale = 1f;
            person.avatarOffsetX = 0f;
            person.avatarOffsetY = 0f;
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
        if (person == null
            || uris == null
            || uris.isEmpty()) return;
        if (!activity.requireEditingEnabled()) return;
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
                AnalyticsReporter.treeExported("gedcom".equals(kind) ? "gedcom" : "json", exportState);
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
                AnalyticsReporter.treeExported("ftree", exportState);
                activity.runOnUiThread(() -> activity.toast(".ftree экспортирован"));
                DiagnosticsLogger.breadcrumb(activity, "export.ftree.finish");
            } catch (Exception error) {
                DiagnosticsLogger.handled(activity, "export.ftree", error);
                activity.runOnUiThread(() ->
                    activity.toast("Экспорт не выполнен: " + safeError(error)));
            }
        }, "tree-package-export").start();
    }

    void exportPngToUri(Uri uri) { exportImageToUri(uri, false); }

    void exportTilesToUri(Uri uri) { exportImageToUri(uri, true); }

    private void exportImageToUri(Uri uri, boolean split) {
        if (uri == null) return;
        TreeImageExport export = pendingImageExport;
        pendingImageExport = null;
        if (export == null) {
            activity.toast("Откройте предпросмотр PNG и выберите параметры ещё раз");
            return;
        }
        ExportLayout layout = export.layout(PNG_QUALITY_SCALES[pendingPngQuality],
            PNG_QUALITY_MAX_PIXELS[pendingPngQuality]);
        activity.toast(split ? "Создание частей PNG…" : "Создание PNG…");
        new Thread(() -> {
            try (OutputStream output = activity.getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IOException("Файл не создан");
                if (split) writeImageArchive(export, layout, output);
                else {
                    Bitmap bitmap = imageOnUi(() -> export.tile(layout, 0, 0));
                    try { compressPng(bitmap, output); }
                    finally { bitmap.recycle(); }
                }
                AnalyticsReporter.treeExported(split ? "png_tiles_zip" : "png", export.state);
                activity.runOnUiThread(() -> activity.toast(split ? "Архив PNG экспортирован" : "PNG экспортирован"));
            } catch (Exception | OutOfMemoryError error) {
                DiagnosticsLogger.handled(activity, "export.image", error);
                activity.runOnUiThread(() -> activity.toast("PNG не создан: " + safeError(error)));
            } finally {
                activity.runOnUiThread(export::close);
            }
        }, "tree-image-export").start();
    }

    private Bitmap imageOnUi(java.util.concurrent.Callable<Bitmap> render) throws Exception {
        java.util.concurrent.FutureTask<Bitmap> task = new java.util.concurrent.FutureTask<>(render);
        activity.runOnUiThread(task);
        return task.get();
    }

    private static void compressPng(Bitmap bitmap, OutputStream output) throws IOException {
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            throw new IOException("Не удалось закодировать PNG");
    }

    private void writeImageArchive(TreeImageExport export, ExportLayout layout, OutputStream output) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.setLevel(0);
            org.json.JSONArray tiles = new org.json.JSONArray();
            for (int row = 0; row < layout.rows; row++) {
                for (int column = 0; column < layout.columns; column++) {
                    final int r = row, c = column;
                    Bitmap bitmap = imageOnUi(() -> export.tile(layout, c, r));
                    String name = String.format(Locale.US, "tiles/tile-r%02d-c%02d.png", row + 1, column + 1);
                    try {
                        zip.putNextEntry(new ZipEntry(name));
                        compressPng(bitmap, zip);
                        zip.closeEntry();
                        tiles.put(new JSONObject().put("file", name).put("x", layout.x(column))
                            .put("y", layout.y(row)).put("width", bitmap.getWidth()).put("height", bitmap.getHeight()));
                    } finally { bitmap.recycle(); }
                }
            }
            ExportLayout overviewLayout = new ExportLayout(export.crop.width(), export.crop.height(),
                Math.min(1400f / export.crop.width(), 1400f / export.crop.height()), 2_000_000L, 0, 0);
            Bitmap overview = imageOnUi(() -> export.tile(overviewLayout, 0, 0));
            try {
                android.graphics.Canvas canvas = new android.graphics.Canvas(overview);
                Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
                line.setColor(Color.rgb(0, 126, 116)); line.setStrokeWidth(2f);
                for (int col = 1; col < layout.columns; col++) {
                    float x = overview.getWidth() * layout.x(col) / (float) layout.width;
                    canvas.drawLine(x, 0, x, overview.getHeight(), line);
                }
                for (int row = 1; row < layout.rows; row++) {
                    float y = overview.getHeight() * layout.y(row) / (float) layout.height;
                    canvas.drawLine(0, y, overview.getWidth(), y, line);
                }
                zip.putNextEntry(new ZipEntry("overview.png"));
                compressPng(overview, zip); zip.closeEntry();
            } finally { overview.recycle(); }
            JSONObject manifest = new JSONObject().put("format", "ru.drshapaya.familytree.tiles")
                .put("version", 2).put("width", layout.width).put("height", layout.height)
                .put("columns", layout.columns).put("rows", layout.rows)
                .put("verticalCuts", export.verticalCuts).put("horizontalCuts", export.horizontalCuts)
                .put("left", export.crop.left).put("top", export.crop.top)
                .put("right", export.crop.right).put("bottom", export.crop.bottom)
                .put("tiles", tiles);
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifest.toString(2).getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
    }

    void exportPdfToUri(Uri uri) {
        if (uri == null) return;
        TreeState exportState = TreeStateCopier.copy(activity.state);
        String pageSize = pendingPdfPage;
        boolean landscape = pendingPdfLandscape;
        int requestedScale = pendingPdfScale;
        boolean monochrome = pendingPdfMonochrome;
        boolean fitPage = pendingPdfFit;
        String frame = RewardCatalog.belongsToCategory(pendingPdfFrame, RewardCatalog.EXPORT_FRAMES)
            ? pendingPdfFrame
            : new RewardWallet(activity).selected(RewardCatalog.EXPORT_FRAMES);
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
                    monochrome, fitPage, frame);
                AnalyticsReporter.treeExported("pdf", exportState);
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
        boolean monochrome, boolean fitPage, String frame
    ) throws Exception {
        int portraitWidth = "A3".equals(pageSize) ? 842 : 595;
        int portraitHeight = "A3".equals(pageSize) ? 1191 : 842;
        int pageWidth = landscape ? portraitHeight : portraitWidth;
        int pageHeight = landscape ? portraitWidth : portraitHeight;
        float margin = 28f;
        float footer = 18f;
        float renderScale = 0.55f * Math.max(25, Math.min(100, requestedScale)) / 100f;
        float contentWidth = pageWidth - margin * 2f;
        float contentHeight = pageHeight - margin * 2f - footer;

        TreeDocumentRenderer renderer = new TreeDocumentRenderer(
            activity,
            exportState,
            activity.store.mediaStore());
        RectF bounds = renderer.bounds();
        if (fitPage) renderScale = ExportLayout.fitPdfScale(bounds.width(), bounds.height(), contentWidth, contentHeight);
        float pageWorldWidth = contentWidth / renderScale;
        float pageWorldHeight = contentHeight / renderScale;
        int columns = fitPage ? 1 : Math.max(1, (int) Math.ceil(bounds.width() / pageWorldWidth));
        int rows = fitPage ? 1 : Math.max(1, (int) Math.ceil(bounds.height() / pageWorldHeight));
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
                    page.getCanvas().drawColor(Color.WHITE);
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
                    if (fitPage) {
                        world.set(bounds);
                        float w = bounds.width() * renderScale, h = bounds.height() * renderScale;
                        target.set(margin + (contentWidth - w) / 2f, margin + (contentHeight - h) / 2f,
                            margin + (contentWidth + w) / 2f, margin + (contentHeight + h) / 2f);
                    }
                    renderer.render(page.getCanvas(), world, target, renderScale, monochrome);
                    ExportDecoration.draw(page.getCanvas(), new RectF(8, 8, pageWidth - 8,
                        pageHeight - margin), frame, monochrome);
                    String caption = "Family Tree DS " + MainActivity.VERSION_NAME
                        + " · " + number + "/" + pageCount
                        + (AppLanguage.isEnglish(activity)
                            ? " · row " + (row + 1) + ", column " + (column + 1)
                            : " · ряд " + (row + 1) + ", колонка " + (column + 1));
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

    String exportGedcomText() {
        return exportGedcomText(activity.state);
    }

    private String exportGedcomText(TreeState sourceState) {
        StringBuilder out = new StringBuilder();
        out.append("0 HEAD\n1 SOUR Family Tree DS\n1 CHAR UTF-8\n");
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
            AnalyticsReporter.treeExported("json_share", activity.state);
            Uri uri = TreeShareProvider.uriFor(filename);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/json");
            send.putExtra(Intent.EXTRA_SUBJECT, activity.tr("Семейное древо (JSON)"));
            send.putExtra(Intent.EXTRA_TITLE, filename);
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.setClipData(ClipData.newRawUri(filename, uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(
                send,
                activity.tr("Поделиться деревом (JSON)")));
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
                AnalyticsReporter.treeExported("ftree_share", exportState);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    Uri uri = TreeShareProvider.uriFor(filename);
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType(TreePackageIO.MIME_TYPE);
                    send.putExtra(
                        Intent.EXTRA_SUBJECT,
                        activity.tr("Семейное дерево Family Tree DS"));
                    send.putExtra(Intent.EXTRA_TITLE, filename);
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.setClipData(ClipData.newRawUri(filename, uri));
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    activity.startActivity(Intent.createChooser(
                        send,
                        activity.tr("Поделиться деревом")));
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

    private static String safeError(Throwable error) {
        if (error instanceof java.util.concurrent.ExecutionException && error.getCause() != null)
            return safeError(error.getCause());
        if (error instanceof OutOfMemoryError) return "недостаточно памяти; уменьшите качество или добавьте разрезы";
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
