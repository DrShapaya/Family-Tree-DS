package ru.drshapaya.androidft2;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AppLanguage {
    static final String AUTO = "auto";
    static final String RUSSIAN = "ru";
    static final String ENGLISH = "en";

    private static final String PREFS = "androidft-language";
    private static final String MODE = "mode";
    private static volatile Map<String, String> translations;
    private static volatile List<Map.Entry<String, String>> fragments;

    private AppLanguage() {}

    static Context wrap(Context context) {
        if (context == null) return null;
        String language = resolvedLanguage(context);
        Locale locale = new Locale(language);
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(new LocaleList(locale));
        } else {
            configuration.setLocale(locale);
        }
        return context.createConfigurationContext(configuration);
    }

    static String mode(Context context) {
        if (context == null) return AUTO;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(MODE, AUTO);
    }

    static void setMode(Context context, String mode) {
        if (context == null) return;
        String safe = RUSSIAN.equals(mode) || ENGLISH.equals(mode) ? mode : AUTO;
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(MODE, safe)
            .commit()) {
            throw new IllegalStateException("Не удалось сохранить язык приложения");
        }
    }

    static String resolvedLanguage(Context context) {
        String mode = mode(context);
        if (RUSSIAN.equals(mode) || ENGLISH.equals(mode)) return mode;
        Locale locale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList locales = LocaleList.getDefault();
            locale = locales.isEmpty() ? Locale.getDefault() : locales.get(0);
        } else {
            locale = Locale.getDefault();
        }
        return "ru".equalsIgnoreCase(locale.getLanguage()) ? RUSSIAN : ENGLISH;
    }

    static boolean isEnglish(Context context) {
        return ENGLISH.equals(resolvedLanguage(context));
    }

    static String modeLabel(Context context) {
        String selected = mode(context);
        if (RUSSIAN.equals(selected)) return isEnglish(context) ? "Russian" : "Русский";
        if (ENGLISH.equals(selected)) return "English";
        return isEnglish(context) ? "Automatic" : "Автоматически";
    }

    static CharSequence translate(Context context, CharSequence source) {
        if (source == null || !isEnglish(context)) return source;
        String value = source.toString();
        if (!containsRussian(value)) return source;
        Map<String, String> map = translations(context);
        String exact = map.get(value);
        if (exact != null && !exact.isEmpty()) {
            return preserveOuterWhitespace(value, exact);
        }

        String result = value;
        for (Map.Entry<String, String> entry : fragmentTranslations(context)) {
            String russian = entry.getKey();
            if (!result.contains(russian)) continue;
            result = result.replace(
                russian,
                preserveOuterWhitespace(russian, entry.getValue()));
        }
        return result;
    }

    static String text(Context context, String source) {
        CharSequence translated = translate(context, source);
        return translated == null ? "" : translated.toString();
    }

    static String translateFully(Context context, String source) {
        String result = text(context, source);
        if (!isEnglish(context) || !containsRussian(result)) return result;
        List<Map.Entry<String, String>> all = new ArrayList<>(
            translations(context).entrySet());
        all.sort(Comparator.comparingInt(
            (Map.Entry<String, String> entry) -> entry.getKey().length()).reversed());
        for (Map.Entry<String, String> entry : all) {
            String russian = entry.getKey();
            if (russian.length() < 2 || !result.contains(russian)) continue;
            result = result.replace(
                russian,
                preserveOuterWhitespace(russian, entry.getValue()));
        }
        return result;
    }

    private static boolean containsRussian(String value) {
        if (value == null) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if ((character >= 'А' && character <= 'я')
                || character == 'Ё'
                || character == 'ё') {
                return true;
            }
        }
        return false;
    }

    private static String preserveOuterWhitespace(String source, String translated) {
        if (translated == null) return source;
        int leading = 0;
        int trailing = 0;
        while (leading < source.length() && Character.isWhitespace(source.charAt(leading))) {
            leading++;
        }
        while (trailing < source.length() - leading
            && Character.isWhitespace(source.charAt(source.length() - 1 - trailing))) {
            trailing++;
        }
        StringBuilder result = new StringBuilder();
        result.append(source, 0, leading);
        result.append(translated.trim());
        if (trailing > 0) {
            result.append(source, source.length() - trailing, source.length());
        }
        return result.toString();
    }

    private static Map<String, String> translations(Context context) {
        Map<String, String> cached = translations;
        if (cached != null) return cached;
        synchronized (AppLanguage.class) {
            if (translations != null) return translations;
            LinkedHashMap<String, String> loaded = new LinkedHashMap<>();
            try (InputStream input = context.getAssets().open("i18n/en.json");
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
                JSONObject json = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
                Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = json.optString(key, "");
                    if (!key.isEmpty() && !value.isEmpty()) loaded.put(key, value);
                }
            } catch (Exception error) {
                DiagnosticsLogger.handled(context, "language.load", error);
            }
            translations = loaded;
            return loaded;
        }
    }

    private static List<Map.Entry<String, String>> fragmentTranslations(Context context) {
        List<Map.Entry<String, String>> cached = fragments;
        if (cached != null) return cached;
        synchronized (AppLanguage.class) {
            if (fragments != null) return fragments;
            List<Map.Entry<String, String>> result = new ArrayList<>();
            for (Map.Entry<String, String> entry : translations(context).entrySet()) {
                String value = entry.getKey();
                if (value.length() < 3 || value.indexOf('\n') >= 0) continue;
                boolean fragment = Character.isWhitespace(value.charAt(0))
                    || Character.isWhitespace(value.charAt(value.length() - 1))
                    || value.endsWith(":")
                    || value.endsWith(": ");
                if (fragment) result.add(entry);
            }
            result.sort(Comparator.comparingInt(
                (Map.Entry<String, String> entry) -> entry.getKey().length()).reversed());
            fragments = result;
            return result;
        }
    }
}
