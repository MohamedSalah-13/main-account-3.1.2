package com.hamza.account.features.employee;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins every statement the employee screens run.
 * <p>
 * The reason is {@code DocumentDaoStatementsTest}'s: a merge that swaps two adjacent columns still
 * produces valid SQL - it just saves one thing as another - and nothing between that and a
 * customer's database except a test that reads the text. Here the shapes that matter are the
 * parameter counts, because the binding order is written out by hand in
 * {@link JdbcEmployeeRepository} and a statement bound in a different order than it is written
 * still runs.
 */
class EmployeeQueryTest {

    private static int parameters(String sql) {
        return (int) sql.chars().filter(c -> c == '?').count();
    }

    @Nested
    @DisplayName("the shared WHERE")
    class Where {

        @Test
        @DisplayName("an unfiltered list has no conditions at all")
        void empty() {
            assertEquals("", EmployeeQuery.whereSql(EmployeeFilter.all()));
        }

        @Test
        @DisplayName("the text match is bracketed as a whole")
        void textIsBracketed() {
            String where = EmployeeQuery.whereSql(EmployeeFilter.all().withText("عمر"));
            assertTrue(where.contains("(e.column_name LIKE ? ESCAPE '!'"),
                    "the alternation must open with a bracket: " + where);
            assertTrue(where.contains("OR e.id = ?)"),
                    "and close after the last branch, or AND binds tighter than OR and only one "
                            + "branch stays filtered by the rest of the bar: " + where);
            assertEquals(4, parameters(where));
        }

        @Test
        @DisplayName("every filter that is set adds exactly one parameter, and the delegate flag adds none")
        void oneParameterEach() {
            EmployeeFilter filter = new EmployeeFilter("", 3, EmployeeState.ACTIVE,
                    SalaryKind.MONTHLY, EmploymentType.FULL_TIME,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                    new BigDecimal("1000"), new BigDecimal("5000"), true, 0, 50);

            String where = EmployeeQuery.whereSql(filter);
            // job, state, salary kind, employment type, hired from, hired to, min rate, max rate
            assertEquals(8, parameters(where));
            assertTrue(where.contains("j.is_delegate = 1"),
                    "the delegate flag is a literal, not a bound value: " + where);
            assertFalse(where.contains("?, ?"), "conditions are separate, not a list: " + where);
        }

        @Test
        @DisplayName("a state of ALL adds no condition, which is not the same as is_active = 1")
        void allAddsNothing() {
            assertEquals("", EmployeeQuery.whereSql(EmployeeFilter.all().withState(EmployeeState.ALL)));
            assertTrue(EmployeeQuery.whereSql(EmployeeFilter.all().withState(EmployeeState.INACTIVE))
                    .contains("e.is_active = ?"));
        }
    }

    @Nested
    @DisplayName("the page and its summary")
    class PageAndSummary {

        @Test
        @DisplayName("are built from the same WHERE")
        void oneWhere() {
            EmployeeFilter filter = EmployeeFilter.all().withText("سالم").withJob(2);
            String where = EmployeeQuery.whereSql(filter);

            assertTrue(EmployeeQuery.pageSql(filter, true).contains(where));
            assertTrue(EmployeeQuery.summarySql(filter, true).contains(where),
                    "filtering the footer separately is what makes a pager describe rows the "
                            + "table does not show");
        }

        @Test
        @DisplayName("the page binds the filter, then the ranking, then the limit and offset")
        void pageParameters() {
            EmployeeFilter plain = EmployeeFilter.all();
            // no conditions, no ranking: limit and offset alone
            assertEquals(2, parameters(EmployeeQuery.pageSql(plain, true)));

            EmployeeFilter searched = plain.withText("12");
            // four for the text, three for the ranking, two for the page
            assertEquals(9, parameters(EmployeeQuery.pageSql(searched, true)));
            assertEquals(4, parameters(EmployeeQuery.summarySql(searched, true)),
                    "the summary has no ranking and no page");
        }

        @Test
        @DisplayName("a search ranks an exact code or telephone above a name that starts with the text")
        void ranking() {
            String sql = EmployeeQuery.pageSql(EmployeeFilter.all().withText("عمر"), true);
            int exact = sql.indexOf("WHEN e.id = ? OR e.tel = ? THEN 0");
            int starts = sql.indexOf("WHEN e.column_name LIKE ? ESCAPE '!' THEN 1");
            assertTrue(exact > 0 && starts > exact,
                    "the three phases of the old search are one ORDER BY now: " + sql);
        }

        @Test
        @DisplayName("an unsearched list puts the people still working first, then the name")
        void defaultOrder() {
            assertTrue(EmployeeQuery.pageSql(EmployeeFilter.all(), true)
                    .contains("ORDER BY e.is_active DESC, e.column_name"));
        }
    }

    @Nested
    @DisplayName("the salary columns")
    class Salary {

        @Test
        @DisplayName("are not selected at all for a reader who may not see them")
        void notProjected() {
            String hidden = EmployeeQuery.pageSql(EmployeeFilter.all(), false);
            assertFalse(hidden.contains("e.salary AS hire_salary"),
                    "hiding a column afterwards is a UI hint; this is the enforcement: " + hidden);
            assertTrue(hidden.contains("NULL AS current_rate"));
            assertTrue(EmployeeQuery.pageSql(EmployeeFilter.all(), true).contains("c.rate AS current_rate"));
        }

        @Test
        @DisplayName("the row shape is identical either way, so one mapper reads both")
        void sameShape() {
            String with = EmployeeQuery.pageSql(EmployeeFilter.all(), true);
            String without = EmployeeQuery.pageSql(EmployeeFilter.all(), false);
            for (String column : new String[]{"hire_salary", "current_rate", "rate_from", "salary_kind"}) {
                assertTrue(with.contains(column) && without.contains(column), column + " is missing");
            }
        }

        @Test
        @DisplayName("the wage bill counts monthly rates of people still working, and nothing else")
        void payroll() {
            String sql = EmployeeQuery.summarySql(EmployeeFilter.all(), true);
            assertTrue(sql.contains("SUM(CASE WHEN e.is_active = 1 AND c.salary_kind = 'MONTHLY'"),
                    "adding a daily wage to a monthly salary is not anybody's wage bill: " + sql);
            assertTrue(EmployeeQuery.summarySql(EmployeeFilter.all(), false)
                            .contains("NULL AS monthly_payroll"),
                    "absence of an answer is not zero");
        }
    }

    @Nested
    @DisplayName("writing")
    class Writing {

        @Test
        @DisplayName("the update names neither the salary nor the status nor the picture")
        void updateLeavesAlone() {
            String sql = EmployeeQuery.UPDATE_SQL;
            assertFalse(sql.contains("salary"), "pay is a dated row: " + sql);
            assertFalse(sql.contains("is_active"), "the status has its own statement: " + sql);
            assertFalse(sql.contains("image"), "the picture has its own statement: " + sql);
            assertFalse(sql.contains("user_id"),
                    "user_id records who entered the employee, not who last edited them: " + sql);
        }

        @Test
        @DisplayName("every write binds exactly as many values as it names")
        void parameterCounts() {
            assertEquals(15, parameters(EmployeeQuery.INSERT_SQL));
            assertEquals(13, parameters(EmployeeQuery.UPDATE_SQL));
            assertEquals(2, parameters(EmployeeQuery.SET_ACTIVE_SQL));
            assertEquals(2, parameters(EmployeeQuery.UPDATE_IMAGE_SQL));
            assertEquals(2, parameters(EmployeeQuery.UPDATE_HIRE_SALARY_SQL));
            assertEquals(6, parameters(EmployeeQuery.INSERT_COMPENSATION_SQL));
            assertEquals(5, parameters(EmployeeQuery.UPDATE_COMPENSATION_SQL));
            assertEquals(2, parameters(EmployeeQuery.DELETE_COMPENSATION_SQL));
            assertEquals(6, parameters(EmployeeQuery.INSERT_JOB_SQL));
            assertEquals(6, parameters(EmployeeQuery.UPDATE_JOB_SQL));
        }

        @Test
        @DisplayName("the insert writes the hire salary, and it is the only statement that does")
        void hireSalaryWrittenOnce() {
            assertTrue(EmployeeQuery.INSERT_SQL.contains("salary"));
            assertTrue(EmployeeQuery.UPDATE_HIRE_SALARY_SQL.contains("SET salary = ?"));
        }

        @Test
        @DisplayName("a compensation row is replaced for the day it is dated, not appended twice")
        void compensationIsPerDay() {
            assertTrue(EmployeeQuery.UPDATE_COMPENSATION_SQL
                    .contains("WHERE employee_id = ? AND effective_from = ?"));
        }
    }

    @Nested
    @DisplayName("lookups")
    class Lookups {

        @Test
        @DisplayName("a combo for a new document offers only the people still working")
        void activeOnly() {
            String sql = EmployeeQuery.namesSql(EmployeeScope.ACTIVE_ONLY, true);
            assertTrue(sql.contains("j.is_delegate = 1"));
            assertTrue(sql.contains("e.is_active = 1"));
            assertTrue(sql.contains("j.is_active = 1"));
        }

        @Test
        @DisplayName("and a saved document can still resolve the delegate it was written with")
        void everyone() {
            String sql = EmployeeQuery.lookupSql(EmployeeScope.EVERYONE, false);
            assertFalse(sql.contains("is_active"),
                    "applying the status literally here is what would make an old document "
                            + "unreadable: " + sql);
        }

        @Test
        @DisplayName("a name is read as a name, never as a whole row")
        void namesOnly() {
            assertTrue(EmployeeQuery.namesSql(EmployeeScope.EVERYONE, false)
                    .startsWith("SELECT e.column_name FROM employees"));
            assertFalse(EmployeeQuery.namesSql(EmployeeScope.EVERYONE, false).contains("salary"));
        }
    }

    @Nested
    @DisplayName("search patterns")
    class Patterns {

        @Test
        @DisplayName("wildcards a person may legitimately type are escaped")
        void escaped() {
            assertEquals("%50!% off%", EmployeeQuery.containsPattern("50% off"));
            assertEquals("!_x%", EmployeeQuery.startsPattern("_x"));
            assertEquals("%!!%", EmployeeQuery.containsPattern("!"));
        }

        @Test
        @DisplayName("blank text produces a pattern that matches everything rather than throwing")
        void blank() {
            assertEquals("%%", EmployeeQuery.containsPattern(null));
        }
    }
}
