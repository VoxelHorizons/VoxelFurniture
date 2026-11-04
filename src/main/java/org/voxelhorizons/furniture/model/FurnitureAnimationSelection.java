package org.voxelhorizons.furniture.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.content.ContentID;

import java.util.List;

/** Resolves independent full, open/use and manual toggle model states. */
public final class FurnitureAnimationSelection {
    private FurnitureAnimationSelection() {}

    /**
     * Full wins over open to keep overflowing bins visually full even when inspected.
     * Open/use wins over the manually toggled state; legacy non-inventory use toggles still work.
     */
    public static ContentID model(FurnitureDefinition definition, ContentID idle,
                                  boolean inventoryOpen, boolean full, boolean toggled) {
        return model(definition, idle, inventoryOpen, full, toggled, false);
    }

    /**
     * Actual cooking capability and inventory openness form independent axes.
     * The active model is not shown when the output is full or the input is gone,
     * even though the GUI's fuel flame may remain lit until fuel burns out.
     * Full-bin precedence and non-inventory toggle behaviour remain unchanged.
     */
    public static ContentID model(FurnitureDefinition definition, ContentID idle,
                                  boolean inventoryOpen, boolean full, boolean toggled,
                                  boolean cooking) {
        if (full && definition.animationFullModel() != null) return definition.animationFullModel();
        if (cooking && inventoryOpen && definition.animationUseActiveModel() != null)
            return definition.animationUseActiveModel();
        if (inventoryOpen && definition.animationUseModel() != null) return definition.animationUseModel();
        if (cooking && definition.animationActiveModel() != null) return definition.animationActiveModel();
        if (toggled && definition.animationToggleModel() != null) return definition.animationToggleModel();
        if (toggled && !definition.hasInventory() && definition.animationUseModel() != null)
            return definition.animationUseModel();
        return idle;
    }

    /** A bin is full when every configured slot is occupied, regardless of stack size. */
    public static boolean isFull(List<ItemStack> contents, int size) {
        if (size <= 0 || contents == null || contents.size() < size) return false;
        for (int slot = 0; slot < size; slot++) {
            ItemStack item = contents.get(slot);
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) return false;
        }
        return true;
    }

    public static boolean isFull(ItemStack[] contents, int size) {
        if (contents == null || size <= 0 || contents.length < size) return false;
        for (int slot = 0; slot < size; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) return false;
        }
        return true;
    }
}
