package ru.drshapaya.androidft2;

import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import java.util.Iterator;
import java.util.LinkedHashMap;

/** Native drawing commands, never a scaled bitmap or a forced hardware layer. */
@android.annotation.TargetApi(29)
final class CardDisplayListCache {
    private final LinkedHashMap<Person,Entry> entries=new LinkedHashMap<>(64,.75f,true);
    private long builds,buildNanos;
    private int frameBuilds;
    private boolean pending;
    private static final class Entry {
        final long signature;final int layer;final float scale;final RenderNode node;final int pad;
        Entry(long signature,int layer,float scale,RenderNode node,int pad) {
            this.signature=signature;this.layer=layer;this.scale=scale;this.node=node;this.pad=pad;
        }
    }
    void beginFrame(){frameBuilds=0;buildNanos=0;pending=false;}
    void clear(){entries.clear();pending=false;}
    boolean needsAnotherFrame(){return pending;}
    long builds(){return builds;}
    int frameBuilds(){return frameBuilds;}
    int size(){return entries.size();}
    boolean draw(Canvas canvas,Person person,int layer,long signature,float width,float height,float scale,
                 float left,float top,boolean exact,CardRasterCache.Renderer renderer) {
        Entry entry=entries.get(person);
        boolean valid=entry!=null&&entry.signature==signature&&entry.layer==layer&&entry.scale==scale
            &&entry.node.hasDisplayList();
        if(!valid) {
            // While fingers move, execute the complete original drawing at
            // the current size. Never scale stale text or screen-space icons.
            if(!exact)return false;
            if(frameBuilds>=8||buildNanos>=2_000_000L){pending=true;return false;}
            int pad=(int)Math.ceil(Math.max(48*scale,8))+2;
            int w=(int)Math.ceil(width*scale)+pad*2,h=(int)Math.ceil(height*scale)+pad*2;
            RenderNode node=new RenderNode("tree-card");node.setPosition(0,0,w,h);node.setClipToBounds(false);
            long start=System.nanoTime();
            RecordingCanvas recording=node.beginRecording(w,h);
            try{recording.translate(pad,pad);renderer.draw(recording,person,layer);}
            finally{node.endRecording();buildNanos+=System.nanoTime()-start;}
            frameBuilds++;builds++;
            entry=new Entry(signature,layer,scale,node,pad);entries.put(person,entry);
            // Bound retained commands for trees much larger than the viewport.
            // Dropping references is safe for commands already in RenderThread.
            while(entries.size()>1200){Iterator<Person> old=entries.keySet().iterator();old.next();old.remove();}
        }
        int save=canvas.save();
        canvas.translate(left-entry.pad,top-entry.pad);canvas.drawRenderNode(entry.node);canvas.restoreToCount(save);
        return true;
    }
}
