package org.voxelhorizons.furniture;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureInstance;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Bedrock storage interaction safeguard.
 *
 * Geyser/Floodgate input translation can be diagnosed without imposing a
 * Floodgate dependency on Java-only servers. Bedrock storage pickup requires
 * a deliberate sneak + attack when the safety option is enabled. Ordinary
 * Java pickup and non-storage furniture are unchanged.
 */
final class BedrockFurnitureSafety {
    private final JavaPlugin plugin;
    private final FurnitureManager furniture;
    private final boolean requireSneak;
    private final boolean traceEvents;
    private final Object floodgateApi;
    private final Method isFloodgatePlayer;
    private boolean warned;

    BedrockFurnitureSafety(JavaPlugin plugin, FurnitureManager furniture) {
        this.plugin = plugin;
        this.furniture = furniture;
        requireSneak = plugin.getConfig().getBoolean(
                "bedrock.interaction.storage-pickup-requires-sneak", true);
        traceEvents = plugin.getConfig().getBoolean("bedrock.interaction.trace-events", false);
        Object api = null;
        Method method = null;
        Plugin floodgate = plugin.getServer().getPluginManager().getPlugin("floodgate");
        if (floodgate != null && floodgate.isEnabled()) {
            try {
                Class<?> clazz = Class.forName("org.geysermc.floodgate.api.FloodgateApi",
                        true, floodgate.getClass().getClassLoader());
                api = clazz.getMethod("getInstance").invoke(null);
                method = clazz.getMethod("isFloodgatePlayer", UUID.class);
            } catch (ReflectiveOperationException | LinkageError error) {
                plugin.getLogger().warning("Cannot connect Floodgate Bedrock player detection: "
                        + error.getClass().getSimpleName() + " " + error.getMessage());
            }
        } else if (requireSneak || traceEvents) {
            plugin.getLogger().info("Floodgate not installed: Bedrock-only furniture "
                    + "pickup safeguards require Floodgate player detection.");
        }
        floodgateApi = api;
        isFloodgatePlayer = method;
    }

    boolean isBedrock(Player player) {
        if (player == null || floodgateApi == null || isFloodgatePlayer == null) return false;
        try {
            return Boolean.TRUE.equals(isFloodgatePlayer.invoke(floodgateApi, player.getUniqueId()));
        } catch (ReflectiveOperationException | LinkageError error) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Floodgate player detection unavailable; "
                        + "Bedrock storage protection cannot identify client edition: " + error);
            }
            return false;
        }
    }

    static boolean blocksStoragePickup(boolean enabled, boolean bedrock,
                                       boolean hasInventory, boolean sneaking) {
        return enabled && bedrock && hasInventory && !sneaking;
    }

    boolean blockPickup(Player player, FurnitureInstance instance, String source) {
        if (!requireSneak || !isBedrock(player)) return false;
        FurnitureDefinition definition = furniture.definition(instance.definitionId()).orElse(null);
        if (!blocksStoragePickup(requireSneak, true,
                definition != null && definition.hasInventory(), player.isSneaking())) return false;
        if (traceEvents) log(player, instance, "blocked " + source
                + ": Bedrock storage pickup requires sneak + attack");
        return true;
    }

    void trace(Player player, FurnitureInstance instance, String event) {
        if (traceEvents && isBedrock(player)) log(player, instance, event);
    }

    private void log(Player player, FurnitureInstance instance, String event) {
        plugin.getLogger().info("[BedrockFurnitureInput] player=" + player.getName()
                + " instance=" + instance.id() + " definition=" + instance.definitionId()
                + " sneaking=" + player.isSneaking() + " event=" + event);
    }
}
