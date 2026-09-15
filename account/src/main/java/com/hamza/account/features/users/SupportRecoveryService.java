package com.hamza.account.features.users;

import com.hamza.account.config.MachineId;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.security.PasswordHasher;
import com.hamza.account.security.ReleaseSigningKey;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

/**
 * Emergency recovery of the administrator account: explicit, audited, and answerable only
 * with a signature this program cannot produce.
 *
 * <p>It never reads or reveals an existing password.
 *
 * <p><b>There is deliberately no {@code AuthorizationGuard.require} here</b>, and it is the
 * one write path in the application without one: it exists for the case where nobody can
 * sign in, so there is no session to ask a permission of. What stands in its place:
 *
 * <ol>
 *   <li>a challenge the machine issues, answerable only by the private key that issues
 *       licences - which this repository has never held;
 *   <li>{@link #MAX_FAILURES} refused responses per {@link #FAILURE_WINDOW_MINUTES};
 *   <li>a row in {@code support_recovery_audit} for every attempt, refused ones included.
 * </ol>
 *
 * <p>It replaced a shared support key whose bcrypt hash was seeded by a migration, so it
 * shipped inside every copy of this repository. The attempt limit did not protect that: an
 * attacker worked on the hash offline - unlimited, unlogged - and arrived with the right key
 * on the first try. The limit here guards something narrower and still worth guarding: a
 * response fished out of someone's messages, replayed against the wrong machine or after its
 * challenge has gone.
 */
public record SupportRecoveryService(DaoFactory daoFactory) {

    /** Refused responses tolerated in {@link #FAILURE_WINDOW_MINUTES} before recovery closes. */
    static final int MAX_FAILURES = 5;
    static final int FAILURE_WINDOW_MINUTES = 15;

    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String FAILED = "FAILED";
    private static final String BLOCKED = "BLOCKED";

    /**
     * Records a fresh challenge and returns it for the operator to send to support.
     *
     * <p>The row is what makes the answer usable once: redeeming marks it, and a response
     * naming a nonce that is missing, already redeemed or older than
     * {@link SupportRecoveryChallenge#VALID_FOR_MINUTES} is refused.
     *
     * <p><b>What is returned is the stored row, read back - not the challenge Java built.</b>
     * The issue time support signs is the time in this row, because {@code redeem} rebuilds
     * the signed text from the row. It used to display {@code LocalDateTime.now()} from this
     * machine while the column took its {@code DEFAULT CURRENT_TIMESTAMP} from the server:
     * a till whose clock was a second away from MySQL's - the ordinary case once the
     * database lives on another computer - or an insert that crossed a second boundary
     * produced a request whose every correctly signed answer was refused. One clock now
     * issues the challenge and the same clock expires it.
     */
    public SupportRecoveryChallenge issueChallenge() throws DaoException {
        refuseWhileBlocked();
        String nonce = SupportRecoveryChallenge.newNonce();
        daoFactory.usersDao().insertRecoveryChallenge(nonce, MachineId.displayName());
        SupportRecoveryChallenge stored = daoFactory.usersDao()
                .findRedeemableRecoveryChallenge(nonce, SupportRecoveryChallenge.VALID_FOR_MINUTES);
        if (stored == null) {
            throw new DaoException("Recovery challenge " + nonce + " was written and could not be read back");
        }
        return stored;
    }

    /**
     * Verifies a signed response against the challenge it names and, if it holds, resets the
     * administrator.
     *
     * @param signedResponse {@code BASE64(payload).BASE64(signature)} - the same shape as
     *                       {@code license.dat}, so support signs it with the procedure it
     *                       already follows.
     * @return the administrator's user name, which whoever forgot the password may well have
     * forgotten too - row 1 is not necessarily still called {@code admin}.
     */
    public String redeem(String signedResponse, String newPassword) throws DaoException {
        String machine = MachineId.displayName();
        refuseWhileBlocked();

        SupportRecoveryResponse response = SupportRecoveryResponse.parse(signedResponse).orElse(null);
        if (response == null || !ReleaseSigningKey.verifies(response.payload(), response.signature())) {
            throw refuse(machine, "support.recovery.error.response");
        }
        String nonce = response.nonce().orElse(null);
        if (nonce == null) throw refuse(machine, "support.recovery.error.response");

        SupportRecoveryChallenge challenge = daoFactory.usersDao()
                .findRedeemableRecoveryChallenge(nonce, SupportRecoveryChallenge.VALID_FOR_MINUTES);
        if (!response.answers(challenge)) {
            // The stored challenge is what the payload is checked against, field by field,
            // rather than the payload being believed about itself. A signature over a
            // machine or a moment this row does not agree with authorises nothing here.
            throw refuse(machine, "support.recovery.error.response");
        }

        // After the response is accepted, so a mistyped password is not charged against the
        // limit - whoever is here has already proved they hold an authorisation.
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 8) {
            throw new UserValidationException(LanguageManager.getInstance().getString("user.password.minimum"));
        }

        // The reset, the spent challenge and the audit row are one fact.
        return TransactionTemplate.execute(() -> {
            if (daoFactory.usersDao().redeemRecoveryChallenge(challenge.nonce()) != 1) {
                // Someone answered the same challenge first - a double press, or a second
                // window. Nothing half-applied, and a sentence the operator can act on rather
                // than a reference code: TransactionTemplate rethrows a DaoException as it is.
                throw new UserValidationException(LanguageManager.getInstance()
                        .getString("support.recovery.error.redeemed"));
            }
            if (daoFactory.usersDao().updateAdministratorPassword(PasswordHasher.hash(newPassword)) != 1) {
                throw new DaoException("Administrator row 1 was not updated by support recovery");
            }
            daoFactory.usersDao().insertRecoveryAttempt(machine, SUCCEEDED);
            return daoFactory.usersDao().administratorUserName();
        });
    }

    /**
     * The window is asked of the stored rows, never of a counter in memory: an attempt costs
     * the price of relaunching the program, and anything this process remembers the next one
     * forgets. {@code recovered_at} and {@code NOW()} are both the machine's local time - see
     * the connectionTimeZone note in CLAUDE.md.
     */
    private void refuseWhileBlocked() throws DaoException {
        if (daoFactory.usersDao().countRecentRecoveryFailures(FAILURE_WINDOW_MINUTES) < MAX_FAILURES) {
            return;
        }
        daoFactory.usersDao().insertRecoveryAttempt(MachineId.displayName(), BLOCKED);
        throw new UserValidationException(LanguageManager.getInstance()
                .getString("support.recovery.error.blocked", FAILURE_WINDOW_MINUTES));
    }

    /** Records the refusal before reporting it, so a run of them is visible afterwards. */
    private UserValidationException refuse(String machine, String messageKey) throws DaoException {
        daoFactory.usersDao().insertRecoveryAttempt(machine, FAILED);
        return new UserValidationException(LanguageManager.getInstance().getString(messageKey));
    }
}
