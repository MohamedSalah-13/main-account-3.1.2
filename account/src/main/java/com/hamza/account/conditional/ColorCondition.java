package com.hamza.account.conditional;

public class ColorCondition {
    private String column;
    private ConditionType condition;
    private String value;
    private String applyTo; // "Cell" or "Row"
    private String color;

    public ColorCondition(String column, ConditionType condition, String value, String applyTo, String color) {
        this.column = column;
        this.condition = condition;
        this.value = value;
        this.applyTo = applyTo;
        this.color = color;
    }

    public boolean matches(Person person) {
        try {
            switch (column) {
                case "Name":
                    return condition.matchesString(person.getName(), value);
                case "Age":
                    return condition.matchesNumber(person.getAge(), Integer.parseInt(value));
                case "Email":
                    return condition.matchesString(person.getEmail(), value);
                case "Department":
                    return condition.matchesString(person.getDepartment(), value);
                case "Salary":
                    return condition.matchesNumber(person.getSalary(), Integer.parseInt(value));
                default:
                    return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // Getters
    public String getColumn() {
        return column;
    }

    public ConditionType getCondition() {
        return condition;
    }

    public String getValue() {
        return value;
    }

    public String getApplyTo() {
        return applyTo;
    }

    public String getColor() {
        return color;
    }

    @Override
    public String toString() {
        return String.format("%s %s %s → %s (%s)", column, condition.getDisplayName(), value, applyTo, color);
    }
}
