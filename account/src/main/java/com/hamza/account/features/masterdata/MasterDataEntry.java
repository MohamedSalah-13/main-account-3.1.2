package com.hamza.account.features.masterdata;

/** A detached list row; never carries JavaFX state or an implicit user session. */
public record MasterDataEntry(int id, String name, int parentId, double factor, long contentCount) {
    public MasterDataEntry(int id, String name, int parentId, double factor) {
        this(id, name, parentId, factor, 0);
    }

    public boolean hasNoContents(MasterDataKind kind) {
        return (kind == MasterDataKind.MAIN || kind == MasterDataKind.SUB) && contentCount == 0;
    }
}
