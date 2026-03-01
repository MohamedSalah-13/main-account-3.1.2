package com.hamza.controlsfx.table.conditional;

public enum Operator {
    GREATER_THAN(">"),
    GREATER_OR_EQUAL(">="),
    LESS_THAN("<"),
    LESS_OR_EQUAL("<="),
    EQUALS("="),
    NOT_EQUALS("≠"),
    CONTAINS("contains"),
    STARTS_WITH("starts with"),
    ENDS_WITH("ends with");

    private final String display;

    Operator(String display) {
        this.display = display;
    }

    @Override
    public String toString() {
        return display;
    }
}
