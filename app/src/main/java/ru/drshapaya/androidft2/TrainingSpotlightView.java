package ru.drshapaya.androidft2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

final class TrainingSpotlightView extends View {
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path dimPath = new Path();
    private final Path arrowPath = new Path();
    private final RectF target = new RectF();
    private final RectF badge = new RectF();
    private final RectF pulsingTarget = new RectF();
    private Runnable targetAction;
    private String targetLabel = "";
    private boolean interactive;
    private long lastTargetTap;

    TrainingSpotlightView(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        dimPaint.setColor(Color.argb(166, 9, 21, 26));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(3));
        ringPaint.setColor(Color.rgb(83, 211, 197));
        badgePaint.setColor(Color.rgb(8, 122, 115));
        badgeTextPaint.setColor(Color.WHITE);
        badgeTextPaint.setTextSize(sp(11));
        badgeTextPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        badgeTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    void showTarget(RectF bounds, boolean canTap, String label, Runnable action) {
        target.set(bounds == null ? new RectF() : bounds);
        interactive = canTap;
        targetLabel = label == null ? "" : label;
        targetAction = action;
        setContentDescription(interactive ? targetLabel : null);
        invalidate();
    }

    void clearTarget() {
        target.setEmpty();
        interactive = false;
        targetAction = null;
        targetLabel = "";
        setContentDescription(null);
        invalidate();
    }

    void disableTargetTap() {
        interactive = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        dimPath.reset();
        dimPath.setFillType(Path.FillType.EVEN_ODD);
        dimPath.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        if (!target.isEmpty()) {
            dimPath.addRoundRect(target, dp(16), dp(16), Path.Direction.CW);
        }
        canvas.drawPath(dimPath, dimPaint);
        if (target.isEmpty()) return;

        float pulse = (float) ((Math.sin(SystemClock.uptimeMillis() / 190.0) + 1.0) * 0.5);
        float spread = dp(2) + dp(3) * pulse;
        pulsingTarget.set(target);
        pulsingTarget.inset(-spread, -spread);
        ringPaint.setAlpha(170 + Math.round(85 * pulse));
        canvas.drawRoundRect(pulsingTarget, dp(18), dp(18), ringPaint);

        if (!targetLabel.isEmpty()) drawTargetBadge(canvas);
        postInvalidateDelayed(32L);
    }

    private void drawTargetBadge(Canvas canvas) {
        float badgeHeight = dp(32);
        float badgeWidth = Math.max(dp(112), badgeTextPaint.measureText(targetLabel) + dp(28));
        badgeWidth = Math.min(badgeWidth, Math.max(dp(112), getWidth() - dp(24)));
        float centerX = clamp(target.centerX(), dp(12) + badgeWidth / 2f, getWidth() - dp(12) - badgeWidth / 2f);
        boolean above = target.top >= dp(54);
        float top = above ? target.top - badgeHeight - dp(14) : target.bottom + dp(14);
        top = clamp(top, dp(8), getHeight() - badgeHeight - dp(8));
        badge.set(centerX - badgeWidth / 2f, top, centerX + badgeWidth / 2f, top + badgeHeight);
        canvas.drawRoundRect(badge, dp(16), dp(16), badgePaint);

        Paint.FontMetrics metrics = badgeTextPaint.getFontMetrics();
        float baseline = badge.centerY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(targetLabel, badge.centerX(), baseline, badgeTextPaint);

        float tipY = above ? target.top - dp(3) : target.bottom + dp(3);
        float baseY = above ? badge.bottom : badge.top;
        arrowPath.reset();
        arrowPath.moveTo(target.centerX(), tipY);
        arrowPath.lineTo(clamp(target.centerX() - dp(8), badge.left + dp(10), badge.right - dp(10)), baseY);
        arrowPath.lineTo(clamp(target.centerX() + dp(8), badge.left + dp(10), badge.right - dp(10)), baseY);
        arrowPath.close();
        canvas.drawPath(arrowPath, badgePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            if (interactive && target.contains(event.getX(), event.getY())) {
                long now = SystemClock.uptimeMillis();
                if (now - lastTargetTap > 450L) {
                    lastTargetTap = now;
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if (targetAction != null) targetAction.run();
                }
            } else {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
