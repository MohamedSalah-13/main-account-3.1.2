package com.hamza.account.features.expense.report;

import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.expense.ExpenseFilter;
import com.hamza.account.features.expense.ExpenseHeading;
import com.hamza.account.features.expense.ExpenseHeadingDraft;
import com.hamza.account.features.expense.ExpenseHeadingRepository;
import com.hamza.account.features.expense.ExpenseHeadingUsage;
import com.hamza.account.features.rbac.UserSessionContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Headings, amounts and a signed-in user for the report tests. */
final class ReportFixtures {

    /** A main heading with two headings under it, a main heading with none, and a stopped one. */
    static final ExpenseHeading ADMIN = new ExpenseHeading(10, "إدارية", null, null, true, null, false);
    static final ExpenseHeading ELECTRICITY = new ExpenseHeading(11, "كهرباء", 10, "إدارية", true, null, false);
    static final ExpenseHeading WATER = new ExpenseHeading(12, "مياه", 10, "إدارية", true, null, false);
    static final ExpenseHeading RENT = new ExpenseHeading(20, "إيجار", null, null, true, null, false);
    static final ExpenseHeading OLD = new ExpenseHeading(30, "قديم", null, null, false, null, false);
    static final List<ExpenseHeading> HEADINGS = List.of(ADMIN, ELECTRICITY, WATER, RENT, OLD);

    static final ExpenseFilter SEPTEMBER = ExpenseFilter.between(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    private ReportFixtures() {
    }

    static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    static ExpenseReportRows.HeadingTotal total(int heading, int count, String amount) {
        return new ExpenseReportRows.HeadingTotal(heading, count, money(amount));
    }

    static ExpenseReportRows.Day day(String date, String amount) {
        return new ExpenseReportRows.Day(LocalDate.parse(date), money(amount));
    }

    /** User 2, never user 1: user 1 bypasses every permission and would prove nothing. */
    static void signInWith(PermissionKey... granted) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(2, "operator", Arrays.asList(granted));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    static void signOut() {
        ServiceRegistry.register(UserSessionContext.class, null);
    }

    /** The headings, read-only. */
    static final class Headings implements ExpenseHeadingRepository {
        final List<String> calls = new ArrayList<>();

        @Override
        public List<ExpenseHeading> all() {
            calls.add("all");
            return HEADINGS;
        }

        @Override
        public ExpenseHeading find(int id) {
            return HEADINGS.stream().filter(heading -> heading.id() == id).findFirst().orElse(null);
        }

        @Override
        public ExpenseHeading bySystemKey(String systemKey) {
            return null;
        }

        @Override
        public Map<Integer, ExpenseHeadingUsage> usage(LocalDate since) {
            return Map.of();
        }

        @Override
        public boolean nameTaken(String name, int exceptId) {
            throw new AssertionError("a report writes nothing");
        }

        @Override
        public int insert(ExpenseHeadingDraft draft, int userId) {
            throw new AssertionError("a report writes nothing");
        }

        @Override
        public int update(ExpenseHeadingDraft draft) {
            throw new AssertionError("a report writes nothing");
        }

        @Override
        public int delete(int id) {
            throw new AssertionError("a report writes nothing");
        }
    }

    /** Answers fixed rows and records what it was asked, in order. */
    static final class Recording implements ExpenseReportRepository {
        final List<String> calls = new ArrayList<>();
        List<ExpenseReportRows.HeadingTotal> headingTotals = List.of();
        List<ExpenseReportRows.Day> days = List.of();
        List<ExpenseReportRows.Day> sales = List.of();

        @Override
        public List<ExpenseReportRows.HeadingTotal> headingTotals(ExpenseFilter filter) {
            calls.add("headings " + filter.from() + ".." + filter.to());
            return headingTotals;
        }

        @Override
        public List<ExpenseReportRows.HeadingDay> headingDays(ExpenseFilter filter) {
            calls.add("heading days " + filter.from() + ".." + filter.to());
            return List.of();
        }

        @Override
        public List<ExpenseReportRows.Day> days(ExpenseFilter filter) {
            calls.add("days " + filter.from() + ".." + filter.to());
            return days;
        }

        @Override
        public List<ExpenseReportRows.DimensionTotal> dimension(ExpenseFilter filter, ExpenseDimension dimension) {
            calls.add("dimension " + dimension);
            return List.of();
        }

        @Override
        public List<ExpenseReportRows.Day> netSalesDays(LocalDate from, LocalDate to) {
            calls.add("sales " + from + ".." + to);
            return sales;
        }
    }
}
