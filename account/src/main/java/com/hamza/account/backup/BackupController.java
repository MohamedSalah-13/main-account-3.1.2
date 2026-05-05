package com.hamza.account.backup;

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

import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.prefs.Preferences;

import static com.hamza.account.backup.ScheduledBackup.BACKUP_PATH;
import static com.hamza.account.backup.ScheduledBackup.ENCRYPTION_PASSWORD;

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
    private ProgressIndicator progressIndicator;
    @FXML
    private Label progressLabel, statusLabel;


    private BackupService backupService;
    private Preferences prefs;
//    private ScheduledExecutorService scheduler;

    // بيانات الاتصال (يجب تهيئتها من التطبيق الرئيسي)
    private String dbHost, dbPort, dbName, dbUser, dbPassword;
//    private ScheduledFuture<?> backupTaskHandle;

    @FXML
    public void initialize() {
        prefs = Preferences.userNodeForPackage(BackupController.class);
        // تعبئة القيم المحفوظة
        backupPathField.setText(BACKUP_PATH);
        encryptionPasswordField.setText(ENCRYPTION_PASSWORD);
        intervalCombo.getItems().addAll("معطل", "كل ساعة", "كل ساعتين", "كل 6 ساعات", "كل يوم");
        String savedInterval = prefs.get("interval", "معطل");
        intervalCombo.setValue(savedInterval);

        // تهيئة BackupService عند توفر المعلومات (يمكن تمريرها عبر setter)
        // مثلاً: backupService = new BackupService(dbHost,dbPort,dbName,dbUser,dbPassword, encryptionPasswordField.getText());

        // عند تغيير كلمة المرور نحدث الخدمة (بسيط)
        encryptionPasswordField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (backupService != null) {
                // تحديث كلمة المرور في BackupService (يمكن إضافة setter)
                prefs.put("encryptionPassword", newVal);
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
        chooser.setTitle("اختر مجلد النسخ الاحتياطي");
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
        setUIForTask(true, "جارٍ النسخ الاحتياطي...");
        backupBtn.setDisable(true);
        restoreBtn.setDisable(true);

        task.setOnSucceeded(e -> {
            File result = task.getValue();
            setStatus("✓ تم إنشاء النسخة: " + result.getName());
            resetUIAfterTask();
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            setStatus("✗ فشل النسخ: " + ex.getMessage());
            resetUIAfterTask();
        });

        new Thread(task).start(); // تشغيل العملية في الخلفية
    }

    @FXML
    private void restoreBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("اختر ملف النسخة الاحتياطية");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ملفات مشفرة", "*.enc"));
        File file = chooser.showOpenDialog(backupPathField.getScene().getWindow());
        if (file == null) return;

        // طلب كلمة المرور للتأكيد (قد يكون أفضل من الحقل المخزن)
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("كلمة مرور التشفير");
        dialog.setHeaderText("أدخل كلمة مرور فك تشفير النسخة:");
        dialog.setContentText("كلمة المرور:");
        Optional<String> passwordResult = dialog.showAndWait();

        if (!passwordResult.isPresent() || passwordResult.get().trim().isEmpty()) return;
        final String password = passwordResult.get().trim();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "سيتم استبدال البيانات الحالية! متابعة؟");
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

            setUIForTask(true, "جارٍ استعادة النسخة...");
            backupBtn.setDisable(true);
            restoreBtn.setDisable(true);

            task.setOnSucceeded(e -> {
                Toolkit.getDefaultToolkit().beep();
                setStatus("✓ تمت الاستعادة بنجاح");
                resetUIAfterTask();
            });

            task.setOnFailed(e -> {
                Throwable ex = task.getException();
                ex.printStackTrace();
                String msg = ex.getMessage();
                if (msg.contains("كلمة مرور خاطئة") || msg.contains("تالف")) {
                    setStatus("✗ " + msg);
                } else {
                    setStatus("✗ فشل الاستعادة: " + msg);
                }
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
            ScheduledBackup.startScheduler(backupService);
            setStatus("تم تفعيل النسخ التلقائي كل " + selected);
        } else {
            ScheduledBackup.stopScheduler();
            setStatus("تم إيقاف النسخ التلقائي");
        }
    }
    // ... إضافة startScheduler/stopScheduler التي رأيناها سابقاً

    private void setStatus(String msg) {
        Platform.runLater(() -> statusLabel.setText(msg + " | " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))));
    }

//    public static void startScheduler(long intervalHours) {
//        if (scheduler == null || scheduler.isShutdown()) {
//            scheduler = Executors.newSingleThreadScheduledExecutor();
//        }
//        if (backupTaskHandle != null) backupTaskHandle.cancel(false);
//
//        backupTaskHandle = scheduler.scheduleAtFixedRate(() -> {
//            try {
//                File dir = new File(prefs.get("backupPath", System.getProperty("user.home")));
//                File backup = backupService.backupToFile(dir);
//                Platform.runLater(() -> setStatus("نسخ تلقائي: " + backup.getName()));
//            } catch (Exception e) {
//                Platform.runLater(() -> setStatus("فشل النسخ التلقائي: " + e.getMessage()));
//            }
//        }, 0, intervalHours, TimeUnit.MINUTES);
//    }
//
//    private void stopScheduler() {
//        if (backupTaskHandle != null) backupTaskHandle.cancel(false);
//        if (scheduler != null) scheduler.shutdownNow();
//    }
}
