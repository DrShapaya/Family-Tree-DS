package ru.drshapaya.androidft2;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

/** Read-only, scrollable summary; definitions remain visible alongside the numbers. */
final class MainActivityStatistics {
    static Dialog show(MainActivity activity, String referenceId) {
        TreeState state = activity.state;
        TreeStatistics stats = TreeStatistics.calculate(state, referenceId);
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = column(activity);
        shell.setPadding(activity.dp(18), activity.dp(16), activity.dp(18), activity.dp(12));
        shell.setBackground(activity.panelBg(Color.WHITE, activity.dp(18), Color.rgb(217, 224, 229)));
        TextView statisticsTitle = text(activity, "Статистика дерева", 21, true);
        statisticsTitle.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_menu_statistics, 0, 0, 0);
        statisticsTitle.setCompoundDrawablePadding(activity.dp(8));
        activity.tintDrawables(statisticsTitle, AppThemePalette.secondary());
        shell.addView(statisticsTitle);
        LinearLayout tabs = new LinearLayout(activity);
        android.widget.Button numbers = activity.actionButton("Показатели",null);
        android.widget.Button diagrams = activity.actionButton("Диаграммы",null);
        numbers.setTag("statistics.numbers"); diagrams.setTag("statistics.charts");
        tabs.addView(numbers,new LinearLayout.LayoutParams(0,activity.dp(46),1));
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0,activity.dp(46),1); tabParams.setMargins(activity.dp(8),0,0,0); tabs.addView(diagrams,tabParams);
        shell.addView(tabs);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        LinearLayout content = column(activity);
        content.setPadding(0, activity.dp(12), 0, activity.dp(12));
        LinearLayout pages = column(activity);
        LinearLayout charts = column(activity); charts.setVisibility(android.view.View.GONE);
        pages.addView(content); pages.addView(charts); scroll.addView(pages);
        java.util.function.Consumer<Boolean> page = chart -> {
            content.setVisibility(chart ? android.view.View.GONE : android.view.View.VISIBLE);
            charts.setVisibility(chart ? android.view.View.VISIBLE : android.view.View.GONE);
            numbers.setBackground(activity.panelBg(chart ? Color.WHITE : AppThemePalette.primarySurface(),activity.dp(10),chart ? Color.rgb(217,224,229) : AppThemePalette.primary()));
            diagrams.setBackground(activity.panelBg(chart ? AppThemePalette.primarySurface() : Color.WHITE,activity.dp(10),chart ? AppThemePalette.primary() : Color.rgb(217,224,229)));
            scroll.scrollTo(0,0);
        };
        numbers.setOnClickListener(v -> page.accept(false)); diagrams.setOnClickListener(v -> page.accept(true)); page.accept(false);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        metric(activity, content, "Всего человек", String.valueOf(stats.people));
        metric(activity, content, "С фотографией", String.valueOf(stats.photos[0]));
        metric(activity, content, "С биографией", String.valueOf(stats.peopleWithNotes));
        metric(activity, content, "Без семейных связей", String.valueOf(stats.isolatedPeople));
        Person reference = state == null ? null : state.people.get(referenceId);
        content.addView(text(activity, "Родство", 17, true));
        if (reference != null) {
            TextView name = text(activity, "", 13, false);
            LocalizedViews.setRaw(name, AppLanguage.translateFully(activity, "Относительно: ") + reference.name);
            content.addView(name);
            metric(activity, content, "Близкие родственники", String.valueOf(stats.closeRelatives));
            metric(activity, content, "Дальние родственники", String.valueOf(stats.distantRelatives));
            metric(activity, content, "Другие семейные связи", String.valueOf(stats.familyConnections));
            metric(activity, content, "Родство не установлено", String.valueOf(stats.unrelated));
            content.addView(text(activity,
                "Близкие: родители, дети, братья и сёстры, дедушки и бабушки, внуки. Остальные родственники — дальние. "
                + "Другие семейные связи — например, супруги и их родня. Расчёт основан на записанных связях; выбранный человек не входит в эти группы.", 12, false));
        } else {
            content.addView(text(activity, "Выберите карточку или задайте корень дерева для расчёта родства.", 13, false));
        }
        if (state != null && state.people.containsKey(state.selectedId) && !state.selectedId.equals(referenceId)) {
            content.addView(activity.actionButton("Относительно выбранной карточки", v -> {
                dialog.dismiss(); show(activity, state.selectedId);
            }));
        }
        if (state != null && state.people.containsKey(state.rootId) && !state.rootId.equals(referenceId)) {
            content.addView(activity.actionButton("Относительно корня дерева", v -> {
                dialog.dismiss(); show(activity, state.rootId);
            }));
        }
        content.addView(text(activity, "Продолжительность жизни", 17, true));
        metric(activity, content, "Средняя, лет", stats.lifeSamples == 0 ? "—" : number(activity, stats.averageLife));
        metric(activity, content, "Максимальная, лет", stats.lifeSamples == 0 ? "—" : String.valueOf(stats.maximumLife));
        metric(activity, content, "Человек с датами жизни", String.valueOf(stats.lifeSamples));
        metric(activity, content, "Из них с неполными датами", String.valueOf(stats.approximateLifeSamples));
        content.addView(text(activity, "Только люди с датами рождения и смерти. Неполные даты дают приблизительный возраст по годам; ошибочные даты исключены.", 12, false));
        content.addView(text(activity, "Дети", 17, true));
        metric(activity, content, "Среднее число детей", stats.parentSamples == 0 ? "—" : number(activity, stats.averageChildren));
        metric(activity, content, "Максимальное число детей", stats.parentSamples == 0 ? "—" : String.valueOf(stats.maximumChildren));
        metric(activity, content, "Родителей с детьми в дереве", String.valueOf(stats.parentSamples));
        content.addView(text(activity, "Среднее рассчитано среди людей, у которых записан хотя бы один ребёнок. Учитываются только связи в этом дереве, повторные связи не увеличивают число детей.", 12, false));
        buildCharts(activity, charts, stats, reference);
        shell.addView(activity.actionButton("Закрыть", v -> dialog.dismiss()));
        dialog.setContentView(shell);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels - activity.dp(24), activity.dp(520)),
                (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.85f));
        }
        return dialog;
    }

    private static void buildCharts(MainActivity a, LinearLayout host, TreeStatistics s, Person reference) {
        host.addView(text(a,"Нажмите на сектор или подпись, чтобы увидеть число и долю.",12,false));
        if (reference != null) {
            chart(a,host,"Родство",new String[]{"Близкие родственники","Дальние родственники","Другие семейные связи","Родство не установлено"},
                new int[]{s.closeRelatives,s.distantRelatives,s.familyConnections,s.unrelated},s.people);
            host.addView(text(a,"Доли родства рассчитаны без человека, относительно которого строится статистика.",12,false));
        }
        chart(a,host,"Пол",new String[]{"Мужчины","Женщины","Не указан"},s.genders);
        chart(a,host,"Годы рождения",new String[]{"До 1900","1900–1949","1950–1999","С 2000","Нет корректной даты"},s.birthPeriods);
        chart(a,host,"Дети в дереве",new String[]{"Дети отсутствуют","Один ребёнок","Двое детей","Трое детей","Четверо и более"},s.childrenDistribution);
        chart(a,host,"Фотографии",new String[]{"С фотографией","Без фотографии"},s.photos);
        host.addView(text(a,"Диаграммы описывают заполненные данные дерева. Отсутствие записи о детях не означает, что детей не было. Пол может быть определён приложением по имени.",12,false));
    }

    private static void chart(MainActivity a, LinearLayout host, String title, String[] labels, int[] counts) {
        chart(a,host,title,labels,counts,-1);
    }
    private static void chart(MainActivity a, LinearLayout host, String title, String[] labels, int[] counts,int displayedTotal) {
        LinearLayout card=column(a); card.setPadding(a.dp(14),a.dp(10),a.dp(14),a.dp(12));
        card.setTag("statistics.chart."+title);
        card.setBackground(a.panelBg(Color.WHITE,a.dp(16),Color.rgb(217,224,229)));
        card.addView(text(a,title,18,true));
        StatisticsDonutView donut=new StatisticsDonutView(a,labels,counts,displayedTotal); card.addView(donut,new LinearLayout.LayoutParams(-1,a.dp(210)));
        TextView selection=text(a,"Все группы",13,true); selection.setGravity(android.view.Gravity.CENTER); card.addView(selection);
        int total=0; for(int count:counts) total+=count;
        for(int i=0;i<labels.length;i++) {
            final int index=i;
            LinearLayout row=new LinearLayout(a); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(a.dp(44)); row.setPadding(a.dp(4),0,a.dp(4),0);
            android.view.View dot=new android.view.View(a); dot.setBackground(a.panelBg(StatisticsDonutView.COLORS[i%StatisticsDonutView.COLORS.length],a.dp(99),Color.TRANSPARENT));
            row.addView(dot,new LinearLayout.LayoutParams(a.dp(10),a.dp(10)));
            TextView label=text(a,labels[i],13,false); label.setPadding(a.dp(10),a.dp(5),a.dp(4),a.dp(5)); row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            String value=counts[i]+" · "+number(a,total==0?0:100.0*counts[i]/total)+"%";
            row.addView(text(a,value,13,true)); row.setOnClickListener(v -> donut.select(index));
            row.setContentDescription(a.tr(labels[i])+": "+value); card.addView(row);
        }
        donut.setSelectionListener(index -> selection.setText(index<0 ? a.tr("Все группы") : a.tr(labels[index])));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.setMargins(0,a.dp(12),0,0); host.addView(card,params);
    }

    private static String number(MainActivity activity, double value) {
        return String.format(Locale.forLanguageTag(AppLanguage.resolvedLanguage(activity)), "%.1f", value);
    }

    private static LinearLayout column(MainActivity activity) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private static TextView text(MainActivity activity, String label, int size, boolean bold) {
        TextView view = new LocalizedTextView(activity);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(activity.uiColor(bold ? Color.rgb(28, 34, 38) : Color.rgb(101, 113, 122)));
        view.setTypeface(bold ? activity.uiBold() : activity.ui());
        view.setPadding(0, activity.dp(6), 0, activity.dp(6));
        return view;
    }

    private static void metric(MainActivity activity, LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dp(12), activity.dp(4), activity.dp(12), activity.dp(4));
        row.setBackground(activity.panelBg(Color.rgb(243, 248, 249), activity.dp(10), Color.TRANSPARENT));
        row.addView(text(activity, label, 14, false), new LinearLayout.LayoutParams(0, -2, 1));
        TextView number = text(activity, value, 22, true);
        number.setPadding(activity.dp(12), activity.dp(6), 0, activity.dp(6));
        row.addView(number, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, activity.dp(6));
        parent.addView(row, params);
    }
}
