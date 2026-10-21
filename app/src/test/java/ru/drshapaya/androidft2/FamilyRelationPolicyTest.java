package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FamilyRelationPolicyTest {
    @Test
    public void outerPartnerAutomaticallyUsesItsOnlyConnection() {
        TreeState state = partnerFan();

        assertFalse(FamilyRelationPolicy.needsCoParentChoice(state, "outerA"));
        assertEquals("center", FamilyRelationPolicy.automaticCoParent(state, "outerA"));
    }

    @Test
    public void commonPersonWithSeveralPartnersRequiresExplicitChoice() {
        TreeState state = partnerFan();

        assertTrue(FamilyRelationPolicy.needsCoParentChoice(state, "center"));
        assertEquals("", FamilyRelationPolicy.automaticCoParent(state, "center"));
        assertEquals(
            "outerB",
            FamilyRelationPolicy.validChosenCoParent(state, "center", "outerB"));
        assertEquals(
            "",
            FamilyRelationPolicy.validChosenCoParent(state, "center", "unrelated"));
    }

    @Test
    public void partnerPromptAppearsOnlyWhenPersonAlreadyHasChildren() {
        TreeState state = partnerFan();
        person(state, "child");
        state.addRelation("parent", "center", "child");

        assertTrue(FamilyRelationPolicy.askWhetherNewPartnerParentsExistingChildren(
            state,
            "center"));
        assertFalse(FamilyRelationPolicy.askWhetherNewPartnerParentsExistingChildren(
            state,
            "outerA"));
    }

    private static TreeState partnerFan() {
        TreeState state = new TreeState();
        person(state, "center");
        person(state, "outerA");
        person(state, "outerB");
        person(state, "outerC");
        state.addRelation("partner", "center", "outerA");
        state.addRelation("partner", "center", "outerB");
        state.addRelation("family", "center", "outerC");
        return state;
    }

    private static void person(TreeState state, String id) {
        Person person = new Person(id);
        person.name = id;
        state.people.put(id, person);
    }
}
