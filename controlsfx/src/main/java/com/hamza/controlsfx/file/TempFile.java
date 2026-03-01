package com.hamza.controlsfx.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public class TempFile {


    public Path getTempFile() throws IOException {
        Path temp = Files.createTempFile("", ".tmp");

        String absolutePath = temp.toString();
        System.out.println("Temp file : " + absolutePath);

        String separator = FileSystems.getDefault().getSeparator();
        String tempFilePath = absolutePath
                .substring(0, absolutePath.lastIndexOf(separator));

        System.out.println("Temp file path : " + tempFilePath);
        return temp;
    }

    public File getTempFileWithName() throws IOException {
        File temp = File.createTempFile("log_", ".tmp");
        System.out.println("Temp file : " + temp.getAbsolutePath());

        String absolutePath = temp.getAbsolutePath();
        String tempFilePath = absolutePath
                .substring(0, absolutePath.lastIndexOf(File.separator));

        System.out.println("Temp file path : " + tempFilePath);
        return temp;
    }

    public File createTempDirectory() throws IOException {
        final File temp = File.createTempFile("temp", Long.toString(System.nanoTime()));

        if (!(temp.delete())) {
            throw new IOException("Could not delete temp file: " + temp.getAbsolutePath());
        }

        if (!(temp.mkdir())) {
            throw new IOException("Could not create temp directory: " + temp.getAbsolutePath());
        }

        return (temp);
    }

}
