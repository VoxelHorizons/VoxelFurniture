package org.voxelhorizons.furniture.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.voxelhorizons.furniture.model.FurnitureInstance;

public final class FurnitureBreakEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final FurnitureInstance instance;
    private boolean cancelled;

    public FurnitureBreakEvent(Player player, FurnitureInstance instance) {
        this.player = player;
        this.instance = instance;
    }

    public Player getPlayer() { return player; }
    public FurnitureInstance getInstance() { return instance; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
