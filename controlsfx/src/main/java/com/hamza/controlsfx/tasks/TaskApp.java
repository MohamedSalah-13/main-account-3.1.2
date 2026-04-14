package com.hamza.controlsfx.tasks;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.concurrent.Task;
import lombok.extern.log4j.Log4j2;

import static com.hamza.controlsfx.util.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
public class TaskApp<V> extends Task<V> {
    private final TaskInterface<V> appData;
    private final IntegerProperty length = new SimpleIntegerProperty();
    private final IntegerProperty sleepProperty = new SimpleIntegerProperty();

    public TaskApp(TaskInterface<V> appData, int length) {
        this.appData = appData;
        this.setLength(length);
    }

    @Override
    protected V call() throws Exception {
        try {
            return appData.action(this);
        } catch (Exception e) {
            log.error(e.getMessage(), e.getCause());
        }
        return null;
    }

    public void getData(double i, String string) {
        try {
            int sum = (int) roundToTwoDecimalPlaces((i / getLength()) * 100);
            updateProgress(i, getLength());
            updateMessage(String.valueOf(sum));
            updateTitle("Load Data " + string);
            Thread.sleep(100);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e.getCause());
        }
    }

    public int getLength() {
        return length.get();
    }

    public void setLength(int length) {
        this.length.set(length);
    }

    public IntegerProperty lengthProperty() {
        return length;
    }

    public int getSleepProperty() {
        return sleepProperty.get();
    }

    public void setSleepProperty(int sleepProperty) {
        this.sleepProperty.set(sleepProperty);
    }

    public IntegerProperty sleepPropertyProperty() {
        return sleepProperty;
    }
}
