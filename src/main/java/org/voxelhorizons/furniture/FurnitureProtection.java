package org.voxelhorizons.furniture;

import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionQuery;
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
import org.voxelhorizons.furniture.model.FurnitureBlockPosition;
import java.util.List;

/** Optional region integrations. Both protection systems must allow an operation. */
public final class FurnitureProtection implements Listener {
    public enum Action { PLACE, BREAK, INTERACT, INVENTORY, DYE }

    private final JavaPlugin plugin;
    private final boolean worldGuard;
    private final boolean landsEnabled;
    private final LandsHook lands;

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
        return !landsEnabled || lands.allows(player, at, action);
    }

    /** Apply protection to every occupied collision block, not just the furniture origin. */
    public boolean allowsFootprint(Player player, Location origin,
                                   List<FurnitureBlockPosition> positions, Action action) {
        if (!allows(player, origin, action)) return false;
        for (FurnitureBlockPosition block : positions) {
            Location target = new Location(origin.getWorld(), block.x(), block.y(), block.z());
            if (!allows(player, target, action)) return false;
        }
        return true;
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
        if (!allowsFootprint(event.getPlayer(), event.getInstance().location(),
                event.getInstance().blocks(), Action.BREAK)) event.setCancelled(true);
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

        private static com.sk89q.worldedit.util.Location worldEditLocation(Location at) {
            return new com.sk89q.worldedit.util.Location(new BukkitWorld(at.getWorld()),
                    at.getX(), at.getY(), at.getZ());
        }

        private static RegionQuery query() {
            return WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        }

        private static boolean explicitAllow(Player player, Location at, Action action) {
            StateFlag flag = forAction(action);
            return flag != null && query().queryState(worldEditLocation(at),
                    WorldGuardPlugin.inst().wrapPlayer(player), flag) == StateFlag.State.ALLOW;
        }

        private static boolean allows(Player player, Location at, Action action) {
            StateFlag flag = forAction(action);
            if (flag == null) return false; // registration failure must not open protected regions
            com.sk89q.worldedit.util.Location worldEditLocation = worldEditLocation(at);
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

    /**
     * Late-bound public Lands API. The Lands Maven artifact is not publicly
     * resolvable on all build runners; an isolated runtime hook retains a
     * genuine Lands role check without bundling or shading their classes.
     * A changed/incompatible API fails closed instead of granting access.
     */
    private static final class LandsHook {
        private final JavaPlugin plugin;
        private final Object api;
        private final java.lang.reflect.Method getWorld;
        private final java.lang.reflect.Method getLandPlayer;
        private final java.lang.reflect.Method hasRoleFlag;
        private final java.util.Map<String,Object> flags = new java.util.HashMap<String,Object>();
        private boolean warned;

        private LandsHook(JavaPlugin plugin) throws ReflectiveOperationException {
            this.plugin = plugin;
            ClassLoader loader = Bukkit.getPluginManager().getPlugin("Lands").getClass().getClassLoader();
            Class<?> integration = Class.forName("me.angeschossen.lands.api.LandsIntegration", true, loader);
            Class<?> landWorld = Class.forName("me.angeschossen.lands.api.land.LandWorld", true, loader);
            Class<?> landPlayer = Class.forName("me.angeschossen.lands.api.player.LandPlayer", true, loader);
            Class<?> flagClass = Class.forName("me.angeschossen.lands.api.flags.type.RoleFlag", true, loader);
            Class<?> registry = Class.forName("me.angeschossen.lands.api.flags.type.Flags", true, loader);
            api = integration.getMethod("of", org.bukkit.plugin.Plugin.class).invoke(null, plugin);
            getWorld = integration.getMethod("getWorld", org.bukkit.World.class);
            getLandPlayer = integration.getMethod("getLandPlayer", java.util.UUID.class);
            hasRoleFlag = landWorld.getMethod("hasRoleFlag", landPlayer, Location.class,
                    flagClass, Material.class, boolean.class);
            for (String key : new String[] {"BLOCK_PLACE", "BLOCK_BREAK",
                    "INTERACT_CONTAINER", "INTERACT_GENERAL", "VEHICLE_USE"}) {
                flags.put(key, registry.getField(key).get(null));
            }
        }

        private static LandsHook connect(JavaPlugin plugin) {
            try {
                return new LandsHook(plugin);
            } catch (ReflectiveOperationException | LinkageError error) {
                throw new IllegalStateException("Cannot initialize installed Lands API safely", error);
            }
        }

        private boolean allows(Player player, Location at, Action action) {
            String flagName;
            switch (action) {
                case PLACE: flagName = "BLOCK_PLACE"; break;
                case BREAK: flagName = "BLOCK_BREAK"; break;
                case DYE: flagName = "BLOCK_BREAK"; break;
                case INVENTORY: flagName = "INTERACT_CONTAINER"; break;
                case INTERACT: flagName = "INTERACT_GENERAL"; break;
                default: return false;
            }
            try {
                Object world = getWorld.invoke(api, at.getWorld());
                if (world == null) return true; // not a managed Lands world
                Object landPlayer = getLandPlayer.invoke(api, player.getUniqueId());
                Object state = hasRoleFlag.invoke(world, landPlayer, at, flags.get(flagName),
                        action == Action.INVENTORY ? Material.CHEST : null, Boolean.FALSE);
                return Boolean.TRUE.equals(state);
            } catch (ReflectiveOperationException | LinkageError error) {
                if (!warned) {
                    warned = true;
                    plugin.getLogger().warning("Lands API check failed (denying action): " + error);
                }
                return false;
            }
        }
    }
}
