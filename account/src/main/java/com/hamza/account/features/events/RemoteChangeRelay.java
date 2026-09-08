package com.hamza.account.features.events;

import com.hamza.account.config.MachineId;
import com.hamza.controlsfx.observer.AppEvent;
import com.hamza.controlsfx.observer.EventBus;
import com.hamza.controlsfx.observer.Subscriptions;
import javafx.application.Platform;
import lombok.extern.log4j.Log4j2;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Carries the events that already refresh a screen across to the other machines.
 *
 * <p>{@code EventBus} is in-process, which was the whole truth while there was one
 * process. With a till at each end of the counter, a price the manager changed reaches a
 * cashier's open item list when they next reopen it - and until then they are reading a
 * list that is out of date and does not look it.
 *
 * <p>So: what is published here is announced in {@code data_change}, and what other
 * machines announce is published here. The screens are untouched - they already listen for
 * these events, and an event that arrived over the network is the same event.
 *
 * <p>Three things it must not do, and each is a line in the code below:
 *
 * <ul>
 *   <li><b>Loop.</b> A relayed event, republished locally, reaches this class's own
 *       listener; announcing it again would have two machines bouncing one change between
 *       them for ever. The injection is done on the FX thread with a flag set around it,
 *       and {@code Publisher} runs listeners inline when it is already on that thread - so
 *       the listener sees the flag, every time.</li>
 *   <li><b>Announce its own arrival.</b> A machine ignores rows it wrote itself, which is
 *       what {@code changed_by} is for.</li>
 *   <li><b>Refresh everything at login.</b> The first poll records what it finds without
 *       publishing: a machine that has been off all night would otherwise open on a burst
 *       of reloads for changes it never needed to hear.</li>
 * </ul>
 */
@Log4j2
public final class RemoteChangeRelay {

    /**
     * Frequent enough that a cashier does not sell from a stale price for long, rare
     * enough to be one small query per till per interval.
     */
    private static final long EVERY_SECONDS = 20;

    private static ScheduledExecutorService poller;
    private static Subscriptions subscriptions;
    private static volatile boolean injecting;

    private final EventBus eventBus;
    private final JdbcDataChangeRepository repository;
    private final String machineId;
    private final Map<String, Long> lastSeen = new HashMap<>();

    private RemoteChangeRelay(EventBus eventBus, JdbcDataChangeRepository repository, String machineId) {
        this.eventBus = eventBus;
        this.repository = repository;
        this.machineId = machineId;
    }

    /** Starts listening and polling; a second call replaces the first. */
    public static synchronized void start(EventBus eventBus) {
        stop();
        if (eventBus == null) {
            return;
        }
        String machineId = MachineId.current().orElse(null);
        if (machineId == null) {
            // Without an identity a machine cannot tell its own announcements from
            // anyone else's, which is the one thing this class has to be able to do.
            log.warn("This machine could not be identified; changes made here will not reach the other tills");
            return;
        }

        RemoteChangeRelay relay = new RemoteChangeRelay(eventBus, new JdbcDataChangeRepository(), machineId);
        subscriptions = new Subscriptions();
        for (Class<? extends AppEvent> eventType : RemoteChangeTopics.announcementTypes()) {
            subscribe(relay, eventType);
        }
        relay.readBaseline();

        poller = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "remote-change-relay");
            thread.setDaemon(true);
            return thread;
        });
        poller.scheduleAtFixedRate(relay::poll, EVERY_SECONDS, EVERY_SECONDS, TimeUnit.SECONDS);
    }

    public static synchronized void stop() {
        if (poller != null) {
            poller.shutdownNow();
            poller = null;
        }
        if (subscriptions != null) {
            subscriptions.unsubscribe();
            subscriptions = null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends AppEvent> void subscribe(
            RemoteChangeRelay relay, Class<? extends AppEvent> eventType) {
        Class<E> type = (Class<E>) eventType;
        subscriptions.add(relay.eventBus.subscribe(type, relay::announce));
    }

    /** What every topic looked like when this machine joined, recorded and not acted on. */
    private void readBaseline() {
        try {
            repository.readAll().forEach((topic, change) -> lastSeen.put(topic, change.revision()));
        } catch (Exception e) {
            log.warn("Could not read the change log; this machine starts from nothing", e);
        }
    }

    private void announce(AppEvent event) {
        if (injecting) {
            return;
        }
        String topic = RemoteChangeTopics.topicOf(event);
        if (topic == null) {
            return;
        }
        try {
            repository.announce(topic, machineId);
        } catch (Exception e) {
            // The local screens have already refreshed; what is lost is the other tills
            // hearing about it, which is not worth a dialog in front of whoever saved.
            log.warn("Could not announce {} to the other machines", topic, e);
        }
    }

    private void poll() {
        Map<String, JdbcDataChangeRepository.Change> changes;
        try {
            changes = repository.readAll();
        } catch (Exception e) {
            log.debug("Change poll failed; trying again next interval", e);
            return;
        }

        changes.forEach((topic, change) -> {
            if (machineId.equals(change.changedBy())) {
                return;
            }
            Long seen = lastSeen.get(topic);
            if (seen != null && change.revision() <= seen) {
                return;
            }
            lastSeen.put(topic, change.revision());
            AppEvent event = RemoteChangeTopics.eventOf(topic);
            if (event != null) {
                inject(event);
            }
        });
    }

    /**
     * Publishes an event that came from another machine, with the guard the listener
     * above reads. On the FX thread because that is where the flag is reliable: the
     * publisher runs its listeners inline there, so the flag is set for exactly the
     * dispatch it belongs to and for nothing else.
     */
    private void inject(AppEvent event) {
        Platform.runLater(() -> {
            injecting = true;
            try {
                eventBus.publish(event);
            } finally {
                injecting = false;
            }
        });
    }
}
