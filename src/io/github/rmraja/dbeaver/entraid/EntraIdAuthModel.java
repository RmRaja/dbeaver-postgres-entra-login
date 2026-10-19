package io.github.rmraja.dbeaver.entraid;

import io.github.rmraja.dbeaver.entraid.oauth.EntraTokenProvider;
import io.github.rmraja.dbeaver.entraid.oauth.OAuthException;
import io.github.rmraja.dbeaver.entraid.oauth.TokenRequest;
import io.github.rmraja.dbeaver.entraid.ui.DbeaverSignInUi;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBPDataSource;
import org.jkiss.dbeaver.model.DBPDataSourceContainer;
import org.jkiss.dbeaver.model.connection.DBPConnectionConfiguration;
import org.jkiss.dbeaver.model.impl.auth.AuthModelDatabaseNative;
import org.jkiss.dbeaver.model.impl.auth.AuthModelDatabaseNativeCredentials;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.utils.CommonUtils;

import java.io.IOException;
import java.util.Properties;

/**
 * PostgreSQL authentication with a Microsoft Entra ID access token as the password.
 */
public class EntraIdAuthModel extends AuthModelDatabaseNative<AuthModelDatabaseNativeCredentials> {

    public static final String ID = "postgres_entra_id";

    private static final Log log = Log.getLog(EntraIdAuthModel.class);
    private static final EntraTokenProvider TOKEN_PROVIDER = new EntraTokenProvider(new SecureRefreshTokenStore());

    public static EntraTokenProvider getTokenProvider() {
        return TOKEN_PROVIDER;
    }

    @Override
    public boolean isUserPasswordApplicable() {
        return false;
    }

    @Override
    public boolean isCredentialsPromptRequired() {
        return false;
    }

    @Override
    public Object initAuthentication(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DBPDataSource dataSource,
        @NotNull AuthModelDatabaseNativeCredentials credentials,
        @NotNull DBPConnectionConfiguration configuration,
        @NotNull Properties connProperties
    ) throws DBException {
        String userName = credentials.getUserName();
        if (CommonUtils.isEmpty(userName)) {
            userName = configuration.getUserName();
        }
        if (CommonUtils.isEmpty(userName)) {
            throw new DBException("Enter your Microsoft Entra ID user name (e.g. user@contoso.com) or the "
                + "Entra group name that is mapped to a PostgreSQL role");
        }

        TokenRequest request = EntraIdSettings.toTokenRequest(configuration, userName);
        String accessToken;
        try {
            accessToken = TOKEN_PROVIDER.getAccessToken(request, new DbeaverSignInUi(monitor));
        } catch (OAuthException e) {
            throw new DBException("Microsoft Entra ID sign-in failed: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new DBException("Cannot reach Microsoft Entra ID: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DBException("Microsoft Entra ID sign-in was interrupted", e);
        }

        connProperties.put("user", userName);
        connProperties.put("password", accessToken);
        // Azure Database for PostgreSQL only accepts Entra ID tokens over TLS
        connProperties.putIfAbsent("sslmode", "require");
        log.debug("Connecting to " + dataSource.getContainer().getName() + " as " + userName + " with an Entra ID token");
        return credentials;
    }

    @Override
    protected boolean isUserPasswordNeeded(DBPDataSourceContainer dataSource) {
        return false;
    }
}
