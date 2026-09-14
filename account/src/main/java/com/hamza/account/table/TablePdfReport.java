package com.hamza.account.table;

import com.hamza.account.features.export.DirectPdfPrintService;
import com.hamza.account.features.export.PdfExportService;
import com.hamza.account.features.export.ReportOutputMode;
import com.hamza.account.features.export.ReportPaperSize;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.itextpdf.kernel.geom.PageSize;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.hamza.account.config.PropertiesName.getReportPdfOutputMode;
import static com.hamza.account.config.PropertiesName.getReportPdfPaperSize;
import static com.hamza.account.config.PropertiesName.getSettingPrinterNormal;

/**
 * Saving a list as a PDF: choosing the file, writing it off the JavaFX thread, and saying where
 * it went.
 *
 * <p>The parties list printed this way first, then the accounts screen, the ageing report and the
 * trend chart, and now the items list - all from a {@link TablePdfLayout} of the columns on screen,
 * so they cannot come to differ. It was {@code PartyPdfReport} until the items list needed it; the
 * message keys it uses still carry the party prefix they were written under, and say nothing
 * specific to a party.</p>
 */
public final class TablePdfReport {

    /** More columns than this do not fit across an upright page. */
    private static final int UPRIGHT_COLUMN_LIMIT = 5;
    private static final Map<String, ReportOutputMode> OUTPUT_TARGETS = new ConcurrentHashMap<>();

    private TablePdfReport() {
    }

    @FunctionalInterface
    public interface PdfFileWriter {
        boolean write(File target) throws Exception;
    }

    /**
     * Resolves the configured report destination. The legacy callers still pass a File, so a
     * direct-print request uses a temporary PDF that is deleted after it has reached the spooler.
     */
    public static File chooseTarget(Window owner, String title) {
        ReportOutputMode mode = configuredOutputMode();
        if (mode == ReportOutputMode.ASK) {
            mode = askOutputMode(owner);
        }
        if (mode == null) {
            return null;
        }
        if (mode == ReportOutputMode.PRINT_DIRECT) {
            try {
                File target = File.createTempFile("account-report-", ".pdf");
                OUTPUT_TARGETS.put(target.getAbsolutePath(), mode);
                return target;
            } catch (IOException e) {
                AllAlerts.handleError(text("party.error.export.generic"), e);
                return null;
            }
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(text("party.dialog.save.report"));
        chooser.setInitialFileName(safeFileName(title) + ".pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File target = chooser.showSaveDialog(owner);
        if (target != null) {
            OUTPUT_TARGETS.put(target.getAbsolutePath(), ReportOutputMode.SAVE_PDF);
        }
        return target;
    }

    /** A table, with its totals line when the layout carries one. */
    public static void write(File target, String title, String subtitle, TablePdfLayout layout,
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
    public static void write(File target, String title, String subtitle, byte[] chartPng,
                             TablePdfLayout layout, Runnable afterSaved) {
        if (layout.headers().length == 0) {
            AllAlerts.alertError(text("party.error.no.data.print"));
            return;
        }
        PageSize pageSize = chartPng != null ? configuredPageSize().rotate() : pageSizeFor(layout.headers().length);
        write(target, file -> new PdfExportService().exportChartReport(file.getAbsolutePath(), title,
                subtitle, chartPng, layout.headers(), layout.columnWidths(), layout.rows(),
                layout.totals(), pageSize), afterSaved);
    }

    /**
     * Writes a report that is not a flat table - it brings its own renderer - to the target
     * {@link #chooseTarget} resolved, with the same direct-print and announcement as a table.
     */
    public static void write(File target, PdfFileWriter writer) {
        write(target, writer, () -> { });
    }

    private static void write(File target, PdfFileWriter writer, Runnable afterSaved) {
        ReportOutputMode mode = OUTPUT_TARGETS.remove(target.getAbsolutePath());
        Task<Boolean> write = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                boolean exported = writer.write(target);
                if (exported && mode == ReportOutputMode.PRINT_DIRECT) {
                    DirectPdfPrintService.print(target, getSettingPrinterNormal(), configuredPaperSize());
                }
                return exported;
            }
        };
        write.setOnSucceeded(event -> {
            if (Boolean.TRUE.equals(write.getValue())) {
                if (mode == ReportOutputMode.PRINT_DIRECT) {
                    deleteTemporaryPrintPdf(target);
                    AllAlerts.alertSaveWithMessage(text("report.pdf.print.sent", getSettingPrinterNormal()));
                } else {
                    AllAlerts.alertSaveWithMessage(text("party.export.success.saved.at", target.getAbsolutePath()));
                }
                afterSaved.run();
            } else {
                deleteTemporaryPrintPdf(target, mode);
                AllAlerts.alertError(text("party.error.export.generic"));
            }
        });
        write.setOnFailed(event -> deleteTemporaryPrintPdf(target, mode));
        AllAlerts.handleTaskFailure(text("party.error.export.generic"), write);
        start(write, "table-pdf-write");
    }

    /** The configured paper, turned sideways when the columns would not fit across it upright. */
    public static PageSize pageSizeFor(int columns) {
        PageSize configured = configuredPageSize();
        return columns > UPRIGHT_COLUMN_LIMIT ? configured.rotate() : configured;
    }

    private static PageSize configuredPageSize() {
        return configuredPaperSize() == ReportPaperSize.A5 ? PageSize.A5 : PageSize.A4;
    }

    public static void start(Task<?> task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private static String safeFileName(String title) {
        return title.replaceAll("[\\\\/:*?\"<>|]", " ").trim();
    }

    private static ReportOutputMode configuredOutputMode() {
        return ReportOutputMode.fromStoredValue(getReportPdfOutputMode());
    }

    private static ReportPaperSize configuredPaperSize() {
        return ReportPaperSize.fromStoredValue(getReportPdfPaperSize());
    }

    private static ReportOutputMode askOutputMode(Window owner) {
        ButtonType save = new ButtonType(text("report.pdf.output.save"), ButtonBar.ButtonData.YES);
        ButtonType print = new ButtonType(text("report.pdf.output.print"), ButtonBar.ButtonData.NO);
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION, text("report.pdf.output.ask"), save, print,
                ButtonType.CANCEL);
        dialog.initOwner(owner);
        dialog.setTitle(text("report.pdf.output.title"));
        Optional<ButtonType> choice = dialog.showAndWait();
        if (choice.isEmpty() || choice.get().equals(ButtonType.CANCEL)) {
            return null;
        }
        return choice.get().equals(print) ? ReportOutputMode.PRINT_DIRECT : ReportOutputMode.SAVE_PDF;
    }

    private static void deleteTemporaryPrintPdf(File target) {
        deleteTemporaryPrintPdf(target, ReportOutputMode.PRINT_DIRECT);
    }

    private static void deleteTemporaryPrintPdf(File target, ReportOutputMode mode) {
        if (mode == ReportOutputMode.PRINT_DIRECT && target.exists() && !target.delete()) {
            target.deleteOnExit();
        }
    }

    private static String text(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }
}
