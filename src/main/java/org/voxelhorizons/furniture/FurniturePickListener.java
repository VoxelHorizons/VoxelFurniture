package org.voxelhorizons.furniture;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.furniture.model.FurnitureInstance;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

/** Bridges Paper's optional pick-item events without linking the universal jar against Paper. */
public final class FurniturePickListener implements Listener {
    private static final String PICK_BLOCK_EVENT =
            "io.papermc.paper.event.player.PlayerPickBlockEvent";
    private static final String PICK_ENTITY_EVENT =
            "io.papermc.paper.event.player.PlayerPickEntityEvent";

    private final Plugin plugin;
    private final VoxelCore core;
    private final FurnitureManager furniture;

    public FurniturePickListener(Plugin plugin, VoxelCore core, FurnitureManager furniture) {
        this.plugin = plugin;
        this.core = core;
        this.furniture = furniture;
    }

    public boolean register() {
        boolean block = register(PICK_BLOCK_EVENT, "getBlock");
        boolean entity = register(PICK_ENTITY_EVENT, "getEntity");
        if (block || entity) {
            plugin.getLogger().info("Enabled Creative pick-block support for furniture.");
        } else {
            plugin.getLogger().info("Creative pick-block events are unavailable on this server version.");
        }
        return block || entity;
    }

    @SuppressWarnings("unchecked")
    private boolean register(String eventName, String targetMethod) {
        try {
            Class<?> rawEvent = Class.forName(eventName);
            final Method getPlayer = rawEvent.getMethod("getPlayer");
            final Method getTarget = rawEvent.getMethod(targetMethod);
            final Method getTargetSlot = rawEvent.getMethod("getTargetSlot");
            final Method setCancelled = rawEvent.getMethod("setCancelled", Boolean.TYPE);
            plugin.getServer().getPluginManager().registerEvent((Class<? extends Event>) rawEvent, this,
                    EventPriority.HIGHEST, new EventExecutor() {
                        @Override public void execute(Listener listener, Event event) throws EventException {
                            try {
                                Player player = (Player) getPlayer.invoke(event);
                                Object target = getTarget.invoke(event);
                                Optional<FurnitureInstance> instance = target instanceof Block
                                        ? furniture.byBlock((Block) target)
                                        : furniture.byEntity(((Entity) target).getUniqueId());
                                if (player.getGameMode() != GameMode.CREATIVE || !instance.isPresent()) return;

                                int targetSlot = ((Number) getTargetSlot.invoke(event)).intValue();
                                setCancelled.invoke(event, true);
                                schedulePick(player, instance.get().definitionId(), targetSlot);
                            } catch (IllegalAccessException exception) {
                                throw new EventException(exception);
                            } catch (InvocationTargetException exception) {
                                throw new EventException(exception.getCause());
                            }
                        }
                    }, plugin, true);
            return true;
        } catch (ClassNotFoundException unavailable) {
            return false;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("Unable to register " + eventName + ": " + exception.getMessage());
            return false;
        }
    }

    private void schedulePick(final Player player, final ContentID furnitureId, final int targetSlot) {
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                if (!player.isOnline() || player.getGameMode() != GameMode.CREATIVE) return;
                int sourceSlot = findFurniture(player, furnitureId);
                if (sourceSlot >= 0 && sourceSlot != targetSlot) {
                    ItemStack target = player.getInventory().getItem(targetSlot);
                    player.getInventory().setItem(targetSlot, player.getInventory().getItem(sourceSlot));
                    player.getInventory().setItem(sourceSlot, target);
                } else if (sourceSlot < 0) {
                    player.getInventory().setItem(targetSlot, core.getItemManager().createItem(furnitureId));
                }
                player.getInventory().setHeldItemSlot(targetSlot);
                player.updateInventory();
            }
        });
    }

    private int findFurniture(Player player, ContentID furnitureId) {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null) continue;
            Optional<ContentID> id = core.getItemManager().identify(item);
            if (id.isPresent() && id.get().equals(furnitureId)) return slot;
        }
        return -1;
    }
}
