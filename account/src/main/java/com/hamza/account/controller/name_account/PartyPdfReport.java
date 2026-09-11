package com.hamza.account.controller.name_account;

import com.hamza.account.features.export.PdfExportService;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.itextpdf.kernel.geom.PageSize;
import javafx.concurrent.Task;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;

/**
 * Saving a party screen as a PDF: choosing the file, writing it off the JavaFX thread, and
 * saying where it went.
 *
 * <p>The parties list printed this way first; the accounts screen printed a Jasper template
 * with fixed columns until it was moved here, and the ageing report and the trend chart print
 * here too - so they all print the same way, from a {@link PartyListPdfLayout} of the columns on
 * screen, and cannot come to differ.</p>
 */
final class PartyPdfReport {

    /** More columns than this do not fit across an upright A4 page. */
    private static final int UPRIGHT_COLUMN_LIMIT = 5;

    private PartyPdfReport() {
    }

    /** Asks where to save, suggesting the report's title as the file name. Null if cancelled. */
    static File chooseTarget(Window owner, String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(text("party.dialog.save.report"));
        chooser.setInitialFileName(safeFileName(title) + ".pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        return chooser.showSaveDialog(owner);
    }

    /** A table, with its totals line when the layout carries one. */
    static void write(File target, String title, String subtitle, PartyListPdfLayout layout,
                      Runnable afterSaved) {
        write(target, title, subtitle, null, layout, afterSaved);
    }

    /**
     * Writes the report in the background. The layout and the chart are captured by the caller on
     * the JavaFX thread - one reads the table's columns, the other is a snapshot - so only the file
     * work happens here.
     *
     * @param chartPng   a chart to place above the table, or null. A chart always gets a landscape
     *                   page: upright, it is a strip too thin to read
     * @param afterSaved runs on the JavaFX thread once the file is written and announced
     */
    static void write(File target, String title, String subtitle, byte[] chartPng,
                      PartyListPdfLayout layout, Runnable afterSaved) {
        if (layout.headers().length == 0) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        PageSize pageSize = chartPng != null || layout.headers().length > UPRIGHT_COLUMN_LIMIT
                ? PageSize.A4.rotate() : PageSize.A4;
        Task<Boolean> write = new Task<>() {
            @Override
            protected Boolean call() {
                return new PdfExportService().exportChartReport(target.getAbsolutePath(), title,
                        subtitle, chartPng, layout.headers(), layout.columnWidths(), layout.rows(),
                        layout.totals(), pageSize);
            }
        };
        write.setOnSucceeded(event -> {
            if (Boolean.TRUE.equals(write.getValue())) {
                AllAlerts.alertSaveWithMessage(text("party.export.success.saved.at", target.getAbsolutePath()));
                afterSaved.run();
            } else {
                AllAlerts.alertError(text("party.error.export.generic"));
            }
        });
        AllAlerts.handleTaskFailure(text("party.error.export.generic"), write);
        start(write, "party-pdf-write");
    }

    static void start(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private static String safeFileName(String title) {
        return title.replaceAll("[\\\\/:*?\"<>|]", " ").trim();
    }

    private static String text(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }
}
