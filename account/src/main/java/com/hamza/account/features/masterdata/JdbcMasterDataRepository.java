package com.hamza.account.features.masterdata;

import com.hamza.controlsfx.database.AbstractDao;
import com.hamza.controlsfx.database.DaoException;
import java.util.List;

/** Paged read projection over the existing tables; writes remain in the existing services. */
public final class JdbcMasterDataRepository extends AbstractDao<MasterDataEntry> implements MasterDataRepository {
    @Override
    public List<MasterDataEntry> search(MasterDataKind kind, String text, int parentId, int page) throws DaoException {
        Object[] args = kind == MasterDataKind.SUB
                ? new Object[]{MasterDataQuery.pattern(text), parentId, MasterDataQuery.PAGE_SIZE + 1, page * MasterDataQuery.PAGE_SIZE}
                : new Object[]{MasterDataQuery.pattern(text), MasterDataQuery.PAGE_SIZE + 1, page * MasterDataQuery.PAGE_SIZE};
        return queryForObjects(MasterDataQuery.searchSql(kind), rs -> {
            try {
                return new MasterDataEntry(rs.getInt("entry_id"), rs.getString("entry_name"),
                        rs.getInt("parent_id"), rs.getDouble("factor"), rs.getLong("content_count"));
            } catch (java.sql.SQLException e) {
                throw new DaoException(e);
            }
        }, args);
    }

    @Override
    public long countEmptyGroups(MasterDataKind kind) throws DaoException {
        String sql = MasterDataQuery.emptyGroupsSql(kind);
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(sql); var rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        });
    }

    @Override
    public boolean nameExists(MasterDataKind kind, String name, int parentId, int exceptId) throws DaoException {
        Object[] args = new Object[]{name, exceptId};
        return withConnection(connection -> {
            try (var statement = connection.prepareStatement(MasterDataQuery.duplicateSql(kind))) {
                for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
                try (var rs = statement.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        });
    }
}
