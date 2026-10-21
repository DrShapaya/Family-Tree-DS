package ru.drshapaya.androidft2;

import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Counts recorded relationships, never infers blood relationship through a shared child. */
final class TreeStatistics {
    final int people, closeRelatives, distantRelatives, familyConnections, unrelated;
    final boolean hasReference;
    final int lifeSamples, approximateLifeSamples, maximumLife, parentSamples, maximumChildren;
    final double averageLife, averageChildren;
    final int[] genders = new int[3], birthPeriods = new int[5], childrenDistribution = new int[5], photos = new int[2];
    int isolatedPeople, peopleWithNotes;

    private TreeStatistics(int people, boolean hasReference, int close, int distant, int family, int unrelated,
        int lifeSamples, int approximateLifeSamples, long lifeSum, int maximumLife,
        int parentSamples, long childSum, int maximumChildren) {
        this.people = people;
        this.hasReference = hasReference;
        this.closeRelatives = close;
        this.distantRelatives = distant;
        this.familyConnections = family;
        this.unrelated = unrelated;
        this.lifeSamples = lifeSamples;
        this.approximateLifeSamples = approximateLifeSamples;
        this.averageLife = lifeSamples == 0 ? 0 : (double) lifeSum / lifeSamples;
        this.maximumLife = maximumLife;
        this.parentSamples = parentSamples;
        this.averageChildren = parentSamples == 0 ? 0 : (double) childSum / parentSamples;
        this.maximumChildren = maximumChildren;
    }

    static TreeStatistics calculate(TreeState state, String referenceId) {
        if (state == null) state = new TreeState();
        Map<String, Set<String>> parents = new HashMap<>(), children = new HashMap<>();
        Map<String, Set<String>> siblings = new HashMap<>(), connections = new HashMap<>();
        for (Relation r : state.links) {
            if (!state.people.containsKey(r.from) || !state.people.containsKey(r.to) || r.from.equals(r.to)) continue;
            if ("parent".equals(r.type)) {
                add(parents, r.to, r.from);
                add(children, r.from, r.to);
            } else if ("sibling".equals(r.type)) {
                add(siblings, r.from, r.to);
                add(siblings, r.to, r.from);
            } else if (!"partner".equals(r.type) && !"family".equals(r.type)) continue;
            add(connections, r.from, r.to);
            add(connections, r.to, r.from);
        }
        boolean hasReference = state.people.containsKey(referenceId);
        Set<String> seeds = hasReference ? Collections.singleton(referenceId) : Collections.emptySet();
        Set<String> ancestors = reachable(parents, seeds);
        Set<String> bloodSeeds = new HashSet<>(ancestors);
        // One explicit sibling step is allowed: half-sibling relationships are not transitive.
        for (String ancestor : ancestors) bloodSeeds.addAll(get(siblings, ancestor));
        Set<String> relatives = reachable(children, bloodSeeds);
        Set<String> connected = reachable(connections, seeds);
        Set<String> close = new HashSet<>();
        if (hasReference) {
            close.addAll(get(parents, referenceId));
            close.addAll(get(children, referenceId));
            close.addAll(get(siblings, referenceId));
            for (String parent : get(parents, referenceId)) {
                close.addAll(get(parents, parent));
                close.addAll(get(children, parent));
            }
            for (String child : get(children, referenceId)) close.addAll(get(children, child));
        }
        close.remove(referenceId);
        relatives.remove(referenceId);
        connected.remove(referenceId);
        int distant = relatives.size() - close.size();
        connected.removeAll(relatives);
        int unrelated = state.people.size() - (hasReference ? 1 : 0) - relatives.size() - connected.size();
        int lifeCount = 0, approximate = 0, maxLife = 0, parentCount = 0, maxChildren = 0;
        long lifeSum = 0, childSum = 0;
        Calendar today = Calendar.getInstance();
        for (Person p : state.people.values()) {
            int life = lifeYears(p, today);
            if (life >= 0) {
                lifeCount++;
                lifeSum += life;
                maxLife = Math.max(maxLife, life);
                if (!completeDates(p)) approximate++;
            }
            int count = get(children, p.id).size();
            if (count > 0) {
                parentCount++;
                childSum += count;
                maxChildren = Math.max(maxChildren, count);
            }
        }
        TreeStatistics result = new TreeStatistics(state.people.size(), hasReference, close.size(), distant,
            connected.size(), unrelated, lifeCount, approximate, lifeSum, maxLife,
            parentCount, childSum, maxChildren);
        for (Person p : state.people.values()) {
            String gender = PersonGender.resolve(p);
            result.genders[PersonGender.MALE.equals(gender) ? 0 : PersonGender.FEMALE.equals(gender) ? 1 : 2]++;
            result.childrenDistribution[Math.min(4,get(children,p.id).size())]++;
            result.photos[blank(p.photo) && blank(p.photoMediaId) ? 1 : 0]++;
            if (!blank(p.notes)) result.peopleWithNotes++;
            if (get(connections,p.id).isEmpty()) result.isolatedPeople++;
            int period = 4;
            try {
                Calendar birth = date(p.bornYear,p.bornMonth,p.bornDay);
                if (!birth.after(today)) {
                    int year = birth.get(Calendar.YEAR);
                    period = year < 1900 ? 0 : year < 1950 ? 1 : year < 2000 ? 2 : 3;
                }
            } catch (IllegalArgumentException | NullPointerException invalid) { /* Unknown bucket. */ }
            result.birthPeriods[period]++;
        }
        return result;
    }

    private static void add(Map<String, Set<String>> graph, String from, String to) {
        graph.computeIfAbsent(from, key -> new HashSet<>()).add(to);
    }

    private static Set<String> get(Map<String, Set<String>> graph, String id) {
        Set<String> result = graph.get(id);
        return result == null ? Collections.emptySet() : result;
    }

    private static Set<String> reachable(Map<String, Set<String>> graph, Set<String> seeds) {
        Set<String> visited = new HashSet<>(seeds);
        ArrayDeque<String> queue = new ArrayDeque<>(seeds);
        while (!queue.isEmpty()) {
            for (String next : get(graph, queue.remove())) if (visited.add(next)) queue.add(next);
        }
        return visited;
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    private static boolean completeDates(Person p) {
        return !blank(p.bornMonth) && !blank(p.bornDay) && !blank(p.diedMonth) && !blank(p.diedDay);
    }

    private static Calendar date(String year, String month, String day) {
        int y = Integer.parseInt(year.trim());
        if (y < 1 || y > 9999 || (!blank(day) && blank(month))) throw new IllegalArgumentException();
        GregorianCalendar date = new GregorianCalendar();
        date.setGregorianChange(new java.util.Date(Long.MIN_VALUE));
        date.setLenient(false);
        date.clear();
        date.set(y, blank(month) ? 0 : Integer.parseInt(month.trim()) - 1, blank(day) ? 1 : Integer.parseInt(day.trim()));
        date.getTimeInMillis(); // Reject February 30, month 13, etc.
        return date;
    }

    private static int lifeYears(Person p, Calendar today) {
        try {
            Calendar birth = date(p.bornYear, p.bornMonth, p.bornDay);
            Calendar death = date(p.diedYear, p.diedMonth, p.diedDay);
            Calendar latestDeath = (Calendar) death.clone();
            if (blank(p.diedMonth)) latestDeath.set(Calendar.MONTH, Calendar.DECEMBER);
            if (blank(p.diedDay)) latestDeath.set(Calendar.DAY_OF_MONTH, latestDeath.getActualMaximum(Calendar.DAY_OF_MONTH));
            if (death.after(today) || latestDeath.before(birth)) return -1;
            int years = death.get(Calendar.YEAR) - birth.get(Calendar.YEAR);
            if (completeDates(p) && (death.get(Calendar.MONTH) < birth.get(Calendar.MONTH)
                || (death.get(Calendar.MONTH) == birth.get(Calendar.MONTH)
                    && death.get(Calendar.DAY_OF_MONTH) < birth.get(Calendar.DAY_OF_MONTH)))) years--;
            return years;
        } catch (IllegalArgumentException | NullPointerException invalidOrUnknown) {
            return -1;
        }
    }
}
