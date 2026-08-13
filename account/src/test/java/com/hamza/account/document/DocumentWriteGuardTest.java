package com.hamza.account.document;

import com.hamza.controlsfx.database.DaoException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class DocumentWriteGuardTest {

    @ParameterizedTest
    @EnumSource(DocumentType.class)
    void acceptsExactlyOneHeaderRow(DocumentType type) {
        assertDoesNotThrow(() -> DocumentWriteGuard.requireSingleHeaderRow(1, type));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, -1})
    void rejectsMissingOrAmbiguousHeaderWrites(int affectedRows) {
        DaoException error = assertThrows(DaoException.class,
                () -> DocumentWriteGuard.requireSingleHeaderRow(
                        affectedRows, DocumentType.SALES));
        assertTrue(error.getMessage().contains("رأس فاتورة واحد"));
    }
}
