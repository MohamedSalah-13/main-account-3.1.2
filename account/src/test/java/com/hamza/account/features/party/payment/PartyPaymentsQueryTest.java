package com.hamza.account.features.party.payment;

import com.hamza.account.features.events.PartyKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartyPaymentsQueryTest {

    @Test
    void customersPaymentsAreTheCashRowsOfTheirLedger() {
        assertEquals("""
                SELECT a.account_num AS movement_id,
                       a.account_date AS movement_date,
                       a.account_code AS party_id,
                       p.name AS party_name,
                       a.paid AS paid,
                       COALESCE(t.t_name, '') AS treasury_name,
                       COALESCE(a.numberInv, 0) AS invoice_number,
                       COALESCE(a.notes, '') AS notes
                FROM customers_accounts a
                         JOIN custom p ON p.id = a.account_code
                         LEFT JOIN treasury t ON t.id = a.treasury_id
                WHERE a.account_date BETWEEN ? AND ?
                  AND a.paid <> 0
                ORDER BY a.account_date, a.account_num""", PartyPaymentsQuery.betweenSql(PartyKind.CUSTOMER));
    }

    @Test
    void suppliersReadTheirOwnTables() {
        String sql = PartyPaymentsQuery.betweenSql(PartyKind.SUPPLIER);
        assertEquals(true, sql.contains("FROM suppliers_accounts a"));
        assertEquals(true, sql.contains("JOIN suppliers p ON p.id = a.account_code"));
    }

    @Test
    void theParameterCountIsTheStatementsOwn() {
        for (PartyKind kind : PartyKind.values()) {
            assertEquals(PartyPaymentsQuery.PARAMETERS,
                    PartyPaymentsQuery.betweenSql(kind).chars().filter(c -> c == '?').count());
        }
    }
}
