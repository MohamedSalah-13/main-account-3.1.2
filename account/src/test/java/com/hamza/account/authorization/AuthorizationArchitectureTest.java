package com.hamza.account.authorization;

import com.hamza.controlsfx.database.DaoException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationArchitectureTest {

    @Test
    void catalogueIsUniqueValidAndSelfDescribing() {
        assertTrue(AppPermissions.definitions().size() >= 100);
        var keys = new HashSet<String>();
        AppPermissions.definitions().forEach(definition -> {
            assertTrue(keys.add(definition.key().value()), "duplicate " + definition.key());
            assertFalse(definition.module().isBlank());
            assertFalse(definition.resource().isBlank());
            assertFalse(definition.action().isBlank());
            assertNotNull(definition.risk());
            assertFalse(definition.key().isMarker());
        });
    }

    @Test
    void menuActionsDeclarePublicAccessInsteadOfUsingNull() throws IOException {
        Path dash = Path.of("src", "main", "java", "com", "hamza", "account", "dash");
        try (var files = Files.walk(dash)) {
            var offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).matches(
                            "(?s).*PermissionKey\\s+getPermissionType\\(\\)\\s*\\{\\s*return\\s+null\\s*;.*"))
                    .toList();
            assertTrue(offenders.isEmpty(), "implicit public permission actions: " + offenders);
        }
    }

    @Test
    void missingPermissionDefinitionIsRejectedCleanly() {
        assertThrows(DaoException.class, () -> AuthorizationGuard.require(null));
    }

    @Test
    void sensitivePresentationUsesPermissionsInsteadOfAdministratorIds() {
        var files = java.util.List.of(
                Path.of("src/main/java/com/hamza/account/controller/items/ItemsController.java"),
                Path.of("src/main/java/com/hamza/account/controller/invoice/ShowInvoiceController.java"),
                Path.of("src/main/java/com/hamza/account/controller/others/EmployeesController.java"),
                Path.of("src/main/java/com/hamza/account/controller/main/MainScreenController.java"),
                Path.of("src/main/java/com/hamza/account/interfaces/impl_totalDesgin/TotalSalesImpDesign.java"),
                Path.of("src/main/java/com/hamza/account/interfaces/impl_totalDesgin/TotalSalesReturnImplDesign.java"));

        var offenders = files.stream()
                .filter(path -> read(path).matches("(?s).*usersVo\\.getId\\(\\)\\s*==\\s*1.*"))
                .toList();
        assertTrue(offenders.isEmpty(), "sensitive visibility tied to administrator id: " + offenders);
    }

    @Test
    void legacyAuthorizationTypesCannotReturn() throws IOException {
        Path source = Path.of("src", "main", "java");
        try (var files = Files.walk(source)) {
            var offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, "UserPermissionType")
                            || contains(path, "PermissionGuard")
                            || contains(path, "Users_Permission"))
                    .toList();
            assertTrue(offenders.isEmpty(), "legacy authorization references: " + offenders);
        }
    }

    @Test
    void controllersDoNotWriteBusinessRowsDirectlyThroughDaos() throws IOException {
        var roots = java.util.List.of(
                Path.of("src", "main", "java", "com", "hamza", "account", "controller"),
                Path.of("src", "main", "java", "com", "hamza", "account", "interfaces"),
                Path.of("src", "main", "java", "com", "hamza", "account", "features", "choiceDialoge"),
                Path.of("src", "main", "java", "com", "hamza", "account", "view"),
                Path.of("src", "main", "java", "com", "hamza", "account", "dash"));
        var offenders = new java.util.ArrayList<Path>();
        for (Path root : roots) {
            try (var files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> containsDirectDaoWrite(read(path)))
                        .forEach(offenders::add);
            }
        }
        assertTrue(offenders.isEmpty(), "writes must cross an authorized service boundary: " + offenders);
    }

    /**
     * Every service method that writes a row asks permission first.
     * <p>
     * This is the rule {@code CLAUDE.md} has claimed for some time - "it fails the build
     * when a service write path has no guard" - and until now no test made it true. The
     * closest was {@link #controllersDoNotWriteBusinessRowsDirectlyThroughDaos()}, which
     * says a screen must go through a service; it says nothing about whether the service
     * then checks anything. A service method with no {@code require} passed it happily,
     * and two of them did: opening and closing a shift, which any signed-in user could do
     * for anyone.
     * <p>
     * Hiding a button was never enforcement, and neither is routing through a service.
     * <p>
     * The check is per method, not per file: a class where {@code delete} is guarded and
     * {@code open} is not has to fail, and a file-level search cannot see that.
     */
    @Test
    void serviceWritePathsAskPermissionFirst() throws IOException {
        var offenders = new java.util.TreeSet<String>();
        for (Path root : SERVICE_ROOTS) {
            try (var files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith("Service.java"))
                        .forEach(path -> offenders.addAll(unguardedWrites(path)));
            }
        }

        var unexpected = new java.util.TreeSet<>(offenders);
        unexpected.removeAll(WRITES_WITHOUT_A_GUARD);
        assertTrue(unexpected.isEmpty(),
                "these service methods write a row without asking permission: " + unexpected
                        + ". Add AuthorizationGuard.require(...) as the first thing the method "
                        + "does - before the period lock, before the arithmetic - or, if it is "
                        + "genuinely a step inside an already-authorized operation, add it to "
                        + "WRITES_WITHOUT_A_GUARD with the reason.");

        var stale = new java.util.TreeSet<>(WRITES_WITHOUT_A_GUARD);
        stale.removeAll(offenders);
        assertTrue(stale.isEmpty(),
                "these are on the exception list but are guarded now: " + stale
                        + ". Delete them from WRITES_WITHOUT_A_GUARD - a list that outlives what "
                        + "it excuses is how an exception becomes invisible.");
    }

    /**
     * A service method handing over someone else's money figure asks permission too.
     * <p>
     * {@link #serviceWritePathsAskPermissionFirst()} watches writes, and every
     * architecture test in this suite did the same - which is why three separate leaks
     * lived here for months without any of them noticing. A read has no verb in its name
     * and touches no DAO write, so it is invisible to all of them:
     * <ul>
     *   <li>{@code ProfitLossService.load} - the profit, every sale's cost, every expense.
     *       Checked only by the sidebar button that opened the screen.</li>
     *   <li>{@code UserShiftService.getAllShifts} - every cashier's opening cash, closing
     *       cash, and the difference they answer for. Checked by nothing at all.</li>
     *   <li>{@code EmployeeService.getEmployeesList} - what everybody is paid. The screen
     *       hid the salary column from anyone without {@code employees.show.salary}; the
     *       figure was fetched and handed over regardless.</li>
     * </ul>
     * Hiding a button is not enforcement, and neither is hiding a column.
     * <p>
     * The rule is by <b>return type</b>, because that is what can be decided by reading:
     * a method whose return mentions one of {@link #TYPES_CARRYING_SOMEONE_ELSES_MONEY}
     * hands the caller a figure about a person, and must call {@code require}. It is not a
     * general "reads must be guarded" rule - most reads are catalogue data and are
     * rightly open. It is narrow on purpose: the point is the money, not the reading.
     */
    @Test
    void readsOfOtherPeoplesMoneyAskPermissionFirst() throws IOException {
        var offenders = new java.util.TreeSet<String>();
        for (Path root : SERVICE_ROOTS) {
            try (var files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith("Service.java"))
                        .forEach(path -> offenders.addAll(unguardedSensitiveReads(path)));
            }
        }

        var unexpected = new java.util.TreeSet<>(offenders);
        unexpected.removeAll(READS_SCOPED_TO_THE_CALLER);
        assertTrue(unexpected.isEmpty(),
                "these service methods hand over a figure about a person without asking "
                        + "permission: " + unexpected + ". Either require a permission, or - if "
                        + "the method only ever returns the caller their own, or carries no "
                        + "figure - add it to READS_SCOPED_TO_THE_CALLER with the reason.");

        var stale = new java.util.TreeSet<>(READS_SCOPED_TO_THE_CALLER);
        stale.removeAll(offenders);
        assertTrue(stale.isEmpty(),
                "these are on the exception list but are guarded now: " + stale
                        + ". Delete them from READS_SCOPED_TO_THE_CALLER.");
    }

    /**
     * Models that carry a figure about a person: a salary, or a till's cash and the
     * difference a cashier answers for, or a business's profit. Add a model here when it
     * gains such a column - that is the whole of the maintenance this rule asks for.
     */
    private static final java.util.List<String> TYPES_CARRYING_SOMEONE_ELSES_MONEY =
            java.util.List.of("Employees", "UserShift", "ShiftSummary", "ProfitLossRow");

    /**
     * The reads that hand over one of those types and correctly do not ask. Every one is
     * either scoped to the caller by its own argument, or open for a reason that outweighs
     * what it leaks - and where it is the latter, that is written down rather than implied.
     */
    private static final java.util.Set<String> READS_SCOPED_TO_THE_CALLER = java.util.Set.of(
            // The text-based scanner sees the constructor's class name as a return type.
            // A constructor hands no row to a caller and therefore has nothing to guard.
            "UserShiftService#UserShiftService",

            // The delegate on an invoice. Cashiers write invoices, so guarding these breaks
            // the delegate combo on every invoice screen - which is worse than what they
            // leak. But they DO leak: a delegate is an Employees and the model carries
            // salary. The real fix is a projection of id and name, which is a change to the
            // model and its callers rather than a line in a service, and until then the
            // invoice screens use getDelegateNames, which is already only names.
            "EmployeeService#getDelegateList",
            "EmployeeService#getDelegateByName",
            "EmployeeService#getDelegateById");

    /** The methods in one service file that return such a type and never call the guard. */
    private static java.util.List<String> unguardedSensitiveReads(Path path) {
        String source = read(path);
        String type = path.getFileName().toString().replace(".java", "");
        var offenders = new java.util.ArrayList<String>();
        var signature = java.util.regex.Pattern
                .compile("(?m)^    (?:public|protected)[^;{=]*\\{").matcher(source);
        while (signature.find()) {
            String declaration = signature.group();
            int parameters = declaration.indexOf('(');
            if (parameters < 0) {
                continue;
            }
            String returned = declaration.substring(0, parameters);
            if (TYPES_CARRYING_SOMEONE_ELSES_MONEY.stream().noneMatch(returned::contains)) {
                continue;
            }
            String body = bodyOf(source, signature.end() - 1);
            if (!body.contains("AuthorizationGuard.require(")) {
                offenders.add(type + "#" + methodName(declaration));
            }
        }
        return offenders;
    }

    private static final java.util.List<Path> SERVICE_ROOTS = java.util.List.of(
            Path.of("src", "main", "java", "com", "hamza", "account", "service"),
            Path.of("src", "main", "java", "com", "hamza", "account", "features"));

    /**
     * The four methods that write without a guard of their own. Two are fine and two are
     * debt, and they are listed together because the test cannot tell them apart - only a
     * reader can, which is what these comments are for.
     */
    private static final java.util.Set<String> WRITES_WITHOUT_A_GUARD = java.util.Set.of(
            // Fine: a read that seeds the single company row when the table is empty, so
            // the settings screen has something to open. Not a user write path.
            "CompanyService#load",

            // Fine: not reachable on its own. Both callers - AccountCustomerService.save
            // and AccountSupplierService.save - require the account permission before
            // they get here, and the fee is written in their transaction. Guarding it
            // again would ask permission twice for one operation.
            "WalletFeeService#post");

    /** The methods in one service file that write a row and never call the guard. */
    private static java.util.List<String> unguardedWrites(Path path) {
        String source = read(path);
        String type = path.getFileName().toString().replace(".java", "");
        var offenders = new java.util.ArrayList<String>();
        var signature = java.util.regex.Pattern
                .compile("(?m)^    (?:public|protected)[^;{=]*\\{").matcher(source);
        while (signature.find()) {
            String body = bodyOf(source, signature.end() - 1);
            if (containsDirectDaoWrite(body) && !body.contains("AuthorizationGuard.require(")) {
                offenders.add(type + "#" + methodName(signature.group()));
            }
        }
        return offenders;
    }

    /** From the opening brace to the one that closes it, nesting counted. */
    private static String bodyOf(String source, int openingBrace) {
        int depth = 0;
        for (int i = openingBrace; i < source.length(); i++) {
            char character = source.charAt(i);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(openingBrace, i);
            }
        }
        return source.substring(openingBrace);
    }

    /** The identifier before the parameter list - {@code save} in {@code public int save(}. */
    private static String methodName(String signature) {
        String beforeParameters = signature.substring(0, signature.indexOf('('));
        String[] words = beforeParameters.trim().split("[\\s<>\\[\\]]+");
        return words[words.length - 1];
    }

    private static boolean containsDirectDaoWrite(String source) {
        // Covers fields such as treasuryDao.insert(...), accessors such as
        // accountDao().deleteById(...), and longer generic seams ending in totalDao().
        return source.matches("(?s).*(?:[A-Za-z0-9_]+Dao)(?:\\(\\))?\\s*\\.\\s*"
                + "(?:insert[A-Za-z0-9_]*|update|deleteById|deleteInvoicesInRange)\\s*\\(.*");
    }

    private static boolean contains(Path path, String text) {
        return read(path).contains(text);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
