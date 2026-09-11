package com.hamza.account.features.party.balances;

import com.hamza.account.features.events.PartyKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The balances list's SQL, and the two properties that keep it honest.
 * <p>
 * The statements here are assembled from the filter rather than constant - the {@code HAVING} carries
 * only the conditions the filter sets - so what is pinned is not one piece of text but the rules:
 * that the page and the footer are built from the same select, that the balance answers the date and
 * nothing else, and that the parameter count matches what the repository binds for every combination
 * of filters. The last one matters most: a statement whose placeholders and values have drifted by
 * one does not fail, it shifts every value along and answers something.
 */
class PartyBalanceQueryTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 10);

    private static PartyBalanceFilter filter(BalanceState state, BigDecimal min, BigDecimal max,
                                             boolean overLimit, Integer idleDays, String text) {
        return new PartyBalanceFilter(PartyKind.CUSTOMER, AS_OF, null, state, min, max,
                null, null, overLimit, idleDays, text, 0, 50);
    }

    private static int placeholders(String sql) {
        return (int) sql.chars().filter(c -> c == '?').count();
    }

    /**
     * How many values {@code JdbcPartyBalanceRepository.bindFilter} supplies, before the limit and
     * offset: six dates, then area ×2, tier ×2, text ×3, then one per aggregate condition that is set.
     */
    private static int boundValues(PartyBalanceFilter f) {
        int fixed = 6 + 2 + 2 + 3;
        int aggregates = (f.minBalance() == null ? 0 : 1)
                + (f.maxBalance() == null ? 0 : 1)
                + (f.idleDays() == null ? 0 : 1);
        return fixed + aggregates;
    }

    @Test
    @DisplayName("the page and the footer are built from one select")
    void thePageAndTheSummaryShareTheirSelect() {
        PartyBalanceFilter f = filter(BalanceState.DEBTOR, null, null, false, null, "");
        String page = PartyBalanceQuery.pageSql(f);
        String summary = PartyBalanceQuery.summarySql(f);

        String shared = page.substring(0, page.indexOf("\nORDER BY"));
        assertTrue(summary.contains(shared),
                "the summary must be built over the page's own select, or the footer will start "
                        + "describing a different set of parties from the table");
    }

    /**
     * The balance is a sum up to a day, and the period window touches only the movement columns.
     * <p>
     * The rule a later filter is most likely to break, and breaking it yields a plausible number
     * rather than an error - the same trap as the statement's opening balance.
     */
    @ParameterizedTest
    @EnumSource(PartyKind.class)
    void theBalanceAnswersTheDateAlone(PartyKind kind) {
        PartyBalanceFilter f = new PartyBalanceFilter(kind, AS_OF, LocalDate.of(2026, 9, 1),
                BalanceState.ALL, null, null, null, null, false, null, "", 0, 50);
        String sql = PartyBalanceQuery.pageSql(f);
        assertTrue(sql.contains(
                        "ROUND(SUM(CASE WHEN m.account_date <= ? THEN m.purchase - m.discount - m.paid ELSE 0 END), 2)"),
                "the balance must be everything up to asOf: " + sql);
        assertTrue(sql.contains("m.account_date BETWEEN ? AND ?"),
                "the period window belongs on the movement columns");
    }

    /** Each ledger reads its own view and its own party table, from the two specifications. */
    @Test
    void eachKindReadsItsOwnTables() {
        String customer = PartyBalanceQuery.pageSql(PartyBalanceFilter.allToday(PartyKind.CUSTOMER));
        String supplier = PartyBalanceQuery.pageSql(PartyBalanceFilter.allToday(PartyKind.SUPPLIER));

        assertTrue(customer.contains("FROM account_customer_table m"), customer);
        assertTrue(customer.contains("JOIN custom p"), customer);
        assertTrue(supplier.contains("FROM account_suppliers_table m"), supplier);
        assertTrue(supplier.contains("JOIN suppliers p"), supplier);
    }

    /**
     * A supplier has no credit limit and no price tier, so those select a literal zero rather than a
     * column that is not on the table - which would be a statement that cannot run at all.
     */
    @Test
    @DisplayName("a supplier's query names no limit_num and no price_id")
    void aSupplierHasNoLimitOrTier() {
        String supplier = PartyBalanceQuery.pageSql(PartyBalanceFilter.allToday(PartyKind.SUPPLIER));
        assertFalse(supplier.contains("limit_num"), supplier);
        assertFalse(supplier.contains("price_id"), supplier);
        String customer = PartyBalanceQuery.pageSql(PartyBalanceFilter.allToday(PartyKind.CUSTOMER));
        assertTrue(customer.contains("p.limit_num"), customer);
        assertTrue(customer.contains("p.price_id"), customer);
    }

    /** The three balance states, and that "all" adds no condition at all. */
    @Test
    void eachStateAddsItsOwnCondition() {
        assertEquals("", BalanceState.ALL.havingSql("balance"));
        assertEquals("balance > 0", BalanceState.DEBTOR.havingSql("balance"));
        assertEquals("balance < 0", BalanceState.CREDITOR.havingSql("balance"));
        assertEquals("balance = 0", BalanceState.SETTLED.havingSql("balance"));

        assertFalse(PartyBalanceQuery.pageSql(filter(BalanceState.ALL, null, null, false, null, ""))
                .contains("HAVING"), "an unfiltered list needs no HAVING at all");
        assertTrue(PartyBalanceQuery.pageSql(filter(BalanceState.DEBTOR, null, null, false, null, ""))
                .contains("HAVING balance > 0"));
    }

    /**
     * The placeholder count matches what the repository binds, for every combination of the
     * conditions that are only written when set.
     */
    @Test
    @DisplayName("the parameters and the values stay in step, whichever filters are set")
    void theParameterCountMatchesWhatIsBound() {
        BigDecimal ten = BigDecimal.TEN;
        for (BalanceState state : BalanceState.values()) {
            for (BigDecimal min : new BigDecimal[]{null, ten}) {
                for (BigDecimal max : new BigDecimal[]{null, new BigDecimal("999")}) {
                    for (boolean overLimit : new boolean[]{false, true}) {
                        for (Integer idle : new Integer[]{null, 90}) {
                            for (String text : new String[]{"", "ahmed"}) {
                                PartyBalanceFilter f = filter(state, min, max, overLimit, idle, text);
                                assertEquals(boundValues(f) + 2,
                                        placeholders(PartyBalanceQuery.pageSql(f)),
                                        "page parameters for " + describe(f));
                                assertEquals(boundValues(f),
                                        placeholders(PartyBalanceQuery.summarySql(f)),
                                        "summary parameters for " + describe(f));
                            }
                        }
                    }
                }
            }
        }
    }

    /** The over-limit filter is a comparison between two aggregates, so it binds nothing. */
    @Test
    void theOverLimitFilterBindsNothing() {
        PartyBalanceFilter without = filter(BalanceState.ALL, null, null, false, null, "");
        PartyBalanceFilter with = filter(BalanceState.ALL, null, null, true, null, "");
        assertEquals(placeholders(PartyBalanceQuery.pageSql(without)),
                placeholders(PartyBalanceQuery.pageSql(with)));
        assertTrue(PartyBalanceQuery.pageSql(with).contains("credit_limit > 0 AND balance > credit_limit"));
    }

    @Test
    void noUserValueIsSplicedIn() {
        PartyBalanceFilter f = new PartyBalanceFilter(PartyKind.CUSTOMER, AS_OF, null,
                BalanceState.DEBTOR, new BigDecimal("777"), null, 3, 2, true, 90,
                "'; DROP TABLE custom; --", 0, 50);
        String sql = PartyBalanceQuery.pageSql(f) + PartyBalanceQuery.summarySql(f);
        assertFalse(sql.contains("DROP TABLE"), "a search must never reach the statement");
        assertFalse(sql.contains("777"), "an amount must never reach the statement");
        assertFalse(sql.contains(" = 90"), "a day count must never reach the statement");
    }

    @Test
    void theFilterRefusesWhatCannotBeAsked() {
        assertThrows(IllegalArgumentException.class, () -> new PartyBalanceFilter(
                PartyKind.CUSTOMER, AS_OF, AS_OF.plusDays(1), BalanceState.ALL,
                null, null, null, null, false, null, "", 0, 50));
        assertThrows(IllegalArgumentException.class, () -> new PartyBalanceFilter(
                PartyKind.CUSTOMER, AS_OF, null, BalanceState.ALL,
                new BigDecimal("100"), BigDecimal.TEN, null, null, false, null, "", 0, 50));
        assertThrows(IllegalArgumentException.class, () -> new PartyBalanceFilter(
                PartyKind.CUSTOMER, AS_OF, null, BalanceState.ALL,
                null, null, null, null, false, -1, "", 0, 50));
    }

    /** The wildcard escape that makes a party whose name contains a {@code %} searchable. */
    @Test
    void theTextPatternEscapesItsWildcards() {
        assertEquals("%50!% off%", PartyBalanceQuery.pattern("50% off"));
        assertEquals("%a!_b%", PartyBalanceQuery.pattern("a_b"));
        assertEquals("%%", PartyBalanceQuery.pattern(null));
    }

    private static String describe(PartyBalanceFilter f) {
        return f.state() + " min=" + f.minBalance() + " max=" + f.maxBalance()
                + " overLimit=" + f.overLimitOnly() + " idle=" + f.idleDays()
                + " text='" + f.text() + "'";
    }
}
