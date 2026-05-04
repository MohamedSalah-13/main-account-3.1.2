package com.hamza.account.controller.search;

import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.service.ItemsService;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.List;

public class ItemsSearch implements SearchInterface<ItemsModel> {

    private final ItemsService itemsService;
    private final StringProperty stockName = new SimpleStringProperty();

    // 1. تعريف متغيرات الكاش
    private List<ItemsModel> cachedItems = null;
    private boolean isCacheValid = false;

    public ItemsSearch(ItemsService itemsService) {
        this.itemsService = itemsService;

        // 2. مستمع (Listener) لمسح الكاش تلقائياً إذا تم تغيير اسم المخزن
        this.stockName.addListener((observable, oldValue, newValue) -> invalidateCache());
    }

    @Override
    public Class<? super ItemsModel> getSearchClass() {
        return ItemsModel.class;
    }

    @Override
    public List<ItemsModel> searchItems() throws Exception {
        // 3. التحقق من حالة الكاش قبل الاتصال بقاعدة البيانات
        if (isCacheValid && cachedItems != null) {
            return cachedItems; // إرجاع البيانات من الكاش مباشرة (زمن استجابة فوري)
        }

        // 4. جلب البيانات من الداتا بيز (يحدث فقط أول مرة أو بعد مسح الكاش)
        // TODO 11/23/2025 6:55 AM Mohamed: filter to remove item package
        cachedItems = itemsService.filterItemListsByStockName(stockNameProperty().get());
        isCacheValid = true; // تفعيل الكاش للاستخدامات القادمة

        return cachedItems;
    }

    @Override
    public String getName(ItemsModel itemsModel) {
        return itemsModel.getNameItem();
    }

    // 5. دالة هامة لمسح الكاش يدوياً
    public void invalidateCache() {
        this.isCacheValid = false;
        this.cachedItems = null;
    }

    public String getStockName() {
        return stockName.get();
    }

    public void setStockName(String stockName) {
        this.stockName.set(stockName);
    }

    public StringProperty stockNameProperty() {
        return stockName;
    }
}