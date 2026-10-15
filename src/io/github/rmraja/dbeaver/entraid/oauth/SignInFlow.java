package io.github.rmraja.dbeaver.entraid.oauth;

/**
 * How the user signs in when no cached or refreshable token is available.
 */
public enum SignInFlow {
    BROWSER("Interactive browser"),
    DEVICE_CODE("Device code");

    private final String label;

    SignInFlow(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Parses a stored value, defaulting to {@link #BROWSER}. */
    public static SignInFlow fromString(String value) {
        if (value != null) {
            for (SignInFlow flow : values()) {
                if (flow.name().equalsIgnoreCase(value)) {
                    return flow;
                }
            }
        }
        return BROWSER;
    }
}
