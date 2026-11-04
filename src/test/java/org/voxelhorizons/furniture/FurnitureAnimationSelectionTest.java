package org.voxelhorizons.furniture;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.content.item.ItemType;
import org.voxelhorizons.furniture.model.FurnitureAnimationSelection;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureDefinitionParser;
import org.voxelhorizons.furniture.model.FurnitureRendererType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;

public class FurnitureAnimationSelectionTest {
    private static final ContentID ID = ContentID.parse("voxel:park_bin", "voxel");
    private static final ContentID FULL = ContentID.parse("voxel:park_bin_filled", "voxel");
    private static final ContentID OPEN = ContentID.parse("voxel:park_bin_open", "voxel");
    private static final ContentID TOGGLE = ContentID.parse("voxel:sink_flowing", "voxel");

    @Test public void binBecomesFullWhenEverySlotHasAnItemAndRevertsWhenOneIsRemoved() {
        Map<String,Object> animation = new LinkedHashMap<String,Object>();
        animation.put("full", FULL.toString());
        animation.put("use", OPEN.toString());
        Map<String,Object> inventory = new LinkedHashMap<String,Object>();
        inventory.put("size", 9);
        FurnitureDefinition definition = definition(animation, inventory);
        ItemStack[] contents = new ItemStack[9];
        assertFalse(FurnitureAnimationSelection.isFull(contents, 9));
        for (int slot=0; slot<9; slot++) contents[slot] = new ItemStack(Material.STONE);
        assertTrue(FurnitureAnimationSelection.isFull(contents, 9));
        assertEquals(FULL, FurnitureAnimationSelection.model(definition, ID, false, true, false));
        assertEquals(FULL, FurnitureAnimationSelection.model(definition, ID, true, true, false));
        contents[4] = null;
        assertFalse(FurnitureAnimationSelection.isFull(contents, 9));
        assertEquals(ID, FurnitureAnimationSelection.model(definition, ID, false, false, false));
        assertEquals(OPEN, FurnitureAnimationSelection.model(definition, ID, true, false, false));
    }

    @Test public void toggleOnlySinkSwitchesWithoutUseOrInventory() {
        Map<String,Object> animation = new LinkedHashMap<String,Object>();
        animation.put("toggle", TOGGLE.toString());
        FurnitureDefinition definition = definition(animation, null);
        assertEquals(ID, FurnitureAnimationSelection.model(definition, ID, false, false, false));
        assertEquals(TOGGLE, FurnitureAnimationSelection.model(definition, ID, false, false, true));
    }

    @Test public void ovenUseAnimationIsDrivenByInventoryOpening() {
        Map<String,Object> animation = new LinkedHashMap<String,Object>();
        animation.put("use", OPEN.toString());
        Map<String,Object> inventory = new LinkedHashMap<String,Object>();
        inventory.put("type", "SMOKER");
        FurnitureDefinition definition = definition(animation, inventory);
        assertEquals(ID, FurnitureAnimationSelection.model(definition, ID, false, false, false));
        assertEquals(OPEN, FurnitureAnimationSelection.model(definition, ID, true, false, false));
        assertEquals(ID, FurnitureAnimationSelection.model(definition, ID, false, false, true));
    }

    private static FurnitureDefinition definition(Map<String,Object> animation, Map<String,Object> inventory) {
        Map<String,Object> furniture = new LinkedHashMap<String,Object>();
        furniture.put("animation", animation);
        if (inventory != null) furniture.put("inventory", inventory);
        Map<String,Object> properties = new LinkedHashMap<String,Object>();
        properties.put("furniture", furniture);
        ItemDefinition item = new ItemDefinition(ID, ItemType.ITEM, "PAPER", "Test",
                Collections.<String>emptyList(), true, Optional.<ContentID>empty(), null, properties);
        return new FurnitureDefinitionParser(FurnitureRendererType.AUTO, 45.0F).parse(item).get();
    }
}
