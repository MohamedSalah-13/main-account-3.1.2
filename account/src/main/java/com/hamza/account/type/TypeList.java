package com.hamza.account.type;

import java.util.Arrays;
import java.util.List;

public class TypeList {

    public static List<String> processTypeList = Arrays.stream(ProcessType.values()).map(ProcessType::getType).toList();
}
