package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Filters a general candidate generator through a local impact region. */
final class ScopedLayoutCandidateGenerator implements LayoutCandidateGenerator {
    private final LayoutCandidateGenerator delegate;
    private final LayoutImpactRegion region;

    ScopedLayoutCandidateGenerator(
        LayoutCandidateGenerator delegate,
        LayoutImpactRegion region
    ) {
        this.delegate = delegate;
        this.region = region;
    }

    @Override
    public List<LayoutOperation> generate(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        if (delegate == null || region == null) return Collections.emptyList();
        List<LayoutOperation> result = new ArrayList<>();
        for (LayoutOperation operation : delegate.generate(graph, snapshot)) {
            if (region.allows(operation)) result.add(operation);
        }
        return result;
    }
}
