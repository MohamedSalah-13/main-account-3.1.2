package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.perm.PermissionGuard;
import com.hamza.account.type.UserPermissionType;
import com.hamza.account.model.dao.TotalsSalesDao;
import com.hamza.account.model.domain.Total_Sales;
import com.hamza.controlsfx.database.DaoException;
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

    public int deleteMultiData(Integer[] ids) throws DaoException {
        PermissionGuard.require(UserPermissionType.SALES_DELETE);
        return getTotalsSalesDao().deleteInvoicesInRange(ids);
    }

    public List<Total_Sales> getTotalSalesByCustomerId(int customer_id) throws DaoException {
        return getTotalsSalesDao().getTotalSalesByCustomerId(customer_id);
    }

}
