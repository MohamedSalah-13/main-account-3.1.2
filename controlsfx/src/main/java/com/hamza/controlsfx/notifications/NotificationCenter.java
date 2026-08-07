package com.hamza.controlsfx.notifications;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The inbox every notification passes through.
 * <p>
 * Publishing is safe from any thread - rules run on a background scheduler, and
 * controllers publish from the FX thread - because everything is funnelled onto
 * the UI executor before it touches the list, in the same way {@code Publisher}
 * marshals its observers. Callers therefore never need {@code Platform.runLater}
 * of their own.
 * <p>
 * The list is the single source of truth for the UI: the bell binds to
 * {@link #unreadCountProperty()} and the panel binds to {@link #getInbox()}, so
 * neither has to be told when something arrives.
 * <p>
 * There is a process-wide {@link #getInstance()} because the alternative in this
 * codebase is another entry in {@code ServiceRegistry}, which returns null if the
 * registration order changes. Tests build their own with
 * {@link #NotificationCenter(NotificationPolicy, Executor, Supplier)} and pass
 * {@code Runnable::run} so nothing needs a JavaFX toolkit.
 */
@Log4j2
public class NotificationCenter {

    private static final NotificationCenter INSTANCE = new NotificationCenter();

    private final ObservableList<AppNotification> inbox = FXCollections.observableArrayList();
    private final ObservableList<AppNotification> readOnlyInbox = FXCollections.unmodifiableObservableList(inbox);
    private final Map<String, AppNotification> byKey = new HashMap<>();
    private final List<NotificationListener> listeners = new CopyOnWriteArrayList<>();
    private final ReadOnlyIntegerWrapper unreadCount = new ReadOnlyIntegerWrapper(0);

    private final NotificationPolicy policy;
    private final Executor uiExecutor;
    private final Supplier<LocalDateTime> clock;

    private NotificationCenter() {
        this(new NotificationPolicy(), NotificationCenter::runOnFxThread, LocalDateTime::now);
    }

    /**
     * @param policy     the coalescing and muting rules
     * @param uiExecutor where mutations run; {@code Runnable::run} in tests, the FX
     *                   thread in the application
     * @param clock      the time source, injected so cooldown behaviour can be tested
     */
    public NotificationCenter(@NotNull NotificationPolicy policy,
                              @NotNull Executor uiExecutor,
                              @NotNull Supplier<LocalDateTime> clock) {
        this.policy = policy;
        this.uiExecutor = uiExecutor;
        this.clock = clock;
    }

    public static NotificationCenter getInstance() {
        return INSTANCE;
    }

    /**
     * Runs inline when already on the FX thread so a caller that reads the inbox on
     * the next line sees its own publish, and posts otherwise. A missing toolkit is
     * logged rather than thrown: a notification failing to display must never take
     * down the operation that produced it.
     */
    private static void runOnFxThread(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
            return;
        }
        try {
            Platform.runLater(runnable);
        } catch (IllegalStateException e) {
            log.error("JavaFX is not running, so the notification was dropped", e);
        }
    }

    public NotificationPolicy policy() {
        return policy;
    }

    /** Newest first. Unmodifiable - go through the centre to change it. */
    public ObservableList<AppNotification> getInbox() {
        return readOnlyInbox;
    }

    public ReadOnlyIntegerProperty unreadCountProperty() {
        return unreadCount.getReadOnlyProperty();
    }

    public int getUnreadCount() {
        return unreadCount.get();
    }

    public void addListener(@NotNull NotificationListener listener) {
        listeners.add(listener);
    }

    public void removeListener(@NotNull NotificationListener listener) {
        listeners.remove(listener);
    }

    /**
     * Offers a notification to the inbox. Whether it lands as a new row, is folded
     * into an existing one, or is dropped entirely is {@link NotificationPolicy}'s
     * call - see there for why repeats are not simply appended.
     */
    public void publish(@NotNull AppNotification notification) {
        uiExecutor.execute(() -> publishNow(notification));
    }

    /** Convenience for the common case of a message with no action and no payload. */
    public void publish(@NotNull String key,
                        @NotNull String category,
                        @NotNull NotificationSeverity severity,
                        @NotNull String title,
                        @NotNull String message) {
        publish(AppNotification.builder(key)
                .category(category)
                .severity(severity)
                .title(title)
                .message(message)
                .build());
    }

    public void publishAll(@NotNull List<AppNotification> notifications) {
        if (notifications.isEmpty()) {
            return;
        }
        uiExecutor.execute(() -> notifications.forEach(this::publishNow));
    }

    private void publishNow(AppNotification candidate) {
        LocalDateTime now = clock.get();
        AppNotification existing = byKey.get(candidate.key());
        NotificationPolicy.Decision decision = policy.decide(candidate, existing, now);

        AppNotification delivered;
        switch (decision.outcome()) {
            case DROP -> {
                log.debug("Notification suppressed by policy: {}", candidate.key());
                return;
            }
            case COALESCE -> {
                existing.recordRepeat(now);
                // Back to the top: a condition that is still true is more current than
                // whatever arrived while it sat unchanged.
                inbox.remove(existing);
                inbox.addFirst(existing);
                delivered = existing;
            }
            case ADD -> {
                byKey.put(candidate.key(), candidate);
                inbox.addFirst(candidate);
                trimToMaxSize();
                delivered = candidate;
            }
            default -> throw new IllegalStateException("Unhandled outcome: " + decision.outcome());
        }

        recomputeUnread();

        if (decision.announce()) {
            notifyListeners(delivered);
        }
    }

    /**
     * Keeps the inbox bounded. Read entries go first - the user has seen them - and
     * only once those run out does an unread entry get discarded, oldest first.
     */
    private void trimToMaxSize() {
        int max = policy.getMaxInbox();
        while (inbox.size() > max) {
            AppNotification victim = null;
            for (int i = inbox.size() - 1; i >= 0; i--) {
                if (inbox.get(i).isRead()) {
                    victim = inbox.get(i);
                    break;
                }
            }
            if (victim == null) {
                victim = inbox.getLast();
            }
            inbox.remove(victim);
            byKey.remove(victim.key(), victim);
        }
    }

    private void notifyListeners(AppNotification notification) {
        for (NotificationListener listener : listeners) {
            try {
                listener.onNotification(notification);
            } catch (Exception e) {
                // One broken presenter must not stop the others, and must not
                // propagate back into the code that published.
                log.error("A notification listener failed for {}", notification.key(), e);
            }
        }
    }

    public void markRead(@NotNull AppNotification notification) {
        uiExecutor.execute(() -> {
            notification.setRead(true);
            recomputeUnread();
        });
    }

    public void markAllRead() {
        uiExecutor.execute(() -> {
            inbox.forEach(n -> n.setRead(true));
            recomputeUnread();
        });
    }

    public void dismiss(@NotNull AppNotification notification) {
        uiExecutor.execute(() -> {
            inbox.remove(notification);
            byKey.remove(notification.key(), notification);
            recomputeUnread();
        });
    }

    /**
     * Removes the entry and stops the same condition coming back for {@code duration}.
     * This is what "not now" on a low-stock warning should do - dismissing alone
     * would let the next poll put it straight back.
     */
    public void snooze(@NotNull AppNotification notification, @NotNull Duration duration) {
        uiExecutor.execute(() -> {
            policy.snooze(notification.key(), duration, clock.get());
            inbox.remove(notification);
            byKey.remove(notification.key(), notification);
            recomputeUnread();
        });
    }

    public void clearAll() {
        uiExecutor.execute(() -> {
            inbox.clear();
            byKey.clear();
            recomputeUnread();
        });
    }

    /** The entry currently held under {@code key}, for callers that want to update in place. */
    @Nullable
    public AppNotification find(@NotNull String key) {
        return byKey.get(key);
    }

    public List<AppNotification> snapshot() {
        return new ArrayList<>(inbox);
    }

    private void recomputeUnread() {
        int unread = 0;
        for (AppNotification notification : inbox) {
            if (!notification.isRead()) {
                unread++;
            }
        }
        unreadCount.set(unread);
    }
}
