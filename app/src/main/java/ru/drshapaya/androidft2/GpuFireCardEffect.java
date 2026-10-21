package ru.drshapaya.androidft2;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.*;
import android.os.Build;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** The original flame curves and all particles, evaluated by the GPU rather than whole-frame CPU atlases. */
final class GpuFireCardEffect {
    private final AssetManager assets;
    private Api33 renderer;
    private String failure;
    GpuFireCardEffect(Context context) { assets=context.getAssets(); }

    boolean prepare(Canvas canvas,float width,float height) {
        if(Build.VERSION.SDK_INT<33 || !canvas.isHardwareAccelerated() || failure!=null)return false;
        if(Math.round(width/14f)+2*Math.round(height/18f)>48)return false;
        try { if(renderer==null)renderer=new Api33(assets);return true; }
        catch(IOException|IllegalArgumentException|OutOfMemoryError error) {
            failure=error.toString();android.util.Log.w("GpuFireCardEffect","Using complete procedural fallback",error);return false;
        }
    }
    String failureReason() { return failure; }
    void behind(Canvas c,float w,float h,int seed,float seconds,float scale) { renderer.behind(c,w,h,seed,seconds,scale); }
    void surface(Canvas c,float w,float h,int seed,float seconds,float scale) { renderer.surface(c,w,h,seed,seconds,scale); }
    void rim(Canvas c,float w,float h,int seed,float seconds,float scale) { renderer.rim(c,w,h,seed,seconds,scale); }
    void trim() { renderer=null;failure=null; }

    @android.annotation.TargetApi(33)
    private static final class Api33 {
        final RuntimeShader behind,surface,rim;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        float lastWidth,lastHeight,lastTime=Float.NaN,lastScale;
        Api33(AssetManager assets) throws IOException {
            String common=read(assets,"shaders/fire_common.agsl");
            behind=new RuntimeShader(common+read(assets,"shaders/fire_behind.agsl"));
            surface=new RuntimeShader(common+read(assets,"shaders/fire_surface.agsl"));
            rim=new RuntimeShader(common+read(assets,"shaders/fire_rim.agsl"));
            BitmapShader contour=new BitmapShader(contourTable(),Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
            contour.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);behind.setInputBuffer("contourTable",contour);
        }
        void frame(float w,float h,float t,float scale) {
            if(w==lastWidth &&h==lastHeight &&t==lastTime &&scale==lastScale)return;
            lastWidth=w;lastHeight=h;lastTime=t;lastScale=scale;
            for(RuntimeShader shader:new RuntimeShader[]{behind,surface,rim}) {
                shader.setFloatUniform("cardSize",w,h);shader.setFloatUniform("pixel",1/scale);shader.setFloatUniform("seconds",t);
            }
            behind.setFloatUniform("counts",Math.round(w/14f),Math.round(h/18f));
        }
        void behind(Canvas canvas,float w,float h,int seed,float t,float scale) {
            frame(w,h,t,scale);behind.setFloatUniform("phase",phase(seed));
            float pad=48+1/scale;
            paint.setShader(behind);canvas.drawRect(-pad,-pad,w+pad,h+pad,paint);paint.setShader(null);
        }
        void surface(Canvas canvas,float w,float h,int seed,float t,float scale) {
            frame(w,h,t,scale);surface.setFloatUniform("phase",phase(seed));
            paint.setShader(surface);canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
        }
        void rim(Canvas canvas,float w,float h,int seed,float t,float scale) {
            frame(w,h,t,scale);rim.setFloatUniform("phase",phase(seed));
            float pad=8+1/scale;
            paint.setShader(rim);canvas.drawRect(-pad,-pad,w+pad,h+pad,paint);paint.setShader(null);
        }
        private static String read(AssetManager assets,String name) throws IOException {
            try(InputStream in=assets.open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] bytes=new byte[4096];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        }
        /** Cubic Y coordinates never change: store X's affine coefficients in bend and curl. */
        private static Bitmap contourTable() {
            int[] pixels=new int[512];
            for(int side=0;side<2;side++)for(int i=0;i<256;i++) {
                float y=-i/255f,a,b,c,t;
                if(side==0 &&y>=-.62f) {
                    t=invert(y,0,-.24f,-.38f,-.62f);float u=1-t;
                    a=-u*u*u-1.35f*3*u*u*t-.2f*3*u*t*t-.3f*t*t*t;b=t*t*t;c=3*u*t*t;
                } else if(side==0) {
                    t=invert(y,-.62f,-.79f,-.83f,-1);float u=1-t;
                    a=-.3f*u*u*u-.55f*3*u*u*t+.5f*3*u*t*t+.25f*t*t*t;b=1;c=0;
                } else if(y>=-.28f) {
                    float low=0,high=1;for(int k=0;k<24;k++) { float mid=(low+high)*.5f,v=1-mid;
                        float value=-.24f*v*mid-.28f*mid*mid;if(value>y)low=mid;else high=mid; }
                    t=(low+high)*.5f;float u=1-t;a=u*u+2.7f*u*t+.8f*t*t;b=0;c=0;
                } else {
                    t=invert(y,-.28f,-.51f,-.73f,-1);float u=1-t;
                    a=.8f*u*u*u+.05f*3*u*u*t+1.25f*3*u*t*t+.25f*t*t*t;b=3*u*t*t+t*t*t;c=3*u*u*t;
                }
                pixels[side*256+i]=Color.rgb(Math.round((a+2)*255/4),Math.round((b+1)*255/2),Math.round((c+1)*255/2));
            }
            return Bitmap.createBitmap(pixels,256,2,Bitmap.Config.ARGB_8888);
        }
        private static float invert(float y,float a,float b,float c,float d) {
            float low=0,high=1;for(int i=0;i<24;i++) { float t=(low+high)*.5f,u=1-t;
                float value=a*u*u*u+3*b*u*u*t+3*c*u*t*t+d*t*t*t;if(value>y)low=t;else high=t; }
            return (low+high)*.5f;
        }
    }
    private static float phase(int seed) {
        int mixed=(seed^(seed>>>16))*0x45d9f3b;return ((mixed^(mixed>>>16))&65535)*.0031f;
    }
}
