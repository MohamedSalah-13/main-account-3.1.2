package com.hamza.account.features.party.trend;

import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.balances.PartyBalanceFilter;
import com.hamza.account.features.party.balances.PartyBalanceQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyTrendQueryTest {

    private static String collapsed(String sql) {
        return sql.replaceAll("\\s+", " ");
    }

    /**
     * The chart's two figures are the balances screen's period columns, per row of the same view.
     * <p>
     * Written out twice they would drift, and the chart would draw a month the "مدين الفترة" column
     * disagrees with. So the balances statement must contain this query's two expressions, word for
     * word, in the place it sums them.
     */
    @ParameterizedTest
    @EnumSource(PartyKind.class)
    void theChartsFiguresAreTheBalanceScreensPeriodColumns(PartyKind kind) {
        String balances = collapsed(PartyBalanceQuery.pageSql(PartyBalanceFilter.allToday(kind)));

        assertTrue(balances.contains("THEN " + PartyTrendQuery.DEBIT + " ELSE 0 END), 2) AS period_debit"),
                "period debit and the chart's debit must be one expression: " + balances);
        assertTrue(balances.contains("THEN " + PartyTrendQuery.CREDIT + " ELSE 0 END), 2) AS period_credit"),
                "period credit and the chart's credit must be one expression: " + balances);
    }

    @Test
    void eachKindReadsItsOwnLedger() {
        assertTrue(PartyTrendQuery.dailySql(PartyKind.CUSTOMER).contains("FROM account_customer_table m"));
        assertTrue(PartyTrendQuery.dailySql(PartyKind.SUPPLIER).contains("FROM account_suppliers_table m"));
    }

    /** Two dates and the party twice - what JdbcPartyTrendRepository binds, in that order. */
    @ParameterizedTest
    @EnumSource(PartyKind.class)
    void fourValuesAreBound(PartyKind kind) {
        String sql = PartyTrendQuery.dailySql(kind);

        assertEquals(4, sql.chars().filter(c -> c == '?').count());
        assertTrue(collapsed(sql).contains("WHERE m.account_date BETWEEN ? AND ? AND (? IS NULL OR m.account_code = ?)"), sql);
    }

    @Test
    void oneRowPerDayOldestFirst() {
        String sql = collapsed(PartyTrendQuery.dailySql(PartyKind.CUSTOMER));

        assertTrue(sql.contains("GROUP BY m.account_date ORDER BY m.account_date"), sql);
    }
}
