package ru.drshapaya.androidft2;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class GitHubApi {
    private static final String API = "https://api.github.com";
    private static final String UPLOADS = "https://uploads.github.com";
    private static final String OAUTH = "https://github.com/login";
    private static final String API_VERSION = "2026-03-10";
    private static final int CONNECT_TIMEOUT = 15_000;
    private static final int READ_TIMEOUT = 30_000;
    private static final int MEDIA_READ_TIMEOUT = 15 * 60_000;
    private static final int MAX_REDIRECTS = 5;

    private final ConnectivityManager connectivityManager;

    GitHubApi(Context context) {
        Context app = context == null ? null : context.getApplicationContext();
        connectivityManager = app == null
            ? null
            : (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    static final class ApiException extends Exception {
        final int status;
        final String apiMessage;

        ApiException(int status, String message) {
            super(message);
            this.status = status;
            this.apiMessage = message == null ? "" : message;
        }
    }

    static final class FileContent {
        final String text;
        final String sha;
        final String etag;
        final boolean notModified;

        FileContent(String text, String sha) {
            this(text, sha, "", false);
        }

        FileContent(String text, String sha, String etag, boolean notModified) {
            this.text = text == null ? "" : text;
            this.sha = sha == null ? "" : sha;
            this.etag = etag == null ? "" : etag;
            this.notModified = notModified;
        }
    }

    static final class BinaryContent {
        final byte[] bytes;
        final String sha;

        BinaryContent(byte[] bytes, String sha) {
            this.bytes = bytes == null ? new byte[0] : bytes;
            this.sha = sha == null ? "" : sha;
        }
    }

    interface StreamReader<T> {
        T read(InputStream input, long contentLength) throws Exception;
    }

    static final class ReleaseAsset {
        final long id;
        final String name;
        final long size;
        final String state;

        ReleaseAsset(long id, String name, long size) {
            this(id, name, size, "");
        }

        ReleaseAsset(long id, String name, long size, String state) {
            this.id = id;
            this.name = name == null ? "" : name;
            this.size = Math.max(0L, size);
            this.state = state == null ? "" : state;
        }
    }

    JSONObject requestDeviceCode(String clientId) throws Exception {
        return form(
            OAUTH + "/device/code",
            "client_id=" + encode(clientId) + "&scope=" + encode("repo gist"));
    }

    String browserAuthorizationUrl(
        String clientId,
        String redirectUri,
        String state,
        String codeChallenge
    ) throws Exception {
        return OAUTH + "/oauth/authorize"
            + "?client_id=" + encode(clientId)
            + "&redirect_uri=" + encode(redirectUri)
            + "&scope=" + encode("repo gist")
            + "&state=" + encode(state)
            + "&code_challenge=" + encode(codeChallenge)
            + "&code_challenge_method=S256";
    }

    JSONObject exchangeAuthorizationCode(
        String clientId,
        String clientSecret,
        String code,
        String redirectUri,
        String codeVerifier
    ) throws Exception {
        return form(
            OAUTH + "/oauth/access_token",
            "client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(redirectUri)
                + "&code_verifier=" + encode(codeVerifier));
    }

    JSONObject pollDeviceToken(String clientId, String deviceCode) throws Exception {
        return form(
            OAUTH + "/oauth/access_token",
            "client_id=" + encode(clientId)
                + "&device_code=" + encode(deviceCode)
                + "&grant_type=" + encode("urn:ietf:params:oauth:grant-type:device_code"));
    }

    JSONObject currentUser(String token) throws Exception {
        return json("GET", API + "/user", token, null);
    }

    JSONObject createPrivateRepository(String token, String name) throws Exception {
        JSONObject body = new JSONObject()
            .put("name", name)
            .put("description", "Приватное онлайн-дерево AndroidFT")
            .put("private", true)
            .put("auto_init", true)
            .put("has_issues", false)
            .put("has_projects", false)
            .put("has_wiki", false);
        return json("POST", API + "/user/repos", token, body);
    }

    JSONObject createInvitationGist(String token, String treeId) throws Exception {
        JSONObject channel = new JSONObject()
            .put("protocol", 1)
            .put("type", "androidft-invitation-channel")
            .put("treeIdHash", OnlineInviteKey.shortHash(treeId))
            .put("notice", "Служебный канал AndroidFT. Данные дерева здесь не хранятся.");
        JSONObject body = new JSONObject()
            .put("description", "AndroidFT invitation channel")
            .put("public", false)
            .put("files", new JSONObject().put(
                "androidft-invite.json",
                new JSONObject().put("content", channel.toString())));
        return json("POST", API + "/gists", token, body);
    }

    JSONObject getGist(String token, String gistId) throws Exception {
        return json("GET", API + "/gists/" + segment(gistId), token, null);
    }

    JSONArray listGistComments(String token, String gistId) throws Exception {
        return listGistComments(token, gistId, 0L);
    }

    JSONArray listGistComments(String token, String gistId, long sinceMillis) throws Exception {
        JSONArray result = new JSONArray();
        String since = sinceMillis <= 0L ? "" : "&since=" + encode(isoTime(sinceMillis));
        for (int page = 1; ; page++) {
            JSONArray items = jsonArray(
                "GET",
                API + "/gists/" + segment(gistId)
                    + "/comments?per_page=100&page=" + page + since,
                token,
                null);
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) result.put(item);
            }
            if (items.length() < 100) break;
        }
        return result;
    }

    JSONObject createGistComment(String token, String gistId, String body) throws Exception {
        return json(
            "POST",
            API + "/gists/" + segment(gistId) + "/comments",
            token,
            new JSONObject().put("body", body));
    }

    void deleteGist(String token, String gistId) throws Exception {
        request("DELETE", API + "/gists/" + segment(gistId), token, null);
    }

    JSONObject addCollaborator(String token, String owner, String repo, String username) throws Exception {
        return setCollaboratorPermission(token, owner, repo, username, true);
    }

    JSONObject setCollaboratorPermission(
        String token,
        String owner,
        String repo,
        String username,
        boolean canEdit
    ) throws Exception {
        return jsonAllowEmpty(
            "PUT",
            API + "/repos/" + segment(owner) + "/" + segment(repo)
                + "/collaborators/" + segment(username),
            token,
            new JSONObject().put("permission", canEdit ? "push" : "pull"));
    }

    JSONArray listCollaborators(String token, String owner, String repo) throws Exception {
        return jsonArray(
            "GET",
            API + "/repos/" + segment(owner) + "/" + segment(repo)
                + "/collaborators?affiliation=direct&per_page=100",
            token,
            null);
    }

    void removeCollaborator(String token, String owner, String repo, String username) throws Exception {
        request(
            "DELETE",
            API + "/repos/" + segment(owner) + "/" + segment(repo)
                + "/collaborators/" + segment(username),
            token,
            null);
    }

    JSONArray listMyRepositoryInvitations(String token) throws Exception {
        return jsonArray(
            "GET",
            API + "/user/repository_invitations?per_page=100",
            token,
            null);
    }

    JSONArray listAccessibleRepositories(String token) throws Exception {
        JSONArray result = new JSONArray();
        for (int page = 1; ; page++) {
            JSONArray items = jsonArray(
                "GET",
                API + "/user/repos"
                    + "?visibility=all"
                    + "&affiliation=owner%2Ccollaborator"
                    + "&sort=updated"
                    + "&direction=desc"
                    + "&per_page=100"
                    + "&page=" + page,
                token,
                null);
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) result.put(item);
            }
            if (items.length() < 100) break;
        }
        return result;
    }

    JSONObject getRepository(
        String token,
        String owner,
        String repo
    ) throws Exception {
        return json(
            "GET",
            API + "/repos/" + segment(owner) + "/" + segment(repo),
            token,
            null);
    }

    void acceptRepositoryInvitation(String token, long invitationId) throws Exception {
        request(
            "PATCH",
            API + "/user/repository_invitations/" + invitationId,
            token,
            new JSONObject());
    }

    FileContent getFile(String token, String owner, String repo, String path) throws Exception {
        String url = API + "/repos/" + segment(owner) + "/" + segment(repo)
            + "/contents/" + path(path);
        Response raw = request("GET", url, token, null);
        JSONObject response = raw.body.isEmpty() ? new JSONObject() : new JSONObject(raw.body);
        return decodeFileContent(token, url, response, raw.etag);
    }

    FileContent getFileIfChanged(
        String token,
        String owner,
        String repo,
        String path,
        String etag
    ) throws Exception {
        if (etag == null || etag.trim().isEmpty()) {
            return getFile(token, owner, repo, path);
        }
        String url = API + "/repos/" + segment(owner) + "/" + segment(repo)
            + "/contents/" + path(path);
        HttpURLConnection connection = open("GET", url, token);
        connection.setRequestProperty("If-None-Match", etag.trim());
        Response raw = read(connection);
        if (raw.status == HttpURLConnection.HTTP_NOT_MODIFIED) {
            return new FileContent("", "", raw.etag.isEmpty() ? etag : raw.etag, true);
        }
        if (raw.status < 200 || raw.status >= 300) throw apiException(raw);
        JSONObject response = raw.body.isEmpty() ? new JSONObject() : new JSONObject(raw.body);
        return decodeFileContent(token, url, response, raw.etag);
    }

    private FileContent decodeFileContent(
        String token,
        String url,
        JSONObject response,
        String etag
    ) throws Exception {
        String content = response.optString("content", "").replaceAll("\\s+", "");
        byte[] decoded = content.isEmpty()
            ? getRawFile(token, url, 12 * 1024 * 1024)
            : Base64.decode(content, Base64.DEFAULT);
        return new FileContent(
            new String(decoded, StandardCharsets.UTF_8),
            response.optString("sha", ""),
            etag,
            false);
    }

    FileContent putFile(
        String token,
        String owner,
        String repo,
        String path,
        String text,
        String sha,
        String message
    ) throws Exception {
        String encoded = Base64.encodeToString(
            (text == null ? "" : text).getBytes(StandardCharsets.UTF_8),
            Base64.NO_WRAP);
        JSONObject body = new JSONObject()
            .put("message", message == null || message.isEmpty() ? "Синхронизация AndroidFT" : message)
            .put("content", encoded);
        if (sha != null && !sha.isEmpty()) body.put("sha", sha);
        JSONObject response = json(
            "PUT",
            API + "/repos/" + segment(owner) + "/" + segment(repo)
                + "/contents/" + path(path),
            token,
            body);
        JSONObject content = response.optJSONObject("content");
        return new FileContent(text, content == null ? "" : content.optString("sha", ""));
    }

    BinaryContent getBinaryFile(
        String token,
        String owner,
        String repo,
        String path
    ) throws Exception {
        String url = API + "/repos/" + segment(owner) + "/" + segment(repo)
            + "/contents/" + path(path);
        return new BinaryContent(getRawFile(token, url, 9 * 1024 * 1024), "");
    }

    private byte[] getRawFile(String token, String url, int maxBytes) throws Exception {
        HttpURLConnection connection = open("GET", url, token);
        connection.setRequestProperty("Accept", "application/vnd.github.raw+json");
        try {
            int status = connection.getResponseCode();
            InputStream source = status >= 200 && status < 400
                ? connection.getInputStream()
                : connection.getErrorStream();
            byte[] bytes = source == null ? new byte[0] : readBinary(source, maxBytes);
            if (status < 200 || status >= 300) {
                String errorBody = new String(bytes, StandardCharsets.UTF_8);
                JSONObject error = null;
                try {
                    error = errorBody.isEmpty() ? null : new JSONObject(errorBody);
                } catch (Exception ignored) {
                }
                throw new ApiException(status, message(error, errorBody));
            }
            return bytes;
        } finally {
            connection.disconnect();
        }
    }

    BinaryContent putBinaryFile(
        String token,
        String owner,
        String repo,
        String path,
        byte[] bytes,
        String message
    ) throws Exception {
        String encoded = Base64.encodeToString(
            bytes == null ? new byte[0] : bytes,
            Base64.NO_WRAP);
        JSONObject body = new JSONObject()
            .put("message", message == null || message.isEmpty() ? "Медиа AndroidFT" : message)
            .put("content", encoded);
        JSONObject response = json(
            "PUT",
            API + "/repos/" + segment(owner) + "/" + segment(repo)
                + "/contents/" + path(path),
            token,
            body);
        JSONObject content = response.optJSONObject("content");
        return new BinaryContent(
            bytes,
            content == null ? "" : content.optString("sha", ""));
    }

    JSONObject getReleaseByTag(
        String token,
        String owner,
        String repo,
        String tag
    ) throws Exception {
        return json(
            "GET",
            API + "/repos/" + segment(owner) + "/" + segment(repo)
                + "/releases/tags/" + segment(tag),
            token,
            null);
    }

    JSONObject createMediaRelease(
        String token,
        String owner,
        String repo,
        String tag
    ) throws Exception {
        JSONObject body = new JSONObject()
            .put("tag_name", tag)
            .put("name", "AndroidFT · хранилище медиа")
            .put(
                "body",
                "Служебное хранилище фото и вложений онлайн-дерева AndroidFT. "
                    + "Не удаляйте и не переименовывайте файлы вручную.")
            .put("draft", false)
            .put("prerelease", true)
            .put("generate_release_notes", false)
            .put("make_latest", "false");
        return json(
            "POST",
            API + "/repos/" + segment(owner) + "/" + segment(repo) + "/releases",
            token,
            body);
    }

    JSONArray listReleaseAssets(
        String token,
        String owner,
        String repo,
        long releaseId
    ) throws Exception {
        if (releaseId <= 0L) throw new IllegalArgumentException("Некорректный релиз медиа");
        JSONArray result = new JSONArray();
        for (int page = 1; ; page++) {
            JSONArray items = jsonArray(
                "GET",
                API + "/repos/" + segment(owner) + "/" + segment(repo)
                    + "/releases/" + releaseId + "/assets?per_page=100&page=" + page,
                token,
                null);
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) result.put(item);
            }
            if (items.length() < 100) break;
        }
        return result;
    }

    void deleteReleaseAsset(
        String token,
        String owner,
        String repo,
        long assetId
    ) throws Exception {
        if (assetId <= 0L) return;
        json(
            "DELETE",
            API + "/repos/" + segment(owner) + "/" + segment(repo)
                + "/releases/assets/" + assetId,
            token,
            null);
    }

    ReleaseAsset uploadReleaseAsset(
        String token,
        String owner,
        String repo,
        long releaseId,
        String assetName,
        String contentType,
        File file
    ) throws Exception {
        if (releaseId <= 0L) throw new IllegalArgumentException("Некорректный релиз медиа");
        if (file == null || !file.isFile()) throw new IllegalArgumentException("Медиафайл не найден");
        String safeName = segment(assetName);
        HttpURLConnection connection = open(
            "POST",
            UPLOADS + "/repos/" + segment(owner) + "/" + segment(repo)
                + "/releases/" + releaseId + "/assets?name=" + encode(safeName),
            token);
        connection.setReadTimeout(MEDIA_READ_TIMEOUT);
        connection.setRequestProperty(
            "Content-Type",
            contentType == null || contentType.trim().isEmpty()
                ? "application/octet-stream"
                : contentType.trim());
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(file.length());
        try {
            try (InputStream input = new FileInputStream(file);
                 OutputStream output = connection.getOutputStream()) {
                copy(input, output);
            }
            Response response = read(connection);
            if (response.status < 200 || response.status >= 300) {
                throw apiException(response);
            }
            JSONObject uploaded = response.body.isEmpty()
                ? new JSONObject()
                : new JSONObject(response.body);
            return releaseAsset(uploaded);
        } catch (Exception error) {
            connection.disconnect();
            throw error;
        }
    }

    <T> T downloadReleaseAsset(
        String token,
        String owner,
        String repo,
        long assetId,
        StreamReader<T> reader
    ) throws Exception {
        if (assetId <= 0L) throw new IllegalArgumentException("Некорректный медиафайл");
        if (reader == null) throw new IllegalArgumentException("Не указан обработчик медиа");
        String url = API + "/repos/" + segment(owner) + "/" + segment(repo)
            + "/releases/assets/" + assetId;
        String authorization = token;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            HttpURLConnection connection = open("GET", url, authorization);
            connection.setInstanceFollowRedirects(false);
            connection.setReadTimeout(MEDIA_READ_TIMEOUT);
            connection.setRequestProperty("Accept", "application/octet-stream");
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.trim().isEmpty()) {
                        throw new ApiException(status, "GitHub не вернул адрес медиафайла");
                    }
                    URI redirected = URI.create(location);
                    if (!"https".equalsIgnoreCase(redirected.getScheme())) {
                        throw new IllegalStateException("Небезопасный адрес медиафайла");
                    }
                    url = redirected.toString();
                    authorization = "";
                    continue;
                }
                if (status < 200 || status >= 300) {
                    InputStream source = connection.getErrorStream();
                    String body = source == null
                        ? ""
                        : new String(readBinary(source, 1024 * 1024), StandardCharsets.UTF_8);
                    JSONObject error = null;
                    try {
                        error = body.isEmpty() ? null : new JSONObject(body);
                    } catch (Exception ignored) {
                    }
                    throw new ApiException(status, message(error, body));
                }
                long contentLength = connection.getContentLengthLong();
                try (InputStream input = connection.getInputStream()) {
                    return reader.read(input, contentLength);
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IllegalStateException("Слишком много перенаправлений при загрузке медиа");
    }

    static ReleaseAsset releaseAsset(JSONObject source) {
        if (source == null) return new ReleaseAsset(0L, "", 0L);
        return new ReleaseAsset(
            source.optLong("id", 0L),
            source.optString("name", ""),
            source.optLong("size", 0L),
            source.optString("state", ""));
    }

    private JSONObject form(String url, String body) throws Exception {
        HttpURLConnection connection = open("POST", url, "");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        write(connection, body.getBytes(StandardCharsets.UTF_8));
        Response response = read(connection);
        JSONObject json = response.body.isEmpty() ? new JSONObject() : new JSONObject(response.body);
        if (response.status < 200 || response.status >= 300) {
            throw new ApiException(response.status, message(json, response.body));
        }
        return json;
    }

    private JSONObject json(String method, String url, String token, JSONObject body) throws Exception {
        Response response = request(method, url, token, body);
        if (response.body.isEmpty()) return new JSONObject();
        return new JSONObject(response.body);
    }

    private JSONObject jsonAllowEmpty(String method, String url, String token, JSONObject body) throws Exception {
        Response response = request(method, url, token, body);
        return response.body.isEmpty() ? new JSONObject() : new JSONObject(response.body);
    }

    private JSONArray jsonArray(String method, String url, String token, JSONObject body) throws Exception {
        Response response = request(method, url, token, body);
        return response.body.isEmpty() ? new JSONArray() : new JSONArray(response.body);
    }

    private Response request(String method, String url, String token, JSONObject body) throws Exception {
        HttpURLConnection connection = open(method, url, token);
        if (body != null) {
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            write(connection, body.toString().getBytes(StandardCharsets.UTF_8));
        }
        Response response = read(connection);
        if (response.status < 200 || response.status >= 300) {
            throw apiException(response);
        }
        return response;
    }

    private HttpURLConnection open(String method, String url, String token) throws Exception {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Разрешены только HTTPS-запросы");
        }
        HttpURLConnection connection = null;
        if (connectivityManager != null) {
            try {
                Network activeNetwork = connectivityManager.getActiveNetwork();
                if (activeNetwork != null) {
                    connection = (HttpURLConnection) activeNetwork.openConnection(uri.toURL());
                }
            } catch (SecurityException ignored) {
                // Fallback below keeps networking available on unusual OEM builds.
            }
        }
        if (connection == null) {
            connection = (HttpURLConnection) uri.toURL().openConnection();
        }
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setUseCaches(false);
        connection.setRequestProperty("Connection", "close");
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", API_VERSION);
        connection.setRequestProperty("User-Agent", "AndroidFT/" + MainActivity.VERSION_NAME);
        if (token != null && !token.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        return connection;
    }

    private static void write(HttpURLConnection connection, byte[] data) throws Exception {
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(data.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(data);
        }
    }

    private static Response read(HttpURLConnection connection) throws Exception {
        int status = connection.getResponseCode();
        String etag = connection.getHeaderField("ETag");
        if (status == HttpURLConnection.HTTP_NOT_MODIFIED) {
            connection.disconnect();
            return new Response(status, "", etag);
        }
        InputStream source = status >= 200 && status < 400
            ? connection.getInputStream()
            : connection.getErrorStream();
        if (source == null) {
            connection.disconnect();
            return new Response(status, "", etag);
        }
        try (InputStream input = source; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > 16 * 1024 * 1024) {
                    throw new IllegalStateException("Ответ GitHub слишком большой");
                }
                output.write(buffer, 0, read);
            }
            return new Response(status, output.toString("UTF-8"), etag);
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readBinary(InputStream source, int maxBytes) throws Exception {
        try (InputStream input = source; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[64 * 1024];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new IllegalStateException("Медиафайл слишком большой");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static void copy(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[128 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    private static ApiException apiException(Response response) {
        JSONObject error = null;
        try {
            error = response.body.isEmpty() ? null : new JSONObject(response.body);
        } catch (Exception ignored) {
        }
        return new ApiException(response.status, message(error, response.body));
    }

    private static String message(JSONObject json, String fallback) {
        String value = json == null ? "" : json.optString("message", "");
        if (!value.isEmpty()) return value;
        if (fallback == null || fallback.isEmpty()) return "Ошибка GitHub";
        return fallback.length() > 240 ? fallback.substring(0, 240) : fallback;
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    private static String isoTime(long millis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(Math.max(0L, millis)));
    }

    private static String segment(String value) {
        String safe = value == null ? "" : value.trim();
        if (!safe.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Некорректный идентификатор GitHub");
        }
        return safe;
    }

    private static String path(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.isEmpty() || safe.startsWith("/") || safe.contains("..")) {
            throw new IllegalArgumentException("Некорректный путь файла");
        }
        StringBuilder encoded = new StringBuilder();
        for (String part : safe.split("/")) {
            if (encoded.length() > 0) encoded.append('/');
            encoded.append(segment(part));
        }
        return encoded.toString();
    }

    private static final class Response {
        final int status;
        final String body;
        final String etag;

        Response(int status, String body, String etag) {
            this.status = status;
            this.body = body == null ? "" : body;
            this.etag = etag == null ? "" : etag;
        }
    }
}
