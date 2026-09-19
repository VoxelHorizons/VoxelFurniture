package org.voxelhorizons.furniture.model;

public final class FurnitureSeatDefinition {
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final float yawOffset;

    public FurnitureSeatDefinition(double offsetX, double offsetY, double offsetZ, float yawOffset) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.yawOffset = yawOffset;
    }

    public double offsetX() { return offsetX; }
    public double offsetY() { return offsetY; }
    public double offsetZ() { return offsetZ; }
    public float yawOffset() { return yawOffset; }
}
