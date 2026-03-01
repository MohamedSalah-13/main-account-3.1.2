package com.hamza.account.interfaces.api;

import com.hamza.account.model.base.BaseAccount;
import com.hamza.account.model.base.BaseNames;
import com.hamza.account.model.base.BasePurchasesAndSales;
import com.hamza.account.model.base.BaseTotals;
import com.hamza.account.model.domain.*;
import com.hamza.account.type.DiscountType;
import com.hamza.account.type.InvoiceType;
import com.hamza.controlsfx.database.DaoException;

import java.time.LocalDate;
import java.util.List;

public interface InvoiceBuy<T1 extends BasePurchasesAndSales, T2 extends BaseTotals, T3 extends BaseNames, T4 extends BaseAccount> {

    T1 object_TableData(int id, int num, int numItem, double price, double quantity, double discount, double total
            , UnitsModel type, ItemsModel itemsModel, LocalDate expireDate);

    T2 object_Totals(int num_invoice, InvoiceType invoiceType, String date, double total, double discount
            , DiscountType discountType, double after, double paid, double rest, String notes, T3 t3, Stock stock
            , Employees userDelegate, List<T1> list, TreasuryModel treasuryModel) throws DaoException;

    T3 objectName(int code, String name);

    double getItemsPrice(ItemsModel itemsModel, int idForCustomerForSales);

    default boolean updateItemPrice(ItemsModel itemsModel, double price, int idForCustomerForSales) {
        return false;
    }

}
