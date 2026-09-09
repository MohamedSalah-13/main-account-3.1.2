package com.hamza.account.reportData;

import com.hamza.account.features.barcodeprint.BarcodeLabelOptions;
import com.hamza.account.features.barcodeprint.BarcodeLabelLayout;
import com.hamza.account.features.barcodeprint.BarcodeLabelText;
import com.hamza.account.features.barcodeprint.BarcodePrintBatch;
import com.hamza.account.features.barcodeprint.BarcodePrintEngine;
import com.hamza.account.features.barcodeprint.BarcodePrintLine;
import com.hamza.account.finance.MoneyMath;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperPrintManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** JasperReports adapter for a whole barcode batch: one compile and one spool job. */
public final class JasperBarcodePrintEngine implements BarcodePrintEngine {
    private static final float PREVIEW_ZOOM = 3F;
    private static final String DEFAULT_TEMPLATE_RESOURCE = "/reports/ar/barcode-one-label.jrxml";

    private final JasperData jasperData;
    private final String reportPath;
    private final Map<LabelSize, JasperReport> compiledReports = new ConcurrentHashMap<>();

    public JasperBarcodePrintEngine() {
        this(new JasperData(false), null);
    }

    JasperBarcodePrintEngine(JasperData jasperData, String reportPath) {
        this.jasperData = jasperData;
        this.reportPath = reportPath;
    }

    @Override
    public byte[] previewPng(BarcodePrintBatch batch) throws Exception {
        JasperPrint preview = renderLine(batch.lines().getFirst(), batch.options());
        Image pageImage = JasperPrintManager.printPageToImage(preview, 0, PREVIEW_ZOOM);
        BufferedImage image = new BufferedImage(pageImage.getWidth(null), pageImage.getHeight(null),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.drawImage(pageImage, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    @Override
    public void print(BarcodePrintBatch batch) throws Exception {
        JasperPrint combined = null;
        for (BarcodePrintLine line : batch.lines()) {
            JasperPrint single = renderLine(line, batch.options());
            JRPrintPage page = firstPage(single);
            if (combined == null) {
                combined = single;
                for (int copy = 1; copy < line.copies(); copy++) {
                    combined.addPage(page);
                }
            } else {
                for (int copy = 0; copy < line.copies(); copy++) {
                    combined.addPage(page);
                }
            }
        }
        if (combined == null) {
            throw new JRException("Barcode batch rendered no pages");
        }
        jasperData.printPreparedToNamedPrinterOrThrow(combined, batch.printerName());
    }

    private JasperPrint renderLine(BarcodePrintLine line, BarcodeLabelOptions options) throws JRException {
        return JasperFillManager.fillReport(compiledReport(options), parameters(line, options),
                new JREmptyDataSource());
    }

    private JasperReport compiledReport(BarcodeLabelOptions options) throws JRException {
        LabelSize size = new LabelSize(options.widthMm(), options.heightMm());
        JasperReport cached = compiledReports.get(size);
        if (cached != null) {
            return cached;
        }
        synchronized (compiledReports) {
            cached = compiledReports.get(size);
            if (cached == null) {
                JasperDesign design = loadDesign();
                BarcodeLabelLayout.apply(design, size.widthMm(), size.heightMm());
                cached = JasperCompileManager.compileReport(design);
                compiledReports.put(size, cached);
            }
            return cached;
        }
    }

    private JasperDesign loadDesign() throws JRException {
        if (reportPath != null) {
            return JRXmlLoader.load(reportPath);
        }
        InputStream template = JasperBarcodePrintEngine.class.getResourceAsStream(DEFAULT_TEMPLATE_RESOURCE);
        if (template == null) {
            throw new JRException("Missing packaged barcode report: " + DEFAULT_TEMPLATE_RESOURCE);
        }
        try (template) {
            return JRXmlLoader.load(template);
        } catch (IOException exception) {
            throw new JRException("Could not close packaged barcode report: " + DEFAULT_TEMPLATE_RESOURCE, exception);
        }
    }

    private HashMap<String, Object> parameters(BarcodePrintLine line, BarcodeLabelOptions options) {
        BarcodeLabelText.RenderedName renderedName = BarcodeLabelText.renderName(line.name(),
                options.nameOverflow(), options.nameMaximumCharacters(), options.nameFontSize());
        boolean nameVisible = options.showName() && renderedName.visible();
        var parameters = new HashMap<String, Object>();
        parameters.put("barcode", line.barcode());
        parameters.put("name", nameVisible ? renderedName.value() : "");
        parameters.put("details", details(line, options));
        parameters.put("show_name", nameVisible);
        parameters.put("name_font_size", renderedName.fontSize());
        parameters.put("label_count", options.doubleLabel() ? 2 : 1);
        return parameters;
    }

    private String details(BarcodePrintLine line, BarcodeLabelOptions options) {
        var value = new StringBuilder();
        if (options.showBarcodeNumber()) {
            value.append(line.barcode());
        }
        if (options.showBarcodeNumber() && options.showPrice()) {
            value.append(" - ");
        }
        if (options.showPrice()) {
            value.append(MoneyMath.text(line.price()));
        }
        return value.toString();
    }

    private JRPrintPage firstPage(JasperPrint print) throws JRException {
        if (print.getPages().isEmpty()) {
            throw new JRException("Barcode label rendered no page");
        }
        return print.getPages().getFirst();
    }

    private record LabelSize(double widthMm, double heightMm) {
    }
}
