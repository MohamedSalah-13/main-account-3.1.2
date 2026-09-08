package com.hamza.account.architecture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the controller wiring that keeps derived balances current across machines. */
class MultiDeviceRefreshArchitectureTest {

    @Test
    void stockTransfersAnnouncePostAndReverse() {
        String source = SourceTree.withoutComments(SourceTree.readJava(
                "com/hamza/account/controller/items/StockTransferController.java"));
        assertTrue(occurrences(source, "publish(new StockBalancesChanged())") >= 2,
                "Stock transfers must announce both posting and reversal");
    }

    @Test
    void inventoryListensForNonInvoiceBalanceChanges() {
        String source = SourceTree.withoutComments(SourceTree.readJava(
                "com/hamza/account/controller/items/InventoryController.java"));
        assertTrue(source.contains("subscribe(StockBalancesChanged.class"),
                "Inventory must reload after remote counts and transfers");
    }

    @Test
    void treasuryListensForInvoicesAndRemoteCashMovements() {
        String source = SourceTree.withoutComments(SourceTree.readJava(
                "com/hamza/account/controller/convert_treasury/TreasuryController.java"));
        assertTrue(source.contains("subscribe(InvoiceSaved.class"),
                "Treasury balances must reload after invoices");
        assertTrue(source.contains("subscribe(TreasuryBalancesChanged.class"),
                "Treasury balances must reload after remote cash movements");
    }

    @Test
    void editorsCarryTheLoadedVersionIntoTheirSaveCommands() {
        String itemEditor = SourceTree.withoutComments(SourceTree.readJava(
                "com/hamza/account/controller/items/AddItemController.java"));
        assertTrue(itemEditor.contains("loadedUpdatedAt = item.getUpdated_at()"));
        assertTrue(itemEditor.contains("itemsModel.setUpdated_at(loadedUpdatedAt)"));

        String invoiceEditor = SourceTree.withoutComments(SourceTree.readJava(
                "com/hamza/account/controller/invoice/BuyController2.java"));
        assertTrue(invoiceEditor.contains("loadedUpdatedAt = dataById.getUpdated_at()"));
        assertTrue(invoiceEditor.contains("invoiceStockId, correctionReason, loadedUpdatedAt"));
    }

    @Test
    void bulkItemEditsAreOptimisticAndAnnounced() {
        String dao = SourceTree.withoutComments(SourceTree.readJava(
                "com/hamza/account/model/dao/ItemsDao.java"));
        assertTrue(dao.contains("dataWithoutOpeningBalance(model), model.getUpdated_at()"));
        assertTrue(dao.contains("requireOptimisticUpdate(executeUpdateWithException(sql, values))"));

        String screen = SourceTree.withoutComments(SourceTree.readJava(
                "com/hamza/account/controller/items/UpdateSomeItems.java"));
        assertTrue(screen.contains("publish(new ItemsChanged())"),
                "A bulk catalogue edit must refresh local and remote item lists");
    }

    @Test
    void relayFollowsEverySignedInSessionIncludingKiosks() {
        String navigator = SourceTree.withoutComments(SourceTree.readJava(
                "com/hamza/account/view/ApplicationNavigator.java"));
        int start = navigator.indexOf("startSessionServices()", navigator.indexOf("void showMain"));
        int kioskRoute = navigator.indexOf("KioskRouting.destinationFor", navigator.indexOf("void showMain"));
        assertTrue(start >= 0 && start < kioskRoute,
                "The relay must start before standard and kiosk routes diverge");
        assertTrue(navigator.contains("RemoteChangeRelay.stop()"));
        assertTrue(navigator.contains("WorkstationHeartbeat.stop()"));
    }

    @Test
    void openInvoicesWarnWithoutOverwritingEnteredLineValues() {
        String invoiceEditor = SourceTree.withoutComments(SourceTree.readJava(
                "com/hamza/account/controller/invoice/BuyController2.java"));
        assertTrue(invoiceEditor.contains("subscribe(ItemsChanged.class"));
        assertTrue(invoiceEditor.contains("subscribe(ItemSaved.class"));
        assertTrue(invoiceEditor.contains("invoice.catalog.changed.confirm"));
    }

    @Test
    void durableAnnouncementsAreWrittenByTransactionalServices() {
        for (String sourceFile : new String[]{
                "com/hamza/account/service/ItemsService.java",
                "com/hamza/account/features/invoice/InvoiceSaveService.java",
                "com/hamza/account/features/stockcount/StockCountService.java",
                "com/hamza/account/features/stocktransfer/StockTransferService.java",
                "com/hamza/account/features/treasury/TreasuryCashService.java",
                "com/hamza/account/features/treasury/TreasuryTransferService.java"}) {
            String source = SourceTree.withoutComments(SourceTree.readJava(sourceFile));
            assertTrue(source.contains("announce(new "),
                    sourceFile + " must record its remote invalidation before commit");
        }

        String relay = SourceTree.withoutComments(SourceTree.readJava(
                "com/hamza/account/features/events/RemoteChangeRelay.java"));
        assertTrue(relay.contains("change.revision()"));
        assertTrue(!relay.contains("change.changedAt().after"));
    }

    private static int occurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) >= 0) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}
