package com.hamza.account.features.chart;

import javafx.collections.ObservableList;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.Chart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

@FunctionalInterface
public interface ChartInterface {

    Chart CHART(CategoryAxis categoryAxis,
                NumberAxis yAxis, ObservableList<XYChart.Series<String, Number>> series);
}
