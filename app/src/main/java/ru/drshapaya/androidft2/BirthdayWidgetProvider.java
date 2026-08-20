package ru.drshapaya.androidft2;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BirthdayWidgetProvider extends AppWidgetProvider {
    static final String EXTRA_PERSON_ID = "birthday_widget_person_id";
    static final int SIZE_COMPACT = 1;
    static final int SIZE_REGULAR = 2;
    static final int SIZE_WIDE = 3;
    static final int SIZE_SQUARE = 4;

    private static final ExecutorService UPDATER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "birthday-widget-updater");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    int presetSize() {
        return SIZE_REGULAR;
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        updateAsync(context, manager, appWidgetIds, null, presetSize());
    }

    @Override
    public void onAppWidgetOptionsChanged(
        Context context,
        AppWidgetManager manager,
        int appWidgetId,
        Bundle newOptions
    ) {
        updateAsync(context, manager, new int[] {appWidgetId}, newOptions, presetSize());
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) BirthdayWidgetSettings.delete(context, appWidgetId);
    }

    static void update(Context context, int appWidgetId) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentPreset preset = presetForWidget(manager, context, appWidgetId);
        updateAsync(context, manager, new int[] {appWidgetId}, null, preset.size);
    }

    static void updateAll(Context context) {
        Context appContext = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        for (ComponentPreset preset : presets(appContext)) {
            int[] ids = manager.getAppWidgetIds(preset.component);
            if (ids.length > 0) updateAsync(appContext, manager, ids, null, preset.size);
        }
    }

    private static void updateAsync(
        Context context,
        AppWidgetManager manager,
        int[] appWidgetIds,
        Bundle optionsOverride,
        int presetSize
    ) {
        Context appContext = context.getApplicationContext();
        int[] ids = appWidgetIds == null ? new int[0] : appWidgetIds.clone();
        UPDATER.execute(() -> {
            TreeState state;
            try {
                state = new TreeStore(appContext).load();
            } catch (Exception error) {
                DiagnosticsLogger.handled(appContext, "widget.load", error);
                state = new TreeState();
            }
            for (int appWidgetId : ids) {
                try {
                    Bundle options = optionsOverride != null && ids.length == 1
                        ? optionsOverride
                        : manager.getAppWidgetOptions(appWidgetId);
                    int fallbackWidth = presetSize == SIZE_COMPACT
                        ? 180
                        : presetSize == SIZE_WIDE
                            ? 330
                            : presetSize == SIZE_SQUARE ? 180 : 250;
                    int fallbackHeight = presetSize == SIZE_COMPACT ? 74 : 155;
                    int width = option(options, AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, fallbackWidth);
                    int height = option(options, AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, fallbackHeight);
                    int layoutSize = height <= 96
                        ? SIZE_COMPACT
                        : width >= 285
                            ? SIZE_WIDE
                            : presetSize == SIZE_SQUARE && width < 220 ? SIZE_SQUARE : SIZE_REGULAR;
                    BirthdayWidgetSettings settings = BirthdayWidgetSettings.load(appContext, appWidgetId);
                    BirthdayCalculator.Result birthday = BirthdayCalculator.nearest(
                        state,
                        settings.excludedPersonIds);
                    RemoteViews views = new RemoteViews(appContext.getPackageName(), layoutFor(layoutSize));
                    bindViews(appContext, views, birthday, settings, appWidgetId, width, height, layoutSize);
                    manager.updateAppWidget(appWidgetId, views);
                } catch (Exception error) {
                    DiagnosticsLogger.handled(appContext, "widget.update", error);
                }
            }
        });
    }

    private static void bindViews(
        Context context,
        RemoteViews views,
        BirthdayCalculator.Result birthday,
        BirthdayWidgetSettings settings,
        int appWidgetId,
        int width,
        int height,
        int layoutSize
    ) {
        views.setImageViewBitmap(
            R.id.birthday_widget_background,
            BirthdayWidgetRenderer.renderBackground(context, birthday, settings, width, height));
        int primary = BirthdayWidgetRenderer.primaryTextColor(birthday, settings);
        int secondary = BirthdayWidgetRenderer.secondaryTextColor(birthday, settings);
        views.setTextViewText(R.id.birthday_widget_name, BirthdayWidgetRenderer.personName(context, birthday));
        views.setTextColor(R.id.birthday_widget_name, primary);

        if (layoutSize == SIZE_COMPACT) {
            views.setTextViewText(
                R.id.birthday_widget_countdown,
                BirthdayWidgetRenderer.compactCountdown(context, birthday));
            views.setTextColor(R.id.birthday_widget_countdown, secondary);
        } else {
            views.setTextViewText(
                R.id.birthday_widget_date,
                layoutSize == SIZE_SQUARE
                    ? BirthdayWidgetRenderer.shortDate(context, birthday)
                    : BirthdayWidgetRenderer.date(context, birthday));
            views.setTextViewText(R.id.birthday_widget_number, BirthdayWidgetRenderer.number(context, birthday));
            views.setTextViewText(R.id.birthday_widget_days_label, BirthdayWidgetRenderer.daysLabel(context, birthday));
            views.setTextColor(R.id.birthday_widget_date, secondary);
            views.setTextColor(R.id.birthday_widget_number, primary);
            views.setTextColor(R.id.birthday_widget_days_label, secondary);
        }

        Bitmap photo = settings.showPhoto
            ? BirthdayWidgetRenderer.renderPhoto(
                context,
                birthday,
                layoutSize == SIZE_COMPACT ? 58 : layoutSize == SIZE_SQUARE ? 70 : layoutSize == SIZE_WIDE ? 92 : 84)
            : null;
        if (photo == null) {
            views.setViewVisibility(R.id.birthday_widget_photo, View.GONE);
        } else {
            views.setViewVisibility(R.id.birthday_widget_photo, View.VISIBLE);
            views.setImageViewBitmap(R.id.birthday_widget_photo, photo);
        }
        if (layoutSize == SIZE_COMPACT) {
            views.setViewPadding(
                R.id.birthday_widget_text_container,
                dp(context, 17),
                dp(context, 6),
                dp(context, photo == null ? 17 : 76),
                dp(context, 6));
        }
        views.setOnClickPendingIntent(
            R.id.birthday_widget_root,
            openPersonIntent(context, appWidgetId, birthday));
    }

    private static PendingIntent openPersonIntent(
        Context context,
        int appWidgetId,
        BirthdayCalculator.Result birthday
    ) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("androidft://birthday-widget/" + appWidgetId));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (birthday != null && birthday.person != null) {
            intent.putExtra(EXTRA_PERSON_ID, birthday.person.id);
        }
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int layoutFor(int size) {
        if (size == SIZE_COMPACT) return R.layout.birthday_widget_compact;
        if (size == SIZE_SQUARE) return R.layout.birthday_widget_square;
        if (size == SIZE_WIDE) return R.layout.birthday_widget_wide;
        return R.layout.birthday_widget_regular;
    }

    private static ComponentPreset presetForWidget(
        AppWidgetManager manager,
        Context context,
        int appWidgetId
    ) {
        for (ComponentPreset preset : presets(context)) {
            for (int id : manager.getAppWidgetIds(preset.component)) {
                if (id == appWidgetId) return preset;
            }
        }
        return new ComponentPreset(new ComponentName(context, BirthdayWidgetProvider.class), SIZE_REGULAR);
    }

    private static List<ComponentPreset> presets(Context context) {
        List<ComponentPreset> result = new ArrayList<>();
        result.add(new ComponentPreset(new ComponentName(context, BirthdayWidgetCompactProvider.class), SIZE_COMPACT));
        result.add(new ComponentPreset(new ComponentName(context, BirthdayWidgetSquareProvider.class), SIZE_SQUARE));
        result.add(new ComponentPreset(new ComponentName(context, BirthdayWidgetRegularProvider.class), SIZE_REGULAR));
        result.add(new ComponentPreset(new ComponentName(context, BirthdayWidgetWideProvider.class), SIZE_WIDE));
        return result;
    }

    private static int option(Bundle options, String key, int fallback) {
        return options == null ? fallback : Math.max(1, options.getInt(key, fallback));
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class ComponentPreset {
        final ComponentName component;
        final int size;

        ComponentPreset(ComponentName component, int size) {
            this.component = component;
            this.size = size;
        }
    }
}
