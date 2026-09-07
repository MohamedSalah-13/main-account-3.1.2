package com.hamza.account.features.audit;

import com.hamza.account.features.rbac.UserSessionContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Stamps every borrowed pooled connection with the actor and workstation that own
 * statements executed through it. MySQL session variables belong to a connection,
 * so this must run on every borrow, not only at sign-in.
 */
public final class AuditSessionInitializer {

    private static final String SET_CONTEXT = """
            SET @app_user_id = ?,
                @app_actor_name = ?,
                @app_machine_id = ?,
                @app_machine_name = ?,
                @app_audit_source = ?,
                @app_audit_delete_reason = NULL,
                @app_bulk_wipe = NULL
            """;

    private final UserSessionContext session;
    private final String machineId;
    private final String machineName;

    public AuditSessionInitializer(UserSessionContext session, String machineId, String machineName) {
        this.session = session;
        this.machineId = blankToNull(machineId);
        this.machineName = blankToNull(machineName);
    }

    public void initialize(Connection connection) throws SQLException {
        boolean signedIn = session != null && session.isSignedIn();
        try (PreparedStatement statement = connection.prepareStatement(SET_CONTEXT)) {
            statement.setObject(1, signedIn ? session.currentUserId() : null);
            statement.setString(2, signedIn ? session.currentUsername() : null);
            statement.setString(3, machineId);
            statement.setString(4, machineName);
            statement.setString(5, signedIn ? "APP" : "SYSTEM");
            statement.executeUpdate();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
