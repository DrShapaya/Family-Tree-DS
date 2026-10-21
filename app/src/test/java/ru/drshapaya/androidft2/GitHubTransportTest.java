package ru.drshapaya.androidft2;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class GitHubTransportTest {
    @Test public void completeApiResponsesCloseBodiesAndAllowConnectionReuse() throws Exception {
        FakeConnection first = new FakeConnection(200, "{\"id\":42}");
        FakeConnection second = new FakeConnection(200, "[]");
        Backend backend = new Backend(first, second);
        GitHubApi api = new GitHubApi(null, backend);
        assertEquals(42, api.getReleaseByTag("test-token", "owner", "repo", "tag").getInt("id"));
        assertEquals(0, api.listReleaseAssets("test-token", "owner", "repo", 42).length());
        for (FakeConnection connection : new FakeConnection[]{first, second}) {
            assertTrue(connection.bodyClosed);
            assertFalse(connection.disconnected);
            assertNull(connection.getRequestProperty("Connection"));
            assertEquals("Bearer test-token", connection.getRequestProperty("Authorization"));
            assertEquals(15_000, connection.getConnectTimeout());
        }
        assertEquals("https", backend.requests.get(0).getScheme());
    }

    @Test public void conditionalNotModifiedKeepsEtagAndDoesNotOpenBody() throws Exception {
        FakeConnection connection = new FakeConnection(304, "");
        GitHubApi.FileContent result = new GitHubApi(null, new Backend(connection))
            .getFileIfChanged("token", "owner", "repo", "androidft/tree.json", "\"cached\"");
        assertTrue(result.notModified);
        assertEquals("\"cached\"", result.etag);
        assertEquals("\"cached\"", connection.getRequestProperty("If-None-Match"));
        assertFalse(connection.bodyOpened);
        assertFalse(connection.disconnected);
    }

    @Test public void failedStatusAndBodyReadsDisconnectEvenBeforeStreamOpens() throws Exception {
        FakeConnection status = new FakeConnection(200, "{}");
        status.failStatus = true;
        FakeConnection body = new FakeConnection(200, "{}");
        body.failRead = true;
        for (FakeConnection connection : new FakeConnection[]{status, body}) {
            try {
                new GitHubApi(null, new Backend(connection)).getReleaseByTag("", "owner", "repo", "tag");
                fail("Expected transport failure");
            } catch (IOException expected) {
                assertTrue(connection.disconnected);
            }
        }
        assertTrue(body.bodyClosed);
    }

    @Test public void failedRequestWriteClosesBodyAndDisconnects() throws Exception {
        FakeConnection connection = new FakeConnection(200, "{}");
        connection.failWrite = true;
        try {
            new GitHubApi(null, new Backend(connection)).createMediaRelease("", "owner", "repo", "tag");
            fail("Expected write failure");
        } catch (IOException expected) {
            assertTrue(connection.outputClosed);
            assertTrue(connection.disconnected);
        }
    }

    @Test public void consumedApiErrorKeepsUsefulMessageWithoutLeakingBody() throws Exception {
        FakeConnection connection = new FakeConnection(403, "{\"message\":\"Permission denied\"}");
        try {
            new GitHubApi(null, new Backend(connection)).getReleaseByTag("", "owner", "repo", "tag");
            fail("Expected API error");
        } catch (GitHubApi.ApiException expected) {
            assertEquals(403, expected.status);
            assertEquals("Permission denied", expected.getMessage());
            assertTrue(connection.bodyClosed);
            assertFalse(connection.disconnected);
        }
    }

    @Test public void redirectedMediaDropsTokenAndClosesBothConnections() throws Exception {
        FakeConnection redirect = new FakeConnection(302, "");
        redirect.location = "https://assets.example.test/photo";
        FakeConnection data = new FakeConnection(200, "original-media");
        Backend backend = new Backend(redirect, data);
        byte[] bytes = new GitHubApi(null, backend).downloadReleaseAsset("secret", "owner", "repo", 42,
            (input, length) -> input.readAllBytes());
        assertEquals("original-media", new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("Bearer secret", redirect.getRequestProperty("Authorization"));
        assertNull(data.getRequestProperty("Authorization"));
        assertFalse(redirect.getInstanceFollowRedirects());
        assertTrue(redirect.disconnected);
        assertTrue(data.disconnected);
        assertTrue(data.bodyClosed);
    }

    @Test public void mediaRejectsInsecureRedirectBeforeOpeningAnotherConnection() throws Exception {
        FakeConnection redirect = new FakeConnection(302, "");
        redirect.location = "http://assets.example.test/photo";
        Backend backend = new Backend(redirect);
        try {
            new GitHubApi(null, backend).downloadReleaseAsset("secret", "owner", "repo", 42,
                (input, length) -> input.readAllBytes());
            fail("Expected HTTPS guard");
        } catch (IllegalStateException expected) {
            assertEquals(1, backend.requests.size());
            assertTrue(redirect.disconnected);
        }
    }

    private static final class Backend implements GitHubApi.ConnectionFactory {
        final ArrayDeque<FakeConnection> responses = new ArrayDeque<>();
        final List<URI> requests = new ArrayList<>();
        Backend(FakeConnection... connections) { java.util.Collections.addAll(responses, connections); }
        @Override public HttpURLConnection open(URI uri) {
            requests.add(uri);
            if (responses.isEmpty()) throw new AssertionError("Unexpected request " + uri);
            return responses.removeFirst();
        }
    }

    private static final class FakeConnection extends HttpURLConnection {
        final byte[] body;
        boolean disconnected, bodyOpened, bodyClosed, outputClosed;
        boolean failStatus, failRead, failWrite;
        String location;
        FakeConnection(int status, String body) throws Exception {
            super(URI.create("https://api.example.test").toURL());
            responseCode = status;
            this.body = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        @Override public void connect() { }
        @Override public boolean usingProxy() { return false; }
        @Override public void disconnect() { disconnected = true; }
        @Override public int getResponseCode() throws IOException {
            if (failStatus) throw new IOException("Status unavailable");
            return responseCode;
        }
        @Override public String getHeaderField(String name) {
            return "Location".equals(name) ? location : null;
        }
        @Override public long getContentLengthLong() { return body.length; }
        @Override public InputStream getErrorStream() { return stream(); }
        @Override public InputStream getInputStream() { return stream(); }
        private InputStream stream() {
            bodyOpened = true;
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new InputStream() {
                @Override public int read() throws IOException {
                    if (failRead) throw new IOException("Interrupted body");
                    return source.read();
                }
                @Override public void close() { bodyClosed = true; }
            };
        }
        @Override public OutputStream getOutputStream() {
            return new OutputStream() {
                @Override public void write(int value) throws IOException {
                    if (failWrite) throw new IOException("Interrupted write");
                }
                @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                    if (failWrite) throw new IOException("Interrupted write");
                }
                @Override public void close() { outputClosed = true; }
            };
        }
    }
}
