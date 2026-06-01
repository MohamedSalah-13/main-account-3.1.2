package com.hamza.account.model.domain;

import com.hamza.account.model.base.UnitExtends;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Setter
@Getter
@NoArgsConstructor
public class Items_Stock_Model extends UnitExtends {

    private Integer id;
    private ItemsModel itemsModel;
    private Stock stock;
    private double firstBalance;
    private double currentQuantity;

    public Items_Stock_Model(int itemId, int stockId, double firstBalance, double currentQuantity) {
        this.itemsModel = new ItemsModel(itemId);
        this.stock = new Stock(stockId);
        this.firstBalance = firstBalance;
        this.currentQuantity = currentQuantity;
    }

    /**
     * يحسب الفرق بين الكمية الحالية ورصيد أول المدة
     * (المشتريات - المبيعات + المرتجعات - التحويلات...)
     */
    public double getMovementBalance() {
        return currentQuantity - firstBalance;
    }

    /**
     * يتحقق من أن الرصيد صحيح (لا يوجد سالب)
     */
    public boolean isValidBalance() {
        return firstBalance >= 0 && currentQuantity >= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Items_Stock_Model that = (Items_Stock_Model) o;
        return Objects.equals(id, that.id) ||
                (itemsModel != null && that.itemsModel != null &&
                        stock != null && that.stock != null &&
                        itemsModel.getId() == that.itemsModel.getId() &&
                        stock.getId() == that.stock.getId());
    }

    @Override
    public int hashCode() {
        if (id != null) return Objects.hash(id);
        int itemId = itemsModel != null ? itemsModel.getId() : 0;
        int stockId = stock != null ? stock.getId() : 0;
        return Objects.hash(itemId, stockId);
    }

    @Override
    public String toString() {
        return "Items_Stock_Model{" +
                "id=" + id +
                ", itemId=" + (itemsModel != null ? itemsModel.getId() : null) +
                ", stockId=" + (stock != null ? stock.getId() : null) +
                ", firstBalance=" + firstBalance +
                ", currentQuantity=" + currentQuantity +
                '}';
    }
}