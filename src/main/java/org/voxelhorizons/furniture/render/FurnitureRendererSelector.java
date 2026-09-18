package org.voxelhorizons.furniture.render;

import org.voxelhorizons.furniture.model.FurnitureRendererType;

public final class FurnitureRendererSelector {
    private final FurnitureRenderer armorStand = new ArmorStandFurnitureRenderer();
    private final FurnitureRenderer display = new DisplayFurnitureRenderer();

    public FurnitureRenderer select(FurnitureRendererType requested) {
        if (requested == FurnitureRendererType.ARMOR_STAND) return armorStand;
        if (requested == FurnitureRendererType.DISPLAY) {
            if (!display.supported()) throw new IllegalArgumentException(
                    "Furniture requires display entities, but this server is older than Minecraft 1.19.4");
            return display;
        }
        return display.supported() ? display : armorStand;
    }
}
