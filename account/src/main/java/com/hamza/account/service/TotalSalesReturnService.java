package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.TotalsSalesReturnDao;
import com.hamza.account.model.domain.Total_Sales_Re;
import com.hamza.controlsfx.database.DaoException;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.List;

public record TotalSalesReturnService(DaoFactory daoFactory) {

    public List<Total_Sales_Re> getListByCurrentMonth() throws DaoException {
        var string = LocalDate.now().withDayOfMonth(1).toString();
        var string1 = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).toString();
//        System.out.println(string + " -  " + string1);
        return getTotalSalesByDateRange(string, string1);
    }

    public List<Total_Sales_Re> getTotalSalesByDateRange(String dateFrom, String dateTo) throws DaoException {
//        return LoadDataAndList.getTotalSalesReturn().stream().filter(totalSales -> !LocalDate.parse(totalSales.getDate()).isBefore(LocalDate.parse(dateFrom)) &&
//                !LocalDate.parse(totalSales.getDate()).isAfter(LocalDate.parse(dateTo))).toList();
        return getTotalsSalesReturnDao().loadDataBetweenDate(dateFrom, dateTo);

    }

    public int deleteMultiData(Integer[] ids) throws DaoException {
        return getTotalsSalesReturnDao().deleteInvoicesInRange(ids);
    }

    public List<Total_Sales_Re> getTotalSalesByCustomerId(int customer_id) throws DaoException {
        return getTotalsSalesReturnDao().getTotalSalesByCustomerId(customer_id);
    }

    public List<Total_Sales_Re> getTotalSalesByYear(int year) throws DaoException {
        return getTotalsSalesReturnDao().getTotalSalesByYear(year);
    }

    @NotNull
    private TotalsSalesReturnDao getTotalsSalesReturnDao() {
        return daoFactory.totalsSalesReturnDao();
    }

    public int getMaxId() {
        return getTotalsSalesReturnDao().getMaxId();
    }
}
