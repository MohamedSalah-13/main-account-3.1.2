package com.hamza.account.type;

import com.hamza.controlsfx.language.Setting_Language;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum ExpensesType {

    SALARIES(1, Setting_Language.SALARIES),
    ELECTRICITY(2, Setting_Language.ELECTRICS),
    PREDECESSOR(3, Setting_Language.PRED),
    WATER(4, Setting_Language.WATERS),
    RENTALS(5, Setting_Language.RENTALS),
    OTHERS(6, Setting_Language.OTHERS);

    private final int id;
    private final String type;

    ExpensesType(int id, String type) {
        this.id = id;
        this.type = type;
    }

    public static ExpensesType fromType(String type) {
        return Arrays.stream(ExpensesType.values())
                .filter(expensesType -> expensesType.getType().equals(type))
                .findFirst()
                .orElse(null);
    }

}
