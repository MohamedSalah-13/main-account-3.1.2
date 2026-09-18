package com.hamza.account.features.treasury;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.PartyKind;
import com.hamza.account.features.shift.ShiftCashSource;

/**
 * The movement a wallet fee was paid for - what {@code expenses_details.fee_source_type} and
 * {@code fee_source_id} hold (V67).
 * <p>
 * The kind is a {@link ShiftCashSource}: the same six numbers the shift journal already files a
 * document or a party payment under, so a fee and the cash it was charged on are named one way.
 * Only those six can carry a fee - an expense, a deposit or a transfer is refused here, which is
 * what the CHECK in V67 refuses in the database.
 */
public record WalletFeeSource(ShiftCashSource kind, int id) {

    public WalletFeeSource {
        if (kind == null || kind.code() < ShiftCashSource.PURCHASE.code()
                || kind.code() > ShiftCashSource.SUPPLIER_ACCOUNT.code()) {
            throw new IllegalArgumentException("A wallet fee belongs to a document or a party payment: " + kind);
        }
        if (id <= 0) {
            throw new IllegalArgumentException("A wallet fee needs the key of the movement it was paid for");
        }
    }

    public static WalletFeeSource party(PartyKind kind, int movementId) {
        return new WalletFeeSource(ShiftCashSource.party(kind), movementId);
    }

    public static WalletFeeSource document(DocumentType type, int documentNumber) {
        return new WalletFeeSource(ShiftCashSource.document(type), documentNumber);
    }
}
