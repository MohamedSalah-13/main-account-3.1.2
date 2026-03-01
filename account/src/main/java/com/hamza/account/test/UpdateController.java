package com.hamza.account.test;


import com.hamza.account.openFxml.FxmlPath;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

@FxmlPath(pathFile = "update-data.fxml")
public class UpdateController {
    @FXML
    private Label statusLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private TextArea releaseNotesArea;
    @FXML
    private Button updateButton;
    @FXML
    private Button cancelButton;

    private UpdateManager updateManager;
    private Stage stage;
    private String currentVersion = "1.0.0"; // الإصدار الحالي

    public void initialize() {
        updateManager = new UpdateManager(currentVersion);
        checkForUpdates();
    }

    private void checkForUpdates() {
        statusLabel.setText("جاري التحقق من التحديثات...");

        new Thread(() -> {
            UpdateManager.UpdateCheckResult result = updateManager.checkForUpdates();

            Platform.runLater(() -> {
                if (result.isUpdateAvailable()) {
                    statusLabel.setText("يتوفر تحديث جديد: " + result.getLatestVersion());
                    releaseNotesArea.setText(result.getReleaseNotes());
                    updateButton.setDisable(false);

                    if (result.isMandatory()) {
                        cancelButton.setDisable(true);
                    }
                } else {
                    statusLabel.setText("التطبيق محدث");
                    updateButton.setDisable(true);
                }
            });
        }).start();
    }

    @FXML
    private void handleUpdate() {
        updateButton.setDisable(true);
        statusLabel.setText("جاري تحميل التحديث...");

        new Thread(() -> {
            UpdateManager.UpdateCheckResult result = updateManager.checkForUpdates();

            if (result.isUpdateAvailable()) {
                String fileName = "update-" + result.getLatestVersion() + ".jar";
                boolean success = updateManager.downloadUpdate(result.getDownloadUrl(), fileName,result.getLatestVersion());

                Platform.runLater(() -> {
                    if (success) {
                        statusLabel.setText("تم تحميل التحديث بنجاح");
                        showRestartDialog(fileName);
                    } else {
                        statusLabel.setText("فشل تحميل التحديث");
                        updateButton.setDisable(false);
                    }
                });
            }
        }).start();
    }

    private void showRestartDialog(String updateFile) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("التحديث جاهز");
        alert.setHeaderText("تم تحميل التحديث بنجاح");
        alert.setContentText("يجب إعادة تشغيل التطبيق لتطبيق التحديث");

        ButtonType restartButton = new ButtonType("إعادة التشغيل الآن");
        ButtonType laterButton = new ButtonType("لاحقاً");

        alert.getButtonTypes().setAll(restartButton, laterButton);

        alert.showAndWait().ifPresent(buttonType -> {
            if (buttonType == restartButton) {
                applyUpdate(updateFile);
            }
        });
    }

    private void applyUpdate(String updateFile) {
        // هنا يمكنك إضافة منطق استبدال الملفات
        // وإعادة تشغيل التطبيق
        System.exit(0);
    }

    @FXML
    private void handleCancel() {
        stage.close();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }
}
