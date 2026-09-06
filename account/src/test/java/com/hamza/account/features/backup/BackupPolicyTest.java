package com.hamza.account.features.backup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ownership rule, which is four answers and each of them is a shop's backups.
 * <p>
 * Written against {@link BackupPolicy#isOwner} rather than the public method because that
 * one reads the shared settings and the Windows registry: a test that goes through it
 * proves something about the computer it ran on, which is not the property anybody wants
 * checked.
 */
class BackupPolicyTest {

    private static final String THIS_MACHINE = "5a2b0f10-0000-0000-0000-000000000001";
    private static final String OTHER_MACHINE = "5a2b0f10-0000-0000-0000-000000000002";

    @Test
    @DisplayName("the recorded machine takes the backups")
    void ownerRunsThem() {
        assertTrue(BackupPolicy.isOwner(Optional.of(THIS_MACHINE), Optional.of(THIS_MACHINE)));
    }

    @Test
    @DisplayName("every other machine schedules nothing - which is the whole point")
    void othersDoNot() {
        assertFalse(BackupPolicy.isOwner(Optional.of(OTHER_MACHINE), Optional.of(THIS_MACHINE)));
    }

    /**
     * A shop that has never chosen must not end up with no backups at all. Duplication is
     * a folder full of copies; the other answer is no copy anywhere.
     */
    @Test
    @DisplayName("with nobody recorded, a machine still backs up")
    void unownedFallsBackToEverybody() {
        assertTrue(BackupPolicy.isOwner(Optional.empty(), Optional.of(THIS_MACHINE)));
        assertTrue(BackupPolicy.isOwner(Optional.empty(), Optional.empty()));
    }

    @Test
    @DisplayName("a machine that cannot identify itself is not the owner of anything")
    void unknownMachineIsNotTheOwner() {
        assertFalse(BackupPolicy.isOwner(Optional.of(THIS_MACHINE), Optional.empty()));
    }
}
