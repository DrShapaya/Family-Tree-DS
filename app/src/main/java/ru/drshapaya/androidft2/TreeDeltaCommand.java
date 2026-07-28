package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reversible state delta. Only changed people and changed structural lists are
 * retained; media remains referenced by id and is never copied into Undo.
 */
final class TreeDeltaCommand implements TreeCommand {
    private final String label;
    private final Map<String, Person> beforePeople = new LinkedHashMap<>();
    private final Map<String, Person> afterPeople = new LinkedHashMap<>();
    private final Set<String> changedPersonIds = new LinkedHashSet<>();
    private final List<Relation> beforeLinks;
    private final List<Relation> afterLinks;
    private final List<Guide> beforeGuides;
    private final List<Guide> afterGuides;
    private final Metadata beforeMetadata;
    private final Metadata afterMetadata;
    private final int estimatedBytes;

    static TreeDeltaCommand between(TreeState before, TreeState after, String label) {
        return new TreeDeltaCommand(before, after, label);
    }

    private TreeDeltaCommand(TreeState before, TreeState after, String value) {
        label = value == null || value.trim().isEmpty() ? "последнее действие" : value.trim();
        Set<String> ids = new LinkedHashSet<>(before.people.keySet());
        ids.addAll(after.people.keySet());
        int estimate = 128;
        for (String id : ids) {
            Person oldPerson = before.people.get(id);
            Person newPerson = after.people.get(id);
            if (samePerson(oldPerson, newPerson)) continue;
            changedPersonIds.add(id);
            if (oldPerson != null) {
                Person copy = TreeStateCopier.copyPerson(oldPerson);
                beforePeople.put(id, copy);
                estimate += estimatePerson(copy);
            }
            if (newPerson != null) {
                Person copy = TreeStateCopier.copyPerson(newPerson);
                afterPeople.put(id, copy);
                estimate += estimatePerson(copy);
            }
        }
        if (sameRelations(before.links, after.links)) {
            beforeLinks = null;
            afterLinks = null;
        } else {
            beforeLinks = copyRelations(before.links);
            afterLinks = copyRelations(after.links);
            estimate += (beforeLinks.size() + afterLinks.size()) * 160;
        }
        if (sameGuides(before.guides, after.guides)) {
            beforeGuides = null;
            afterGuides = null;
        } else {
            beforeGuides = copyGuides(before.guides);
            afterGuides = copyGuides(after.guides);
            estimate += (beforeGuides.size() + afterGuides.size()) * 128;
        }
        beforeMetadata = new Metadata(before);
        afterMetadata = new Metadata(after);
        estimatedBytes = Math.max(256, estimate);
    }

    boolean isEmpty() {
        return changedPersonIds.isEmpty()
            && beforeLinks == null
            && beforeGuides == null
            && beforeMetadata.same(afterMetadata);
    }

    @Override
    public void undo(TreeState state) {
        apply(state, beforePeople, beforeLinks, beforeGuides, beforeMetadata);
    }

    @Override
    public void redo(TreeState state) {
        apply(state, afterPeople, afterLinks, afterGuides, afterMetadata);
    }

    @Override
    public int estimatedBytes() {
        return estimatedBytes;
    }

    @Override
    public String label() {
        return label;
    }

    private void apply(
        TreeState state,
        Map<String, Person> people,
        List<Relation> links,
        List<Guide> guides,
        Metadata metadata
    ) {
        for (String id : changedPersonIds) {
            Person person = people.get(id);
            if (person == null) state.people.remove(id);
            else state.people.put(id, TreeStateCopier.copyPerson(person));
        }
        if (links != null) {
            state.links.clear();
            state.links.addAll(copyRelations(links));
        }
        if (guides != null) {
            state.guides.clear();
            state.guides.addAll(copyGuides(guides));
        }
        metadata.apply(state);
    }

    private static List<Relation> copyRelations(List<Relation> source) {
        List<Relation> result = new ArrayList<>(source.size());
        for (Relation relation : source) result.add(TreeStateCopier.copyRelation(relation));
        return result;
    }

    private static List<Guide> copyGuides(List<Guide> source) {
        List<Guide> result = new ArrayList<>(source.size());
        for (Guide guide : source) result.add(TreeStateCopier.copyGuide(guide));
        return result;
    }

    private static boolean sameRelations(List<Relation> first, List<Relation> second) {
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            Relation a = first.get(i);
            Relation b = second.get(i);
            if (!Objects.equals(a.id, b.id)
                || !Objects.equals(a.type, b.type)
                || !Objects.equals(a.from, b.from)
                || !Objects.equals(a.to, b.to)
                || !Objects.equals(a.side, b.side)) return false;
        }
        return true;
    }

    private static boolean sameGuides(List<Guide> first, List<Guide> second) {
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            Guide a = first.get(i);
            Guide b = second.get(i);
            if (!Objects.equals(a.id, b.id)
                || !Objects.equals(a.axis, b.axis)
                || Float.compare(a.position, b.position) != 0
                || !Objects.equals(a.color, b.color)
                || !Objects.equals(a.label, b.label)) return false;
        }
        return true;
    }

    private static boolean samePerson(Person a, Person b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.id, b.id)
            && Objects.equals(a.name, b.name)
            && Objects.equals(a.born, b.born)
            && Objects.equals(a.died, b.died)
            && Objects.equals(a.bornDay, b.bornDay)
            && Objects.equals(a.bornMonth, b.bornMonth)
            && Objects.equals(a.bornYear, b.bornYear)
            && Objects.equals(a.diedDay, b.diedDay)
            && Objects.equals(a.diedMonth, b.diedMonth)
            && Objects.equals(a.diedYear, b.diedYear)
            && Objects.equals(a.place, b.place)
            && Objects.equals(a.notes, b.notes)
            && Objects.equals(a.photoMediaId, b.photoMediaId)
            && Objects.equals(a.photo, b.photo)
            && Objects.equals(a.gender, b.gender)
            && a.genderManual == b.genderManual
            && Objects.equals(a.colorMode, b.colorMode)
            && Objects.equals(a.manualColor, b.manualColor)
            && a.color == b.color
            && Float.compare(a.x, b.x) == 0
            && Float.compare(a.y, b.y) == 0
            && a.pinned == b.pinned
            && sameMemories(a.memories, b.memories);
    }

    private static boolean sameMemories(List<Memory> first, List<Memory> second) {
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            Memory a = first.get(i);
            Memory b = second.get(i);
            if (!Objects.equals(a.id, b.id)
                || !Objects.equals(a.type, b.type)
                || !Objects.equals(a.title, b.title)
                || !Objects.equals(a.text, b.text)
                || !Objects.equals(a.filename, b.filename)
                || !Objects.equals(a.mimeType, b.mimeType)
                || !Objects.equals(a.data, b.data)
                || !Objects.equals(a.at, b.at)
                || !sameAttachments(a.attachments, b.attachments)) return false;
        }
        return true;
    }

    private static boolean sameAttachments(
        List<MemoryAttachment> first,
        List<MemoryAttachment> second
    ) {
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            MemoryAttachment a = first.get(i);
            MemoryAttachment b = second.get(i);
            if (!Objects.equals(a.id, b.id)
                || !Objects.equals(a.filename, b.filename)
                || !Objects.equals(a.mimeType, b.mimeType)
                || !Objects.equals(a.type, b.type)
                || !Objects.equals(a.mediaId, b.mediaId)
                || a.size != b.size
                || !Objects.equals(a.data, b.data)) return false;
        }
        return true;
    }

    private static int estimatePerson(Person person) {
        int size = 256;
        size += length(person.name) + length(person.born) + length(person.died);
        size += length(person.place) + length(person.notes) + length(person.photoMediaId);
        size += length(person.photo) + length(person.gender) + length(person.manualColor);
        for (Memory memory : person.memories) {
            size += 192 + length(memory.title) + length(memory.text) + length(memory.data);
            for (MemoryAttachment attachment : memory.attachments) {
                size += 160 + length(attachment.filename) + length(attachment.mediaId)
                    + length(attachment.data);
            }
        }
        return size * 2;
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }

    private static final class Metadata {
        final String rootId;
        final String selectedId;
        final String theme;
        final int printScale;
        final boolean editLocked;
        final boolean historyHidden;
        final boolean inspectorHidden;
        final boolean adminCollapsed;
        final boolean readerMode;
        final boolean onboardingCompleted;
        final boolean onboardingOffered;
        final boolean guidesVisible;
        final boolean hideCardDetails;
        final boolean compactCards;
        final boolean focusTree;
        final String parentLineMode;

        Metadata(TreeState state) {
            rootId = state.rootId;
            selectedId = state.selectedId;
            theme = state.theme;
            printScale = state.printScale;
            editLocked = state.editLocked;
            historyHidden = state.historyHidden;
            inspectorHidden = state.inspectorHidden;
            adminCollapsed = state.adminCollapsed;
            readerMode = state.readerMode;
            onboardingCompleted = state.onboardingCompleted;
            onboardingOffered = state.onboardingOffered;
            guidesVisible = state.guidesVisible;
            hideCardDetails = state.hideCardDetails;
            compactCards = state.compactCards;
            focusTree = state.focusTree;
            parentLineMode = state.parentLineMode;
        }

        boolean same(Metadata other) {
            return other != null
                && Objects.equals(rootId, other.rootId)
                && Objects.equals(selectedId, other.selectedId)
                && Objects.equals(theme, other.theme)
                && printScale == other.printScale
                && editLocked == other.editLocked
                && historyHidden == other.historyHidden
                && inspectorHidden == other.inspectorHidden
                && adminCollapsed == other.adminCollapsed
                && readerMode == other.readerMode
                && onboardingCompleted == other.onboardingCompleted
                && onboardingOffered == other.onboardingOffered
                && guidesVisible == other.guidesVisible
                && hideCardDetails == other.hideCardDetails
                && compactCards == other.compactCards
                && focusTree == other.focusTree
                && Objects.equals(parentLineMode, other.parentLineMode);
        }

        void apply(TreeState state) {
            state.rootId = rootId;
            state.selectedId = selectedId;
            state.theme = theme;
            state.printScale = printScale;
            state.editLocked = editLocked;
            state.historyHidden = historyHidden;
            state.inspectorHidden = inspectorHidden;
            state.adminCollapsed = adminCollapsed;
            state.readerMode = readerMode;
            state.onboardingCompleted = onboardingCompleted;
            state.onboardingOffered = onboardingOffered;
            state.guidesVisible = guidesVisible;
            state.hideCardDetails = hideCardDetails;
            state.compactCards = compactCards;
            state.focusTree = focusTree;
            state.parentLineMode = parentLineMode;
        }
    }
}
