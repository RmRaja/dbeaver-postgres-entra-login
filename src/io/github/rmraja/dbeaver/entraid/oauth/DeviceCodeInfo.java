package io.github.rmraja.dbeaver.entraid.oauth;

/**
 * Response of the device authorization endpoint.
 *
 * @param message human-readable instructions from Entra ID (already localized)
 */
public record DeviceCodeInfo(
    String deviceCode,
    String userCode,
    String verificationUri,
    String message,
    int expiresInSeconds,
    int intervalSeconds
) {
}
