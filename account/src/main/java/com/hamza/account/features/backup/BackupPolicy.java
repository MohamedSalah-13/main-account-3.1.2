package com.hamza.account.features.backup;

import com.hamza.account.config.MachineId;
import com.hamza.account.config.SharedSettingKeys;
import com.hamza.account.config.SharedSettings;
import lombok.extern.log4j.Log4j2;

import java.util.Optional;

/**
 * Which computer takes the backups.
 *
 * <p>A backup is a {@code mysqldump} of the whole database. On one machine that is a
 * chore; on four machines pointed at one server it is four of them, at the same moment,
 * over the network, each writing a copy of the same rows into its own folder. Every switch
 * in this program that starts a dump was written when there was only ever one computer:
 *
 * <ul>
 *   <li><b>"Take a backup after saving an invoice"</b> runs a full dump after <em>every
 *       sale</em>. Three cashiers is three concurrent dumps, several times a minute, and
 *       {@code InvoicePostSaveService} does not wait for them - so they pile up.</li>
 *   <li><b>The scheduled backup</b> runs on whatever machines happen to be open, and each
 *       prunes its own folder to thirty files, so "we keep thirty backups" quietly becomes
 *       "we keep thirty, four times, of which three sets are on computers nobody looks
 *       at".</li>
 * </ul>
 *
 * <p>So both are answered here, one machine each:
 *
 * <ul>
 *   <li><b>After an invoice: only where the database is.</b> A dump taken across the
 *       network is the slow case of the one that is already too slow, and the copy lands
 *       on a till rather than on the server.</li>
 *   <li><b>On a schedule: only the owner.</b> The owner is recorded in the shared settings
 *       - one machine for the shop, chosen once. The first machine to start after the
 *       upgrade claims it, which on the shop that has been running until now is the only
 *       machine there is; the connected-machines screen is where it gets moved when that
 *       guess is wrong.</li>
 * </ul>
 */
@Log4j2
public final class BackupPolicy {

    private static volatile Boolean databaseIsLocal;

    private BackupPolicy() {
    }

    /**
     * @return the machine recorded as the shop's backup owner, empty when the shop has
     * never had one or the shared settings are not readable
     */
    public static Optional<String> ownerMachine() {
        return SharedSettings.read(SharedSettingKeys.BACKUP_OWNER_MACHINE)
                .filter(value -> !value.isBlank());
    }

    /** Whether this machine is the one that runs the scheduled backup. */
    public static boolean isBackupOwner() {
        return isOwner(ownerMachine(), MachineId.current());
    }

    /**
     * The rule on its own, so it can be read and tested without a registry or a machine.
     *
     * @param owner the machine the shop has recorded, empty when it has recorded none
     * @param here  this machine's id, empty when the registry could not be read
     */
    static boolean isOwner(Optional<String> owner, Optional<String> here) {
        if (owner.isEmpty()) {
            // Nothing recorded: the shop has not been asked yet. Answering "no" would
            // leave a single-machine shop with no backups at all, which is worse than
            // any duplication - and duplication is what a recorded owner prevents.
            // claimIfUnowned settles it at startup, so this is only reached when the
            // shared settings could not be read at all.
            return true;
        }
        // An unidentifiable machine is not the owner. It cannot be: the owner is a
        // MachineGuid, and a machine that cannot read its own is not equal to anything.
        return here.isPresent() && owner.get().equals(here.get());
    }

    /**
     * Records this machine as the owner when the shop has none. Called from the bootstrap,
     * after the shared settings are installed.
     */
    public static void claimIfUnowned() {
        if (ownerMachine().isPresent()) {
            return;
        }
        MachineId.current().ifPresent(machine -> {
            if (SharedSettings.write(SharedSettingKeys.BACKUP_OWNER_MACHINE, machine)) {
                log.info("This machine is now the shop's backup owner ({})", machine);
            }
        });
    }

    /** Moves ownership, from the screen that lists the shop's machines. */
    public static boolean assignOwner(String machineId) {
        return SharedSettings.write(SharedSettingKeys.BACKUP_OWNER_MACHINE, machineId);
    }

    /**
     * Whether a backup after each saved invoice may run here.
     *
     * <p>The setting stays where it is and keeps meaning what it says; this is the second
     * half of the question, and it is about the machine rather than about the wish.
     */
    public static boolean mayBackupAfterEachInvoice() {
        return databaseIsOnThisMachine();
    }

    /**
     * Cached for the life of the process: the database does not move while the program is
     * running, and this is asked once per saved invoice.
     */
    public static boolean databaseIsOnThisMachine() {
        Boolean known = databaseIsLocal;
        if (known != null) {
            return known;
        }
        boolean local;
        try {
            local = new ConnectedMachines().databaseIsOnThisMachine();
        } catch (Exception e) {
            // Unknown is treated as remote: the failure mode of "no automatic backup on
            // this till" is a missing copy somebody can take by hand, and the failure mode
            // of the other answer is four dumps a minute.
            log.warn("Could not determine whether the database is on this machine; assuming it is not", e);
            local = false;
        }
        databaseIsLocal = local;
        return local;
    }
}
