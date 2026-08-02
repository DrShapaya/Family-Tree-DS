package ru.drshapaya.androidft2;

import android.graphics.Color;

/** Maps the programmatic light UI palette to accessible dark surfaces and text. */
final class AppThemePalette {
    private static volatile boolean dark;

    private AppThemePalette() {}

    static void setDark(boolean enabled) {
        dark = enabled;
    }

    static boolean isDark() {
        return dark;
    }

    static int text(int color) {
        if (!dark || Color.alpha(color) == 0) return color;
        int alpha = Color.alpha(color);
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        int light = (r * 299 + g * 587 + b * 114) / 1000;

        if (r >= 245 && g >= 245 && b >= 245) return color;
        if (max - min < 42) {
            if (light < 75) return withAlpha(alpha, 238, 244, 246);
            if (light < 165) return withAlpha(alpha, 174, 189, 195);
            return color;
        }
        if (g > r * 1.18f && g > b * 1.05f && light < 155) {
            return withAlpha(alpha, 83, 211, 197);
        }
        if (r > g * 1.35f && r > b * 1.25f && light < 170) {
            return withAlpha(alpha, 255, 139, 130);
        }
        return color;
    }

    static int surface(int color) {
        if (!dark || Color.alpha(color) == 0) return color;
        int alpha = Color.alpha(color);
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        int light = (r * 299 + g * 587 + b * 114) / 1000;
        if (light < 185) return color;

        if (g > r + 5 && g > b - 8) return withAlpha(alpha, 20, 52, 48);
        if (r > g + 8) return withAlpha(alpha, 54, 35, 34);
        if (b > r + 8) return withAlpha(alpha, 25, 42, 53);
        if (max - min < 30) return withAlpha(alpha, light > 246 ? 24 : 30, light > 246 ? 34 : 43, light > 246 ? 38 : 48);
        return withAlpha(alpha, 29, 42, 47);
    }

    static int stroke(int color) {
        if (!dark || Color.alpha(color) == 0) return color;
        int alpha = Color.alpha(color);
        int mapped = text(color);
        int light = (Color.red(mapped) * 299 + Color.green(mapped) * 587 + Color.blue(mapped) * 114) / 1000;
        if (light > 185) return withAlpha(alpha, 61, 78, 84);
        return mapped;
    }

    private static int withAlpha(int alpha, int r, int g, int b) {
        return Color.argb(alpha, r, g, b);
    }
}
