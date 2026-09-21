package com.hamza.account.features.invoice;

import com.hamza.account.document.DocumentType;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Database boundary used by the transactional invoice stock guard. */
public interface InvoiceStockRepository {

    List<StoredLine> originalLinesForUpdate(DocumentType type, int documentId)
            throws DaoException;

    Map<Integer, String> lockItems(List<Integer> itemIds) throws DaoException;

    default Map<Integer, String> lockItems(int stockId, List<Integer> itemIds) throws DaoException {
        return lockItems(itemIds);
    }

    Map<Integer, Double> currentBaseBalances(List<Integer> itemIds)
        throws DaoException;

    default Map<Integer, Double> currentBaseBalances(int stockId, List<Integer> itemIds) throws DaoException {
        return currentBaseBalances(itemIds);
    }

    Map<BatchKey, Double> currentExpiryBalances(List<Integer> itemIds)
        throws DaoException;

    default Map<BatchKey, Double> currentExpiryBalances(int stockId, List<Integer> itemIds) throws DaoException {
        return currentExpiryBalances(itemIds);
    }

    /**
     * The warehouse's name when it is switched off (V77), empty when it is in use. Defaults to "in
     * use" so a repository that predates the column - the guard's test doubles - answers as every
     * warehouse did before it.
     */
    default java.util.Optional<String> inactiveStockName(int stockId) throws DaoException {
        return java.util.Optional.empty();
    }

    record StoredLine(int itemId, double baseQuantity, LocalDate expirationDate) {
    }

    record BatchKey(int itemId, LocalDate expirationDate) {
    }
}
