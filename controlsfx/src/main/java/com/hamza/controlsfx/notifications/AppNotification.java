package com.hamza.controlsfx.notifications;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One thing worth telling the user about.
 * <p>
 * The descriptive half - key, category, severity, title, message - is final. The
 * three fields that the inbox mutates are JavaFX properties so a list cell can
 * bind to them and repaint itself: {@code read}, {@code occurrences} (how many
 * times the same thing has been reported, see {@link NotificationPolicy}) and
 * {@code lastOccurredAt}.
 * <p>
 * Build one with {@link #builder(String)}. The {@code key} is the identity used
 * for coalescing, so two reports of the same condition must produce the same key
 * and two different conditions must not - {@code "items.low-stock"} is right for
 * "some items are low", {@code "items.low-stock." + itemId} is right for
 * per-item messages.
 */
public final class AppNotification {

    private final String key;
    private final String category;
    private final NotificationSeverity severity;
    private final String title;
    private final String message;
    private final LocalDateTime createdAt;
    private final Object payload;
    private final NotificationCommand onOpen;
    private final String actionLabel;

    private final BooleanProperty read = new SimpleBooleanProperty(false);
    private final IntegerProperty occurrences = new SimpleIntegerProperty(1);
    private final ObjectProperty<LocalDateTime> lastOccurredAt = new SimpleObjectProperty<>();

    /**
     * Which rule produced this, stamped by {@link NotificationScheduler} so the
     * settings screen can route one rule to Windows and another to the in-app
     * toast. Null for anything published directly through the centre - those are
     * routed by category instead.
     */
    private String sourceId;

    private AppNotification(Builder builder) {
        this.key = builder.key;
        this.category = builder.category;
        this.severity = builder.severity;
        this.title = builder.title;
        this.message = builder.message;
        this.createdAt = builder.createdAt;
        this.payload = builder.payload;
        this.onOpen = builder.onOpen;
        this.actionLabel = builder.actionLabel;
        this.lastOccurredAt.set(builder.createdAt);
    }

    public static Builder builder(@NotNull String key) {
        return new Builder(key);
    }

    public String key() {
        return key;
    }

    public String category() {
        return category;
    }

    public NotificationSeverity severity() {
        return severity;
    }

    public String title() {
        return title;
    }

    public String message() {
        return message;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    /**
     * Whatever the source attached - the list of low-stock rows, the failed file,
     * the customer record. The presenter does not look at it; the {@link #onOpen}
     * action does.
     */
    @Nullable
    public Object payload() {
        return payload;
    }

    @Nullable
    public String sourceId() {
        return sourceId;
    }

    /**
     * Set once by the scheduler as the notification leaves its source. Not on the
     * builder: a source should not have to remember to stamp its own id, and one
     * that stamped the wrong id would silently take another rule's routing.
     */
    void stampSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    @Nullable
    public NotificationCommand onOpen() {
        return onOpen;
    }

    public boolean hasAction() {
        return onOpen != null;
    }

    @Nullable
    public String actionLabel() {
        return actionLabel;
    }

    public boolean isRead() {
        return read.get();
    }

    public void setRead(boolean value) {
        read.set(value);
    }

    public BooleanProperty readProperty() {
        return read;
    }

    /**
     * How many times this condition has been reported since it entered the inbox.
     * Stays at 1 unless the policy coalesces a repeat into it.
     */
    public int getOccurrences() {
        return occurrences.get();
    }

    public IntegerProperty occurrencesProperty() {
        return occurrences;
    }

    public LocalDateTime getLastOccurredAt() {
        return lastOccurredAt.get();
    }

    public ObjectProperty<LocalDateTime> lastOccurredAtProperty() {
        return lastOccurredAt;
    }

    /**
     * Folds a fresh report of the same condition into this entry rather than adding
     * a second row: bumps the counter, moves the timestamp forward and marks it
     * unread again so it is not lost among the entries the user has already seen.
     * Called by {@link NotificationCenter}, on the UI thread.
     */
    void recordRepeat(LocalDateTime at) {
        occurrences.set(occurrences.get() + 1);
        lastOccurredAt.set(at);
        read.set(false);
    }

    @Override
    public String toString() {
        return "AppNotification[" + severity + " " + key + ": " + title + "]";
    }

    public static final class Builder {

        private final String key;
        private String category = "general";
        private NotificationSeverity severity = NotificationSeverity.INFO;
        private String title = "";
        private String message = "";
        private LocalDateTime createdAt = LocalDateTime.now();
        private Object payload;
        private NotificationCommand onOpen;
        private String actionLabel;

        private Builder(String key) {
            this.key = Objects.requireNonNull(key, "a notification needs a key to be coalesced by");
        }

        /**
         * Groups the notification for muting and filtering. Callers should use their
         * own constants rather than literals so a rename is a compile error.
         */
        public Builder category(String category) {
            this.category = Objects.requireNonNull(category);
            return this;
        }

        public Builder severity(NotificationSeverity severity) {
            this.severity = Objects.requireNonNull(severity);
            return this;
        }

        public Builder title(String title) {
            this.title = Objects.requireNonNullElse(title, "");
            return this;
        }

        public Builder message(String message) {
            this.message = Objects.requireNonNullElse(message, "");
            return this;
        }

        /** Overrides the clock, for tests and for reporting something that already happened. */
        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = Objects.requireNonNull(createdAt);
            return this;
        }

        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }

        /**
         * What to run when the user clicks the notification. Anything thrown is logged
         * and shown by the caller of the action, never swallowed here.
         */
        public Builder onOpen(String actionLabel, NotificationCommand onOpen) {
            this.actionLabel = actionLabel;
            this.onOpen = onOpen;
            return this;
        }

        public AppNotification build() {
            return new AppNotification(this);
        }
    }
}
