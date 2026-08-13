package com.hamza.account.features.rbac;

import com.hamza.account.controller.others.ServiceRegistry;
import com.hamza.account.model.domain.Users;

/** Access to the user held by the process-wide authorization session. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Users get() {
        Users user = getOrNull();
        if (user == null) throw new IllegalStateException("No user is signed in");
        return user;
    }

    public static Users getOrNull() {
        UserSessionContext session = ServiceRegistry.get(UserSessionContext.class);
        return session == null ? null : session.currentUser();
    }
}
