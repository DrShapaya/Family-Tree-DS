package ru.drshapaya.androidft2;

import org.json.*;
import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.util.*;
import static org.junit.Assert.*;

/** Independent imported graphs used to audit general layout invariants. */
public final class ImportedLayoutAuditTest {
    @Test public void auditImportedGraphs() throws Exception {
        TreeState kennedy = readKennedy();
        save(kennedy, "kennedy-reference");
        TreeLayoutEngine.layoutFresh(kennedy);
        save(kennedy, "kennedy-current");
        validate(kennedy);
        assertEquals(208, kennedy.people.size());
        assertParentCenter(kennedy, 107);
        assertParentCenter(kennedy, 111);
        assertTrue(numbered(kennedy, 109).x < numbered(kennedy, 107).x);
        assertTrue(numbered(kennedy, 139).x < numbered(kennedy, 159).x);
        assertGap(kennedy, 131, 132, 5);
        assertGap(kennedy, 166, 172, 5);
        assertGap(kennedy, 169, 170, 1);
        assertGap(kennedy, 159, 160, 1);
        assertGap(kennedy, 130, 131, 5);
        assertGap(kennedy, 171, 182, 5);
        TreeState washington = TreeStore.parseGedcom(resource("washington-numbered.ged"));
        save(washington, "washington-current");
        validate(washington);
        assertEquals(529, washington.people.size());
        assertParentCenter(washington, 411);
        assertParentCenter(washington, 414);
        assertGap(washington, 383, 433, 5);
        assertGap(washington, 433, 434, 1);
        assertGap(washington, 214, 215, 1);
        assertGap(washington, 320, 321, 1);
        assertGap(washington, 321, 322, 1);
        assertGap(washington, 322, 323, 1);
        assertGap(washington, 323, 281, 5);
        assertGap(washington, 527, 512, 5);
        // The 233/234 ancestry now precedes the Bullock branch, so these
        // grandparent couples need clearance but are no longer adjacent.
        assertClearance(washington, 278, 279, 5);
        assertTrue(numbered(washington, 278).x < numbered(washington, 281).x
            && numbered(washington, 282).x < numbered(washington, 279).x);
        assertGap(washington, 135, 184, 5);
        assertGap(washington, 187, 188, 5);
        assertClearance(washington, 188, 136, 5);
        LayoutSnapshot expected = LayoutSnapshot.capture(washington);
        TreeLayoutEngine.layoutFresh(washington);
        for (Person person : washington.people.values()) {
            assertEquals(person.name, expected.positionOf(person.id).x, person.x, .5f);
            assertEquals(person.name, expected.positionOf(person.id).y, person.y, .5f);
        }
    }

    @Test public void auditReportedSavedWashington() throws Exception {
        TreeState state = readFixture("washington-reported-gaps.json");
        save(state, "washington-reported-before");
        TreeLayoutEngine.layoutFresh(state);
        save(state, "washington-reported-current");
        validate(state);
        assertGap(state, 323, 281, 5);
        assertGap(state, 527, 512, 5);
        assertClearance(state, 278, 279, 5);
        assertGap(state, 135, 184, 5);
        assertGap(state, 187, 188, 5);
    }

    private static Person numbered(TreeState state, int number) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b0*" + number + "\\b");
        for (Person person : state.people.values()) if (pattern.matcher(person.name).find()) return person;
        throw new AssertionError("Missing numbered fixture person " + number);
    }

    private static void assertGap(TreeState state, int left, int right, int cells) {
        assertEquals("Fixture gap " + left + "/" + right, cells * TreeLayoutEngine.GRID,
            numbered(state, right).x - numbered(state, left).x - TreeLayoutEngine.CARD_W, .5f);
    }

    private static void assertClearance(TreeState state, int left, int right, int cells) {
        assertTrue("Fixture branch clearance " + left + "/" + right,
            numbered(state, right).x - numbered(state, left).x - TreeLayoutEngine.CARD_W
                >= cells * TreeLayoutEngine.GRID - .5f);
    }

    private static void assertParentCenter(TreeState state, int number) {
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        String id = numbered(state, number).id;
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (!family.parents.contains(id)) continue;
            float sum = 0f, left = Float.POSITIVE_INFINITY, right = Float.NEGATIVE_INFINITY;
            for (String parent : family.parents) sum += state.people.get(parent).x + TreeLayoutEngine.CARD_W / 2f;
            for (String child : family.children) {
                left = Math.min(left, state.people.get(child).x + TreeLayoutEngine.CARD_W / 2f);
                right = Math.max(right, state.people.get(child).x + TreeLayoutEngine.CARD_W / 2f);
            }
            assertEquals("Fixture parent center " + number, (left + right) / 2f,
                sum / family.parents.size(), TreeLayoutEngine.GRID / 2f);
            return;
        }
        throw new AssertionError("No child family for " + number);
    }

    private static void validate(TreeState state) {
        LayoutConstraints.ValidationResult validation = new LayoutConstraints().validate(
            FamilyLayoutGraph.from(state), LayoutSnapshot.capture(state));
        assertTrue(validation.violations.toString(), validation.isValid());
    }

    static TreeState readKennedy() throws Exception {
        return readFixture("kennedy-numbered-corrected.json");
    }

    static TreeState readFixture(String name) throws Exception {
        JSONObject document = new JSONObject(resource(name));
        TreeState state = new TreeState();
        JSONObject people = document.getJSONObject("people");
        Iterator<String> ids = people.keys();
        while (ids.hasNext()) {
            String id = ids.next();
            JSONObject value = people.getJSONObject(id);
            Person person = new Person(id);
            person.name = value.getString("name");
            person.gender = PersonGender.normalize(value.optString("gender", ""));
            person.genderManual = value.optBoolean("genderManual");
            person.bornYear = value.optString("bornYear");
            person.bornMonth = value.optString("bornMonth");
            person.bornDay = value.optString("bornDay");
            person.pinned = value.optBoolean("pinned");
            JSONObject position = document.getJSONObject("positions").getJSONObject(id);
            person.x = (float) position.getDouble("x");
            person.y = (float) position.getDouble("y");
            state.people.put(id, person);
        }
        JSONArray relations = document.getJSONArray("links");
        for (int index = 0; index < relations.length(); index++) {
            JSONObject link = relations.getJSONObject(index);
            state.links.add(new Relation(link.getString("id"), link.getString("type"),
                link.getString("from"), link.getString("to"), link.optString("side", "right")));
        }
        state.rootId = document.getString("rootId");
        return state;
    }

    static String resource(String name) throws Exception {
        try (InputStream input = ImportedLayoutAuditTest.class.getResourceAsStream("/fixtures/" + name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static void save(TreeState state, String name) throws Exception {
        JSONObject document = new JSONObject();
        JSONArray people = new JSONArray(), links = new JSONArray();
        for (Person p : state.people.values()) {
            people.put(new JSONObject().put("id", p.id).put("name", p.name)
                .put("x", p.x).put("y", p.y).put("pinned", p.pinned));
        }
        for (Relation r : state.links) {
            links.put(new JSONObject().put("from", r.from).put("to", r.to).put("type", r.type));
        }
        document.put("people", people).put("links", links).put("rootId", state.rootId);
        JSONArray routes = new JSONArray();
        FamilyLineRouter.Plan plan = FamilyLineRouter.build(state, TreeLayoutEngine.CARD_W,
            TreeLayoutEngine.CARD_H, TreeLayoutEngine.GRID);
        for (FamilyLineRouter.FamilyRoute route : plan.routes) {
            JSONArray segments = new JSONArray();
            for (FamilyLineRouter.Segment segment : route.segments) {
                segments.put(new JSONArray(new float[]{segment.x1, segment.y1, segment.x2, segment.y2}));
            }
            routes.put(new JSONObject().put("parents", route.parentIds).put("children", route.childIds)
                .put("segments", segments));
        }
        document.put("routes", routes);
        Path directory = Paths.get("build", "layout-qa");
        Files.createDirectories(directory);
        Files.write(directory.resolve(name + ".json"), document.toString().getBytes(StandardCharsets.UTF_8));
    }
}
