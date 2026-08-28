package com.hamza.account.features.stockcount;

import com.hamza.account.config.DefaultStock;
import com.hamza.account.features.stockledger.StockMovementAssembler;
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

import java.time.LocalDate;
import java.util.List;

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

    public List<StockCount> recent(int limit) throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_COUNT_SHOW);
        return dao().recent(limit);
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

    /** Saves the sheet as a draft. Refuses to touch one that has been posted. */
    public int save(StockCount count) throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_COUNT_SHOW);
        requireEditable(count);
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
            throw new UserValidationException("لا يمكن ترحيل جرد بدون أصناف");
        }

        int moved = count.linesWithDifference().size();
        return TransactionTemplate.execute(() -> {
            dao().save(count);
            if (dao().post(count.getId()) == 0) {
                throw new BusinessRuleException("تم ترحيل هذا الجرد بالفعل");
            }
            count.setStatus(StockCountStatus.POSTED);
            daoFactory.stockMovementDao().insertBatch(StockMovementAssembler.forStockCount(count));
            return moved;
        });
    }

    /**
     * Removes a draft. A posted count is a record of a correction that was made, so it
     * stays: undoing one means counting again and posting that.
     */
    public int deleteDraft(StockCount count) throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_COUNT_POST);
        requireEditable(count);
        if (count.isNew()) {
            return 0;
        }
        return dao().deleteDraft(count.getId());
    }

    private void requireEditable(StockCount count) throws DaoException {
        if (count.isPosted()) {
            throw new BusinessRuleException("الجرد المرحّل لا يمكن تعديله");
        }
    }
}
