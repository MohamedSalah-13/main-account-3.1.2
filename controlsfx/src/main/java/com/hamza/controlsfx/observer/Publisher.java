package com.hamza.controlsfx.observer;

import javafx.application.Platform;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notifies its observers on the JavaFX application thread.
 * <p>
 * Every observer registered in this application updates the UI - refreshing a
 * table, refilling a combo box, firing a refresh button - but publishers are
 * notified from background threads too, such as the one that runs after an
 * invoice is saved. Those updates were being applied off the FX thread, where
 * JavaFX gives no guarantee about what happens: sometimes an exception, and
 * sometimes a corrupted control that misbehaves much later.
 * <p>
 * Subscribing with {@link #subscribe(Observer)} rather than
 * {@link #addObserver(Observer)} is what lets a screen unregister when it
 * closes; see {@link Subscription} for why that matters.
 */
@Log4j2
public class Publisher<T> implements Subject<T> {

    /**
     * Copy-on-write because {@code subscribe} and {@code publish} are called from
     * background threads - the save that fires a publisher rarely runs on the FX
     * thread - while the dispatch iterates on the FX thread.
     */
    private final List<Observer<T>> observers = new CopyOnWriteArrayList<>();

    /**
     * Only kept for the no-argument {@link #notifyObservers()}. Written from any
     * thread, read on the FX thread.
     */
    private volatile T availability;

    @Override
    public Subscription subscribe(Observer<T> observer) {
        observers.add(observer);
        return () -> observers.remove(observer);
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
     * Sends one message without storing it. This is the form to reach for: the
     * observers see exactly what this call passed, and nothing is retained
     * afterwards.
     */
    public void publish(T message) {
        this.availability = message;
        deliver(message);
    }

    /**
     * Re-sends whatever was published last - {@code null} where nothing ever was,
     * which is the case for most publishers here, since they carry no payload and
     * exist only to say "something changed". An observer reading the message must
     * therefore tolerate null; unboxing a {@code Publisher<Boolean>} message
     * straight into an {@code if} would throw.
     */
    @Override
    public void notifyObservers() {
        deliver(availability);
    }

    private void deliver(T message) {
        if (Platform.isFxApplicationThread()) {
            dispatch(message);
            return;
        }
        try {
            Platform.runLater(() -> dispatch(message));
        } catch (IllegalStateException e) {
            log.error("JavaFX is not running, so observers were not notified", e);
        }
    }

    /**
     * One failing observer must not cost the others their notification - they
     * belong to unrelated screens, and the exception would otherwise abandon the
     * rest of the list halfway through.
     * <p>
     * The list iterates its own snapshot, so an observer is free to subscribe or
     * unsubscribe while being notified.
     */
    private void dispatch(T message) {
        for (Observer<T> observer : observers) {
            try {
                observer.update(message);
            } catch (Exception e) {
                log.error("An observer failed while handling a notification", e);
            }
        }
    }

    /**
     * @deprecated use {@link #publish(T)}, which does the same thing under a name
     * that says what it does.
     */
    @Deprecated
    public void setAvailability(T availability) {
        publish(availability);
    }

}
