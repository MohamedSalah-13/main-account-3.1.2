package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The events that travel between machines, and the names they travel under.
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
 */
public final class RemoteChangeTopics {

    private static final Map<String, AppEvent> EVENT_BY_TOPIC = new LinkedHashMap<>();
    private static final Map<AppEvent, String> TOPIC_BY_EVENT = new LinkedHashMap<>();
    private static final Map<Class<? extends AppEvent>, String> TOPIC_BY_SOURCE_TYPE = new LinkedHashMap<>();

    static {
        declare("items", new ItemsChanged());
        declare("stocks", new StocksChanged());
        declare("areas", new AreasChanged());
        declare("units", new UnitsChanged());
        declare("treasuries", new TreasuriesChanged());
        declare("expenses", new ExpensesChanged());
        declare("employees", new EmployeesChanged());
        declare("company", new CompanyChanged());
        declare("users", new UsersChanged());
        declare("groups.main", new GroupsChanged(GroupLevel.MAIN));
        declare("groups.sub", new GroupsChanged(GroupLevel.SUB));
        declare("name.customer", new NameChanged(PartyKind.CUSTOMER));
        declare("name.supplier", new NameChanged(PartyKind.SUPPLIER));
        declare("account.customer", new AccountChanged(PartyKind.CUSTOMER));
        declare("account.supplier", new AccountChanged(PartyKind.SUPPLIER));
        declare("invoice.sales", new InvoiceSaved(InvoiceSide.SALES));
        declare("invoice.purchase", new InvoiceSaved(InvoiceSide.PURCHASE));
        declare("stock.balances", new StockBalancesChanged());
        declare("treasury.balances", new TreasuryBalancesChanged());

        // These local events carry details which are useful in-process but cannot be
        // reconstructed safely on another machine. Announce the broad topic and inject
        // the payload-free event declared above on the receiving side.
        announceAs(ItemSaved.class, "items");
        announceAs(StockCountPosted.class, "stock.balances");
        announceAs(TreasuryMovementRecorded.class, "treasury.balances");
    }

    private RemoteChangeTopics() {
    }

    private static void declare(String topic, AppEvent event) {
        EVENT_BY_TOPIC.put(topic, event);
        TOPIC_BY_EVENT.put(event, topic);
        TOPIC_BY_SOURCE_TYPE.put(event.getClass(), topic);
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

    /** Every local event type the relay must listen for, including normalized aliases. */
    public static Set<Class<? extends AppEvent>> announcementTypes() {
        return Set.copyOf(new LinkedHashSet<>(TOPIC_BY_SOURCE_TYPE.keySet()));
    }
}
