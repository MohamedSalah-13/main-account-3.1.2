package com.hamza.account.controller.dataByName;

import com.hamza.controlsfx.observer.Publisher;

import java.util.List;
import java.util.function.ToIntFunction;

public interface AreaInterface<T> {

    Class<T> classData();

    List<? extends T> listData() throws Exception;

    T object(int id, String name);

    int getId(T t);

    String getName(T t);

    void setName(T t, String name);

    int insert(T t) throws Exception;

    int update(T t) throws Exception;

    T getDataById(int code) throws Exception;

    int deleteData(int code) throws Exception;

    ToIntFunction<T> getIdFunction();

    Publisher<String> publisherTable();
}
