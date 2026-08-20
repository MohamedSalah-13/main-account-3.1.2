package com.hamza.account.model.domain;

import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.type.InvoiceType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor
public class Total_Buy_Re extends BaseTotals {

    /**
     * The invoice this return reverses, or {@code 0} for a free return - what
     * {@code ReturnGuard} and {@code ReturnCostResolver} need in order to check an
     * <em>edit</em> of a saved return, not just its first save. Written by
     * {@code ReturnSourceWriter} and read back through the return's view.
     */
    private int sourceInvoiceNumber;
    /** Why it was returned, or {@code null}; {@code ReturnReason}'s stored name. */
    private String returnReason;


    //    private final double paid_to_treasury;
    private Suppliers suppliers;
    private List<Purchase_Return> purchaseReturnList = new ArrayList<>();

    public Total_Buy_Re(int id, String date, double total, double discount, double paid, String notes
            , Suppliers suppliers, Stock stock, Treasury treasury, InvoiceType invoiceType
            , List<Purchase_Return> purchaseReturnList) {
        setId(id);
        setDate(date);
        setTotal(total);
        setDiscount(discount);
        setPaid(paid);
        setNotes(notes);
        setStockData(stock);
        setTreasuryModel(treasury);
        setInvoiceType(invoiceType);
        this.suppliers = suppliers;
        this.purchaseReturnList = purchaseReturnList;
    }

}
