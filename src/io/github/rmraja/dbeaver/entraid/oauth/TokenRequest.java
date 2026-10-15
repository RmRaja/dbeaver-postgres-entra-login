package io.github.rmraja.dbeaver.entraid.oauth;

/**
 * What to sign in for: one Entra ID account against one tenant and client.
 *
 * @param username Entra user principal name; used as login hint and part of the cache key
 */
public record TokenRequest(SignInFlow flow, String tenant, String clientId, String username) {

    /** Azure Database for PostgreSQL / MySQL resource scope. */
    public static final String POSTGRES_SCOPE = "https://ossrdbms-aad.database.windows.net/.default";

    /** Microsoft's public Azure CLI client; allows http://localhost redirects. */
    public static final String DEFAULT_CLIENT_ID = "04b07795-8ddb-461a-bbee-02f9e1bf7b46";

    /** Any work or school account; a specific tenant ID or domain is recommended. */
    public static final String DEFAULT_TENANT = "organizations";

    public TokenRequest {
        tenant = tenant == null || tenant.isBlank() ? DEFAULT_TENANT : tenant.trim();
        clientId = clientId == null || clientId.isBlank() ? DEFAULT_CLIENT_ID : clientId.trim();
        username = username == null ? "" : username.trim();
        flow = flow == null ? SignInFlow.BROWSER : flow;
    }

    /** Key for cached tokens; the flow is irrelevant to which tokens are valid. */
    public String cacheKey() {
        return tenant.toLowerCase() + "|" + clientId.toLowerCase() + "|" + username.toLowerCase();
    }
}
