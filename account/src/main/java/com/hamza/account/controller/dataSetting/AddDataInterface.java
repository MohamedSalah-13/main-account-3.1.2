package com.hamza.account.controller.dataSetting;

import com.hamza.controlsfx.observer.Publisher;

import java.util.List;

public interface AddDataInterface {
    void addData() throws Exception;

    void updateData(String name) throws Exception;

    int deleteData(String name) throws Exception;

    List<String> listData() throws Exception;

    String titlePane();

    Publisher<?> publisher();
}
