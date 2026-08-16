package com.hamza.account.document;

import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.language.LanguageManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The refusal message resolves through {@link LanguageManager}, whose current language
 * is a machine-wide preference - pinned to Arabic here and restored afterward, rather
 * than assuming the developer's saved language.
 */
class DocumentWriteGuardTest {

    private static Locale previousLocale;

    @BeforeAll
    static void useArabic() {
        previousLocale = LanguageManager.getInstance().getCurrentLocale();
        LanguageManager.getInstance().setLocale(LanguageManager.ARABIC);
    }

    @AfterAll
    static void restoreLocale() {
        LanguageManager.getInstance().setLocale(previousLocale);
    }

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
