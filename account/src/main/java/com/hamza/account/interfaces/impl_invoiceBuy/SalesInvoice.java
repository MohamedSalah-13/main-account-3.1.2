package com.hamza.account.interfaces.impl_invoiceBuy;

import com.hamza.account.interfaces.api.InvoiceBuy;
import com.hamza.account.model.domain.*;
import com.hamza.account.type.DiscountType;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.DaoException;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
public class SalesInvoice implements InvoiceBuy<Sales, Total_Sales, Customers, CustomerAccount> {

    @Override
    public Sales object_TableData(int id, int num, int numItem, double price, double quantity, double discount, double total, UnitsModel type
            , ItemsModel itemsModel, LocalDate expireDate) {
        Sales sales = new Sales();
        sales.setId(id);
        sales.setInvoiceNumber(num);
        sales.setNumItem(numItem);
        sales.setQuantity(quantity);
        sales.setPrice(price);
        sales.setDiscount(discount);
        sales.setUnitsType(type);
        sales.setTotal_after_discount(total - discount);
        sales.setTotal(total);
        sales.setBuy_price(roundToTwoDecimalPlaces(itemsModel.getBuyPrice() * type.getValue()));
        sales.setItems(itemsModel);
        sales.setExpiration_date(expireDate);
        sales.setItem_has_package(itemsModel.isHasPackage());
        return sales;
    }

    @Override
    public Total_Sales object_Totals(int num_invoice, InvoiceType invoiceType, String date, double total, double discount
            , DiscountType discountType, double after, double paid, double rest, String notes, Customers t3, Stock stock
            , Employees userDelegate, List<Sales> list, TreasuryModel treasuryModel) throws DaoException {
        var totalSales = new Total_Sales();
        totalSales.setId(num_invoice);
        totalSales.setInvoiceType(invoiceType);
        totalSales.setDate(date);
        totalSales.setTotal(total);
        totalSales.setDiscount(discount);
        totalSales.setDiscountType(discountType);
        totalSales.setTotal_after_discount(after);
        totalSales.setPaid(paid);
        totalSales.setRest(rest);
        totalSales.setNotes(notes);
        totalSales.setCustomers(t3);
        totalSales.setStockData(stock);
        totalSales.setEmployeeObject(userDelegate);
        totalSales.setTreasuryModel(treasuryModel);

        for (Sales sales : list) {
            sales.setTotalSelPrice(BigDecimal.valueOf(sales.getQuantity() * sales.getPrice()));
            sales.setTotal_buy_price(sales.getQuantity() * sales.getBuy_price());
            sales.setTotal_profit(sales.getTotalSelPrice().subtract(BigDecimal.valueOf(sales.getTotal_buy_price())));
        }

        totalSales.setSalesList(list);
        return totalSales;
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

    @Override
    public boolean updateItemPrice(ItemsModel itemsModel, double price, int idForCustomerForSales) {
        switch (idForCustomerForSales) {
            case 2 -> {
                itemsModel.setSelPrice2(price);
                return true;
            }
            case 3 -> {
                itemsModel.setSelPrice3(price);
                return true;
            }
            default -> itemsModel.setSelPrice1(price);
        }
        return true;
    }

}
