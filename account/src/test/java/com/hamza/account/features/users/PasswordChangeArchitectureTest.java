package com.hamza.account.features.users;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordChangeArchitectureTest {

    @Test
    void accountSpecificPasswordUiDoesNotReturnToControlsfx() throws IOException {
        Path controlsfx = Path.of("..", "controlsfx", "src", "main");
        assertFalse(Files.exists(controlsfx.resolve(
                "java/com/hamza/controlsfx/controller/ChangePassController.java")));
        assertFalse(Files.exists(controlsfx.resolve(
                "java/com/hamza/controlsfx/view/ChangePassApplication.java")));
        assertFalse(Files.exists(controlsfx.resolve(
                "java/com/hamza/controlsfx/interfaceData/ChangePassInt.java")));
        assertFalse(Files.exists(controlsfx.resolve(
                "resources/com/hamza/controlsfx/view/changePass-view.fxml")));

        String accountView = Files.readString(Path.of("src/main/java/com/hamza/account/view/ChangePassView.java"));
        assertFalse(accountView.contains("com.hamza.controlsfx.controller.ChangePassController"));
        assertFalse(accountView.contains("com.hamza.controlsfx.view.ChangePassApplication"));
        assertTrue(accountView.contains("com.hamza.account.controller.users.ChangePassController"));
    }

    @Test
    void passwordAndForcedChangeFlagAreUpdatedAtomically() throws IOException {
        String dao = Files.readString(Path.of(
                "src/main/java/com/hamza/account/model/dao/UsersDao.java"));
        int method = dao.indexOf("int updateOwnPassword(");
        assertTrue(method >= 0, "the credential-specific DAO operation is missing");
        String body = dao.substring(method, Math.min(dao.length(), method + 500))
                .replaceAll("\\s+", " ");

        assertTrue(body.contains("UPDATE users SET user_pass = ?, must_change_password = 0 WHERE id = ?"),
                "the new hash and clearing the forced-change flag must stay in one SQL statement");
    }

    @Test
    void successfulSaveLeavesBusyStateBeforeClosingDialog() throws IOException {
        String controller = Files.readString(Path.of(
                "src/main/java/com/hamza/account/controller/users/ChangePassController.java"));
        int succeeded = controller.indexOf("task.setOnSucceeded");
        int failed = controller.indexOf("task.setOnFailed", succeeded);
        assertTrue(succeeded >= 0 && failed > succeeded, "the password-save handlers are missing");

        String successHandler = controller.substring(succeeded, failed);
        int leaveBusyState = successHandler.indexOf("setBusy(false);");
        int closeDialog = successHandler.indexOf("close(true);");
        assertTrue(leaveBusyState >= 0,
                "a successful save must restore the controller from its busy state");
        assertTrue(closeDialog > leaveBusyState,
                "close(boolean) refuses to close while busy, so busy must be cleared first");
    }

    @Test
    void passwordDialogKeepsItsContentProportionedAtBothWindowExtremes() throws IOException {
        String fxml = Files.readString(Path.of(
                "src/main/resources/com/hamza/account/view/change-password.fxml"));
        assertTrue(fxml.contains("alignment=\"TOP_CENTER\""),
                "the naturally-sized card should remain anchored at the top when maximized");
        assertTrue(fxml.contains("maxHeight=\"-Infinity\""),
                "the card must keep its computed height instead of stretching into empty space");
        assertTrue(fxml.contains("fx:id=\"strengthProgress\" minHeight=\"7.0\" prefHeight=\"7.0\""),
                "the strength meter needs a non-compressible height at the minimum dialog size");
        assertTrue(fxml.contains("fx:id=\"actionsBar\""),
                "the action row must have an explicit direction independent from text direction");
    }

    @Test
    void everyPasswordChangeStatementHasAnExplicitDatabaseTimeout() throws IOException {
        String dao = Files.readString(Path.of(
                "src/main/java/com/hamza/account/model/dao/UsersDao.java"));
        int start = dao.indexOf("boolean requiresPasswordChange(");
        int end = dao.indexOf("int updateAdministratorPassword(", start);
        assertTrue(start >= 0 && end > start, "the password-change DAO boundary is missing");

        String passwordChangeBoundary = dao.substring(start, end);
        assertEquals(3, occurrences(passwordChangeBoundary,
                        "setQueryTimeout(PASSWORD_CHANGE_TIMEOUT_SECONDS)"),
                "the requirement read, credential read, and password update must all be bounded");
    }

    @Test
    void submitCannotLaunchASecondSaveWhileTheFirstIsRunning() throws IOException {
        String controller = Files.readString(Path.of(
                "src/main/java/com/hamza/account/controller/users/ChangePassController.java"));
        int start = controller.indexOf("private void submit()");
        int end = controller.indexOf("private void showValidation", start);
        assertTrue(start >= 0 && end > start, "the password submit boundary is missing");

        String submit = controller.substring(start, end);
        int guard = submit.indexOf("if (busy) return;");
        int enterBusyState = submit.indexOf("setBusy(true);");
        int startThread = submit.indexOf("thread.start();");
        assertTrue(guard >= 0 && enterBusyState > guard && startThread > enterBusyState,
                "the busy guard must run before a second background save can be launched");
    }

    @Test
    void usersAuditRecordsTheActorButNeverTheCredential() throws IOException {
        String triggers = Files.readString(Path.of(
                "src/main/resources/db/migration/V2__audit_triggers.sql"));
        int start = triggers.indexOf("CREATE TRIGGER audit_users_update");
        int end = triggers.indexOf("CREATE TRIGGER audit_users_delete", start);
        assertTrue(start >= 0 && end > start, "the users update audit trigger is missing");

        String updateTrigger = triggers.substring(start, end);
        assertTrue(updateTrigger.contains("COALESCE(@app_user_id, 1)"),
                "the password change must retain the signed-in audit actor");
        assertFalse(updateTrigger.contains("user_pass"),
                "neither a password nor its hash may be copied into audit JSON");
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int index = value.indexOf(token); index >= 0;
             index = value.indexOf(token, index + token.length())) {
            count++;
        }
        return count;
    }
}
