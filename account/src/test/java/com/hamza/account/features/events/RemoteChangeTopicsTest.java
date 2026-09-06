package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Which events cross to the other tills, and - the half that matters - which do not.
 */
class RemoteChangeTopicsTest {

    @Test
    @DisplayName("a topic and its event name each other, both ways")
    void roundTrips() {
        for (Map.Entry<String, AppEvent> entry : RemoteChangeTopics.all().entrySet()) {
            assertEquals(entry.getKey(), RemoteChangeTopics.topicOf(entry.getValue()),
                    "the event for " + entry.getKey() + " does not lead back to it");
            assertSame(entry.getValue(), RemoteChangeTopics.eventOf(entry.getKey()));
        }
    }

    /**
     * The two-valued events are two topics, not one. Relaying {@code NameChanged} under a
     * single name would reload a customers screen because a supplier changed on another
     * machine - the exact confusion the {@code PartyKind} on the event was added to end.
     */
    @Test
    @DisplayName("an event carrying a side travels under one topic per side")
    void sidesAreSeparateTopics() {
        assertEquals("name.customer", RemoteChangeTopics.topicOf(new NameChanged(PartyKind.CUSTOMER)));
        assertEquals("name.supplier", RemoteChangeTopics.topicOf(new NameChanged(PartyKind.SUPPLIER)));
        assertEquals("account.customer", RemoteChangeTopics.topicOf(new AccountChanged(PartyKind.CUSTOMER)));
        assertEquals("invoice.sales", RemoteChangeTopics.topicOf(new InvoiceSaved(InvoiceSide.SALES)));
        assertEquals("invoice.purchase", RemoteChangeTopics.topicOf(new InvoiceSaved(InvoiceSide.PURCHASE)));
        assertEquals("groups.main", RemoteChangeTopics.topicOf(new GroupsChanged(GroupLevel.MAIN)));
        assertEquals("groups.sub", RemoteChangeTopics.topicOf(new GroupsChanged(GroupLevel.SUB)));
    }

    /**
     * An event carrying a model cannot be rebuilt by a machine that did not raise it, and
     * a listener handed a fabricated model is worse than one hearing nothing at all. The
     * bulk event covers the same screens.
     */
    @Test
    @DisplayName("an event carrying data another machine does not have stays at home")
    void payloadCarryingEventsAreNotRelayed() {
        assertNull(RemoteChangeTopics.topicOf(new ItemSaved(null)));
        assertNull(RemoteChangeTopics.topicOf(new UserRenamed("admin")));
        assertNull(RemoteChangeTopics.topicOf(new SelPriceNamesChanged(Map.of())));
        assertNull(RemoteChangeTopics.topicOf(new StockCountPosted(1, 1)));

        assertNotNull(RemoteChangeTopics.topicOf(new ItemsChanged()),
                "the bulk event is what a remote catalogue change travels as");
    }

    /**
     * Language and font are the choice of the person at one desk. Relaying either would
     * change somebody else's screen while they are serving a customer.
     */
    @Test
    @DisplayName("what belongs to one desk is not announced to the shop")
    void perMachineEventsAreNotRelayed() {
        assertNull(RemoteChangeTopics.topicOf(new LanguageChanged(java.util.Locale.forLanguageTag("ar"))));
        assertNull(RemoteChangeTopics.topicOf(new FontChanged("Cairo")));
    }

    @Test
    @DisplayName("an unknown topic is ignored rather than guessed at")
    void unknownTopic() {
        assertNull(RemoteChangeTopics.eventOf("something.else"));
    }
}
