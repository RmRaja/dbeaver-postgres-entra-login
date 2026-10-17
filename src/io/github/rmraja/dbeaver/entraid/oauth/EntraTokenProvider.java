package io.github.rmraja.dbeaver.entraid.oauth;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Returns an access token for Azure Database for PostgreSQL: from memory, by refresh token, or by
 * an interactive sign-in, in that order.
 */
public final class EntraTokenProvider {

    /** Don't hand out tokens that may expire while the connection is being opened. */
    private static final Duration MIN_REMAINING = Duration.ofMinutes(5);

    private final RefreshTokenStore refreshTokens;
    private final Map<String, TokenResult> accessTokens = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public EntraTokenProvider(RefreshTokenStore refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    public String getAccessToken(TokenRequest request, SignInUi ui)
        throws IOException, InterruptedException, OAuthException {
        String key = request.cacheKey();
        // DBeaver may open several connections at once; only one of them should prompt
        synchronized (locks.computeIfAbsent(key, k -> new Object())) {
            TokenResult cached = accessTokens.get(key);
            if (cached != null && cached.isValidFor(MIN_REMAINING)) {
                return cached.accessToken();
            }

            OAuthClient client = new OAuthClient(request.tenant(), request.clientId(), TokenRequest.POSTGRES_SCOPE);
            TokenResult result = null;

            String refreshToken = refreshTokens.load(key);
            if (refreshToken != null) {
                ui.status("Refreshing Microsoft Entra ID token...");
                try {
                    result = client.refresh(refreshToken);
                } catch (OAuthException e) {
                    // Expired or revoked (invalid_grant), or MFA/consent now required: sign in again
                    refreshTokens.delete(key);
                }
            }

            if (result == null) {
                result = switch (request.flow()) {
                    case BROWSER -> BrowserFlow.signIn(client, request.username(), ui);
                    case DEVICE_CODE -> DeviceCodeFlow.signIn(client, ui);
                };
            }

            accessTokens.put(key, result);
            if (result.refreshToken() != null) {
                refreshTokens.save(key, result.refreshToken());
            }
            return result.accessToken();
        }
    }

    /** Forgets cached and stored tokens, so the next connect prompts for sign-in. */
    public void signOut(TokenRequest request) {
        String key = request.cacheKey();
        accessTokens.remove(key);
        refreshTokens.delete(key);
    }
}
