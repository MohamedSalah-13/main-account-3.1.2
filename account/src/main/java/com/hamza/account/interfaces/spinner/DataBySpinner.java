package com.hamza.account.interfaces.spinner;

import com.hamza.account.interfaces.FilterDateInterface;
import com.hamza.account.interfaces.api.DataInterface;
import com.hamza.account.interfaces.api.TotalDesignInterface;
import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.controlsfx.database.DaoException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import static com.hamza.controlsfx.others.MonthsWithArabic.retrieveArabicMonths;

/**
 * @param <T1> for purchase or sales or purchase return or sales return
 * @param <T2> for Totals (purchase or sales or purchase return or sales return)
 * @param <T3> for Names (Customers or Suppliers)
 * @param <T4> for Accounts (Customers or Suppliers)
 */
@Log4j2
public class DataBySpinner<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount> {

    private final List<T3> nameList;
    private final TotalDesignInterface<T2> totalDesignInterface;
    private final FilterDateInterface<T2> filterDateInterface;
    private final SpinnerInterface<T3, T4> spinnerInterface;
    private final List<T2> totalPurchaseList;

    public DataBySpinner(DataInterface<T1, T2, T3, T4> dataInterface) throws Exception {
        this.nameList = dataInterface.nameAndAccountInterface().nameList();
        this.totalDesignInterface = dataInterface.totalDesignInterface();
        this.filterDateInterface = dataInterface.filterDateInterface();
        this.spinnerInterface = dataInterface.spinnerInterface();
        this.totalPurchaseList = totalDesignInterface.dataList();
    }

    public ObservableList<XYChart.Series<String, Number>> chartObservableList(int year) {
        List<T4> top5Accounts = getTopNAccounts(getTotalBuyList(), year);
        ObservableList<XYChart.Series<String, Number>> seriesList = FXCollections.observableArrayList();
        List<String> arabicMonths = retrieveArabicMonths();

        for (T4 account : top5Accounts) {
            XYChart.Series<String, Number> series = createSeriesForAccount(account, year, arabicMonths);
            seriesList.add(series);
        }
        return seriesList;
    }

    private XYChart.Series<String, Number> createSeriesForAccount(T4 account, int year, List<String> months) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        T3 name = spinnerInterface.objectName(account);
        series.setName(spinnerInterface.nameString(name));

        for (int i = 0; i < months.size(); i++) {
            double monthlySum = sumByMonth(spinnerInterface.nameId(name), i + 1, year);
            series.getData().add(new XYChart.Data<>(months.get(i), monthlySum));
        }

        return series;
    }

    private List<T2> getTotalBuyList() {
        try {
            return totalDesignInterface.dataList();
        } catch (DaoException e) {
            log.error(e.getMessage(), e.getCause());
            return Collections.emptyList();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<T4> getTopNAccounts(List<T2> totalBuyList, int year) {
        Predicate<T2> yearPredicate = filterDateInterface.predicateByYear(year);
        List<T4> accounts = new ArrayList<>();

        for (T3 name : nameList) {
            double totalAmount = calculateTotalAmountForName(totalBuyList, name, yearPredicate);
            accounts.add(spinnerInterface.objectAccount(name, totalAmount));
        }

        accounts.sort(Comparator.comparing(spinnerInterface.getAmount()).reversed());
        return accounts.stream().limit(5).toList();
    }

    private double calculateTotalAmountForName(List<T2> totalBuyList, T3 name, Predicate<T2> yearPredicate) {
        return totalBuyList.stream()
                .filter(totalDesignInterface.filterById(spinnerInterface.nameId(name)).and(yearPredicate))
                .mapToDouble(BaseTotals::getTotal_after_discount)
                .sum();
    }

    private double sumByMonth(int id, int month, int year) {
        Predicate<T2> monthPredicate = filterDateInterface.predicateByMonth(month);
        Predicate<T2> yearPredicate = filterDateInterface.predicateByYear(year);

        return totalPurchaseList.stream()
                .filter(totalDesignInterface.filterById(id).and(monthPredicate).and(yearPredicate))
                .mapToDouble(BaseTotals::getTotal_after_discount)
                .sum();
    }
}