package org.voxelhorizons.furniture;

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
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.furniture.event.FurnitureInteractEvent;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureInstance;

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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (furniture.byBlock(event.getClickedBlock()).isPresent()) return;
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) return;
        if (event.getBlockFace() != BlockFace.UP) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("voxelfurniture.place")) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        Optional<ContentID> id = core.getItemManager().identify(held);
        if (!id.isPresent()) return;
        Optional<FurnitureDefinition> definition = furniture.definition(id.get());
        if (!definition.isPresent()) return;

        Block target = event.getClickedBlock().getRelative(event.getBlockFace());
        if (target.getType() != Material.AIR) return;
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
            event.setCancelled(true);
            org.bukkit.Bukkit.getPluginManager().callEvent(
                    new FurnitureInteractEvent(event.getPlayer(), instance.get()));
        }
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
        event.setCancelled(true);
        org.bukkit.Bukkit.getPluginManager().callEvent(new FurnitureInteractEvent(event.getPlayer(), instance.get()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (protectEntities && furniture.byEntity(event.getRightClicked().getUniqueId()).isPresent()) {
            event.setCancelled(true);
        }
    }
}
