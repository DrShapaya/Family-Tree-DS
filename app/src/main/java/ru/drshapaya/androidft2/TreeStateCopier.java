package ru.drshapaya.androidft2;

final class TreeStateCopier {
    private TreeStateCopier() {
    }

    static TreeState copy(TreeState source) {
        TreeState target = new TreeState();
        if (source == null) return target;
        for (Person person : source.people.values()) {
            Person copied = copyPerson(person);
            target.people.put(copied.id, copied);
        }
        for (Relation relation : source.links) target.links.add(copyRelation(relation));
        for (Guide guide : source.guides) target.guides.add(copyGuide(guide));
        for (HistoryEntry history : source.history) target.history.add(copyHistory(history));
        copyMetadata(source, target);
        return target;
    }

    static Person copyPerson(Person source) {
        Person target = new Person(source == null ? "" : source.id);
        if (source == null) return target;
        target.name = source.name;
        target.born = source.born;
        target.died = source.died;
        target.bornDay = source.bornDay;
        target.bornMonth = source.bornMonth;
        target.bornYear = source.bornYear;
        target.diedDay = source.diedDay;
        target.diedMonth = source.diedMonth;
        target.diedYear = source.diedYear;
        target.place = source.place;
        target.notes = source.notes;
        target.photoMediaId = source.photoMediaId;
        target.photo = source.photo;
        target.gender = source.gender;
        target.genderManual = source.genderManual;
        target.colorMode = source.colorMode;
        target.manualColor = source.manualColor;
        target.color = source.color;
        target.x = source.x;
        target.y = source.y;
        target.pinned = source.pinned;
        for (Memory memory : source.memories) target.memories.add(copyMemory(memory));
        return target;
    }

    static Relation copyRelation(Relation source) {
        return new Relation(source.id, source.type, source.from, source.to, source.side);
    }

    static Guide copyGuide(Guide source) {
        Guide target = new Guide();
        target.id = source.id;
        target.axis = source.axis;
        target.position = source.position;
        target.color = source.color;
        target.label = source.label;
        return target;
    }

    private static Memory copyMemory(Memory source) {
        Memory target = new Memory();
        target.id = source.id;
        target.type = source.type;
        target.title = source.title;
        target.text = source.text;
        target.filename = source.filename;
        target.mimeType = source.mimeType;
        target.data = source.data;
        target.at = source.at;
        for (MemoryAttachment attachment : source.attachments) {
            MemoryAttachment copied = new MemoryAttachment();
            copied.id = attachment.id;
            copied.filename = attachment.filename;
            copied.mimeType = attachment.mimeType;
            copied.type = attachment.type;
            copied.mediaId = attachment.mediaId;
            copied.size = attachment.size;
            copied.data = attachment.data;
            target.attachments.add(copied);
        }
        return target;
    }

    private static HistoryEntry copyHistory(HistoryEntry source) {
        HistoryEntry target = new HistoryEntry();
        target.id = source.id;
        target.label = source.label;
        target.detail = source.detail;
        target.at = source.at;
        return target;
    }

    static void copyMetadata(TreeState source, TreeState target) {
        target.rootId = source.rootId;
        target.selectedId = source.selectedId;
        target.theme = source.theme;
        target.printScale = source.printScale;
        target.editLocked = source.editLocked;
        target.historyHidden = source.historyHidden;
        target.inspectorHidden = source.inspectorHidden;
        target.adminCollapsed = source.adminCollapsed;
        target.readerMode = source.readerMode;
        target.onboardingCompleted = source.onboardingCompleted;
        target.onboardingOffered = source.onboardingOffered;
        target.guidesVisible = source.guidesVisible;
        target.hideCardDetails = source.hideCardDetails;
        target.compactCards = source.compactCards;
        target.focusTree = source.focusTree;
        target.autoArrangeOnAdd = source.autoArrangeOnAdd;
        target.workspaceBoundsVisible = source.workspaceBoundsVisible;
        target.workspaceBoundsStyle = source.workspaceBoundsStyle;
        target.workspaceWidth = source.workspaceWidth;
        target.workspaceHeight = source.workspaceHeight;
        target.parentLineMode = source.parentLineMode;
    }
}
