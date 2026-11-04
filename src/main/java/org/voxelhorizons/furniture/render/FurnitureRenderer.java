package org.voxelhorizons.furniture.render;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureRendererType;

import java.util.List;
import java.util.UUID;

public interface FurnitureRenderer {
    FurnitureRendererType type();
    boolean supported();
    List<UUID> spawn(Location location, float yaw, ItemStack modelItem, FurnitureDefinition definition);

    /**
     * Update an existing renderer in place, preserving every entity UUID.
     * Return false if the layout/type is incompatible and needs a full rebuild.
     * The standard animation model swap must not despawn the current display.
     */
    default boolean update(List<UUID> entityIds, Location location, float yaw,
                           ItemStack modelItem, FurnitureDefinition definition) {
        return false;
    }

    void remove(List<UUID> entityIds);
}
