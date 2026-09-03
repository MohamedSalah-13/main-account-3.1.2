package com.hamza.account.features.shift;

/** A tracked till the signed-in cashier is allowed to open. */
public record CashierTreasuryChoice(int treasuryId, String treasuryName, boolean defaultTreasury) {
}
