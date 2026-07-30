package ru.drshapaya.androidft2;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class OnlineTreeConfig {
    private static final String PREFS = "androidft-online-v1";
    private static final String TOKEN = "github-token";
    private static final String INVITE_SECRET = "invite-secret";
    private static final String OAUTH_STATE = "oauth-state";
    private static final String OAUTH_VERIFIER = "oauth-verifier";

    private final SharedPreferences preferences;
    private final OnlineSecrets secrets;
    private final File baseFile;
    private final File pendingFile;
    private final AtomicFile atomicBase;
    private final AtomicFile atomicPending;

    OnlineTreeConfig(Context context) {
        Context app = context.getApplicationContext();
        preferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        secrets = new OnlineSecrets(app);
        baseFile = new File(app.getFilesDir(), "androidft-online-base.json");
        pendingFile = new File(app.getFilesDir(), "androidft-online-pending.json");
        atomicBase = new AtomicFile(baseFile);
        atomicPending = new AtomicFile(pendingFile);
    }

    String token() { return secrets.get(TOKEN); }
    void setToken(String value) { secrets.put(TOKEN, value); }
    String inviteSecret() { return secrets.get(INVITE_SECRET); }
    void setInviteSecret(String value) { secrets.put(INVITE_SECRET, value); }
    String oauthState() { return secrets.get(OAUTH_STATE); }
    String oauthVerifier() { return secrets.get(OAUTH_VERIFIER); }

    void setOAuthRequest(String state, String verifier) {
        secrets.put(OAUTH_STATE, state);
        secrets.put(OAUTH_VERIFIER, verifier);
    }

    void clearOAuthRequest() {
        secrets.remove(OAUTH_STATE);
        secrets.remove(OAUTH_VERIFIER);
    }

    String login() { return preferences.getString("login", ""); }
    String accountId() { return preferences.getString("accountId", ""); }
    String treeAccountId() { return preferences.getString("treeAccountId", ""); }
    String owner() { return preferences.getString("owner", ""); }
    String repo() { return preferences.getString("repo", ""); }
    String treeId() { return preferences.getString("treeId", ""); }
    String gistId() { return preferences.getString("gistId", ""); }
    String lastSha() { return preferences.getString("lastSha", ""); }
    String lastEtag() { return preferences.getString("lastEtag", ""); }
    boolean isOwner() { return preferences.getBoolean("isOwner", false); }
    boolean canEdit() { return preferences.getBoolean("canEdit", true); }
    long lastSyncAt() { return preferences.getLong("lastSyncAt", 0L); }
    boolean localEditPending() { return preferences.getBoolean("localEditPending", false); }
    Set<String> remoteMediaIds() {
        Set<String> value = preferences.getStringSet("remoteMediaIds", Collections.emptySet());
        return value == null ? new HashSet<>() : new HashSet<>(value);
    }

    boolean signedIn() { return !token().isEmpty() && !login().isEmpty(); }
    boolean connected() { return signedIn() && !owner().isEmpty() && !repo().isEmpty(); }

    void setIdentity(String login, String accountId) {
        requireCommit(preferences.edit()
            .putString("login", safe(login))
            .putString("accountId", safe(accountId)));
    }

    void bindTreeToAccount(String accountId) {
        requireCommit(preferences.edit().putString("treeAccountId", safe(accountId)));
    }

    void setLocalEditPending(boolean pending) {
        requireCommit(preferences.edit().putBoolean("localEditPending", pending));
    }

    void setCanEdit(boolean canEdit) {
        requireCommit(preferences.edit().putBoolean("canEdit", canEdit));
    }

    void setTree(
        String owner,
        String repo,
        String treeId,
        String gistId,
        String inviteSecret,
        boolean isOwner,
        String lastSha
    ) {
        requireCommit(preferences.edit()
            .putString("owner", safe(owner))
            .putString("repo", safe(repo))
            .putString("treeId", safe(treeId))
            .putString("gistId", safe(gistId))
            .putString("lastSha", safe(lastSha))
            .putString("lastEtag", "")
            .putString("treeAccountId", accountId())
            .putBoolean("isOwner", isOwner)
            .putBoolean("canEdit", isOwner)
            .putLong("lastSyncAt", System.currentTimeMillis())
            .putBoolean("localEditPending", false));
        setInviteSecret(inviteSecret);
    }

    void setLastRemote(String sha, String etag) {
        requireCommit(preferences.edit()
            .putString("lastSha", safe(sha))
            .putString("lastEtag", safe(etag))
            .putLong("lastSyncAt", System.currentTimeMillis())
        );
    }

    void markRemoteMedia(String mediaId) {
        if (mediaId == null || mediaId.isEmpty()) return;
        Set<String> ids = remoteMediaIds();
        if (ids.add(mediaId)) {
            requireCommit(preferences.edit().putStringSet("remoteMediaIds", ids));
        }
    }

    void clearTree() {
        requireCommit(preferences.edit()
            .remove("owner")
            .remove("repo")
            .remove("treeId")
            .remove("gistId")
            .remove("lastSha")
            .remove("lastEtag")
            .remove("isOwner")
            .remove("canEdit")
            .remove("treeAccountId")
            .remove("lastSyncAt")
            .remove("remoteMediaIds")
            .remove("localEditPending"));
        secrets.remove(INVITE_SECRET);
        atomicBase.delete();
        atomicPending.delete();
    }

    void signOut() {
        clearTree();
        clearOAuthRequest();
        secrets.remove(TOKEN);
        requireCommit(preferences.edit()
            .remove("login")
            .remove("accountId"));
    }

    synchronized void writeBase(String json) throws Exception {
        writeAtomic(atomicBase, json, "online.base.write");
    }

    synchronized String readBase() {
        return readAtomic(atomicBase);
    }

    synchronized void writePending(String json) throws Exception {
        writeAtomic(atomicPending, json, "online.pending.write");
    }

    synchronized String readPending() {
        return readAtomic(atomicPending);
    }

    synchronized void clearPending() {
        atomicPending.delete();
    }

    String treeFullName() {
        return owner().isEmpty() || repo().isEmpty() ? "" : owner() + "/" + repo();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static void requireCommit(SharedPreferences.Editor editor) {
        if (!editor.commit()) {
            throw new IllegalStateException("Не удалось сохранить состояние синхронизации");
        }
    }

    private static void writeAtomic(AtomicFile file, String json, String area) throws Exception {
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write((json == null ? "" : json).getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
            file.finishWrite(output);
        } catch (Exception error) {
            if (output != null) file.failWrite(output);
            DiagnosticsLogger.handled(null, area, error);
            throw error;
        }
    }

    private static String readAtomic(AtomicFile file) {
        try {
            if (!file.getBaseFile().exists()) return "";
            return new String(file.readFully(), StandardCharsets.UTF_8);
        } catch (Exception error) {
            DiagnosticsLogger.handled(null, "online.state.read", error);
            return "";
        }
    }
}
