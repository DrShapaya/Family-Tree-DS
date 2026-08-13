package ru.drshapaya.androidft2;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Horizontal branch envelope for every occupied grid row. */
final class BranchContour {
    final Map<Integer, Span> rows;

    private BranchContour(Map<Integer, Span> rows) {
        this.rows = Collections.unmodifiableMap(rows);
    }

    static BranchContour from(TreeState state, Collection<String> ids) {
        Map<Integer, Span> rows = new LinkedHashMap<>();
        if (state == null || ids == null) return new BranchContour(rows);
        for (String id : ids) {
            Person person = state.people.get(id);
            if (person == null || !Float.isFinite(person.x) || !Float.isFinite(person.y)) continue;
            int row = Math.round(person.y / TreeLayoutEngine.GRID);
            Span current = rows.get(row);
            float right = person.x + TreeLayoutEngine.CARD_W;
            rows.put(row, current == null
                ? new Span(person.x, right, 1)
                : new Span(
                    Math.min(current.left, person.x),
                    Math.max(current.right, right),
                    current.count + 1));
        }
        return new BranchContour(rows);
    }

    static BranchContour from(LayoutSnapshot snapshot, Collection<String> ids) {
        Map<Integer, Span> rows = new LinkedHashMap<>();
        if (snapshot == null || ids == null) return new BranchContour(rows);
        for (String id : ids) {
            LayoutSnapshot.Position position = snapshot.positionOf(id);
            if (position == null || !position.isFinite()) continue;
            int row = Math.round(position.y / TreeLayoutEngine.GRID);
            Span current = rows.get(row);
            float right = position.x + TreeLayoutEngine.CARD_W;
            rows.put(row, current == null
                ? new Span(position.x, right, 1)
                : new Span(
                    Math.min(current.left, position.x),
                    Math.max(current.right, right),
                    current.count + 1));
        }
        return new BranchContour(rows);
    }

    BranchContour shifted(float dx) {
        Map<Integer, Span> shifted = new LinkedHashMap<>();
        for (Map.Entry<Integer, Span> entry : rows.entrySet()) {
            Span span = entry.getValue();
            shifted.put(entry.getKey(), new Span(span.left + dx, span.right + dx, span.count));
        }
        return new BranchContour(shifted);
    }

    float requiredRightShiftFrom(BranchContour left, float gap) {
        float required = 0f;
        if (left == null) return required;
        for (Map.Entry<Integer, Span> entry : rows.entrySet()) {
            Span leftSpan = left.rows.get(entry.getKey());
            if (leftSpan == null) continue;
            required = Math.max(required, leftSpan.right + gap - entry.getValue().left);
        }
        return Math.max(0f, required);
    }

    float horizontalGapFrom(BranchContour left) {
        float gap = Float.MAX_VALUE;
        if (left == null) return gap;
        for (Map.Entry<Integer, Span> entry : rows.entrySet()) {
            Span leftSpan = left.rows.get(entry.getKey());
            if (leftSpan == null) continue;
            gap = Math.min(gap, entry.getValue().left - leftSpan.right);
        }
        return gap;
    }

    boolean structurallyMatches(BranchContour other) {
        if (other == null || rows.size() != other.rows.size() || rows.isEmpty()) return false;
        int thisBottom = Collections.max(rows.keySet());
        int otherBottom = Collections.max(other.rows.keySet());
        for (Map.Entry<Integer, Span> entry : rows.entrySet()) {
            Span otherSpan = other.rows.get(entry.getKey() - thisBottom + otherBottom);
            if (otherSpan == null) return false;
            Span span = entry.getValue();
            if (span.count != otherSpan.count
                || Math.abs(span.width() - otherSpan.width()) > 0.5f) return false;
        }
        return true;
    }

    float left() {
        float value = Float.MAX_VALUE;
        for (Span span : rows.values()) value = Math.min(value, span.left);
        return value == Float.MAX_VALUE ? 0f : value;
    }

    float right() {
        float value = -Float.MAX_VALUE;
        for (Span span : rows.values()) value = Math.max(value, span.right);
        return value == -Float.MAX_VALUE ? 0f : value;
    }

    static final class Span {
        final float left;
        final float right;
        final int count;

        Span(float left, float right, int count) {
            this.left = left;
            this.right = right;
            this.count = count;
        }

        float width() {
            return right - left;
        }
    }
}
