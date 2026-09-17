package com.hamza.account.features.expense;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.hamza.account.features.expense.ExpenseFixtures.ELECTRICITY;
import static com.hamza.account.features.expense.ExpenseFixtures.OLD_RENT;
import static com.hamza.account.features.expense.ExpenseFixtures.SALARIES;
import static com.hamza.account.features.expense.ExpenseFixtures.WALLET_FEE;
import static com.hamza.account.features.expense.ExpenseFixtures.signInWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the headings service decides. Deleting through {@code DeletionService} needs its registry and a
 * database, so a delete here is only ever refused before it gets there.
 */
class ExpenseHeadingServiceTest {

    private final ExpenseFixtures.Headings repository =
            new ExpenseFixtures.Headings(ELECTRICITY, SALARIES, OLD_RENT, WALLET_FEE);
    private final ExpenseHeadingService service = new ExpenseHeadingService(repository);

    @Test
    @DisplayName("each screen is offered its own headings, and a stopped one is offered to neither")
    void pickers() throws Exception {
        assertEquals(List.of(ELECTRICITY, WALLET_FEE), service.forExpenses());
        assertEquals(List.of(SALARIES), service.forEmployeePayments());
        assertEquals(4, service.all().size(), "the headings screen shows the stopped as well");
    }

    @Test
    @DisplayName("saving asks expenses.headings.update, and a refusal writes nothing")
    void savingIsGuarded() {
        signInWith(AppPermissions.EXPENSES_UPDATE, AppPermissions.EXPENSES_SHOW);
        assertThrows(BusinessRuleException.class,
                () -> service.save(new ExpenseHeadingDraft(0, "صيانة", null, true, false)));
        assertTrue(repository.saved.isEmpty());
    }

    @Test
    @DisplayName("a name already taken is refused with a sentence before the index has to")
    void nameTaken() {
        signInWith(AppPermissions.EXPENSES_HEADINGS_UPDATE);
        repository.nameTaken = true;
        UserValidationException refused = assertThrows(UserValidationException.class,
                () -> service.save(new ExpenseHeadingDraft(0, "كهرباء", null, true, false)));
        assertEquals("expense.heading.error.name.taken", refused.getMessage());
    }

    @Test
    @DisplayName("a new heading answers its generated code; an edit answers its own")
    void saveAnswersTheCode() throws Exception {
        signInWith(AppPermissions.EXPENSES_HEADINGS_UPDATE);
        assertEquals(50, service.save(new ExpenseHeadingDraft(0, "صيانة", null, true, false)));
        assertEquals(ELECTRICITY.id(), service.save(new ExpenseHeadingDraft(ELECTRICITY.id(), "الكهرباء",
                null, true, false)));
    }

    @Test
    @DisplayName("the heading the system depends on is refused a delete before the registry is asked")
    void systemHeadingIsNotDeleted() {
        signInWith(AppPermissions.EXPENSES_HEADINGS_UPDATE);
        UserValidationException refused = assertThrows(UserValidationException.class,
                () -> service.delete(WALLET_FEE.id()));
        assertEquals("expense.heading.error.system.delete", refused.getMessage());
        assertTrue(repository.deleted.isEmpty());
    }

    @Test
    @DisplayName("the figures beside the headings ask the permission the expenses list asks")
    void usageIsGuarded() {
        signInWith(AppPermissions.EXPENSES_HEADINGS_UPDATE);
        assertThrows(BusinessRuleException.class, () -> service.usage(LocalDate.of(2026, 1, 1)));
    }
}
