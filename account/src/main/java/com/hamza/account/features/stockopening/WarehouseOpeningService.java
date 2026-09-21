package com.hamza.account.features.stockopening;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.ItemsChanged;
import com.hamza.account.features.events.StockBalancesChanged;
import com.hamza.account.features.items.BulkOpeningBalance;
import com.hamza.account.features.items.WarehouseOpeningBalance;
import com.hamza.account.features.items.WarehouseStockDao;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A warehouse's opening balances: listed, and entered for the items nothing has moved there yet.
 * <p>
 * <b>Why a screen per warehouse, and not a column per warehouse on the item screen.</b> The case it
 * exists for is a second warehouse joining a shop that already trades: what is on its shelves is
 * entered once, for many items, one warehouse at a time - the item screen's one field per item is
 * the wrong gesture for that, and it keeps answering for the default warehouse as it always has.
 * <p>
 * The rule is {@link WarehouseOpeningBalance}'s, asked again inside the saving transaction - a line
 * posted on another till while the screen was open closes the item as surely as one posted before.
 * The refusals each refuse the whole batch, before anything is written:
 * <ul>
 *   <li>a figure below zero, or beyond what the column holds;</li>
 *   <li>the warehouse has been switched off (V77) - it takes no new movement, and an opening is one;</li>
 *   <li>an item gone from the warehouse since the screen read it;</li>
 *   <li>a figure changed since the screen read it - written over, somebody's entry would be lost
 *       without a word;</li>
 *   <li>an item that has moved there would change - {@code BulkOpeningBalance}, the bulk editor's
 *       sentence.</li>
 * </ul>
 * The rows are locked {@code FOR UPDATE} in item-id order through {@link WarehouseStockDao}, the one
 * place a warehouse's rows are locked, so an opening entered here queues behind a sale taking stock
 * out of the same shelf rather than racing it.
 */
public class WarehouseOpeningService {

    /** {@code DECIMAL(14,3)}. */
    static final double MAX_OPENING = 99_999_999_999.999;

    private final DaoFactory daoFactory;

    public WarehouseOpeningService(DaoFactory daoFactory) {
        this.daoFactory = daoFactory;
    }

    /** One page of the warehouse's items with their openings, and how many the filter matches. */
    public WarehouseOpeningPage page(WarehouseOpeningFilter filter) throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_SHOW);
        WarehouseOpeningDao dao = daoFactory.warehouseOpeningDao();
        return WarehouseOpeningPage.of(dao.page(filter), dao.count(filter), filter);
    }

    /**
     * Writes the changed openings of one warehouse, all or none.
     * <p>
     * {@code items.update} is the permission, as it is for the item screen's opening field: an
     * opening is a fact about the item, entered for one of its shelves.
     *
     * @return how many openings were written
     */
    public int save(int stockId, List<WarehouseOpeningDraft.Change> changes) throws DaoException {
        AuthorizationGuard.require(AppPermissions.ITEMS_UPDATE);
        if (changes.isEmpty()) {
            return 0;
        }
        for (WarehouseOpeningDraft.Change change : changes) {
            if (change.typed() < 0) {
                throw new UserValidationException(text("stock.opening.error.negative", change.name()));
            }
            if (change.typed() > MAX_OPENING) {
                throw new UserValidationException(text("stock.opening.error.too.large", change.name()));
            }
        }
        return TransactionTemplate.execute(() -> {
            WarehouseStockDao warehouse = daoFactory.warehouseStockDao();
            Optional<String> inactive = warehouse.nameIfInactive(stockId);
            if (inactive.isPresent()) {
                throw new BusinessRuleException(text("stock.opening.error.inactive", inactive.get()));
            }
            List<Integer> ids = changes.stream().map(WarehouseOpeningDraft.Change::itemId).distinct().sorted().toList();
            Map<Integer, String> locked = warehouse.lockItems(stockId, ids);

            WarehouseOpeningBalance rule = new WarehouseOpeningBalance(warehouse);
            for (WarehouseOpeningDraft.Change change : changes) {
                if (!locked.containsKey(change.itemId())) {
                    throw new BusinessRuleException(text("stock.opening.error.gone", change.name()));
                }
                double stored = rule.stored(change.itemId(), stockId);
                if (Math.abs(stored - change.read()) >= WarehouseOpeningDraft.TOLERANCE) {
                    throw new BusinessRuleException(text("stock.opening.error.changed",
                            change.name(), change.read(), stored));
                }
            }
            Set<Integer> writable = BulkOpeningBalance.writableIds(
                    changes.stream()
                            .map(change -> new BulkOpeningBalance.Item(change.itemId(), change.name(), change.typed()))
                            .toList(),
                    (itemId, incoming) -> rule.verdict(itemId, stockId, incoming),
                    text("opening.correction.items"));

            int written = 0;
            for (WarehouseOpeningDraft.Change change : changes) {
                if (writable.contains(change.itemId())) {
                    written += daoFactory.getItemsStockDao().updateOpeningBalance(change.itemId(), stockId, change.typed());
                }
            }
            // A balance moved on every screen that shows one: the lists of items and the inventory.
            ChangeAnnouncer.jdbc().announce(new StockBalancesChanged());
            ChangeAnnouncer.jdbc().announce(new ItemsChanged());
            return written;
        });
    }

    private static String text(String key, Object... arguments) {
        return LanguageManager.getInstance().getString(key, arguments);
    }
}
