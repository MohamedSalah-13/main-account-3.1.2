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
public class SalesInvoiceReturn implements InvoiceBuy<Sales_Return, Total_Sales_Re, Customers, CustomerAccount> {

    @Override
    public Sales_Return object_TableData(int id, int num, int numItem, double price, double quantity, double discount, double total, UnitsModel type, ItemsModel itemsModel, LocalDate expireDate) {
        var salesReturn = new Sales_Return();
        salesReturn.setNumItem(numItem);
        salesReturn.setItems(itemsModel);
        salesReturn.setUnitsType(type);
        salesReturn.setPrice(price);
        salesReturn.setQuantity(quantity);
        salesReturn.setTotal_after_discount(total - discount);
        salesReturn.setTotal(total);
        salesReturn.setDiscount(discount);
        salesReturn.setId(id);
        salesReturn.setInvoiceNumber(num);
        salesReturn.setExpiration_date(expireDate);
        return salesReturn;
    }

    @Override
    public Total_Sales_Re object_Totals(int num_invoice, InvoiceType invoiceType, String date, double total, double discount
            , DiscountType discountType, double after, double paid, double rest, String notes, Customers t3, Stock stock
            , Employees userDelegate, List<Sales_Return> list, TreasuryModel treasuryModel) throws DaoException {

        var totalSalesRe = new Total_Sales_Re();
        totalSalesRe.setId(num_invoice);
        totalSalesRe.setDate(date);
        totalSalesRe.setTotal(total);
        totalSalesRe.setDiscount(discount);
        totalSalesRe.setPaid(paid);
        totalSalesRe.setNotes(notes);
        totalSalesRe.setCustomer(t3);
        totalSalesRe.setStockData(stock);
        totalSalesRe.setEmployeeObject(userDelegate);
        totalSalesRe.setTreasuryModel(treasuryModel);
        totalSalesRe.setSalesReturnList(list);
        totalSalesRe.setInvoiceType(invoiceType);
        return totalSalesRe;
    }

    @Override
    public Customers objectName(int code, String name) {
        return new Customers(code, name);
    }

    @Override
    public double getItemsPrice(ItemsModel itemsModel, int idForCustomerForSales) {
        switch (idForCustomerForSales) {
            case 2 -> {
                return itemsModel.getSelPrice2();
            }
            case 3 -> {
                return itemsModel.getSelPrice3();
            }
            default -> {
                return itemsModel.getSelPrice1();
            }
        }
    }

}
