package org.voxelhorizons.furniture;

import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.item.ItemType;
import org.voxelhorizons.content.item.RawItemDefinition;
import org.voxelhorizons.content.compile.ItemDefinitionCompiler;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureDefinitionParser;
import org.voxelhorizons.furniture.model.FurnitureRendererType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FurnitureDefinitionParserTest {

    @Test public void scalarScaleRemainsUniformAndBackwardsCompatible() {
        FurnitureDefinition definition = parse(2.0D);

        assertEquals(2.0F, definition.scale(), 0.001F);
        assertEquals(2.0F, definition.scaleX(), 0.001F);
        assertEquals(2.0F, definition.scaleY(), 0.001F);
        assertEquals(2.0F, definition.scaleZ(), 0.001F);
    }

    @Test public void mappingScaleSupportsIndependentAxes() {
        Map<String, Object> scale = new LinkedHashMap<String, Object>();
        scale.put("x", 2.0D);
        scale.put("y", 3.0D);
        scale.put("z", 2.0D);

        FurnitureDefinition definition = parse(scale);

        assertEquals(2.0F, definition.scaleX(), 0.001F);
        assertEquals(3.0F, definition.scaleY(), 0.001F);
        assertEquals(2.0F, definition.scaleZ(), 0.001F);
    }

    @Test public void missingScaleAxesDefaultToOne() {
        Map<String, Object> scale = new LinkedHashMap<String, Object>();
        scale.put("y", 2.5D);

        FurnitureDefinition definition = parse(scale);

        assertEquals(1.0F, definition.scaleX(), 0.001F);
        assertEquals(2.5F, definition.scaleY(), 0.001F);
        assertEquals(1.0F, definition.scaleZ(), 0.001F);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveScaleAxis() {
        Map<String, Object> scale = new LinkedHashMap<String, Object>();
        scale.put("x", 0.0D);
        parse(scale);
    }

    @Test public void parsesInventoryAndUseAnimation() {
        Map<String, Object> animation = new LinkedHashMap<String, Object>();
        animation.put("use", "voxel:test_open");
        animation.put("close_delay", 16);
        Map<String, Object> inventory = new LinkedHashMap<String, Object>();
        inventory.put("size", 27);

        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("animation", animation);
        furniture.put("inventory", inventory);
        FurnitureDefinition definition = parseFurniture(furniture);

        assertEquals(27, definition.inventorySize());
        assertTrue(definition.hasInventory());
        assertEquals(ContentID.parse("voxel:test_open", "voxel"), definition.animationUseModel());
        assertEquals(16, definition.animationCloseDelay());
    }

    @Test public void parsesNeighborAnimationSynchronization() {
        Map<String, Object> animation = new LinkedHashMap<String, Object>();
        animation.put("use", "voxel:test_closed");
        animation.put("sync_neighbors", true);
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("animation", animation);

        assertTrue(parseFurniture(furniture).animationSyncNeighbors());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonBooleanNeighborAnimationSynchronization() {
        Map<String, Object> animation = new LinkedHashMap<String, Object>();
        animation.put("use", "voxel:test_closed");
        animation.put("sync_neighbors", "yes");
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("animation", animation);
        parseFurniture(furniture);
    }

    @Test public void useAnimationDefaultsToTenTickCloseDelay() {
        Map<String, Object> animation = new LinkedHashMap<String, Object>();
        animation.put("use", "voxel:test_open");
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("animation", animation);

        assertEquals(10, parseFurniture(furniture).animationCloseDelay());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeAnimationCloseDelay() {
        Map<String, Object> animation = new LinkedHashMap<String, Object>();
        animation.put("use", "voxel:test_open");
        animation.put("close_delay", -1);
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("animation", animation);
        parseFurniture(furniture);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInventorySizeThatIsNotAChestRow() {
        Map<String, Object> inventory = new LinkedHashMap<String, Object>();
        inventory.put("size", 10);
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("inventory", inventory);
        parseFurniture(furniture);
    }

    @Test public void parsesRenderOffsetRotation() {
        Map<String, Object> offset = new LinkedHashMap<String, Object>();
        offset.put("x", 0.25D);
        offset.put("rotation", -90.0D);
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("offset", offset);

        FurnitureDefinition definition = parseFurniture(furniture);

        assertEquals(0.25D, definition.offsetX(), 0.000001D);
        assertEquals(-90.0F, definition.offsetRotation(), 0.000001F);
    }

    @Test public void inheritedFurnitureOffsetAllowsChildAxisOverrides() {
        ContentID parentId = ContentID.parse("voxel:curtain_open", "voxel");
        ContentID childId = ContentID.parse("voxel:curtain_closed", "voxel");

        Map<String, Object> parentOffset = new LinkedHashMap<String, Object>();
        parentOffset.put("x", 0.0D);
        parentOffset.put("y", 0.5D);
        parentOffset.put("z", 0.5D);
        parentOffset.put("rotation", 0.0D);
        Map<String, Object> parentFurniture = new LinkedHashMap<String, Object>();
        parentFurniture.put("offset", parentOffset);
        Map<String, Object> parentProperties = new LinkedHashMap<String, Object>();
        parentProperties.put("furniture", parentFurniture);

        Map<String, Object> childOffset = new LinkedHashMap<String, Object>();
        childOffset.put("y", -0.5D);
        Map<String, Object> childFurniture = new LinkedHashMap<String, Object>();
        childFurniture.put("offset", childOffset);
        Map<String, Object> childProperties = new LinkedHashMap<String, Object>();
        childProperties.put("furniture", childFurniture);

        RawItemDefinition parent = new RawItemDefinition(parentId, null, ItemType.ITEM, "PAPER", "Curtain",
                Collections.<String>emptyList(), Boolean.FALSE, Boolean.FALSE, null, parentProperties, null);
        RawItemDefinition child = new RawItemDefinition(childId, parentId, null, null, null,
                null, null, Boolean.TRUE, null, childProperties, null);

        ItemDefinition resolvedChild = new ItemDefinitionCompiler()
                .compile(java.util.Arrays.asList(parent, child)).get(childId).get();
        FurnitureDefinitionParser parser = new FurnitureDefinitionParser(FurnitureRendererType.AUTO, 45.0F);
        FurnitureDefinition definition = parser.parse(resolvedChild).get();

        assertEquals(0.0D, definition.offsetX(), 0.000001D);
        assertEquals(-0.5D, definition.offsetY(), 0.000001D);
        assertEquals(0.5D, definition.offsetZ(), 0.000001D);
        assertEquals(0.0F, definition.offsetRotation(), 0.000001F);
    }

    private static FurnitureDefinition parse(Object scale) {
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("scale", scale);
        return parseFurniture(furniture);
    }

    private static FurnitureDefinition parseFurniture(Map<String, Object> furniture) {

        Map<String, Object> properties = new LinkedHashMap<String, Object>();
        properties.put("furniture", furniture);

        ItemDefinition item = new ItemDefinition(
                ContentID.parse("voxel:test", "voxel"),
                ItemType.ITEM,
                "PAPER",
                "Test",
                Collections.<String>emptyList(),
                true,
                Optional.<ContentID>empty(),
                null,
                properties);

        FurnitureDefinitionParser parser =
                new FurnitureDefinitionParser(FurnitureRendererType.AUTO, 45.0F);
        Optional<FurnitureDefinition> parsed = parser.parse(item);
        assertTrue(parsed.isPresent());
        return parsed.get();
    }
}
