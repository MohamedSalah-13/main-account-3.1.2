package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.controlsfx.table.ColumnData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class ItemsMiniQuantity {

    @ColumnData(titleName = NamesTables.CODE)
    private int id;
    @ColumnData(titleName = NamesTables.NAME_ITEM)
    private String nameItem;
    @ColumnData(titleName = NamesTables.MINI_QUANTITY)
    private double miniQuantity;
    @ColumnData(titleName = NamesTables.BALANCE)
    private double balance;
}
