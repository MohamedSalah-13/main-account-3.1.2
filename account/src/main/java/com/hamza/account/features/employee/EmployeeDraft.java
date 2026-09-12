package com.hamza.account.features.employee;

import com.hamza.controlsfx.error.UserValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What a screen submits to create or edit an employee, after its own limits have been checked.
 * <p>
 * <b>Validation returns message keys, never Arabic sentences.</b> A service that throws a
 * translated literal cannot be read by an English install and cannot be tested without a
 * bundle - the rule §5 of {@code docs/new-code-rules.md} states, and the one
 * {@code MasterDataForm} already follows.
 * <p>
 * The limits are read off the schema rather than guessed: {@code column_name VARCHAR(50)},
 * {@code national_id VARCHAR(20)}, {@code email}/{@code tel}/{@code address VARCHAR(200)},
 * {@code notes VARCHAR(500)} and {@code DECIMAL(14, 2)} for a rate. A form that lets somebody
 * type more than the column holds turns a typing mistake into a reference code.
 *
 * @param salaryKind how this employee is paid. At creation it is written as the first dated
 *                   compensation row; on an edit it is only looked at while
 *                   {@link SalaryChangeGuard} still allows the hire figure to be corrected
 * @param rate       the figure that goes with it
 */
public record EmployeeDraft(int id,
                            String name,
                            int jobId,
                            LocalDate birthDate,
                            LocalDate hireDate,
                            LocalDate endDate,
                            EmploymentType employmentType,
                            String nationalId,
                            String email,
                            String phone,
                            String address,
                            String notes,
                            Integer defaultTreasuryId,
                            SalaryKind salaryKind,
                            BigDecimal rate) {

    private static final int NAME_MAX = 50;
    private static final int NATIONAL_ID_MAX = 20;
    private static final int CONTACT_MAX = 200;
    private static final int NOTES_MAX = 500;

    /** {@code DECIMAL(14, 2)}: twelve digits before the point. */
    private static final BigDecimal RATE_MAX = new BigDecimal("999999999999.99");

    public static EmployeeDraft parse(int id, String name, int jobId, LocalDate birthDate,
                                      LocalDate hireDate, LocalDate endDate,
                                      EmploymentType employmentType, String nationalId,
                                      String email, String phone, String address, String notes,
                                      Integer defaultTreasuryId, SalaryKind salaryKind,
                                      BigDecimal rate) throws UserValidationException {
        String cleanName = strip(name);
        if (cleanName.isEmpty()) {
            throw new UserValidationException("employee.error.name");
        }
        if (length(cleanName) > NAME_MAX) {
            throw new UserValidationException("employee.error.name.length");
        }
        if (jobId <= 0) {
            throw new UserValidationException("employee.error.job");
        }
        if (hireDate == null) {
            throw new UserValidationException("employee.error.hire.required");
        }
        if (birthDate != null && !birthDate.isBefore(hireDate)) {
            throw new UserValidationException("employee.error.birth.after.hire");
        }
        if (endDate != null && endDate.isBefore(hireDate)) {
            throw new UserValidationException("employee.error.end.before.hire");
        }
        if (length(strip(nationalId)) > NATIONAL_ID_MAX) {
            throw new UserValidationException("employee.error.national.length");
        }
        if (length(strip(email)) > CONTACT_MAX || length(strip(phone)) > CONTACT_MAX
                || length(strip(address)) > CONTACT_MAX) {
            throw new UserValidationException("employee.error.contact.length");
        }
        if (length(strip(notes)) > NOTES_MAX) {
            throw new UserValidationException("employee.error.notes.length");
        }
        BigDecimal cleanRate = rate == null ? BigDecimal.ZERO : rate;
        if (cleanRate.signum() < 0) {
            throw new UserValidationException("employee.error.salary.negative");
        }
        if (cleanRate.compareTo(RATE_MAX) > 0) {
            throw new UserValidationException("employee.error.salary.range");
        }
        return new EmployeeDraft(id, cleanName, jobId, birthDate, hireDate, endDate,
                employmentType == null ? EmploymentType.FULL_TIME : employmentType,
                strip(nationalId), strip(email), strip(phone), strip(address), strip(notes),
                defaultTreasuryId, salaryKind == null ? SalaryKind.MONTHLY : salaryKind,
                cleanRate.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }

    /**
     * Counted in code points, not in {@code char}s: an Arabic name is within the column at
     * fifty letters whatever the encoding, and a length check that counts UTF-16 units refuses
     * names the database would have taken.
     */
    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }
}
