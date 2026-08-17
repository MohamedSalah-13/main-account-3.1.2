package com.hamza.account.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins what a document does to its party's account and to the treasury.
 * <p>
 * These numbers were only ever expressed as SQL inside {@code account_customer_table},
 * {@code account_suppliers_table} and {@code treasury_balance}, which is why nobody
 * noticed that the return families disagreed with the invoice families and with each
 * other. Asserted here first, in Java and without a database, so that
 * {@code PartyLedgerViewAcceptanceTest} has something to hold the views <em>to</em>
 * rather than merely describing whatever they happen to produce.
 * <p>
 * The cases are the ones the screen can actually reach: {@code InvoicePaymentTerms}
 * forces {@code paid = net} for a cash document and leaves it to the user for a
 * deferred one, so "cash", "deferred, nothing paid" and "deferred, part paid" are the
 * three shapes a stored header comes in.
 */
class DocumentLedgerEffectTest {

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }

    @Nested
    @DisplayName("A sale, which the views already had right")
    class Sale {

        @Test
        void cashSaleSettlesItselfAndLeavesTheBalanceAlone() {
            // 1000 less 100 discount, all 900 collected at the counter.
            DocumentLedgerEffect effect =
                    DocumentLedgerEffect.of(DocumentType.SALES, 1000, 100, 900);

            assertEquals(money("900.00"), effect.net());
            assertEquals(money("0.00"), effect.onAccount());
            assertEquals(money("0.00"), effect.balanceChange());
            assertEquals(money("900.00"), effect.treasuryIn());
            assertEquals(money("0.00"), effect.treasuryOut());
        }

        @Test
        void deferredSalePutsWhatWasNotCollectedOnTheAccount() {
            DocumentLedgerEffect effect =
                    DocumentLedgerEffect.of(DocumentType.SALES, 1000, 100, 300);

            assertEquals(money("600.00"), effect.balanceChange());
            assertEquals(money("300.00"), effect.treasuryIn());
        }

        @Test
        void theThreeStatementColumnsKeepTheirStoredSign() {
            DocumentLedgerEffect effect =
                    DocumentLedgerEffect.of(DocumentType.SALES, 1000, 100, 300);

            assertEquals(money("1000.00"), effect.ledgerPurchase());
            assertEquals(money("100.00"), effect.ledgerDiscount());
            assertEquals(money("300.00"), effect.ledgerPaid());
        }
    }

    @Nested
    @DisplayName("A sales return - the case both views got backwards")
    class SalesReturn {

        @Test
        void cashRefundLeavesTheBalanceAloneAndEmptiesTheTill() {
            // Goods worth 1000 come back and 1000 goes over the counter.
            DocumentLedgerEffect effect =
                    DocumentLedgerEffect.of(DocumentType.SALES_RETURN, 1000, 0, 1000);

            assertEquals(money("0.00"), effect.balanceChange());
            assertEquals(money("0.00"), effect.treasuryIn());
            assertEquals(money("1000.00"), effect.treasuryOut());
        }

        @Test
        void deferredReturnCreditsTheAccountAndTouchesNoCash() {
            // The headline defect. account_customer_table used to answer 0 here, so the
            // customer still owed the 1000 they had handed back; treasury_balance used
            // to answer 1000 out, from a till that never opened.
            DocumentLedgerEffect effect =
                    DocumentLedgerEffect.of(DocumentType.SALES_RETURN, 1000, 0, 0);

            assertEquals(money("-1000.00"), effect.balanceChange());
            assertEquals(money("0.00"), effect.treasuryOut());
            assertEquals(money("0.00"), effect.treasuryIn());
        }

        @Test
        void deferredReturnSplitsBetweenTheTillAndTheAccount() {
            // 300 refunded in cash, the other 700 comes off what they owe.
            DocumentLedgerEffect effect =
                    DocumentLedgerEffect.of(DocumentType.SALES_RETURN, 1000, 0, 300);

            assertEquals(money("-700.00"), effect.balanceChange());
            assertEquals(money("300.00"), effect.treasuryOut());
        }

        @Test
        void aDiscountOnTheReturnReducesWhatIsCreditedBack() {
            DocumentLedgerEffect effect =
                    DocumentLedgerEffect.of(DocumentType.SALES_RETURN, 1000, 100, 0);

            assertEquals(money("900.00"), effect.net());
            assertEquals(money("-900.00"), effect.balanceChange());
        }

        @Test
        void theStatementColumnsAreNegated() {
            DocumentLedgerEffect effect =
                    DocumentLedgerEffect.of(DocumentType.SALES_RETURN, 1000, 100, 300);

            assertEquals(money("-1000.00"), effect.ledgerPurchase());
            assertEquals(money("-100.00"), effect.ledgerDiscount());
            // A refund is a receipt going the other way. The old view counted it as
            // money the customer had paid in, which is what cancelled the credit.
            assertEquals(money("-300.00"), effect.ledgerPaid());
        }
    }

    @Nested
    @DisplayName("A purchase and its return, which mirror the sales side")
    class PurchaseSide {

        @Test
        void cashPurchasePaysOutAndSettlesItself() {
            DocumentLedgerEffect effect =
                    DocumentLedgerEffect.of(DocumentType.PURCHASE, 500, 0, 500);

            assertEquals(money("0.00"), effect.balanceChange());
            assertEquals(money("500.00"), effect.treasuryOut());
            assertEquals(money("0.00"), effect.treasuryIn());
        }

        @Test
        void deferredPurchaseOwesTheSupplierWhatWasNotPaid() {
            DocumentLedgerEffect effect =
                    DocumentLedgerEffect.of(DocumentType.PURCHASE, 500, 0, 200);

            assertEquals(money("300.00"), effect.balanceChange());
            assertEquals(money("200.00"), effect.treasuryOut());
        }

        @Test
        void deferredPurchaseReturnReducesWhatIsOwedToTheSupplier() {
            // Same defect, same shape: goods go back to the supplier on account, so what
            // we owe them drops and no money is collected.
            DocumentLedgerEffect effect =
                    DocumentLedgerEffect.of(DocumentType.PURCHASE_RETURN, 500, 0, 0);

            assertEquals(money("-500.00"), effect.balanceChange());
            assertEquals(money("0.00"), effect.treasuryIn());
        }

        @Test
        void cashPurchaseReturnBringsTheMoneyBackIn() {
            DocumentLedgerEffect effect =
                    DocumentLedgerEffect.of(DocumentType.PURCHASE_RETURN, 500, 0, 500);

            assertEquals(money("0.00"), effect.balanceChange());
            assertEquals(money("500.00"), effect.treasuryIn());
            assertEquals(money("0.00"), effect.treasuryOut());
        }
    }

    @Nested
    @DisplayName("The rules that hold for all four")
    class AcrossTheFour {

        @ParameterizedTest
        @EnumSource(DocumentType.class)
        void balanceChangeIsTheThreeStatementColumnsWorkedOutTheWayTheViewWorksThemOut(
                DocumentType type) {
            // PartyLedgerSpec.statementSql computes purchase - discount - paid. If that
            // ever parts company with balanceChange(), a statement's rows would stop
            // adding up to its own total.
            DocumentLedgerEffect effect = DocumentLedgerEffect.of(type, 1000, 100, 250);

            assertEquals(
                    effect.ledgerPurchase()
                            .subtract(effect.ledgerDiscount())
                            .subtract(effect.ledgerPaid()),
                    effect.balanceChange());
        }

        @ParameterizedTest
        @EnumSource(DocumentType.class)
        void aCashDocumentNeverMovesTheAccount(DocumentType type) {
            // Which is why none of this needs to branch on invoice_type: paid = net is
            // what "cash" means, and the account effect falls out as zero by itself.
            DocumentLedgerEffect effect = DocumentLedgerEffect.of(type, 1000, 100, 900);

            assertEquals(money("0.00"), effect.balanceChange());
        }

        @ParameterizedTest
        @EnumSource(DocumentType.class)
        void theWholeNetIsSplitBetweenTheTillAndTheAccount(DocumentType type) {
            DocumentLedgerEffect effect = DocumentLedgerEffect.of(type, 1000, 100, 250);

            // Nothing is lost between the two destinations, and nothing is counted twice:
            // |balance move| + |cash move| = net, on every document type.
            assertEquals(effect.net(),
                    effect.balanceChange().abs().add(effect.treasuryChange().abs()));
        }

        @ParameterizedTest
        @EnumSource(DocumentType.class)
        void aReturnIsExactlyTheDocumentItReversesWithBothSignsFlipped(DocumentType type) {
            DocumentLedgerEffect document = DocumentLedgerEffect.of(type, 1000, 100, 250);
            DocumentLedgerEffect reversed =
                    DocumentLedgerEffect.of(type.reverses(), 1000, 100, 250);

            if (type.isReturn()) {
                assertEquals(document.balanceChange().negate(), reversed.balanceChange());
                assertEquals(document.treasuryChange().negate(), reversed.treasuryChange());
            } else {
                assertEquals(document.balanceChange(), reversed.balanceChange());
            }
        }
    }
}
