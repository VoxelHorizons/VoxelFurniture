package org.voxelhorizons.furniture.model;

import org.bukkit.Material;

public final class FurnitureBlockPosition {
    private final int x;
    private final int y;
    private final int z;
    private final Material material;

    public FurnitureBlockPosition(int x, int y, int z, Material material) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.material = material;
    }

    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public Material material() { return material; }
}
