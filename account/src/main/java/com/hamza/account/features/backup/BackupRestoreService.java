package com.hamza.account.features.backup;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.backup.BackupService;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.util.List;

/**
 * The one way into a restore, and the two refusals that belong in front of it.
 *
 * <p>{@link BackupService#restoreFromFile} pours a dump over the live schema with
 * {@code DROP TABLE}. Until now the only thing standing in front of it was a confirmation
 * dialog on a tab that {@code setting.show} opened - which is to say, whoever could open
 * the settings screen could replace the shop's database, and hiding the button was never
 * enforcement.
 *
 * <ol>
 *   <li><b>{@code backup.restore}</b>, a permission of its own. Taking a backup and
 *       replacing everything with one are not the same ability and must not be granted
 *       together by accident.</li>
 *   <li><b>Nobody else connected.</b> A restore drops and recreates every table. A till in
 *       the middle of a sale against those tables does not get an error it can act on - it
 *       gets a half-written invoice, or a line pointing at an item id the new dump numbers
 *       differently. The refusal names the machines so somebody can go and close them.</li>
 * </ol>
 */
@Log4j2
public final class BackupRestoreService {

    private final ConnectedMachines connectedMachines;

    public BackupRestoreService() {
        this(new ConnectedMachines());
    }

    public BackupRestoreService(ConnectedMachines connectedMachines) {
        this.connectedMachines = connectedMachines;
    }

    /**
     * @param backupService the service already holding this database's connection details
     *                      and the password that opened the file
     */
    public void restore(BackupService backupService, File encryptedBackup, String encryptionPassword)
            throws Exception {
        AuthorizationGuard.require(AppPermissions.BACKUP_RESTORE);
        refuseWhileOthersAreConnected();
        backupService.restoreFromFile(encryptedBackup, encryptionPassword);
    }

    private void refuseWhileOthersAreConnected() throws Exception {
        List<String> others = connectedMachines.otherClients();
        if (others.isEmpty()) {
            return;
        }
        log.warn("Restore refused: {} other machine(s) are connected: {}", others.size(), others);
        throw new BusinessRuleException(LanguageManager.getInstance()
                .getString("backup.error.other.machines.connected", String.join(", ", others)));
    }
}
