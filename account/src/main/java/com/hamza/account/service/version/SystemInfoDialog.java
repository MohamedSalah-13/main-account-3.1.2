package com.hamza.account.service.version;

import com.hamza.account.config.AppVersionInfo;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class SystemInfoDialog {

    private final SystemInfoService systemInfoService;
    private final AppVersionInfo appVersionInfo;

    public SystemInfoDialog() {
        this.systemInfoService = new SystemInfoService();
        this.appVersionInfo = new AppVersionInfo();
    }

    public void show() {
        try {
            SystemInfo systemInfo = systemInfoService.getSystemInfo();

            String info = """
                    اسم البرنامج: %s
                    نسخة البرنامج: %s
                    تاريخ البناء: %s

                    كود العميل: %s
                    اسم العميل: %s

                    نسخة قاعدة البيانات: %s
                    النسخة المطلوبة لقاعدة البيانات: %s

                    اسم قاعدة البيانات: %s
                    السيرفر / IP: %s

                    تاريخ التثبيت: %s
                    آخر تحديث: %s

                    مفتاح الترخيص: %s

                    ملاحظات:
                    %s
                    """.formatted(
                    appVersionInfo.getAppName(),
                    appVersionInfo.getAppVersion(),
                    appVersionInfo.getBuildDate(),

                    value(systemInfo.getClientCode()),
                    value(systemInfo.getClientName()),

                    value(systemInfo.getDatabaseVersion()),
                    appVersionInfo.getRequiredDatabaseVersion(),

                    value(systemInfo.getDatabaseName()),
                    value(systemInfo.getServerIp()),

                    value(systemInfo.getInstallDate()),
                    value(systemInfo.getLastUpdate()),

                    value(systemInfo.getLicenseKey()),
                    value(systemInfo.getNotes())
            );

            TextArea textArea = new TextArea(info);
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setPrefWidth(600);
            textArea.setPrefHeight(420);
            textArea.setPadding(new Insets(10));

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("معلومات النظام");
            alert.setHeaderText("بيانات النسخة والعميل");
            alert.getDialogPane().setContent(textArea);
            alert.showAndWait();

        } catch (Exception e) {
            log.error("Failed to show system info", e);

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("خطأ");
            alert.setHeaderText("تعذر عرض معلومات النظام");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    private String value(Object value) {
        return value == null ? "-" : value.toString();
    }
}
