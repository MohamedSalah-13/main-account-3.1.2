package com.hamza.account.controller.reports;

@FunctionalInterface
public interface ExportReport {
    boolean success(String path);
}
