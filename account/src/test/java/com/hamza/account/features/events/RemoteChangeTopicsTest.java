package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

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
    @DisplayName("payload events are normalized when a safe broad event exists")
    void payloadCarryingEventsAreNormalized() {
        assertEquals("items", RemoteChangeTopics.topicOf(new ItemSaved(null)));
        assertEquals("stock.balances", RemoteChangeTopics.topicOf(new StockCountPosted(1, 1)));
        assertEquals("treasury.balances", RemoteChangeTopics.topicOf(new TreasuryMovementRecorded(7)));
        assertEquals(new ItemsChanged(), RemoteChangeTopics.eventOf("items"));
        assertEquals(new StockBalancesChanged(), RemoteChangeTopics.eventOf("stock.balances"));
        assertEquals(new TreasuryBalancesChanged(), RemoteChangeTopics.eventOf("treasury.balances"));

        assertNull(RemoteChangeTopics.topicOf(new UserRenamed("admin")));
        assertNull(RemoteChangeTopics.topicOf(new SelPriceNamesChanged(Map.of())));

        assertNotNull(RemoteChangeTopics.topicOf(new ItemsChanged()),
                "the bulk event is what a remote catalogue change travels as");
    }

    /**
     * The relay listens for a topic only when nothing else announces it.
     *
     * <p>These three normalize onto topics their services announce inside the transaction
     * that made the change, so the relay stays out of it: an item save publishes
     * {@link ItemSaved} on the bus <em>after</em> {@code ItemsService} has already written
     * the row, and a relay listening here would write it a second time. What the
     * normalization is for is still tested above - {@code topicOf} answers "items" for an
     * {@link ItemSaved} - because {@code ChangeAnnouncer} is the caller that needs it.
     */
    @Test
    @DisplayName("the relay stays out of topics a service announces")
    void serviceAnnouncedSourceTypesAreNotSubscribed() {
        Set<Class<? extends AppEvent>> types = RemoteChangeTopics.announcementTypes();
        assertEquals(false, types.contains(ItemSaved.class));
        assertEquals(false, types.contains(StockCountPosted.class));
        assertEquals(false, types.contains(TreasuryMovementRecorded.class));
        assertEquals(false, types.contains(ItemsChanged.class));
        assertEquals(false, types.contains(InvoiceSaved.class));
    }

    /** And it does listen for the topics nothing else writes. */
    @Test
    @DisplayName("the relay is the only announcer of the rest")
    void relayAnnouncedTypesAreSubscribed() {
        Set<Class<? extends AppEvent>> types = RemoteChangeTopics.announcementTypes();
        assertEquals(true, types.contains(UsersChanged.class));
        assertEquals(true, types.contains(GroupsChanged.class));
        assertEquals(true, types.contains(AreasChanged.class));
        assertEquals(true, types.contains(UnitsChanged.class));
        assertEquals(true, types.contains(CompanyChanged.class));
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
