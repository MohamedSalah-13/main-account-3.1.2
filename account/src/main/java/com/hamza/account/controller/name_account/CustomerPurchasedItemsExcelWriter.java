package com.hamza.account.controller.name_account;

import com.hamza.account.controller.model.PurchasedItemByCustomerView;
import com.hamza.controlsfx.excel.WriteExcelInterface;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class CustomerPurchasedItemsExcelWriter implements WriteExcelInterface<PurchasedItemByCustomerView> {

    private final List<PurchasedItemByCustomerView> items;
    private final String customerName;

    @Override
    public Object[] columnHeader() {
        return new Object[]{
                "رقم الفاتورة", "التاريخ", "العميل", "الصنف", "الباركود", "الوحدة",
                "الكمية", "السعر", "الخصم", "الإجمالي", "الصافي"
        };
    }

    @Override
    public Object[] dataRow(PurchasedItemByCustomerView item) {
        return new Object[]{
                item.getInvoiceNumber(),
                item.getInvoiceDate(),
                customerName,
                item.getItemName(),
                item.getItemBarcode(),
                item.getUnitName(),
                item.getQuantity(),
                item.getPrice(),
                item.getDiscount(),
                item.getTotal(),
                item.getTotalAfterDiscount()
        };
    }

    @Override
    public List<PurchasedItemByCustomerView> itemsList() {
        return items;
    }

    @Override
    public String sheetName() {
        return "Purchased Items";
    }

    @Override
    public boolean addDataToFile() {
        return true;
    }
}