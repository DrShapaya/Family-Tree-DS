package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

public final class LayoutSolverFoundationTest {
    private static final float EPSILON = 0.01f;

    @Test
    public void semanticGraphRecognizesMirroredAncestryWithoutNamesOrCoordinates() {
        TreeState state = symmetricTree();
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);

        assertEquals(10, graph.people.size());
        assertEquals(5, graph.partnerUnits.size());
        assertEquals(4, graph.parentFamilies.size());
        assertTrue(graph.structurallyMirrored("partner", "root"));
        assertEquals(Integer.valueOf(0), graph.generationByPerson.get("root"));
        assertEquals(Integer.valueOf(-1), graph.generationByPerson.get("rightParent1"));
        assertEquals(Integer.valueOf(-2), graph.generationByPerson.get("rightGrandparent1"));
    }

    @Test
    public void semanticGraphKeepsMultipleUnionsSeparateAndGroupsHalfSiblings() {
        TreeState state = multipleUnionFamily();

        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);

        assertEquals(5, graph.partnerUnits.size());
        assertEquals(2, graph.partnerUnitsOf("center").size());
        assertEquals(1, graph.partnerUnitsOf("partnerA").size());
        assertEquals(1, graph.partnerUnitsOf("partnerB").size());
        for (FamilyLayoutGraph.PartnerUnit unit : graph.partnerUnits) {
            assertFalse(unit.people.contains("partnerA") && unit.people.contains("partnerB"));
        }
        assertEquals(2, graph.parentFamilies.size());
        assertTrue(graph.parentFamilyOf("childA1") == graph.parentFamilyOf("childA2"));
        assertFalse(graph.parentFamilyOf("childA1") == graph.parentFamilyOf("childB"));
        assertEquals(1, graph.siblingGroups.size());
        assertTrue(graph.siblingGroups.get(0).people.containsAll(
            Arrays.asList("childA1", "childA2", "childB")));

        FamilyLayoutGraph.PartnerUnit firstUnion = graph.partnerUnitsOf("center").get(0);
        FamilyLayoutGraph.PartnerUnit secondUnion = graph.partnerUnitsOf("center").get(1);
        assertEquals(2, firstUnion.children.size());
        assertEquals(1, secondUnion.children.size());
    }

    @Test
    public void branchContourMatchesEqualStructuresAndComputesRequiredGap() {
        TreeState state = symmetricTree();
        BranchContour left = BranchContour.from(state, Arrays.asList(
            "leftParent1",
            "leftParent2",
            "leftGrandparent1",
            "leftGrandparent2"));
        BranchContour right = BranchContour.from(state, Arrays.asList(
            "rightParent1",
            "rightParent2",
            "rightGrandparent1",
            "rightGrandparent2"));

        assertTrue(left.structurallyMatches(right));
        BranchContour collidingRight = right.shifted(left.left() - right.left());
        assertTrue(
            collidingRight.requiredRightShiftFrom(left, TreeLayoutEngine.GRID * 5f) > 0f);
        assertEquals(600f, left.rows.get(Math.round(7440f / 40f)).width(), EPSILON);
    }

    @Test
    public void snapshotOperationsAreImmutableAndExactlyReversible() {
        TreeState state = symmetricTree();
        LayoutSnapshot original = LayoutSnapshot.capture(state);
        LayoutOperation move = new ShiftLayoutOperation(
            new LinkedHashSet<>(Arrays.asList("rightParent1", "rightParent2")),
            320f,
            0f,
            "make-room");

        LayoutSnapshot moved = move.apply(original);
        LayoutSnapshot restored = move.inverse().apply(moved);

        assertEquals(12120f, original.positionOf("rightParent1").x, EPSILON);
        assertEquals(12440f, moved.positionOf("rightParent1").x, EPSILON);
        for (String id : original.positions.keySet()) {
            assertEquals(original.positionOf(id).x, restored.positionOf(id).x, EPSILON);
            assertEquals(original.positionOf(id).y, restored.positionOf(id).y, EPSILON);
        }
        assertEquals("make-room", move.reason());
    }

    @Test
    public void hardConstraintsRejectOverlapAndWrongGeneration() {
        TreeState state = symmetricTree();
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutConstraints constraints = new LayoutConstraints();
        assertTrue(constraints.validate(graph, LayoutSnapshot.capture(state)).isValid());

        LayoutSnapshot invalid = LayoutSnapshot.capture(state)
            .shifted(Arrays.asList("root"), -320f, -480f);
        LayoutConstraints.ValidationResult validation = constraints.validate(graph, invalid);

        assertFalse(validation.isValid());
        assertTrue(validation.violations.stream().anyMatch(
            violation -> "card-overlap".equals(violation.code)));
        assertTrue(validation.violations.stream().anyMatch(
            violation -> "partner-generation".equals(violation.code)));
    }

    @Test
    public void scorerPrefersSymmetricSolutionForIdenticalBranches() {
        TreeState symmetric = symmetricTree();
        TreeState asymmetric = TreeStateCopier.copy(symmetric);
        set(asymmetric, "leftParent1", 11160f, 7440f);
        set(asymmetric, "leftParent2", 11480f, 7440f);
        set(asymmetric, "rightParent1", 12280f, 7440f);
        set(asymmetric, "rightParent2", 12600f, 7440f);
        set(asymmetric, "leftGrandparent1", 11320f, 6960f);
        set(asymmetric, "leftGrandparent2", 11640f, 6960f);
        set(asymmetric, "rightGrandparent1", 12120f, 6960f);
        set(asymmetric, "rightGrandparent2", 12440f, 6960f);

        FamilyLayoutGraph graph = FamilyLayoutGraph.from(symmetric);
        LayoutScorer scorer = new LayoutScorer(new LayoutConstraints());
        LayoutScorer.Score symmetricScore = scorer.score(
            graph,
            LayoutSnapshot.capture(symmetric),
            null,
            LayoutWeights.defaults());
        LayoutScorer.Score asymmetricScore = scorer.score(
            graph,
            LayoutSnapshot.capture(asymmetric),
            null,
            LayoutWeights.defaults());

        assertNotNull(symmetricScore);
        assertEquals(0, symmetricScore.hardViolations);
        assertEquals(0d, symmetricScore.symmetryError, 0.01d);
        assertTrue(asymmetricScore.symmetryError > symmetricScore.symmetryError);
        assertTrue(
            "зеркальный вариант должен иметь меньший общий штраф: symmetric="
                + symmetricScore.total + " asymmetric=" + asymmetricScore.total,
            symmetricScore.total < asymmetricScore.total);
    }

    @Test
    public void beamSearchFindsSymmetricCandidateInsteadOfUsingCoordinateTemplate() {
        TreeState asymmetric = symmetricTree();
        set(asymmetric, "leftParent1", 11160f, 7440f);
        set(asymmetric, "leftParent2", 11480f, 7440f);
        set(asymmetric, "rightParent1", 12280f, 7440f);
        set(asymmetric, "rightParent2", 12600f, 7440f);
        set(asymmetric, "leftGrandparent1", 11320f, 6960f);
        set(asymmetric, "leftGrandparent2", 11640f, 6960f);
        set(asymmetric, "rightGrandparent1", 12120f, 6960f);
        set(asymmetric, "rightGrandparent2", 12440f, 6960f);
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(asymmetric);
        LayoutSnapshot initial = LayoutSnapshot.capture(asymmetric);
        LayoutScorer scorer = new LayoutScorer(new LayoutConstraints());
        LayoutScorer.Score initialScore = scorer.score(
            graph,
            initial,
            null,
            LayoutWeights.defaults());
        BeamLayoutSolver solver = new BeamLayoutSolver(
            scorer,
            Collections.singletonList(new SymmetricBranchCandidateGenerator(200f)),
            8,
            3);

        BeamLayoutSolver.Result result = solver.solve(
            graph,
            initial,
            null,
            LayoutWeights.defaults());

        assertTrue(result.exploredCandidates >= 2);
        assertEquals(1, result.operations.size());
        assertTrue(result.score.total < initialScore.total);
        assertEquals(0d, result.score.symmetryError, 0.01d);
        assertEquals(11000f, result.snapshot.positionOf("leftParent1").x, EPSILON);
        assertEquals(12120f, result.snapshot.positionOf("rightParent1").x, EPSILON);
        assertTrue(new LayoutConstraints().validate(graph, result.snapshot).isValid());
    }

    @Test
    public void collisionGeneratorMovesWholeSideFamilyWithoutMovingMainTrunk() {
        TreeState state = collidingSideFamily();
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot initial = LayoutSnapshot.capture(state);
        LayoutConstraints constraints = new LayoutConstraints();
        assertFalse(constraints.validate(graph, initial).isValid());

        LayoutCandidateGenerator generator = new CollisionBranchCandidateGenerator(
            constraints,
            TreeLayoutEngine.GRID);
        BeamLayoutSolver solver = new BeamLayoutSolver(
            new LayoutScorer(constraints),
            Collections.singletonList(generator),
            12,
            4);

        BeamLayoutSolver.Result result = solver.solve(
            graph,
            initial,
            initial,
            LayoutWeights.defaults());

        assertTrue(constraints.validate(graph, result.snapshot).isValid());
        assertEquals(initial.positionOf("root").x, result.snapshot.positionOf("root").x, EPSILON);
        assertEquals(
            initial.positionOf("mainParent").x,
            result.snapshot.positionOf("mainParent").x,
            EPSILON);
        float sideShift = result.snapshot.positionOf("side").x - initial.positionOf("side").x;
        assertTrue(Math.abs(sideShift) >= TreeLayoutEngine.GRID);
        assertEquals(
            sideShift,
            result.snapshot.positionOf("sidePartner").x
                - initial.positionOf("sidePartner").x,
            EPSILON);
        assertEquals(
            sideShift,
            result.snapshot.positionOf("sideBaby").x
                - initial.positionOf("sideBaby").x,
            EPSILON);
    }

    @Test
    public void collisionCandidatesNeverMoveOnlyOneCardOfSideFamily() {
        TreeState state = collidingSideFamily();
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutCandidateGenerator generator = new CollisionBranchCandidateGenerator(
            new LayoutConstraints(),
            TreeLayoutEngine.GRID);

        java.util.List<LayoutOperation> operations = generator.generate(
            graph,
            LayoutSnapshot.capture(state));

        assertFalse(operations.isEmpty());
        for (LayoutOperation operation : operations) {
            assertFalse(operation.affectedIds().contains("root"));
            assertFalse(operation.affectedIds().contains("mainParent"));
            assertTrue(operation.affectedIds().contains("side"));
            assertTrue(operation.affectedIds().contains("sidePartner"));
            assertTrue(operation.affectedIds().contains("sideBaby"));
        }
    }

    @Test
    public void siblingSwapExchangesWholeBranchesAndCanReduceOccupiedWidth() {
        TreeState state = siblingsWithWideMiddleBranch();
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot initial = LayoutSnapshot.capture(state);
        LayoutCandidateGenerator generator = new SiblingBranchSwapCandidateGenerator(
            TreeLayoutEngine.GRID);

        java.util.List<LayoutOperation> operations = generator.generate(graph, initial);

        assertFalse(operations.isEmpty());
        LayoutOperation wideWithRight = operations.stream()
            .filter(operation -> operation.affectedIds().contains("wideChild1"))
            .filter(operation -> operation.affectedIds().contains("rightSibling"))
            .findFirst()
            .orElse(null);
        assertNotNull(wideWithRight);
        assertTrue(wideWithRight.affectedIds().contains("wideSibling"));
        assertTrue(wideWithRight.affectedIds().contains("widePartner"));
        assertTrue(wideWithRight.affectedIds().contains("wideChild2"));
        assertFalse(wideWithRight.affectedIds().contains("leftSibling"));
        assertFalse(wideWithRight.affectedIds().contains("parent1"));
        assertFalse(wideWithRight.affectedIds().contains("parent2"));

        LayoutSnapshot swapped = wideWithRight.apply(initial);
        assertTrue(swapped.positionOf("rightSibling").x
            < swapped.positionOf("wideSibling").x);
        assertTrue(swapped.bounds().width() < initial.bounds().width());
        assertTrue(new LayoutConstraints().validate(graph, swapped).isValid());
    }

    @Test
    public void diagnosticComparisonProposesFixWithoutMutatingCurrentTree() {
        TreeState state = collidingSideFamily();
        LayoutSnapshot before = LayoutSnapshot.capture(state);

        LayoutSolverDiagnostics.Report report = LayoutSolverDiagnostics.analyze(state);

        assertTrue(report.improvesCurrent());
        assertTrue(report.currentScore.hardViolations > 0);
        assertEquals(0, report.proposal.score.hardViolations);
        assertTrue(report.proposal.exploredCandidates > 1);
        for (String id : before.positions.keySet()) {
            assertEquals(before.positionOf(id).x, state.people.get(id).x, EPSILON);
            assertEquals(before.positionOf(id).y, state.people.get(id).y, EPSILON);
        }
    }

    @Test
    public void localSolverExpandsToHouseholdButLeavesDistantBranchOutside() {
        TreeState state = collidingSideFamilyWithDistantAncestry();
        LayoutSnapshot before = LayoutSnapshot.capture(state);

        LocalBeamLayoutSolver.Result result = LayoutSolverDiagnostics.analyzeAfterAddition(
            state,
            Collections.singleton("sideBaby"),
            "side");

        assertTrue(result.solved);
        assertEquals(2, result.attempts);
        assertEquals(1, result.usedRegion.expansionLevel);
        assertTrue(result.usedRegion.activeIds.contains("side"));
        assertTrue(result.usedRegion.activeIds.contains("sidePartner"));
        assertTrue(result.usedRegion.activeIds.contains("sideBaby"));
        assertFalse(result.usedRegion.activeIds.contains("remoteParent1"));
        assertFalse(result.usedRegion.activeIds.contains("remoteParent2"));
        assertTrue(result.usedRegion.lockedIds.contains("root"));
        assertTrue(new LayoutConstraints().validate(
            FamilyLayoutGraph.from(state),
            result.proposal.snapshot).isValid());
        assertEquals(
            before.positionOf("root").x,
            result.proposal.snapshot.positionOf("root").x,
            EPSILON);
        assertEquals(
            before.positionOf("remoteParent1").x,
            result.proposal.snapshot.positionOf("remoteParent1").x,
            EPSILON);
        assertEquals(
            before.positionOf("remoteParent2").x,
            result.proposal.snapshot.positionOf("remoteParent2").x,
            EPSILON);
        for (LayoutOperation operation : result.proposal.operations) {
            assertTrue(result.usedRegion.activeIds.containsAll(operation.affectedIds()));
            assertTrue(Collections.disjoint(
                result.usedRegion.lockedIds,
                operation.affectedIds()));
        }
    }

    @Test
    public void localSolverCentersNewParentsOverDirectChildWithoutUsingLowerRows() {
        TreeState state = offCenterParentsWithWideLowerGeneration();
        LayoutSnapshot before = LayoutSnapshot.capture(state);

        LocalBeamLayoutSolver.Result result = LayoutSolverDiagnostics.analyzeAfterAddition(
            state,
            Arrays.asList("newParent1", "newParent2"),
            "root");

        assertTrue(result.solved);
        assertEquals(1, result.attempts);
        assertEquals(0, result.usedRegion.expansionLevel);
        assertEquals(
            before.positionOf("root").x,
            result.proposal.snapshot.positionOf("root").x,
            EPSILON);
        assertEquals(
            before.positionOf("farChild1").x,
            result.proposal.snapshot.positionOf("farChild1").x,
            EPSILON);
        assertEquals(
            before.positionOf("farChild2").x,
            result.proposal.snapshot.positionOf("farChild2").x,
            EPSILON);
        float parentCenter = (
            result.proposal.snapshot.positionOf("newParent1").x
                + TreeLayoutEngine.CARD_W / 2f
                + result.proposal.snapshot.positionOf("newParent2").x
                + TreeLayoutEngine.CARD_W / 2f) / 2f;
        float rootCenter = result.proposal.snapshot.positionOf("root").x
            + TreeLayoutEngine.CARD_W / 2f;
        assertEquals(rootCenter, parentCenter, EPSILON);
        assertTrue(new LayoutConstraints().validate(
            FamilyLayoutGraph.from(state),
            result.proposal.snapshot).isValid());
    }

    @Test
    public void beamSearchKeepsInvalidIntermediateCandidatesForMultiStepRepair() {
        TreeState state = new TreeState();
        person(state, "root", 1000f, 1000f);
        person(state, "left", 1000f, 1000f);
        person(state, "right", 1000f, 1000f);
        state.rootId = "root";
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot initial = LayoutSnapshot.capture(state);
        LayoutCandidateGenerator twoStepGenerator = (candidateGraph, snapshot) -> {
            java.util.List<LayoutOperation> operations = new java.util.ArrayList<>();
            if (Math.abs(snapshot.positionOf("left").x - 1000f) < EPSILON) {
                operations.add(new ShiftLayoutOperation(
                    Collections.singleton("left"),
                    -320f,
                    0f,
                    "repair-left"));
            }
            if (Math.abs(snapshot.positionOf("right").x - 1000f) < EPSILON) {
                operations.add(new ShiftLayoutOperation(
                    Collections.singleton("right"),
                    320f,
                    0f,
                    "repair-right"));
            }
            return operations;
        };

        BeamLayoutSolver.Result result = new BeamLayoutSolver(
            new LayoutScorer(new LayoutConstraints()),
            Collections.singletonList(twoStepGenerator),
            8,
            2).solve(graph, initial, initial, LayoutWeights.defaults());

        assertEquals(2, result.operations.size());
        assertEquals(0, result.score.hardViolations);
        assertTrue(new LayoutConstraints().validate(graph, result.snapshot).isValid());
    }

    @Test
    public void semanticSiblingGroupIncludesExplicitSiblingsWithoutParents() {
        TreeState state = tooCloseSiblingFamily();
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);

        assertEquals(1, graph.siblingGroups.size());
        assertTrue(graph.siblingGroups.get(0).people.contains("root"));
        assertTrue(graph.siblingGroups.get(0).people.contains("side"));
        assertEquals(2, graph.siblingGroups.get(0).people.size());
    }

    @Test
    public void localSolverUsesFiveCellsBesideFamilyAndMovesWholeBranch() {
        TreeState state = tooCloseSiblingFamily();
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot before = LayoutSnapshot.capture(state);
        LayoutScorer scorer = new LayoutScorer(new LayoutConstraints());
        LayoutScorer.Score beforeScore = scorer.score(
            graph,
            before,
            before,
            LayoutWeights.defaults());

        LocalBeamLayoutSolver.Result result = LayoutSolverDiagnostics.analyzeAfterAddition(
            state,
            Collections.singleton("sideChild"),
            "side");

        assertTrue(result.solved);
        assertEquals(2, result.attempts);
        assertEquals(1, result.usedRegion.expansionLevel);
        assertEquals(160d, beforeScore.siblingSpacingError, 0.01d);
        assertEquals(0d, result.proposal.score.siblingSpacingError, 0.01d);
        assertEquals(
            TreeLayoutEngine.GRID * 5f,
            result.proposal.snapshot.positionOf("side").x
                - result.proposal.snapshot.positionOf("root").x
                - TreeLayoutEngine.CARD_W,
            EPSILON);
        float shift = result.proposal.snapshot.positionOf("side").x
            - before.positionOf("side").x;
        assertEquals(
            shift,
            result.proposal.snapshot.positionOf("sidePartner").x
                - before.positionOf("sidePartner").x,
            EPSILON);
        assertEquals(
            shift,
            result.proposal.snapshot.positionOf("sideChild").x
                - before.positionOf("sideChild").x,
            EPSILON);
        assertEquals(before.positionOf("root").x,
            result.proposal.snapshot.positionOf("root").x,
            EPSILON);
    }

    @Test
    public void simpleSiblingBranchesKeepOneCellGap() {
        TreeState state = new TreeState();
        person(state, "root", 1000f, 1000f);
        person(state, "simpleSibling", 1320f, 1000f);
        link(state, "sibling", "root", "simpleSibling");
        state.rootId = "root";
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot snapshot = LayoutSnapshot.capture(state);

        LayoutScorer.Score score = new LayoutScorer(new LayoutConstraints()).score(
            graph,
            snapshot,
            snapshot,
            LayoutWeights.defaults());

        assertEquals(0d, score.siblingSpacingError, 0.01d);
        assertTrue(new SiblingSpacingCandidateGenerator()
            .generate(graph, snapshot).isEmpty());
    }

    @Test
    public void lineageBranchIncludesCollateralFamilyButExcludesRootPath() {
        TreeState state = crossedAncestryWithCollateralFamily();
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        FamilyLayoutGraph.ParentFamily family = graph.parentFamilyOf("father");

        java.util.Set<String> branch = graph.lineageBranch(family.parents);

        assertTrue(branch.contains("paternalGrandfather"));
        assertTrue(branch.contains("paternalGrandmother"));
        assertTrue(branch.contains("paternalGrandUncle"));
        assertTrue(branch.contains("paternalGrandUnclePartner"));
        assertTrue(branch.contains("paternalGrandUncleChild"));
        assertFalse(branch.contains("father"));
        assertFalse(branch.contains("mother"));
        assertFalse(branch.contains("root"));
    }

    @Test
    public void solverRestoresBothAncestrySidesAndMovesCollateralFamilyTogether() {
        TreeState state = crossedAncestryWithCollateralFamily();
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot initial = LayoutSnapshot.capture(state);
        LayoutConstraints constraints = new LayoutConstraints();
        LayoutScorer scorer = new LayoutScorer(constraints);
        LayoutScorer.Score initialScore = scorer.score(
            graph,
            initial,
            initial,
            LayoutWeights.defaults());
        BeamLayoutSolver.Result result = new BeamLayoutSolver(
            scorer,
            SmartLayoutSolver.defaultGenerators(constraints),
            16,
            4).solve(graph, initial, initial, LayoutWeights.defaults());

        assertTrue(initialScore.wrongSideError > 0d);
        assertEquals(0d, result.score.wrongSideError, 0.01d);
        assertTrue(result.score.total < initialScore.total);
        assertTrue(constraints.validate(graph, result.snapshot).isValid());
        assertEquals(initial.positionOf("root").x,
            result.snapshot.positionOf("root").x,
            EPSILON);
        assertEquals(initial.positionOf("father").x,
            result.snapshot.positionOf("father").x,
            EPSILON);
        assertEquals(initial.positionOf("mother").x,
            result.snapshot.positionOf("mother").x,
            EPSILON);

        float collateralShift = result.snapshot.positionOf("paternalGrandUncle").x
            - initial.positionOf("paternalGrandUncle").x;
        assertEquals(collateralShift,
            result.snapshot.positionOf("paternalGrandUnclePartner").x
                - initial.positionOf("paternalGrandUnclePartner").x,
            EPSILON);
        assertEquals(collateralShift,
            result.snapshot.positionOf("paternalGrandUncleChild").x
                - initial.positionOf("paternalGrandUncleChild").x,
            EPSILON);

        java.util.Set<String> paternalIds = graph.lineageBranch(
            graph.parentFamilyOf("father").parents);
        java.util.Set<String> maternalIds = graph.lineageBranch(
            graph.parentFamilyOf("mother").parents);
        BranchContour paternal = BranchContour.from(result.snapshot, paternalIds);
        BranchContour maternal = BranchContour.from(result.snapshot, maternalIds);
        assertTrue(paternal.right() + TreeLayoutEngine.GRID * 5f <= maternal.left());
        assertTrue(result.snapshot.positionOf("paternalGrandUncleChild").x
                + TreeLayoutEngine.CARD_W + TreeLayoutEngine.GRID * 2f
            <= result.snapshot.positionOf("father").x);
    }

    private static TreeState multipleUnionFamily() {
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

    private static TreeState symmetricTree() {
        TreeState state = new TreeState();
        person(state, "partner", 11560f, 7920f);
        person(state, "root", 11880f, 7920f);
        person(state, "leftParent1", 11000f, 7440f);
        person(state, "leftParent2", 11320f, 7440f);
        person(state, "rightParent1", 12120f, 7440f);
        person(state, "rightParent2", 12440f, 7440f);
        person(state, "leftGrandparent1", 11160f, 6960f);
        person(state, "leftGrandparent2", 11480f, 6960f);
        person(state, "rightGrandparent1", 11960f, 6960f);
        person(state, "rightGrandparent2", 12280f, 6960f);
        link(state, "partner", "partner", "root");
        link(state, "partner", "leftParent1", "leftParent2");
        link(state, "parent", "leftParent1", "partner");
        link(state, "parent", "leftParent2", "partner");
        link(state, "partner", "rightParent1", "rightParent2");
        link(state, "parent", "rightParent1", "root");
        link(state, "parent", "rightParent2", "root");
        link(state, "partner", "leftGrandparent1", "leftGrandparent2");
        link(state, "parent", "leftGrandparent1", "leftParent2");
        link(state, "parent", "leftGrandparent2", "leftParent2");
        link(state, "partner", "rightGrandparent1", "rightGrandparent2");
        link(state, "parent", "rightGrandparent1", "rightParent1");
        link(state, "parent", "rightGrandparent2", "rightParent1");
        state.rootId = "root";
        return state;
    }

    private static TreeState collidingSideFamily() {
        TreeState state = new TreeState();
        person(state, "root", 11880f, 7920f);
        person(state, "rootPartner", 12200f, 7920f);
        person(state, "mainParent", 11880f, 7440f);
        person(state, "side", 11880f, 7920f);
        person(state, "sidePartner", 11560f, 7920f);
        person(state, "sideBaby", 11720f, 8400f);
        link(state, "partner", "root", "rootPartner");
        link(state, "parent", "mainParent", "root");
        link(state, "sibling", "root", "side");
        link(state, "partner", "side", "sidePartner");
        link(state, "parent", "side", "sideBaby");
        link(state, "parent", "sidePartner", "sideBaby");
        state.rootId = "root";
        return state;
    }

    private static TreeState siblingsWithWideMiddleBranch() {
        TreeState state = new TreeState();
        person(state, "parent1", 1440f, 1000f);
        person(state, "parent2", 1760f, 1000f);
        person(state, "leftSibling", 1000f, 1480f);
        person(state, "wideSibling", 1320f, 1480f);
        person(state, "widePartner", 1640f, 1480f);
        person(state, "rightSibling", 2200f, 1480f);
        person(state, "wideChild1", 1320f, 1960f);
        person(state, "wideChild2", 1640f, 1960f);
        link(state, "partner", "parent1", "parent2");
        link(state, "parent", "parent1", "leftSibling");
        link(state, "parent", "parent2", "leftSibling");
        link(state, "parent", "parent1", "wideSibling");
        link(state, "parent", "parent2", "wideSibling");
        link(state, "parent", "parent1", "rightSibling");
        link(state, "parent", "parent2", "rightSibling");
        link(state, "partner", "wideSibling", "widePartner");
        link(state, "parent", "wideSibling", "wideChild1");
        link(state, "parent", "widePartner", "wideChild1");
        link(state, "parent", "wideSibling", "wideChild2");
        link(state, "parent", "widePartner", "wideChild2");
        state.rootId = "leftSibling";
        return state;
    }

    private static TreeState collidingSideFamilyWithDistantAncestry() {
        TreeState state = collidingSideFamily();
        person(state, "remoteParent1", 15000f, 7440f);
        person(state, "remoteParent2", 15320f, 7440f);
        link(state, "partner", "remoteParent1", "remoteParent2");
        link(state, "parent", "remoteParent1", "rootPartner");
        link(state, "parent", "remoteParent2", "rootPartner");
        return state;
    }

    private static TreeState offCenterParentsWithWideLowerGeneration() {
        TreeState state = new TreeState();
        person(state, "root", 11880f, 7920f);
        person(state, "newParent1", 10000f, 7440f);
        person(state, "newParent2", 10320f, 7440f);
        person(state, "farChild1", 8000f, 8400f);
        person(state, "farChild2", 15000f, 8400f);
        link(state, "partner", "newParent1", "newParent2");
        link(state, "parent", "newParent1", "root");
        link(state, "parent", "newParent2", "root");
        link(state, "parent", "root", "farChild1");
        link(state, "parent", "root", "farChild2");
        state.rootId = "root";
        return state;
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

    private static void person(TreeState state, String id, float x, float y) {
        Person person = new Person(id);
        person.name = "Одинаковая структура";
        person.x = x;
        person.y = y;
        state.people.put(id, person);
    }

    private static void set(TreeState state, String id, float x, float y) {
        Person person = state.people.get(id);
        person.x = x;
        person.y = y;
    }

    private static void link(TreeState state, String type, String from, String to) {
        state.links.add(new Relation(type + "_" + from + "_" + to, type, from, to));
    }
}
