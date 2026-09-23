package com.hamza.account.features.currency.online;

import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * {@link HttpText} over the JDK's own client - the first thing in this program that talks to the
 * internet, so it is deliberately narrow (docs/currency-plan.md §12):
 * <ul>
 *   <li><b>HTTPS only.</b> A plain-HTTP address is refused before anything is sent.</li>
 *   <li><b>Short timeouts</b>: five seconds to connect, ten for the answer. A shop with no connection is
 *       told so in seconds rather than left looking at a spinner.</li>
 *   <li><b>A bounded answer</b>: a body longer than {@link #MAX_BODY_BYTES} is refused unread past the
 *       limit. Every source's whole list of currencies is a few kilobytes.</li>
 *   <li><b>The system's proxy settings</b>, through the default {@link ProxySelector}.</li>
 * </ul>
 * The client is built on the first request, not with the service: building one starts a thread, and a
 * shop that never presses the button should not pay for it.
 */
public final class JdkHttpText implements HttpText {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    static final int MAX_BODY_BYTES = 1024 * 1024;

    private final String userAgent;
    private volatile HttpClient client;

    /** @param userAgent names the program to the source, as its terms ask of a client */
    public JdkHttpText(String userAgent) {
        this.userAgent = userAgent == null || userAgent.isBlank() ? "AccountK" : userAgent;
    }

    @Override
    public Response get(URI uri) throws RateFetchException, InterruptedException {
        if (uri == null || !"https".equals(uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only HTTPS addresses are fetched: " + uri);
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = client().send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                byte[] bytes = body.readNBytes(MAX_BODY_BYTES + 1);
                if (bytes.length > MAX_BODY_BYTES) {
                    throw new RateFetchException(RateFetchException.Kind.SOURCE,
                            "The answer from " + uri.getHost() + " is longer than " + MAX_BODY_BYTES + " bytes");
                }
                return new Response(response.statusCode(), new String(bytes, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // A refused connection, a name that does not resolve, a timeout, a TLS handshake a captive
            // portal broke: in every case nothing that could be read as rates came back.
            throw new RateFetchException(RateFetchException.Kind.OFFLINE,
                    "No answer from " + uri.getHost() + ": " + e.getClass().getSimpleName(), e);
        }
    }

    private HttpClient client() {
        HttpClient built = client;
        if (built == null) {
            synchronized (this) {
                built = client;
                if (built == null) {
                    built = HttpClient.newBuilder()
                            .connectTimeout(CONNECT_TIMEOUT)
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .proxy(ProxySelector.getDefault())
                            .build();
                    client = built;
                }
            }
        }
        return built;
    }
}
