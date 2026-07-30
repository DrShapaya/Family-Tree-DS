package ru.drshapaya.androidft2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GitHubApiTest {
    @Test
    public void browserAuthorizationUrlIncludesRedirectStateAndPkce() throws Exception {
        GitHubApi api = new GitHubApi(null);

        String url = api.browserAuthorizationUrl(
            "client-id",
            "androidft://oauth/github",
            "random-state",
            "sha256-challenge");

        assertTrue(url.startsWith("https://github.com/login/oauth/authorize?"));
        assertTrue(url.contains("client_id=client-id"));
        assertTrue(url.contains("redirect_uri=androidft%3A%2F%2Foauth%2Fgithub"));
        assertTrue(url.contains("scope=repo+gist"));
        assertTrue(url.contains("state=random-state"));
        assertTrue(url.contains("code_challenge=sha256-challenge"));
        assertTrue(url.contains("code_challenge_method=S256"));
        assertFalse(url.contains("client_secret"));
    }
}
