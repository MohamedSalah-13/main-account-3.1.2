package com.hamza.account.architecture;

import com.hamza.account.features.events.RemoteChangeTopics;
import com.hamza.controlsfx.observer.AppEvent;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                "com/hamza/account/controller/invoice/InvoiceScreenController.java"));
        assertTrue(invoiceEditor.contains("loadedUpdatedAt = dataById.getUpdated_at()"));
        assertTrue(invoiceEditor.contains("invoiceStockId, correctionReason, loadedUpdatedAt"));
    }

    @Test
    void bulkItemEditsAreOptimisticAndAnnounced() {
        String dao = SourceTree.withoutComments(SourceTree.readJava(
                "com/hamza/account/model/dao/ItemsDao.java"));
        // Each row is saved against the version it was loaded with. Which columns it writes -
        // the picture and the opening balance only on request - is ItemsBulkUpdateTest's.
        assertTrue(dao.contains(
                "optimisticValues(bulkUpdateValues(model, writesImage), model.getUpdated_at())"));
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
    void shiftScreensReloadWhenAnotherWorkstationChangesAShift() {
        String cashier = SourceTree.withoutComments(SourceTree.readJava(
                "com/hamza/account/controller/users/UserShiftController.java"));
        String supervisor = SourceTree.withoutComments(SourceTree.readJava(
                "com/hamza/account/controller/users/AdminShiftsController.java"));
        assertTrue(cashier.contains("subscribe(ShiftsChanged.class"));
        assertTrue(supervisor.contains("subscribe(ShiftsChanged.class"));
        assertEquals("shifts", RemoteChangeTopics.topicOfType(
                com.hamza.account.features.shift.ShiftOpened.class));
        assertEquals("shifts", RemoteChangeTopics.topicOfType(
                com.hamza.account.features.shift.ShiftClosed.class));
    }

    @Test
    void permissionEditorRefreshesSignedInSessionsAfterEverySecurityChange() {
        String editor = SourceTree.withoutComments(SourceTree.readJava(
                "com/hamza/account/controller/users/UserPermissionController.java"));
        assertTrue(occurrences(editor, "publishUsersChanged()") >= 4,
                "Saving roles, changing overrides, and deleting either must invalidate active sessions");
        assertTrue(editor.contains("eventBus.publish(new UsersChanged())"));

        String bootstrap = SourceTree.withoutComments(SourceTree.readJava(
                "com/hamza/account/view/DownLoadApplication.java"));
        assertTrue(bootstrap.contains("subscribe(UsersChanged.class"));
        assertTrue(bootstrap.contains("rbacService.refreshCurrentSession()"));
    }

    @Test
    void openInvoicesWarnWithoutOverwritingEnteredLineValues() {
        String invoiceEditor = SourceTree.withoutComments(SourceTree.readJava(
                "com/hamza/account/controller/invoice/InvoiceScreenController.java"));
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

    /**
     * A topic is announced once, by one half of the system.
     *
     * <p>The relay listens for a topic only when no service announces it. Measured against a
     * real database before this rule existed, one invoice save moved {@code revision} by two:
     * the service wrote the row inside its transaction and the relay's bus listener wrote it
     * again when the screen published the same event afterwards.
     */
    @Test
    void theRelayDoesNotRepeatAnAnnouncementAServiceAlreadyWrote() {
        Set<String> serviceTopics = RemoteChangeTopics.serviceAnnouncedTopics();
        assertFalse(serviceTopics.isEmpty(), "the split is the point; an empty half means it is gone");

        for (Class<? extends AppEvent> listened : RemoteChangeTopics.announcementTypes()) {
            String topic = RemoteChangeTopics.topicOfType(listened);
            assertFalse(serviceTopics.contains(topic),
                    "the relay listens for " + listened.getSimpleName() + ", whose topic \"" + topic
                            + "\" is already announced inside the writing service's transaction;"
                            + " every such change would be announced twice");
        }
    }

    /**
     * The declaration and the code must agree, and both directions are a real defect.
     *
     * <p>A topic that some service announces but is declared {@code RELAY} is announced
     * twice - the bug this rule was written for. A topic declared {@code SERVICE} that no
     * service announces is worse and quieter: the relay deliberately does not listen for it,
     * so that change never reaches another machine at all.
     */
    @Test
    void everyTopicIsAnnouncedByExactlyTheHalfItDeclares() {
        String sources = SourceTree.javaFiles(SourceTree.javaPackage()).stream()
                .filter(file -> !file.contains("/features/events/"))
                .map(file -> SourceTree.withoutComments(SourceTree.readJava(file)))
                .collect(Collectors.joining("\n"));

        RemoteChangeTopics.all().forEach((topic, event) -> {
            String call = "announce(new " + event.getClass().getSimpleName();
            boolean announcedByAService = sources.contains(call);
            boolean declaredService =
                    RemoteChangeTopics.announcerOf(topic) == RemoteChangeTopics.Announcer.SERVICE;
            assertEquals(declaredService, announcedByAService, declaredService
                    ? "topic \"" + topic + "\" is declared SERVICE but nothing calls " + call
                            + "...); the relay does not listen for it, so the change would never"
                            + " reach another machine"
                    : "topic \"" + topic + "\" is declared RELAY, yet " + call + "...) is called"
                            + " inside a transaction; the relay would then announce the same"
                            + " change a second time when the screen publishes the event");
        });
    }

    /**
     * An event a screen listens for has to be published on this machine, and an announcement is not that.
     *
     * <p>{@code ChangeAnnouncer} writes a row for the other tills, and the relay deliberately passes over the
     * rows its own machine wrote - so an event only ever announced reaches every till but the one it happened
     * on. The offers screen went on listing an offer it had just activated, stopped or deleted, and an
     * invoice open on the same till kept the old offers, while every test was green; only driving the screen
     * found it. The tiers' names had the same gap on the items screen. So some code other than an
     * {@code announce(...)} call must build the event - which is what publishes it here.</p>
     */
    @Test
    void anEventAScreenListensForIsPublishedOnThisMachine() {
        String sources = SourceTree.javaFiles(SourceTree.javaPackage()).stream()
                .filter(file -> !file.endsWith("RemoteChangeTopics.java"))
                .map(file -> SourceTree.withoutComments(SourceTree.readJava(file)))
                .collect(Collectors.joining("\n"));
        java.util.regex.Matcher listened = java.util.regex.Pattern
                .compile("subscribe\\(\\s*([A-Z]\\w*)\\.class").matcher(sources);
        Set<String> events = new java.util.TreeSet<>();
        while (listened.find()) {
            events.add(listened.group(1));
        }
        assertFalse(events.isEmpty(), "no subscription found; the scan is broken");

        for (String event : events) {
            String built = "new " + event + "(";
            int published = occurrences(sources, built) - occurrences(sources, "announce(" + built);
            assertTrue(published > 0, event + " is listened for, but only ever announced to the other"
                    + " machines; the relay skips this machine's own rows, so the screens here never hear it."
                    + " Publish it on the EventBus after the write, as the treasury screens do");
        }
    }

    /**
     * A restore replaces the whole database and has no service behind it, so it is the one
     * place that announces the service topics itself.
     */
    @Test
    void aRestoreTellsTheOtherMachinesTheDatabaseWasReplaced() {
        String source = SourceTree.withoutComments(SourceTree.readJava(
                "com/hamza/account/controller/main/LoadDataAndList.java"));
        assertTrue(source.contains("RemoteChangeTopics.serviceAnnouncedTopics()"),
                "a restore must announce the topics no service announced for it");
        assertTrue(source.contains("ChangeAnnouncer"),
                "and must write those announcements through ChangeAnnouncer");
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
