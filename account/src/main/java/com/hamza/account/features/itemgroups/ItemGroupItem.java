package com.hamza.account.features.itemgroups;

/** The small, read-only item projection required by the group-management tree. */
public record ItemGroupItem(int id, String name, String barcode,
                            int subGroupId, boolean active) {
}
