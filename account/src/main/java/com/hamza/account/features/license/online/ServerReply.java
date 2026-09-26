package com.hamza.account.features.license.online;

import java.time.LocalDate;

/**
 * What one request to the licence server came to. Four shapes, and a caller answers each:
 * a licence file, "nothing changed", a refusal by code, or no answer at all.
 */
public sealed interface ServerReply {

    /**
     * A licence file, whole - what {@code activate} and a changed {@code refresh} return. It has not been
     * checked yet: nothing received is written before this build has judged it itself.
     *
     * @param text     the file, as the server signed and encoded it
     * @param activation what the server said about the licence with it; null for a refresh, which says nothing else
     */
    record Licence(String text, Activation activation) implements ServerReply {
    }

    /** {@code refresh} answered 204: the file this machine holds is already the current one. */
    record Unchanged() implements ServerReply {
    }

    /**
     * The server answered, and said no.
     *
     * @param seatsUsed  with {@link ServerRefusal#SEATS_FULL}, otherwise null
     * @param seatsTotal with {@link ServerRefusal#SEATS_FULL}, otherwise null
     * @param reference  with {@link ServerRefusal#SERVER_ERROR}, the line support looks for; otherwise null
     */
    record Refused(ServerRefusal refusal, Integer seatsUsed, Integer seatsTotal, String reference) implements ServerReply {

        static Refused of(ServerRefusal refusal) {
            return new Refused(refusal, null, null, null);
        }
    }

    /**
     * No answer that means anything: no connection, a name that does not resolve, a timeout, or the
     * gateway answering in the server's place because the server itself is down.
     *
     * @param why for the log - never shown
     */
    record Unreachable(String why) implements ServerReply {
    }

    /**
     * The licence an activation was for, as the server described it beside the file - what the About
     * window tells the person who typed the code.
     *
     * @param expires null for a perpetual licence
     */
    record Activation(String customerId, String customerName, String edition, int seatsUsed, int seatsTotal,
                      LocalDate updatesUntil, LocalDate expires) {
    }
}
