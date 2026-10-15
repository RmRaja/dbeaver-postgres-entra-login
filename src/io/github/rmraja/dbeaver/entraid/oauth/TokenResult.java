package io.github.rmraja.dbeaver.entraid.oauth;

import java.time.Duration;
import java.time.Instant;

/**
 * Access token (plus optional refresh token) returned by the token endpoint.
 */
public record TokenResult(String accessToken, String refreshToken, Instant expiresAt) {

    /** True if the access token is still valid for at least {@code margin}. */
    public boolean isValidFor(Duration margin) {
        return accessToken != null && Instant.now().plus(margin).isBefore(expiresAt);
    }
}
