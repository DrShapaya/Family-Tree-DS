package ru.drshapaya.androidft2;

final class MainActivityRelations {
    private final MainActivity activity;

    MainActivityRelations(MainActivity activity) {
        this.activity = activity;
    }

    void startLink(String type) {
        if (activity.state == null || activity.state.people.isEmpty()) return;
        if (!"kinship".equals(type) && !activity.requireEditingEnabled()) return;
        activity.resetTransientCanvasModes(false);
        activity.pendingLinkType = type;
        activity.pendingLinkFrom = "";
        activity.selectedLinkId = "";
        activity.treeView.setLinkState("", "");
        activity.updateCanvasModePanel();
        activity.showPanel("");
    }

    boolean handlePendingLink(Person person) {
        if (activity.pendingLinkType.isEmpty() || person == null) return false;
        boolean kinshipMode = "kinship".equals(activity.pendingLinkType);
        if (activity.pendingLinkFrom.isEmpty()) {
            if (activity.editingBlocked() && !kinshipMode) {
                cancelLinkMode();
                activity.showEditingLockedPrompt();
                return true;
            }
            activity.pendingLinkFrom = person.id;
            activity.selectedLinkId = "";
            activity.state.selectedId = person.id;
            activity.treeView.setLinkState(activity.pendingLinkFrom, "");
            activity.toast("Первая карточка выбрана. Теперь выберите вторую");
            activity.bindState();
            return true;
        }
        if (person.id.equals(activity.pendingLinkFrom)) {
            cancelLinkMode();
            activity.bindState();
            return true;
        }
        if (kinshipMode) {
            Person first = activity.state.people.get(activity.pendingLinkFrom);
            KinshipCalculator.Result result = KinshipCalculator.calculate(
                activity.state,
                activity.pendingLinkFrom,
                person.id);
            activity.pendingLinkType = "";
            activity.pendingLinkFrom = "";
            activity.selectedLinkId = "";
            activity.state.selectedId = person.id;
            activity.treeView.setLinkState("", "");
            activity.bindState();
            activity.treeView.focusPerson(person.id);
            activity.showKinshipResult(first, person, result);
            return true;
        }
        if (activity.editingBlocked()) {
            cancelLinkMode();
            activity.showEditingLockedPrompt();
            return true;
        }
        String fromName = activity.state.people.containsKey(activity.pendingLinkFrom) ? activity.state.people.get(activity.pendingLinkFrom).name : "Без имени";
        String toName = person.name.isEmpty() ? "Без имени" : person.name;
        if ("erase".equals(activity.pendingLinkType)) {
            if (!hasRelationsBetween(activity.pendingLinkFrom, person.id)) {
                activity.pendingLinkType = "";
                activity.pendingLinkFrom = "";
                activity.selectedLinkId = "";
                activity.state.selectedId = person.id;
                activity.toast("Связь не найдена");
                activity.bindState();
                return true;
            }
            activity.recordUndo("Удалена связь: " + fromName + " - " + toName);
            activity.state.removeRelationsBetween(activity.pendingLinkFrom, person.id);
            activity.pendingLinkType = "";
            activity.pendingLinkFrom = "";
            activity.selectedLinkId = "";
            activity.state.selectedId = person.id;
            activity.saveToast("Связь удалена");
            activity.bindState();
            return true;
        }
        activity.recordUndo("Создана связь: " + fromName + " - " + toName);
        Relation relation = activity.state.addRelation(activity.pendingLinkType, activity.pendingLinkFrom, person.id);
        activity.pendingLinkType = "";
        activity.pendingLinkFrom = "";
        activity.selectedLinkId = relation == null ? "" : relation.id;
        activity.state.selectedId = person.id;
        activity.saveToast("Связь создана");
        activity.bindState();
        return true;
    }

    void cancelLinkMode() {
        cancelLinkMode(true);
    }

    void cancelLinkMode(boolean notify) {
        boolean kinshipMode = "kinship".equals(activity.pendingLinkType);
        boolean active = !activity.pendingLinkType.isEmpty();
        activity.pendingLinkType = "";
        activity.pendingLinkFrom = "";
        activity.selectedLinkId = "";
        if (activity.treeView != null) activity.treeView.setLinkState("", "");
        activity.updateCanvasModePanel();
        if (notify && active) {
            activity.toast(kinshipMode ? "Определение родства выключено" : "Режим связи выключен");
        }
    }

    private boolean hasRelationsBetween(String firstId, String secondId) {
        for (Relation relation : activity.state.links) {
            boolean forward = firstId.equals(relation.from) && secondId.equals(relation.to);
            boolean backward = firstId.equals(relation.to) && secondId.equals(relation.from);
            if (forward || backward) return true;
        }
        return false;
    }
}
