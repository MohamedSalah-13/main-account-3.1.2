package com.hamza.controlsfx.observer;

/**
 * Something that happened in the application and that another screen may need to
 * hear about - an item was added, an invoice was saved, the signed-in user was
 * renamed.
 * <p>
 * Implementations are records carrying whatever the listeners need, and they are
 * the vocabulary of {@link EventBus}: the event is identified by its type rather
 * than by which field of a shared object it was published on, so the compiler
 * knows what a listener receives.
 */
public interface AppEvent {
}
