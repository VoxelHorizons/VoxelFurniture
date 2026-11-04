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
        Location renderLocation = FurnitureRenderTransform.modelLocation(
                location, yaw, definition, FurnitureRendererType.ARMOR_STAND);
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

    @Override
    public boolean update(List<UUID> entityIds, Location location, float yaw,
                          ItemStack modelItem, FurnitureDefinition definition) {
        if (entityIds == null || entityIds.size() != 1) return false;
        org.bukkit.entity.Entity entity = EntitySupport.find(entityIds.get(0));
        if (!(entity instanceof ArmorStand) || !entity.isValid()
                || !entity.getScoreboardTags().contains("voxelfurniture")) return false;
        ArmorStand stand = (ArmorStand) entity;
        stand.setHelmet(modelItem.clone());
        Location target = FurnitureRenderTransform.modelLocation(
                location, yaw, definition, FurnitureRendererType.ARMOR_STAND);
        Location actual = stand.getLocation();
        if (actual.getWorld() != target.getWorld()
                || actual.distanceSquared(target) > 0.000001D
                || Math.abs(actual.getYaw() - target.getYaw()) > 0.001F) {
            stand.teleport(target);
        }
        return true;
    }

    @Override public void remove(List<UUID> entityIds) { EntitySupport.remove(entityIds); }
}
