package com.hamza.account.table;

import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.Chart;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A chart as a PNG for a printed report, the way it is drawn on screen.
 * <p>
 * It was the collections trend's own code until the expenses trend needed the same picture; two copies of
 * the mirroring fix below would be one copy that is fixed and one that is not. Three things are true only
 * for the length of the snapshot:
 * <ul>
 *   <li><b>The printing class</b> ({@code trend-print}) paints the chart dark on white whatever the theme:
 *       the page is white, and a dark theme's light axis labels would print as nothing at all.</li>
 *   <li><b>The chart's own legend is shown.</b> On screen the checkboxes beside it are the legend; a page
 *       has no checkboxes. Its markers are given the lines' own classes, since JavaFX colours a legend by
 *       position and these lines are coloured by class.</li>
 *   <li><b>The picture is flipped back when the chart is mirrored.</b> A chart runs left to right inside a
 *       right-to-left screen, so JavaFX gives it a mirroring transform the screen's own mirror cancels; a
 *       snapshot takes the chart without its parent, and the first printed chart came out backwards.</li>
 * </ul>
 */
public final class ChartSnapshot {

    private ChartSnapshot() {
    }

    /**
     * @param printingClass the class that repaints the chart for paper
     * @param seriesStyles  the classes each line wears, by the line's name - what the legend markers get
     */
    public static byte[] png(Chart chart, String printingClass, Map<String, List<String>> seriesStyles,
                             Runnable showLegend, Runnable hideLegend) throws IOException {
        boolean mirrored = chart.getParent() != null
                && chart.getParent().getEffectiveNodeOrientation() != chart.getEffectiveNodeOrientation();
        chart.getStyleClass().add(printingClass);
        showLegend.run();
        try {
            chart.applyCss();
            chart.layout();
            styleLegend(chart, seriesStyles);
            chart.applyCss();
            chart.layout();
            SnapshotParameters parameters = new SnapshotParameters();
            parameters.setFill(Color.WHITE);
            parameters.setTransform(Transform.scale(2, 2));
            return png(chart.snapshot(parameters, null), mirrored);
        } finally {
            hideLegend.run();
            chart.getStyleClass().remove(printingClass);
            chart.applyCss();
        }
    }

    /** Gives each legend marker the classes of the line it names, matched by the series name. */
    private static void styleLegend(Chart chart, Map<String, List<String>> seriesStyles) {
        for (Node item : chart.lookupAll(".chart-legend-item")) {
            if (item instanceof Label label && label.getGraphic() != null) {
                List<String> classes = seriesStyles.get(label.getText());
                if (classes != null) {
                    for (String styleClass : classes) {
                        if (!label.getGraphic().getStyleClass().contains(styleClass)) {
                            label.getGraphic().getStyleClass().add(styleClass);
                        }
                    }
                }
            }
        }
    }

    /**
     * Pixel by pixel, so no javafx.swing module is needed for SwingFXUtils - and read from the far edge when
     * the snapshot came out mirrored.
     */
    private static byte[] png(Image image, boolean mirrored) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage picture = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                picture.setRGB(x, y, pixels.getArgb(mirrored ? width - 1 - x : x, y));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(picture, "png", out);
        return out.toByteArray();
    }
}
