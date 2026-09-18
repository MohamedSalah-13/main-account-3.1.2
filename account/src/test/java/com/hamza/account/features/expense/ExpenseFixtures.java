package com.hamza.account.features.expense;

import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.account.treasury.TreasuryBalanceSummary;
import com.hamza.controlsfx.database.DaoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory stand-ins for the expense repositories, and a session to sign in with.
 * <p>
 * <b>The session is never user 1</b>: {@code isSystemAdministrator()} is {@code currentUserId() == 1} and
 * bypasses every permission, so a test signed in as user 1 proves nothing about authorization.
 */
final class ExpenseFixtures {

    static final int OPERATOR = 9;
    static final LocalDate DAY = LocalDate.of(2026, 9, 10);

    static final ExpenseHeading ELECTRICITY = new ExpenseHeading(2, "كهرباء", null, null, true, null, false);
    static final ExpenseHeading SALARIES = new ExpenseHeading(1, "مرتبات", null, null, true, null, true);
    static final ExpenseHeading OLD_RENT = new ExpenseHeading(5, "إيجار قديم", null, null, false, null, false);
    static final ExpenseHeading WALLET_FEE = new ExpenseHeading(7, "عمولات تحويل", null, null, true,
            ExpenseHeading.WALLET_FEE, false);

    private ExpenseFixtures() {
    }

    static void signInWith(PermissionKey... granted) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(OPERATOR, "operator", Arrays.asList(granted));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    static ExpenseEntry entry(int headingId, int treasuryId, String amount) {
        return new ExpenseEntry(0, DAY, headingId, treasuryId, new BigDecimal(amount), null, null, null);
    }

    static ExpenseRow row(int id, ExpenseHeading heading, int treasuryId, String amount, Integer employeeId) {
        return new ExpenseRow(id, DAY, heading.id(), heading.name(), null, heading.employeePayment(), treasuryId,
                "الخزينة", new BigDecimal(amount), null, null, null, employeeId,
                employeeId == null ? null : "أحمد", OPERATOR, "operator", null, LocalDateTime.of(2026, 9, 10, 9, 0));
    }

    static TreasuryBalanceSummary till(int id, String balance) {
        BigDecimal figure = new BigDecimal(balance);
        return new TreasuryBalanceSummary(id, "till-" + id, null, true, 0, null, BigDecimal.ZERO, figure,
                BigDecimal.ZERO, figure);
    }

    /** Every write it was asked to make, and what it answers reads with. */
    static final class Repository implements ExpenseRepository {

        record Insert(ExpenseEntry entry, Integer employeeId, Integer shiftId, int userId,
                      Integer recurringId) {
        }

        final List<Insert> inserts = new ArrayList<>();
        final List<ExpenseEntry> updates = new ArrayList<>();
        final List<ExpenseFilter> summarized = new ArrayList<>();
        final Map<Integer, ExpenseRow> rows = new HashMap<>();
        final Map<LocalDate, BigDecimal> totalsByFrom = new HashMap<>();
        List<ExpenseRow> page = List.of();

        @Override
        public List<ExpenseRow> search(ExpenseFilter filter) {
            return page;
        }

        @Override
        public ExpenseSummary summarize(ExpenseFilter filter) {
            summarized.add(filter);
            BigDecimal total = totalsByFrom.getOrDefault(filter.from(), BigDecimal.ZERO);
            return new ExpenseSummary(total.signum() == 0 ? 0 : 1, total, null, null, null);
        }

        @Override
        public ExpenseRow find(int id) {
            return rows.get(id);
        }

        @Override
        public List<ExpenseUserOption> users() {
            return List.of(new ExpenseUserOption(OPERATOR, "operator"));
        }

        @Override
        public List<String> payees(String prefix, int limit) {
            return List.of(prefix + "-payee");
        }

        @Override
        public int insert(ExpenseEntry entry, Integer employeeId, Integer shiftId, int userId,
                          Integer recurringId) {
            inserts.add(new Insert(entry, employeeId, shiftId, userId, recurringId));
            return 100 + inserts.size();
        }

        @Override
        public int update(ExpenseEntry entry) {
            updates.add(entry);
            return 1;
        }

        @Override
        public int delete(int id) {
            return rows.remove(id) == null ? 0 : 1;
        }
    }

    static final class Headings implements ExpenseHeadingRepository {

        final Map<Integer, ExpenseHeading> byId = new LinkedHashMap<>();
        final List<ExpenseHeadingDraft> saved = new ArrayList<>();
        final List<Integer> deleted = new ArrayList<>();
        boolean nameTaken;

        Headings(ExpenseHeading... headings) {
            for (ExpenseHeading heading : headings) {
                byId.put(heading.id(), heading);
            }
        }

        @Override
        public List<ExpenseHeading> all() {
            return List.copyOf(byId.values());
        }

        @Override
        public ExpenseHeading find(int id) {
            return byId.get(id);
        }

        @Override
        public ExpenseHeading bySystemKey(String systemKey) {
            return byId.values().stream().filter(heading -> systemKey.equals(heading.systemKey())).findFirst()
                    .orElse(null);
        }

        @Override
        public Map<Integer, ExpenseHeadingUsage> usage(LocalDate since) {
            return Map.of();
        }

        @Override
        public boolean nameTaken(String name, int exceptId) {
            return nameTaken;
        }

        @Override
        public int insert(ExpenseHeadingDraft draft, int userId) {
            saved.add(draft);
            return 50;
        }

        @Override
        public int update(ExpenseHeadingDraft draft) throws DaoException {
            saved.add(draft);
            return 1;
        }

        @Override
        public int delete(int id) {
            deleted.add(id);
            return 1;
        }
    }
}
