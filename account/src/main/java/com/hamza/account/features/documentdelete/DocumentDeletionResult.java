package com.hamza.account.features.documentdelete;

/**
 * How many documents were asked to be deleted and how many actually were.
 * <p>
 * They differ when another machine deleted some of them first. The screen used to discard the
 * count and say "deleted" either way - including over a batch of which nothing was left to delete.
 */
public record DocumentDeletionResult(int requested, int deleted) {

    public DocumentDeletionResult {
        if (requested < 0 || deleted < 0 || deleted > requested) {
            throw new IllegalArgumentException("deleted " + deleted + " of " + requested);
        }
    }

    public boolean nothingDeleted() {
        return deleted == 0;
    }

    public boolean allDeleted() {
        return deleted == requested;
    }
}
