package com.hamza.account.features.party;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The one reading of "the customers this delegate follows" - {@link CustomerDelegateCondition}. */
class CustomerDelegateConditionTest {

    @Test
    @DisplayName("no delegate asked about writes nothing and binds nothing")
    void everyone() {
        assertEquals("", CustomerDelegateCondition.sql(null));
        assertEquals(List.of(), CustomerDelegateCondition.values(null));
    }

    @Test
    @DisplayName("one delegate is the customer's default, bound")
    void oneDelegate() {
        assertEquals("\n  AND p.default_delegate_id = ?", CustomerDelegateCondition.sql(7));
        assertEquals(List.of(7), CustomerDelegateCondition.values(7));
    }

    /**
     * The column has no foreign key (V56), so a default can name an employee since deleted, or one who
     * is no longer a delegate - and the customer's screen shows nobody for either. "No delegate" answers
     * the way that screen does, so the delegates and "no delegate" together reach every customer once.
     */
    @Test
    @DisplayName("no delegate is no delegate behind the default, not a stored zero")
    void nobody() {
        String sql = CustomerDelegateCondition.sql(CustomerDelegateCondition.NO_DELEGATE);
        assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM employees e JOIN jobs j ON j.id = e.job WHERE e.id = p.default_delegate_id AND j.is_delegate = 1)"), sql);
        assertEquals(List.of(), CustomerDelegateCondition.values(CustomerDelegateCondition.NO_DELEGATE));
    }

    @Test
    @DisplayName("only a customer has a delegate who follows them")
    void onlyACustomer() {
        assertThrows(IllegalArgumentException.class, () -> CustomerDelegateCondition.requireCustomer(false, 7));
        assertThrows(IllegalArgumentException.class, () -> CustomerDelegateCondition.requireCustomer(true, -1));
        assertDoesNotThrow(() -> CustomerDelegateCondition.requireCustomer(false, null));
        assertDoesNotThrow(() -> CustomerDelegateCondition.requireCustomer(true, CustomerDelegateCondition.NO_DELEGATE));
    }
}
