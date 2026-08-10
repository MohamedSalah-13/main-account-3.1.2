package com.hamza.account.controller.dataSetting;

import com.hamza.controlsfx.observer.AppEvent;

import java.util.List;

public interface AddDataInterface {
    void addData() throws Exception;

    void updateData(String name) throws Exception;

    int deleteData(String name) throws Exception;

    List<String> listData() throws Exception;

    String titlePane();

    /**
     * The event that means this list is out of date, or null for a list that
     * nothing announces changes to.
     */
    default Class<? extends AppEvent> refreshOn() {
        return null;
    }
}
