package com.hamza.account.interfaces.implReportTotals;

import com.hamza.account.interfaces.FilterDateInterface;
import com.hamza.account.interfaces.ReportTotalsInterface;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.TotalsDataInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.dateTime.DateUtils;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

@Log4j2
public class SearchByYear<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount>
        implements ReportTotalsInterface<T2> {

    private final DataInterface<T1, T2, T3, T4> dataInterface;
    private final FilterDateInterface<T2> filterDateInterface;
    private final TotalsDataInterface<T2> totalsDataInterface;

    public SearchByYear(DataInterface<T1, T2, T3, T4> dataInterface, FilterDateInterface<T2> filterDateInterface) {
        this.dataInterface = dataInterface;
        this.filterDateInterface = filterDateInterface;
        this.totalsDataInterface = dataInterface.totalDesignInterface().totalsDataInterface();
    }

    @NotNull
    @Override
    public String title() {
        return "تقارير إجمالى " + dataInterface.designInterface().nameTextOfInvoice();
    }

    @Override
    public void getYear(int year) {

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
        int years = (int) o;
        Predicate<T2> byMonth = filterDateInterface.predicateByMonth(month);
        Predicate<T2> byYear = filterDateInterface.predicateByYear(years);

        if (month == 0) {
            return getSum(byYear);
        }
        return getSum(byYear.and(byMonth));
    }

    @Override
    public double sumColumnTotal(int month) throws DaoException {
        Predicate<T2> byMonth = filterDateInterface.predicateByMonth(month);
        return getSum(byMonth);
    }

    @Override
    public List<Integer> listByYearOrDay() {
        List<String> totalBuyList = new ArrayList<>();
        try {
            totalBuyList = list().stream().map(totalsDataInterface.getDateFunction()).toList();
        } catch (DaoException e) {
            log.error(e.getMessage());
        }
        return DateUtils.getDistinctYears(totalBuyList);
    }

}
