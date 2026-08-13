package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TreeStateTest {
    @Test
    public void newPeopleUseSurnameColorByDefault() {
        TreeState state = new TreeState();

        Person person = state.addPerson("Иванов Иван", 0f, 0f);

        assertEquals("auto-surname", person.colorMode);
        assertEquals(TreeState.colorFor("иванов", 0), person.color);
    }

    @Test
    public void copiedStateKeepsAutoArrangePreference() {
        TreeState state = new TreeState();
        state.autoArrangeOnAdd = true;

        TreeState copied = TreeStateCopier.copy(state);

        assertEquals(true, copied.autoArrangeOnAdd);
    }

    @Test
    public void newPartnerBecomesParentOfAllExistingChildren() {
        TreeState state = new TreeState();
        Person parent = state.addPerson("Родитель", 0f, 0f);
        Person partner = state.addPerson("Новый партнёр", 0f, 0f);
        Person firstChild = state.addPerson("Ребёнок 1", 0f, 0f);
        Person secondChild = state.addPerson("Ребёнок 2", 0f, 0f);
        state.addRelation("parent", parent.id, firstChild.id);
        state.addRelation("parent", parent.id, secondChild.id);

        state.copyParentLinks(parent.id, partner.id);

        assertTrue(hasParentLink(state, partner.id, firstChild.id));
        assertTrue(hasParentLink(state, partner.id, secondChild.id));
    }

    @Test
    public void newParentsAttachToExistingSiblingGroupWithoutParents() {
        TreeState state = new TreeState();
        Person first = state.addPerson("Брат 1", 0f, 0f);
        Person second = state.addPerson("Брат 2", 0f, 0f);
        Person third = state.addPerson("Брат 3", 0f, 0f);
        state.addRelation("sibling", first.id, second.id);
        state.addRelation("sibling", second.id, third.id);

        java.util.List<String> targets = state.siblingFamilyTargetsForNewParent(
            second.id,
            java.util.Collections.emptyList());
        Person father = state.addPerson("Отец", 0f, 0f);
        Person mother = state.addPerson("Мать", 0f, 0f);
        for (String childId : targets) {
            state.addRelation("parent", father.id, childId);
            state.addRelation("parent", mother.id, childId);
        }

        for (Person child : new Person[]{first, second, third}) {
            assertTrue(hasParentLink(state, father.id, child.id));
            assertTrue(hasParentLink(state, mother.id, child.id));
        }
    }

    @Test
    public void newParentDoesNotAttachToSiblingWithDifferentKnownParents() {
        TreeState state = new TreeState();
        Person selected = state.addPerson("Брат", 0f, 0f);
        Person halfSibling = state.addPerson("Сводная сестра", 0f, 0f);
        Person otherFather = state.addPerson("Другой отец", 0f, 0f);
        Person otherMother = state.addPerson("Другая мать", 0f, 0f);
        state.addRelation("sibling", selected.id, halfSibling.id);
        state.addRelation("parent", otherFather.id, halfSibling.id);
        state.addRelation("parent", otherMother.id, halfSibling.id);

        java.util.List<String> targets = state.siblingFamilyTargetsForNewParent(
            selected.id,
            java.util.Collections.emptyList());

        assertTrue(targets.contains(selected.id));
        assertTrue(!targets.contains(halfSibling.id));
    }

    private boolean hasParentLink(TreeState state, String parentId, String childId) {
        for (Relation relation : state.links) {
            if ("parent".equals(relation.type)
                && parentId.equals(relation.from)
                && childId.equals(relation.to)) return true;
        }
        return false;
    }
}
