package ru.drshapaya.androidft2;

import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Display preferences are applied live, just like other visual settings. */
final class LifeStatusAppearanceDialog {
    private static final String[] STATUSES = {PersonLifeStatus.LIVING, PersonLifeStatus.DECEASED, PersonLifeStatus.UNKNOWN};
    private final MainActivity activity;
    private final Dialog dialog;
    private final LifeStatusBadge renderer = new LifeStatusBadge();
    private final List<View> tabs = new ArrayList<>(), tiles = new ArrayList<>(), styles = new ArrayList<>();
    private final List<Preview> previews = new ArrayList<>();
    private final LinearLayout grid;
    private final ColorPickerView hue;
    private final EditText hex;
    private String status = PersonLifeStatus.LIVING;
    private boolean updating;

    static Dialog show(MainActivity activity) { return new LifeStatusAppearanceDialog(activity).dialog; }

    private LifeStatusAppearanceDialog(MainActivity activity) {
        this.activity = activity;
        dialog = new Dialog(activity); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        hex = new EditText(activity);
        LinearLayout shell = column(); shell.setTag("life.appearance");
        shell.setPadding(dp(16),dp(16),dp(16),dp(12));
        shell.setBackground(activity.panelBg(Color.WHITE,dp(20),Color.rgb(217,224,229)));
        shell.addView(text("Иконки статуса жизни",20,true));
        ScrollView scroll = new ScrollView(activity);
        LinearLayout body = column(); scroll.addView(body);
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView hint = text("Выберите статус, затем иконку и цвет. Изменения сразу видны на карточках.",12,false);
        hint.setPadding(0,dp(8),0,dp(12)); body.addView(hint);

        LinearLayout statusRow = new LinearLayout(activity);
        for(String value : STATUSES) {
            LinearLayout tab = column(); tab.setGravity(Gravity.CENTER); tab.setTag("life.status."+value);
            Preview preview = new Preview(value,null); previews.add(preview);
            tab.addView(preview,new LinearLayout.LayoutParams(-1,dp(54)));
            TextView label = text(PersonLifeStatus.label(value),13,true); label.setGravity(Gravity.CENTER);
            tab.addView(label,new LinearLayout.LayoutParams(-1,dp(26)));
            tab.setContentDescription(activity.tr(PersonLifeStatus.label(value))); tab.setFocusable(true);
            tab.setOnClickListener(v -> { hex.clearFocus(); status=value; rebuildGrid(); refresh(); });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,dp(86),1); params.setMargins(dp(2),0,dp(2),0);
            statusRow.addView(tab,params); tabs.add(tab);
        }
        body.addView(statusRow);
        body.addView(section("Иконки"));
        grid = column(); body.addView(grid);
        body.addView(section("Фон значка"));
        LinearLayout styleRow = new LinearLayout(activity);
        String[] styleIds={"solid","soft","plain"}, styleLabels={"Заливка","Светлый круг","Без фона"};
        for(int i=0;i<styleIds.length;i++) {
            final String id=styleIds[i]; Button button=activity.actionButton(styleLabels[i],v -> {
                appearance().style=id; changed();
            });
            button.setTextSize(11); button.setPadding(dp(3),0,dp(3),0); button.setTag("life.style."+id);
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(48),1); params.setMargins(dp(2),0,dp(2),0);
            styleRow.addView(button,params); styles.add(button);
        }
        body.addView(styleRow);
        body.addView(section("Цвет выбранного статуса"));
        hue = new ColorPickerView(activity); hue.setTag("life.colorPicker"); body.addView(hue,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout exact = new LinearLayout(activity); exact.setGravity(Gravity.CENTER_VERTICAL);
        exact.addView(text("HEX",12,true),new LinearLayout.LayoutParams(dp(42),dp(46)));
        hex.setTag("life.hex"); hex.setSingleLine(true); hex.setTextSize(15);
        hex.setTextColor(activity.uiColor(Color.rgb(28,34,38))); hex.setHint("#26836C");
        hex.setContentDescription(activity.tr("Точный цвет значка"));
        hex.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        exact.addView(hex,new LinearLayout.LayoutParams(0,dp(46),1)); body.addView(exact);
        hue.setListener((color,fromUser) -> {
            if (!fromUser) return;
            appearance().setColor(status,String.format(java.util.Locale.ROOT,"#%06X",color & 0xFFFFFF));
            hex.clearFocus(); changed();
        });
        hex.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) { }
            public void afterTextChanged(Editable value) {
                if(updating) return;
                String color=value.toString().trim();
                if(color.matches("#[0-9a-fA-F]{6}")) { hex.setError(null); appearance().setColor(status,color); changed(); }
                else if(color.length()>=7) hex.setError(activity.tr("Введите цвет в формате #RRGGBB"));
            }
        });
        LinearLayout footer = new LinearLayout(activity); footer.setPadding(0,dp(12),0,0);
        Button reset=activity.actionButton("По умолчанию",v -> {
            activity.state.lifeStatusAppearance=new LifeStatusAppearance(); hex.clearFocus(); changed();
        }); reset.setTag("life.reset"); reset.setTextSize(12);
        footer.addView(reset,new LinearLayout.LayoutParams(0,dp(48),1));
        Button done=activity.actionButton("Готово",v -> dialog.dismiss()); done.setTag("life.done");
        done.setTextColor(Color.WHITE); done.setBackground(activity.tealGradientBg(dp(10)));
        LinearLayout.LayoutParams doneParams=new LinearLayout.LayoutParams(0,dp(48),1); doneParams.setMargins(dp(8),0,0,0);
        footer.addView(done,doneParams); shell.addView(footer);
        rebuildGrid(); refresh();
        dialog.setContentView(shell); dialog.show();
        Window window=dialog.getWindow();
        if(window!=null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            window.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(24),dp(480)),
                (int)(activity.getResources().getDisplayMetrics().heightPixels*.85f));
        }
    }

    private void rebuildGrid() {
        grid.removeAllViews(); tiles.clear();
        // Only the three status previews persist when switching the catalog.
        while(previews.size()>3) previews.remove(previews.size()-1);
        String[][] icons=LifeStatusAppearance.icons(status);
        LinearLayout row=null;
        for(int i=0;i<icons.length;i++) {
            if(i%4==0) { row=new LinearLayout(activity); grid.addView(row); }
            final String id=icons[i][0];
            LinearLayout tile=column(); tile.setGravity(Gravity.CENTER); tile.setTag("life.icon."+id);
            Preview preview=new Preview(status,id); previews.add(preview);
            tile.addView(preview,new LinearLayout.LayoutParams(-1,dp(47)));
            TextView label=text(icons[i][1],10,false); label.setGravity(Gravity.CENTER); label.setMaxLines(2);
            tile.addView(label,new LinearLayout.LayoutParams(-1,dp(30)));
            tile.setContentDescription(activity.tr(icons[i][1])); tile.setFocusable(true);
            tile.setOnClickListener(v -> { appearance().setIcon(status,id); changed(); });
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(80),1); params.setMargins(dp(2),dp(2),dp(2),dp(2));
            row.addView(tile,params); tiles.add(tile);
        }
        if(row!=null) for(int i=icons.length%4;i>0 && i<4;i++) row.addView(new View(activity),new LinearLayout.LayoutParams(0,dp(80),1));
    }

    private void changed() { activity.saveOnly(); activity.treeView.invalidate(); refresh(); }

    private void refresh() {
        for(int i=0;i<tabs.size();i++) select(tabs.get(i),STATUSES[i].equals(status));
        for(View tile:tiles) select(tile,tile.getTag().equals("life.icon."+appearance().icon(status)));
        for(View style:styles) select(style,style.getTag().equals("life.style."+appearance().style));
        for(Preview preview:previews) preview.invalidate();
        hue.setColor(Color.parseColor(appearance().color(status)));
        if(!hex.hasFocus()) { updating=true; hex.setText(appearance().color(status)); hex.setError(null); updating=false; }
    }

    private void select(View view,boolean selected) {
        view.setSelected(selected); view.setBackground(activity.panelBg(selected ? AppThemePalette.primarySurface() : Color.WHITE,
            dp(10),selected ? AppThemePalette.primary() : Color.rgb(225,230,234)));
    }

    private LifeStatusAppearance appearance() { return activity.state.lifeStatusAppearance; }
    private int dp(int value) { return activity.dp(value); }
    private LinearLayout column() { LinearLayout v=new LinearLayout(activity); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView section(String title) { TextView v=text(title,14,true); v.setPadding(0,dp(12),0,dp(6)); return v; }
    private TextView text(String raw,int size,boolean bold) {
        TextView v=new LocalizedTextView(activity); v.setText(raw); v.setTextSize(size);
        v.setTypeface(bold ? activity.uiBold() : activity.ui()); v.setTextColor(activity.uiColor(Color.rgb(46,56,63))); return v;
    }

    private final class Preview extends View {
        private final String value, icon;
        private final LifeStatusAppearance previewAppearance = new LifeStatusAppearance();
        Preview(String value,String icon) { super(activity); this.value=value; this.icon=icon; }
        @Override protected void onDraw(Canvas canvas) {
            LifeStatusAppearance current=appearance();
            previewAppearance.setIcon(value,icon == null ? current.icon(value) : icon);
            previewAppearance.setColor(value,current.color(value)); previewAppearance.style=current.style;
            renderer.draw(canvas,value,previewAppearance,getWidth()/2f,getHeight()/2f,dp(34),1);
        }
    }
}
