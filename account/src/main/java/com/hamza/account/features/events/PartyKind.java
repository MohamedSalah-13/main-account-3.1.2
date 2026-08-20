package com.hamza.account.features.events;

import com.hamza.controlsfx.language.LanguageManager;

/**
 * Which side of the ledger a name belongs to: someone the business sells to, or
 * someone it buys from.
 * <p>
 * It is the axis the four name and account publishers were split along, one per
 * combination, and now selects between two events instead. The two i18n keys are
 * the other thing that only ever varied with this axis - {@code impl_design}'s four
 * classes agreed with each other in pairs on {@code nameTextOfData}/{@code
 * nameTextOfAccount} well before {@link com.hamza.account.document.DocumentType}
 * existed to say why.
 */
public enum PartyKind {
    CUSTOMER("customers", "cuAcc"),
    SUPPLIER("suppliers", "supAcc");

    private final String nameTextKey;
    private final String accountTextKey;

    PartyKind(String nameTextKey, String accountTextKey) {
        this.nameTextKey = nameTextKey;
        this.accountTextKey = accountTextKey;
    }

    /** What the names screen is called: customers or suppliers. */
    public String nameText() {
        return LanguageManager.getInstance().getString(nameTextKey);
    }

    /** What the account screen is called: a customer's account or a supplier's. */
    public String accountText() {
        return LanguageManager.getInstance().getString(accountTextKey);
    }
}
