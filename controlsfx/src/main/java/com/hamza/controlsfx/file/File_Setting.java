package com.hamza.controlsfx.file;

import org.apache.commons.io.FileUtils;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;

/**
 * The File_Setting class provides utility methods for common file operations such
 * as copying, opening, and deleting files or directories.
 */
public class File_Setting {

    /**
     * Deletes the directory containing the specified file.
     *
     * @param s the path of the file whose parent directory is to be deleted.
     * @throws IOException if an I/O error occurs.
     */
    public static void deleteFileDirectory(String s) throws IOException {
        File file = new File(s);
        FileUtils.deleteDirectory(file.getParentFile());
    }

    /**
     * Deletes the specified file or directory.
     * If the path represents a directory, its contents will also be deleted.
     *
     * @param s the path to the file or directory to be deleted
     * @throws IOException if an I/O error occurs
     */
    public static void deleteFile(String s) throws IOException {
        File file = new File(s);
        FileUtils.deleteDirectory(file);
    }

    /**
     * Copies a file from the specified source URL to the target URL.
     *
     * @param url the path to the source file that needs to be copied
     * @param toUrl the path to the target file location
     * @throws IOException if an I/O error occurs during the copying process
     */
    public void copy_file(String url, String toUrl) throws IOException {
        Path from = Paths.get(url);
        Path to = Paths.get(toUrl);
        CopyOption[] options = new CopyOption[]{
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES
        };
        Files.copy(from, to, options);
    }

    /**
     * Opens a file with the default application associated with its type.
     *
     * @param pathname the path to the file to be opened
     * @throws IOException if an I/O error occurs
     */
    public void open_file(String pathname) throws IOException {
        Desktop.getDesktop().open(new File(pathname));
    }

}
