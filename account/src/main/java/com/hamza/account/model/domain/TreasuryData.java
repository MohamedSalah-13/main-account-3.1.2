package com.hamza.account.model.domain;

import com.hamza.account.model.base.DForColumnTable;
import com.hamza.account.type.OperationType;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TreasuryData extends DForColumnTable {

    private IntegerProperty id = new SimpleIntegerProperty();
    private String date_inv;
    private BigDecimal amount;
    private String notes;
    private OperationType operationType;

    public int getId() {
        return id.get();
    }

    public void setId(int id) {
        this.id.set(id);
    }

    public IntegerProperty idProperty() {
        return id;
    }

}
