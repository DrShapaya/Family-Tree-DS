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

    @Test public void cardLabelsMatchPairwiseCalculationAndSkipTheReference() {
        TreeState state = cousins(4, 5, false);
        java.util.Map<String, String> labels = KinshipCalculator.labelsRelativeTo(state, "a4", true);
        assertFalse(labels.containsKey("a4"));
        for (String id : state.people.keySet()) {
            if (id.equals("a4")) continue;
            assertEquals(KinshipCalculator.calculate(state, id, "a4", true).firstToSecond, labels.get(id));
        }
    }

    @Test public void spouseTermsFollowPartnerStatus() {
        TreeState state = new TreeState();
        person(state, "wife");
        Person husband = new Person("husband"); husband.name = "husband"; husband.gender = PersonGender.MALE;
        state.people.put("husband", husband);
        state.addRelation("partner", "husband", "wife");
        Relation couple = state.links.get(0);
        assertEquals("жена", KinshipCalculator.calculate(state, "wife", "husband", true).firstToSecond);
        couple.status = PartnerStatus.ENGAGED;
        assertEquals("невеста", KinshipCalculator.calculate(state, "wife", "husband", true).firstToSecond);
        assertEquals("жених", KinshipCalculator.labelsRelativeTo(state, "wife", true).get("husband"));
        couple.status = PartnerStatus.DIVORCED;
        assertEquals("бывшая жена", KinshipCalculator.calculate(state, "wife", "husband", true).firstToSecond);
        assertEquals("бывший муж", KinshipCalculator.calculate(state, "wife", "husband", true).secondToFirst);
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
        assertEquals("внучка четвероюродной сестры", result.firstToSecond);
        assertEquals("четвероюродная сестра бабушки", result.secondToFirst);
    }

    @Test public void directNieceAndOlderCollateralGenerations() {
        for (boolean explicit : new boolean[]{false, true}) {
            TreeState state = cousins(1, 4, explicit);
            assertEquals("племянница", KinshipCalculator.calculate(state, "b2", "a1", true).firstToSecond);
            assertEquals("тётя", KinshipCalculator.calculate(state, "a1", "b2", true).firstToSecond);
            assertEquals("внучка сестры", KinshipCalculator.calculate(state, "b3", "a1", true).firstToSecond);
            assertEquals("сестра прабабушки", KinshipCalculator.calculate(state, "a1", "b4", true).firstToSecond);
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

    @Test public void sisterOfGrandmotherVersusSisterOfGrandfather() {
        TreeState state = new TreeState();
        person(state, "greatGrandpa"); state.people.get("greatGrandpa").gender = PersonGender.MALE;
        person(state, "grandma"); state.people.get("grandma").gender = PersonGender.FEMALE;
        person(state, "grandpa"); state.people.get("grandpa").gender = PersonGender.MALE;
        person(state, "sisterOfGrandma"); state.people.get("sisterOfGrandma").gender = PersonGender.FEMALE;
        person(state, "sisterOfGrandpa"); state.people.get("sisterOfGrandpa").gender = PersonGender.FEMALE;
        person(state, "brotherOfGrandma"); state.people.get("brotherOfGrandma").gender = PersonGender.MALE;
        person(state, "brotherOfGrandpa"); state.people.get("brotherOfGrandpa").gender = PersonGender.MALE;
        person(state, "father"); state.people.get("father").gender = PersonGender.MALE;
        person(state, "mother"); state.people.get("mother").gender = PersonGender.FEMALE;
        person(state, "me");

        state.addRelation("parent", "greatGrandpa", "grandma");
        state.addRelation("parent", "greatGrandpa", "sisterOfGrandma");
        state.addRelation("parent", "greatGrandpa", "brotherOfGrandma");
        state.addRelation("parent", "grandma", "mother");
        state.addRelation("parent", "mother", "me");

        assertEquals("сестра бабушки", KinshipCalculator.calculate(state, "sisterOfGrandma", "me", true).firstToSecond);
        assertEquals("брат бабушки", KinshipCalculator.calculate(state, "brotherOfGrandma", "me", true).firstToSecond);
        assertEquals("внучка сестры", KinshipCalculator.calculate(state, "me", "sisterOfGrandma", true).firstToSecond);
        assertEquals("внучка сестры", KinshipCalculator.calculate(state, "me", "brotherOfGrandma", true).firstToSecond);

        // Line through grandfather:
        person(state, "greatGrandpa2"); state.people.get("greatGrandpa2").gender = PersonGender.MALE;
        state.addRelation("parent", "greatGrandpa2", "grandpa");
        state.addRelation("parent", "greatGrandpa2", "sisterOfGrandpa");
        state.addRelation("parent", "greatGrandpa2", "brotherOfGrandpa");
        state.addRelation("parent", "grandpa", "father");
        state.addRelation("parent", "father", "me");

        assertEquals("сестра дедушки", KinshipCalculator.calculate(state, "sisterOfGrandpa", "me", true).firstToSecond);
        assertEquals("брат дедушки", KinshipCalculator.calculate(state, "brotherOfGrandpa", "me", true).firstToSecond);
        assertEquals("внучка брата", KinshipCalculator.calculate(state, "me", "sisterOfGrandpa", true).firstToSecond);
        assertEquals("внучка брата", KinshipCalculator.calculate(state, "me", "brotherOfGrandpa", true).firstToSecond);
    }

    @Test public void distantGenerationsHaveSpecificTerms() {
        TreeState state = new TreeState();
        person(state, "ancestor5"); state.people.get("ancestor5").gender = PersonGender.MALE;
        person(state, "ancestor4"); state.people.get("ancestor4").gender = PersonGender.FEMALE;
        person(state, "ancestor3"); state.people.get("ancestor3").gender = PersonGender.MALE;
        person(state, "ancestor2"); state.people.get("ancestor2").gender = PersonGender.FEMALE;
        person(state, "ancestor1"); state.people.get("ancestor1").gender = PersonGender.MALE;
        person(state, "root");

        state.addRelation("parent", "ancestor5", "ancestor4");
        state.addRelation("parent", "ancestor4", "ancestor3");
        state.addRelation("parent", "ancestor3", "ancestor2");
        state.addRelation("parent", "ancestor2", "ancestor1");
        state.addRelation("parent", "ancestor1", "root");

        // 4 generations (ancestor4 -> root is depth 4)
        assertEquals("прапрабабушка", KinshipCalculator.calculate(state, "ancestor4", "root", true).firstToSecond);
        assertEquals("праправнучка", KinshipCalculator.calculate(state, "root", "ancestor4", true).firstToSecond);

        // 5 generations (ancestor5 -> root is depth 5)
        assertEquals("прапрапрадедушка", KinshipCalculator.calculate(state, "ancestor5", "root", true).firstToSecond);

        // Sister of great-great-grandmother:
        person(state, "sisterOfPraPra"); state.people.get("sisterOfPraPra").gender = PersonGender.FEMALE;
        state.addRelation("parent", "ancestor5", "sisterOfPraPra");
        assertEquals("сестра прапрабабушки", KinshipCalculator.calculate(state, "sisterOfPraPra", "root", true).firstToSecond);
    }

    @Test public void unknownGenderFallsBackGracefully() {
        TreeState state = new TreeState();
        person(state, "greatGrandparent"); state.people.get("greatGrandparent").gender = PersonGender.UNKNOWN;
        person(state, "grandparent"); state.people.get("grandparent").gender = PersonGender.UNKNOWN;
        person(state, "parent"); state.people.get("parent").gender = PersonGender.UNKNOWN;
        person(state, "sister"); state.people.get("sister").gender = PersonGender.FEMALE;
        person(state, "me");

        state.addRelation("parent", "greatGrandparent", "grandparent");
        state.addRelation("parent", "greatGrandparent", "sister");
        state.addRelation("parent", "grandparent", "parent");
        state.addRelation("parent", "parent", "me");

        assertEquals("сестра дедушки или бабушки", KinshipCalculator.calculate(state, "sister", "me", true).firstToSecond);
        assertEquals("внучка брата или сестры", KinshipCalculator.calculate(state, "me", "sister", true).firstToSecond);
    }

    private void person(TreeState state, String id, String gender) {
        person(state, id);
        state.people.get(id).gender = gender;
    }

    /** Me with a paternal and a maternal side, each with a great-grandparent and a sibling. */
    private TreeState twoSides() {
        TreeState state = new TreeState();
        for (String id : new String[]{"father", "grandpa", "greatGrandpaP", "uncle", "sisterOfGrandpa"}) person(state, id, PersonGender.MALE);
        for (String id : new String[]{"mother", "grandma", "greatGrandmaM", "sisterOfGrandma"}) person(state, id, PersonGender.FEMALE);
        state.people.get("sisterOfGrandpa").gender = PersonGender.FEMALE;
        person(state, "me");
        state.addRelation("parent", "father", "me");
        state.addRelation("parent", "mother", "me");
        state.addRelation("parent", "grandpa", "father");
        state.addRelation("parent", "grandpa", "uncle");
        state.addRelation("parent", "greatGrandpaP", "grandpa");
        state.addRelation("parent", "greatGrandpaP", "sisterOfGrandpa");
        state.addRelation("parent", "grandma", "mother");
        state.addRelation("parent", "greatGrandmaM", "grandma");
        state.addRelation("parent", "greatGrandmaM", "sisterOfGrandma");
        return state;
    }

    @Test public void lineageFollowsTheParentOfTheReference() {
        TreeState state = twoSides();
        KinshipCalculator.Result grandAunt = KinshipCalculator.calculate(state, "sisterOfGrandma", "me", true);
        assertEquals("сестра бабушки", grandAunt.firstToSecond);
        assertEquals("по материнской линии", grandAunt.firstToSecondLineage);
        // The younger side has no line of its own: "внучка сестры" says it already.
        assertEquals("", grandAunt.secondToFirstLineage);
        assertEquals("по отцовской линии", KinshipCalculator.calculate(state, "sisterOfGrandpa", "me", true).firstToSecondLineage);
        assertEquals("по отцовской линии", KinshipCalculator.calculate(state, "uncle", "me", true).firstToSecondLineage);
        assertEquals("по отцовской линии", KinshipCalculator.calculate(state, "greatGrandpaP", "me", true).firstToSecondLineage);
        assertEquals("по материнской линии", KinshipCalculator.calculate(state, "grandma", "me", true).firstToSecondLineage);
        // A parent is not "on a line".
        assertEquals("", KinshipCalculator.calculate(state, "father", "me", true).firstToSecondLineage);
        // An aunt linked to the father only as an explicit sibling is still on his side.
        person(state, "aunt", PersonGender.FEMALE);
        state.addRelation("sibling", "aunt", "father");
        KinshipCalculator.Result aunt = KinshipCalculator.calculate(state, "aunt", "me", true);
        assertEquals("тётя", aunt.firstToSecond);
        assertEquals("по отцовской линии", aunt.firstToSecondLineage);
    }

    @Test public void spouseOfARelativeKeepsTheRelativesLine() {
        TreeState state = twoSides();
        person(state, "auntByMarriage", PersonGender.FEMALE);
        state.addRelation("partner", "uncle", "auntByMarriage");
        KinshipCalculator.Result result = KinshipCalculator.calculate(state, "auntByMarriage", "me", true);
        assertEquals("жена дяди", result.firstToSecond);
        assertEquals("по отцовской линии", result.firstToSecondLineage);
    }

    /** Father's mother and mother's father are siblings, so the grand-aunt is on both sides. */
    private TreeState pedigreeCollapse(boolean reversed) {
        TreeState state = new TreeState();
        person(state, "top", PersonGender.MALE);
        person(state, "grandmaP", PersonGender.FEMALE);
        person(state, "grandpaM", PersonGender.MALE);
        person(state, "grandAunt", PersonGender.FEMALE);
        person(state, "father", PersonGender.MALE);
        person(state, "mother", PersonGender.FEMALE);
        person(state, "me");
        String[][] links = {
            {"top", "grandmaP"}, {"top", "grandpaM"}, {"top", "grandAunt"},
            {"grandmaP", "father"}, {"grandpaM", "mother"},
            {"father", "me"}, {"mother", "me"},
        };
        if (reversed) java.util.Collections.reverse(java.util.Arrays.asList(links));
        for (String[] link : links) state.addRelation("parent", link[0], link[1]);
        return state;
    }

    @Test public void pedigreeCollapseIsStableAndReportsBothLines() {
        KinshipCalculator.Result forward = KinshipCalculator.calculate(pedigreeCollapse(false), "grandAunt", "me", true);
        KinshipCalculator.Result backward = KinshipCalculator.calculate(pedigreeCollapse(true), "grandAunt", "me", true);
        assertEquals(forward.firstToSecond, backward.firstToSecond);
        assertEquals(forward.detail, backward.detail);
        assertEquals("по обеим линиям", forward.firstToSecondLineage);
        assertEquals(forward.firstToSecondLineage, backward.firstToSecondLineage);
        assertEquals(forward.firstToSecond, KinshipCalculator.labelsRelativeTo(pedigreeCollapse(true), "me", true).get("grandAunt"));
    }

    @Test public void chainFollowsBloodRelationNotAShorterMarriageLink() {
        TreeState state = twoSides();
        // Widowed father later married the grandmother's sister: the graph shortcut is one step.
        state.addRelation("partner", "father", "sisterOfGrandma");
        KinshipCalculator.Result result = KinshipCalculator.calculate(state, "sisterOfGrandma", "me", true);
        assertEquals("сестра бабушки", result.firstToSecond);
        assertEquals(java.util.Arrays.asList("sisterOfGrandma", "greatGrandmaM", "grandma", "mother", "me"), result.pathIds);
        assertEquals("Цепочка в дереве: sisterOfGrandma → greatGrandmaM → grandma → mother → me", result.detail);
    }

    @Test public void chainForAncestorsDescendantsAndExplicitSiblings() {
        TreeState state = twoSides();
        assertEquals(java.util.Arrays.asList("greatGrandpaP", "grandpa", "father", "me"),
            KinshipCalculator.calculate(state, "greatGrandpaP", "me", true).pathIds);
        assertEquals(java.util.Arrays.asList("me", "father", "grandpa", "greatGrandpaP"),
            KinshipCalculator.calculate(state, "me", "greatGrandpaP", true).pathIds);
        TreeState explicit = cousins(3, 1, true);
        assertEquals(java.util.Arrays.asList("a3", "a2", "a1", "b1"),
            KinshipCalculator.calculate(explicit, "a3", "b1", true).pathIds);
    }

    @Test public void youngerLinkingRelativeIsTakenAtTheReferenceGeneration() {
        // a1 ~ b1 explicit siblings; a5 is three generations below b3's generation peer a3.
        TreeState state = cousins(5, 3, true);
        state.people.get("a3").gender = PersonGender.MALE;
        assertEquals("внучка троюродного брата", KinshipCalculator.calculate(state, "a5", "b3", true).firstToSecond);
    }

    @Test public void traditionalTermIsGivenOnlyWhereItDiffersFromTheLabel() {
        TreeState state = twoSides();
        KinshipCalculator.Result grandAunt = KinshipCalculator.calculate(state, "sisterOfGrandma", "me", true);
        assertEquals("двоюродная бабушка", grandAunt.firstToSecondTraditional);
        assertEquals("внучатая племянница", grandAunt.secondToFirstTraditional);
        assertEquals("сестра бабушки, по материнской линии", grandAunt.firstToSecondSummary());
        assertEquals("внучка сестры", grandAunt.secondToFirstSummary());
        // "дядя" is already the classic name.
        assertEquals("", KinshipCalculator.calculate(state, "uncle", "me", true).firstToSecondTraditional);
        // Parametric labels get the classic name beside them.
        KinshipCalculator.Result parametric = KinshipCalculator.calculate(cousins(4, 5, false), "a4", "b5", false);
        assertEquals("четвероюродная сестра с разницей в 1 поколение (старшее поколение)", parametric.firstToSecond);
        assertEquals("четвероюродная тётя", parametric.firstToSecondTraditional);
        assertEquals("четвероюродная племянница", parametric.secondToFirstTraditional);
        // A grandparent's cousin is one adjective further: "троюродная бабушка".
        TreeState far = cousins(2, 4, false);
        assertEquals("двоюродная сестра бабушки", KinshipCalculator.calculate(far, "a2", "b4", true).firstToSecond);
        assertEquals("троюродная бабушка", KinshipCalculator.calculate(far, "a2", "b4", true).firstToSecondTraditional);
    }

    @Test public void compactLabelsFitCardTags() {
        assertEquals("4-юродная сестра ↓2 пок.",
            KinshipCalculator.compactLabel("четвероюродная сестра с разницей в 2 поколения (младшее поколение)"));
        assertEquals("двоюродный брат ↑1 пок.",
            KinshipCalculator.compactLabel("двоюродный брат с разницей в 1 поколение (старшее поколение)"));
        assertEquals("сестра дедушки/бабушки", KinshipCalculator.compactLabel("сестра дедушки или бабушки"));
        assertEquals("внучка 4-юродного брата/сестры",
            KinshipCalculator.compactLabel("внучка четвероюродного брата или четвероюродной сестры"));
        assertEquals("супруг(а) брата/сестры", KinshipCalculator.compactLabel("супруг или супруга брата или сестры"));
        assertEquals("сестра бабушки", KinshipCalculator.compactLabel("сестра бабушки"));
    }

    @Test public void searchSummaryNamesTheFamilySide() {
        TreeState state = twoSides();
        state.people.get("sisterOfGrandma").name = "Ольга";
        state.people.get("me").name = "Анна";
        assertTrue(SmartPeopleSearch.parse(state, "Кто Ольга для Анны", "me", true).kinshipSummary
            .endsWith("сестра бабушки, по материнской линии"));
    }

    /**
     * 25 clans that never intermarry, joined only by explicit sibling links, so most pairs
     * are found through sibling ancestors rather than a common ancestor.
     */
    @Test(timeout = 30000) public void cardLabelsOnALargeTreeStayFast() {
        TreeState state = new TreeState();
        java.util.Random random = new java.util.Random(42);
        int perGeneration = 250, generations = 20, clan = 10;
        for (int g = 0; g < generations; g++) {
            for (int i = 0; i < perGeneration; i++) {
                String id = "p" + g + "_" + i;
                person(state, id, i % 2 == 0 ? PersonGender.MALE : PersonGender.FEMALE);
                if (g > 0) {
                    int father = i / clan * clan + random.nextInt(clan / 2) * 2;
                    state.links.add(new Relation("l" + state.links.size(), "parent", "p" + (g - 1) + "_" + father, id));
                    state.links.add(new Relation("l" + state.links.size(), "parent", "p" + (g - 1) + "_" + (father + 1), id));
                }
                if (i % 7 == 3) {
                    state.links.add(new Relation("l" + state.links.size(), "sibling", id, "p" + g + "_" + random.nextInt(i)));
                }
            }
        }
        String reference = "p" + (generations - 1) + "_0";
        long start = System.nanoTime();
        java.util.Map<String, String> labels = KinshipCalculator.labelsRelativeTo(state, reference, true);
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertTrue("only " + labels.size() + " labels", labels.size() > perGeneration);
        assertTrue("labelsRelativeTo took " + millis + " ms", millis < 4000);
    }
}
