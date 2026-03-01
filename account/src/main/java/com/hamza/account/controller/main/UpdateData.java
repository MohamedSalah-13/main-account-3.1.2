package com.hamza.account.controller.main;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class UpdateData {

    public static Runnable getRunnable(RunnableData runnableData) {
        return () -> {
            while (true) {
                try {
                    Thread.sleep(5000);
                    runnableData.defaultMethod();
                    log.info("name: {}", runnableData.name());
                } catch (InterruptedException e) {
                    log.error(e.getMessage(), e.getCause());
                }
            }
        };
    }
}
