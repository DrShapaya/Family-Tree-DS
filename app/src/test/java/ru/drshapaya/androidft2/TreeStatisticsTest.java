package ru.drshapaya.androidft2;

import org.junit.Test;
import static org.junit.Assert.*;

public class TreeStatisticsTest {
    private TreeState tree(String... ids) {
        TreeState state = new TreeState();
        for (String id : ids) { Person p = new Person(id); p.name = id; state.people.put(id, p); }
        return state;
    }
    private void years(TreeState state, String id, String birth, String death) {
        state.people.get(id).bornYear = birth; state.people.get(id).diedYear = death;
    }

    @Test public void distinguishesBloodFamilyAndUnknownConnections() {
        TreeState s = tree("me", "parent", "grandparent", "sibling", "aunt", "cousin", "child", "coparent", "spouse", "stranger");
        s.addRelation("parent", "grandparent", "parent"); s.addRelation("parent", "grandparent", "aunt");
        s.addRelation("parent", "parent", "me"); s.addRelation("parent", "parent", "sibling");
        s.addRelation("parent", "aunt", "cousin"); s.addRelation("parent", "me", "child");
        s.addRelation("parent", "coparent", "child"); s.addRelation("partner", "me", "spouse");
        TreeStatistics x = TreeStatistics.calculate(s, "me");
        assertEquals(10, x.people); assertEquals(4, x.closeRelatives); assertEquals(2, x.distantRelatives);
        assertEquals(2, x.familyConnections); assertEquals(1, x.unrelated);
        assertEquals(x.people, 1 + x.closeRelatives + x.distantRelatives + x.familyConnections + x.unrelated);
    }

    @Test public void explicitSiblingsDoNotMakeHalfSiblingRelationshipsTransitive() {
        TreeState s = tree("me", "sibling", "theirHalfSibling", "niece");
        s.addRelation("sibling", "me", "sibling"); s.addRelation("sibling", "sibling", "theirHalfSibling");
        s.addRelation("parent", "sibling", "niece");
        TreeStatistics x = TreeStatistics.calculate(s, "me");
        assertEquals(1, x.closeRelatives); assertEquals(1, x.distantRelatives); assertEquals(1, x.familyConnections);
    }

    @Test public void countsUniqueExistingChildrenAndKeepsFractionalMean() {
        TreeState s = tree("a", "b", "c", "d");
        s.links.add(new Relation("1", "parent", "a", "c"));
        s.links.add(new Relation("2", "parent", "a", "c"));
        s.links.add(new Relation("3", "parent", "a", "missing"));
        s.links.add(new Relation("4", "parent", "a", "d"));
        s.links.add(new Relation("5", "parent", "b", "c"));
        s.links.add(new Relation("6", "parent", "a", "a"));
        TreeStatistics x = TreeStatistics.calculate(s, "a");
        assertEquals(2, x.parentSamples); assertEquals(1.5, x.averageChildren, 0.001); assertEquals(2, x.maximumChildren);
    }

    @Test public void agesUseCompletedBirthdaysAndMarkPartialDates() {
        TreeState s = tree("a", "b", "alive");
        years(s, "a", "1940", "2000"); years(s, "b", "1950", "2010"); years(s, "alive", "1980", "");
        Person a = s.people.get("a"); a.bornMonth = "12"; a.bornDay = "25"; a.diedMonth = "01"; a.diedDay = "01";
        TreeStatistics x = TreeStatistics.calculate(s, "a");
        assertEquals(2, x.lifeSamples); assertEquals(1, x.approximateLifeSamples);
        assertEquals(59.5, x.averageLife, 0.001); assertEquals(60, x.maximumLife);
    }

    @Test public void invalidReversedAndFutureDatesAreExcluded() {
        TreeState s = tree("invalid", "reversed", "future", "unknown", "zeroAge");
        years(s, "invalid", "1950", "2000"); s.people.get("invalid").bornMonth = "02"; s.people.get("invalid").bornDay = "30";
        years(s, "reversed", "2000", "1900"); years(s, "future", "2000", "9999"); years(s, "unknown", "ABT 1900", "1980");
        years(s, "zeroAge", "2000", "2000");
        TreeStatistics x = TreeStatistics.calculate(s, "invalid");
        assertEquals(1, x.lifeSamples); assertEquals(0, x.maximumLife);
    }

    @Test public void incompleteDeathDateCanOverlapBirthDateInSameYear() {
        TreeState s = tree("infant");
        years(s, "infant", "1900", "1900");
        s.people.get("infant").bornMonth = "12"; s.people.get("infant").bornDay = "10";
        TreeStatistics x = TreeStatistics.calculate(s, "infant");
        assertEquals(1, x.lifeSamples); assertEquals(1, x.approximateLifeSamples); assertEquals(0, x.maximumLife);
    }

    @Test public void chartBucketsPartitionEveryPersonAndKeepUnknownRecords() {
        TreeState s=tree("a","b","c","d","e");
        s.people.get("a").gender=PersonGender.MALE;s.people.get("b").gender=PersonGender.FEMALE;
        s.people.get("a").photo="data:image/png;base64,YQ==";s.people.get("b").photoMediaId="portrait";
        s.people.get("a").notes="История";
        years(s,"a","1899","");years(s,"b","1949","");years(s,"c","1999","");years(s,"d","2000","");years(s,"e","9999","");
        s.addRelation("parent","a","b");s.addRelation("parent","a","c");s.addRelation("parent","c","d");
        TreeStatistics x=TreeStatistics.calculate(s,"a");
        assertArrayEquals(new int[]{1,1,3},x.genders);
        assertArrayEquals(new int[]{1,1,1,1,1},x.birthPeriods);
        assertArrayEquals(new int[]{3,1,1,0,0},x.childrenDistribution);
        assertArrayEquals(new int[]{2,3},x.photos);
        assertEquals(1,x.peopleWithNotes);assertEquals(1,x.isolatedPeople);
    }

    @Test public void emptyTreeAndMissingReferenceAreSafe() {
        assertEquals(0, TreeStatistics.calculate(null, null).people);
        TreeStatistics x = TreeStatistics.calculate(tree("a", "b"), "missing");
        assertFalse(x.hasReference); assertEquals(2, x.unrelated);
        assertEquals(0, x.lifeSamples); assertEquals(0, x.parentSamples);
    }

    @Test public void cyclicMalformedGraphTerminatesWithoutCountingReferenceTwice() {
        TreeState s = tree("a", "b", "c");
        s.links.add(new Relation("1", "parent", "a", "b"));
        s.links.add(new Relation("2", "parent", "b", "c"));
        s.links.add(new Relation("3", "parent", "c", "a"));
        TreeStatistics x = TreeStatistics.calculate(s, "a");
        assertEquals(2, x.closeRelatives); assertEquals(0, x.distantRelatives); assertEquals(0, x.unrelated);
    }
}
