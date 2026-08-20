package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
public class ItemsMiniQuantity {

    private int id;
    private String nameItem;
    private double miniQuantity;
    private double balance;
}
