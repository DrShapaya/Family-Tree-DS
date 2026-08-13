package ru.drshapaya.androidft2;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/** Reversible candidate transformation explored by the solver. */
interface LayoutOperation {
    LayoutSnapshot apply(LayoutSnapshot source);

    LayoutOperation inverse();

    Set<String> affectedIds();

    String reason();
}

final class ShiftLayoutOperation implements LayoutOperation {
    private final LinkedHashSet<String> ids;
    private final float dx;
    private final float dy;
    private final String reason;

    ShiftLayoutOperation(Collection<String> ids, float dx, float dy, String reason) {
        this.ids = new LinkedHashSet<>(ids == null ? Collections.emptySet() : ids);
        this.dx = dx;
        this.dy = dy;
        this.reason = reason == null ? "" : reason;
    }

    @Override
    public LayoutSnapshot apply(LayoutSnapshot source) {
        return source == null ? null : source.shifted(ids, dx, dy);
    }

    @Override
    public LayoutOperation inverse() {
        return new ShiftLayoutOperation(ids, -dx, -dy, "undo:" + reason);
    }

    @Override
    public Set<String> affectedIds() {
        return Collections.unmodifiableSet(ids);
    }

    @Override
    public String reason() {
        return reason;
    }
}

final class CompositeLayoutOperation implements LayoutOperation {
    private final List<LayoutOperation> operations;
    private final String reason;

    CompositeLayoutOperation(Collection<? extends LayoutOperation> operations, String reason) {
        this.operations = new ArrayList<>(operations == null
            ? Collections.emptyList()
            : operations);
        this.reason = reason == null ? "" : reason;
    }

    @Override
    public LayoutSnapshot apply(LayoutSnapshot source) {
        LayoutSnapshot result = source;
        for (LayoutOperation operation : operations) result = operation.apply(result);
        return result;
    }

    @Override
    public LayoutOperation inverse() {
        List<LayoutOperation> reversed = new ArrayList<>();
        for (int index = operations.size() - 1; index >= 0; index--) {
            reversed.add(operations.get(index).inverse());
        }
        return new CompositeLayoutOperation(reversed, "undo:" + reason);
    }

    @Override
    public Set<String> affectedIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (LayoutOperation operation : operations) ids.addAll(operation.affectedIds());
        return Collections.unmodifiableSet(ids);
    }

    @Override
    public String reason() {
        return reason;
    }
}
