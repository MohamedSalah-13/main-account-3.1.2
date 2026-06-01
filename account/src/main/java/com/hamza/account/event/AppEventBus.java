package com.hamza.account.event;

import javafx.application.Platform;

import java.util.ArrayList;
import java.util.List;

public class AppEventBus {

    private final List<AppEventListener> listeners = new ArrayList<>();

    public void subscribe(AppEventListener listener) {
        listeners.add(listener);
    }

    public void unsubscribe(AppEventListener listener) {
        listeners.remove(listener);
    }

    public void publish(AppEvent event) {
        for (AppEventListener listener : new ArrayList<>(listeners)) {
            if (Platform.isFxApplicationThread()) {
                listener.onEvent(event);
            } else {
                Platform.runLater(() -> listener.onEvent(event));
            }
        }
    }
}
