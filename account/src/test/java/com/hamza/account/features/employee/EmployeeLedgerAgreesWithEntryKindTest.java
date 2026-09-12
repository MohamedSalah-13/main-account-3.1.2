package com.hamza.account.features.employee;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds {@link EmployeeEntryKind} and the schema to the same statement about direction.
 * <p>
 * <b>Two definitions of a direction is the defect this exists to prevent</b>, and it is not a
 * hypothetical one: the party ledger carried two for months — the views decided a deferred return
 * for themselves while {@code DocumentLedgerEffect} said something else — and closing it cost a
 * data migration ({@code V15}) and a screen that had been showing customers the wrong balance.
 * <p>
 * The direction is declared once, in {@link EmployeeEntryKind#sign()}. The view restates it as a
 * {@code CASE} because SQL cannot call Java, and {@code V58} restates the list of names again as a
 * CHECK. This test reads both files and refuses to let the three drift.
 */
class EmployeeLedgerAgreesWithEntryKindTest {

    private static String read(String resource) {
        try (InputStream in = EmployeeLedgerAgreesWithEntryKindTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing on the classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The employee_account_table definition, from its DROP to the semicolon that ends it. */
    private static String accountView() {
        String views = read("db/migration/R__views.sql");
        int start = views.indexOf("CREATE VIEW employee_account_table AS");
        assertTrue(start > 0, "employee_account_table is not in R__views.sql");
        int end = views.indexOf(';', start);
        return views.substring(start, end);
    }

    /** The names inside one {@code IN (...)} list of the view's CASE. */
    private static Set<String> namesIn(String fragment) {
        Set<String> names = new LinkedHashSet<>();
        Matcher name = Pattern.compile("'([A-Z_]+)'").matcher(fragment);
        while (name.find()) {
            names.add(name.group(1));
        }
        return names;
    }

    private static Set<String> creditKindsInTheView() {
        String view = accountView();
        int credit = view.indexOf("AS credit");
        int start = view.lastIndexOf("CASE", credit);
        return namesIn(view.substring(start, credit));
    }

    private static Set<String> debitKindsInTheView() {
        String view = accountView();
        int debit = view.indexOf("AS debit");
        int start = view.lastIndexOf("CASE", debit);
        return namesIn(view.substring(start, debit));
    }

    @Test
    @DisplayName("the file really was read - the rest of this class would pass vacuously")
    void theViewWasRead() {
        assertFalse(creditKindsInTheView().isEmpty(), "no credit kinds parsed out of the view");
        assertFalse(debitKindsInTheView().isEmpty(), "no debit kinds parsed out of the view");
    }

    @Test
    @DisplayName("every kind the view credits has a positive sign in Java")
    void creditKindsAgree() {
        for (String name : creditKindsInTheView()) {
            EmployeeEntryKind kind = EmployeeEntryKind.of(name);
            assertEquals(1, kind.sign(),
                    name + " is credited by employee_account_table but sign() says otherwise - "
                            + "the statement and the balance would disagree about it");
        }
    }

    @Test
    @DisplayName("every kind the view debits has a negative sign in Java")
    void debitKindsAgree() {
        for (String name : debitKindsInTheView()) {
            EmployeeEntryKind kind = EmployeeEntryKind.of(name);
            assertEquals(-1, kind.sign(),
                    name + " is debited by employee_account_table but sign() says otherwise");
        }
    }

    @Test
    @DisplayName("every kind appears in exactly one of the two columns")
    void everyKindIsInExactlyOneColumn() {
        Set<String> credit = creditKindsInTheView();
        Set<String> debit = debitKindsInTheView();

        for (EmployeeEntryKind kind : EmployeeEntryKind.values()) {
            boolean credited = credit.contains(kind.name());
            boolean debited = debit.contains(kind.name());
            assertTrue(credited || debited,
                    kind + " is in neither column of employee_account_table, so a row of that "
                            + "kind would be read as zero on both sides and vanish from the balance");
            assertFalse(credited && debited,
                    kind + " is in both columns, so a row of that kind would count twice");
        }
    }

    @Test
    @DisplayName("the CHECK in V58 accepts exactly the kinds the enum declares")
    void theSchemaAcceptsExactlyTheseKinds() {
        String migration = read("db/migration/V58__employee_ledger.sql");
        // The list up to the bracket that closes IN, wherever the file happens to wrap it - the
        // first draft of this test looked for a literal "));" and the migration writes the two
        // brackets on separate lines, so it read to the end of the file and threw.
        Matcher constraint = Pattern.compile(
                "employee_ledger_kind_chk\\s*CHECK \\(kind IN \\(([^)]*)\\)", Pattern.DOTALL)
                .matcher(migration);
        assertTrue(constraint.find(), "the kind CHECK is not in V58");
        Set<String> accepted = namesIn(constraint.group(1));

        Set<String> declared = Arrays.stream(EmployeeEntryKind.values())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(declared, accepted,
                "the database would accept a kind Java cannot read, or refuse one it writes - "
                        + "and the first is a row that draws as its own code on the statement "
                        + "while the second is a save that fails with a constraint name");
    }

    @Test
    @DisplayName("the cash half of the view is unconditionally a debit")
    void cashIsAlwaysADebit() {
        String view = accountView();
        int union = view.indexOf("UNION ALL");
        assertTrue(union > 0, "the view no longer unions the two halves: " + view);
        String cash = view.substring(union).replaceAll("[ \t]+", " ");
        assertTrue(cash.contains("0 AS credit"),
                "every pound paid to an employee reduces what is owed them - there is no "
                        + "direction to decide there, and no enum agreement to keep: " + cash);
        assertTrue(cash.contains("ROUND(ed.amount, 2) AS debit"),
                "the cash half must put the whole amount on the debit side: " + cash);
    }
}
