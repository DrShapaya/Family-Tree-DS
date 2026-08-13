package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;

public final class SmartLayoutSolverIntegrationTest {
    private static final float EPSILON = 0.01f;

    @Test
    public void productionBoundaryAppliesLocalFamilySpacingImprovement() {
        TreeState state = tooCloseSiblingFamily();
        LayoutSnapshot before = LayoutSnapshot.capture(state);

        SmartLayoutSolver.ApplyResult result = SmartLayoutSolver.improveAfterAddition(
            state,
            Collections.singleton("sideChild"),
            "side");

        assertTrue(result.applied);
        assertEquals("applied", result.reason);
        assertEquals(before.positionOf("root").x, state.people.get("root").x, EPSILON);
        assertEquals(
            TreeLayoutEngine.GRID * 5f,
            state.people.get("side").x
                - state.people.get("root").x
                - TreeLayoutEngine.CARD_W,
            EPSILON);
        float shift = state.people.get("side").x - before.positionOf("side").x;
        assertEquals(
            shift,
            state.people.get("sidePartner").x - before.positionOf("sidePartner").x,
            EPSILON);
        assertEquals(
            shift,
            state.people.get("sideChild").x - before.positionOf("sideChild").x,
            EPSILON);
    }

    @Test
    public void productionBoundaryKeepsCurrentLayoutWhenThereIsNoImprovement() {
        TreeState state = new TreeState();
        person(state, "root", 1000f, 1000f);
        person(state, "simpleSibling", 1320f, 1000f);
        link(state, "sibling", "root", "simpleSibling");
        state.rootId = "root";
        LayoutSnapshot before = LayoutSnapshot.capture(state);

        SmartLayoutSolver.ApplyResult result = SmartLayoutSolver.improveAfterAddition(
            state,
            Collections.singleton("simpleSibling"),
            "root");

        assertFalse(result.applied);
        assertEquals("no-improvement", result.reason);
        assertSnapshotEquals(before, state);
    }

    @Test
    public void productionBoundaryRejectsProposalWhenUnrelatedTreeIsInvalid() {
        TreeState state = tooCloseSiblingFamily();
        person(state, "unrelatedInvalid", -40f, 3000f);
        LayoutSnapshot before = LayoutSnapshot.capture(state);

        SmartLayoutSolver.ApplyResult result = SmartLayoutSolver.improveAfterAddition(
            state,
            Collections.singleton("sideChild"),
            "side");

        assertFalse(result.applied);
        assertEquals("no-solution", result.reason);
        assertSnapshotEquals(before, state);
    }

    @Test
    public void productionBoundaryRepairsCrossedAncestryAfterCollateralPartnerAdded() {
        TreeState state = crossedAncestryWithCollateralFamily();
        LayoutSnapshot before = LayoutSnapshot.capture(state);

        SmartLayoutSolver.ApplyResult result = SmartLayoutSolver.improveAfterAddition(
            state,
            Collections.singleton("paternalGrandUnclePartner"),
            "paternalGrandUncle");

        assertTrue(result.reason, result.applied);
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot after = LayoutSnapshot.capture(state);
        LayoutScorer.Score score = new LayoutScorer(new LayoutConstraints()).score(
            graph,
            after,
            before,
            LayoutWeights.defaults());
        assertEquals(0d, score.wrongSideError, 0.01d);
        assertEquals(before.positionOf("root").x, after.positionOf("root").x, EPSILON);
        assertEquals(before.positionOf("father").x, after.positionOf("father").x, EPSILON);
        assertEquals(before.positionOf("mother").x, after.positionOf("mother").x, EPSILON);
    }

    @Test
    public void finalCompactionClosesExcessiveSiblingFamilyGapsWithoutMovingRoot() {
        TreeState state = sparseSiblingFamilies();
        LayoutSnapshot before = LayoutSnapshot.capture(state);
        double beforeWidth = before.bounds().width();
        float firstX = state.people.get("child1").x;
        float thirdX = state.people.get("child3").x;

        SmartLayoutSolver.ApplyResult result = SmartLayoutSolver.compactRebuiltTree(state);

        assertTrue(result.reason, result.applied);
        assertEquals("compacted", result.reason);
        assertEquals(before.positionOf("father").x, state.people.get("father").x, EPSILON);
        assertTrue(LayoutSnapshot.capture(state).bounds().width() < beforeWidth);
        assertTrue(state.people.get("child1").x < state.people.get("child2").x);
        assertTrue(state.people.get("child2").x < state.people.get("child3").x);
        assertTrue(state.people.get("child1").x >= firstX);
        assertTrue(state.people.get("child3").x <= thirdX);
        BranchContour first = BranchContour.from(
            state,
            java.util.Arrays.asList("child1", "partner1"));
        BranchContour second = BranchContour.from(
            state,
            java.util.Arrays.asList("child2", "partner2"));
        BranchContour third = BranchContour.from(
            state,
            java.util.Arrays.asList("child3", "partner3"));
        assertEquals(TreeLayoutEngine.GRID * 5f, second.horizontalGapFrom(first), EPSILON);
        assertEquals(TreeLayoutEngine.GRID * 5f, third.horizontalGapFrom(second), EPSILON);
        assertTrue(new LayoutConstraints().validate(
            FamilyLayoutGraph.from(state),
            LayoutSnapshot.capture(state)).isValid());
    }

    @Test
    public void addingChildToSecondUnionSeparatesHalfSiblingFamiliesLocally() {
        TreeState state = twoUnionsWithCloseChildren();
        LayoutSnapshot before = LayoutSnapshot.capture(state);

        SmartLayoutSolver.ApplyResult result = SmartLayoutSolver.improveAfterAddition(
            state,
            Collections.singleton("childB"),
            "center");

        assertTrue(result.reason, result.applied);
        assertEquals(before.positionOf("center").x, state.people.get("center").x, EPSILON);
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        FamilyLayoutGraph.ParentFamily first = graph.parentFamilyOf("childA1");
        FamilyLayoutGraph.ParentFamily second = graph.parentFamilyOf("childB");
        assertTrue(first != null && second != null && first != second);
        assertEquals(directCenter(first.parents, state), directCenter(first.children, state),
            TreeLayoutEngine.GRID);
        assertEquals(operationReasons(result), directCenter(second.parents, state),
            directCenter(second.children, state), TreeLayoutEngine.GRID);
        BranchContour firstChildren = BranchContour.from(state, first.children);
        BranchContour secondChildren = BranchContour.from(state, second.children);
        BranchContour left = firstChildren.left() < secondChildren.left()
            ? firstChildren : secondChildren;
        BranchContour right = left == firstChildren ? secondChildren : firstChildren;
        assertEquals(TreeLayoutEngine.GRID * 5f, right.horizontalGapFrom(left), EPSILON);
        assertTrue(new LayoutConstraints().validate(
            graph,
            LayoutSnapshot.capture(state)).isValid());
    }

    @Test
    public void autoArrangeProductionPathKeepsTwoUnionsSeparate() {
        TreeState state = twoUnionsWithCloseChildren();
        state.autoArrangeOnAdd = true;
        float rootX = state.people.get("center").x;

        TreeLayoutEngine.layoutAfterAddition(
            state,
            Collections.singleton("childB"),
            "center",
            "children");

        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        FamilyLayoutGraph.ParentFamily first = graph.parentFamilyOf("childA1");
        FamilyLayoutGraph.ParentFamily second = graph.parentFamilyOf("childB");
        assertEquals(rootX, state.people.get("center").x, EPSILON);
        assertEquals(directCenter(first.parents, state), directCenter(first.children, state),
            TreeLayoutEngine.GRID);
        assertEquals(directCenter(second.parents, state), directCenter(second.children, state),
            TreeLayoutEngine.GRID);
        BranchContour firstChildren = BranchContour.from(state, first.children);
        BranchContour secondChildren = BranchContour.from(state, second.children);
        BranchContour left = firstChildren.left() < secondChildren.left()
            ? firstChildren : secondChildren;
        BranchContour right = left == firstChildren ? secondChildren : firstChildren;
        assertEquals(TreeLayoutEngine.GRID * 5f, right.horizontalGapFrom(left), EPSILON);
        assertTrue(new LayoutConstraints().validate(
            graph,
            LayoutSnapshot.capture(state)).isValid());
    }

    private static TreeState tooCloseSiblingFamily() {
        TreeState state = new TreeState();
        person(state, "root", 1000f, 1000f);
        person(state, "side", 1320f, 1000f);
        person(state, "sidePartner", 1640f, 1000f);
        person(state, "sideChild", 1480f, 1480f);
        link(state, "sibling", "root", "side");
        link(state, "partner", "side", "sidePartner");
        link(state, "parent", "side", "sideChild");
        link(state, "parent", "sidePartner", "sideChild");
        state.rootId = "root";
        return state;
    }

    private static TreeState crossedAncestryWithCollateralFamily() {
        TreeState state = new TreeState();
        person(state, "father", 10000f, 1480f);
        person(state, "mother", 10320f, 1480f);
        person(state, "root", 10160f, 1960f);
        person(state, "paternalGrandfather", 10400f, 1000f);
        person(state, "paternalGrandmother", 10720f, 1000f);
        person(state, "paternalGrandUncle", 11040f, 1000f);
        person(state, "paternalGrandUnclePartner", 11360f, 1000f);
        person(state, "paternalGrandUncleChild", 11200f, 1480f);
        person(state, "maternalGrandfather", 8000f, 1000f);
        person(state, "maternalGrandmother", 8320f, 1000f);
        link(state, "partner", "father", "mother");
        link(state, "parent", "father", "root");
        link(state, "parent", "mother", "root");
        link(state, "partner", "paternalGrandfather", "paternalGrandmother");
        link(state, "parent", "paternalGrandfather", "father");
        link(state, "parent", "paternalGrandmother", "father");
        link(state, "sibling", "paternalGrandfather", "paternalGrandUncle");
        link(state, "partner", "paternalGrandUncle", "paternalGrandUnclePartner");
        link(state, "parent", "paternalGrandUncle", "paternalGrandUncleChild");
        link(state, "parent", "paternalGrandUnclePartner", "paternalGrandUncleChild");
        link(state, "partner", "maternalGrandfather", "maternalGrandmother");
        link(state, "parent", "maternalGrandfather", "mother");
        link(state, "parent", "maternalGrandmother", "mother");
        state.rootId = "root";
        return state;
    }

    private static TreeState sparseSiblingFamilies() {
        TreeState state = new TreeState();
        person(state, "father", 10000f, 7000f);
        person(state, "mother", 10320f, 7000f);
        person(state, "child1", 6000f, 7480f);
        person(state, "partner1", 6320f, 7480f);
        person(state, "child2", 10000f, 7480f);
        person(state, "partner2", 10320f, 7480f);
        person(state, "child3", 14000f, 7480f);
        person(state, "partner3", 14320f, 7480f);
        link(state, "partner", "father", "mother");
        for (int index = 1; index <= 3; index++) {
            String child = "child" + index;
            link(state, "parent", "father", child);
            link(state, "parent", "mother", child);
            link(state, "partner", child, "partner" + index);
        }
        state.rootId = "father";
        return state;
    }

    private static TreeState twoUnionsWithCloseChildren() {
        TreeState state = new TreeState();
        person(state, "center", 1000f, 1000f);
        person(state, "partnerA", 680f, 1000f);
        person(state, "partnerB", 1320f, 1000f);
        person(state, "childA1", 680f, 1480f);
        person(state, "childA2", 1000f, 1480f);
        person(state, "childB", 1320f, 1480f);
        link(state, "partner", "center", "partnerA");
        link(state, "partner", "center", "partnerB");
        link(state, "parent", "center", "childA1");
        link(state, "parent", "partnerA", "childA1");
        link(state, "parent", "center", "childA2");
        link(state, "parent", "partnerA", "childA2");
        link(state, "parent", "center", "childB");
        link(state, "parent", "partnerB", "childB");
        state.rootId = "center";
        return state;
    }

    private static float directCenter(Iterable<String> ids, TreeState state) {
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        for (String id : ids) {
            Person person = state.people.get(id);
            if (person == null) continue;
            left = Math.min(left, person.x);
            right = Math.max(right, person.x + TreeLayoutEngine.CARD_W);
        }
        return (left + right) / 2f;
    }

    private static String operationReasons(SmartLayoutSolver.ApplyResult result) {
        if (result == null || result.localResult == null
            || result.localResult.proposal == null) return "no-operations";
        StringBuilder value = new StringBuilder();
        for (LayoutOperation operation : result.localResult.proposal.operations) {
            if (value.length() > 0) value.append(", ");
            value.append(operation.reason());
        }
        return value.toString();
    }

    private static void assertSnapshotEquals(LayoutSnapshot expected, TreeState actual) {
        for (String id : expected.positions.keySet()) {
            assertEquals(expected.positionOf(id).x, actual.people.get(id).x, EPSILON);
            assertEquals(expected.positionOf(id).y, actual.people.get(id).y, EPSILON);
        }
    }

    private static void person(TreeState state, String id, float x, float y) {
        Person person = new Person(id);
        person.name = id;
        person.x = x;
        person.y = y;
        state.people.put(id, person);
    }

    private static void link(TreeState state, String type, String from, String to) {
        state.links.add(new Relation(type + "_" + from + "_" + to, type, from, to));
    }
}
