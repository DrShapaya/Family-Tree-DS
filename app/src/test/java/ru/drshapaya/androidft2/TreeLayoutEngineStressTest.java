package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class TreeLayoutEngineStressTest {
    private static final float EPSILON = 0.01f;

    @Test(timeout = 10_000L)
    public void simpleTreeRecoversFromEveryBrokenPlacement() {
        exerciseEveryPlacement(descendantTree(2, 2));
    }

    @Test(timeout = 15_000L)
    public void mediumFilledTreeRecoversFromEveryBrokenPlacement() {
        exerciseEveryPlacement(descendantTree(4, 3));
    }

    @Test(timeout = 30_000L)
    public void largeFilledTreeRecoversFromEveryBrokenPlacement() {
        TreeState state = descendantTree(5, 3);
        assertTrue("большой сценарий должен содержать сотни карточек", state.people.size() >= 200);
        exerciseEveryPlacement(state);
    }

    @Test(timeout = 20_000L)
    public void complexRemarriageTreeRecoversFromEveryBrokenPlacement() {
        exerciseEveryPlacement(complexRemarriageTree());
    }

    private static void exerciseEveryPlacement(TreeState source) {
        for (BrokenPlacement placement : BrokenPlacement.values()) {
            TreeState damaged = TreeStateCopier.copy(source);
            applyPlacement(damaged, placement);
            TreeState first = TreeStateCopier.copy(damaged);
            TreeState second = TreeStateCopier.copy(damaged);

            TreeLayoutEngine.ensurePositions(first);
            TreeLayoutEngine.ensurePositions(second);

            assertLayoutInvariants(first, placement.name());
            assertSameLayout(first, second, placement.name());
        }
    }

    private static TreeState descendantTree(int generations, int childrenPerFamily) {
        TreeState state = new TreeState();
        state.workspaceWidth = TreeLayoutEngine.MIN_SURFACE_W;
        state.workspaceHeight = TreeLayoutEngine.MIN_SURFACE_H;

        String firstFather = "g0_f0_father";
        String firstMother = "g0_f0_mother";
        addPerson(state, firstFather, "Основатель", 1900, PersonGender.MALE);
        addPerson(state, firstMother, "Основательница", 1902, PersonGender.FEMALE);
        addLink(state, "partner", firstFather, firstMother);
        state.rootId = firstFather;

        List<Family> families = new ArrayList<>();
        families.add(new Family(firstFather, firstMother));
        int familySerial = 1;
        for (int generation = 1; generation < generations; generation++) {
            List<Family> next = new ArrayList<>();
            int familyIndex = 0;
            for (Family family : families) {
                List<String> siblings = new ArrayList<>();
                for (int childIndex = 0; childIndex < childrenPerFamily; childIndex++) {
                    String prefix = "g" + generation + "_f" + familyIndex + "_c" + childIndex;
                    String childId = prefix + "_person";
                    String partnerId = prefix + "_partner";
                    String childGender = childIndex % 2 == 0 ? PersonGender.MALE : PersonGender.FEMALE;
                    String partnerGender = PersonGender.MALE.equals(childGender)
                        ? PersonGender.FEMALE
                        : PersonGender.MALE;
                    int born = 1900 + generation * 28 + childIndex;
                    addPerson(state, childId, "Человек " + childId, born, childGender);
                    addPerson(state, partnerId, "Партнёр " + partnerId, born + 1, partnerGender);
                    addLink(state, "parent", family.first, childId);
                    addLink(state, "parent", family.second, childId);
                    addLink(state, "partner", childId, partnerId);
                    siblings.add(childId);
                    next.add(new Family(childId, partnerId));
                    familySerial++;
                }
                connectSiblings(state, siblings);
                familyIndex++;
            }
            families = next;
        }
        assertTrue(familySerial > 1);
        return state;
    }

    private static TreeState complexRemarriageTree() {
        TreeState state = descendantTree(4, 2);
        String remarried = "g1_f0_c0_person";
        String secondPartner = "second_partner";
        addPerson(state, secondPartner, "Вторая супруга", 1931, PersonGender.FEMALE);
        addLink(state, "partner", remarried, secondPartner);

        List<String> secondMarriageChildren = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String child = "second_marriage_child_" + i;
            addPerson(state, child, "Ребёнок второго брака " + i, 1958 + i, i == 1
                ? PersonGender.FEMALE
                : PersonGender.MALE);
            addLink(state, "parent", remarried, child);
            addLink(state, "parent", secondPartner, child);
            secondMarriageChildren.add(child);
        }
        connectSiblings(state, secondMarriageChildren);

        String secondPartnerFather = "second_partner_father";
        String secondPartnerMother = "second_partner_mother";
        addPerson(state, secondPartnerFather, "Отец второй супруги", 1901, PersonGender.MALE);
        addPerson(state, secondPartnerMother, "Мать второй супруги", 1903, PersonGender.FEMALE);
        addLink(state, "partner", secondPartnerFather, secondPartnerMother);
        addLink(state, "parent", secondPartnerFather, secondPartner);
        addLink(state, "parent", secondPartnerMother, secondPartner);

        String sideSibling = "second_partner_sibling";
        String sideSiblingPartner = "second_partner_sibling_partner";
        addPerson(state, sideSibling, "Сестра второй супруги", 1934, PersonGender.FEMALE);
        addPerson(state, sideSiblingPartner, "Муж сестры", 1932, PersonGender.MALE);
        addLink(state, "parent", secondPartnerFather, sideSibling);
        addLink(state, "parent", secondPartnerMother, sideSibling);
        addLink(state, "partner", sideSiblingPartner, sideSibling);
        addLink(state, "sibling", secondPartner, sideSibling);
        return state;
    }

    private static void applyPlacement(TreeState state, BrokenPlacement placement) {
        if (placement == BrokenPlacement.ORDERED) {
            TreeLayoutEngine.layout(state);
            return;
        }
        int index = 0;
        for (Person person : state.people.values()) {
            if (placement == BrokenPlacement.NONE) {
                person.x = Float.NaN;
                person.y = Float.NaN;
            } else if (placement == BrokenPlacement.ONE_ROW) {
                person.x = 200f + index * (TreeLayoutEngine.CARD_W + TreeLayoutEngine.GRID);
                person.y = 1200f;
            } else if (placement == BrokenPlacement.ONE_COLUMN) {
                person.x = 1200f;
                person.y = 200f + index * (TreeLayoutEngine.CARD_H + TreeLayoutEngine.GRID);
            } else if (placement == BrokenPlacement.STACK) {
                person.x = 1200f;
                person.y = 1200f;
            } else if (placement == BrokenPlacement.PARTIAL) {
                person.x = index % 3 == 0 ? 900f + ((index * 97) % 640) : Float.NaN;
                person.y = index % 3 == 0 ? 900f + ((index * 131) % 520) : Float.NaN;
            } else {
                person.x = 900f + ((index * 97) % 640);
                person.y = 900f + ((index * 131) % 520);
            }
            index++;
        }
    }

    private static void assertLayoutInvariants(TreeState state, String scenario) {
        List<Person> people = new ArrayList<>(state.people.values());
        for (Person person : people) {
            assertTrue(scenario + ": координата X должна быть конечной", Float.isFinite(person.x));
            assertTrue(scenario + ": координата Y должна быть конечной", Float.isFinite(person.y));
            assertEquals(scenario + ": X должен быть на сетке", 0f, person.x % TreeLayoutEngine.GRID, EPSILON);
            assertEquals(scenario + ": Y должен быть на сетке", 0f, person.y % TreeLayoutEngine.GRID, EPSILON);
            assertTrue(scenario + ": карточка вышла за левую границу", person.x >= 0f);
            assertTrue(scenario + ": карточка вышла за верхнюю границу", person.y >= 0f);
            assertTrue(
                scenario + ": карточка вышла за правую границу",
                person.x + TreeLayoutEngine.CARD_W <= state.workspaceWidth + EPSILON);
            assertTrue(
                scenario + ": карточка вышла за нижнюю границу",
                person.y + TreeLayoutEngine.CARD_H <= state.workspaceHeight + EPSILON);
        }

        for (int i = 0; i < people.size(); i++) {
            Person first = people.get(i);
            for (int j = i + 1; j < people.size(); j++) {
                Person second = people.get(j);
                boolean horizontalOverlap = Math.abs(first.x - second.x) < TreeLayoutEngine.CARD_W;
                boolean verticalOverlap = Math.abs(first.y - second.y) < TreeLayoutEngine.CARD_H;
                assertTrue(
                    scenario + ": пересечение " + first.id + " и " + second.id,
                    !horizontalOverlap || !verticalOverlap);
            }
        }

        for (Relation relation : state.links) {
            Person from = state.people.get(relation.from);
            Person to = state.people.get(relation.to);
            if (from == null || to == null) continue;
            if ("parent".equals(relation.type)) {
                assertEquals(
                    scenario + ": ребёнок должен быть на поколение ниже родителя",
                    TreeLayoutEngine.LEVEL_GAP,
                    to.y - from.y,
                    EPSILON);
            } else if ("partner".equals(relation.type) || "family".equals(relation.type)) {
                assertEquals(scenario + ": партнёры должны быть в одной строке", from.y, to.y, EPSILON);
            } else if ("sibling".equals(relation.type)) {
                assertEquals(scenario + ": братья должны быть в одной строке", from.y, to.y, EPSILON);
            }
        }
    }

    private static void assertSameLayout(TreeState first, TreeState second, String scenario) {
        assertEquals(scenario + ": ширина должна быть детерминированной", first.workspaceWidth, second.workspaceWidth);
        assertEquals(scenario + ": высота должна быть детерминированной", first.workspaceHeight, second.workspaceHeight);
        for (String id : first.people.keySet()) {
            Person a = first.people.get(id);
            Person b = second.people.get(id);
            assertEquals(scenario + ": недетерминированный X для " + id, a.x, b.x, EPSILON);
            assertEquals(scenario + ": недетерминированный Y для " + id, a.y, b.y, EPSILON);
        }
    }

    private static void addPerson(TreeState state, String id, String name, int year, String gender) {
        Person person = new Person(id);
        person.name = name;
        person.bornYear = Integer.toString(year);
        person.gender = gender;
        person.genderManual = true;
        state.people.put(id, person);
    }

    private static void addLink(TreeState state, String type, String from, String to) {
        state.links.add(new Relation(type + "_" + from + "_" + to, type, from, to));
    }

    private static void connectSiblings(TreeState state, List<String> siblings) {
        for (int first = 0; first < siblings.size(); first++) {
            for (int second = first + 1; second < siblings.size(); second++) {
                addLink(state, "sibling", siblings.get(first), siblings.get(second));
            }
        }
    }

    private enum BrokenPlacement {
        ORDERED,
        NONE,
        ONE_ROW,
        ONE_COLUMN,
        STACK,
        PARTIAL,
        HEAP
    }

    private static final class Family {
        final String first;
        final String second;

        Family(String first, String second) {
            this.first = first;
            this.second = second;
        }
    }
}
