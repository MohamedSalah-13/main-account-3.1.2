package com.hamza.account.model.domain;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Target_Delegate {

    private int delegate_id;
    private double target;
    private double target_achieved;
    private double target_not_achieved;
}
