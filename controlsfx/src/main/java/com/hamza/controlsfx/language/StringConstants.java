package com.hamza.controlsfx.language;

import lombok.extern.log4j.Log4j2;

import static com.hamza.controlsfx.language.ResourceLanguage.getWordData;

@Log4j2
public class StringConstants {
    public static final String OK = getWordData("OK");
    public static final String CANCEL = getWordData("CANCEL");
    public static final String WRONG = getWordData("WRONG");
    public static final String SAVE_DONE = getWordData("SAVE_DONE");
    public static final String SAVE_ALL_DATA = getWordData("SAVE_ALL_DATA");
    public static final String DELETE_DONE = getWordData("DELETE_DONE");
    public static final String DELETE_DATA = getWordData("DELETE_DATA");
    public static final String DELETE_ALL_DATA = getWordData("DELETE_ALL_DATA");
    public static final String DELETE = getWordData("DELETE");
    public static final String DO_YOU_WANT = getWordData("DO_YOU_WANT");
    public static final String DATA = getWordData("DATA");
    public static final String ALL_DATA = getWordData("ALL_DATA");
    public static final String BACKUP = getWordData("BACKUP");
    public static final String CLOSE = getWordData("CLOSE");
    public static final String SAVE = getWordData("SAVE");
    public static final String CHOOSE = getWordData("CHOOSE");
    public static final String RECOVERY = getWordData("RECOVERY");
    public static final String HOUR = getWordData("HOUR");
    public static final String WHERE_TO_SAVE = getWordData("WHERE_TO_SAVE");
    public static final String SAVING_TIME = getWordData("SAVING_TIME");
    public static final String ENTER = getWordData("ENTER");
    public static final String PASSWORD = getWordData("PASSWORD");
    public static final String SHOW = getWordData("SHOW");
    public static final String USERNAME = getWordData("USERNAME");
    public static final String COPY_RIGHT = getWordData("COPY_RIGHT");
    public static final String SCREEN_LOGIN = getWordData("SCREEN_LOGIN");
    public static final String PLEASE_WAIT = getWordData("PLEASE_WAIT");
    public static final String DATE = getWordData("DATE");
    public static final String TIME = getWordData("TIME");
    public static final String SYSTEM = getWordData("SYSTEM");
    public static final String COMPUTER_NAME = getWordData("COMPUTER_NAME");
    public static final String DOWNLOAD_THE_DATABASE = getWordData("DOWNLOAD_THE_DATABASE");
    public static final String SAVE_BEFORE_CLOSE = getWordData("SAVE_BEFORE_CLOSE");
    public static final String AUTOMATIC_BACKUP = getWordData("AUTOMATIC_BACKUP");
    public static final String MESSAGE = getWordData("MESSAGE");
    public static final String LOCATION_IS_NOT_SET = getWordData("LOCATION_IS_NOT_SET");
    public static final String PLEASE_ENTER_YOUR_SYSTEM_PASSWORD = getWordData("PLEASE_ENTER_YOUR_SYSTEM_PASSWORD");

    private StringConstants() {
    }

}
