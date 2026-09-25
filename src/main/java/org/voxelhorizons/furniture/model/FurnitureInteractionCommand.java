package org.voxelhorizons.furniture.model;

public final class FurnitureInteractionCommand {
    public enum Executor {
        PLAYER,
        CONSOLE;

        public static Executor parse(Object raw) {
            if (raw == null) return PLAYER;
            try {
                return valueOf(String.valueOf(raw).trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("executor must be PLAYER or CONSOLE");
            }
        }
    }

    private final String command;
    private final Executor executor;

    public FurnitureInteractionCommand(String command, Executor executor) {
        this.command = command;
        this.executor = executor;
    }

    public String command() { return command; }
    public Executor executor() { return executor; }
}
