package com.hamza.account.features.currency.difference;

import com.hamza.account.features.party.statement.PartyMovementKind;
import com.hamza.account.features.treasury.statement.TreasuryMovementKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One movement of a foreign account, as its own statement shows it: what it moved in the account's currency
 * and what it moved in the base, both in the balance's direction.
 *
 * @param source    the row's kind as its view codes it: {@code treasury_balance.source_type} for a treasury,
 *                  the ledger view's {@code information} for a party
 * @param reference the document or movement number on the statement
 * @param own       what the balance moved by in the account's currency
 * @param book      what its book value moved by, in the base
 */
public record ExchangeMovement(ExchangeAccountKind kind, int accountId, LocalDate date, int source,
                               long reference, BigDecimal own, BigDecimal book) {

    public ExchangeMovement {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(date, "date");
        own = own == null ? BigDecimal.ZERO : own;
        book = book == null ? BigDecimal.ZERO : book;
    }

    /** What the statement calls this movement - the same key its own statement shows. */
    public String labelKey() {
        return kind == ExchangeAccountKind.TREASURY
                ? TreasuryMovementKind.fromCode(source).labelKey()
                : PartyMovementKind.fromCode(source).messageKey();
    }
}
