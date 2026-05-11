package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.BaseEntity;
import com.hamza.controlsfx.table.ColumnData;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TreasuryTransfer extends BaseEntity {

    @ColumnData(titleName = NamesTables.AMOUNT)
    private BigDecimal amount = BigDecimal.ZERO;
    @ColumnData(titleName = NamesTables.DATE)
    private LocalDate transferDate;
    @ColumnData(titleName = "من خزينة")
    private String treasuryNameFrom;
    @ColumnData(titleName = "إلى خزينة")
    private String treasuryNameTo;
    @ColumnData(titleName = NamesTables.NOTES)
    private String notes;

    private Treasury treasuryFrom;
    private Treasury treasuryTo;

}
