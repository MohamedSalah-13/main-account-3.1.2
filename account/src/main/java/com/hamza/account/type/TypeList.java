package com.hamza.account.type;

import java.util.Arrays;
import java.util.List;

public class TypeList {

    public static List<String> unitType2List = Arrays.stream(UnitsType2.values()).map(UnitsType2::getType).toList();
    public static List<String> usersTypeList = Arrays.stream(UsersType.values()).map(UsersType::getType).toList();
    public static List<String> processTypeList = Arrays.stream(ProcessType.values()).map(ProcessType::getType).toList();
    public static List<String> expensesTypeList = Arrays.stream(ExpensesType.values()).map(ExpensesType::getType).toList();
}
