package com.hamza.account.features.party.statement;

import java.util.List;

/**
 * What the filter combos offer. Read once per screen opening, not per keystroke.
 *
 * @param treasuries every till, active or not: a movement entered against a till that
 *                   has since been closed still has to be filterable, the same reason
 *                   {@code Add_AccountController.selectTreasury} adds a missing name back
 * @param users      everyone who could have entered a movement
 */
public record PartyStatementOptions(List<PartyStatementTreasuryOption> treasuries,
                                    List<PartyStatementUserOption> users) {
    public PartyStatementOptions {
        treasuries = List.copyOf(treasuries);
        users = List.copyOf(users);
    }
}
