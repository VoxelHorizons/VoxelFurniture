package org.voxelhorizons.furniture.model;

import org.voxelhorizons.content.ContentID;

public final class FurnitureDisplayPartDefinition {
    public enum Type { ITEM, TEXT }

    private final String id;
    private final Type type;
    private final ContentID modelItem;
    private final String text;
    private final String billboard;
    private final double offsetX, offsetY, offsetZ;
    private final float scale;
    private final boolean bob;
    private final boolean spin;

    public FurnitureDisplayPartDefinition(String id, Type type, ContentID modelItem, String text,
                                          String billboard, double offsetX, double offsetY, double offsetZ,
                                          float scale, boolean bob, boolean spin) {
        this.id = id;
        this.type = type;
        this.modelItem = modelItem;
        this.text = text;
        this.billboard = billboard;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.scale = scale;
        this.bob = bob;
        this.spin = spin;
    }

    public String id() { return id; }
    public Type type() { return type; }
    public ContentID modelItem() { return modelItem; }
    public String text() { return text; }
    public String billboard() { return billboard; }
    public double offsetX() { return offsetX; }
    public double offsetY() { return offsetY; }
    public double offsetZ() { return offsetZ; }
    public float scale() { return scale; }
    public boolean bob() { return bob; }
    public boolean spin() { return spin; }
}
