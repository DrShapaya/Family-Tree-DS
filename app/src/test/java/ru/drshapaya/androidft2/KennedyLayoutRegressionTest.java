package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A large connected family graph with several generations and shared ancestry. */
public final class KennedyLayoutRegressionTest {
    @Test public void fullArrangeKeepsRelatedRowsAlignedAndRepeatable() throws Exception {
        TreeState state = fixture();
        assertEquals(208, state.people.size());
        assertTrue(TreeLayoutEngine.layoutFresh(state, null));
        assertTrue(new LayoutConstraints().validate(
            FamilyLayoutGraph.from(state), LayoutSnapshot.capture(state)).isValid());

        // The Bouvier children belong to the branch on the right of Joseph
        // and Rose Kennedy, even though marriages connect both family groups.
        float patricia = person(state, "Patricia Kennedy").x;
        assertTrue(person(state, "Jacqueline Lee Bouvier").x > patricia);
        assertTrue(person(state, "Caroline Lee Bouvier").x > patricia);
        assertEquals(0f, familyOffset(state,
            "Joseph Lawrence Kane", "Frederick Kane"), 1f);
        assertEquals(0f, familyOffset(state,
            "Daniel Mahoney", "Frederick Louis Mahoney"), 1f);
        assertTrue("neighbouring child branches must follow their parent families",
            directChildrenMax(state, "John Fitzgerald KENNEDY", "Jacqueline Lee Bouvier")
                < directChildrenMin(state, "Robert Francis Kennedy", "Ethel Skakel"));
        assertFamilyNearCenter(state, "Robert Sargent Shriver Jr.",
            "Eunice Mary Kennedy");
        assertFamilyNearCenter(state, "John Fitzgerald KENNEDY",
            "Jacqueline Lee Bouvier");
        assertFamilyNearCenter(state, "Prince Stanislaus Radziwill",
            "Caroline Lee Bouvier");
        assertFamilyNearCenter(state, "Peter Lawford", "Patricia Kennedy");
        assertFamilyNearCenter(state, "Stephen Edward Smith", "Jean Ann Kennedy");
        assertFamilyNearCenter(state, "Robert Francis Kennedy", "Ethel Skakel");
        assertEquals(0f, directFamilyOffset(state,
            "Edward Moore Kennedy", "Virginia Joan Bennett"), 1f);

        assertEquals(0f, familyOffset(state,
            "Meaghan Anne Kennedy Townsend", "Rose Katherine Kennedy Townsend"), 1f);
        assertEquals(0f, familyOffset(state, "John Kennedy", "John Kennedy"), 1f);
        assertTrue(person(state, "Jean Ann Kennedy").x
            < person(state, "John Fitzgerald KENNEDY").x);
        assertTrue(person(state, "John Fitzgerald KENNEDY").x
            < person(state, "Caroline Lee Bouvier").x);
        assertEquals("plain siblings should have one grid cell between them",
            TreeLayoutEngine.GRID,
            person(state, "Patrick Bouvier Kennedy").x
                - person(state, "John Fitzgerald Kennedy Jr.").x
                - TreeLayoutEngine.CARD_W, 0.5f);
        assertEquals("paired sibling needs the normal family gap",
            TreeLayoutEngine.GRID * 5f,
            person(state, "Robert Sargent Shriver III").x
                - person(state, "Maria Owings Shriver").x
                - TreeLayoutEngine.CARD_W, 0.5f);
        assertEquals(0f, familyOffset(state,
            "Francis Benedict Kennedy", "Francis Benedict Kennedy"), 1f);
        assertEquals("unmarried sibling belongs next to his own sibling household", 5f,
            (person(state, "William John Robert Cavendish").x
                - person(state, "Joseph Patrick Kennedy Jr.").x - TreeLayoutEngine.CARD_W)
                / TreeLayoutEngine.GRID, 0.01f);
        assertBetween(state, "Jacqueline Lee Bouvier",
            "John Fitzgerald KENNEDY", "Aristotle Socrates Onassis");
        assertBetween(state, "Caroline Lee Bouvier",
            "Prince Stanislaus Radziwill", "Michael Canfield");
        assertBetween(state, "Janet Norton Lee", "John Vernou Bouvier III",
            "Hugh D. Auchincloss");
        assertTrue("Robert and Ethel's child generation is too wide",
            childFamilySpan(state, "Robert Francis Kennedy")
                <= 147f * TreeLayoutEngine.GRID + 0.5f);

        // Adjacent tight couples need a balanced position relative to their
        // respective children, rather than pushing the entire run one way.
        assertTrue(Math.abs(familyOffset(state, "Thomas Fitzgerald", "Thomas Fitzgerald"))
            <= 24 * TreeLayoutEngine.GRID);
        assertTrue(Math.abs(familyOffset(state, "Rosanna Cox", "Rosanna Cox"))
            <= 24 * TreeLayoutEngine.GRID);
        assertTrue(Math.abs(familyOffset(state, "Michael Hannon", "Michael Hannon"))
            <= 24 * TreeLayoutEngine.GRID);
        assertTrue(Math.abs(familyOffset(state, "Mary Ann Fitzgerald", "Mary Ann Fitzgerald"))
            <= 24 * TreeLayoutEngine.GRID);

        Map<String, float[]> first = new LinkedHashMap<>();
        for (Person person : state.people.values()) {
            first.put(person.id, new float[]{person.x, person.y});
        }
        assertTrue(TreeLayoutEngine.layoutFresh(state, null));
        for (Person person : state.people.values()) {
            assertEquals("x of " + person.name, first.get(person.id)[0], person.x, 0.5f);
            assertEquals("y of " + person.name, first.get(person.id)[1], person.y, 0.5f);
        }
    }

    private static void assertBetween(TreeState state, String shared, String first, String second) {
        float x = person(state, shared).x;
        float a = person(state, first).x;
        float b = person(state, second).x;
        assertTrue(shared + " must be between its partners", (a < x && x < b) || (b < x && x < a));
    }

    private static float childFamilySpan(TreeState state, String parentName) {
        String parentId = person(state, parentName).id;
        java.util.Set<String> childIds = new java.util.LinkedHashSet<>();
        for (Relation link : state.links) {
            if ("parent".equals(link.type) && parentId.equals(link.from)) childIds.add(link.to);
        }
        java.util.Set<String> rowIds = new java.util.LinkedHashSet<>(childIds);
        for (Relation link : state.links) {
            if ("partner".equals(link.type)) {
                if (childIds.contains(link.from)) rowIds.add(link.to);
                if (childIds.contains(link.to)) rowIds.add(link.from);
            }
        }
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        for (String id : rowIds) {
            Person member = state.people.get(id);
            left = Math.min(left, member.x);
            right = Math.max(right, member.x + TreeLayoutEngine.CARD_W);
        }
        return right - left;
    }

    private static void assertFamilyNearCenter(
        TreeState state, String firstParent, String secondParent
    ) {
        assertTrue(firstParent + " and " + secondParent + " are too far from their children",
            Math.abs(directFamilyOffset(state, firstParent, secondParent))
                <= 40f * TreeLayoutEngine.GRID);
    }

    private static float directFamilyOffset(
        TreeState state, String firstParent, String secondParent
    ) {
        Person first = person(state, firstParent);
        Person second = person(state, secondParent);
        float childrenCenter = (directChildrenMin(state, firstParent, secondParent)
            + directChildrenMax(state, firstParent, secondParent)
            + TreeLayoutEngine.CARD_W) / 2f;
        return childrenCenter
            - (first.x + second.x + TreeLayoutEngine.CARD_W) / 2f;
    }

    private static float directChildrenMin(
        TreeState state, String firstParent, String secondParent
    ) {
        return directChildrenEdge(state, firstParent, secondParent, true);
    }

    private static float directChildrenMax(
        TreeState state, String firstParent, String secondParent
    ) {
        return directChildrenEdge(state, firstParent, secondParent, false);
    }

    private static float directChildrenEdge(
        TreeState state, String firstParent, String secondParent, boolean minimum
    ) {
        String firstId = person(state, firstParent).id;
        String secondId = person(state, secondParent).id;
        java.util.Set<String> firstChildren = new java.util.HashSet<>();
        java.util.Set<String> secondChildren = new java.util.HashSet<>();
        for (Relation link : state.links) {
            if (!"parent".equals(link.type)) continue;
            if (firstId.equals(link.from)) firstChildren.add(link.to);
            if (secondId.equals(link.from)) secondChildren.add(link.to);
        }
        firstChildren.retainAll(secondChildren);
        assertTrue("Common children missing for " + firstParent, !firstChildren.isEmpty());
        float edge = minimum ? Float.MAX_VALUE : -Float.MAX_VALUE;
        for (String id : firstChildren) {
            Person child = state.people.get(id);
            if (child == null) continue;
            edge = minimum ? Math.min(edge, child.x) : Math.max(edge, child.x);
        }
        return edge;
    }

    private static float familyOffset(TreeState state, String firstChild, String lastChild) {
        Person first = person(state, firstChild);
        Person last = person(state, lastChild);
        List<Person> parents = new ArrayList<>();
        for (Relation link : state.links) {
            if ("parent".equals(link.type) && first.id.equals(link.to)) {
                Person parent = state.people.get(link.from);
                if (parent != null && !parents.contains(parent)) parents.add(parent);
            }
        }
        assertTrue("Parents missing for " + firstChild, !parents.isEmpty());
        float parentCenter = 0f;
        for (Person parent : parents) parentCenter += parent.x + TreeLayoutEngine.CARD_W / 2f;
        parentCenter /= parents.size();
        float childCenter = (first.x + last.x + TreeLayoutEngine.CARD_W) / 2f;
        return childCenter - parentCenter;
    }

    private static Person person(TreeState state, String name) {
        for (Person person : state.people.values()) {
            if (name.equals(person.name)) return person;
        }
        throw new AssertionError("Missing person: " + name);
    }
    private static TreeState fixture() throws Exception {
        try (InputStream input = KennedyLayoutRegressionTest.class.getResourceAsStream(
            "/fixtures/kennedy-208-layout.json")) {
            if (input == null) throw new AssertionError("Fixture not found");
            JSONObject document = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            TreeState state = new TreeState();
            JSONObject people = document.getJSONObject("people");
            Iterator<String> ids = people.keys();
            while (ids.hasNext()) {
                String id = ids.next();
                JSONObject value = people.getJSONObject(id);
                Person person = new Person(id);
                person.name = value.getString("name");
                person.bornYear = value.optString("bornYear", "");
                person.bornMonth = value.optString("bornMonth", "");
                person.bornDay = value.optString("bornDay", "");
                person.genderManual = value.optBoolean("genderManual", value.has("gender"));
                person.gender = PersonGender.normalize(value.optString("gender", ""));
                if (!person.genderManual) person.gender = PersonGender.infer(person.name);
                person.pinned = value.optBoolean("pinned", false);
                state.people.put(id, person);
            }
            JSONArray links = document.getJSONArray("links");
            for (int index = 0; index < links.length(); index++) {
                JSONObject link = links.getJSONObject(index);
                state.links.add(new Relation(link.getString("id"), link.getString("type"),
                    link.getString("from"), link.getString("to"), link.optString("side", "right")));
            }
            state.rootId = document.getString("rootId");
            return state;
        }
    }
}
