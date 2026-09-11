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
 * Saving a party list as a PDF: choosing the file, writing it off the JavaFX thread, and
 * saying where it went.
 *
 * <p>The parties list printed this way first; the accounts screen printed a Jasper template
 * with fixed columns until it was moved here, so the two now print the same way - from a
 * {@link PartyListPdfLayout} of the columns on screen - and cannot come to differ.</p>
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

    /**
     * Writes the layout in the background. The layout is captured by the caller on the JavaFX
     * thread - it reads the table's columns - so only the file work happens here.
     *
     * @param afterSaved runs on the JavaFX thread once the file is written and announced
     */
    static void write(File target, String title, String subtitle, PartyListPdfLayout layout,
                      Runnable afterSaved) {
        if (layout.headers().length == 0) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        PageSize pageSize = layout.headers().length > UPRIGHT_COLUMN_LIMIT
                ? PageSize.A4.rotate() : PageSize.A4;
        Task<Boolean> write = new Task<>() {
            @Override
            protected Boolean call() {
                return new PdfExportService().exportGroupedReport(target.getAbsolutePath(), title,
                        subtitle, layout.headers(), layout.columnWidths(), layout.rows(),
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
