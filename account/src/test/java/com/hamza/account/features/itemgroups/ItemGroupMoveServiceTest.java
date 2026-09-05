package com.hamza.account.features.itemgroups;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.features.rbac.UserSessionContext;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemGroupMoveServiceTest {

    private final FakeRepository repository = new FakeRepository();
    private final ItemGroupMoveService service =
            new ItemGroupMoveService(repository, ItemGroupTransactionExecutor.direct());

    @BeforeEach
    void signIn() {
        UserSessionContext session = new UserSessionContext();
        session.signIn(9, "operator", List.of(AppPermissions.ITEMS_SHOW, AppPermissions.ITEMS_GROUP_MOVE));
        ServiceRegistry.register(UserSessionContext.class, session);
        repository.groups.put(7, 2);
        repository.groups.put(8, 4);
        repository.subGroups.addAll(Set.of(2, 3, 4));
    }

    @Test
    void movesOnlyTheRequestedGroupAssignments() throws Exception {
        var command = new ItemGroupMoveCommand(List.of(
                new ItemGroupChange(7, 2, 3),
                new ItemGroupChange(8, 4, 3)), 9);

        ItemGroupMoveResult result = service.move(command);

        assertEquals(2, result.movedCount());
        assertEquals(Map.of(7, 3, 8, 3), repository.groups);
        assertEquals(9, repository.lastUserId);
    }

    @Test
    void refusesAStaleSourceInsteadOfOverwritingIt() {
        var command = new ItemGroupMoveCommand(List.of(new ItemGroupChange(7, 4, 3)), 9);

        BusinessRuleException failure = assertThrows(BusinessRuleException.class,
                () -> service.move(command));

        assertEquals("item.group.manager.error.concurrent", failure.getMessage());
        assertEquals(2, repository.groups.get(7));
    }

    @Test
    void refusesAMissingTarget() {
        var command = new ItemGroupMoveCommand(List.of(new ItemGroupChange(7, 2, 99)), 9);

        BusinessRuleException failure = assertThrows(BusinessRuleException.class,
                () -> service.move(command));

        assertEquals("item.group.manager.error.target.missing", failure.getMessage());
    }

    private static final class FakeRepository implements ItemGroupRepository {
        private final Map<Integer, Integer> groups = new LinkedHashMap<>();
        private final Set<Integer> subGroups = new LinkedHashSet<>();
        private int lastUserId;

        @Override public List<ItemGroupSummary> findGroups(String search) { return List.of(); }
        @Override public List<ItemGroupItem> findItems(int subGroupId, String search, int limit, int offset) {
            return List.of();
        }
        @Override public List<ItemGroupItem> findItemsByIds(Set<Integer> itemIds) { return List.of(); }
        @Override public Map<Integer, Integer> lockCurrentGroups(Set<Integer> itemIds) {
            Map<Integer, Integer> found = new LinkedHashMap<>();
            for (int id : itemIds) if (groups.containsKey(id)) found.put(id, groups.get(id));
            return found;
        }
        @Override public Set<Integer> existingSubGroups(Set<Integer> subGroupIds) {
            Set<Integer> found = new LinkedHashSet<>(subGroups);
            found.retainAll(subGroupIds);
            return found;
        }
        @Override public int moveItems(List<ItemGroupChange> changes, int userId) throws DaoException {
            lastUserId = userId;
            for (ItemGroupChange change : changes) {
                if (!Integer.valueOf(change.sourceSubGroupId()).equals(groups.get(change.itemId()))) return 0;
            }
            changes.forEach(change -> groups.put(change.itemId(), change.targetSubGroupId()));
            return changes.size();
        }
    }
}
