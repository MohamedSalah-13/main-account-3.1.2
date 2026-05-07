package com.hamza.account.controller.others;

import com.hamza.controlsfx.language.Setting_Language;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.controlsfx.control.TaskProgressView;
import org.controlsfx.glyphfont.FontAwesome;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskProgressViewAction extends Application {

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final FontAwesome fontAwesome = new FontAwesome();
    private final TaskProgressView<Task<?>> taskProgressView = new TaskProgressView<>();
    private final Task<?> voidTask;
    private int taskCounter;

    public TaskProgressViewAction(Task<?> voidTask) {
        this.voidTask = voidTask;
    }


    @Override
    public void start(Stage primaryStage) {
        Callback<Task<?>, Node> factory = task -> {
            org.controlsfx.glyphfont.Glyph result = fontAwesome.create(FontAwesome.Glyph.DOWNLOAD).size(24);

            switch ((int) task.getProgress()) {
                case 20:
                    result = fontAwesome.create(FontAwesome.Glyph.MOBILE_PHONE).size(24)
                            .color(Color.RED);
                    break;
                case 50:
                    result = fontAwesome.create(FontAwesome.Glyph.COMPASS).size(24)
                            .color(Color.GREEN);
                    break;
                case 80:
                    result = fontAwesome.create(FontAwesome.Glyph.APPLE).size(24)
                            .color(Color.BLUE);
                    break;
                default:
            }

            if (result != null) {
                result.setEffect(new DropShadow(8, Color.RED));
                result.setAlignment(Pos.CENTER);

                /*
                 * We have to make sure all glyps have the same size. Otherwise
                 * the progress cells will not be aligned properly.
                 */
                result.setPrefSize(24, 24);
            }

            return result;
        };

        taskProgressView.setGraphicFactory(factory);
        VBox root = new VBox(5);
        root.setPadding(new Insets(10));
        root.setAlignment(Pos.CENTER);

        // Add our controls to the scene
        root.getChildren().addAll(taskProgressView);
        Scene scene = new Scene(root);
        primaryStage.setWidth(450);
        primaryStage.setHeight(200);
        primaryStage.setTitle(Setting_Language.WORD_REFRESH);
        primaryStage.initModality(Modality.APPLICATION_MODAL);
        primaryStage.setResizable(false);
        primaryStage.setScene(scene);
        primaryStage.show();
        startTask(voidTask);
    }

    private void startTask(Task<?> voidTask) {
        taskCounter++;
//        MyTask task = new MyTask("Task #" + taskCounter);
//        taskProgressView.getTasks().add(task);
        taskProgressView.getTasks().add(voidTask);
        taskProgressView.setRetainTasks(true);
        // execute task
        executorService.submit(voidTask);
//        executorService.submit(task);
    }
}
