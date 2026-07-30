package ru.drshapaya.androidft2;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class OnlineInviteKey {
    private static final String KEY_PREFIX = "AFT1";
    private static final String REQUEST_PREFIX = "AFTREQ1.";
    private static final String RESPONSE_PREFIX = "AFTOK1.";
    private static final char[] BASE64_URL =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    static final class Parsed {
        final String gistId;
        final String secret;

        Parsed(String gistId, String secret) {
            this.gistId = gistId;
            this.secret = secret;
        }
    }

    private OnlineInviteKey() {
    }

    static String newSecret() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return base64(value);
    }

    static String create(String gistId, String secret) {
        if (!validGistId(gistId) || decodeSecret(secret).length != 32) {
            throw new IllegalArgumentException("Не удалось сформировать ключ дерева");
        }
        return KEY_PREFIX + "-" + gistId + "-" + secret;
    }

    static Parsed parse(String source) {
        String normalized = source == null ? "" : source.trim().replaceAll("\\s+", "");
        if (!normalized.startsWith(KEY_PREFIX + "-")) {
            throw new IllegalArgumentException("Это не ключ AndroidFT");
        }
        String body = normalized.substring((KEY_PREFIX + "-").length());
        int separator = body.indexOf('-');
        if (separator <= 0 || separator >= body.length() - 1) {
            throw new IllegalArgumentException("Ключ дерева неполный");
        }
        String gistId = body.substring(0, separator);
        String secret = body.substring(separator + 1);
        if (!validGistId(gistId) || decodeSecret(secret).length != 32) {
            throw new IllegalArgumentException("Ключ дерева повреждён");
        }
        return new Parsed(gistId, secret);
    }

    static String randomNonce() {
        byte[] value = new byte[16];
        RANDOM.nextBytes(value);
        return base64(value);
    }

    static String request(String secret, String login, String nonce) throws Exception {
        JSONObject payload = new JSONObject()
            .put("type", "join")
            .put("login", login)
            .put("nonce", nonce)
            .put("createdAt", System.currentTimeMillis());
        return REQUEST_PREFIX + encrypt(secret, "request", payload.toString());
    }

    static JSONObject readRequest(String secret, String body) throws Exception {
        if (body == null || !body.startsWith(REQUEST_PREFIX)) return null;
        JSONObject result = new JSONObject(
            decrypt(secret, "request", body.substring(REQUEST_PREFIX.length())));
        return "join".equals(result.optString("type", "")) ? result : null;
    }

    static String response(
        String secret,
        String login,
        String nonce,
        String owner,
        String repo,
        String treeId
    ) throws Exception {
        JSONObject payload = new JSONObject()
            .put("type", "accepted")
            .put("login", login)
            .put("nonce", nonce)
            .put("owner", owner)
            .put("repo", repo)
            .put("treeId", treeId)
            .put("createdAt", System.currentTimeMillis());
        return RESPONSE_PREFIX + encrypt(secret, "response", payload.toString());
    }

    static JSONObject readResponse(String secret, String body) throws Exception {
        if (body == null || !body.startsWith(RESPONSE_PREFIX)) return null;
        JSONObject result = new JSONObject(
            decrypt(secret, "response", body.substring(RESPONSE_PREFIX.length())));
        return "accepted".equals(result.optString("type", "")) ? result : null;
    }

    static String shortHash(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < 8; i++) result.append(String.format("%02x", hash[i] & 0xff));
            return result.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String encrypt(String secret, String purpose, String text) throws Exception {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
            Cipher.ENCRYPT_MODE,
            new SecretKeySpec(decodeSecret(secret), "AES"),
            new GCMParameterSpec(128, iv));
        cipher.updateAAD(("AndroidFT/" + purpose + "/1").getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        byte[] packed = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, packed, 0, iv.length);
        System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
        return base64(packed);
    }

    private static String decrypt(String secret, String purpose, String packed) throws Exception {
        byte[] data = decode(packed);
        if (data.length < 12 + 16) throw new IllegalArgumentException("Сообщение повреждено");
        byte[] iv = new byte[12];
        byte[] encrypted = new byte[data.length - iv.length];
        System.arraycopy(data, 0, iv, 0, iv.length);
        System.arraycopy(data, iv.length, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
            Cipher.DECRYPT_MODE,
            new SecretKeySpec(decodeSecret(secret), "AES"),
            new GCMParameterSpec(128, iv));
        cipher.updateAAD(("AndroidFT/" + purpose + "/1").getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static byte[] decodeSecret(String value) {
        try {
            return decode(value);
        } catch (Exception ignored) {
            return new byte[0];
        }
    }

    private static String base64(byte[] value) {
        StringBuilder result = new StringBuilder((value.length * 4 + 2) / 3);
        for (int offset = 0; offset < value.length; offset += 3) {
            int first = value[offset] & 0xff;
            int second = offset + 1 < value.length ? value[offset + 1] & 0xff : 0;
            int third = offset + 2 < value.length ? value[offset + 2] & 0xff : 0;
            result.append(BASE64_URL[first >>> 2]);
            result.append(BASE64_URL[((first & 3) << 4) | (second >>> 4)]);
            if (offset + 1 < value.length) {
                result.append(BASE64_URL[((second & 15) << 2) | (third >>> 6)]);
            }
            if (offset + 2 < value.length) result.append(BASE64_URL[third & 63]);
        }
        return result.toString();
    }

    private static byte[] decode(String value) {
        String source = value == null ? "" : value;
        if (source.length() % 4 == 1) throw new IllegalArgumentException("Некорректный Base64");
        ByteArrayOutputStream output = new ByteArrayOutputStream(source.length() * 3 / 4);
        int buffer = 0;
        int bits = 0;
        for (int index = 0; index < source.length(); index++) {
            int decoded = base64Value(source.charAt(index));
            if (decoded < 0) throw new IllegalArgumentException("Некорректный Base64");
            buffer = (buffer << 6) | decoded;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                output.write((buffer >>> bits) & 0xff);
            }
        }
        if (bits > 0 && (buffer & ((1 << bits) - 1)) != 0) {
            throw new IllegalArgumentException("Некорректный Base64");
        }
        return output.toByteArray();
    }

    private static int base64Value(char value) {
        if (value >= 'A' && value <= 'Z') return value - 'A';
        if (value >= 'a' && value <= 'z') return value - 'a' + 26;
        if (value >= '0' && value <= '9') return value - '0' + 52;
        if (value == '-') return 62;
        if (value == '_') return 63;
        return -1;
    }

    private static boolean validGistId(String value) {
        return value != null && value.matches("[A-Fa-f0-9]{12,64}");
    }
}
