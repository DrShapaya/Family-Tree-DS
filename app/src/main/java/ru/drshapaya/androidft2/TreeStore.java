package ru.drshapaya.androidft2;

import android.content.Context;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class TreeStore {
    private static final int PHOTO_DATA_LIMIT = 60_000_000;
    private static final long MAX_INTERNAL_TREE_BYTES = 256L * 1024L * 1024L;
    private static final int VERSION_LIMIT = 10;
    private static final String CURRENT_FILE = "androidft2-current.ftree";
    private static final String VERSIONS_DIRECTORY = "androidft2-versions";
    private final File file;
    private final Context appContext;
    private final AtomicFile atomicFile;
    private final File versionsDirectory;
    private final TreeMediaStore mediaStore;
    private final ExecutorService versionExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tree-version-writer");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private final AtomicReference<PendingVersion> pendingVersion = new AtomicReference<>();
    private final AtomicBoolean versionWriterRunning = new AtomicBoolean(false);
    private String recoveryNotice = "";

    TreeStore(Context context) {
        appContext = context.getApplicationContext();
        file = new File(context.getFilesDir(), CURRENT_FILE);
        atomicFile = new AtomicFile(file);
        versionsDirectory = new File(context.getFilesDir(), VERSIONS_DIRECTORY);
        mediaStore = new TreeMediaStore(context);
    }

    TreeMediaStore mediaStore() {
        return mediaStore;
    }

    TreeState load() {
        boolean interruptedWrite = backupFileFor(file).exists();
        if (file.exists() || interruptedWrite) {
            try {
                TreeState loaded = parse(readAtomicFile(atomicFile));
                if (interruptedWrite) {
                    recoveryNotice = "Незавершённое сохранение восстановлено автоматически.";
                }
                return loaded;
            } catch (Exception error) {
                DiagnosticsLogger.handled(appContext, "load.current", error);
                // A corrupt current file must never be treated as a valid empty tree.
            }
        }

        for (StoredVersion version : listVersions()) {
            try {
                TreeState recovered = loadVersion(version);
                recoveryNotice = "Текущий файл был повреждён. Загружена последняя сохранённая версия.";
                writeCurrent(toJson(recovered).toString(2).getBytes(StandardCharsets.UTF_8));
                return recovered;
            } catch (Exception error) {
                DiagnosticsLogger.handled(appContext, "load.version", error);
                // Try the next older valid version.
            }
        }
        return new TreeState();
    }

    boolean save(TreeState state) {
        byte[] data;
        try {
            data = toJson(state).toString(2).getBytes(StandardCharsets.UTF_8);
            writeCurrent(data);
        } catch (Exception error) {
            DiagnosticsLogger.handled(appContext, "save.current", error);
            return false;
        }
        enqueueVersion(new PendingVersion(
            data,
            state.people.size(),
            state.links.size(),
            latestActionLabel(state)));
        return true;
    }

    String consumeRecoveryNotice() {
        String notice = recoveryNotice;
        recoveryNotice = "";
        return notice;
    }

    List<StoredVersion> listVersions() {
        File[] files = versionFiles();
        List<StoredVersion> versions = new ArrayList<>();
        for (File versionFile : files) {
            int peopleCount = -1;
            int linkCount = -1;
            String action = "";
            try {
                JSONObject metadata = new JSONObject(readFile(metadataFileFor(versionFile)));
                peopleCount = Math.max(0, metadata.optInt("peopleCount", 0));
                linkCount = Math.max(0, metadata.optInt("linkCount", 0));
                action = safeText(metadata.optString("actionLabel", ""), 120);
            } catch (Exception ignored) {
                // Older snapshots have no index. They remain restorable without parsing on the UI thread.
            }
            versions.add(new StoredVersion(
                versionFile,
                versionFile.lastModified(),
                peopleCount,
                linkCount,
                action));
        }
        return versions;
    }

    TreeState loadVersion(StoredVersion version) throws Exception {
        if (version == null || version.file == null || !version.file.exists()) {
            throw new IllegalArgumentException("Версия больше недоступна");
        }
        String expectedParent = versionsDirectory.getCanonicalPath();
        String actualParent = version.file.getCanonicalFile().getParent();
        if (!expectedParent.equals(actualParent)) {
            throw new IllegalArgumentException("Некорректный файл версии");
        }
        return parse(readFile(version.file));
    }

    private void writeCurrent(byte[] data) throws Exception {
        writeAtomic(atomicFile, data);
    }

    private void enqueueVersion(PendingVersion version) {
        pendingVersion.set(version);
        if (!versionWriterRunning.compareAndSet(false, true)) return;
        versionExecutor.execute(() -> {
            try {
                while (true) {
                    PendingVersion next = pendingVersion.getAndSet(null);
                    if (next == null) break;
                    try {
                        writeVersion(next);
                    } catch (Exception error) {
                        DiagnosticsLogger.handled(appContext, "save.version", error);
                        // The main file is already committed; a later save will retry a snapshot.
                    }
                }
            } finally {
                versionWriterRunning.set(false);
                if (pendingVersion.get() != null) enqueueVersion(pendingVersion.get());
            }
        });
    }

    private void writeVersion(PendingVersion version) throws Exception {
        if (!versionsDirectory.exists() && !versionsDirectory.mkdirs()) {
            throw new IllegalStateException("Не удалось создать каталог версий");
        }
        long now = System.currentTimeMillis();
        File versionFile = new File(versionsDirectory, "version-" + now + ".ftree");
        int suffix = 2;
        while (versionFile.exists()) {
            versionFile = new File(versionsDirectory, "version-" + now + "-" + suffix++ + ".ftree");
        }
        writeAtomic(new AtomicFile(versionFile), version.data);
        versionFile.setLastModified(now);
        JSONObject metadata = new JSONObject()
            .put("savedAt", now)
            .put("peopleCount", version.peopleCount)
            .put("linkCount", version.linkCount)
            .put("actionLabel", version.actionLabel);
        writeAtomic(
            new AtomicFile(metadataFileFor(versionFile)),
            metadata.toString().getBytes(StandardCharsets.UTF_8));
        pruneVersions();
    }

    private void pruneVersions() {
        File[] versions = versionFiles();
        for (int i = VERSION_LIMIT; i < versions.length; i++) {
            deleteAtomicFiles(versions[i]);
            deleteAtomicFiles(metadataFileFor(versions[i]));
        }
    }

    private File[] versionFiles() {
        File[] files = versionsDirectory.listFiles((directory, name) ->
            name.startsWith("version-") && name.endsWith(".ftree"));
        if (files == null) return new File[0];
        Arrays.sort(files, Comparator
            .comparingLong(File::lastModified)
            .reversed()
            .thenComparing(File::getName, Comparator.reverseOrder()));
        return files;
    }

    private static void writeAtomic(AtomicFile destination, byte[] data) throws Exception {
        FileOutputStream output = null;
        try {
            output = destination.startWrite();
            output.write(data);
            destination.finishWrite(output);
        } catch (Exception error) {
            if (output != null) destination.failWrite(output);
            throw error;
        }
    }

    private static String readAtomicFile(AtomicFile source) throws Exception {
        try (FileInputStream input = source.openRead()) {
            return readAll(input);
        }
    }

    private static File backupFileFor(File source) {
        return new File(source.getPath() + ".bak");
    }

    private static File metadataFileFor(File versionFile) {
        return new File(versionFile.getPath() + ".meta");
    }

    private static void deleteAtomicFiles(File base) {
        base.delete();
        backupFileFor(base).delete();
        new File(base.getPath() + ".new").delete();
    }

    private static String latestActionLabel(TreeState state) {
        if (state == null || state.history == null || state.history.isEmpty()) return "";
        return safeText(state.history.get(0).label, 120);
    }

    private static final class PendingVersion {
        final byte[] data;
        final int peopleCount;
        final int linkCount;
        final String actionLabel;

        PendingVersion(byte[] data, int peopleCount, int linkCount, String actionLabel) {
            this.data = data;
            this.peopleCount = peopleCount;
            this.linkCount = linkCount;
            this.actionLabel = actionLabel == null ? "" : actionLabel;
        }
    }

    static final class StoredVersion {
        final long savedAt;
        final int peopleCount;
        final int linkCount;
        final String actionLabel;
        private final File file;

        private StoredVersion(File file, long savedAt, int peopleCount, int linkCount, String actionLabel) {
            this.file = file;
            this.savedAt = savedAt;
            this.peopleCount = peopleCount;
            this.linkCount = linkCount;
            this.actionLabel = actionLabel == null ? "" : actionLabel;
        }
    }

    TreeState parse(String text) throws Exception {
        String value = text == null ? "" : text.trim();
        if (looksLikeGedcom(value)) return parseGedcom(value);

        JSONObject document = new JSONObject(value);
        boolean viewPackage = "view".equals(document.optString("mode", ""));
        JSONObject root = document.optJSONObject("state");
        if (root == null) root = document;

        TreeState state = new TreeState();
        JSONObject people = root.optJSONObject("people");
        JSONObject positions = root.optJSONObject("positions");
        Map<String, String> idMap = new HashMap<>();
        Set<String> usedIds = new HashSet<>();
        if (people != null) {
            Iterator<String> ids = people.keys();
            while (ids.hasNext()) {
                String key = ids.next();
                JSONObject item = people.optJSONObject(key);
                if (item == null) continue;
                Person person = parsePerson(key, item, state.people.size());
                String originalId = person.id == null || person.id.trim().isEmpty() ? key : person.id.trim();
                String id = uniqueId(originalId, usedIds, "p");
                idMap.put(originalId, id);
                idMap.put(key, id);
                person.id = id;
                JSONObject pos = positions == null ? null : positions.optJSONObject(key);
                if (pos == null && positions != null) pos = positions.optJSONObject(originalId);
                person.x = (float) (pos == null ? item.optDouble("x", Float.NaN) : pos.optDouble("x", Float.NaN));
                person.y = (float) (pos == null ? item.optDouble("y", Float.NaN) : pos.optDouble("y", Float.NaN));
                state.people.put(person.id, person);
            }
        }

        JSONArray links = root.optJSONArray("links");
        if (links != null) {
            for (int i = 0; i < links.length(); i++) {
                JSONObject item = links.optJSONObject(i);
                if (item == null) continue;
                String type = item.optString("type", "family");
                if ("manual".equals(type)) type = "family";
                if (!"parent".equals(type) && !"partner".equals(type) && !"sibling".equals(type) && !"family".equals(type)) continue;
                String from = remapId(idMap, item.optString("from", ""));
                String to = remapId(idMap, item.optString("to", ""));
                if (!state.people.containsKey(from) || !state.people.containsKey(to) || from.equals(to)) continue;
                state.links.add(new Relation(item.optString("id", makeId("l")), type, from, to, item.optString("side", "right")));
            }
        }

        parseGuides(root.optJSONArray("guides"), state);
        parseSettings(root.optJSONObject("settings"), state);
        parseHistory(root.optJSONArray("history"), state);

        state.rootId = remapId(idMap, root.optString("rootId", state.people.isEmpty() ? "" : state.people.values().iterator().next().id));
        if (!state.people.containsKey(state.rootId) && !state.people.isEmpty()) state.rootId = state.people.values().iterator().next().id;
        state.selectedId = remapId(idMap, root.optString("selectedId", state.rootId));
        if (!state.people.containsKey(state.selectedId)) state.selectedId = state.rootId;
        if (viewPackage) {
            state.readerMode = true;
            state.editLocked = false;
        }
        migrateInlineMedia(state);
        refreshColors(state);
        return state;
    }

    JSONObject toJson(TreeState state) throws Exception {
        JSONObject root = new JSONObject();
        JSONObject people = new JSONObject();
        JSONObject positions = new JSONObject();
        int index = 0;
        for (Person person : state.people.values()) {
            JSONObject item = new JSONObject();
            item.put("id", person.id);
            item.put("name", person.name);
            item.put("born", person.born);
            item.put("died", person.died);
            item.put("bornDay", person.bornDay);
            item.put("bornMonth", person.bornMonth);
            item.put("bornYear", person.bornYear);
            item.put("diedDay", person.diedDay);
            item.put("diedMonth", person.diedMonth);
            item.put("diedYear", person.diedYear);
            item.put("place", person.place);
            item.put("notes", person.notes);
            item.put("photoId", safeMediaId(person.photoMediaId));
            item.put("photo", person.photoMediaId.isEmpty() ? safeDataUrl(person.photo) : "");
            item.put("gender", PersonGender.resolve(person));
            item.put("genderManual", person.genderManual);
            item.put("memories", memoriesToJson(person));
            item.put("pinned", person.pinned);
            item.put("colorMode", normalizeColorMode(person.colorMode));
            item.put("manualColor", safeColorString(person.manualColor, TreeState.colorString(TreeState.colorFor(person.name, index))));
            people.put(person.id, item);
            positions.put(person.id, new JSONObject().put("x", person.x).put("y", person.y));
            index++;
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

        root.put("format", "ru.drshapaya.familytree.ftree");
        root.put("version", 2);
        root.put("exportedAt", isoNow());
        root.put("mode", state.readerMode ? "view" : "copy");
        root.put("rootId", state.rootId);
        root.put("selectedId", state.selectedId);
        root.put("people", people);
        root.put("positions", positions);
        root.put("links", links);
        root.put("guides", guidesToJson(state));
        root.put("settings", settingsToJson(state));
        root.put("history", historyToJson(state));
        return root;
    }

    String exportText(TreeState state) throws Exception {
        return toJson(state).toString(2);
    }

    String exportPackageText(TreeState state, String mode) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", "ru.drshapaya.familytree.ftree");
        root.put("version", 1);
        root.put("exportedAt", isoNow());
        root.put("mode", "view".equals(mode) ? "view" : "copy");
        root.put("title", "Семейное древо");
        root.put("state", toJson(state));
        return root.toString(2);
    }

    private static Person parsePerson(String key, JSONObject item, int index) {
        Person person = new Person(item.optString("id", key));
        person.name = safeText(item.optString("name", "Без имени"), 160);
        person.born = safeText(item.optString("born", ""), 32);
        person.died = safeText(item.optString("died", ""), 32);
        DateParts born = parseLooseDate(person.born);
        DateParts died = parseLooseDate(person.died);
        person.bornDay = safeDatePart(item.optString("bornDay", born.day), 31);
        person.bornMonth = safeDatePart(item.optString("bornMonth", born.month), 12);
        person.bornYear = safeYear(item.optString("bornYear", item.optString("bornYear", born.year)));
        if (person.bornYear.isEmpty()) person.bornYear = safeYear(item.optString("born", ""));
        person.diedDay = safeDatePart(item.optString("diedDay", died.day), 31);
        person.diedMonth = safeDatePart(item.optString("diedMonth", died.month), 12);
        person.diedYear = safeYear(item.optString("diedYear", died.year));
        if (person.diedYear.isEmpty()) person.diedYear = safeYear(item.optString("died", ""));
        person.place = safeText(item.optString("place", ""), 240);
        person.notes = safeText(item.optString("notes", ""), 4000);
        person.photoMediaId = safeMediaId(item.optString("photoId", ""));
        person.photo = safeDataUrl(firstPhotoValue(item));
        person.genderManual = item.optBoolean("genderManual", item.has("gender"));
        person.gender = PersonGender.normalize(item.optString("gender", ""));
        if (!person.genderManual) person.gender = PersonGender.infer(person.name);
        parseMemories(item.optJSONArray("memories"), person);
        person.pinned = item.optBoolean("pinned", false);
        person.colorMode = normalizeColorMode(item.optString("colorMode", "auto-name"));
        person.manualColor = safeColorString(item.optString("manualColor", item.optString("color", "")), TreeState.colorString(TreeState.colorFor(person.name, index)));
        person.color = TreeState.displayColor(person, index);
        return person;
    }

    private static void parseMemories(JSONArray source, Person person) {
        if (source == null) return;
        int count = Math.min(80, source.length());
        for (int i = 0; i < count; i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            Memory memory = new Memory();
            memory.id = item.optString("id", makeId("m"));
            memory.type = normalizeMemoryType(item.optString("type", "story"));
            memory.title = safeText(item.optString("title", "Воспоминание"), 120);
            memory.text = safeText(item.optString("text", item.optString("note", "")), 6000);
            memory.filename = safeText(item.optString("filename", ""), 180);
            memory.mimeType = safeText(item.optString("mimeType", ""), 120);
            String legacyData = item.optString("data", item.optString("src", ""));
            memory.data = legacyData.trim().toLowerCase(Locale.ROOT).startsWith("data:")
                ? safeAttachmentDataUrl(legacyData)
                : safeDataUrl(legacyData);
            memory.at = safeText(item.optString("at", isoNow()), 40);
            JSONArray attachments = item.optJSONArray("attachments");
            if (attachments != null) {
                int attachmentCount = Math.min(24, attachments.length());
                for (int attachmentIndex = 0; attachmentIndex < attachmentCount; attachmentIndex++) {
                    JSONObject attachmentItem = attachments.optJSONObject(attachmentIndex);
                    if (attachmentItem == null) continue;
                    MemoryAttachment attachment = new MemoryAttachment();
                    attachment.id = attachmentItem.optString("id", makeId("a"));
                    attachment.filename = safeText(attachmentItem.optString("filename", "Файл"), 180);
                    attachment.mimeType = safeText(attachmentItem.optString("mimeType", "application/octet-stream"), 120);
                    attachment.type = normalizeMemoryType(attachmentItem.optString("type", "document"));
                    attachment.mediaId = safeMediaId(attachmentItem.optString("mediaId", ""));
                    attachment.size = Math.max(0L, attachmentItem.optLong("size", 0L));
                    attachment.data = safeAttachmentDataUrl(attachmentItem.optString("data", ""));
                    if (!attachment.mediaId.isEmpty() || !attachment.data.isEmpty()) {
                        memory.attachments.add(attachment);
                    }
                }
            } else if (!memory.data.isEmpty()) {
                MemoryAttachment attachment = new MemoryAttachment();
                attachment.id = makeId("a");
                attachment.filename = memory.filename.isEmpty() ? "Файл" : memory.filename;
                attachment.mimeType = memory.mimeType.isEmpty() ? "application/octet-stream" : memory.mimeType;
                attachment.type = normalizeMemoryType(memory.type);
                attachment.data = memory.data;
                memory.attachments.add(attachment);
            }
            person.memories.add(memory);
        }
    }

    private static JSONArray memoriesToJson(Person person) throws Exception {
        JSONArray array = new JSONArray();
        for (Memory memory : person.memories) {
            JSONArray attachments = new JSONArray();
            for (MemoryAttachment attachment : memory.attachments) {
                attachments.put(new JSONObject()
                    .put("id", emptyToId(attachment.id, "a"))
                    .put("filename", safeText(attachment.filename, 180))
                    .put("mimeType", safeText(attachment.mimeType, 120))
                    .put("type", normalizeMemoryType(attachment.type))
                    .put("mediaId", safeMediaId(attachment.mediaId))
                    .put("size", Math.max(0L, attachment.size))
                    .put(
                        "data",
                        attachment.mediaId.isEmpty()
                            ? safeAttachmentDataUrl(attachment.data)
                            : ""));
            }
            array.put(new JSONObject()
                .put("id", emptyToId(memory.id, "m"))
                .put("type", normalizeMemoryType(memory.type))
                .put("title", safeText(memory.title, 120))
                .put("text", safeText(memory.text, 6000))
                .put("filename", memory.attachments.isEmpty() ? safeText(memory.filename, 180) : "")
                .put("mimeType", memory.attachments.isEmpty() ? safeText(memory.mimeType, 120) : "")
                .put("data", memory.attachments.isEmpty() ? safeAttachmentDataUrl(memory.data) : "")
                .put("attachments", attachments)
                .put("at", safeText(memory.at, 40)));
        }
        return array;
    }

    private void migrateInlineMedia(TreeState state) {
        if (state == null) return;
        for (Person person : state.people.values()) {
            if (person == null) continue;
            if (person.photoMediaId.isEmpty() && person.photo != null && !person.photo.isEmpty()) {
                try {
                    TreeMediaStore.StoredMedia stored = mediaStore.importDataUrl(
                        person.photo,
                        true,
                        "photo.jpg");
                    person.photoMediaId = stored.id;
                    person.photo = "";
                } catch (Exception ignored) {
                    // Keep the inline value until a later migration can safely commit it.
                }
            }
            for (Memory memory : person.memories) {
                if (memory == null) continue;
                boolean allExternal = !memory.attachments.isEmpty();
                for (MemoryAttachment attachment : memory.attachments) {
                    if (attachment == null) continue;
                    if (attachment.mediaId.isEmpty()
                        && attachment.data != null
                        && !attachment.data.isEmpty()) {
                        try {
                            TreeMediaStore.StoredMedia stored = mediaStore.importDataUrl(
                                attachment.data,
                                false,
                                attachment.filename);
                            attachment.mediaId = stored.id;
                            attachment.size = stored.size;
                            if (attachment.mimeType == null || attachment.mimeType.isEmpty()) {
                                attachment.mimeType = stored.mimeType;
                            }
                            attachment.data = "";
                        } catch (Exception ignored) {
                            allExternal = false;
                        }
                    }
                    if (attachment.mediaId.isEmpty()) allExternal = false;
                }
                if (allExternal) memory.data = "";
            }
        }
    }

    private static void parseGuides(JSONArray source, TreeState state) {
        if (source == null) return;
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            Guide guide = new Guide();
            guide.id = item.optString("id", makeId("g"));
            guide.axis = "v".equals(item.optString("axis", "h")) ? "v" : "h";
            guide.position = (float) item.optDouble("position", 0);
            guide.color = safeColorString(item.optString("color", "#2f7d75"), "#2f7d75");
            guide.label = safeText(item.optString("label", ""), 32);
            state.guides.add(guide);
        }
    }

    private static JSONArray guidesToJson(TreeState state) throws Exception {
        JSONArray array = new JSONArray();
        for (Guide guide : state.guides) {
            array.put(new JSONObject()
                .put("id", emptyToId(guide.id, "g"))
                .put("axis", "v".equals(guide.axis) ? "v" : "h")
                .put("position", guide.position)
                .put("color", safeColorString(guide.color, "#2f7d75"))
                .put("label", safeText(guide.label, 32)));
        }
        return array;
    }

    private static void parseSettings(JSONObject settings, TreeState state) {
        if (settings == null) return;
        state.theme = normalizeTheme(settings.optString("theme", state.theme));
        state.printScale = Math.max(55, Math.min(130, settings.optInt("printScale", state.printScale)));
        state.editLocked = settings.optBoolean("editLocked", state.editLocked);
        state.historyHidden = settings.optBoolean("historyHidden", state.historyHidden);
        state.inspectorHidden = settings.optBoolean("inspectorHidden", state.inspectorHidden);
        state.adminCollapsed = settings.optBoolean("adminCollapsed", state.adminCollapsed);
        state.readerMode = settings.optBoolean("readerMode", state.readerMode);
        state.onboardingCompleted = settings.optBoolean("onboardingCompleted", state.onboardingCompleted);
        state.onboardingOffered = settings.optBoolean("onboardingOffered", state.onboardingOffered);
        state.guidesVisible = !settings.has("guidesVisible") || settings.optBoolean("guidesVisible", true);
        state.hideCardDetails = settings.optBoolean("hideCardDetails", state.hideCardDetails);
        state.compactCards = settings.optBoolean("compactCards", state.compactCards);
        state.focusTree = settings.optBoolean("focusTree", state.focusTree);
        state.parentLineMode = "orthogonal".equals(settings.optString("parentLineMode", "smart")) ? "orthogonal" : "smart";
    }

    private static JSONObject settingsToJson(TreeState state) throws Exception {
        return new JSONObject()
            .put("theme", normalizeTheme(state.theme))
            .put("printScale", Math.max(55, Math.min(130, state.printScale)))
            .put("editLocked", state.editLocked)
            .put("historyHidden", state.historyHidden)
            .put("inspectorHidden", state.inspectorHidden)
            .put("adminCollapsed", state.adminCollapsed)
            .put("readerMode", state.readerMode)
            .put("onboardingCompleted", state.onboardingCompleted)
            .put("onboardingOffered", state.onboardingOffered)
            .put("guidesVisible", state.guidesVisible)
            .put("hideCardDetails", state.hideCardDetails)
            .put("compactCards", state.compactCards)
            .put("focusTree", state.focusTree)
            .put("parentLineMode", "orthogonal".equals(state.parentLineMode) ? "orthogonal" : "smart");
    }

    private static void parseHistory(JSONArray source, TreeState state) {
        if (source == null) return;
        int count = Math.min(30, source.length());
        for (int i = 0; i < count; i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null) continue;
            HistoryEntry entry = new HistoryEntry();
            entry.id = item.optString("id", makeId("h"));
            entry.label = safeText(item.optString("label", "Действие"), 120);
            entry.detail = safeText(item.optString("detail", ""), 160);
            entry.at = safeText(item.optString("at", isoNow()), 40);
            state.history.add(entry);
        }
    }

    private static JSONArray historyToJson(TreeState state) throws Exception {
        JSONArray array = new JSONArray();
        int count = Math.min(30, state.history.size());
        for (int i = 0; i < count; i++) {
            HistoryEntry entry = state.history.get(i);
            array.put(new JSONObject()
                .put("id", emptyToId(entry.id, "h"))
                .put("label", safeText(entry.label, 120))
                .put("detail", safeText(entry.detail, 160))
                .put("at", safeText(entry.at, 40)));
        }
        return array;
    }

    private static TreeState parseGedcom(String text) {
        TreeState state = new TreeState();
        Map<String, String> pointers = new HashMap<>();
        Map<String, FamilyDraft> families = new HashMap<>();
        String currentKind = "";
        String currentId = "";
        String currentEvent = "";

        String[] lines = text.split("\\r?\\n");
        for (String raw : lines) {
            GedcomLine line = GedcomLine.parse(raw);
            if (line == null) continue;
            if (line.level == 0) {
                currentEvent = "";
                if ("INDI".equals(line.tag)) {
                    Person person = new Person(makeId("p"));
                    person.name = "Без имени";
                    person.manualColor = TreeState.colorString(TreeState.colorFor(person.name, state.people.size()));
                    person.color = TreeState.displayColor(person, state.people.size());
                    pointers.put(line.pointer, person.id);
                    state.people.put(person.id, person);
                    currentKind = "person";
                    currentId = person.id;
                } else if ("FAM".equals(line.tag)) {
                    currentKind = "family";
                    currentId = line.pointer.isEmpty() ? makeId("f") : line.pointer;
                    families.put(currentId, new FamilyDraft());
                } else {
                    currentKind = "";
                    currentId = "";
                }
                continue;
            }
            if ("person".equals(currentKind)) {
                Person person = state.people.get(currentId);
                if (person == null) continue;
                if (line.level == 1 && "NAME".equals(line.tag)) person.name = cleanGedcomName(line.value);
                if (line.level == 1 && "SEX".equals(line.tag)) {
                    person.gender = "M".equalsIgnoreCase(line.value) ? PersonGender.MALE
                        : "F".equalsIgnoreCase(line.value) ? PersonGender.FEMALE : PersonGender.UNKNOWN;
                    person.genderManual = !PersonGender.UNKNOWN.equals(person.gender);
                }
                if (line.level == 1 && "NOTE".equals(line.tag)) person.notes = joinLines(person.notes, safeText(line.value, 1000));
                if (line.level == 1 && ("BIRT".equals(line.tag) || "DEAT".equals(line.tag))) currentEvent = "BIRT".equals(line.tag) ? "born" : "died";
                if (line.level == 2 && "DATE".equals(line.tag) && !currentEvent.isEmpty()) applyGedcomDate(person, currentEvent, line.value);
                if (line.level == 2 && "PLAC".equals(line.tag) && "born".equals(currentEvent)) person.place = safeText(line.value, 240);
            } else if ("family".equals(currentKind)) {
                FamilyDraft family = families.get(currentId);
                if (family == null) continue;
                if ("HUSB".equals(line.tag) || "WIFE".equals(line.tag)) family.parents.add(line.value);
                if ("CHIL".equals(line.tag)) family.children.add(line.value);
            }
        }

        for (FamilyDraft family : families.values()) {
            java.util.List<String> parents = new java.util.ArrayList<>();
            for (String pointer : family.parents) {
                String id = pointers.get(pointer);
                if (id != null) parents.add(id);
            }
            java.util.List<String> children = new java.util.ArrayList<>();
            for (String pointer : family.children) {
                String id = pointers.get(pointer);
                if (id != null) children.add(id);
            }
            if (parents.size() >= 2) state.addRelation("partner", parents.get(0), parents.get(1), "right");
            for (String parent : parents) for (String child : children) state.addRelation("parent", parent, child);
        }
        state.rootId = state.people.isEmpty() ? "" : state.people.values().iterator().next().id;
        state.selectedId = state.rootId;
        TreeLayoutEngine.layout(state);
        refreshColors(state);
        return state;
    }

    private static void refreshColors(TreeState state) {
        int index = 0;
        for (Person person : state.people.values()) {
            person.colorMode = normalizeColorMode(person.colorMode);
            person.color = TreeState.displayColor(person, index++);
        }
    }

    private static String readFile(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            return readAll(input);
        }
    }

    static String readAll(java.io.InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_INTERNAL_TREE_BYTES) {
                throw new java.io.IOException("Файл дерева превышает 256 МБ");
            }
            output.write(buffer, 0, read);
        }
        return output.toString("UTF-8");
    }

    private static boolean looksLikeGedcom(String text) {
        return text.matches("(?is)^\\s*0\\s+HEAD.*");
    }

    private static String makeId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String uniqueId(String source, Set<String> used, String prefix) {
        String base = safeId(source, prefix);
        String candidate = base;
        int suffix = 2;
        while (used.contains(candidate)) candidate = base + "_" + suffix++;
        used.add(candidate);
        return candidate;
    }

    private static String safeId(String source, String prefix) {
        String value = source == null ? "" : source.trim().replaceAll("[^A-Za-z0-9_\\-]", "_");
        return value.isEmpty() ? makeId(prefix) : value;
    }

    private static String remapId(Map<String, String> idMap, String id) {
        String key = id == null ? "" : id;
        String mapped = idMap.get(key);
        return mapped == null ? key : mapped;
    }

    private static String emptyToId(String value, String prefix) {
        return value == null || value.trim().isEmpty() ? makeId(prefix) : value.trim();
    }

    private static String isoNow() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date());
    }

    private static String safeText(String value, int max) {
        String text = value == null ? "" : value.replace('\u0000', ' ').trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String firstPhotoValue(JSONObject item) {
        String value = item.optString("photo", "");
        if (value == null || value.trim().isEmpty()) value = item.optString("photoUrl", "");
        if (value == null || value.trim().isEmpty()) value = item.optString("avatar", "");
        if (value == null || value.trim().isEmpty()) value = item.optString("image", "");
        return value;
    }

    private static String safeDataUrl(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return "";
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("data:image/") && text.length() < PHOTO_DATA_LIMIT) return text;
        if (text.length() < PHOTO_DATA_LIMIT && text.matches("^[A-Za-z0-9+/=\\r\\n]+$")) return "data:image/jpeg;base64," + text.replaceAll("\\s+", "");
        return "";
    }

    private static String safeAttachmentDataUrl(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() >= PHOTO_DATA_LIMIT) return "";
        String lower = text.toLowerCase(Locale.ROOT);
        int marker = lower.indexOf(";base64,");
        if (!lower.startsWith("data:") || marker < 6 || marker + 8 >= text.length()) return "";
        return text;
    }

    private static String safeMediaId(String value) {
        String text = value == null ? "" : value.trim();
        return text.matches("(photo|attachment)_[A-Za-z0-9._-]{8,170}") ? text : "";
    }

    private static String safeDatePart(String value, int max) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return "";
        int number;
        try {
            number = Integer.parseInt(digits);
        } catch (Exception ignored) {
            return "";
        }
        if (number <= 0) return "";
        if (max == 9999) return String.format(Locale.US, "%04d", Math.min(9999, number));
        return String.format(Locale.US, "%02d", Math.min(max, number));
    }

    private static String safeYear(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.length() >= 4) digits = digits.substring(digits.length() - 4);
        return safeDatePart(digits, 9999);
    }

    private static String safeColorString(String value, String fallback) {
        int parsed = TreeState.parseColor(value, TreeState.parseColor(fallback, 0xff84c7ae));
        return TreeState.colorString(parsed);
    }

    private static String normalizeColorMode(String mode) {
        if ("manual".equals(mode) || "auto-surname".equals(mode)) return mode;
        return "auto-name";
    }

    private static String normalizeMemoryType(String type) {
        if ("photo".equals(type) || "document".equals(type) || "audio".equals(type) || "video".equals(type) || "source".equals(type)) return type;
        return "story";
    }

    private static String normalizeTheme(String theme) {
        if ("print".equals(theme)) return "clean";
        if ("dark".equals(theme) || "clean".equals(theme)) return theme;
        return "light";
    }

    private static String cleanGedcomName(String value) {
        String text = value == null ? "" : value.replace("/", "").trim();
        return text.isEmpty() ? "Без имени" : safeText(text, 160);
    }

    private static String joinLines(String first, String second) {
        if (first == null || first.isEmpty()) return second == null ? "" : second;
        if (second == null || second.isEmpty()) return first;
        return first + "\n" + second;
    }

    private static DateParts parseLooseDate(String value) {
        DateParts parts = new DateParts();
        if (value == null) return parts;
        String[] tokens = value.replaceAll("[^0-9.\\-/ ]", " ").trim().split("[.\\-/ ]+");
        if (tokens.length >= 3) {
            parts.day = safeDatePart(tokens[0], 31);
            parts.month = safeDatePart(tokens[1], 12);
            parts.year = safeYear(tokens[2]);
        } else if (tokens.length == 1) {
            parts.year = safeYear(tokens[0]);
        }
        return parts;
    }

    private static void applyGedcomDate(Person person, String prefix, String value) {
        DateParts parts = parseGedcomDate(value);
        if ("born".equals(prefix)) {
            person.bornDay = parts.day;
            person.bornMonth = parts.month;
            person.bornYear = parts.year;
        } else {
            person.diedDay = parts.day;
            person.diedMonth = parts.month;
            person.diedYear = parts.year;
        }
    }

    private static DateParts parseGedcomDate(String value) {
        DateParts parts = new DateParts();
        String text = value == null ? "" : value.trim().toUpperCase(Locale.US);
        Map<String, String> months = new HashMap<>();
        months.put("JAN", "01"); months.put("FEB", "02"); months.put("MAR", "03"); months.put("APR", "04");
        months.put("MAY", "05"); months.put("JUN", "06"); months.put("JUL", "07"); months.put("AUG", "08");
        months.put("SEP", "09"); months.put("OCT", "10"); months.put("NOV", "11"); months.put("DEC", "12");
        String[] tokens = text.split("\\s+");
        for (String token : tokens) {
            if (months.containsKey(token)) parts.month = months.get(token);
            else if (token.matches("\\d{4}")) parts.year = safeYear(token);
            else if (token.matches("\\d{1,2}") && parts.day.isEmpty()) parts.day = safeDatePart(token, 31);
        }
        if (parts.year.isEmpty()) parts.year = safeYear(text);
        return parts;
    }

    private static String formattedDate(Person person, boolean born) {
        String day = born ? person.bornDay : person.diedDay;
        String month = born ? person.bornMonth : person.diedMonth;
        String year = born ? person.bornYear : person.diedYear;
        if (!day.isEmpty() && !month.isEmpty() && !year.isEmpty()) return day + "." + month + "." + year;
        return year;
    }

    static TreeState demoState() {
        TreeState state = new TreeState();
        Person root = state.addPerson("Алексей Иванов", 4000, 3000);
        root.bornYear = "1978";
        root.place = "Красноярск";
        state.rootId = root.id;
        state.selectedId = root.id;

        String[] surnames = {"Иванов", "Петров", "Сидоров", "Кузнецов", "Смирнов", "Федоров"};
        String[] names = {"Мария", "Нина", "Виктор", "Анна", "Павел", "Елена", "Сергей", "Ирина", "Дмитрий", "Ольга"};
        for (int i = 0; i < 119; i++) {
            Person person = state.addPerson(names[i % names.length] + " " + surnames[i % surnames.length], 0, 0);
            person.bornYear = String.valueOf(1930 + (i * 7) % 85);
            person.place = i % 3 == 0 ? "Красноярский край" : "";
        }
        TreeLayoutEngine.layout(state);
        String[] ids = state.people.keySet().toArray(new String[0]);
        for (int i = 1; i < ids.length; i++) {
            int parent = Math.max(0, (i - 1) / 2);
            state.addRelation("parent", ids[parent], ids[i]);
            if (i % 9 == 0 && i + 1 < ids.length) state.addRelation("partner", ids[i], ids[i + 1]);
            if (i % 11 == 0 && i + 2 < ids.length) state.addRelation("sibling", ids[i], ids[i + 2]);
        }
        return state;
    }

    private static final class DateParts {
        String day = "";
        String month = "";
        String year = "";
    }

    private static final class FamilyDraft {
        final java.util.List<String> parents = new java.util.ArrayList<>();
        final java.util.List<String> children = new java.util.ArrayList<>();
    }

    private static final class GedcomLine {
        final int level;
        final String pointer;
        final String tag;
        final String value;

        GedcomLine(int level, String pointer, String tag, String value) {
            this.level = level;
            this.pointer = pointer;
            this.tag = tag;
            this.value = value;
        }

        static GedcomLine parse(String raw) {
            if (raw == null) return null;
            String line = raw.trim();
            if (line.isEmpty()) return null;
            String[] parts = line.split("\\s+", 3);
            if (parts.length < 2) return null;
            int level;
            try {
                level = Integer.parseInt(parts[0]);
            } catch (Exception ignored) {
                return null;
            }
            String pointer = "";
            String tag;
            String value = "";
            if (parts[1].startsWith("@") && parts.length >= 3) {
                pointer = parts[1];
                String[] rest = parts[2].split("\\s+", 2);
                tag = rest[0];
                if (rest.length > 1) value = rest[1];
            } else {
                tag = parts[1];
                if (parts.length > 2) value = parts[2];
            }
            return new GedcomLine(level, pointer, tag, value);
        }
    }
}
