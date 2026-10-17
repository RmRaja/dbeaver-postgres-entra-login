package io.github.rmraja.dbeaver.entraid.oauth;

/**
 * Persistent storage for refresh tokens, keyed by {@link TokenRequest#cacheKey()}.
 */
public interface RefreshTokenStore {

    String load(String key);

    void save(String key, String refreshToken);

    void delete(String key);

    /** Keeps refresh tokens for the lifetime of the process only. */
    RefreshTokenStore IN_MEMORY = new RefreshTokenStore() {
        private final java.util.Map<String, String> tokens = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public String load(String key) {
            return tokens.get(key);
        }

        @Override
        public void save(String key, String refreshToken) {
            tokens.put(key, refreshToken);
        }

        @Override
        public void delete(String key) {
            tokens.remove(key);
        }
    };
}
