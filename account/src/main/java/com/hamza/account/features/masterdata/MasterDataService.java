package com.hamza.account.features.masterdata;

import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.model.domain.Area;
import com.hamza.account.model.domain.MainGroups;
import com.hamza.account.model.domain.SubGroups;
import com.hamza.account.service.*;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

public record MasterDataService(MasterDataRepository repository, MainGroupService mainGroups,
                                SupGroupService subGroups, AreaService areas, UnitsService units) {
    public List<MasterDataEntry> search(MasterDataKind kind, String text, int parentId, int page) throws DaoException {
        // Sub-group users need the main names as a parent picker, even without main-group editing rights.
        if (kind == MasterDataKind.MAIN && AuthorizationGuard.isGranted(MasterDataKind.SUB.show)) {
            AuthorizationGuard.require(MasterDataKind.SUB.show);
        } else {
            AuthorizationGuard.require(kind.show);
        }
        return repository.search(kind, text, parentId, Math.max(0, page));
    }

    public int save(MasterDataKind kind, int id, String name, int parentId, String factorText) throws DaoException {
        AuthorizationGuard.require(id > 0 ? kind.update : kind.create);
        MasterDataForm form = MasterDataForm.parse(kind, id, name, parentId, factorText);
        if (repository.nameExists(kind, form.name(), parentId, id))
            throw new UserValidationException("masterdata.error.duplicate");
        try {
            return persist(kind, id, form, parentId);
        } catch (DaoException failure) {
            // The check above is a courtesy, not the guarantee: two people saving the same name
            // both pass it, and the unique index - main_group_pk, sub_group_pk, table_area_pk_2,
            // units_pk - refuses the second. Widening the check into a transaction would not
            // close that, since the row it must see does not exist yet; the index is the only
            // thing that can arbitrate, so its refusal is what gets translated. Without this the
            // loser of the race read "the operation failed" and a reference code, for something
            // they could have fixed by typing a different name.
            if (isDuplicateKey(failure)) throw new UserValidationException("masterdata.error.duplicate", failure);
            throw failure;
        }
    }

    /** True when this failure is a unique-index refusal, whatever language it was worded in. */
    private static boolean isDuplicateKey(Throwable failure) {
        for (Throwable link = failure; link != null && link != link.getCause(); link = link.getCause()) {
            if (link instanceof SQLIntegrityConstraintViolationException
                    && link.getMessage() != null && link.getMessage().contains("Duplicate entry")) {
                return true;
            }
        }
        return false;
    }

    private int persist(MasterDataKind kind, int id, MasterDataForm form, int parentId) throws DaoException {
        int result = switch (kind) {
            case MAIN -> {
                MainGroups row = new MainGroups();
                row.setId(id);
                row.setName(form.name());
                yield id > 0 ? mainGroups.update(row) : mainGroups.insert(row);
            }
            case SUB -> {
                MainGroups parent = mainGroups.getMainGroupsById(parentId);
                if (parent == null) throw new UserValidationException("masterdata.error.parent");
                SubGroups row = new SubGroups();
                row.setId(id);
                row.setName(form.name());
                row.setMainGroups(parent);
                yield id > 0 ? subGroups.update(row) : subGroups.insert(row);
            }
            case AREA -> {
                Area row = new Area();
                row.setId(id);
                row.setArea_name(form.name());
                yield id > 0 ? areas.updateArea(row) : areas.insertArea(row);
            }
            case UNIT -> id > 0 ? units.update(id, form.name(), form.factor()) : units.insert(form.name(), form.factor());
        };
        if (result <= 0) throw new UserValidationException("masterdata.error.stale");
        return result;
    }

    public int delete(MasterDataKind kind, int id) throws DaoException {
        AuthorizationGuard.require(kind.delete);
        int result = switch (kind) {
            case MAIN -> mainGroups.deleteMainGroup(id);
            case SUB -> subGroups.deleteSubGroup(id);
            case AREA -> areas.deleteArea(id);
            case UNIT -> units.delete(id);
        };
        if (result <= 0) throw new UserValidationException("masterdata.error.stale");
        return result;
    }
}
