package ru.drshapaya.androidft2;

import android.content.*;
import android.graphics.*;
import android.test.InstrumentationTestCase;
import android.os.SystemClock;
import android.view.View;
import android.widget.ListView;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class OptimizationUiTest extends InstrumentationTestCase {
    private MainActivity activity;
    private TreeState previous;
    @Override protected void setUp() throws Exception {
        super.setUp();
        activity=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        TestStartup.await(getInstrumentation(),activity);
        onMain(() -> previous=activity.state);
    }
    @Override protected void tearDown() throws Exception {
        onMain(() -> { activity.state=previous;activity.finish(); });super.tearDown();
    }
    private interface Action { void run() throws Exception; }
    private void onMain(Action action) throws Exception {
        AtomicReference<Throwable> error=new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> { try { action.run(); } catch(Throwable failure) { error.set(failure); } });
        if(error.get()!=null) throw new AssertionError(error.get());
    }
    private TreeState tree(int count) {
        TreeState state=new TreeState();
        for(int i=0;i<count;i++) { Person p=state.addPerson("Человек "+i,(i%100)*320,(i/100)*480);p.bornYear="1980";p.place="Томск"; }
        state.selectedId="";return state;
    }
    public void testPngRendererWorksOnWorkerWithIdenticalPixels() throws Exception {
        TreeImageExport[] export={null}; Bitmap[] reference={null}; ExportLayout[] layout={null};
        onMain(() -> {
            activity.state=tree(3);export[0]=new TreeImageExport(activity);
            layout[0]=export[0].layout(1,2000000);reference[0]=export[0].tile(layout[0],0,0);
        });
        try {
            Bitmap worker=export[0].tile(layout[0],0,0);
            int size=worker.getWidth()*worker.getHeight();int[] a=new int[size],b=new int[size];
            worker.getPixels(a,0,worker.getWidth(),0,0,worker.getWidth(),worker.getHeight());
            reference[0].getPixels(b,0,worker.getWidth(),0,0,worker.getWidth(),worker.getHeight());
            assertTrue("Worker changed PNG pixels",Arrays.equals(a,b));worker.recycle();
        } finally { onMain(() -> export[0].close());reference[0].recycle(); }
    }
    public void testFarZoomStillDrawsCardDetails() throws Exception {
        onMain(() -> {
            TreeCanvasView view=new TreeCanvasView(activity);view.setState(tree(1));view.layout(0,0,160,120);
            for(String field:new String[]{"scale","offsetX","offsetY"}) {
                Field f=TreeCanvasView.class.getDeclaredField(field);f.setAccessible(true);f.setFloat(view,field.equals("scale")?.16f:40f);
            }
            Bitmap details=Bitmap.createBitmap(160,120,Bitmap.Config.ARGB_8888),hidden=Bitmap.createBitmap(160,120,Bitmap.Config.ARGB_8888);
            view.setHideDetails(false);view.draw(new Canvas(details));view.setHideDetails(true);view.draw(new Canvas(hidden));
            int[] a=new int[19200],b=new int[19200];details.getPixels(a,0,160,0,0,160,120);hidden.getPixels(b,0,160,0,0,160,120);
            assertFalse("Dates/place disappeared at far zoom",Arrays.equals(a,b));details.recycle();hidden.recycle();view.releaseExportResources();
        });
    }
    public void testCatalogKeepsAllFiveThousandPeopleWithFewLiveRows() throws Exception {
        onMain(() -> { activity.state=tree(5000);activity.bindState();activity.showPanel("people"); });
        ListView[] list={null};long end=SystemClock.uptimeMillis()+20000;
        while(SystemClock.uptimeMillis()<end) {
            onMain(() -> {
                Field f=MainActivity.class.getDeclaredField("peopleModule");f.setAccessible(true);Object module=f.get(activity);
                Field l=MainActivityPeople.class.getDeclaredField("peopleList");l.setAccessible(true);list[0]=(ListView)l.get(module);
            });
            boolean[] ready={false};onMain(() -> ready[0]=list[0]!=null && list[0].getCount()>=5000);
            if(ready[0])break;SystemClock.sleep(25);
        }
        onMain(() -> {
            assertNotNull(list[0]);assertTrue(list[0].getCount()>=5000);assertTrue(list[0].getChildCount()<100);
            list[0].setSelection(list[0].getCount()-1);
        });
    }
    public void testFailedSaveRetriesOnFlushWithoutAnotherEdit() throws Exception {
        Context context=getInstrumentation().getTargetContext();
        File base=new File(context.getCacheDir(),"retry-"+UUID.randomUUID());assertTrue(base.mkdirs());
        File blocked=new File(base,"files");assertTrue(blocked.createNewFile());
        TreeStore store=new TreeStore(new ContextWrapper(context) {
            @Override public Context getApplicationContext(){return this;}
            @Override public File getFilesDir(){return blocked;}
        });
        CountDownLatch failed=new CountDownLatch(1),saved=new CountDownLatch(1);
        TreeState state=tree(1);TreeSaveCoordinator[] coordinator={null};
        onMain(() -> {
            coordinator[0]=new TreeSaveCoordinator(store,()->state,()->{},new TreeSaveCoordinator.Listener(){
                public void onSaveError(){failed.countDown();}
                public void onSaved(TreeState snapshot){saved.countDown();}
            });coordinator[0].requestImmediate();
        });
        assertTrue(failed.await(5,TimeUnit.SECONDS));assertTrue(blocked.delete());assertTrue(blocked.mkdir());
        onMain(() -> coordinator[0].flush());assertTrue("Flush did not retry failed revision",saved.await(5,TimeUnit.SECONDS));
        assertEquals(1,store.load().people.size());onMain(() -> coordinator[0].close());
    }
    public void testQueuedRemoteSnapshotCannotReplaceNewLocalEdit() throws Exception {
        AtomicInteger applied=new AtomicInteger(),committed=new AtomicInteger();CountDownLatch accepted=new CountDownLatch(1);
        OnlineTreeManager[] manager={null};
        onMain(() -> manager[0]=new OnlineTreeManager(activity,new TreeStore(activity),new OnlineTreeManager.Listener(){
            public void onRemoteTree(TreeState state,String message){applied.incrementAndGet();}
            public void onMediaChanged(){} public void onEditingPermissionChanged(boolean value){}
            public void onStatusChanged(){} public void onMessage(String message){}
        }));
        try {
            Class<?> commitType=Class.forName(OnlineTreeManager.class.getName()+"$RemoteCommit");
            Object commit=Proxy.newProxyInstance(commitType.getClassLoader(),new Class<?>[]{commitType},(proxy,method,args)->{
                committed.incrementAndGet();accepted.countDown();return null;
            });
            Method accept=OnlineTreeManager.class.getDeclaredMethod("acceptRemote",TreeState.class,String.class,long.class,long.class,commitType);accept.setAccessible(true);
            onMain(() -> {accept.invoke(manager[0],tree(1),"",0L,0L,commit);manager[0].markLocalTreeChanged();});
            onMain(()->{});assertEquals(0,applied.get());assertEquals(0,committed.get());
            onMain(() -> accept.invoke(manager[0],tree(1),"",0L,1L,commit));
            assertTrue(accepted.await(5,TimeUnit.SECONDS));assertEquals(1,applied.get());assertEquals(1,committed.get());
        } finally {onMain(()->manager[0].close());}
    }
}
