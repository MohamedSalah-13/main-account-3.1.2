package com.hamza.account.features.documentdelete;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.document.DocumentType;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.database.DaoException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Deletes documents of one family - one or many, on one path - for all four families.
 * <p>
 * It replaces four {@code deleteMultiData} methods that were the same method four times,
 * differing only in the permission, the period lock and the table, every one of which
 * {@link DocumentType} already declares. Four copies is four places for the next rule to be
 * added to three of.
 * <p>
 * <b>The order is the point.</b>
 * <ol>
 *   <li>The permission, before anything is read.</li>
 *   <li>The period lock and the returns, before the backup. The screen used to take the
 *       {@code before-delete_} backup first, so a delete the rules were about to refuse still
 *       cost a full dump - and counted towards the thirty copies of that kind retention keeps,
 *       pushing out one taken before a delete that really happened.</li>
 *   <li>The backup, before anything is removed. If it fails, nothing is.</li>
 *   <li>The same checks again inside the transaction, then the delete. The backup takes
 *       seconds, which is long enough for another till to save a return against one of these
 *       invoices or close the period, and a check made before that pause answers for the moment
 *       it was made.</li>
 * </ol>
 * The result says how many were asked for and how many went, because they differ when another
 * machine has already deleted some of them - and "deleted" over nothing is a false answer.
 */
public final class DocumentDeletionService {

    private final DocumentDeletionRepository repository;
    private final DocumentDeletionTransaction transaction;
    private final BackupBeforeDelete backup;

    public DocumentDeletionService(DocumentDeletionRepository repository,
                                   DocumentDeletionTransaction transaction,
                                   BackupBeforeDelete backup) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.transaction = Objects.requireNonNull(transaction, "transaction");
        this.backup = Objects.requireNonNull(backup, "backup");
    }

    /** The application's wiring: the real tables, a real transaction, and the given backup. */
    public static DocumentDeletionService jdbc(DaoFactory daoFactory, BackupBeforeDelete backup) {
        return new DocumentDeletionService(new JdbcDocumentDeletionRepository(daoFactory),
                DocumentDeletionTransaction.jdbc(), backup);
    }

    /**
     * @param correctionReason recorded against every shift cash reversal the delete writes; may be
     *                         null only where no shift is involved
     */
    public DocumentDeletionResult delete(DocumentType type, List<Integer> ids, String correctionReason)
            throws DaoException {
        Objects.requireNonNull(type, "type");
        AuthorizationGuard.require(type.deletePermission());
        List<Integer> distinct = distinct(ids);
        if (distinct.isEmpty()) {
            return new DocumentDeletionResult(0, 0);
        }
        requireDeletable(type, distinct);
        takeBackup();
        int deleted = transaction.execute(() -> {
            requireDeletable(type, distinct);
            return repository.deleteDocuments(type, distinct, correctionReason);
        });
        return new DocumentDeletionResult(distinct.size(), deleted);
    }

    /**
     * What would go below zero on the shelf if these documents were deleted - empty for a family
     * that puts goods back rather than taking them (a sale, a purchase return), and empty when
     * nothing would. The screen asks before confirming; nothing here refuses, see
     * {@link DocumentDeleteStockCheck}.
     */
    public List<DocumentDeleteStockCheck.Shortfall> stockShortfalls(
            DocumentType type, List<Integer> ids) throws DaoException {
        Objects.requireNonNull(type, "type");
        List<Integer> distinct = distinct(ids);
        if (distinct.isEmpty() || type.stockSign() <= 0) {
            return List.of();
        }
        // Inside a transaction, because the balance is read through JdbcInvoiceStockRepository -
        // the one definition there is of it - and that class refuses to work outside one: its own
        // reads take FOR UPDATE row locks, which are meaningless with no transaction to hold them.
        // Asking it from the screen's thread without this turned a courtesy warning into a
        // reference-code error in front of somebody deleting an invoice.
        return transaction.execute(() ->
                DocumentDeleteStockCheck.shortfalls(repository.stockLinesOf(type, distinct)));
    }

    private void requireDeletable(DocumentType type, List<Integer> ids) throws DaoException {
        repository.requirePeriodOpen(type, ids);
        repository.requireNoReturns(type, ids);
    }

    private void takeBackup() throws DaoException {
        try {
            backup.take();
        } catch (DaoException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new DaoException("The backup taken before deleting documents failed; nothing was deleted", e);
        }
    }

    /** A ticked row and a focused row can name the same document; it is one document. */
    private static List<Integer> distinct(List<Integer> ids) {
        if (ids == null) {
            return List.of();
        }
        LinkedHashSet<Integer> unique = new LinkedHashSet<>();
        for (Integer id : ids) {
            unique.add(Objects.requireNonNull(id, "a document id"));
        }
        return List.copyOf(new ArrayList<>(unique));
    }
}
