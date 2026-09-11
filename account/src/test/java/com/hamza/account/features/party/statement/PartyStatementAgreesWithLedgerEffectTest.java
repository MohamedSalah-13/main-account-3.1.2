package com.hamza.account.features.party.statement;

import com.hamza.account.document.DocumentLedgerEffect;
import com.hamza.account.document.DocumentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Holds a statement row to {@link DocumentLedgerEffect}, which is the system's one
 * statement of what a document does to a party's account.
 * <p>
 * <b>This test is the reason the package exists, and it is aimed at a defect that had
 * already happened twice.</b>
 * <p>
 * The rule is written down in exactly one place: the cash column is what the treasury moved,
 * and whatever it did not cover goes onto the party's account. Three readers used to decide
 * it for themselves instead — {@code account_customer_table}, {@code treasury_balance} and
 * the statement screen — and all three got the return families wrong, each in its own way.
 * {@code V15__return_cash_split.sql} and {@code PartyLedgerViewAcceptanceTest} fixed and
 * pinned the first two against a real database. The third was never fixed: the statement
 * screen still carried
 * <pre>{@code double total = 0;
 * if (invoiceType.equals(InvoiceType.CASH)) total = totalAfterDiscount;}</pre>
 * which is the same {@code IF(invoice_type = 1, total, 0)} the migration had removed from
 * SQL. A deferred sales return therefore reached the statement as a debit of zero and a
 * credit of zero — an invisible row — so a customer who returned a thousand pounds of goods
 * on account still appeared to owe the thousand, on the one page they are shown and asked to
 * agree with.
 * <p>
 * The lesson is not "returns are tricky". It is that a second reader of a rule drifts from
 * the first, and nothing notices until someone compares two screens. So rather than assert
 * the numbers this package produces, every case here asserts that it produces <em>the same
 * number</em> as {@code DocumentLedgerEffect} — the two are held together, and a future
 * change to either breaks the build instead of a customer's statement.
 */
class PartyStatementAgreesWithLedgerEffectTest {

    /**
     * A statement row exactly as the view hands it over.
     * <p>
     * The views apply {@link DocumentType#ledgerSign()} themselves — a return's three
     * columns are stored negated — so this is what {@code DocumentLedgerEffect}'s own
     * {@code ledgerPurchase}/{@code ledgerDiscount}/{@code ledgerPaid} answer. Building the
     * row from those three is the point: it is the seam where the two definitions meet.
     */
    private static PartyStatementRow rowFor(DocumentLedgerEffect effect) {
        PartyMovementKind kind = effect.type().isReturn()
                ? PartyMovementKind.RETURN : PartyMovementKind.INVOICE;
        return new PartyStatementRow(1, LocalDate.of(2026, 9, 1), LocalDateTime.now(), kind,
                effect.onAccount().signum() == 0, 1,
                effect.ledgerPurchase(), effect.ledgerDiscount(), effect.ledgerPaid(),
                BigDecimal.ZERO, 1, "الدرج", 1, "admin", "");
    }

    /**
     * The figures of a real document, over all four families and both payment terms.
     * <p>
     * A cash document stores {@code paid = net} ({@code InvoicePaymentTerms} forces it), and
     * a deferred one stores {@code paid = 0} or a part payment. The four rows below are
     * those three shapes plus the partial, which is where hand-written branches break.
     */
    @ParameterizedTest(name = "{0}: total {1}, discount {2}, paid {3}")
    @CsvSource({
            // cash: paid covers the whole net, so the account does not move
            "SALES,          1000, 0,   1000",
            "SALES,          1000, 50,  950",
            "PURCHASE,       800,  0,   800",
            "SALES_RETURN,   1000, 0,   1000",
            "PURCHASE_RETURN, 600, 0,   600",
            // deferred: nothing paid, the whole net goes on the account
            "SALES,          1000, 0,   0",
            "SALES,          1000, 50,  0",
            "PURCHASE,       800,  25,  0",
            "SALES_RETURN,   1000, 0,   0",
            "PURCHASE_RETURN, 600, 0,   0",
            // part paid, which no branch on invoice_type can get right
            "SALES,          1000, 0,   400",
            "SALES_RETURN,   1000, 0,   400",
            "PURCHASE,       800,  0,   300",
            "PURCHASE_RETURN, 600, 50,  200"
    })
    void aStatementRowMovesTheBalanceByExactlyWhatTheLedgerEffectSays(
            DocumentType type, double total, double discount, double paid) {
        DocumentLedgerEffect effect = DocumentLedgerEffect.of(type, total, discount, paid);
        PartyStatementRow row = rowFor(effect);

        assertEquals(0, row.balanceChange().compareTo(effect.balanceChange()),
                "the statement row and DocumentLedgerEffect must agree on the balance change"
                        + " (row " + row.balanceChange() + ", effect " + effect.balanceChange() + ")");
        assertEquals(0, row.debit().subtract(row.credit()).compareTo(effect.balanceChange()),
                "the two columns a reader sees must subtract to the same change"
                        + " (debit " + row.debit() + ", credit " + row.credit() + ")");
    }

    /**
     * <b>The case the old screen got wrong, stated on its own so it cannot be lost in a
     * parameter list.</b> A deferred sales return of 1000 credits the customer by 1000.
     */
    @Test
    @DisplayName("a deferred sales return credits the customer its whole value, not nothing")
    void aDeferredSalesReturnIsNotInvisible() {
        DocumentLedgerEffect effect = DocumentLedgerEffect.of(DocumentType.SALES_RETURN, 1000, 0, 0);
        PartyStatementRow row = rowFor(effect);

        assertEquals(new BigDecimal("-1000.00"), effect.balanceChange());
        assertEquals(new BigDecimal("-1000.00"), row.balanceChange());
        assertEquals(new BigDecimal("1000.00"), row.credit());
        assertEquals(new BigDecimal("0.00"), row.debit());
    }

    /**
     * And the mirror image on the supplier side: a deferred purchase return reduces what the
     * shop owes, rather than doing nothing.
     * <p>
     * <b>Which column it lands in is a presentation convention, and this is the one the
     * application already has.</b> Both ledgers put {@code purchase} in the column labelled
     * {@code common.debtor} and {@code paid} in the one labelled {@code common.creditor},
     * whichever party it is — so a reduction of the balance shows as a credit on a supplier's
     * statement as much as on a customer's. Keeping that is deliberate: it is what users and
     * {@code account-statement-A4.jrxml} already read, and turning the supplier's statement
     * round is a decision for the redesign in phase ج, made on purpose and once, not as a
     * side effect of this package.
     * <p>
     * What matters here is that the <em>balance</em> moves by what the one rule says, and the
     * two columns still subtract to it.
     */
    @Test
    @DisplayName("a deferred purchase return reduces what is owed to the supplier")
    void aDeferredPurchaseReturnReducesTheDebtToTheSupplier() {
        DocumentLedgerEffect effect = DocumentLedgerEffect.of(DocumentType.PURCHASE_RETURN, 600, 0, 0);
        PartyStatementRow row = rowFor(effect);

        assertEquals(new BigDecimal("-600.00"), effect.balanceChange());
        assertEquals(0, row.balanceChange().compareTo(effect.balanceChange()));
        assertEquals(new BigDecimal("600.00"), row.credit());
        assertEquals(new BigDecimal("0.00"), row.debit());
    }

    /**
     * A cash document moves the account by nothing at all — on every one of the four
     * families, and without anything branching on the payment terms to make it so. That is
     * the property the one rule buys, and the reason no reader of it needs an
     * {@code if (CASH)} of its own.
     */
    @ParameterizedTest
    @EnumSource(DocumentType.class)
    void aCashDocumentOfAnyFamilyLeavesTheAccountWhereItWas(DocumentType type) {
        DocumentLedgerEffect effect = DocumentLedgerEffect.of(type, 1000, 100, 900);
        PartyStatementRow row = rowFor(effect);

        assertEquals(0, BigDecimal.ZERO.compareTo(effect.balanceChange()));
        assertEquals(0, BigDecimal.ZERO.compareTo(row.balanceChange()));
        // and it is still on the page, with its value and its payment against it
        assertEquals(new BigDecimal("900.00"), row.debit());
        assertEquals(new BigDecimal("900.00"), row.credit());
    }
}
