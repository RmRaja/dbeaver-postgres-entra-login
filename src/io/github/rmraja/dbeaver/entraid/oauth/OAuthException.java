package io.github.rmraja.dbeaver.entraid.oauth;

/**
 * Error returned by the Entra ID token endpoint (or a failed sign-in flow).
 */
public class OAuthException extends Exception {

    private final String error;

    public OAuthException(String error, String description) {
        super(description == null || description.isBlank() ? error : description);
        this.error = error;
    }

    /** OAuth error code, e.g. {@code invalid_grant}, {@code authorization_pending}. */
    public String getError() {
        return error;
    }
}
