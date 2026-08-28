package com.hamza.account.architecture;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the point of {@code StockService}, the per-warehouse overloads added to
 * {@code CardItemDao}/{@code InventoryDao}/{@code JdbcInvoiceStockRepository}, and
 * {@code BuyController2.invoiceStockId}: an operation that reads or writes a specific
 * warehouse's balance takes a {@code stockId}, and {@code DefaultStock.ID} answers only
 * "which one, if nothing else says" - a UI's initial combo selection, a compatibility
 * overload kept for an old caller, or the one opening-balance field the item screen has
 * never had a warehouse picker for.
 * <p>
 * That distinction cannot be checked by a regex - "is this occurrence a default or a
 * bug" is exactly the judgement a reviewer makes once, which is what this pins down.
 * {@link #FILES_USING_DEFAULT_STOCK} is every file allowed to reference it today,
 * reviewed at the point multi-warehouse support returned; a file added to it later is a
 * deliberate choice made in the same review that adds the reference, the same shape as
 * {@code LocalizationArchitectureTest.FEATURES_WITH_ARABIC_LITERALS}. A new file that
 * reaches for {@code DefaultStock.ID} instead of threading a real {@code stockId} through
 * fails the build here rather than shipping a warehouse silently ignored.
 */
class DefaultStockUsageArchitectureTest {

    private static final Set<String> FILES_USING_DEFAULT_STOCK = Set.of(
            "com/hamza/account/controller/invoice/BuyController2.java",
            "com/hamza/account/controller/items/CardController.java",
            "com/hamza/account/controller/items/InventoryController.java",
            "com/hamza/account/controller/items/StockCountController.java",
            "com/hamza/account/controller/items/StockTransferController.java",
            "com/hamza/account/delete/DeleteRegistry.java",
            "com/hamza/account/features/inventory/InventoryQuery.java",
            "com/hamza/account/features/invoice/InvoiceSaveCommand.java",
            "com/hamza/account/features/invoice/JdbcInvoiceStockRepository.java",
            "com/hamza/account/features/stockcount/StockCountService.java",
            "com/hamza/account/interfaces/api/DataInterface.java",
            "com/hamza/account/model/dao/CardItemDao.java",
            "com/hamza/account/model/dao/ItemsDao.java",
            "com/hamza/account/reportData/Print_Reports.java",
            "com/hamza/account/service/StockService.java");

    private static Set<String> filesReferencingDefaultStock() {
        var offenders = new TreeSet<String>();
        for (String file : SourceTree.javaFiles(SourceTree.javaPackage())) {
            if (file.endsWith("config/DefaultStock.java")) {
                continue;
            }
            String source = SourceTree.withoutComments(SourceTree.readJava(file));
            if (source.contains("DefaultStock.ID")) {
                offenders.add(file);
            }
        }
        return offenders;
    }

    @Test
    void noNewFileReachesForDefaultStockIdOutsideTheReviewedList() {
        var unexpected = new TreeSet<>(filesReferencingDefaultStock());
        unexpected.removeAll(FILES_USING_DEFAULT_STOCK);
        assertTrue(unexpected.isEmpty(),
                "DefaultStock.ID belongs in a UI default or a documented compatibility overload, "
                        + "not a new hardcoded warehouse. Either thread a real stockId through instead, "
                        + "or add the file to FILES_USING_DEFAULT_STOCK here after confirming the "
                        + "reference really is one of those two things. New references: " + unexpected);
    }

    @Test
    void theReviewedListStaysHonest() {
        var cleaned = new TreeSet<>(FILES_USING_DEFAULT_STOCK);
        cleaned.removeAll(filesReferencingDefaultStock());
        assertTrue(cleaned.isEmpty(),
                "These files no longer reference DefaultStock.ID - strike them off "
                        + "FILES_USING_DEFAULT_STOCK so the list stays accurate: " + cleaned);
    }
}
