package org.voxelhorizons.furniture.render;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureRendererType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DisplayFurnitureRenderer implements FurnitureRenderer {
    @Override public FurnitureRendererType type() { return FurnitureRendererType.DISPLAY; }

    @Override
    public boolean supported() {
        try {
            EntityType.valueOf("ITEM_DISPLAY");
            EntityType.valueOf("INTERACTION");
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    @Override
    public List<UUID> spawn(Location location, float yaw, ItemStack modelItem, FurnitureDefinition definition) {
        if (!supported()) throw new IllegalStateException("Display entities are unavailable on this server");
        List<UUID> entities = new ArrayList<UUID>(2);
        Location renderLocation = FurnitureRenderTransform.applyLocalOffset(
                location, yaw, definition.offsetX(), definition.offsetY(), definition.offsetZ());
        Entity display = renderLocation.getWorld().spawnEntity(renderLocation, EntityType.valueOf("ITEM_DISPLAY"));
        entities.add(display.getUniqueId());
        try {
            Method setItem = display.getClass().getMethod("setItemStack", ItemStack.class);
            setItem.invoke(display, modelItem.clone());
            applyDisplayTransform(display);
            applyScale(display, definition.scaleX(), definition.scaleY(), definition.scaleZ());
            applyViewDistance(display, definition.viewDistance());

            Entity interaction = location.getWorld().spawnEntity(location, EntityType.valueOf("INTERACTION"));
            entities.add(interaction.getUniqueId());
            interaction.teleport(location.clone().add(0.0D, definition.height() / 2.0D, 0.0D));
            invoke(interaction, "setInteractionWidth", Float.TYPE, definition.width());
            invoke(interaction, "setInteractionHeight", Float.TYPE, definition.height());
            invoke(interaction, "setResponsive", Boolean.TYPE, true);
            display.addScoreboardTag("voxelfurniture");
            interaction.addScoreboardTag("voxelfurniture");
            return entities;
        } catch (ReflectiveOperationException exception) {
            EntitySupport.remove(entities);
            throw new IllegalStateException("Unable to initialize display furniture", exception);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyDisplayTransform(Entity display) throws ReflectiveOperationException {
        Class<?> transformType = Class.forName("org.bukkit.entity.ItemDisplay$ItemDisplayTransform");
        Object fixed = Enum.valueOf((Class<? extends Enum>) transformType.asSubclass(Enum.class), "FIXED");
        display.getClass().getMethod("setItemDisplayTransform", transformType).invoke(display, fixed);
    }

    private static void applyScale(Entity display, float x, float y, float z) {
        if (x == 1.0F && y == 1.0F && z == 1.0F) return;
        try {
            Class<?> matrixType = Class.forName("org.joml.Matrix4f");
            Object matrix = matrixType.getConstructor().newInstance();
            matrixType.getMethod("scale", Float.TYPE, Float.TYPE, Float.TYPE).invoke(matrix, x, y, z);
            display.getClass().getMethod("setTransformationMatrix", matrixType).invoke(display, matrix);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to apply furniture display scale", exception);
        }
    }

    private static void applyViewDistance(Entity display, float blocks) throws ReflectiveOperationException {
        // Display#setViewRange uses Minecraft's native 64-block multiplier.
        float nativeRange = blocks / 64.0F;
        display.getClass().getMethod("setViewRange", Float.TYPE).invoke(display, nativeRange);
    }

    private static void invoke(Object target, String name, Class<?> parameter, Object value)
            throws ReflectiveOperationException {
        target.getClass().getMethod(name, parameter).invoke(target, value);
    }

    @Override public void remove(List<UUID> entityIds) { EntitySupport.remove(entityIds); }
}
