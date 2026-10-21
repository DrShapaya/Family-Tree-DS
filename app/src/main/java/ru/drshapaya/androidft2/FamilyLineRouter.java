package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds deterministic orthogonal routes for parent families.
 *
 * <p>Every exact set of parents owns one union anchor, one vertical trunk and one
 * child bus.  A father enters the union from the left and a mother from the right;
 * the route therefore joins at the union instead of letting two parent-to-child
 * curves cross.  Repeated partnerships stay independent because their family keys,
 * anchors and bus lanes are different.</p>
 */
final class FamilyLineRouter {
    private FamilyLineRouter() {
    }

    static Plan build(TreeState state, float cardWidth, float cardHeight, float grid) {
        return build(state, cardWidth, cardHeight, grid, null);
    }

    static Plan build(
        TreeState state,
        float cardWidth,
        float cardHeight,
        float grid,
        Collection<String> visibleIds
    ) {
        if (state == null || state.people.isEmpty()) return Plan.empty();
        Set<String> visible = visibleIds == null
            ? null
            : new LinkedHashSet<>(visibleIds);
        ObstacleIndex obstacles = new ObstacleIndex(state.people.values(), cardHeight, grid);
        Map<String, LinkedHashSet<String>> parentsByChild = new LinkedHashMap<>();
        Map<String, List<String>> relationIdsByChild = new LinkedHashMap<>();
        Map<String, Relation> relationsById = new LinkedHashMap<>();
        for (Relation relation : state.links) {
            if (relation == null || relation.id == null) continue;
            relationsById.put(relation.id, relation);
            if (!"parent".equals(relation.type)
                || !state.people.containsKey(relation.from)
                || !state.people.containsKey(relation.to)
                || relation.from.equals(relation.to)
                || (visible != null
                    && (!visible.contains(relation.from) || !visible.contains(relation.to)))) {
                continue;
            }
            parentsByChild.computeIfAbsent(
                relation.to,
                ignored -> new LinkedHashSet<>()).add(relation.from);
            relationIdsByChild.computeIfAbsent(
                relation.to,
                ignored -> new ArrayList<>()).add(relation.id);
        }

        Map<String, FamilyBuilder> builders = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> entry : parentsByChild.entrySet()) {
            List<String> parents = new ArrayList<>(entry.getValue());
            parents.sort(String::compareTo);
            if (parents.isEmpty() || parents.size() > 2) continue;
            String key = String.join("|", parents);
            FamilyBuilder builder = builders.computeIfAbsent(
                key,
                ignored -> new FamilyBuilder(key, parents));
            builder.children.add(entry.getKey());
            builder.relationIds.addAll(relationIdsByChild.getOrDefault(
                entry.getKey(),
                Collections.emptyList()));
        }

        List<FamilyBuilder> ordered = new ArrayList<>(builders.values());
        for (FamilyBuilder builder : ordered) builder.measure(state, cardWidth);
        ordered.sort(Comparator
            .comparingDouble((FamilyBuilder value) -> value.parentTop)
            .thenComparingDouble(value -> value.childTop)
            // Nested routes must reserve the inner lane from the narrowest
            // interval outwards. Otherwise a wide bus can sit between a
            // neighboring family's bus and children, cutting its vertical drops.
            .thenComparingDouble(value -> value.routeRight - value.routeLeft)
            .thenComparingDouble(value -> value.unionX)
            .thenComparing(value -> value.key));

        Map<String, List<List<HorizontalRange>>> lanesByBand = new HashMap<>();
        List<FamilyRoute> routes = new ArrayList<>();
        LinkedHashSet<String> routedRelationIds = new LinkedHashSet<>();
        LinkedHashSet<String> consumedPartnerKeys = new LinkedHashSet<>();
        for (FamilyBuilder builder : ordered) {
            if (!builder.isUsable()) continue;
            String band = Math.round(builder.parentTop) + "|" + Math.round(builder.childTop);
            int lane = reserveLane(
                lanesByBand.computeIfAbsent(band, ignored -> new ArrayList<>()),
                builder.routeLeft,
                builder.routeRight,
                Math.max(1f, grid));
            FamilyRoute route = builder.route(
                state,
                cardWidth,
                cardHeight,
                Math.max(1f, grid),
                lane,
                obstacles);
            if (route == null || route.segments.isEmpty()) continue;
            routes.add(route);
            routedRelationIds.addAll(route.relationIds);
            if (route.parentIds.size() == 2) {
                consumedPartnerKeys.add(pairKey(
                    route.parentIds.get(0),
                    route.parentIds.get(1)));
            }
        }
        return new Plan(routes, routedRelationIds, consumedPartnerKeys, relationsById);
    }

    private static int reserveLane(
        List<List<HorizontalRange>> lanes,
        float left,
        float right,
        float gap
    ) {
        for (int lane = 0; lane < lanes.size(); lane++) {
            boolean available = true;
            for (HorizontalRange occupied : lanes.get(lane)) {
                if (left < occupied.right + gap && occupied.left < right + gap) {
                    available = false;
                    break;
                }
            }
            if (!available) continue;
            lanes.get(lane).add(new HorizontalRange(left, right));
            return lane;
        }
        List<HorizontalRange> added = new ArrayList<>();
        added.add(new HorizontalRange(left, right));
        lanes.add(added);
        return lanes.size() - 1;
    }

    static String pairKey(String first, String second) {
        if (first == null || second == null) return "";
        return first.compareTo(second) <= 0
            ? first + "|" + second
            : second + "|" + first;
    }

    private static int roleRank(Person person) {
        String gender = PersonGender.resolve(person);
        if (PersonGender.MALE.equals(gender)) return 0;
        if (PersonGender.FEMALE.equals(gender)) return 2;
        return 1;
    }

    private static final class FamilyBuilder {
        final String key;
        final List<String> parents;
        final LinkedHashSet<String> children = new LinkedHashSet<>();
        final LinkedHashSet<String> relationIds = new LinkedHashSet<>();
        float parentTop = Float.NaN;
        float childTop = Float.NaN;
        float unionX = Float.NaN;
        float routeLeft = Float.NaN;
        float routeRight = Float.NaN;

        FamilyBuilder(String key, Collection<String> parents) {
            this.key = key;
            this.parents = new ArrayList<>(parents);
        }

        void measure(TreeState state, float cardWidth) {
            List<Person> parentPeople = people(state, parents);
            List<Person> childPeople = people(state, children);
            if (parentPeople.isEmpty() || childPeople.isEmpty()) return;
            parentTop = Float.MAX_VALUE;
            float parentRow = -Float.MAX_VALUE;
            float sum = 0f;
            for (Person parent : parentPeople) {
                sum += parent.x + cardWidth / 2f;
                parentTop = Math.min(parentTop, parent.y);
                parentRow = Math.max(parentRow, parent.y);
            }
            // The family junction belongs to the parents' generation. Moving
            // one child must not move this band (or any sibling's connection).
            childTop = parentRow + TreeLayoutEngine.LEVEL_GAP;
            unionX = sum / parentPeople.size();
            routeLeft = unionX;
            routeRight = unionX;
            for (Person child : childPeople) {
                float center = child.x + cardWidth / 2f;
                routeLeft = Math.min(routeLeft, center);
                routeRight = Math.max(routeRight, center);
            }
        }

        boolean isUsable() {
            return Float.isFinite(parentTop)
                && Float.isFinite(childTop)
                && Float.isFinite(unionX)
                && Float.isFinite(routeLeft)
                && Float.isFinite(routeRight);
        }

        FamilyRoute route(
            TreeState state,
            float cardWidth,
            float cardHeight,
            float grid,
            int lane,
            ObstacleIndex obstacles
        ) {
            List<Person> parentPeople = people(state, parents);
            List<Person> childPeople = people(state, children);
            if (parentPeople.isEmpty() || childPeople.isEmpty()) return null;
            parentPeople.sort(Comparator
                .comparingInt(FamilyLineRouter::roleRank)
                .thenComparingDouble(person -> person.x)
                .thenComparing(person -> person.id));
            childPeople.sort(Comparator
                .comparingDouble((Person person) -> person.x)
                .thenComparing(person -> person.id));

            float parentBottom = -Float.MAX_VALUE;
            for (Person parent : parentPeople) {
                parentBottom = Math.max(parentBottom, parent.y + cardHeight);
            }
            float clearTop = parentBottom + grid;
            float clearBottom = Math.max(clearTop, childTop - grid);
            int laneCount = Math.max(1, (int) Math.floor((clearBottom - clearTop) / grid) + 1);
            int centerLane = (laneCount - 1) / 2;
            int distance = (lane + 1) / 2;
            int laneIndex = lane == 0
                ? centerLane
                : centerLane + ((lane & 1) == 1 ? -distance : distance);
            laneIndex = Math.max(0, Math.min(laneIndex, laneCount - 1));
            float busY = clearTop + laneIndex * grid;

            List<Segment> segments = new ArrayList<>();
            float resolvedUnionX;
            float unionY;
            if (parentPeople.size() == 1) {
                Person parent = parentPeople.get(0);
                resolvedUnionX = parent.x + cardWidth / 2f;
                unionY = parent.y + cardHeight;
            } else {
                Person first = parentPeople.get(0);
                Person second = parentPeople.get(1);
                Person left = first.x <= second.x ? first : second;
                Person right = first.x <= second.x ? second : first;
                float leftPort = left.x + cardWidth;
                float rightPort = right.x;
                resolvedUnionX = (leftPort + rightPort) / 2f;
                if (hasCardBetween(obstacles, left, right, cardWidth, cardHeight)) {
                    unionY = Math.max(left.y, right.y) + cardHeight + grid / 2f;
                    float leftCenter = left.x + cardWidth / 2f;
                    float rightCenter = right.x + cardWidth / 2f;
                    add(segments, leftCenter, left.y + cardHeight, leftCenter, unionY);
                    add(segments, leftCenter, unionY, resolvedUnionX, unionY);
                    assignOwner(segments, 0, left.id);
                    int rightStart = segments.size();
                    add(segments, rightCenter, right.y + cardHeight, rightCenter, unionY);
                    add(segments, rightCenter, unionY, resolvedUnionX, unionY);
                    assignOwner(segments, rightStart, right.id);
                } else {
                    unionY = (left.y + right.y) / 2f + cardHeight / 2f;
                    add(segments, leftPort, left.y + cardHeight / 2f, resolvedUnionX, unionY);
                    assignOwner(segments, 0, left.id);
                    int rightStart = segments.size();
                    add(segments, rightPort, right.y + cardHeight / 2f, resolvedUnionX, unionY);
                    assignOwner(segments, rightStart, right.id);
                }
            }
            add(segments, resolvedUnionX, unionY, resolvedUnionX, busY);

            float childLeft = Float.MAX_VALUE;
            float childRight = -Float.MAX_VALUE;
            for (Person child : childPeople) {
                float center = child.x + cardWidth / 2f;
                childLeft = Math.min(childLeft, center);
                childRight = Math.max(childRight, center);
            }
            add(segments, Math.min(resolvedUnionX, childLeft), busY,
                Math.max(resolvedUnionX, childRight), busY);
            Set<String> routedChildIds = new LinkedHashSet<>();
            for (Person child : childPeople) {
                float center = child.x + cardWidth / 2f;
                int first = segments.size();
                if (child.y < busY + grid) {
                    // Only this child's attachment changes. Join the existing
                    // common centre, leaving the parents and siblings intact.
                    routedChildIds.add(child.id);
                    float portX = center;
                    float portY = child.y + cardHeight;
                    if (child.y > busY) {
                        portY = child.y;
                    } else if (portY > busY) {
                        portX = center > resolvedUnionX ? child.x : child.x + cardWidth;
                        portY = child.y + cardHeight / 2f;
                    }
                    addAvoidingCards(segments, obstacles, portX, portY,
                        resolvedUnionX, busY, cardWidth, cardHeight, grid);
                } else {
                    add(segments, center, busY, center, child.y);
                }
                assignOwner(segments, first, child.id);
            }

            List<String> orderedParentIds = new ArrayList<>();
            for (Person parent : parentPeople) orderedParentIds.add(parent.id);
            List<String> orderedChildIds = new ArrayList<>();
            for (Person child : childPeople) orderedChildIds.add(child.id);
            return new FamilyRoute(
                "family:" + key,
                orderedParentIds,
                orderedChildIds,
                relationIds,
                resolvedUnionX,
                unionY,
                busY,
                segments,
                routedChildIds);
        }

        private boolean hasCardBetween(
            ObstacleIndex obstacles,
            Person left,
            Person right,
            float cardWidth,
            float cardHeight
        ) {
            float corridorLeft = left.x + cardWidth;
            float corridorRight = right.x;
            for (Person person : obstacles.query(left.y, left.y + cardHeight)) {
                if (person == null || person == left || person == right || children.contains(person.id)
                    || !Float.isFinite(person.x) || !Float.isFinite(person.y)) continue;
                boolean sameRow = person.y < left.y + cardHeight
                    && left.y < person.y + cardHeight;
                boolean inside = person.x < corridorRight
                    && corridorLeft < person.x + cardWidth;
                if (sameRow && inside) return true;
            }
            return false;
        }

        private static List<Person> people(TreeState state, Collection<String> ids) {
            List<Person> result = new ArrayList<>();
            for (String id : ids) {
                Person person = state.people.get(id);
                if (person != null
                    && Float.isFinite(person.x)
                    && Float.isFinite(person.y)) result.add(person);
            }
            return result;
        }
    }

    private static void add(List<Segment> segments, float x1, float y1, float x2, float y2) {
        if (Math.abs(x1 - x2) < 0.01f && Math.abs(y1 - y2) < 0.01f) return;
        segments.add(new Segment(x1, y1, x2, y2));
    }

    private static void assignOwner(List<Segment> segments, int first, String personId) {
        for (int i = first; i < segments.size(); i++) segments.get(i).personId = personId;
    }

    /** Shortest orthogonal detour for a child moved across generation rows. */
    private static void addAvoidingCards(List<Segment> segments, ObstacleIndex obstacleIndex,
        float x1, float y1, float x2, float y2, float width, float height, float grid) {
        float minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        List<Person> obstacles = obstacleIndex.query(minY, maxY);
        if (clearSegment(x1, y1, x2, y2, obstacles, width, height)) {
            add(segments, x1, y1, x2, y2);
            return;
        }
        if (clearSegment(x1, y1, x1, y2, obstacles, width, height)
            && clearSegment(x1, y2, x2, y2, obstacles, width, height)) {
            add(segments, x1, y1, x1, y2);
            add(segments, x1, y2, x2, y2);
            return;
        }
        if (clearSegment(x1, y1, x2, y1, obstacles, width, height)
            && clearSegment(x2, y1, x2, y2, obstacles, width, height)) {
            add(segments, x1, y1, x2, y1);
            add(segments, x2, y1, x2, y2);
            return;
        }
        java.util.TreeSet<Float> xSet = new java.util.TreeSet<>();
        java.util.TreeSet<Float> ySet = new java.util.TreeSet<>();
        xSet.add(x1); xSet.add(x2); ySet.add(y1); ySet.add(y2);
        for (Person person : obstacles) {
            xSet.add(person.x - grid); xSet.add(person.x + width + grid);
            ySet.add(Math.max(minY, person.y - grid));
            ySet.add(Math.min(maxY, person.y + height + grid));
        }
        List<Float> xs = new ArrayList<>(xSet), ys = new ArrayList<>(ySet);
        int columns = xs.size(), count = columns * ys.size();
        int start = ys.indexOf(y1) * columns + xs.indexOf(x1);
        int target = ys.indexOf(y2) * columns + xs.indexOf(x2);
        double[] distances = new double[count];
        java.util.Arrays.fill(distances, Double.POSITIVE_INFINITY);
        int[] previous = new int[count];
        java.util.Arrays.fill(previous, -1);
        java.util.PriorityQueue<double[]> queue = new java.util.PriorityQueue<>(
            Comparator.comparingDouble(item -> item[1]));
        distances[start] = 0d;
        queue.add(new double[]{start, 0d});
        while (!queue.isEmpty()) {
            double[] entry = queue.remove();
            int current = (int) entry[0];
            if (entry[1] > distances[current]) continue;
            if (current == target) break;
            int cx = current % columns, cy = current / columns;
            int[] neighbors = {cx > 0 ? current - 1 : -1,
                cx + 1 < columns ? current + 1 : -1,
                cy > 0 ? current - columns : -1,
                cy + 1 < ys.size() ? current + columns : -1};
            for (int next : neighbors) {
                if (next < 0) continue;
                float ax = xs.get(cx), ay = ys.get(cy);
                float bx = xs.get(next % columns), by = ys.get(next / columns);
                if (!clearSegment(ax, ay, bx, by, obstacles, width, height)) continue;
                double distance = distances[current] + Math.abs(bx - ax) + Math.abs(by - ay);
                if (distance >= distances[next]) continue;
                distances[next] = distance;
                previous[next] = current;
                queue.add(new double[]{next, distance});
            }
        }
        if (!Double.isFinite(distances[target])) return;
        List<Integer> path = new ArrayList<>();
        for (int at = target; at != -1; at = previous[at]) path.add(at);
        for (int i = path.size() - 1; i > 0; i--) {
            int a = path.get(i), b = path.get(i - 1);
            add(segments, xs.get(a % columns), ys.get(a / columns),
                xs.get(b % columns), ys.get(b / columns));
        }
    }

    private static boolean clearSegment(float x1, float y1, float x2, float y2,
        List<Person> obstacles, float width, float height) {
        if (Math.abs(x1 - x2) >= 0.01f && Math.abs(y1 - y2) >= 0.01f) return false;
        for (Person person : obstacles) {
            if (Math.abs(x1 - x2) < 0.01f) {
                if (x1 > person.x && x1 < person.x + width
                    && Math.max(y1, y2) > person.y && Math.min(y1, y2) < person.y + height) return false;
            } else if (y1 > person.y && y1 < person.y + height
                && Math.max(x1, x2) > person.x && Math.min(x1, x2) < person.x + width) return false;
        }
        return true;
    }

    /**
     * Vertical broad phase for route obstacle checks.  The exact rectangle
     * tests still run after the query, so the index only removes impossible
     * candidates and cannot change the selected route.
     */
    private static final class ObstacleIndex {
        private final float cellHeight;
        private final Map<Integer,List<Person>> cells = new HashMap<>();

        ObstacleIndex(Collection<Person> people, float cardHeight, float grid) {
            cellHeight = Math.max(64f, Math.max(cardHeight, grid * 8f));
            for (Person person : people) {
                if (person == null || !Float.isFinite(person.x) || !Float.isFinite(person.y)) continue;
                int first = cell(person.y), last = cell(person.y + cardHeight);
                for (int index = first; index <= last; index++) {
                    cells.computeIfAbsent(index, ignored -> new ArrayList<>()).add(person);
                }
            }
        }

        List<Person> query(float minY, float maxY) {
            if (cells.isEmpty()) return Collections.emptyList();
            int first = cell(Math.min(minY, maxY)) - 1;
            int last = cell(Math.max(minY, maxY)) + 1;
            List<Person> result = new ArrayList<>();
            Set<Person> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (int index = first; index <= last; index++) {
                List<Person> bucket = cells.get(index);
                if (bucket == null) continue;
                for (Person person : bucket) if (seen.add(person)) result.add(person);
            }
            return result;
        }

        private int cell(float y) { return (int)Math.floor(y / cellHeight); }
    }

    static final class Plan {
        final List<FamilyRoute> routes;
        final Set<String> routedRelationIds;
        final Set<String> consumedPartnerKeys;
        final Map<String, Relation> relationsById;
        private final Map<FamilyRoute,Relation> representatives=new java.util.IdentityHashMap<>();

        Plan(
            Collection<FamilyRoute> routes,
            Collection<String> routedRelationIds,
            Collection<String> consumedPartnerKeys,
            Map<String, Relation> relationsById
        ) {
            this.routes = Collections.unmodifiableList(new ArrayList<>(routes));
            this.routedRelationIds = Collections.unmodifiableSet(
                new LinkedHashSet<>(routedRelationIds));
            this.consumedPartnerKeys = Collections.unmodifiableSet(
                new LinkedHashSet<>(consumedPartnerKeys));
            this.relationsById = Collections.unmodifiableMap(
                new LinkedHashMap<>(relationsById));
            for(FamilyRoute route:this.routes) {
                Relation representative=null;
                for(String id:route.relationIds) {
                    representative=this.relationsById.get(id);if(representative!=null)break;
                }
                representatives.put(route,representative);
            }
        }

        static Plan empty() {
            return EmptyPlan.INSTANCE;
        }

        private static final class EmptyPlan {
            static final Plan INSTANCE = new Plan(
                Collections.emptyList(),
                Collections.emptySet(),
                Collections.emptySet(),
                Collections.emptyMap());
        }

        boolean consumes(Relation relation) {
            if (relation == null) return false;
            if (routedRelationIds.contains(relation.id)) return true;
            return ("partner".equals(relation.type) || "family".equals(relation.type))
                && consumedPartnerKeys.contains(pairKey(relation.from, relation.to));
        }

        Relation representative(FamilyRoute route) {
            if (route == null) return null;
            Relation cached=representatives.get(route);
            if(cached!=null ||representatives.containsKey(route))return cached;
            for (String id : route.relationIds) {
                Relation relation = relationsById.get(id);
                if (relation != null) return relation;
            }
            return null;
        }
    }

    static final class FamilyRoute {
        final String id;
        final List<String> parentIds;
        final List<String> childIds;
        final Set<String> relationIds;
        final float unionX;
        final float unionY;
        final float busY;
        final List<Segment> segments;
        final Set<String> routedChildIds;

        FamilyRoute(
            String id,
            Collection<String> parentIds,
            Collection<String> childIds,
            Collection<String> relationIds,
            float unionX,
            float unionY,
            float busY,
            Collection<Segment> segments,
            Collection<String> routedChildIds
        ) {
            this.id = id;
            this.parentIds = Collections.unmodifiableList(new ArrayList<>(parentIds));
            this.childIds = Collections.unmodifiableList(new ArrayList<>(childIds));
            this.relationIds = Collections.unmodifiableSet(new LinkedHashSet<>(relationIds));
            this.unionX = unionX;
            this.unionY = unionY;
            this.busY = busY;
            this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
            this.routedChildIds = Collections.unmodifiableSet(new LinkedHashSet<>(routedChildIds));
        }

        boolean containsRelation(String relationId) {
            return relationId != null && relationIds.contains(relationId);
        }
    }

    static final class Segment {
        final float x1;
        final float y1;
        final float x2;
        final float y2;
        String personId;

        Segment(float x1, float y1, float x2, float y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    private static final class HorizontalRange {
        final float left;
        final float right;

        HorizontalRange(float left, float right) {
            this.left = Math.min(left, right);
            this.right = Math.max(left, right);
        }
    }
}
