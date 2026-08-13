package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Removes candidate operations that would move a protected layout anchor. */
final class LockedLayoutCandidateGenerator implements LayoutCandidateGenerator {
    private final LayoutCandidateGenerator delegate;
    private final Set<String> lockedIds;

    LockedLayoutCandidateGenerator(
        LayoutCandidateGenerator delegate,
        Collection<String> lockedIds
    ) {
        this.delegate = delegate;
        this.lockedIds = Collections.unmodifiableSet(new LinkedHashSet<>(
            lockedIds == null ? Collections.emptySet() : lockedIds));
    }

    @Override
    public List<LayoutOperation> generate(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        if (delegate == null) return Collections.emptyList();
        List<LayoutOperation> result = new ArrayList<>();
        for (LayoutOperation operation : delegate.generate(graph, snapshot)) {
            if (Collections.disjoint(operation.affectedIds(), lockedIds)) result.add(operation);
        }
        return result;
    }
}
