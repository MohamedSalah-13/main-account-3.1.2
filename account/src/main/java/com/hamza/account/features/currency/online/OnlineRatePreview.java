package com.hamza.account.features.currency.online;

import com.hamza.account.features.currency.Currency;
import com.hamza.account.features.currency.CurrencyConverter;
import com.hamza.account.features.currency.ExchangeRateDraft;
import com.hamza.account.features.currency.RateInForce;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What the button found: each active currency with the rate the source gives it today, beside the rate
 * the shop already has (docs/currency-plan.md ق-٩, §12). It writes nothing - the internet suggests, and
 * a rate is recorded only when somebody ticks it and presses record.
 * <p>
 * The rules, each decided once here:
 * <ul>
 *   <li>A rate is dated <b>the day it was fetched</b> on this machine - "today's rate", which is what the
 *       owner asked for - and copied into the rate's notes with the source and the day it published.</li>
 *   <li><b>A day that already has a rate keeps it</b>, typed by hand or fetched earlier: the fetch fills
 *       empty days only, and nothing here offers to replace a recorded rate.</li>
 *   <li>A currency is <b>ticked for you</b> when its rate moved by {@link OnlineRateLine#LARGE_MOVE_PERCENT}
 *       or less, or has none yet - and nothing is ticked when the source's figures are more than
 *       {@link #STALE_AFTER_DAYS} days old, since recording them as today's would be a claim they do not
 *       support.</li>
 * </ul>
 *
 * @param published the day the source says its figures are from
 * @param day       the day a recorded rate is dated - the day of the fetch
 */
public record OnlineRatePreview(String source, String site, LocalDate published, LocalDate day,
                                List<OnlineRateLine> lines) {

    /** Figures older than this, in days, are shown and offered but not ticked. */
    public static final int STALE_AFTER_DAYS = 3;

    public OnlineRatePreview {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(published, "published");
        Objects.requireNonNull(day, "day");
        lines = List.copyOf(lines);
    }

    /**
     * The preview for {@code currencies} - every active one but the base, in the order given - against
     * the rates in force on {@code day}.
     */
    public static OnlineRatePreview of(QuotedRates quote, List<Currency> currencies,
                                       Map<Integer, RateInForce> inForce, LocalDate day) {
        List<OnlineRateLine> lines = new ArrayList<>();
        for (Currency currency : currencies) {
            if (currency.base() || !currency.active()) {
                continue;
            }
            RateInForce current = inForce.get(currency.id());
            BigDecimal fetched = OnlineRateMath.rateFrom(quote.quoteFor(currency.code()));
            lines.add(new OnlineRateLine(currency, current, fetched, statusOf(current, fetched, day)));
        }
        return new OnlineRatePreview(quote.source(), quote.site(), quote.published(), day, lines);
    }

    private static OnlineRateLine.Status statusOf(RateInForce current, BigDecimal fetched, LocalDate day) {
        if (current != null && current.effectiveDate().equals(day)) {
            return OnlineRateLine.Status.RECORDED_TODAY;
        }
        if (fetched == null) {
            return OnlineRateLine.Status.NOT_OFFERED;
        }
        if (current == null) {
            return OnlineRateLine.Status.READY;
        }
        BigDecimal change = CurrencyConverter.changePercent(fetched, current.rate());
        return change != null && change.abs().compareTo(OnlineRateLine.LARGE_MOVE_PERCENT) > 0
                ? OnlineRateLine.Status.LARGE_MOVE : OnlineRateLine.Status.READY;
    }

    /** How many days before {@link #day} the source's figures are from; never below zero. */
    public long publishedDaysAgo() {
        return Math.max(0, day.toEpochDay() - published.toEpochDay());
    }

    public boolean stale() {
        return publishedDaysAgo() > STALE_AFTER_DAYS;
    }

    /** The currencies ticked when the dialog opens - see the class comment. */
    public Set<Integer> preselected() {
        Set<Integer> ids = new LinkedHashSet<>();
        if (stale()) {
            return ids;
        }
        for (OnlineRateLine line : lines) {
            if (line.status() == OnlineRateLine.Status.READY) {
                ids.add(line.currency().id());
            }
        }
        return ids;
    }

    /**
     * The rates to record for the ticked currencies: only lines that may be recorded, each dated
     * {@link #day} and noted with where it came from. A tick on any other line is ignored rather than
     * obeyed - the dialog cannot tick one, and this is what makes sure.
     */
    public List<ExchangeRateDraft> drafts(Collection<Integer> chosen) {
        String notes = source + " " + published;
        List<ExchangeRateDraft> drafts = new ArrayList<>();
        for (OnlineRateLine line : lines) {
            if (line.recordable() && chosen.contains(line.currency().id())) {
                drafts.add(new ExchangeRateDraft(0, line.currency().id(), day, line.fetched(), notes));
            }
        }
        return drafts;
    }
}
