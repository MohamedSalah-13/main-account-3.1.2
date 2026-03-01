package com.hamza.account.model.base;


import com.hamza.account.config.NamesTables;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.controlsfx.table.ColumnData;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class UnitExtends extends DForColumnTable {

    private UnitsModel unitsType;

    @ColumnData(titleName = NamesTables.TYPE)
    private String typeName;

    public UnitExtends() {
    }

    public UnitExtends(UnitsModel unitsType) {
        this.unitsType = unitsType;
    }
}
