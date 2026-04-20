package com.hamza.account.service;

import com.hamza.account.controller.model.PurchasedItemByCustomerView;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Sales;
import com.hamza.account.model.domain.Total_Sales;
import com.hamza.controlsfx.database.DaoException;

import java.util.ArrayList;
import java.util.List;

public record CustomerPurchasedItemsService(DaoFactory daoFactory) {

    public List<PurchasedItemByCustomerView> getPurchasedItemsByCustomerId(int customerId) throws DaoException {
        List<PurchasedItemByCustomerView> result = new ArrayList<>();

        List<Total_Sales> customerInvoices = daoFactory.totalsSalesDao().getTotalSalesByCustomerId(customerId);
        if (customerInvoices == null || customerInvoices.isEmpty()) {
            return result;
        }

        for (Total_Sales totalSales : customerInvoices) {
            List<Sales> invoiceItems = daoFactory.salesDao().loadAllById(totalSales.getId());
            if (invoiceItems == null || invoiceItems.isEmpty()) {
                continue;
            }

            for (Sales sales : invoiceItems) {
                var item = sales.getItems();
                var unit = sales.getUnitsType();
                var customer = totalSales.getCustomers();

                result.add(new PurchasedItemByCustomerView(
                        totalSales.getId(),
                        totalSales.getDate(),
                        customer != null ? customer.getId() : 0,
                        customer != null ? customer.getName() : "",
                        item != null ? item.getId() : 0,
                        item != null ? item.getBarcode() : "",
                        item != null ? item.getNameItem() : "",
                        unit != null ? unit.getUnit_name() : "",
                        sales.getQuantity(),
                        sales.getPrice(),
                        sales.getDiscount(),
                        sales.getTotal(),
                        sales.getTotal_after_discount()
                ));
            }
        }

        return result;
    }
}