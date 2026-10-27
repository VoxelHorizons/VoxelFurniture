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

    /**
     * Only recover interactions/inventories explicitly allowed by the active region
     * policy. A cancelled PLACE/BREAK/DYE event must never be reopened by a custom
     * state flag; other plugins can have cancelled those events for independent reasons.
     */
    public boolean explicitWorldGuardAllow(Player player, Location at, Action action) {
        if (action != Action.INTERACT && action != Action.INVENTORY) return false;
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

    // Keep all WorldGuard-specific types in this nested class: on 1.12.2 or
    // servers without WorldGuard, the outer protection hook must still load.
    static final class WorldGuardHook {
        private static StateFlag place, breakFlag, interact, inventory, dye;

        /** A custom ALLOW never overrides BUILD when physically changing furniture. */
        static boolean structuralAllowed(boolean buildAllowed, StateFlag.State customState) {
            return buildAllowed && customState != StateFlag.State.DENY;
        }

        /** An unset inventory rule inherits the interaction policy. */
        static StateFlag inventoryFallback(StateFlag.State inventoryState, StateFlag.State interactState) {
            return inventoryState == null ? interactState : inventoryState;
        }

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
            if (flag == null || player == null || at == null || at.getWorld() == null) return false;
            RegionQuery regions = query();
            com.sk89q.worldedit.util.Location location = worldEditLocation(at);
            com.sk89q.worldguard.LocalPlayer local = WorldGuardPlugin.inst().wrapPlayer(player);
            StateFlag.State state = regions.queryState(location, local, flag);
            if (action == Action.INVENTORY && interact != null) {
                // A Hub granting interactions can also open public storage until
                // an explicit furniture-inventory flag changes that policy.
                state = inventoryFallback(state, regions.queryState(location, local, interact));
            }
            return state == StateFlag.State.ALLOW;
        }

        private static boolean allows(Player player, Location at, Action action) {
            StateFlag flag = forAction(action);
            if (flag == null) return false; // an unregistered flag must not grant access
            com.sk89q.worldedit.util.Location location = worldEditLocation(at);
            com.sk89q.worldguard.LocalPlayer local = WorldGuardPlugin.inst().wrapPlayer(player);
            RegionQuery regions = query();
            // Vanilla WorldGuard checks bypass separately from region queries.
            // Match that behavior for operators and explicitly exempt players.
            if (WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(
                    local, new BukkitWorld(at.getWorld()))) return true;

            if (action == Action.PLACE || action == Action.BREAK || action == Action.DYE) {
                // WorldGuard's testBuild(location, player, customFlag) intentionally
                // permits a custom ALLOW to override vanilla BUILD DENY. That is
                // unsafe for furniture's physical mutation in protected Hubs.
                // Use BUILD and the dedicated flag as independent restrictions.
                boolean canBuild = regions.testState(location, local,
                        com.sk89q.worldguard.protection.flags.Flags.BUILD);
                StateFlag.State customState = regions.queryState(location, local, flag);
                return structuralAllowed(canBuild, customState);
            }

            if (action == Action.INVENTORY) {
                // A specific inventory rule takes precedence. When unset, inherit
                // the interaction flag, so public Hub seating/storage work together.
                // CHEST_ACCESS remains an independent explicit deny.
                if (regions.queryState(location, local,
                        com.sk89q.worldguard.protection.flags.Flags.CHEST_ACCESS) == StateFlag.State.DENY)
                    return false;
                if (interact == null) return false;
                StateFlag.State inventoryState = regions.queryState(location, local, inventory);
                if (inventoryState == StateFlag.State.DENY) return false;
                if (inventoryState == StateFlag.State.ALLOW) return true;
                return regions.testBuild(location, local, interact);
            }
            // Interactions may be explicitly allowed in a build-protected Hub.
            return regions.testBuild(location, local, flag);
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
