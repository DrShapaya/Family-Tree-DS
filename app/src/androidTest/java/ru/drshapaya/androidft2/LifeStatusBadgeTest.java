package ru.drshapaya.androidft2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.test.InstrumentationTestCase;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class LifeStatusBadgeTest extends InstrumentationTestCase {
    public void testBadgeMovesIntoQualityPositionAndCanBeHidden() {
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> {
            TreeCanvasView view = new TreeCanvasView(getInstrumentation().getTargetContext());
            Bitmap bitmap = Bitmap.createBitmap(300, 180, Bitmap.Config.ARGB_8888);
            try {
                TreeState state = new TreeState();
                Person p = new Person("p");
                state.people.put(p.id, p);
                state.showLifeStatusBadge = true;
                view.setState(state);
                Field scale = TreeCanvasView.class.getDeclaredField("scale");
                scale.setAccessible(true);
                scale.setFloat(view, 1f);
                view.setQualityReports(TreeQualityAnalyzer.analyze(state).people);
                Method draw = TreeCanvasView.class.getDeclaredMethod("drawLifeStatusIndicator", Canvas.class, Person.class,
                    float.class, float.class, float.class, float.class);
                draw.setAccessible(true);
                for (String status : new String[]{PersonLifeStatus.LIVING, PersonLifeStatus.DECEASED, PersonLifeStatus.UNKNOWN}) {
                    p.lifeStatus = status;
                    for (boolean quality : new boolean[]{true, false}) {
                        state.showQualityBadge = quality;
                        bitmap.eraseColor(Color.TRANSPARENT);
                        draw.invoke(view, new Canvas(bitmap), p, 0f, 0f, 280f, 160f);
                        int cy = quality ? 111 : 139;
                        assertEquals(LifeStatusBadge.color(status), bitmap.getPixel(267, cy));
                        assertEquals(0, Color.alpha(bitmap.getPixel(258, quality ? 139 : 111)));
                        assertTrue("White icon must be visible", countWhite(bitmap, 239, cy - 10, 20, 20) > 6);
                    }
                }
                state.showLifeStatusBadge = false;
                bitmap.eraseColor(Color.TRANSPARENT);
                draw.invoke(view, new Canvas(bitmap), p, 0f, 0f, 280f, 160f);
                assertEquals(0, Color.alpha(bitmap.getPixel(258, 139)));
            } catch (Throwable e) { failure.set(e); }
            finally { bitmap.recycle(); view.releaseExportResources(); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    public void testRenderRegularAndCompactCards() {
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> {
            TreeCanvasView view = new TreeCanvasView(getInstrumentation().getTargetContext());
            Bitmap bitmap = Bitmap.createBitmap(1080, 660, Bitmap.Config.ARGB_8888);
            try {
                bitmap.eraseColor(Color.rgb(244, 247, 249));
                TreeState state = new TreeState();
                state.showLifeStatusBadge = true;
                String[] statuses = {PersonLifeStatus.LIVING, PersonLifeStatus.DECEASED, PersonLifeStatus.UNKNOWN};
                String[] names = {"Соколов Алексей", "Соколова Анна", "Соколов Николай"};
                for (int i = 0; i < 3; i++) {
                    Person p = new Person("p" + i);
                    p.name = names[i]; p.lifeStatus = statuses[i]; p.bornYear = "1960";
                    p.place = "Томск, Россия"; p.color = Color.rgb(132, 199, 174);
                    state.people.put(p.id, p);
                }
                view.setState(state);
                Field scale = TreeCanvasView.class.getDeclaredField("scale");
                scale.setAccessible(true); scale.setFloat(view, 1f);
                Field offsetX = TreeCanvasView.class.getDeclaredField("offsetX");
                offsetX.setAccessible(true); offsetX.setFloat(view, 0f);
                Field offsetY = TreeCanvasView.class.getDeclaredField("offsetY");
                offsetY.setAccessible(true); offsetY.setFloat(view, 0f);
                view.setQualityReports(TreeQualityAnalyzer.analyze(state).people);
                Method draw = TreeCanvasView.class.getDeclaredMethod("drawCard", Canvas.class, Person.class, boolean.class);
                draw.setAccessible(true);
                Canvas canvas = new Canvas(bitmap);
                for (int row = 0; row < 3; row++) {
                    view.setCompactCards(row == 2);
                    state.showQualityBadge = row != 1;
                    int i = 0;
                    for (Person p : state.people.values()) {
                        p.x = 20 + i++ * 355; p.y = 20 + row * 215;
                        draw.invoke(view, canvas, p, false);
                    }
                }
                java.io.File out = new java.io.File(getInstrumentation().getTargetContext().getExternalFilesDir(null), "life-status-cards.png");
                try (java.io.FileOutputStream stream = new java.io.FileOutputStream(out)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                }
            } catch (Throwable e) { failure.set(e); }
            finally { bitmap.recycle(); view.releaseExportResources(); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    public void testJsonRoundTripPreservesStatusAndSwitch() throws Exception {
        TreeStore store = new TreeStore(getInstrumentation().getTargetContext());
        TreeState state = new TreeState();
        state.showLifeStatusBadge = true;
        state.lifeStatusAppearance.livingIcon = "pulse";
        state.lifeStatusAppearance.deceasedIcon = "ribbon";
        state.lifeStatusAppearance.livingColor = "#B55976";
        state.lifeStatusAppearance.style = "soft";
        Person p = new Person("life-status-test");
        p.lifeStatus = PersonLifeStatus.LIVING;
        p.x = 100f;
        p.y = 100f;
        state.people.put(p.id, p);
        state.rootId = p.id;
        TreeState parsed = store.parse(store.exportText(state));
        assertTrue(parsed.showLifeStatusBadge);
        assertEquals(state.lifeStatusAppearance.encode(), parsed.lifeStatusAppearance.encode());
        assertEquals(PersonLifeStatus.LIVING, parsed.people.get(p.id).lifeStatus);
    }

    public void testAllIconChoicesRenderInAllStylesAndUseCustomColor() throws Exception {
        LifeStatusBadge badge=new LifeStatusBadge();
        Bitmap sheet=Bitmap.createBitmap(960,720,Bitmap.Config.ARGB_8888);
        sheet.eraseColor(Color.WHITE); Canvas canvas=new Canvas(sheet);
        int row=0;
        for(String status:new String[]{PersonLifeStatus.LIVING,PersonLifeStatus.DECEASED,PersonLifeStatus.UNKNOWN}) {
            for(String style:new String[]{"plain","soft","solid"}) {
                int col=0;
                for(String[] icon:LifeStatusAppearance.icons(status)) {
                    LifeStatusAppearance appearance=new LifeStatusAppearance(); appearance.style=style;
                    appearance.setIcon(status,icon[0]); appearance.setColor(status,"#8E5EB5");
                    Bitmap glyph=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
                    badge.draw(new Canvas(glyph),status,appearance,32,32,48,1);
                    int tinted=0;
                    for(int x=0;x<64;x++) for(int y=0;y<64;y++) if(glyph.getPixel(x,y)==Color.parseColor("#8E5EB5")) tinted++;
                    assertTrue(icon[0]+" "+style+" must contain the selected color",tinted>12);
                    if("plain".equals(style)) assertEquals(0,Color.alpha(glyph.getPixel(53,32)));
                    canvas.drawBitmap(glyph,col++*80+8,row*80+8,null); glyph.recycle();
                }
                row++;
            }
        }
        java.io.File output=new java.io.File(getInstrumentation().getTargetContext().getExternalFilesDir(null),"life-icon-catalog.png");
        try(java.io.FileOutputStream stream=new java.io.FileOutputStream(output)) { sheet.compress(Bitmap.CompressFormat.PNG,100,stream); }
        sheet.recycle();
    }

    private int countWhite(Bitmap bitmap, int x, int y, int width, int height) {
        int count = 0;
        for (int dx = 0; dx < width; dx++) for (int dy = 0; dy < height; dy++) {
            int pixel = bitmap.getPixel(x + dx, y + dy);
            if (Color.alpha(pixel) > 200 && Color.red(pixel) > 230 && Color.green(pixel) > 230 && Color.blue(pixel) > 230) count++;
        }
        return count;
    }
}
