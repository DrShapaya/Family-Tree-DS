package ru.drshapaya.androidft2;

import org.junit.Test;
import static org.junit.Assert.*;

/** Anonymous graphs exercise the rules independently of imported names and IDs. */
public final class LineageBandReflowTest {
    @Test public void compactsWholeIndependentContourAndKeepsItsCenter() {
        TreeState state = independentContours();
        assertTrue(BranchSlackCompaction.improve(state));
        valid(state);
        assertEquals(200f, state.people.get("right").x - state.people.get("leftMate").x - 280f, .5f);
        assertEquals(320f, state.people.get("rightMate").x - state.people.get("right").x, .5f);
        assertEquals(center(state, "right", "rightMate"), state.people.get("rightChild").x + 140f, .5f);
        assertEquals(480f, state.people.get("rightChild").y - state.people.get("right").y, .5f);
        assertFalse(BranchSlackCompaction.improve(state));
    }

    @Test public void pinnedContourPreventsUnnecessaryTranslation() {
        TreeState state = independentContours();
        state.people.get("rightChild").pinned = true;
        assertFalse(BranchSlackCompaction.improve(state));
        assertEquals(7000f, state.people.get("right").x, .5f);
        assertEquals(7160f, state.people.get("rightChild").x, .5f);
        valid(state);
    }

    @Test public void repeatedSharedMarriagesStayTogetherAcrossLaterParentAndLeafPasses() {
        TreeState state = new TreeState();
        pair(state, "a", "ap", 4000, 400);
        pair(state, "b", "bp", 4800, 400);
        pair(state, "firstA", "firstB", 2400, 880);
        pair(state, "foreign", "foreignMate", 3520, 880);
        pair(state, "secondA", "secondB", 7000, 880);
        person(state, "brother", 7800, 880);
        person(state, "foreignChild1", 3520, 1360);
        person(state, "foreignChild2", 3840, 1360);
        for (String id : new String[]{"firstA", "secondA"}) child(state, "a", "ap", id);
        for (String id : new String[]{"firstB", "secondB", "brother"}) child(state, "b", "bp", id);
        child(state, "foreign", "foreignMate", "foreignChild1");
        child(state, "foreign", "foreignMate", "foreignChild2");
        state.rootId = "a";
        valid(state);
        LineageBandReflow.improve(state);
        LineageBandReflow.alignParents(state);
        TreeLayoutEngine.layout(state);
        valid(state);
        assertTrue(state.people.get("firstB").x < state.people.get("secondA").x);
        assertTrue(state.people.get("secondB").x < state.people.get("brother").x);
        assertTrue(state.people.get("brother").x < state.people.get("foreign").x);
        assertEquals(200f, state.people.get("brother").x - state.people.get("secondB").x - 280f, .5f);
    }

    private static TreeState independentContours() {
        TreeState state = new TreeState();
        pair(state, "left", "leftMate", 4000, 400);
        pair(state, "right", "rightMate", 7000, 400);
        person(state, "leftChild", 4160, 880);
        person(state, "rightChild", 7160, 880);
        child(state, "left", "leftMate", "leftChild");
        child(state, "right", "rightMate", "rightChild");
        state.rootId = "left";
        return state;
    }

    @Test public void sharedDescendantDoesNotDisableIndependentParents() {
        TreeState state = new TreeState();
        pair(state, "a", "ap", 800, 400);
        pair(state, "b", "bp", 2400, 400);
        pair(state, "c", "cp", 5000, 400);
        pair(state, "ac", "bc", 1400, 880);
        person(state, "shared", 1560, 1360);
        person(state, "c1", 4000, 880);
        person(state, "c2", 4320, 880);
        child(state, "a", "ap", "ac");
        child(state, "b", "bp", "bc");
        child(state, "ac", "bc", "shared");
        child(state, "c", "cp", "c1");
        child(state, "c", "cp", "c2");
        state.rootId = "shared";
        valid(state);

        assertTrue(LineageBandReflow.improve(state));

        valid(state);
        assertEquals(center(state, "c1", "c2"), center(state, "c", "cp"), 20f);
    }

    @Test public void movesAllRightHalfSiblingsPastSharedMarriageTogether() {
        TreeState state = new TreeState();
        pair(state, "a", "ap", 0, 400);
        pair(state, "b", "bp", 960, 400);
        person(state, "otherUnion", 1600, 400);
        relation(state, "partner", "bp", "otherUnion");
        pair(state, "ac", "bc", 880, 880);
        person(state, "half1", 0, 880);
        person(state, "half2", 320, 880);
        child(state, "a", "ap", "ac");
        child(state, "b", "bp", "bc");
        child(state, "bp", "otherUnion", "half1");
        child(state, "bp", "otherUnion", "half2");
        for (String id : new String[]{"a", "ap", "b", "bp", "otherUnion"}) state.people.get(id).pinned = true;
        state.rootId = "a";
        valid(state);

        LineageBandReflow.improve(state);

        valid(state);
        assertTrue(state.people.get("bc").x < state.people.get("half1").x);
        assertTrue(state.people.get("half1").x < state.people.get("half2").x);
        assertEquals(40f, state.people.get("half2").x - state.people.get("half1").x - 280f, .5f);
    }

    @Test public void centersParentsAfterExchangingForeignChildlessHousehold() {
        TreeState state = new TreeState();
        pair(state, "grand", "grandMate", 0, 400);
        pair(state, "parents", "mate", 800, 880);
        pair(state, "leaf", "leafMate", 1600, 880);
        person(state, "child", 2400, 1360);
        child(state, "grand", "grandMate", "leaf");
        child(state, "parents", "mate", "child");
        state.rootId = "grand";
        valid(state);

        assertTrue(LineageBandReflow.alignParents(state));

        valid(state);
        assertTrue(state.people.get("leafMate").x < state.people.get("parents").x);
        assertEquals(state.people.get("child").x + 140f, center(state, "parents", "mate"), 20f);
    }

    @Test public void closingSiblingGapCarriesIndependentUpperParents() {
        TreeState state = new TreeState();
        pair(state, "parent", "parentMate", 5000, 400);
        pair(state, "branch", "branchMate", 4000, 880);
        person(state, "single", 6000, 880);
        person(state, "grandchild", 4160, 1360);
        child(state, "parent", "parentMate", "branch");
        child(state, "parent", "parentMate", "single");
        child(state, "branch", "branchMate", "grandchild");
        state.rootId = "grandchild";
        valid(state);
        float previousParent = state.people.get("parent").x;

        assertTrue(SiblingContourPacking.improve(state));

        valid(state);
        assertEquals(200f, state.people.get("single").x - state.people.get("branchMate").x - 280f, .5f);
        assertNotEquals(previousParent, state.people.get("parent").x, .5f);
        assertEquals(0f, center(state, "branch", "single") - center(state, "parent", "parentMate"), 20f);
    }

    @Test public void manyDisconnectedCardsRemainSeparatedBelowPinnedCard() {
        TreeState state = new TreeState();
        person(state, "parent", 1000, 400);
        person(state, "child", 1000, 880);
        relation(state, "parent", "parent", "child");
        person(state, "pinned", 1200, 15000);
        state.people.get("pinned").pinned = true;
        for (int i = 0; i < 100; i++) person(state, "isolated" + i, 0, 0);
        state.rootId = "parent";

        TreeLayoutEngine.layoutFresh(state);

        valid(state);
        assertEquals(1200f, state.people.get("pinned").x, .5f);
        assertEquals(15000f, state.people.get("pinned").y, .5f);
        for (int i = 0; i < 100; i++) assertTrue(state.people.get("isolated" + i).y > 15160f);
    }

    @Test public void rightMovingPlainSiblingsReleaseSlotsBeforeTheirFirstSibling() {
        TreeState state = movingLeafRun(true);
        TreeLayoutEngine.layout(state);
        valid(state);
        assertTrue(state.people.get("first").x >= 3520f);
        assertEquals(40f, state.people.get("second").x - state.people.get("first").x - 280f, .5f);
        assertEquals(40f, state.people.get("third").x - state.people.get("second").x - 280f, .5f);
    }

    @Test public void leftMovingPlainSiblingsKeepTheirInternalSpacing() {
        TreeState state = movingLeafRun(false);
        TreeLayoutEngine.layout(state);
        valid(state);
        assertTrue(state.people.get("third").x <= 4800f);
        assertEquals(40f, state.people.get("second").x - state.people.get("first").x - 280f, .5f);
        assertEquals(40f, state.people.get("third").x - state.people.get("second").x - 280f, .5f);
    }

    @Test public void alignedMiddleLeafUsesWholeSiblingHouseholds() {
        TreeState state = new TreeState();
        pair(state, "parent", "mate", 1600, 400);
        pair(state, "left", "leftMate", 800, 880);
        person(state, "middle", 1760, 880);
        pair(state, "right", "rightMate", 2400, 880);
        for (String id : new String[]{"left", "middle", "right"}) child(state, "parent", "mate", id);
        state.rootId = "parent";
        valid(state);
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot before = LayoutSnapshot.capture(state);
        java.util.Set<String> parents = new java.util.HashSet<>(java.util.Arrays.asList("parent", "mate"));
        LayoutSnapshot shifted = before.shifted(parents, 400f, 0f);
        assertFalse(SiblingContourPacking.preservesProperlyAlignedLeaves(graph, before, shifted));
    }

    private static TreeState movingLeafRun(boolean right) {
        TreeState state = new TreeState();
        pair(state, "parent", "mate", 4000, 400);
        state.people.get("parent").pinned = true;
        state.people.get("mate").pinned = true;
        pair(state, "branch", "branchMate", right ? 6400 : 1600, 880);
        float x = right ? 1600 : 7000;
        person(state, "first", x, 880);
        person(state, "second", x + 320, 880);
        person(state, "third", x + 640, 880);
        if (!right) child(state, "parent", "mate", "branch");
        for (String id : new String[]{"first", "second", "third"}) child(state, "parent", "mate", id);
        if (right) child(state, "parent", "mate", "branch");
        state.rootId = "parent";
        return state;
    }

    private static float center(TreeState s, String a, String b) {
        return (s.people.get(a).x + s.people.get(b).x) / 2f + 140f;
    }
    private static void valid(TreeState state) {
        LayoutConstraints.ValidationResult result = new LayoutConstraints().validate(FamilyLayoutGraph.from(state), LayoutSnapshot.capture(state));
        assertTrue(result.violations.toString(), result.isValid());
    }
    private static void person(TreeState state, String id, float x, float y) {
        Person p = new Person(id); p.name = id; p.x = x; p.y = y; state.people.put(id, p);
    }
    private static void pair(TreeState s, String a, String b, float x, float y) {
        person(s, a, x, y); person(s, b, x + 320, y); relation(s, "partner", a, b);
    }
    private static void child(TreeState s, String a, String b, String c) {
        relation(s, "parent", a, c); relation(s, "parent", b, c);
    }
    private static void relation(TreeState s, String type, String a, String b) {
        s.links.add(new Relation(type + a + b, type, a, b));
    }
}
