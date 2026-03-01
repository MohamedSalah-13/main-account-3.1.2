package com.hamza.controlsfx.interfaceData;

import javafx.concurrent.WorkerStateEvent;

public interface DownloadTask {

    /**
     * Executes the specified action once the progress has successfully finished.
     *
     * @param workerStateEvent the event triggered upon the successful completion of the progress task
     */
    // this use to make action after progress finish
    void onSucceededProgressFinished(WorkerStateEvent workerStateEvent);
}
