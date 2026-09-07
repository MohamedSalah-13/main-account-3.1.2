package com.hamza.account.features.users;

import com.hamza.account.config.MachineId;
import com.hamza.account.model.dao.DaoFactory;
import com.hamza.account.security.PasswordHasher;
import com.hamza.account.security.ReleaseSigningKey;
import com.hamza.controlsfx.database.DaoException;
import com.hamza.controlsfx.database.TransactionTemplate;
import com.hamza.controlsfx.error.UserValidationException;
import com.hamza.controlsfx.language.LanguageManager;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

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
     */
    public SupportRecoveryChallenge issueChallenge() throws DaoException {
        refuseWhileBlocked();
        SupportRecoveryChallenge challenge =
                SupportRecoveryChallenge.issue(MachineId.displayName(), LocalDateTime.now());
        daoFactory.usersDao().insertRecoveryChallenge(challenge.nonce(), challenge.machineId());
        return challenge;
    }

    /**
     * Verifies a signed response against the challenge it names and, if it holds, resets the
     * administrator.
     *
     * @param signedResponse {@code BASE64(payload).BASE64(signature)} - the same shape as
     *                       {@code license.dat}, so support signs it with the procedure it
     *                       already follows.
     */
    public void redeem(String signedResponse, String newPassword) throws DaoException {
        String machine = MachineId.displayName();
        refuseWhileBlocked();

        String payload = payloadOf(signedResponse, machine);
        SupportRecoveryChallenge challenge = daoFactory.usersDao()
                .findRedeemableRecoveryChallenge(nonceOf(payload, machine),
                        SupportRecoveryChallenge.VALID_FOR_MINUTES);
        if (challenge == null || !challenge.signedText().equals(payload)) {
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
        TransactionTemplate.execute(() -> {
            if (daoFactory.usersDao().redeemRecoveryChallenge(challenge.nonce()) != 1) {
                // Someone answered the same challenge first. Not an error the operator
                // caused, and not one to leave half-applied.
                throw new DaoException("Recovery challenge " + challenge.nonce() + " was already redeemed");
            }
            if (daoFactory.usersDao().updateAdministratorPassword(PasswordHasher.hash(newPassword)) != 1) {
                throw new DaoException("Administrator row 1 was not updated by support recovery");
            }
            return daoFactory.usersDao().insertRecoveryAttempt(machine, SUCCEEDED);
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

    /** The payload, only once this key's signature over it has been checked. */
    private String payloadOf(String signedResponse, String machine) throws DaoException {
        String response = signedResponse == null ? "" : signedResponse.trim().replaceAll("\\s+", "");
        int dot = response.indexOf('.');
        if (dot <= 0 || dot == response.length() - 1) throw refuse(machine, "support.recovery.error.response");
        try {
            Base64.Decoder decoder = Base64.getDecoder();
            String payload = new String(decoder.decode(response.substring(0, dot)), StandardCharsets.UTF_8);
            byte[] signature = decoder.decode(response.substring(dot + 1));
            if (!ReleaseSigningKey.verifies(payload, signature)) {
                throw refuse(machine, "support.recovery.error.response");
            }
            return payload;
        } catch (IllegalArgumentException malformed) {
            throw refuse(machine, "support.recovery.error.response");
        }
    }

    private String nonceOf(String payload, String machine) throws DaoException {
        String[] parts = payload.split("\\|");
        if (parts.length != 4 || !SupportRecoveryChallenge.TAG.equals(parts[0])) {
            throw refuse(machine, "support.recovery.error.response");
        }
        return parts[2];
    }

    /** Records the refusal before reporting it, so a run of them is visible afterwards. */
    private UserValidationException refuse(String machine, String messageKey) throws DaoException {
        daoFactory.usersDao().insertRecoveryAttempt(machine, FAILED);
        return new UserValidationException(LanguageManager.getInstance().getString(messageKey));
    }
}
