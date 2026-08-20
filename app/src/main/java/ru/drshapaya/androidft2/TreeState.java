package ru.drshapaya.androidft2;

import android.graphics.Color;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

final class TreeState {
    final Map<String, Person> people = new LinkedHashMap<>();
    final List<Relation> links = new ArrayList<>();
    final List<Guide> guides = new ArrayList<>();
    final List<HistoryEntry> history = new ArrayList<>();
    final Map<String, List<String>> photoAlbums = new LinkedHashMap<>();
    final Map<String, List<String>> photoAlbumMedia = new LinkedHashMap<>();
    final Map<String, List<PhotoAlbumFolder>> photoAlbumFolders = new LinkedHashMap<>();
    final Map<String, List<String>> familyAlbumMedia = new LinkedHashMap<>();
    final Map<String, List<String>> personAlbumMedia = new LinkedHashMap<>();
    final Set<String> familyAlbums = new LinkedHashSet<>();
    String rootId = "";
    String selectedId = "";
    String theme = "light";
    int printScale = 100;
    boolean editLocked = false;
    boolean historyHidden = true;
    boolean inspectorHidden = false;
    boolean adminCollapsed = false;
    boolean readerMode = false;
    boolean onboardingCompleted = false;
    boolean onboardingOffered = false;
    boolean guidesVisible = true;
    boolean hideCardDetails = false;
    boolean compactCards = false;
    boolean focusTree = false;
    boolean autoArrangeOnAdd = false;
    boolean workspaceBoundsVisible = true;
    String workspaceBoundsStyle = "soft";
    int workspaceWidth = 24000;
    int workspaceHeight = 16000;
    String parentLineMode = "smart";

    private static final int[] COLORS = {
        Color.rgb(242, 209, 107),
        Color.rgb(132, 199, 174),
        Color.rgb(131, 173, 223),
        Color.rgb(233, 157, 143),
        Color.rgb(187, 166, 222),
        Color.rgb(240, 168, 95),
        Color.rgb(115, 192, 212),
        Color.rgb(165, 201, 111),
        Color.rgb(223, 142, 182),
        Color.rgb(148, 168, 230)
    };

    Person selectedPerson() {
        Person selected = people.get(selectedId);
        if (selected != null) return selected;
        if (!people.isEmpty()) {
            selected = people.values().iterator().next();
            selectedId = selected.id;
            if (rootId.isEmpty()) rootId = selected.id;
        }
        return selected;
    }

    Person addPerson(String name, float x, float y) {
        String id = "p_" + UUID.randomUUID().toString().replace("-", "");
        Person person = new Person(id);
        person.name = name == null || name.trim().isEmpty() ? "Новый человек" : name.trim();
        person.gender = PersonGender.infer(person.name);
        person.x = x;
        person.y = y;
        person.colorMode = "auto-surname";
        person.manualColor = colorString(colorFor(person.name, people.size()));
        person.color = displayColor(person, people.size());
        people.put(id, person);
        selectedId = id;
        if (rootId.isEmpty()) rootId = id;
        return person;
    }

    Relation addRelation(String type, String from, String to) {
        if (from == null || to == null || from.equals(to)) return null;
        if (!people.containsKey(from) || !people.containsKey(to)) return null;
        for (Relation link : links) {
            boolean sameDirected = link.from.equals(from) && link.to.equals(to);
            boolean sameUndirected = !"parent".equals(type) && link.from.equals(to) && link.to.equals(from);
            if (link.type.equals(type) && (sameDirected || sameUndirected)) return link;
        }
        Relation relation = new Relation("l_" + UUID.randomUUID().toString().replace("-", ""), type, from, to);
        links.add(relation);
        return relation;
    }

    Relation addRelation(String type, String from, String to, String side) {
        Relation relation = addRelation(type, from, to);
        if (relation != null) relation.side = "left".equals(side) ? "left" : "right";
        return relation;
    }

    void copyParentLinks(String existingParentId, String newParentId) {
        if (existingParentId == null || newParentId == null
            || existingParentId.equals(newParentId)) return;
        List<String> childIds = new ArrayList<>();
        for (Relation link : links) {
            if ("parent".equals(link.type) && existingParentId.equals(link.from)) {
                childIds.add(link.to);
            }
        }
        for (String childId : childIds) addRelation("parent", newParentId, childId);
    }

    List<String> siblingFamilyTargetsForNewParent(
        String childId,
        Collection<String> existingParentIds
    ) {
        LinkedHashSet<String> siblingGroup = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        if (people.containsKey(childId)) queue.add(childId);
        while (!queue.isEmpty()) {
            String personId = queue.removeFirst();
            if (!people.containsKey(personId) || !siblingGroup.add(personId)) continue;
            for (Relation link : links) {
                if (!"sibling".equals(link.type)) continue;
                if (personId.equals(link.from) && people.containsKey(link.to)) queue.addLast(link.to);
                else if (personId.equals(link.to) && people.containsKey(link.from)) queue.addLast(link.from);
            }
        }

        Set<String> expectedParents = new LinkedHashSet<>();
        if (existingParentIds != null) expectedParents.addAll(existingParentIds);
        List<String> result = new ArrayList<>();
        for (String personId : siblingGroup) {
            if (personId.equals(childId)) {
                result.add(personId);
                continue;
            }
            Set<String> actualParents = new LinkedHashSet<>();
            for (Relation link : links) {
                if ("parent".equals(link.type) && personId.equals(link.to)) {
                    actualParents.add(link.from);
                }
            }
            if (actualParents.isEmpty() || actualParents.equals(expectedParents)) result.add(personId);
        }
        return result;
    }

    void deletePerson(String id) {
        if (id == null) return;
        people.remove(id);
        links.removeIf(link -> id.equals(link.from) || id.equals(link.to));
        for (List<String> ids : photoAlbums.values()) ids.remove(id);
        for (List<PhotoAlbumFolder> folders : photoAlbumFolders.values()) {
            for (PhotoAlbumFolder folder : folders) folder.personIds.remove(id);
        }
        personAlbumMedia.remove(id);
        if (id.equals(rootId)) rootId = people.isEmpty() ? "" : people.values().iterator().next().id;
        if (id.equals(selectedId)) selectedId = rootId;
    }

    int removeRelationsBetween(String first, String second) {
        if (first == null || second == null || first.equals(second)) return 0;
        int before = links.size();
        links.removeIf(link -> (first.equals(link.from) && second.equals(link.to)) || (first.equals(link.to) && second.equals(link.from)));
        return before - links.size();
    }

    Set<String> ancestorsOf(String id) {
        Set<String> result = new HashSet<>();
        collectAncestors(id, result);
        return result;
    }

    Set<String> descendantsOf(String id) {
        Set<String> result = new HashSet<>();
        collectDescendants(id, result);
        return result;
    }

    Set<String> nearOf(String id) {
        Set<String> result = new HashSet<>();
        if (id == null || id.isEmpty()) return result;
        result.add(id);
        for (Relation link : links) {
            if (id.equals(link.from)) result.add(link.to);
            if (id.equals(link.to)) result.add(link.from);
        }
        return result;
    }

    private void collectAncestors(String id, Set<String> result) {
        if (id == null || id.isEmpty()) return;
        result.add(id);
        for (Relation link : links) {
            if ("parent".equals(link.type) && id.equals(link.to) && result.add(link.from)) {
                collectAncestors(link.from, result);
            }
        }
    }

    private void collectDescendants(String id, Set<String> result) {
        if (id == null || id.isEmpty()) return;
        result.add(id);
        for (Relation link : links) {
            if ("parent".equals(link.type) && id.equals(link.from) && result.add(link.to)) {
                collectDescendants(link.to, result);
            }
        }
    }

    static int colorFor(String value, int fallback) {
        String key = value == null ? "" : value.trim();
        if (key.isEmpty()) return COLORS[Math.floorMod(fallback, COLORS.length)];
        long hash = 2166136261L;
        for (int i = 0; i < key.length(); i++) hash = ((hash * 31L) + key.charAt(i)) & 0xffffffffL;
        return COLORS[(int) (hash % COLORS.length)];
    }

    static int displayColor(Person person, int fallback) {
        if (person == null) return COLORS[Math.floorMod(fallback, COLORS.length)];
        if ("manual".equals(person.colorMode)) return parseColor(person.manualColor, colorFor(person.name, fallback));
        if ("auto-surname".equals(person.colorMode)) return colorFor(surnameOf(person.name), fallback);
        return colorFor(person.name, fallback);
    }

    static String colorString(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    static int parseColor(String value, int fallback) {
        try {
            if (value == null || value.trim().isEmpty()) return fallback;
            return Color.parseColor(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    static String surnameOf(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) return "";
        String[] parts = value.split("\\s+");
        return normalizeSurname(parts.length == 0 ? value : parts[0]);
    }

    private static String normalizeSurname(String value) {
        String surname = value == null ? "?" : value.trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
        surname = surname.replaceAll("[^\\p{L}\\-]", "");
        if (surname.endsWith("ская")) return surname.substring(0, surname.length() - 4) + "ский";
        if (surname.endsWith("цкая")) return surname.substring(0, surname.length() - 4) + "цкий";
        if (surname.endsWith("ова") || surname.endsWith("ева")) return surname.substring(0, surname.length() - 1);
        if (surname.endsWith("ина") || surname.endsWith("ына")) return surname.substring(0, surname.length() - 1);
        if (surname.endsWith("ая")) return surname.substring(0, surname.length() - 2);
        return surname.isEmpty() ? "?" : surname;
    }
}
