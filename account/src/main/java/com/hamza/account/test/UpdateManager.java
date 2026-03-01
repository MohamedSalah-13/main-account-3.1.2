package com.hamza.account.test;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;

@Slf4j
public class UpdateManager {
    private static final String UPDATE_SERVER_URL = "https://your-domain.com/api/update";
    private String currentVersion;
    private DownloadProgressListener progressListener;

    public UpdateManager(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public void setProgressListener(DownloadProgressListener listener) {
        this.progressListener = listener;
    }

    public UpdateCheckResult checkForUpdates() {
        HttpURLConnection conn = null;
        try {
            String urlString = UPDATE_SERVER_URL + "/check?currentVersion=" + currentVersion;
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return new UpdateCheckResult(false, "Server returned: " + responseCode);
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();

            JSONObject response = new JSONObject(content.toString());
            boolean updateAvailable = response.getBoolean("updateAvailable");

            if (updateAvailable) {
                return new UpdateCheckResult(
                        true,
                        response.getString("latestVersion"),
                        response.getString("releaseNotes"),
                        response.getBoolean("isMandatory"),
                        response.getString("downloadUrl"),
                        response.getLong("fileSize"),
                        response.optString("checksum", null)
                );
            }

            return new UpdateCheckResult(false, response.optString("message", "No updates available"));

        } catch (Exception e) {
            log.error("Error checking for updates", e);
            return new UpdateCheckResult(false, "Error checking for updates: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public boolean downloadUpdate(String downloadUrl, String savePath, String expectedChecksum) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(downloadUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            long fileSize = conn.getContentLengthLong();

            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(savePath)) {

                byte[] buffer = new byte[8192];
                long totalBytesRead = 0;
                int bytesRead;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    if (progressListener != null && fileSize > 0) {
                        int progress = (int) ((totalBytesRead * 100) / fileSize);
                        progressListener.onProgress(progress, totalBytesRead, fileSize);
                    }
                }
            }

            // التحقق من checksum
            if (expectedChecksum != null && !expectedChecksum.isEmpty()) {
                String actualChecksum = calculateChecksum(savePath);
                if (!actualChecksum.equalsIgnoreCase(expectedChecksum)) {
                    log.error("Checksum mismatch! Expected: {}, Got: {}", expectedChecksum, actualChecksum);
                    Files.deleteIfExists(Paths.get(savePath));
                    return false;
                }
            }

            if (progressListener != null) {
                progressListener.onComplete(savePath);
            }

            return true;

        } catch (Exception e) {
            log.error("Error downloading update", e);
            if (progressListener != null) {
                progressListener.onError(e);
            }
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String calculateChecksum(String filePath) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(Paths.get(filePath))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }

        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void installUpdate(String updateFilePath) {
        try {
            // إنشاء ملف batch للتحديث
            String batchContent = "@echo off\n" +
                    "timeout /t 3 /nobreak > nul\n" +
                    "taskkill /F /IM \"" + getCurrentJarName() + "\"\n" +
                    "timeout /t 2 /nobreak > nul\n" +
                    "move /Y \"" + updateFilePath + "\" \"" + getCurrentJarPath() + "\"\n" +
                    "start \"\" \"" + getCurrentJarPath() + "\"\n" +
                    "del \"%~f0\"";

            String batchFile = System.getProperty("user.dir") + "\\update.bat";
            Files.write(Paths.get(batchFile), batchContent.getBytes());

            // تشغيل ملف batch
            Runtime.getRuntime().exec("cmd /c start " + batchFile);

            // إغلاق التطبيق الحالي
            System.exit(0);

        } catch (Exception e) {
            log.error("Error installing update", e);
        }
    }

    private String getCurrentJarPath() {
        try {
            return new File(UpdateManager.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getPath();
        } catch (Exception e) {
            return "";
        }
    }

    private String getCurrentJarName() {
        try {
            return new File(getCurrentJarPath()).getName();
        } catch (Exception e) {
            return "";
        }
    }

    // واجهة للاستماع لتقدم التحميل
    public interface DownloadProgressListener {
        void onProgress(int percentage, long bytesDownloaded, long totalBytes);

        void onComplete(String filePath);

        void onError(Exception e);
    }

    @Setter
    @Getter
    public static class UpdateCheckResult {
        private boolean updateAvailable;
        private String latestVersion;
        private String releaseNotes;
        private boolean isMandatory;
        private String downloadUrl;
        private long fileSize;
        private String checksum;
        private String message;

        public UpdateCheckResult(boolean updateAvailable, String message) {
            this(updateAvailable, null, null, false, null, 0, null);
            this.message = message;
        }

        public UpdateCheckResult(boolean updateAvailable, String latestVersion,
                                 String releaseNotes, boolean isMandatory,
                                 String downloadUrl, long fileSize, String checksum) {
            this.updateAvailable = updateAvailable;
            this.latestVersion = latestVersion;
            this.releaseNotes = releaseNotes;
            this.isMandatory = isMandatory;
            this.downloadUrl = downloadUrl;
            this.fileSize = fileSize;
            this.checksum = checksum;
            this.message = null;
        }
    }
}