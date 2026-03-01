package com.hamza.account.type;


import lombok.Getter;

@Getter
public enum OperationType {
    DEPOSIT(1, "إيداع"),
    EXCHANGE(2, "صرف");

    private final int id;
    private final String type;

    OperationType(int id, String type) {
        this.id = id;
        this.type = type;
    }

    public static OperationType getById(int id) {
        return getBy(operationType -> operationType.id == id);
    }

    public static OperationType getByType(String type) {
        return getBy(operationType -> operationType.type.equals(type));
    }

    private static OperationType getBy(java.util.function.Predicate<OperationType> predicate) {
        for (OperationType operationType : OperationType.values()) {
            if (predicate.test(operationType)) {
                return operationType;
            }
        }
        return null;
    }
}