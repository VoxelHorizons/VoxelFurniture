package org.voxelhorizons.furniture;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.item.DyeableItemListener;
import org.voxelhorizons.furniture.event.FurnitureInteractEvent;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureInstance;
import org.voxelhorizons.furniture.model.FurnitureInteractionCommand;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Iterator;

public final class FurnitureListener implements Listener {
    private final VoxelCore core;
    private final FurnitureManager furniture;
    private final boolean protectEntities;

    public FurnitureListener(VoxelCore core, FurnitureManager furniture, boolean protectEntities) {
        this.core = core;
        this.furniture = furniture;
        this.protectEntities = protectEntities;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        furniture.onChunkLoad(event.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (furniture.byBlock(event.getClickedBlock()).isPresent()) return;
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        Optional<ContentID> id = core.getItemManager().identify(held);
        if (!id.isPresent()) return;
        Optional<FurnitureDefinition> definition = furniture.definition(id.get());
        if (!definition.isPresent()) return;

        // A recognized furniture item owns this interaction. Always cancel the vanilla
        // item use so denied/failed furniture placement can never place its carrier block.
        event.setCancelled(true);
        if (!player.hasPermission("voxelfurniture.place")) return;
        if (!definition.get().placement().allows(event.getBlockFace())) return;

        Block target = event.getClickedBlock().getRelative(event.getBlockFace());
        if (!FurnitureManager.isReplaceable(target.getType())) return;
        Location location = target.getLocation().add(0.5D, 0.0D, 0.5D);
        if (!furniture.place(player, definition.get(), location).isPresent()) return;
        event.setCancelled(true);
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            if (held.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
            else held.setAmount(held.getAmount() - 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCollisionInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Optional<FurnitureInstance> instance = furniture.byBlock(event.getClickedBlock());
        if (!instance.isPresent()) return;
        if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (event.getPlayer().hasPermission("voxelfurniture.break")) {
                furniture.breakFurniture(event.getPlayer(), instance.get());
            }
        } else if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            if (tryDye(event.getPlayer(), instance.get(), event.getItem())) { event.setCancelled(true); return; }
            FurnitureDefinition definition = furniture.definition(instance.get().definitionId()).orElse(null);
            if (allowsBlockPlacement(event.getPlayer(), event.getItem(), definition)) return;
            event.setCancelled(true);
            interact(event.getPlayer(), instance.get());
        }
    }

    private boolean allowsBlockPlacement(Player player, ItemStack held, FurnitureDefinition definition) {
        if (definition == null) return false;
        if (definition.hasInventory() && !player.isSneaking()) return false;
        if (held == null || held.getType() == Material.AIR || !held.getType().isBlock()) return false;

        Optional<ContentID> heldId = core.getItemManager().identify(held);
        return !heldId.isPresent() || !furniture.definition(heldId.get()).isPresent();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCollisionBreak(BlockBreakEvent event) {
        Optional<FurnitureInstance> instance = furniture.byBlock(event.getBlock());
        if (!instance.isPresent()) return;
        event.setCancelled(true);
        if (event.getPlayer().hasPermission("voxelfurniture.break")) {
            furniture.breakFurniture(event.getPlayer(), instance.get());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (furniture.byBlock(event.getToBlock()).isPresent()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (furniture.byBlock(event.getBlock()).isPresent()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (furniture.byBlock(block).isPresent()
                    || furniture.byBlock(block.getRelative(event.getDirection())).isPresent()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (furniture.byBlock(block).isPresent()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) { protectExplosion(event.blockList().iterator()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) { protectExplosion(event.blockList().iterator()); }

    private void protectExplosion(Iterator<Block> blocks) {
        while (blocks.hasNext()) {
            if (furniture.byBlock(blocks.next()).isPresent()) blocks.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Optional<FurnitureInstance> instance = furniture.byEntity(event.getEntity().getUniqueId());
        if (!instance.isPresent()) return;
        event.setCancelled(true);
        if (!(event instanceof EntityDamageByEntityEvent)) return;
        EntityDamageByEntityEvent byEntity = (EntityDamageByEntityEvent) event;
        if (!(byEntity.getDamager() instanceof Player)) return;
        Player player = (Player) byEntity.getDamager();
        if (player.hasPermission("voxelfurniture.break")) furniture.breakFurniture(player, instance.get());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        Optional<FurnitureInstance> instance = furniture.byEntity(event.getRightClicked().getUniqueId());
        if (!instance.isPresent()) return;
        ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        if (tryDye(event.getPlayer(), instance.get(), held)) { event.setCancelled(true); return; }
        FurnitureDefinition definition = furniture.definition(instance.get().definitionId()).orElse(null);
        if (allowsBlockPlacement(event.getPlayer(), held, definition)) {
            return;
        }
        event.setCancelled(true);
        interact(event.getPlayer(), instance.get());
    }

    private void interact(Player player, FurnitureInstance instance) {
        FurnitureInteractEvent interaction = new FurnitureInteractEvent(player, instance);
        org.bukkit.Bukkit.getPluginManager().callEvent(interaction);
        if (interaction.isCancelled()) return;
        FurnitureDefinition definition = furniture.definition(instance.definitionId()).orElse(null);
        if (definition != null && definition.hasInteractionCommands()
                && player.hasPermission("voxelfurniture.interaction.commands")) {
            executeCommands(player, instance, definition);
            return;
        }
        if (definition != null && !definition.hasInventory() && definition.animationUseModel() != null
                && furniture.toggleUseAnimation(instance)) return;
        if (player.hasPermission("voxelfurniture.inventory") && furniture.openInventory(player, instance)) return;
        if (player.hasPermission("voxelfurniture.sit")) furniture.sit(player, instance);
    }

    private void executeCommands(Player player, FurnitureInstance instance, FurnitureDefinition definition) {
        for (FurnitureInteractionCommand configured : definition.interactionCommands()) {
            String command = resolveCommand(player, instance, configured.command());
            if (configured.executor() == FurnitureInteractionCommand.Executor.CONSOLE) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } else {
                Bukkit.dispatchCommand(player, command);
            }
        }
    }

    private String resolveCommand(Player player, FurnitureInstance instance, String input) {
        Location location = instance.location();
        String resolved = input
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString())
                .replace("{world}", location.getWorld() == null ? "" : location.getWorld().getName())
                .replace("{x}", String.valueOf(location.getBlockX()))
                .replace("{y}", String.valueOf(location.getBlockY()))
                .replace("{z}", String.valueOf(location.getBlockZ()))
                .replace("{furniture_id}", instance.definitionId().toString());

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return resolved;
        try {
            Class<?> placeholderApi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method method = placeholderApi.getMethod("setPlaceholders", Player.class, String.class);
            Object expanded = method.invoke(null, player, resolved);
            return expanded instanceof String ? (String) expanded : resolved;
        } catch (ReflectiveOperationException ignored) {
            return resolved;
        }
    }

    private boolean tryDye(Player player, FurnitureInstance instance, ItemStack held) {
        Integer color = DyeableItemListener.dyeColor(held);
        if (color == null || !player.hasPermission("voxelfurniture.dye")) return false;
        if (!furniture.dye(instance, color.intValue())) return false;
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            if (held.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
            else held.setAmount(held.getAmount() - 1);
        }
        return true;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        furniture.closeInventory(event.getInventory());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (protectEntities && furniture.byEntity(event.getRightClicked().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }
}
