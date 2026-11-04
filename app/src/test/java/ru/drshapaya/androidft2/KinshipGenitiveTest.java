package ru.drshapaya.androidft2;

import org.junit.Test;
import static org.junit.Assert.*;

public class KinshipGenitiveTest {
    @Test public void declinesNounsAdjectivesAndRepeatedPra() {
        assertEquals("дяди", KinshipGenitive.of("дядя"));
        assertEquals("двоюродной сестры", KinshipGenitive.of("двоюродная сестра"));
        assertEquals("троюродного брата или сестры", KinshipGenitive.of("троюродный брат или сестра"));
        assertEquals("прапрадедушки", KinshipGenitive.of("прапрадедушка"));
        assertEquals("дочери", KinshipGenitive.of("дочь"));
        assertEquals("сестры бабушки", KinshipGenitive.of("сестра бабушки"));
        assertEquals("брата дедушки", KinshipGenitive.of("брат дедушки"));
        assertEquals("внучки сестры", KinshipGenitive.of("внучка сестры"));
        assertEquals("внука брата", KinshipGenitive.of("внук брата"));
        assertEquals("двоюродной сестры бабушки", KinshipGenitive.of("двоюродная сестра бабушки"));
        assertEquals("праправнука", KinshipGenitive.of("праправнук"));
        assertNull(KinshipGenitive.of("родственник в 5 поколении"));
    }

    @Test public void uncleWifeReadsAsWifeOfUncle() {
        TreeState state = new TreeState();
        person(state, "grandpa", PersonGender.MALE);
        person(state, "uncle", PersonGender.MALE);
        person(state, "father", PersonGender.MALE);
        person(state, "me", PersonGender.MALE);
        person(state, "aunt", PersonGender.FEMALE);
        state.addRelation("parent", "grandpa", "uncle");
        state.addRelation("parent", "grandpa", "father");
        state.addRelation("parent", "father", "me");
        state.addRelation("partner", "uncle", "aunt");
        assertEquals("дядя", KinshipCalculator.calculate(state, "uncle", "me", true).firstToSecond);
        assertEquals("жена дяди", KinshipCalculator.calculate(state, "aunt", "me", true).firstToSecond);
    }

    @Test public void spouseOfGrandmotherSisterReadsAsHusbandOfGrandmotherSister() {
        TreeState state = new TreeState();
        person(state, "greatGrandpa", PersonGender.MALE);
        person(state, "grandma", PersonGender.FEMALE);
        person(state, "sisterOfGrandma", PersonGender.FEMALE);
        person(state, "husbandOfSister", PersonGender.MALE);
        person(state, "mother", PersonGender.FEMALE);
        person(state, "me", PersonGender.MALE);

        state.addRelation("parent", "greatGrandpa", "grandma");
        state.addRelation("parent", "greatGrandpa", "sisterOfGrandma");
        state.addRelation("parent", "grandma", "mother");
        state.addRelation("parent", "mother", "me");
        state.addRelation("partner", "sisterOfGrandma", "husbandOfSister");

        assertEquals("сестра бабушки", KinshipCalculator.calculate(state, "sisterOfGrandma", "me", true).firstToSecond);
        assertEquals("муж сестры бабушки", KinshipCalculator.calculate(state, "husbandOfSister", "me", true).firstToSecond);
    }

    private static void person(TreeState state, String id, String gender) {
        Person p = new Person(id);
        p.name = id;
        p.gender = gender;
        p.genderManual = true;
        state.people.put(id, p);
    }
}
