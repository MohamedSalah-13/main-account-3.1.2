package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Total_Buy_Re;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

public record TotalBuyReturnService(DaoFactory daoFactory) {


    public List<Total_Buy_Re> getTotalBuyByDate(String date) throws DaoException {
        return getListByCurrentMonth().stream().filter(totalBuy -> totalBuy.getDate().equals(date)).toList();
    }

    public List<Total_Buy_Re> getListByCurrentMonth() throws DaoException {
        var string = LocalDate.now().withDayOfMonth(1).toString();
        var string1 = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).toString();
//        System.out.println(string + " -  " + string1);
        return getTotalBuyReturnsByDateRange(string, string1);
    }

    public List<Total_Buy_Re> getTotalBuyReturnsByDateRange(String dateFrom, String dateTo) throws DaoException {
//        return getTotalBuyReturns().stream().filter(totalBuy -> !LocalDate.parse(totalBuy.getDate()).isBefore(LocalDate.parse(dateFrom)) &&
//                !LocalDate.parse(totalBuy.getDate()).isAfter(LocalDate.parse(dateTo))).toList();

        return daoFactory.totalsBuyReturnDao().loadDataBetweenDate(dateFrom, dateTo);

    }

    public int deleteMultiData(Integer[] ids) throws DaoException {
        return daoFactory.totalsBuyReturnDao().deleteInvoicesInRange(ids);
    }

    public List<Total_Buy_Re> getTotalBuyBySupId(int customer_id) throws DaoException {
        return daoFactory.totalsBuyReturnDao().getTotalBuyBySupId(customer_id);
    }

    public List<Total_Buy_Re> getTotalBuyByYear(int year) throws DaoException {
        return daoFactory.totalsBuyReturnDao().getTotalBuyByYear(year);
    }
}
