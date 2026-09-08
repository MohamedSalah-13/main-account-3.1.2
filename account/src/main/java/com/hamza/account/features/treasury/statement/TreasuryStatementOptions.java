package com.hamza.account.features.treasury.statement;

import java.util.List;

public record TreasuryStatementOptions(List<TreasuryOption> treasuries, List<TreasuryUserOption> users) {
    public TreasuryStatementOptions {
        treasuries = List.copyOf(treasuries);
        users = List.copyOf(users);
    }
}
