package com.hamza.account.service;

import com.hamza.account.config.DefaultStock;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.Stock;
import com.hamza.controlsfx.database.DaoException;

/**
 * Reads the single stock the application works against.
 * <p>
 * Multi-warehouse support was removed: there is no screen to create, rename from a
 * list, transfer between or delete a warehouse any more, so the list/insert/update/
 * delete methods that served those screens went with them. What remains is the one
 * lookup the reports still need - the stock's name for a printed header.
 * <p>
 * The {@code stocks} table itself is untouched; see {@link DefaultStock}.
 */
public record StockService(DaoFactory daoFactory) {

    public Stock getDefaultStock() throws DaoException {
        return daoFactory.stockDao().getDataById(DefaultStock.ID);
    }
}
