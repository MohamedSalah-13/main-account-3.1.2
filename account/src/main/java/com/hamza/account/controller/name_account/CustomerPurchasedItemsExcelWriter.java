package com.hamza.account.controller.name_account;

import com.hamza.account.model.domain.CustomerPurchasedItem;
import com.hamza.controlsfx.excel.WriteExcelInterface;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class CustomerPurchasedItemsExcelWriter implements WriteExcelInterface<CustomerPurchasedItem> {

    private final List<CustomerPurchasedItem> items;

    @Override
    public Object[] columnHeader() {
        return new Object[]{
                "رقم العميل", "العميل", "الصنف",
                "الكمية", "السعر", "التاريخ", "رقم الفاتورة"
        };
    }

    @Override
    public Object[] dataRow(CustomerPurchasedItem item) {
        return new Object[]{
                item.getCustomerId(),
                item.getCustomerName(),
                item.getItemName(),
                item.getQuantity(),
                item.getSellingPrice(),
                item.getInvoiceDate(),
                item.getInvoiceNumber()
        };
    }

    @Override
    public List<CustomerPurchasedItem> itemsList() {
        return items;
    }

    @Override
    public boolean addDataToFile() {
        return true;
    }

    @Override
    public String sheetName() {
        return "Purchased Items";
    }
}