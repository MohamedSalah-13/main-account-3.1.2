package com.hamza.account.features.currency.online;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The real client, as far as a build without the internet can take it: what it refuses to send, and
 * that nobody answering is "no connection" rather than an error with a reference code. A whole answer
 * read through it is not tested here - docs/currency-plan.md §12.5 says how that was checked.
 */
class JdkHttpTextTest {

    private final JdkHttpText http = new JdkHttpText("AccountK-test");

    @Test
    @DisplayName("an address that is not HTTPS is refused before anything is sent")
    void httpsOnly() {
        assertThrows(IllegalArgumentException.class, () -> http.get(URI.create("http://open.er-api.com/v6/latest/EGP")));
        assertThrows(IllegalArgumentException.class, () -> http.get(URI.create("ftp://example.com/x")));
        assertThrows(IllegalArgumentException.class, () -> http.get(null));
    }

    @Test
    @DisplayName("a port nobody listens on is no connection")
    void nobodyAnswers() throws Exception {
        int closed;
        try (ServerSocket socket = new ServerSocket(0)) {
            closed = socket.getLocalPort();
        }
        RateFetchException failure = assertThrows(RateFetchException.class,
                () -> http.get(URI.create("https://127.0.0.1:" + closed + "/v6/latest/EGP")));
        assertEquals(RateFetchException.Kind.OFFLINE, failure.kind());
    }
}
