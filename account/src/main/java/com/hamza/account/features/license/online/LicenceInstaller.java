package com.hamza.account.features.license.online;

import java.io.IOException;
import java.util.Optional;

/**
 * Puts a licence file where the start-up reads it - <b>only when it licenses this machine</b>, and whole or
 * not at all. The one real implementation is the About window's own install, handed in from outside this
 * package: a file that came over the network passes the very check a file somebody chose passes, and this
 * package never judges or writes a licence on its own authority.
 */
@FunctionalInterface
public interface LicenceInstaller {

    /**
     * @return the reason it was not installed, for the log; empty when it was
     * @throws IOException it licensed this machine and could not be written
     */
    Optional<String> install(byte[] licence) throws IOException;
}
