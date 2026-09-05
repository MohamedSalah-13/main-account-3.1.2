package com.hamza.account.features.masterdata;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;

public enum MasterDataKind {
    MAIN("mainGroup", "main_group", "id", "name_g", AppPermissions.MAIN_GROUP_SHOW,
            AppPermissions.MAIN_GROUP_CREATE, AppPermissions.MAIN_GROUP_UPDATE, AppPermissions.MAIN_GROUP_DELETE),
    SUB("subGroup", "sub_group", "id", "name", AppPermissions.SUB_GROUP_SHOW,
            AppPermissions.SUB_GROUP_CREATE, AppPermissions.SUB_GROUP_UPDATE, AppPermissions.SUB_GROUP_DELETE),
    AREA("party.area", "table_area", "id", "area_name", AppPermissions.ITEMS_SHOW,
            AppPermissions.AREA_CREATE, AppPermissions.AREA_UPDATE, AppPermissions.AREA_DELETE),
    UNIT("tab.units", "units", "unit_id", "unit_name", AppPermissions.UNITS_SHOW,
            AppPermissions.UNITS_CREATE, AppPermissions.UNITS_UPDATE, AppPermissions.UNITS_DELETE);

    public final String titleKey;
    final String table, idColumn, nameColumn;
    public final PermissionKey show, create, update, delete;

    MasterDataKind(String titleKey, String table, String idColumn, String nameColumn,
                   PermissionKey show, PermissionKey create, PermissionKey update, PermissionKey delete) {
        this.titleKey = titleKey;
        this.table = table;
        this.idColumn = idColumn;
        this.nameColumn = nameColumn;
        this.show = show;
        this.create = create;
        this.update = update;
        this.delete = delete;
    }
}
