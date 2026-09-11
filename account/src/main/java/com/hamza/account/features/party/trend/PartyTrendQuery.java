package com.hamza.account.features.party.trend;

import com.hamza.account.features.events.PartyKind;
import com.hamza.account.party.PartyLedgerSpec;

/**
 * The trend chart's one statement: a day's debit and credit over a ledger view.
 * <p>
 * <b>The two figures are the balances screen's own.</b> {@link #DEBIT} and {@link #CREDIT} are
 * the expressions {@code PartyBalanceQuery} sums into "مدين الفترة" and "دائن الفترة", per row of
 * the same view - so a month on the chart and that month's period columns cannot disagree, and
 * {@code PartyTrendQueryTest} fails the build if the text of either drifts. Two consequences come
 * with that, both deliberate rather than accidents of it:
 * <ul>
 *   <li>a party's opening balance is debit on the day the party was created, as it is in the
 *       period-debit column; and</li>
 *   <li>a return is credit, not a collection - the balances screen counts it the same way.</li>
 * </ul>
 * <p>
 * Only identifiers {@link PartyLedgerSpec} owns are concatenated; the dates and the party are
 * bound. Four placeholders: the two dates, then the party twice for {@code ? IS NULL OR}.
 */
public final class PartyTrendQuery {

    /** What a ledger row charged the party: its value when positive, and cash handed back to them. */
    public static final String DEBIT = "GREATEST(m.purchase - m.discount, 0) + GREATEST(-m.paid, 0)";

    /** What a ledger row credited them: what they paid, and a negative value such as a return. */
    public static final String CREDIT = "GREATEST(m.paid, 0) + GREATEST(-(m.purchase - m.discount), 0)";

    private PartyTrendQuery() {
    }

    /** One row per day with movement, oldest first. */
    public static String dailySql(PartyKind kind) {
        return """
                SELECT m.account_date        AS day,
                       ROUND(SUM(%2$s), 2) AS debit,
                       ROUND(SUM(%3$s), 2) AS credit
                FROM %1$s m
                WHERE m.account_date BETWEEN ? AND ?
                  AND (? IS NULL OR m.%4$s = ?)
                GROUP BY m.account_date
                ORDER BY m.account_date"""
                .formatted(PartyLedgerSpec.of(kind).view(), DEBIT, CREDIT, PartyLedgerSpec.PARTY);
    }
}
