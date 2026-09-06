package com.hamza.account.features.workstation;

import java.time.LocalDateTime;

/**
 * One computer of this shop's, as the database last heard from it.
 *
 * @param machineId       the Windows MachineGuid - the identity, and the only field that is one
 * @param machineName     what a person calls it
 * @param appVersion      the build it is running
 * @param databaseVersion the schema version it last migrated to
 * @param userName        who was signed in at its last heartbeat, or {@code null}
 * @param firstSeen       when it first registered
 * @param lastSeen        when it last reported in
 * @param connectedNow    whether the server can see a connection from it at this moment
 * @param backupOwner     whether it is the machine that runs the scheduled backup
 */
public record Workstation(String machineId,
                          String machineName,
                          String appVersion,
                          String databaseVersion,
                          String userName,
                          LocalDateTime firstSeen,
                          LocalDateTime lastSeen,
                          boolean connectedNow,
                          boolean backupOwner) {

    public Workstation withStatus(boolean connected, boolean owner) {
        return new Workstation(machineId, machineName, appVersion, databaseVersion, userName,
                firstSeen, lastSeen, connected, owner);
    }
}
