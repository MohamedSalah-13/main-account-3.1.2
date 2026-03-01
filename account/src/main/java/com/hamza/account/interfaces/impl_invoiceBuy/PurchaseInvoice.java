package com.hamza.account.interfaces.impl_invoiceBuy;

import com.hamza.account.interfaces.api.InvoiceBuy;
import com.hamza.account.model.domain.*;
import com.hamza.account.type.DiscountType;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.DaoException;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDate;
import java.util.List;

@Log4j2
public class PurchaseInvoice implements InvoiceBuy<Purchase, Total_buy, Suppliers, SupplierAccount> {

    @Override
    public Purchase object_TableData(int id, int num, int numItem, double price, double quantity, double discount, double total, UnitsModel type, ItemsModel itemsModel, LocalDate expireDate) {
        var purchase = new Purchase();
        purchase.setUnitsType(type);
        purchase.setId(id);
        purchase.setInvoiceNumber(num);
        purchase.setNumItem(numItem);
        purchase.setQuantity(quantity);
        purchase.setPrice(price);
        purchase.setDiscount(discount);
        purchase.setTotal(total);
        purchase.setTotal_after_discount(total - discount);
        purchase.setItems(itemsModel);
        purchase.setExpiration_date(expireDate);
        return purchase;
    }

    @Override
    public Total_buy object_Totals(int num_invoice, InvoiceType invoiceType, String date, double total
            , double discount, DiscountType discountType, double after, double paid, double rest, String notes
            , Suppliers t3, Stock stock, Employees userDelegate, List<Purchase> list, TreasuryModel treasuryModel) throws DaoException {

        var totalBuy = new Total_buy();
        totalBuy.setId(num_invoice);
        totalBuy.setInvoiceType(invoiceType);
        totalBuy.setDate(date);
        totalBuy.setTotal(total);
        totalBuy.setDiscount(discount);
        totalBuy.setDiscountType(discountType);
        totalBuy.setTotal_after_discount(after);
        totalBuy.setPaid(paid);
        totalBuy.setRest(rest);
        totalBuy.setNotes(notes);
        totalBuy.setSupplierData(t3);
        totalBuy.setStockData(stock);
        totalBuy.setTreasuryModel(treasuryModel);
        totalBuy.setPurchaseList(list);
        return totalBuy;
    }


    @Override
    public Suppliers objectName(int code, String name) {
        return new Suppliers(code, name);
    }

    @Override
    public double getItemsPrice(ItemsModel itemsModel, int idForCustomerForSales) {
        return itemsModel.getBuyPrice();
    }

    @Override
    public boolean updateItemPrice(ItemsModel itemsModel, double price, int idForCustomerForSales) {
        itemsModel.setBuyPrice(price);
        return true;
    }

}
