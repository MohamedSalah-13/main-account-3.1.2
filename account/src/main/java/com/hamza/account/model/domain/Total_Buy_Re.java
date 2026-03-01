package com.hamza.account.model.domain;

import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.type.InvoiceType;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor
public class Total_Buy_Re extends BaseTotals {

    //    private final double paid_to_treasury;
    private ObjectProperty<Suppliers> suppliers = new SimpleObjectProperty<>();
    private List<Purchase_Return> purchaseReturnList = new ArrayList<>();

    public Total_Buy_Re(int id, String date, double total, double discount, double paid, String notes
            , Suppliers suppliers, Stock stock, TreasuryModel treasury, InvoiceType invoiceType
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
        this.suppliers = new SimpleObjectProperty<>(suppliers);
        this.purchaseReturnList = purchaseReturnList;
    }

    public Suppliers getSuppliers() {
        return suppliers.get();
    }

    public void setSuppliers(Suppliers suppliers) {
        this.suppliers.set(suppliers);
    }

    public ObjectProperty<Suppliers> suppliersProperty() {
        return suppliers;
    }
}
