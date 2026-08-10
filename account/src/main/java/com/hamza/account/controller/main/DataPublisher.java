package com.hamza.account.controller.main;

import com.hamza.controlsfx.observer.Publisher;
import lombok.Getter;

/**
 * What is left of the publisher bag: signals about windows, not about data.
 * <p>
 * Every domain event that used to live here is now a record on the
 * {@code EventBus}, under {@code account.features.events}. These four stay: they
 * say "close yourself", "the login screen setting changed", "show or hide the
 * totals box", "the background image changed" and "the shift changed", which are
 * window control rather than something that happened to the business. They also
 * belong to the main screen exactly as long as the windows they steer do, so the
 * bag being thrown away at logout is the disposal they want.
 */
@Getter
public class DataPublisher {

    private final Publisher<Boolean> closeStageFromLogout = new Publisher<>();
    private final Publisher<Boolean> showLoginScreen = new Publisher<>();
    private final Publisher<Boolean> showMainTotalsScreen = new Publisher<>();
    private final Publisher<String> changeMainScreenImage = new Publisher<>();
    private final Publisher<Boolean> publisherShiftChanged = new Publisher<>();

}
