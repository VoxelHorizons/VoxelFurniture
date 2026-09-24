package org.voxelhorizons.furniture.model;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.content.ContentID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class FurnitureInstance {
    private final UUID id;
    private final ContentID definitionId;
    private final Location location;
    private final float yaw;
    private final FurnitureRendererType renderer;
    private final List<UUID> entities;
    private final List<FurnitureBlockPosition> blocks;
    private final ContentID renderedModel;
    private final float renderedYaw;
    private final String renderSignature;
    private final List<ItemStack> inventoryContents;
    private final Integer dyeColor;

    public FurnitureInstance(UUID id, ContentID definitionId, Location location, float yaw,
                             FurnitureRendererType renderer, List<UUID> entities,
                             List<FurnitureBlockPosition> blocks) {
        this(id, definitionId, location, yaw, renderer, entities, blocks, null, Float.NaN, null);
    }

    public FurnitureInstance(UUID id, ContentID definitionId, Location location, float yaw,
                             FurnitureRendererType renderer, List<UUID> entities,
                             List<FurnitureBlockPosition> blocks, ContentID renderedModel, float renderedYaw) {
        this(id, definitionId, location, yaw, renderer, entities, blocks, renderedModel, renderedYaw, null);
    }

    public FurnitureInstance(UUID id, ContentID definitionId, Location location, float yaw,
                             FurnitureRendererType renderer, List<UUID> entities,
                             List<FurnitureBlockPosition> blocks, ContentID renderedModel, float renderedYaw,
                             String renderSignature) {
        this(id, definitionId, location, yaw, renderer, entities, blocks, renderedModel, renderedYaw,
                renderSignature, Collections.<ItemStack>emptyList(), null);
    }

    public FurnitureInstance(UUID id, ContentID definitionId, Location location, float yaw,
                             FurnitureRendererType renderer, List<UUID> entities,
                             List<FurnitureBlockPosition> blocks, ContentID renderedModel, float renderedYaw,
                             String renderSignature, List<ItemStack> inventoryContents) {
        this(id, definitionId, location, yaw, renderer, entities, blocks, renderedModel, renderedYaw,
                renderSignature, inventoryContents, null);
    }

    public FurnitureInstance(UUID id, ContentID definitionId, Location location, float yaw,
                             FurnitureRendererType renderer, List<UUID> entities,
                             List<FurnitureBlockPosition> blocks, ContentID renderedModel, float renderedYaw,
                             String renderSignature, List<ItemStack> inventoryContents, Integer dyeColor) {
        this.id = id;
        this.definitionId = definitionId;
        this.location = location.clone();
        this.yaw = yaw;
        this.renderer = renderer;
        this.entities = Collections.unmodifiableList(new ArrayList<UUID>(entities));
        this.blocks = Collections.unmodifiableList(new ArrayList<FurnitureBlockPosition>(blocks));
        this.renderedModel = renderedModel;
        this.renderedYaw = renderedYaw;
        this.renderSignature = renderSignature;
        this.inventoryContents = cloneItems(inventoryContents);
        this.dyeColor = dyeColor;
    }

    public UUID id() { return id; }
    public ContentID definitionId() { return definitionId; }
    public Location location() { return location.clone(); }
    public float yaw() { return yaw; }
    public FurnitureRendererType renderer() { return renderer; }
    public List<UUID> entities() { return entities; }
    public List<FurnitureBlockPosition> blocks() { return blocks; }
    public ContentID renderedModel() { return renderedModel; }
    public float renderedYaw() { return renderedYaw; }
    public String renderSignature() { return renderSignature; }
    public List<ItemStack> inventoryContents() { return cloneItems(inventoryContents); }
    public Integer dyeColor() { return dyeColor; }

    private static List<ItemStack> cloneItems(List<ItemStack> items) {
        List<ItemStack> copy = new ArrayList<ItemStack>();
        if (items != null) {
            for (ItemStack item : items) copy.add(item == null ? null : item.clone());
        }
        return Collections.unmodifiableList(copy);
    }
}
