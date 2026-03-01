package com.hamza.account.conditional;

public enum ConditionType {
    EQUALS("Equals"),
    NOT_EQUALS("Not Equals"),
    GREATER_THAN("Greater Than"),
    LESS_THAN("Less Than"),
    GREATER_THAN_OR_EQUAL("Greater Than or Equal"),
    LESS_THAN_OR_EQUAL("Less Than or Equal"),
    CONTAINS("Contains"),
    STARTS_WITH("Starts With"),
    ENDS_WITH("Ends With");

    private final String displayName;

    ConditionType(String displayName) {
        this.displayName = displayName;
    }

    public static ConditionType fromDisplayName(String displayName) {
        for (ConditionType type : values()) {
            if (type.displayName.equals(displayName)) {
                return type;
            }
        }
        return EQUALS;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean matchesString(String actual, String expected) {
        switch (this) {
            case EQUALS:
                return actual.equalsIgnoreCase(expected);
            case NOT_EQUALS:
                return !actual.equalsIgnoreCase(expected);
            case CONTAINS:
                return actual.toLowerCase().contains(expected.toLowerCase());
            case STARTS_WITH:
                return actual.toLowerCase().startsWith(expected.toLowerCase());
            case ENDS_WITH:
                return actual.toLowerCase().endsWith(expected.toLowerCase());
            default:
                return false;
        }
    }

    public boolean matchesNumber(int actual, int expected) {
        switch (this) {
            case EQUALS:
                return actual == expected;
            case NOT_EQUALS:
                return actual != expected;
            case GREATER_THAN:
                return actual > expected;
            case LESS_THAN:
                return actual < expected;
            case GREATER_THAN_OR_EQUAL:
                return actual >= expected;
            case LESS_THAN_OR_EQUAL:
                return actual <= expected;
            default:
                return false;
        }
    }
}
