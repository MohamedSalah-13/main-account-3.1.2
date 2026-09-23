package com.hamza.account.features.currency.online;

import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.CurrencyConverter;
import com.hamza.account.features.currency.RateInForce;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One currency in the internet's answer, beside what the shop already has for it
 * (docs/currency-plan.md ق-٩, §12).
 *
 * @param inForce the rate in force on the day, or {@code null} for a currency with none yet
 * @param fetched the source's rate turned into this system's direction, or {@code null} when it gave none
 */
public record OnlineRateLine(Currency currency, RateInForce inForce, BigDecimal fetched, Status status) {

    /**
     * A move larger than this, in percent either way, is offered unticked: it may be a devaluation that
     * happened overnight, or a source that has gone wrong, and the person at the screen is the one who
     * knows which.
     */
    public static final BigDecimal LARGE_MOVE_PERCENT = BigDecimal.TEN;

    public enum Status {
        /** Quoted, and close to the rate in force - or the currency's first rate. Offered and ticked. */
        READY,
        /** Quoted, but more than {@link #LARGE_MOVE_PERCENT} from the rate in force. Offered, not ticked. */
        LARGE_MOVE,
        /** The day already has a rate. It stays, whatever the source says: the fetch fills empty days only. */
        RECORDED_TODAY,
        /** The source does not quote this currency, or quoted nothing a rate could be made of. */
        NOT_OFFERED
    }

    public OnlineRateLine {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(status, "status");
    }

    /** May be recorded - the only two statuses a tick box is offered for. */
    public boolean recordable() {
        return status == Status.READY || status == Status.LARGE_MOVE;
    }

    /**
     * How far the fetched rate is from the one in force, in percent to two places - {@code null} with
     * nothing to compare: a change against nothing is not a number.
     */
    public BigDecimal changePercent() {
        return fetched == null || inForce == null ? null : CurrencyConverter.changePercent(fetched, inForce.rate());
    }
}
