package ru.drshapaya.androidft2;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Supplier;

/**
 * Shared programmatic-view primitives used by feature controllers.
 *
 * <p>Keeping these details here lets feature code depend on a small UI surface
 * instead of reaching through the main screen for every styled view.</p>
 */
final class MainUiKit {
    private final Activity activity;
    private final Supplier<Float> uiScaleProvider;
    private Typeface regularTypeface;
    private Typeface boldTypeface;

    MainUiKit(Activity activity, Supplier<Float> uiScaleProvider) {
        this.activity = activity;
        this.uiScaleProvider = uiScaleProvider;
    }

    int dp(int value) {
        Float scale = uiScaleProvider.get();
        float normalizedScale = normalizeUiScale(scale == null ? 1f : scale);
        return Math.round(
            value * activity.getResources().getDisplayMetrics().density * normalizedScale);
    }

    Typeface ui() {
        if (regularTypeface == null) {
            regularTypeface = loadTypeface("fonts/NotoSans-Regular.ttf", Typeface.NORMAL);
        }
        return regularTypeface;
    }

    Typeface uiBold() {
        if (boldTypeface == null) {
            boldTypeface = loadTypeface("fonts/NotoSans-Bold.ttf", Typeface.BOLD);
        }
        return boldTypeface;
    }

    GradientDrawable panelBg(int color, int radius, int stroke) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(AppThemePalette.surface(color));
        background.setCornerRadius(radius);
        background.setStroke(dp(1), AppThemePalette.stroke(stroke));
        return background;
    }

    GradientDrawable tealGradientBg(int radius) {
        GradientDrawable background = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{AppThemePalette.ctaPrimary(), AppThemePalette.ctaSecondary()});
        background.setCornerRadius(radius);
        return background;
    }

    Button actionButton(String text, View.OnClickListener listener) {
        Button button = new LocalizedButton(activity);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(uiBold());
        button.setTextColor(Color.rgb(28, 34, 38));
        button.setOnClickListener(listener);
        button.setStateListAnimator(null);
        button.setElevation(0f);
        button.setIncludeFontPadding(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(44));
        button.setMinWidth(dp(44));
        button.setBackground(panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        button.setPadding(dp(10), 0, dp(10), 0);
        return button;
    }

    void tintDrawables(TextView view, int color) {
        int resolvedColor = AppThemePalette.text(color);
        for (Drawable drawable : view.getCompoundDrawables()) {
            if (drawable != null) drawable.mutate().setTint(resolvedColor);
        }
    }

    LinearLayout editorDialogShell() {
        LinearLayout shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(12), dp(12), dp(12), dp(12));
        shell.setBackground(panelBg(
            Color.WHITE,
            dp(20),
            Color.argb(56, 63, 82, 94)));
        return shell;
    }

    View editorDialogHeader(
        Dialog dialog,
        int icon,
        String titleText,
        String subtitleText
    ) {
        LinearLayout top = new LinearLayout(activity);
        top.setGravity(Gravity.CENTER_VERTICAL);

        ImageView mark = new ImageView(activity);
        mark.setImageResource(icon);
        mark.setColorFilter(AppThemePalette.text(Color.rgb(8, 122, 115)));
        mark.setPadding(dp(9), dp(9), dp(9), dp(9));
        mark.setBackground(panelBg(
            Color.rgb(232, 248, 246),
            dp(999),
            Color.TRANSPARENT));
        top.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout heading = new LinearLayout(activity);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(10), 0, dp(6), 0);

        TextView title = new LocalizedTextView(activity);
        title.setText(titleText);
        title.setTextSize(17);
        title.setTextColor(AppThemePalette.text(Color.rgb(28, 34, 38)));
        title.setTypeface(uiBold());
        heading.addView(title, new LinearLayout.LayoutParams(-1, dp(25)));

        TextView subtitle = new LocalizedTextView(activity);
        subtitle.setText(subtitleText);
        subtitle.setTextSize(10);
        subtitle.setTextColor(AppThemePalette.text(Color.rgb(101, 113, 122)));
        subtitle.setTypeface(ui());
        heading.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(19)));
        top.addView(heading, new LinearLayout.LayoutParams(0, dp(44), 1));
        top.addView(closeButton(v -> dialog.dismiss()), new LinearLayout.LayoutParams(dp(40), dp(40)));
        return top;
    }

    void showEditorDialog(Dialog dialog, View shell) {
        dialog.setContentView(shell);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) return;
        int width = Math.min(
            activity.getResources().getDisplayMetrics().widthPixels - dp(20),
            dp(640));
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.width = width;
        attributes.height = Math.min(
            Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.92f),
            dp(900));
        attributes.dimAmount = 0.48f;
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setAttributes(attributes);
    }

    TextView closeButton(View.OnClickListener listener) {
        TextView button = new LocalizedTextView(activity);
        button.setGravity(Gravity.CENTER);
        button.setBackground(panelBg(Color.WHITE, dp(8), Color.rgb(217, 224, 229)));
        button.setForeground(centeredIcon(R.drawable.ic_menu_close, Color.rgb(28, 34, 38)));
        button.setForegroundGravity(Gravity.CENTER);
        button.setOnClickListener(listener);
        return button;
    }

    TextView actionTitle(String value, boolean danger) {
        TextView text = new LocalizedTextView(activity);
        text.setText(value);
        text.setTextColor(
            danger ? Color.rgb(197, 83, 75) : Color.rgb(28, 34, 38));
        text.setTextSize(13);
        text.setTypeface(uiBold());
        text.setSingleLine(true);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setIncludeFontPadding(false);
        return text;
    }

    TextView actionDetail(String value, boolean danger) {
        TextView text = new LocalizedTextView(activity);
        text.setText(value);
        text.setTextColor(
            danger ? Color.rgb(173, 91, 84) : Color.rgb(101, 113, 122));
        text.setTextSize(9);
        text.setSingleLine(true);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setIncludeFontPadding(false);
        return text;
    }

    TextView sectionLabel(String value) {
        TextView section = new LocalizedTextView(activity);
        section.setText(value);
        section.setTextColor(Color.rgb(101, 113, 122));
        section.setTextSize(9);
        section.setTypeface(uiBold());
        section.setGravity(Gravity.LEFT | Gravity.BOTTOM);
        section.setPadding(dp(8), 0, 0, dp(5));
        section.setIncludeFontPadding(false);
        return section;
    }

    GradientDrawable softAccentGradientBg(int radius) {
        GradientDrawable background = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{
                AppThemePalette.primarySurface(),
                AppThemePalette.surface(Color.rgb(250, 248, 255)),
                AppThemePalette.secondarySurface()
            });
        background.setCornerRadius(radius);
        background.setStroke(
            dp(1),
            AppThemePalette.alpha(AppThemePalette.secondaryBright(), 54));
        return background;
    }

    GradientDrawable qualityGradientBg(int radius) {
        GradientDrawable background = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{
                Color.rgb(220, 249, 232),
                Color.rgb(250, 247, 255),
                Color.rgb(231, 218, 255)
            });
        background.setCornerRadius(radius);
        background.setStroke(dp(1), Color.argb(110, 122, 91, 177));
        return background;
    }

    private Drawable centeredIcon(int iconRes, int tint) {
        Drawable icon = activity.getDrawable(iconRes);
        if (icon == null) return null;
        icon = icon.mutate();
        icon.setTint(AppThemePalette.text(tint));
        return icon;
    }

    private Typeface loadTypeface(String assetPath, int fallbackStyle) {
        try {
            return Typeface.createFromAsset(activity.getAssets(), assetPath);
        } catch (RuntimeException ignored) {
            return Typeface.create("sans-serif", fallbackStyle);
        }
    }

    private static float normalizeUiScale(float value) {
        if (!Float.isFinite(value)) return 1f;
        return Math.max(0.9f, Math.min(1.2f, value));
    }
}
