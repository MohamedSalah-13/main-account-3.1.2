package com.hamza.account.features.events;

import com.hamza.controlsfx.observer.AppEvent;

/**
 * The company details - name, address, tax number, logo - were saved. Carries
 * nothing; a listener re-reads them.
 * <p>
 * Nothing listens yet, as nothing listened to the publisher it replaced. The
 * invoice headers and printed reports read the company row when they are built,
 * so a screen already open keeps the old details until it is reopened.
 */
public record CompanyChanged() implements AppEvent {
}
