package com.hamza.account.service;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.TargetDao;
import com.hamza.account.model.domain.Employees;
import com.hamza.account.model.domain.Target;
import com.hamza.controlsfx.database.DaoException;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public record TargetService(DaoFactory daoFactory) {

    public List<Target> targetList() throws DaoException {
        return getTargetDao().loadAll();
    }

    @NotNull
    private TargetDao getTargetDao() {
        return daoFactory.targetDao();
    }

    public int insertData(double target_ratio1, double rate1
            , double target_ratio2, double rate2, double target_ratio3, double rate3
            , double target, Employees employees, String notes) throws DaoException {
        try {
            return getTargetDao().insert(new Target(0, target_ratio1, rate1
                    , target_ratio2, rate2, target_ratio3, rate3
                    , target, employees, notes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int updateData(int id, double target_ratio1, double rate1
            , double target_ratio2, double rate2, double target_ratio3, double rate3
            , double target, Employees employees, String notes) throws DaoException {
        return getTargetDao().update(new Target(id, target_ratio1, rate1
                , target_ratio2, rate2, target_ratio3, rate3
                , target, employees, notes));
    }

    public Target targetById(int id) throws DaoException {
        return getTargetDao().getDataById(id);
    }

    public Optional<Target> targetByDelegateId(int id) throws DaoException {
        return Optional.ofNullable(getTargetDao().getDataByDelegateId(id));
    }

    public int deleteById(int id) throws DaoException {
        return getTargetDao().deleteById(id);
    }
}
