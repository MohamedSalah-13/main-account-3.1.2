package com.hamza.account.features.currency.online;

/**
 * Somewhere on the internet that publishes exchange rates (docs/currency-plan.md §12).
 * <p>
 * A source is asked about the base currency alone, so one request answers for every currency the shop
 * deals in, and the only thing that leaves the machine is the base's ISO code.
 */
public interface RateSource {

    /**
     * What one unit of {@code baseCode} buys of every currency the source quotes.
     *
     * @throws RateFetchException   the source gave no rates, and why
     * @throws InterruptedException the screen gave up waiting
     */
    QuotedRates fetch(String baseCode) throws RateFetchException, InterruptedException;
}
