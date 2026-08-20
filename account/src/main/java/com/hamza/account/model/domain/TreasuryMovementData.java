package com.hamza.account.model.domain;

import com.hamza.account.model.base.DForColumnTable;
import com.hamza.account.type.TreasuryMovementType;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class TreasuryMovementData extends DForColumnTable {

    private LongProperty id = new SimpleLongProperty();

    private String movementDate;

    private String treasuryName;

    private String movementTypeName;

    private BigDecimal amountIn = BigDecimal.ZERO;

    private BigDecimal amountOut = BigDecimal.ZERO;

    private BigDecimal balanceAfter = BigDecimal.ZERO;

    private String notes;

    private TreasuryMovementType movementType;

    public long getId() {
        return id.get();
    }

    public void setId(long id) {
        this.id.set(id);
    }

    public LongProperty idProperty() {
        return id;
    }

}
