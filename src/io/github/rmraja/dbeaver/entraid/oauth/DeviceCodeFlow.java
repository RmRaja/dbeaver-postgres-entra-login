package io.github.rmraja.dbeaver.entraid.oauth;

import java.io.IOException;
import java.time.Instant;

/**
 * Device authorization grant: the user enters a code at microsoft.com/devicelogin.
 */
final class DeviceCodeFlow {

    private DeviceCodeFlow() {
    }

    static TokenResult signIn(OAuthClient client, SignInUi ui) throws IOException, InterruptedException, OAuthException {
        DeviceCodeInfo info = client.requestDeviceCode();
        Instant deadline = Instant.now().plusSeconds(info.expiresInSeconds());
        long intervalMs = Math.max(1, info.intervalSeconds()) * 1000L;

        ui.status("Waiting for device code sign-in (code " + info.userCode() + ")...");
        ui.showDeviceCode(info);
        try {
            while (true) {
                // Sleep in short steps so Cancel takes effect quickly
                long wakeAt = System.currentTimeMillis() + intervalMs;
                while (System.currentTimeMillis() < wakeAt) {
                    if (ui.isCancelled()) {
                        throw new OAuthException("cancelled", "Sign-in was cancelled");
                    }
                    Thread.sleep(200);
                }
                if (Instant.now().isAfter(deadline)) {
                    throw new OAuthException("expired_token", "The device code expired before sign-in completed");
                }
                try {
                    return client.pollDeviceCode(info.deviceCode());
                } catch (OAuthException e) {
                    switch (e.getError()) {
                        case "authorization_pending":
                            break;
                        case "slow_down":
                            intervalMs += 5000;
                            break;
                        default:
                            throw e;
                    }
                }
            }
        } finally {
            ui.deviceCodeFinished();
        }
    }
}
