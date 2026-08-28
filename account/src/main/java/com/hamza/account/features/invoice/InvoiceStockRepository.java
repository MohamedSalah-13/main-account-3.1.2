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

    record StoredLine(int itemId, double baseQuantity, LocalDate expirationDate) {
    }

    record BatchKey(int itemId, LocalDate expirationDate) {
    }
}
