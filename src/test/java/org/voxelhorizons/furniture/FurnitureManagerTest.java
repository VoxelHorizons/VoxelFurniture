package org.voxelhorizons.furniture;

import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.Test;
import org.voxelhorizons.furniture.model.FurnitureBlockDefinition;
import org.voxelhorizons.furniture.model.FurnitureBlockPosition;
import org.voxelhorizons.furniture.model.FurnitureInstance;
import org.voxelhorizons.furniture.model.FurnitureRendererType;
import org.voxelhorizons.content.ContentID;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class FurnitureManagerTest {
    @Test public void inventoryNamePlaceholderStripsItemColorButPreservesTitleFormatting() {
        assertEquals(":offset_-8::generic_27_top::offset_-170:&rOak Drawer Bench",
                FurnitureManager.expandInventoryTitleNameTokens(
                        ":offset_-8::generic_27_top::offset_-170:&r<name>",
                        "&fOak Drawer Bench"));
    }

    @Test public void inventoryItemAliasAlsoStripsLegacyAndRgbFormatting() {
        assertEquals("&rOak Drawer Bench / Oak Drawer Bench",
                FurnitureManager.expandInventoryTitleNameTokens(
                        "&r<item> / <name>", "&#FFAA33Oak &lDrawer §bBench"));
    }

    @Test public void inventoryTokensCanBeRepeatedAndRetainUnicodeUiGlyphs() {
        assertEquals(":offset_-8:Chair - Chair",
                FurnitureManager.expandInventoryTitleNameTokens(
                        ":offset_-8:<name> - <item>", "&6Chair"));
    }

    @Test public void titleWithoutTokensRemainsUnchanged() {
        String title = ":offset_-8:&rStatic Title";
        assertEquals(title, FurnitureManager.expandInventoryTitleNameTokens(title, "&cOak Drawer Bench"));
    }

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

    @Test public void interactionAnimationStateDefaultsOffAndCanPersistOnInstance() {
        Location location = new Location(null, 0.5D, 64.0D, 0.5D);
        FurnitureInstance normal = new FurnitureInstance(UUID.randomUUID(), ContentID.parse("voxel:curtain", "voxel"),
                location, 0.0F, FurnitureRendererType.ARMOR_STAND, Collections.<UUID>emptyList(),
                Collections.<FurnitureBlockPosition>emptyList());
        assertFalse(normal.useAnimationActive());

        FurnitureInstance active = new FurnitureInstance(UUID.randomUUID(), ContentID.parse("voxel:curtain", "voxel"),
                location, 0.0F, FurnitureRendererType.ARMOR_STAND, Collections.<UUID>emptyList(),
                Collections.<FurnitureBlockPosition>emptyList(), ContentID.parse("voxel:curtain_closed", "voxel"),
                0.0F, null, Collections.<org.bukkit.inventory.ItemStack>emptyList(), null, true);
        assertTrue(active.useAnimationActive());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsCellsThatOverlapAfterRotation() {
        FurnitureManager.resolveBlocks(Arrays.asList(
                new FurnitureBlockDefinition(-2, 0, 0, Material.BARRIER),
                new FurnitureBlockDefinition(-1, 0, 0, Material.BARRIER)
        ), new Location(null, 0.5D, 64.0D, 0.5D), 45.0F);
    }
    @Test public void ignoresAlreadyRemovedFurnitureWhenRepeatedBreakEventArrives() {
        Map<UUID, FurnitureInstance> registry = new HashMap<UUID, FurnitureInstance>();
        FurnitureInstance old = new FurnitureInstance(UUID.randomUUID(),
                ContentID.parse("voxel:bamboo_cupboard_bench", "voxel"),
                new Location(null, 0.5D, 64.0D, 0.5D), 0.0F,
                FurnitureRendererType.ARMOR_STAND, Collections.<UUID>emptyList(),
                Collections.<FurnitureBlockPosition>emptyList());
        registry.put(old.id(), old);
        assertSame(old, FurnitureManager.currentForBreak(registry, old));
        registry.remove(old.id());
        assertNull(FurnitureManager.currentForBreak(registry, old));
    }

    @Test public void staleSnapshotResolvesToNewestLiveFurnitureState() {
        Map<UUID, FurnitureInstance> registry = new HashMap<UUID, FurnitureInstance>();
        UUID id = UUID.randomUUID();
        Location at = new Location(null, 3.5D, 70.0D, 2.5D);
        ContentID key = ContentID.parse("voxel:bamboo_cupboard_bench", "voxel");
        FurnitureInstance stale = new FurnitureInstance(id, key, at, 0.0F,
                FurnitureRendererType.ARMOR_STAND, Collections.<UUID>emptyList(),
                Collections.<FurnitureBlockPosition>emptyList());
        FurnitureInstance updated = new FurnitureInstance(id, key, at, 90.0F,
                FurnitureRendererType.ARMOR_STAND, Collections.<UUID>emptyList(),
                Collections.<FurnitureBlockPosition>emptyList());
        registry.put(id, updated);
        assertSame(updated, FurnitureManager.currentForBreak(registry, stale));
        assertNull(FurnitureManager.currentForBreak(registry, null));
    }

}
