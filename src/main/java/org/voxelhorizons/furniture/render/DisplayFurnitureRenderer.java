package org.voxelhorizons.furniture.render;

import org.voxelhorizons.VoxelCore;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureDisplayPartDefinition;
import org.voxelhorizons.furniture.model.FurnitureHighlightDefinition;
import org.voxelhorizons.furniture.model.FurnitureRendererType;
import org.voxelhorizons.pack.UiSpacingGlyphs;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DisplayFurnitureRenderer implements FurnitureRenderer {
    private static final Pattern OFFSET_PLACEHOLDER = Pattern.compile(":offset_(-?\\d+):");
    private final VoxelCore core;

    public DisplayFurnitureRenderer(VoxelCore core) {
        this.core = core;
    }

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
        List<UUID> entities = new ArrayList<UUID>(2 + definition.displayParts().size());
        Location renderLocation = FurnitureRenderTransform.modelLocation(
                location, yaw, definition, FurnitureRendererType.DISPLAY);
        Entity display = renderLocation.getWorld().spawnEntity(renderLocation, EntityType.valueOf("ITEM_DISPLAY"));
        entities.add(display.getUniqueId());
        try {
            Method setItem = display.getClass().getMethod("setItemStack", ItemStack.class);
            setItem.invoke(display, modelItem.clone());
            applyDisplayTransform(display);
            applyScale(display, definition.scaleX(), definition.scaleY(), definition.scaleZ());
            applyViewDistance(display, definition.viewDistance());
            applyPermanentGlow(display, definition.highlight());
            invokeOptional(display, "setTeleportDuration", Integer.TYPE, Integer.valueOf(1));

            Location hitboxLocation = FurnitureRenderTransform.applyLocalOffset(
                    location, yaw, definition.hitboxOffsetX(),
                    definition.hitboxOffsetY() + definition.height() / 2.0D,
                    definition.hitboxOffsetZ());
            Entity interaction = location.getWorld().spawnEntity(hitboxLocation, EntityType.valueOf("INTERACTION"));
            entities.add(interaction.getUniqueId());
            interaction.teleport(hitboxLocation);
            invoke(interaction, "setInteractionWidth", Float.TYPE, definition.width());
            invoke(interaction, "setInteractionHeight", Float.TYPE, definition.height());
            invoke(interaction, "setResponsive", Boolean.TYPE, true);
            display.addScoreboardTag("voxelfurniture");
            display.addScoreboardTag("voxelfurniture-main");
            interaction.addScoreboardTag("voxelfurniture");
            interaction.addScoreboardTag("voxelfurniture-interaction");

            for (FurnitureDisplayPartDefinition part : definition.displayParts()) {
                Location partLocation = FurnitureRenderTransform.applyLocalOffset(
                        location, yaw, part.offsetX(), part.offsetY(), part.offsetZ());
                Entity extra;
                if (part.type() == FurnitureDisplayPartDefinition.Type.TEXT) {
                    extra = location.getWorld().spawnEntity(partLocation, EntityType.valueOf("TEXT_DISPLAY"));
                    invoke(extra, "setText", String.class, resolveDisplayText(part.text()));
                    invokeOptional(extra, "setDefaultBackground", Boolean.TYPE, Boolean.FALSE);
                    try {
                        Class<?> colorType = Class.forName("org.bukkit.Color");
                        Object transparent = colorType.getMethod("fromARGB", Integer.TYPE).invoke(null, Integer.valueOf(0));
                        invokeOptional(extra, "setBackgroundColor", colorType, transparent);
                    } catch (ClassNotFoundException ignored) {
                        // Older APIs do not expose Bukkit Color on display entities.
                    }
                } else {
                    extra = location.getWorld().spawnEntity(partLocation, EntityType.valueOf("ITEM_DISPLAY"));
                    ItemStack partItem = core.getItemManager().createRenderItem(part.modelItem());
                    invoke(extra, "setItemStack", ItemStack.class, partItem);
                    applyDisplayTransform(extra);
                }
                entities.add(extra.getUniqueId());
                applyBillboard(extra, part.billboard());
                applyScale(extra, part.scale(), part.scale(), part.scale());
                applyViewDistance(extra, definition.viewDistance());
                if (definition.highlight() != null && definition.highlight().includeParts()) {
                    applyPermanentGlow(extra, definition.highlight());
                }
                invokeOptional(extra, "setTeleportDuration", Integer.TYPE, Integer.valueOf(1));
                extra.addScoreboardTag("voxelfurniture");
                extra.addScoreboardTag("voxelfurniture-part-" + part.id());
            }
            return entities;
        } catch (ReflectiveOperationException exception) {
            EntitySupport.remove(entities);
            throw new IllegalStateException("Unable to initialize display furniture", exception);
        }
    }

    /**
     * Animate by updating the ItemDisplay's item stack rather than replacing it.
     * Keep the interaction hitbox and all display part UUIDs stable.
     * Structural changes (parts added/removed or part type changed) still require spawn.
     */
    @Override
    public boolean update(List<UUID> entityIds, Location location, float yaw,
                          ItemStack modelItem, FurnitureDefinition definition) {
        if (entityIds == null || entityIds.size() != 2 + definition.displayParts().size()) return false;
        Entity display = EntitySupport.find(entityIds.get(0));
        Entity interaction = EntitySupport.find(entityIds.get(1));
        if (!isExpected(display, "ITEM_DISPLAY", "voxelfurniture-main")
                || !isExpected(interaction, "INTERACTION", "voxelfurniture-interaction")) return false;

        List<Entity> parts = new ArrayList<Entity>();
        for (int i = 0; i < definition.displayParts().size(); i++) {
            FurnitureDisplayPartDefinition part = definition.displayParts().get(i);
            Entity entity = EntitySupport.find(entityIds.get(i + 2));
            if (!isExpected(entity, part.type() == FurnitureDisplayPartDefinition.Type.TEXT
                    ? "TEXT_DISPLAY" : "ITEM_DISPLAY", "voxelfurniture-part-" + part.id())) return false;
            parts.add(entity);
        }

        try {
            // Send a model/item metadata update to the existing ItemDisplay. This
            // is the common animation transition and causes no entity removal.
            invoke(display, "setItemStack", ItemStack.class, modelItem.clone());
            applyDisplayTransform(display);
            applyScale(display, definition.scaleX(), definition.scaleY(), definition.scaleZ());
            applyViewDistance(display, definition.viewDistance());
            applyPermanentGlow(display, definition.highlight());
            moveIfNeeded(display, FurnitureRenderTransform.modelLocation(
                    location, yaw, definition, FurnitureRendererType.DISPLAY));

            Location hitbox = FurnitureRenderTransform.applyLocalOffset(
                    location, yaw, definition.hitboxOffsetX(),
                    definition.hitboxOffsetY() + definition.height() / 2.0D,
                    definition.hitboxOffsetZ());
            moveIfNeeded(interaction, hitbox);
            invoke(interaction, "setInteractionWidth", Float.TYPE, definition.width());
            invoke(interaction, "setInteractionHeight", Float.TYPE, definition.height());

            for (int i = 0; i < parts.size(); i++) {
                Entity entity = parts.get(i);
                FurnitureDisplayPartDefinition part = definition.displayParts().get(i);
                if (part.type() == FurnitureDisplayPartDefinition.Type.TEXT) {
                    invoke(entity, "setText", String.class, resolveDisplayText(part.text()));
                } else {
                    ItemStack partItem = core.getItemManager().createRenderItem(part.modelItem());
                    invoke(entity, "setItemStack", ItemStack.class, partItem);
                    applyDisplayTransform(entity);
                }
                applyBillboard(entity, part.billboard());
                applyScale(entity, part.scale(), part.scale(), part.scale());
                applyViewDistance(entity, definition.viewDistance());
                if (definition.highlight() != null && definition.highlight().includeParts()) {
                    applyPermanentGlow(entity, definition.highlight());
                } else {
                    entity.setGlowing(false);
                }
                moveIfNeeded(entity, FurnitureRenderTransform.applyLocalOffset(
                        location, yaw, part.offsetX(), part.offsetY(), part.offsetZ()));
            }
            return true;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to update display furniture in place", exception);
        }
    }

    private static boolean isExpected(Entity entity, String type, String tag) {
        return entity != null && entity.isValid() && type.equals(entity.getType().name())
                && entity.getScoreboardTags().contains("voxelfurniture")
                && entity.getScoreboardTags().contains(tag);
    }

    private static void moveIfNeeded(Entity entity, Location target) {
        Location current = entity.getLocation();
        if (current.getWorld() == target.getWorld()
                && current.distanceSquared(target) < 0.000001D
                && Math.abs(current.getYaw() - target.getYaw()) < 0.001F
                && Math.abs(current.getPitch() - target.getPitch()) < 0.001F) return;
        entity.teleport(target);
    }

    private static void applyPermanentGlow(Entity entity, FurnitureHighlightDefinition highlight)
            throws ReflectiveOperationException {
        if (highlight == null || highlight.hover() || highlight.lineOfSight()) {
            entity.setGlowing(false);
            return;
        }
        entity.setGlowing(true);
        Class<?> colorType = Class.forName("org.bukkit.Color");
        Object color = colorType.getMethod("fromRGB", Integer.TYPE).invoke(null, highlight.rgb());
        entity.getClass().getMethod("setGlowColorOverride", colorType).invoke(entity, color);
    }

    private String resolveDisplayText(String input) {
        if (input == null || input.indexOf(':') < 0) return input;
        Matcher matcher = OFFSET_PLACEHOLDER.matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(0);
            try {
                int offset = Integer.parseInt(matcher.group(1));
                if (offset < -UiSpacingGlyphs.maxOffset() || offset > UiSpacingGlyphs.maxOffset()) {
                    throw new IllegalArgumentException("Offset is outside the public placeholder range");
                }
                replacement = UiSpacingGlyphs.charactersForOffset(offset);
            } catch (IllegalArgumentException ignored) {
                // Preserve malformed/out-of-range placeholders so content mistakes remain visible.
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return core.getPackManager().uiGlyphs(false)
                .resolveAliases(output.toString(), false, true, true);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyBillboard(Entity display, String mode) throws ReflectiveOperationException {
        Class<?> billboardType = Class.forName("org.bukkit.entity.Display$Billboard");
        Object value = Enum.valueOf((Class<? extends Enum>) billboardType.asSubclass(Enum.class), mode);
        display.getClass().getMethod("setBillboard", billboardType).invoke(display, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyDisplayTransform(Entity display) throws ReflectiveOperationException {
        Class<?> transformType = Class.forName("org.bukkit.entity.ItemDisplay$ItemDisplayTransform");
        Object fixed = Enum.valueOf((Class<? extends Enum>) transformType.asSubclass(Enum.class), "FIXED");
        display.getClass().getMethod("setItemDisplayTransform", transformType).invoke(display, fixed);
    }

    private static void applyScale(Entity display, float x, float y, float z) {
        // Even the identity matrix must be written: a prior animation variant
        // may have applied non-unit scaling on this same display entity.
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

    private static void invokeOptional(Object target, String name, Class<?> parameter, Object value)
            throws ReflectiveOperationException {
        try {
            target.getClass().getMethod(name, parameter).invoke(target, value);
        } catch (NoSuchMethodException ignored) {
            // Older display APIs do not expose teleport interpolation.
        }
    }

    private static void invoke(Object target, String name, Class<?> parameter, Object value)
            throws ReflectiveOperationException {
        target.getClass().getMethod(name, parameter).invoke(target, value);
    }

    @Override public void remove(List<UUID> entityIds) { EntitySupport.remove(entityIds); }
}
