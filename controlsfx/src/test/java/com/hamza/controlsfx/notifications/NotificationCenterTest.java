package com.hamza.controlsfx.notifications;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the decisions that make the difference between a useful inbox and one
 * the user switches off: coalescing repeats, staying quiet during a cooldown,
 * honouring mutes and snoozes, and staying bounded.
 * <p>
 * No JavaFX toolkit is started. The centre takes its UI executor and its clock as
 * constructor arguments precisely so these can be {@code Runnable::run} and a
 * field the test moves by hand.
 */
class NotificationCenterTest {

    private static final String CATEGORY = "items";
    private static final String KEY = "items.low-stock";

    private final AtomicReference<LocalDateTime> now =
            new AtomicReference<>(LocalDateTime.of(2026, 8, 7, 9, 0));

    private NotificationPolicy policy;
    private NotificationCenter center;
    private List<AppNotification> announced;

    @BeforeEach
    void setUp() {
        policy = new NotificationPolicy();
        center = new NotificationCenter(policy, Runnable::run, now::get);
        announced = new ArrayList<>();
        center.addListener(announced::add);
    }

    private void advance(Duration duration) {
        now.set(now.get().plus(duration));
    }

    private AppNotification lowStock(int count) {
        return AppNotification.builder(KEY)
                .category(CATEGORY)
                .severity(NotificationSeverity.WARNING)
                .title("أصناف رصيدها قارب على الانتهاء")
                .message("عدد الأصناف: " + count)
                .createdAt(now.get())
                .build();
    }

    @Nested
    @DisplayName("A new condition")
    class NewCondition {

        @Test
        @DisplayName("lands in the inbox, unread, and is announced")
        void isAddedAndAnnounced() {
            center.publish(lowStock(3));

            assertEquals(1, center.getInbox().size());
            assertEquals(1, center.getUnreadCount());
            assertEquals(1, announced.size());
            assertFalse(center.getInbox().getFirst().isRead());
        }

        @Test
        @DisplayName("with a different key gets its own row")
        void differentKeysDoNotCollide() {
            center.publish(lowStock(3));
            center.publish(AppNotification.builder("treasury.negative")
                    .category("treasury")
                    .severity(NotificationSeverity.ERROR)
                    .title("رصيد بالسالب")
                    .createdAt(now.get())
                    .build());

            assertEquals(2, center.getInbox().size());
        }
    }

    @Nested
    @DisplayName("A repeat of a condition already in the inbox")
    class Repeat {

        @Test
        @DisplayName("is folded into the existing row rather than added")
        void coalescesInsteadOfAppending() {
            center.publish(lowStock(3));
            advance(Duration.ofMinutes(30));
            center.publish(lowStock(4));

            assertEquals(1, center.getInbox().size(), "a repeat must not add a second row");
            assertEquals(2, center.getInbox().getFirst().getOccurrences());
        }

        @Test
        @DisplayName("stays silent while inside the cooldown window")
        void doesNotReAnnounceDuringCooldown() {
            policy.setDefaultCooldown(Duration.ofHours(4));

            center.publish(lowStock(3));
            advance(Duration.ofMinutes(30));
            center.publish(lowStock(4));

            assertEquals(1, announced.size(), "the toast must not fire again inside the cooldown");
        }

        @Test
        @DisplayName("is announced again once the cooldown has passed")
        void reAnnouncesAfterCooldown() {
            policy.setDefaultCooldown(Duration.ofHours(4));

            center.publish(lowStock(3));
            advance(Duration.ofHours(5));
            center.publish(lowStock(4));

            assertEquals(2, announced.size());
        }

        @Test
        @DisplayName("marks a row the user had already read as unread again")
        void bringsBackAnAlreadyReadEntry() {
            center.publish(lowStock(3));
            center.markAllRead();
            assertEquals(0, center.getUnreadCount());

            advance(Duration.ofHours(5));
            center.publish(lowStock(4));

            assertEquals(1, center.getUnreadCount());
        }

        @Test
        @DisplayName("at CRITICAL ignores the cooldown entirely")
        void criticalIsExemptFromCooldown() {
            policy.setDefaultCooldown(Duration.ofHours(4));

            center.publish(critical());
            advance(Duration.ofMinutes(1));
            center.publish(critical());

            assertEquals(2, announced.size(), "a critical condition must keep announcing");
        }

        private AppNotification critical() {
            return AppNotification.builder("system.critical")
                    .category("system")
                    .severity(NotificationSeverity.CRITICAL)
                    .title("توقف")
                    .createdAt(now.get())
                    .build();
        }
    }

    @Nested
    @DisplayName("Suppression")
    class Suppression {

        @Test
        @DisplayName("drops everything in a muted category")
        void mutedCategoryIsDropped() {
            policy.setMuted(CATEGORY, true);

            center.publish(lowStock(3));

            assertTrue(center.getInbox().isEmpty());
            assertTrue(announced.isEmpty());
        }

        @Test
        @DisplayName("drops everything while notifications are switched off")
        void disabledDropsEverything() {
            policy.setEnabled(false);

            center.publish(lowStock(3));

            assertTrue(center.getInbox().isEmpty());
        }

        @Test
        @DisplayName("keeps a snoozed condition out until the snooze expires")
        void snoozeSuppressesThenReleases() {
            center.publish(lowStock(3));
            center.snooze(center.getInbox().getFirst(), Duration.ofHours(8));
            assertTrue(center.getInbox().isEmpty(), "snoozing removes the entry");

            advance(Duration.ofHours(1));
            center.publish(lowStock(3));
            assertTrue(center.getInbox().isEmpty(), "the next poll must not bring it straight back");

            advance(Duration.ofHours(8));
            center.publish(lowStock(3));
            assertEquals(1, center.getInbox().size());
        }

        @Test
        @DisplayName("only silences the snoozed key, not the rest of its category")
        void snoozeIsPerKey() {
            center.publish(lowStock(3));
            center.snooze(center.getInbox().getFirst(), Duration.ofHours(8));

            center.publish(AppNotification.builder("items.expired")
                    .category(CATEGORY)
                    .severity(NotificationSeverity.WARNING)
                    .title("أصناف منتهية")
                    .createdAt(now.get())
                    .build());

            assertEquals(1, center.getInbox().size());
        }
    }

    @Nested
    @DisplayName("The inbox")
    class Inbox {

        @Test
        @DisplayName("puts the most recent entry first")
        void newestFirst() {
            center.publish(named("a"));
            advance(Duration.ofMinutes(1));
            center.publish(named("b"));

            assertEquals("b", center.getInbox().getFirst().key());
        }

        @Test
        @DisplayName("moves a coalesced entry back to the top")
        void coalescedEntryReturnsToTheTop() {
            center.publish(named("a"));
            advance(Duration.ofMinutes(1));
            center.publish(named("b"));
            advance(Duration.ofHours(5));
            center.publish(named("a"));

            assertEquals("a", center.getInbox().getFirst().key());
        }

        @Test
        @DisplayName("stays within the configured maximum")
        void isBounded() {
            policy.setMaxInbox(5);

            for (int i = 0; i < 20; i++) {
                advance(Duration.ofMinutes(1));
                center.publish(named("key-" + i));
            }

            assertEquals(5, center.getInbox().size());
        }

        @Test
        @DisplayName("discards read entries before unread ones when trimming")
        void discardsReadEntriesFirst() {
            policy.setMaxInbox(2);

            center.publish(named("old-and-read"));
            center.markAllRead();
            advance(Duration.ofMinutes(1));
            center.publish(named("unread-one"));
            advance(Duration.ofMinutes(1));
            center.publish(named("unread-two"));

            assertEquals(2, center.getInbox().size());
            assertTrue(center.getInbox().stream().noneMatch(n -> n.key().equals("old-and-read")));
        }

        @Test
        @DisplayName("stops tracking a dismissed key, so the condition can be reported afresh")
        void dismissForgetsTheKey() {
            center.publish(lowStock(3));
            AppNotification first = center.getInbox().getFirst();
            center.dismiss(first);

            center.publish(lowStock(9));

            assertEquals(1, center.getInbox().size());
            assertEquals(1, center.getInbox().getFirst().getOccurrences());
            assertEquals("عدد الأصناف: 9", center.getInbox().getFirst().message());
        }

        @Test
        @DisplayName("announces the stored entry, not the candidate, when coalescing")
        void announcesTheStoredEntry() {
            center.publish(lowStock(3));
            AppNotification stored = center.getInbox().getFirst();
            advance(Duration.ofHours(5));
            center.publish(lowStock(4));

            assertSame(stored, announced.get(1),
                    "listeners need the entry with the bumped counter, not the transient candidate");
        }

        private AppNotification named(String key) {
            return AppNotification.builder(key)
                    .category(CATEGORY)
                    .severity(NotificationSeverity.INFO)
                    .title(key)
                    .createdAt(now.get())
                    .build();
        }
    }

    @Nested
    @DisplayName("Toast filtering")
    class Toasting {

        @Test
        @DisplayName("keeps quiet below the threshold and toasts at or above it")
        void respectsTheThreshold() {
            policy.setToastThreshold(NotificationSeverity.WARNING);

            assertFalse(policy.shouldToast(withSeverity(NotificationSeverity.INFO)));
            assertTrue(policy.shouldToast(withSeverity(NotificationSeverity.WARNING)));
            assertTrue(policy.shouldToast(withSeverity(NotificationSeverity.ERROR)));
        }

        @Test
        @DisplayName("toasts nothing while notifications are switched off")
        void nothingToastsWhenDisabled() {
            policy.setEnabled(false);

            assertFalse(policy.shouldToast(withSeverity(NotificationSeverity.CRITICAL)));
        }

        private AppNotification withSeverity(NotificationSeverity severity) {
            return AppNotification.builder("k").severity(severity).title("t").build();
        }
    }

    @Nested
    @DisplayName("Channel routing")
    class Routing {

        private static final String SOURCE_ID = "items.low-stock";

        @Test
        @DisplayName("falls back to the global default when nothing is pinned")
        void usesTheDefault() {
            policy.setDefaultChannel(NotificationChannel.WINDOWS);

            assertEquals(NotificationChannel.WINDOWS, policy.channelFor(fromSource()));
        }

        @Test
        @DisplayName("prefers the category setting over the global default")
        void categoryBeatsDefault() {
            policy.setDefaultChannel(NotificationChannel.IN_APP);
            policy.setChannel(CATEGORY, NotificationChannel.WINDOWS);

            assertEquals(NotificationChannel.WINDOWS, policy.channelFor(fromSource()));
        }

        @Test
        @DisplayName("prefers the rule setting over the category setting")
        void sourceBeatsCategory() {
            policy.setDefaultChannel(NotificationChannel.IN_APP);
            policy.setChannel(CATEGORY, NotificationChannel.WINDOWS);
            policy.setChannel(SOURCE_ID, NotificationChannel.BOTH);

            assertEquals(NotificationChannel.BOTH, policy.channelFor(fromSource()));
        }

        @Test
        @DisplayName("clearing a rule setting hands it back to the category")
        void clearingRestoresInheritance() {
            policy.setChannel(CATEGORY, NotificationChannel.WINDOWS);
            policy.setChannel(SOURCE_ID, NotificationChannel.IN_APP);
            policy.setChannel(SOURCE_ID, null);

            assertEquals(NotificationChannel.WINDOWS, policy.channelFor(fromSource()));
        }

        @Test
        @DisplayName("routes a notification published outside any rule by its category")
        void unstampedNotificationUsesItsCategory() {
            policy.setDefaultChannel(NotificationChannel.IN_APP);
            policy.setChannel(CATEGORY, NotificationChannel.WINDOWS);

            // No source id: this is what AppNotifications.error(...) produces.
            assertEquals(NotificationChannel.WINDOWS, policy.channelFor(lowStock(1)));
        }

        @Test
        @DisplayName("SILENT records the entry but reaches neither channel")
        void silentReachesNoChannel() {
            policy.setDefaultChannel(NotificationChannel.SILENT);
            NotificationChannel channel = policy.channelFor(fromSource());

            assertFalse(channel.includesInApp());
            assertFalse(channel.includesWindows());
        }

        @Test
        @DisplayName("BOTH reaches both channels")
        void bothReachesBoth() {
            assertTrue(NotificationChannel.BOTH.includesInApp());
            assertTrue(NotificationChannel.BOTH.includesWindows());
        }

        private AppNotification fromSource() {
            AppNotification notification = lowStock(1);
            notification.stampSourceId(SOURCE_ID);
            return notification;
        }
    }

    @Nested
    @DisplayName("Per-rule intervals")
    class Intervals {

        private final NotificationScheduler scheduler = new NotificationScheduler(center);

        @Test
        @DisplayName("use the rule's own interval when the user has set none")
        void defaultsToTheRuleInterval() {
            scheduler.register(source(Duration.ofMinutes(30)));

            assertEquals(Duration.ofMinutes(30), scheduler.effectiveInterval("rule"));
        }

        @Test
        @DisplayName("use the user's value once one is set")
        void overrideWins() {
            scheduler.register(source(Duration.ofMinutes(30)));
            scheduler.setInterval("rule", Duration.ofMinutes(120));

            assertEquals(Duration.ofMinutes(120), scheduler.effectiveInterval("rule"));
        }

        @Test
        @DisplayName("go back to the rule's own interval when the override is cleared")
        void clearingRestoresTheRuleInterval() {
            scheduler.register(source(Duration.ofMinutes(30)));
            scheduler.setInterval("rule", Duration.ofMinutes(120));
            scheduler.setInterval("rule", null);

            assertEquals(Duration.ofMinutes(30), scheduler.effectiveInterval("rule"));
        }

        @Test
        @DisplayName("never drop below the scheduler's floor")
        void isClampedToTheMinimum() {
            scheduler.register(source(Duration.ofMinutes(30)));
            scheduler.setInterval("rule", Duration.ofSeconds(5));

            assertEquals(NotificationScheduler.MINIMUM_INTERVAL, scheduler.effectiveInterval("rule"));
        }

        private NotificationSource source(Duration interval) {
            return new NotificationSource() {
                @Override
                public String id() {
                    return "rule";
                }

                @Override
                public String category() {
                    return CATEGORY;
                }

                @Override
                public String displayName() {
                    return "rule";
                }

                @Override
                public Duration interval() {
                    return interval;
                }

                @Override
                public List<AppNotification> poll() {
                    return List.of();
                }
            };
        }
    }
}
