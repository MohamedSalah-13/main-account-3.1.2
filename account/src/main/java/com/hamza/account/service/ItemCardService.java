package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.ItemCardModel;
import com.hamza.account.database.DaoException;

import java.time.LocalDate;
import java.util.List;

public record ItemCardService(DaoFactory daoFactory) {

    public List<ItemCardModel> getItemMovements(Integer itemId, Integer stockId,
                                                LocalDate startDate, LocalDate endDate) throws DaoException {
        return daoFactory.itemCardDao().getItemMovements(itemId, stockId, startDate, endDate);
    }
}
