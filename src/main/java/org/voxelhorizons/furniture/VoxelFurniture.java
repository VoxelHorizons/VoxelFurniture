package org.voxelhorizons.furniture;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.furniture.model.FurnitureDefinitionParser;
import org.voxelhorizons.furniture.model.FurnitureRendererType;
import org.voxelhorizons.furniture.render.FurnitureRendererSelector;
import org.voxelhorizons.furniture.store.FurnitureStore;

import java.util.logging.Level;

public final class VoxelFurniture extends JavaPlugin {
    private FurnitureManager furnitureManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Plugin dependency = getServer().getPluginManager().getPlugin("VoxelCore");
        if (!(dependency instanceof VoxelCore) || !dependency.isEnabled()) {
            getLogger().severe("VoxelCore is required and must be enabled before VoxelFurniture.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            VoxelCore core = (VoxelCore) dependency;
            FurnitureRendererType renderer = FurnitureRendererType.parse(getConfig().getString("renderer"),
                    FurnitureRendererType.AUTO);
            float rotationStep = (float) getConfig().getDouble("default-rotation-step", 45.0D);
            FurnitureDefinitionParser parser = new FurnitureDefinitionParser(renderer, rotationStep);
            furnitureManager = new FurnitureManager(this, core, parser, new FurnitureRendererSelector(core),
                    new FurnitureStore(getDataFolder().toPath().resolve("furniture.yml")));
            furnitureManager.validateDefinitions();
            furnitureManager.synchronizeDefinitions();
            getServer().getPluginManager().registerEvents(new FurnitureListener(core, furnitureManager,
                    getConfig().getBoolean("protect-entities", true)), this);
            new FurniturePickListener(this, core, furnitureManager).register();

            FurnitureCommand executor = new FurnitureCommand(core, furnitureManager);
            PluginCommand command = getCommand("voxelfurniture");
            if (command == null) throw new IllegalStateException("voxelfurniture command is missing from plugin.yml");
            command.setExecutor(executor);
            command.setTabCompleter(executor);
            getLogger().info("VoxelFurniture ready with " + furnitureManager.definitions().size()
                    + " definitions and " + furnitureManager.instances().size() + " placed instances.");
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "VoxelFurniture failed to initialize: " + exception.getMessage(), exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (furnitureManager != null) furnitureManager.shutdown();
    }

    public FurnitureManager getFurnitureManager() { return furnitureManager; }
}
