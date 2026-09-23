package com.hamza.account.features.currency.difference;

import java.util.Objects;

/**
 * A treasury, a customer or a supplier that deals in a currency other than the base - the only accounts a
 * rate can move. An account in the base has no difference by definition, and is not one of these.
 *
 * @param currencyId the account's own currency; never the base, which is written NULL on its row
 */
public record ExchangeAccount(ExchangeAccountKind kind, int id, String name, int currencyId) {

    public ExchangeAccount {
        Objects.requireNonNull(kind, "kind");
        name = name == null ? "" : name;
    }
}
