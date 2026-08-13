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
        for (String id : graph.people.keySet()) {
            LayoutSnapshot.Position position = snapshot.positionOf(id);
            if (position == null || !position.isFinite()) {
                violations.add(new Violation("invalid-position", id, "", "Некорректная координата"));
                continue;
            }
            if (position.x < 0f || position.y < 0f) {
                violations.add(new Violation("outside-workspace", id, "", "Отрицательная координата"));
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
                if (second.x >= first.x + TreeLayoutEngine.CARD_W) break;
                if (cardsOverlap(first, second)) {
                    violations.add(new Violation(
                        "card-overlap",
                        firstId,
                        secondId,
                        "Карточки пересекаются"));
                }
            }
        }

        for (Map.Entry<String, java.util.Set<String>> entry : graph.partnersByPerson.entrySet()) {
            LayoutSnapshot.Position first = snapshot.positionOf(entry.getKey());
            if (first == null) continue;
            for (String partnerId : entry.getValue()) {
                if (entry.getKey().compareTo(partnerId) >= 0) continue;
                LayoutSnapshot.Position partner = snapshot.positionOf(partnerId);
                if (partner != null && Math.abs(first.y - partner.y) > EPSILON) {
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
                if (child != null
                    && child.y - parent.y < TreeLayoutEngine.LEVEL_GAP - EPSILON) {
                    violations.add(new Violation(
                        "parent-generation",
                        entry.getKey(),
                        childId,
                        "Родитель расположен недостаточно высоко"));
                }
            }
        }

        for (Map.Entry<String, java.util.Set<String>> entry : graph.siblingsByPerson.entrySet()) {
            LayoutSnapshot.Position first = snapshot.positionOf(entry.getKey());
            if (first == null) continue;
            for (String siblingId : entry.getValue()) {
                if (entry.getKey().compareTo(siblingId) >= 0) continue;
                LayoutSnapshot.Position sibling = snapshot.positionOf(siblingId);
                if (sibling != null && Math.abs(first.y - sibling.y) > EPSILON) {
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
