package org.voxelhorizons.furniture.inventory;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Identifies a live furniture inventory without relying on titles or slots. */
public final class FurnitureInventoryHolder implements InventoryHolder {
    private final UUID instanceId;
    private Inventory inventory;

    public FurnitureInventoryHolder(UUID instanceId) {
        this.instanceId = instanceId;
    }

    public UUID instanceId() {
        return instanceId;
    }

    public void bind(Inventory inventory) {
        if (this.inventory != null) throw new IllegalStateException("Inventory is already bound");
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
