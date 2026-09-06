package ru.drshapaya.androidft2;

import android.graphics.Color;

/** Maps the programmatic light UI palette to accessible dark surfaces and text. */
final class AppThemePalette {
    private static volatile boolean dark;
    private static final int PRIMARY = Color.rgb(8, 122, 115);
    private static final int PRIMARY_BRIGHT = Color.rgb(24, 169, 153);
    private static final int PRIMARY_SURFACE = Color.rgb(232, 248, 246);
    private static final int SECONDARY = Color.rgb(112, 82, 190);
    private static final int SECONDARY_BRIGHT = Color.rgb(143, 109, 226);
    private static final int SECONDARY_SURFACE = Color.rgb(244, 239, 255);
    private static final int CTA_PRIMARY = Color.rgb(8, 132, 122);
    private static final int CTA_BLUE = Color.rgb(45, 105, 190);
    private static final int CTA_SECONDARY = Color.rgb(104, 72, 198);
    private static final int CTA_PRIMARY_DARK = Color.rgb(8, 108, 101);
    private static final int CTA_BLUE_DARK = Color.rgb(40, 82, 154);
    private static final int CTA_SECONDARY_DARK = Color.rgb(86, 58, 174);
    private static final int BLUE = Color.rgb(47, 140, 255);
    private static final int BLUE_SURFACE = Color.rgb(233, 242, 255);
    private static final int PARTNER = Color.rgb(116, 103, 132);
    private static final int NEUTRAL_RELATION = Color.rgb(94, 104, 112);

    private AppThemePalette() {}

    static void setDark(boolean enabled) {
        dark = enabled;
    }

    static boolean isDark() {
        return dark;
    }

    static int primary() {
        return text(PRIMARY);
    }

    static int primaryBright() {
        return text(PRIMARY_BRIGHT);
    }

    static int primarySurface() {
        return surface(PRIMARY_SURFACE);
    }

    static int secondary() {
        return text(SECONDARY);
    }

    static int secondaryBright() {
        return text(SECONDARY_BRIGHT);
    }

    static int secondarySurface() {
        return surface(SECONDARY_SURFACE);
    }

    static int blue() {
        return text(BLUE);
    }

    static int blueSurface() {
        return surface(BLUE_SURFACE);
    }

    static int ctaPrimary() {
        return dark ? CTA_PRIMARY_DARK : CTA_PRIMARY;
    }

    static int ctaBlue() {
        return dark ? CTA_BLUE_DARK : CTA_BLUE;
    }

    static int ctaSecondary() {
        return dark ? CTA_SECONDARY_DARK : CTA_SECONDARY;
    }

    static int relationColor(String type) {
        if ("parent".equals(type)) return primary();
        if ("sibling".equals(type)) return text(NEUTRAL_RELATION);
        if ("family".equals(type)) return text(NEUTRAL_RELATION);
        if ("partner".equals(type)) return text(PARTNER);
        return text(NEUTRAL_RELATION);
    }

    static int alpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
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
            return withAlpha(alpha, 106, 222, 209);
        }
        if (b > g * 1.08f && r > g * 0.68f && light < 170) {
            return withAlpha(alpha, 184, 160, 255);
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

        if (g > r + 5 && g > b - 8) return withAlpha(alpha, 22, 48, 45);
        if (r > g + 8) return withAlpha(alpha, 58, 37, 36);
        if (b > r + 8) return withAlpha(alpha, 28, 38, 56);
        if (max - min < 30) return withAlpha(alpha, light > 246 ? 20 : 27, light > 246 ? 29 : 37, light > 246 ? 34 : 43);
        return withAlpha(alpha, 27, 38, 45);
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
