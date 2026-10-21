package ru.drshapaya.androidft2;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.function.IntConsumer;

/** Compact themed dialogs with a scrollable body and permanently reachable actions. */
final class AppDialog extends Dialog {
    private final Activity activity;
    private final MainUiKit ui;
    private final LinearLayout shell, body, actions;
    private View bodyView;
    private int preferredBodyHeight=ViewGroup.LayoutParams.WRAP_CONTENT;

    static AppDialog create(MainActivity activity,int icon,String title) {
        return new AppDialog(activity,new MainUiKit(activity,() -> activity.uiScale),icon,title);
    }

    AppDialog(Activity activity,MainUiKit ui,int icon,String title) {
        super(activity);
        this.activity=activity; this.ui=ui;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        shell=column(); shell.setTag("appDialog.shell");
        shell.setPadding(ui.dp(18),ui.dp(18),ui.dp(18),ui.dp(16));
        shell.setBackground(ui.panelBg(Color.WHITE,ui.dp(22),Color.argb(56,63,82,94)));
        LinearLayout header=new LinearLayout(activity); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0,0,0,ui.dp(12));
        header.addView(mark(icon),new LinearLayout.LayoutParams(ui.dp(44),ui.dp(44)));
        TextView heading=text(title,18,true); heading.setTag("appDialog.title");
        heading.setPadding(ui.dp(10),0,ui.dp(8),0);
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=ui.closeButton(v -> cancel()); close.setTag("appDialog.close");
        close.setContentDescription(AppLanguage.translate(activity,"Закрыть")); close.setFocusable(true);
        header.addView(close,new LinearLayout.LayoutParams(ui.dp(44),ui.dp(44))); shell.addView(header);
        body=column(); body.setPadding(0,ui.dp(4),0,ui.dp(4));
        ScrollView scroll=new ScrollView(activity); scroll.setFillViewport(false); scroll.addView(body);
        bodyView=scroll; shell.addView(bodyView,new LinearLayout.LayoutParams(-1,-2));
        actions=new LinearLayout(activity); actions.setPadding(0,ui.dp(14),0,0);
        shell.addView(actions,new LinearLayout.LayoutParams(-1,-2));
        setContentView(shell); setCanceledOnTouchOutside(true);
    }

    AppDialog message(String message) {
        if(message!=null && !message.isEmpty()) {
            TextView detail=text(message,14,false); detail.setTag("appDialog.message");
            detail.setTextColor(AppThemePalette.text(Color.rgb(83,94,103))); detail.setLineSpacing(ui.dp(3),1f);
            detail.setPadding(0,0,0,ui.dp(12)); body.addView(detail);
        }
        return this;
    }

    AppDialog content(View content) { body.addView(content); return this; }

    AppDialog choice(int icon,String title,String detail,Runnable action) {
        LinearLayout row=itemRow(new Item(icon,title,detail));
        row.setFocusable(true); row.setOnClickListener(v -> { dismiss(); action.run(); });
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.setMargins(0,ui.dp(4),0,ui.dp(4));
        row.setTag("appDialog.choice."+body.getChildCount()); body.addView(row,params); return this;
    }

    AppDialog progress(String message) {
        LinearLayout status=new LinearLayout(activity); status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));
        status.setBackground(ui.panelBg(AppThemePalette.primarySurface(),ui.dp(12),Color.TRANSPARENT));
        ProgressBar spinner=new ProgressBar(activity); spinner.setTag("appDialog.progress");
        spinner.setIndeterminateTintList(ColorStateList.valueOf(AppThemePalette.primary()));
        status.addView(spinner,new LinearLayout.LayoutParams(ui.dp(28),ui.dp(28)));
        TextView label=text(message,13,false); label.setPadding(ui.dp(12),0,0,0);
        status.addView(label,new LinearLayout.LayoutParams(0,-2,1)); body.addView(status);
        setCanceledOnTouchOutside(false); return this;
    }

    AppDialog action(String label,boolean primary,Runnable action) {
        Button button=ui.actionButton(label,v -> { dismiss(); if(action!=null) action.run(); });
        button.setTag("appDialog.action."+actions.getChildCount());
        button.setTextColor(primary ? Color.WHITE : AppThemePalette.text(Color.rgb(28,34,38)));
        button.setMinHeight(ui.dp(48)); button.setPadding(ui.dp(10),ui.dp(10),ui.dp(10),ui.dp(10));
        if(primary) button.setBackground(ui.tealGradientBg(ui.dp(12)));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1); params.setMargins(actions.getChildCount()>0 ? ui.dp(8):0,0,0,0);
        actions.addView(button,params); return this;
    }

    AppDialog items(List<Item> entries,IntConsumer chosen) {
        shell.removeView(bodyView);
        ListView list=new ListView(activity); list.setTag("appDialog.items");
        list.setDivider(new ColorDrawable(Color.TRANSPARENT)); list.setDividerHeight(ui.dp(8));
        list.setPadding(0,ui.dp(4),0,ui.dp(4)); list.setClipToPadding(false);
        list.setAdapter(new BaseAdapter() {
            public int getCount() { return entries.size(); }
            public Object getItem(int position) { return entries.get(position); }
            public long getItemId(int position) { return position; }
            public View getView(int position,View recycled,ViewGroup parent) {
                LinearLayout row=recycled instanceof LinearLayout ? (LinearLayout)recycled : itemRow(entries.get(position));
                bindRow(row,entries.get(position)); row.setTag("appDialog.item."+position); return row;
            }
        });
        list.setOnItemClickListener((parent,view,position,id) -> { dismiss(); chosen.accept(position); });
        ((ViewGroup)body.getParent()).removeView(body);
        LinearLayout listBody=column(); listBody.addView(body,new LinearLayout.LayoutParams(-1,-2));
        listBody.addView(list,new LinearLayout.LayoutParams(-1,0,1)); bodyView=listBody;
        // The list recycles rows even in folders with thousands of files.
        preferredBodyHeight=ui.dp(Math.min(416,Math.max(180,entries.size()*86+60)));
        shell.addView(bodyView,1,new LinearLayout.LayoutParams(-1,preferredBodyHeight));
        return this;
    }

    @Override public void show() {
        if(activity.isFinishing() || activity.isDestroyed()) return;
        bodyView.setLayoutParams(new LinearLayout.LayoutParams(-1,preferredBodyHeight));
        int width=Math.min(activity.getResources().getDisplayMetrics().widthPixels-ui.dp(24),ui.dp(520));
        shell.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int height=Math.min(shell.getMeasuredHeight(),Math.round(activity.getResources().getDisplayMetrics().heightPixels*.85f));
        bodyView.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1));
        super.show(); Window window=getWindow();
        if(window!=null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.48f); window.setLayout(width,height);
        }
    }

    private LinearLayout itemRow(Item item) {
        LinearLayout row=new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(12),ui.dp(12),ui.dp(10),ui.dp(12)); row.setMinimumHeight(ui.dp(76));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(AppThemePalette.alpha(AppThemePalette.primary(),28)),
            ui.panelBg(Color.rgb(248,251,252),ui.dp(14),Color.rgb(225,231,235)),null));
        row.addView(mark(item.icon),new LinearLayout.LayoutParams(ui.dp(42),ui.dp(42)));
        LinearLayout copy=column(); copy.setPadding(ui.dp(12),0,ui.dp(8),0);
        TextView title=text(item.title,14,true); title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END); copy.addView(title);
        TextView detail=text(item.detail,11,false); detail.setTextColor(AppThemePalette.text(Color.rgb(101,113,122)));
        detail.setPadding(0,ui.dp(3),0,0); copy.addView(detail); row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        ImageView arrow=new ImageView(activity); arrow.setImageResource(R.drawable.ic_menu_chevron_right);
        arrow.setColorFilter(AppThemePalette.text(Color.rgb(101,113,122))); row.addView(arrow,new LinearLayout.LayoutParams(ui.dp(18),ui.dp(18)));
        return row;
    }

    private void bindRow(LinearLayout row,Item item) {
        ImageView icon=(ImageView)row.getChildAt(0); icon.setImageResource(item.icon);
        LinearLayout copy=(LinearLayout)row.getChildAt(1);
        ((TextView)copy.getChildAt(0)).setText(item.title); ((TextView)copy.getChildAt(1)).setText(item.detail);
        copy.getChildAt(1).setVisibility(item.detail.isEmpty() ? View.GONE : View.VISIBLE);
        row.setContentDescription(item.title+". "+AppLanguage.translate(activity,item.detail));
    }

    private ImageView mark(int icon) {
        ImageView mark=new ImageView(activity); mark.setImageResource(icon);
        mark.setColorFilter(icon==R.drawable.ic_menu_warning ? AppThemePalette.text(Color.rgb(180,94,40)) : AppThemePalette.primary());
        mark.setPadding(ui.dp(10),ui.dp(10),ui.dp(10),ui.dp(10));
        mark.setBackground(icon==R.drawable.ic_menu_warning ? ui.panelBg(Color.rgb(255,246,226),ui.dp(14),Color.rgb(234,211,164))
            : ui.softAccentGradientBg(ui.dp(14))); return mark;
    }

    private LinearLayout column() { LinearLayout v=new LinearLayout(activity); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(String value,int size,boolean bold) {
        TextView text=new LocalizedTextView(activity); text.setText(value); text.setTextSize(size);
        text.setTypeface(bold ? ui.uiBold() : ui.ui()); text.setTextColor(AppThemePalette.text(Color.rgb(28,34,38)));
        text.setIncludeFontPadding(false); return text;
    }

    static final class Item {
        final int icon; final String title,detail;
        Item(int icon,String title,String detail) { this.icon=icon; this.title=title; this.detail=detail; }
    }
}
