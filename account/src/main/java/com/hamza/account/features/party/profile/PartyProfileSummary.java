package com.hamza.account.features.party.profile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * The figures above a profile, added up from the days and the items it shows.
 *
 * <p><b>A figure with nothing to divide by is absent, not zero</b> - the rule
 * {@code PartyTrendSummary} set: an average invoice of a party with no invoices is not a number, and
 * neither is the gap between visits of a party seen once.</p>
 *
 * @param net            the documents less the returns, off the headers - what the ledger says
 * @param cash           the documents' cash less what the returns handed back
 * @param headerDiscount the documents' own discounts less the returns' own discounts
 * @param itemsNet       the same documents read off their lines
 * @param lastEver       the party's last document whatever the period, or empty for none
 */
public record PartyProfileSummary(int documents, int returns, BigDecimal documentsNet, BigDecimal returnedNet,
                                  BigDecimal net, BigDecimal cash, BigDecimal headerDiscount,
                                  BigDecimal itemsNet, int activeDays, Optional<LocalDate> firstDay,
                                  Optional<LocalDate> lastDay, Optional<LocalDate> lastEver) {

    public static PartyProfileSummary of(List<PartyProfileDay> days, List<PartyItemRow> items,
                                         Optional<LocalDate> lastEver) {
        int documents = 0;
        int returns = 0;
        BigDecimal documentsNet = BigDecimal.ZERO;
        BigDecimal returnedNet = BigDecimal.ZERO;
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal headerDiscount = BigDecimal.ZERO;
        int activeDays = 0;
        LocalDate first = null;
        LocalDate last = null;
        for (PartyProfileDay day : days) {
            documents += day.documents();
            returns += day.returns();
            documentsNet = documentsNet.add(day.documentsNet());
            returnedNet = returnedNet.add(day.returnedNet());
            cash = cash.add(day.cash()).subtract(day.refunded());
            headerDiscount = headerDiscount.add(day.headerDiscount()).subtract(day.returnsHeaderDiscount());
            if (day.documents() > 0) {
                activeDays++;
                first = first == null || day.day().isBefore(first) ? day.day() : first;
                last = last == null || day.day().isAfter(last) ? day.day() : last;
            }
        }
        BigDecimal itemsNet = BigDecimal.ZERO;
        for (PartyItemRow item : items) {
            itemsNet = itemsNet.add(item.net());
        }
        return new PartyProfileSummary(documents, returns, documentsNet, returnedNet,
                documentsNet.subtract(returnedNet), cash, headerDiscount, itemsNet, activeDays,
                Optional.ofNullable(first), Optional.ofNullable(last), lastEver);
    }

    /** What was not settled in cash - left on the account. */
    public BigDecimal deferred() {
        return net.subtract(cash);
    }

    /** The documents' net over their number: what one visit is worth. */
    public Optional<BigDecimal> averageDocument() {
        return documents == 0 ? Optional.empty()
                : Optional.of(documentsNet.divide(BigDecimal.valueOf(documents), 2, RoundingMode.HALF_UP));
    }

    /** Days from the first visit of the period to the last, over the gaps between them. */
    public Optional<BigDecimal> averageGapDays() {
        if (activeDays < 2 || firstDay.isEmpty() || lastDay.isEmpty()) {
            return Optional.empty();
        }
        long span = ChronoUnit.DAYS.between(firstDay.get(), lastDay.get());
        return Optional.of(BigDecimal.valueOf(span).divide(BigDecimal.valueOf(activeDays - 1L), 1, RoundingMode.HALF_UP));
    }

    /**
     * What the lines do not explain: the headers' net less the lines' net and the headers' own
     * discounts. Zero on every document this application saves; a figure here is a stored row
     * whose total is not the sum of its lines, and a profile says so rather than hiding it.
     */
    public BigDecimal unexplained() {
        return net.subtract(itemsNet.subtract(headerDiscount));
    }
}
