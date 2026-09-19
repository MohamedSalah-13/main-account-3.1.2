package com.hamza.account.features.dbsetup;

import java.nio.file.Path;

/**
 * What a run of the provisioner did. Deliberately holds no password, so it can be written to a
 * file the installer reads and a log a technician is sent.
 */
public record LocalProvisioningResult(Outcome outcome, int port, String database, String account,
                                      Path configDirectory) {

    public enum Outcome {
        /** A new server was initialized and the machine's {@code config.xml} written. */
        PROVISIONED,
        /** A data directory was already there: nothing was initialized, created or written. */
        ALREADY_PROVISIONED
    }

    static LocalProvisioningResult alreadyProvisioned(Path configDirectory) {
        return new LocalProvisioningResult(Outcome.ALREADY_PROVISIONED, 0, "", "", configDirectory);
    }

    /** {@code key=value} lines, the one format Inno's Pascal reads without a parser. */
    public String toProperties() {
        return "outcome=" + outcome + "\n"
                + "port=" + port + "\n"
                + "database=" + database + "\n"
                + "account=" + account + "\n"
                + "configDirectory=" + configDirectory + "\n";
    }
}
