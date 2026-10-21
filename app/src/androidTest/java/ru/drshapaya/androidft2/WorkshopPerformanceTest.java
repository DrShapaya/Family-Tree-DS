package ru.drshapaya.androidft2;

import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.test.InstrumentationTestCase;
import android.view.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Repeatable full-content workshop matrix; timings are observations, never pass/fail budgets. */
public final class WorkshopPerformanceTest extends InstrumentationTestCase {
    private MainActivity activity;
    private TreeCanvasView view;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private interface Action { void run() throws Exception; }
    private void ui(Action action) throws Exception {
        failure.set(null);
        getInstrumentation().runOnMainSync(() -> { try { action.run(); } catch (Throwable e) { failure.set(e); } });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
    @Override protected void setUp() throws Exception {
        activity = (MainActivity) getInstrumentation().startActivitySync(new Intent(
            getInstrumentation().getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        TestStartup.await(getInstrumentation(), activity);
        Bundle arguments=((android.test.InstrumentationTestRunner)getInstrumentation()).getArguments();
        TreeState fixture=tree("true".equals(arguments.getString("workshopPhotos","false")));
        ui(() -> { view = new TreeCanvasView(activity); view.setState(fixture); view.setFamilyLineMode("centered"); });
    }
    @Override protected void tearDown() throws Exception {
        ui(() -> { view.setVisibility(View.GONE); view.releaseExportResources(); activity.finish(); });
        super.tearDown();
    }
    private static TreeState tree(boolean photos) throws Exception {
        TreeState result = new TreeState();
        for (int i = 0; i < 500; i++) {
            Person person = new Person("workshop-person-" + i);
            person.name = "Иванов Человек " + i; person.bornYear = "1980"; person.place = "Томск";
            person.x = (i % 25) * 350; person.y = (i / 25) * 300;
            person.color = Color.HSVToColor(new float[]{i * 137.5f % 360, .4f, .85f});
            if(photos) {
                Bitmap avatar=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);avatar.eraseColor(person.color);
                Canvas art=new Canvas(avatar);Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);pen.setColor(Color.WHITE);
                art.drawCircle(15+i%30,20+i%17,7+i%9,pen);
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();avatar.compress(Bitmap.CompressFormat.PNG,100,bytes);avatar.recycle();
                person.photo="data:image/png;base64,"+android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);
            }
            result.people.put(person.id, person);
            if (i >= 25) result.links.add(new Relation("parent-" + i, "parent", "workshop-person-" + (i - 25), person.id));
            if (i % 5 == 1) result.links.add(new Relation("sibling-" + i, "sibling", "workshop-person-" + (i - 1), person.id));
        }
        result.selectedId = ""; result.rootId = "workshop-person-0";
        return result;
    }
    private static final class Case {
        final String name, card, edge, background, link;
        Case(String name, String card, String edge, String background, String link) {
            this.name=name;this.card=card;this.edge=edge;this.background=background;this.link=link;
        }
    }
    private List<Case> cases() {
        List<Case> result = new ArrayList<>();
        result.add(new Case("standard", "standard", "standard", "standard", "standard"));
        for (RewardCatalog.Item item : RewardCatalog.items()) {
            String category=item.categoryId;
            if (!Arrays.asList(RewardCatalog.CARD_THEMES,RewardCatalog.CARD_EDGES,
                    RewardCatalog.CANVAS_BACKGROUNDS,RewardCatalog.LINK_STYLES).contains(category)) continue;
            result.add(new Case(item.id, RewardCatalog.CARD_THEMES.equals(category)?item.id:"standard",
                RewardCatalog.CARD_EDGES.equals(category)?item.id:"standard",
                RewardCatalog.CANVAS_BACKGROUNDS.equals(category)?item.id:"standard",
                RewardCatalog.LINK_STYLES.equals(category)?item.id:"standard"));
        }
        result.add(new Case("all-fire","card_theme_fire","card_edge_fire","canvas_background_paper","link_style_fire"));
        result.add(new Case("all-smoke-ribbon","card_theme_smoke","card_edge_smoke","canvas_background_deep_blueprint","link_style_ribbon"));
        result.add(new Case("all-botanical","card_theme_botanical","card_edge_botanical","canvas_background_botanical","link_style_botanical"));
        result.add(new Case("fire-with-leaves","card_theme_fire","card_edge_botanical","canvas_background_garden","link_style_ribbon"));
        result.add(new Case("leaves-with-smoke","card_theme_botanical","card_edge_smoke","canvas_background_paper","link_style_botanical"));
        result.add(new Case("smoke-with-fire","card_theme_smoke","card_edge_fire","canvas_background_deep_blueprint","link_style_fire"));
        result.add(new Case("glass-with-fire","card_theme_glass","card_edge_fire","canvas_background_garden","link_style_card_colors"));
        String only=((android.test.InstrumentationTestRunner)getInstrumentation()).getArguments().getString("workshopOnly","");
        if(!only.isEmpty())result.removeIf(item->!Arrays.asList(only.split(",")).contains(item.name));
        return result;
    }
    private void apply(Case c) {
        view.setCardStyle(c.card);view.setCardEdgeStyle(c.edge);view.setCanvasStyle(c.background);view.setLinkStyle(c.link);
    }
    private static void set(Object target,String name,float value) throws Exception {
        Field field=TreeCanvasView.class.getDeclaredField(name);field.setAccessible(true);field.setFloat(target,value);
    }
    public void testSoftwareMatrix500People() throws Exception {
        JSONArray results=new JSONArray();
        ui(() -> { view.layout(0,0,900,1300);set(view,"scale",.1f);set(view,"offsetX",20);set(view,"offsetY",200); });
        Bitmap bitmap=Bitmap.createBitmap(900,1300,Bitmap.Config.ARGB_8888);
        try {
            for (Case c:cases()) {
                double[] samples=new double[5];double[] cold={0};
                ui(() -> apply(c));
                {
                    long start=System.nanoTime();view.draw(new Canvas(bitmap));cold[0]=(System.nanoTime()-start)/1e6;
                    for(int i=0;i<samples.length;i++) { start=System.nanoTime();view.draw(new Canvas(bitmap));samples[i]=(System.nanoTime()-start)/1e6; }
                }
                Arrays.sort(samples);
                results.put(new JSONObject().put("style",c.name).put("coldMs",cold[0]).put("medianMs",samples[2]).put("maxMs",samples[4]));
                save("workshop-software.json",results);
            }
        } finally { bitmap.recycle(); }
    }
    public void testHardwarePanMatrix500People() throws Exception {
        Bundle arguments=((android.test.InstrumentationTestRunner)getInstrumentation()).getArguments();
        int frames=Integer.parseInt(arguments.getString("workshopFrames","32"));
        String motion=arguments.getString("workshopMotion","pan");
        float zoomAmplitude=Float.parseFloat(arguments.getString("workshopZoomAmplitude","0.45"));
        boolean settle="true".equals(arguments.getString("workshopSettle","false"));
        List<String> skip=Arrays.asList(arguments.getString("workshopSkip","").split(","));
        HandlerThread metricsThread=new HandlerThread("workshop-frame-metrics");metricsThread.start();
        AtomicReference<Sample> current=new AtomicReference<>();
        Field stages=TreeCanvasView.class.getDeclaredField("debugFrameStages");stages.setAccessible(true);
        Window.OnFrameMetricsAvailableListener listener=(window,metrics,dropped)-> {
            Sample sample=current.get();
            if(sample!=null)try { sample.add(metrics,dropped,(long[])stages.get(view)); }
            catch(IllegalAccessException error) { failure.set(error); }
        };
        ui(() -> {
            ViewGroup content=activity.findViewById(android.R.id.content);content.removeAllViews();content.addView(view);
            activity.getWindow().addOnFrameMetricsAvailableListener(listener,new Handler(metricsThread.getLooper()));
        });
        getInstrumentation().waitForIdleSync();
        JSONArray results=new JSONArray();
        boolean[] pumping={true};
        Field panOffset=TreeCanvasView.class.getDeclaredField("offsetX");panOffset.setAccessible(true);
        Runnable pump=new Runnable() { int frame;long down;
            @Override public void run() {
                if(!pumping[0])return;
                try {
                    if("pinch".equals(motion)) {
                        long now=SystemClock.uptimeMillis();
                        if(frame==0) {
                            down=now;touch(down,now,MotionEvent.ACTION_DOWN,250,800);
                            touch(down,now,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),250,800,750,800);
                        }
                        float half=300*(float)Math.exp(zoomAmplitude*(float)Math.sin(frame*.018));
                        touch(down,now,MotionEvent.ACTION_MOVE,500-half,800,500+half,800);frame++;
                    } else if("entering".equals(motion)) {
                        panOffset.setFloat(view,20-700*(1+(float)Math.sin(frame++*.015)));
                    } else panOffset.setFloat(view,20+(frame++%20)*.5f);
                } catch(Exception error) { failure.set(error); }
                view.invalidate();view.postOnAnimation(this);
            }
        };
        try {
            ui(() -> {set(view,"scale","entering".equals(motion)?.35f:.1f);set(view,"offsetY",200);if(!settle)view.postOnAnimation(pump);});
            if("true".equals(arguments.getString("workshopPhotos","false")))awaitPhotos();
            if(settle) {awaitCacheWarm();ui(()->view.postOnAnimation(pump));}
            for(Case c:cases()) {
                if(skip.contains(c.name))continue;
                current.set(null);ui(() -> apply(c));
                if(settle) { awaitCacheWarm(); awaitEffectWarm(c); }
                Sample sample=new Sample(frames);current.set(sample);
                assertTrue("Timed out: "+c.name,sample.done.await(60,TimeUnit.SECONDS));
                current.set(null);
                ui(() -> {
                    Field visible=TreeCanvasView.class.getDeclaredField("visiblePeopleScratch");visible.setAccessible(true);
                    if("pan".equals(motion))assertEquals("The benchmark must retain all 500 cards",500,((List<?>)visible.get(view)).size());
                    Field state=TreeCanvasView.class.getDeclaredField("state");state.setAccessible(true);
                    assertEquals(500,((TreeState)state.get(view)).people.size());
                    if(Build.VERSION.SDK_INT>=33 &&("card_edge_fire".equals(c.edge)||"card_theme_fire".equals(c.card))) {
                        Field active=TreeCanvasView.class.getDeclaredField("gpuFireForFrame");active.setAccessible(true);
                        Field renderer=TreeCanvasView.class.getDeclaredField("gpuFireCardEffect");renderer.setAccessible(true);
                        Field atlas=TreeCanvasView.class.getDeclaredField("animatedCardAtlas");atlas.setAccessible(true);
                        boolean gpu=active.getBoolean(view);
                        boolean atlasActive=(boolean)atlas.get(view).getClass().getDeclaredMethod("hasActiveFrame").invoke(atlas.get(view));
                        assertTrue("Fire renderer unavailable: "+((GpuFireCardEffect)renderer.get(view)).failureReason(),gpu||atlasActive);
                    }
                });
                JSONObject result=sample.json(c.name).put("motion",motion);
                android.os.Debug.MemoryInfo memory=new android.os.Debug.MemoryInfo();android.os.Debug.getMemoryInfo(memory);
                result.put("pssKiB",memory.getTotalPss()).put("nativeHeapBytes",android.os.Debug.getNativeHeapAllocatedSize())
                    .put("javaHeapBytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())
                    .put("maxHeapBytes",Runtime.getRuntime().maxMemory());
                results.put(result);save("workshop-hardware.json",results);
                Bitmap screenshot=getInstrumentation().getUiAutomation().takeScreenshot();
                if(screenshot!=null) {
                    try(FileOutputStream output=new FileOutputStream(new File(activity.getExternalCacheDir(),label("workshop-"+c.name+".png")))) {
                        screenshot.compress(Bitmap.CompressFormat.PNG,100,output);
                    } finally { screenshot.recycle(); }
                }
            }
        } finally {
            current.set(null);ui(() -> { pumping[0]=false;activity.getWindow().removeOnFrameMetricsAvailableListener(listener); });
            metricsThread.quitSafely();
        }
    }
    private static final class Sample {
        int warmup=6,dropped;
        final int frames;
        final List<Double> draw=new ArrayList<>(),total=new ArrayList<>();
        final List<Double> background=new ArrayList<>(),links=new ArrayList<>(),cards=new ArrayList<>(),intervals=new ArrayList<>();
        long firstVsync,lastVsync,firstAtlas,lastAtlas,firstGpu,lastGpu,firstBuild,lastBuild,maxBuildBatch;
        final long allocatedBefore=runtime("art.gc.bytes-allocated"),gcBefore=runtime("art.gc.gc-count");
        final List<Double> input=new ArrayList<>(),sync=new ArrayList<>(),gpu=new ArrayList<>();
        final CountDownLatch done=new CountDownLatch(1);
        Sample(int frames) { this.frames=Math.max(8,frames); }
        synchronized void add(FrameMetrics metrics,int drops,long[] stages) {
            if(warmup-->0 || draw.size()>=frames)return;
            long vsync=metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP);
            if(lastVsync!=0)intervals.add((vsync-lastVsync)/1e6);else firstVsync=vsync;
            lastVsync=vsync;
            if(stages!=null) {
                background.add(stages[0]/1e6);links.add(stages[1]/1e6);cards.add(stages[2]/1e6);
                if(draw.isEmpty())firstAtlas=stages[3];lastAtlas=stages[3];
                if(stages.length>4) { if(draw.isEmpty())firstGpu=stages[4];lastGpu=stages[4]; }
                if(stages.length>5) {if(draw.isEmpty())firstBuild=lastBuild=stages[5];lastBuild=stages[5];}
                if(stages.length>6)maxBuildBatch=Math.max(maxBuildBatch,stages[6]);
            }
            draw.add(metrics.getMetric(FrameMetrics.DRAW_DURATION)/1e6);
            total.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION)/1e6);dropped+=drops;
            input.add(metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION)/1e6);
            sync.add(metrics.getMetric(FrameMetrics.SYNC_DURATION)/1e6);
            if(Build.VERSION.SDK_INT>=31)gpu.add(metrics.getMetric(FrameMetrics.GPU_DURATION)/1e6);
            if(draw.size()==frames)done.countDown();
        }
        synchronized JSONObject json(String style) throws Exception {
            Collections.sort(draw);Collections.sort(total);
            int middle=draw.size()/2,tail=Math.min(draw.size()-1,(int)Math.ceil(draw.size()*.95)-1);
            JSONObject result=new JSONObject().put("style",style).put("samples",draw.size()).put("drawP50Ms",draw.get(middle))
                .put("drawP95Ms",draw.get(tail)).put("totalP50Ms",total.get(middle)).put("totalP95Ms",total.get(tail))
                .put("listenerDropped",dropped);
            result.put("drawP99Ms",percentile(draw,.99)).put("drawMaxMs",percentile(draw,1))
                .put("totalP99Ms",percentile(total,.99)).put("totalMaxMs",percentile(total,1))
                .put("inputP99Ms",percentile(input,.99)).put("syncP99Ms",percentile(sync,.99))
                .put("gpuP99Ms",percentile(gpu,.99)).put("allocatedBytes",runtime("art.gc.bytes-allocated")-allocatedBefore)
                .put("gcCount",runtime("art.gc.gc-count")-gcBefore).put("cardRasterBuilds",lastBuild-firstBuild)
                .put("maxRasterBuildsPerFrame",maxBuildBatch);
            if(!cards.isEmpty()) {
                Collections.sort(background);Collections.sort(links);Collections.sort(cards);Collections.sort(intervals);
                result.put("backgroundP50Ms",background.get(background.size()/2)).put("linksP50Ms",links.get(links.size()/2))
                    .put("cardsP50Ms",cards.get(cards.size()/2)).put("appFrameIntervalP50Ms",intervals.get(intervals.size()/2))
                    .put("appFrameIntervalP99Ms",percentile(intervals,.99)).put("cardsP99Ms",percentile(cards,.99))
                    .put("backgroundP99Ms",percentile(background,.99)).put("linksP99Ms",percentile(links,.99))
                    .put("cardsMaxMs",percentile(cards,1))
                    .put("atlasFramesPresented",lastAtlas-firstAtlas)
                    .put("atlasPresentationHz",(lastAtlas-firstAtlas)*1e9/Math.max(1,lastVsync-firstVsync));
                result.put("gpuEffectFramesPresented",lastGpu-firstGpu)
                    .put("gpuEffectPresentationHz",(lastGpu-firstGpu)*1e9/Math.max(1,lastVsync-firstVsync));
            }
            return result;
        }
        static double percentile(List<Double> values,double fraction) {
            if(values.isEmpty())return -1;Collections.sort(values);return values.get(Math.min(values.size()-1,(int)Math.ceil(values.size()*fraction)-1));
        }
        static long runtime(String key) { try{return Long.parseLong(Debug.getRuntimeStat(key));}catch(Exception ignored){return -1;} }
    }
    private void touch(long down,long time,int action,float... coordinates) {
        int count=coordinates.length/2;MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] positions=new MotionEvent.PointerCoords[count];
        for(int i=0;i<count;i++) { properties[i]=new MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            positions[i]=new MotionEvent.PointerCoords();positions[i].x=coordinates[i*2];positions[i].y=coordinates[i*2+1];positions[i].pressure=1;positions[i].size=1; }
        MotionEvent event=MotionEvent.obtain(down,time,action,count,properties,positions,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        try{view.onTouchEvent(event);}finally{event.recycle();}
    }
    private void awaitCacheWarm() throws Exception {
        Field cacheField=TreeCanvasView.class.getDeclaredField("cardRasterCache");cacheField.setAccessible(true);
        Object cache=cacheField.get(view);java.lang.reflect.Method pending;
        try {pending=cache.getClass().getDeclaredMethod("needsAnotherFrame");pending.setAccessible(true);}
        catch(NoSuchMethodException baseline) {pending=null;}
        final java.lang.reflect.Method query=pending;
        AtomicReference<Throwable> warmFailure=new AtomicReference<>();
        CountDownLatch done=new CountDownLatch(1);boolean[] active={true};
        Runnable warm=new Runnable() {int frames,stable;
            @Override public void run() {
                if(!active[0])return;
                try {
                    stable=query!=null &&(boolean)query.invoke(cache)?0:stable+1;
                    if(frames++>3 &&stable>=2) {done.countDown();return;}
                    view.invalidate();view.postOnAnimation(this);
                }catch(Throwable error){warmFailure.set(error);done.countDown();}
            }
        };
        ui(()->view.postOnAnimation(warm));
        try {assertTrue("Cache warm-up timed out",done.await(30,TimeUnit.SECONDS));}
        finally {ui(()-> {active[0]=false;view.removeCallbacks(warm);});}
        if(warmFailure.get()!=null)throw new AssertionError(warmFailure.get());
    }
    private void awaitPhotos() throws Exception {
        Field stateField=TreeCanvasView.class.getDeclaredField("state");stateField.setAccessible(true);
        java.lang.reflect.Method photo=TreeCanvasView.class.getDeclaredMethod("photoBitmap",Person.class,boolean.class);photo.setAccessible(true);
        long deadline=SystemClock.uptimeMillis()+20_000;
        int[] ready={0};
        do {
            ui(()-> {
                ready[0]=0;
                for(Person person:((TreeState)stateField.get(view)).people.values())if(photo.invoke(view,person,false)!=null)ready[0]++;
            });
            if(ready[0]==500)return;
            SystemClock.sleep(50);
        }while(SystemClock.uptimeMillis()<deadline);
        fail("Only "+ready[0]+" / 500 photos loaded before timing");
    }
    private void awaitEffectWarm(Case item) throws Exception {
        if(!"card_edge_fire".equals(item.edge) && !"card_theme_fire".equals(item.card))return;
        Field atlasField=TreeCanvasView.class.getDeclaredField("animatedCardAtlas");atlasField.setAccessible(true);
        Object atlas=atlasField.get(view);java.lang.reflect.Method active=atlas.getClass().getDeclaredMethod("hasActiveFrame");active.setAccessible(true);
        CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();boolean[] running={true};
        Runnable warm=new Runnable(){int frames;
            @Override public void run(){if(!running[0])return;try{
                if(frames++>3 &&(boolean)active.invoke(atlas)){done.countDown();return;}
                if(frames>600)throw new AssertionError("Effect atlas warm-up timed out");
                view.invalidate();view.postOnAnimation(this);
            }catch(Throwable failure){error.set(failure);done.countDown();}}
        };
        ui(()->view.postOnAnimation(warm));
        try{assertTrue("Effect atlas warm-up timed out",done.await(30,TimeUnit.SECONDS));}
        finally{ui(()->{running[0]=false;view.removeCallbacks(warm);});}
        if(error.get()!=null)throw new AssertionError(error.get());
    }
    private void save(String name,JSONArray result) throws Exception {
        android.util.Log.i("WorkshopPerf", label(name)+" "+result.toString());
        try(FileOutputStream output=new FileOutputStream(new File(activity.getExternalCacheDir(),label(name)))) {
            output.write(result.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    private String label(String name) {
        String label=((android.test.InstrumentationTestRunner)getInstrumentation()).getArguments().getString("workshopLabel","");
        return label.isEmpty()?name:name.replace("workshop-","workshop-"+label+"-");
    }
}
