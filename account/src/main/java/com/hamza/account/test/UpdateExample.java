package com.hamza.account.test;

import javax.swing.*;
import java.io.File;

public class UpdateExample {
    
    public static void main(String[] args) {
        String currentVersion = "1.0.0";
        UpdateManager updateManager = new UpdateManager(currentVersion);
        
        // التحقق من التحديثات
        UpdateManager.UpdateCheckResult result = updateManager.checkForUpdates();
        
        if (result.isUpdateAvailable()) {
            showUpdateDialog(result, updateManager);
        } else {
            System.out.println(result.getMessage());
        }
    }
    
    private static void showUpdateDialog(UpdateManager.UpdateCheckResult result, 
                                         UpdateManager updateManager) {
        String message = String.format(
            "تحديث جديد متوفر!\n\n" +
            "الإصدار الجديد: %s\n" +
            "حجم الملف: %.2f MB\n\n" +
            "ملاحظات الإصدار:\n%s\n\n" +
            "هل تريد التحديث الآن؟",
            result.getLatestVersion(),
            result.getFileSize() / (1024.0 * 1024.0),
            result.getReleaseNotes()
        );
        
        int option = JOptionPane.showConfirmDialog(
            null, 
            message, 
            "تحديث متوفر",
            result.isMandatory() ? JOptionPane.DEFAULT_OPTION : JOptionPane.YES_NO_OPTION
        );
        
        if (option == JOptionPane.YES_OPTION || result.isMandatory()) {
            downloadAndInstall(result, updateManager);
        }
    }
    
    private static void downloadAndInstall(UpdateManager.UpdateCheckResult result,
                                          UpdateManager updateManager) {
        JProgressBar progressBar = new JProgressBar(0, 100);
        JLabel statusLabel = new JLabel("جاري التحميل...");
        
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(statusLabel);
        panel.add(progressBar);
        
        JDialog dialog = new JDialog();
        dialog.setTitle("تحميل التحديث");
        dialog.setContentPane(panel);
        dialog.setSize(400, 100);
        dialog.setLocationRelativeTo(null);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setVisible(true);
        
        // التحميل في thread منفصل
        new Thread(() -> {
            updateManager.setProgressListener(new UpdateManager.DownloadProgressListener() {
                @Override
                public void onProgress(int percentage, long bytesDownloaded, long totalBytes) {
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(percentage);
                        statusLabel.setText(String.format(
                            "جاري التحميل... %.2f / %.2f MB",
                            bytesDownloaded / (1024.0 * 1024.0),
                            totalBytes / (1024.0 * 1024.0)
                        ));
                    });
                }
                
                @Override
                public void onComplete(String filePath) {
                    SwingUtilities.invokeLater(() -> {
                        dialog.dispose();
                        JOptionPane.showMessageDialog(null, 
                            "تم تحميل التحديث بنجاح!\nسيتم إعادة تشغيل التطبيق.");
                        updateManager.installUpdate(filePath);
                    });
                }
                
                @Override
                public void onError(Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        dialog.dispose();
                        JOptionPane.showMessageDialog(null, 
                            "حدث خطأ أثناء التحميل: " + e.getMessage(),
                            "خطأ",
                            JOptionPane.ERROR_MESSAGE);
                    });
                }
            });
            
            String savePath = System.getProperty("user.dir") + File.separator + "update.jar";
            updateManager.downloadUpdate(
                result.getDownloadUrl(), 
                savePath,
                result.getChecksum()
            );
        }).start();
    }
}
