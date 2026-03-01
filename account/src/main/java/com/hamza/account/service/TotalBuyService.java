package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Total_buy;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

public record TotalBuyService(DaoFactory daoFactory) {

    public Total_buy getTotalBuyById(int id) throws DaoException {
//        return totalBuyList().stream().filter(totalBuy -> totalBuy.getId() == id).findFirst().orElse(null);
        return daoFactory.totalsPurchaseDao().getDataById(id);
    }

    public double sumTotal() throws DaoException {
        return getListByCurrentMonth().stream().mapToDouble(Total_buy::getTotal).sum();
    }

    public List<Total_buy> totalBuyByDate(String date) throws DaoException {
        return getListByCurrentMonth().stream().filter(totalBuy -> totalBuy.getDate().equals(date)).toList();
    }

    public List<Total_buy> getListByCurrentMonth() throws DaoException {
        var string = LocalDate.now().withDayOfMonth(1).toString();
        var string1 = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).toString();
//        System.out.println(string + " -  " + string1);
        return getTotalPurchaseByDateRange(string, string1);
    }

    public List<Total_buy> getTotalPurchaseByDateRange(String dateFrom, String dateTo) throws DaoException {
//        return totalBuyList().stream().filter(totalBuy -> !LocalDate.parse(totalBuy.getDate()).isBefore(LocalDate.parse(dateFrom)) &&
//                !LocalDate.parse(totalBuy.getDate()).isAfter(LocalDate.parse(dateTo))).toList();

        return daoFactory.totalsPurchaseDao().loadDataBetweenDate(dateFrom, dateTo);

    }

    public int deleteMultiData(Integer[] ids) throws DaoException {
        return daoFactory.totalsPurchaseDao().deleteInvoicesInRange(ids);
    }

    public List<Total_buy> getTotalBuyBySupId(int customer_id) throws DaoException {
        return daoFactory.totalsPurchaseDao().getTotalBuyBySupId(customer_id);
    }

    public List<Total_buy> getTotalBuyByYear(int year) throws DaoException {
        return daoFactory.totalsPurchaseDao().getTotalBuyByYear(year);
    }

    public List<Integer> getListYear() throws DaoException {
        return daoFactory.totalsPurchaseDao().getListYear();
    }
}
