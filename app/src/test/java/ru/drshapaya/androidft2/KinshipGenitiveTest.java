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

    private static void person(TreeState state, String id, String gender) {
        Person p = new Person(id);
        p.name = id;
        p.gender = gender;
        p.genderManual = true;
        state.people.put(id, p);
    }
}
