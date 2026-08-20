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

    PermissionKey updateAccounts();

    PermissionKey deleteAccounts();

    PermissionKey showNames();

    PermissionKey createNames();

    PermissionKey updateNames();

    PermissionKey deleteNames();
}
