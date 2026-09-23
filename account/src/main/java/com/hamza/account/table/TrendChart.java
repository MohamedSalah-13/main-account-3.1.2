package com.hamza.account.table;

import com.hamza.controlsfx.table.Columns;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The line chart of an amount over periods - the collections trend, the delegate trend and the expenses
 * trend draw theirs through this one.
 *
 * <p>It was three copies of one chart, and each copy carried the same four decisions: <b>time runs left to
 * right in either language</b> (mirrored, the latest month sits where a reader looks for the first);
 * <b>the amount axis is whole amounts with separators in Latin digits</b> (an axis in one script beside a
 * table in another is the trap the printed reports fell into); <b>a line is coloured by the classes it
 * wears, not by its position</b>, so a line keeps its colour whichever others are ticked off, and the
 * checkboxes beside the chart carry the same markers and are its legend; and <b>every point says its own
 * figure on hover</b>, since an axis of whole thousands cannot. A fourth screen that wanted a trend would
 * have copied them a fourth time, with the chance to miss one.</p>
 *
 * <p>What stays with each screen is what differs between them: which lines there are, the empty message,
 * and whatever else shares the chart's place.</p>
 */
public final class TrendChart {

    /** The class a line of the year before wears on top of its own - dashed, in the theme. */
    public static final String PREVIOUS = "trend-previous";

    private static final String PRINTING = "trend-print";

    private final CategoryAxis periodAxis = new CategoryAxis();
    private final NumberAxis amountAxis = new NumberAxis();
    private final LineChart<String, Number> chart = new LineChart<>(periodAxis, amountAxis);
    /** The classes each drawn line wears, by its name - so the printed legend can wear them too. */
    private final Map<String, List<String>> seriesStyles = new HashMap<>();

    public TrendChart(String id, double minHeight) {
        chart.getStyleClass().add("party-trend-chart");
        chart.setId(id);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setLegendVisible(false);
        chart.setMinHeight(minHeight);
        chart.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        periodAxis.setAnimated(false);
        amountAxis.setAnimated(false);
        amountAxis.setForceZeroInRange(true);
        DecimalFormat whole = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        amountAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number value) {
                return value == null ? "" : whole.format(value);
            }

            @Override
            public Number fromString(String text) {
                return null;
            }
        });
    }

    /** The chart itself, to place on the screen. */
    public LineChart<String, Number> chart() {
        return chart;
    }

    /** Takes every line off, before a redraw. */
    public void clear() {
        chart.getData().clear();
        seriesStyles.clear();
    }

    /**
     * One line. Its nodes exist only once the series is in the chart, so the classes that colour it are
     * added after.
     *
     * @param classes what colours the line - its own class, and {@link #PREVIOUS} for the year before
     */
    public <P> void addSeries(String name, List<P> points, Function<P, String> label,
                              Function<P, BigDecimal> value, List<String> classes) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(name);
        for (P point : points) {
            series.getData().add(new XYChart.Data<>(label.apply(point), value.apply(point)));
        }
        chart.getData().add(series);
        seriesStyles.put(name, List.copyOf(classes));
        restyle();
        for (int index = 0; index < points.size(); index++) {
            Node symbol = series.getData().get(index).getNode();
            if (symbol != null) {
                P point = points.get(index);
                Tooltip.install(symbol, new Tooltip(name + "\n" + label.apply(point) + ": "
                        + Columns.money(value.apply(point))));
            }
        }
    }

    /** A line's own class, and the year before's on top of it where it is one. */
    public static List<String> classes(String line, boolean previous) {
        return previous ? List.of(line, PREVIOUS) : List.of(line);
    }

    /** The chart as a PNG, at twice its size on screen - see {@link ChartSnapshot} for what it adjusts. */
    public byte[] png() throws IOException {
        return ChartSnapshot.png(chart, PRINTING, seriesStyles,
                () -> chart.setLegendVisible(true), () -> chart.setLegendVisible(false));
    }

    /** The marker a checkbox wears to be the legend of the line it shows. */
    public static Region marker(String line) {
        Region marker = new Region();
        marker.getStyleClass().addAll("trend-marker", line);
        return marker;
    }

    /**
     * Puts every drawn line's classes back. {@code LineChart} sets the classes of <em>all</em> its lines
     * and symbols back to its own ({@code chart-series-line seriesN default-colorN}) each time a line is
     * added, so classing only the new line left every earlier one in JavaFX's default palette: of two
     * lines, the first was drawn orange whatever its class said - close enough to the danger red on the
     * collections trend that it passed for it, and plainly wrong on the yearly report's blue.
     */
    private void restyle() {
        for (XYChart.Series<String, Number> drawn : chart.getData()) {
            List<String> classes = seriesStyles.getOrDefault(drawn.getName(), List.of());
            style(drawn.getNode(), classes);
            for (XYChart.Data<String, Number> point : drawn.getData()) {
                style(point.getNode(), classes);
            }
        }
    }

    private static void style(Node node, List<String> classes) {
        if (node == null) {
            return;
        }
        for (String styleClass : classes) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
        }
    }
}
