package com.hamza.account.features.party.payment;

import com.hamza.account.features.events.PartyKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyPaymentsQueryTest {

    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    private static long parameters(String sql) {
        return sql.chars().filter(character -> character == '?').count();
    }

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
                       COALESCE(a.notes, '') AS notes,
                       COALESCE(u.user_name, '') AS user_name
                FROM customers_accounts a
                         JOIN custom p ON p.id = a.account_code
                         LEFT JOIN treasury t ON t.id = a.treasury_id
                         LEFT JOIN users u ON u.id = a.user_id
                WHERE a.account_date BETWEEN ? AND ?
                  AND a.paid <> 0
                ORDER BY a.account_date, a.account_num""",
                PartyPaymentsQuery.pageSql(PartyPaymentsFilter.of(PartyKind.CUSTOMER, FROM, TO)));
    }

    @Test
    void suppliersReadTheirOwnTables() {
        String sql = PartyPaymentsQuery.pageSql(PartyPaymentsFilter.of(PartyKind.SUPPLIER, FROM, TO));
        assertTrue(sql.contains("FROM suppliers_accounts a"));
        assertTrue(sql.contains("JOIN suppliers p ON p.id = a.account_code"));
    }

    @Test
    @DisplayName("a name is found by part; a number also as a party's code or an allocated invoice")
    void theText() {
        PartyPaymentsFilter name = new PartyPaymentsFilter(PartyKind.CUSTOMER, FROM, TO, " أحمد ", 0);
        assertTrue(PartyPaymentsQuery.pageSql(name).contains("AND p.name LIKE ? ESCAPE '!'\n"));
        assertEquals(List.of(FROM, TO, "%أحمد%"), PartyPaymentsQuery.values(name));

        PartyPaymentsFilter number = new PartyPaymentsFilter(PartyKind.CUSTOMER, FROM, TO, "١٢٠", 0);
        assertEquals("120", number.text(), "digits typed on an Arabic keyboard are the digits they are");
        assertTrue(PartyPaymentsQuery.pageSql(number)
                .contains("AND (p.name LIKE ? ESCAPE '!' OR a.account_code = ? OR a.numberInv = ?)"));
        assertEquals(List.of(FROM, TO, "%120%", 120, 120), PartyPaymentsQuery.values(number));
    }

    @Test
    @DisplayName("a typed % or _ is a character, not a wildcard")
    void wildcardsAreEscaped() {
        assertEquals("50!%!_off!!", PartyPaymentsQuery.escape("50%_off!"));
    }

    @Test
    void aTreasury() {
        PartyPaymentsFilter filter = new PartyPaymentsFilter(PartyKind.SUPPLIER, FROM, TO, "", 3);
        assertTrue(PartyPaymentsQuery.pageSql(filter).contains("AND a.treasury_id = ?"));
        assertEquals(List.of(FROM, TO, 3), PartyPaymentsQuery.values(filter));
        assertFalse(PartyPaymentsQuery.pageSql(PartyPaymentsFilter.of(PartyKind.SUPPLIER, FROM, TO))
                .contains("treasury_id = ?"));
    }

    @Test
    @DisplayName("the statement takes exactly the values it is bound with, for every combination")
    void theParameterCountIsTheBindersOwn() {
        for (PartyKind kind : PartyKind.values()) {
            for (String text : new String[]{"", "name", "42"}) {
                for (int treasury : new int[]{0, 5}) {
                    PartyPaymentsFilter filter = new PartyPaymentsFilter(kind, FROM, TO, text, treasury);
                    assertEquals(PartyPaymentsQuery.values(filter).size(),
                            parameters(PartyPaymentsQuery.pageSql(filter)), filter.toString());
                }
            }
        }
    }
}
