package com.hamza.account.model.base;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class BaseTarget extends DForColumnTable {

    private double target_ratio1;
    private double target_ratio2;
    private double target_ratio3;
    private double rate1;
    private double rate2;
    private double rate3;

}
