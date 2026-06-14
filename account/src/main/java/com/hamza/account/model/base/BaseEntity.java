package com.hamza.account.model.base;

import com.hamza.account.config.NamesTables;
import com.hamza.controlsfx.table.ColumnData;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class BaseEntity extends DForColumnTable {

    @ColumnData(titleName = NamesTables.CODE)
    private int id;
}
