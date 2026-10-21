package ru.drshapaya.androidft2;

import android.graphics.*;
import java.util.*;

/** Complete card artwork: native vector commands on API 29+, exact-scale raster fallback on older Android. */
final class CardRasterCache {
    interface Renderer { void draw(Canvas canvas,Person person,int layer); }
    private final CardDisplayListCache commands=android.os.Build.VERSION.SDK_INT>=29?new CardDisplayListCache():null;
    // Keep complete card rasters resident while a large tree is moved. The
    // cap is bounded for low-memory devices, while flagship devices can keep
    // the full 500-card viewport without repeatedly rebuilding artwork.
    private final long budget=Math.max(2L*1024*1024,Math.min(32L*1024*1024,Runtime.getRuntime().maxMemory()/6));
    private final LinkedHashMap<Person,Entry> entries=new LinkedHashMap<>(64,.75f,true);
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF destination=new RectF();
    private long bytes,debugBuilds,frame,frameBuildNanos;
    private int frameBuilds;
    private boolean initialFrame,pending,activeEvicted,allocationFailed;
    void beginFrame() {
        if(commands!=null)commands.beginFrame();
        frame++;initialFrame=entries.isEmpty();frameBuilds=0;frameBuildNanos=0;
        pending=false;activeEvicted=false;allocationFailed=false;
    }
    boolean needsAnotherFrame() {return commands!=null?commands.needsAnotherFrame():pending&&!activeEvicted&&!allocationFailed;}
    long buildsForTests() {return commands!=null?commands.builds():debugBuilds;}
    int frameBuildsForTests() {return commands!=null?commands.frameBuilds():frameBuilds;}
    int cachedCardsForTests() {return commands!=null?commands.size():entries.size();}
    String debugSummary() {return commands!=null?"vector entries="+commands.size()+" builds="+commands.builds()
        :"raster entries="+entries.size()+" bytes="+bytes+" builds="+debugBuilds;}

    private static final class Entry {
        final long signature;final int layer;
        int pad;
        float density,referenceScale;
        Bitmap bitmap;
        long lastFrame;
        Entry(long signature,int layer) {this.signature=signature;this.layer=layer;}
    }
    void clear() {entries.clear();bytes=0;if(commands!=null)commands.clear();}

    boolean draw(Canvas canvas,Person person,int layer,long signature,float width,float height,float scale,
                 float left,float top,boolean exact,Renderer renderer) {
        if(!canvas.isHardwareAccelerated() ||!Float.isFinite(scale)||scale<=0)return false;
        if(commands!=null)return commands.draw(canvas,person,layer,signature,width,height,scale,left,top,exact,renderer);
        Entry entry=entries.get(person);
        boolean retained=entry!=null &&entry.signature==signature &&entry.layer==layer;
        if(!retained)entry=new Entry(signature,layer);
        entry.lastFrame=frame;
        // Artwork includes screen-space stroke widths, text fitting and badge
        // geometry. It is valid ONLY at the scale at which it was rendered:
        // scaling a complete card changes those details and blurs the text.
        boolean valid=entry.bitmap!=null &&entry.referenceScale==scale;
        boolean eligible=true;
        // Never allocate a new raster while the scale detector is active.
        // Allocator and text raster spikes are visible as a short hitch even
        // when only a few cards cross a density bucket. Exact replacements
        // resume on the first settled frame after the gesture.
        if(!valid &&exact&&!allocationFailed&&(initialFrame ||(frameBuilds<8 &&frameBuildNanos<2_000_000L))) {
            float density=scale;
            int pad=(int)Math.ceil(layer==0?Math.max(48*density,8*density/scale):2*density/scale)+2;
            int pixelsW=(int)Math.ceil(width*density)+pad*2,pixelsH=(int)Math.ceil(height*density)+pad*2;
            eligible=pixelsW<=4096 &&pixelsH<=4096 &&(long)pixelsW*pixelsH*4<=budget;
            if(eligible) {
                Bitmap bitmap=null;long start=System.nanoTime();frameBuilds++;
                try {
                    bitmap=Bitmap.createBitmap(pixelsW,pixelsH,Bitmap.Config.ARGB_8888);
                    if(BuildConfig.DEBUG)debugBuilds++;
                    Canvas raster=new Canvas(bitmap);raster.translate(pad,pad);raster.scale(density/scale,density/scale);
                    renderer.draw(raster,person,layer);
                    if(!retained) {
                        Entry previous=entries.put(person,entry);
                        if(previous!=null)bytes-=previous.bitmap.getAllocationByteCount();
                    } else bytes-=entry.bitmap.getAllocationByteCount();
                    entry.bitmap=bitmap;entry.pad=pad;entry.density=density;entry.referenceScale=scale;
                    bytes+=bitmap.getAllocationByteCount();bitmap=null;valid=true;evict();
                } catch(OutOfMemoryError error) {allocationFailed=true;if(bitmap!=null)bitmap.recycle();}
                finally {frameBuildNanos+=System.nanoTime()-start;}
            }
        }
        if(!valid &&eligible&&!allocationFailed)pending=true;
        if(!valid)return false; // Never publish a stale or differently scaled card.
        float factor=scale/entry.density;
        destination.set(left-entry.pad*factor,top-entry.pad*factor,
            left+(entry.bitmap.getWidth()-entry.pad)*factor,top+(entry.bitmap.getHeight()-entry.pad)*factor);
        canvas.drawBitmap(entry.bitmap,null,destination,paint);return true;
    }
    private void evict() {
        if(bytes<=budget)return;
        Iterator<Entry> old=entries.values().iterator();
        while(bytes>budget &&old.hasNext()) {
            Entry entry=old.next();if(entry.lastFrame==frame)activeEvicted=true;
            bytes-=entry.bitmap.getAllocationByteCount();old.remove();
        }
        // RenderThread can still use published images: release references, never recycle.
    }
}

