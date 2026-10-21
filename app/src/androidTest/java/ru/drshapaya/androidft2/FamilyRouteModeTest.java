package ru.drshapaya.androidft2;

import android.graphics.Path;
import android.graphics.PathMeasure;
import android.test.InstrumentationTestCase;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

/** Checks actual curve geometry, rather than only the saved mode setting. */
public final class FamilyRouteModeTest extends InstrumentationTestCase {
    public void testMovingChildPreservesCurvesAndOrthogonalMode() throws Exception {
        checkedMain(() -> {
            TreeCanvasView view = new TreeCanvasView(getInstrumentation().getTargetContext());
            try {
                TreeState state = family();
                view.setState(state);
                set(view, "scale", 1f); set(view, "offsetX", 0f); set(view, "offsetY", 0f);
                for (boolean compact : new boolean[]{false, true}) {
                    view.setCompactCards(compact);
                    float width = compact ? TreeLayoutEngine.GRID * 5f : TreeLayoutEngine.CARD_W;
                    float height = compact ? TreeLayoutEngine.GRID * 3f : TreeLayoutEngine.CARD_H;
                    for (String style : new String[]{RewardCatalog.STANDARD,
                        RewardCatalog.LINK_STYLE_CARD_COLORS, "link_style_botanical"}) {
                        view.setLinkStyle(style);
                        // Reuse one view and move the same person up and back down.
                        for (float y : new float[]{880f, 400f, 80f, 880f}) {
                            Person child = state.people.get("child");
                            child.y = y;
                            child.x = y == 400f ? 1040f : 400f;
                            FamilyLineRouter.FamilyRoute route = FamilyLineRouter.build(state,
                                width, height, TreeLayoutEngine.GRID).routes.get(0);
                            assertEquals(y < 880f, route.routedChildIds.contains("child"));
                            for (String mode : new String[]{"smart", "orthogonal", "smart"}) {
                                view.setParentLineMode(mode);
                                Path path = new Path((Path) invoke(view, "familyRoutePath",
                                    new Class<?>[]{FamilyLineRouter.FamilyRoute.class}, route));
                                boolean curves = hasCurvedTangent(path);
                                assertEquals(style + "/" + mode + "/" + y + "/compact=" + compact,
                                    "smart".equals(mode), curves);
                                if (!route.routedChildIds.isEmpty()) assertAvoidsCards(path, state, width, height);
                                // Colored branches and selection use the same geometry.
                                if (y == 80f) {
                                    Path childPath = new Path();
                                    invoke(view, "appendFamilyRoute", new Class<?>[]{Path.class,
                                        FamilyLineRouter.FamilyRoute.class, String.class}, childPath, route, "child");
                                    assertEquals("Colored child branch mode", "smart".equals(mode), hasCurvedTangent(childPath));
                                }
                            }
                            assertEquals("centered", state.familyLineMode);
                            assertEquals(3, state.links.size());
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

    public void testMovedFamilyCurvesRemainSelectableAfterPanAndZoom() throws Exception {
        checkedMain(() -> {
            TreeCanvasView view = new TreeCanvasView(getInstrumentation().getTargetContext());
            try {
                TreeState state = family(); state.people.get("child").y = 80f;
                view.setState(state); view.setParentLineMode("smart");
                set(view, "scale", 0.65f); set(view, "offsetX", 83f); set(view, "offsetY", -47f);
                FamilyLineRouter.FamilyRoute route = FamilyLineRouter.build(state,
                    TreeLayoutEngine.CARD_W, TreeLayoutEngine.CARD_H, TreeLayoutEngine.GRID).routes.get(0);
                Path path = new Path((Path) invoke(view, "familyRoutePath",
                    new Class<?>[]{FamilyLineRouter.FamilyRoute.class}, route));
                PathMeasure measure = new PathMeasure(path, false);
                int curvedSamples = 0;
                do {
                    float[] point = new float[2], tangent = new float[2];
                    for (float distance = 1f; distance < measure.getLength(); distance += 1f) {
                        measure.getPosTan(distance, point, tangent);
                        if (Math.abs(tangent[0]) < 0.1f || Math.abs(tangent[1]) < 0.1f) continue;
                        Object hit = invoke(view, "hitLink",
                            new Class<?>[]{float.class, float.class}, point[0], point[1]);
                        assertTrue(hit instanceof Relation && route.containsRelation(((Relation) hit).id));
                        curvedSamples++;
                    }
                } while (measure.nextContour());
                assertTrue(curvedSamples > 0);
            } catch (Exception error) { throw new AssertionError(error); }
            finally { view.releaseExportResources(); }
        });
    }

    public void testMovingBrotherKeepsParentsSiblingsAndCommonCentreExactlyInPlace() {
        checkedMain(() -> {
            TreeCanvasView view = new TreeCanvasView(getInstrumentation().getTargetContext());
            try {
                TreeState state = family();
                Person brother = state.people.get("child"); brother.x = 80f;
                Person sister = new Person("sister"); sister.name = "sister";
                sister.x = 1040f; sister.y = 880f; state.people.put(sister.id, sister);
                state.addRelation("parent", "father", "sister");
                state.addRelation("parent", "mother", "sister");
                view.setState(state);
                set(view, "scale", 1f); set(view, "offsetX", 0f); set(view, "offsetY", 0f);
                for (boolean compact : new boolean[]{false, true}) {
                    view.setCompactCards(compact);
                    for (String style : new String[]{RewardCatalog.STANDARD, RewardCatalog.LINK_STYLE_CARD_COLORS, "link_style_botanical"}) {
                        view.setLinkStyle(style);
                        for (String mode : new String[]{"smart", "orthogonal"}) {
                            view.setParentLineMode(mode);
                            brother.y = 880f;
                            FamilyLineRouter.FamilyRoute before = plan(view, state);
                            java.util.Map<String, String> paths = new java.util.HashMap<>();
                            for (String id : new String[]{"father", "mother", "sister"}) {
                                paths.put(id, pathShape(branch(view, before, id)));
                            }
                            for (float y : new float[]{600f, 400f, 80f, 880f}) {
                                brother.y = y;
                                FamilyLineRouter.FamilyRoute after = plan(view, state);
                                assertEquals(before.unionX, after.unionX, 0f);
                                assertEquals(before.unionY, after.unionY, 0f);
                                assertEquals(before.busY, after.busY, 0f);
                                for (String id : paths.keySet()) {
                                    assertEquals(mode + "/" + style + "/" + y + "/" + id,
                                        paths.get(id), pathShape(branch(view, after, id)));
                                }
                                Path moved = branch(view, after, "child");
                                assertFalse(moved.isEmpty());
                            }
                        }
                    }
                }
            } catch (Exception error) { throw new AssertionError(error); }
            finally { view.releaseExportResources(); }
        });
    }

    private static FamilyLineRouter.FamilyRoute plan(TreeCanvasView view, TreeState state) throws Exception {
        FamilyLineRouter.Plan plan = (FamilyLineRouter.Plan) invoke(view, "familyLinePlan",
            new Class<?>[]{java.util.Set.class}, new java.util.LinkedHashSet<>(state.people.keySet()));
        return plan.routes.get(0);
    }

    private static Path branch(TreeCanvasView view, FamilyLineRouter.FamilyRoute route, String owner) throws Exception {
        Path path = new Path();
        invoke(view, "appendFamilyRoute", new Class<?>[]{Path.class, FamilyLineRouter.FamilyRoute.class, String.class}, path, route, owner);
        return path;
    }

    private static String pathShape(Path path) {
        PathMeasure measure = new PathMeasure(path, false);
        StringBuilder result = new StringBuilder();
        float[] point = new float[2];
        do {
            result.append(measure.getLength()).append(':');
            for (int step = 0; step <= 20; step++) {
                measure.getPosTan(measure.getLength() * step / 20f, point, null);
                result.append(point[0]).append(',').append(point[1]).append(';');
            }
        } while (measure.nextContour());
        return result.toString();
    }

    private static boolean hasCurvedTangent(Path path) {
        PathMeasure measure = new PathMeasure(path, false);
        float[] tangent = new float[2];
        do {
            for (float d = 0.5f; d < measure.getLength(); d += 0.5f) {
                measure.getPosTan(d, null, tangent);
                if (Math.abs(tangent[0]) > 0.05f && Math.abs(tangent[1]) > 0.05f) return true;
            }
        } while (measure.nextContour());
        return false;
    }

    private static void assertAvoidsCards(Path path, TreeState state, float width, float height) {
        PathMeasure measure = new PathMeasure(path, false);
        float[] point = new float[2];
        do {
            for (float d = 0f; d < measure.getLength(); d += 1f) {
                measure.getPosTan(d, point, null);
                for (Person person : state.people.values()) {
                    assertFalse("Curve cuts card " + person.id,
                        point[0] > person.x + 0.1f && point[0] < person.x + width - 0.1f
                        && point[1] > person.y + 0.1f && point[1] < person.y + height - 0.1f);
                }
            }
        } while (measure.nextContour());
    }

    private static TreeState family() {
        TreeState state = new TreeState();
        for (String id : new String[]{"father", "mother", "child"}) {
            Person person = new Person(id); person.name = id;
            person.x = id.equals("mother") ? 720f : 400f;
            person.y = id.equals("child") ? 880f : 400f;
            state.people.put(id, person);
        }
        state.addRelation("partner", "father", "mother");
        state.addRelation("parent", "father", "child");
        state.addRelation("parent", "mother", "child");
        return state;
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = TreeCanvasView.class.getDeclaredMethod(name, types); method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void set(TreeCanvasView view, String name, float value) throws Exception {
        Field field = TreeCanvasView.class.getDeclaredField(name); field.setAccessible(true); field.setFloat(view, value);
    }

    private void checkedMain(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> {
            try { action.run(); } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
