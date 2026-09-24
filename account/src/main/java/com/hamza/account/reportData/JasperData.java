package com.hamza.account.reportData;

import com.hamza.account.table.ReportPreviewWindow;
import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.util.CheckPrinterSetting;
import javafx.application.Platform;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.export.JRPrintServiceExporter;
import net.sf.jasperreports.engine.export.JRPrintServiceExporterParameter;
import net.sf.jasperreports.engine.xml.JRXmlLoader;

import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.HashPrintServiceAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.PrinterName;
import java.sql.Connection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.function.Consumer;

public class JasperData {

    private final boolean showBeforePrint;

    public JasperData(boolean showBeforePrint) {
        this.showBeforePrint = showBeforePrint;
    }

    public void printJasperPrint(String nameUrl, String title, HashMap<String, Object> parameters, int copies
            , String printerName) {
        try {
            JasperPrint jasperPrint = prepareJasperPrint(nameUrl, parameters);
            processJasperPrint(title, jasperPrint, copies, printerName);
        } catch (JRException e) {
            handleJrException(e);
        }
    }

    /**
     * The same print, with the failure handed back to the caller instead of shown.
     * <p>
     * {@link #printJasperPrint} tells the user itself, which is right when printing <em>is</em>
     * the operation - somebody pressed a print button and needs to know it failed. It is wrong
     * when printing is an automatic consequence of an operation that has already succeeded and
     * cannot be undone. Closing a shift prints a Z report after the drawer is closed and its
     * immutable snapshot written; a failure there was being reported as "could not complete the
     * operation" over a close that had completed, and the cashier read it as a failed close.
     * Callers in that position take this method and decide what a failure means.
     */
    public void printJasperPrintOrThrow(String nameUrl, String title, HashMap<String, Object> parameters,
                                        int copies, String printerName) throws JRException {
        processJasperPrint(title, prepareJasperPrint(nameUrl, parameters), copies, printerName);
    }

    public void printJasperResource(String resourcePath, String title, HashMap<String, Object> parameters,
                                    int copies, String printerName) {
        try {
            printJasperResourceOrThrow(resourcePath, title, parameters, copies, printerName);
        } catch (JRException e) {
            handleJrException(e);
        }
    }

    public void printJasperResourceOrThrow(String resourcePath, String title,
                                           HashMap<String, Object> parameters,
                                           int copies, String printerName) throws JRException {
        printJasperResourceOrThrow(resourcePath, title, parameters, new JREmptyDataSource(), copies, printerName);
    }

    /**
     * A packaged template whose detail band is filled from {@code rows}, one band per row - for a
     * paper that is a list, such as the shift reports.
     */
    public void printJasperResourceOrThrow(String resourcePath, String title,
                                           HashMap<String, Object> parameters, JRDataSource rows,
                                           int copies, String printerName) throws JRException {
        processJasperPrint(title, JasperFillManager.fillReport(CompiledReports.resource(resourcePath), parameters, rows),
                copies, printerName);
    }

    /**
     * A packaged template filled from {@code rows} and not yet sent anywhere - for a paper the program
     * previews in its own window rather than in Jasper's ({@link JasperPreviewDocument}).
     */
    public JasperPrint fillResource(String resourcePath, HashMap<String, Object> parameters, JRDataSource rows)
            throws JRException {
        return JasperFillManager.fillReport(CompiledReports.resource(resourcePath), parameters, rows);
    }

    /** Whether a paper is shown before it is printed - the checks tab's «عرض قبل الطباعة». */
    public boolean showsBeforePrint() {
        return showBeforePrint;
    }

    /**
     * Sends a filled report to the printer the settings name, or the one {@link CheckPrinterSetting}
     * falls back to - the road every Jasper paper that is not shown first takes.
     */
    public void printFilledOrThrow(JasperPrint jasperPrint, int copies, String printerName) throws JRException {
        printReportToPrinter(jasperPrint, copies, CheckPrinterSetting.checkPrinter(printerName));
    }

    /**
     * Sends an already prepared report to exactly the named printer.
     * Unlike the legacy route, this never substitutes the PDF printer when a label
     * printer is unavailable; callers can report that operational problem explicitly.
     */
    public void printPreparedToNamedPrinterOrThrow(JasperPrint jasperPrint, String printerName) throws JRException {
        boolean available = printerName != null && !printerName.isBlank()
                && Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .anyMatch(service -> service.getName().equals(printerName));
        if (!available) {
            throw new JRException("Configured printer is unavailable: " + printerName);
        }
        printReportToPrinter(jasperPrint, 1, printerName);
    }

    public void printJasperPrint(String nameUrl, String title, HashMap<String, Object> parameters, int copies, String printerName, Consumer<JasperDesign> customizer) {
        try {
            JasperDesign design = JRXmlLoader.load(nameUrl);
            customizer.accept(design);
            JasperReport report = JasperCompileManager.compileReport(design);
            processJasperPrint(title, JasperFillManager.fillReport(report, parameters, new JREmptyDataSource()), copies, printerName);
        } catch (JRException e) {
            handleJrException(e);
        }
    }

    public void printJasperPrintWithConnection(String nameUrl, String title, HashMap<String, Object> parameters, int copies
            , String printerName, Connection connection) {
        try {
            JasperPrint jasperPrint = prepareJasperPrintWithConnection(nameUrl, parameters, connection);
            processJasperPrint(title, jasperPrint, copies, printerName);
        } catch (JRException e) {
            handleJrException(e);
        }
    }

    // Compiled once per template, not per print: see CompiledReports.
    private JasperPrint prepareJasperPrint(String nameUrl, HashMap<String, Object> parameters) throws JRException {
        return JasperFillManager.fillReport(CompiledReports.file(nameUrl), parameters, new JREmptyDataSource());
    }

    private JasperPrint prepareJasperPrintWithConnection(String nameUrl, HashMap<String, Object> parameters, Connection connection) throws JRException {
        return JasperFillManager.fillReport(CompiledReports.file(nameUrl), parameters, connection);
    }

    private void processJasperPrint(String title, JasperPrint jasperPrint, int copies, String printerName) throws JRException {
        if (showBeforePrint) {
            showInPreview(title, jasperPrint, printerName);
        } else {
            printerName = CheckPrinterSetting.checkPrinter(printerName);
            printReportToPrinter(jasperPrint, copies, printerName);
        }
    }

    /**
     * Handles a given JRException by logging an error message.
     * If the exception message contains the word "null", logs a specific error message "No Data".
     *
     * @param e the JRException to be handled
     */
    private void handleJrException(JRException e) {
        AllAlerts.handleError(LanguageManager.getInstance().getString("report.error.print"), e);
    }

    /**
     * Shows a filled paper in the program's own preview window, offering {@code printerName} first, and
     * sends nothing - printing is done from the window, to the printer chosen there. Safe from any
     * thread: the window is opened on the JavaFX one.
     * <p>
     * It replaced Jasper's Swing viewer, which «عرض قبل الطباعة» opened for the receipt: an English
     * window beside an Arabic program, whose print button asked the system which printer rather than
     * offering the thermal one.
     */
    public void showInPreview(String title, JasperPrint jasperPrint, String printerName) {
        JasperPreviewDocument document = new JasperPreviewDocument(jasperPrint);
        Runnable open = () -> ReportPreviewWindow.open(null, title, document, printerName);
        if (Platform.isFxApplicationThread()) {
            open.run();
        } else {
            Platform.runLater(open);
        }
    }

    /**
     * Sends a JasperPrint report to a specified printer.
     *
     * @param jasperPrint The JasperPrint object representing the report to be printed
     * @param copies      The number of copies to print
     * @param printerName The name of the printer to which the report should be sent
     * @throws JRException If there is an error during the printing process
     */
    @SuppressWarnings("deprecation")
    static void printReportToPrinter(JasperPrint jasperPrint, int copies, String printerName) throws JRException {
        PrintRequestAttributeSet printRequestAttributes = new HashPrintRequestAttributeSet();
        printRequestAttributes.add(new Copies(copies));
        PrinterName printer = new PrinterName(printerName, null);
        PrintServiceAttributeSet printServiceAttributes = new HashPrintServiceAttributeSet();
        printServiceAttributes.add(printer);
        JRPrintServiceExporter exporter = new JRPrintServiceExporter();
        exporter.setParameter(JRExporterParameter.JASPER_PRINT, jasperPrint);
        exporter.setParameter(JRPrintServiceExporterParameter.PRINT_REQUEST_ATTRIBUTE_SET, printRequestAttributes);
        exporter.setParameter(JRPrintServiceExporterParameter.PRINT_SERVICE_ATTRIBUTE_SET, printServiceAttributes);
        exporter.setParameter(JRPrintServiceExporterParameter.DISPLAY_PAGE_DIALOG, Boolean.FALSE);
        exporter.setParameter(JRPrintServiceExporterParameter.DISPLAY_PRINT_DIALOG, Boolean.FALSE);
        exporter.exportReport();
    }
}
