package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Small deterministic beam search. It remains diagnostic until candidate coverage is sufficient. */
final class BeamLayoutSolver {
    private static final int MAX_OPERATIONS_PER_STATE = 16;

    private final LayoutScorer scorer;
    private final List<LayoutCandidateGenerator> generators;
    private final int beamWidth;
    private final int maxDepth;

    BeamLayoutSolver(
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
        LayoutWeights weights
    ) {
        Candidate start = candidate(graph, initial, baseline, weights, new ArrayList<>());
        List<Candidate> beam = new ArrayList<>();
        beam.add(start);
        Candidate best = start;
        int explored = 1;

        for (int depth = 0; depth < maxDepth; depth++) {
            List<Candidate> expanded = new ArrayList<>(beam);
            Set<String> fingerprints = new HashSet<>();
            boolean generatedAny = false;
            for (Candidate candidate : beam) fingerprints.add(fingerprint(candidate.snapshot));
            for (Candidate current : beam) {
                List<LayoutOperation> operations = new ArrayList<>();
                for (LayoutCandidateGenerator generator : generators) {
                    operations.addAll(generator.generate(graph, current.snapshot));
                }
                operations.sort(Comparator
                    .comparingInt(BeamLayoutSolver::operationPriority)
                    .thenComparingInt(operation -> operation.affectedIds().size())
                    .thenComparing(LayoutOperation::reason)
                    .thenComparing(operation -> new java.util.TreeSet<>(
                        operation.affectedIds()).toString()));
                int operationLimit = Math.min(MAX_OPERATIONS_PER_STATE, operations.size());
                for (int operationIndex = 0;
                    operationIndex < operationLimit;
                    operationIndex++) {
                        LayoutOperation operation = operations.get(operationIndex);
                        LayoutSnapshot next = operation.apply(current.snapshot);
                        String fingerprint = fingerprint(next);
                        if (!fingerprints.add(fingerprint)) continue;
                        generatedAny = true;
                        List<LayoutOperation> path = new ArrayList<>(current.operations);
                        path.add(operation);
                        Candidate generated = candidate(graph, next, baseline, weights, path);
                        explored++;
                        if (generated.score.total < best.score.total) best = generated;
                        expanded.add(generated);
                }
            }
            expanded.sort(Comparator
                .comparingDouble((Candidate value) -> value.score.total)
                .thenComparing(value -> fingerprint(value.snapshot)));
            beam = new ArrayList<>(expanded.subList(0, Math.min(beamWidth, expanded.size())));
            // An invalid intermediate state may need another operation before its score becomes
            // finite, so lack of immediate improvement is not a valid stopping condition.
            if (!generatedAny) break;
        }
        return new Result(best.snapshot, best.score, best.operations, explored);
    }

    private static int operationPriority(LayoutOperation operation) {
        if (operation == null) return Integer.MAX_VALUE;
        if (operation.reason().startsWith("multi-union-family:")) return -2;
        if (operation.reason().startsWith("collision-")) return -1;
        return 0;
    }

    private Candidate candidate(
        FamilyLayoutGraph graph,
        LayoutSnapshot snapshot,
        LayoutSnapshot baseline,
        LayoutWeights weights,
        List<LayoutOperation> operations
    ) {
        return new Candidate(
            snapshot,
            scorer.score(graph, snapshot, baseline, weights),
            operations);
    }

    private static String fingerprint(LayoutSnapshot snapshot) {
        StringBuilder value = new StringBuilder();
        for (String id : new java.util.TreeSet<>(snapshot.positions.keySet())) {
            LayoutSnapshot.Position position = snapshot.positionOf(id);
            value.append(id).append('@').append(Math.round(position.x)).append(',')
                .append(Math.round(position.y)).append(';');
        }
        return value.toString();
    }

    static final class Result {
        final LayoutSnapshot snapshot;
        final LayoutScorer.Score score;
        final List<LayoutOperation> operations;
        final int exploredCandidates;

        Result(
            LayoutSnapshot snapshot,
            LayoutScorer.Score score,
            List<LayoutOperation> operations,
            int exploredCandidates
        ) {
            this.snapshot = snapshot;
            this.score = score;
            this.operations = java.util.Collections.unmodifiableList(
                new ArrayList<>(operations));
            this.exploredCandidates = exploredCandidates;
        }
    }

    private static final class Candidate {
        final LayoutSnapshot snapshot;
        final LayoutScorer.Score score;
        final List<LayoutOperation> operations;

        Candidate(
            LayoutSnapshot snapshot,
            LayoutScorer.Score score,
            List<LayoutOperation> operations
        ) {
            this.snapshot = snapshot;
            this.score = score;
            this.operations = new ArrayList<>(operations);
        }
    }
}
