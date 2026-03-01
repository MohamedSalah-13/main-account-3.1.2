package com.hamza.controlsfx.observer;

import java.util.ArrayList;
import java.util.List;

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

    @Override
    public void notifyObservers() {
        for (Observer<T> observer : observers) {
            observer.update(availability);
        }
    }

    public void setAvailability(T availability) {
        this.availability = availability;
        notifyObservers();
    }

}
