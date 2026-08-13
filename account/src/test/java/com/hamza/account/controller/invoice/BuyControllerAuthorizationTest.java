package com.hamza.account.controller.invoice;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.model.domain.Sales;
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

    @Test
    void anExistingLineKeepsItsHistoricalCostDuringInvoiceRebuild() {
        Sales source = new Sales();
        source.setId(17);
        source.setBuy_price(125.75);
        Sales rebuilt = new Sales();
        rebuilt.setBuy_price(999);

        BuyController2.preservePersistedCost(source, rebuilt);

        assertEquals(125.75, rebuilt.getBuy_price());
    }

    @Test
    void aNewLineKeepsTheCurrentCostCalculatedByItsInvoiceStrategy() {
        Sales source = new Sales();
        source.setId(0);
        source.setBuy_price(125.75);
        Sales rebuilt = new Sales();
        rebuilt.setBuy_price(999);

        BuyController2.preservePersistedCost(source, rebuilt);

        assertEquals(999, rebuilt.getBuy_price());
    }
}
