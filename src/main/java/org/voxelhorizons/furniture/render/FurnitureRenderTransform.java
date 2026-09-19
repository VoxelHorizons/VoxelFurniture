package org.voxelhorizons.furniture.render;

import org.bukkit.Location;

public final class FurnitureRenderTransform {
    private FurnitureRenderTransform() {}

    /**
     * Applies a furniture-local offset to a world location.
     *
     * X/Z rotate around the Y axis with the furniture yaw while Y remains
     * vertical. This mirrors the local-coordinate behavior already used for
     * furniture collision blocks and seats.
     */
    public static Location applyLocalOffset(Location origin, float yaw,
                                            double offsetX, double offsetY, double offsetZ) {
        double radians = Math.toRadians(yaw);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);

        double worldX = offsetX * cosine - offsetZ * sine;
        double worldZ = offsetX * sine + offsetZ * cosine;

        Location result = origin.clone().add(worldX, offsetY, worldZ);
        result.setYaw(yaw);
        return result;
    }
}
