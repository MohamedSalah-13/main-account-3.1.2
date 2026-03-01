package com.hamza.controlsfx.tasks;

/**
 * Functional interface representing a generic task that can be executed with a given TaskApp context.
 *
 * @param <T> the type of the result produced by the task
 */
@FunctionalInterface
public interface TaskInterface<T> {

    /**
     * Executes a task using the provided TaskApp instance and returns the result.
     *
     * @param vTaskApp the TaskApp instance that provides the necessary context and execution framework for the task
     * @return the result of the task execution
     * @throws Exception if an error occurs during the task execution
     */
    T action(TaskApp<T> vTaskApp) throws Exception;
}
