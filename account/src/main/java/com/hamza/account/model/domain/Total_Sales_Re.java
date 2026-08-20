package com.hamza.account.model.domain;

import com.hamza.account.model.base.BaseTotals;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@NoArgsConstructor
public class Total_Sales_Re extends BaseTotals {

    /**
     * The invoice this return reverses, or {@code 0} for a free return - what
     * {@code ReturnGuard} and {@code ReturnCostResolver} need in order to check an
     * <em>edit</em> of a saved return, not just its first save. Written by
     * {@code ReturnSourceWriter} and read back through the return's view.
     */
    private int sourceInvoiceNumber;
    /** Why it was returned, or {@code null}; {@code ReturnReason}'s stored name. */
    private String returnReason;


    private Customers customer;
    private Employees employeeObject;
    private List<Sales_Return> salesReturnList;
    private double total_profit;
    private double profit_percent;

}
