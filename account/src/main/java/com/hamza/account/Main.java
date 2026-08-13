package com.hamza.account;

import com.hamza.account.view.DownLoadApplication;
import com.hamza.controlsfx.error.GlobalExceptionHandler;

public class Main {

    public static void main(String[] args) throws Exception {
        GlobalExceptionHandler.install();
        DownLoadApplication.main(args);
    }

}
