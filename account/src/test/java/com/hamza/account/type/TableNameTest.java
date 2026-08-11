package com.hamza.account.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the {@code information} column on an account movement is allowed to say.
 * <p>
 * The two ledgers read it into {@link TableName#getTableNameById} and then called a
 * method on the answer - one of them through {@code requireNonNull} and the other not -
 * so a value outside 1..4 came back as a {@code NullPointerException} from inside a row
 * mapper, naming neither the column nor the value.
 */
class TableNameTest {

    @ParameterizedTest
    @EnumSource(TableName.class)
    void everyKindIsFoundByItsOwnId(TableName kind) {
        assertSame(kind, TableName.getTableNameById(kind.getId()));
        assertSame(kind, TableName.requireById(kind.getId()));
    }

    @ParameterizedTest(name = "information = {0} is not a kind")
    @ValueSource(ints = {0, 5, -1, 99})
    @DisplayName("An id no kind carries is refused, with the value in the message")
    void anUnknownIdIsRefused(int id) {
        assertNull(TableName.getTableNameById(id));
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> TableName.requireById(id));
        assertTrue(refused.getMessage().contains(String.valueOf(id)), refused.getMessage());
    }

    /** The four ids are the ones the schema's {@code information} column stores. */
    @Test
    void theIdsAreOneToFour() {
        assertEquals(1, TableName.NAMES.getId());
        assertEquals(2, TableName.ACCOUNTS.getId());
        assertEquals(3, TableName.TOTALS.getId());
        assertEquals(4, TableName.RETURNS.getId());
    }
}
