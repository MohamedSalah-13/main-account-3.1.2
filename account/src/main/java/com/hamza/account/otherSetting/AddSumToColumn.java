package com.hamza.account.otherSetting;

import com.hamza.controlsfx.util.NumberUtils;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;


public class AddSumToColumn extends HBox {

    private final DoubleProperty sum = new SimpleDoubleProperty();

    public AddSumToColumn(String name) {
        Text text = new Text();
        text.textProperty().bind(sum.asObject().asString());
        setSpacing(5);
        setAlignment(Pos.CENTER_LEFT);
        text.getStyleClass().add("text-sum");
        getChildren().addAll(new Label(name + " : "), text);
    }


    public double getSum() {
        return sum.get();
    }

    public void setSum(double sum) {
        this.sum.set(NumberUtils.roundToTwoDecimalPlaces(sum));
    }

    public DoubleProperty sumProperty() {
        return sum;
    }
}
