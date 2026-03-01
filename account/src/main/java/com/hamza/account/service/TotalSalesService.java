package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Total_Sales;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.others.ThisLocalizedWeek;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public record TotalSalesService(DaoFactory daoFactory) {

    public Total_Sales getTotalSalesById(int id) throws DaoException {
        return daoFactory.totalsSalesDao().getDataById(id);
//        return totalSalesList().stream().filter(totalSales -> totalSales.getId() == id).findFirst().orElse(null);
    }

    public double sumTotal() throws DaoException {
        return getListByCurrentMonth().stream().mapToDouble(Total_Sales::getTotal).sum();
    }

    public double sumTotalByDay() throws DaoException {
        return getListByCurrentMonth().stream().filter(totalSales -> totalSales.getDate().equals(LocalDate.now().toString())).mapToDouble(Total_Sales::getTotal).sum();
    }

    public double sumPreviousDay() throws DaoException {
        return getListByCurrentMonth().stream().filter(totalSales -> totalSales.getDate().equals(LocalDate.now().minusDays(1).toString())).mapToDouble(Total_Sales::getTotal).sum();
    }

    public double sumMonth() throws DaoException {
        Predicate<Total_Sales> totalSalesPredicate = totalSales -> LocalDate.parse(totalSales.getDate()).getMonth() == LocalDate.now().getMonth()
                && LocalDate.parse(totalSales.getDate()).getYear() == LocalDate.now().getYear();
        return getListByCurrentMonth().stream().filter(totalSalesPredicate).mapToDouble(Total_Sales::getTotal).sum();
    }

    @SuppressWarnings("deprecation")
    public double sumWeek() throws DaoException {
        ThisLocalizedWeek frenchWeek = new ThisLocalizedWeek(new Locale("ar", "EG"));
        Predicate<Total_Sales> totalSalesWeek = totalSales -> (LocalDate.parse(totalSales.getDate()).isEqual(frenchWeek.getFirstDay()) || LocalDate.parse(totalSales.getDate()).isAfter(frenchWeek.getFirstDay()))
                && (LocalDate.parse(totalSales.getDate()).isEqual(frenchWeek.getLastDay()) || LocalDate.parse(totalSales.getDate()).isBefore(frenchWeek.getLastDay()));
        return getListByCurrentMonth().stream().filter(totalSalesWeek).mapToDouble(Total_Sales::getTotal).sum();
    }

    public List<Total_Sales> getListByCurrentMonth() throws DaoException {
        var string = LocalDate.now().withDayOfMonth(1).toString();
        var string1 = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).toString();
//        System.out.println(string + " -  " + string1);
        return getTotalSalesByDateRange(string, string1);
    }

    public List<Total_Sales> getTotalSalesByDateRange(String startDate, String endDate) throws DaoException {
        return daoFactory.totalsSalesDao().loadDataBetweenDate(startDate, endDate);

//        return totalSalesList().stream().filter(totalSales -> !LocalDate.parse(totalSales.getDate()).isBefore(LocalDate.parse(startDate)) &&
//                !LocalDate.parse(totalSales.getDate()).isAfter(LocalDate.parse(endDate))).toList();

    }

    public Total_Sales getMaxId() throws DaoException {
        return daoFactory.totalsSalesDao().getMaxId();
//        return totalSalesList().stream().max(Comparator.comparing(Total_Sales::getId)).orElse(null);
    }

    public int deleteMultiData(Integer[] ids) throws DaoException {
        return daoFactory.totalsSalesDao().deleteInvoicesInRange(ids);
    }

    public List<Total_Sales> getTotalSalesByCustomerId(int customer_id) throws DaoException {
        return daoFactory.totalsSalesDao().getTotalSalesByCustomerId(customer_id);
    }

    public List<Total_Sales> getTotalSalesByYear(int year) throws DaoException {
        return daoFactory.totalsSalesDao().getTotalSalesByYear(year);
    }
}
