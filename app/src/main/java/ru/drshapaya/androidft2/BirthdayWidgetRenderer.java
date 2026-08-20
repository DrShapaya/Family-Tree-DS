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
        return bitmap;
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

    static String date(Context context, BirthdayCalculator.Result birthday) {
        if (birthday == null) {
            return AppLanguage.isEnglish(context)
                ? "Add a full birth date"
                : "Добавьте день и месяц рождения";
        }
        String result = shortDate(context, birthday);
        if (birthday.age > 0) {
            result += AppLanguage.isEnglish(context)
                ? " · turns " + birthday.age
                : " · исполнится " + birthday.age;
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
            return AppLanguage.isEnglish(context) ? "Birthday today" : "День рождения сегодня";
        }
        return AppLanguage.isEnglish(context)
            ? "In " + birthday.daysUntil + " " + daysWord(context, birthday.daysUntil)
            : "Через " + birthday.daysUntil + " " + daysWord(context, birthday.daysUntil);
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
