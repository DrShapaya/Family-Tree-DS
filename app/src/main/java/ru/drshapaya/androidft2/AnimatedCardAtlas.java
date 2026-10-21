package ru.drshapaya.androidft2;

import android.graphics.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Full procedural effects, rasterized into bounded atlases instead of thousands of GPU paths.
 * Only interactive hardware Canvas uses this path; PNG/PDF keep their original vector renderer. */
final class AnimatedCardAtlas {
    // Full smoke envelope reaches 47 world units; front rims need at most 6.
    private static final int BACK_PAD=48,FRONT_PAD=16;
    private static final int GUTTER=2;
    private static final long MAX_FRAME_BYTES=Math.max(2L*1024*1024,
        Math.min(16L*1024*1024,Runtime.getRuntime().maxMemory()/12));
    private final Runnable ready;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Rect source=new Rect();
    private final RectF destination=new RectF();
    private final AtomicReference<Object> busy=new AtomicReference<>();
    private final AtomicLong generation=new AtomicLong();
    private final Object publicationLock=new Object();
    private volatile Frame published;
    private Frame active;
    private Frame lastPresented;
    private long presentedFrames;
    private ExecutorService worker;
    private Key requested;
    /**
     * The visible people list is rebuilt for every frame.  Keep the expensive
     * seed sort only for frames where that list actually changed.
     */
    private long requestedPeopleSignature=Long.MIN_VALUE;
    private final Map<Integer,Frame> entering=new LinkedHashMap<>();
    private long enteringBytes;
    private volatile boolean rejected;

    AnimatedCardAtlas(Runnable ready) { this.ready=ready; }

    void begin(Canvas canvas,List<Person> people,float width,float height,float scale,float seconds,
               boolean fire,boolean smoke,boolean surface,long peopleSignature) {
        active=null;
        long requestedAt=android.os.SystemClock.uptimeMillis();
        if(!canvas.isHardwareAccelerated() || !(fire||smoke||surface)) {
            if(requested!=null)release();return;
        }
        if(people.size()<48)return;
        int[] seeds;
        if(peopleSignature==requestedPeopleSignature && requested!=null) {
            seeds=requested.seeds;
        } else {
            seeds=new int[people.size()];
            for(int i=0;i<seeds.length;i++)seeds[i]=people.get(i).id==null?0:people.get(i).id.hashCode();
            Arrays.sort(seeds);
            int unique=0;
            for(int seed:seeds)if(unique==0 || seeds[unique-1]!=seed)seeds[unique++]=seed;
            seeds=Arrays.copyOf(seeds,unique);
            requestedPeopleSignature=peopleSignature;
        }
        // Round upward: every original path/particle is rasterized, at least at display density.
        float density=(float)Math.ceil(Math.max(.0625f,scale)*16f)/16f;
        Key key=new Key(width,height,density,fire,smoke,surface,seeds);
        if(!key.fits())return; // Preserve the full vector renderer when the memory budget cannot fit.
        Frame latest=published;
        if(requested==null || !requested.sameStyle(key)) {
            synchronized(publicationLock) { generation.incrementAndGet();published=null; }
            entering.clear();enteringBytes=0;rejected=false;latest=null;
        }
        if(latest!=null && !latest.key.sameStyle(key))latest=null;
        requested=key;
        if(rejected) { active=latest;return; }
        if(latest==null) {
            // One software warm-up avoids submitting the original pathological GPU frame.
            // Subsequent animation frames are produced exclusively by the worker.
            try { latest=render(key,seconds,generation.get(),false);published=latest; }
            catch(OutOfMemoryError error) { rejected=true;entering.clear();return; }
        }
        active=latest;
        if(latest!=lastPresented) { lastPresented=latest;presentedFrames++; }
        if(latest!=null && latest.key.equals(key)) { entering.clear();enteringBytes=0; }
        Object ticket=new Object();
        if(!busy.compareAndSet(null,ticket))return;
        if(worker==null || worker.isShutdown())worker=Executors.newSingleThreadExecutor(job -> {
            Thread thread=new Thread(job,"workshop-effect-atlas");thread.setPriority(Thread.NORM_PRIORITY-1);return thread;
        });
        long expected=generation.get();
        worker.execute(() -> {
            try {
                float animationTime=seconds+(android.os.SystemClock.uptimeMillis()-requestedAt)/1000f;
                Frame frame=render(key,animationTime,expected,true);
                synchronized(publicationLock) {
                    if(frame!=null && generation.get()==expected)published=frame;
                }
            } catch(OutOfMemoryError error) {
                // Keep the last complete frame; retry only after the style or viewport changes.
                synchronized(publicationLock) { if(generation.get()==expected)rejected=true; }
            } finally { busy.compareAndSet(ticket,null);ready.run(); }
        });
    }

    boolean contains(int seed) { return active!=null && (active.indices.containsKey(seed)||entering.containsKey(seed)); }
    long presentedFrames() { return presentedFrames; }
    boolean hasActiveFrame() { return active!=null; }

    /** New cards entering during pan retain their complete effect while the next atlas is prepared. */
    void prepare(int seed,float seconds) {
        if(active==null || contains(seed))return;
        try {
            Frame extra=render(new Key(requested.width,requested.height,requested.density,
                requested.fire,requested.smoke,requested.surface,new int[]{seed}),seconds,generation.get(),false);
            if(extra!=null) {
                entering.put(seed,extra);enteringBytes+=extra.bytes();
                Iterator<Frame> old=entering.values().iterator();
                while(enteringBytes>MAX_FRAME_BYTES && old.hasNext()) { enteringBytes-=old.next().bytes();old.remove(); }
            }
        } catch(OutOfMemoryError error) { /* Caller falls back to the original full vector effect. */ }
    }

    void behind(Canvas canvas,int seed) { draw(canvas,seed,true); }
    void front(Canvas canvas,int seed) { draw(canvas,seed,false); }
    private void draw(Canvas canvas,int seed,boolean behind) {
        Frame frame=active;
        if(frame==null)return;
        Integer index=frame.indices.get(seed);
        if(index==null) { frame=entering.get(seed);if(frame==null)return;index=0; }
        Bitmap bitmap=behind?frame.behind:frame.front;
        if(bitmap==null)return;
        int tileW=behind?frame.key.tileW:frame.key.frontTileW,tileH=behind?frame.key.tileH:frame.key.frontTileH;
        int x=(index%frame.key.columns)*tileW,y=(index/frame.key.columns)*tileH;
        source.set(x,y,x+tileW,y+tileH);
        float left=-(behind?BACK_PAD:FRONT_PAD)-GUTTER/frame.key.density,top=left;
        destination.set(left,top,left+tileW/frame.key.density,top+tileH/frame.key.density);
        canvas.drawBitmap(bitmap,source,destination,paint);
    }

    void release() {
        synchronized(publicationLock) { generation.incrementAndGet();published=null; }
        active=null;lastPresented=null;requested=null;requestedPeopleSignature=Long.MIN_VALUE;
        entering.clear();enteringBytes=0;rejected=false;busy.set(null);
        if(worker!=null)worker.shutdownNow();
    }

    private Frame render(Key key,float seconds,long expected,boolean cancellable) {
        Bitmap behind=null,front=null;
        try {
            if(key.fire||key.smoke)behind=Bitmap.createBitmap(key.atlasW,key.atlasH,Bitmap.Config.ARGB_8888);
            front=Bitmap.createBitmap(key.frontAtlasW,key.frontAtlasH,Bitmap.Config.ARGB_8888);
            Canvas backCanvas=behind==null?null:new Canvas(behind),frontCanvas=new Canvas(front);
            FireCardEffect fire=new FireCardEffect();SmokeCardEffect smoke=new SmokeCardEffect();
            Map<Integer,Integer> indices=new HashMap<>();
            for(int i=0;i<key.seeds.length;i++) {
                if(cancellable && (Thread.currentThread().isInterrupted() || generation.get()!=expected))return null;
                int seed=key.seeds[i];indices.put(seed,i);
                int x=(i%key.columns)*key.tileW,y=(i/key.columns)*key.tileH;
                if(backCanvas!=null) {
                    backCanvas.save();backCanvas.clipRect(x,y,x+key.tileW,y+key.tileH);
                    backCanvas.translate(x+GUTTER+BACK_PAD*key.density,y+GUTTER+BACK_PAD*key.density);backCanvas.scale(key.density,key.density);
                    if(key.fire)fire.behind(backCanvas,key.width,key.height,seed,seconds);
                    if(key.smoke)smoke.behind(backCanvas,key.width,key.height,seed,seconds);
                    backCanvas.restore();
                }
                int frontX=(i%key.columns)*key.frontTileW,frontY=(i/key.columns)*key.frontTileH;
                frontCanvas.save();frontCanvas.clipRect(frontX,frontY,frontX+key.frontTileW,frontY+key.frontTileH);
                frontCanvas.translate(frontX+GUTTER+FRONT_PAD*key.density,frontY+GUTTER+FRONT_PAD*key.density);frontCanvas.scale(key.density,key.density);
                if(key.surface)fire.surface(frontCanvas,key.width,key.height,seed,seconds);
                if(key.fire)fire.rim(frontCanvas,key.width,key.height,seed,seconds);
                if(key.smoke)smoke.rim(frontCanvas,key.width,key.height);
                frontCanvas.restore();
            }
            Frame result=new Frame(key,behind,front,indices);behind=null;front=null;return result;
        } finally {
            // Only unpublished buffers can be recycled: displayed bitmaps may still be used by RenderThread.
            if(behind!=null)behind.recycle();if(front!=null)front.recycle();
        }
    }

    private static final class Frame {
        final Key key;final Bitmap behind,front;final Map<Integer,Integer> indices;
        Frame(Key key,Bitmap behind,Bitmap front,Map<Integer,Integer> indices) {
            this.key=key;this.behind=behind;this.front=front;this.indices=indices;
        }
        long bytes() { return (behind==null?0:behind.getAllocationByteCount())+(long)front.getAllocationByteCount(); }
    }
    private static final class Key {
        final float width,height,density;final boolean fire,smoke,surface;final int[] seeds;
        final int tileW,tileH,columns,atlasW,atlasH,frontTileW,frontTileH,frontAtlasW,frontAtlasH;
        Key(float width,float height,float density,boolean fire,boolean smoke,boolean surface,int[] seeds) {
            this.width=width;this.height=height;this.density=density;this.fire=fire;this.smoke=smoke;this.surface=surface;this.seeds=seeds;
            tileW=Math.max(1,(int)Math.ceil((width+BACK_PAD*2)*density))+GUTTER*2;tileH=Math.max(1,(int)Math.ceil((height+BACK_PAD*2)*density))+GUTTER*2;
            frontTileW=Math.max(1,(int)Math.ceil((width+FRONT_PAD*2)*density))+GUTTER*2;
            frontTileH=Math.max(1,(int)Math.ceil((height+FRONT_PAD*2)*density))+GUTTER*2;
            columns=Math.max(1,(int)Math.ceil(Math.sqrt(seeds.length*(double)tileH/tileW)));
            atlasW=columns*tileW;atlasH=((seeds.length+columns-1)/columns)*tileH;
            frontAtlasW=columns*frontTileW;frontAtlasH=((seeds.length+columns-1)/columns)*frontTileH;
        }
        boolean fits() {
            long bytes=(long)frontAtlasW*frontAtlasH*4+((fire||smoke)?(long)atlasW*atlasH*4:0);
            return atlasW<=4096 && atlasH<=4096 && frontAtlasW<=4096 && frontAtlasH<=4096 && bytes<=MAX_FRAME_BYTES;
        }
        boolean sameStyle(Key other) { return width==other.width && height==other.height && fire==other.fire && smoke==other.smoke && surface==other.surface; }
        @Override public boolean equals(Object value) {
            if(!(value instanceof Key))return false;Key other=(Key)value;
            return sameStyle(other)&&density==other.density&&Arrays.equals(seeds,other.seeds);
        }
        @Override public int hashCode() { return Arrays.hashCode(seeds); }
    }
}
