package ru.drshapaya.androidft2;

import android.widget.RadioGroup;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Button;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;

/** Exercises the actual dialogs without persisting the synthetic family tree. */
public final class KinshipStatisticsUiTest extends InstrumentationTestCase {
    private MainActivity activity;
    private TreeState original;
    private String language;
    private boolean hadMode, originalMode, hadTraining, originalTraining;
    private Dialog dialog;

    @Override protected void setUp() throws Exception {
        super.setUp();
        Context context = getInstrumentation().getTargetContext();
        language = AppLanguage.mode(context);
        AppLanguage.setMode(context, AppLanguage.RUSSIAN);
        SharedPreferences prefs = context.getSharedPreferences("androidft-ui", Context.MODE_PRIVATE);
        hadMode = prefs.contains("kinship_terminological");
        originalMode = prefs.getBoolean("kinship_terminological", true);
        hadTraining = prefs.contains("training_offer_handled");
        originalTraining = prefs.getBoolean("training_offer_handled", false);
        prefs.edit().putBoolean("training_offer_handled", true).commit();
        Intent intent = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = (MainActivity) getInstrumentation().startActivitySync(intent);
        TestStartup.await(getInstrumentation(), activity);
        getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(() -> {
            original = activity.state;
            TreeState state = new TreeState();
            for (String id : new String[]{"a", "b", "c"}) {
                Person p = new Person(id); p.name = id.equals("a") ? "Иванова Анна Петровна" : id.equals("b") ? "Иванова Мария" : "Иванов Иван";
                p.x=id.equals("a")?200f:id.equals("b")?0f:400f; p.y=id.equals("a")?0f:300f;
                state.people.put(id, p);
            }
            state.rootId = "a"; state.selectedId = "b";
            state.addRelation("parent", "a", "b"); state.addRelation("parent", "a", "c");
            state.people.get("a").bornYear = "1940"; state.people.get("a").diedYear = "2000";
            activity.state = state;
            activity.bindState();
        });
    }

    @Override protected void tearDown() throws Exception {
        if (activity != null) {
            getInstrumentation().runOnMainSync(() -> {
                if (dialog != null) dialog.dismiss();
                activity.state = original;
                activity.saveOnly();
                activity.finish();
            });
            getInstrumentation().waitForIdleSync();
            SharedPreferences.Editor edit = getInstrumentation().getTargetContext().getSharedPreferences("androidft-ui", Context.MODE_PRIVATE).edit();
            if (hadMode) edit.putBoolean("kinship_terminological", originalMode); else edit.remove("kinship_terminological");
            if (hadTraining) edit.putBoolean("training_offer_handled", originalTraining); else edit.remove("training_offer_handled");
            edit.commit();
        }
        AppLanguage.setMode(getInstrumentation().getTargetContext(), language);
        super.tearDown();
    }

    public void testLifeStatusIconPickerAppliesLiveAndReopensWithSavedChoices() throws Exception {
        getInstrumentation().runOnMainSync(() -> dialog=LifeStatusAppearanceDialog.show(activity));
        getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(() -> {
            View decor=dialog.getWindow().getDecorView();
            decor.findViewWithTag("life.icon.sprout").performClick();
            decor.findViewWithTag("life.style.soft").performClick();
            ((EditText)decor.findViewWithTag("life.hex")).setText("#0C7844");
        });
        assertEquals("sprout",activity.state.lifeStatusAppearance.livingIcon);
        assertEquals("soft",activity.state.lifeStatusAppearance.style);
        assertEquals("#0C7844",activity.state.lifeStatusAppearance.livingColor);
        getInstrumentation().waitForIdleSync(); capture("life-icon-picker-living.png");
        getInstrumentation().runOnMainSync(() -> {
            View decor=dialog.getWindow().getDecorView();
            decor.findViewWithTag("life.status.deceased").performClick();
            decor.findViewWithTag("life.icon.ribbon").performClick();
            decor.findViewWithTag("color.preset.black").performClick();
        });
        assertEquals("ribbon",activity.state.lifeStatusAppearance.deceasedIcon);
        assertEquals("#000000",activity.state.lifeStatusAppearance.deceasedColor);
        getInstrumentation().waitForIdleSync(); capture("life-icon-picker-deceased.png");
        TreeState restored=activity.store.parse(activity.store.exportText(activity.state));
        assertEquals(activity.state.lifeStatusAppearance.encode(),restored.lifeStatusAppearance.encode());
        getInstrumentation().runOnMainSync(() -> {
            dialog.dismiss(); dialog=LifeStatusAppearanceDialog.show(activity);
        });
        getInstrumentation().waitForIdleSync();
        boolean[] checks=new boolean[3];
        getInstrumentation().runOnMainSync(() -> {
            View decor=dialog.getWindow().getDecorView();
            checks[0]=decor.findViewWithTag("life.icon.sprout").isSelected();
            checks[1]=decor.findViewWithTag("life.style.soft").isSelected();
            checks[2]=decor.findViewWithTag("life.done").getGlobalVisibleRect(new android.graphics.Rect());
            decor.findViewWithTag("life.reset").performClick();
        });
        assertTrue(checks[0]); assertTrue(checks[1]); assertTrue("Done stays reachable",checks[2]);
        assertEquals(new LifeStatusAppearance().encode(),activity.state.lifeStatusAppearance.encode());
    }

    public void testFullColorPickerHasExactSpectrumAndEightStandardPresets() throws Exception {
        ColorPickerView[] picker={null};
        checkedMain(() -> {
            picker[0]=new ColorPickerView(activity);
            picker[0].setListener((color,fromUser) -> picker[0].setColor(color));
            dialog=AppDialog.create(activity,R.drawable.ic_menu_paint_can,"Выбор цвета").content(picker[0]).action("Готово",true,null);
            dialog.show();
        });getInstrumentation().waitForIdleSync();
        checkedMain(() -> {
            picker[0].findViewWithTag("color.preset.green").performClick();
            tapSlider(picker[0].findViewWithTag("color.hue"),0); assertEquals(android.graphics.Color.RED,picker[0].color());
            tapSlider(picker[0].findViewWithTag("color.hue"),1); assertEquals(android.graphics.Color.RED,picker[0].color());
            tapSlider(picker[0].findViewWithTag("color.brightness"),0); assertEquals(android.graphics.Color.BLACK,picker[0].color());
            tapSlider(picker[0].findViewWithTag("color.brightness"),1); assertEquals(android.graphics.Color.RED,picker[0].color());
            tapSlider(picker[0].findViewWithTag("color.saturation"),0); assertEquals(android.graphics.Color.WHITE,picker[0].color());
            String[] ids={"red","green","blue","yellow","purple","orange","black","white"};
            for(int i=0;i<ids.length;i++) {
                picker[0].findViewWithTag("color.preset."+ids[i]).performClick(); assertEquals(ColorPickerView.PRESETS[i],picker[0].color());
            }
            picker[0].setColor(0xFF1F83E6); assertEquals(0xFF1F83E6,picker[0].color());
        });getInstrumentation().waitForIdleSync();capture("full-color-picker.png");
    }

    public void testCardAndGenerationColorsUseSharedPickerAndKeepBlackAndWhite() throws Exception {
        getInstrumentation().runOnMainSync(activity::openPersonEditor);getInstrumentation().waitForIdleSync();
        java.lang.reflect.Field editorField=MainActivity.class.getDeclaredField("editorModule");editorField.setAccessible(true);
        java.lang.reflect.Field current=MainActivityEditor.class.getDeclaredField("currentPersonDialog");current.setAccessible(true);
        dialog=(Dialog)current.get(editorField.get(activity));
        checkedMain(() -> {
            ColorPickerView picker=(ColorPickerView)dialog.getWindow().getDecorView().findViewWithTag("person.colorPicker");
            assertNotNull(picker); ((View)picker.getParent().getParent()).setVisibility(View.VISIBLE);
            picker.findViewWithTag("color.preset.black").performClick();
            assertEquals(android.graphics.Color.BLACK,TreeState.parseColor(activity.state.people.get("b").manualColor,0));
            picker.findViewWithTag("color.preset.white").performClick();
            assertEquals(android.graphics.Color.WHITE,TreeState.parseColor(activity.state.people.get("b").manualColor,0));
        });getInstrumentation().waitForIdleSync();
        checkedMain(() -> {
            ColorPickerView picker=(ColorPickerView)dialog.getWindow().getDecorView().findViewWithTag("person.colorPicker");
            picker.requestRectangleOnScreen(new android.graphics.Rect(0,0,picker.getWidth(),picker.getHeight()),true);
        });getInstrumentation().waitForIdleSync();capture("person-full-color-picker.png");
        java.lang.reflect.Method guide=MainActivity.class.getDeclaredMethod("guideColorRow");guide.setAccessible(true);
        java.lang.reflect.Field guideColor=MainActivity.class.getDeclaredField("guideDraftColor");guideColor.setAccessible(true);
        checkedMain(() -> {
            try {
                dialog.dismiss(); View row=(View)guide.invoke(activity);
                dialog=AppDialog.create(activity,R.drawable.ic_menu_paint_can,"Цвет линии").content(row).action("Готово",true,null);dialog.show();
                ColorPickerView picker=(ColorPickerView)row.findViewWithTag("guide.colorPicker");
                picker.findViewWithTag("color.preset.red").performClick();
                assertEquals(android.graphics.Color.RED,TreeState.parseColor((String)guideColor.get(activity),0));
                picker.findViewWithTag("color.preset.white").performClick();
                assertEquals(android.graphics.Color.WHITE,TreeState.parseColor((String)guideColor.get(activity),0));
            }catch(Exception e) { throw new AssertionError(e); }
        });getInstrumentation().waitForIdleSync();capture("guide-full-color-picker.png");
    }

    public void testQualityScrollMovesOnlyProblemsAndKeepsSummaryVisible() throws Exception {
        java.lang.reflect.Field quality=MainActivity.class.getDeclaredField("qualityModule");quality.setAccessible(true);
        int[] before=new int[2];
        checkedMain(() -> {
            try {
                for(int i=0;i<40;i++) { Person p=new Person("issue"+i);p.name="Иванов Иван";p.bornYear="2050";p.diedYear="1900";activity.state.people.put(p.id,p); }
                dialog=((MainActivityQuality)quality.get(activity)).showTreeDialog();
            }catch(Exception e) { throw new AssertionError(e); }
        });getInstrumentation().waitForIdleSync();
        checkedMain(() -> {
            View decor=dialog.getWindow().getDecorView();
            ScrollView scroll=(ScrollView)decor.findViewWithTag("quality.issues");
            ViewGroup problems=(ViewGroup)scroll.getChildAt(0);
            ((ViewGroup)problems.getChildAt(0)).getChildAt(0).performClick();
            decor.findViewWithTag("quality.score").getLocationOnScreen(before);
        });getInstrumentation().waitForIdleSync();capture("quality-fixed-summary-top.png");
        checkedMain(() -> {
            ScrollView scroll=(ScrollView)dialog.getWindow().getDecorView().findViewWithTag("quality.issues");
            assertTrue(scroll.canScrollVertically(1)); scroll.scrollTo(0,scroll.getChildAt(0).getHeight());
        });getInstrumentation().waitForIdleSync();capture("quality-fixed-summary-bottom.png");
        checkedMain(() -> {
            int[] after=new int[2]; View decor=dialog.getWindow().getDecorView();
            View score=decor.findViewWithTag("quality.score");score.getLocationOnScreen(after);
            assertEquals(before[1],after[1]); assertTrue(score.getGlobalVisibleRect(new android.graphics.Rect()));
            assertTrue(decor.findViewWithTag("quality.header").getGlobalVisibleRect(new android.graphics.Rect()));
            assertTrue(decor.findViewWithTag("quality.metrics").getGlobalVisibleRect(new android.graphics.Rect()));
        });
    }

    public void testLegacyJsonNamesMigrateRawAndPreviouslySplitForms() throws Exception {
        org.json.JSONObject raw=new org.json.JSONObject(activity.store.exportText(activity.state));
        org.json.JSONObject person=raw.getJSONObject("people").getJSONObject("b");
        person.put("name","Иванова (Соколова) Анна Петровна").put("namePartsSet",false).put("maidenName","");
        Person first=activity.store.parse(raw.toString()).people.get("b");
        assertEquals("Анна",first.givenName);assertEquals("Соколова",first.maidenName);
        person.put("namePartsSet",true).put("surname","Иванова").put("givenName","(Соколова)").put("patronymic","Анна Петровна");
        Person repaired=activity.store.parse(raw.toString()).people.get("b");
        assertEquals("Анна",repaired.givenName);assertEquals("Петровна",repaired.patronymic);assertEquals("Соколова",repaired.maidenName);
        TreeState again=activity.store.parse(activity.store.exportText(activity.store.parse(raw.toString())));
        assertEquals("Иванова Анна Петровна",again.people.get("b").name);
    }

    public void testKinshipChartExcludesReferenceFromSectorsButKeepsOverallTotal() throws Exception {
        checkedMain(() -> {
            Person stranger=new Person("stranger");stranger.name="Другой человек";activity.state.people.put(stranger.id,stranger);
            dialog=MainActivityStatistics.show(activity,"a");
            dialog.getWindow().getDecorView().findViewWithTag("statistics.charts").performClick();
        });getInstrumentation().waitForIdleSync();
        checkedMain(() -> {
            View decor=dialog.getWindow().getDecorView();
            ViewGroup card=(ViewGroup)decor.findViewWithTag("statistics.chart.Родство");
            assertNull(findText(card,"Выбранный человек"));
            assertNotNull(findText(card,"2 · 66,7%"));assertNotNull(findText(card,"1 · 33,3%"));
            StatisticsDonutView donut=(StatisticsDonutView)card.getChildAt(1);
            try {
                java.lang.reflect.Field shown=StatisticsDonutView.class.getDeclaredField("displayedTotal");shown.setAccessible(true);
                java.lang.reflect.Field denominator=StatisticsDonutView.class.getDeclaredField("total");denominator.setAccessible(true);
                assertEquals(4,shown.getInt(donut));assertEquals(3,denominator.getInt(donut));
            }catch(Exception e) { throw new AssertionError(e); }
            assertNotNull(findText(decor,"Дети отсутствуют"));assertNull(findText(decor,"Не записаны"));
        });capture("statistics-kinship-without-reference.png");
        checkedMain(() -> {
            View card=dialog.getWindow().getDecorView().findViewWithTag("statistics.chart.Дети в дереве");
            card.requestRectangleOnScreen(new android.graphics.Rect(0,0,card.getWidth(),card.getHeight()),true);
        });getInstrumentation().waitForIdleSync();capture("statistics-children-clarified.png");
    }

    public void testSinglePersonKinshipChartDoesNotAddSelfOrDivideByZero() throws Exception {
        checkedMain(() -> {
            activity.state.people.remove("b");activity.state.people.remove("c");activity.state.links.clear();
            dialog=MainActivityStatistics.show(activity,"a");
            dialog.getWindow().getDecorView().findViewWithTag("statistics.charts").performClick();
        });getInstrumentation().waitForIdleSync();
        checkedMain(() -> {
            ViewGroup card=(ViewGroup)dialog.getWindow().getDecorView().findViewWithTag("statistics.chart.Родство");
            assertNull(findText(card,"Выбранный человек"));assertNotNull(findText(card,"0 · 0,0%"));
            ((StatisticsDonutView)card.getChildAt(1)).select(0);
            assertNotNull(findText(card,"Близкие родственники"));
        });
    }

    private void checkedMain(Runnable action) {
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> { try { action.run(); }catch(Throwable e) { failure.set(e); } });
        if(failure.get()!=null) throw new AssertionError(failure.get());
    }

    private void tapSlider(View view,float position) {
        float pad=12f*activity.getResources().getDisplayMetrics().density;
        float x=pad+(view.getWidth()-2f*pad)*position,y=view.getHeight()/2f;
        long now=android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,x,y,0);
        android.view.MotionEvent up=android.view.MotionEvent.obtain(now,now+1,android.view.MotionEvent.ACTION_UP,x,y,0);
        view.dispatchTouchEvent(down);view.dispatchTouchEvent(up);down.recycle();up.recycle();
    }

    public void testStatisticsScrollsAndShowsSampleCounts() throws Exception {
        getInstrumentation().runOnMainSync(() -> dialog = MainActivityStatistics.show(activity, "a"));
        getInstrumentation().waitForIdleSync();
        boolean[] checks = new boolean[4];
        getInstrumentation().runOnMainSync(() -> {
            View decor = dialog.getWindow().getDecorView();
            checks[0] = findText(decor, "Статистика дерева") != null;
            checks[1] = findText(decor, "Родителей с детьми в дереве") != null;
            checks[2] = findText(decor, "Закрыть") != null;
            ScrollView scroll = findScroll(decor);
            checks[3] = scroll != null && scroll.canScrollVertically(1);
        });
        assertTrue("Title", checks[0]);
        assertTrue("Child sample size", checks[1]);
        assertTrue("Close button", checks[2]);
        assertTrue("All metric groups must remain reachable on a phone", checks[3]);
        capture("statistics-top.png");
        getInstrumentation().runOnMainSync(() -> findScroll(dialog.getWindow().getDecorView()).fullScroll(View.FOCUS_DOWN));
        getInstrumentation().waitForIdleSync();
        capture("statistics-bottom.png");
    }

    public void testStatisticsIsAccessibleFromMoreAndRemovedFromSettings() throws Exception {
        checkedMain(() -> {
            android.widget.LinearLayout settings = new android.widget.LinearLayout(activity);
            activity.addSettingsContent(settings);
            assertNull(findText(settings, "Статистика дерева"));
            activity.showPanel("more");
        });
        getInstrumentation().waitForIdleSync();
        checkedMain(() -> {
            TextView statistics = findText(activity.morePanel, "Статистика дерева");
            assertNotNull(statistics);
            ScrollView scroll = findScroll(activity.morePanel);
            assertNotNull("Menu must scroll on smaller phones", scroll);
            scroll.requestChildRectangleOnScreen(statistics,
                new android.graphics.Rect(0, 0, statistics.getWidth(), statistics.getHeight()), true);
        });
        getInstrumentation().waitForIdleSync();
        capture("statistics-more-menu.png");
    }

    public void testKinshipModeIsSavedAndUsedByActivity() throws Exception {
        getInstrumentation().runOnMainSync(() -> dialog = activity.showKinshipModeDialog());
        getInstrumentation().waitForIdleSync();
        capture("kinship-mode.png");
        chooseMode(1);
        assertFalse(activity.kinshipTerminological);
        assertFalse(activity.getSharedPreferences("androidft-ui", Context.MODE_PRIVATE).getBoolean("kinship_terminological", true));
        getInstrumentation().runOnMainSync(() -> dialog = activity.showKinshipModeDialog());
        getInstrumentation().waitForIdleSync();
        chooseMode(0);
        assertTrue(activity.kinshipTerminological);
    }

    public void testHelpKeepsLongHeadingAndLastParagraphReachable() throws Exception {
        String title="Как отображается степень родства между людьми в вашем дереве";
        getInstrumentation().runOnMainSync(() -> dialog=activity.showHelp(title,KinshipModeDialog.HELP));
        getInstrumentation().waitForIdleSync();
        capture("kinship-help-top.png");
        getInstrumentation().runOnMainSync(() -> findScroll(dialog.getWindow().getDecorView()).fullScroll(View.FOCUS_DOWN));
        getInstrumentation().waitForIdleSync();
        boolean[] checks=new boolean[3];
        getInstrumentation().runOnMainSync(() -> {
            TextView body=findText(dialog.getWindow().getDecorView(),KinshipModeDialog.HELP);
            TextView heading=findText(dialog.getWindow().getDecorView(),title);
            TextView close=findText(dialog.getWindow().getDecorView(),"Понятно");
            checks[0]=body!=null && body.getLayout().getLineBottom(body.getLineCount()-1)<=body.getHeight()-body.getPaddingBottom();
            checks[1]=heading!=null && heading.getLayout().getLineBottom(heading.getLineCount()-1)<=heading.getHeight();
            checks[2]=close!=null && close.getGlobalVisibleRect(new android.graphics.Rect());
        });
        assertTrue("Last help paragraph fits the text view",checks[0]);assertTrue("Heading wraps fully",checks[1]);assertTrue("Close remains visible",checks[2]);
        capture("kinship-help-bottom.png");
    }

    public void testChartsAndLegendAreSelectable() throws Exception {
        getInstrumentation().runOnMainSync(() -> dialog=MainActivityStatistics.show(activity,"a"));
        getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(() -> dialog.getWindow().getDecorView().findViewWithTag("statistics.charts").performClick());
        getInstrumentation().waitForIdleSync();capture("statistics-charts.png");
        boolean[] selected={false};
        getInstrumentation().runOnMainSync(() -> {
            StatisticsDonutView chart=(StatisticsDonutView)findClass(dialog.getWindow().getDecorView(),StatisticsDonutView.class);
            if(chart!=null) { chart.select(0); selected[0]=true; }
        });
        assertTrue("Donut visible",selected[0]);getInstrumentation().waitForIdleSync();capture("statistics-chart-selected.png");
    }

    public void testStructuredNameEditorKeepsBothSurnamesAndLocalOverride() throws Exception {
        getInstrumentation().runOnMainSync(activity::openPersonEditor);getInstrumentation().waitForIdleSync();
        java.lang.reflect.Field field=MainActivityEditor.class.getDeclaredField("currentPersonDialog");field.setAccessible(true);java.lang.reflect.Field editorField=MainActivity.class.getDeclaredField("editorModule");editorField.setAccessible(true);
        dialog=(Dialog)field.get(editorField.get(activity));
        getInstrumentation().runOnMainSync(() -> {
            View decor=dialog.getWindow().getDecorView();
            ((EditText)decor.findViewWithTag("person.surname")).setText("Иванова");
            ((EditText)decor.findViewWithTag("person.givenName")).setText("Мария");
            ((EditText)decor.findViewWithTag("person.patronymic")).setText("Петровна");
            ((EditText)decor.findViewWithTag("person.maidenName")).setText("Соколова");
            decor.findViewWithTag("person.maidenVisibility").performClick();
        });getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(() -> {
            View fieldView=dialog.getWindow().getDecorView().findViewWithTag("person.surname");
            fieldView.requestRectangleOnScreen(new android.graphics.Rect(0,0,fieldView.getWidth(),fieldView.getHeight()),true);
        });getInstrumentation().waitForIdleSync();capture("person-name-fields.png");
        getInstrumentation().runOnMainSync(() -> {
            View fieldView=dialog.getWindow().getDecorView().findViewWithTag("person.maidenVisibility");
            fieldView.requestRectangleOnScreen(new android.graphics.Rect(0,0,fieldView.getWidth(),fieldView.getHeight()),true);
        });getInstrumentation().waitForIdleSync();capture("person-maiden-settings.png");
        Person p=activity.state.people.get("b");assertEquals("Иванова Мария Петровна",p.name);assertEquals("Соколова Мария Петровна",PersonNames.display(p,false));
        getInstrumentation().runOnMainSync(() -> dialog.getWindow().getDecorView().findViewWithTag("person.maidenVisibility").performClick());
        assertEquals("Соколова",p.maidenName);assertEquals(p.name,PersonNames.display(p,true));
        assertEquals(View.VISIBLE, dialog.getWindow().getDecorView().findViewWithTag("person.maidenName").getVisibility());
        capture("person-maiden-hidden.png");
    }

    public void testEditorPinAndCrownStayOpenAndReflectState() throws Exception {
        getInstrumentation().runOnMainSync(activity::openPersonEditor);
        getInstrumentation().waitForIdleSync();
        java.lang.reflect.Field editorField=MainActivity.class.getDeclaredField("editorModule");editorField.setAccessible(true);
        java.lang.reflect.Field field=MainActivityEditor.class.getDeclaredField("currentPersonDialog");field.setAccessible(true);
        dialog=(Dialog)field.get(editorField.get(activity));
        View decor=dialog.getWindow().getDecorView();
        getInstrumentation().runOnMainSync(() -> decor.findViewWithTag("person.pin").performClick());
        assertTrue(activity.state.people.get("b").pinned);
        assertTrue(decor.findViewWithTag("person.pin").isSelected());
        assertTrue(dialog.isShowing());
        capture("person-pin-crown-outline.png");
        getInstrumentation().runOnMainSync(() -> decor.findViewWithTag("person.root").performClick());
        getInstrumentation().waitForIdleSync();
        assertEquals("b", activity.state.rootId);
        assertTrue(decor.findViewWithTag("person.root").isSelected());
        assertTrue(dialog.isShowing());
        capture("person-pin-crown-filled.png");
        getInstrumentation().runOnMainSync(() -> decor.findViewWithTag("person.pin").performClick());
        assertFalse(activity.state.people.get("b").pinned);
    }

    public void testNearGenerationsChangeLiveAndBothPanelsFitBesideAdd() throws Exception {
        getInstrumentation().runOnMainSync(() -> activity.showPersonBranch(activity.state.people.get("b"), "near"));
        getInstrumentation().waitForIdleSync();
        assertEquals(1, activity.nearGenerations);
        getInstrumentation().runOnMainSync(() -> activity.stage.findViewWithTag("near.plus").performClick());
        assertEquals(2, activity.nearGenerations);
        assertTrue(activity.state.nearOf("b", activity.nearGenerations).contains("c"));
        getInstrumentation().runOnMainSync(() -> activity.nearGenerationInput.setText("3"));
        assertEquals(3, activity.nearGenerations);
        getInstrumentation().runOnMainSync(() -> activity.stage.findViewWithTag("near.minus").performClick());
        assertEquals(2, activity.nearGenerations);
        getInstrumentation().waitForIdleSync();
        android.graphics.Rect branch=new android.graphics.Rect(), add=new android.graphics.Rect();
        getInstrumentation().runOnMainSync(() -> {
            activity.branchStatusPanel.getGlobalVisibleRect(branch);
            activity.addPersonButton.getGlobalVisibleRect(add);
        });
        assertFalse(branch.isEmpty()); assertTrue(branch.right <= add.left);
        capture("near-generations-bottom.png");
        getInstrumentation().runOnMainSync(() -> activity.showKinshipToRoot(activity.state.people.get("b")));
        getInstrumentation().waitForIdleSync();
        assertEquals(View.GONE, activity.branchStatusPanel.getVisibility());
        assertEquals(View.VISIBLE, activity.kinshipStatusPanel.getVisibility());
        android.graphics.Rect kinship=new android.graphics.Rect();
        getInstrumentation().runOnMainSync(() -> activity.kinshipStatusPanel.getGlobalVisibleRect(kinship));
        assertFalse(kinship.isEmpty()); assertTrue(kinship.right <= add.left);
        capture("kinship-bottom.png");
    }

    public void testInternalSaveRetainsNameAndPresentationSettings() throws Exception {
        Person p=activity.state.people.get("b");PersonNames.set(p,"Иванова","Мария","Петровна");p.maidenName="Соколова";p.maidenNameEnabled=true;p.surnameDisplay="current";
        activity.state.useMaidenNames=true;activity.state.showQualityBadge=false;
        TreeStore store=new TreeStore(getInstrumentation().getTargetContext());
        TreeState restored=store.parse(store.exportText(activity.state));
        assertTrue(PersonNames.equal(p,restored.people.get("b")));assertTrue(restored.useMaidenNames);assertFalse(restored.showQualityBadge);
    }

    public void testQualityBadgeStopsPaintingWhenHidden() throws Exception {
        boolean[] painted=new boolean[2];
        getInstrumentation().runOnMainSync(() -> {
            android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(200,200,android.graphics.Bitmap.Config.ARGB_8888);
            TreeCanvasView view=new TreeCanvasView(activity);
            try {
                view.setState(activity.state);
                java.lang.reflect.Field scale=TreeCanvasView.class.getDeclaredField("scale");scale.setAccessible(true);scale.setFloat(view,1f);
                java.util.Map<String,TreeQualityAnalyzer.PersonReport> reports=new java.util.HashMap<>();reports.put("a",new TreeQualityAnalyzer.PersonReport("a"));view.setQualityReports(reports);
                java.lang.reflect.Method draw=TreeCanvasView.class.getDeclaredMethod("drawQualityIndicator",android.graphics.Canvas.class,Person.class,float.class,float.class,float.class,float.class);draw.setAccessible(true);
                for(int i=0;i<2;i++) {
                    activity.state.showQualityBadge=i==0;image.eraseColor(android.graphics.Color.TRANSPARENT);
                    draw.invoke(view,new android.graphics.Canvas(image),activity.state.people.get("a"),0f,0f,200f,200f);
                    for(int x=160;x<200;x++)for(int y=160;y<200;y++)if(android.graphics.Color.alpha(image.getPixel(x,y))>0)painted[i]=true;
                }
            } catch(Exception e) { throw new RuntimeException(e); }
            finally { image.recycle();view.releaseExportResources(); }
        });
        assertTrue(painted[0]);assertFalse(painted[1]);
    }

    private static View findClass(View view,Class<?> type) {
        if(type.isInstance(view))return view;
        if(view instanceof ViewGroup) { ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View child=findClass(group.getChildAt(i),type);if(child!=null)return child;} }
        return null;
    }

    private void chooseMode(int mode) {
        getInstrumentation().runOnMainSync(() -> {
            View decor = dialog.getWindow().getDecorView();
            ((RadioGroup)decor.findViewWithTag("kinship.choices")).check(mode+1);
            decor.findViewWithTag("kinship.apply").performClick();
        });
        getInstrumentation().waitForIdleSync();
    }

    private void capture(String name) throws Exception {
        android.os.SystemClock.sleep(600); // Wait for window/scroll animation to reach the compositor.
        Bitmap image = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        File file = new File(activity.getExternalCacheDir(), name);
        try (FileOutputStream out = new FileOutputStream(file)) { image.compress(Bitmap.CompressFormat.PNG, 100, out); }
        image.recycle();
    }
    private static TextView findText(View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) { TextView result = findText(group.getChildAt(i), label); if (result != null) return result; }
        }
        return null;
    }
    private static ScrollView findScroll(View view) {
        if (view instanceof ScrollView) return (ScrollView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) { ScrollView result = findScroll(group.getChildAt(i)); if (result != null) return result; }
        }
        return null;
    }
}
