package ru.drshapaya.androidft2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Each sector is selectable; the adjacent legend provides the same action accessibly. */
final class StatisticsDonutView extends View {
    static final int[] COLORS = {0xff168a83,0xff6875cf,0xffdfa34b,0xffbe6f96,0xff8b9ca5};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] values;
    private final String[] labels;
    private final int total;
    private final int displayedTotal;
    private int selected = -1;
    private IntConsumer listener;
    private float radius, cx, cy;

    StatisticsDonutView(Context context, String[] labels, int[] values) {
        this(context,labels,values,-1);
    }
    StatisticsDonutView(Context context, String[] labels, int[] values,int displayedTotal) {
        super(context); this.labels = labels.clone(); this.values = values.clone();
        int sum = 0; for(int value:values) sum += value; total = sum;
        this.displayedTotal=displayedTotal<0 ? total : displayedTotal;
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        setMinimumHeight(dp(210)); setClickable(true);
        setContentDescription(AppLanguage.text(context,"Круговая диаграмма. Выберите сектор или строку легенды."));
    }
    void setSelectionListener(IntConsumer listener) { this.listener = listener; }
    void select(int index) { selected = selected == index ? -1 : index; if(listener!=null) listener.accept(selected); invalidate(); }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); cx=getWidth()/2f; cy=getHeight()/2f; radius=Math.min(getWidth(),getHeight())*.38f;
        float stroke=radius*.26f;
        RectF oval=new RectF(cx-radius,cy-radius,cx+radius,cy+radius);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(stroke); paint.setColor(AppThemePalette.stroke(Color.rgb(230,237,240)));
        canvas.drawOval(oval,paint);
        float start=-90;
        for(int i=0;i<values.length && total>0;i++) {
            float sweep=360f*values[i]/total; if(sweep<=0) continue;
            paint.setColor(COLORS[i%COLORS.length]); paint.setAlpha(selected<0||selected==i ? 255:75);
            paint.setStrokeWidth(selected==i ? stroke*1.2f : stroke);
            float gap = Math.min(2f,sweep*.15f);
            canvas.drawArc(oval,start+gap/2,sweep-gap,false,paint); start+=sweep;
        }
        paint.setAlpha(255); paint.setStyle(Paint.Style.FILL); paint.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(dp(34)); paint.setColor(AppThemePalette.text(Color.rgb(28,34,38)));
        canvas.drawText(String.valueOf(selected<0?displayedTotal:values[selected]),cx,cy+dp(5),paint);
        paint.setTextSize(dp(13)); paint.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL));
        paint.setColor(AppThemePalette.text(Color.rgb(101,113,122)));
        String caption=selected<0 ? AppLanguage.text(getContext(),displayedTotal==0 ? "Нет данных" : "Всего человек")
            : String.format(Locale.forLanguageTag(AppLanguage.resolvedLanguage(getContext())),"%.1f%%",total==0?0:100.0*values[selected]/total);
        canvas.drawText(caption,cx,cy+dp(29),paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(event.getAction()==MotionEvent.ACTION_DOWN) return true;
        if(event.getAction()==MotionEvent.ACTION_UP) {
            performClick();
            float x=event.getX()-cx,y=event.getY()-cy;
            double distance=Math.hypot(x,y);
            if(total>0 && distance>radius*.7 && distance<radius*1.3) {
                double angle=(Math.toDegrees(Math.atan2(y,x))+450)%360, boundary=0;
                for(int i=0;i<values.length;i++) { boundary+=360.0*values[i]/total; if(angle<boundary){select(i);break;} }
            } else { selected=-1; if(listener!=null)listener.accept(-1); invalidate(); }
            return true;
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
