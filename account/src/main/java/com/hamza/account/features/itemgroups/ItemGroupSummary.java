package com.hamza.account.features.itemgroups;

/** One subgroup and its parent, with the number of items matching the active search. */
public record ItemGroupSummary(int mainGroupId, String mainGroupName,
                               int subGroupId, String subGroupName,
                               int itemCount) {
}
