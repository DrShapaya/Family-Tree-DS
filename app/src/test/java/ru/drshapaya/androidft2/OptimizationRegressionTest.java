package ru.drshapaya.androidft2;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class OptimizationRegressionTest {
    @Test public void retainedSpatialQueriesMatchIndependentQueriesWhilePanningAndAfterAdding() {
        SceneBoundsIndex<Integer> index=new SceneBoundsIndex<>();Random random=new Random(83191);
        for(int i=0;i<500;i++) {
            float x=random.nextFloat()*20000-10000,y=random.nextFloat()*12000-6000;
            index.add(i,x,y,x+random.nextFloat()*2000,y+random.nextFloat()*1000);
        }
        index.add(500,-100000,30,100000,40);
        List<Integer> reused=new ArrayList<>();
        for(int frame=0;frame<1200;frame++) {
            float x=(float)Math.sin(frame*.01)*9000,y=(float)Math.cos(frame*.013)*5000;
            index.queryInto(x,y,x+1200,y+1800,reused);
            assertEquals("frame="+frame,index.query(x,y,x+1200,y+1800),reused);
            if(frame==600)index.add(501,x,y,x+40,y+40);
        }
        index.queryInto(-1e30f,-1e30f,1e30f,1e30f,reused);assertEquals(502,reused.size());
    }
    @Test public void spatialIndexKeepsOrderAndLongLinesWithBothEndsOutsideViewport() {
        SceneBoundsIndex<String> index=new SceneBoundsIndex<>();
        index.add("long",-100000,40,100000,50);
        index.add("first",10,10,40,60); index.add("outside",9000,9000,9200,9200);
        index.add("second",30,30,80,80);
        assertEquals(Arrays.asList("long","first","second"),index.query(0,0,100,100));
        assertEquals(Collections.singletonList("long"),index.query(-90000,0,-89900,100));
    }

    @Test(timeout=1000) public void spatialIndexAcceptsExtremeImportedCoordinatesWithoutOverflow() {
        SceneBoundsIndex<String> index=new SceneBoundsIndex<>();
        index.add("extreme",1e30f,1e30f,1e30f,1e30f);
        index.add("crossing",-1e30f,-1e30f,1e30f,1e30f);
        assertEquals(Arrays.asList("extreme","crossing"),index.query(1e30f,1e30f,1e30f,1e30f));
        assertEquals(Collections.singletonList("crossing"),index.query(-10,-10,10,10));
    }

    @Test public void ancestrySccMatchesReachabilityOnRandomGraphs() {
        Random random=new Random(15923);
        for(int sample=0;sample<30;sample++) {
            TreeState state=new TreeState();
            for(int i=0;i<40;i++) state.people.put("p"+i,new Person("p"+i));
            for(int i=0;i<70;i++) state.addRelation("parent","p"+random.nextInt(40),"p"+random.nextInt(40));
            TreeRelationsIndex index=new TreeRelationsIndex(state);
            for(Relation r:state.links) assertEquals(reaches(index.children,r.to,r.from),index.ancestryCycle(r.from,r.to));
        }
    }

    private boolean reaches(Map<String,Set<String>> children,String start,String target) {
        Set<String> seen=new HashSet<>();ArrayDeque<String> queue=new ArrayDeque<>();queue.add(start);
        while(!queue.isEmpty()) {
            String id=queue.removeFirst();if(!seen.add(id))continue;if(id.equals(target))return true;
            queue.addAll(children.get(id));
        }
        return false;
    }

    @Test public void sharedPreparedSearchDoesNotUseStaleNamesOrNotesAcrossUndoSnapshots() {
        TreeState state=new TreeState();Person p=state.addPerson("Новосельцев Иван",0,0);
        p.notes="УникальнаяБиография";
        SmartPeopleSearch.Query query=SmartPeopleSearch.parse(state,"УникальнаяБиография",p.id);
        assertTrue(SmartPeopleSearch.matches(state,query,p,ignored->false,Calendar.getInstance()));
        TreeState snapshot=TreeStateCopier.copy(state);
        p.notes="Другая история";
        assertFalse(SmartPeopleSearch.matches(state,query,p,ignored->false,Calendar.getInstance()));
        assertTrue(SmartPeopleSearch.matches(snapshot,query,snapshot.people.get(p.id),ignored->false,Calendar.getInstance()));
        assertFalse(SmartPeopleSearch.matches(state,query,p,ignored->false,Calendar.getInstance()));
    }

    @Test public void graphReuseIsScopedAndDoesNotLeakIntoLaterStructuralEdits() {
        TreeState state=new TreeState();state.addPerson("Первый",0,0);
        FamilyLayoutGraph first;
        try(FamilyLayoutGraph.ReuseScope ignored=FamilyLayoutGraph.reuseTopology(state)) {
            first=FamilyLayoutGraph.from(state);
            state.people.values().iterator().next().x=320;
            assertSame(first,FamilyLayoutGraph.from(state));
        }
        state.addPerson("Второй",640,0);
        FamilyLayoutGraph second=FamilyLayoutGraph.from(state);
        assertNotSame(first,second);assertEquals(2,second.people.size());
    }
}
