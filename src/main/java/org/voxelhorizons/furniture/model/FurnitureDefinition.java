package org.voxelhorizons.furniture.model;

import org.voxelhorizons.content.ContentID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FurnitureDefinition {
    private final ContentID itemId;
    private final ContentID modelItemId;
    private final ContentID dropItemId;
    private final FurnitureRendererType renderer;
    private final float width;
    private final float height;
    private final double hitboxOffsetX;
    private final double hitboxOffsetY;
    private final double hitboxOffsetZ;
    private final float scaleX;
    private final float scaleY;
    private final float scaleZ;
    private final float viewDistance;
    private final float rotationStep;
    private final FurniturePlacement placement;
    private final FurnitureSeatDefinition seat;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final float offsetRotation;
    private final List<FurnitureBlockDefinition> blocks;
    private final List<FurnitureStateRule> states;
    private final ContentID animationUseModel;
    private final int animationCloseDelay;
    private final boolean animationSyncNeighbors;
    private final FurnitureIdleAnimationDefinition idleAnimation;
    private final List<FurnitureDisplayPartDefinition> displayParts;
    private final int inventorySize;

    public FurnitureDefinition(ContentID itemId, ContentID modelItemId, ContentID dropItemId,
                               FurnitureRendererType renderer, float width, float height, float scale,
                               float rotationStep, double offsetX, double offsetY, double offsetZ,
                               List<FurnitureBlockDefinition> blocks) {
        this(itemId, modelItemId, dropItemId, renderer, width, height, scale, scale, scale, 64.0F, rotationStep,
                FurniturePlacement.TOP, null, offsetX, offsetY, offsetZ, blocks,
                Collections.<FurnitureStateRule>emptyList());
    }

    public FurnitureDefinition(ContentID itemId, ContentID modelItemId, ContentID dropItemId,
                               FurnitureRendererType renderer, float width, float height, float scale,
                               float rotationStep, double offsetX, double offsetY, double offsetZ,
                               List<FurnitureBlockDefinition> blocks, List<FurnitureStateRule> states) {
        this(itemId, modelItemId, dropItemId, renderer, width, height, scale, scale, scale, 64.0F, rotationStep,
                FurniturePlacement.TOP, null, offsetX, offsetY, offsetZ, blocks, states);
    }

    public FurnitureDefinition(ContentID itemId, ContentID modelItemId, ContentID dropItemId,
                               FurnitureRendererType renderer, float width, float height, float scale,
                               float viewDistance, float rotationStep, FurniturePlacement placement,
                               FurnitureSeatDefinition seat,
                               double offsetX, double offsetY, double offsetZ,
                               List<FurnitureBlockDefinition> blocks, List<FurnitureStateRule> states) {
        this(itemId, modelItemId, dropItemId, renderer, width, height, scale, scale, scale, viewDistance,
                rotationStep, placement, seat, offsetX, offsetY, offsetZ, blocks, states);
    }

    public FurnitureDefinition(ContentID itemId, ContentID modelItemId, ContentID dropItemId,
                               FurnitureRendererType renderer, float width, float height,
                               float scaleX, float scaleY, float scaleZ,
                               float viewDistance, float rotationStep, FurniturePlacement placement,
                               FurnitureSeatDefinition seat,
                               double offsetX, double offsetY, double offsetZ,
                               List<FurnitureBlockDefinition> blocks, List<FurnitureStateRule> states) {
        this(itemId, modelItemId, dropItemId, renderer, width, height, scaleX, scaleY, scaleZ,
                viewDistance, rotationStep, placement, seat, offsetX, offsetY, offsetZ, blocks, states, null, 0);
    }

    public FurnitureDefinition(ContentID itemId, ContentID modelItemId, ContentID dropItemId,
                               FurnitureRendererType renderer, float width, float height,
                               float scaleX, float scaleY, float scaleZ,
                               float viewDistance, float rotationStep, FurniturePlacement placement,
                               FurnitureSeatDefinition seat,
                               double offsetX, double offsetY, double offsetZ,
                               List<FurnitureBlockDefinition> blocks, List<FurnitureStateRule> states,
                               ContentID animationUseModel, int inventorySize) {
        this(itemId, modelItemId, dropItemId, renderer, width, height, scaleX, scaleY, scaleZ,
                viewDistance, rotationStep, placement, seat, offsetX, offsetY, offsetZ, 0.0F,
                blocks, states, animationUseModel, 10, inventorySize);
    }

    public FurnitureDefinition(ContentID itemId, ContentID modelItemId, ContentID dropItemId,
                               FurnitureRendererType renderer, float width, float height,
                               float scaleX, float scaleY, float scaleZ,
                               float viewDistance, float rotationStep, FurniturePlacement placement,
                               FurnitureSeatDefinition seat,
                               double offsetX, double offsetY, double offsetZ, float offsetRotation,
                               List<FurnitureBlockDefinition> blocks, List<FurnitureStateRule> states,
                               ContentID animationUseModel, int inventorySize) {
        this(itemId, modelItemId, dropItemId, renderer, width, height, scaleX, scaleY, scaleZ,
                viewDistance, rotationStep, placement, seat, offsetX, offsetY, offsetZ, offsetRotation,
                blocks, states, animationUseModel, 10, inventorySize);
    }

    public FurnitureDefinition(ContentID itemId, ContentID modelItemId, ContentID dropItemId,
                               FurnitureRendererType renderer, float width, float height,
                               float scaleX, float scaleY, float scaleZ,
                               float viewDistance, float rotationStep, FurniturePlacement placement,
                               FurnitureSeatDefinition seat,
                               double offsetX, double offsetY, double offsetZ, float offsetRotation,
                               List<FurnitureBlockDefinition> blocks, List<FurnitureStateRule> states,
                               ContentID animationUseModel, int animationCloseDelay, int inventorySize) {
        this(itemId, modelItemId, dropItemId, renderer, width, height, scaleX, scaleY, scaleZ,
                viewDistance, rotationStep, placement, seat, offsetX, offsetY, offsetZ, offsetRotation,
                blocks, states, animationUseModel, animationCloseDelay, false, inventorySize);
    }

    public FurnitureDefinition(ContentID itemId, ContentID modelItemId, ContentID dropItemId,
                               FurnitureRendererType renderer, float width, float height,
                               float scaleX, float scaleY, float scaleZ,
                               float viewDistance, float rotationStep, FurniturePlacement placement,
                               FurnitureSeatDefinition seat,
                               double offsetX, double offsetY, double offsetZ, float offsetRotation,
                               List<FurnitureBlockDefinition> blocks, List<FurnitureStateRule> states,
                               ContentID animationUseModel, int animationCloseDelay,
                               boolean animationSyncNeighbors, int inventorySize) {
        this(itemId, modelItemId, dropItemId, renderer, width, height, 0.0D, 0.0D, 0.0D,
                scaleX, scaleY, scaleZ, viewDistance, rotationStep, placement, seat,
                offsetX, offsetY, offsetZ, offsetRotation, blocks, states, animationUseModel,
                animationCloseDelay, animationSyncNeighbors, inventorySize);
    }

    public FurnitureDefinition(ContentID itemId, ContentID modelItemId, ContentID dropItemId,
                               FurnitureRendererType renderer, float width, float height,
                               double hitboxOffsetX, double hitboxOffsetY, double hitboxOffsetZ,
                               float scaleX, float scaleY, float scaleZ,
                               float viewDistance, float rotationStep, FurniturePlacement placement,
                               FurnitureSeatDefinition seat,
                               double offsetX, double offsetY, double offsetZ, float offsetRotation,
                               List<FurnitureBlockDefinition> blocks, List<FurnitureStateRule> states,
                               ContentID animationUseModel, int animationCloseDelay,
                               boolean animationSyncNeighbors, int inventorySize) {
        this(itemId, modelItemId, dropItemId, renderer, width, height,
                hitboxOffsetX, hitboxOffsetY, hitboxOffsetZ, scaleX, scaleY, scaleZ,
                viewDistance, rotationStep, placement, seat, offsetX, offsetY, offsetZ, offsetRotation,
                blocks, states, animationUseModel, animationCloseDelay, animationSyncNeighbors,
                null, Collections.<FurnitureDisplayPartDefinition>emptyList(), inventorySize);
    }

    public FurnitureDefinition(ContentID itemId, ContentID modelItemId, ContentID dropItemId,
                               FurnitureRendererType renderer, float width, float height,
                               double hitboxOffsetX, double hitboxOffsetY, double hitboxOffsetZ,
                               float scaleX, float scaleY, float scaleZ,
                               float viewDistance, float rotationStep, FurniturePlacement placement,
                               FurnitureSeatDefinition seat,
                               double offsetX, double offsetY, double offsetZ, float offsetRotation,
                               List<FurnitureBlockDefinition> blocks, List<FurnitureStateRule> states,
                               ContentID animationUseModel, int animationCloseDelay,
                               boolean animationSyncNeighbors, FurnitureIdleAnimationDefinition idleAnimation,
                               List<FurnitureDisplayPartDefinition> displayParts, int inventorySize) {
        this.itemId = itemId;
        this.modelItemId = modelItemId;
        this.dropItemId = dropItemId;
        this.renderer = renderer;
        this.width = width;
        this.height = height;
        this.hitboxOffsetX = hitboxOffsetX;
        this.hitboxOffsetY = hitboxOffsetY;
        this.hitboxOffsetZ = hitboxOffsetZ;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.scaleZ = scaleZ;
        this.viewDistance = viewDistance;
        this.rotationStep = rotationStep;
        this.placement = placement;
        this.seat = seat;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.offsetRotation = offsetRotation;
        this.blocks = Collections.unmodifiableList(new ArrayList<FurnitureBlockDefinition>(blocks));
        this.states = Collections.unmodifiableList(new ArrayList<FurnitureStateRule>(states));
        this.animationUseModel = animationUseModel;
        this.animationCloseDelay = animationCloseDelay;
        this.animationSyncNeighbors = animationSyncNeighbors;
        this.idleAnimation = idleAnimation;
        this.displayParts = Collections.unmodifiableList(new ArrayList<FurnitureDisplayPartDefinition>(displayParts));
        this.inventorySize = inventorySize;
    }

    public ContentID itemId() { return itemId; }
    public ContentID modelItemId() { return modelItemId; }
    public ContentID dropItemId() { return dropItemId; }
    public FurnitureRendererType renderer() { return renderer; }
    public float width() { return width; }
    public float height() { return height; }
    public double hitboxOffsetX() { return hitboxOffsetX; }
    public double hitboxOffsetY() { return hitboxOffsetY; }
    public double hitboxOffsetZ() { return hitboxOffsetZ; }
    /**
     * Legacy uniform-scale accessor retained for source/binary compatibility.
     * For axis-specific definitions this returns the X scale.
     */
    public float scale() { return scaleX; }
    public float scaleX() { return scaleX; }
    public float scaleY() { return scaleY; }
    public float scaleZ() { return scaleZ; }
    public float viewDistance() { return viewDistance; }
    public float rotationStep() { return rotationStep; }
    public FurniturePlacement placement() { return placement; }
    public FurnitureSeatDefinition seat() { return seat; }
    public double offsetX() { return offsetX; }
    public double offsetY() { return offsetY; }
    public double offsetZ() { return offsetZ; }
    public float offsetRotation() { return offsetRotation; }
    public List<FurnitureBlockDefinition> blocks() { return blocks; }
    public List<FurnitureStateRule> states() { return states; }
    public ContentID animationUseModel() { return animationUseModel; }
    public int animationCloseDelay() { return animationCloseDelay; }
    public boolean animationSyncNeighbors() { return animationSyncNeighbors; }
    public FurnitureIdleAnimationDefinition idleAnimation() { return idleAnimation; }
    public List<FurnitureDisplayPartDefinition> displayParts() { return displayParts; }
    public int inventorySize() { return inventorySize; }
    public boolean hasInventory() { return inventorySize > 0; }
}
