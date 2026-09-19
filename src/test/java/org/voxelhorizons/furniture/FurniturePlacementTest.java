package org.voxelhorizons.furniture;

import org.bukkit.block.BlockFace;
import org.junit.Test;
import org.voxelhorizons.furniture.model.FurniturePlacement;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FurniturePlacementTest {
    @Test public void topOnlyAllowsUp() {
        assertTrue(FurniturePlacement.TOP.allows(BlockFace.UP));
        assertFalse(FurniturePlacement.TOP.allows(BlockFace.DOWN));
        assertFalse(FurniturePlacement.TOP.allows(BlockFace.NORTH));
    }

    @Test public void bottomOnlyAllowsDown() {
        assertTrue(FurniturePlacement.BOTTOM.allows(BlockFace.DOWN));
        assertFalse(FurniturePlacement.BOTTOM.allows(BlockFace.UP));
        assertFalse(FurniturePlacement.BOTTOM.allows(BlockFace.EAST));
    }

    @Test public void sideAllowsOnlyHorizontalFaces() {
        assertTrue(FurniturePlacement.SIDE.allows(BlockFace.NORTH));
        assertTrue(FurniturePlacement.SIDE.allows(BlockFace.EAST));
        assertTrue(FurniturePlacement.SIDE.allows(BlockFace.SOUTH));
        assertTrue(FurniturePlacement.SIDE.allows(BlockFace.WEST));
        assertFalse(FurniturePlacement.SIDE.allows(BlockFace.UP));
        assertFalse(FurniturePlacement.SIDE.allows(BlockFace.DOWN));
    }

    @Test public void allAllowsEveryPlacementFace() {
        assertTrue(FurniturePlacement.ALL.allows(BlockFace.UP));
        assertTrue(FurniturePlacement.ALL.allows(BlockFace.DOWN));
        assertTrue(FurniturePlacement.ALL.allows(BlockFace.NORTH));
        assertTrue(FurniturePlacement.ALL.allows(BlockFace.EAST));
        assertTrue(FurniturePlacement.ALL.allows(BlockFace.SOUTH));
        assertTrue(FurniturePlacement.ALL.allows(BlockFace.WEST));
    }
}
