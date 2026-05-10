package com.hamza.account.view;

import com.hamza.account.config.Image_Setting;
import com.hamza.account.config.SaveDatabaseFile;
import com.hamza.account.controller.main.MainScreenController;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.otherSetting.ExitClass;
import com.hamza.controlsfx.language.Error_Text_Show;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.util.Screen_Size;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.Properties;

import static com.hamza.account.config.PropertiesName.getBackupDatabaseSaveBeforeClose;
import static com.hamza.account.config.PropertiesName.setAppLastRunVersion;

@Log4j2
@RequiredArgsConstructor
public class MainScreenApplication extends Application {

    public static Scene sceneMainScreen;
    private final DaoFactory daoFactory;

    private static String getAppVersion() {
        // 1) من الـ Manifest عندما يكون التطبيق مبنياً كـ JAR
//        String v = MainScreenApplication.class.getPackage().getImplementationVersion();
//        if (v != null && !v.isBlank()) {
//            return v;
//        }

        // 2) احتياطي: من ملف خصائص ضمن resources عند التشغيل من IDE
        try (var is = MainScreenApplication.class.getResourceAsStream("/version.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String pv = props.getProperty("app.version");
                if (pv != null && !pv.isBlank()) {
                    return pv;
                }
            }
        } catch (Exception ignored) {
        }

        // 3) قيمة افتراضية في بيئة التطوير
        return "dev";
    }


    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("mainScreen-view.fxml"));
        MainScreenController controller = new MainScreenController(daoFactory);
        fxmlLoader.setController(controller);
        Parent load = fxmlLoader.load();
        Scene scene = new SceneAll(load);
        sceneMainScreen = scene;

        Screen_Size.adjustStageToFullScreen(stage);

        String version = getAppVersion(); // اجلب رقم النسخة تلقائياً
        stage.setTitle(Setting_Language.PROGRAM_TITLE + " - version: " + version);

        stage.getIcons().add(new Image(new Image_Setting().tools));
        stage.setScene(scene);
        stage.show();

        // خزّن رقم النسخة الحالية (اختياري)
        setAppLastRunVersion(version);

        var exitClass = new ExitClass() {
            @Override
            public void updateData() {
                updateDataAll(true);
            }
        };
        exitClass.exit(stage);

        // this use for logout
        controller.getCloseStageFromLogout().addObserver(message -> {
            if (message) {
                stage.close();
                updateDataAll(false);
            }
        });

    }

    private void updateDataAll(boolean b) {
        Thread thread = new Thread(() -> {
            try {
                if (getBackupDatabaseSaveBeforeClose()) SaveDatabaseFile.saveBeforeClose(false);

                Thread.sleep(500);
                var usersVo = LogApplication.usersVo;
                usersVo.setUser_available(0);
                int i = daoFactory.usersDao().updateAvailable(usersVo);
                if (i == 1) {
                    log.info(Error_Text_Show.DONE_READING_FROM_FILE, "user logout");
                    if (b) System.exit(0);
                } else log.error("User not found");
            } catch (Exception e) {
                log.error(e.getMessage(), e.getCause());
                System.exit(0);
            }
        });
        thread.start();
    }
}

