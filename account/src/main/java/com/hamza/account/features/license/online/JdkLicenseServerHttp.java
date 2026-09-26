package com.hamza.account.features.license.online;

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
 * {@link LicenseServerHttp} over the JDK's own client, held to the same narrow rules as the currencies'
 * {@code JdkHttpText} ({@code server-plan.md} §5):
 * <ul>
 *   <li><b>HTTPS only.</b> A plain-HTTP address is refused before anything is sent.</li>
 *   <li><b>Five seconds to connect, ten for the answer</b>, so a shop with no connection is told so in
 *       seconds rather than left looking at a spinner.</li>
 *   <li><b>A bounded answer</b>: a licence file is under a kilobyte, and a body longer than
 *       {@link #MAX_BODY_BYTES} is refused unread past the limit.</li>
 *   <li><b>No redirect is followed</b>: a request that carries a purchase code or a licence goes to the
 *       address written here and nowhere else.</li>
 *   <li><b>The system's proxy settings</b>, through the default {@link ProxySelector}.</li>
 * </ul>
 * The client is built on the first request: building one starts a thread, and a machine that never
 * activates online should not pay for it.
 */
public final class JdkLicenseServerHttp implements LicenseServerHttp {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    static final int MAX_BODY_BYTES = 64 * 1024;

    private final String userAgent;
    private volatile HttpClient client;

    public JdkLicenseServerHttp(String userAgent) {
        this.userAgent = userAgent == null || userAgent.isBlank() ? "AccountK" : userAgent;
    }

    @Override
    public Response post(URI uri, String json) throws IOException, InterruptedException {
        if (uri == null || !"https".equals(uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only HTTPS addresses are asked: " + uri);
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<InputStream> response = client().send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            byte[] bytes = body.readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new IOException("The answer from " + uri.getHost() + " is longer than " + MAX_BODY_BYTES + " bytes");
            }
            return new Response(response.statusCode(), new String(bytes, StandardCharsets.UTF_8));
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
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .proxy(ProxySelector.getDefault())
                            .build();
                    client = built;
                }
            }
        }
        return built;
    }
}
