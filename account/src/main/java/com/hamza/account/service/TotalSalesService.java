package com.hamza.account.service;

import com.hamza.account.database.DaoException;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.TotalsSalesDao;
import com.hamza.account.model.domain.Total_Sales;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.List;

public record TotalSalesService(DaoFactory daoFactory) {


    public List<Total_Sales> getListByCurrentMonth() throws DaoException {
        var string = LocalDate.now().withDayOfMonth(1).toString();
        var string1 = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).toString();
        return getTotalSalesByDateRange(string, string1);
    }

    public List<Total_Sales> getTotalSalesByDateRange(String startDate, String endDate) throws DaoException {
        return getTotalsSalesDao().loadDataBetweenDate(startDate, endDate);
    }

    public int getMaxId() {
        return getTotalsSalesDao().getMaxId();
    }

    @NotNull
    private TotalsSalesDao getTotalsSalesDao() {
        return daoFactory.totalsSalesDao();
    }

    public int deleteMultiData(Integer[] ids) throws DaoException {
        return getTotalsSalesDao().deleteInvoicesInRange(ids);
    }

    public List<Total_Sales> getTotalSalesByCustomerId(int customer_id) throws DaoException {
        return getTotalsSalesDao().getTotalSalesByCustomerId(customer_id);
    }

}
