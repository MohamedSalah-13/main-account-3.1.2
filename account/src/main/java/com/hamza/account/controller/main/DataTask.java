package com.hamza.account.controller.main;

import com.hamza.controlsfx.database.DaoList;
import javafx.concurrent.Task;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.List;

@Log4j2
@Getter
public class DataTask extends Task<Void> {

    protected int length;

    protected static <T> List<T> listData(DaoList<T> list) {
        try {
            return list.loadAll();
        } catch (Exception e) {
            log.error(e.getMessage(), e.getCause());
            throw new RuntimeException();
        }
//        return Collections.emptyList();
    }

    @Override
    protected Void call() throws Exception {
        return null;
    }
}
