package com.hamza.account.features.audit;

import com.hamza.account.features.rbac.UserSessionContext;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditSessionInitializerTest {

    @Test
    void stampsTheSignedInActorAndWorkstationOnEachBorrow() throws Exception {
        UserSessionContext session = new UserSessionContext();
        session.signIn(7, "mohamed", java.util.Set.of());
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement);

        new AuditSessionInitializer(session, "machine-guid", "FRONT-DESK").initialize(connection);

        verify(statement).setObject(1, 7);
        verify(statement).setString(2, "mohamed");
        verify(statement).setString(3, "machine-guid");
        verify(statement).setString(4, "FRONT-DESK");
        verify(statement).setString(5, "APP");
        verify(statement).executeUpdate();
        verify(statement).close();
    }

    @Test
    void marksPreLoginWorkAsSystemRatherThanAdmin() throws Exception {
        UserSessionContext session = new UserSessionContext();
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement);

        new AuditSessionInitializer(session, null, "BACKOFFICE").initialize(connection);

        verify(statement).setObject(1, null);
        verify(statement).setString(2, null);
        verify(statement).setString(5, "SYSTEM");
    }
}
