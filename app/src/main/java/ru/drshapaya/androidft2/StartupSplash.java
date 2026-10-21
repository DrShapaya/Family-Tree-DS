package ru.drshapaya.androidft2;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

/** Keeps the launch artwork in the existing activity while the tree loads. */
final class StartupSplash implements AutoCloseable {
    private static final String PREFS = "androidft-ui";
    private static final String THEME = "startup_theme";
    private static Boolean systemThemeDark;
    private final MainActivity activity;
    private final StartupSplashView view;
    private final boolean animated;
    private boolean closed;
    private View readyRoot;
    private ViewTreeObserver.OnPreDrawListener readyListener;
    private Runnable afterSplash;

    static boolean cachedDark(Context context) {
        String theme = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(THEME, "");
        if (!theme.isEmpty()) return "dark".equals(theme);
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
    }

    static void rememberTheme(Context context, String theme) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!theme.equals(prefs.getString(THEME, ""))) prefs.edit().putString(THEME, theme).apply();
        syncSystemTheme(context, "dark".equals(theme));
    }

    static void applyLaunchTheme(Activity activity) {
        boolean dark = cachedDark(activity);
        syncSystemTheme(activity, dark);
        activity.setTheme(dark ? R.style.AppLaunchThemeDark : R.style.AppLaunchTheme);
    }

    private static void syncSystemTheme(Context context, boolean dark) {
        if (Build.VERSION.SDK_INT < 31) return;
        if (systemThemeDark != null && systemThemeDark == dark) return;
        UiModeManager manager = context.getSystemService(UiModeManager.class);
        // Android creates its launch window before Activity.onCreate. Persist
        // the app's night mode so that window uses our cached launch palette too.
        if (manager != null) {
            manager.setApplicationNightMode(dark ? UiModeManager.MODE_NIGHT_YES : UiModeManager.MODE_NIGHT_NO);
            systemThemeDark = dark;
        }
    }

    static boolean shouldAnimate(Context context, Intent intent, Bundle savedState) {
        if (savedState != null || intent == null || !Intent.ACTION_MAIN.equals(intent.getAction())
            || !intent.hasCategory(Intent.CATEGORY_LAUNCHER)) return false;
        if (Build.VERSION.SDK_INT >= 26) return ValueAnimator.areAnimatorsEnabled();
        return Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
    }

    StartupSplash(MainActivity activity, Bundle savedState) {
        this.activity = activity;
        animated = shouldAnimate(activity, activity.getIntent(), savedState);
        view = new StartupSplashView(activity, animated, cachedDark(activity));
        if (Build.VERSION.SDK_INT >= 31) {
            // The system launch window uses the same plain backing. Remove it
            // at the first app frame, so the logo assembly is shown only once.
            activity.getSplashScreen().setOnExitAnimationListener(splash -> splash.remove());
        }
        activity.setContentView(view);
    }

    boolean isAnimated() { return animated; }

    StartupSplashView.Artwork prepareArtwork() {
        return StartupSplashView.Artwork.load(activity.getResources(), animated);
    }

    void setArtwork(StartupSplashView.Artwork artwork) {
        if (closed) { if (artwork != null) artwork.close(); return; }
        view.setArtwork(artwork);
    }

    void whenAssemblyFinished(Runnable initializeTree) {
        if (!closed) view.whenAssemblyFinished(initializeTree);
    }

    boolean installContent(View root) {
        if (closed) return false;
        ViewGroup content = activity.findViewById(android.R.id.content);
        content.addView(root, 0, new ViewGroup.LayoutParams(-1, -1));
        return true;
    }

    void onTreeReady(View target, Runnable afterSplash) {
        if (closed) { afterSplash.run(); return; }
        this.afterSplash = afterSplash;
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (!animated) {
            dismiss();
            return;
        }
        readyRoot = content;
        readyListener = () -> {
            removeReadyListener();
            // Header insets can request another layout after the first draw.
            // Read its destination after that traversal, while the splash stays attached.
            view.postOnAnimation(() -> {
                if (closed) return;
                readyRoot = content;
                readyListener = () -> {
                    removeReadyListener();
                    view.post(() -> { if (!closed) view.finishInto(target, this::dismiss); });
                    return true;
                };
                content.getViewTreeObserver().addOnPreDrawListener(readyListener);
                content.invalidate();
            });
            return true;
        };
        content.getViewTreeObserver().addOnPreDrawListener(readyListener);
        content.requestLayout();
    }

    void dismiss() {
        Runnable callback = afterSplash;
        close();
        if (callback != null) callback.run();
    }

    private void removeReadyListener() {
        if (readyRoot != null && readyListener != null && readyRoot.getViewTreeObserver().isAlive()) {
            readyRoot.getViewTreeObserver().removeOnPreDrawListener(readyListener);
        }
        readyRoot = null;
        readyListener = null;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        afterSplash = null;
        removeReadyListener();
        view.dispose();
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
    }
}
