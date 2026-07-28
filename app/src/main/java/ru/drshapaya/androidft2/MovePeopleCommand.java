package ru.drshapaya.androidft2;

import android.graphics.PointF;

import java.util.LinkedHashMap;
import java.util.Map;

final class MovePeopleCommand implements TreeCommand {
    private final Map<String, PointF> before = new LinkedHashMap<>();
    private final Map<String, PointF> after = new LinkedHashMap<>();
    private final String label;

    MovePeopleCommand(Map<String, PointF> start, Map<String, PointF> end, String label) {
        copy(start, before);
        copy(end, after);
        this.label = label == null || label.isEmpty() ? "Перемещена карточка" : label;
    }

    boolean isEmpty() {
        if (before.size() != after.size()) return false;
        for (Map.Entry<String, PointF> entry : before.entrySet()) {
            PointF end = after.get(entry.getKey());
            PointF start = entry.getValue();
            if (end == null
                || Float.compare(start.x, end.x) != 0
                || Float.compare(start.y, end.y) != 0) return false;
        }
        return true;
    }

    @Override
    public void undo(TreeState state) {
        apply(state, before);
    }

    @Override
    public void redo(TreeState state) {
        apply(state, after);
    }

    @Override
    public int estimatedBytes() {
        return 96 + (before.size() + after.size()) * 40;
    }

    @Override
    public String label() {
        return label;
    }

    private static void apply(TreeState state, Map<String, PointF> positions) {
        for (Map.Entry<String, PointF> entry : positions.entrySet()) {
            Person person = state.people.get(entry.getKey());
            if (person == null) continue;
            person.x = entry.getValue().x;
            person.y = entry.getValue().y;
        }
    }

    private static void copy(Map<String, PointF> source, Map<String, PointF> target) {
        if (source == null) return;
        for (Map.Entry<String, PointF> entry : source.entrySet()) {
            PointF point = entry.getValue();
            if (point != null) target.put(entry.getKey(), new PointF(point.x, point.y));
        }
    }
}
