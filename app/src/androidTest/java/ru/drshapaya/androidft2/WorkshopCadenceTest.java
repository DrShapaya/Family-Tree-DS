package ru.drshapaya.androidft2;

import android.content.Intent;
import android.graphics.*;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Hardware checks for the complete card cache, original flame artwork and actual touch input. */
public final class WorkshopCadenceTest extends InstrumentationTestCase {
    private MainActivity activity;
    private TreeCanvasView tree;
    private interface Action { void run() throws Exception; }
    private void ui(Action action) throws Exception {
        AtomicReference<Throwable> error=new AtomicReference<>();
        getInstrumentation().runOnMainSync(()-> {try {action.run();}catch(Throwable e){error.set(e);}});
        if(error.get()!=null)throw new AssertionError(error.get());
    }
    private static Field field(String name) throws Exception {
        Field result=TreeCanvasView.class.getDeclaredField(name);result.setAccessible(true);return result;
    }
    @Override protected void setUp() throws Exception {
        activity=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        TestStartup.await(getInstrumentation(),activity);
    }
    @Override protected void tearDown() throws Exception {
        ui(()-> {if(tree!=null)tree.releaseExportResources();activity.finish();});super.tearDown();
    }
    private TreeState installTree(float zoom) throws Exception {
        TreeState state=new TreeState();state.selectedId="";state.showLifeStatusBadge=true;
        for(int i=0;i<500;i++) {
            Person p=state.addPerson("Иванов Человек "+i,(i%25)*350,(i/25)*300);
            p.bornYear="1980";p.place="Томск";p.lifeStatus=PersonLifeStatus.LIVING;
        }
        ui(()-> {
            tree=new TreeCanvasView(activity);tree.setState(state);activity.setContentView(tree);
            field("scale").setFloat(tree,zoom);field("offsetX").setFloat(tree,20);field("offsetY").setFloat(tree,100);
        });
        return state;
    }
    private Bitmap capture(View view) throws Exception {
        CountDownLatch frames=new CountDownLatch(1);
        ui(()-> {view.getViewTreeObserver().registerFrameCommitCallback(frames::countDown);view.invalidate();});
        assertTrue("Hardware frame was not committed",frames.await(10,TimeUnit.SECONDS));
        int[] location=new int[2];ui(()->view.getLocationInWindow(location));
        Bitmap result=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
        CountDownLatch copied=new CountDownLatch(1);int[] status={-1};
        ui(()->PixelCopy.request(activity.getWindow(),new Rect(location[0],location[1],location[0]+view.getWidth(),location[1]+view.getHeight()),
            result,value->{status[0]=value;copied.countDown();},new android.os.Handler(android.os.Looper.getMainLooper())));
        assertTrue(copied.await(10,TimeUnit.SECONDS));assertEquals(PixelCopy.SUCCESS,status[0]);return result;
    }
    private void save(Bitmap bitmap,String name) throws Exception {
        try(FileOutputStream out=new FileOutputStream(new File(activity.getExternalCacheDir(),"cadence-"+name+".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
        }
    }
    private static double difference(Bitmap a,Bitmap b) {
        long delta=0;int[] first=new int[a.getWidth()*a.getHeight()],second=new int[first.length];
        a.getPixels(first,0,a.getWidth(),0,0,a.getWidth(),a.getHeight());b.getPixels(second,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());
        for(int i=0;i<first.length;i++)for(int shift=0;shift<24;shift+=8)delta+=Math.abs(((first[i]>>shift)&255)-((second[i]>>shift)&255));
        return delta/(double)(first.length*3);
    }
    public void testCachedArtworkAndEditedCardRemainCurrent() throws Exception {
        TreeState state=installTree(.1f);Person person=state.people.values().iterator().next();
        for(float zoom:new float[]{.1f,.55f}) {
            ui(()-> {field("scale").setFloat(tree,zoom);field("debugDisableCardRaster").setBoolean(tree,false);});
            awaitRasterSettled();
            Bitmap cached=capture(tree);save(cached,"cached-"+zoom);
            ui(()->android.util.Log.i("WorkshopCadenceTest",((CardRasterCache)field("cardRasterCache").get(tree)).debugSummary()));
            ui(()->field("debugDisableCardRaster").setBoolean(tree,true));Bitmap direct=capture(tree);
            double error=difference(cached,direct);android.util.Log.i("WorkshopCadenceTest","Cache/native MAE "+zoom+" = "+error);
            assertTrue("Card artwork changed: "+error,error<2.5);cached.recycle();direct.recycle();
        }
        ui(()-> {field("debugDisableCardRaster").setBoolean(tree,false);});Bitmap before=capture(tree);
        ui(()-> {
            person.name="Обновлённая карточка";person.bornYear="2001";person.place="Новосибирск";person.color=Color.MAGENTA;
            person.pinned=true;person.lifeStatus=PersonLifeStatus.DECEASED;state.selectedId=person.id;
            Bitmap photo=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888);photo.eraseColor(Color.CYAN);
            Canvas canvas=new Canvas(photo);Paint paint=new Paint();paint.setColor(Color.RED);canvas.drawRect(0,0,40,80,paint);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();photo.compress(Bitmap.CompressFormat.PNG,100,bytes);photo.recycle();
            person.photo="data:image/png;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
            person.avatarScale=1.5f;person.avatarOffsetX=.25f;
        });
        // Let the real asynchronous thumbnail queue load the replacement image.
        Method photo=TreeCanvasView.class.getDeclaredMethod("photoBitmap",Person.class,boolean.class);photo.setAccessible(true);
        boolean[] ready={false};for(int attempt=0;attempt<100&&!ready[0];attempt++) {
            ui(()->ready[0]=photo.invoke(tree,person,false)!=null);SystemClock.sleep(20);
        }
        assertTrue("Replacement photo never loaded",ready[0]);
        Bitmap edited=capture(tree);save(edited,"edited");
        assertTrue("The edited card kept its stale bitmap",difference(before,edited)>.2);
        ui(()->((CardRasterCache)field("cardRasterCache").get(tree)).clear());Bitmap rebuilt=capture(tree);
        // Other cards may still be warming their cache; compare the entire edited card and its halo.
        int width=Math.round(TreeLayoutEngine.CARD_W*.55f)+20,height=Math.round(TreeLayoutEngine.CARD_H*.55f)+20;
        Bitmap editedCard=Bitmap.createBitmap(edited,10,90,width,height),rebuiltCard=Bitmap.createBitmap(rebuilt,10,90,width,height);
        assertTrue("Cache invalidation missed an edited field: "+difference(editedCard,rebuiltCard),difference(editedCard,rebuiltCard)<.05);
        editedCard.recycle();rebuiltCard.recycle();
        before.recycle();edited.recycle();rebuilt.recycle();
    }

    public void testGestureCacheRefreshIsBoundedAndRetainsAll500CompleteCards() throws Exception {
        installTree(.1f);Bitmap initial=capture(tree);initial.recycle();
        ui(()->field("scale").setFloat(tree,.11f));Bitmap first=capture(tree);
        CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();
        boolean[] running={true};int[] frames={0},maximum={0};long[] last={0};
        Runnable pump=new Runnable() {
            @Override public void run() {
                if(!running[0])return;
                try {
                    CardRasterCache cache=(CardRasterCache)field("cardRasterCache").get(tree);
                    long count=cache.buildsForTests();maximum[0]=Math.max(maximum[0],cache.frameBuildsForTests());last[0]=count;
                    if(cache.cachedCardsForTests()==500&&!cache.needsAnotherFrame()) {done.countDown();return;}
                    if(++frames[0]>600)throw new AssertionError("Visible card cache never completed: "+cache.debugSummary());
                    tree.invalidate();tree.postOnAnimation(this);
                } catch(Throwable failure) {error.set(failure);done.countDown();}
            }
        };
        ui(()-> {last[0]=((CardRasterCache)field("cardRasterCache").get(tree)).buildsForTests();tree.postOnAnimation(pump);});
        try {assertTrue("Cache warm-up timed out",done.await(30,TimeUnit.SECONDS));}
        finally {ui(()-> {running[0]=false;tree.removeCallbacks(pump);});}
        if(error.get()!=null)throw new AssertionError(error.get());
        assertTrue("The whole tree was rebuilt in one frame: "+maximum[0],maximum[0]<=8);
        Bitmap cached=capture(tree);assertTrue("Cache warm-up removed artwork",difference(first,cached)<1);
        first.recycle();cached.recycle();
    }

    private void awaitRasterSettled() throws Exception {
        CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();boolean[] running={true};
        Runnable pump=new Runnable() {int frames,stable;
            @Override public void run() {
                if(!running[0])return;
                try {
                    CardRasterCache cache=(CardRasterCache)field("cardRasterCache").get(tree);
                    stable=cache.needsAnotherFrame()?0:stable+1;
                    if(frames++>2 &&stable>=2) {done.countDown();return;}
                    if(frames>600)throw new AssertionError("Exact-scale cache never settled: "+cache.debugSummary());
                    tree.invalidate();tree.postOnAnimation(this);
                }catch(Throwable failure){error.set(failure);done.countDown();}
            }
        };
        ui(()->tree.postOnAnimation(pump));
        try {assertTrue(done.await(30,TimeUnit.SECONDS));}
        finally {ui(()-> {running[0]=false;tree.removeCallbacks(pump);});}
        if(error.get()!=null)throw new AssertionError(error.get());
    }

    public void testHeldPinchAndZoomOutMatchCurrentVectorArtwork() throws Exception {
        installTree(.1f);awaitRasterSettled();
        long now=SystemClock.uptimeMillis();
        ui(()->{
            touch(now,now,MotionEvent.ACTION_DOWN,250,800);
            touch(now,now+20,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),250,800,750,800);
            touch(now,now+40,MotionEvent.ACTION_MOVE,150,800,850,800);
            touch(now,now+60,MotionEvent.ACTION_MOVE,100,800,900,800);
            assertTrue(((ScaleGestureDetector)field("scaleDetector").get(tree)).isInProgress());
        });
        // Hold the actual detector active while visiting distant and close
        // scales. Compare the card region, not the mostly empty full screen.
        for(float zoom:new float[]{.05f,.08f,.12f,.27f,.29f,.65f,1.4f}) {
            ui(()->{field("scale").setFloat(tree,zoom);field("offsetX").setFloat(tree,20);
                field("offsetY").setFloat(tree,100);field("debugDisableCardRaster").setBoolean(tree,false);});
            Bitmap held=capture(tree);
            ui(()->field("debugDisableCardRaster").setBoolean(tree,true));Bitmap direct=capture(tree);
            int w=Math.min(held.getWidth()-10,(int)Math.ceil(TreeLayoutEngine.CARD_W*zoom)+20);
            int h=Math.min(held.getHeight()-90,(int)Math.ceil(TreeLayoutEngine.CARD_H*zoom)+20);
            Bitmap a=Bitmap.createBitmap(held,10,90,w,h),b=Bitmap.createBitmap(direct,10,90,w,h);
            double error=difference(a,b);
            assertTrue("Held pinch reused blurred artwork at "+zoom+": "+error,error<.1);
            save(held,"held-pinch-"+zoom);a.recycle();b.recycle();held.recycle();direct.recycle();
        }
        ui(()->touch(now,SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,100,800,900,800));
        for(float zoom:new float[]{.05f,.08f,.12f,.29f,.65f}) {
            ui(()->{field("scale").setFloat(tree,zoom);field("offsetX").setFloat(tree,20);
                field("offsetY").setFloat(tree,100);field("debugDisableCardRaster").setBoolean(tree,false);});
            awaitRasterSettled();Bitmap cached=capture(tree);
            ui(()->field("debugDisableCardRaster").setBoolean(tree,true));Bitmap direct=capture(tree);
            int w=(int)Math.ceil(TreeLayoutEngine.CARD_W*zoom)+20,h=(int)Math.ceil(TreeLayoutEngine.CARD_H*zoom)+20;
            Bitmap a=Bitmap.createBitmap(cached,10,90,w,h),b=Bitmap.createBitmap(direct,10,90,w,h);
            double error=difference(a,b);
            assertTrue("Settled artwork changed after pinch at "+zoom+": "+error,error<1);
            a.recycle();b.recycle();cached.recycle();direct.recycle();
        }
    }

    public void testReusedThumbnailRequestObservesSourceSizeAndEviction() throws Exception {
        Object owner=new Object();Runnable ready=()->{};ThumbnailLoader.Request request=new ThumbnailLoader.Request();
        String red=inlinePhoto(Color.RED),blue=inlinePhoto(Color.BLUE);
        try {
            Bitmap first=awaitPhoto(owner,ready,request,red,16);
            assertSame(first,awaitPhoto(owner,ready,request,red,16));
            Bitmap changed=awaitPhoto(owner,ready,request,blue,16);
            assertEquals(Color.BLUE,changed.getPixel(0,0));assertNotSame(first,changed);
            Bitmap large=awaitPhoto(owner,ready,request,blue,64);assertNotSame(changed,large);
            assertTrue(large.getWidth()>=changed.getWidth());
            ThumbnailLoader.SHARED.trim(true);
            Bitmap reloaded=awaitPhoto(owner,ready,request,blue,64);assertNotSame(large,reloaded);
            assertEquals(Color.BLUE,reloaded.getPixel(0,0));
        } finally {ThumbnailLoader.SHARED.cancel(owner);}
    }
    private Bitmap awaitPhoto(Object owner,Runnable ready,ThumbnailLoader.Request request,String inline,int size) throws Exception {
        Bitmap[] result={null};
        for(int i=0;i<100 &&result[0]==null;i++) {
            ui(()->result[0]=ThumbnailLoader.SHARED.get(owner,ready,null,"",inline,size,1,request));
            if(result[0]==null)SystemClock.sleep(20);
        }
        assertNotNull("Thumbnail did not load",result[0]);return result[0];
    }
    private static String inlinePhoto(int color) {
        Bitmap bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);bitmap.eraseColor(color);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();
        return "data:image/png;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
    }
    public void testGpuFireMatchesOriginalFlamesAndAllParticles() throws Exception {
        if(android.os.Build.VERSION.SDK_INT<33)return;
        final class Comparison extends View {
            final FireCardEffect original=new FireCardEffect();final GpuFireCardEffect gpu=new GpuFireCardEffect(activity);
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);float time,requestedDensity=1;boolean prepared;float density;int cellWidth,cellHeight;
            Comparison(){super(activity);}
            @Override protected void onDraw(Canvas canvas) {
                canvas.drawColor(0xff151b23);density=Math.min(requestedDensity,(getWidth()/2f-20)/416f);
                canvas.translate(20,100);
                cellWidth=Math.round(416*density);cellHeight=Math.round(276*density);
                prepared=gpu.prepare(canvas,320,180);
                for(int row=0;row<3;row++)for(int column=0;column<2;column++) {
                    int seed=new int[]{17,25,1027}[row];canvas.save();canvas.translate(column*cellWidth+48*density,row*cellHeight+48*density);canvas.scale(density,density);
                    if(column==0)original.behind(canvas,320,180,seed,time);else if(prepared)gpu.behind(canvas,320,180,seed,time,density);
                    paint.setColor(0xff483528);canvas.drawRoundRect(0,0,320,180,10,10,paint);
                    if(column==0) {original.surface(canvas,320,180,seed,time);original.rim(canvas,320,180,seed,time);}
                    else if(prepared) {gpu.surface(canvas,320,180,seed,time,density);gpu.rim(canvas,320,180,seed,time,density);}
                    canvas.restore();
                }
            }
        }
        Comparison[] holder={null};ui(()-> {holder[0]=new Comparison();activity.setContentView(holder[0]);});
        Comparison comparison=holder[0];
        for(float density:new float[]{.1f,.55f,1})for(float time:new float[]{0,2.4f,9.8f}) {
            ui(()-> {comparison.time=time;comparison.requestedDensity=density;});Bitmap screen=capture(comparison);assertTrue(comparison.gpu.failureReason(),comparison.prepared);
            save(screen,"gpu-native-"+density+"-"+time);
            Bitmap a=Bitmap.createBitmap(screen,20,100,comparison.cellWidth,comparison.cellHeight*3);
            Bitmap b=Bitmap.createBitmap(screen,20+comparison.cellWidth,100,comparison.cellWidth,comparison.cellHeight*3);
            double error=difference(a,b);android.util.Log.i("WorkshopCadenceTest","GPU/native MAE scale="+density+" time="+time+" = "+error);
            // At 10% the subpixel flames use analytical AA instead of Skia's path coverage.
            assertTrue("GPU flame artwork differs: "+error,error<(density<.5f?2.5:1.5));a.recycle();b.recycle();screen.recycle();
        }
    }
    public void testPinchTracksFingersWithoutDelayedSettleAndPanKeepsCards() throws Exception {
        installTree(.1f);
        Bitmap initial=capture(tree);initial.recycle();
        ui(()-> {
            long now=SystemClock.uptimeMillis();
            touch(now,now,MotionEvent.ACTION_DOWN,350,500);
            touch(now,now+20,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),350,500,650,500);
            touch(now,now+40,MotionEvent.ACTION_MOVE,290,500,710,500);
            touch(now,now+60,MotionEvent.ACTION_MOVE,250,500,750,500);
            ScaleGestureDetector detector=(ScaleGestureDetector)field("scaleDetector").get(tree);
            float before=field("scale").getFloat(tree);
            touch(now,now+75,MotionEvent.ACTION_MOVE,150,500,850,500);
            float after=field("scale").getFloat(tree);
            assertTrue("Detector never started pinch",detector.isInProgress());
            assertTrue("Pinch scale lagged behind fingers: "+before+" -> "+after,after>before*1.1f);
            assertFalse("A settle animation fights the active pinch",field("zoomAnimationRunning").getBoolean(tree));
            assertEquals("The world point under the pinch centre moved",4800f,(500-field("offsetX").getFloat(tree))/after,2f);
            touch(now,now+80,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),150,500,850,500);
            touch(now,now+100,MotionEvent.ACTION_UP,150,500);
            float x=field("offsetX").getFloat(tree),y=field("offsetY").getFloat(tree);
            touch(now+200,now+200,MotionEvent.ACTION_DOWN,15,20);
            touch(now+200,now+230,MotionEvent.ACTION_MOVE,70,65);
            assertEquals(x+55,field("offsetX").getFloat(tree),.01f);assertEquals(y+45,field("offsetY").getFloat(tree),.01f);
            touch(now+200,now+250,MotionEvent.ACTION_UP,70,65);
        });
        Bitmap image=capture(tree);image.recycle();
        ui(()->assertEquals(500,((TreeState)field("state").get(tree)).people.size()));
    }
    private void touch(long down,long time,int action,float... coordinates) {
        int count=coordinates.length/2;MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] positions=new MotionEvent.PointerCoords[count];
        for(int i=0;i<count;i++) {properties[i]=new MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            positions[i]=new MotionEvent.PointerCoords();positions[i].x=coordinates[i*2];positions[i].y=coordinates[i*2+1];positions[i].pressure=1;positions[i].size=1;}
        MotionEvent event=MotionEvent.obtain(down,time,action,count,properties,positions,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);
        try{tree.onTouchEvent(event);}finally{event.recycle();}
    }
}
