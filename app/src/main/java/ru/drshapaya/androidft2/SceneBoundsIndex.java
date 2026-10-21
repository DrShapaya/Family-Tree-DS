package ru.drshapaya.androidft2;

import java.util.*;

/** Conservative bounds index. Query preserves drawing order, including long crossing lines. */
final class SceneBoundsIndex<T> {
    private static final float CELL=640f;
    private final List<Entry<T>> entries=new ArrayList<>();
    private final Map<Long,List<Integer>> cells=new HashMap<>();
    private final List<Integer> longEntries=new ArrayList<>();
    private final BitSet queryCandidates=new BitSet();
    private boolean queryValid;
    private int queryX1,queryX2,queryY1,queryY2;

    void add(T item,float left,float top,float right,float bottom) {
        if (!Float.isFinite(left) || !Float.isFinite(top) || !Float.isFinite(right) || !Float.isFinite(bottom)) return;
        queryValid=false;
        int id=entries.size(); entries.add(new Entry<>(item,left,top,right,bottom));
        int x1=cell(left),x2=cell(right),y1=cell(top),y2=cell(bottom);
        long columns=(long)x2-x1+1,rows=(long)y2-y1+1;
        if (columns<=0 || rows<=0 || columns>64 || rows>64 || columns*rows>64) { longEntries.add(id); return; }
        for(long y=y1;y<=y2;y++) for(long x=x1;x<=x2;x++)
            cells.computeIfAbsent(key((int)x,(int)y),ignored -> new ArrayList<>()).add(id);
    }

    List<T> query(float left,float top,float right,float bottom) {
        List<T> result=new ArrayList<>();
        collect(left,top,right,bottom,new BitSet(entries.size()),result,false);
        return result;
    }

    /** Single-consumer drawing path; results retain insertion order and exact viewport filtering. */
    void queryInto(float left,float top,float right,float bottom,List<T> result) {
        result.clear();collect(left,top,right,bottom,queryCandidates,result,true);
    }

    private void collect(float left,float top,float right,float bottom,BitSet candidates,List<T> result,boolean retained) {
        int x1=cell(left),x2=cell(right),y1=cell(top),y2=cell(bottom);
        long columns=(long)x2-x1+1,rows=(long)y2-y1+1;
        boolean rebuild=!retained ||!queryValid ||x1!=queryX1 ||x2!=queryX2 ||y1!=queryY1 ||y2!=queryY2;
        if(rebuild) {
        candidates.clear();
        if (columns<=0 || rows<=0 || columns>4096 || rows>4096 || columns*rows>4096) candidates.set(0,entries.size());
        else {
            for(int i=0;i<longEntries.size();i++)candidates.set(longEntries.get(i));
            for(long y=y1;y<=y2;y++) for(long x=x1;x<=x2;x++) {
                List<Integer> ids=cells.get(key((int)x,(int)y));
                if(ids!=null)for(int i=0;i<ids.size();i++)candidates.set(ids.get(i));
            }
        }
        if(retained) {queryValid=true;queryX1=x1;queryX2=x2;queryY1=y1;queryY2=y2;}
        }
        for(int id=candidates.nextSetBit(0);id>=0;id=candidates.nextSetBit(id+1)) {
            Entry<T> e=entries.get(id);
            if(e.left<=right && e.right>=left && e.top<=bottom && e.bottom>=top) result.add(e.item);
        }
    }

    private static int cell(float v) { return (int)Math.floor(v/CELL); }
    private static long key(int x,int y) { return ((long)x<<32)^(y&0xffffffffL); }
    private static final class Entry<T> {
        final T item; final float left,top,right,bottom;
        Entry(T item,float left,float top,float right,float bottom) {
            this.item=item;this.left=left;this.top=top;this.right=right;this.bottom=bottom;
        }
    }
}
