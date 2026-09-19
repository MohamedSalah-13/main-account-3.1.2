package com.hamza.account.features.dbsetup;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.OptionalInt;
import java.util.function.IntPredicate;

/**
 * Finds the port the bundled server will listen on.
 * <p>
 * 3306 is deliberately not where the search starts: it is taken on half the machines an
 * installer meets - an old MySQL, a XAMPP somebody tried once - and a collision there is the
 * commonest way this kind of install fails. The search is a short fixed range rather than "any
 * free port", so a technician reading {@code my.ini} a year later finds a number he expects.
 */
public final class PortProbe {

    public static final int FIRST = 3307;
    public static final int LAST = 3316;

    private final IntPredicate free;

    public PortProbe() {
        this(PortProbe::canBind);
    }

    PortProbe(IntPredicate free) {
        this.free = free;
    }

    /** The preferred port when it is free, otherwise the first free one after it, up to {@link #LAST}. */
    public OptionalInt firstFree(int preferred) {
        if (preferred < 1 || preferred > 65535) {
            throw new IllegalArgumentException("not a port: " + preferred);
        }
        int last = Math.max(preferred, LAST);
        for (int port = preferred; port <= last; port++) {
            if (free.test(port)) {
                return OptionalInt.of(port);
            }
        }
        return OptionalInt.empty();
    }

    /** Bound on every interface: a port busy on the LAN address is busy for a server that may listen there. */
    private static boolean canBind(int port) {
        try (ServerSocket socket = new ServerSocket(port, 1, (InetAddress) null)) {
            socket.setReuseAddress(false);
            return true;
        } catch (IOException taken) {
            return false;
        }
    }
}
