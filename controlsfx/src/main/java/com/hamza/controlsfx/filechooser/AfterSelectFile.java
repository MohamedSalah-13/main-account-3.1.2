package com.hamza.controlsfx.filechooser;

@FunctionalInterface
public interface AfterSelectFile {

    void afterSelect(String url) throws Exception;

}
