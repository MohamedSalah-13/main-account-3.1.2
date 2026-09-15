package com.hamza.account.features.documentdelete;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentDeletionResultTest {

    @Test
    void tellsAWholeDeleteFromAPartialOneAndFromNone() {
        assertTrue(new DocumentDeletionResult(3, 3).allDeleted());
        assertFalse(new DocumentDeletionResult(3, 1).allDeleted());
        assertFalse(new DocumentDeletionResult(3, 1).nothingDeleted());
        assertTrue(new DocumentDeletionResult(3, 0).nothingDeleted());
    }

    @Test
    void refusesMoreDeletedThanAskedFor() {
        assertThrows(IllegalArgumentException.class, () -> new DocumentDeletionResult(1, 2));
        assertThrows(IllegalArgumentException.class, () -> new DocumentDeletionResult(-1, 0));
    }
}
