package ru.drshapaya.androidft2;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.Consumer;

final class KinshipModeDialog {
    static final String HELP = "Эта настройка меняет только название родства. Люди, связи и расположение карточек остаются прежними.\n\n"
        + "Терминологический — привычные русские названия с учётом того, кто относится к старшему или младшему поколению: например, двоюродная племянница или тётя.\n\n"
        + "Параметрический — степень двоюродного родства и число поколений между людьми, с указанием старшего или младшего поколения. Так удобнее сравнивать дальних родственников.\n\n"
        + "Разница поколений считается в обе стороны по семейным связям, а не по возрасту и не по высоте карточки на экране. Выбранный режим действует в поиске и при просмотре родства.";

    static Dialog show(MainActivity activity, Consumer<Boolean> save) {
        Dialog dialog = new Dialog(activity); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = new LinearLayout(activity); shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(activity.dp(18),activity.dp(18),activity.dp(18),activity.dp(16));
        shell.setBackground(activity.panelBg(Color.WHITE,activity.dp(20),Color.rgb(217,224,229)));
        LinearLayout header = new LinearLayout(activity); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new LocalizedTextView(activity); title.setText("Степень родства");
        title.setTextSize(21); title.setTypeface(activity.uiBold()); title.setTextColor(activity.uiColor(Color.rgb(28,34,38)));
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button help = activity.actionButton("?",v -> activity.showHelp("Как отображается родство",HELP));
        help.setContentDescription(activity.tr("Как отображается родство")); help.setTag("kinship.help");
        header.addView(help,new LinearLayout.LayoutParams(activity.dp(46),activity.dp(46))); shell.addView(header);
        TextView hint = new LocalizedTextView(activity); hint.setText("Выберите удобный способ отображения");
        hint.setTextSize(13); hint.setTypeface(activity.ui()); hint.setTextColor(activity.uiColor(Color.rgb(101,113,122)));
        hint.setPadding(0,activity.dp(10),0,activity.dp(18)); shell.addView(hint);
        RadioGroup choices = new RadioGroup(activity); choices.setTag("kinship.choices");
        String[] labels = {"Терминологический", "Параметрический"};
        for (int i=0;i<labels.length;i++) {
            RadioButton option = new RadioButton(activity); option.setId(i==0 ? R.id.kinship_terminological : R.id.kinship_parametric); option.setTag("kinship.option."+i);
            option.setText(activity.tr(labels[i])); option.setTextSize(15); option.setTypeface(activity.uiBold());
            option.setTextColor(activity.uiColor(Color.rgb(28,34,38)));
            option.setButtonTintList(ColorStateList.valueOf(AppThemePalette.primary()));
            option.setPadding(activity.dp(12),activity.dp(14),activity.dp(12),activity.dp(14));
            RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(-1,-2); params.setMargins(0,0,0,activity.dp(10)); choices.addView(option,params);
        }
        Runnable restyle = () -> {
            for(int i=0;i<choices.getChildCount();i++) {
                RadioButton option = (RadioButton)choices.getChildAt(i);
                option.setBackground(activity.panelBg(option.isChecked() ? AppThemePalette.primarySurface() : Color.WHITE,
                    activity.dp(12),option.isChecked() ? AppThemePalette.primary() : Color.rgb(217,224,229)));
            }
        };
        choices.setOnCheckedChangeListener((group,id) -> restyle.run());
        choices.check(activity.kinshipTerminological ? R.id.kinship_terminological : R.id.kinship_parametric); restyle.run(); shell.addView(choices);
        LinearLayout actions = new LinearLayout(activity);
        actions.addView(activity.actionButton("Отмена",v -> dialog.dismiss()),new LinearLayout.LayoutParams(0,activity.dp(48),1));
        Button apply = activity.actionButton("Применить",v -> { save.accept(choices.getCheckedRadioButtonId()==R.id.kinship_terminological); dialog.dismiss(); });
        apply.setTag("kinship.apply"); apply.setTextColor(Color.WHITE); apply.setBackground(activity.tealGradientBg(activity.dp(10)));
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(0,activity.dp(48),1); applyParams.setMargins(activity.dp(10),0,0,0); actions.addView(apply,applyParams); shell.addView(actions);
        ScrollView scroll = new ScrollView(activity); scroll.addView(shell); dialog.setContentView(scroll); dialog.show();
        Window window = dialog.getWindow();
        if(window!=null) {
            int width=Math.min(activity.getResources().getDisplayMetrics().widthPixels-activity.dp(24),activity.dp(480));
            shell.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(0,android.view.View.MeasureSpec.UNSPECIFIED));
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(width,Math.min(shell.getMeasuredHeight(),(int)(activity.getResources().getDisplayMetrics().heightPixels*.85f)));
        }
        return dialog;
    }
}
