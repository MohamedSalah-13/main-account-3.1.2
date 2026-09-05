package com.hamza.account.features.masterdata;

import com.hamza.account.authorization.PermissionKey;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;

/** The unified navigation entry needs access to any one section, never all sections. */
public final class MasterDataAccess {
    private MasterDataAccess() { }

    public static Optional<MasterDataKind> firstVisible(Predicate<PermissionKey> granted) {
        return Arrays.stream(MasterDataKind.values()).filter(kind -> granted.test(kind.show)).findFirst();
    }
}
