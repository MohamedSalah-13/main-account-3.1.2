package com.hamza.account.view;

import com.hamza.account.backup.ScheduledBackup;
import com.hamza.account.config.SaveDatabaseFile;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.notification.NotificationBootstrap;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.features.rbac.RbacService;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import static com.hamza.account.config.PropertiesName.getBackupDatabaseSaveBeforeClose;

/** Owns navigation on the one primary stage and the boundaries of a user session. */
@Log4j2
public final class ApplicationNavigator {

    private final Stage stage;
    private final DaoFactory daoFactory;

    public ApplicationNavigator(Stage stage, DaoFactory daoFactory) {
        this.stage = stage;
        this.daoFactory = daoFactory;
    }

    public void showLogin() {
        try {
            stage.setFullScreen(false);
            stage.setMaximized(false);
            new LogApplication(daoFactory, this::showMain).show(stage);
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("login.screen.load.failed"), e);
        }
    }

    public void showMain(Users user) {
        try {
            new MainScreenApplication(daoFactory, this).show(stage);
        } catch (Exception e) {
            AllAlerts.handleError(LanguageManager.getInstance().getString("main.screen.load.failed"), e);
            logout(false);
        }
    }

    public void logout() {
        logout(true);
    }

    private void logout(boolean createBackup) {
        Users user = CurrentUser.getOrNull();
        stopSessionServices();
        showBusy("session.signing.out");

        Task<Void> task = sessionEndTask(user, createBackup);
        task.setOnSucceeded(event -> showLogin());
        task.setOnFailed(event -> {
            AllAlerts.handleError(LanguageManager.getInstance().getString("session.sign.out.failed"),
                    task.getException());
            showLogin();
        });
        start(task, "session-sign-out");
    }

    public void requestExit() {
        LanguageManager language = LanguageManager.getInstance();
        if (!AllAlerts.confirm_all(language.getString("session.exit.title"),
                language.getString("session.exit.confirm"))) {
            return;
        }

        Users user = CurrentUser.getOrNull();
        stopSessionServices();
        showBusy("session.closing");

        Task<Void> task = sessionEndTask(user, true);
        task.setOnSucceeded(event -> Platform.exit());
        task.setOnFailed(event -> {
            log.error("Session cleanup failed while closing the application", task.getException());
            Platform.exit();
        });
        start(task, "application-close");
    }

    private Task<Void> sessionEndTask(Users user, boolean createBackup) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                Exception failure = null;
                if (createBackup && getBackupDatabaseSaveBeforeClose()) {
                    try {
                        SaveDatabaseFile.saveBeforeClose(false);
                    } catch (Exception e) {
                        failure = e;
                    }
                }

                if (user != null) {
                    try {
                        user.setUser_available(0);
                        if (daoFactory.usersDao().updateAvailable(user) != 1) {
                            throw new IllegalStateException("Signed-out user could not be marked unavailable");
                        }
                    } catch (Exception e) {
                        if (failure == null) failure = e;
                        else failure.addSuppressed(e);
                    }
                }

                if (failure != null) throw failure;
                return null;
            }
        };
    }

    private void stopSessionServices() {
        NotificationBootstrap.stop();
        ScheduledBackup.stopScheduler();
        RbacService rbac = ServiceRegistry.get(RbacService.class);
        if (rbac != null) rbac.signOut();
    }

    private void showBusy(String messageKey) {
        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(48, 48);
        Label message = new Label(LanguageManager.getInstance().getString(messageKey));
        VBox root = new VBox(16, progress, message);
        root.setAlignment(Pos.CENTER);
        Scene scene = new Scene(root, 380, 240);
        ChangeOrientation.sceneOrientation(scene);
        stage.setOnCloseRequest(event -> event.consume());
        stage.setScene(scene);
        stage.show();
    }

    private static void start(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}
