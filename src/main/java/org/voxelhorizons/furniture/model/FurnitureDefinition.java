package org.voxelhorizons.furniture.model;

import org.voxelhorizons.content.ContentID;

public final class FurnitureDefinition {
    private final ContentID itemId;
    private final ContentID modelItemId;
    private final ContentID dropItemId;
    private final FurnitureRendererType renderer;
    private final float width;
    private final float height;
    private final float scale;
    private final float rotationStep;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;

    public FurnitureDefinition(ContentID itemId, ContentID modelItemId, ContentID dropItemId,
                               FurnitureRendererType renderer, float width, float height, float scale,
                               float rotationStep, double offsetX, double offsetY, double offsetZ) {
        this.itemId = itemId;
        this.modelItemId = modelItemId;
        this.dropItemId = dropItemId;
        this.renderer = renderer;
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.rotationStep = rotationStep;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
    }

    public ContentID itemId() { return itemId; }
    public ContentID modelItemId() { return modelItemId; }
    public ContentID dropItemId() { return dropItemId; }
    public FurnitureRendererType renderer() { return renderer; }
    public float width() { return width; }
    public float height() { return height; }
    public float scale() { return scale; }
    public float rotationStep() { return rotationStep; }
    public double offsetX() { return offsetX; }
    public double offsetY() { return offsetY; }
    public double offsetZ() { return offsetZ; }
}
