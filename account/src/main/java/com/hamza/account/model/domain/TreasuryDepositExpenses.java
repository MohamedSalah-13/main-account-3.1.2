package com.hamza.account.model.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class TreasuryDepositExpenses {

    private int id;
    private String statement;
    private LocalDate dateInter;
    private BigDecimal amount = BigDecimal.ZERO;
    private String descriptionData;
    private int depositOrExpenses;
    private Treasury treasury;
    private LocalDateTime dateInsert;
    private LocalDateTime updatedAt;
    private int userId;

    public boolean isDeposit() {
        return depositOrExpenses == 1;
    }

    public boolean isExpenses() {
        return depositOrExpenses == 2;
    }
}