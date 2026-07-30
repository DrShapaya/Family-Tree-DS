package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

public final class OnlineInviteKeyTest {
    @Test
    public void parsesStandardUnpaddedBase64UrlSecret() {
        String secret = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

        OnlineInviteKey.Parsed parsed = OnlineInviteKey.parse(
            "AFT1-0123456789abcdef-" + secret);

        assertEquals(secret, parsed.secret);
    }

    @Test
    public void invitationRequestAndResponseRoundTrip() throws Exception {
        String secret = OnlineInviteKey.newSecret();
        String nonce = OnlineInviteKey.randomNonce();
        String key = OnlineInviteKey.create("0123456789abcdef", secret);

        OnlineInviteKey.Parsed parsed = OnlineInviteKey.parse(key);
        JSONObject request = OnlineInviteKey.readRequest(
            parsed.secret,
            OnlineInviteKey.request(secret, "octocat", nonce));
        JSONObject response = OnlineInviteKey.readResponse(
            parsed.secret,
            OnlineInviteKey.response(
                secret,
                "octocat",
                nonce,
                "owner",
                "androidft-tree",
                "12345678-abcd"));

        assertEquals("0123456789abcdef", parsed.gistId);
        assertEquals("octocat", request.getString("login"));
        assertEquals(nonce, response.getString("nonce"));
        assertEquals("owner", response.getString("owner"));
    }
}
