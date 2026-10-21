package ru.drshapaya.androidft2;

import android.graphics.Path;
import android.graphics.PathMeasure;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Requires real Android PathMeasure; JVM Android stubs cannot exercise hit testing. */
public class LinkSelectionTest extends InstrumentationTestCase {
    public void testAllContoursAreHittableWithoutConnectingTheGaps() throws Exception {
        Path path = new Path();
        path.moveTo(0f, 0f);
        path.lineTo(100f, 0f);
        path.moveTo(300f, 200f);
        path.lineTo(400f, 200f);
        assertEquals(0f, distance(path, 100f, 0f), 0.01f);
        assertEquals(0f, distance(path, 400f, 200f), 0.01f);
        assertTrue(distance(path, 200f, 100f) > 100f);
        assertEquals(Float.MAX_VALUE, distance(new Path(), 0f, 0f), 0f);
    }

    public void testCenteredBranchesSelectTheirFamilyAfterPanAndZoom() {
        getInstrumentation().runOnMainSync(() -> {
            TreeCanvasView view = new TreeCanvasView(getInstrumentation().getTargetContext());
            try {
                TreeState state = new TreeState();
                addPerson(state, "father", PersonGender.MALE, 400f, 400f);
                addPerson(state, "mother", PersonGender.FEMALE, 720f, 400f);
                addPerson(state, "childA", PersonGender.UNKNOWN, 400f, 880f);
                addPerson(state, "childB", PersonGender.UNKNOWN, 1040f, 880f);
                state.addRelation("partner", "father", "mother");
                for (String child : new String[]{"childA", "childB"}) {
                    state.addRelation("parent", "father", child);
                    state.addRelation("parent", "mother", child);
                }
                view.setState(state);
                view.setFamilyLineMode("centered");
                view.layout(0, 0, 1600, 1600);
                FamilyLineRouter.FamilyRoute route = FamilyLineRouter.build(state,
                    TreeLayoutEngine.CARD_W, TreeLayoutEngine.CARD_H, TreeLayoutEngine.GRID).routes.get(0);
                for (String routing : new String[]{"orthogonal", "smart"}) {
                    view.setParentLineMode(routing);
                    for (String style : new String[]{"standard", "link_style_botanical"}) {
                        view.setLinkStyle(style);
                        for (float scale : new float[]{1f, 0.6f}) {
                            set(view, "scale", scale);
                            set(view, "offsetX", 83f);
                            set(view, "offsetY", -47f);
                            Path rendered = new Path((Path) invoke(view, "familyRoutePath",
                                new Class<?>[]{FamilyLineRouter.FamilyRoute.class}, route));
                            PathMeasure contours = new PathMeasure(rendered, false);
                            int tested = 0;
                            do {
                                if (contours.getLength() <= 0f) continue;
                                float[] point = new float[2];
                                contours.getPosTan(contours.getLength() / 2f, point, null);
                                if ("link_style_botanical".equals(style)) {
                                    point[0] = point[0] * scale + 83f;
                                    point[1] = point[1] * scale - 47f;
                                }
                                view.setLinkState("", "");
                                tap(view, point[0], point[1]);
                                Field selection = TreeCanvasView.class.getDeclaredField("selectedLinkId");
                                selection.setAccessible(true);
                                assertTrue(routing + "/" + style + "/" + scale + " contour " + tested,
                                    route.containsRelation((String) selection.get(view)));
                                tested++;
                            } while (contours.nextContour());
                            assertTrue("Must exercise multiple disconnected contours", tested >= 4);
                        }
                    }
                }
            } catch (Exception error) {
                throw new AssertionError(error);
            } finally {
                view.releaseExportResources();
            }
        });
    }

    private static float distance(Path path, float x, float y) throws Exception {
        return (Float) invoke(null, "distanceToPath", new Class<?>[]{Path.class, float.class, float.class}, path, x, y);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = TreeCanvasView.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void set(TreeCanvasView view, String name, float value) throws Exception {
        Field field = TreeCanvasView.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setFloat(view, value);
    }

    private static void tap(TreeCanvasView view, float x, float y) {
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(time, time + 20, MotionEvent.ACTION_UP, x, y, 0);
        try {
            view.onTouchEvent(down);
            view.onTouchEvent(up);
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    private static void addPerson(TreeState state, String id, String gender, float x, float y) {
        Person person = new Person(id);
        person.name = id;
        person.gender = gender;
        person.genderManual = true;
        person.x = x;
        person.y = y;
        state.people.put(id, person);
    }
}
