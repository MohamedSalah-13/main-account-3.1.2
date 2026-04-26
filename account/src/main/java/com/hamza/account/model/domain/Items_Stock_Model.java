package com.hamza.account.model.domain;

import com.hamza.account.model.base.UnitExtends;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class Items_Stock_Model extends UnitExtends {

    private Integer id;
    private ItemsModel itemsModel;
    private Stock stock;
    private double firstBalance;
    private double currentQuantity;

    public Items_Stock_Model(int itemId, int stockId, double firstBalance
            , double currentQuantity) {
        this.itemsModel = new ItemsModel(itemId);
        this.stock = new Stock(stockId);
        this.firstBalance = firstBalance;
        this.currentQuantity = currentQuantity;
    }

}
