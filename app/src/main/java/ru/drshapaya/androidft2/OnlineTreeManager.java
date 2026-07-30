package ru.drshapaya.androidft2;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class OnlineTreeManager {
    interface Listener {
        void onRemoteTree(TreeState remote, String message);
        void onMediaChanged();
        void onEditingPermissionChanged(boolean canEdit);
        void onStatusChanged();
        void onMessage(String message);
    }

    interface AuthListener {
        void onCode(String code, String verificationUrl);
        void onSuccess(String login);
        void onError(String message);
    }

    interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    static final class Participant {
        final String login;
        final String avatarUrl;
        final String role;
        final boolean owner;
        final boolean canEdit;

        Participant(
            String login,
            String avatarUrl,
            String role,
            boolean owner,
            boolean canEdit
        ) {
            this.login = login;
            this.avatarUrl = avatarUrl;
            this.role = role;
            this.owner = owner;
            this.canEdit = owner || canEdit;
        }
    }

    static final class AvailableTree {
        final String owner;
        final String repo;
        final String treeId;
        final boolean ownerAccount;
        final boolean canEdit;
        final long updatedAt;

        AvailableTree(
            String owner,
            String repo,
            String treeId,
            boolean ownerAccount,
            boolean canEdit,
            long updatedAt
        ) {
            this.owner = owner == null ? "" : owner;
            this.repo = repo == null ? "" : repo;
            this.treeId = treeId == null ? "" : treeId;
            this.ownerAccount = ownerAccount;
            this.canEdit = ownerAccount || canEdit;
            this.updatedAt = Math.max(0L, updatedAt);
        }

        String fullName() {
            return owner + "/" + repo;
        }
    }

    private static final String TREE_PATH = "androidft/tree.json";
    private static final long FOREGROUND_INTERVAL = 25_000L;
    private static final long PUSH_DEBOUNCE = 1_800L;
    private static final long JOIN_TIMEOUT = 150_000L;
    private static final long AUTH_NETWORK_RECOVERY_TIMEOUT = 120_000L;
    private static final long INVITATION_WINDOW = 7L * 24L * 3600L * 1000L;
    private static final long MEDIA_MANIFEST_CACHE = 20_000L;
    private static final String OAUTH_REDIRECT_URI = "androidft://oauth/github";

    private final Context context;
    private final TreeStore store;
    private final Listener listener;
    private final OnlineTreeConfig config;
    private final GitHubApi api;
    private final ConnectivityManager connectivityManager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService authWorker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "androidft-github-auth");
        thread.setPriority(Thread.NORM_PRIORITY);
        return thread;
    });
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "androidft-online");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final AtomicBoolean foregroundWorkRunning = new AtomicBoolean(false);
    private final AtomicLong authAttempt = new AtomicLong();
    private final AtomicLong localRevision = new AtomicLong();
    private final AtomicLong sessionGeneration = new AtomicLong();
    private volatile Future<?> authFuture;
    private volatile boolean foregroundChecks;
    private volatile boolean closed;
    private volatile boolean dirty;
    private volatile boolean localChangePending;
    private volatile String pendingStateJson = "";
    private volatile Set<String> pendingMediaIds = new HashSet<>();
    private volatile String status = "Локально";
    private volatile String lastError = "";
    private volatile boolean identityVerified;
    private volatile boolean mediaAuditNeeded = true;
    private long cachedReleaseId;
    private String cachedReleaseTreeId = "";
    private long cachedAssetsAt;
    private Map<String, GitHubApi.ReleaseAsset> cachedAssets = new HashMap<>();

    private final Runnable foregroundTick = new Runnable() {
        @Override public void run() {
            if (!foregroundChecks || closed) return;
            if (foregroundWorkRunning.compareAndSet(false, true)) {
                worker.execute(() -> {
                    try {
                        if (config.connected()) {
                            ensureIdentityVerified();
                            if (!config.connected()) return;
                            refreshEditingPermission();
                            if (config.isOwner()) processJoinRequests();
                            if (dirty) {
                                if (canEdit()) pushPending();
                                else setStatus("Только просмотр · локальные правки сохранены");
                            } else {
                                pullRemote(false);
                            }
                        }
                    } catch (Exception error) {
                        setError(error);
                    } finally {
                        foregroundWorkRunning.set(false);
                        main.post(() -> {
                            if (foregroundChecks && !closed) {
                                main.postDelayed(foregroundTick, FOREGROUND_INTERVAL);
                            }
                        });
                    }
                });
            } else {
                main.postDelayed(this, FOREGROUND_INTERVAL);
            }
        }
    };

    OnlineTreeManager(Context context, TreeStore store, Listener listener) {
        this.context = context.getApplicationContext();
        this.store = store;
        this.listener = listener;
        api = new GitHubApi(this.context);
        connectivityManager = (ConnectivityManager) this.context.getSystemService(
            Context.CONNECTIVITY_SERVICE);
        config = new OnlineTreeConfig(context);
        localChangePending = config.localEditPending();
        refreshStatus();
    }

    boolean signedIn() { return config.signedIn(); }
    boolean connected() { return config.connected(); }
    boolean isOwner() { return config.isOwner(); }
    String login() { return config.login(); }
    String treeName() { return config.treeFullName(); }
    String status() { return status; }
    String lastError() { return lastError; }
    long lastSyncAt() { return config.lastSyncAt(); }
    boolean browserSignInAvailable() {
        return BuildConfig.GITHUB_CLIENT_ID != null
            && !BuildConfig.GITHUB_CLIENT_ID.trim().isEmpty()
            && BuildConfig.GITHUB_CLIENT_SECRET != null
            && !BuildConfig.GITHUB_CLIENT_SECRET.trim().isEmpty();
    }

    String invitationKey() {
        if (!config.isOwner() || config.gistId().isEmpty() || config.inviteSecret().isEmpty()) {
            return "";
        }
        try {
            return OnlineInviteKey.create(config.gistId(), config.inviteSecret());
        } catch (Exception ignored) {
            return "";
        }
    }

    boolean canEdit() {
        return !config.connected() || config.isOwner() || config.canEdit();
    }

    void startForegroundChecks() {
        foregroundChecks = true;
        main.removeCallbacks(foregroundTick);
        main.postDelayed(foregroundTick, 900L);
    }

    void stopForegroundChecks() {
        foregroundChecks = false;
        main.removeCallbacks(foregroundTick);
    }

    void close() {
        closed = true;
        stopForegroundChecks();
        cancelSignIn();
        authWorker.shutdownNow();
        worker.shutdownNow();
    }

    String beginBrowserSignIn() throws Exception {
        if (closed) throw new IllegalStateException("Приложение закрывается");
        if (!browserSignInAvailable()) {
            throw new IllegalStateException("Быстрый вход GitHub не настроен в этой сборке");
        }
        cancelSignIn();
        String state = oauthRandom(32);
        String verifier = oauthRandom(48);
        String challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(
                verifier.getBytes(StandardCharsets.US_ASCII)),
            Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        config.setOAuthRequest(state, verifier);
        lastError = "";
        DiagnosticsLogger.breadcrumb(context, "online.auth.browser.start");
        setStatus("Открываем безопасный вход GitHub…");
        return api.browserAuthorizationUrl(
            BuildConfig.GITHUB_CLIENT_ID.trim(),
            OAUTH_REDIRECT_URI,
            state,
            challenge);
    }

    void completeBrowserSignIn(Uri callbackUri, Callback<String> callback) {
        if (closed) return;
        try {
            validateOAuthCallback(callbackUri);
        } catch (Exception error) {
            config.clearOAuthRequest();
            setError(error);
            post(() -> callback.onError(friendlyError(error)));
            return;
        }
        String expectedState = config.oauthState();
        String verifier = config.oauthVerifier();
        String returnedState = callbackUri.getQueryParameter("state");
        String code = callbackUri.getQueryParameter("code");
        String oauthError = callbackUri.getQueryParameter("error");
        if (oauthError != null && !oauthError.isEmpty()) {
            config.clearOAuthRequest();
            String description = callbackUri.getQueryParameter("error_description");
            Exception error = new IllegalStateException(
                description == null || description.trim().isEmpty()
                    ? "Вход GitHub отменён"
                    : description.trim());
            setError(error);
            post(() -> callback.onError(friendlyError(error)));
            return;
        }
        if (expectedState.isEmpty()
            || verifier.isEmpty()
            || returnedState == null
            || !secureEquals(expectedState, returnedState)
            || code == null
            || code.trim().isEmpty()) {
            config.clearOAuthRequest();
            Exception error = new IllegalStateException(
                "Запрос входа устарел или не прошёл проверку безопасности");
            setError(error);
            post(() -> callback.onError(friendlyError(error)));
            return;
        }

        Future<?> previous = authFuture;
        if (previous != null) previous.cancel(true);
        long attempt = authAttempt.incrementAndGet();
        setStatus("Завершаем вход в GitHub…");
        authFuture = authWorker.submit(() -> {
            try {
                JSONObject tokenResponse = exchangeBrowserCodeWithRetry(
                    code.trim(),
                    verifier,
                    attempt);
                String token = tokenResponse.optString("access_token", "");
                if (token.isEmpty()) {
                    throw new IllegalStateException(tokenResponse.optString(
                        "error_description",
                        tokenResponse.optString("error", "GitHub не выдал токен доступа")));
                }
                JSONObject user = currentUserWithRetry(
                    token,
                    attempt,
                    System.currentTimeMillis() + AUTH_NETWORK_RECOVERY_TIMEOUT);
                String login = user.optString("login", "");
                if (login.isEmpty()) throw new IllegalStateException("GitHub не вернул имя аккаунта");
                applyAuthenticatedUser(token, user);
                config.clearOAuthRequest();
                lastError = "";
                DiagnosticsLogger.breadcrumb(context, "online.auth.browser.success");
                refreshStatus();
                postAuth(attempt, () -> callback.onSuccess(login));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception error) {
                if (!authActive(attempt)) return;
                config.clearOAuthRequest();
                String message = friendlyError(error);
                setError(error);
                postAuth(attempt, () -> callback.onError(message));
            } finally {
                if (authAttempt.get() == attempt) authFuture = null;
            }
        });
    }

    void signIn(AuthListener callback) {
        if (closed) return;
        if (BuildConfig.GITHUB_CLIENT_ID == null || BuildConfig.GITHUB_CLIENT_ID.trim().isEmpty()) {
            post(() -> callback.onError(
                "В сборке не указан GitHub Client ID. Добавьте ANDROIDFT_GITHUB_CLIENT_ID в gradle.properties."));
            return;
        }
        Future<?> previous = authFuture;
        if (previous != null) previous.cancel(true);
        long attempt = authAttempt.incrementAndGet();
        DiagnosticsLogger.breadcrumb(context, "online.auth.start");
        setStatus("Подключение к GitHub…");
        authFuture = authWorker.submit(() -> {
            try {
                JSONObject device = requestDeviceCodeWithRetry(attempt);
                if (!authActive(attempt)) return;
                String deviceCode = device.optString("device_code", "");
                String userCode = device.optString("user_code", "");
                String url = device.optString(
                    "verification_uri_complete",
                    device.optString("verification_uri", "https://github.com/login/device"));
                int intervalSeconds = Math.max(5, device.optInt("interval", 5));
                long expiresAt = System.currentTimeMillis()
                    + Math.max(60, device.optInt("expires_in", 900)) * 1000L;
                if (deviceCode.isEmpty() || userCode.isEmpty()) {
                    throw new IllegalStateException("GitHub не выдал код входа");
                }
                setStatus("Ожидаем подтверждение в GitHub…");
                DiagnosticsLogger.breadcrumb(context, "online.auth.code");
                postAuth(attempt, () -> callback.onCode(userCode, url));

                long networkFailureSince = 0L;
                while (authActive(attempt) && System.currentTimeMillis() < expiresAt) {
                    Thread.sleep(intervalSeconds * 1000L);
                    if (!authActive(attempt)) return;
                    if (!hasInternetNetwork()) {
                        if (networkFailureSince == 0L) {
                            networkFailureSince = System.currentTimeMillis();
                        }
                        if (System.currentTimeMillis() - networkFailureSince
                            >= AUTH_NETWORK_RECOVERY_TIMEOUT) {
                            throw new UnknownHostException(
                                "Android не видит активное интернет-соединение");
                        }
                        setStatus("Ожидаем восстановление интернета…");
                        continue;
                    }
                    JSONObject tokenResponse;
                    try {
                        tokenResponse = api.pollDeviceToken(
                            BuildConfig.GITHUB_CLIENT_ID.trim(),
                            deviceCode);
                    } catch (GitHubApi.ApiException apiError) {
                        if (apiError.status >= 500 && apiError.status <= 599) {
                            if (networkFailureSince == 0L) {
                                networkFailureSince = System.currentTimeMillis();
                            }
                            if (System.currentTimeMillis() - networkFailureSince
                                < AUTH_NETWORK_RECOVERY_TIMEOUT) {
                                setStatus("GitHub временно недоступен · повторяем…");
                                continue;
                            }
                        }
                        throw apiError;
                    } catch (Exception networkError) {
                        if (!isTransientNetworkError(networkError)) throw networkError;
                        if (networkFailureSince == 0L) {
                            networkFailureSince = System.currentTimeMillis();
                        }
                        if (System.currentTimeMillis() - networkFailureSince
                            >= AUTH_NETWORK_RECOVERY_TIMEOUT) {
                            throw networkError;
                        }
                        setStatus("Восстанавливаем связь с GitHub…");
                        continue;
                    }
                    networkFailureSince = 0L;
                    String token = tokenResponse.optString("access_token", "");
                    if (!token.isEmpty()) {
                        setStatus("Завершаем вход в GitHub…");
                        JSONObject user = currentUserWithRetry(
                            token,
                            attempt,
                            Math.min(
                                expiresAt,
                                System.currentTimeMillis() + AUTH_NETWORK_RECOVERY_TIMEOUT));
                        String login = user.optString("login", "");
                        if (login.isEmpty()) throw new IllegalStateException("GitHub не вернул имя аккаунта");
                        applyAuthenticatedUser(token, user);
                        lastError = "";
                        DiagnosticsLogger.breadcrumb(context, "online.auth.success");
                        refreshStatus();
                        postAuth(attempt, () -> callback.onSuccess(login));
                        return;
                    }
                    String oauthError = tokenResponse.optString("error", "");
                    if ("authorization_pending".equals(oauthError)) {
                        setStatus("Ожидаем подтверждение в GitHub…");
                        continue;
                    }
                    if ("slow_down".equals(oauthError)) {
                        intervalSeconds += 5;
                        setStatus("GitHub просит подождать подтверждение…");
                        continue;
                    }
                    if ("access_denied".equals(oauthError)) {
                        throw new IllegalStateException("Вход отменён");
                    }
                    if ("expired_token".equals(oauthError)) {
                        throw new IllegalStateException("Код входа истёк");
                    }
                    if (!oauthError.isEmpty()) {
                        throw new IllegalStateException(tokenResponse.optString(
                            "error_description",
                            oauthError));
                    }
                }
                throw new IllegalStateException("Время ожидания входа истекло");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception error) {
                if (!authActive(attempt)) return;
                String message = friendlyError(error);
                setError(error);
                postAuth(attempt, () -> callback.onError(message));
            } finally {
                if (authAttempt.get() == attempt) authFuture = null;
            }
        });
    }

    void cancelSignIn() {
        authAttempt.incrementAndGet();
        Future<?> current = authFuture;
        authFuture = null;
        if (current != null) current.cancel(true);
        config.clearOAuthRequest();
        if (!closed) {
            DiagnosticsLogger.breadcrumb(context, "online.auth.cancel");
            lastError = "";
            refreshStatus();
        }
    }

    void signOut() {
        sessionGeneration.incrementAndGet();
        config.signOut();
        dirty = false;
        localChangePending = false;
        pendingStateJson = "";
        pendingMediaIds = new HashSet<>();
        identityVerified = false;
        clearMediaCache();
        lastError = "";
        post(() -> listener.onEditingPermissionChanged(true));
        refreshStatus();
    }

    void disconnectTree() {
        sessionGeneration.incrementAndGet();
        config.clearTree();
        dirty = false;
        localChangePending = false;
        pendingStateJson = "";
        pendingMediaIds = new HashSet<>();
        clearMediaCache();
        lastError = "";
        post(() -> listener.onEditingPermissionChanged(true));
        refreshStatus();
    }

    void createOnlineTree(TreeState currentState, Callback<String> callback) {
        if (!config.signedIn()) {
            callback.onError("Сначала войдите в GitHub");
            return;
        }
        TreeState snapshot = TreeStateCopier.copy(currentState);
        setStatus("Создаём приватное дерево…");
        long generation = sessionGeneration.get();
        worker.execute(() -> {
            String createdGistId = "";
            String operationToken = "";
            try {
                if (!sessionActive(generation)) return;
                ensureIdentityVerified();
                if (!config.signedIn()) throw new IllegalStateException("Сначала войдите в GitHub");
                operationToken = config.token();
                String treeId = UUID.randomUUID().toString();
                String repositoryName = "androidft-tree-"
                    + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
                JSONObject repository = api.createPrivateRepository(operationToken, repositoryName);
                if (!sessionActive(generation)) return;
                String owner = repository.optJSONObject("owner") == null
                    ? config.login()
                    : repository.optJSONObject("owner").optString("login", config.login());
                String repo = repository.optString("name", repositoryName);

                String stateJson = serializeState(snapshot);
                syncMediaUp(referencedMedia(snapshot), owner, repo, treeId);
                if (!sessionActive(generation)) return;
                String document = onlineDocument(treeId, stateJson, config.login(), 1L);
                GitHubApi.FileContent uploaded = api.putFile(
                    operationToken,
                    owner,
                    repo,
                    TREE_PATH,
                    document,
                    "",
                    "Создано онлайн-дерево AndroidFT");
                if (!sessionActive(generation)) return;
                JSONObject gist = api.createInvitationGist(operationToken, treeId);
                createdGistId = gist.optString("id", "");
                if (createdGistId.isEmpty()) {
                    throw new IllegalStateException("Не создан канал приглашений");
                }
                if (!sessionActive(generation)) throw new InterruptedException();
                String secret = OnlineInviteKey.newSecret();
                config.writeBase(stateJson);
                config.setTree(
                    owner,
                    repo,
                    treeId,
                    createdGistId,
                    secret,
                    true,
                    uploaded.sha);
                config.clearPending();
                dirty = false;
                localChangePending = false;
                pendingStateJson = "";
                lastError = "";
                refreshStatus();
                String key = OnlineInviteKey.create(createdGistId, secret);
                post(() -> callback.onSuccess(key));
            } catch (Exception error) {
                if (!createdGistId.isEmpty() && !config.connected()) {
                    try {
                        api.deleteGist(operationToken, createdGistId);
                    } catch (Exception cleanupError) {
                        DiagnosticsLogger.handled(
                            context,
                            "online-create-cleanup",
                            cleanupError);
                    }
                }
                if (!sessionActive(generation)) return;
                String message = friendlyError(error);
                setError(error);
                post(() -> callback.onError(message));
            }
        });
    }

    void discoverOnlineTrees(Callback<List<AvailableTree>> callback) {
        if (!config.signedIn()) {
            callback.onError("Сначала войдите в GitHub");
            return;
        }
        setStatus("Ищем онлайн-деревья…");
        long generation = sessionGeneration.get();
        worker.execute(() -> {
            try {
                if (!sessionActive(generation)) return;
                ensureIdentityVerified();
                if (!sessionActive(generation) || !config.signedIn()) return;
                JSONArray repositories = api.listAccessibleRepositories(config.token());
                List<AvailableTree> result = new ArrayList<>();
                for (int i = 0; i < repositories.length(); i++) {
                    if (!sessionActive(generation)) return;
                    JSONObject repository = repositories.optJSONObject(i);
                    if (!looksLikeAndroidFtRepository(repository)) continue;
                    JSONObject repositoryOwner = repository.optJSONObject("owner");
                    String owner = repositoryOwner == null
                        ? ""
                        : repositoryOwner.optString("login", "");
                    String repo = repository.optString("name", "");
                    if (owner.isEmpty() || repo.isEmpty()) continue;
                    try {
                        GitHubApi.FileContent file = api.getFile(
                            config.token(),
                            owner,
                            repo,
                            TREE_PATH);
                        JSONObject document = new JSONObject(file.text);
                        String treeId = document.optString("treeId", "");
                        validateOnlineDocument(document, treeId);
                        if (document.optJSONObject("state") == null) continue;
                        result.add(new AvailableTree(
                            owner,
                            repo,
                            treeId,
                            config.login().equalsIgnoreCase(owner),
                            repositoryCanEdit(repository),
                            document.optLong("updatedAt", 0L)));
                    } catch (GitHubApi.ApiException unavailable) {
                        if (unavailable.status != 403 && unavailable.status != 404) {
                            throw unavailable;
                        }
                    } catch (Exception ignoredInvalidTree) {
                        if (isTransientNetworkError(ignoredInvalidTree)) {
                            throw ignoredInvalidTree;
                        }
                        DiagnosticsLogger.handled(
                            context,
                            "online-discovery-invalid",
                            ignoredInvalidTree);
                    }
                }
                lastError = "";
                refreshStatus();
                post(() -> callback.onSuccess(result));
            } catch (Exception error) {
                if (!sessionActive(generation)) return;
                String message = friendlyError(error);
                setError(error);
                post(() -> callback.onError(message));
            }
        });
    }

    void restoreOnlineTree(AvailableTree available, Callback<Void> callback) {
        if (available == null
            || available.owner.isEmpty()
            || available.repo.isEmpty()
            || available.treeId.isEmpty()) {
            callback.onError("Онлайн-дерево не выбрано");
            return;
        }
        if (!config.signedIn()) {
            callback.onError("Сначала войдите в GitHub");
            return;
        }
        setStatus("Загружаем онлайн-дерево…");
        long generation = sessionGeneration.get();
        worker.execute(() -> {
            try {
                if (!sessionActive(generation)) return;
                ensureIdentityVerified();
                GitHubApi.FileContent remote = api.getFile(
                    config.token(),
                    available.owner,
                    available.repo,
                    TREE_PATH);
                JSONObject document = new JSONObject(remote.text);
                validateOnlineDocument(document, available.treeId);
                JSONObject stateJson = document.optJSONObject("state");
                if (stateJson == null) {
                    throw new IllegalStateException("Онлайн-дерево повреждено");
                }
                TreeState restored = store.parse(stateJson.toString());
                if (!sessionActive(generation)) return;
                String canonicalState = stateJson.toString();
                config.writeBase(canonicalState);
                config.setTree(
                    available.owner,
                    available.repo,
                    available.treeId,
                    "",
                    "",
                    available.ownerAccount,
                    remote.sha);
                config.setCanEdit(available.canEdit);
                post(() -> listener.onEditingPermissionChanged(available.canEdit));
                config.setLastRemote(remote.sha, remote.etag);
                config.clearPending();
                config.setLocalEditPending(false);
                dirty = false;
                localChangePending = false;
                pendingStateJson = "";
                pendingMediaIds = new HashSet<>();
                mediaAuditNeeded = true;
                lastError = "";
                refreshStatus();
                deliverRemote(restored, "Онлайн-дерево восстановлено");
                post(() -> callback.onSuccess(null));
                continueMediaRestore(
                    restored,
                    available.owner,
                    available.repo,
                    available.treeId,
                    generation);
            } catch (Exception error) {
                if (!sessionActive(generation)) return;
                String message = friendlyError(error);
                setError(error);
                post(() -> callback.onError(message));
            }
        });
    }

    private static boolean looksLikeAndroidFtRepository(JSONObject repository) {
        if (repository == null
            || repository.optBoolean("archived", false)
            || repository.optBoolean("disabled", false)
            || !repository.optBoolean("private", false)) {
            return false;
        }
        String name = repository.optString("name", "").toLowerCase(Locale.ROOT);
        String description = repository.optString("description", "")
            .toLowerCase(Locale.ROOT);
        return name.startsWith("androidft-tree-")
            || description.contains("androidft");
    }

    private static boolean repositoryCanEdit(JSONObject repository) {
        if (repository == null) return true;
        JSONObject permissions = repository.optJSONObject("permissions");
        if (permissions == null) return true;
        return permissions.optBoolean("push", false)
            || permissions.optBoolean("admin", false)
            || permissions.optBoolean("maintain", false);
    }

    private void refreshEditingPermission() throws Exception {
        if (!config.connected()) return;
        boolean previous = canEdit();
        boolean current = config.isOwner() || repositoryCanEdit(api.getRepository(
            config.token(),
            config.owner(),
            config.repo()));
        if (previous != current) {
            config.setCanEdit(current);
            post(() -> listener.onEditingPermissionChanged(current));
            post(() -> listener.onMessage(
                current
                    ? "Глава разрешил редактирование дерева"
                    : "Глава включил для вас режим просмотра"));
        }
    }

    void joinTree(String invitationKey, Callback<Void> callback) {
        if (!config.signedIn()) {
            callback.onError("Сначала войдите в GitHub");
            return;
        }
        final OnlineInviteKey.Parsed parsed;
        try {
            parsed = OnlineInviteKey.parse(invitationKey);
        } catch (Exception error) {
            callback.onError(friendlyError(error));
            return;
        }
        setStatus("Отправляем запрос главе…");
        long generation = sessionGeneration.get();
        worker.execute(() -> {
            try {
                if (!sessionActive(generation)) return;
                ensureIdentityVerified();
                JSONObject gist = api.getGist(config.token(), parsed.gistId);
                JSONObject gistOwner = gist.optJSONObject("owner");
                String expectedOwner = gistOwner == null
                    ? ""
                    : gistOwner.optString("login", "");
                if (expectedOwner.isEmpty()) {
                    throw new IllegalStateException("Не удалось определить владельца приглашения");
                }
                String nonce = OnlineInviteKey.randomNonce();
                long requestStartedAt = System.currentTimeMillis();
                String request = OnlineInviteKey.request(
                    parsed.secret,
                    config.login(),
                    nonce);
                api.createGistComment(config.token(), parsed.gistId, request);

                long deadline = System.currentTimeMillis() + JOIN_TIMEOUT;
                while (sessionActive(generation) && System.currentTimeMillis() < deadline) {
                    JSONObject response = findJoinResponse(
                        parsed,
                        nonce,
                        expectedOwner,
                        requestStartedAt);
                    if (response != null) {
                        String owner = response.optString("owner", "");
                        String repo = response.optString("repo", "");
                        String treeId = response.optString("treeId", "");
                        acceptMatchingInvitation(owner, repo);
                        GitHubApi.FileContent remote = retryGetTree(owner, repo, deadline);
                        JSONObject document = new JSONObject(remote.text);
                        validateOnlineDocument(document, treeId);
                        JSONObject remoteStateJson = document.optJSONObject("state");
                        if (remoteStateJson == null) throw new IllegalStateException("Файл дерева повреждён");
                        TreeState remoteState = store.parse(remoteStateJson.toString());
                        if (!sessionActive(generation)) return;
                        config.writeBase(remoteStateJson.toString());
                        config.setTree(
                            owner,
                            repo,
                            treeId,
                            parsed.gistId,
                            parsed.secret,
                            false,
                            remote.sha);
                        config.setCanEdit(true);
                        post(() -> listener.onEditingPermissionChanged(true));
                        config.clearPending();
                        dirty = false;
                        localChangePending = false;
                        pendingStateJson = "";
                        pendingMediaIds = new HashSet<>();
                        mediaAuditNeeded = true;
                        lastError = "";
                        refreshStatus();
                        deliverRemote(remoteState, "Онлайн-дерево подключено");
                        post(() -> callback.onSuccess(null));
                        continueMediaRestore(
                            remoteState,
                            owner,
                            repo,
                            treeId,
                            generation);
                        return;
                    }
                    Thread.sleep(4_000L);
                }
                throw new IllegalStateException(
                    "Запрос отправлен. Глава должен открыть AndroidFT; затем повторите подключение.");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception error) {
                String message = friendlyError(error);
                setError(error);
                post(() -> callback.onError(message));
            }
        });
    }

    void syncNow(Callback<Void> callback) {
        if (!config.connected()) {
            callback.onError("Онлайн-дерево не подключено");
            return;
        }
        setStatus("Синхронизация…");
        long generation = sessionGeneration.get();
        worker.execute(() -> {
            try {
                if (!sessionActive(generation)) return;
                ensureIdentityVerified();
                if (!sessionActive(generation) || !config.connected()) return;
                refreshEditingPermission();
                if (config.isOwner()) processJoinRequests();
                if (dirty) {
                    if (canEdit()) pushPending();
                    else setStatus("Только просмотр · локальные правки сохранены");
                } else {
                    pullRemote(true);
                }
                if (!sessionActive(generation)) return;
                refreshStatus();
                post(() -> callback.onSuccess(null));
            } catch (Exception error) {
                if (!sessionActive(generation)) return;
                String message = friendlyError(error);
                setError(error);
                post(() -> callback.onError(message));
            }
        });
    }

    void rotateInvitation(Callback<String> callback) {
        if (!config.connected() || !config.isOwner()) {
            callback.onError("Только глава дерева может менять ключ");
            return;
        }
        setStatus("Меняем ключ…");
        long generation = sessionGeneration.get();
        worker.execute(() -> {
            try {
                if (!sessionActive(generation)) return;
                ensureIdentityVerified();
                String oldGist = config.gistId();
                JSONObject gist = api.createInvitationGist(config.token(), config.treeId());
                String gistId = gist.optString("id", "");
                String secret = OnlineInviteKey.newSecret();
                if (!sessionActive(generation)) return;
                config.setTree(
                    config.owner(),
                    config.repo(),
                    config.treeId(),
                    gistId,
                    secret,
                    true,
                    config.lastSha());
                if (!oldGist.isEmpty()) {
                    try {
                        api.deleteGist(config.token(), oldGist);
                    } catch (Exception ignored) {
                    }
                }
                String key = OnlineInviteKey.create(gistId, secret);
                refreshStatus();
                post(() -> callback.onSuccess(key));
            } catch (Exception error) {
                String message = friendlyError(error);
                setError(error);
                post(() -> callback.onError(message));
            }
        });
    }

    void loadParticipants(Callback<List<Participant>> callback) {
        if (!config.connected() || !config.isOwner()) {
            callback.onError("Управление участниками доступно только главе");
            return;
        }
        worker.execute(() -> {
            try {
                ensureIdentityVerified();
                JSONArray source = api.listCollaborators(
                    config.token(),
                    config.owner(),
                    config.repo());
                List<Participant> result = new ArrayList<>();
                boolean hasOwner = false;
                for (int i = 0; i < source.length(); i++) {
                    JSONObject item = source.optJSONObject(i);
                    if (item == null) continue;
                    String login = item.optString("login", "");
                    boolean owner = login.equalsIgnoreCase(config.owner());
                    JSONObject permissions = item.optJSONObject("permissions");
                    boolean canEdit = owner
                        || (permissions != null
                            && (permissions.optBoolean("push", false)
                                || permissions.optBoolean("admin", false)
                                || permissions.optBoolean("maintain", false)))
                        || "write".equalsIgnoreCase(item.optString("role_name", ""))
                        || "admin".equalsIgnoreCase(item.optString("role_name", ""));
                    hasOwner |= owner;
                    result.add(new Participant(
                        login,
                        item.optString("avatar_url", ""),
                        item.optString("role_name", owner ? "owner" : "write"),
                        owner,
                        canEdit));
                }
                if (!hasOwner) {
                    result.add(0, new Participant(
                        config.owner(),
                        "",
                        "owner",
                        true,
                        true));
                }
                post(() -> callback.onSuccess(result));
            } catch (Exception error) {
                post(() -> callback.onError(friendlyError(error)));
            }
        });
    }

    void setParticipantEditing(
        String username,
        boolean canEdit,
        Callback<Void> callback
    ) {
        if (!config.connected() || !config.isOwner()) {
            callback.onError("Только глава дерева может менять права участников");
            return;
        }
        if (username == null
            || username.equalsIgnoreCase(config.owner())
            || !username.matches("[A-Za-z0-9-]{1,39}")) {
            callback.onError("Права этого участника изменить нельзя");
            return;
        }
        worker.execute(() -> {
            try {
                ensureIdentityVerified();
                api.setCollaboratorPermission(
                    config.token(),
                    config.owner(),
                    config.repo(),
                    username,
                    canEdit);
                post(() -> callback.onSuccess(null));
            } catch (Exception error) {
                post(() -> callback.onError(friendlyError(error)));
            }
        });
    }

    void removeParticipant(String username, Callback<Void> callback) {
        if (!config.connected() || !config.isOwner()) {
            callback.onError("Только глава дерева может удалять участников");
            return;
        }
        if (username == null
            || username.equalsIgnoreCase(config.owner())
            || !username.matches("[A-Za-z0-9-]{1,39}")) {
            callback.onError("Этого участника нельзя удалить");
            return;
        }
        worker.execute(() -> {
            try {
                ensureIdentityVerified();
                api.removeCollaborator(
                    config.token(),
                    config.owner(),
                    config.repo(),
                    username);
                post(() -> callback.onSuccess(null));
            } catch (Exception error) {
                post(() -> callback.onError(friendlyError(error)));
            }
        });
    }

    void leaveTree(Callback<Void> callback) {
        if (!config.connected() || config.isOwner()) {
            callback.onError("Глава не может покинуть собственный репозиторий");
            return;
        }
        worker.execute(() -> {
            try {
                ensureIdentityVerified();
                api.removeCollaborator(
                    config.token(),
                    config.owner(),
                    config.repo(),
                    config.login());
                config.clearTree();
                dirty = false;
                localChangePending = false;
                pendingStateJson = "";
                lastError = "";
                post(() -> listener.onEditingPermissionChanged(true));
                refreshStatus();
                post(() -> callback.onSuccess(null));
            } catch (Exception error) {
                post(() -> callback.onError(friendlyError(error)));
            }
        });
    }

    void onLocalTreeSaved(TreeState snapshot) {
        if (snapshot == null || !config.connected() || !canEdit() || closed) return;
        long revision = localRevision.incrementAndGet();
        TreeState copy = TreeStateCopier.copy(snapshot);
        worker.execute(() -> {
            try {
                String json = serializeState(copy);
                Set<String> mediaIds = referencedMedia(copy);
                String base = config.readBase();
                if (!base.isEmpty() && sameJson(base, json)) {
                    config.clearPending();
                    config.setLocalEditPending(false);
                    localChangePending = false;
                    dirty = false;
                    pendingStateJson = "";
                    pendingMediaIds = new HashSet<>();
                    refreshStatus();
                    return;
                }
                config.writePending(json);
                config.setLocalEditPending(false);
                localChangePending = false;
                pendingStateJson = json;
                pendingMediaIds = mediaIds;
                dirty = true;
                setStatus("Ожидает синхронизации");
                worker.schedule(() -> {
                    if (!closed && revision == localRevision.get() && dirty) {
                        try {
                            pushPending();
                        } catch (Exception error) {
                            setError(error);
                        }
                    }
                }, PUSH_DEBOUNCE, TimeUnit.MILLISECONDS);
            } catch (Exception error) {
                setError(error);
            }
        });
    }

    void markLocalTreeChanged() {
        if (!config.connected() || closed) return;
        try {
            config.setLocalEditPending(true);
            localChangePending = true;
            setStatus("Ожидает сохранения");
        } catch (Exception error) {
            setError(error);
        }
    }

    void reconcileLocalTree(TreeState snapshot) {
        if (snapshot == null || !config.connected() || !canEdit() || closed) return;
        TreeState copy = TreeStateCopier.copy(snapshot);
        worker.execute(() -> {
            try {
                String local = serializeState(copy);
                String base = config.readBase();
                String pending = config.readPending();
                boolean hasLocalJournal = config.localEditPending() || !pending.isEmpty();
                if (!base.isEmpty() && sameJson(local, base)) {
                    config.clearPending();
                    config.setLocalEditPending(false);
                    pendingStateJson = "";
                    pendingMediaIds = new HashSet<>();
                    dirty = false;
                    localChangePending = false;
                } else if (base.isEmpty() || hasLocalJournal) {
                    config.writePending(local);
                    config.setLocalEditPending(false);
                    pendingStateJson = local;
                    pendingMediaIds = referencedMedia(copy);
                    dirty = true;
                    localChangePending = false;
                } else {
                    TreeState accepted = store.parse(base);
                    pendingStateJson = "";
                    pendingMediaIds = new HashSet<>();
                    dirty = false;
                    localChangePending = false;
                    deliverRemote(accepted, "Восстановлено принятое онлайн-дерево");
                }
                refreshStatus();
            } catch (Exception error) {
                setError(error);
            }
        });
    }

    private void processJoinRequests() throws Exception {
        if (!config.isOwner() || config.gistId().isEmpty() || config.inviteSecret().isEmpty()) return;
        JSONArray comments = api.listGistComments(
            config.token(),
            config.gistId(),
            System.currentTimeMillis() - INVITATION_WINDOW);
        Set<String> answered = new HashSet<>();
        for (int i = 0; i < comments.length(); i++) {
            JSONObject item = comments.optJSONObject(i);
            if (item == null) continue;
            JSONObject responseAuthor = item.optJSONObject("user");
            String responseLogin = responseAuthor == null
                ? ""
                : responseAuthor.optString("login", "");
            if (!config.owner().equalsIgnoreCase(responseLogin)) continue;
            JSONObject response;
            try {
                response = OnlineInviteKey.readResponse(
                    config.inviteSecret(),
                    item.optString("body", ""));
            } catch (Exception ignored) {
                response = null;
            }
            if (response != null) answered.add(response.optString("nonce", ""));
        }

        int accepted = 0;
        for (int i = 0; i < comments.length(); i++) {
            JSONObject item = comments.optJSONObject(i);
            if (item == null) continue;
            JSONObject request;
            try {
                request = OnlineInviteKey.readRequest(
                    config.inviteSecret(),
                    item.optString("body", ""));
            } catch (Exception ignored) {
                request = null;
            }
            if (request == null) continue;
            String nonce = request.optString("nonce", "");
            if (nonce.isEmpty() || answered.contains(nonce)) continue;
            long createdAt = request.optLong("createdAt", 0L);
            if (createdAt <= 0L
                || Math.abs(System.currentTimeMillis() - createdAt) > INVITATION_WINDOW) {
                continue;
            }
            JSONObject user = item.optJSONObject("user");
            String author = user == null ? "" : user.optString("login", "");
            String claimed = request.optString("login", "");
            if (!author.equalsIgnoreCase(claimed)
                || !author.matches("[A-Za-z0-9-]{1,39}")
                || author.equalsIgnoreCase(config.owner())) {
                continue;
            }
            api.addCollaborator(config.token(), config.owner(), config.repo(), author);
            String responseBody = OnlineInviteKey.response(
                config.inviteSecret(),
                author,
                nonce,
                config.owner(),
                config.repo(),
                config.treeId());
            api.createGistComment(config.token(), config.gistId(), responseBody);
            answered.add(nonce);
            accepted++;
        }
        if (accepted > 0) {
            int count = accepted;
            post(() -> listener.onMessage(
                count == 1 ? "Подключён новый участник" : "Подключено участников: " + count));
        }
    }

    private JSONObject findJoinResponse(
        OnlineInviteKey.Parsed parsed,
        String nonce,
        String expectedOwner,
        long requestStartedAt
    ) throws Exception {
        JSONArray comments = api.listGistComments(
            config.token(),
            parsed.gistId,
            Math.max(0L, requestStartedAt - 60_000L));
        for (int i = comments.length() - 1; i >= 0; i--) {
            JSONObject item = comments.optJSONObject(i);
            if (item == null) continue;
            JSONObject author = item.optJSONObject("user");
            String authorLogin = author == null ? "" : author.optString("login", "");
            if (!expectedOwner.equalsIgnoreCase(authorLogin)) continue;
            JSONObject response;
            try {
                response = OnlineInviteKey.readResponse(
                    parsed.secret,
                    item.optString("body", ""));
            } catch (Exception ignored) {
                response = null;
            }
            if (response == null) continue;
            if (nonce.equals(response.optString("nonce", ""))
                && config.login().equalsIgnoreCase(response.optString("login", ""))) {
                return response;
            }
        }
        return null;
    }

    private void acceptMatchingInvitation(String owner, String repo) throws Exception {
        JSONArray invitations = api.listMyRepositoryInvitations(config.token());
        String expected = owner + "/" + repo;
        for (int i = 0; i < invitations.length(); i++) {
            JSONObject item = invitations.optJSONObject(i);
            if (item == null) continue;
            JSONObject repository = item.optJSONObject("repository");
            if (repository != null
                && expected.equalsIgnoreCase(repository.optString("full_name", ""))) {
                api.acceptRepositoryInvitation(config.token(), item.optLong("id", 0L));
                return;
            }
        }
    }

    private GitHubApi.FileContent retryGetTree(
        String owner,
        String repo,
        long deadline
    ) throws Exception {
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                return api.getFile(config.token(), owner, repo, TREE_PATH);
            } catch (GitHubApi.ApiException error) {
                last = error;
                if (error.status != 403 && error.status != 404 && error.status != 409) throw error;
            }
            Thread.sleep(2_000L);
        }
        throw last == null ? new IllegalStateException("Дерево пока недоступно") : last;
    }

    private void pushPending() throws Exception {
        if (!config.connected() || !dirty) return;
        ensureIdentityVerified();
        if (!config.connected()) return;
        long generation = sessionGeneration.get();
        String local = pendingStateJson;
        if (local == null || local.isEmpty()) return;
        setStatus("Синхронизация…");
        syncMediaUp(
            pendingMediaIds,
            config.owner(),
            config.repo(),
            config.treeId());
        for (int attempt = 0; attempt < 4; attempt++) {
            GitHubApi.FileContent remoteFile = api.getFile(
                config.token(),
                config.owner(),
                config.repo(),
                TREE_PATH);
            JSONObject remoteDocument = new JSONObject(remoteFile.text);
            validateOnlineDocument(remoteDocument, config.treeId());
            JSONObject remoteObject = remoteDocument.optJSONObject("state");
            if (remoteObject == null) throw new IllegalStateException("Онлайн-дерево повреждено");
            String remote = remoteObject.toString();
            String merged = local;
            String base = config.readBase();
            if (!base.isEmpty() && !sameJson(remote, base)) {
                merged = OnlineTreeMerge.merge(
                    new JSONObject(base),
                    new JSONObject(local),
                    new JSONObject(remote)).toString();
            }
            String document = onlineDocument(
                config.treeId(),
                merged,
                config.login(),
                remoteDocument.optLong("revision", 0L) + 1L);
            try {
                GitHubApi.FileContent uploaded = api.putFile(
                    config.token(),
                    config.owner(),
                    config.repo(),
                    TREE_PATH,
                    document,
                    remoteFile.sha,
                    "Синхронизация AndroidFT");
                if (generation != sessionGeneration.get() || !config.connected()) return;
                config.writeBase(merged);
                config.setLastRemote(uploaded.sha, uploaded.etag);
                config.clearPending();
                config.setLocalEditPending(false);
                pendingStateJson = "";
                pendingMediaIds = new HashSet<>();
                dirty = false;
                lastError = "";
                refreshStatus();
                if (!sameJson(local, merged)) {
                    TreeState mergedState = store.parse(merged);
                    deliverRemote(mergedState, "Изменения объединены");
                }
                return;
            } catch (GitHubApi.ApiException conflict) {
                if (conflict.status != 409 || attempt >= 3) throw conflict;
            }
        }
    }

    private void pullRemote(boolean announce) throws Exception {
        if (!config.connected()) return;
        ensureIdentityVerified();
        if (!config.connected()) return;
        long generation = sessionGeneration.get();
        if (localChangePending) {
            setStatus("Ожидает сохранения");
            return;
        }
        setStatus("Проверяем изменения…");
        boolean conditional = !mediaAuditNeeded
            && lastError.isEmpty()
            && !config.lastEtag().isEmpty();
        GitHubApi.FileContent remoteFile = conditional
            ? api.getFileIfChanged(
                config.token(),
                config.owner(),
                config.repo(),
                TREE_PATH,
                config.lastEtag())
            : api.getFile(
                config.token(),
                config.owner(),
                config.repo(),
                TREE_PATH);
        if (remoteFile.notModified) {
            lastError = "";
            refreshStatus();
            return;
        }
        JSONObject document = new JSONObject(remoteFile.text);
        validateOnlineDocument(document, config.treeId());
        JSONObject remoteState = document.optJSONObject("state");
        if (remoteState == null) throw new IllegalStateException("Онлайн-дерево повреждено");
        String remoteJson = remoteState.toString();
        TreeState parsed = store.parse(remoteJson);
        Set<String> mediaIds = referencedMedia(parsed);
        boolean treeChanged = !remoteFile.sha.equals(config.lastSha());
        if (treeChanged) cachedAssetsAt = 0L;
        if (treeChanged || mediaAuditNeeded) {
            syncMediaUp(mediaIds, config.owner(), config.repo(), config.treeId());
            syncMediaDown(parsed, config.owner(), config.repo(), config.treeId());
            mediaAuditNeeded = false;
        }
        if (!treeChanged) {
            if (!remoteFile.etag.isEmpty()) {
                config.setLastRemote(remoteFile.sha, remoteFile.etag);
            }
            lastError = "";
            refreshStatus();
            return;
        }
        if (!sessionActive(generation)) return;
        config.writeBase(remoteJson);
        config.setLastRemote(remoteFile.sha, remoteFile.etag);
        config.clearPending();
        config.setLocalEditPending(false);
        lastError = "";
        refreshStatus();
        deliverRemote(parsed, announce ? "Дерево синхронизировано" : "Получены изменения дерева");
    }

    private void syncMediaUp(
        Set<String> mediaIds,
        String owner,
        String repo,
        String treeId
    ) throws Exception {
        if (mediaIds == null || mediaIds.isEmpty()) return;
        setStatus("Отправляем медиа…");
        long releaseId = ensureMediaRelease(owner, repo, treeId);
        Map<String, GitHubApi.ReleaseAsset> remoteAssets =
            releaseAssets(owner, repo, releaseId);
        int uploadTotal = 0;
        for (String mediaId : mediaIds) {
            File candidate = store.mediaStore().file(mediaId);
            if (candidate != null
                && candidate.isFile()
                && !remoteAssets.containsKey(mediaId)) {
                uploadTotal++;
            }
        }
        int uploadIndex = 0;
        for (String mediaId : mediaIds) {
            File file = store.mediaStore().file(mediaId);
            if (file == null || !file.isFile()) continue;
            GitHubApi.ReleaseAsset existing = remoteAssets.get(mediaId);
            if (assetMatches(existing, file)) {
                config.markRemoteMedia(mediaId);
                continue;
            }
            if (existing != null) {
                deleteStaleAsset(owner, repo, existing);
                remoteAssets.remove(mediaId);
            }
            uploadIndex++;
            GitHubApi.ReleaseAsset uploaded = uploadMediaWithRetry(
                owner,
                repo,
                releaseId,
                mediaId,
                file,
                uploadIndex,
                uploadTotal);
            remoteAssets.put(mediaId, uploaded);
            cachedAssets.put(mediaId, uploaded);
            config.markRemoteMedia(mediaId);
        }
    }

    private GitHubApi.ReleaseAsset uploadMediaWithRetry(
        String owner,
        String repo,
        long releaseId,
        String mediaId,
        File file,
        int index,
        int total
    ) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            setStatus(
                "Отправляем медиа " + index + " из " + total
                    + (attempt == 1 ? "…" : " · попытка " + attempt + " из 3"));
            try {
                GitHubApi.ReleaseAsset uploaded = api.uploadReleaseAsset(
                    config.token(),
                    owner,
                    repo,
                    releaseId,
                    mediaId,
                    mimeFromName(mediaId),
                    file);
                if (!assetMatches(uploaded, file)) {
                    throw new IllegalStateException(
                        "GitHub не подтвердил загрузку медиафайла");
                }
                return uploaded;
            } catch (Exception error) {
                last = error;
                cachedAssetsAt = 0L;
                GitHubApi.ReleaseAsset remote = null;
                try {
                    Map<String, GitHubApi.ReleaseAsset> refreshed =
                        releaseAssets(owner, repo, releaseId);
                    remote = refreshed.get(mediaId);
                    if (assetMatches(remote, file)) return remote;
                    if (remote != null) deleteStaleAsset(owner, repo, remote);
                } catch (Exception refreshError) {
                    if (!isRetryableMediaError(refreshError)) throw refreshError;
                    last = refreshError;
                }
                if (attempt >= 3 || !isRetryableMediaError(error)) throw error;
                Thread.sleep(attempt * 1_500L);
            }
        }
        throw last == null
            ? new IllegalStateException("Не удалось отправить медиафайл")
            : last;
    }

    private void deleteStaleAsset(
        String owner,
        String repo,
        GitHubApi.ReleaseAsset asset
    ) throws Exception {
        if (asset == null || asset.id <= 0L) return;
        setStatus("Восстанавливаем прерванную загрузку…");
        api.deleteReleaseAsset(config.token(), owner, repo, asset.id);
        cachedAssets.remove(asset.name);
        cachedAssetsAt = 0L;
    }

    private static boolean assetMatches(GitHubApi.ReleaseAsset asset, File file) {
        return asset != null
            && asset.id > 0L
            && file != null
            && asset.size == file.length()
            && assetReady(asset);
    }

    private static boolean assetReady(GitHubApi.ReleaseAsset asset) {
        return asset != null
            && asset.id > 0L
            && (asset.state.isEmpty() || "uploaded".equalsIgnoreCase(asset.state));
    }

    private int syncMediaDown(
        TreeState state,
        String owner,
        String repo,
        String treeId
    ) throws Exception {
        Set<String> mediaIds = referencedMedia(state);
        if (mediaIds.isEmpty()) return 0;
        long releaseId = findMediaRelease(owner, repo, treeId);
        Map<String, GitHubApi.ReleaseAsset> remoteAssets = releaseId <= 0L
            ? new HashMap<>()
            : releaseAssets(owner, repo, releaseId);
        int missingCount = 0;
        int downloadTotal = 0;
        for (String mediaId : mediaIds) {
            if (!store.mediaStore().exists(mediaId)
                && remoteAssets.containsKey(mediaId)) {
                downloadTotal++;
            }
        }
        int downloadIndex = 0;
        int downloadedCount = 0;
        for (String mediaId : mediaIds) {
            if (store.mediaStore().exists(mediaId)) {
                config.markRemoteMedia(mediaId);
                continue;
            }
            GitHubApi.ReleaseAsset asset = remoteAssets.get(mediaId);
            if (asset != null) {
                downloadIndex++;
                downloadMediaWithRetry(
                    owner,
                    repo,
                    releaseId,
                    mediaId,
                    asset,
                    downloadIndex,
                    downloadTotal);
                config.markRemoteMedia(mediaId);
                downloadedCount++;
                continue;
            }

            // Совместимость с онлайн-деревьями, созданными до перехода на
            // Release Assets: небольшие файлы могли храниться прямо в репозитории.
            try {
                GitHubApi.BinaryContent legacy = api.getBinaryFile(
                    config.token(),
                    owner,
                    repo,
                    "androidft/media/" + mediaId);
                try (ByteArrayInputStream input = new ByteArrayInputStream(legacy.bytes)) {
                    TreeMediaStore.StoredMedia stored = mediaId.startsWith("photo_")
                        ? store.mediaStore().importPhoto(input, mediaId, mimeFromName(mediaId))
                        : store.mediaStore().importAttachment(input, mediaId, mimeFromName(mediaId));
                    if (!mediaId.equals(stored.id)) {
                        throw new IllegalStateException(
                            "Контрольная сумма медиафайла не совпала");
                    }
                }
                config.markRemoteMedia(mediaId);
                downloadedCount++;
            } catch (GitHubApi.ApiException missing) {
                if (missing.status != 404) throw missing;
                missingCount++;
            }
        }
        if (missingCount > 0) {
            int count = missingCount;
            post(() -> listener.onMessage("В онлайн-дереве отсутствуют вложения: " + count));
        }
        return downloadedCount;
    }

    private void continueMediaRestore(
        TreeState state,
        String owner,
        String repo,
        String treeId,
        long generation
    ) {
        if (!sessionActive(generation)) return;
        try {
            int downloaded = syncMediaDown(state, owner, repo, treeId);
            if (!sessionActive(generation)) return;
            mediaAuditNeeded = false;
            lastError = "";
            refreshStatus();
            post(listener::onMediaChanged);
            if (downloaded > 0) {
                post(() -> listener.onMessage(
                    "Медиа загружены в фоне: " + downloaded));
            }
        } catch (Exception error) {
            if (!sessionActive(generation)) return;
            mediaAuditNeeded = true;
            setError(error);
        }
    }

    private void downloadMediaWithRetry(
        String owner,
        String repo,
        long releaseId,
        String mediaId,
        GitHubApi.ReleaseAsset asset,
        int index,
        int total
    ) throws Exception {
        GitHubApi.ReleaseAsset currentAsset = asset;
        for (int attempt = 1; attempt <= 3; attempt++) {
            setStatus(
                "Загружаем медиа " + index + " из " + total
                    + (attempt == 1 ? "…" : " · попытка " + attempt + " из 3"));
            try {
                if (!assetReady(currentAsset)) {
                    throw new IllegalStateException("Медиафайл на GitHub ещё загружается");
                }
                TreeMediaStore.StoredMedia stored = api.downloadReleaseAsset(
                    config.token(),
                    owner,
                    repo,
                    currentAsset.id,
                    (input, contentLength) -> {
                        long maximum = maximumMediaBytes(mediaId);
                        if (contentLength > maximum) {
                            throw new IllegalStateException(
                                "Медиафайл превышает допустимые "
                                    + TreeMediaStore.humanSize(maximum));
                        }
                        return mediaId.startsWith("photo_")
                            ? store.mediaStore().importPhoto(
                                input,
                                mediaId,
                                mimeFromName(mediaId))
                            : store.mediaStore().importAttachment(
                                input,
                                mediaId,
                                mimeFromName(mediaId));
                    });
                if (!mediaId.equals(stored.id)) {
                    throw new IllegalStateException(
                        "Контрольная сумма медиафайла не совпала");
                }
                return;
            } catch (Exception error) {
                cachedAssetsAt = 0L;
                try {
                    GitHubApi.ReleaseAsset refreshed = releaseAssets(
                        owner,
                        repo,
                        releaseId).get(mediaId);
                    if (refreshed != null) currentAsset = refreshed;
                } catch (Exception refreshError) {
                    if (!isRetryableMediaError(refreshError)) throw refreshError;
                }
                boolean waitingForUpload = error instanceof IllegalStateException
                    && "Медиафайл на GitHub ещё загружается".equals(error.getMessage());
                if (attempt >= 3
                    || (!waitingForUpload && !isRetryableMediaError(error))) {
                    throw error;
                }
                Thread.sleep(attempt * 1_500L);
            }
        }
    }

    private long ensureMediaRelease(String owner, String repo, String treeId) throws Exception {
        long existing = findMediaRelease(owner, repo, treeId);
        if (existing > 0L) return existing;
        try {
            JSONObject created = api.createMediaRelease(
                config.token(),
                owner,
                repo,
                mediaReleaseTag(treeId));
            long releaseId = created.optLong("id", 0L);
            if (releaseId <= 0L) {
                throw new IllegalStateException("GitHub не создал хранилище медиа");
            }
            cachedReleaseId = releaseId;
            cachedReleaseTreeId = treeId;
            return releaseId;
        } catch (GitHubApi.ApiException race) {
            if (race.status != 422) throw race;
            long releaseId = findMediaRelease(owner, repo, treeId);
            if (releaseId <= 0L) throw race;
            return releaseId;
        }
    }

    private long findMediaRelease(String owner, String repo, String treeId) throws Exception {
        if (treeId != null
            && treeId.equals(cachedReleaseTreeId)
            && cachedReleaseId > 0L) {
            return cachedReleaseId;
        }
        try {
            JSONObject release = api.getReleaseByTag(
                config.token(),
                owner,
                repo,
                mediaReleaseTag(treeId));
            long releaseId = release.optLong("id", 0L);
            if (releaseId > 0L) {
                cachedReleaseId = releaseId;
                cachedReleaseTreeId = treeId;
            }
            return releaseId;
        } catch (GitHubApi.ApiException missing) {
            if (missing.status == 404) return 0L;
            throw missing;
        }
    }

    private Map<String, GitHubApi.ReleaseAsset> releaseAssets(
        String owner,
        String repo,
        long releaseId
    ) throws Exception {
        if (releaseId == cachedReleaseId
            && System.currentTimeMillis() - cachedAssetsAt < MEDIA_MANIFEST_CACHE) {
            return new HashMap<>(cachedAssets);
        }
        JSONArray source = api.listReleaseAssets(
            config.token(),
            owner,
            repo,
            releaseId);
        Map<String, GitHubApi.ReleaseAsset> result = new HashMap<>();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item == null || !"uploaded".equals(item.optString("state", ""))) continue;
            GitHubApi.ReleaseAsset asset = GitHubApi.releaseAsset(item);
            if (validMediaId(asset.name, "photo_")
                || validMediaId(asset.name, "attachment_")) {
                result.put(asset.name, asset);
            }
        }
        cachedReleaseId = releaseId;
        cachedAssets = new HashMap<>(result);
        cachedAssetsAt = System.currentTimeMillis();
        return result;
    }

    private static String mediaReleaseTag(String treeId) {
        String safe = treeId == null ? "" : treeId.trim().toLowerCase(Locale.ROOT);
        if (!safe.matches("[a-z0-9-]{8,64}")) {
            throw new IllegalArgumentException("Некорректный идентификатор дерева");
        }
        return "androidft-media-" + safe;
    }

    private static long maximumMediaBytes(String mediaId) {
        if (mediaId != null && mediaId.startsWith("photo_")) {
            return TreeMediaStore.MAX_PHOTO_BYTES;
        }
        return mediaId != null && isVideoName(mediaId)
            ? TreeMediaStore.MAX_VIDEO_BYTES
            : TreeMediaStore.MAX_ATTACHMENT_BYTES;
    }

    private static Set<String> referencedMedia(TreeState state) {
        Set<String> result = new HashSet<>();
        if (state == null) return result;
        for (Person person : state.people.values()) {
            if (validMediaId(person.photoMediaId, "photo_")) result.add(person.photoMediaId);
            for (Memory memory : person.memories) {
                for (MemoryAttachment attachment : memory.attachments) {
                    if (validMediaId(attachment.mediaId, "attachment_")) {
                        result.add(attachment.mediaId);
                    }
                }
            }
        }
        return result;
    }

    private static boolean validMediaId(String value, String prefix) {
        return value != null
            && value.startsWith(prefix)
            && value.matches("[A-Za-z0-9._-]{16,180}");
    }

    private static String mimeFromName(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        return "application/octet-stream";
    }

    private static boolean isVideoName(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp4")
            || lower.endsWith(".webm")
            || lower.endsWith(".mov")
            || lower.endsWith(".mkv")
            || lower.endsWith(".avi")
            || lower.endsWith(".m4v");
    }

    private String serializeState(TreeState state) throws Exception {
        return OnlineSyncPayload.fromTreeJson(store.toJson(state)).toString();
    }

    private static String onlineDocument(
        String treeId,
        String stateJson,
        String login,
        long revision
    ) throws Exception {
        return new JSONObject()
            .put("format", "ru.drshapaya.androidft.online")
            .put("protocol", 1)
            .put("treeId", treeId)
            .put("revision", Math.max(1L, revision))
            .put("updatedAt", System.currentTimeMillis())
            .put("updatedBy", login)
            .put("state", new JSONObject(stateJson))
            .toString(2);
    }

    private static void validateOnlineDocument(JSONObject document, String expectedTreeId) {
        if (document == null
            || !"ru.drshapaya.androidft.online".equals(document.optString("format", ""))
            || document.optInt("protocol", 0) != 1) {
            throw new IllegalStateException("Неподдерживаемый формат онлайн-дерева");
        }
        String actualTreeId = document.optString("treeId", "");
        if (expectedTreeId == null
            || expectedTreeId.isEmpty()
            || !expectedTreeId.equals(actualTreeId)) {
            throw new IllegalStateException("Идентификатор онлайн-дерева не совпадает");
        }
    }

    private static boolean sameJson(String first, String second) {
        try {
            return new JSONObject(first).toString().equals(new JSONObject(second).toString());
        } catch (Exception ignored) {
            return first != null && first.equals(second);
        }
    }

    private void deliverRemote(TreeState state, String message) {
        post(() -> listener.onRemoteTree(state, message));
    }

    private void refreshStatus() {
        if (!config.signedIn()) status = "Локально";
        else if (!config.connected()) status = "GitHub: @" + config.login();
        else if (!lastError.isEmpty()) status = "Офлайн";
        else if (localChangePending) status = "Ожидает сохранения";
        else if (dirty) status = "Ожидает синхронизации";
        else status = "Синхронизировано";
        post(listener::onStatusChanged);
    }

    private void setStatus(String value) {
        status = value == null ? "" : value;
        post(listener::onStatusChanged);
    }

    private void setError(Exception error) {
        lastError = friendlyError(error);
        status = config.connected() ? "Офлайн" : "Ошибка подключения";
        DiagnosticsLogger.handled(context, "online", error);
        post(listener::onStatusChanged);
    }

    private String friendlyError(Exception error) {
        if (error instanceof GitHubApi.ApiException) {
            GitHubApi.ApiException apiError = (GitHubApi.ApiException) error;
            if (apiError.status == 401) return "Сеанс GitHub истёк. Войдите снова.";
            if (apiError.status == 403) return "GitHub не разрешил операцию или временно ограничил запросы.";
            if (apiError.status == 404) return "Онлайн-дерево или приглашение не найдено.";
            if (apiError.status == 409) return "Дерево изменилось одновременно. Повторите синхронизацию.";
            if (apiError.status == 422) return "GitHub отклонил запрос. Проверьте права приложения.";
            return "Ошибка GitHub: " + apiError.apiMessage;
        }
        if (hasCause(error, UnknownHostException.class)) {
            return "Android не смог найти github.com. Проверьте подключение, "
                + "Private DNS или VPN и повторите вход.";
        }
        if (isTransientNetworkError(error)) {
            return "Связь с GitHub прервалась. Проверьте интернет и повторите вход.";
        }
        String message = error == null ? "" : error.getMessage();
        return message == null || message.trim().isEmpty()
            ? "Не удалось выполнить операцию"
            : message.trim();
    }

    private void post(Runnable action) {
        if (action != null && !closed) main.post(action);
    }

    private void postAuth(long attempt, Runnable action) {
        if (action == null || !authActive(attempt)) return;
        main.post(() -> {
            if (authActive(attempt)) action.run();
        });
    }

    private boolean authActive(long attempt) {
        return !closed && authAttempt.get() == attempt;
    }

    private boolean sessionActive(long generation) {
        return !closed && sessionGeneration.get() == generation;
    }

    private void applyAuthenticatedUser(String token, JSONObject user) {
        String login = user == null ? "" : user.optString("login", "").trim();
        long numericId = user == null ? 0L : user.optLong("id", 0L);
        if (login.isEmpty() || numericId <= 0L) {
            throw new IllegalStateException("GitHub не вернул идентификатор аккаунта");
        }
        String accountId = Long.toString(numericId);
        String previousId = config.accountId();
        String previousLogin = config.login();
        String treeAccountId = config.treeAccountId();
        boolean hadTree = !config.owner().isEmpty() && !config.repo().isEmpty();
        boolean mismatch = (!previousId.isEmpty() && !previousId.equals(accountId))
            || (previousId.isEmpty()
                && hadTree
                && !previousLogin.isEmpty()
                && !previousLogin.equalsIgnoreCase(login))
            || (!treeAccountId.isEmpty() && !treeAccountId.equals(accountId));
        if (mismatch) {
            sessionGeneration.incrementAndGet();
            config.clearTree();
            dirty = false;
            localChangePending = false;
            pendingStateJson = "";
            pendingMediaIds = new HashSet<>();
            clearMediaCache();
        }
        config.setToken(token);
        config.setIdentity(login, accountId);
        if (!mismatch && hadTree && config.treeAccountId().isEmpty()) {
            config.bindTreeToAccount(accountId);
        }
        identityVerified = true;
    }

    private void ensureIdentityVerified() throws Exception {
        if (identityVerified || !config.signedIn()) return;
        boolean hadTree = !config.owner().isEmpty() && !config.repo().isEmpty();
        String token = config.token();
        JSONObject user = api.currentUser(token);
        applyAuthenticatedUser(token, user);
        if (hadTree && !config.connected()) {
            throw new IllegalStateException(
                "GitHub-аккаунт изменился. Старое онлайн-дерево отключено для безопасности.");
        }
    }

    private void clearMediaCache() {
        cachedReleaseId = 0L;
        cachedReleaseTreeId = "";
        cachedAssetsAt = 0L;
        cachedAssets = new HashMap<>();
        mediaAuditNeeded = true;
    }

    private JSONObject requestDeviceCodeWithRetry(long attempt) throws Exception {
        Exception last = null;
        for (int retry = 0; retry < 6 && authActive(attempt); retry++) {
            try {
                return api.requestDeviceCode(BuildConfig.GITHUB_CLIENT_ID.trim());
            } catch (GitHubApi.ApiException apiError) {
                throw apiError;
            } catch (Exception networkError) {
                if (!isTransientNetworkError(networkError)) throw networkError;
                last = networkError;
                if (retry >= 5) break;
                setStatus("Связываемся с GitHub · попытка " + (retry + 2) + " из 6");
                Thread.sleep(Math.min(8_000L, 1_500L * (retry + 1L)));
            }
        }
        if (!authActive(attempt)) throw new InterruptedException();
        throw last == null
            ? new IllegalStateException("Не удалось связаться с GitHub")
            : last;
    }

    private JSONObject currentUserWithRetry(
        String token,
        long attempt,
        long deadline
    ) throws Exception {
        Exception last = null;
        while (authActive(attempt) && System.currentTimeMillis() < deadline) {
            if (!hasInternetNetwork()) {
                setStatus("Ожидаем восстановление интернета…");
                Thread.sleep(2_000L);
                continue;
            }
            try {
                return api.currentUser(token);
            } catch (GitHubApi.ApiException apiError) {
                if (apiError.status < 500 || apiError.status > 599) throw apiError;
                last = apiError;
            } catch (Exception networkError) {
                if (!isTransientNetworkError(networkError)) throw networkError;
                last = networkError;
            }
            setStatus("Завершаем вход · восстанавливаем связь…");
            Thread.sleep(3_000L);
        }
        if (!authActive(attempt)) throw new InterruptedException();
        throw last == null
            ? new UnknownHostException("GitHub недоступен через активную сеть")
            : last;
    }

    private JSONObject exchangeBrowserCodeWithRetry(
        String code,
        String verifier,
        long attempt
    ) throws Exception {
        long deadline = System.currentTimeMillis() + AUTH_NETWORK_RECOVERY_TIMEOUT;
        Exception last = null;
        while (authActive(attempt) && System.currentTimeMillis() < deadline) {
            if (!hasInternetNetwork()) {
                setStatus("Ожидаем восстановление интернета…");
                Thread.sleep(2_000L);
                continue;
            }
            try {
                return api.exchangeAuthorizationCode(
                    BuildConfig.GITHUB_CLIENT_ID.trim(),
                    BuildConfig.GITHUB_CLIENT_SECRET.trim(),
                    code,
                    OAUTH_REDIRECT_URI,
                    verifier);
            } catch (GitHubApi.ApiException apiError) {
                if (apiError.status < 500 || apiError.status > 599) throw apiError;
                last = apiError;
            } catch (Exception networkError) {
                if (!isTransientNetworkError(networkError)) throw networkError;
                last = networkError;
            }
            setStatus("Восстанавливаем связь с GitHub…");
            Thread.sleep(3_000L);
        }
        if (!authActive(attempt)) throw new InterruptedException();
        throw last == null
            ? new UnknownHostException("GitHub недоступен через активную сеть")
            : last;
    }

    private boolean hasInternetNetwork() {
        if (connectivityManager == null) return true;
        try {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities capabilities =
                connectivityManager.getNetworkCapabilities(network);
            return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (SecurityException ignored) {
            return true;
        }
    }

    private static boolean isTransientNetworkError(Throwable error) {
        return hasCause(error, UnknownHostException.class)
            || hasCause(error, SocketTimeoutException.class)
            || hasCause(error, ConnectException.class)
            || hasCause(error, NoRouteToHostException.class)
            || hasCause(error, SocketException.class);
    }

    private static boolean isRetryableMediaError(Throwable error) {
        if (isTransientNetworkError(error)) return true;
        if (!(error instanceof GitHubApi.ApiException)) return false;
        int status = ((GitHubApi.ApiException) error).status;
        return status == 408
            || status == 409
            || status == 422
            || status == 429
            || (status >= 500 && status <= 599);
    }

    private static boolean hasCause(
        Throwable error,
        Class<? extends Throwable> expected
    ) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (expected.isInstance(current)) return true;
            Throwable next = current.getCause();
            if (next == current) break;
            current = next;
        }
        return false;
    }

    private static void validateOAuthCallback(Uri uri) {
        if (uri == null
            || !"androidft".equalsIgnoreCase(uri.getScheme())
            || !"oauth".equalsIgnoreCase(uri.getHost())
            || !"/github".equals(uri.getPath())) {
            throw new IllegalArgumentException("Некорректный ответ входа GitHub");
        }
    }

    private static String oauthRandom(int byteCount) {
        byte[] bytes = new byte[Math.max(32, byteCount)];
        new SecureRandom().nextBytes(bytes);
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static boolean secureEquals(String first, String second) {
        return MessageDigest.isEqual(
            (first == null ? "" : first).getBytes(StandardCharsets.UTF_8),
            (second == null ? "" : second).getBytes(StandardCharsets.UTF_8));
    }
}
