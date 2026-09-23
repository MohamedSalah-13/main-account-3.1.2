package com.hamza.account.features.currency.online;

import java.net.URI;

/**
 * One HTTPS GET, answered as text - the seam that keeps the sources testable without a network.
 * {@link JdkHttpText} is the real one.
 */
public interface HttpText {

    /** The status and body of one answer. A status other than 200 is not a failure here: a source reads it. */
    record Response(int status, String body) {
    }

    /**
     * @throws RateFetchException {@link RateFetchException.Kind#OFFLINE} when nothing answered at all
     */
    Response get(URI uri) throws RateFetchException, InterruptedException;
}
