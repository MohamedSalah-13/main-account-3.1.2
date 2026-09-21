package com.hamza.account.features.party;

import java.util.List;

/**
 * Narrows a list of customers to the ones a delegate follows - {@code custom.default_delegate_id}.
 * <p>
 * <b>This is derived from the customer's row as it is today, and here that is right.</b> The question
 * a delegate's balances and debt ageing answer is "who follows this customer now", which is exactly
 * what the default says. A commission is the opposite case: there the delegate is written onto the
 * invoice and the collection when they happen and is never derived, or moving a customer between
 * delegates would rewrite both delegates' history ({@code docs/delegates-plan.md}, ق-3).
 * <p>
 * <b>"No delegate" means no delegate stands behind the customer's default</b>, not merely a stored
 * zero. The column carries no foreign key on purpose (V56), so it can name an employee since deleted,
 * or one whose job is no longer a delegate's - and the customer's own screen shows nobody for either.
 * The filter's combo lists every delegate, stopped ones included ({@code EmployeeScope.EVERYONE}), so
 * those delegates and "no delegate" between them reach every customer exactly once: a customer whose
 * default names nobody a delegate would otherwise be under no choice at all, visible only under "all".
 * <p>
 * One class for both reports that ask - the balances list and the ageing report - so the two cannot
 * come to mean two things by one choice in two combos.
 */
public final class CustomerDelegateCondition {

    /** The value a filter carries for "customers no delegate follows". No employee has this id. */
    public static final int NO_DELEGATE = 0;

    /** No delegate behind the default: {@code jobs.is_delegate} is what makes an employee one. */
    static final String NOBODY = "\n  AND NOT EXISTS (SELECT 1 FROM employees e JOIN jobs j ON j.id = e.job"
            + " WHERE e.id = p.default_delegate_id AND j.is_delegate = 1)";

    private CustomerDelegateCondition() {
    }

    /**
     * The condition on a customer row aliased {@code p}, or an empty string when the filter asks about
     * no delegate at all. It begins with {@code AND}, to follow whatever condition precedes it.
     */
    public static String sql(Integer delegateId) {
        if (delegateId == null) {
            return "";
        }
        if (delegateId == NO_DELEGATE) {
            return NOBODY;
        }
        return "\n  AND p.default_delegate_id = ?";
    }

    /** What {@link #sql} binds, in its order. */
    public static List<Object> values(Integer delegateId) {
        return delegateId == null || delegateId == NO_DELEGATE ? List.of() : List.of(delegateId);
    }

    /** Only a customer has a default delegate: a supplier's row has no such column. */
    public static void requireCustomer(boolean isCustomer, Integer delegateId) {
        if (delegateId != null && !isCustomer) {
            throw new IllegalArgumentException("only a customer has a delegate who follows them");
        }
        if (delegateId != null && delegateId < 0) {
            throw new IllegalArgumentException("invalid delegate: " + delegateId);
        }
    }
}
