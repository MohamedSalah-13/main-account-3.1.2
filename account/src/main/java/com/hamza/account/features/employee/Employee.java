package com.hamza.account.features.employee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An employee, as a detached row.
 * <p>
 * No {@code javafx.beans.property}, and no {@code DForColumnTable} - which the model this
 * replaces extended, and which fills a field from {@code CurrentUser.getOrNull()} at
 * construction, so every row it built captured whoever happened to be signed in. Rule ق-ل2 of
 * {@code docs/new-code-rules.md}.
 * <p>
 * <b>{@code photo} is not here on purpose.</b> The picture is a {@code LONGBLOB}, and a list
 * of fifty employees that selects it moves fifty images to draw fifty names - the
 * {@code ItemsDao.map} lesson, where a page of items cost several hundred round trips nobody
 * could see. The profile screen asks for one picture by id.
 *
 * @param hireSalary  {@code employees.salary}: what this employee was hired at, never the
 *                    current figure. {@code null} when the reader may not see salaries
 * @param rate        the rate in force today, from {@code employee_current_compensation}.
 *                    {@code null} either because the reader may not see salaries, or because
 *                    every compensation row this employee has is dated in the future
 * @param rateFrom    the day {@link #rate} started, or {@code null} on the same terms
 * @param salaryKind  how they are paid. Not a salary, so it is readable by anyone who can
 *                    open the screen - "paid daily" is a fact about the job, not a figure
 */
public record Employee(int id,
                       String name,
                       int jobId,
                       String jobName,
                       boolean delegate,
                       LocalDate birthDate,
                       LocalDate hireDate,
                       LocalDate endDate,
                       boolean active,
                       EmploymentType employmentType,
                       String nationalId,
                       String email,
                       String phone,
                       String address,
                       String notes,
                       Integer defaultTreasuryId,
                       BigDecimal hireSalary,
                       SalaryKind salaryKind,
                       BigDecimal rate,
                       LocalDate rateFrom,
                       LocalDateTime createdAt) {

    /** Whether any salary figure reached this row at all - the reader's permission, in one place. */
    public boolean carriesSalary() {
        return rate != null || hireSalary != null;
    }
}
