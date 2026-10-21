package ru.drshapaya.androidft2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;

public final class GitHubApiTest {
    @Test
    public void validationReasonReachesUserInsteadOfGenericPermissionAdvice() throws Exception {
        JSONObject response = new JSONObject().put("message", "Validation Failed")
            .put("errors", new JSONArray().put(new JSONObject()
                .put("field", "permission")
                .put("message", "Cannot assign requested permission")));
        String message = OnlineTreeManager.friendlyError(new GitHubApi.ApiException(
            422, GitHubApi.message(response, "")));
        assertTrue(message.contains("Cannot assign requested permission"));
        assertFalse(message.contains("Проверьте права приложения"));
    }

    @Test
    public void validationDetailsSupportStringAndFieldCodeErrors() throws Exception {
        JSONObject response = new JSONObject().put("message", "Validation Failed")
            .put("errors", new JSONArray().put("First reason")
                .put(new JSONObject().put("field", "permission").put("code", "invalid")));
        assertEquals("Validation Failed; First reason; permission: invalid",
            GitHubApi.message(response, ""));
    }

    @Test
    public void errorMessagesHandleEmptyResponsesAndBoundLongDetails() throws Exception {
        assertEquals("Ошибка GitHub", GitHubApi.message(null, null));
        assertEquals("Upstream unavailable", GitHubApi.message(null, "Upstream\n unavailable"));
        assertEquals("GitHub отклонил запрос.",
            OnlineTreeManager.friendlyError(new GitHubApi.ApiException(422, "")));
        assertEquals(480, GitHubApi.message(new JSONObject().put("message", "x".repeat(600)), "").length());
        assertEquals("Сеанс GitHub истёк. Войдите снова.",
            OnlineTreeManager.friendlyError(new GitHubApi.ApiException(401, "Bad credentials")));
    }

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
