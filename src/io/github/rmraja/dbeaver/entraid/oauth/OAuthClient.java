package io.github.rmraja.dbeaver.entraid.oauth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calls to the Microsoft identity platform v2.0 endpoints for one tenant/client.
 */
public final class OAuthClient {

    private static final String AUTHORITY_HOST = "https://login.microsoftonline.com/";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(HTTP_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private final String endpointBase;
    private final String clientId;
    private final String scope;

    public OAuthClient(String tenant, String clientId, String resourceScope) {
        this.endpointBase = AUTHORITY_HOST + encode(tenant) + "/oauth2/v2.0/";
        this.clientId = clientId;
        // offline_access gives us a refresh token, so later connects need no prompt
        this.scope = resourceScope + " offline_access";
    }

    /** Builds the browser sign-in URL for the authorization code flow with PKCE. */
    public String authorizeUrl(String redirectUri, String state, String codeChallenge, String loginHint) {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("client_id", clientId);
        q.put("response_type", "code");
        q.put("redirect_uri", redirectUri);
        q.put("response_mode", "query");
        q.put("scope", scope);
        q.put("state", state);
        q.put("code_challenge", codeChallenge);
        q.put("code_challenge_method", "S256");
        if (loginHint != null && loginHint.contains("@")) {
            q.put("login_hint", loginHint);
        } else {
            q.put("prompt", "select_account");
        }
        return endpointBase + "authorize?" + formEncode(q);
    }

    public TokenResult exchangeCode(String code, String redirectUri, String codeVerifier)
        throws IOException, InterruptedException, OAuthException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("code_verifier", codeVerifier);
        form.put("scope", scope);
        return toTokenResult(post("token", form));
    }

    public TokenResult refresh(String refreshToken) throws IOException, InterruptedException, OAuthException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("scope", scope);
        TokenResult result = toTokenResult(post("token", form));
        // Entra ID normally rotates the refresh token; keep the old one if it didn't
        return result.refreshToken() == null
            ? new TokenResult(result.accessToken(), refreshToken, result.expiresAt())
            : result;
    }

    public DeviceCodeInfo requestDeviceCode() throws IOException, InterruptedException, OAuthException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("scope", scope);
        Map<String, Object> r = post("devicecode", form);
        return new DeviceCodeInfo(
            str(r, "device_code"),
            str(r, "user_code"),
            str(r, "verification_uri"),
            str(r, "message"),
            num(r, "expires_in", 900),
            num(r, "interval", 5));
    }

    /**
     * One poll of the token endpoint for a device code.
     * Throws {@link OAuthException} with {@code authorization_pending} / {@code slow_down} while waiting.
     */
    public TokenResult pollDeviceCode(String deviceCode) throws IOException, InterruptedException, OAuthException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
        form.put("device_code", deviceCode);
        return toTokenResult(post("token", form));
    }

    private Map<String, Object> post(String endpoint, Map<String, String> form)
        throws IOException, InterruptedException, OAuthException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpointBase + endpoint))
            .timeout(HTTP_TIMEOUT)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(formEncode(form)))
            .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> body;
        try {
            body = Json.parseObject(response.body());
        } catch (IOException e) {
            throw new IOException("Unexpected response from " + endpoint + " (HTTP " + response.statusCode() + ")", e);
        }
        if (body.get("error") != null) {
            throw new OAuthException(str(body, "error"), str(body, "error_description"));
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode() + " from " + endpoint);
        }
        return body;
    }

    private static TokenResult toTokenResult(Map<String, Object> r) throws OAuthException {
        String accessToken = str(r, "access_token");
        if (accessToken == null) {
            throw new OAuthException("no_access_token", "Token response contained no access token");
        }
        Instant expiresAt = Instant.now().plusSeconds(num(r, "expires_in", 3600));
        return new TokenResult(accessToken, str(r, "refresh_token"), expiresAt);
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private static int num(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return def;
    }

    static String formEncode(Map<String, String> params) {
        return params.entrySet().stream()
            .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
            .collect(Collectors.joining("&"));
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
