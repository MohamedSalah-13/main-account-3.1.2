package com.hamza.account.model.domain;

import com.hamza.account.model.base.BasePurchasesAndSales;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
@NoArgsConstructor
public class Sales_Return extends BasePurchasesAndSales {

    private boolean item_has_package;
    private BigDecimal totalSelPrice;
    private BigDecimal total_profit;
}


