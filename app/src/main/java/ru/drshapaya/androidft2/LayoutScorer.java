package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Scores valid candidates; hard violations always produce positive infinity. */
final class LayoutScorer {
    private final LayoutConstraints constraints;

    LayoutScorer(LayoutConstraints constraints) {
        this.constraints = constraints == null ? new LayoutConstraints() : constraints;
    }

    Score score(
        FamilyLayoutGraph graph,
        LayoutSnapshot candidate,
        LayoutSnapshot baseline,
        LayoutWeights weights
    ) {
        LayoutWeights actualWeights = weights == null ? LayoutWeights.defaults() : weights;
        LayoutConstraints.ValidationResult validation = constraints.validate(graph, candidate);
        if (!validation.isValid()) return Score.invalid(validation.violations.size());

        LayoutSnapshot.Bounds bounds = candidate.bounds();
        double movement = baseline == null ? 0d : candidate.totalManhattanMovementFrom(baseline);
        double mainMovement = movementOf(graph.rootId, candidate, baseline);
        double crossings = parentLineCrossings(graph, candidate);
        double centerError = familyCenterError(graph, candidate);
        double symmetryError = symmetryError(graph, candidate);
        double siblingSpacingError = siblingSpacingError(graph, candidate);
        double wrongSideError = wrongSideError(graph, candidate);
        double connectionLength = connectionLength(graph, candidate);
        double emptySpace = Math.max(0d, bounds.width() * bounds.height()
            - graph.people.size() * TreeLayoutEngine.CARD_W * TreeLayoutEngine.CARD_H);
        double total =
            crossings * actualWeights.lineCrossings
                + movement * actualWeights.movement
                + mainMovement * actualWeights.mainTrunkMovement
                + bounds.width() * actualWeights.width
                + bounds.height() * actualWeights.height
                + centerError * actualWeights.familyCenter
                + symmetryError * actualWeights.symmetry
                + siblingSpacingError * actualWeights.siblingSpacing
                + wrongSideError * actualWeights.wrongSide
                + emptySpace * actualWeights.emptySpace
                + connectionLength * actualWeights.connectionLength;
        return new Score(
            total,
            0,
            crossings,
            movement,
            centerError,
            symmetryError,
            siblingSpacingError,
            wrongSideError,
            bounds.width(),
            bounds.height());
    }

    private static double movementOf(
        String id,
        LayoutSnapshot candidate,
        LayoutSnapshot baseline
    ) {
        if (baseline == null) return 0d;
        LayoutSnapshot.Position before = baseline.positionOf(id);
        LayoutSnapshot.Position after = candidate.positionOf(id);
        if (before == null || after == null || !before.isFinite() || !after.isFinite()) return 0d;
        return Math.abs(after.x - before.x) + Math.abs(after.y - before.y);
    }

    private static double familyCenterError(
        FamilyLayoutGraph graph,
        LayoutSnapshot snapshot
    ) {
        double error = 0d;
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            double parentCenter = averageCenter(family.parents, snapshot);
            double childCenter = outerCenter(family.children, snapshot);
            if (Double.isFinite(parentCenter) && Double.isFinite(childCenter)) {
                error += Math.abs(parentCenter - childCenter);
            }
        }
        return error;
    }

    private static double symmetryError(
        FamilyLayoutGraph graph,
        LayoutSnapshot snapshot
    ) {
        double error = 0d;
        for (FamilyLayoutGraph.PartnerUnit unit : graph.partnerUnits) {
            if (unit.people.size() != 2) continue;
            List<String> members = new ArrayList<>(unit.people);
            String first = members.get(0);
            String second = members.get(1);
            if (!graph.structurallyMirrored(first, second)) continue;
            FamilyLayoutGraph.ParentFamily firstFamily = graph.parentFamilyOf(first);
            FamilyLayoutGraph.ParentFamily secondFamily = graph.parentFamilyOf(second);
            if (firstFamily == null || secondFamily == null || firstFamily == secondFamily) continue;
            double axis = averageCenter(unit.people, snapshot);
            double firstCenter = averageCenter(firstFamily.parents, snapshot);
            double secondCenter = averageCenter(secondFamily.parents, snapshot);
            if (Double.isFinite(axis)
                && Double.isFinite(firstCenter)
                && Double.isFinite(secondCenter)) {
                error += Math.abs(axis - (firstCenter + secondCenter) / 2d);
                error += Math.abs(
                    Math.abs(axis - firstCenter) - Math.abs(secondCenter - axis));
            }
        }
        return error;
    }

    private static double siblingSpacingError(
        FamilyLayoutGraph graph,
        LayoutSnapshot snapshot
    ) {
        double error = 0d;
        for (FamilyLayoutGraph.SiblingGroup group : graph.siblingGroups) {
            List<SiblingBranch> branches = siblingBranches(graph, group, snapshot);
            branches.sort(java.util.Comparator
                .comparingDouble((SiblingBranch branch) -> branch.contour.left())
                .thenComparing(branch -> branch.personId));
            for (int index = 0; index + 1 < branches.size(); index++) {
                SiblingBranch left = branches.get(index);
                SiblingBranch right = branches.get(index + 1);
                float actual = right.contour.horizontalGapFrom(left.contour);
                if (!Float.isFinite(actual)) continue;
                FamilyLayoutGraph.ParentFamily leftFamily = graph.parentFamilyOf(
                    left.personId);
                FamilyLayoutGraph.ParentFamily rightFamily = graph.parentFamilyOf(
                    right.personId);
                float desired = leftFamily != rightFamily
                    ? TreeLayoutEngine.GRID * 5f
                    : left.simple && right.simple
                        ? TreeLayoutEngine.GRID
                        : TreeLayoutEngine.GRID * 5f;
                error += Math.abs(actual - desired);
            }
        }
        return error;
    }

    private static List<SiblingBranch> siblingBranches(
        FamilyLayoutGraph graph,
        FamilyLayoutGraph.SiblingGroup group,
        LayoutSnapshot snapshot
    ) {
        List<SiblingBranch> branches = new ArrayList<>();
        for (String personId : group.people) {
            Set<String> ids = graph.descendantBranch(java.util.Collections.singleton(personId));
            branches.add(new SiblingBranch(
                personId,
                BranchContour.from(snapshot, ids),
                ids.size() == 1));
        }
        return branches;
    }

    private static double wrongSideError(
        FamilyLayoutGraph graph,
        LayoutSnapshot snapshot
    ) {
        double error = 0d;
        for (FamilyLayoutGraph.PartnerUnit unit : graph.partnerUnits) {
            if (unit.people.size() != 2) continue;
            List<String> members = new ArrayList<>(unit.people);
            members.sort(java.util.Comparator
                .comparingDouble((String id) -> {
                    LayoutSnapshot.Position position = snapshot.positionOf(id);
                    return position == null ? Double.POSITIVE_INFINITY : position.x;
                })
                .thenComparing(id -> id));
            String leftPersonId = members.get(0);
            String rightPersonId = members.get(1);
            FamilyLayoutGraph.ParentFamily leftFamily = graph.parentFamilyOf(leftPersonId);
            FamilyLayoutGraph.ParentFamily rightFamily = graph.parentFamilyOf(rightPersonId);
            if (leftFamily == null || rightFamily == null || leftFamily == rightFamily) continue;
            LayoutSnapshot.Position leftPerson = snapshot.positionOf(leftPersonId);
            LayoutSnapshot.Position rightPerson = snapshot.positionOf(rightPersonId);
            if (leftPerson == null || rightPerson == null) continue;
            BranchContour left = BranchContour.from(
                snapshot,
                graph.lineageBranch(leftFamily.parents));
            BranchContour right = BranchContour.from(
                snapshot,
                graph.lineageBranch(rightFamily.parents));
            error += AncestrySideRules.leftError(left, leftPerson);
            error += AncestrySideRules.rightError(right, rightPerson);
            error += right.requiredRightShiftFrom(left, TreeLayoutEngine.GRID * 5f);
        }
        return error;
    }

    private static double connectionLength(
        FamilyLayoutGraph graph,
        LayoutSnapshot snapshot
    ) {
        double length = 0d;
        Set<String> seenPartners = new HashSet<>();
        for (String parentId : graph.childrenByParent.keySet()) {
            for (String childId : graph.childrenByParent.get(parentId)) {
                length += centerDistance(parentId, childId, snapshot);
            }
        }
        for (String personId : graph.partnersByPerson.keySet()) {
            for (String partnerId : graph.partnersByPerson.get(personId)) {
                String key = personId.compareTo(partnerId) < 0
                    ? personId + "|" + partnerId
                    : partnerId + "|" + personId;
                if (seenPartners.add(key)) length += centerDistance(personId, partnerId, snapshot);
            }
        }
        return length;
    }

    private static double parentLineCrossings(
        FamilyLayoutGraph graph,
        LayoutSnapshot snapshot
    ) {
        List<Segment> segments = new ArrayList<>();
        for (String parentId : graph.childrenByParent.keySet()) {
            for (String childId : graph.childrenByParent.get(parentId)) {
                LayoutSnapshot.Position parent = snapshot.positionOf(parentId);
                LayoutSnapshot.Position child = snapshot.positionOf(childId);
                if (parent == null || child == null) continue;
                segments.add(new Segment(
                    parentId,
                    childId,
                    parent.x + TreeLayoutEngine.CARD_W / 2f,
                    parent.y + TreeLayoutEngine.CARD_H,
                    child.x + TreeLayoutEngine.CARD_W / 2f,
                    child.y));
            }
        }
        int crossings = 0;
        for (int first = 0; first < segments.size(); first++) {
            for (int second = first + 1; second < segments.size(); second++) {
                Segment a = segments.get(first);
                Segment b = segments.get(second);
                if (a.sharesPersonWith(b)) continue;
                if (a.intersects(b)) crossings++;
            }
        }
        return crossings;
    }

    private static double centerDistance(
        String firstId,
        String secondId,
        LayoutSnapshot snapshot
    ) {
        LayoutSnapshot.Position first = snapshot.positionOf(firstId);
        LayoutSnapshot.Position second = snapshot.positionOf(secondId);
        if (first == null || second == null) return 0d;
        return Math.abs(first.x - second.x) + Math.abs(first.y - second.y);
    }

    private static double averageCenter(Iterable<String> ids, LayoutSnapshot snapshot) {
        double sum = 0d;
        int count = 0;
        for (String id : ids) {
            LayoutSnapshot.Position position = snapshot.positionOf(id);
            if (position == null || !position.isFinite()) continue;
            sum += position.x + TreeLayoutEngine.CARD_W / 2d;
            count++;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private static double outerCenter(Iterable<String> ids, LayoutSnapshot snapshot) {
        double left = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY;
        for (String id : ids) {
            LayoutSnapshot.Position position = snapshot.positionOf(id);
            if (position == null || !position.isFinite()) continue;
            left = Math.min(left, position.x);
            right = Math.max(right, position.x + TreeLayoutEngine.CARD_W);
        }
        return Double.isFinite(left) ? (left + right) / 2d : Double.NaN;
    }

    static final class Score {
        final double total;
        final int hardViolations;
        final double lineCrossings;
        final double movement;
        final double familyCenterError;
        final double symmetryError;
        final double siblingSpacingError;
        final double wrongSideError;
        final double width;
        final double height;

        Score(
            double total,
            int hardViolations,
            double lineCrossings,
            double movement,
            double familyCenterError,
            double symmetryError,
            double siblingSpacingError,
            double wrongSideError,
            double width,
            double height
        ) {
            this.total = total;
            this.hardViolations = hardViolations;
            this.lineCrossings = lineCrossings;
            this.movement = movement;
            this.familyCenterError = familyCenterError;
            this.symmetryError = symmetryError;
            this.siblingSpacingError = siblingSpacingError;
            this.wrongSideError = wrongSideError;
            this.width = width;
            this.height = height;
        }

        static Score invalid(int violations) {
            return new Score(
                Double.POSITIVE_INFINITY,
                violations,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d);
        }
    }

    private static final class SiblingBranch {
        final String personId;
        final BranchContour contour;
        final boolean simple;

        SiblingBranch(String personId, BranchContour contour, boolean simple) {
            this.personId = personId;
            this.contour = contour;
            this.simple = simple;
        }
    }

    private static final class Segment {
        final String firstId;
        final String secondId;
        final double x1;
        final double y1;
        final double x2;
        final double y2;

        Segment(String firstId, String secondId, double x1, double y1, double x2, double y2) {
            this.firstId = firstId;
            this.secondId = secondId;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        boolean sharesPersonWith(Segment other) {
            return firstId.equals(other.firstId)
                || firstId.equals(other.secondId)
                || secondId.equals(other.firstId)
                || secondId.equals(other.secondId);
        }

        boolean intersects(Segment other) {
            double d1 = direction(other.x1, other.y1, other.x2, other.y2, x1, y1);
            double d2 = direction(other.x1, other.y1, other.x2, other.y2, x2, y2);
            double d3 = direction(x1, y1, x2, y2, other.x1, other.y1);
            double d4 = direction(x1, y1, x2, y2, other.x2, other.y2);
            return ((d1 > 0d && d2 < 0d) || (d1 < 0d && d2 > 0d))
                && ((d3 > 0d && d4 < 0d) || (d3 < 0d && d4 > 0d));
        }

        private static double direction(
            double ax,
            double ay,
            double bx,
            double by,
            double cx,
            double cy
        ) {
            return (cx - ax) * (by - ay) - (cy - ay) * (bx - ax);
        }
    }
}
