package com.hamza.account.features.party.balances;

/** One entry of the area filter. {@code id == 0} is the "all areas" entry. */
public record PartyAreaOption(int id, String name) {
    public PartyAreaOption {
        name = name == null ? "" : name;
    }
}
