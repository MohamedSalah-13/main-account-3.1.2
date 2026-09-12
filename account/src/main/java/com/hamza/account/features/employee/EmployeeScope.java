package com.hamza.account.features.employee;

/**
 * Whether a lookup answers with everybody or only with those still working.
 * <p>
 * <b>There is deliberately no no-argument form.</b> The two callers want opposite answers and
 * a default would silently give one of them the other's: an invoice being written must offer
 * only the delegates still working, while an invoice being <em>re-opened</em> has to be able
 * to show the delegate it was written with, whoever that was. That is
 * {@code PartyTableSpec.PartySearchScope}'s rule, and it was learned there the expensive way -
 * applying "a stopped party leaves the combos" literally would have made an old debt
 * uncollectable.
 */
public enum EmployeeScope {

    /** Only those still working here: every combo a new document is written from. */
    ACTIVE_ONLY,

    /** Everyone, stopped included: re-opening a saved document, and the employees screen. */
    EVERYONE
}
