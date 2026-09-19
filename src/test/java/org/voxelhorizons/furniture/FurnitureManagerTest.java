package org.voxelhorizons.furniture;

import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.Test;
import org.voxelhorizons.furniture.model.FurnitureBlockDefinition;
import org.voxelhorizons.furniture.model.FurnitureBlockPosition;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FurnitureManagerTest {
    @Test public void snapsPositiveYaw() {
        assertEquals(90.0F, FurnitureManager.snapYaw(70.0F, 45.0F), 0.001F);
    }

    @Test public void wrapsNegativeYaw() {
        assertEquals(315.0F, FurnitureManager.snapYaw(-40.0F, 45.0F), 0.001F);
    }

    @Test public void wrapsFullRotation() {
        assertEquals(0.0F, FurnitureManager.snapYaw(359.0F, 45.0F), 0.001F);
    }

    @Test public void treatsAirAndNonSolidMaterialsAsReplaceable() {
        assertTrue(FurnitureManager.isReplaceable(Material.AIR));
        assertTrue(FurnitureManager.isReplaceable(Material.WATER));
        assertTrue(FurnitureManager.isReplaceable(Material.LAVA));
        assertFalse(FurnitureManager.isReplaceable(Material.STONE));
    }

    @Test public void removesOnlyUnreferencedFurnitureRendererEntities() {
        UUID tracked = UUID.randomUUID();
        UUID orphan = UUID.randomUUID();
        Set<UUID> referenced = new HashSet<UUID>(Collections.singletonList(tracked));

        assertFalse(FurnitureManager.isOrphanRenderer(
                tracked, Collections.singleton("voxelfurniture"), referenced));
        assertTrue(FurnitureManager.isOrphanRenderer(
                orphan, Collections.singleton("voxelfurniture"), referenced));
        assertFalse(FurnitureManager.isOrphanRenderer(
                orphan, Collections.singleton("voxelfurniture-seat"), referenced));
        assertFalse(FurnitureManager.isOrphanRenderer(
                orphan, Collections.singleton("unrelated-plugin"), referenced));
    }

    @Test public void rotatesCollisionBlocksAroundPlacementOrigin() {
        List<FurnitureBlockPosition> blocks = FurnitureManager.resolveBlocks(Arrays.asList(
                new FurnitureBlockDefinition(1, 0, 0, Material.BARRIER),
                new FurnitureBlockDefinition(0, 1, -1, Material.BARRIER)
        ), new Location(null, 10.5D, 64.0D, 20.5D), 90.0F);

        assertEquals(10, blocks.get(0).x());
        assertEquals(64, blocks.get(0).y());
        assertEquals(21, blocks.get(0).z());
        assertEquals(11, blocks.get(1).x());
        assertEquals(65, blocks.get(1).y());
        assertEquals(20, blocks.get(1).z());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsCellsThatOverlapAfterRotation() {
        FurnitureManager.resolveBlocks(Arrays.asList(
                new FurnitureBlockDefinition(-2, 0, 0, Material.BARRIER),
                new FurnitureBlockDefinition(-1, 0, 0, Material.BARRIER)
        ), new Location(null, 0.5D, 64.0D, 0.5D), 45.0F);
    }
}
