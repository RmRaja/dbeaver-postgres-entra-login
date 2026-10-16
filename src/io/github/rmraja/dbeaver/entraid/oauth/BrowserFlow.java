package io.github.rmraja.dbeaver.entraid.oauth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Authorization code flow with PKCE. The redirect is received on a one-shot loopback HTTP listener.
 */
final class BrowserFlow {

    private static final Duration SIGN_IN_TIMEOUT = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();

    private BrowserFlow() {
    }

    static TokenResult signIn(OAuthClient client, String loginHint, SignInUi ui)
        throws IOException, InterruptedException, OAuthException {
        String verifier = randomUrlSafe(32);
        String state = randomUrlSafe(16);

        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            server.setSoTimeout(500);
            // Entra ID accepts any port for http://localhost on public clients
            String redirectUri = "http://localhost:" + server.getLocalPort();

            ui.status("Waiting for sign-in in your web browser...");
            ui.openBrowser(client.authorizeUrl(redirectUri, state, challengeFor(verifier), loginHint));

            Map<String, String> params = awaitRedirect(server, state, ui);
            if (params.get("error") != null) {
                throw new OAuthException(params.get("error"), params.get("error_description"));
            }
            ui.status("Requesting access token...");
            return client.exchangeCode(params.get("code"), redirectUri, verifier);
        }
    }

    private static Map<String, String> awaitRedirect(ServerSocket server, String state, SignInUi ui)
        throws IOException, OAuthException {
        Instant deadline = Instant.now().plus(SIGN_IN_TIMEOUT);
        while (true) {
            if (ui.isCancelled()) {
                throw new OAuthException("cancelled", "Sign-in was cancelled");
            }
            if (Instant.now().isAfter(deadline)) {
                throw new OAuthException("timeout", "Timed out waiting for browser sign-in");
            }
            Socket socket;
            try {
                socket = server.accept();
            } catch (SocketTimeoutException e) {
                continue;
            }
            try (socket) {
                socket.setSoTimeout(5000);
                Map<String, String> params = readQuery(socket);
                // Ignore unrelated requests (favicon, probes) and responses for another attempt
                if (!state.equals(params.get("state")) || (params.get("code") == null && params.get("error") == null)) {
                    respond(socket, 404, "Not found");
                    continue;
                }
                if (params.get("error") != null) {
                    respond(socket, 200, page("Sign-in failed",
                        "DBeaver did not get access: " + escape(params.getOrDefault("error_description", params.get("error")))));
                } else {
                    respond(socket, 200, page("Signed in",
                        "You are signed in. You can close this tab and return to DBeaver."));
                }
                return params;
            }
        }
    }

    private static Map<String, String> readQuery(Socket socket) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        String requestLine = in.readLine();
        Map<String, String> params = new HashMap<>();
        if (requestLine == null) {
            return params;
        }
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            return params;
        }
        String query = URI.create(parts[1]).getRawQuery();
        if (query == null) {
            return params;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                params.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    private static void respond(Socket socket, int status, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + status + (status == 200 ? " OK" : " Not Found") + "\r\n"
            + "Content-Type: text/html; charset=utf-8\r\n"
            + "Content-Length: " + body.length + "\r\n"
            + "Connection: close\r\n\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static String page(String title, String text) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>" + title + "</title></head>"
            + "<body style=\"font-family:sans-serif;margin:3em\"><h2>" + title + "</h2><p>" + text + "</p></body></html>";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String randomUrlSafe(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String challengeFor(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
