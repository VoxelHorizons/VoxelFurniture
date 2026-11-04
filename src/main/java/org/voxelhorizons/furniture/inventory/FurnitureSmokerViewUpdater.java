package org.voxelhorizons.furniture.inventory;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Drives the native smoker's flame and cooking-arrow progress for furniture.
 *
 * Bukkit.createInventory(SMOKER) creates a virtual menu with no block entity.
 * Setting the block-entity progress through FurnaceView/setProperty is therefore
 * not sufficient on all Paper builds. The normal Minecraft window data is sent
 * to the already-open player container, without adding a packet plugin.
 *
 * The reflective bridge only runs on the primary thread and uses Mojang-mapped
 * server classes; older CraftBukkit servers retain the public API fallback.
 */
public final class FurnitureSmokerViewUpdater {
    private static boolean warned;
    private FurnitureSmokerViewUpdater() {}

    public static void update(Inventory inventory, FurnitureSmokerProcessor processor) {
        if (inventory == null || processor == null || inventory.getViewers().isEmpty()) return;

        int burning = processor.remainingBurnTicks();
        int fuelDuration = processor.fuelDuration();
        int cooking = processor.cookingTicks();
        int cookingDuration = processor.cookingDuration();

        for (HumanEntity viewer : new ArrayList<HumanEntity>(inventory.getViewers())) {
            InventoryView view = viewer.getOpenInventory();
            if (view == null) continue;
            Inventory shown = view.getTopInventory();
            if (shown != inventory && (shown == null
                    || shown.getHolder() != inventory.getHolder())) continue;

            // The protocol's data-slot values are raw ticks, not a 0..1 ratio.
            // Send to the actual open container id so the client animates the
            // vanilla flame and arrow even though there is no furnace tile.
            if (NativeProgress.send(viewer, burning, fuelDuration, cooking, cookingDuration)) continue;

            // Older servers may support the public view-property API instead.
            if (!setProgress(view, burning, fuelDuration, cooking, cookingDuration) && !warned) {
                warned = true;
                Bukkit.getLogger().warning("[VoxelFurniture] This server did not accept native or Bukkit "
                        + "smoker progress updates; the GUI may not display flame/arrow animations.");
            }
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
            // Legacy Bukkit versions used InventoryView.Property.
        } catch (IllegalAccessException | InvocationTargetException exception) {
            // Fall back to the public inventory-property API.
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

    /**
     * A Minecraft smoker menu has four integer properties:
     * 0: burn remaining, 1: maximum burn, 2: cook progress, 3: cook duration.
     * Use the open player's real container id; do not create or replace a menu.
     */
    static final class NativeProgress {
        private static final Constructor<?> PACKET = packetConstructor();

        private NativeProgress() {}

        static boolean send(HumanEntity viewer, int burn, int burnTotal, int cook, int cookTotal) {
            if (PACKET == null) return false;
            try {
                Object serverPlayer = viewer.getClass().getMethod("getHandle").invoke(viewer);
                Object menu = readField(serverPlayer, "containerMenu");
                int containerId = ((Number) readField(menu, "containerId")).intValue();
                if (containerId <= 0) return false;

                Object connection = readField(serverPlayer, "connection");
                Method sendMethod = null;
                for (Method candidate : connection.getClass().getMethods()) {
                    if (!candidate.getName().equals("send") || candidate.getParameterTypes().length != 1) continue;
                    if (candidate.getParameterTypes()[0].isAssignableFrom(PACKET.getDeclaringClass())) {
                        sendMethod = candidate;
                        break;
                    }
                }
                if (sendMethod == null) return false;
                int[] values = windowData(burn, burnTotal, cook, cookTotal);
                for (int property = 0; property < values.length; property++) {
                    sendMethod.invoke(connection,
                            PACKET.newInstance(containerId, property, values[property]));
                }
                return true;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                return false;
            }
        }

        /** Vanilla smoker/furnace window data slots 0..3, in protocol order. */
        static int[] windowData(int burn, int burnTotal, int cook, int cookTotal) {
            return new int[] {
                    Math.max(0, burn),
                    Math.max(1, burnTotal),
                    Math.max(0, cook),
                    Math.max(1, cookTotal)
            };
        }

        private static Constructor<?> packetConstructor() {
            try {
                Class<?> packet = Class.forName(
                        "net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket");
                return packet.getConstructor(int.class, int.class, int.class);
            } catch (ReflectiveOperationException exception) {
                return null;
            }
        }

        private static Object readField(Object target, String name) throws ReflectiveOperationException {
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                    // NMS player fields can be declared in a superclass.
                }
            }
            throw new NoSuchFieldException(name);
        }
    }
}
