package com.hamza.account.features.stockcount;

import com.hamza.account.config.DefaultStock;
import com.hamza.account.features.stockledger.StockMovementAssembler;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.StockBalancesChanged;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemsModel;
import com.hamza.account.model.domain.UnitsModel;
import com.hamza.account.period.PeriodLock;
import com.hamza.account.period.PeriodLockRegistry;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.service.ItemUnits;
import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.features.rbac.CurrentUser;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The rules of a stock count, in one place.
 * <p>
 * The screen asks for a sheet, adds lines to it, and posts it; what a count may do -
 * and what it may not - is decided here rather than in the controller, so the checks
 * hold for any caller. The important ones:
 * <ul>
 *   <li>a shop has at most one open draft, and opening the screen continues it rather
 *       than starting a second one that would post the same shelves twice;</li>
 *   <li>posting needs its own permission, checked before anything is written;</li>
 *   <li>a posted count is never edited, deleted or posted again.</li>
 * </ul>
 */
public record StockCountService(DaoFactory daoFactory) {

    private StockCountDao dao() {
        return daoFactory.stockCountDao();
    }

    /**
     * The sheet to work on: the open draft if there is one, otherwise a new one.
     * <p>
     * Nothing is written for a new sheet until it is saved, so opening the screen and
     * closing it again leaves no empty counts behind.
     */
    public StockCount openDraft() throws DaoException {
        return openDraft(DefaultStock.ID);
    }

    public StockCount openDraft(int stockId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_COUNT_SHOW);
        StockCount existing = dao().findOpenDraft(stockId);
        if (existing != null) return existing;

        StockCount fresh = new StockCount();
        fresh.setStockId(stockId);
        fresh.setCountDate(LocalDate.now());
        var user = CurrentUser.getOrNull();
        fresh.setUserId(user == null ? 1 : user.getId());
        return fresh;
    }
    public StockCount findById(int id) throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_COUNT_SHOW);
        return dao().findById(id);
    }

    /**
     * One page of past sheets - drafts and posted ones, by period, warehouse and status - with the
     * totals of everything the filter matches. A posted count could not be seen again before this:
     * {@code recent} and {@code findById} existed and nothing called them.
     */
    public StockCountHistoryPage history(StockCountHistoryFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_COUNT_SHOW);
        long[] totals = dao().totals(filter);
        return StockCountHistoryPage.of(dao().page(filter), totals[0], totals[1], totals[2], filter);
    }

    /**
     * What the posted sheets of the filter's period and warehouse found, per item - which items keep
     * going missing. Drafts are never read: they have moved nothing.
     *
     * @return the rows, or an empty optional above {@link StockCountHistoryFilter#PRINT_LIMIT} items,
     *         since a report cut short without saying so looks complete
     */
    public Optional<List<StockCountVarianceRow>> variance(StockCountHistoryFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_COUNT_SHOW);
        List<StockCountVarianceRow> rows = dao().variance(filter, StockCountHistoryFilter.PRINT_LIMIT + 1);
        return rows.size() > StockCountHistoryFilter.PRINT_LIMIT ? Optional.empty() : Optional.of(rows);
    }

    /** One sheet read again by its id, header and lines, for its paper and for the history's detail. */
    public StockCountDocument document(int countId) throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_COUNT_SHOW);
        StockCountSummary header = dao().summary(countId);
        if (header == null) {
            // A draft discarded on another machine since the list was read.
            throw new BusinessRuleException(message("item.stockcount.error.not.found"));
        }
        return new StockCountDocument(header, dao().linesOf(countId));
    }

    /**
     * Builds a line for an item, taking the snapshot of what the system currently says.
     * <p>
     * The unit is resolved through {@link ItemUnits}, so scanning the code printed on a
     * carton counts cartons and the factor stored on the line is that carton's own -
     * not {@code units.value_d}, which is one number for the whole database.
     *
     * @param unitName the unit being counted in, or null for the item's base unit
     */
    public StockCountLine lineFor(ItemsModel item, String unitName) {
        UnitsModel unit = unitName == null ? ItemUnits.baseUnit(item) : ItemUnits.unitByName(item, unitName);
        if (unit == null) {
            unit = ItemUnits.baseUnit(item);
        }
        // getSumAllBalance is what the item's own query worked out, in base units, and
        // it now includes earlier posted counts - so counting twice in a row shows a
        // difference of zero the second time rather than posting the same gap again.
        return new StockCountLine(0, item.getId(), item.getNameItem(), item.getBarcode(),
                unit == null ? 1 : unit.getUnit_id(),
                unit == null ? "" : unit.getUnit_name(),
                ItemUnits.factor(unit),
                item.getSumAllBalance(),
                0);
    }

    /**
     * Saves the sheet as a draft. Refuses to touch one that has been posted.
     * <p>
     * Asks {@code stock.count.create} since V74, where it asked only for the right to
     * <em>see</em> a count - so every reader of the screen was a writer of it.
     */
    public int save(StockCount count) throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_COUNT_CREATE);
        requireEditable(count);
        requireOneLinePerItem(count);
        return dao().save(count);
    }

    /**
     * Posts the sheet, and answers how many lines actually moved something.
     * <p>
     * It saves first: posting what is on screen means the rows on screen have to be the
     * rows in the table, and a count posted from a sheet with unsaved edits would move
     * the wrong quantities. The save, the status flip and the {@code stock_movements}
     * dual-write (see {@code docs/erp-roadmap.md} §8.3-8.4) now share one explicit
     * {@link TransactionTemplate} - each is its own {@code insertMultiData} underneath,
     * which only <em>joins</em> an already-open transaction rather than starting one, so
     * without this wrapper the save and the status flip were in practice two separate
     * transactions.
     * <p>
     * Posting is what makes the differences real - {@code adjustment_agg} in
     * {@code R__views.sql} counts only posted sheets - so it is guarded by its own
     * permission, and the DAO's {@code WHERE status = 'DRAFT'} stops a second press
     * posting it twice.
     */
    public int post(StockCount count) throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_COUNT_POST);
        requireEditable(count);
        // Posting moves every balance on the sheet at the count's own date, so a count
        // dated into a closed month would rewrite a stock valuation already reported.
        PeriodLock.require(count.getCountDate(), PeriodLockRegistry.STOCK_COUNT.label());
        if (count.getLines().isEmpty()) {
            throw new UserValidationException(message("item.stockcount.error.no.lines"));
        }
        requireOneLinePerItem(count);

        int moved = count.linesWithDifference().size();
        return TransactionTemplate.execute(() -> {
            ensureEveryCountedItemHasARow(count);
            dao().save(count);
            if (dao().post(count.getId()) == 0) {
                throw new BusinessRuleException(message("item.stockcount.error.already.posted"));
            }
            count.setStatus(StockCountStatus.POSTED);
            daoFactory.stockMovementDao().insertBatch(StockMovementAssembler.forStockCount(count));
            ChangeAnnouncer.jdbc().announce(new StockBalancesChanged());
            return moved;
        });
    }

    /**
     * Removes a draft. A posted count is a record of a correction that was made, so it
     * stays: undoing one means counting again and posting that.
     * <p>
     * Asks {@code stock.count.create} since V74, where it asked for the right to post: throwing
     * away a sheet that has moved nothing needed the right to move balances with one. Whoever
     * enters a draft is whoever discards it.
     */
    public int deleteDraft(StockCount count) throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_COUNT_CREATE);
        requireEditable(count);
        if (count.isNew()) {
            return 0;
        }
        return dao().deleteDraft(count.getId());
    }

    /**
     * Gives every counted item a row in the warehouse being counted, if it has none.
     * <p>
     * {@code quantity_items_table} is driven by {@code items_stock}, so an adjustment about an
     * item with no row there is summed into a row the view never reads: the count posts, the
     * screen says how many lines moved, and nothing moves. Every item and warehouse made since
     * {@code fbadd53} has its rows and {@code V18} backfilled the rest, so this is the case of a
     * row seeded outside the application - the same reason
     * {@code StockTransferDao.ensureDestination} exists on the other side of a transfer.
     * <p>
     * <b>It deliberately takes no lock and re-reads no snapshot.</b> A line's {@code system_qty}
     * is fixed when the item is scanned, and that is correct rather than an oversight: the
     * adjustment posted is a <em>difference</em> against that moment, not the balance to end at,
     * so a sale made while the shop is counting is counted by the sale and by nothing else. Book
     * 10, shelf 9, a sale of 2, counted 9: the adjustment is -1 and the balance lands on 7, which
     * is what is on the shelf. Re-reading the snapshot at post time is what would swallow the
     * sale, and {@code StockCountPostAcceptanceTest} holds that case against a real database.
     */
    private void ensureEveryCountedItemHasARow(StockCount count) throws DaoException {
        List<Integer> itemIds = count.getLines().stream()
                .map(StockCountLine::getItemId).distinct().sorted().toList();
        if (itemIds.isEmpty()) {
            return;
        }
        daoFactory.warehouseStockDao().ensureRows(count.getStockId(), itemIds);
    }

    /**
     * A message the user reads, resolved from the bundles rather than written here.
     * <p>
     * This service threw three Arabic sentences, which is the one thing a service may not do -
     * an English bundle ships with this application and those three reached it untranslated.
     */
    private static String message(String key) {
        return LanguageManager.getInstance().getString(key);
    }

    /**
     * Refuses a sheet naming one item on two lines. Each line carries the item's whole book, so
     * posting two subtracts it twice - see {@link StockCountLines}. The screen never builds such a
     * sheet any more; a draft saved before it stopped, or another caller, is told which item.
     */
    private static void requireOneLinePerItem(StockCount count) throws UserValidationException {
        var repeated = StockCountLines.repeatedItem(count.getLines());
        if (repeated.isPresent()) {
            throw new UserValidationException(LanguageManager.getInstance()
                    .getString("item.stockcount.error.item.twice", repeated.get().getItemName()));
        }
    }

    private void requireEditable(StockCount count) throws DaoException {
        if (count.isPosted()) {
            throw new BusinessRuleException(message("item.stockcount.error.posted.readonly"));
        }
    }
}
