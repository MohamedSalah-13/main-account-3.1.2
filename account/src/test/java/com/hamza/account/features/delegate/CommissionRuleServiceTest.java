package com.hamza.account.features.delegate;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the service decides before it reaches a database.
 *
 * <p><b>The session is never user 1</b>, who bypasses every permission - a test signed in as
 * user 1 proves nothing about authorization.
 *
 * <p>Not covered here, and said rather than implied: what happens inside
 * {@code TransactionTemplate} - the delegate check and the write itself. Those open a real
 * transaction; {@code CommissionRuleDatabaseAcceptanceTest} is their proof.
 */
class CommissionRuleServiceTest {

    private static final List<CommissionTiers.Tier> FLAT =
            List.of(new CommissionTiers.Tier(BigDecimal.ZERO, new BigDecimal("2")));

    private final FakeRules rules = new FakeRules();
    private final CommissionRuleService service = new CommissionRuleService(rules, new NoRuns());

    private void signInWith(PermissionKey... granted) {
        UserSessionContext session = new UserSessionContext();
        session.signIn(9, "operator", Arrays.asList(granted));
        ServiceRegistry.register(UserSessionContext.class, session);
    }

    /** A rate is a figure about a person: it is not read for somebody who may not see one. */
    @Test
    void readingARuleNeedsItsOwnPermission() {
        signInWith(AppPermissions.EMPLOYEE_SHOW, AppPermissions.EMPLOYEES_SHOW_SALARY);
        assertThrows(BusinessRuleException.class, () -> service.history(5));
        assertThrows(BusinessRuleException.class, () -> service.ruleForMonth(5, 2026, 10));
        assertEquals(0, rules.reads, "refused before any query, not fetched and then withheld");
    }

    /** Seeing the rules is not deciding them. */
    @Test
    void writingARuleNeedsMoreThanSeeingOne() {
        signInWith(AppPermissions.COMMISSION_SHOW, AppPermissions.EMPLOYEE_UPDATE);
        assertThrows(BusinessRuleException.class, () -> service.save(5, LocalDate.of(2026, 10, 1),
                CommissionBasis.SALES, TierMode.WHOLE, BigDecimal.ZERO, FLAT, null));
        assertThrows(BusinessRuleException.class, () -> service.remove(5, 1));
    }

    /**
     * A month is judged by the rule in force on its <b>first</b> day - so a month is never split
     * between two targets, and a harder target cannot be applied once the month's sales are in.
     */
    @Test
    void aMonthAsksForTheRuleOfItsFirstDay() throws Exception {
        signInWith(AppPermissions.COMMISSION_SHOW);
        service.ruleForMonth(5, 2026, 2);
        assertEquals(LocalDate.of(2026, 2, 1), rules.askedDay);
        assertEquals(5, rules.askedEmployee);
    }

    @Test
    void aRuleWithoutADateIsRefusedWithTheKeyOfTheSentence() {
        signInWith(AppPermissions.COMMISSION_RULE_UPDATE);
        UserValidationException refusal = assertThrows(UserValidationException.class,
                () -> service.save(5, null, CommissionBasis.SALES, TierMode.WHOLE, BigDecimal.ZERO, FLAT, null));
        assertEquals("commission.error.rule.date", refusal.getMessage());
    }

    /** What the records refuse reaches the user as a validation message, not as a reference code. */
    @Test
    void whatTheRuleRefusesIsAValidationMessage() {
        signInWith(AppPermissions.COMMISSION_RULE_UPDATE);
        List<CommissionTiers.Tier> needsTarget = List.of(
                new CommissionTiers.Tier(new BigDecimal("80"), new BigDecimal("2")));

        UserValidationException noTarget = assertThrows(UserValidationException.class,
                () -> service.save(5, LocalDate.of(2026, 10, 1), CommissionBasis.SALES, TierMode.WHOLE,
                        BigDecimal.ZERO, needsTarget, null));
        assertEquals("commission.error.target.missing", noTarget.getMessage());

        UserValidationException negative = assertThrows(UserValidationException.class,
                () -> service.save(5, LocalDate.of(2026, 10, 1), CommissionBasis.SALES, TierMode.WHOLE,
                        new BigDecimal("-1"), FLAT, null));
        assertEquals("commission.error.target.negative", negative.getMessage());

        UserValidationException noTiers = assertThrows(UserValidationException.class,
                () -> service.save(5, LocalDate.of(2026, 10, 1), CommissionBasis.SALES, TierMode.WHOLE,
                        BigDecimal.TEN, List.of(), null));
        assertEquals("commission.error.tier.count", noTiers.getMessage());
        assertTrue(rules.writes == 0);
    }

    /** No month has been computed under any rule - the state of every shop before its first run. */
    private static final class NoRuns implements CommissionRunRepository {
        @Override
        public Optional<Integer> activeRunId(java.time.YearMonth period) {
            return Optional.empty();
        }

        @Override
        public int insertRun(java.time.YearMonth period, String notes, int userId) {
            return 0;
        }

        @Override
        public int insertLine(int runId, CommissionLine line) {
            return 0;
        }

        @Override
        public List<CommissionRun> runs() {
            return List.of();
        }

        @Override
        public Optional<CommissionRun> run(int runId) {
            return Optional.empty();
        }

        @Override
        public List<CommissionLine> linesOf(int runId) {
            return List.of();
        }

        @Override
        public int cancel(int runId, int userId, String reason) {
            return 0;
        }

        @Override
        public List<Unposted> unpostedLinesForUpdate(int runId) {
            return List.of();
        }

        @Override
        public int insertLedgerCommission(int employeeId, LocalDate date, BigDecimal amount, String notes,
                                          int userId) {
            return 0;
        }

        @Override
        public int insertAccountPosting(int lineId, int ledgerEntryId, int userId) {
            return 0;
        }

        @Override
        public BigDecimal payrollDue(int employeeId, java.time.YearMonth period) {
            return BigDecimal.ZERO;
        }

        @Override
        public int insertPayrollPostings(int payrollRunId, int userId, int employeeId, java.time.YearMonth period) {
            return 0;
        }

        @Override
        public boolean ruleOfDayUsed(int employeeId, LocalDate effectiveFrom) {
            return false;
        }

        @Override
        public boolean ruleUsed(int ruleId) {
            return false;
        }
    }

    private static final class FakeRules implements CommissionRuleRepository {
        int reads;
        int writes;
        int askedEmployee;
        LocalDate askedDay;

        @Override
        public List<CommissionRule> history(int employeeId) {
            reads++;
            return List.of();
        }

        @Override
        public Optional<CommissionRule> inForceOn(int employeeId, LocalDate day) {
            reads++;
            askedEmployee = employeeId;
            askedDay = day;
            return Optional.empty();
        }

        @Override
        public int save(CommissionRule rule, int userId) throws DaoException {
            writes++;
            return 1;
        }

        @Override
        public int delete(int employeeId, int ruleId) {
            writes++;
            return 1;
        }

        @Override
        public boolean isDelegate(int employeeId) {
            return true;
        }
    }
}
