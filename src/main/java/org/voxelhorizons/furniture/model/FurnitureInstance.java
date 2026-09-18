package org.voxelhorizons.furniture.model;

import org.bukkit.Location;
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

    public FurnitureInstance(UUID id, ContentID definitionId, Location location, float yaw,
                             FurnitureRendererType renderer, List<UUID> entities,
                             List<FurnitureBlockPosition> blocks) {
        this.id = id;
        this.definitionId = definitionId;
        this.location = location.clone();
        this.yaw = yaw;
        this.renderer = renderer;
        this.entities = Collections.unmodifiableList(new ArrayList<UUID>(entities));
        this.blocks = Collections.unmodifiableList(new ArrayList<FurnitureBlockPosition>(blocks));
    }

    public UUID id() { return id; }
    public ContentID definitionId() { return definitionId; }
    public Location location() { return location.clone(); }
    public float yaw() { return yaw; }
    public FurnitureRendererType renderer() { return renderer; }
    public List<UUID> entities() { return entities; }
    public List<FurnitureBlockPosition> blocks() { return blocks; }
}
