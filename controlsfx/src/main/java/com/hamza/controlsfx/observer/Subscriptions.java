package com.hamza.controlsfx.observer;

import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Window;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The subscriptions one screen holds, closed together when the screen goes away.
 * <p>
 * A screen subscribes in {@code initialize()} and ends with
 * {@link #disposeWith(Node)}; nothing else has to remember to clean up:
 * <pre>
 * subscriptions.subscribe(dataPublisher.getPublisherAddUnits(), message -&gt; btnRefresh.fire());
 * subscriptions.add(eventBus.subscribe(ItemSaved.class, event -&gt; btnRefresh.fire()));
 * subscriptions.disposeWith(stackPane);
 * </pre>
 * The alternative - having each of the ten classes that open these screens close
 * them by hand - was one forgotten call away from the leak this exists to stop,
 * and two of those screens (an invoice pane put into a tab, a totals pane) never
 * see a {@code Stage} at all.
 */
public final class Subscriptions implements Subscription {

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    private final ChangeListener<Boolean> onShowing = (observable, was, showing) -> {
        if (!showing) unsubscribe();
    };
    private final ChangeListener<Window> onWindow = (observable, old, window) -> watchWindow(window);
    private final ChangeListener<Scene> onScene = (observable, old, scene) -> {
        // Removed from the scene graph - a closed tab - rather than merely moved.
        if (old != null && scene == null) unsubscribe();
        else watchScene(scene);
    };

    private Node node;
    private Scene scene;
    private Window window;

    /**
     * Subscribes and keeps the handle.
     */
    public <T> void subscribe(Subject<T> subject, Observer<T> observer) {
        subscriptions.add(subject.subscribe(observer));
    }

    public void add(Subscription subscription) {
        subscriptions.add(subscription);
    }

    /**
     * Closes everything held, and stops watching the node. Safe to call twice, and
     * safe to call from a listener it installed itself.
     */
    @Override
    public void unsubscribe() {
        for (Subscription subscription : subscriptions) {
            subscription.unsubscribe();
        }
        subscriptions.clear();
        stopWatching();
    }

    /**
     * Closes the subscriptions once {@code node} leaves the scene graph or the
     * window showing it is hidden.
     * <p>
     * Two signals are needed because neither covers both cases: closing a stage
     * leaves its scene attached to the root node, so the node never notices, and a
     * pane sitting in a tab of a window that stays open is detached without any
     * window being hidden.
     */
    public void disposeWith(Node node) {
        this.node = node;
        node.sceneProperty().addListener(onScene);
        watchScene(node.getScene());
    }

    private void watchScene(Scene scene) {
        if (scene == null || scene == this.scene) return;
        if (this.scene != null) this.scene.windowProperty().removeListener(onWindow);
        this.scene = scene;
        scene.windowProperty().addListener(onWindow);
        watchWindow(scene.getWindow());
    }

    private void watchWindow(Window window) {
        if (window == null || window == this.window) return;
        if (this.window != null) this.window.showingProperty().removeListener(onShowing);
        this.window = window;
        window.showingProperty().addListener(onShowing);
    }

    /**
     * Leaving the listeners installed would keep this object attached to a window
     * that outlives the screen - the main stage, for a tab - once for every time
     * that screen was opened.
     */
    private void stopWatching() {
        if (node != null) node.sceneProperty().removeListener(onScene);
        if (scene != null) scene.windowProperty().removeListener(onWindow);
        if (window != null) window.showingProperty().removeListener(onShowing);
        node = null;
        scene = null;
        window = null;
    }
}
