package com.hamza.account.features.party.rfm;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.ItemNetLines;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.party.profile.PartyProfileQuery;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerRfmQueryTest {

    @Test
    void theBoundValuesMatchThePlaceholdersInEveryStatement() {
        CustomerRfmFilter filter = new CustomerRfmFilter(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                "علي", 1, CustomerRfmOrder.SCORE, 0, 50);

        assertEquals(CustomerRfmQuery.SCORED_PARAMETERS, placeholders(CustomerRfmQuery.scoredSql()));
        assertEquals(CustomerRfmQuery.SUMMARY_PARAMETERS, placeholders(CustomerRfmQuery.summarySql()));
        assertEquals(CustomerRfmQuery.SUMMARY_PARAMETERS, JdbcCustomerRfmRepository.bindFilter(filter).size());
        for (CustomerRfmOrder order : CustomerRfmOrder.values()) {
            assertEquals(CustomerRfmQuery.PAGE_PARAMETERS, placeholders(CustomerRfmQuery.pageSql(order)), order.name());
        }
    }

    /** A search finds a customer; it must never move one from one fifth to another. */
    @Test
    void theTextIsAppliedAfterTheScoresAreWorkedOut() {
        for (String sql : new String[]{CustomerRfmQuery.pageSql(CustomerRfmOrder.VALUE), CustomerRfmQuery.summarySql()}) {
            int lastWindow = sql.lastIndexOf("OVER ()");
            int text = sql.indexOf(CustomerRfmQuery.TEXT_CONDITION);
            assertTrue(lastWindow > 0 && text > lastWindow, "the text condition sits outside the scored CTE");
            assertEquals(1, count(sql, "LIKE ?"), "the text is matched once, on the scored rows");
        }
    }

    /** The cash-sales customer is left out of the population, so it takes no share of any fifth. */
    @Test
    void theExcludedCustomerIsLeftOutBeforeAnybodyIsScored() {
        String sql = CustomerRfmQuery.scoredSql();
        assertTrue(sql.indexOf("WHERE l.party_id <> ?") < sql.indexOf("RANK()"));
    }

    /** The value is the profile's own expression: a second definition would be a second answer. */
    @Test
    void theValueIsWhatTheProfileSumsForTheSameDocuments() {
        String profile = PartyProfileQuery.daysSql(PartyKind.CUSTOMER);
        String rfm = CustomerRfmQuery.scoredSql();
        DocumentTableSpec sales = ItemNetLines.SALES.documents();
        DocumentTableSpec returns = ItemNetLines.SALES.returns();

        assertTrue(profile.contains("SUM(h.total - h.discount) AS net"));
        assertTrue(rfm.contains("SUM(h.total - h.discount) AS sold"));
        assertTrue(profile.contains("SUM(r.total - r.discount)"));
        assertTrue(rfm.contains("SUM(r.total - r.discount) AS returned"));
        assertTrue(rfm.contains("FROM " + sales.table() + " h"));
        assertTrue(rfm.contains("FROM " + returns.table() + " r"));
        assertTrue(rfm.contains("h." + sales.dateColumn() + " BETWEEN ? AND ?"));
        assertTrue(rfm.contains("r." + returns.dateColumn() + " BETWEEN ? AND ?"));
    }

    @Test
    void eachScoreRanksItsOwnFigureOverTheWholePopulation() {
        String sql = CustomerRfmQuery.scoredSql();
        assertTrue(sql.contains("1 + FLOOR(5 * (RANK() OVER (ORDER BY a.last_day) - 1) / COUNT(*) OVER ()) AS r_score"));
        assertTrue(sql.contains("1 + FLOOR(5 * (RANK() OVER (ORDER BY a.documents) - 1) / COUNT(*) OVER ()) AS f_score"));
        assertTrue(sql.contains("1 + FLOOR(5 * (RANK() OVER (ORDER BY a.net) - 1) / COUNT(*) OVER ()) AS m_score"));
    }

    /** Equal figures in a fixed order, or a page boundary can show one customer twice and drop another. */
    @Test
    void everyOrderEndsOnTheCustomersId() {
        for (CustomerRfmOrder order : CustomerRfmOrder.values()) {
            assertTrue(order.orderBy().endsWith("party_id"), order.name());
            assertTrue(CustomerRfmQuery.pageSql(order).contains("ORDER BY " + order.orderBy() + "\nLIMIT ? OFFSET ?"));
        }
    }

    private static int placeholders(String sql) {
        return count(sql, "?");
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
            count++;
        }
        return count;
    }
}
