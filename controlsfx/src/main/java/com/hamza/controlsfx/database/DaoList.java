package com.hamza.controlsfx.database;

import java.sql.ResultSet;
import java.util.List;

public interface DaoList<T> {

    default List<T> loadAll() throws DaoException {
        return null;
    }

    default List<T> loadAllById(int id) throws DaoException {
        return null;
    }

    default List<T> loadDataBetweenDate(String startDate, String endDate) throws DaoException {
        return null;
    }

    default int insert(T t) throws DaoException {
        return 0;
    }

    default int update(T t) throws DaoException {
        return 0;
    }

    default int deleteById(int id) throws DaoException {
        return 0;
    }

    default T getDataById(int id) throws DaoException {
        return null;
    }

    default T getDataByString(String s) throws DaoException {
        return null;
    }

    default Object[] getData(T t) throws DaoException {
        return new Object[0];
    }

    default T map(ResultSet rs) throws DaoException {
        return null;
    }

    default int insertList(List<T> list) throws DaoException {
        return 0;
    }

    default int updateList(List<T> list) throws DaoException {
        return 0;
    }
}
