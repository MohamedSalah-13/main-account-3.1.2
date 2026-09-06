package com.hamza.account.features.workstation;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.config.AppVersionInfo;
import com.hamza.account.config.MachineId;
import com.hamza.account.features.backup.BackupPolicy;
import com.hamza.account.features.backup.ConnectedMachines;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.model.domain.Users;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

/**
 * The shop's computers: registering this one, listing them all, and moving the backup
 * ownership between them.
 *
 * <p>The registry is deliberately small. It exists because two facts a multi-machine shop
 * needs are invisible without it - which build each till is running, and which machine the
 * backups are landing on - and because a machine id on its own is a GUID that means nothing
 * to the person reading it.
 */
@Log4j2
public final class WorkstationService {

    private final JdbcWorkstationRepository repository;
    private final ConnectedMachines connectedMachines;

    public WorkstationService() {
        this(new JdbcWorkstationRepository(), new ConnectedMachines());
    }

    public WorkstationService(JdbcWorkstationRepository repository, ConnectedMachines connectedMachines) {
        this.repository = repository;
        this.connectedMachines = connectedMachines;
    }

    /**
     * Records that this machine is running, with what.
     *
     * <p>No permission is asked for, and that is deliberate rather than an omission: it is
     * a machine writing down its own name, on every till, for whoever is signed in. A
     * permission would mean a cashier's computer stays invisible on the one screen that
     * exists to notice a computer nobody has updated.
     *
     * @param databaseVersion read once at startup rather than per beat - it cannot change
     *                        while the process runs, and asking would open a connection of
     *                        its own every minute
     */
    public void reportIn(String databaseVersion) {
        MachineId.current().ifPresent(machineId -> {
            try {
                Users user = CurrentUser.getOrNull();
                repository.heartbeat(machineId, MachineId.displayName(),
                        new AppVersionInfo().getAppVersion(), databaseVersion,
                        user == null || user.getId() == 0 ? null : user.getId());
            } catch (DaoException e) {
                // A heartbeat that does not land costs a row on an information screen.
                // It must never be the reason a till stops working.
                log.warn("Could not record this machine's heartbeat", e);
            }
        });
    }

    /** Every machine this shop has seen, newest first, marked with what is true right now. */
    public List<Workstation> list() throws DaoException {
        AuthorizationGuard.require(AppPermissions.SETTING_BACKUP_SHOW);
        List<String> connected = connectedMachines.otherClients();
        String owner = BackupPolicy.ownerMachine().orElse(null);
        String here = MachineId.current().orElse(null);

        List<Workstation> answered = new ArrayList<>();
        for (Workstation machine : repository.list()) {
            // This machine is connected by definition - it is the one asking. The others
            // are matched by name where the registry knows one, because the process list
            // holds an address and nothing else; an unmatched machine is reported as not
            // connected rather than guessed at.
            boolean isHere = machine.machineId().equals(here);
            boolean seen = isHere || connected.stream().anyMatch(client -> client.equalsIgnoreCase(machine.machineName()));
            answered.add(machine.withStatus(seen, machine.machineId().equals(owner)));
        }
        return answered;
    }

    /** Hands the scheduled backup to another machine. */
    public void assignBackupOwner(String machineId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SETTING_BACKUP_SHOW);
        if (machineId == null || machineId.isBlank()) {
            throw new BusinessRuleException(LanguageManager.getInstance()
                    .getString("workstations.error.no.machine.selected"));
        }
        if (!BackupPolicy.assignOwner(machineId)) {
            throw new BusinessRuleException(LanguageManager.getInstance()
                    .getString("workstations.error.owner.not.saved"));
        }
        log.info("The scheduled backup now belongs to {}", machineId);
    }

    /**
     * Removes a machine's row - a till that was sold, replaced or reinstalled.
     *
     * <p>It only forgets: a machine that is still running writes itself back on its next
     * heartbeat, which is the right behaviour and not a bug. Forgetting the machine that
     * owns the backup would leave the shop with no owner at all, so that one is refused
     * until the ownership has been moved.
     */
    public void forget(String machineId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.SETTING_BACKUP_SHOW);
        if (BackupPolicy.ownerMachine().filter(owner -> owner.equals(machineId)).isPresent()) {
            throw new BusinessRuleException(LanguageManager.getInstance()
                    .getString("workstations.error.forget.owner"));
        }
        repository.forget(machineId);
    }
}
