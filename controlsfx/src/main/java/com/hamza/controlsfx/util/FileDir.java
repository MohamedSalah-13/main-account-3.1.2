package com.hamza.controlsfx.util;

import java.io.File;

public class FileDir {

    /**
     * Holds the directory path of the current user's home directory.
     * This value is retrieved dynamically from the system properties
     * using the key "user.home".
     */
    public static final String USER_HOME = System.getProperty("user.home");
    /**
     * The name of the operating system.
     * This variable holds the value of the system property "os.name", which represents
     * the name of the operating system on which the Java Virtual Machine is running.
     */
    public static final String OPERATE_SYSTEM = System.getProperty("os.name");
    /**
     * The name of the currently logged-in user.
     * This variable is initialized with the system property "user.name".
     */
    public static final String USER_NAME = System.getProperty("user.name");
    /**
     * Represents the current working directory of the user.
     * This is initialized to the value of the "user.dir" system property.
     * Typically, this is the directory from which the Java Virtual Machine was invoked.
     */
    public static final String USER_DIR = System.getProperty("user.dir");
    /**
     * Represents the system temporary directory path.
     *
     * This variable is initialized with the value of the system property "java.io.tmpdir".
     * It indicates the default directory used for temporary files created by the application.
     */
    public static final String PROPERTY_TEMP = System.getProperty("java.io.tmpdir");

    /**
     * Determines the appropriate file saving path.
     *
     * @param pathFile the initial file path; if null or empty, defaults to user home directory.
     * @return the resolved file path for saving.
     */
    public static String whereToSaveFile(String pathFile) {
        if (pathFile == null || pathFile.isEmpty()) {
            pathFile = USER_HOME;
        }
        return pathFile;
    }

    /**
     * Determines the absolute path to store files based on the provided storage location.
     * If the provided storage location exists and is a directory, its absolute path is returned.
     * Otherwise, the user's home directory is returned.
     *
     * @param storageLocation the desired storage location, which could be a directory path
     * @return the absolute path to store files, either the provided storage location (if valid) or the user's home directory
     */
    public static String storageLocation(String storageLocation) {
        String savePlace = USER_HOME;
        File f = new File(storageLocation);
        if (f.exists() && f.isDirectory()) {
            savePlace = f.getAbsolutePath();
        }
        return savePlace;
    }

}
