package com.hamza.account.backup;

import com.hamza.account.controller.main.LoadDataAndList;
import com.hamza.controlsfx.language.LanguageManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import lombok.extern.log4j.Log4j2;

import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;


@Log4j2
public class BackupController {
    @FXML
    private TextField backupPathField;
    @FXML
    private PasswordField encryptionPasswordField;
    @FXML
    private ComboBox<String> intervalCombo;
    @FXML
    private Button backupBtn, restoreBtn;
    @FXML
    private HBox progressBox;
    @FXML
    private Label progressLabel, statusLabel;


    private BackupService backupService;
    private Preferences prefs;
//    private ScheduledExecutorService scheduler;

    // بيانات الاتصال (يجب تهيئتها من التطبيق الرئيسي)
    private String dbHost, dbPort, dbName, dbUser, dbPassword;
//    private ScheduledFuture<?> backupTaskHandle;

    private static final List<String> INTERVAL_CODES = List.of(
            ScheduledBackup.INTERVAL_DISABLED, ScheduledBackup.INTERVAL_HOURLY,
            ScheduledBackup.INTERVAL_EVERY_2_HOURS, ScheduledBackup.INTERVAL_EVERY_6_HOURS,
            ScheduledBackup.INTERVAL_DAILY);

    /** Old installs may still have the Arabic label saved directly; map it to its code. */
    private static String normalizeInterval(String stored) {
        return switch (stored) {
            case "كل ساعة" -> ScheduledBackup.INTERVAL_HOURLY;
            case "كل ساعتين" -> ScheduledBackup.INTERVAL_EVERY_2_HOURS;
            case "كل 6 ساعات" -> ScheduledBackup.INTERVAL_EVERY_6_HOURS;
            case "كل يوم" -> ScheduledBackup.INTERVAL_DAILY;
            case "معطل" -> ScheduledBackup.INTERVAL_DISABLED;
            default -> stored;
        };
    }

    private static String intervalLabel(String code) {
        return LanguageManager.getInstance().getString("backup.interval." + code);
    }

    @FXML
    public void initialize() {
        prefs = Preferences.userNodeForPackage(BackupController.class);
        // تعبئة القيم المحفوظة
        backupPathField.setText(ScheduledBackup.backupPath());
        encryptionPasswordField.setText(ScheduledBackup.encryptionPassword());
        intervalCombo.getItems().addAll(INTERVAL_CODES);
        intervalCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(String code) {
                return code == null ? "" : intervalLabel(code);
            }

            @Override
            public String fromString(String label) {
                return INTERVAL_CODES.stream()
                        .filter(code -> intervalLabel(code).equals(label))
                        .findFirst()
                        .orElse(ScheduledBackup.INTERVAL_DISABLED);
            }
        });
        intervalCombo.setValue(normalizeInterval(ScheduledBackup.interval()));

        // عند تغيير كلمة المرور نحفظها ونحدث الخدمة
        encryptionPasswordField.textProperty().addListener((obs, oldVal, newVal) -> {
            // Saving used to be skipped whenever backupService was still null, which
            // is its state until initConnection runs - so a password typed before
            // then was silently dropped. The stored password also has to reach the
            // running service, which kept using the one it was built with.
            prefs.put("encryptionPassword", newVal);
            if (backupService != null) {
                updateBackupService();
            }
        });
    }

    // هذه الدالة تُستدعى من التطبيق الرئيسي لضبط بيانات الاتصال
    public void initConnection(String dbHost, String dbPort, String dbName,
                               String dbUser, String dbPassword) {
        this.dbHost = dbHost;
        this.dbPort = dbPort;
        this.dbName = dbName;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        updateBackupService();
    }

    private void updateBackupService() {
        backupService = new BackupService(dbHost, dbPort, dbName, dbUser, dbPassword,
                encryptionPasswordField.getText());
    }

    @FXML
    private void changeBackupPath() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(LanguageManager.getInstance().getString("backup.dialog.choose.folder.title"));
        File dir = chooser.showDialog(backupPathField.getScene().getWindow());
        if (dir != null) {
            backupPathField.setText(dir.getAbsolutePath());
            prefs.put("backupPath", dir.getAbsolutePath());

        }
    }

    @FXML
    private void backupNow() {
        if (backupService == null) updateBackupService();
        File dir = new File(backupPathField.getText());
        if (!dir.exists()) dir.mkdirs();

        // إنشاء المهمة
        Task<File> task = new Task<>() {
            @Override
            protected File call() throws Exception {
                // هذه التعليمة ستنفذ في خيط خلفي
                return backupService.backupToFile(dir);
            }
        };

        // إظهار المؤشر وإخفاء الأزرار
        setUIForTask(true, LanguageManager.getInstance().getString("backup.status.backing.up"));
        backupBtn.setDisable(true);
        restoreBtn.setDisable(true);

        task.setOnSucceeded(e -> {
            File result = task.getValue();
            // Only once the new copy exists, so a failed backup cannot take the
            // existing ones down with it.
            ScheduledBackup.pruneOldBackups(dir, ScheduledBackup.MAX_BACKUP_FILES);
            setStatus("✓ " + LanguageManager.getInstance().getString("backup.status.created", result.getName()));
            resetUIAfterTask();
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            var report = com.hamza.controlsfx.error.ErrorReporter.shared()
                    .report(LanguageManager.getInstance().getString("backup.op.create"), ex);
            setStatus("✗ " + report.message());
            resetUIAfterTask();
        });

        new Thread(task).start(); // تشغيل العملية في الخلفية
    }

    @FXML
    private void restoreBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(LanguageManager.getInstance().getString("backup.dialog.choose.file.title"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                LanguageManager.getInstance().getString("backup.filter.encrypted.files"), "*.enc"));
        File file = chooser.showOpenDialog(backupPathField.getScene().getWindow());
        if (file == null) return;

        // طلب كلمة المرور للتأكيد (قد يكون أفضل من الحقل المخزن)
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(LanguageManager.getInstance().getString("backup.dialog.password.title"));
        dialog.setHeaderText(LanguageManager.getInstance().getString("backup.dialog.password.header"));
        dialog.setContentText(LanguageManager.getInstance().getString("backup.dialog.password.content"));
        Optional<String> passwordResult = dialog.showAndWait();

        if (!passwordResult.isPresent() || passwordResult.get().trim().isEmpty()) return;
        final String password = passwordResult.get().trim();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                LanguageManager.getInstance().getString("backup.confirm.restore"));
        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) return;

            // تحديث مؤقت للـ backupService بكلمة المرور هذه
            // يمكنك إضافة دالة setEncryptionPassword في BackupService
            BackupService restoreService = new BackupService(dbHost, dbPort, dbName,
                    dbUser, dbPassword, password);

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    restoreService.restoreFromFile(file, password);
                    return null;
                }
            };

            setUIForTask(true, LanguageManager.getInstance().getString("backup.status.restoring"));
            backupBtn.setDisable(true);
            restoreBtn.setDisable(true);

            task.setOnSucceeded(e -> {
                Toolkit.getDefaultToolkit().beep();
                setStatus("✓ " + LanguageManager.getInstance().getString("backup.status.restored"));
                resetUIAfterTask();
                LoadDataAndList.updateData();
            });

            task.setOnFailed(e -> {
                Throwable ex = task.getException();
                var report = com.hamza.controlsfx.error.ErrorReporter.shared()
                        .report(LanguageManager.getInstance().getString("backup.op.restore"), ex);
                setStatus("✗ " + report.message());
                resetUIAfterTask();
            });

            new Thread(task).start();
        });
    }

    // تفعيل / إخفاء واجهة التقدم
    private void setUIForTask(boolean running, String message) {
        progressBox.setVisible(running);
        progressLabel.setText(message);
    }

    private void resetUIAfterTask() {
        setUIForTask(false, "");
        backupBtn.setDisable(false);
        restoreBtn.setDisable(false);
    }

    @FXML
    private void applySchedule() {
        String selected = intervalCombo.getValue();
        prefs.put("interval", selected);

        if (ScheduledBackup.getTime() > 0) {
            // Scheduling a null service only surfaces later, as an NPE inside the
            // scheduler thread at the first run.
            if (backupService == null) updateBackupService();
            ScheduledBackup.startScheduler(backupService);
            setStatus(LanguageManager.getInstance().getString("backup.status.schedule.enabled", intervalLabel(selected)));
        } else {
            ScheduledBackup.stopScheduler();
            setStatus(LanguageManager.getInstance().getString("backup.status.schedule.disabled"));
        }
    }
    // ... إضافة startScheduler/stopScheduler التي رأيناها سابقاً

    private void setStatus(String msg) {
        Platform.runLater(() -> statusLabel.setText(msg + " | " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))));
    }

}
