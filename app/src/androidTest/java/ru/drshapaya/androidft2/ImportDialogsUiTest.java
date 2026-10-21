package ru.drshapaya.androidft2;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class ImportDialogsUiTest extends InstrumentationTestCase {
    private MainActivity activity;
    private Dialog dialog;
    private TreeState original;
    private String language;
    private boolean hadTraining,training;

    @Override protected void setUp() throws Exception {
        super.setUp(); Context context=getInstrumentation().getTargetContext();
        language=AppLanguage.mode(context); AppLanguage.setMode(context,AppLanguage.RUSSIAN);
        SharedPreferences prefs=context.getSharedPreferences("androidft-ui",Context.MODE_PRIVATE);
        hadTraining=prefs.contains("training_offer_handled"); training=prefs.getBoolean("training_offer_handled",false);
        prefs.edit().putBoolean("training_offer_handled",true).commit();
        activity=(MainActivity)getInstrumentation().startActivitySync(new Intent(context,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        TestStartup.await(getInstrumentation(), activity);
        getInstrumentation().waitForIdleSync();
        onMain(() -> {
            original=activity.state; TreeState sample=new TreeState();
            Person p=new Person("existing"); p.name="Иванова Мария"; p.x=100; p.y=100;
            sample.people.put(p.id,p); sample.rootId=p.id;
            activity.state=sample; activity.bindState();
        });
    }

    @Override protected void tearDown() throws Exception {
        if(activity!=null) {
            onMain(() -> { if(dialog!=null) dialog.dismiss(); activity.state=original; activity.saveOnly(); activity.finish(); });
            getInstrumentation().waitForIdleSync();
        }
        SharedPreferences.Editor prefs=getInstrumentation().getTargetContext().getSharedPreferences("androidft-ui",Context.MODE_PRIVATE).edit();
        if(hadTraining) prefs.putBoolean("training_offer_handled",training); else prefs.remove("training_offer_handled"); prefs.commit();
        AppLanguage.setMode(getInstrumentation().getTargetContext(),language);
        AppThemePalette.setDark("dark".equals(original.theme)); super.tearDown();
    }

    public void testSourceChoiceAndFolderGuideHaveStyledReachableActions() throws Exception {
        Object files=field(activity,"filesModule"),picker=field(files,"importPicker");
        onMain(() -> { invoke(picker,"chooseSource",new Class<?>[0]); dialog=(Dialog)field(picker,"dialog"); });
        getInstrumentation().waitForIdleSync(); capture("import-source.png");
        onMain(() -> {
            View decor=decor(); assertNotNull(decor.findViewWithTag("appDialog.shell"));
            assertVisible(decor.findViewWithTag("appDialog.action.0"));
            decor.findViewWithTag("appDialog.choice.2").performClick(); dialog=(Dialog)field(picker,"dialog");
        });
        getInstrumentation().waitForIdleSync(); capture("import-folder-guide.png");
        onMain(() -> {
            assertEquals("Папка для импорта",((TextView)decor().findViewWithTag("appDialog.title")).getText().toString());
            assertVisible(decor().findViewWithTag("appDialog.action.1"));
            decor().findViewWithTag("appDialog.action.0").performClick(); assertFalse(dialog.isShowing());
        });
    }

    public void testFilePickerAllowsGedcomReportedAsGenericBinary() {
        Intent intent=TreeImportPicker.fileIntent();
        assertEquals(Intent.ACTION_OPEN_DOCUMENT,intent.getAction());
        assertEquals("*/*",intent.getType());
        assertFalse(intent.hasExtra(Intent.EXTRA_MIME_TYPES));
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE));
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);
        assertTrue(TreeImportPicker.supports("family-tree.GED"));
    }

    public void testImportReviewScrollsLongWarningsAndCancelPreservesCurrentTree() throws Exception {
        TreeState current=activity.state,imported=TreeStateCopier.copy(current);
        StringBuilder warning=new StringBuilder();
        for(int i=0;i<30;i++) warning.append("Некоторые фотографии отсутствуют в исходном файле.\n");
        Object files=field(activity,"filesModule");
        onMain(() -> dialog=(Dialog)invoke(files,"confirmImportedTree",new Class<?>[]{TreeState.class,String.class,String.class},
            imported,"Семейное дерево с длинным названием за несколько поколений.ftree",warning.toString()));
        getInstrumentation().waitForIdleSync(); capture("import-review-top.png");
        onMain(() -> {
            assertVisible(decor().findViewWithTag("appDialog.action.1"));
            ScrollView scroll=findScroll(decor()); assertTrue(scroll.canScrollVertically(1)); scroll.fullScroll(View.FOCUS_DOWN);
        });
        getInstrumentation().waitForIdleSync(); capture("import-review-bottom.png");
        onMain(() -> {
            ScrollView scroll=findScroll(decor()); assertFalse(scroll.canScrollVertically(1));
            assertVisible(decor().findViewWithTag("appDialog.action.0")); decor().findViewWithTag("appDialog.action.0").performClick();
        });
        assertSame(current,activity.state); assertFalse(dialog.isShowing());
    }

    public void testLargeFileListRecyclesRowsAndSelectsCorrectLastFile() throws Exception {
        List<AppDialog.Item> entries=new ArrayList<>();
        for(int i=0;i<1000;i++) entries.add(new AppDialog.Item(i<2 ? R.drawable.ic_menu_folder : R.drawable.ic_menu_file,
            i<2 ? "Семейный архив "+i : "Дерево семьи за несколько поколений — версия "+i+".ged",i<2 ? "Папка" : "GEDCOM"));
        int[] selected={-1};
        onMain(() -> {
            dialog=AppDialog.create(activity,R.drawable.ic_menu_folder,"Импорт · поддерживаемые файлы")
                .message("Семейный архив").items(entries,index -> selected[0]=index)
                .action("Назад",false,null).action("Другая папка",true,null); dialog.show();
        });
        getInstrumentation().waitForIdleSync(); capture("import-files.png");
        onMain(() -> {
            ListView list=(ListView)decor().findViewWithTag("appDialog.items");
            assertTrue("Rows must be recycled",list.getChildCount()<20); assertVisible(decor().findViewWithTag("appDialog.action.1"));
            list.setSelection(999);
        });
        getInstrumentation().waitForIdleSync();
        onMain(() -> {
            ListView list=(ListView)decor().findViewWithTag("appDialog.items");
            int index=999-list.getFirstVisiblePosition(); View last=list.getChildAt(index);
            assertNotNull(last); list.performItemClick(last,999,999);
        });
        assertEquals(999,selected[0]); assertFalse(dialog.isShowing());
    }

    public void testProgressCancelAndImportFailureUseStyledWindows() throws Exception {
        boolean[] cancelled={false};
        onMain(() -> {
            dialog=AppDialog.create(activity,R.drawable.ic_menu_import,"Проверка дерева")
                .message("Семейное дерево.ftree").progress("Читаем персоны, связи и фотографии…")
                .action("Отмена",false,() -> cancelled[0]=true);
            dialog.setOnCancelListener(d -> cancelled[0]=true); dialog.show();
        });
        getInstrumentation().waitForIdleSync(); capture("import-progress.png");
        onMain(() -> { assertNotNull(decor().findViewWithTag("appDialog.progress")); decor().findViewWithTag("appDialog.close").performClick(); });
        getInstrumentation().waitForIdleSync();
        assertTrue(cancelled[0]);
        Object files=field(activity,"filesModule");
        onMain(() -> dialog=(Dialog)invoke(files,"showImportFailure",new Class<?>[]{Uri.class,Exception.class},
            Uri.parse("file:///invalid-tree.ged"),new java.io.IOException("Не удалось прочитать файл. Проверьте формат дерева.")));
        getInstrumentation().waitForIdleSync(); capture("import-error.png");
        onMain(() -> {
            assertEquals("Импорт не выполнен",((TextView)decor().findViewWithTag("appDialog.title")).getText().toString());
            assertVisible(decor().findViewWithTag("appDialog.action.0")); decor().findViewWithTag("appDialog.action.0").performClick();
        });
        assertFalse(dialog.isShowing());
    }

    public void testWorkshopDialogAndDarkEnglishThemeAreReadable() throws Exception {
        Object workshop=field(activity,"workshopModule");
        onMain(() -> dialog=(Dialog)invoke(workshop,"showJoinLeaderboardDialog",new Class<?>[0]));
        getInstrumentation().waitForIdleSync(); capture("workshop-dialog.png");
        onMain(() -> { assertNotNull(decor().findViewWithTag("appDialog.shell")); assertVisible(decor().findViewWithTag("appDialog.action.0")); dialog.dismiss(); });
        AppLanguage.setMode(activity,AppLanguage.ENGLISH);
        onMain(() -> {
            AppThemePalette.setDark(true);
            dialog=AppDialog.create(activity,R.drawable.ic_menu_folder,"Папка для импорта")
                .message("Выберите папку с деревом. В ней будут показаны только .ftree, .tree, .json, .ged, .gedcom, .zip и XML Family Tree DS.")
                .action("Отмена",false,null).action("Выбрать папку",true,null); dialog.show();
        });
        getInstrumentation().waitForIdleSync(); capture("import-dark-english.png");
        onMain(() -> {
            TextView title=(TextView)decor().findViewWithTag("appDialog.title");
            assertEquals("Import folder",title.getText().toString());
            assertTrue("Light text on dark background",android.graphics.Color.red(title.getCurrentTextColor())>150);
            assertVisible(decor().findViewWithTag("appDialog.action.1"));
        });
    }

    private View decor() { return dialog.getWindow().getDecorView(); }
    private void assertVisible(View view) { assertNotNull(view); assertTrue(view.getGlobalVisibleRect(new android.graphics.Rect())); }
    private ScrollView findScroll(View view) {
        if(view instanceof ScrollView) return (ScrollView)view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
            ScrollView found=findScroll(((ViewGroup)view).getChildAt(i)); if(found!=null) return found;
        }
        return null;
    }
    private Object field(Object object,String name) {
        try { Field field=object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch(Exception error) { throw new AssertionError(error); }
    }
    private Object invoke(Object object,String name,Class<?>[] types,Object... values) {
        try { Method method=object.getClass().getDeclaredMethod(name,types); method.setAccessible(true); return method.invoke(object,values); }
        catch(Exception error) { throw new AssertionError(error); }
    }
    private void onMain(Runnable action) {
        AtomicReference<Throwable> failure=new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> { try { action.run(); }catch(Throwable error) { failure.set(error); } });
        if(failure.get()!=null) throw new AssertionError(failure.get());
    }
    private void capture(String name) throws Exception {
        getInstrumentation().waitForIdleSync();
        // Wait for Android's window transition before taking a visual QA image.
        android.os.SystemClock.sleep(350);
        Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();
        try(FileOutputStream stream=new FileOutputStream(new File(activity.getExternalCacheDir(),name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);
        }finally { bitmap.recycle(); }
    }
}
