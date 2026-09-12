package com.hamza.account.features.employee;

/**
 * Which employees a list keeps: everyone, only those still working, or only those who have
 * left.
 * <p>
 * <b>{@link #ALL} is not a convenience, it is the way back.</b> A stopped employee is marked
 * on the list rather than hidden from it, because the employees screen is the only place one
 * is switched back on - hiding them there would make the flag a one-way door. The same
 * decision, for the same reason, as {@code PartyTableSpec.PartySearchScope} on the parties
 * list.
 */
public enum EmployeeState {

    /** Everyone. What the screen opens on. */
    ALL(null),

    /** Still working here. */
    ACTIVE(Boolean.TRUE),

    /** Left, or stopped. Their history stays on every invoice and every expense. */
    INACTIVE(Boolean.FALSE);

    private final Boolean active;

    EmployeeState(Boolean active) {
        this.active = active;
    }

    /** {@code null} when the state adds no condition at all. */
    public Boolean active() {
        return active;
    }
}
