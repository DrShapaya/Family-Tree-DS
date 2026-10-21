package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Central hard-constraint validator shared by every future candidate generator. */
final class LayoutConstraints {
    private static final float EPSILON = 0.5f;

    ValidationResult validate(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        List<Violation> violations = new ArrayList<>();
        if (graph == null || snapshot == null) {
            violations.add(new Violation("missing-input", "", "", "Нет графа или координат"));
            return new ValidationResult(violations);
        }
        for (FamilyLayoutGraph.GenerationConflict conflict : graph.generationConflicts) {
            violations.add(new Violation(
                "generation-conflict",
                conflict.sourceId,
                conflict.targetId,
                "Противоречивые родственные связи задают разные поколения"));
        }
        for (String id : graph.people.keySet()) {
            LayoutSnapshot.Position position = snapshot.positionOf(id);
            if (position == null || !position.isFinite()) {
                violations.add(new Violation("invalid-position", id, "", "Некорректная координата"));
                continue;
            }
            if (position.x < 0f || position.y < 0f) {
                violations.add(new Violation("outside-workspace", id, "", "Отрицательная координата"));
            }
            // The workspace may grow after a layout operation. Only the absolute
            // supported canvas limit is a hard constraint here; the current saved
            // dimensions are not, otherwise a candidate that legitimately expands
            // the canvas would be rejected before expandWorkspaceToFit() runs.
            if (position.x + TreeLayoutEngine.CARD_W
                    > TreeLayoutEngine.MAX_SURFACE_SIZE + EPSILON
                || position.y + TreeLayoutEngine.CARD_H
                    > TreeLayoutEngine.MAX_SURFACE_SIZE + EPSILON) {
                violations.add(new Violation(
                    "outside-workspace",
                    id,
                    "",
                    "Карточка выходит за правую или нижнюю границу"));
            }
            if (!onGrid(position.x) || !onGrid(position.y)) {
                violations.add(new Violation("off-grid", id, "", "Координата не привязана к сетке"));
            }
        }

        List<String> ids = new ArrayList<>(graph.people.keySet());
        ids.sort((firstId, secondId) -> {
            LayoutSnapshot.Position first = snapshot.positionOf(firstId);
            LayoutSnapshot.Position second = snapshot.positionOf(secondId);
            float firstX = first == null ? Float.MAX_VALUE : first.x;
            float secondX = second == null ? Float.MAX_VALUE : second.x;
            int byX = Float.compare(firstX, secondX);
            return byX != 0 ? byX : firstId.compareTo(secondId);
        });
        for (int firstIndex = 0; firstIndex < ids.size(); firstIndex++) {
            String firstId = ids.get(firstIndex);
            LayoutSnapshot.Position first = snapshot.positionOf(firstId);
            if (first == null || !first.isFinite()) continue;
            for (int secondIndex = firstIndex + 1; secondIndex < ids.size(); secondIndex++) {
                String secondId = ids.get(secondIndex);
                LayoutSnapshot.Position second = snapshot.positionOf(secondId);
                if (second == null || !second.isFinite()) continue;
                if (second.x >= first.x + TreeLayoutEngine.CARD_W + TreeLayoutEngine.GRID) {
                    break;
                }
                if (cardsOverlap(first, second)) {
                    violations.add(new Violation(
                        "card-overlap",
                        firstId,
                        secondId,
                        "Карточки пересекаются"));
                } else if (verticallyOverlap(first, second)
                    && horizontalGap(first, second) < TreeLayoutEngine.GRID - EPSILON) {
                    violations.add(new Violation(
                        "card-spacing",
                        firstId,
                        secondId,
                        "Между карточками нет минимального интервала"));
                }
            }
        }

        for (Map.Entry<String, java.util.Set<String>> entry : graph.partnersByPerson.entrySet()) {
            LayoutSnapshot.Position first = snapshot.positionOf(entry.getKey());
            if (first == null) continue;
            for (String partnerId : entry.getValue()) {
                if (entry.getKey().compareTo(partnerId) >= 0) continue;
                LayoutSnapshot.Position partner = snapshot.positionOf(partnerId);
                float expected = graph.generationStep(entry.getKey(), partnerId, 0) * TreeLayoutEngine.LEVEL_GAP;
                if (partner != null && Math.abs(partner.y - first.y - expected) > EPSILON) {
                    violations.add(new Violation(
                        "partner-generation",
                        entry.getKey(),
                        partnerId,
                        "Партнёры находятся на разных уровнях"));
                }
            }
        }

        for (Map.Entry<String, java.util.Set<String>> entry : graph.childrenByParent.entrySet()) {
            LayoutSnapshot.Position parent = snapshot.positionOf(entry.getKey());
            if (parent == null) continue;
            for (String childId : entry.getValue()) {
                LayoutSnapshot.Position child = snapshot.positionOf(childId);
                float expected = graph.generationStep(entry.getKey(), childId, 1) * TreeLayoutEngine.LEVEL_GAP;
                if (child != null
                    && Math.abs(child.y - parent.y - expected) > EPSILON) {
                    violations.add(new Violation(
                        "parent-generation",
                        entry.getKey(),
                        childId,
                        "Положение ребёнка не соответствует родительским связям"));
                }
            }
        }

        for (Map.Entry<String, java.util.Set<String>> entry : graph.siblingsByPerson.entrySet()) {
            LayoutSnapshot.Position first = snapshot.positionOf(entry.getKey());
            if (first == null) continue;
            for (String siblingId : entry.getValue()) {
                if (entry.getKey().compareTo(siblingId) >= 0) continue;
                LayoutSnapshot.Position sibling = snapshot.positionOf(siblingId);
                float expected = graph.generationStep(entry.getKey(), siblingId, 0) * TreeLayoutEngine.LEVEL_GAP;
                if (sibling != null && Math.abs(sibling.y - first.y - expected) > EPSILON) {
                    violations.add(new Violation(
                        "sibling-generation",
                        entry.getKey(),
                        siblingId,
                        "Братья или сёстры находятся на разных уровнях"));
                }
            }
        }
        return new ValidationResult(violations);
    }

    private static boolean onGrid(float value) {
        return Math.abs(value - Math.round(value / TreeLayoutEngine.GRID) * TreeLayoutEngine.GRID)
            <= EPSILON;
    }

    private static boolean cardsOverlap(
        LayoutSnapshot.Position first,
        LayoutSnapshot.Position second
    ) {
        return first.x < second.x + TreeLayoutEngine.CARD_W
            && second.x < first.x + TreeLayoutEngine.CARD_W
            && first.y < second.y + TreeLayoutEngine.CARD_H
            && second.y < first.y + TreeLayoutEngine.CARD_H;
    }

    private static boolean verticallyOverlap(
        LayoutSnapshot.Position first,
        LayoutSnapshot.Position second
    ) {
        return first.y < second.y + TreeLayoutEngine.CARD_H
            && second.y < first.y + TreeLayoutEngine.CARD_H;
    }

    private static float horizontalGap(
        LayoutSnapshot.Position first,
        LayoutSnapshot.Position second
    ) {
        if (first.x <= second.x) {
            return second.x - (first.x + TreeLayoutEngine.CARD_W);
        }
        return first.x - (second.x + TreeLayoutEngine.CARD_W);
    }

    static final class ValidationResult {
        final List<Violation> violations;

        ValidationResult(List<Violation> violations) {
            this.violations = Collections.unmodifiableList(new ArrayList<>(violations));
        }

        boolean isValid() {
            return violations.isEmpty();
        }
    }

    static final class Violation {
        final String code;
        final String firstId;
        final String secondId;
        final String detail;

        Violation(String code, String firstId, String secondId, String detail) {
            this.code = code;
            this.firstId = firstId;
            this.secondId = secondId;
            this.detail = detail;
        }
    }
}
