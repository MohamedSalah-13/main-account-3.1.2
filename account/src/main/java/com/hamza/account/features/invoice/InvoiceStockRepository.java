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

    Map<Integer, Double> currentBaseBalances(List<Integer> itemIds)
            throws DaoException;

    Map<BatchKey, Double> currentExpiryBalances(List<Integer> itemIds)
            throws DaoException;

    record StoredLine(int itemId, double baseQuantity, LocalDate expirationDate) {
    }

    record BatchKey(int itemId, LocalDate expirationDate) {
    }
}
