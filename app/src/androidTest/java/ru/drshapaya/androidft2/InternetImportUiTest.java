package ru.drshapaya.androidft2;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.inspector.WindowInspector;
import android.widget.TextView;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Opt-in device audit: adb push the public corpus to the target app's external cache first. */
public final class InternetImportUiTest extends InstrumentationTestCase {
    private MainActivity activity;
    private TreeState original;
    private File corpus;
    private String language;
    private boolean hadTraining,training;

    @Override protected void setUp() throws Exception {
        super.setUp();Context context=getInstrumentation().getTargetContext();
        corpus=new File(context.getExternalCacheDir(),"internet-import-review");
        if(!new File(corpus,"manifest.json").isFile()) return;
        language=AppLanguage.mode(context);AppLanguage.setMode(context,AppLanguage.RUSSIAN);
        SharedPreferences prefs=context.getSharedPreferences("androidft-ui",Context.MODE_PRIVATE);
        hadTraining=prefs.contains("training_offer_handled");training=prefs.getBoolean("training_offer_handled",false);
        prefs.edit().putBoolean("training_offer_handled",true).commit();
        activity=(MainActivity)getInstrumentation().startActivitySync(new Intent(context,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        TestStartup.await(getInstrumentation(), activity);
        getInstrumentation().waitForIdleSync();
        onMain(() -> original=activity.state);
    }
    @Override protected void tearDown() throws Exception {
        if(activity!=null) {
            onMain(() -> { activity.state=original;activity.saveOnly();activity.finish(); });
            SharedPreferences.Editor prefs=activity.getSharedPreferences("androidft-ui",Context.MODE_PRIVATE).edit();
            if(hadTraining) prefs.putBoolean("training_offer_handled",training);else prefs.remove("training_offer_handled");prefs.commit();
            AppLanguage.setMode(activity,language);AppThemePalette.setDark("dark".equals(original.theme));
        }
        super.tearDown();
    }

    public void test01OriginalCorpusWithAndroidDecoderAndStorage() throws Exception {
        if(activity==null) return;
        JSONArray manifest=new JSONArray(read(new File(corpus,"manifest.json"))),report=new JSONArray();
        List<String> failures=new ArrayList<>();
        for(int i=0;i<manifest.length();i++) {
            JSONObject item=manifest.getJSONObject(i),row=new JSONObject(item.toString());
            long started=SystemClock.elapsedRealtime();
            try(InputStream input=new BufferedInputStream(new FileInputStream(new File(corpus,item.getString("id"))))) {
                if(item.getInt("expectedPeople")>1000 && "open".equals(item.getString("expectedDisposition"))) {
                    GedcomReader.Result parsed=GedcomReader.read(TreeImportIO.decodeText(TreeImportIO.readBytes(input,TreeImportIO.MAX_TEXT)),false);
                    assertEquals(item.getInt("expectedPeople"),parsed.state.people.size());
                    row.put("status","parsed_without_layout").put("people",parsed.state.people.size()).put("links",parsed.state.links.size());
                }else {
                    TreeImportIO.Result result="zip".equals(item.getString("kind")) ? TreeImportIO.readZip(input,activity.store) : TreeImportIO.readText(input,activity.store);
                    assertEquals(item.getInt("expectedPeople"),result.state.people.size());
                    row.put("status",result.state.people.isEmpty() ? "no_people" : "open").put("people",result.state.people.size())
                        .put("links",result.state.links.size()).put("missingPhotos",result.missingPhotos).put("unresolvedReferences",result.unresolvedReferences);
                    if(!result.state.people.isEmpty()) {
                        assertEquals("open",item.getString("expectedDisposition"));
                        assertModelEqual(result.state,activity.store.parse(activity.store.exportText(result.state)));
                        row.put("roundTrip",true);
                    }
                    if(item.getString("id").equals("gedcom7-maximal70.gdz")) {
                        assertEquals(0,result.missingPhotos);int portraits=0;
                        for(Person person:result.state.people.values()) if(!person.photoMediaId.isEmpty()) {
                            Bitmap bitmap=activity.store.mediaStore().decodeBitmap(person.photoMediaId,128);
                            assertNotNull("The downloaded portrait must decode on Android",bitmap);bitmap.recycle();portraits++;
                        }
                        assertTrue(portraits>0);row.put("decodedPortraits",portraits);
                    }
                }
            }catch(Exception | AssertionError error) {
                row.put("status","rejected").put("error",error.toString());
                if("open".equals(item.getString("expectedDisposition")) || error instanceof AssertionError)
                    failures.add(item.getString("id")+": "+error);
            }
            row.put("elapsedMs",SystemClock.elapsedRealtime()-started);report.put(row);
            write(new File(corpus,"android-results.json"),report.toString(2));
        }
        assertTrue(failures.toString(),failures.isEmpty());
    }

    public void test02OpenOriginalFilesThroughImportWindowsAndReload() throws Exception {
        if(activity==null) return;
        String[] names={"frizbog-5.5.1_sample_2.ged","frizbog-willis-ascii.ged","frizbog-legacycustomtags.ged",
            "frizbog-famhistcustomtags.ged","gramps-project-sample.ged","D-Jeffrey-bourbon.ged","D-Jeffrey-washington.ged",
            "gramps-project-imp_cp1252_LF.ged","frizbog-unicode-littleendian-crlf.ged","gedcom7-xref.ged","gedcom7-maximal70.gdz"};
        int[] counts={147,761,8,3,42,303,529,1,35,7,4};JSONArray results=new JSONArray();
        for(int i=0;i<names.length;i++) {
            long started=SystemClock.elapsedRealtime();open(new File(corpus,names[i]),counts[i]);
            if(names[i].endsWith(".gdz")) capture("internet-gedzip-tree.png");
            results.put(new JSONObject().put("id",names[i]).put("people",activity.state.people.size())
                .put("links",activity.state.links.size()).put("elapsedMs",SystemClock.elapsedRealtime()-started).put("status","opened_and_reloaded"));
            write(new File(corpus,"ui-results.json"),results.toString(2));
        }
        TreeState saved=TreeStateCopier.copy(activity.state);
        onMain(() -> activity.finish());getInstrumentation().waitForIdleSync();
        Context context=getInstrumentation().getTargetContext();
        activity=(MainActivity)getInstrumentation().startActivitySync(new Intent(context,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        TestStartup.await(getInstrumentation(), activity);
        getInstrumentation().waitForIdleSync();assertModelEqual(saved,activity.state);
        capture("internet-gedzip-after-relaunch.png");
    }

    public void test03EveryAppFormatFromTheDownloadedGedzipPreservesNamesNotesAndRelations() throws Exception {
        if(activity==null) return;
        TreeState sample;
        try(InputStream input=new FileInputStream(new File(corpus,"gedcom7-maximal70.gdz"))) { sample=TreeImportIO.readZip(input,activity.store).state; }
        File variants=new File(corpus,"generated-roundtrips");assertTrue(variants.isDirectory() || variants.mkdirs());
        String json=activity.store.exportText(sample);
        for(String extension:new String[]{"json","tree","ftree"}) write(new File(variants,"text."+extension),json);
        try(OutputStream output=new FileOutputStream(new File(variants,"container.ftree"))) { TreePackageIO.write(sample,activity.store,output,"copy"); }
        StringWriter xml=new StringWriter();TreeReadableExport.xml(sample,xml,p -> {
            if(p.photoMediaId.isEmpty()) return p.photo;
            Bitmap bitmap=activity.store.mediaStore().decodeBitmap(p.photoMediaId,320);assertNotNull(bitmap);
            try { ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,82,bytes);
                return "data:image/jpeg;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
            }finally { bitmap.recycle(); }
        });
        write(new File(variants,"tree.xml"),xml.toString());
        write(new File(variants,"tree.gedcom"),GedcomWriter.write(sample));
        try(InputStream input=new FileInputStream(new File(corpus,"gedcom7-maximal70.gdz"));OutputStream output=new FileOutputStream(new File(variants,"original.zip"))) {
            byte[] bytes=new byte[65536];int n;while((n=input.read(bytes))!=-1) output.write(bytes,0,n);
        }
        JSONArray report=new JSONArray();
        for(String name:new String[]{"text.json","text.tree","text.ftree","container.ftree","tree.xml","tree.gedcom","original.zip"}) {
            open(new File(variants,name),sample.people.size());
            assertEquals(sample.links.size(),activity.state.links.size());
            // GEDCOM generates new local identifiers; compare content by insertion order.
            List<Person> expected=new ArrayList<>(sample.people.values()),actual=new ArrayList<>(activity.state.people.values());
            for(int i=0;i<expected.size();i++) { assertEquals(expected.get(i).name,actual.get(i).name);assertEquals(expected.get(i).notes,actual.get(i).notes); }
            report.put(new JSONObject().put("id",name).put("people",activity.state.people.size()).put("links",activity.state.links.size()).put("status","opened_and_reloaded"));
            write(new File(corpus,"format-results.json"),report.toString(2));
        }
    }

    private void open(File file,int count) throws Exception {
        assertTrue(file.getName(),TreeImportPicker.supports(file.getName()));
        Object files=field(activity,"filesModule");
        onMain(() -> ((MainActivityFiles)files).importFromUri(Uri.fromFile(file)));
        long deadline=SystemClock.elapsedRealtime()+120_000;AtomicReference<View> result=new AtomicReference<>();AtomicReference<String> title=new AtomicReference<>();
        while(result.get()==null && SystemClock.elapsedRealtime()<deadline) {
            onMain(() -> {
                for(View root:WindowInspector.getGlobalWindowViews()) {
                    TextView label=root.findViewWithTag("appDialog.title");
                    if(label==null || !root.isShown()) continue;
                    String text=label.getText().toString();
                    if(text.equals("Дерево готово к открытию") || text.equals("Импорт не выполнен")) { result.set(root);title.set(text); }
                }
            });
            if(result.get()==null) SystemClock.sleep(100);
        }
        assertNotNull("Import window timed out: "+file.getName(),result.get());
        assertEquals("Import rejected "+file.getName(),"Дерево готово к открытию",title.get());
        if(file.getName().endsWith(".gdz")) capture("internet-gedzip-review.png");
        onMain(() -> result.get().findViewWithTag("appDialog.action.1").performClick());
        getInstrumentation().waitForIdleSync();assertEquals(file.getName(),count,activity.state.people.size());
        // saveOnly schedules a debounced background write; wait for that write before reopening.
        TreeState expected=TreeStateCopier.copy(activity.state),saved=activity.store.load();
        long saveDeadline=SystemClock.elapsedRealtime()+15_000;
        while((saved.people.size()!=expected.people.size() || !saved.rootId.equals(expected.rootId))
            && SystemClock.elapsedRealtime()<saveDeadline) {
            SystemClock.sleep(100);saved=activity.store.load();
        }
        assertModelEqual(expected,saved);
    }
    private void assertModelEqual(TreeState a,TreeState b) {
        assertEquals(a.people.size(),b.people.size());assertEquals(a.links.size(),b.links.size());
        for(Person person:a.people.values()) {
            Person saved=b.people.get(person.id);assertNotNull(saved);assertEquals(person.name,saved.name);assertEquals(person.notes,saved.notes);
            assertEquals(person.bornYear,saved.bornYear);assertEquals(person.diedYear,saved.diedYear);
            if(!person.photoMediaId.isEmpty()) assertEquals(person.photoMediaId,saved.photoMediaId);
            else if(!person.photo.isEmpty()) {
                assertFalse("Inline XML portraits must migrate to stored media",saved.photoMediaId.isEmpty());
                assertTrue(activity.store.mediaStore().exists(saved.photoMediaId));
            }
        }
        for(int i=0;i<a.links.size();i++) { Relation first=a.links.get(i),second=b.links.get(i);assertEquals(first.type,second.type);assertEquals(first.from,second.from);assertEquals(first.to,second.to); }
    }
    private Object field(Object object,String name) {
        try { Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object); }catch(Exception e) { throw new AssertionError(e); }
    }
    private void onMain(Runnable action) {
        AtomicReference<Throwable> error=new AtomicReference<>();getInstrumentation().runOnMainSync(() -> { try { action.run(); }catch(Throwable e) { error.set(e); } });
        if(error.get()!=null) throw new AssertionError(error.get());
    }
    private String read(File file) throws Exception { try(InputStream input=new FileInputStream(file)) { return new String(TreeImportIO.readBytes(input,TreeImportIO.MAX_TEXT),StandardCharsets.UTF_8); } }
    private void write(File file,String value) throws Exception { try(OutputStream output=new FileOutputStream(file)) { output.write(value.getBytes(StandardCharsets.UTF_8)); } }
    private void capture(String name) throws Exception {
        SystemClock.sleep(600);Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();
        try(OutputStream output=new FileOutputStream(new File(corpus,name))) { bitmap.compress(Bitmap.CompressFormat.PNG,100,output); }finally { bitmap.recycle(); }
    }
}
