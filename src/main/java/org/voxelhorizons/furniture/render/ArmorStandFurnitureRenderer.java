package org.voxelhorizons.furniture.render;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureRendererType;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class ArmorStandFurnitureRenderer implements FurnitureRenderer {
    @Override public FurnitureRendererType type() { return FurnitureRendererType.ARMOR_STAND; }
    @Override public boolean supported() { return true; }

    @Override
    public List<UUID> spawn(Location location, float yaw, ItemStack modelItem, FurnitureDefinition definition) {
        Location renderLocation = FurnitureRenderTransform.applyLocalOffset(
                location, yaw, definition.offsetX(), definition.offsetY() - 1.45D, definition.offsetZ());
        ArmorStand stand = (ArmorStand) renderLocation.getWorld().spawnEntity(renderLocation, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setCanPickupItems(false);
        stand.setRemoveWhenFarAway(false);
        stand.setHelmet(modelItem.clone());
        stand.addScoreboardTag("voxelfurniture");
        return Collections.singletonList(stand.getUniqueId());
    }

    @Override public void remove(List<UUID> entityIds) { EntitySupport.remove(entityIds); }
}
