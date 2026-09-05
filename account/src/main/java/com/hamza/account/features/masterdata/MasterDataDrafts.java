package com.hamza.account.features.masterdata;

import java.util.HashMap;
import java.util.Map;

/** Each parent has its own unfinished sub-group form. Tab switches reuse the same instance. */
public final class MasterDataDrafts {
    public record Draft(int id, String name, String factor, boolean dirty) {
        public static Draft empty() { return new Draft(0, "", "1", false); }
    }
    private final Map<Integer, Draft> drafts = new HashMap<>();
    public Draft get(int parentId) { return drafts.getOrDefault(parentId, Draft.empty()); }
    public void put(int parentId, Draft draft) { drafts.put(parentId, draft); }
    public void clear(int parentId) { drafts.remove(parentId); }
    public boolean hasChanges() { return drafts.values().stream().anyMatch(Draft::dirty); }
}
