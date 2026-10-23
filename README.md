# dbeaver-postgres-entra-login

A DBeaver Community plugin that lets you sign in to **Azure Database for PostgreSQL** with
**Microsoft Entra ID** (formerly Azure Active Directory).

DBeaver Community has no built-in Entra ID login for PostgreSQL. Without it you have to run
`az account get-access-token`, copy the token, and paste it into the password field every hour.
This plugin adds an authentication method, **Microsoft Entra ID (Azure)**, that does this for you
each time you connect.

## Features

- Two sign-in methods:
  - **Interactive browser**: opens the Microsoft sign-in page in your browser (authorization code flow with PKCE).
  - **Device code**: shows a code to enter at `microsoft.com/devicelogin`. Useful when the browser flow is blocked.
- Works with MFA and Conditional Access, because you sign in on Microsoft's own page.
- Signs you in once. Later connections and reconnects get a fresh token silently, including after a DBeaver restart.
- No Azure CLI and no third-party libraries. It uses only Java's built-in HTTP client and DBeaver's own APIs.
- Supports user accounts and Entra groups mapped to PostgreSQL roles.

## Requirements

- DBeaver Community 26.x on Windows. Developed and tested against 26.2.3.
- JDK 21 or newer, only if you build from source.
- An Azure Database for PostgreSQL Flexible Server with **Microsoft Entra authentication** enabled.
- Your Entra user, or a group you belong to, set up as a PostgreSQL role. Either it is the server's
  Entra admin, or an admin has created it:

  ```sql
  select * from pgaadauth_create_principal('user@contoso.com', false, false);
  ```

## Installation

1. Build the plugin:

   ```powershell
   .\build.ps1
   ```

   This produces `dist\io.github.rmraja.dbeaver.entraid_1.0.0.jar`.

2. Close DBeaver, then install:

   ```powershell
   .\install.ps1
   ```

   This copies the jar into DBeaver's `plugins` folder and registers it in
   `configuration\org.eclipse.equinox.simpleconfigurator\bundles.info`.
   It saves a backup as `bundles.info.bak` the first time.

3. Start DBeaver once with `-clean` so it rebuilds its plugin cache:

   ```powershell
   & "$env:LOCALAPPDATA\DBeaver\dbeaver.exe" -clean
   ```

Both scripts assume DBeaver is installed per user in `%LOCALAPPDATA%\DBeaver`. If yours is elsewhere,
pass the path, for example `.\install.ps1 -DBeaverHome "C:\Program Files\DBeaver"`.

DBeaver does not load plugins from its `dropins` folder by default, which is why the installer
registers the plugin directly. **A DBeaver upgrade resets this registration.** Run `.\install.ps1`
again after upgrading.

To remove the plugin, close DBeaver and run `.\install.ps1 -Uninstall`.

## Usage

1. **Database > New Database Connection > PostgreSQL.**
2. Host `<server>.postgres.database.azure.com`, port `5432`, database `postgres` (or your database).
3. Set **Authentication** to **Microsoft Entra ID (Azure)**.
4. Fill in the fields:

   | Field | What to enter |
   | - | - |
   | Username | Your Entra sign-in name, e.g. `user@contoso.com`. If you connect through a group role, the group name. |
   | Sign-in method | Interactive browser or Device code. |
   | Tenant | Your directory (tenant) ID or domain, e.g. `contoso.onmicrosoft.com`. Leave empty to allow any work or school account. Setting it is recommended. |
   | Client ID | Leave empty to use Microsoft's public Azure CLI app. See [Using your own app registration](#using-your-own-app-registration). |

5. Click **Test Connection**. The first time, you are asked to sign in.

**Clear cached sign-in** on the same page forgets the saved sign-in for that account, so you are
asked to sign in again on the next connection.

SSL is set to `require` unless you configure it yourself on the SSL tab. Azure only accepts Entra ID
tokens over TLS.

## How it works

1. When DBeaver opens a connection, the plugin looks for a valid access token in memory.
2. If there is none, it uses the saved refresh token to get a new access token from Entra ID, without
   any prompt.
3. If that is not possible (first use, expired or revoked sign-in), it runs the sign-in method you chose.
4. The token, scoped to `https://ossrdbms-aad.database.windows.net`, is passed to PostgreSQL as the
   password. Your username is passed as the user.

Access tokens last about 60 to 90 minutes. Because every new connection or reconnect goes through
these steps, expiry is handled for you.

## Security and privacy

The plugin talks to exactly two places:

| Destination | What is sent |
| - | - |
| `https://login.microsoftonline.com` | Sign-in requests, and the refresh token when renewing. |
| Your PostgreSQL server | Your username and the access token, over TLS. |

During browser sign-in it also opens a temporary listener on `127.0.0.1` (your own machine only) to
receive the response from your browser. It closes as soon as sign-in completes.

- The plugin never sees your Microsoft password. You type it into Microsoft's sign-in page.
- The refresh token is stored in DBeaver's secure storage, which on Windows is encrypted with your
  Windows account (DPAPI). Access tokens are kept in memory only.
- Nothing secret is written to DBeaver's connection settings. Only the username, tenant, client ID
  and sign-in method are saved there.
- Tokens are never written to DBeaver's log.
- There is no telemetry, analytics or other network traffic.

The code is small (about 1,000 lines in [`src/`](src/)) and has no dependencies, so it is easy to review.

## Using your own app registration

By default the plugin signs in as the public Azure CLI app (`04b07795-8ddb-461a-bbee-02f9e1bf7b46`),
which Microsoft owns and which most tenants already allow. If your organization requires its own app:

1. In Entra ID, create an app registration.
2. Under **Authentication**, add a **Mobile and desktop applications** platform with the redirect URI
   `http://localhost`, and enable **Allow public client flows** (needed for device code).
3. Under **API permissions**, add the delegated permission for **Azure OSSRDBMS Database** and grant consent.
4. Enter the app's Application (client) ID in the **Client ID** field.

## Testing sign-in without DBeaver

The build also produces a small command-line tool that runs the same sign-in code:

```powershell
java -cp "dist\io.github.rmraja.dbeaver.entraid_1.0.0.jar;build\tools" TokenCli -flow browser -tenant <tenant> -user <upn>
java -cp "dist\io.github.rmraja.dbeaver.entraid_1.0.0.jar;build\tools" TokenCli -flow device -tenant <tenant>
```

It prints the token on standard output and its decoded claims on standard error. The `aud` claim
should be `https://ossrdbms-aad.database.windows.net`. You can use the token with `psql` by setting
`$env:PGPASSWORD`.

The printed token is a real credential. Do not share it or paste it anywhere.

## Troubleshooting

| Problem | What to check |
| - | - |
| The authentication method is not in the list | Open **Help > About DBeaver > Installation Details > Plug-ins** and look for `io.github.rmraja.dbeaver.entraid`. If it is missing, close DBeaver, run `.\install.ps1` again, and start DBeaver with `-clean`. Errors are logged in `%APPDATA%\DBeaverData\workspace6\.metadata\.log`. |
| `password authentication failed` | The PostgreSQL role name does not match your user or group name, or the role was not created with `pgaadauth_create_principal`. |
| `AADSTS65001` (consent required) | Your custom client ID lacks the **Azure OSSRDBMS Database** permission, or an admin has not granted consent. |
| `AADSTS50076` or `AADSTS50079` (MFA required) | Click **Clear cached sign-in** and connect again to sign in interactively. |
| The browser does not open | The sign-in URL is written to DBeaver's log. Open it manually, or switch to **Device code**. |

## Project layout

```
META-INF/MANIFEST.MF         OSGi bundle definition
plugin.xml                   Registers the authentication method and its settings panel
src/.../entraid/             DBeaver integration (auth model, secure token storage)
src/.../entraid/oauth/       Entra ID sign-in flows, no DBeaver dependencies
src/.../entraid/ui/          Settings panel and device code dialog
tools/TokenCli.java          Command-line test tool
build.ps1, install.ps1       Build and install scripts
```

## License

[MIT](LICENSE)

## Disclaimer

This is an independent project. It is not affiliated with or endorsed by DBeaver Corp or Microsoft.
