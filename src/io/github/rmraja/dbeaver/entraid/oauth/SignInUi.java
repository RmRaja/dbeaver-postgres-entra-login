package io.github.rmraja.dbeaver.entraid.oauth;

/**
 * Callbacks a sign-in flow uses to talk to the user. Implemented by DBeaver UI and by the CLI test tool.
 */
public interface SignInUi {

    /** Polled while waiting; returning true aborts the sign-in. */
    boolean isCancelled();

    /** Progress text, e.g. "Waiting for sign-in in browser". */
    void status(String message);

    void openBrowser(String url);

    void showDeviceCode(DeviceCodeInfo info);

    /** Called when a device code sign-in ends (success, failure or cancel). */
    void deviceCodeFinished();
}
