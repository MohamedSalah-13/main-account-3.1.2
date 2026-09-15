package com.hamza.account.features.users;

import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.model.dao.UsersDao;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.error.UserValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The refusals and the issue path, against a mocked DAO. The success path needs a transaction
 * and therefore MySQL, and has no test here - see docs/users-and-recovery-plan.md §9.
 */
class SupportRecoveryServiceTest {

    private final DaoFactory daoFactory = mock(DaoFactory.class);
    private final UsersDao usersDao = mock(UsersDao.class);
    private final SupportRecoveryService service = new SupportRecoveryService(daoFactory);

    @BeforeEach
    void wire() throws DaoException {
        when(daoFactory.usersDao()).thenReturn(usersDao);
        when(usersDao.countRecentRecoveryFailures(anyInt())).thenReturn(0);
    }

    /**
     * The defect this pins: the window used to show Java's clock while the row took MySQL's
     * DEFAULT CURRENT_TIMESTAMP, and redeem rebuilds the signed text from the row. One second
     * between the two - an insert crossing a second, or a till a second off the server - and
     * every correctly signed response was refused. What is shown is now what is stored.
     */
    @Test
    void theChallengeShownIsTheRowTheDatabaseStoredWithItsOwnClock() throws Exception {
        LocalDateTime serverTime = LocalDateTime.of(2020, 1, 1, 8, 0, 1);
        when(usersDao.findRedeemableRecoveryChallenge(anyString(), eq(SupportRecoveryChallenge.VALID_FOR_MINUTES)))
                .thenAnswer(call -> new SupportRecoveryChallenge(call.getArgument(0), "SERVER-SIDE", serverTime));

        SupportRecoveryChallenge issued = service.issueChallenge();

        verify(usersDao).insertRecoveryChallenge(eq(issued.nonce()), anyString());
        assertEquals(serverTime, issued.issuedAt());
        assertEquals("SERVER-SIDE", issued.machineId());
    }

    @Test
    void aChallengeThatCannotBeReadBackIsNotShown() throws Exception {
        when(usersDao.findRedeemableRecoveryChallenge(anyString(), anyInt())).thenReturn(null);

        assertThrows(DaoException.class, service::issueChallenge);
    }

    @Test
    void aMalformedOrUnsignedResponseIsRefusedAndRecordedWithoutTouchingTheAdministrator() throws Exception {
        SupportRecoveryChallenge row = new SupportRecoveryChallenge("A1B2C3D4E5F60718", "PC-01", LocalDateTime.now().withNano(0));
        String unsigned = SupportRecoveryResponse.envelope(row.signedText(), new byte[]{1, 2, 3});

        for (String response : new String[]{"", "not a response", unsigned}) {
            assertThrows(UserValidationException.class, () -> service.redeem(response, "a-new-password"));
        }

        verify(usersDao, org.mockito.Mockito.times(3)).insertRecoveryAttempt(anyString(), eq("FAILED"));
        verify(usersDao, never()).updateAdministratorPassword(anyString());
        verify(usersDao, never()).redeemRecoveryChallenge(anyString());
    }

    @Test
    void recoveryClosesAfterTheLimitAndSaysSoBeforeReadingAnything() throws Exception {
        when(usersDao.countRecentRecoveryFailures(SupportRecoveryService.FAILURE_WINDOW_MINUTES))
                .thenReturn(SupportRecoveryService.MAX_FAILURES);

        assertThrows(UserValidationException.class, service::issueChallenge);
        assertThrows(UserValidationException.class, () -> service.redeem("x.y", "a-new-password"));

        verify(usersDao, org.mockito.Mockito.times(2)).insertRecoveryAttempt(anyString(), eq("BLOCKED"));
        verify(usersDao, never()).insertRecoveryChallenge(anyString(), anyString());
        verify(usersDao, never()).updateAdministratorPassword(anyString());
    }
}
