package org.voxelhorizons.furniture;

import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.item.ItemType;
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
        Map<String, Object> inventory = new LinkedHashMap<String, Object>();
        inventory.put("size", 27);

        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("animation", animation);
        furniture.put("inventory", inventory);
        FurnitureDefinition definition = parseFurniture(furniture);

        assertEquals(27, definition.inventorySize());
        assertTrue(definition.hasInventory());
        assertEquals(ContentID.parse("voxel:test_open", "voxel"), definition.animationUseModel());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInventorySizeThatIsNotAChestRow() {
        Map<String, Object> inventory = new LinkedHashMap<String, Object>();
        inventory.put("size", 10);
        Map<String, Object> furniture = new LinkedHashMap<String, Object>();
        furniture.put("inventory", inventory);
        parseFurniture(furniture);
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
