package com.hamza.account.interfaces.api;


import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.domain.Employees;

import java.time.LocalDateTime;
import java.util.function.ToDoubleFunction;


public interface TotalsDataInterface<T extends BaseTotals> {

    default LocalDateTime getDateInsert(T t2) {
        return t2.getCreated_at() == null ? LocalDateTime.now() : t2.getCreated_at();
    }

    default Employees getDelegateData(T t2) {
        return new Employees();
    }

    /**
     * The invoice a saved return reverses, or {@code 0}. Zero for the two invoice
     * families, which reverse nothing - the same shape {@link #getDelegateData} uses to
     * answer a question only the sales side has.
     */
    default int getSourceInvoiceNumber(T t2) {
        return 0;
    }

    /** The stored {@code return_reason} of a saved return, or {@code null}. */
    default String getReturnReason(T t2) {
        return null;
    }

    int getIdData(T t2);

    String getNameData(T t2);

    default ToDoubleFunction<T> getTotalProfit() {
        return t -> 0;
    }
}
