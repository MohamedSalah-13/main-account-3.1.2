package com.hamza.account.features.chart;

import javafx.collections.ObservableList;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.Chart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.AnchorPane;
import lombok.Getter;

@Getter
public class ChartDesign {

    /**
     * The default anchor value used to set padding distances within an {@link AnchorPane}.
     * <p>
     * This value is applied to all four sides (left, right, top, bottom) of the chart in the {@link AnchorPane},
     * ensuring that the chart is properly spaced from the edges of the pane.
     */
    private static final double ANCHOR_VALUE = 5.0;
    /**
     * The main container for the chart in the ChartDesign class.
     * This AnchorPane is responsible for holding and anchoring the chart to specific positions.
     */
    private final AnchorPane pane;

    /**
     * Initializes a new instance of the ChartDesign class. This class is responsible for
     * creating a line chart with specified data series, axis labels, chart title,
     * and chart type using a provided ChartInterface.
     *
     * @param series         an ObservableList of XYChart.Series objects representing the data series for the chart
     * @param xAxisLabel     a String representing the label for the x-axis
     * @param yAxisLabel     a String representing the label for the y-axis
     * @param chartTitle     a String representing the title of the chart
     * @param chartInterface an instance of ChartInterface used to define the type of chart to be created
     */
    public ChartDesign(ObservableList<XYChart.Series<String, Number>> series, String xAxisLabel, String yAxisLabel,
                       String chartTitle, ChartInterface chartInterface) {
        CategoryAxis categoryAxis = new CategoryAxis();
        NumberAxis numberAxis = new NumberAxis();
        setupAxes(categoryAxis, numberAxis, xAxisLabel, yAxisLabel);

        Chart lineChart = chartInterface.CHART(categoryAxis, numberAxis, series);
        lineChart.setTitle(chartTitle);

        pane = new AnchorPane(lineChart);
        AnchorPane.setLeftAnchor(lineChart, ANCHOR_VALUE);
        AnchorPane.setRightAnchor(lineChart, ANCHOR_VALUE);
        AnchorPane.setTopAnchor(lineChart, ANCHOR_VALUE);
        AnchorPane.setBottomAnchor(lineChart, ANCHOR_VALUE);
    }

    /**
     * Configures the axes for a chart by setting their labels and tick units.
     *
     * @param categoryAxis the CategoryAxis instance representing the x-axis of the chart
     * @param numberAxis   the NumberAxis instance representing the y-axis of the chart
     * @param xAxisLabel   a String representing the label for the x-axis
     * @param yAxisLabel   a String representing the label for the y-axis
     */
    private void setupAxes(CategoryAxis categoryAxis, NumberAxis numberAxis, String xAxisLabel, String yAxisLabel) {
        categoryAxis.setLabel(xAxisLabel);
        numberAxis.setLabel(yAxisLabel);
        numberAxis.setTickUnit(5);
    }
}