package com.hamza.account.features.party.statement;

/** One entry of the treasury filter. {@code id == 0} is the "all tills" entry. */
public record PartyStatementTreasuryOption(int id, String name, boolean active) {
    public PartyStatementTreasuryOption {
        name = name == null ? "" : name;
    }
}
