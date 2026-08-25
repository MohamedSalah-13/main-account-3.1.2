package com.hamza.account.interfaces.api;


import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.domain.Employees;

import java.time.LocalDateTime;
import java.util.function.ToDoubleFunction;


/**
 * What a totals row means for its own document family - the party it names, the
 * delegate on it, the invoice a return reverses.
 *
 * <p>It is fixed on {@link BaseTotals} rather than generic on the concrete row. The four
 * implementations narrow their argument with an ordinary checked cast, because what
 * differs between them cannot be pulled up: {@code getCustomers()}, {@code getCustomer()},
 * {@code getSuppliers()} and {@code getSupplierData()} are four different methods on four
 * different classes. That is a JVM-verified cast on a plain class, not the unchecked
 * generics cast this seam exists to avoid.
 */
public interface TotalsDataInterface {

    default LocalDateTime getDateInsert(BaseTotals t2) {
        return t2.getCreated_at() == null ? LocalDateTime.now() : t2.getCreated_at();
    }

    default Employees getDelegateData(BaseTotals t2) {
        return new Employees();
    }

    /**
     * The invoice a saved return reverses, or {@code 0}. Zero for the two invoice
     * families, which reverse nothing - the same shape {@link #getDelegateData} uses to
     * answer a question only the sales side has.
     */
    default int getSourceInvoiceNumber(BaseTotals t2) {
        return 0;
    }

    /** The stored {@code return_reason} of a saved return, or {@code null}. */
    default String getReturnReason(BaseTotals t2) {
        return null;
    }

    int getIdData(BaseTotals t2);

    String getNameData(BaseTotals t2);

    default ToDoubleFunction<BaseTotals> getTotalProfit() {
        return t -> 0;
    }
}
