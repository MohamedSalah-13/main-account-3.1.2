package com.hamza.controlsfx.observer;

import javafx.application.Platform;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

/**
 * Notifies its observers on the JavaFX application thread.
 * <p>
 * Every observer registered in this application updates the UI - refreshing a
 * table, refilling a combo box, firing a refresh button - but publishers are
 * notified from background threads too, such as the one that runs after an
 * invoice is saved. Those updates were being applied off the FX thread, where
 * JavaFX gives no guarantee about what happens: sometimes an exception, and
 * sometimes a corrupted control that misbehaves much later.
 */
@Log4j2
public class Publisher<T> implements Subject<T> {

    private final List<Observer<T>> observers;
    private T availability;

    public Publisher() {
        observers = new ArrayList<>();
    }


    @Override
    public void addObserver(Observer<T> observer) {
        observers.add(observer);
    }

    @Override
    public void removeObserver(Observer<T> observer) {
        observers.remove(observer);
    }

    /**
     * On the FX thread the observers run inline, so a caller that reads the UI on
     * the next line still sees the update. From any other thread the notification
     * is posted to the FX thread instead.
     */
    @Override
    public void notifyObservers() {
        if (Platform.isFxApplicationThread()) {
            dispatch();
            return;
        }
        try {
            Platform.runLater(this::dispatch);
        } catch (IllegalStateException e) {
            log.error("JavaFX is not running, so observers were not notified", e);
        }
    }

    /**
     * Iterates a copy: an observer is free to subscribe or unsubscribe while being
     * notified, which would otherwise throw ConcurrentModificationException.
     */
    private void dispatch() {
        for (Observer<T> observer : new ArrayList<>(observers)) {
            observer.update(availability);
        }
    }

    public void setAvailability(T availability) {
        this.availability = availability;
        notifyObservers();
    }

}
