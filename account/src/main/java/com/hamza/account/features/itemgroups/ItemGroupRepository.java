package com.hamza.account.features.itemgroups;

import com.hamza.controlsfx.database.DaoException;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ItemGroupRepository {

    List<ItemGroupSummary> findGroups(String search) throws DaoException;

    List<ItemGroupItem> findItems(int subGroupId, String search, int limit, int offset) throws DaoException;

    List<ItemGroupItem> findItemsByIds(Set<Integer> itemIds) throws DaoException;

    Map<Integer, Integer> lockCurrentGroups(Set<Integer> itemIds) throws DaoException;

    Set<Integer> existingSubGroups(Set<Integer> subGroupIds) throws DaoException;

    int moveItems(List<ItemGroupChange> changes, int userId) throws DaoException;
}
