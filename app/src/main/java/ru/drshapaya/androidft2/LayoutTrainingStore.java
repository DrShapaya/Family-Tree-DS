package ru.drshapaya.androidft2;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PointF;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Local, private learning loop for layout preferences. No personal tree text is logged. */
final class LayoutTrainingStore {
    private static final String PREFS = "androidft-layout-training";
    private static final String LEARNING = "learning";
    private static final String LOGGING = "logging";
    private static final String SAMPLES = "samples";
    private static final long CORRECTION_WINDOW_MS = 30L * 60L * 1000L;
    private static final int MAX_LOG_CHARS = 180_000;

    private final Context appContext;
    private Session pendingSession;

    LayoutTrainingStore(Context context) {
        appContext = context == null ? null : context.getApplicationContext();
    }

    boolean learningEnabled() {
        return preferences().getBoolean(LEARNING, true);
    }

    boolean loggingEnabled() {
        return preferences().getBoolean(LOGGING, false);
    }

    boolean toggleLearning() {
        boolean next = !learningEnabled();
        preferences().edit().putBoolean(LEARNING, next).apply();
        if (!next) pendingSession = null;
        return next;
    }

    boolean toggleLogging() {
        boolean next = !loggingEnabled();
        preferences().edit().putBoolean(LOGGING, next).apply();
        JSONObject event = new JSONObject();
        put(event, "enabled", next);
        log("logging", event);
        return next;
    }

    LayoutWeights weights() {
        SharedPreferences prefs = preferences();
        LayoutWeights defaults = LayoutWeights.defaults();
        return defaults.with(
            prefs.getFloat("lineCrossings", (float) defaults.lineCrossings),
            prefs.getFloat("movement", (float) defaults.movement),
            defaults.mainTrunkMovement,
            prefs.getFloat("width", (float) defaults.width),
            prefs.getFloat("height", (float) defaults.height),
            prefs.getFloat("familyCenter", (float) defaults.familyCenter),
            prefs.getFloat("symmetry", (float) defaults.symmetry),
            prefs.getFloat("siblingSpacing", (float) defaults.siblingSpacing),
            prefs.getFloat("wrongSide", (float) defaults.wrongSide),
            prefs.getFloat("emptySpace", (float) defaults.emptySpace),
            defaults.changedOrder,
            prefs.getFloat("connectionLength", (float) defaults.connectionLength));
    }

    Session beginAutoArrange(
        TreeState state,
        Collection<String> affectedIds,
        String anchorId,
        String mode
    ) {
        if (state == null || (!learningEnabled() && !loggingEnabled())) return null;
        Session session = new Session(
            LayoutSnapshot.capture(state),
            ids(affectedIds),
            anchorId == null || anchorId.isEmpty() ? 0 : 1,
            mode);
        pendingSession = session;
        JSONObject event = session.baseJson();
        put(event, "people", state.people.size());
        put(event, "links", state.links.size());
        put(event, "before", scoreJson(score(state, session.before, session.before)));
        log("auto-start", event);
        return session;
    }

    void finishAutoArrange(Session session, TreeState state, boolean applied) {
        if (session == null || state == null) return;
        session.afterAuto = LayoutSnapshot.capture(state);
        session.applied = applied;
        session.finishedAt = System.currentTimeMillis();
        if (applied) pendingSession = session;
        JSONObject event = session.baseJson();
        put(event, "applied", applied);
        put(event, "after", scoreJson(score(state, session.afterAuto, session.before)));
        log("auto-finish", event);
    }

    void learnFromManualMove(
        TreeState state,
        Map<String, PointF> before,
        Map<String, PointF> after,
        String detail
    ) {
        if (state == null || before == null || after == null || after.isEmpty()) return;
        Set<String> moved = new HashSet<>(after.keySet());
        Session session = pendingSession;
        if (session == null || session.afterAuto == null) {
            logManualOnly(state, moved);
            return;
        }
        if (System.currentTimeMillis() - session.finishedAt > CORRECTION_WINDOW_MS) {
            pendingSession = null;
            logManualOnly(state, moved);
            return;
        }
        if (!session.affectedIds.isEmpty() && !intersects(session.affectedIds, moved)) {
            logManualOnly(state, moved);
            return;
        }

        LayoutScorer.Score autoScore = score(state, session.afterAuto, session.before);
        LayoutScorer.Score correctedScore = score(state, LayoutSnapshot.capture(state), session.before);
        JSONObject event = session.baseJson();
        put(event, "moved", moved.size());
        put(event, "auto", scoreJson(autoScore));
        put(event, "corrected", scoreJson(correctedScore));
        if (learningEnabled()) {
            LayoutWeights next = adjustedWeights(weights(), autoScore, correctedScore);
            saveWeights(next);
            preferences().edit().putInt(SAMPLES, preferences().getInt(SAMPLES, 0) + 1).apply();
            SmartLayoutSolver.setRuntimeWeights(next);
            put(event, "weights", weightsJson(next));
        }
        log("manual-correction", event);
        pendingSession = null;
    }

    String logText() {
        File file = logFile();
        if (!file.isFile()) return "";
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), MAX_LOG_CHARS)];
            long skip = Math.max(0L, file.length() - data.length);
            while (skip > 0L) {
                long skipped = input.skip(skip);
                if (skipped <= 0L) break;
                skip -= skipped;
            }
            int read = input.read(data);
            return read <= 0 ? "" : new String(data, 0, read, StandardCharsets.UTF_8);
        } catch (Exception error) {
            DiagnosticsLogger.handled(appContext, "layout-training.log.read", error);
            return "";
        }
    }

    void clearLog() {
        File file = logFile();
        if (file.isFile() && !file.delete()) {
            log("log-clear-failed", new JSONObject());
        }
    }

    int sampleCount() {
        return preferences().getInt(SAMPLES, 0);
    }

    private void logManualOnly(TreeState state, Set<String> moved) {
        if (!loggingEnabled()) return;
        JSONObject event = new JSONObject();
        put(event, "people", state == null ? 0 : state.people.size());
        put(event, "moved", moved == null ? 0 : moved.size());
        log("manual-move", event);
    }

    private LayoutWeights adjustedWeights(
        LayoutWeights current,
        LayoutScorer.Score autoScore,
        LayoutScorer.Score correctedScore
    ) {
        double gridSignal = TreeLayoutEngine.GRID / 2d;
        double lineCrossings = factorLowerIsBetter(autoScore.lineCrossings, correctedScore.lineCrossings, 0.01d, 1.12d);
        double movement = factorLowerIsBetter(autoScore.movement, correctedScore.movement, gridSignal, 1.08d);
        double familyCenter = factorLowerIsBetter(autoScore.familyCenterError, correctedScore.familyCenterError, gridSignal, 1.10d);
        double symmetry = factorLowerIsBetter(autoScore.symmetryError, correctedScore.symmetryError, gridSignal, 1.08d);
        double siblingSpacing = factorLowerIsBetter(autoScore.siblingSpacingError, correctedScore.siblingSpacingError, gridSignal, 1.10d);
        double wrongSide = factorLowerIsBetter(autoScore.wrongSideError, correctedScore.wrongSideError, gridSignal, 1.12d);
        double connection = factorLowerIsBetter(autoScore.connectionLength, correctedScore.connectionLength, gridSignal, 1.04d);
        double width = 1d;
        double height = 1d;
        double empty = 1d;
        if (correctedScore.width + TreeLayoutEngine.GRID * 2f < autoScore.width) width = 1.06d;
        else if (correctedScore.width > autoScore.width + TreeLayoutEngine.GRID * 2f) width = 0.96d;
        if (correctedScore.height + TreeLayoutEngine.GRID * 2f < autoScore.height) height = 1.04d;
        else if (correctedScore.height > autoScore.height + TreeLayoutEngine.GRID * 2f) height = 0.97d;
        if (correctedScore.emptySpace + TreeLayoutEngine.GRID * TreeLayoutEngine.GRID
            < autoScore.emptySpace) {
            empty = 1.05d;
        } else if (correctedScore.emptySpace > autoScore.emptySpace
            + TreeLayoutEngine.GRID * TreeLayoutEngine.GRID) {
            empty = 0.97d;
        }
        return current.nudge(
            lineCrossings,
            movement,
            width,
            height,
            familyCenter,
            symmetry,
            siblingSpacing,
            wrongSide,
            empty,
            connection);
    }

    private static double factorLowerIsBetter(
        double autoValue,
        double correctedValue,
        double threshold,
        double gain
    ) {
        threshold = Math.max(0.01d, threshold);
        if (!Double.isFinite(autoValue) || !Double.isFinite(correctedValue)) return 1d;
        if (correctedValue + threshold < autoValue) return gain;
        if (correctedValue > autoValue + threshold) return Math.max(0.92d, 2d - gain);
        return 1d;
    }

    private LayoutScorer.Score score(
        TreeState state,
        LayoutSnapshot snapshot,
        LayoutSnapshot baseline
    ) {
        return new LayoutScorer(new LayoutConstraints()).score(
            FamilyLayoutGraph.from(state),
            snapshot,
            baseline,
            weights());
    }

    private Set<String> ids(Collection<String> ids) {
        Set<String> result = new HashSet<>();
        if (ids == null) return result;
        for (String id : ids) if (id != null && !id.isEmpty()) result.add(id);
        return result;
    }

    private static boolean intersects(Set<String> first, Set<String> second) {
        if (first == null || second == null) return false;
        for (String value : second) if (first.contains(value)) return true;
        return false;
    }

    private void saveWeights(LayoutWeights weights) {
        preferences().edit()
            .putFloat("lineCrossings", (float) weights.lineCrossings)
            .putFloat("movement", (float) weights.movement)
            .putFloat("width", (float) weights.width)
            .putFloat("height", (float) weights.height)
            .putFloat("familyCenter", (float) weights.familyCenter)
            .putFloat("symmetry", (float) weights.symmetry)
            .putFloat("siblingSpacing", (float) weights.siblingSpacing)
            .putFloat("wrongSide", (float) weights.wrongSide)
            .putFloat("emptySpace", (float) weights.emptySpace)
            .putFloat("connectionLength", (float) weights.connectionLength)
            .apply();
    }

    private JSONObject scoreJson(LayoutScorer.Score score) {
        JSONObject result = new JSONObject();
        put(result, "total", finite(score.total));
        put(result, "hard", score.hardViolations);
        put(result, "cross", finite(score.lineCrossings));
        put(result, "move", finite(score.movement));
        put(result, "center", finite(score.familyCenterError));
        put(result, "symmetry", finite(score.symmetryError));
        put(result, "spacing", finite(score.siblingSpacingError));
        put(result, "side", finite(score.wrongSideError));
        put(result, "empty", finite(score.emptySpace));
        put(result, "link", finite(score.connectionLength));
        put(result, "w", finite(score.width));
        put(result, "h", finite(score.height));
        return result;
    }

    private JSONObject weightsJson(LayoutWeights weights) {
        JSONObject result = new JSONObject();
        put(result, "cross", finite(weights.lineCrossings));
        put(result, "move", finite(weights.movement));
        put(result, "width", finite(weights.width));
        put(result, "height", finite(weights.height));
        put(result, "center", finite(weights.familyCenter));
        put(result, "symmetry", finite(weights.symmetry));
        put(result, "spacing", finite(weights.siblingSpacing));
        put(result, "side", finite(weights.wrongSide));
        put(result, "empty", finite(weights.emptySpace));
        put(result, "link", finite(weights.connectionLength));
        return result;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : -1d;
    }

    private void log(String type, JSONObject payload) {
        if (!loggingEnabled() && !"logging".equals(type)) return;
        try {
            JSONObject line = payload == null ? new JSONObject() : payload;
            put(line, "type", type);
            put(line, "at", System.currentTimeMillis());
            File file = logFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) parent.mkdirs();
            try (FileOutputStream output = new FileOutputStream(file, true)) {
                output.write((line.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception error) {
            DiagnosticsLogger.handled(appContext, "layout-training.log.write", error);
        }
    }

    private File logFile() {
        File root = appContext == null ? new File(".") : appContext.getFilesDir();
        return new File(root, "layout-training.jsonl");
    }

    private SharedPreferences preferences() {
        return appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONObject put(JSONObject object, String key, Object value) {
        try {
            return object.put(key, value);
        } catch (Exception ignored) {
            return object;
        }
    }

    static final class Session {
        final LayoutSnapshot before;
        final Set<String> affectedIds;
        final int hasAnchor;
        final String mode;
        final long startedAt = System.currentTimeMillis();
        LayoutSnapshot afterAuto;
        boolean applied;
        long finishedAt;

        Session(LayoutSnapshot before, Set<String> affectedIds, int hasAnchor, String mode) {
            this.before = before;
            this.affectedIds = affectedIds == null ? new HashSet<>() : new HashSet<>(affectedIds);
            this.hasAnchor = hasAnchor;
            this.mode = mode == null ? "" : mode;
        }

        JSONObject baseJson() {
            JSONObject result = new JSONObject();
            put(result, "mode", mode);
            put(result, "affected", affectedIds.size());
            put(result, "anchor", hasAnchor);
            put(result, "started", startedAt);
            return result;
        }
    }
}
