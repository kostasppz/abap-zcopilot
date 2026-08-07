package com.abapguardian.eclipse.ui;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Shared, source-free UI status for the Copilot and background analysis jobs. */
public final class GuardianUiState {

    public enum Kind { READY, ANALYZING, CHATTING, SUCCESS, OFFLINE, ERROR }

    public record State(Kind kind, String message) {
    }

    private static final CopyOnWriteArrayList<Consumer<State>> LISTENERS =
            new CopyOnWriteArrayList<>();
    private static volatile State current = new State(Kind.READY, "Ready");

    private GuardianUiState() {
    }

    public static State get() {
        return current;
    }

    public static void set(Kind kind, String message) {
        current = new State(kind, message);
        for (Consumer<State> listener : LISTENERS) {
            listener.accept(current);
        }
    }

    public static void addListener(Consumer<State> listener) {
        LISTENERS.add(listener);
        listener.accept(current);
    }

    public static void removeListener(Consumer<State> listener) {
        LISTENERS.remove(listener);
    }
}
