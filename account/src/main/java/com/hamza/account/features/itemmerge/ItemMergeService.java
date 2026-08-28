package com.hamza.account.features.itemmerge;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Users;
import com.hamza.account.period.PeriodLock;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Making two items one.
 *
 * <h2>Why this exists</h2>
 * Before {@code item_barcodes} (V3) an item had exactly one barcode, so a shop selling
 * five flavours of the same thing entered five items and sold, bought and returned
 * against all five for years. They are one item now, with five barcodes - and the five
 * old items have to be folded into it rather than deleted, which is what merging is:
 * every line they ever appeared on is repointed at the survivor, and only then is the
 * row removed.
 *
 * <h2>What it does not change</h2>
 * Any figure. A document line carries its own price, buy price, profit and unit factor,
 * so moving it alters nothing but which item it is filed under, and the stock balance is
 * a sum over those same lines ({@code quantity_items_table}) that corrects itself
 * without a single balance being written. The one value the merge does write is the
 * opening balance, which is added to the target's - the source's row is about to go, and
 * with it the only number that was never derived from a line.
 *
 * <h2>The two refusals</h2>
 * A different base unit, and expiry tracking the target does not do. Both are cases
 * where the moved lines would still be arithmetically valid and would still mean the
 * wrong thing - a line in cartons filed under an item sold by the piece, an expiry date
 * on an item with no batches to attach it to. Neither is worth a silent conversion.
 *
 * @see ItemReferenceRegistry for where the history lives - the list this is only as
 * correct as, and the reason the source is deleted through {@link DeletionService}
 * rather than by a {@code DELETE} of its own.
 */
public record ItemMergeService(DaoFactory daoFactory) {

    private ItemMergeDao dao() {
        return daoFactory.itemMergeDao();
    }

    /**
     * The items that look like duplicates of each other, grouped by {@code groupBy}.
     * <p>
     * Nothing here decides that two items <em>are</em> the same thing - that is the
     * user's judgement, and the screen exists to put the likely pairs in front of them.
     */
    public List<ItemMergeCandidate> candidates(MergeGroupBy groupBy, int limit) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ITEMS_MERGE);
        return dao().candidates(groupBy, limit);
    }

    /**
     * What merging {@code sourceId} into {@code targetId} would move. Reads only.
     * <p>
     * The rules are applied here too, so a screen finds out that the units differ while
     * it is still showing a preview rather than when the user presses the button.
     */
    public ItemMergePreview preview(int sourceId, int targetId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ITEMS_MERGE);
        MergeItem source = require(sourceId);
        MergeItem target = require(targetId);
        checkRules(source, target);
        return previewOf(source, target);
    }

    /** One merge. */
    public ItemMergeResult merge(int sourceId, int targetId) throws DaoException {
        return mergeAll(List.of(sourceId), targetId).getFirst();
    }

    /**
     * Several sources into one target, all of it or none of it.
     * <p>
     * A batch is one transaction on purpose: the five flavours are one decision, and
     * stopping half way through leaves a shop with two of them merged, three not, and
     * no way to tell which without reading the log.
     */
    public List<ItemMergeResult> mergeAll(List<Integer> sourceIds, int targetId) throws DaoException {
        // Both permissions, and both before anything is written. A merge deletes an item,
        // so holding only 'items.merge' must not become a way to delete items without
        // 'items.delete' - and finding that out from DeletionService at the very end
        // would throw away every move it had already made.
        AuthorizationGuard.require(AppPermissions.ITEMS_MERGE);
        AuthorizationGuard.require(AppPermissions.ITEMS_DELETE);

        if (sourceIds == null || sourceIds.isEmpty()) {
            throw new UserValidationException(message("item.merge.error.no.source"));
        }

        Users user = CurrentUser.getOrNull();
        int userId = user == null ? 1 : user.getId();
        String userName = user == null ? null : user.getUsername();

        return TransactionTemplate.execute(() -> {
            List<ItemMergeResult> results = new ArrayList<>();
            for (Integer sourceId : sourceIds) {
                results.add(mergeOne(sourceId == null ? 0 : sourceId, targetId, userId, userName));
            }
            return results;
        });
    }

    /**
     * The order below is the whole of it, and every step is where it is for a reason:
     * the units move before the barcodes are rescued (so what is rescued is only what
     * the cascade is about to destroy), the barcodes are rescued before the row is
     * deleted (they hang off it), and the counts are taken before anything moves.
     */
    private ItemMergeResult mergeOne(int sourceId, int targetId, int userId, String userName) throws DaoException {
        MergeItem source = require(sourceId);
        // Re-read per source: a batch adds each one's opening balance to the target, and
        // the second merge has to see what the first one left.
        MergeItem target = require(targetId);
        checkRules(source, target);

        ItemMergePreview preview = previewOf(source, target);

        for (ItemReference reference : ItemReferenceRegistry.MOVABLE) {
            dao().move(reference, targetId, sourceId);
        }
        dao().mergeStockCountLines(targetId, sourceId);
        dao().fillMissingItemsStock(targetId, sourceId);
        dao().addItemsStockBalances(targetId, sourceId);
        dao().moveUnits(targetId, sourceId, target.unitId());
        dao().keepBarcodes(targetId, sourceId);
        dao().mergePackages(targetId, sourceId);
        dao().addFirstBalance(source.firstBalance(), targetId);

        int mergeId = dao().log(preview, userId, userName);

        // Through the delete rule rather than a DELETE of its own: the rule scans every
        // non-cascading reference, so a table this feature forgot refuses the delete and
        // rolls the whole merge back, instead of the merge silently orphaning it.
        DeletionService.shared()
                .delete(DeleteRegistry.ITEMS, sourceId, id -> daoFactory.getItemsDao().deleteById(id))
                .rowsOrThrow();

        return new ItemMergeResult(mergeId, preview);
    }

    private ItemMergePreview previewOf(MergeItem source, MergeItem target) throws DaoException {
        Map<String, Integer> rows = dao().countRows(source.id());
        int locked = dao().countLockedLines(source.id(), PeriodLock.lockedUntil());
        return new ItemMergePreview(source, target, rows, locked);
    }

    private MergeItem require(int id) throws DaoException {
        MergeItem item = id <= 0 ? null : dao().findItem(id);
        if (item == null) {
            throw new UserValidationException(message("item.merge.error.missing", id));
        }
        return item;
    }

    /**
     * The rules, as a pure function of the two items, so they can be held to without a
     * database - see {@code ItemMergeRulesTest}.
     */
    public static void checkRules(MergeItem source, MergeItem target) throws BusinessRuleException {
        if (source.id() == target.id()) {
            throw new BusinessRuleException(message("item.merge.error.same"));
        }
        if (source.unitId() != target.unitId()) {
            throw new BusinessRuleException(message("item.merge.error.unit", source.name(), target.name()));
        }
        if (source.hasValidity() && !target.hasValidity()) {
            throw new BusinessRuleException(message("item.merge.error.validity", source.name(), target.name()));
        }
    }

    private static String message(String key, Object... args) {
        return args.length == 0
                ? LanguageManager.getInstance().getString(key)
                : LanguageManager.getInstance().getString(key, args);
    }
}
