package ru.drshapaya.androidft2;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable coordinate state used while comparing solver candidates. */
final class LayoutSnapshot {
    final Map<String, Position> positions;

    private LayoutSnapshot(Map<String, Position> positions) {
        this.positions = Collections.unmodifiableMap(positions);
    }

    static LayoutSnapshot capture(TreeState state) {
        Map<String, Position> positions = new LinkedHashMap<>();
        if (state != null) {
            for (Person person : state.people.values()) {
                positions.put(person.id, new Position(person.x, person.y));
            }
        }
        return new LayoutSnapshot(positions);
    }

    Position positionOf(String id) {
        return positions.get(id);
    }

    LayoutSnapshot shifted(Collection<String> ids, float dx, float dy) {
        Map<String, Position> result = new LinkedHashMap<>(positions);
        if (ids != null) {
            for (String id : ids) {
                Position current = result.get(id);
                if (current != null) result.put(id, new Position(current.x + dx, current.y + dy));
            }
        }
        return new LayoutSnapshot(result);
    }

    void applyTo(TreeState state) {
        if (state == null) return;
        for (Map.Entry<String, Position> entry : positions.entrySet()) {
            Person person = state.people.get(entry.getKey());
            if (person == null) continue;
            person.x = entry.getValue().x;
            person.y = entry.getValue().y;
        }
    }

    float totalManhattanMovementFrom(LayoutSnapshot baseline) {
        if (baseline == null) return 0f;
        float movement = 0f;
        for (Map.Entry<String, Position> entry : positions.entrySet()) {
            Position before = baseline.positions.get(entry.getKey());
            Position after = entry.getValue();
            if (before == null || !before.isFinite() || !after.isFinite()) continue;
            movement += Math.abs(after.x - before.x) + Math.abs(after.y - before.y);
        }
        return movement;
    }

    Bounds bounds() {
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (Position position : positions.values()) {
            if (!position.isFinite()) continue;
            left = Math.min(left, position.x);
            top = Math.min(top, position.y);
            right = Math.max(right, position.x + TreeLayoutEngine.CARD_W);
            bottom = Math.max(bottom, position.y + TreeLayoutEngine.CARD_H);
        }
        if (left == Float.MAX_VALUE) return new Bounds(0f, 0f, 0f, 0f);
        return new Bounds(left, top, right, bottom);
    }

    static final class Position {
        final float x;
        final float y;

        Position(float x, float y) {
            this.x = x;
            this.y = y;
        }

        boolean isFinite() {
            return Float.isFinite(x) && Float.isFinite(y);
        }
    }

    static final class Bounds {
        final float left;
        final float top;
        final float right;
        final float bottom;

        Bounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        float width() {
            return right - left;
        }

        float height() {
            return bottom - top;
        }
    }
}
