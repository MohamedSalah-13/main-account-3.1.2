package com.hamza.account.features.party.payment;

import com.hamza.account.finance.MoneyMath;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One of a party's invoices that is not fully settled, as the allocation picker shows it.
 *
 * @param invoiceNumber the document's own number, which is what a payment records in
 *                      {@code numberInv}
 * @param date          the invoice's date
 * @param net           its total after discount
 * @param settled       what has been put against it so far: the cash it carried itself, plus
 *                      every payment allocated to it
 * @param notes         the invoice's notes, so the picker can be read without opening it
 */
public record OpenInvoice(long invoiceNumber, LocalDate date, BigDecimal net,
                          BigDecimal settled, String notes) {

    public OpenInvoice {
        Objects.requireNonNull(date, "date");
        net = MoneyMath.money(net == null ? BigDecimal.ZERO : net);
        settled = MoneyMath.money(settled == null ? BigDecimal.ZERO : settled);
        notes = notes == null ? "" : notes;
    }

    /** What is still owed on this invoice. */
    public BigDecimal remaining() {
        return MoneyMath.subtract(net, settled);
    }
}
