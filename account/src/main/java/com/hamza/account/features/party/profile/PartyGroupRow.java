package com.hamza.account.features.party.profile;

import java.math.BigDecimal;

/**
 * The items of one group added together. There is no quantity: a carton of juice and a kilo of rice
 * in one group add up to nothing a person can read.
 */
public record PartyGroupRow(int groupId, String groupName, int items, BigDecimal net) {
}
