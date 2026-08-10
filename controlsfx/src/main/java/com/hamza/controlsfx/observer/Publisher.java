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
     * Sends one message. The observers see exactly what this call passed, and
     * nothing is kept afterwards - this class holds no state at all, so no
     * observer can be handed a value some earlier, unrelated call published.
     */
    public void publish(T message) {
        deliver(message);
    }

    /**
     * Says "something changed" without a payload, which is what most publishers
     * here are for. Observers are handed {@code null}, so one that reads its
     * message has to expect it - unboxing a {@code Publisher<Boolean>} message
     * straight into an {@code if} would throw.
     */
    public void publish() {
        deliver(null);
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

}
