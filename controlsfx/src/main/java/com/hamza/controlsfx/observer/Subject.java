package com.hamza.controlsfx.observer;

public interface Subject<T> {

    /**
     * Registers an observer and returns the handle that removes it again. Prefer
     * this to {@link #addObserver(Observer)} - a lambda cannot be passed to
     * {@link #removeObserver(Observer)} afterwards, since it is not the same
     * object the second time it is written.
     */
    Subscription subscribe(Observer<T> observer);

    void addObserver(Observer<T> observer);

    void removeObserver(Observer<T> observer);

    void notifyObservers();

}
