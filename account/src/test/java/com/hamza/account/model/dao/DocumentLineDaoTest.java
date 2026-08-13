package com.hamza.account.model.dao;

import com.hamza.account.model.domain.Purchase;
import com.hamza.controlsfx.database.DaoException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentLineDaoTest {

    @Test
    void plansUpdatesInsertsAndDeletesWithoutReplacingRetainedLines() throws DaoException {
        Purchase retained = line(41, 700);
        Purchase added = line(0, 700);

        var plan = DocumentLineDao.plan(700, Set.of(41, 42), List.of(retained, added));

        assertEquals(List.of(retained), plan.updates());
        assertEquals(List.of(added), plan.inserts());
        assertEquals(Set.of(42), plan.deletes());
    }

    @Test
    void rejectsAStoredLineSubmittedTwice() {
        Purchase first = line(41, 700);
        Purchase duplicate = line(41, 700);

        assertThrows(DaoException.class,
                () -> DocumentLineDao.plan(700, Set.of(41), List.of(first, duplicate)));
    }

    @Test
    void rejectsAnIdentityThatDoesNotBelongToTheLockedDocument() {
        assertThrows(DaoException.class,
                () -> DocumentLineDao.plan(700, Set.of(41), List.of(line(99, 700))));
    }

    @Test
    void rejectsMovingALineToAnotherDocument() {
        assertThrows(DaoException.class,
                () -> DocumentLineDao.plan(700, Set.of(41), List.of(line(41, 701))));
    }

    @Test
    void rejectsAnInvoiceWithoutLines() {
        assertThrows(DaoException.class,
                () -> DocumentLineDao.plan(700, Set.of(41), List.of()));
    }

    private static Purchase line(int id, int documentId) {
        Purchase line = new Purchase();
        line.setId(id);
        line.setInvoiceNumber(documentId);
        return line;
    }
}
