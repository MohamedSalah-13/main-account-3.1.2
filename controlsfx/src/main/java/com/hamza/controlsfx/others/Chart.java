package com.hamza.controlsfx.others;

import javafx.collections.ObservableList;
import javafx.geometry.Side;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

public class Chart {

    public Pane getChart(ObservableList<PieChart.Data> list) {
        PieChart pieChart = new PieChart();
        StackPane stackPane = new StackPane();
        pieChart.setData(list);
        pieChart.setClockwise(true);
        pieChart.setLabelLineLength(15);
        pieChart.setLegendSide(Side.BOTTOM);

        Label caption = new Label("");
        caption.setTextFill(Color.BISQUE);
        caption.setStyle("-fx-font: 16 Tahoma;");

        pieChart.getData().forEach((data) -> {
            data.nameProperty().set(data.getName() + " " + data.getPieValue() + " % ");
            data.getNode().addEventHandler(MouseEvent.MOUSE_MOVED, (MouseEvent e) -> {
                caption.setTranslateX(e.getX());
                caption.setTranslateY(e.getY());
                caption.setText(data.getPieValue() + " % ");
            });
        });

        stackPane.getChildren().addAll(pieChart, caption);
        return stackPane;
    }
}
