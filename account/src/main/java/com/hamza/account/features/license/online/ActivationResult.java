package com.hamza.account.features.license.online;

/**
 * What an activation by code came to, for the About window to say.
 *
 * @param activation with {@link Kind#ACTIVATED}, what the server said about the licence (may be null)
 * @param refused    with {@link Kind#REFUSED}, the server's code and what came with it
 */
public record ActivationResult(Kind kind, ServerReply.Activation activation, ServerReply.Refused refused) {

    public enum Kind {
        /** Licensed: the file was judged by this build and written. */
        ACTIVATED,
        /** The code failed its own check; nothing was sent. */
        CODE_WRITTEN_WRONG,
        /** This computer's {@code MachineGuid} could not be read; nothing was sent. */
        NO_MACHINE_CODE,
        /** The server answered no - {@link #refused()} says why. */
        REFUSED,
        /** No answer: no connection, or the server down. */
        UNREACHABLE,
        /** A file came back and this build did not accept it for this machine; nothing was replaced. */
        NOT_ACCEPTED,
        /** It licensed this machine and could not be written; nothing was replaced. */
        NOT_WRITTEN
    }

    static ActivationResult of(Kind kind) {
        return new ActivationResult(kind, null, null);
    }

    /**
     * What to tell the user. {@link Kind#ACTIVATED} takes the customer, the seats used and total and the
     * last day of updates ({@code %s %d %d %s}); a refusal's arguments are {@link ServerRefusal#messageKey()}'s.
     */
    public String messageKey() {
        return switch (kind) {
            case ACTIVATED -> "license.online.activated";
            case CODE_WRITTEN_WRONG -> "license.online.code.invalid";
            case NO_MACHINE_CODE -> "license.online.machine.unavailable";
            case REFUSED -> refused.refusal().messageKey();
            case UNREACHABLE -> "license.online.unreachable";
            case NOT_ACCEPTED -> "license.online.not.accepted";
            case NOT_WRITTEN -> "license.online.not.written";
        };
    }
}
