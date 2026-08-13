package ru.drshapaya.androidft2;

import java.util.Collection;

/**
 * Read-only entry point for comparing the current layout with the solver proposal. Nothing is
 * applied to the live tree until the diagnostic solver has enough regression coverage.
 */
final class LayoutSolverDiagnostics {
    private LayoutSolverDiagnostics() {}

    static Report analyze(TreeState state) {
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        LayoutConstraints constraints = new LayoutConstraints();
        LayoutScorer scorer = new LayoutScorer(constraints);
        LayoutWeights weights = LayoutWeights.defaults();
        LayoutScorer.Score currentScore = scorer.score(graph, current, current, weights);
        BeamLayoutSolver solver = new BeamLayoutSolver(
            scorer,
            SmartLayoutSolver.defaultGenerators(constraints),
            16,
            4);
        BeamLayoutSolver.Result proposal = solver.solve(graph, current, current, weights);
        return new Report(currentScore, proposal);
    }

    static LocalBeamLayoutSolver.Result analyzeAfterAddition(
        TreeState state,
        Collection<String> addedIds,
        String anchorId
    ) {
        return SmartLayoutSolver.proposeAfterAddition(state, addedIds, anchorId);
    }

    static final class Report {
        final LayoutScorer.Score currentScore;
        final BeamLayoutSolver.Result proposal;

        Report(LayoutScorer.Score currentScore, BeamLayoutSolver.Result proposal) {
            this.currentScore = currentScore;
            this.proposal = proposal;
        }

        boolean improvesCurrent() {
            return proposal != null
                && proposal.score.total < currentScore.total;
        }
    }
}
