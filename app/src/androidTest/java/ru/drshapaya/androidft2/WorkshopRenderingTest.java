package ru.drshapaya.androidft2;

import android.graphics.*;
import android.test.InstrumentationTestCase;
import java.lang.reflect.*;

/** Compare cached effects against their original procedural drawing at an identical time and seed. */
public final class WorkshopRenderingTest extends InstrumentationTestCase {
    public void testEveryThemeAndEdgeCombinationKeeps500Cards() throws Exception {
        TreeCanvasView[] holder={null};TreeState state=new TreeState();
        for(int i=0;i<500;i++) {
            Person person=state.addPerson("Человек "+i,(i%25)*350,(i/25)*300);
            person.bornYear="1980";person.place="Томск";person.color=Color.rgb(100+i%100,180,120);
        }
        state.selectedId="";
        getInstrumentation().runOnMainSync(()-> { holder[0]=new TreeCanvasView(getInstrumentation().getTargetContext());holder[0].setState(state);holder[0].layout(0,0,1000,850); });
        TreeCanvasView view=holder[0];
        Field scale=TreeCanvasView.class.getDeclaredField("scale");scale.setAccessible(true);scale.setFloat(view,.1f);
        Field x=TreeCanvasView.class.getDeclaredField("offsetX");x.setAccessible(true);x.setFloat(view,20);
        Field y=TreeCanvasView.class.getDeclaredField("offsetY");y.setAccessible(true);y.setFloat(view,100);
        java.util.List<String> cards=new java.util.ArrayList<>(),edges=new java.util.ArrayList<>();cards.add("standard");edges.add("standard");
        for(RewardCatalog.Item item:RewardCatalog.itemsFor(RewardCatalog.CARD_THEMES))cards.add(item.id);
        for(RewardCatalog.Item item:RewardCatalog.itemsFor(RewardCatalog.CARD_EDGES))edges.add(item.id);
        Bitmap bitmap=Bitmap.createBitmap(1000,850,Bitmap.Config.ARGB_8888);
        try {
            for(String card:cards)for(String edge:edges) {
                view.setCardStyle(card);view.setCardEdgeStyle(edge);view.draw(new Canvas(bitmap));
                int background=bitmap.getPixel(0,0),painted=0;
                for(int i=0;i<500;i++)if(bitmap.getPixel(20+(i%25)*35+14,100+(i/25)*30+8)!=background)painted++;
                assertEquals(card+" + "+edge+" lost cards",500,painted);
            }
        } finally { bitmap.recycle();view.releaseExportResources(); }
    }

    public void testCanvasGradientDoesNotInheritOpacityFromLastCard() throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            TreeCanvasView view=new TreeCanvasView(getInstrumentation().getTargetContext());view.layout(0,0,400,300);
            try {
                Field field=TreeCanvasView.class.getDeclaredField("paint");field.setAccessible(true);
                for(String style:new String[]{"canvas_background_garden","canvas_background_deep_blueprint"}) {
                    view.setCanvasStyle(style);
                    Bitmap a=Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888),b=Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888);
                    ((Paint)field.get(view)).setColor(Color.WHITE);view.draw(new Canvas(a));
                    ((Paint)field.get(view)).setColor(Color.argb(10,40,50,60));view.draw(new Canvas(b));
                    assertTrue("Background opacity changed between frames: "+style,a.sameAs(b));a.recycle();b.recycle();
                }
            } catch(Exception error) { throw new AssertionError(error); }
            finally { view.releaseExportResources(); }
        });
    }

    public void testBotanicalBackgroundHasNoHardTileSeams() throws Exception {
        BotanicalCanvasBackground background=new BotanicalCanvasBackground(getInstrumentation().getTargetContext());
        Bitmap bitmap=Bitmap.createBitmap(520,420,Bitmap.Config.ARGB_8888);
        try {
            // Make both primary tile boundaries land between known adjacent pixel centres.
            float zoom=.02f;
            background.draw(new Canvas(bitmap),520,420,zoom,0,0,false,false);
            Field texture=BotanicalCanvasBackground.class.getDeclaredField("bitmap");texture.setAccessible(true);
            Bitmap art=(Bitmap)texture.get(background);assertNotNull(art);
            float tileWidth=24_000f*zoom,tileHeight=tileWidth*art.getHeight()/art.getWidth();
            for(boolean vertical:new boolean[]{false,true}) {
                float boundary=vertical?tileWidth:tileHeight;
                float x=vertical?300f-boundary:0f,y=vertical?0f:250f-boundary;
                bitmap.eraseColor(Color.WHITE);
                background.draw(new Canvas(bitmap),520,420,zoom,x,y,false,false);
                long total=0;int samples=0;
                for(int along=15;along<(vertical?400:500);along++) {
                    int a=bitmap.getPixel(vertical?299:along,vertical?along:249);
                    int b=bitmap.getPixel(vertical?300:along,vertical?along:250);
                    for(int shift:new int[]{0,8,16})total+=Math.abs(((a>>shift)&255)-((b>>shift)&255));
                    samples+=3;
                }
                assertTrue("Texture jumps at "+(vertical?"vertical":"horizontal")+" tile seam: "+total/(double)samples,
                    total/(double)samples<3);
            }
        } finally {bitmap.recycle();}
    }

    public void testMoreThan1024BotanicalLinksDoNotRebuildEveryFrame() {
        BotanicalEffect effect=new BotanicalEffect();
        java.util.List<Path> paths=new java.util.ArrayList<>();
        for(int i=0;i<1200;i++) { Path path=new Path();path.moveTo(5,15);path.lineTo(40,15);paths.add(path); }
        Bitmap bitmap=Bitmap.createBitmap(64,40,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
        for(int frame=0;frame<2;frame++)for(Path path:paths)effect.link(canvas,path,false,false,.1f);
        assertEquals("The 1024-entry reset rebuilt all visible vines forever",1200,effect.linkBuildCountForTests());
        bitmap.recycle();
    }

    public void testLongBotanicalLinkKeepsItsLastVerticalSegment() {
        Bitmap bitmap=Bitmap.createBitmap(6400,250,Bitmap.Config.ARGB_8888);
        Path path=new Path();path.moveTo(30,90);path.lineTo(6000,90);path.lineTo(6000,160);
        new BotanicalEffect().link(new Canvas(bitmap),path,false,false,1f);
        int painted=0;
        for(int x=5997;x<=6003;x++)for(int y=135;y<=155;y++)if(Color.alpha(bitmap.getPixel(x,y))>60)painted++;
        bitmap.recycle();assertTrue("Long bitmap cache clipped the final vertical segment",painted>25);
    }

    public void testBotanicalSeedsSharingLowBitsRemainDifferent() {
        BotanicalEffect effect=new BotanicalEffect();
        Bitmap a=Bitmap.createBitmap(450,310,Bitmap.Config.ARGB_8888),b=Bitmap.createBitmap(450,310,Bitmap.Config.ARGB_8888);
        Canvas first=new Canvas(a),second=new Canvas(b);first.translate(60,60);second.translate(60,60);
        effect.behind(first,320,180,17);effect.behind(second,320,180,25);
        assertEquals(2,effect.cardBuildCountForTests());
        assertFalse("Distinct leaf variants were collapsed by seed & 7",a.sameAs(b));a.recycle();b.recycle();
    }

    public void testAtlasPreservesEverySeedAndLayer() throws Exception {
        for(boolean smoke:new boolean[]{false,true}) for(float density:new float[]{.125f,.5f,.5625f,1f})
                for(float seconds:new float[]{0f,2.4f,9.8f}) {
            AnimatedCardAtlas atlas=new AnimatedCardAtlas(()->{});
            Class<?> keyClass=Class.forName(AnimatedCardAtlas.class.getName()+"$Key");
            Constructor<?> constructor=keyClass.getDeclaredConstructors()[0];constructor.setAccessible(true);
            int[] seeds={17,25,1027};
            Object key=constructor.newInstance(320f,180f,density,!smoke,smoke,true,seeds);
            Method render=AnimatedCardAtlas.class.getDeclaredMethod("render",keyClass,float.class,long.class,boolean.class);
            render.setAccessible(true);
            Object frame=render.invoke(atlas,key,seconds,0L,false);
            Field active=AnimatedCardAtlas.class.getDeclaredField("active");active.setAccessible(true);active.set(atlas,frame);
            for(int seed:seeds)for(boolean front:new boolean[]{false,true}) {
                assertTrue(atlas.contains(seed));
                Bitmap reference=Bitmap.createBitmap(480,340,Bitmap.Config.ARGB_8888);
                Bitmap cached=Bitmap.createBitmap(480,340,Bitmap.Config.ARGB_8888);
                Canvas a=new Canvas(reference),b=new Canvas(cached);a.translate(80,80);b.translate(80,80);
                a.scale(density,density);b.scale(density,density);
                FireCardEffect fire=new FireCardEffect();SmokeCardEffect mist=new SmokeCardEffect();
                if(front) {
                    fire.surface(a,320,180,seed,seconds);
                    if(smoke)mist.rim(a,320,180);else fire.rim(a,320,180,seed,seconds);
                    atlas.front(b,seed);
                } else {
                    if(smoke)mist.behind(a,320,180,seed,seconds);else fire.behind(a,320,180,seed,seconds);
                    atlas.behind(b,seed);
                }
                assertPixels(reference,cached,smoke+" front="+front+" seed="+seed+" density="+density+" time="+seconds);
                reference.recycle();cached.recycle();
            }
            atlas.release();
        }
    }

    private static void assertPixels(Bitmap a,Bitmap b,String label) {
        int[] first=new int[a.getWidth()*a.getHeight()],second=new int[first.length];
        a.getPixels(first,0,a.getWidth(),0,0,a.getWidth(),a.getHeight());
        b.getPixels(second,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());
        int meaningful=0,changed=0;
        for(int i=0;i<first.length;i++) {
            int alphaA=Color.alpha(first[i]),alphaB=Color.alpha(second[i]);
            if(alphaA>8||alphaB>8) {
                meaningful++;
                // Comparing premultiplied values avoids amplifying rounding in almost transparent pixels.
                int delta=Math.abs(alphaA-alphaB);
                for(int shift:new int[]{0,8,16})delta=Math.max(delta,Math.abs(
                    ((first[i]>>shift)&255)*alphaA/255-((second[i]>>shift)&255)*alphaB/255));
                if(delta>3)changed++;
            }
        }
        assertTrue(label+" differs at "+changed+" / "+meaningful,changed<=Math.max(3,meaningful/500));
    }
}
