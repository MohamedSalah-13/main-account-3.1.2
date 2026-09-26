package com.hamza.account.features.license.online;

import java.io.IOException;
import java.net.URI;

/**
 * One JSON request to the licence server and its answer, as text. An interface so {@link LicenseServer}
 * is tested without a network: {@link JdkLicenseServerHttp} is the one real implementation.
 */
public interface LicenseServerHttp {

    /** The status and the body, which is empty for a 204 or a gateway that sent nothing. */
    record Response(int status, String body) {
    }

    /**
     * Sends {@code json} and returns the answer.
     *
     * @throws IOException no answer came back - no connection, no name, a timeout, a broken handshake
     */
    Response post(URI uri, String json) throws IOException, InterruptedException;
}
