package org.voxelhorizons.furniture.model;

import org.bukkit.block.BlockFace;

import java.util.Locale;

public enum FurniturePlacement {
    TOP,
    BOTTOM,
    SIDE,
    ALL;

    public boolean allows(BlockFace face) {
        if (face == null) return false;
        switch (this) {
            case TOP:
                return face == BlockFace.UP;
            case BOTTOM:
                return face == BlockFace.DOWN;
            case SIDE:
                return face == BlockFace.NORTH || face == BlockFace.EAST
                        || face == BlockFace.SOUTH || face == BlockFace.WEST;
            case ALL:
                return face == BlockFace.UP || face == BlockFace.DOWN
                        || face == BlockFace.NORTH || face == BlockFace.EAST
                        || face == BlockFace.SOUTH || face == BlockFace.WEST;
            default:
                return false;
        }
    }

    public static FurniturePlacement parse(Object raw) {
        if (raw == null) return TOP;
        try {
            return valueOf(String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("placement must be TOP, BOTTOM, SIDE, or ALL");
        }
    }
}
