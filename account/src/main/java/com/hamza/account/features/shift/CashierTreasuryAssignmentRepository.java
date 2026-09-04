package com.hamza.account.features.shift;

import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public interface CashierTreasuryAssignmentRepository {
    List<CashierTreasuryAssignment> loadAll() throws DaoException;

    List<CashierTreasuryAssignmentEvent> loadHistory(int limit) throws DaoException;

    List<CashierTreasuryChoice> availableTreasuries(int userId, boolean enforceAssignments)
            throws DaoException;

    CashierTreasuryAssignment findById(int assignmentId, boolean forUpdate) throws DaoException;

    boolean canOpenShift(int userId, int treasuryId) throws DaoException;

    boolean isAssignable(int userId, int treasuryId) throws DaoException;

    boolean hasOpenShift(int userId, int treasuryId) throws DaoException;

    boolean hasActiveAssignments() throws DaoException;

    void lockUser(int userId) throws DaoException;

    void clearDefault(int userId, int actorUserId) throws DaoException;

    void upsert(int userId, int treasuryId, boolean defaultTreasury, int actorUserId)
            throws DaoException;

    int deactivate(int assignmentId, int actorUserId) throws DaoException;
}
