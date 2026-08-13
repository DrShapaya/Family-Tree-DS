package ru.drshapaya.androidft2;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Safe production boundary around the incremental solver. */
final class SmartLayoutSolver {
    private static final float EPSILON = 0.5f;
    private static final int MAX_PRODUCTION_EXPANSION_LEVEL = 4;
    private static final int LOCAL_BEAM_WIDTH = 16;
    private static final int LOCAL_MAX_DEPTH = 4;
    private static final int REBUILD_BEAM_WIDTH = 6;
    private static final int REBUILD_MAX_DEPTH = 2;
    private static final int COMPACTION_BEAM_WIDTH = 8;
    private static final int COMPACTION_MAX_DEPTH = 3;

    private SmartLayoutSolver() {}

    static ApplyResult improveAfterAddition(
        TreeState state,
        Collection<String> addedIds,
        String anchorId
    ) {
        ApplyResult last = null;
        // The legacy placement may first separate a half-sibling row and only then
        // expose the remaining union-centering defect. Two bounded passes converge
        // that single local action without widening its impact region.
        for (int pass = 0; pass < 2; pass++) {
            ApplyResult current = improveSinglePass(
                state,
                addedIds,
                anchorId,
                LOCAL_BEAM_WIDTH,
                LOCAL_MAX_DEPTH);
            if (!current.applied) return last == null ? current : last;
            last = current;
        }
        return last == null ? ApplyResult.rejected("no-improvement") : last;
    }

    /**
     * Fast profile used while the manual Arrange command replays the whole tree.
     * The legacy step already creates a valid starting point, so two solver moves
     * are enough to center or separate the family without multiplying the total
     * rebuild time by the number of people.
     */
    static ApplyResult improveRebuildStep(
        TreeState state,
        Collection<String> addedIds,
        String anchorId
    ) {
        return improveSinglePass(
            state,
            addedIds,
            anchorId,
            REBUILD_BEAM_WIDTH,
            REBUILD_MAX_DEPTH);
    }

    /**
     * Final global pass for a fully rebuilt tree. It may close excessive gaps, but
     * never moves the root, changes sibling order or accepts a wider result.
     */
    static ApplyResult compactRebuiltTree(TreeState state) {
        if (state == null || state.people.size() < 2) {
            return ApplyResult.rejected("nothing-to-compact");
        }
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot before = LayoutSnapshot.capture(state);
        LayoutConstraints constraints = new LayoutConstraints();
        LayoutScorer scorer = new LayoutScorer(constraints);
        LayoutWeights weights = LayoutWeights.rebuildCompaction();
        LayoutScorer.Score beforeScore = scorer.score(graph, before, null, weights);
        if (!Double.isFinite(beforeScore.total)) {
            return ApplyResult.rejected("invalid-start");
        }

        List<LayoutCandidateGenerator> generators = new ArrayList<>();
        Collection<String> locked = graph.rootId.isEmpty()
            ? Collections.emptySet()
            : Collections.singleton(graph.rootId);
        for (LayoutCandidateGenerator generator : compactionGenerators(constraints)) {
            generators.add(new LockedLayoutCandidateGenerator(generator, locked));
        }
        BeamLayoutSolver.Result proposal = new BeamLayoutSolver(
            scorer,
            generators,
            COMPACTION_BEAM_WIDTH,
            COMPACTION_MAX_DEPTH).solve(graph, before, null, weights);
        if (proposal.operations.isEmpty()) return ApplyResult.rejected("no-compaction");
        if (!Double.isFinite(proposal.score.total)
            || proposal.score.total >= beforeScore.total) {
            return ApplyResult.rejected("score-not-better");
        }
        boolean smaller = proposal.score.width < beforeScore.width - EPSILON;
        boolean repairedFamilyStructure =
            proposal.score.familyCenterError + EPSILON < beforeScore.familyCenterError
                || proposal.score.siblingSpacingError + EPSILON
                    < beforeScore.siblingSpacingError
                || proposal.score.wrongSideError + EPSILON < beforeScore.wrongSideError;
        if (!smaller && !repairedFamilyStructure) {
            return ApplyResult.rejected("width-not-smaller");
        }
        if (!constraints.validate(graph, proposal.snapshot).isValid()) {
            return ApplyResult.rejected("invalid-result");
        }
        if (!samePosition(graph.rootId, before, proposal.snapshot)) {
            return ApplyResult.rejected("root-moved");
        }
        if (!preservesSiblingOrder(graph, before, proposal.snapshot)) {
            return ApplyResult.rejected("sibling-order-changed");
        }
        proposal.snapshot.applyTo(state);
        return smaller ? ApplyResult.compacted() : ApplyResult.normalized();
    }

    private static ApplyResult improveSinglePass(
        TreeState state,
        Collection<String> addedIds,
        String anchorId,
        int beamWidth,
        int maxDepth
    ) {
        if (state == null || state.people.isEmpty()) return ApplyResult.rejected("empty-tree");
        LayoutSnapshot before = LayoutSnapshot.capture(state);
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutConstraints constraints = new LayoutConstraints();
        LayoutScorer scorer = new LayoutScorer(constraints);
        LayoutScorer.Score beforeScore = scorer.score(
            graph,
            before,
            before,
            LayoutWeights.defaults());
        LocalBeamLayoutSolver.Result local = proposeAfterAddition(
            state,
            addedIds,
            anchorId,
            constraints,
            scorer,
            before,
            beamWidth,
            maxDepth);
        if (local == null || local.proposal == null || !local.solved) {
            return ApplyResult.rejected("no-solution", local);
        }
        if (local.proposal.operations.isEmpty()) {
            return ApplyResult.rejected("no-improvement", local);
        }
        if (local.usedRegion == null
            || local.usedRegion.expansionLevel > MAX_PRODUCTION_EXPANSION_LEVEL) {
            return ApplyResult.rejected("scope-too-wide", local);
        }
        if (!Double.isFinite(local.proposal.score.total)
            || (Double.isFinite(beforeScore.total)
                && local.proposal.score.total >= beforeScore.total)) {
            return ApplyResult.rejected("score-not-better", local);
        }
        if (!constraints.validate(graph, local.proposal.snapshot).isValid()) {
            return ApplyResult.rejected("invalid-result", local);
        }
        for (LayoutOperation operation : local.proposal.operations) {
            if (!local.usedRegion.allows(operation)) {
                return ApplyResult.rejected("operation-outside-scope", local);
            }
        }
        if (!samePosition(graph.rootId, before, local.proposal.snapshot)) {
            return ApplyResult.rejected("root-moved", local);
        }
        for (Map.Entry<String, LayoutSnapshot.Position> entry : before.positions.entrySet()) {
            if (local.usedRegion.activeIds.contains(entry.getKey())) continue;
            if (!samePosition(entry.getKey(), before, local.proposal.snapshot)) {
                return ApplyResult.rejected("distant-person-moved", local);
            }
        }
        local.proposal.snapshot.applyTo(state);
        return ApplyResult.applied(local);
    }

    static LocalBeamLayoutSolver.Result proposeAfterAddition(
        TreeState state,
        Collection<String> addedIds,
        String anchorId
    ) {
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        LayoutConstraints constraints = new LayoutConstraints();
        return proposeAfterAddition(
            state,
            addedIds,
            anchorId,
            constraints,
            new LayoutScorer(constraints),
            current,
            LOCAL_BEAM_WIDTH,
            LOCAL_MAX_DEPTH);
    }

    private static LocalBeamLayoutSolver.Result proposeAfterAddition(
        TreeState state,
        Collection<String> addedIds,
        String anchorId,
        LayoutConstraints constraints,
        LayoutScorer scorer,
        LayoutSnapshot current,
        int beamWidth,
        int maxDepth
    ) {
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutImpactRegion region = LayoutImpactRegion.initial(graph, addedIds, anchorId);
        return new LocalBeamLayoutSolver(
            scorer,
            defaultGenerators(constraints),
            beamWidth,
            maxDepth).solve(graph, current, current, LayoutWeights.defaults(), region);
    }

    static List<LayoutCandidateGenerator> defaultGenerators(LayoutConstraints constraints) {
        return Arrays.asList(
            new CollisionBranchCandidateGenerator(constraints, TreeLayoutEngine.GRID),
            new MultiUnionFamilyCandidateGenerator(TreeLayoutEngine.GRID * 5f),
            new FamilyCenterCandidateGenerator(),
            new SiblingSpacingCandidateGenerator(),
            new AncestrySideCandidateGenerator(TreeLayoutEngine.GRID * 5f),
            new SymmetricBranchCandidateGenerator(TreeLayoutEngine.GRID * 5f),
            new SiblingBranchSwapCandidateGenerator(TreeLayoutEngine.GRID));
    }

    private static List<LayoutCandidateGenerator> compactionGenerators(
        LayoutConstraints constraints
    ) {
        return Arrays.asList(
            new CollisionBranchCandidateGenerator(constraints, TreeLayoutEngine.GRID),
            new MultiUnionFamilyCandidateGenerator(TreeLayoutEngine.GRID * 5f),
            new FamilyCenterCandidateGenerator(),
            new SiblingSpacingCandidateGenerator(),
            new AncestrySideCandidateGenerator(TreeLayoutEngine.GRID * 5f),
            new SymmetricBranchCandidateGenerator(TreeLayoutEngine.GRID * 5f));
    }

    private static boolean preservesSiblingOrder(
        FamilyLayoutGraph graph,
        LayoutSnapshot before,
        LayoutSnapshot after
    ) {
        for (FamilyLayoutGraph.SiblingGroup group : graph.siblingGroups) {
            List<String> ids = new ArrayList<>(group.people);
            for (int first = 0; first < ids.size(); first++) {
                for (int second = first + 1; second < ids.size(); second++) {
                    LayoutSnapshot.Position beforeFirst = before.positionOf(ids.get(first));
                    LayoutSnapshot.Position beforeSecond = before.positionOf(ids.get(second));
                    LayoutSnapshot.Position afterFirst = after.positionOf(ids.get(first));
                    LayoutSnapshot.Position afterSecond = after.positionOf(ids.get(second));
                    if (beforeFirst == null || beforeSecond == null
                        || afterFirst == null || afterSecond == null) continue;
                    float beforeDelta = beforeFirst.x - beforeSecond.x;
                    float afterDelta = afterFirst.x - afterSecond.x;
                    if (Math.abs(beforeDelta) <= EPSILON || Math.abs(afterDelta) <= EPSILON) continue;
                    if (Math.signum(beforeDelta) != Math.signum(afterDelta)) return false;
                }
            }
        }
        return true;
    }

    private static boolean samePosition(
        String id,
        LayoutSnapshot first,
        LayoutSnapshot second
    ) {
        LayoutSnapshot.Position a = first.positionOf(id);
        LayoutSnapshot.Position b = second.positionOf(id);
        if (a == null || b == null) return a == b;
        return Math.abs(a.x - b.x) <= EPSILON && Math.abs(a.y - b.y) <= EPSILON;
    }

    static final class ApplyResult {
        final boolean applied;
        final String reason;
        final LocalBeamLayoutSolver.Result localResult;

        private ApplyResult(
            boolean applied,
            String reason,
            LocalBeamLayoutSolver.Result localResult
        ) {
            this.applied = applied;
            this.reason = reason;
            this.localResult = localResult;
        }

        static ApplyResult applied(LocalBeamLayoutSolver.Result result) {
            return new ApplyResult(true, "applied", result);
        }

        static ApplyResult rejected(String reason) {
            return rejected(reason, null);
        }

        static ApplyResult rejected(String reason, LocalBeamLayoutSolver.Result result) {
            return new ApplyResult(false, reason, result);
        }

        static ApplyResult compacted() {
            return new ApplyResult(true, "compacted", null);
        }

        static ApplyResult normalized() {
            return new ApplyResult(true, "normalized", null);
        }
    }
}
