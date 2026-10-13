package org.voxelhorizons.furniture;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.flags.type.Flags;
import me.angeschossen.lands.api.flags.type.RoleFlag;
import me.angeschossen.lands.api.land.LandWorld;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.voxelhorizons.furniture.event.FurnitureBreakEvent;
import org.voxelhorizons.furniture.event.FurnitureInteractEvent;
import org.voxelhorizons.furniture.event.FurniturePlaceEvent;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureInstance;

/** Optional region integrations. Both protection systems must allow an operation. */
public final class FurnitureProtection implements Listener {
    public enum Action { PLACE, BREAK, INTERACT, INVENTORY, DYE }

    private final JavaPlugin plugin;
    private final boolean worldGuard;
    private final boolean landsEnabled;
    private final LandsIntegration lands;

    public static void registerWorldGuardFlags(JavaPlugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null
                && plugin.getConfig().getBoolean("protection.worldguard", true)) {
            WorldGuardHook.register();
        }
    }

    public FurnitureProtection(JavaPlugin plugin) {
        this.plugin = plugin;
        this.worldGuard = plugin.getConfig().getBoolean("protection.worldguard", true)
                && Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
        this.landsEnabled = plugin.getConfig().getBoolean("protection.lands", true)
                && Bukkit.getPluginManager().isPluginEnabled("Lands");
        this.lands = landsEnabled ? LandsHook.connect(plugin) : null;
        plugin.getLogger().info("Protection: WorldGuard=" + worldGuard + ", Lands=" + landsEnabled);
    }

    public boolean allows(Player player, Location at, Action action) {
        if (player == null || at == null || at.getWorld() == null) return false;
        if (worldGuard && !WorldGuardHook.allows(player, at, action)) return false;
        return !landsEnabled || LandsHook.allows(lands, player, at, action);
    }

    /** Used only to recover events cancelled by WorldGuard, not to un-cancel vanilla use. */
    public boolean explicitWorldGuardAllow(Player player, Location at, Action action) {
        return worldGuard && WorldGuardHook.explicitAllow(player, at, action);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurniturePlace(FurniturePlaceEvent event) {
        if (!allows(event.getPlayer(), event.getLocation(), Action.PLACE)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnitureBreak(FurnitureBreakEvent event) {
        if (!allows(event.getPlayer(), event.getInstance().location(), Action.BREAK)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnitureInteract(FurnitureInteractEvent event) {
        FurnitureInstance instance = event.getInstance();
        FurnitureManager manager = ((VoxelFurniture) plugin).getFurnitureManager();
        FurnitureDefinition definition = manager.definition(instance.definitionId()).orElse(null);
        Action action = definition != null && definition.hasInventory()
                && !definition.hasInteractionCommands() ? Action.INVENTORY : Action.INTERACT;
        if (!allows(event.getPlayer(), instance.location(), action)) event.setCancelled(true);
    }

    private static final class WorldGuardHook {
        private static StateFlag place, breakFlag, interact, inventory, dye;

        static void register() {
            FlagRegistry flags = WorldGuard.getInstance().getFlagRegistry();
            place = register(flags, "voxel-furniture-place");
            breakFlag = register(flags, "voxel-furniture-break");
            interact = register(flags, "voxel-furniture-interact");
            inventory = register(flags, "voxel-furniture-inventory");
            dye = register(flags, "voxel-furniture-dye");
        }

        private static StateFlag register(FlagRegistry registry, String name) {
            try {
                StateFlag flag = new StateFlag(name, false);
                registry.register(flag);
                return flag;
            } catch (FlagConflictException conflict) {
                Flag<?> flag = registry.get(name);
                if (flag instanceof StateFlag) return (StateFlag) flag;
                throw new IllegalStateException("WorldGuard flag conflict for " + name, conflict);
            }
        }

        private static StateFlag forAction(Action action) {
            switch (action) {
                case PLACE: return place;
                case BREAK: return breakFlag;
                case INVENTORY: return inventory;
                case DYE: return dye;
                default: return interact;
            }
        }

        private static RegionQuery query() {
            return WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        }

        private static boolean explicitAllow(Player player, Location at, Action action) {
            StateFlag flag = forAction(action);
            return flag != null && query().queryState(BukkitAdapter.adapt(at),
                    WorldGuardPlugin.inst().wrapPlayer(player), flag) == StateFlag.State.ALLOW;
        }

        private static boolean allows(Player player, Location at, Action action) {
            StateFlag flag = forAction(action);
            if (flag == null) return false; // registration failure must not open protected regions
            com.sk89q.worldedit.util.Location worldEditLocation = BukkitAdapter.adapt(at);
            com.sk89q.worldguard.LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            RegionQuery regions = query();
            if (action == Action.INVENTORY) {
                // An explicit chest-access deny always blocks storage, even if the
                // general furniture-interact flag was permitted for Hub decorations.
                if (regions.queryState(worldEditLocation, localPlayer,
                        com.sk89q.worldguard.protection.flags.Flags.CHEST_ACCESS) == StateFlag.State.DENY)
                    return false;
            }
            return regions.testBuild(worldEditLocation, localPlayer, flag);
        }
    }

    private static final class LandsHook {
        private static LandsIntegration connect(JavaPlugin plugin) {
            return LandsIntegration.of(plugin);
        }

        private static boolean allows(LandsIntegration api, Player player, Location at, Action action) {
            LandWorld world = api.getWorld(at.getWorld());
            if (world == null) return true;
            RoleFlag flag;
            switch (action) {
                case PLACE: flag = Flags.BLOCK_PLACE; break;
                case BREAK:
                case DYE: flag = Flags.BLOCK_BREAK; break;
                case INVENTORY: flag = Flags.INTERACT_CONTAINER; break;
                default: flag = Flags.INTERACT_GENERAL; break;
            }
            // Use chest semantics for storage, respecting area trusts and sub-areas.
            Material material = action == Action.INVENTORY ? Material.CHEST : null;
            return world.hasRoleFlag(api.getLandPlayer(player.getUniqueId()), at, flag, material, false);
        }
    }
}
