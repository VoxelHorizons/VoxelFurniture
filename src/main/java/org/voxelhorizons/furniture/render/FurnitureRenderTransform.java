package org.voxelhorizons.furniture.render;

import org.bukkit.Location;

public final class FurnitureRenderTransform {
    private FurnitureRenderTransform() {}

    /**
     * Single authority for the main furniture model's world position. Both spawners
     * and live-state reconciliation must use the SELECTED variant's own offsets.
     */
    public static Location modelLocation(Location origin, float yaw,
                                         org.voxelhorizons.furniture.model.FurnitureDefinition visual,
                                         org.voxelhorizons.furniture.model.FurnitureRendererType renderer) {
        double y = visual.offsetY();
        if (renderer == org.voxelhorizons.furniture.model.FurnitureRendererType.ARMOR_STAND) {
            y -= 1.45D;
        }
        return applyLocalOffset(origin, yaw, visual.offsetX(), y, visual.offsetZ(),
                visual.offsetRotation());
    }

    /**
     * Applies a furniture-local offset to a world location.
     *
     * X/Z rotate around the Y axis with the furniture yaw while Y remains
     * vertical. This mirrors the local-coordinate behavior already used for
     * furniture collision blocks and seats.
     */
    public static Location applyLocalOffset(Location origin, float yaw,
                                            double offsetX, double offsetY, double offsetZ) {
        return applyLocalOffset(origin, yaw, offsetX, offsetY, offsetZ, 0.0F);
    }

    public static Location applyLocalOffset(Location origin, float yaw,
                                            double offsetX, double offsetY, double offsetZ,
                                            float rotation) {
        double radians = Math.toRadians(yaw);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);

        double worldX = offsetX * cosine - offsetZ * sine;
        double worldZ = offsetX * sine + offsetZ * cosine;

        Location result = origin.clone().add(worldX, offsetY, worldZ);
        result.setYaw(yaw + rotation);
        return result;
    }
}
