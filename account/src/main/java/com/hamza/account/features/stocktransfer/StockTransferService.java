package com.hamza.account.features.stocktransfer;

import com.hamza.account.authorization.AppPermissions;
import com.hamza.account.authorization.AuthorizationGuard;
import com.hamza.account.delete.DeleteRegistry;
import com.hamza.account.delete.DeletionService;
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
    private final StockTransferDao dao = new StockTransferDao();
    public StockTransferService(DaoFactory daoFactory) { }

    public long transfer(StockTransferCommand command) throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_TRANSFER_POST);
        // A transfer moves balances at its own date exactly as a stock count does, so
        // one dated into a closed month would rewrite a valuation already reported.
        PeriodLock.require(command.transferDate(), PeriodLockRegistry.STOCK_TRANSFER.label());
        return TransactionTemplate.execute(() -> {
            List<Integer> ids = command.lines().stream().map(StockTransferLine::itemId).sorted().toList();
            Map<Integer, String> names = dao.lockSource(command.fromStockId(), ids);
            if (names.size() != ids.size())
                throw new BusinessRuleException(message("stocks.transfer.error.item.unavailable"));
            Map<Integer, Double> balances = dao.balances(command.fromStockId(), ids);
            for (StockTransferLine line : command.lines()) {
                if (balances.getOrDefault(line.itemId(), 0.0) + 0.000001 < line.baseQuantity())
                    throw new BusinessRuleException(
                            message("stocks.transfer.error.insufficient.balance", names.get(line.itemId())));
            }
            // Every warehouse StockService creates now gets a zero row for each existing
            // item, and every item ItemsDao creates gets one for each existing warehouse -
            // but a warehouse from before that fix, or one seeded outside the application,
            // may still be missing one. Without this, quantity_items_table has nothing to
            // add the incoming quantity onto and the transfer's other half is lost.
            dao.ensureDestination(command.toStockId(), ids);
            long id = dao.insert(command);
            dao.insertLines(id, command.lines());
            return id;
        });
    }

    /**
     * Reverses a posted transfer entirely - there is no partial undo, the same as a
     * document delete elsewhere. Refused inside a closed period for the same reason
     * posting one is: it would change a valuation already reported.
     */
    public void delete(int transferId) throws DaoException {
        PeriodLock.require(PeriodLockRegistry.STOCK_TRANSFER, transferId);
        DeletionService.shared()
                .delete(DeleteRegistry.STOCK_TRANSFERS, transferId, dao::deleteById)
                .rowsOrThrow();
    }

    /** Recent history, for the screen that lets a transfer be found and reversed. */
    public List<StockTransferSummary> recent(int limit) throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_TRANSFER_POST);
        return dao.recent(limit);
    }

    /** Line-level detail over a date range, for the printed transfer log. */
    public List<StockTransferReportRow> reportRows(java.time.LocalDate from, java.time.LocalDate to) throws DaoException {
        AuthorizationGuard.require(AppPermissions.STOCK_TRANSFER_POST);
        return dao.reportRows(from, to);
    }

    private static String message(String key, Object... args) {
        return args.length == 0
                ? LanguageManager.getInstance().getString(key)
                : LanguageManager.getInstance().getString(key, args);
    }
}
