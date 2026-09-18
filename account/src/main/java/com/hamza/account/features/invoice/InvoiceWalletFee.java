package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.shift.ShiftCashEffect;
import com.hamza.account.features.treasury.WalletFeeService;
import com.hamza.account.features.treasury.WalletFeeSource;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.OptionalInt;

/**
 * The e-wallet fee of a document's cash, kept in step with the document inside its own save.
 * <p>
 * A cash sale of 1000 settled on a wallet charging 1% puts 990 in that wallet, not 1000. The
 * collection screen has posted that difference as an expense since the treasury work; a document
 * never did, so every invoice paid on a wallet left its treasury's balance ahead of the wallet's
 * own by the fee, to be found and squared by hand. The rule is the collection's:
 * <b>the fee is an expense on the same treasury, never a deduction</b> - the customer paid the
 * whole amount and the document says so.
 * <p>
 * It applies to all four documents, in whichever direction their cash goes: a wallet charges for
 * a refund sent as much as for a payment received, and the fee is money out either way.
 * <p>
 * A seam rather than a direct call so {@code InvoiceSaveServiceTest} runs with {@link #none()} and
 * without a database, the way it runs with {@code ShiftGate.disabled()}.
 */
@FunctionalInterface
public interface InvoiceWalletFee {

    /**
     * @param previous what the document held in cash before this save, or {@code null} for a new one
     */
    void sync(DocumentType type, int documentNumber, int treasuryId, BigDecimal feePercent, LocalDate date,
              BigDecimal paid, ShiftCashEffect previous, OptionalInt shiftId, String correctionReason)
            throws DaoException;

    static InvoiceWalletFee none() {
        return (type, number, treasuryId, percent, date, paid, previous, shiftId, reason) -> { };
    }

    static InvoiceWalletFee jdbc() {
        WalletFeeService fees = new WalletFeeService();
        return (type, number, treasuryId, percent, date, paid, previous, shiftId, reason) ->
                fees.syncDocument(WalletFeeSource.document(type, number), treasuryId, date, paid,
                        previous == null ? null : new WalletFeeService.PreviousCash(
                                previous.income().add(previous.output()), previous.treasuryId()),
                        percent, shiftId, reason);
    }
}
