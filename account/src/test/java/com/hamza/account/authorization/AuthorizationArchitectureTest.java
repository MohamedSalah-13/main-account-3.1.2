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

    private static boolean containsDirectDaoWrite(String source) {
        // Covers fields such as treasuryDao.insert(...), accessors such as
        // accountDao().deleteById(...), and longer generic seams ending in totalDao().
        return source.matches("(?s).*(?:[A-Za-z0-9_]+Dao)(?:\\(\\))?\\s*\\.\\s*"
                + "(?:insert|update|deleteById|deleteInvoicesInRange)\\s*\\(.*");
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
