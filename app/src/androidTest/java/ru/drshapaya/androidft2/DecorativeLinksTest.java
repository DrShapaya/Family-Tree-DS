package ru.drshapaya.androidft2;

import android.graphics.*;
import android.test.InstrumentationTestCase;
import java.lang.reflect.*;

public class DecorativeLinksTest extends InstrumentationTestCase {
    public void testAllDecorativeContoursPaintIncludingShortStraightSegments() {
        getInstrumentation().runOnMainSync(()-> {
            TreeCanvasView view=new TreeCanvasView(getInstrumentation().getTargetContext());
            Bitmap bitmap=Bitmap.createBitmap(640,640,Bitmap.Config.ARGB_8888);
            try {
                set(view,"scale",1f);set(view,"offsetX",0f);set(view,"offsetY",0f);
                Path path=new Path();path.moveTo(40,50);path.lineTo(140,50);
                path.moveTo(280,200);path.lineTo(280,310);
                path.moveTo(480,450);path.lineTo(488,450);
                Method draw=TreeCanvasView.class.getDeclaredMethod("drawStyledLink",Canvas.class,Path.class,Relation.class,boolean.class,boolean.class);draw.setAccessible(true);
                Field world=TreeCanvasView.class.getDeclaredField("lastLinkWorldPath");world.setAccessible(true);world.set(view,path);
                for(String style:new String[]{"link_style_ribbon","link_style_botanical","link_style_fire"}) {
                    view.setLinkStyle(style);bitmap.eraseColor(Color.TRANSPARENT);
                    draw.invoke(view,new Canvas(bitmap),path,new Relation("r","parent","a","b"),false,false);
                    assertPainted(bitmap,85,50,style+" first");assertPainted(bitmap,280,255,style+" second");assertPainted(bitmap,484,450,style+" short");
                    assertEquals("Must not connect disconnected contours",0,Color.alpha(bitmap.getPixel(210,130)));
                }
                // A short axis-aligned botanical link used to have empty bounds and disappear.
                bitmap.eraseColor(Color.TRANSPARENT);Path shortPath=new Path();shortPath.moveTo(480,450);shortPath.lineTo(488,450);
                new BotanicalEffect().link(new Canvas(bitmap),shortPath,false,false,1f);
                assertPainted(bitmap,484,450,"botanical short standalone");
            }catch(Exception e){throw new AssertionError(e);}finally{bitmap.recycle();view.releaseExportResources();}
        });
    }
    public void testImportedBiographySurvivesInternalSaveAndXmlWorksOnAndroid() throws Exception {
        TreeStore store=new TreeStore(getInstrumentation().getTargetContext());
        TreeState state=new TreeState();Person p=new Person("a");p.name="Тест";StringBuilder note=new StringBuilder();for(int i=0;i<1000;i++)note.append("История ");p.notes=note.toString().trim();state.people.put(p.id,p);state.rootId=p.id;
        p.x=100f;p.y=100f;
        TreeState parsed=store.parse(store.exportText(state));assertEquals(p.notes,parsed.people.get(p.id).notes);
        java.io.StringWriter xml=new java.io.StringWriter();TreeReadableExport.xml(state,xml,q->"");
        assertEquals(p.notes,TreeXmlReader.read(xml.toString()).people.get(p.id).notes);
    }
    private static void assertPainted(Bitmap bitmap,int x,int y,String label) {
        for(int dx=-5;dx<=5;dx++)for(int dy=-5;dy<=5;dy++)if(Color.alpha(bitmap.getPixel(x+dx,y+dy))>20)return;
        fail(label+" is invisible");
    }
    private static void set(Object target,String name,float value)throws Exception {Field f=TreeCanvasView.class.getDeclaredField(name);f.setAccessible(true);f.setFloat(target,value);}
}
