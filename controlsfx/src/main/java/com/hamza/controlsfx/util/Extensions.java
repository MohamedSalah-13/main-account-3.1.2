package com.hamza.controlsfx.util;

import javafx.stage.FileChooser;

public class Extensions {

    /**
     * A FileChooser.ExtensionFilter that filters for image files with the extensions .jpg and .png.
     * This is utilized to restrict the file selection to image files only within a file chooser dialog.
     */
    public static final FileChooser.ExtensionFilter FILTER_IMAGE = new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.png");
    /**
     * A file chooser filter that allows users to select only PNG image files.
     * This filter ensures that the file dialog will display and accept files with a .png extension.
     */
    public static final FileChooser.ExtensionFilter FILTER_IMAGE_PNG = new FileChooser.ExtensionFilter("Image Files (*.png)", "*.png");
    /**
     * A file chooser extension filter for Excel spreadsheet files with the .xlsx extension.
     * This filter allows users to easily locate and select files with the .xlsx extension
     * for opening or saving operations within a file chooser dialog.
     */
    public static final FileChooser.ExtensionFilter FILTER_XLSX = new FileChooser.ExtensionFilter("data files (*.xlsx)", "*.xlsx");
    /**
     * A FileChooser.ExtensionFilter for SQL data files with the extension ".sql".
     * This filter can be used to restrict the selectable files in a file chooser dialog
     * to only SQL files, for easier file selection and to prevent user error.
     */
    public static final FileChooser.ExtensionFilter EXT_FILTER_SQL = new FileChooser.ExtensionFilter("data files (*.sql)", "*.sql");
    /**
     * Defines a file chooser extension filter for ZIP archive files.
     * Filters files to only show those with the extension ".zip".
     * This is commonly used to specify the type of files to look for
     * when opening or saving data files in ZIP format.
     */
    public static final FileChooser.ExtensionFilter EXT_FILTER_ZIP = new FileChooser.ExtensionFilter("data files (*.zip)", "*.zip");
    public static final FileChooser.ExtensionFilter EXT_FILTER_JSON = new FileChooser.ExtensionFilter("data files (*.json)", "*.json");
    public static final FileChooser.ExtensionFilter EXT_FILTER_BAK = new FileChooser.ExtensionFilter("data files (*.bak)", "*.bak");

}
