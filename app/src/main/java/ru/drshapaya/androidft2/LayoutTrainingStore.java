package ru.drshapaya.androidft2;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Optional local layout diagnostics. No personal tree text is logged. */
final class LayoutTrainingStore {
    private static final String PREFS = "androidft-layout-training";
    private static final String LOGGING = "logging";
    private static final int MAX_LOG_CHARS = 180_000;

    private final Context appContext;

    LayoutTrainingStore(Context context) {
        appContext = context == null ? null : context.getApplicationContext();
    }

    boolean loggingEnabled() {
        return preferences().getBoolean(LOGGING, false);
    }

    boolean toggleLogging() {
        boolean next = !loggingEnabled();
        preferences().edit().putBoolean(LOGGING, next).apply();
        JSONObject event = new JSONObject();
        put(event, "enabled", next);
        log("logging", event);
        return next;
    }

    Session beginAutoArrange(
        TreeState state,
        Collection<String> affectedIds,
        String anchorId,
        String mode
    ) {
        if (state == null || !loggingEnabled()) return null;
        Session session = new Session(
            LayoutSnapshot.capture(state),
            ids(affectedIds),
            anchorId == null || anchorId.isEmpty() ? 0 : 1,
            mode);
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
        JSONObject event = session.baseJson();
        put(event, "applied", applied);
        put(event, "after", scoreJson(score(state, session.afterAuto, session.before)));
        log("auto-finish", event);
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

    private LayoutScorer.Score score(
        TreeState state,
        LayoutSnapshot snapshot,
        LayoutSnapshot baseline
    ) {
        return new LayoutScorer(new LayoutConstraints()).score(
            FamilyLayoutGraph.from(state),
            snapshot,
            baseline,
            LayoutWeights.defaults());
    }

    private Set<String> ids(Collection<String> ids) {
        Set<String> result = new HashSet<>();
        if (ids == null) return result;
        for (String id : ids) if (id != null && !id.isEmpty()) result.add(id);
        return result;
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
