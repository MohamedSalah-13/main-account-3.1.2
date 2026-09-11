package com.hamza.account.perm;

import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.features.events.PartyKind;

public interface PermAccountAndNameInt {

    /**
     * Which set of keys guards the names and accounts screens. Whose they are is the
     * only thing that decides it, so it is answered here rather than once per
     * implementation - where the two return screens answered {@code null}, and only
     * the wiring in {@code MainItems} kept a return from ever reaching
     * {@code NameButtons} and throwing.
     */
    static PermAccountAndNameInt forParty(PartyKind partyKind) {
        return partyKind == PartyKind.CUSTOMER
                ? new PermCustomerAccountAndName()
                : new PermSuppliersAccountAndName();
    }

    PermissionKey showAccounts();

    /**
     * Recording a movement on the account - a collection, or a note.
     * <p>
     * Both keys have existed since {@code V1}; only the accessor was missing, so the one
     * screen that writes a movement guarded itself by naming
     * {@code AppPermissions.CUSTOMER_ACCOUNT_CREATE} directly and the supplier side had no
     * way to ask the same question through this interface at all.
     */
    PermissionKey createAccounts();

    PermissionKey updateAccounts();

    PermissionKey deleteAccounts();

    PermissionKey showNames();

    PermissionKey createNames();

    PermissionKey updateNames();

    PermissionKey deleteNames();
}
