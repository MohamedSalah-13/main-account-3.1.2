package com.hamza.account.model.domain;

import com.hamza.account.config.NamesTables;
import com.hamza.account.model.base.DForColumnTable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Treasury extends DForColumnTable {

    private int id;
    private String name;
    private BigDecimal amount = BigDecimal.ZERO;
    private int userId;

    public Treasury(int id) {
        this.id = id;
    }

    public Treasury(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public Treasury(int id, String name, BigDecimal amount) {
        this.id = id;
        this.name = name;
        this.amount = amount;
    }

    public Treasury(String name, BigDecimal amount, int userId) {
        this.name = name;
        this.amount = amount;
        this.userId = userId;
    }

    @Override
    public String toString() {
        return name;
    }
}
