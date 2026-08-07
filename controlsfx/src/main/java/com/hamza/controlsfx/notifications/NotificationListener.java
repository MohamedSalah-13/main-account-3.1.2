package com.hamza.controlsfx.notifications;

/**
 * Told about every notification the policy lets through, on the UI thread.
 * <p>
 * This is the seam that keeps presentation out of {@link NotificationCenter}: the
 * toast, the beep, an audit-log writer and a future e-mail or WhatsApp relay are
 * all just listeners, and adding one does not touch the centre.
 */
@FunctionalInterface
public interface NotificationListener {

    /**
     * @param notification the entry as it now stands in the inbox - for a coalesced
     *                     repeat this is the existing entry with its counter already
     *                     bumped, not the candidate that triggered it
     */
    void onNotification(AppNotification notification);
}
