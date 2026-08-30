package com.hamza.account.features.barcodeprint;

import net.sf.jasperreports.engine.JRBand;
import net.sf.jasperreports.engine.JRElement;
import net.sf.jasperreports.engine.design.JRDesignElement;
import net.sf.jasperreports.engine.design.JasperDesign;

/** Resizes the legacy barcode design in proportion to the selected physical label size. */
public final class BarcodeLabelLayout {
    private static final double POINTS_PER_MM = 72d / 25.4d;
    private BarcodeLabelLayout() { }
    public static void apply(JasperDesign design, double widthMm, double heightMm) {
        int oldWidth = design.getPageWidth();
        int oldHeight = design.getPageHeight();
        int width = Math.max(28, (int) Math.round(widthMm * POINTS_PER_MM));
        int height = Math.max(28, (int) Math.round(heightMm * POINTS_PER_MM));
        double xScale = (double) width / oldWidth;
        double yScale = (double) height / oldHeight;
        design.setPageWidth(width);
        design.setPageHeight(height);
        design.setColumnWidth(Math.max(1, width - design.getLeftMargin() - design.getRightMargin()));
        for (JRBand band : design.getDetailSection().getBands()) {for (JRElement element : band.getElements()) {
                if (element instanceof JRDesignElement editable) {
                    editable.setX((int) Math.round(editable.getX() * xScale));
                    editable.setY((int) Math.round(editable.getY() * yScale));
                    editable.setWidth(Math.max(1, (int) Math.round(editable.getWidth() * xScale)));
                    editable.setHeight(Math.max(1, (int) Math.round(editable.getHeight() * yScale)));
                }
            }
        }
    }
}