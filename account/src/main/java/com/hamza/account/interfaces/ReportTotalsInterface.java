package com.hamza.account.interfaces;

import com.hamza.controlsfx.database.DaoException;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

public interface ReportTotalsInterface<T> {

    @NotNull String title();

    void getYear(int year);

    List<T> list() throws DaoException;

    ToDoubleFunction<T> total();

    default boolean searchByNames() {
        return false;
    }

    double sumColumn(int month, int year, Object o) throws DaoException;

    double sumColumnTotal(int month) throws DaoException;

    default double getSum(Predicate<T> tPredicate) throws DaoException {
        return roundToTwoDecimalPlaces(list().stream().filter(tPredicate).mapToDouble(total()).sum());
    }

    default List<Integer> listByYearOrDay() {
        return null;
    }

    default HashMap<Integer, String> mapNames() {
        return null;
    }

}
