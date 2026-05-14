package com.hamza.account.config;


import lombok.extern.log4j.Log4j2;

import java.io.File;

@Log4j2
public class Configs {

    public static final boolean ADD_PACKAGE_TO_ITEMS = false; // تستخدم فى إضافة المجموعات ام لا
    public static final File FILE_REPORTS = new File("reports/");
}
