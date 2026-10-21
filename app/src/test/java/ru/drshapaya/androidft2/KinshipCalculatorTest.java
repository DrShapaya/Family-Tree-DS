package ru.drshapaya.androidft2;

import org.junit.Test;
import static org.junit.Assert.*;

public class KinshipCalculatorTest {
    private TreeState cousins(int firstDepth, int secondDepth, boolean explicitSiblings) {
        TreeState state = new TreeState();
        person(state, "root");
        for (String branch : new String[]{"a", "b"}) {
            int count = branch.equals("a") ? firstDepth : secondDepth;
            String parent = "root";
            for (int i = 1; i <= count; i++) {
                String id = branch + i;
                person(state, id);
                if (!explicitSiblings || i > 1) state.addRelation("parent", parent, id);
                parent = id;
            }
        }
        if (explicitSiblings) state.addRelation("sibling", "a1", "b1");
        return state;
    }

    private void person(TreeState state, String id) {
        Person p = new Person(id); p.name = id; p.gender = PersonGender.FEMALE;
        state.people.put(id, p);
    }

    @Test public void fourthNieceAndAuntWorkInBothDirections() {
        for (boolean explicit : new boolean[]{false, true}) {
            TreeState state = cousins(4, 5, explicit);
            KinshipCalculator.Result result = KinshipCalculator.calculate(state, "b5", "a4", true);
            assertEquals("четвероюродная племянница", result.firstToSecond);
            assertEquals("четвероюродная тётя", result.secondToFirst);
            assertEquals(result.secondToFirst, KinshipCalculator.calculate(state, "a4", "b5", true).firstToSecond);
            assertEquals("четвероюродная сестра", KinshipCalculator.calculate(state, "b4", "a4", true).firstToSecond);
        }
    }

    @Test public void parametricModePreservesGenerationDirection() {
        TreeState state = cousins(4, 6, false);
        KinshipCalculator.Result result = KinshipCalculator.calculate(state, "b6", "a4", false);
        assertEquals("четвероюродная сестра с разницей в 2 поколения (младшее поколение)", result.firstToSecond);
        assertEquals("четвероюродная сестра с разницей в 2 поколения (старшее поколение)", result.secondToFirst);
    }

    @Test public void twoGenerationsAreNotReportedAsOne() {
        TreeState state = cousins(4, 6, false);
        KinshipCalculator.Result result = KinshipCalculator.calculate(state, "b6", "a4", true);
        assertEquals("внучка четвероюродного брата или четвероюродной сестры", result.firstToSecond);
        assertEquals("четвероюродная сестра дедушки или бабушки", result.secondToFirst);
    }

    @Test public void directNieceAndOlderCollateralGenerations() {
        for (boolean explicit : new boolean[]{false, true}) {
            TreeState state = cousins(1, 4, explicit);
            assertEquals("племянница", KinshipCalculator.calculate(state, "b2", "a1", true).firstToSecond);
            assertEquals("тётя", KinshipCalculator.calculate(state, "a1", "b2", true).firstToSecond);
            assertEquals("внучка брата или сестры", KinshipCalculator.calculate(state, "b3", "a1", true).firstToSecond);
            assertEquals("сестра прадедушки или прабабушки", KinshipCalculator.calculate(state, "a1", "b4", true).firstToSecond);
        }
    }

    @Test public void gendersAndDistantDegreeArePreserved() {
        TreeState state = cousins(6, 7, false);
        state.people.get("b7").gender = PersonGender.MALE;
        assertEquals("шестиюродный племянник", KinshipCalculator.calculate(state, "b7", "a6", true).firstToSecond);
        state.people.get("b7").gender = PersonGender.UNKNOWN;
        assertEquals("шестиюродный племянник или племянница", KinshipCalculator.calculate(state, "b7", "a6", true).firstToSecond);
    }

    @Test public void coordinatesDoNotDefineKinshipAndSearchUsesMode() {
        TreeState state = cousins(2, 3, false);
        state.people.get("a2").y = 10000;
        state.people.get("b3").y = -10000;
        state.people.get("a2").name = "Анна";
        state.people.get("b3").name = "Мария";
        assertTrue(SmartPeopleSearch.parse(state, "Кто Мария для Анны", "a2", true).kinshipSummary.contains("двоюродная племянница"));
        assertTrue(SmartPeopleSearch.parse(state, "Кто Мария для Анны", "a2", false).kinshipSummary.contains("младшее поколение"));
    }

    @Test public void missingSecondParentDoesNotProveHalfSiblings() {
        TreeState state = cousins(1, 1, false);
        assertEquals("сестра", KinshipCalculator.calculate(state, "a1", "b1", true).firstToSecond);
        person(state, "fatherA"); person(state, "fatherB");
        state.addRelation("parent", "fatherA", "a1"); state.addRelation("parent", "fatherB", "b1");
        assertEquals("единоутробная сестра", KinshipCalculator.calculate(state, "a1", "b1", true).firstToSecond);
    }

    @Test public void directRelativesPartnersAndMissingPeopleStillWork() {
        TreeState state = cousins(1, 3, false);
        assertEquals("дочь", KinshipCalculator.calculate(state, "b1", "root", true).firstToSecond);
        assertEquals("бабушка", KinshipCalculator.calculate(state, "root", "b2", true).firstToSecond);
        person(state, "wife"); state.addRelation("partner", "wife", "a1");
        assertEquals("жена", KinshipCalculator.calculate(state, "wife", "a1", true).firstToSecond);
        assertFalse(KinshipCalculator.calculate(state, "missing", "root", true).found);
    }
}
