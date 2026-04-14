package com.hamza.controlsfx.jasperData;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.util.CheckPrinterSetting;
import lombok.extern.log4j.Log4j2;
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

@Log4j2
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
        log.error(e.getMessage(), e.getCause());
        AllAlerts.showExceptionDialog(e);
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
        if (title.equals(Setting_Language.WORD_BARCODE)) {
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
