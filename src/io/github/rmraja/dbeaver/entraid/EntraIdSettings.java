package io.github.rmraja.dbeaver.entraid;

import io.github.rmraja.dbeaver.entraid.oauth.SignInFlow;
import io.github.rmraja.dbeaver.entraid.oauth.TokenRequest;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;

/**
 * Auth properties stored on the connection. The Postgres user name is the connection's own user name.
 */
public final class EntraIdSettings {

    public static final String PROP_FLOW = "entraid.flow";
    public static final String PROP_TENANT = "entraid.tenant";
    public static final String PROP_CLIENT_ID = "entraid.clientId";

    private EntraIdSettings() {
    }

    public static TokenRequest toTokenRequest(DBPConnectionConfiguration configuration, String userName) {
        return new TokenRequest(
            SignInFlow.fromString(configuration.getAuthProperty(PROP_FLOW)),
            configuration.getAuthProperty(PROP_TENANT),
            configuration.getAuthProperty(PROP_CLIENT_ID),
            userName);
    }
}
