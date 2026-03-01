package com.hamza.controlsfx.file;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.*;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static com.hamza.controlsfx.file.FileDir.PROPERTY_TEMP;

public class Zip_File {


    public static void zipFile(File fileToZip, String path) throws IOException {
        /* File destination = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());*/
        ZipFile zipFile = new ZipFile(path);
        zipFile.setPassword(new char[]{'m', '1', '3', 'i', 'd', 'o'});
        ZipParameters zipParameters = new ZipParameters();
        zipParameters.setCompressionMethod(CompressionMethod.DEFLATE);
        zipParameters.setCompressionLevel(CompressionLevel.NORMAL);
        zipParameters.setEncryptFiles(true);
        zipParameters.setEncryptionMethod(EncryptionMethod.AES);
        zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
        zipParameters.setAesVersion(AesVersion.ONE);
        zipFile.addFile(fileToZip, zipParameters);

    }

    @SuppressWarnings("resource")
    public static String openZipFile(String path) throws ZipException {
        File destination = new File(PROPERTY_TEMP, UUID.randomUUID().toString());
        ZipFile zipFile = new ZipFile(path);
        if (zipFile.isEncrypted()) {
            zipFile.setPassword(new char[]{'m', '1', '3', 'i', 'd', 'o'});
        }
        zipFile.extractAll(destination.getAbsolutePath());

        return destination.getAbsolutePath() + "/" + zipFile.getFile().getName().replace(".zip", ".sql");
    }
}
