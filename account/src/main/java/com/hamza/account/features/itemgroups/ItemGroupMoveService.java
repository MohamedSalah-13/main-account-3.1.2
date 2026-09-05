package com.hamza.account.features.itemgroups;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads the group tree and performs optimistic, group-only item moves. */
public final class ItemGroupMoveService {

    private final ItemGroupRepository repository;
    private final ItemGroupTransactionExecutor transactions;

    public ItemGroupMoveService(ItemGroupRepository repository) {
        this(repository, ItemGroupTransactionExecutor.jdbc());
    }

    public ItemGroupMoveService(ItemGroupRepository repository, ItemGroupTransactionExecutor transactions) {
        this.repository = repository;
        this.transactions = transactions;
    }

    public List<ItemGroupSummary> groups(String search) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ITEMS_SHOW);
        return repository.findGroups(normalize(search));
    }

    public List<ItemGroupItem> items(int subGroupId, String search, int limit, int offset)
            throws DaoException {
        AuthorizationGuard.require(AppPermissions.ITEMS_SHOW);
        if (subGroupId <= 0 || limit <= 0 || offset < 0) {
            throw new BusinessRuleException("item.group.manager.error.invalid.page");
        }
        return repository.findItems(subGroupId, normalize(search), limit, offset);
    }

    public List<ItemGroupItem> itemsByIds(Set<Integer> itemIds) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ITEMS_SHOW);
        return itemIds == null || itemIds.isEmpty() ? List.of() : repository.findItemsByIds(Set.copyOf(itemIds));
    }

    public ItemGroupMoveResult move(ItemGroupMoveCommand command) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ITEMS_GROUP_MOVE);
        String rejection = ItemGroupMovePolicy.rejectionKey(command);
        if (rejection != null) throw new BusinessRuleException(rejection);

        List<ItemGroupChange> effective = ItemGroupMovePolicy.effectiveChanges(command);
        if (effective.isEmpty()) return new ItemGroupMoveResult(List.of());

        return transactions.execute(() -> {
            Set<Integer> targets = new LinkedHashSet<>();
            Set<Integer> itemIds = new LinkedHashSet<>();
            for (ItemGroupChange change : effective) {
                targets.add(change.targetSubGroupId());
                itemIds.add(change.itemId());
            }
            if (!repository.existingSubGroups(targets).containsAll(targets)) {
                throw new BusinessRuleException("item.group.manager.error.target.missing");
            }

            Map<Integer, Integer> current = repository.lockCurrentGroups(itemIds);
            if (current.size() != itemIds.size()) {
                throw new BusinessRuleException("item.group.manager.error.item.missing");
            }
            for (ItemGroupChange change : effective) {
                if (!Integer.valueOf(change.sourceSubGroupId()).equals(current.get(change.itemId()))) {
                    throw new BusinessRuleException("item.group.manager.error.concurrent");
                }
            }

            int moved = repository.moveItems(effective, command.userId());
            if (moved != effective.size()) {
                throw new BusinessRuleException("item.group.manager.error.concurrent");
            }
            return new ItemGroupMoveResult(effective);
        });
    }

    private static String normalize(String search) {
        return search == null ? "" : search.trim();
    }
}
