package com.hamza.account.service;

import com.hamza.account.document.DocumentType;
import com.hamza.account.features.returns.ReturnLinkGuard;
import com.hamza.account.features.stockledger.StockMovementAssembler;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.period.PeriodLock;
import com.hamza.account.period.PeriodLockRegistry;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.document.TotalsSearchCriteria;
import com.hamza.account.model.dao.TotalsSalesDao;
import com.hamza.account.model.domain.Total_Sales;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.account.features.shift.ShiftDocumentDeletionJournal;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.List;

public record TotalSalesService(DaoFactory daoFactory) {

    public List<Total_Sales> getListByCurrentMonth() throws DaoException {
        var string = LocalDate.now().withDayOfMonth(1).toString();
        var string1 = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).toString();
//        System.out.println(string + " -  " + string1);
        return getTotalSalesByDateRange(string, string1);
    }

    public List<Total_Sales> getTotalSalesByDateRange(String startDate, String endDate) throws DaoException {
        return getTotalsSalesDao().loadDataBetweenDate(startDate, endDate);
    }

    public int getMaxId() throws DaoException {
        return getTotalsSalesDao().getMaxId();
    }

    @NotNull
    private TotalsSalesDao getTotalsSalesDao() {
        return daoFactory.totalsSalesDao();
    }

    /**
     * Deletes invoices, refusing the batch whole if any of them falls inside a closed
     * accounting period.
     * <p>
     * The dates come from the stored rows, not from the caller, and the oldest decides -
     * so a selection reaching back into a closed month is refused rather than
     * half-deleted.
     */
    public int deleteMultiData(Integer[] ids) throws DaoException {
        return deleteMultiData(ids, null);
    }

    public int deleteMultiData(Integer[] ids, String correctionReason) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SALES_DELETE);
        PeriodLock.require(PeriodLockRegistry.SALES_INVOICE, List.of(ids));
        // Before anything is removed: a sale that has been returned against cannot go,
        // or its return's stock-in is left with nothing to reverse and every returned
        // item gains the returned quantity out of nothing.
        ReturnLinkGuard.requireNoReturns(DocumentType.SALES, ids);
        return TransactionTemplate.execute(() -> {
            var journal = new ShiftDocumentDeletionJournal(daoFactory).capture(DocumentType.SALES, ids);
            daoFactory.stockMovementDao().deleteByReferences(
                    StockMovementAssembler.referenceTypeFor(DocumentType.SALES), ids);
            int rows = getTotalsSalesDao().deleteInvoicesInRange(ids);
            journal.appendReversals(rows, correctionReason);
            return rows;
        });
    }

    public int deleteById(int id) throws DaoException {
        return deleteMultiData(new Integer[]{id});
    }

    public List<Total_Sales> getTotalSalesByCustomerId(int customer_id) throws DaoException {
        return getTotalsSalesDao().getTotalSalesByCustomerId(customer_id);
    }

    public List<Total_Sales> searchTotals(TotalsSearchCriteria criteria) throws DaoException {
        return getTotalsSalesDao().searchTotals(criteria);
    }

}
