package ru.drshapaya.androidft2;

import android.animation.ValueAnimator;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/** Short native transitions; cancelled sheets always return to a usable state. */
final class PanelTransitions {
    private PanelTransitions() { }

    static boolean enabled(View view) {
        return Build.VERSION.SDK_INT >= 26 ? ValueAnimator.areAnimatorsEnabled()
            : Settings.Global.getFloat(view.getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
    }

    static void reset(View view) {
        view.animate().cancel();
        view.animate().setStartDelay(0L).withEndAction(null);
        view.setAlpha(1f);
        view.setTranslationX(0f);
        view.setTranslationY(0f);
    }

    private static void resetRows(ViewGroup rows) {
        if (rows == null) return;
        for (int i = 0; i < rows.getChildCount(); i++) reset(rows.getChildAt(i));
    }

    static void enter(View view, boolean landscape, ViewGroup rows) {
        reset(view);
        resetRows(rows);
        view.setVisibility(View.VISIBLE);
        if (!enabled(view)) return;
        float density = view.getResources().getDisplayMetrics().density;
        view.setAlpha(0f);
        view.setTranslationX(landscape ? 14f * density : 0f);
        view.setTranslationY(landscape ? 0f : 18f * density);
        view.animate().alpha(1f).translationX(0f).translationY(0f)
            .setStartDelay(0L).setDuration(rows == null ? 300L : 360L)
            .setInterpolator(new DecelerateInterpolator(2f)).start();
        if (rows == null) return;
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            if (row.getVisibility() != View.VISIBLE) continue;
            row.setAlpha(0f);
            row.setTranslationY(12f * density);
            row.animate().alpha(1f).translationY(0f)
                .setStartDelay(Math.min(180L, i * 28L)).setDuration(300L)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
    }

    static void hide(View view, ViewGroup rows) {
        reset(view);
        resetRows(rows);
        view.setVisibility(View.GONE);
    }

    // The dim layer stays stationary; only the sheet above it moves.
    static void overlay(View overlay, View sheet, ViewGroup rows, boolean opening,
                        boolean landscape, Runnable onHidden) {
        boolean interrupted = overlay.getVisibility() == View.VISIBLE && overlay.getAlpha() < .999f;
        float alpha = overlay.getAlpha(), sheetAlpha = sheet.getAlpha();
        float x = sheet.getTranslationX(), y = sheet.getTranslationY();
        overlay.animate().cancel();
        overlay.animate().setStartDelay(0L).withEndAction(null);
        sheet.animate().cancel();
        sheet.animate().setStartDelay(0L).withEndAction(null);
        if (!enabled(overlay)) {
            reset(overlay);
            reset(sheet);
            resetRows(rows);
            overlay.setVisibility(opening ? View.VISIBLE : View.GONE);
            if (!opening && onHidden != null) onHidden.run();
            return;
        }
        float distance = 18f * overlay.getResources().getDisplayMetrics().density;
        if (opening) {
            overlay.setVisibility(View.VISIBLE);
            overlay.setAlpha(0f);
            enter(sheet, landscape, rows);
            if (interrupted) {
                overlay.setAlpha(alpha);
                sheet.setAlpha(sheetAlpha);
                sheet.setTranslationX(x);
                sheet.setTranslationY(y);
            }
            overlay.animate().alpha(1f).setDuration(260L)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
        } else {
            sheet.animate().translationX(landscape ? distance : 0f)
                .translationY(landscape ? 0f : distance).setDuration(190L).start();
            overlay.animate().alpha(0f).setDuration(190L)
                .withEndAction(() -> {
                    hide(overlay, rows);
                    reset(sheet);
                    if (onHidden != null) onHidden.run();
                }).start();
        }
    }

    static void popup(View menu, boolean fromBottom) {
        reset(menu);
        if (!enabled(menu)) return;
        menu.setAlpha(0f);
        menu.setTranslationY((fromBottom ? 16f : -10f)
            * menu.getResources().getDisplayMetrics().density);
        menu.setScaleX(.96f);
        menu.setScaleY(.96f);
        menu.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(280L).setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    static void exit(View view, ViewGroup rows) {
        view.animate().cancel();
        view.animate().setStartDelay(0L).withEndAction(null);
        if (view.getVisibility() != View.VISIBLE || !enabled(view)) {
            hide(view, rows);
            return;
        }
        view.animate().alpha(0f)
            .translationY(16f * view.getResources().getDisplayMetrics().density)
            .setDuration(190L).setInterpolator(new DecelerateInterpolator(2f))
            .withEndAction(() -> hide(view, rows)).start();
    }
}
