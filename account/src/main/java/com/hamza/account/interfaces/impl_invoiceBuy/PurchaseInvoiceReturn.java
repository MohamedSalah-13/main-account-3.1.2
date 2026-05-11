package com.hamza.account.interfaces.impl_invoiceBuy;

import com.hamza.account.interfaces.api.InvoiceBuy;
import com.hamza.account.model.domain.*;
import com.hamza.account.type.DiscountType;
import com.hamza.account.type.InvoiceType;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.util.List;

@Log4j2
public class PurchaseInvoiceReturn implements InvoiceBuy<Purchase_Return, Total_Buy_Re, Suppliers, SupplierAccount> {

    @Override
    public Purchase_Return object_TableData(int id, int num, int numItem, double price, double quantity, double discount, double total, UnitsModel type
            , ItemsModel itemsModel, LocalDate expireDate) {
        var purchaseReturn = new Purchase_Return();
        purchaseReturn.setId(id);
        purchaseReturn.setInvoiceNumber(num);
        purchaseReturn.setNumItem(numItem);
        purchaseReturn.setQuantity(quantity);
        purchaseReturn.setPrice(price);
        purchaseReturn.setDiscount(discount);
        purchaseReturn.setTotal_after_discount(total - discount);
        purchaseReturn.setTotal(total);
        purchaseReturn.setItems(itemsModel);
        purchaseReturn.setUnitsType(type);
        purchaseReturn.setExpiration_date(expireDate);
        return purchaseReturn;
    }

    @Override
    public Total_Buy_Re object_Totals(int num_invoice, InvoiceType invoiceType
            , String date, double total, double discount, DiscountType discountType
            , double after, double paid, double rest, String notes, Suppliers t3, Stock stock
            , Employees userDelegate, List<Purchase_Return> list, Treasury treasury) {

        return new Total_Buy_Re(num_invoice, date, total, discount, paid, notes, t3, stock, treasury, invoiceType, list);
    }

    @Override
    public Suppliers objectName(int code, String name) {
        return new Suppliers(code, name);
    }

    @Override
    public double getItemsPrice(ItemsModel itemsModel, int idForCustomerForSales) {
        return itemsModel.getBuyPrice();
    }
}
