package ru.drshapaya.androidft2;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.*;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class GalleryOptimizationUiTest extends InstrumentationTestCase {
    private MainActivity activity;
    private TreeState original;
    private final List<Dialog> dialogs=new ArrayList<>();
    private String mediaId;

    @Override protected void setUp() throws Exception {
        super.setUp();
        activity=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        TestStartup.await(getInstrumentation(),activity);
        onMain(()->original=activity.state);
        Bitmap photo=Bitmap.createBitmap(256,128,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(photo);canvas.drawColor(Color.rgb(22,160,120));
        Paint paint=new Paint();paint.setColor(Color.rgb(220,120,70));canvas.drawRect(0,0,128,128,paint);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();photo.compress(Bitmap.CompressFormat.PNG,100,bytes);photo.recycle();
        mediaId=activity.store.mediaStore().importPhoto(new ByteArrayInputStream(bytes.toByteArray()),"queue-test.png","image/png").id;
    }

    @Override protected void tearDown() throws Exception {
        onMain(()->{
            for (Dialog dialog:dialogs) dialog.dismiss();
            activity.state=original;activity.finish();
        });
        super.tearDown();
    }

    private interface Action { void run() throws Exception; }
    private void onMain(Action action) {
        AtomicReference<Throwable> failure=new AtomicReference<>();
        getInstrumentation().runOnMainSync(()->{try{action.run();}catch(Throwable error){failure.set(error);}});
        if(failure.get()!=null)throw new AssertionError(failure.get());
    }
    private Object module() throws Exception {
        Field field=MainActivity.class.getDeclaredField("peopleModule");field.setAccessible(true);return field.get(activity);
    }
    private Dialog open(String method,Class<?>[] types,Object... args) throws Exception {
        Method target=MainActivityPeople.class.getDeclaredMethod(method,types);target.setAccessible(true);target.invoke(module(),args);
        Field field=MainActivityPeople.class.getDeclaredField("openPhotoDialog");field.setAccessible(true);
        Dialog dialog=(Dialog)field.get(module());dialogs.add(dialog);return dialog;
    }
    private GridView grid(View view) {
        if(view instanceof GridView)return (GridView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            GridView result=grid(((ViewGroup)view).getChildAt(i));if(result!=null)return result;
        }
        return null;
    }
    private ThumbnailImageView thumbnail(View view) {
        if(view instanceof ThumbnailImageView)return (ThumbnailImageView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){
            ThumbnailImageView image=thumbnail(((ViewGroup)view).getChildAt(i));if(image!=null)return image;
        }
        return null;
    }
    private boolean hasText(View view,String text) {
        if(view instanceof TextView && text.contentEquals(((TextView)view).getText()))return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)
            if(hasText(((ViewGroup)view).getChildAt(i),text))return true;
        return false;
    }
    private void waitUntil(java.util.function.BooleanSupplier condition) {
        long end=SystemClock.uptimeMillis()+15000;
        while(SystemClock.uptimeMillis()<end){boolean[] ready={false};onMain(()->ready[0]=condition.getAsBoolean());if(ready[0])return;SystemClock.sleep(25);}
        fail("UI did not reach expected state");
    }

    public void testThreeThousandAvatarsRecycleCellsAndLastPersonOpens() {
        Dialog[] album={null};GridView[] list={null};String[] lastId={null};
        onMain(()->{
            TreeState state=new TreeState();
            for(int i=0;i<3000;i++){Person p=state.addPerson("Человек "+i,(i%100)*320,(i/100)*480);p.photoMediaId=mediaId;lastId[0]=p.id;}
            activity.state=state;activity.bindState();
            album[0]=open("showAvatarAlbum",new Class<?>[0]);list[0]=grid(album[0].getWindow().getDecorView());
        });
        waitUntil(()->list[0]!=null&&list[0].getCount()==3000&&list[0].getChildCount()>0);
        onMain(()->{
            assertTrue(list[0].getChildCount()<100);
            Method columns=list[0].getClass().getDeclaredMethod("updateGalleryColumns",int.class);columns.setAccessible(true);columns.invoke(list[0],5);
        });
        waitUntil(()->list[0].getNumColumns()==5);
        waitUntil(()->{
            View cell=list[0].getChildAt(0);
            ThumbnailImageView image=thumbnail(cell);
            return cell.getHeight()<=cell.getWidth()+activity.dp(60)&&image!=null&&image.getDrawable()!=null;
        });
        getInstrumentation().waitForIdleSync();
        SystemClock.sleep(300);
        Bitmap screenshot=getInstrumentation().getUiAutomation().takeScreenshot();
        try (FileOutputStream output=new FileOutputStream(new File(activity.getExternalCacheDir(),"gallery-3000-five-columns.png"))) {
            assertNotNull(screenshot);screenshot.compress(Bitmap.CompressFormat.PNG,100,output);
        } catch (IOException error) { throw new AssertionError(error); }
        finally { if(screenshot!=null)screenshot.recycle(); }
        onMain(()->{
            ThumbnailImageView image=thumbnail(list[0].getChildAt(0));
            Bitmap pixels=Bitmap.createBitmap(image.getWidth(),image.getHeight(),Bitmap.Config.ARGB_8888);
            image.draw(new Canvas(pixels));
            assertEquals(Color.rgb(220,120,70),pixels.getPixel(image.getWidth()/4,image.getHeight()/2));
            assertEquals(Color.rgb(22,160,120),pixels.getPixel(image.getWidth()*3/4,image.getHeight()/2));
            assertEquals("Thumbnail lost rounded corners after recycling",0,Color.alpha(pixels.getPixel(0,0)));
            pixels.recycle();
        });
        onMain(()->{
            org.json.JSONObject stats=new org.json.JSONObject().put("items",list[0].getCount())
                .put("liveCells",list[0].getChildCount()).put("columns",list[0].getNumColumns());
            try (FileOutputStream output=new FileOutputStream(new File(activity.getExternalCacheDir(),"gallery-3000-stats.json"))) {
                output.write(stats.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        });
        onMain(()->list[0].setSelection(2999));
        waitUntil(()->list[0].getLastVisiblePosition()==2999);
        onMain(()->{
            assertTrue(list[0].getChildCount()<100);
            ViewGroup cell=(ViewGroup)list[0].getChildAt(2999-list[0].getFirstVisiblePosition());
            String selectedName=((TextView)((ViewGroup)cell.getChildAt(0)).getChildAt(1)).getText().toString();
            assertTrue(cell.getChildAt(0).performClick());
            Field field=MainActivityPeople.class.getDeclaredField("openPhotoDialog");field.setAccessible(true);
            Dialog child=(Dialog)field.get(module());dialogs.add(child);
            assertTrue(child!=album[0]);assertTrue("Recycled cell opened another person",hasText(child.getWindow().getDecorView(),selectedName));
        });
        waitUntil(()->grid(dialogs.get(dialogs.size()-1).getWindow().getDecorView()).getCount()==1);
    }

    public void testCustomAlbumAndFolderKeepAllPhotoReferences() {
        Dialog[] album={null},folder={null};GridView[] list={null};
        onMain(()->{
            TreeState state=new TreeState();Person p=state.addPerson("Владелец",0,0);p.photoMediaId=mediaId;
            state.photoAlbums.put("Архив",new ArrayList<>(Collections.singletonList(p.id)));
            PhotoAlbumFolder child=new PhotoAlbumFolder("Снимки");child.personIds.add(p.id);
            for(int i=0;i<3000;i++)child.photoMediaIds.add(mediaId);
            state.photoAlbumFolders.put("Архив",new ArrayList<>(Collections.singletonList(child)));
            activity.state=state;activity.bindState();
            album[0]=open("showCustomAlbum",new Class<?>[]{String.class},"Архив");
            folder[0]=open("showFolder",new Class<?>[]{String.class,String.class},"Архив",child.id);
            list[0]=grid(folder[0].getWindow().getDecorView());
        });
        waitUntil(()->list[0].getCount()==3001&&list[0].getChildCount()>0);
        onMain(()->list[0].setSelection(3000));waitUntil(()->list[0].getLastVisiblePosition()==3000);
        onMain(()->assertTrue(list[0].getChildCount()<100));
    }

    public void testCanvasAndGalleryShareDecodedPhotoAndEvictionDoesNotRecycleIt() {
        Object canvas=new Object(),gallery=new Object();Runnable ready=()->{};Bitmap[] found={null,null};
        long length=activity.store.mediaStore().file(mediaId).length();
        onMain(()->{
            ThumbnailLoader.SHARED.trim(true);
            ThumbnailLoader.SHARED.begin(canvas);ThumbnailLoader.SHARED.begin(gallery);
            ThumbnailLoader.SHARED.get(canvas,ready,activity.store.mediaStore(),mediaId,"",64,1);
            ThumbnailLoader.SHARED.get(gallery,ready,activity.store.mediaStore(),mediaId,"",64,2);
        });
        waitUntil(()->{
            found[0]=ThumbnailLoader.SHARED.get(canvas,ready,activity.store.mediaStore(),mediaId,"",64,1);
            found[1]=ThumbnailLoader.SHARED.get(gallery,ready,activity.store.mediaStore(),mediaId,"",64,2);
            return found[0]!=null&&found[1]!=null;
        });
        onMain(()->{
            assertSame(found[0],found[1]);ThumbnailLoader.SHARED.trim(true);assertFalse(found[0].isRecycled());
            ThumbnailLoader.SHARED.cancel(canvas);ThumbnailLoader.SHARED.cancel(gallery);
        });
        assertEquals(length,activity.store.mediaStore().file(mediaId).length());
    }
}
