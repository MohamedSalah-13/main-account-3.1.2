package com.hamza.account.features.party.statement;

/** One entry of the user filter. {@code id == 0} is the "all users" entry. */
public record PartyStatementUserOption(int id, String name) {
    public PartyStatementUserOption {
        name = name == null ? "" : name;
    }
}
