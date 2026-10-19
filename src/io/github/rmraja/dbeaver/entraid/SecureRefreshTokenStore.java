package io.github.rmraja.dbeaver.entraid;

import io.github.rmraja.dbeaver.entraid.oauth.RefreshTokenStore;
import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.provider.IProviderHints;
import org.jkiss.dbeaver.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * Keeps refresh tokens in Eclipse secure storage (Windows DPAPI on Windows), so sign-in survives restarts.
 * Falls back to memory if secure storage is unavailable.
 */
final class SecureRefreshTokenStore implements RefreshTokenStore {

    private static final Log log = Log.getLog(SecureRefreshTokenStore.class);
    private static final String ROOT_NODE = "io.github.rmraja.dbeaver.entraid";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";

    @Override
    public String load(String key) {
        try {
            ISecurePreferences node = node(key);
            String token = node == null ? null : node.get(KEY_REFRESH_TOKEN, null);
            return token != null ? token : IN_MEMORY.load(key);
        } catch (Exception e) {
            log.warn("Cannot read Entra ID refresh token from secure storage", e);
            return IN_MEMORY.load(key);
        }
    }

    @Override
    public void save(String key, String refreshToken) {
        try {
            ISecurePreferences node = node(key);
            if (node != null) {
                node.put(KEY_REFRESH_TOKEN, refreshToken, true);
                node.flush();
                return;
            }
        } catch (Exception e) {
            log.warn("Cannot save Entra ID refresh token to secure storage; it will be kept in memory only", e);
        }
        IN_MEMORY.save(key, refreshToken);
    }

    @Override
    public void delete(String key) {
        IN_MEMORY.delete(key);
        try {
            ISecurePreferences node = node(key);
            if (node != null) {
                ISecurePreferences root = node.parent();
                node.removeNode();
                root.flush();
            }
        } catch (Exception e) {
            log.warn("Cannot remove Entra ID refresh token from secure storage", e);
        }
    }

    private static ISecurePreferences node(String key) throws Exception {
        // Never pop up a master password dialog from a background connect job
        ISecurePreferences root = SecurePreferencesFactory.open(null, Map.of(IProviderHints.PROMPT_USER, Boolean.FALSE));
        if (root == null) {
            return null;
        }
        // Node names cannot contain '/', so use a hash of tenant|client|user
        return root.node(ROOT_NODE).node(sha256(key));
    }

    private static String sha256(String s) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }
}
