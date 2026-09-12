package com.hamza.account.features.employee;

/**
 * The contract an employee is on. Stored as its own name in
 * {@code employees.employment_type}, pinned by a CHECK in V57.
 * <p>
 * It decides nothing on its own today - it is what a payroll run and a leave quota will ask
 * about, and what a list is filtered by. Declared now because adding it later means an
 * {@code ALTER} on a table with rows in it and a default nobody chose.
 */
public enum EmploymentType {

    FULL_TIME("employee.employment.full"),
    PART_TIME("employee.employment.part"),
    CONTRACT("employee.employment.contract"),
    TEMPORARY("employee.employment.temporary");

    private final String messageKey;

    EmploymentType(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }

    public static EmploymentType of(String stored) {
        if (stored != null) {
            for (EmploymentType type : values()) {
                if (type.name().equals(stored)) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException("Unknown employment type: " + stored);
    }

    public static EmploymentType orDefault(String stored) {
        return stored == null || stored.isBlank() ? FULL_TIME : of(stored);
    }
}
