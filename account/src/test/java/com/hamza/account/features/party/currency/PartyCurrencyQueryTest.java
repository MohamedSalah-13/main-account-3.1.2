package com.hamza.account.features.party.currency;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every statement a party's currency is read and written with, character for character - the foreign
 * columns are V82's own, and every name before them comes from the table specifications.
 */
class PartyCurrencyQueryTest {

    @Test
    @DisplayName("a party's and a treasury's currency")
    void theCurrencies() {
        assertEquals("SELECT p.currency_id FROM custom p WHERE p.id = ?",
                PartyCurrencyQuery.partyCurrencySql(PartyKind.CUSTOMER));
        assertEquals("SELECT p.currency_id FROM suppliers p WHERE p.id = ?",
                PartyCurrencyQuery.partyCurrencySql(PartyKind.SUPPLIER));
        assertEquals("SELECT t.currency_id FROM treasury t WHERE t.id = ?", PartyCurrencyQuery.TREASURY_CURRENCY_SQL);
    }

    @Test
    @DisplayName("a movement's foreign half, on each ledger")
    void aMovement() {
        assertEquals("UPDATE customers_accounts SET paid_foreign = ?, purchase_foreign = ?, exchange_rate = ?"
                + " WHERE account_num = ?", PartyCurrencyQuery.writeMovementSql(PartyKind.CUSTOMER));
        assertEquals("UPDATE suppliers_accounts SET paid_foreign = ?, purchase_foreign = ?, exchange_rate = ?"
                + " WHERE account_num = ?", PartyCurrencyQuery.writeMovementSql(PartyKind.SUPPLIER));
    }

    @Test
    @DisplayName("a document's translation, each table with its own key, party and cash column")
    void aDocument() {
        assertEquals("SELECT d.sup_code AS party_id, d.invoice_date AS document_date, d.exchange_rate"
                + " FROM total_sales d WHERE d.invoice_number = ?", PartyCurrencyQuery.storedDocumentSql(DocumentType.SALES));
        assertEquals("SELECT d.sup_id AS party_id, d.invoice_date AS document_date, d.exchange_rate"
                + " FROM total_buy_re d WHERE d.id = ?", PartyCurrencyQuery.storedDocumentSql(DocumentType.PURCHASE_RETURN));
        assertEquals("SELECT d.total, d.discount, d.paid_from_treasury AS paid FROM total_sales_re d WHERE d.id = ?",
                PartyCurrencyQuery.documentAmountsSql(DocumentType.SALES_RETURN));
        assertEquals("SELECT d.total, d.discount, d.paid_up AS paid FROM total_buy d WHERE d.invoice_number = ?",
                PartyCurrencyQuery.documentAmountsSql(DocumentType.PURCHASE));
        assertEquals("UPDATE total_sales SET exchange_rate = ?, total_foreign = ?, discount_foreign = ?,"
                + " paid_foreign = ? WHERE invoice_number = ?", PartyCurrencyQuery.writeDocumentSql(DocumentType.SALES));
        assertEquals("UPDATE total_buy_re SET exchange_rate = ?, total_foreign = ?, discount_foreign = ?,"
                + " paid_foreign = ? WHERE id = ?", PartyCurrencyQuery.writeDocumentSql(DocumentType.PURCHASE_RETURN));
    }
}
