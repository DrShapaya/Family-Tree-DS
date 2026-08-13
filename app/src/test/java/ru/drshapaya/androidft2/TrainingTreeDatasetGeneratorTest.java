package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Reproducible, step-by-step generator for the first solver training dataset. */
public final class TrainingTreeDatasetGeneratorTest {
    private static final String OUTPUT_ENV = "ANDROIDFT_TRAINING_OUTPUT";
    private static final long ZIP_TIME = 1786255200000L;
    private static final String EXPORTED_AT = "2026-08-09T13:00:00.000+0700";
    private static final String[] COLORS = {
        "#F2D16B", "#84C7AE", "#83ADDF", "#E99D8F", "#BBA6DE",
        "#F0A85F", "#73C0D4", "#A5C96F", "#DF8EB6", "#94A8E6"
    };

    @Test
    public void generateAndValidateTrainingDataset() throws Exception {
        List<Scenario> scenarios = scenarios();
        assertEquals(10, scenarios.size());
        Path output = outputDirectory();
        JSONArray index = new JSONArray();
        int previousCount = 0;
        for (Scenario scenario : scenarios) {
            TreeState state = scenario.factory.create();
            validateState(scenario, state);
            assertTrue("сложность должна возрастать", state.people.size() > previousCount);
            previousCount = state.people.size();
            if (output != null) {
                Files.createDirectories(output);
                Path file = output.resolve(scenario.fileName);
                writePackage(file, state);
                validatePackage(file, scenario.expectedPeople, state.links.size());
                index.put(new JSONObject()
                    .put("id", scenario.id)
                    .put("file", scenario.fileName)
                    .put("title", scenario.title)
                    .put("people", state.people.size())
                    .put("links", state.links.size())
                    .put("description", scenario.description)
                    .put("construction", "stepwise-layoutAfterAddition"));
            }
        }
        assertTrue(previousCount <= 40);
        if (output != null) {
            JSONObject document = new JSONObject()
                .put("format", "androidft-smart-layout-training-index")
                .put("version", 1)
                .put("generatedAt", EXPORTED_AT)
                .put("generator", getClass().getName())
                .put("scenarios", index);
            Files.write(
                output.resolve("dataset-index.json"),
                document.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static List<Scenario> scenarios() {
        return Arrays.asList(
            new Scenario("01", "01_basic_generations__07_people.ftree",
                "Базовые три поколения", 7,
                "Основная пара, родители обеих сторон и один ребёнок.",
                TrainingTreeDatasetGeneratorTest::scenario01),
            new Scenario("02", "02_many_simple_siblings__10_people.ftree",
                "Много простых братьев", 10,
                "Трое боковых братьев без семей и трое детей основной пары.",
                TrainingTreeDatasetGeneratorTest::scenario02),
            new Scenario("03", "03_sibling_families__14_people.ftree",
                "Боковые семьи", 14,
                "Два брата с партнёрами и ветвями разной ширины.",
                TrainingTreeDatasetGeneratorTest::scenario03),
            new Scenario("04", "04_symmetric_ancestry__15_people.ftree",
                "Симметричные предки", 15,
                "Четыре одинаковые родительские ветви и один ребёнок.",
                TrainingTreeDatasetGeneratorTest::scenario04),
            new Scenario("05", "05_multiple_partners__18_people.ftree",
                "Несколько партнёров", 18,
                "Два партнёра, дети от разных союзов и семейная ветвь брата.",
                TrainingTreeDatasetGeneratorTest::scenario05),
            new Scenario("06", "06_father_many_brothers__24_people.ftree",
                "Много братьев у отца", 24,
                "Четыре брата отца, у каждого партнёр и дети разной ширины.",
                TrainingTreeDatasetGeneratorTest::scenario06),
            new Scenario("07", "07_asymmetric_wide_branches__28_people.ftree",
                "Асимметричные широкие ветви", 28,
                "По обе стороны основной пары несколько братьев с семьями.",
                TrainingTreeDatasetGeneratorTest::scenario07),
            new Scenario("08", "08_half_siblings_and_marriages__31_people.ftree",
                "Сводные братья и браки", 31,
                "Дети от разных партнёров и вложенные боковые семейные ветви.",
                TrainingTreeDatasetGeneratorTest::scenario08),
            new Scenario("09", "09_deep_six_generation_tree__35_people.ftree",
                "Глубокое дерево", 35,
                "До шести поколений с боковыми семьями на нескольких уровнях.",
                TrainingTreeDatasetGeneratorTest::scenario09),
            new Scenario("10", "10_complex_balanced_tree__40_people.ftree",
                "Сложное дерево на 40 человек", 40,
                "Симметричная основа, широкие семьи братьев и боковая ветвь предков.",
                TrainingTreeDatasetGeneratorTest::scenario10));
    }

    private static TreeState scenario01() {
        Builder b = new Builder("s01");
        String root = b.root("root");
        String partner = b.partner(root, "partner");
        b.parents(root, "root_parents");
        b.parents(partner, "partner_parents");
        b.child(root, partner, "child");
        return b.state;
    }

    private static TreeState scenario02() {
        Builder b = new Builder("s02");
        String root = b.root("root");
        String[] parents = b.parents(root, "parents");
        b.sibling(root, "simple_sibling_1");
        b.sibling(root, "simple_sibling_2");
        b.sibling(root, "simple_sibling_3");
        String partner = b.partner(root, "partner");
        b.child(root, partner, "child_1");
        b.child(root, partner, "child_2");
        b.child(root, partner, "child_3");
        assertFalse(parents[0].isEmpty());
        return b.state;
    }

    private static TreeState scenario03() {
        Builder b = new Builder("s03");
        String root = b.root("root");
        b.parents(root, "parents");
        String siblingA = b.sibling(root, "sibling_a");
        String siblingB = b.sibling(root, "sibling_b");
        String partnerA = b.partner(siblingA, "sibling_a_partner");
        String partnerB = b.partner(siblingB, "sibling_b_partner");
        b.child(siblingA, partnerA, "a_child_1");
        b.child(siblingA, partnerA, "a_child_2");
        b.child(siblingB, partnerB, "b_child_1");
        b.child(siblingB, partnerB, "b_child_2");
        b.child(siblingB, partnerB, "b_child_3");
        String partner = b.partner(root, "root_partner");
        b.child(root, partner, "root_child");
        return b.state;
    }

    private static TreeState scenario04() {
        Builder b = new Builder("s04");
        String root = b.root("root");
        String partner = b.partner(root, "partner");
        String[] rootParents = b.parents(root, "root_parents");
        String[] partnerParents = b.parents(partner, "partner_parents");
        b.parents(rootParents[0], "root_left_grandparents");
        b.parents(rootParents[1], "root_right_grandparents");
        b.parents(partnerParents[0], "partner_left_grandparents");
        b.parents(partnerParents[1], "partner_right_grandparents");
        b.child(root, partner, "child");
        return b.state;
    }

    private static TreeState scenario05() {
        Builder b = new Builder("s05");
        String root = b.root("root");
        String partner1 = b.partner(root, "partner_1");
        String partner2 = b.partner(root, "partner_2");
        b.parents(root, "root_parents");
        b.parents(partner1, "partner_1_parents");
        b.parents(partner2, "partner_2_parents");
        b.child(root, partner1, "union_1_child_1");
        b.child(root, partner1, "union_1_child_2");
        b.child(root, partner1, "union_1_child_3");
        b.child(root, partner2, "union_2_child_1");
        b.child(root, partner2, "union_2_child_2");
        String sibling = b.sibling(root, "root_sibling");
        String siblingPartner = b.partner(sibling, "root_sibling_partner");
        b.child(sibling, siblingPartner, "sibling_child_1");
        b.child(sibling, siblingPartner, "sibling_child_2");
        return b.state;
    }

    private static TreeState scenario06() {
        Builder b = new Builder("s06");
        String root = b.root("root");
        String partner = b.partner(root, "partner");
        b.parents(root, "root_parents");
        String[] partnerParents = b.parents(partner, "partner_parents");
        String father = partnerParents[0];
        for (int index = 1; index <= 4; index++) {
            // Keep stable IDs so existing corrected training pairs remain comparable;
            // only the human-readable role was wrong in the first generated dataset.
            String brother = b.sibling(father, "grandfather_brother_" + index);
            b.renameRole(brother, "father_brother_" + index);
            String brotherPartner = b.partner(brother, "grandfather_brother_partner_" + index);
            b.renameRole(brotherPartner, "father_brother_partner_" + index);
            int childCount = index <= 2 ? 2 : 3;
            for (int child = 1; child <= childCount; child++) {
                b.child(brother, brotherPartner, "branch_" + index + "_child_" + child);
            }
        }
        return b.state;
    }

    private static TreeState scenario07() {
        Builder b = new Builder("s07");
        String root = b.root("root");
        String partner = b.partner(root, "partner");
        b.parents(root, "root_parents");
        b.parents(partner, "partner_parents");
        List<String> rootSiblings = new ArrayList<>();
        for (int index = 1; index <= 4; index++) {
            rootSiblings.add(b.sibling(root, "root_sibling_" + index));
        }
        String rsPartner1 = b.partner(rootSiblings.get(0), "root_sibling_1_partner");
        String rsPartner2 = b.partner(rootSiblings.get(1), "root_sibling_2_partner");
        for (int child = 1; child <= 3; child++) {
            b.child(rootSiblings.get(0), rsPartner1, "root_branch_1_child_" + child);
        }
        for (int child = 1; child <= 4; child++) {
            b.child(rootSiblings.get(1), rsPartner2, "root_branch_2_child_" + child);
        }
        for (int index = 1; index <= 3; index++) {
            String sibling = b.sibling(partner, "partner_sibling_" + index);
            String siblingPartner = b.partner(sibling, "partner_sibling_partner_" + index);
            b.child(sibling, siblingPartner, "partner_branch_" + index + "_child");
        }
        return b.state;
    }

    private static TreeState scenario08() {
        Builder b = new Builder("s08");
        String root = b.root("root");
        String[] parents = b.parents(root, "root_parents");
        String parentA = parents[0];
        String parentB = parents[1];
        String parentANewPartner = b.partner(parentA, "parent_a_second_partner");
        String fullSibling1 = b.sibling(root, "full_sibling_1");
        String fullSibling2 = b.sibling(root, "full_sibling_2");
        String halfSibling1 = b.child(parentA, parentANewPartner, "half_sibling_1");
        b.child(parentA, parentANewPartner, "half_sibling_2");
        b.child(parentA, parentANewPartner, "half_sibling_3");
        String rootPartner = b.partner(root, "root_partner");
        b.child(root, rootPartner, "root_child_1");
        b.child(root, rootPartner, "root_child_2");
        b.child(root, rootPartner, "root_child_3");
        b.parents(rootPartner, "root_partner_parents");
        b.parents(parentA, "parent_a_parents");
        b.parents(parentB, "parent_b_parents");
        b.parents(parentANewPartner, "second_partner_parents");
        String fullPartner1 = b.partner(fullSibling1, "full_sibling_1_partner");
        String fullPartner2 = b.partner(fullSibling2, "full_sibling_2_partner");
        b.child(fullSibling1, fullPartner1, "full_branch_1_child_1");
        b.child(fullSibling1, fullPartner1, "full_branch_1_child_2");
        b.child(fullSibling2, fullPartner2, "full_branch_2_child_1");
        b.child(fullSibling2, fullPartner2, "full_branch_2_child_2");
        b.child(fullSibling2, fullPartner2, "full_branch_2_child_3");
        String halfPartner = b.partner(halfSibling1, "half_sibling_partner");
        b.child(halfSibling1, halfPartner, "half_branch_child_1");
        b.child(halfSibling1, halfPartner, "half_branch_child_2");
        return b.state;
    }

    private static TreeState scenario09() {
        Builder b = new Builder("s09");
        String root = b.root("root");
        String partner = b.partner(root, "partner");
        String[] rootParents = b.parents(root, "root_parents");
        String[] partnerParents = b.parents(partner, "partner_parents");
        List<String> grandparents = new ArrayList<>();
        grandparents.addAll(Arrays.asList(b.parents(rootParents[0], "rp0_parents")));
        grandparents.addAll(Arrays.asList(b.parents(rootParents[1], "rp1_parents")));
        grandparents.addAll(Arrays.asList(b.parents(partnerParents[0], "pp0_parents")));
        grandparents.addAll(Arrays.asList(b.parents(partnerParents[1], "pp1_parents")));
        b.parents(grandparents.get(0), "deep_branch_1");
        b.parents(grandparents.get(2), "deep_branch_2");
        b.parents(grandparents.get(4), "deep_branch_3");
        b.parents(grandparents.get(6), "deep_branch_4");
        String sibling1 = b.sibling(root, "root_sibling_1");
        String sibling2 = b.sibling(root, "root_sibling_2");
        String siblingPartner1 = b.partner(sibling1, "root_sibling_1_partner");
        String siblingPartner2 = b.partner(sibling2, "root_sibling_2_partner");
        b.child(sibling1, siblingPartner1, "side_1_child_1");
        b.child(sibling1, siblingPartner1, "side_1_child_2");
        b.child(sibling2, siblingPartner2, "side_2_child_1");
        b.child(sibling2, siblingPartner2, "side_2_child_2");
        b.child(sibling2, siblingPartner2, "side_2_child_3");
        String ancestorSibling1 = b.sibling(grandparents.get(0), "ancestor_sibling_1");
        b.sibling(grandparents.get(0), "ancestor_sibling_2");
        String ancestorPartner = b.partner(ancestorSibling1, "ancestor_sibling_partner");
        b.child(ancestorSibling1, ancestorPartner, "ancestor_side_child");
        return b.state;
    }

    private static TreeState scenario10() {
        Builder b = new Builder("s10");
        String root = b.root("root");
        String partner = b.partner(root, "partner");
        String[] rootParents = b.parents(root, "root_parents");
        String[] partnerParents = b.parents(partner, "partner_parents");
        b.parents(rootParents[0], "root_left_grandparents");
        b.parents(rootParents[1], "root_right_grandparents");
        String[] partnerLeftGrandparents = b.parents(
            partnerParents[0],
            "partner_left_grandparents");
        b.parents(partnerParents[1], "partner_right_grandparents");
        b.child(root, partner, "main_child");
        for (int index = 1; index <= 3; index++) {
            String sibling = b.sibling(root, "root_sibling_" + index);
            String siblingPartner = b.partner(sibling, "root_sibling_partner_" + index);
            int children = index == 1 ? 3 : 2;
            for (int child = 1; child <= children; child++) {
                b.child(sibling, siblingPartner, "root_side_" + index + "_child_" + child);
            }
        }
        for (int index = 1; index <= 2; index++) {
            String sibling = b.sibling(partner, "partner_sibling_" + index);
            String siblingPartner = b.partner(sibling, "partner_sibling_partner_" + index);
            int children = index == 1 ? 3 : 2;
            for (int child = 1; child <= children; child++) {
                b.child(sibling, siblingPartner, "partner_side_" + index + "_child_" + child);
            }
        }
        String ancestorSibling1 = b.sibling(
            partnerLeftGrandparents[0],
            "upper_ancestor_sibling_1");
        b.sibling(partnerLeftGrandparents[0], "upper_ancestor_sibling_2");
        b.partner(ancestorSibling1, "upper_ancestor_sibling_partner");
        return b.state;
    }

    private static void validateState(Scenario scenario, TreeState state) {
        assertEquals(scenario.title, scenario.expectedPeople, state.people.size());
        assertTrue(state.people.containsKey(state.rootId));
        assertTrue(state.people.containsKey(state.selectedId));
        assertTrue(state.autoArrangeOnAdd);
        assertTrue(state.people.size() <= 40);
        Set<String> relationIds = new LinkedHashSet<>();
        for (Relation relation : state.links) {
            assertTrue(relationIds.add(relation.id));
            assertTrue(state.people.containsKey(relation.from));
            assertTrue(state.people.containsKey(relation.to));
            assertFalse(relation.from.equals(relation.to));
        }
        for (Person person : state.people.values()) {
            assertTrue(Float.isFinite(person.x));
            assertTrue(Float.isFinite(person.y));
            assertTrue(person.x >= 0f);
            assertTrue(person.y >= 0f);
            assertEquals(0f, person.x % TreeLayoutEngine.GRID, 0.01f);
            assertEquals(0f, person.y % TreeLayoutEngine.GRID, 0.01f);
        }
    }

    private static void writePackage(Path file, TreeState state) throws Exception {
        byte[] tree = treeJson(state).toString(2).getBytes(StandardCharsets.UTF_8);
        byte[] manifest = new JSONObject()
            .put("format", "ru.drshapaya.familytree")
            .put("containerVersion", 2)
            .put("appVersion", "2.6.5")
            .put("mode", "copy")
            .put("createdAt", ZIP_TIME)
            .put("tree", "tree.json")
            .put("mediaCount", 0)
            .toString()
            .getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = Files.newOutputStream(file);
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            writeEntry(zip, "manifest.json", manifest);
            writeEntry(zip, "tree.json", tree);
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] data)
        throws Exception {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(ZIP_TIME);
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    private static JSONObject treeJson(TreeState state) throws Exception {
        JSONObject people = new JSONObject();
        JSONObject positions = new JSONObject();
        int index = 0;
        for (Person person : state.people.values()) {
            people.put(person.id, new JSONObject()
                .put("id", person.id)
                .put("name", person.name)
                .put("born", "")
                .put("died", "")
                .put("bornDay", "")
                .put("bornMonth", "")
                .put("bornYear", "")
                .put("diedDay", "")
                .put("diedMonth", "")
                .put("diedYear", "")
                .put("place", "")
                .put("notes", person.notes)
                .put("photoId", "")
                .put("photo", "")
                .put("gender", "unknown")
                .put("genderManual", false)
                .put("memories", new JSONArray())
                .put("pinned", false)
                .put("colorMode", "auto-surname")
                .put("manualColor", COLORS[index++ % COLORS.length]));
            positions.put(person.id, new JSONObject().put("x", person.x).put("y", person.y));
        }
        JSONArray links = new JSONArray();
        for (Relation relation : state.links) {
            links.put(new JSONObject()
                .put("id", relation.id)
                .put("type", relation.type)
                .put("from", relation.from)
                .put("to", relation.to)
                .put("side", "left".equals(relation.side) ? "left" : "right"));
        }
        JSONObject settings = new JSONObject()
            .put("theme", "light")
            .put("printScale", 100)
            .put("editLocked", false)
            .put("historyHidden", true)
            .put("inspectorHidden", false)
            .put("adminCollapsed", false)
            .put("readerMode", false)
            .put("onboardingCompleted", true)
            .put("onboardingOffered", true)
            .put("guidesVisible", true)
            .put("hideCardDetails", false)
            .put("compactCards", false)
            .put("focusTree", false)
            .put("autoArrangeOnAdd", true)
            .put("workspaceBoundsVisible", true)
            .put("workspaceBoundsStyle", "soft")
            .put("workspaceWidth", state.workspaceWidth)
            .put("workspaceHeight", state.workspaceHeight)
            .put("parentLineMode", "smart");
        return new JSONObject()
            .put("format", "ru.drshapaya.familytree.ftree")
            .put("version", 2)
            .put("exportedAt", EXPORTED_AT)
            .put("mode", "copy")
            .put("rootId", state.rootId)
            .put("selectedId", state.selectedId)
            .put("people", people)
            .put("positions", positions)
            .put("links", links)
            .put("guides", new JSONArray())
            .put("settings", settings)
            .put("history", new JSONArray());
    }

    private static void validatePackage(Path file, int people, int links) throws Exception {
        assertTrue(Files.isRegularFile(file));
        try (ZipFile zip = new ZipFile(file.toFile(), StandardCharsets.UTF_8)) {
            assertTrue(zip.getEntry("manifest.json") != null);
            assertTrue(zip.getEntry("tree.json") != null);
            JSONObject manifest = new JSONObject(new String(
                zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes(),
                StandardCharsets.UTF_8));
            JSONObject tree = new JSONObject(new String(
                zip.getInputStream(zip.getEntry("tree.json")).readAllBytes(),
                StandardCharsets.UTF_8));
            assertEquals(2, manifest.getInt("containerVersion"));
            assertEquals(people, tree.getJSONObject("people").length());
            assertEquals(people, tree.getJSONObject("positions").length());
            assertEquals(links, tree.getJSONArray("links").length());
        }
    }

    private static Path outputDirectory() {
        String value = System.getenv(OUTPUT_ENV);
        return value == null || value.trim().isEmpty() ? null : Paths.get(value.trim());
    }

    private interface ScenarioFactory {
        TreeState create();
    }

    private static final class Scenario {
        final String id;
        final String fileName;
        final String title;
        final int expectedPeople;
        final String description;
        final ScenarioFactory factory;

        Scenario(
            String id,
            String fileName,
            String title,
            int expectedPeople,
            String description,
            ScenarioFactory factory
        ) {
            this.id = id;
            this.fileName = fileName;
            this.title = title;
            this.expectedPeople = expectedPeople;
            this.description = description;
            this.factory = factory;
        }
    }

    private static final class Builder {
        final TreeState state = new TreeState();
        final String prefix;
        int linkIndex = 1;

        Builder(String prefix) {
            this.prefix = prefix;
            state.autoArrangeOnAdd = true;
            state.onboardingCompleted = true;
            state.onboardingOffered = true;
        }

        String root(String role) {
            String id = add(role, 11880f, 7920f);
            state.rootId = id;
            state.selectedId = id;
            return id;
        }

        String partner(String anchorId, String role) {
            Person anchor = state.people.get(anchorId);
            String id = add(role, anchor.x + 320f, anchor.y);
            link("partner", anchorId, id, "right");
            arrange(Collections.singleton(id), anchorId, "partner");
            return id;
        }

        String[] parents(String childId, String rolePrefix) {
            Person child = state.people.get(childId);
            String first = add(rolePrefix + "_1", child.x - 160f, child.y - 480f);
            String second = add(rolePrefix + "_2", child.x + 160f, child.y - 480f);
            link("parent", first, childId, "right");
            link("parent", second, childId, "right");
            link("partner", first, second, "right");
            arrange(Arrays.asList(first, second), childId, "parents");
            return new String[]{first, second};
        }

        String sibling(String anchorId, String role) {
            Person anchor = state.people.get(anchorId);
            Set<String> parents = parentsOf(anchorId);
            String id = add(role, anchor.x + 320f, anchor.y);
            for (String parentId : parents) link("parent", parentId, id, "right");
            for (String existingId : new ArrayList<>(state.people.keySet())) {
                if (existingId.equals(id)) continue;
                if (!parents.isEmpty() && parents.equals(parentsOf(existingId))) {
                    link("sibling", existingId, id, "right");
                }
            }
            if (parents.isEmpty()) link("sibling", anchorId, id, "right");
            arrange(Collections.singleton(id), anchorId, "siblings");
            return id;
        }

        String child(String parentA, String parentB, String role) {
            Person anchor = state.people.get(parentA);
            String id = add(role, anchor.x, anchor.y + 480f);
            link("parent", parentA, id, "right");
            if (parentB != null && !parentB.isEmpty()) link("parent", parentB, id, "right");
            Set<String> parents = parentsOf(id);
            for (String existingId : new ArrayList<>(state.people.keySet())) {
                if (existingId.equals(id)) continue;
                if (parents.equals(parentsOf(existingId))) {
                    link("sibling", existingId, id, "right");
                }
            }
            arrange(Collections.singleton(id), parentA, "children");
            return id;
        }

        private String add(String role, float x, float y) {
            String id = prefix + "_" + role;
            assertFalse(state.people.containsKey(id));
            Person person = new Person(id);
            person.name = prefix.toUpperCase() + " · " + role.replace('_', ' ');
            person.notes = "training-role=" + role;
            person.x = snap(x);
            person.y = snap(y);
            person.colorMode = "auto-surname";
            person.manualColor = COLORS[state.people.size() % COLORS.length];
            state.people.put(id, person);
            state.selectedId = id;
            return id;
        }

        private void renameRole(String id, String role) {
            Person person = state.people.get(id);
            if (person == null) return;
            person.name = prefix.toUpperCase() + " · " + role.replace('_', ' ');
            person.notes = "training-role=" + role;
        }

        private void link(String type, String from, String to, String side) {
            for (Relation relation : state.links) {
                boolean directed = relation.from.equals(from) && relation.to.equals(to);
                boolean reverse = !"parent".equals(type)
                    && relation.from.equals(to)
                    && relation.to.equals(from);
                if (relation.type.equals(type) && (directed || reverse)) return;
            }
            state.links.add(new Relation(
                prefix + "_l" + String.format(java.util.Locale.ROOT, "%03d", linkIndex++),
                type,
                from,
                to,
                side));
        }

        private Set<String> parentsOf(String personId) {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            for (Relation relation : state.links) {
                if ("parent".equals(relation.type) && relation.to.equals(personId)) {
                    result.add(relation.from);
                }
            }
            return result;
        }

        private void arrange(Collection<String> addedIds, String anchorId, String action) {
            TreeLayoutEngine.layoutAfterAddition(state, addedIds, anchorId, action);
        }

        private static float snap(float value) {
            return Math.round(value / TreeLayoutEngine.GRID) * TreeLayoutEngine.GRID;
        }
    }
}
