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
    static final int HUE=0, SATURATION=1, BRIGHTNESS=2;
    private static final int TRACK_SEGMENTS = 72;
    private static final int[] TRACK_COLORS = new int[TRACK_SEGMENTS + 1];
    private static final float[] TRACK_POSITIONS = new float[TRACK_SEGMENTS + 1];

    static {
        for (int index = 0; index <= TRACK_SEGMENTS; index++) {
            float position = index / (float) TRACK_SEGMENTS;
            TRACK_POSITIONS[index] = position;
            TRACK_COLORS[index] = paletteColor(position * 360f);
        }
    }

    interface Listener {
        void onColorChanged(int color, boolean fromUser);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private final float[] hsv={0f,1f,1f};
    private final int component;
    private Listener listener;

    HueSliderView(Context context) {
        this(context,HUE);
    }

    HueSliderView(Context context,int component) {
        super(context);
        this.component=component;
        setFocusable(true);
        setClickable(true);
        setContentDescription(AppLanguage.translate(context,component==HUE ? "Оттенок" : component==SATURATION ? "Насыщенность" : "Яркость"));
        thumbStroke.setStyle(Paint.Style.STROKE);
        thumbStroke.setStrokeWidth(dp(3));
        thumbStroke.setColor(Color.WHITE);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    void setColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        if(hsv[1]==0 || hsv[2]==0) hsv[0]=this.hsv[0];
        setHsv(hsv);
    }

    void setHsv(float[] value) {
        System.arraycopy(value,0,hsv,0,3);
        invalidate();
    }

    void getHsv(float[] value) { System.arraycopy(hsv,0,value,0,3); }

    int color() {
        return Color.HSVToColor(hsv);
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
        trackPaint.setShader(component==HUE ? new LinearGradient(
            track.left,
            0,
            track.right,
            0,
            TRACK_COLORS,
            TRACK_POSITIONS,
            Shader.TileMode.CLAMP) : new LinearGradient(track.left,0,track.right,0,
                component==SATURATION ? Color.HSVToColor(new float[]{hsv[0],0,hsv[2]}) : Color.BLACK,
                component==SATURATION ? Color.HSVToColor(new float[]{hsv[0],1,hsv[2]}) : Color.HSVToColor(new float[]{hsv[0],hsv[1],1}),
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(track, trackHeight / 2f, trackHeight / 2f, trackPaint);
        trackPaint.setShader(null);

        float x = track.left + (component==HUE ? hsv[0]/360f : hsv[component]) * track.width();
        thumbPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setColor(color());
        thumbPaint.setShadowLayer(dp(3), 0, dp(1), Color.argb(96, 0, 0, 0));
        canvas.drawCircle(x, centerY, dp(11), thumbPaint);
        canvas.drawCircle(x, centerY, dp(11), thumbStroke);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        if(event.getActionMasked()==MotionEvent.ACTION_CANCEL) { if(getParent()!=null) getParent().requestDisallowInterceptTouchEvent(false); return true; }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN
            || event.getActionMasked() == MotionEvent.ACTION_MOVE
            || event.getActionMasked() == MotionEvent.ACTION_UP) {
            float pad = dp(12);
            float width = Math.max(1f, getWidth() - pad * 2f);
            hsv[component]=Math.max(0f,Math.min(1f,(event.getX()-pad)/width)) * (component==HUE ? 360f : 1f);
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN && getParent()!=null) getParent().requestDisallowInterceptTouchEvent(true);
            invalidate();
            if (listener != null) listener.onColorChanged(color(), true);
            if (event.getActionMasked() == MotionEvent.ACTION_UP) { if(getParent()!=null) getParent().requestDisallowInterceptTouchEvent(false); performClick(); }
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
            1f,
            1f
        });
    }
}
