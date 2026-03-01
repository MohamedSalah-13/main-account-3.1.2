package com.hamza.account.interfaces.implReportTotals;

import com.hamza.account.interfaces.FilterDateInterface;
import com.hamza.account.interfaces.ReportTotalsInterface;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.dateTime.DateUtils;
import javafx.collections.ObservableList;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

@Log4j2
public class SearchByDay<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount> implements ReportTotalsInterface<T2> {

    private final DataInterface<T1, T2, T3, T4> dataInterface;
    private final FilterDateInterface<T2> filterDateInterface;
    private final TotalsDataInterface<T2> totalsDataInterface;
    private int year;


    public SearchByDay(DataInterface<T1, T2, T3, T4> dataInterface, FilterDateInterface<T2> filterDateInterface) {
        this.dataInterface = dataInterface;
        this.filterDateInterface = filterDateInterface;
        this.totalsDataInterface = dataInterface.totalDesignInterface().totalsDataInterface();

        try {
            List<String> totalBuyList = list()
                    .stream().map(totalsDataInterface.getDateFunction()).toList();
            ObservableList<Integer> years = DateUtils.getDistinctYears(totalBuyList);
            this.year = Collections.max(years);
        } catch (DaoException e) {
            AllAlerts.alertError(e.getMessage());
        }
    }

    @NotNull
    @Override
    public String title() {
        return "تقارير " + dataInterface.designInterface().nameTextOfInvoice() + " باليوم خلال السنة ";
    }

    @Override
    public void getYear(int year) {
        this.year = year;
    }

    @Override
    public List<T2> list() throws DaoException {
        try {
            return dataInterface.totalDesignInterface().dataList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ToDoubleFunction<T2> total() {
        return totalsDataInterface.getTotalToDoubleFunction();
    }

    @Override
    public double sumColumn(int month, int year, Object o) throws DaoException {
        int day = (int) o;
        Predicate<T2> byMonth = filterDateInterface.predicateByMonth(month);
        Predicate<T2> byDay = filterDateInterface.predicateByDay(day);
        return month == 0 ? getSum(getByYear().and(byDay)) : getSum(getByYear().and(byDay).and(byMonth));
    }

    @Override
    public double sumColumnTotal(int month) throws DaoException {
        Predicate<T2> byMonth = filterDateInterface.predicateByMonth(month);
        return getSum(getByYear().and(byMonth));
    }

    @Override
    public List<Integer> listByYearOrDay() {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 31; i++) {
            list.add(i + 1);
        }
        return list;
    }

    private Predicate<T2> getByYear() {
        return this.filterDateInterface.predicateByYear(year);
    }
}
