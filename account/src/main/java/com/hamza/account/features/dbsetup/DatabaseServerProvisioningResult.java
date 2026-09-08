package com.hamza.account.features.dbsetup;

/** The non-secret result shown after a database account is prepared. */
public record DatabaseServerProvisioningResult(String database, String account) {
}
