package ru.drshapaya.androidft2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import java.text.DateFormatSymbols;
import java.util.Locale;

/** Draws only bitmap-only widget layers. Text stays native in RemoteViews. */
final class BirthdayWidgetRenderer {
    private BirthdayWidgetRenderer() {}

    static Bitmap renderBackground(
        Context context,
        BirthdayCalculator.Result birthday,
        BirthdayWidgetSettings settings,
        int widthDp,
        int heightDp
    ) {
        float density = Math.min(2f, Math.max(1f, context.getResources().getDisplayMetrics().density));
        int safeWidthDp = Math.max(110, widthDp);
        int safeHeightDp = Math.max(46, heightDp);
        int width = Math.min(900, Math.round(safeWidthDp * density));
        int height = Math.min(480, Math.round(safeHeightDp * density));
        float scale = width / (float) safeWidthDp;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int background = backgroundColor(birthday, settings);
        int alpha = settings.backgroundEnabled
            ? Math.round(255 * (100 - settings.transparency) / 100f)
            : 0;
        float inset = 1.5f * scale;
        float radius = Math.min(29f, Math.min(safeWidthDp, safeHeightDp) * 0.25f) * scale;
        RectF card = new RectF(inset, inset, width - inset, height - inset);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setColor(withAlpha(background, alpha));
        canvas.drawRoundRect(card, radius, radius, paint);

        Path clip = new Path();
        clip.addRoundRect(card, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        if (alpha > 0) drawOneUiDecoration(canvas, background, alpha, scale, safeHeightDp);
        canvas.restore();
        if (alpha > 0) {
            String frame = new RewardWallet(context).selected(RewardCatalog.WIDGET_FRAMES);
            if ("widget_frame_family".equals(frame)) {
                drawFamilyFrame(canvas, card, radius, scale, alpha, background);
            } else if ("widget_frame_fire".equals(frame)) {
                drawFireFrame(canvas, card, alpha);
            } else if ("widget_frame_botanical".equals(frame)) {
                drawBotanicalFrame(canvas, card, scale, alpha, background);
            }
        }
        return bitmap;
    }

    private static void drawFamilyFrame(Canvas canvas, RectF card, float radius,
                                        float scale, int alpha, int background) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f * scale);
        paint.setColor(withAlpha(isDark(background) ? Color.rgb(158, 225, 193)
            : Color.rgb(61, 121, 93), Math.min(alpha, 205)));
        RectF border = new RectF(card);
        border.inset(.9f * scale, .9f * scale);
        canvas.drawRoundRect(border, Math.max(0, radius - .9f * scale),
            Math.max(0, radius - .9f * scale), paint);
        // Small leaf pairs stay on the border and leave native widget text clear.
        paint.setStyle(Paint.Style.FILL);
        for (int side = 0; side < 2; side++) {
            float x = side == 0 ? card.left + 18f * scale : card.right - 18f * scale;
            float y = card.bottom - 6f * scale;
            canvas.save();
            canvas.rotate(side == 0 ? -25f : 25f, x, y);
            canvas.drawOval(x - 5f * scale, y - 3f * scale, x, y + scale, paint);
            canvas.drawOval(x + scale, y - 4f * scale, x + 6f * scale, y, paint);
            canvas.restore();
        }
    }

    private static void drawFireFrame(Canvas canvas, RectF card, int alpha) {
        RectF layer = expandedLayer(card, 38f);
        int checkpoint = canvas.saveLayerAlpha(layer, Math.min(alpha, 235));
        RectF border = new RectF(card);
        border.inset(.8f, .8f);
        canvas.translate(border.left, border.top);
        FireCardEffect fire = new FireCardEffect();
        // Widgets are bitmaps, so use one deliberate animation frame of the same
        // organic effect as fire cards instead of a row of geometric spikes.
        fire.behind(canvas, border.width(), border.height(), 0x51f3, 1.75f);
        fire.rim(canvas, border.width(), border.height(), 0x51f3, 1.75f);
        canvas.restoreToCount(checkpoint);
    }

    private static void drawBotanicalFrame(
        Canvas canvas,
        RectF card,
        float scale,
        int alpha,
        int background
    ) {
        RectF border = new RectF(card);
        border.inset(.8f * scale, .8f * scale);
        int checkpoint = canvas.saveLayerAlpha(expandedLayer(card, 28f * scale), Math.min(alpha, 235));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean dark = isDark(background);
        int stem = dark ? Color.rgb(151, 211, 106) : Color.rgb(69, 122, 35);
        int leaf = dark ? Color.rgb(111, 177, 71) : Color.rgb(82, 146, 35);
        int highlight = dark ? Color.rgb(193, 232, 124) : Color.rgb(164, 207, 73);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(2.1f * scale);
        paint.setColor(withAlpha(stem, Math.min(alpha, 225)));
        canvas.drawRoundRect(border, Math.max(0f, 12f * scale), Math.max(0f, 12f * scale), paint);
        paint.setStrokeWidth(.8f * scale);
        paint.setColor(withAlpha(highlight, Math.min(alpha, 185)));
        canvas.drawRoundRect(border, Math.max(0f, 12f * scale), Math.max(0f, 12f * scale), paint);

        Path leafPath = new Path();
        leafPath.moveTo(0f, 0f);
        leafPath.cubicTo(4f, -8f, 14f, -8f, 22f, -2f);
        leafPath.cubicTo(16f, 1f, 9f, 9f, 0f, 0f);
        leafPath.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(leaf, Math.min(alpha, 225)));
        float step = Math.max(30f * scale, Math.min(48f * scale, border.width() / 8f));
        int index = 0;
        for (float x = border.left + 16f * scale; x < border.right - 10f * scale; x += step) {
            drawWidgetLeaf(canvas, paint, leafPath, x, border.top + 6f * scale, -50f, scale);
            if ((index++ & 1) == 0) {
                drawWidgetLeaf(canvas, paint, leafPath, x + 8f * scale,
                    border.bottom - 6f * scale, 132f, scale);
            }
        }
        for (float y = border.top + 24f * scale; y < border.bottom - 14f * scale; y += step) {
            drawWidgetLeaf(canvas, paint, leafPath, border.left + 6f * scale, y, -138f, scale);
            if ((index++ & 1) == 0) {
                drawWidgetLeaf(canvas, paint, leafPath, border.right - 6f * scale, y + 7f * scale, 42f, scale);
            }
        }
        paint.setColor(withAlpha(Color.rgb(250, 239, 178), Math.min(alpha, 235)));
        for (int flower = 0; flower < 4; flower++) {
            float x = border.left + (flower + 1f) * border.width() / 5f;
            float y = flower % 2 == 0 ? border.top + 4f * scale : border.bottom - 4f * scale;
            canvas.drawCircle(x, y, 2.8f * scale, paint);
            paint.setColor(withAlpha(Color.rgb(218, 164, 45), Math.min(alpha, 240)));
            canvas.drawCircle(x, y, .95f * scale, paint);
            paint.setColor(withAlpha(Color.rgb(250, 239, 178), Math.min(alpha, 235)));
        }
        canvas.restoreToCount(checkpoint);
    }

    private static void drawWidgetLeaf(
        Canvas canvas,
        Paint paint,
        Path leaf,
        float x,
        float y,
        float angle,
        float scale
    ) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(angle);
        canvas.scale(.84f * scale, .84f * scale);
        canvas.drawPath(leaf, paint);
        canvas.restore();
    }

    private static RectF expandedLayer(RectF source, float bleed) {
        RectF result = new RectF(source);
        result.inset(-bleed, -bleed);
        return result;
    }

    static Bitmap renderPhoto(Context context, BirthdayCalculator.Result birthday, int sizeDp) {
        if (birthday == null || birthday.person == null) return null;
        String mediaId = birthday.person.photoMediaId == null ? "" : birthday.person.photoMediaId.trim();
        if (mediaId.isEmpty()) return null;
        float density = Math.min(3f, Math.max(1f, context.getResources().getDisplayMetrics().density));
        int size = Math.min(480, Math.max(96, Math.round(sizeDp * density)));
        Bitmap source = new TreeStore(context).mediaStore().decodeBitmap(mediaId, size * 2);
        if (source == null) return null;
        Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        float radius = size * 0.27f;
        int checkpoint = canvas.save();
        Path photoClip = new Path();
        photoClip.addRoundRect(new RectF(0, 0, size, size), radius, radius, Path.Direction.CW);
        canvas.clipPath(photoClip);
        canvas.drawBitmap(source, AvatarTransform.sourceRect(source, birthday.person), new RectF(0, 0, size, size), paint);
        canvas.restoreToCount(checkpoint);
        return result;
    }

    static int backgroundColor(BirthdayCalculator.Result birthday, String choice) {
        if (BirthdayWidgetSettings.BACKGROUND_CREAM.equals(choice)) return Color.rgb(250, 244, 229);
        if (BirthdayWidgetSettings.BACKGROUND_MINT.equals(choice)) return Color.rgb(220, 243, 235);
        if (BirthdayWidgetSettings.BACKGROUND_BLUE.equals(choice)) return Color.rgb(221, 235, 250);
        if (BirthdayWidgetSettings.BACKGROUND_LILAC.equals(choice)) return Color.rgb(235, 225, 249);
        if (BirthdayWidgetSettings.BACKGROUND_ROSE.equals(choice)) return Color.rgb(250, 226, 235);
        if (BirthdayWidgetSettings.BACKGROUND_DARK.equals(choice)) return Color.rgb(39, 40, 44);
        int color = birthday == null || birthday.person == null
            ? Color.rgb(220, 243, 235)
            : birthday.person.color;
        return mix(color, Color.WHITE, 0.76f);
    }

    static int backgroundColor(
        BirthdayCalculator.Result birthday,
        BirthdayWidgetSettings settings
    ) {
        if (settings == null || settings.autoColor) {
            return backgroundColor(birthday, BirthdayWidgetSettings.BACKGROUND_PERSON);
        }
        return 0xff000000 | (settings.customColor & 0x00ffffff);
    }

    static int primaryTextColor(BirthdayCalculator.Result birthday, BirthdayWidgetSettings settings) {
        if (settings != null && !settings.backgroundEnabled) return Color.WHITE;
        return isDark(backgroundColor(birthday, settings))
            ? Color.rgb(249, 250, 252)
            : Color.rgb(34, 35, 39);
    }

    static int secondaryTextColor(BirthdayCalculator.Result birthday, BirthdayWidgetSettings settings) {
        if (settings != null && !settings.backgroundEnabled) return Color.rgb(222, 224, 230);
        return isDark(backgroundColor(birthday, settings))
            ? Color.rgb(205, 208, 216)
            : Color.rgb(82, 85, 93);
    }

    static String personName(Context context, BirthdayCalculator.Result birthday) {
        if (birthday == null || birthday.person == null
            || birthday.person.name == null || birthday.person.name.trim().isEmpty()) {
            return AppLanguage.isEnglish(context) ? "Birthdays" : "Дни рождения";
        }
        return birthday.person.name.trim();
    }

    static String compactPersonName(
        Context context,
        BirthdayCalculator.Result birthday,
        boolean photoVisible
    ) {
        String fullName = personName(context, birthday);
        return photoVisible ? withoutPatronymic(fullName) : fullName;
    }

    static String withoutPatronymic(String fullName) {
        if (fullName == null) return "";
        String normalized = fullName.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) return "";
        String[] parts = normalized.split(" ");
        if (parts.length < 3) return normalized;
        return parts[0] + " " + parts[1];
    }

    static String date(Context context, BirthdayCalculator.Result birthday) {
        if (birthday == null) {
            return AppLanguage.isEnglish(context)
                ? "Add a full birth date"
                : "Добавьте день и месяц рождения";
        }
        String result = shortDate(context, birthday);
        if (birthday.age > 0) {
            result += AppLanguage.isEnglish(context)
                ? ", age " + birthday.age
                : ", возраст " + birthday.age;
        }
        return result;
    }

    static String shortDate(Context context, BirthdayCalculator.Result birthday) {
        if (birthday == null) return date(context, null);
        Locale locale = AppLanguage.isEnglish(context) ? Locale.ENGLISH : new Locale("ru");
        String[] months = new DateFormatSymbols(locale).getShortMonths();
        String month = birthday.month >= 1 && birthday.month <= 12
            ? months[birthday.month - 1].replace(".", "")
            : "";
        return birthday.day + " " + month;
    }

    static String number(Context context, BirthdayCalculator.Result birthday) {
        if (birthday == null) return "—";
        if (birthday.daysUntil == 0) return AppLanguage.isEnglish(context) ? "Today" : "Сегодня";
        return Integer.toString(birthday.daysUntil);
    }

    static String daysLabel(Context context, BirthdayCalculator.Result birthday) {
        if (birthday == null || birthday.daysUntil == 0) return "";
        return daysWord(context, birthday.daysUntil);
    }

    static String compactCountdown(Context context, BirthdayCalculator.Result birthday) {
        if (birthday == null) return AppLanguage.isEnglish(context) ? "No dates yet" : "Пока нет дат";
        if (birthday.daysUntil == 0) {
            return AppLanguage.isEnglish(context) ? "Today" : "Сегодня";
        }
        return birthday.daysUntil + " " + daysWord(context, birthday.daysUntil);
    }

    private static String daysWord(Context context, int days) {
        if (AppLanguage.isEnglish(context)) return days == 1 ? "day" : "days";
        int mod100 = days % 100;
        int mod10 = days % 10;
        if (mod100 >= 11 && mod100 <= 14) return "дней";
        if (mod10 == 1) return "день";
        if (mod10 >= 2 && mod10 <= 4) return "дня";
        return "дней";
    }

    private static void drawOneUiDecoration(
        Canvas canvas,
        int background,
        int alpha,
        float scale,
        int heightDp
    ) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float baseX = canvas.getWidth() - 8f * scale;
        float baseY = canvas.getHeight() - 3f * scale;
        int[] colors = {
            Color.rgb(98, 204, 169), Color.rgb(255, 183, 204),
            Color.rgb(255, 215, 113), Color.rgb(134, 188, 244), Color.rgb(190, 157, 235)
        };
        float[] x = {-12f, -36f, -61f, -84f, -107f};
        for (int index = 0; index < colors.length; index++) {
            paint.setColor(withAlpha(colors[index], Math.min(alpha, 46)));
            float radius = (heightDp <= 80 ? 10f : 16f) * scale;
            canvas.drawCircle(baseX + x[index] * scale, baseY - (index % 2) * 17f * scale, radius, paint);
        }
        paint.setColor(withAlpha(isDark(background) ? Color.WHITE : Color.rgb(255, 255, 255), Math.min(alpha, 18)));
        canvas.drawCircle(canvas.getWidth() * 0.76f, -9f * scale, 58f * scale, paint);
    }

    private static int mix(int first, int second, float ratio) {
        float safe = Math.max(0f, Math.min(1f, ratio));
        return Color.rgb(
            Math.round(Color.red(first) * (1f - safe) + Color.red(second) * safe),
            Math.round(Color.green(first) * (1f - safe) + Color.green(second) * safe),
            Math.round(Color.blue(first) * (1f - safe) + Color.blue(second) * safe));
    }

    private static boolean isDark(int color) {
        return Color.red(color) * 0.299f + Color.green(color) * 0.587f + Color.blue(color) * 0.114f < 128f;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }
}
