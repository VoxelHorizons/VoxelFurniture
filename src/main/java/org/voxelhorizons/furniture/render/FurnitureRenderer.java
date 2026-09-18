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
    void remove(List<UUID> entityIds);
}
