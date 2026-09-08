package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The events that travel between machines, the names they travel under, and which half of
 * the system writes each announcement.
 *
 * <p>An event is relayable when a machine that did not raise it can rebuild it exactly.
 * That is the whole rule, and it draws a clean line through the event list:
 *
 * <ul>
 *   <li>{@link ItemsChanged} carries nothing, so the copy another till publishes is the
 *       same event. It is relayed.</li>
 *   <li>{@link NameChanged} carries which side of the ledger changed - two possible
 *       values, so it is two topics rather than one. Also relayed.</li>
 *   <li>{@link ItemSaved} carries the item. Another machine must not receive a fabricated
 *       model, so the local event is announced under the {@code items} topic and arrives
 *       there as the payload-free {@link ItemsChanged} reload signal.</li>
 *   <li>{@link com.hamza.account.features.events.LanguageChanged} and {@code FontChanged}
 *       are about the person sitting at one computer. Relaying them would change the
 *       language on someone else's screen mid-sale.</li>
 * </ul>
 *
 * <p>Both directions are needed: the topic to write when something happens here, and the
 * event to publish when a topic moves elsewhere. Records are value types, so one map does
 * both.
 *
 * <h2>Who writes the announcement</h2>
 *
 * <p>Every topic declares one {@link Announcer}, and getting this wrong is not a crash but
 * a quiet cost or a quiet loss.
 *
 * <p>{@link Announcer#SERVICE} means the service that performs the write announces it
 * through {@link ChangeAnnouncer}, on the write's own connection and inside its own
 * transaction. That is the stronger guarantee: a save that is rolled back - by the
 * optimistic-lock guard, by a period lock, by anything - takes its announcement back with
 * it, so no other till is ever told to reload for a change that did not happen. Those
 * topics are deliberately <b>not</b> subscribed to by {@link RemoteChangeRelay}, because
 * the screen also publishes the same event on the bus after the save returns, and the relay
 * would then write a second, identical announcement: measured on a real database, one
 * invoice save moved {@code data_change.revision} by two.
 *
 * <p>{@link Announcer#RELAY} means nothing announces it transactionally and the relay's own
 * bus listener is the only writer. That is the right answer for a change whose service does
 * not announce - the master-data screens, the company row, the users list.
 *
 * <p>The consequence to keep in mind when adding a topic: a {@code SERVICE} topic that no
 * service actually announces never reaches another machine at all. The relay will not cover
 * for it. {@code MultiDeviceRefreshArchitectureTest} fails the build when that happens, and
 * {@link com.hamza.account.controller.main.LoadDataAndList} is the one place that announces
 * such topics without a service behind it, because a restore replaces the whole database and
 * has no single write to hang an announcement on.
 */
public final class RemoteChangeTopics {

    /** Which half of the system writes a topic's row in {@code data_change}. */
    public enum Announcer {
        /** The writing service, inside its own transaction. */
        SERVICE,
        /** {@link RemoteChangeRelay}, when the event reaches the local bus. */
        RELAY
    }

    private static final Map<String, AppEvent> EVENT_BY_TOPIC = new LinkedHashMap<>();
    private static final Map<AppEvent, String> TOPIC_BY_EVENT = new LinkedHashMap<>();
    private static final Map<Class<? extends AppEvent>, String> TOPIC_BY_SOURCE_TYPE = new LinkedHashMap<>();
    private static final Map<String, Announcer> ANNOUNCER_BY_TOPIC = new LinkedHashMap<>();

    static {
        declare("items", new ItemsChanged(), Announcer.SERVICE);
        declare("stocks", new StocksChanged(), Announcer.RELAY);
        declare("areas", new AreasChanged(), Announcer.RELAY);
        declare("units", new UnitsChanged(), Announcer.RELAY);
        declare("treasuries", new TreasuriesChanged(), Announcer.RELAY);
        declare("expenses", new ExpensesChanged(), Announcer.RELAY);
        declare("employees", new EmployeesChanged(), Announcer.RELAY);
        declare("company", new CompanyChanged(), Announcer.RELAY);
        declare("users", new UsersChanged(), Announcer.RELAY);
        declare("groups.main", new GroupsChanged(GroupLevel.MAIN), Announcer.RELAY);
        declare("groups.sub", new GroupsChanged(GroupLevel.SUB), Announcer.RELAY);
        declare("name.customer", new NameChanged(PartyKind.CUSTOMER), Announcer.SERVICE);
        declare("name.supplier", new NameChanged(PartyKind.SUPPLIER), Announcer.SERVICE);
        declare("account.customer", new AccountChanged(PartyKind.CUSTOMER), Announcer.SERVICE);
        declare("account.supplier", new AccountChanged(PartyKind.SUPPLIER), Announcer.SERVICE);
        declare("invoice.sales", new InvoiceSaved(InvoiceSide.SALES), Announcer.SERVICE);
        declare("invoice.purchase", new InvoiceSaved(InvoiceSide.PURCHASE), Announcer.SERVICE);
        declare("stock.balances", new StockBalancesChanged(), Announcer.SERVICE);
        declare("treasury.balances", new TreasuryBalancesChanged(), Announcer.SERVICE);

        // These local events carry details which are useful in-process but cannot be
        // reconstructed safely on another machine. Announce the broad topic and inject
        // the payload-free event declared above on the receiving side.
        announceAs(ItemSaved.class, "items");
        announceAs(StockCountPosted.class, "stock.balances");
        announceAs(TreasuryMovementRecorded.class, "treasury.balances");
    }

    private RemoteChangeTopics() {
    }

    private static void declare(String topic, AppEvent event, Announcer announcer) {
        EVENT_BY_TOPIC.put(topic, event);
        TOPIC_BY_EVENT.put(event, topic);
        TOPIC_BY_SOURCE_TYPE.put(event.getClass(), topic);
        ANNOUNCER_BY_TOPIC.put(topic, announcer);
    }

    private static void announceAs(Class<? extends AppEvent> sourceType, String topic) {
        if (!EVENT_BY_TOPIC.containsKey(topic)) {
            throw new IllegalArgumentException("Unknown remote change topic: " + topic);
        }
        TOPIC_BY_SOURCE_TYPE.put(sourceType, topic);
    }

    /** Every relayed event, one instance each - the same instances the relay publishes. */
    public static Map<String, AppEvent> all() {
        return Map.copyOf(EVENT_BY_TOPIC);
    }

    /** The topic to announce for an event raised here, or {@code null} when it stays local. */
    public static String topicOf(AppEvent event) {
        String exact = TOPIC_BY_EVENT.get(event);
        return exact != null || event == null ? exact : TOPIC_BY_SOURCE_TYPE.get(event.getClass());
    }

    /** The event to publish for a topic another machine moved, or {@code null} when unknown. */
    public static AppEvent eventOf(String topic) {
        return EVENT_BY_TOPIC.get(topic);
    }

    /** Which half of the system writes this topic's announcement. */
    public static Announcer announcerOf(String topic) {
        return ANNOUNCER_BY_TOPIC.get(topic);
    }

    /**
     * The topic an event type travels under, including the aliases {@link #announceAs}
     * declares. Unlike {@link #topicOf} this needs no instance, so it can answer for
     * {@link ItemSaved} - whose topic is the same whichever item it carries.
     */
    public static String topicOfType(Class<? extends AppEvent> type) {
        return TOPIC_BY_SOURCE_TYPE.get(type);
    }

    /** The topics whose announcement belongs to the writing service's own transaction. */
    public static Set<String> serviceAnnouncedTopics() {
        Set<String> topics = new LinkedHashSet<>();
        ANNOUNCER_BY_TOPIC.forEach((topic, announcer) -> {
            if (announcer == Announcer.SERVICE) {
                topics.add(topic);
            }
        });
        return Set.copyOf(topics);
    }

    /**
     * The local event types {@link RemoteChangeRelay} listens for.
     *
     * <p>Only the {@link Announcer#RELAY} topics: the rest are already announced inside the
     * transaction that made the change, and listening for them too is what wrote every
     * announcement twice.
     */
    public static Set<Class<? extends AppEvent>> announcementTypes() {
        Set<Class<? extends AppEvent>> types = new LinkedHashSet<>();
        TOPIC_BY_SOURCE_TYPE.forEach((type, topic) -> {
            if (ANNOUNCER_BY_TOPIC.get(topic) == Announcer.RELAY) {
                types.add(type);
            }
        });
        return Set.copyOf(types);
    }
}
