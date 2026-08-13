package com.hamza.account.controller.invoice;

import com.hamza.account.authorization.AppPermissions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuyControllerAuthorizationTest {

    @Test
    void blankBarcodeRequiresCreatePermission() {
        assertEquals(AppPermissions.ITEMS_CREATE, BuyController2.itemMutationPermission(null));
        assertEquals(AppPermissions.ITEMS_CREATE, BuyController2.itemMutationPermission("  "));
    }

    @Test
    void selectedItemRequiresUpdatePermission() {
        assertEquals(AppPermissions.ITEMS_UPDATE, BuyController2.itemMutationPermission("123456"));
    }
}
