package com.hamza.account.controller.reports;

import com.hamza.controlsfx.language.LanguageManager;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import java.io.File;

import static com.hamza.account.controller.reports.ErrorReports.showInfo;
import static com.hamza.controlsfx.alert.AllAlerts.alertError;

@Log4j2
public class ChoosePdfFile {

    public void choosePdfFile(String reportName, ExportReport exportReport) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(LanguageManager.getInstance().getString("party.dialog.save.report"));
        fileChooser.setInitialFileName(reportName + ".pdf");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
        );

        File file = fileChooser.showSaveDialog(new Stage());
//        String path = ReportExportService.getDefaultOutputPath("تقرير_المبيعات_الشامل");
        if (file != null) {
            // 2. التصدير
            String path = file.getAbsolutePath();
            boolean success = exportReport.success(path);
            if (success) {
                showInfo(LanguageManager.getInstance().getString("party.export.success.saved.at", path));
                try {
                    java.awt.Desktop.getDesktop().open(new File(path));
                } catch (Exception e) {
                    log.error("Error opening PDF file: ", e);
                }
            } else {
                alertError(LanguageManager.getInstance().getString("report.msg.export.pdf.failed"));
            }
        }
    }
}
