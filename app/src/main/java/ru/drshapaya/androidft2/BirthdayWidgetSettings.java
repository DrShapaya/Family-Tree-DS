package ru.drshapaya.androidft2;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

final class BirthdayWidgetSettings {
    static final String BACKGROUND_PERSON = "person";
    static final String BACKGROUND_CREAM = "cream";
    static final String BACKGROUND_MINT = "mint";
    static final String BACKGROUND_BLUE = "blue";
    static final String BACKGROUND_LILAC = "lilac";
    static final String BACKGROUND_ROSE = "rose";
    static final String BACKGROUND_DARK = "dark";

    private static final String PREFS = "birthday-widget-settings";
    private static final String PREFIX = "widget_";

    String background = BACKGROUND_PERSON;
    int customColor = 0xff84c7ae;
    int transparency = 8;
    boolean backgroundEnabled = true;
    boolean autoColor = true;
    boolean showPhoto = true;
    final Set<String> excludedPersonIds = new HashSet<>();

    static BirthdayWidgetSettings load(Context context, int appWidgetId) {
        BirthdayWidgetSettings result = new BirthdayWidgetSettings();
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String prefix = PREFIX + appWidgetId + "_";
        result.background = normalizeBackground(preferences.getString(prefix + "background", BACKGROUND_PERSON));
        result.customColor = opaqueColor(preferences.contains(prefix + "custom_color")
            ? preferences.getInt(prefix + "custom_color", 0xff84c7ae)
            : legacyColor(result.background));
        result.transparency = clamp(preferences.getInt(prefix + "transparency", 8));
        result.backgroundEnabled = preferences.getBoolean(prefix + "background_enabled", true);
        result.autoColor = preferences.contains(prefix + "auto_color")
            ? preferences.getBoolean(prefix + "auto_color", true)
            : BACKGROUND_PERSON.equals(result.background);
        result.showPhoto = preferences.getBoolean(prefix + "show_photo", true);
        result.excludedPersonIds.addAll(preferences.getStringSet(
            prefix + "excluded_person_ids",
            java.util.Collections.emptySet()));
        return result;
    }

    void save(Context context, int appWidgetId) {
        String prefix = PREFIX + appWidgetId + "_";
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(prefix + "background", normalizeBackground(background))
            .putInt(prefix + "custom_color", opaqueColor(customColor))
            .putInt(prefix + "transparency", clamp(transparency))
            .putBoolean(prefix + "background_enabled", backgroundEnabled)
            .putBoolean(prefix + "auto_color", autoColor)
            .putBoolean(prefix + "show_photo", showPhoto)
            .putStringSet(prefix + "excluded_person_ids", new HashSet<>(excludedPersonIds))
            .apply();
    }

    static void delete(Context context, int appWidgetId) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String prefix = PREFIX + appWidgetId + "_";
        preferences.edit()
            .remove(prefix + "background")
            .remove(prefix + "custom_color")
            .remove(prefix + "transparency")
            .remove(prefix + "background_enabled")
            .remove(prefix + "auto_color")
            .remove(prefix + "show_photo")
            .remove(prefix + "excluded_person_ids")
            .apply();
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static int opaqueColor(int value) {
        return 0xff000000 | (value & 0x00ffffff);
    }

    private static int legacyColor(String background) {
        if (BACKGROUND_CREAM.equals(background)) return 0xffffdfa9;
        if (BACKGROUND_BLUE.equals(background)) return 0xffa7d5f7;
        if (BACKGROUND_LILAC.equals(background)) return 0xffcbb9ee;
        if (BACKGROUND_ROSE.equals(background)) return 0xffffb9cd;
        if (BACKGROUND_DARK.equals(background)) return 0xff25262b;
        return 0xff84c7ae;
    }

    private static String normalizeBackground(String value) {
        if (BACKGROUND_CREAM.equals(value)
            || BACKGROUND_MINT.equals(value)
            || BACKGROUND_BLUE.equals(value)
            || BACKGROUND_LILAC.equals(value)
            || BACKGROUND_ROSE.equals(value)
            || BACKGROUND_DARK.equals(value)) {
            return value;
        }
        return BACKGROUND_PERSON;
    }
}
