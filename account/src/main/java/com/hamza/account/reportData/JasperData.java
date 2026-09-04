package com.hamza.account.reportData;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.LanguageManager;
import com.hamza.controlsfx.util.CheckPrinterSetting;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.export.JRPrintServiceExporter;
import net.sf.jasperreports.engine.export.JRPrintServiceExporterParameter;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.view.JasperViewer;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.HashPrintServiceAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.PrinterName;
import java.sql.Connection;
import java.util.HashMap;
import java.util.function.Consumer;
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

    private JasperPrint prepareJasperPrint(String nameUrl, HashMap<String, Object> parameters) throws JRException {
        JasperDesign jasperDesign = JRXmlLoader.load(nameUrl);
        JasperReport jasperReport = JasperCompileManager.compileReport(jasperDesign);
        return JasperFillManager.fillReport(jasperReport, parameters, new JREmptyDataSource());
    }

    private JasperPrint prepareJasperPrintWithConnection(String nameUrl, HashMap<String, Object> parameters, Connection connection) throws JRException {
        JasperDesign jasperDesign = JRXmlLoader.load(nameUrl);
        JasperReport jasperReport = JasperCompileManager.compileReport(jasperDesign);
        return JasperFillManager.fillReport(jasperReport, parameters, connection);
    }

    private void processJasperPrint(String title, JasperPrint jasperPrint, int copies, String printerName) throws JRException {
        if (showBeforePrint) {
            jasperView(title, jasperPrint);
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
     * Opens a JasperViewer with the provided JasperPrint object and sets its display properties based on the given title.
     *
     * @param title       the title to display on the JasperViewer window.
     * @param jasperPrint the JasperPrint object that will be displayed in the JasperViewer.
     */
    private void jasperView(String title, JasperPrint jasperPrint) {
        JasperViewer jasperViewer = new JasperViewer(jasperPrint, false);
        jasperViewer.setTitle(title);
        if (title.equals(LanguageManager.getInstance().getString("barcode"))) {
            jasperViewer.setZoomRatio(2F);
            jasperViewer.setSize(350, 400);
            jasperViewer.setResizable(false);
        } else {
            jasperViewer.setZoomRatio(.75F);
            jasperViewer.setResizable(true);
        }
        jasperViewer.setVisible(true);
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
    private void printReportToPrinter(JasperPrint jasperPrint, int copies, String printerName) throws JRException {
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
