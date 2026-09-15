package com.hamza.account.features.documentdelete;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.InvoiceSaved;
import com.hamza.account.features.returns.ReturnLinkGuard;
import com.hamza.account.features.shift.ShiftDocumentDeletionJournal;
import com.hamza.account.features.stockledger.StockMovementAssembler;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.period.PeriodLock;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

/** {@link DocumentDeletionRepository} over the four document tables. */
final class JdbcDocumentDeletionRepository implements DocumentDeletionRepository {

    private final DaoFactory daoFactory;

    JdbcDocumentDeletionRepository(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    @Override
    public void requirePeriodOpen(DocumentType type, List<Integer> ids) throws DaoException {
        PeriodLock.require(type.periodLock(), ids);
    }

    @Override
    public void requireNoReturns(DocumentType type, List<Integer> ids) throws DaoException {
        // A return has nothing returned against it, and the guard answers for that itself.
        ReturnLinkGuard.requireNoReturns(type, array(ids));
    }

    @Override
    public int deleteDocuments(DocumentType type, List<Integer> ids, String correctionReason)
            throws DaoException {
        Integer[] numbers = array(ids);
        // Read before anything goes: the reversal is of what the document held in cash.
        var journal = new ShiftDocumentDeletionJournal(daoFactory).capture(type, numbers);
        daoFactory.stockMovementDao().deleteByReferences(
                StockMovementAssembler.referenceTypeFor(type), numbers);
        int rows = deleteHeaders(type, numbers);
        journal.appendReversals(rows, correctionReason);
        if (rows > 0) {
            ChangeAnnouncer.jdbc().announce(new InvoiceSaved(type.side()));
        }
        return rows;
    }

    private int deleteHeaders(DocumentType type, Integer[] numbers) throws DaoException {
        return switch (type) {
            case SALES -> daoFactory.totalsSalesDao().deleteInvoicesInRange(numbers);
            case SALES_RETURN -> daoFactory.totalsSalesReturnDao().deleteInvoicesInRange(numbers);
            case PURCHASE -> daoFactory.totalsPurchaseDao().deleteInvoicesInRange(numbers);
            case PURCHASE_RETURN -> daoFactory.totalsBuyReturnDao().deleteInvoicesInRange(numbers);
        };
    }

    private static Integer[] array(List<Integer> ids) {
        return ids.toArray(Integer[]::new);
    }
}
