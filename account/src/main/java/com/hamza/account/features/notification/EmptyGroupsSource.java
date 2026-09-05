package com.hamza.account.features.notification;

import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.features.masterdata.MasterDataKind;
import com.hamza.account.features.masterdata.MasterDataRepository;
import com.hamza.controlsfx.notifications.AppNotification;
import com.hamza.controlsfx.notifications.NotificationCommand;
import com.hamza.controlsfx.notifications.NotificationSeverity;
import com.hamza.controlsfx.notifications.NotificationSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Two stable summaries, checked independently under each section's view permission. */
public final class EmptyGroupsSource implements NotificationSource {
    public static final String ID = "groups.empty";
    private final MasterDataRepository repository;
    private final Predicate<PermissionKey> granted;
    private final BiFunction<String, Object[], String> messages;
    private final Consumer<String> resolve;
    private final Function<MasterDataKind, NotificationCommand> open;
    private final Map<MasterDataKind, Long> previousCounts = new EnumMap<>(MasterDataKind.class);

    public EmptyGroupsSource(MasterDataRepository repository, Predicate<PermissionKey> granted,
                             BiFunction<String, Object[], String> messages, Consumer<String> resolve,
                             Function<MasterDataKind, NotificationCommand> open) {
        this.repository = repository;
        this.granted = granted;
        this.messages = messages;
        this.resolve = resolve;
        this.open = open;
    }

    @Override public String id() { return ID; }
    @Override public String category() { return NotificationCategories.ITEMS; }
    @Override public String displayName() { return text("masterdata.notify.rule"); }
    @Override public Duration interval() { return Duration.ofMinutes(15); }

    @Override public List<AppNotification> poll() throws Exception {
        List<AppNotification> notifications = new ArrayList<>();
        for (MasterDataKind kind : List.of(MasterDataKind.MAIN, MasterDataKind.SUB)) {
            boolean main = kind == MasterDataKind.MAIN;
            String suffix = main ? "main" : "sub";
            String key = ID + "." + suffix;
            long count = granted.test(kind.show) ? repository.countEmptyGroups(kind) : 0;
            Long previous = previousCounts.put(kind, count);
            // The centre coalesces by key without replacing the old message. Remove
            // changed summaries first, and remove resolved ones even after dismissal/snooze.
            if (count == 0 || !Objects.equals(previous, count)) resolve.accept(key);
            if (count == 0) continue;
            // Keys are written out rather than built from suffix: MessageKeyArchitectureTest
            // can only check a whole literal, and these four were missing from every bundle.
            notifications.add(AppNotification.builder(key)
                    .category(category()).severity(NotificationSeverity.INFO)
                    .title(text(main ? "masterdata.notify.main.title" : "masterdata.notify.sub.title"))
                    .message(main ? text("masterdata.notify.main.message", count)
                            : text("masterdata.notify.sub.message", count))
                    .onOpen(text("masterdata.notify.open"), open.apply(kind))
                    .build());
        }
        return notifications;
    }

    private String text(String key, Object... arguments) { return messages.apply(key, arguments); }
}
