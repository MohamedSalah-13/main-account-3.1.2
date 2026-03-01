package com.hamza.controlsfx.others;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

public class CloseService {

    private final DoubleProperty stageHeight = new SimpleDoubleProperty();
    private final DoubleProperty stageWidth = new SimpleDoubleProperty();
    private final DoubleProperty stageX = new SimpleDoubleProperty();
    private final DoubleProperty stageY = new SimpleDoubleProperty();

    public double getStageHeight() {
        return stageHeight.get();
    }

    public void setStageHeight(double stageHeight) {
        this.stageHeight.set(stageHeight);
    }

    public DoubleProperty stageHeightProperty() {
        return stageHeight;
    }

    public double getStageWidth() {
        return stageWidth.get();
    }

    public void setStageWidth(double stageWidth) {
        this.stageWidth.set(stageWidth);
    }

    public DoubleProperty stageWidthProperty() {
        return stageWidth;
    }

    public double getStageX() {
        return stageX.get();
    }

    public void setStageX(double stageX) {
        this.stageX.set(stageX);
    }

    public DoubleProperty stageXProperty() {
        return stageX;
    }

    public double getStageY() {
        return stageY.get();
    }

    public void setStageY(double stageY) {
        this.stageY.set(stageY);
    }

    public DoubleProperty stageYProperty() {
        return stageY;
    }
}
