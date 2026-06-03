package com.hamza.account.database;

import java.sql.ResultSet;
import java.util.List;

public interface DaoList<T> {

    default List<T> loadAll() throws DaoException {
        throw new UnsupportedOperationException("loadAll not implemented");
    }

    default List<T> loadAllById(int id) throws DaoException {
        throw new UnsupportedOperationException("loadAllById not implemented");
    }

    default List<T> loadDataBetweenDate(String startDate, String endDate) throws DaoException {
        throw new UnsupportedOperationException("loadDataBetweenDate not implemented");
    }

    default int insert(T t) throws DaoException {
        throw new UnsupportedOperationException("insert not implemented");
    }

    default int update(T t) throws DaoException {
        throw new UnsupportedOperationException("update not implemented");
    }

    default int deleteById(int id) throws DaoException {
        throw new UnsupportedOperationException("deleteById not implemented");
    }

    default T getDataById(int id) throws DaoException {
        throw new UnsupportedOperationException("getDataById not implemented");
    }

    default T getDataByString(String s) throws DaoException {
        throw new UnsupportedOperationException("getDataByString not implemented");
    }

    default Object[] getData(T t) throws DaoException {
        throw new UnsupportedOperationException("getData not implemented");
    }

    default T map(ResultSet rs) throws DaoException {
        throw new UnsupportedOperationException("map not implemented");
    }

    default int insertList(List<T> list) throws DaoException {
        throw new UnsupportedOperationException("insertList not implemented");
    }

    default int updateList(List<T> list) throws DaoException {
        throw new UnsupportedOperationException("updateList not implemented");
    }
}
