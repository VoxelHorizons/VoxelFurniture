package org.voxelhorizons.furniture.store;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.furniture.model.FurnitureInstance;
import org.voxelhorizons.furniture.model.FurnitureRendererType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FurnitureStore {
    private final Path file;

    public FurnitureStore(Path file) { this.file = file; }

    public Map<UUID, FurnitureInstance> load() {
        Map<UUID, FurnitureInstance> result = new LinkedHashMap<UUID, FurnitureInstance>();
        if (!Files.exists(file)) return result;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        ConfigurationSection root = yaml.getConfigurationSection("instances");
        if (root == null) return result;
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            UUID id = UUID.fromString(key);
            World world = Bukkit.getWorld(section.getString("world"));
            if (world == null) continue;
            Location location = new Location(world, section.getDouble("x"), section.getDouble("y"),
                    section.getDouble("z"));
            List<UUID> entities = new ArrayList<UUID>();
            for (String entity : section.getStringList("entities")) entities.add(UUID.fromString(entity));
            result.put(id, new FurnitureInstance(id, ContentID.parse(section.getString("definition"), "minecraft"),
                    location, (float) section.getDouble("yaw"),
                    FurnitureRendererType.valueOf(section.getString("renderer")), entities));
        }
        return result;
    }

    public void save(Collection<FurnitureInstance> instances) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 1);
        for (FurnitureInstance instance : instances) {
            String path = "instances." + instance.id();
            Location location = instance.location();
            yaml.set(path + ".definition", instance.definitionId().toString());
            yaml.set(path + ".world", location.getWorld().getName());
            yaml.set(path + ".x", location.getX());
            yaml.set(path + ".y", location.getY());
            yaml.set(path + ".z", location.getZ());
            yaml.set(path + ".yaw", instance.yaw());
            yaml.set(path + ".renderer", instance.renderer().name());
            List<String> entities = new ArrayList<String>();
            for (UUID id : instance.entities()) entities.add(id.toString());
            yaml.set(path + ".entities", entities);
        }
        try {
            Files.createDirectories(file.getParent());
            yaml.save(file.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save furniture instances to " + file, exception);
        }
    }
}
