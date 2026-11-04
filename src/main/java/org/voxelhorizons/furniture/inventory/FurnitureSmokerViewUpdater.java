package org.voxelhorizons.furniture.inventory;

import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Keeps the vanilla smoker's flame and arrow progress synchronized for
 * inventories with no physical block entity. Uses the public FurnaceView API
 * (available on modern servers) without depending on NMS or packet plugins.
 *
 * Reflection is necessary because this plugin also builds against Bukkit 1.12,
 * which has no FurnaceView interface or smoker.
 */
public final class FurnitureSmokerViewUpdater {
    private FurnitureSmokerViewUpdater() {}

    public static void update(Inventory inventory, FurnitureSmokerProcessor processor) {
        if (inventory == null || processor == null || inventory.getViewers().isEmpty()) return;

        int burning = processor.remainingBurnTicks();
        int fuelDuration = processor.fuelDuration();
        int cooking = processor.cookingTicks();
        int cookingDuration = processor.cookingDuration();

        for (HumanEntity viewer : new ArrayList<HumanEntity>(inventory.getViewers())) {
            InventoryView view = viewer.getOpenInventory();
            if (view == null || view.getTopInventory() != inventory) continue;
            setProgress(view, burning, fuelDuration, cooking, cookingDuration);
        }
    }

    static boolean setProgress(InventoryView view, int burn, int burnTotal,
                               int cook, int cookTotal) {
        try {
            Class<?> furnaceView = Class.forName("org.bukkit.inventory.view.FurnaceView");
            if (furnaceView.isInstance(view)) {
                Method setBurnTime = furnaceView.getMethod("setBurnTime", int.class, int.class);
                Method setCookTime = furnaceView.getMethod("setCookTime", int.class, int.class);
                setBurnTime.invoke(view, Math.max(0, burn), Math.max(1, burnTotal));
                setCookTime.invoke(view, Math.max(0, cook), Math.max(1, cookTotal));
                return true;
            }
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Legacy Bukkit versions used InventoryView.Property instead.
        } catch (IllegalAccessException | InvocationTargetException exception) {
            // If a server implementation declines FurnaceView progress, fall
            // back to the older public inventory property API.
        }

        return legacyProgress(view, burn, burnTotal, cook, cookTotal);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean legacyProgress(InventoryView view, int burn, int burnTotal,
                                          int cook, int cookTotal) {
        try {
            Class propertyClass = Class.forName("org.bukkit.inventory.InventoryView$Property");
            Method setProperty = view.getClass().getMethod("setProperty", propertyClass, int.class);
            boolean ok = true;
            ok &= Boolean.TRUE.equals(setProperty.invoke(view,
                    Enum.valueOf(propertyClass, "TICKS_FOR_CURRENT_FUEL"), Math.max(1, burnTotal)));
            ok &= Boolean.TRUE.equals(setProperty.invoke(view,
                    Enum.valueOf(propertyClass, "BURN_TIME"), Math.max(0, burn)));
            ok &= Boolean.TRUE.equals(setProperty.invoke(view,
                    Enum.valueOf(propertyClass, "TICKS_FOR_CURRENT_SMELTING"), Math.max(1, cookTotal)));
            ok &= Boolean.TRUE.equals(setProperty.invoke(view,
                    Enum.valueOf(propertyClass, "COOK_TIME"), Math.max(0, cook)));
            return ok;
        } catch (ReflectiveOperationException | IllegalArgumentException exception) {
            return false;
        }
    }
}
