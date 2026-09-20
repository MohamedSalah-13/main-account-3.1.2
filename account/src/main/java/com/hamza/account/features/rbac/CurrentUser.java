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

    /**
     * Whether the signed-in user is the recovery administrator - the account {@code V1} seeds, which
     * {@link UserSessionContext#hasPermission} lets past every key.
     * <p>
     * <b>It exists so that "is this the administrator" is asked in one place.</b> Three screens wrote
     * {@code CurrentUser.get().getId() == 1} themselves - the invoice table's column menu, the
     * sidebar's role caption and the help button - which is the numbered-administrator test this
     * whole permission system replaced, copied back in by hand. It is still the same test, and
     * {@code docs/permissions-plan.md} §4.1 is the decision about whether it should be a permission
     * instead; what has changed is that there is now one line to change rather than four.
     * <p>
     * <b>Not for guarding anything.</b> A rule about what a user may do asks
     * {@code AuthorizationGuard}, whatever their id.
     */
    public static boolean isSystemAdministrator() {
        UserSessionContext session = ServiceRegistry.get(UserSessionContext.class);
        return session != null && session.isSystemAdministrator();
    }
}
