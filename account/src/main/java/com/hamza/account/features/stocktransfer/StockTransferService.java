package com.hamza.account.features.stocktransfer;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
import com.hamza.account.features.documentdelete.DocumentDeleteStockCheck;
import com.hamza.account.features.events.ChangeAnnouncer;
import com.hamza.account.features.events.StockBalancesChanged;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.period.PeriodLock;
import com.hamza.account.period.PeriodLockRegistry;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.BusinessRuleException;
import com.hamza.controlsfx.language.LanguageManager;

import java.util.List;
import java.util.Map;

/** Posts and reverses a warehouse transfer, after locking the source balance. */
public final class StockTransferService {
    /** A millionth of a unit - below this two quantities are the same quantity. */
    private static final double EPSILON = 0.000_001;

    private final StockTransferDao dao = new StockTransferDao();

    /**
     * The DAO is this package's own and is deliberately not in {@code DaoFactory}; the parameter
     * keeps the constructor the shape every other entry in {@code ServiceRegistry} has.
     */
    public StockTransferService(DaoFactory daoFactory) { }

    public long transfer(StockTransferCommand command) throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_TRANSFER_POST);
        // A transfer moves balances at its own date exactly as a stock count does, so
        // one dated into a closed month would rewrite a valuation already reported.
        PeriodLock.require(command.transferDate(), PeriodLockRegistry.STOCK_TRANSFER.label());
        return TransactionTemplate.execute(() -> {
            List<Integer> ids = command.itemIds();
            Map<Integer, String> names = dao.lockSource(command.fromStockId(), ids);
            if (names.size() != ids.size())
                throw new BusinessRuleException(message("stocks.transfer.error.item.unavailable"));
            Map<Integer, Double> balances = dao.balances(command.fromStockId(), ids);
            // Per item, never per line: an item entered twice - two cartons and three pieces -
            // used to be checked twice against the whole balance, so ten and ten both passed
            // against fifteen. StockTransferCommand sums the lines the way InvoiceStockGuard
            // sums a document's.
            for (Map.Entry<Integer, Double> demand : command.baseQuantityByItem().entrySet()) {
                if (balances.getOrDefault(demand.getKey(), 0.0) + EPSILON < demand.getValue())
                    throw new BusinessRuleException(
                            message("stocks.transfer.error.insufficient.balance", names.get(demand.getKey())));
            }
            // Every warehouse StockService creates now gets a zero row for each existing
            // item, and every item ItemsDao creates gets one for each existing warehouse -
            // but a warehouse from before that fix, or one seeded outside the application,
            // may still be missing one. Without this, quantity_items_table has nothing to
            // add the incoming quantity onto and the transfer's other half is lost.
            dao.ensureDestination(command.toStockId(), ids);
            long id = dao.insert(command);
            dao.insertLines(id, command.lines());
            ChangeAnnouncer.jdbc().announce(new StockBalancesChanged());
            return id;
        });
    }

    /**
     * What reversing this transfer would put below zero in the warehouse it went to - empty when
     * nothing would. Goods that arrived and have since been sold leave the destination short when
     * the transfer goes, and until this existed that happened silently, while deleting a purchase
     * in the same state warned.
     * <p>
     * <b>A warning for the screen to show, never a refusal</b>, the answer
     * {@link DocumentDeleteStockCheck} and {@code ExpenseBalanceCheck} both give: the balance is
     * derived from what has been entered, so refusing would block the correction that puts it
     * right. It takes no lock and opens no transaction - it is read before the delete is asked
     * for, not inside it, so nothing is held while a person reads a dialog.
     */
    public List<DocumentDeleteStockCheck.Shortfall> deleteShortfalls(int transferId) throws DaoException {
        List<StockTransferDao.IncomingLine> incoming = dao.incoming(transferId);
        if (incoming.isEmpty()) {
            return List.of();
        }
        int destination = incoming.getFirst().stockId();
        Map<Integer, Double> balances = dao.balances(destination,
                incoming.stream().map(StockTransferDao.IncomingLine::itemId).sorted().toList());
        return DocumentDeleteStockCheck.shortfalls(incoming.stream()
                .map(line -> new DocumentDeleteStockCheck.StockLine(line.itemName(), line.stockName(),
                        balances.getOrDefault(line.itemId(), 0.0), line.movedBase()))
                .toList());
    }

    /**
     * Reverses a posted transfer entirely - there is no partial undo, the same as a
     * document delete elsewhere. Refused inside a closed period for the same reason
     * posting one is: it would change a valuation already reported.
     */
    public void delete(int transferId) throws DaoException {
        PeriodLock.require(PeriodLockRegistry.STOCK_TRANSFER, transferId);
        TransactionTemplate.execute(() -> {
            int rows = DeletionService.shared()
                    .delete(DeleteRegistry.STOCK_TRANSFERS, transferId, dao::deleteById)
                    .rowsOrThrow();
            if (rows > 0) {
                ChangeAnnouncer.jdbc().announce(new StockBalancesChanged());
            }
            return rows;
        });
    }

    /**
     * Recent history, for the screen that lets a transfer be found and reversed.
     * <p>
     * Asks {@code stock.transfer.show} since V74. It asked for the right to <em>post</em> a
     * transfer, so a storekeeper who was only meant to see what had moved had to be given the
     * ability to move it.
     */
    public List<StockTransferSummary> recent(int limit) throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_TRANSFER_SHOW);
        return dao.recent(limit);
    }

    /** Line-level detail over a date range, for the printed transfer log. */
    public List<StockTransferReportRow> reportRows(java.time.LocalDate from, java.time.LocalDate to) throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_TRANSFER_SHOW);
        return dao.reportRows(from, to);
    }

    private static String message(String key, Object... args) {
        return args.length == 0
                ? LanguageManager.getInstance().getString(key)
                : LanguageManager.getInstance().getString(key, args);
    }
}
