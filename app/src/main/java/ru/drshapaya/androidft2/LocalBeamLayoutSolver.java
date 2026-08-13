package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Expands the movable scope only when the current scope cannot produce a solution. */
final class LocalBeamLayoutSolver {
    private static final double EPSILON = 0.01d;

    private final LayoutScorer scorer;
    private final List<LayoutCandidateGenerator> generators;
    private final int beamWidth;
    private final int maxDepth;

    LocalBeamLayoutSolver(
        LayoutScorer scorer,
        Collection<? extends LayoutCandidateGenerator> generators,
        int beamWidth,
        int maxDepth
    ) {
        this.scorer = scorer == null
            ? new LayoutScorer(new LayoutConstraints())
            : scorer;
        this.generators = new ArrayList<>(generators);
        this.beamWidth = Math.max(1, beamWidth);
        this.maxDepth = Math.max(1, maxDepth);
    }

    Result solve(
        FamilyLayoutGraph graph,
        LayoutSnapshot initial,
        LayoutSnapshot baseline,
        LayoutWeights weights,
        LayoutImpactRegion initialRegion
    ) {
        LayoutImpactRegion region = initialRegion;
        BeamLayoutSolver.Result proposal = null;
        LayoutScorer.Score initialScore = scorer.score(graph, initial, baseline, weights);
        boolean initialValid = Double.isFinite(initialScore.total);
        BeamLayoutSolver.Result bestImprovement = null;
        LayoutImpactRegion bestRegion = null;
        PrimaryDefect primaryDefect = primaryDefect(graph, initialRegion, initialScore);
        int attempts = 0;
        while (region != null) {
            attempts++;
            List<LayoutCandidateGenerator> scoped = new ArrayList<>();
            for (LayoutCandidateGenerator generator : generators) {
                scoped.add(new ScopedLayoutCandidateGenerator(generator, region));
            }
            proposal = new BeamLayoutSolver(scorer, scoped, beamWidth, maxDepth)
                .solve(graph, initial, baseline, weights);
            boolean validRepair = !initialValid && Double.isFinite(proposal.score.total);
            boolean improvedLayout = initialValid
                && !proposal.operations.isEmpty()
                && proposal.score.total < initialScore.total;
            if (validRepair) {
                return new Result(proposal, region, attempts, true);
            }
            if (improvedLayout
                && (bestImprovement == null
                    || proposal.score.total < bestImprovement.score.total)) {
                bestImprovement = proposal;
                bestRegion = region;
            }
            if (improvedLayout
                && primaryDefectResolved(primaryDefect, initialScore, proposal.score)) {
                return new Result(proposal, region, attempts, true);
            }
            if (!region.canExpand()) break;
            region = region.expand(graph, initial);
        }
        if (bestImprovement != null) {
            return new Result(bestImprovement, bestRegion, attempts, true);
        }
        return new Result(proposal, region, attempts, initialValid);
    }

    private static PrimaryDefect primaryDefect(
        FamilyLayoutGraph graph,
        LayoutImpactRegion region,
        LayoutScorer.Score initial
    ) {
        if (graph != null && region != null) {
            boolean addedExternalPartner = false;
            boolean addedParent = false;
            boolean addedChild = false;
            boolean affectsMultipleUnions = false;
            for (String id : region.addedIds) {
                for (String partnerId : graph.partnersByPerson.getOrDefault(
                    id,
                    java.util.Collections.emptySet())) {
                    if (!region.addedIds.contains(partnerId)) addedExternalPartner = true;
                }
                if (!graph.childrenByParent.getOrDefault(
                    id,
                    java.util.Collections.emptySet()).isEmpty()) addedParent = true;
                if (!graph.parentsByChild.getOrDefault(
                    id,
                    java.util.Collections.emptySet()).isEmpty()) addedChild = true;
                for (String parentId : graph.parentsByChild.getOrDefault(
                    id,
                    java.util.Collections.emptySet())) {
                    if (graph.partnerUnitsOf(parentId).size() > 1) {
                        affectsMultipleUnions = true;
                    }
                }
            }
            if (addedExternalPartner && initial.wrongSideError > EPSILON) {
                return PrimaryDefect.WRONG_SIDE;
            }
            if (addedParent && initial.familyCenterError > EPSILON) {
                return PrimaryDefect.FAMILY_CENTER;
            }
            if (addedChild && affectsMultipleUnions
                && (initial.siblingSpacingError > EPSILON
                    || initial.familyCenterError > EPSILON)) {
                return PrimaryDefect.MULTI_UNION;
            }
            if (addedChild && initial.siblingSpacingError > EPSILON) {
                return PrimaryDefect.SIBLING_SPACING;
            }
        }
        if (initial.wrongSideError > EPSILON) return PrimaryDefect.WRONG_SIDE;
        if (initial.siblingSpacingError > EPSILON) return PrimaryDefect.SIBLING_SPACING;
        if (initial.familyCenterError > EPSILON) return PrimaryDefect.FAMILY_CENTER;
        if (initial.symmetryError > EPSILON) return PrimaryDefect.SYMMETRY;
        return PrimaryDefect.NONE;
    }

    private static boolean primaryDefectResolved(
        PrimaryDefect defect,
        LayoutScorer.Score initial,
        LayoutScorer.Score candidate
    ) {
        if (defect == PrimaryDefect.WRONG_SIDE) return candidate.wrongSideError <= EPSILON;
        if (defect == PrimaryDefect.SIBLING_SPACING) {
            return candidate.siblingSpacingError <= EPSILON;
        }
        if (defect == PrimaryDefect.FAMILY_CENTER) {
            // Other, untouched families may already have manual offsets. The generated
            // family-centering operation itself is exact, so a strict reduction is enough.
            return candidate.familyCenterError + EPSILON < initial.familyCenterError;
        }
        if (defect == PrimaryDefect.MULTI_UNION) {
            return candidate.siblingSpacingError <= EPSILON
                && candidate.familyCenterError <= EPSILON;
        }
        if (defect == PrimaryDefect.SYMMETRY) return candidate.symmetryError <= EPSILON;
        return false;
    }

    private enum PrimaryDefect {
        NONE,
        WRONG_SIDE,
        SIBLING_SPACING,
        FAMILY_CENTER,
        MULTI_UNION,
        SYMMETRY
    }

    static final class Result {
        final BeamLayoutSolver.Result proposal;
        final LayoutImpactRegion usedRegion;
        final int attempts;
        final boolean solved;

        Result(
            BeamLayoutSolver.Result proposal,
            LayoutImpactRegion usedRegion,
            int attempts,
            boolean solved
        ) {
            this.proposal = proposal;
            this.usedRegion = usedRegion;
            this.attempts = attempts;
            this.solved = solved;
        }
    }
}
