package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

import java.util.LinkedHashMap;
import java.util.Map;

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
 *   <li>{@link ItemSaved} carries the item. Another machine has an id at best and would
 *       have to invent the rest, and a listener reading a fabricated model is worse than
 *       a listener hearing nothing. It is <b>not</b> relayed - {@code ItemsChanged} is the
 *       event for "the catalogue moved, reload", and the screens that matter listen for
 *       both.</li>
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
    }

    private RemoteChangeTopics() {
    }

    private static void declare(String topic, AppEvent event) {
        EVENT_BY_TOPIC.put(topic, event);
        TOPIC_BY_EVENT.put(event, topic);
    }

    /** Every relayed event, one instance each - the same instances the relay publishes. */
    public static Map<String, AppEvent> all() {
        return Map.copyOf(EVENT_BY_TOPIC);
    }

    /** The topic to announce for an event raised here, or {@code null} when it stays local. */
    public static String topicOf(AppEvent event) {
        return TOPIC_BY_EVENT.get(event);
    }

    /** The event to publish for a topic another machine moved, or {@code null} when unknown. */
    public static AppEvent eventOf(String topic) {
        return EVENT_BY_TOPIC.get(topic);
    }
}
