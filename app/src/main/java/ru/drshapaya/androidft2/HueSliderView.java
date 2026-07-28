package ru.drshapaya.androidft2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

final class HueSliderView extends View {
    private static final float PALETTE_SATURATION = 0.58f;
    private static final float PALETTE_BRIGHTNESS = 0.94f;

    interface Listener {
        void onColorChanged(int color, boolean fromUser);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private float hue;
    private Listener listener;

    HueSliderView(Context context) {
        super(context);
        setFocusable(true);
        setClickable(true);
        setContentDescription("Выбор цвета");
        thumbStroke.setStyle(Paint.Style.STROKE);
        thumbStroke.setStrokeWidth(dp(3));
        thumbStroke.setColor(Color.WHITE);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    void setColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hue = hsv[0];
        invalidate();
    }

    int color() {
        return paletteColor(hue);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = resolveSize(dp(48), heightMeasureSpec);
        setMeasuredDimension(resolveSize(dp(220), widthMeasureSpec), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float pad = dp(12);
        float trackHeight = dp(18);
        float centerY = getHeight() / 2f;
        track.set(pad, centerY - trackHeight / 2f, getWidth() - pad, centerY + trackHeight / 2f);
        int[] colors = {
            paletteColor(0f),
            paletteColor(60f),
            paletteColor(120f),
            paletteColor(180f),
            paletteColor(240f),
            paletteColor(300f),
            paletteColor(360f)
        };
        trackPaint.setShader(new LinearGradient(track.left, 0, track.right, 0, colors, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(track, trackHeight / 2f, trackHeight / 2f, trackPaint);
        trackPaint.setShader(null);

        float x = track.left + (hue / 360f) * track.width();
        thumbPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setColor(color());
        thumbPaint.setShadowLayer(dp(3), 0, dp(1), Color.argb(96, 0, 0, 0));
        canvas.drawCircle(x, centerY, dp(11), thumbPaint);
        canvas.drawCircle(x, centerY, dp(11), thumbStroke);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
            || event.getActionMasked() == MotionEvent.ACTION_MOVE
            || event.getActionMasked() == MotionEvent.ACTION_UP) {
            float pad = dp(12);
            float width = Math.max(1f, getWidth() - pad * 2f);
            hue = Math.max(0f, Math.min(360f, ((event.getX() - pad) / width) * 360f));
            invalidate();
            if (listener != null) listener.onColorChanged(color(), true);
            if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int paletteColor(float hue) {
        return Color.HSVToColor(new float[]{
            hue,
            PALETTE_SATURATION,
            PALETTE_BRIGHTNESS
        });
    }
}
