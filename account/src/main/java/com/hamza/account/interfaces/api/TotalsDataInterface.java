package com.hamza.account.interfaces.api;


import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.domain.Employees;

import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;


public interface TotalsDataInterface<T extends BaseTotals> {

    default int getNum(T t) {
        return t.getId();
    }

    default LocalDateTime getDateInsert(T t2) {
        return t2.getCreated_at() == null ? LocalDateTime.now() : t2.getCreated_at();
    }

    default Function<T, String> getDateFunction() {
        return T::getDate;
    }

    default ToDoubleFunction<T> getTotalToDoubleFunction() {
        return T::getTotal;
    }

    default Employees getDelegateData(T t2) {
        return new Employees();
    }

    default boolean selected(T t2) {
        return t2.isSelectedRow();
    }

    int getIdData(T t2);

    String getNameData(T t2);

    default ToDoubleFunction<T> getTotalProfit() {
        return t -> 0;
    }
}
