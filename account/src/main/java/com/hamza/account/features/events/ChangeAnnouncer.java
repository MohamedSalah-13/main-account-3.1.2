package com.hamza.account.features.events;

import com.hamza.account.config.MachineId;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.observer.AppEvent;

/**
 * Writes a cross-device invalidation while the caller's database transaction is open.
 *
 * <p>The repository joins {@code ConnectionManager}'s thread-bound connection. A failed
 * announcement therefore rolls the business write back too: another till can no longer
 * be left silently reading old data after this one reports a successful save.</p>
 */
@FunctionalInterface
public interface ChangeAnnouncer {

    void announce(AppEvent event) throws DaoException;

    static ChangeAnnouncer jdbc() {
        JdbcDataChangeRepository repository = new JdbcDataChangeRepository();
        return event -> {
            String topic = RemoteChangeTopics.topicOf(event);
            if (topic == null) {
                throw new IllegalArgumentException("Event has no remote change topic: " + event);
            }
            String machineId = MachineId.current().orElse(null);
            if (machineId != null) {
                repository.announce(topic, machineId);
            }
        };
    }

    static ChangeAnnouncer disabled() {
        return event -> { };
    }
}
