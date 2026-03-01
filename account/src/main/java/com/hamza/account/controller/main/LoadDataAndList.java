package com.hamza.account.controller.main;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.controlsfx.observer.Publisher;
import com.hamza.controlsfx.tasks.TaskApp;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.hamza.controlsfx.text.NumberUtils.roundToTwoDecimalPlaces;

@Log4j2
@Getter
public class LoadDataAndList extends DataList {

    public LoadDataAndList(DaoFactory daoFactory) {
        super(daoFactory);
    }

    /**
     * تحديث جميع البيانات عن طريق إشعار جميع المراقبين
     */
    public void updateData(DataPublisher dataPublisher) {
        if (dataPublisher == null) {
            log.warn("DataPublisher is null, skipping update");
            return;
        }

        Stream.of(
                        dataPublisher.getPublisherAddItem(),
                        dataPublisher.getPublisherAddStock(),
                        dataPublisher.getPublisherAddUser(),
                        dataPublisher.getPublisherAddEmployee(),
                        dataPublisher.getPublisherBuy(),
                        dataPublisher.getPublisherSales(),
                        dataPublisher.getPublisherAddAccountCustom(),
                        dataPublisher.getPublisherAddAccountSuppliers(),
                        dataPublisher.getPublisherAddNameCustomer(),
                        dataPublisher.getPublisherAddNameSuppliers(),
                        dataPublisher.getPublisherAddMainGroup(),
                        dataPublisher.getPublisherAddSubGroup()
                ).filter(java.util.Objects::nonNull)
                .forEach(Publisher::notifyObservers);
    }

    @Override
    protected Void call() {
        try {
            return processMethodCalls();
        } catch (Exception e) {
            log.error("Error executing methods: {}", e.getMessage(), e);
            updateMessage("Error: " + e.getMessage());
            throw new RuntimeException("Failed to load data", e);
        }
    }

    private Void processMethodCalls() {
        Method[] methods = DataList.class.getDeclaredMethods();
        List<Method> targetMethods = Arrays.stream(methods)
                .filter(method -> method.getName().contains("get2"))
                .toList();

        int total = targetMethods.size();
        AtomicInteger current = new AtomicInteger(1);

        for (Method method : targetMethods) {
            if (isCancelled()) {
                log.info("Task cancelled by user");
                break;
            }
            processMethod(method, current.getAndIncrement(), total);
        }

        updateProgress(total, total);
        updateMessage("100");
        updateTitle("Load Data Complete");
        done();
        return null;
    }

    private void processMethod(Method method, int current, int total) {
        try {
//            log.info("Loading data from method: {} ({}/{})", method.getName(), current, total);
            method.setAccessible(true); // Enable access to private methods
            method.invoke(this);
            updateProgress(current, total, method.getName());
        } catch (IllegalAccessException | InvocationTargetException e) {
            log.error("Error invoking method {}: {}", method.getName(), e.getMessage(), e);
            // لا نرمي استثناء هنا للسماح باستمرار التحميل
        }
    }

    private void updateProgress(double current, double total, String methodName) {
        try {
            double percentage = (current / total) * 100;
            int percentageInt = (int) roundToTwoDecimalPlaces(percentage);

            updateProgress(current, total);
            updateMessage(String.valueOf(percentageInt));
            updateTitle("Loading: " + methodName);

            Thread.sleep(50); // تقليل وقت الانتظار لتحسين الأداء
        } catch (InterruptedException e) {
            log.warn("Thread interrupted during progress update", e);
            Thread.currentThread().interrupt();
        }
    }

    @NonNull
    private TaskApp<Void> createUpdateTask(String[] methodNames, String taskName) {
        return new TaskApp<>(vTaskApp -> {
            vTaskApp.setLength(methodNames.length);
            Class<DataList> dataListClass = DataList.class;
            Method[] declaredMethods = dataListClass.getDeclaredMethods();
            List<String> targetMethods = Arrays.asList(methodNames);

            int processed = 0;
            for (Method method : declaredMethods) {
                String methodName = method.getName();
                if (targetMethods.contains(methodName)) {
                    try {
                        log.info("Executing method: {} for task: {}", methodName, taskName);
                        method.setAccessible(true); // Enable access to private methods
                        method.invoke(this);
                        vTaskApp.getData(processed, methodName);
                        processed++;
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        log.error("Error executing method {}: {}", methodName, e.getMessage(), e);
                    }
                }
            }

            vTaskApp.getData(processed, "Done - " + taskName);
            log.info("Task completed: {} - Processed {}/{} methods", taskName, processed, methodNames.length);
            return null;
        }, methodNames.length);
    }
}