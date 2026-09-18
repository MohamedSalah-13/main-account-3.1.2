package com.hamza.account.features.treasury;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.shift.ShiftCashSource;

/**
 * The movement a wallet fee was paid for - what {@code expenses_details.fee_source_type} and
 * {@code fee_source_id} hold (V67).
 * <p>
 * The kind is a {@link ShiftCashSource}: the numbers the shift journal already files a movement
 * under, so a fee and the cash it was charged on are named one way. Seven kinds can carry one -
 * the four documents, the two party payments, and since V68 the outgoing half of a transfer
 * between treasuries. An expense, a deposit or the incoming half of a transfer is refused here,
 * which is what the CHECK in V68 refuses in the database.
 */
public record WalletFeeSource(ShiftCashSource kind, int id) {

    public WalletFeeSource {
        if (kind == null || !carriesFee(kind)) {
            throw new IllegalArgumentException(
                    "A wallet fee belongs to a document, a party payment or a transfer sent: " + kind);
        }
        if (id <= 0) {
            throw new IllegalArgumentException("A wallet fee needs the key of the movement it was paid for");
        }
    }

    private static boolean carriesFee(ShiftCashSource kind) {
        return switch (kind) {
            case PURCHASE, PURCHASE_RETURN, SALES, SALES_RETURN, CUSTOMER_ACCOUNT, SUPPLIER_ACCOUNT,
                 TRANSFER_OUT -> true;
            case EXPENSE, CASH_DEPOSIT, CASH_WITHDRAWAL, TRANSFER_IN -> false;
        };
    }

    /** The fee is the sending treasury's, so it is filed under the outgoing half. */
    public static WalletFeeSource transfer(int transferId) {
        return new WalletFeeSource(ShiftCashSource.TRANSFER_OUT, transferId);
    }

    public static WalletFeeSource party(PartyKind kind, int movementId) {
        return new WalletFeeSource(ShiftCashSource.party(kind), movementId);
    }

    public static WalletFeeSource document(DocumentType type, int documentNumber) {
        return new WalletFeeSource(ShiftCashSource.document(type), documentNumber);
    }
}
