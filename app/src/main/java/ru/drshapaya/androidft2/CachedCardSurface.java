package ru.drshapaya.androidft2;

import android.graphics.*;
import android.util.LruCache;
import java.util.HashSet;
import java.util.Set;

/** Static translucent card artwork, fully painted before caching at display pixel density. */
final class CachedCardSurface {
    interface Renderer { void draw(Canvas canvas,float width,float height,int seed); }
    private static final int PAD=64,GUTTER=2;
    private final LruCache<Integer,Bitmap> cache=new LruCache<Integer,Bitmap>((int)Math.max(1024*1024,
        Math.min(8L*1024*1024,Runtime.getRuntime().maxMemory()/32))) {
        @Override protected int sizeOf(Integer key,Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };
    private final Set<Integer> rejected=new HashSet<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF target=new RectF();
    private float lastWidth,lastHeight,lastDensity;

    void clear() { cache.evictAll();rejected.clear(); }

    boolean draw(Canvas canvas,float width,float height,int seed,float scale,Renderer renderer) {
        if(!Float.isFinite(scale) || scale>=1f || scale<=0f)return false;
        float density=(float)Math.pow(2,Math.ceil(Math.log(Math.max(.03125f,scale))/Math.log(2)));
        if(width!=lastWidth || height!=lastHeight || density!=lastDensity) {
            cache.evictAll();rejected.clear();lastWidth=width;lastHeight=height;lastDensity=density;
        }
        Bitmap bitmap=cache.get(seed);
        if(bitmap==null) {
            if(rejected.contains(seed))return false;
            int pixelsW=(int)Math.ceil((width+PAD*2)*density)+GUTTER*2;
            int pixelsH=(int)Math.ceil((height+PAD*2)*density)+GUTTER*2;
            if((long)pixelsW*pixelsH*4>cache.maxSize()) { rejected.add(seed);return false; }
            try {
                bitmap=Bitmap.createBitmap(pixelsW,pixelsH,Bitmap.Config.ARGB_8888);
                Canvas raster=new Canvas(bitmap);
                raster.translate(GUTTER+PAD*density,GUTTER+PAD*density);raster.scale(density,density);
                renderer.draw(raster,width,height,seed);cache.put(seed,bitmap);
            } catch(OutOfMemoryError error) { rejected.add(seed);return false; }
        }
        float left=-PAD-GUTTER/density;
        target.set(left,left,left+bitmap.getWidth()/density,left+bitmap.getHeight()/density);
        canvas.drawBitmap(bitmap,null,target,paint);return true;
    }
}
