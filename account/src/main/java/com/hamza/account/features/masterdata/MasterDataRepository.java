package com.hamza.account.features.masterdata;

import com.hamza.controlsfx.database.DaoException;
import java.util.List;

public interface MasterDataRepository {
    List<MasterDataEntry> search(MasterDataKind kind, String search, int parentId, int page) throws DaoException;
    boolean nameExists(MasterDataKind kind, String name, int parentId, int exceptId) throws DaoException;
    long countEmptyGroups(MasterDataKind kind) throws DaoException;
}
