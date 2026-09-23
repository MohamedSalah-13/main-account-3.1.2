package com.hamza.account.features.party.currency;

import com.hamza.account.document.DocumentTableSpec;
import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.party.PartyLedgerSpec;
import com.hamza.account.party.PartyTableSpec;

/**
 * Every statement the party's currency is read and written with (V82, docs/currency-plan.md §14).
 * <p>
 * The table and column names come from {@link PartyTableSpec}, {@link PartyLedgerSpec} and
 * {@link DocumentTableSpec}, which check each against an identifier pattern; the foreign columns are
 * V82's own and the same on every table. Every value is bound. {@code PartyCurrencyQueryTest} pins the
 * statements.
 * <p>
 * <b>The foreign figures are written by a statement of their own, after the row's own insert or
 * update, inside the same transaction.</b> The rows' own statements are pinned character for character
 * and bind their values from models that know nothing of a currency; writing four columns beside them
 * leaves those untouched. The CHECKs of V82 hold at every step - the row is written with no foreign
 * figure, then given all of them at once.
 */
public final class PartyCurrencyQuery {

    private PartyCurrencyQuery() {
    }

    /** The currency a party deals in; a {@code NULL} is the base. */
    public static String partyCurrencySql(PartyKind kind) {
        return "SELECT p.currency_id FROM " + PartyTableSpec.of(kind).table() + " p WHERE p."
                + PartyTableSpec.KEY + " = ?";
    }

    /** The currency a treasury is in; a {@code NULL} is the base. */
    public static final String TREASURY_CURRENCY_SQL = "SELECT t.currency_id FROM treasury t WHERE t.id = ?";

    /** A movement's foreign figures and the rate between them - all three, or three {@code NULL}s. */
    public static String writeMovementSql(PartyKind kind) {
        return "UPDATE " + PartyLedgerSpec.of(kind).table()
                + " SET paid_foreign = ?, purchase_foreign = ?, exchange_rate = ? WHERE "
                + PartyLedgerSpec.KEY + " = ?";
    }

    /**
     * What a stored document says about its translation: its party, its day and its rate. Read before an
     * edit is written, which is what lets an edit keep its rate while neither of the other two moved.
     */
    public static String storedDocumentSql(DocumentType type) {
        DocumentTableSpec spec = DocumentTableSpec.of(type);
        return "SELECT d." + spec.party() + " AS party_id, d." + spec.dateColumn()
                + " AS document_date, d.exchange_rate, d.currency_id FROM " + spec.table()
                + " d WHERE d." + spec.key() + " = ?";
    }

    /** A document's header in its party's currency - typed (V83) or translated (V82); nothing for the base. */
    public static String foreignHeaderSql(DocumentType type) {
        DocumentTableSpec spec = DocumentTableSpec.of(type);
        return "SELECT d.currency_id, d.exchange_rate, d.total_foreign, d.discount_foreign, d.paid_foreign FROM "
                + spec.table() + " d WHERE d." + spec.key() + " = ? AND d.exchange_rate IS NOT NULL";
    }

    /** The lines of a document typed in its party's currency, each with what was typed on it (V83). */
    public static String writtenLinesSql(DocumentType type) {
        DocumentTableSpec spec = DocumentTableSpec.of(type);
        return "SELECT " + DocumentTableSpec.LINE_KEY + ", price_foreign, discount_foreign FROM " + spec.lineTable()
                + " WHERE " + DocumentTableSpec.LINE_DOCUMENT + " = ? AND price_foreign IS NOT NULL";
    }

    /** The three base figures a document's header was just written with, to translate. */
    public static String documentAmountsSql(DocumentType type) {
        DocumentTableSpec spec = DocumentTableSpec.of(type);
        return "SELECT d.total, d.discount, d." + spec.paid() + " AS paid FROM " + spec.table()
                + " d WHERE d." + spec.key() + " = ?";
    }

    /**
     * A document's figures in its party's currency - the currency it was written in (V83, NULL for one
     * translated from the base), the rate and the three amounts, or five {@code NULL}s.
     */
    public static String writeDocumentSql(DocumentType type) {
        DocumentTableSpec spec = DocumentTableSpec.of(type);
        return "UPDATE " + spec.table() + " SET currency_id = ?, exchange_rate = ?, total_foreign = ?,"
                + " discount_foreign = ?, paid_foreign = ? WHERE " + spec.key() + " = ?";
    }
}
