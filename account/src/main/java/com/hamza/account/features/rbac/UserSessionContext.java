package com.hamza.account.features.rbac;

import com.hamza.account.authorization.PermissionKey;
import com.hamza.account.model.domain.Users;

import java.util.Collection;
import java.util.Set;

/**
 * Process session used by authorization checks. The snapshot is immutable and
 * replaced atomically on login or after the current user's roles are changed.
 */
public final class UserSessionContext {

    private volatile Snapshot snapshot = Snapshot.signedOut();

    public void signIn(Users user, Collection<PermissionKey> permissions) {
        if (user == null || user.getId() <= 0) throw new IllegalArgumentException("Invalid user");
        snapshot = new Snapshot(user,
                permissions == null ? Set.of() : Set.copyOf(permissions));
    }

    /** Compatibility overload for service tests and callers that do not load a full user row. */
    public void signIn(int userId, String username, Collection<PermissionKey> permissions) {
        signIn(new Users(userId, username), permissions);
    }

    public void signOut() {
        snapshot = Snapshot.signedOut();
    }

    public boolean isSignedIn() {
        return snapshot.user() != null;
    }

    public int currentUserId() {
        return snapshot.user() == null ? 0 : snapshot.user().getId();
    }

    public String currentUsername() {
        return snapshot.user() == null ? "" : snapshot.user().getUsername();
    }

    public Users currentUser() {
        return snapshot.user();
    }

    public void updateCurrentUser(Users user) {
        if (!isSignedIn() || user == null || user.getId() != currentUserId()) {
            throw new IllegalArgumentException("User does not match the current session");
        }
        snapshot = new Snapshot(user, snapshot.permissions());
    }

    public boolean isSystemAdministrator() {
        return currentUserId() == 1;
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

    private record Snapshot(Users user, Set<PermissionKey> permissions) {
        private static Snapshot signedOut() {
            return new Snapshot(null, Set.of());
        }
    }
}
