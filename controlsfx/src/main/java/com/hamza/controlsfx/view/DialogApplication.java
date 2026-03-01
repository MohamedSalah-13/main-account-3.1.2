package com.hamza.controlsfx.view;

import com.hamza.controlsfx.alert.AllAlerts;
import com.hamza.controlsfx.interfaceData.AppSettingInterface;
import com.hamza.controlsfx.language.Setting_Language;
import com.hamza.controlsfx.others.ChangeOrientation;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import lombok.extern.log4j.Log4j2;

import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

@Log4j2
public class DialogApplication<T> extends Dialog<T> {

    @SuppressWarnings("unchecked")
    public DialogApplication(AppSettingInterface appSettingInterface) throws Exception {
        setTitle(appSettingInterface.title());

        if (appSettingInterface.header() != null && !appSettingInterface.header().isEmpty())
            setHeaderText(appSettingInterface.header());

        DialogPane dialogPane = this.getDialogPane();
        dialogPane.setContent(appSettingInterface.pane());
        ChangeOrientation.sceneOrientation(dialogPane.getScene());

        if (appSettingInterface.addLastPane()) {
            var ok = ButtonType.OK;
            var cancel = ButtonType.CANCEL;

            dialogPane.getButtonTypes().addAll(ok, cancel);
            Button buttonOK = (Button) getDialogPane().lookupButton(ok);
            buttonOK.setDefaultButton(false);
            Button buttonCancel = (Button) getDialogPane().lookupButton(cancel);
            buttonCancel.setId("btnClose");
            buttonOK.setText(Setting_Language.WORD_SAVE + " F10");
            buttonCancel.setText(Setting_Language.WORD_CANCEL);
            buttonCancel.setCancelButton(true);
            dialogPane.getScene().getAccelerators().put(KeyCombination.keyCombination("F10"), buttonOK::fire);

            // نحفظ نتيجة الحفظ هنا ليقرأها resultConverter لاحقاً
            final AtomicReference<Integer> saveResultRef = new AtomicReference<>(null);

            // منع إغلاق الـ Dialog عند حدوث خطأ أثناء الحفظ
            buttonOK.addEventFilter(ActionEvent.ACTION, evt -> {
                try {
                    // تأكيد الحفظ
                    if (!AllAlerts.confirmSave()) {
                        evt.consume(); // لا تُغلق
                        return;
                    }

                    Integer res = appSettingInterface.save();
                    // اعتبر 1 نجاحاً (عدّل حسب بروتوكولك)
                    if (res == null || res != 1) {
                        // فشل الحفظ أو تحقق فاشل: لا تُغلق
                        evt.consume();
                        return;
                    }

                    // نجاح: خزّن النتيجة ليمررها resultConverter
                    saveResultRef.set(res);
                    // لا تستهلك الحدث لكي يُغلق الـ Dialog بشكل طبيعي
                } catch (Exception e) {
                    AllAlerts.showExceptionDialog(e);
                    log.error(e.getMessage(), e);
                    // لا تُغلق الـ Dialog عند الاستثناء
                    evt.consume();
                }
            });

            // أعد النتيجة بناءً على ما حدث في الـ EventFilter
            setResultConverter(buttonType -> {
                if (buttonType == ButtonType.OK) {
                    // إذا لم يتم الحفظ بنجاح، ستكون null ولن يُغلق (لأننا استهلكنا الحدث)
                    return (T) saveResultRef.get();
                } else if (buttonType == ButtonType.CANCEL) {
                    // 0 = إلغاء
                    return (T) Integer.valueOf(0);
                }
                return null;
            });
        }

        // stage setting
        Stage stage = (Stage) dialogPane.getScene().getWindow();

        if (appSettingInterface.inputStream() != null)
            stage.getIcons().add(new Image(appSettingInterface.inputStream()));

        if (appSettingInterface.resize())
            stage.setResizable(true);
        else Toolkit.getDefaultToolkit().beep();

        stage.setOnCloseRequest(event -> {
            event.consume();
            stage.close();
        });
    }

}

