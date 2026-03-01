package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.domain.TargetsDetails;
import com.hamza.controlsfx.database.DaoException;

import java.util.List;

public record TargetDetailsService(DaoFactory daoFactory) {

    public List<TargetsDetails> getAllTargets() throws DaoException {
        return daoFactory.targetDetailsDao().loadAll();
    }
}