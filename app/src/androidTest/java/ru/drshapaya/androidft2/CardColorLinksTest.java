package ru.drshapaya.androidft2;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.test.InstrumentationTestCase;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class CardColorLinksTest extends InstrumentationTestCase {
    public void testFamilyBranchesUseEachPersonsColorInBothRoutingModes() throws Exception {
        checkedMain(() -> {
            TreeCanvasView view = new TreeCanvasView(getInstrumentation().getTargetContext());
            Bitmap bitmap = Bitmap.createBitmap(1200, 1200, Bitmap.Config.ARGB_8888);
            try {
                TreeState state = new TreeState();
                add(state, "father", 100f, 400f, Color.RED);
                add(state, "mother", 500f, 400f, Color.BLUE);
                add(state, "child", 100f, 880f, Color.GREEN);
                state.addRelation("partner", "father", "mother");
                state.addRelation("parent", "father", "child");
                state.addRelation("parent", "mother", "child");
                view.setState(state);
                view.setLinkStyle(RewardCatalog.LINK_STYLE_CARD_COLORS);
                set(view, "scale", 1f); set(view, "offsetX", 0f); set(view, "offsetY", 0f);
                Method draw = TreeCanvasView.class.getDeclaredMethod("drawLinks", Canvas.class, RectF.class);
                draw.setAccessible(true);
                for (String mode : new String[]{"orthogonal", "smart"}) {
                    view.setParentLineMode(mode);
                    bitmap.eraseColor(Color.TRANSPARENT);
                    draw.invoke(view, new Canvas(bitmap), new RectF(0, 0, 1200, 1200));
                    assertEquals(mode + " father", Color.RED, bitmap.getPixel(410, 480));
                    assertEquals(mode + " mother", Color.BLUE, bitmap.getPixel(470, 480));
                    assertEquals(mode + " child", Color.GREEN, bitmap.getPixel(240, 876));
                }
                // Moving and recoloring must invalidate route geometry and use live colors.
                state.people.get("child").y = 80f;
                state.people.get("child").color = Color.MAGENTA;
                bitmap.eraseColor(Color.TRANSPARENT);
                draw.invoke(view, new Canvas(bitmap), new RectF(0, 0, 1200, 1200));
                FamilyLineRouter.FamilyRoute route = FamilyLineRouter.build(state,
                    TreeLayoutEngine.CARD_W, TreeLayoutEngine.CARD_H, TreeLayoutEngine.GRID).routes.get(0);
                Path branch = new Path();
                Method append = TreeCanvasView.class.getDeclaredMethod("appendFamilyRoute",
                    Path.class, FamilyLineRouter.FamilyRoute.class, String.class);
                append.setAccessible(true); append.invoke(view, branch, route, "child");
                PathMeasure measure = new PathMeasure(branch, false);
                float[] point = new float[2];
                do { measure.getPosTan(Math.min(12f, measure.getLength() / 2f), point, null); }
                while (measure.nextContour());
                boolean painted = false;
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                    painted |= bitmap.getPixel(Math.round(point[0]) + dx, Math.round(point[1]) + dy) == Color.MAGENTA;
                }
                assertTrue("Moved attachment uses the live card color", painted);
                assertEquals("centered", state.familyLineMode);
            } catch (Exception error) {
                throw new AssertionError(error);
            } finally {
                bitmap.recycle();
                view.releaseExportResources();
            }
        });
    }

    public void testIndividualLinkKeepsBothEndpointColorsAndSelectionHighlight() throws Exception {
        checkedMain(() -> {
            TreeCanvasView view = new TreeCanvasView(getInstrumentation().getTargetContext());
            Bitmap bitmap = Bitmap.createBitmap(1000, 500, Bitmap.Config.ARGB_8888);
            try {
                TreeState state = new TreeState();
                add(state, "a", 0f, 100f, Color.RED);
                add(state, "b", 600f, 100f, Color.BLUE);
                state.addRelation("partner", "a", "b");
                view.setState(state); view.setFamilyLineMode("individual");
                view.setLinkStyle(RewardCatalog.LINK_STYLE_CARD_COLORS);
                set(view, "scale", 1f); set(view, "offsetX", 0f); set(view, "offsetY", 0f);
                Method draw = TreeCanvasView.class.getDeclaredMethod("drawLinks", Canvas.class, RectF.class);
                draw.setAccessible(true);
                draw.invoke(view, new Canvas(bitmap), new RectF(0, 0, 1000, 500));
                int left = bitmap.getPixel(300, 180), right = bitmap.getPixel(580, 180);
                assertTrue(Color.red(left) > Color.blue(left));
                assertTrue(Color.blue(right) > Color.red(right));
                view.setLinkState("", state.links.get(0).id);
                bitmap.eraseColor(Color.TRANSPARENT);
                draw.invoke(view, new Canvas(bitmap), new RectF(0, 0, 1000, 500));
                assertEquals(bitmap.getPixel(300, 180), bitmap.getPixel(580, 180));
            } catch (Exception error) {
                throw new AssertionError(error);
            } finally {
                bitmap.recycle(); view.releaseExportResources();
            }
        });
    }

    private static void add(TreeState state, String id, float x, float y, int color) {
        Person person = new Person(id); person.name = id; person.x = x; person.y = y;
        person.color = color; state.people.put(id, person);
    }

    private void checkedMain(Runnable action) throws Exception {
        java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> {
            try { action.run(); } catch (Throwable failure) { error.set(failure); }
        });
        if (error.get() != null) throw new AssertionError(error.get());
    }

    private static void set(TreeCanvasView view, String name, float value) throws Exception {
        Field field = TreeCanvasView.class.getDeclaredField(name); field.setAccessible(true);
        field.setFloat(view, value);
    }
}
