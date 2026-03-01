package com.hamza.account.model.domain;

import com.hamza.account.model.base.BaseEntity;
import com.hamza.controlsfx.table.ColumnData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Area extends BaseEntity {

    @ColumnData(titleName = "المنطقة")
    private String area_name;

    public Area(int id, String area_name) {
        setId(id);
        this.area_name = area_name;
    }

}
