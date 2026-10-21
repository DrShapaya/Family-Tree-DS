package ru.drshapaya.androidft2;

import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.BooleanSupplier;

/** Shared full HSV picker for cards, guides and status icons. */
final class ColorPickerView extends LinearLayout {
    static final int[] PRESETS={Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW,0xFF800080,0xFFFFA500,Color.BLACK,Color.WHITE};
    private static final String[] NAMES={"Красный","Зелёный","Синий","Жёлтый","Фиолетовый","Оранжевый","Чёрный","Белый"};
    private static final String[] IDS={"red","green","blue","yellow","purple","orange","black","white"};
    private final MainActivity activity;
    private final HueSliderView[] sliders=new HueSliderView[3];
    private final View[] presets=new View[8];
    private final float[] hsv={0,1,1};
    private HueSliderView.Listener listener;
    private BooleanSupplier interactionGuard;
    private boolean blocked;

    ColorPickerView(MainActivity activity) {
        super(activity); this.activity=activity; setOrientation(VERTICAL);
        String[] labels={"Оттенок","Насыщенность","Яркость"};
        for(int i=0;i<3;i++) {
            TextView label=text(labels[i]); label.setPadding(dp(8),dp(3),0,0); addView(label,new LayoutParams(-1,dp(20)));
            HueSliderView slider=new HueSliderView(activity,i); sliders[i]=slider;
            slider.setTag("color."+(i==0 ? "hue" : i==1 ? "saturation" : "brightness"));
            slider.setHsv(hsv); addView(slider,new LayoutParams(-1,dp(40)));
            slider.setListener((color,fromUser) -> {
                slider.getHsv(hsv); sync(); if(listener!=null) listener.onColorChanged(color,fromUser);
            });
        }
        for(int rowIndex=0;rowIndex<2;rowIndex++) {
            LinearLayout row=new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL);
            for(int col=0;col<4;col++) {
                final int index=rowIndex*4+col;
                LinearLayout preset=new LinearLayout(activity); preset.setOrientation(VERTICAL); preset.setGravity(Gravity.CENTER);
                preset.setPadding(dp(2),dp(3),dp(2),dp(3)); preset.setTag("color.preset."+IDS[index]);
                View swatch=new View(activity);
                android.graphics.drawable.GradientDrawable fill=new android.graphics.drawable.GradientDrawable();
                fill.setColor(PRESETS[index]); fill.setCornerRadius(dp(99)); fill.setStroke(dp(1),Color.rgb(173,182,190));
                swatch.setBackground(fill); preset.addView(swatch,new LayoutParams(dp(18),dp(18)));
                TextView name=text(NAMES[index]); name.setTextSize(9); name.setPadding(0,dp(3),0,0);
                name.setGravity(Gravity.CENTER); name.setSingleLine(true);
                if(android.os.Build.VERSION.SDK_INT>=26) name.setAutoSizeTextTypeUniformWithConfiguration(7,9,1,android.util.TypedValue.COMPLEX_UNIT_SP);
                else name.setTextSize(8);
                preset.addView(name,new LayoutParams(-1,-2));
                preset.setContentDescription(activity.tr(NAMES[index])); preset.setFocusable(true);
                preset.setOnClickListener(v -> {
                    if(interactionGuard!=null && !interactionGuard.getAsBoolean()) return;
                    setColor(PRESETS[index]); if(listener!=null) listener.onColorChanged(color(),true);
                });
                LayoutParams params=new LayoutParams(0,dp(52),1); params.setMargins(dp(2),dp(2),dp(2),dp(2));
                row.addView(preset,params); presets[index]=preset;
            }
            addView(row,new LayoutParams(-1,-2));
        }
        sync();
    }

    void setColor(int color) {
        // Keep HSV choices at black/white when a live preview feeds the same RGB back.
        if(color==color()) return;
        float previousHue=hsv[0]; Color.colorToHSV(color,hsv);
        if(hsv[1]==0 || hsv[2]==0) hsv[0]=previousHue;
        sync();
    }
    int color() { return Color.HSVToColor(hsv); }
    void setListener(HueSliderView.Listener listener) { this.listener=listener; }
    void setInteractionGuard(BooleanSupplier guard) { interactionGuard=guard; }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN) blocked=interactionGuard!=null && !interactionGuard.getAsBoolean();
        return blocked || super.onInterceptTouchEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event) { return blocked || super.onTouchEvent(event); }

    private void sync() {
        for(HueSliderView slider:sliders) if(slider!=null) slider.setHsv(hsv);
        for(int i=0;i<presets.length;i++) if(presets[i]!=null) {
            boolean selected=color()==PRESETS[i]; presets[i].setSelected(selected);
            presets[i].setBackground(activity.panelBg(selected ? AppThemePalette.primarySurface() : Color.WHITE,dp(8),
                selected ? AppThemePalette.primary() : Color.rgb(217,224,229)));
        }
    }
    private int dp(int value) { return activity.dp(value); }
    private TextView text(String value) {
        TextView label=new LocalizedTextView(activity); label.setText(value); label.setTextSize(11);
        label.setTypeface(activity.ui()); label.setTextColor(activity.uiColor(Color.rgb(83,94,103))); return label;
    }
}
