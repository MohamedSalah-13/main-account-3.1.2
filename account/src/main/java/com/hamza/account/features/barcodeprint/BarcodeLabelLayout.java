package com.hamza.account.features.barcodeprint;

import net.sf.jasperreports.engine.JRBand;
import net.sf.jasperreports.engine.JRElement;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignElement;
import net.sf.jasperreports.engine.design.JasperDesign;

import java.util.ArrayList;
import java.util.List;

/**
 * Resizes the legacy barcode design in proportion to the selected physical label size.
 *
 * <p>The bands are scaled with the page, not only the elements in them: the template's detail band
 * is 76pt on a 79pt (28mm) page, so shrinking the page alone refused to compile any label shorter
 * than that ("the detail section ... do not fit the page height"). Scaling is measured against the
 * area inside the margins, which stay fixed, and every element is clamped inside its band and
 * column, because Jasper refuses an element whose bottom a rounding pushed one point past the band.
 */
public final class BarcodeLabelLayout {
    private static final double POINTS_PER_MM = 72d / 25.4d;
    private static final double ROUNDING_TOLERANCE = 1e-9;
    private BarcodeLabelLayout() { }
    public static void apply(JasperDesign design, double widthMm, double heightMm) {
        int width = Math.max(28, (int) Math.round(widthMm * POINTS_PER_MM));
        int height = Math.max(28, (int) Math.round(heightMm * POINTS_PER_MM));
        int oldColumnWidth = Math.max(1, design.getColumnWidth());
        int oldUsableHeight = Math.max(1, design.getPageHeight() - design.getTopMargin() - design.getBottomMargin());
        int columnWidth = Math.max(1, width - design.getLeftMargin() - design.getRightMargin());
        int usableHeight = Math.max(1, height - design.getTopMargin() - design.getBottomMargin());
        double xScale = (double) columnWidth / oldColumnWidth;
        double yScale = (double) usableHeight / oldUsableHeight;
        design.setPageWidth(width);
        design.setPageHeight(height);
        design.setColumnWidth(columnWidth);
        for (JRBand band : bands(design)) {
            scaleBand(band, xScale, yScale, columnWidth);
        }
    }

    private static List<JRBand> bands(JasperDesign design) {
        var bands = new ArrayList<JRBand>();
        for (JRBand band : new JRBand[]{design.getBackground(), design.getTitle(), design.getPageHeader(),
                design.getColumnHeader(), design.getColumnFooter(), design.getPageFooter(),
                design.getLastPageFooter(), design.getSummary(), design.getNoData()}) {
            if (band != null) {
                bands.add(band);
            }
        }
        if (design.getDetailSection() != null && design.getDetailSection().getBands() != null) {
            bands.addAll(List.of(design.getDetailSection().getBands()));
        }
        return bands;
    }

    private static void scaleBand(JRBand band, double xScale, double yScale, int columnWidth) {
        int bandHeight = band.getHeight();
        if (band instanceof JRDesignBand editableBand) {
            // Floor, never round: the bands on one page must still add up to no more than the page.
            bandHeight = (int) Math.floor(band.getHeight() * yScale + ROUNDING_TOLERANCE);
            editableBand.setHeight(bandHeight);
        }
        for (JRElement element : band.getElements()) {
            if (element instanceof JRDesignElement editable) {
                int x = clamp((int) Math.round(editable.getX() * xScale), 0, columnWidth - 1);
                int y = clamp((int) Math.round(editable.getY() * yScale), 0, bandHeight - 1);
                editable.setX(x);
                editable.setY(y);
                editable.setWidth(clamp((int) Math.round(editable.getWidth() * xScale), 1, columnWidth - x));
                editable.setHeight(clamp((int) Math.round(editable.getHeight() * yScale), 1, bandHeight - y));
            }
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, Math.max(minimum, maximum)));
    }
}
