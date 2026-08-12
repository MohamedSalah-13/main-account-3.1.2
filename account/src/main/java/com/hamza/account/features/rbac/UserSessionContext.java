package com.hamza.account.features.rbac;

import com.hamza.account.authorization.PermissionKey;

import java.util.Collection;
import java.util.Set;

/**
 * Process session used by authorization checks. The snapshot is immutable and
 * replaced atomically on login or after the current user's roles are changed.
 */
public final class UserSessionContext {

    private volatile Snapshot snapshot = Snapshot.signedOut();

    public void signIn(int userId, String username, Collection<PermissionKey> permissions) {
        if (userId <= 0) throw new IllegalArgumentException("Invalid user id");
        snapshot = new Snapshot(userId, username == null ? "" : username,
                permissions == null ? Set.of() : Set.copyOf(permissions));
    }

    public void signOut() {
        snapshot = Snapshot.signedOut();
    }

    public boolean isSignedIn() {
        return snapshot.userId() > 0;
    }

    public int currentUserId() {
        return snapshot.userId();
    }

    public String currentUsername() {
        return snapshot.username();
    }

    public boolean isSystemAdministrator() {
        return snapshot.userId() == 1;
    }

    public boolean hasPermission(PermissionKey permission) {
        if (permission == null || permission.isDenyMarker()) return false;
        if (permission.isPublicMarker()) return true;
        return isSystemAdministrator()
                || snapshot.permissions().contains(permission);
    }

    public Set<PermissionKey> permissions() {
        return snapshot.permissions();
    }

    private record Snapshot(int userId, String username, Set<PermissionKey> permissions) {
        private static Snapshot signedOut() {
            return new Snapshot(0, "", Set.of());
        }
    }
}
