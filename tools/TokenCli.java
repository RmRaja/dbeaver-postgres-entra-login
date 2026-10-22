import io.github.rmraja.dbeaver.entraid.oauth.DeviceCodeInfo;
import io.github.rmraja.dbeaver.entraid.oauth.EntraTokenProvider;
import io.github.rmraja.dbeaver.entraid.oauth.RefreshTokenStore;
import io.github.rmraja.dbeaver.entraid.oauth.SignInFlow;
import io.github.rmraja.dbeaver.entraid.oauth.SignInUi;
import io.github.rmraja.dbeaver.entraid.oauth.TokenRequest;

import java.awt.Desktop;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Gets an Azure Database for PostgreSQL token outside DBeaver, to test the sign-in flows.
 *
 * Usage: java -cp dist\plugin.jar;build\tools TokenCli [-flow browser|device] [-tenant T] [-client C] [-user U]
 */
public class TokenCli {

    public static void main(String[] args) throws Exception {
        String flow = "browser", tenant = null, client = null, user = "";
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "-flow" -> flow = args[i + 1];
                case "-tenant" -> tenant = args[i + 1];
                case "-client" -> client = args[i + 1];
                case "-user" -> user = args[i + 1];
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        TokenRequest request = new TokenRequest(
            flow.startsWith("device") ? SignInFlow.DEVICE_CODE : SignInFlow.BROWSER, tenant, client, user);

        SignInUi ui = new SignInUi() {
            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public void status(String message) {
                System.err.println(message);
            }

            @Override
            public void openBrowser(String url) {
                System.err.println("Sign in at: " + url);
                try {
                    if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url));
                } catch (Exception e) {
                    System.err.println("Could not open browser: " + e.getMessage());
                }
            }

            @Override
            public void showDeviceCode(DeviceCodeInfo info) {
                System.err.println(info.message());
            }

            @Override
            public void deviceCodeFinished() {
            }
        };

        String token = new EntraTokenProvider(RefreshTokenStore.IN_MEMORY).getAccessToken(request, ui);
        String[] parts = token.split("\\.");
        if (parts.length > 1) {
            String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            System.err.println("Token claims: " + claims);
        }
        System.out.println(token);
    }
}
