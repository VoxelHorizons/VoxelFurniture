package org.voxelhorizons.furniture.store;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.furniture.model.FurnitureInstance;
import org.voxelhorizons.furniture.model.FurnitureBlockPosition;
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
            List<FurnitureBlockPosition> blocks = new ArrayList<FurnitureBlockPosition>();
            for (Map<?, ?> block : section.getMapList("blocks")) {
                Material material = Material.matchMaterial(String.valueOf(block.get("material")));
                if (material == null) continue;
                blocks.add(new FurnitureBlockPosition(integer(block.get("x")), integer(block.get("y")),
                        integer(block.get("z")), material));
            }
            result.put(id, new FurnitureInstance(id, ContentID.parse(section.getString("definition"), "minecraft"),
                    location, (float) section.getDouble("yaw"),
                    FurnitureRendererType.valueOf(section.getString("renderer")), entities, blocks,
                    section.contains("rendered_model") ? ContentID.parse(section.getString("rendered_model"), "minecraft") : null,
                    section.contains("rendered_yaw") ? (float) section.getDouble("rendered_yaw") : Float.NaN,
                    section.getString("render_signature")));
        }
        return result;
    }

    public void save(Collection<FurnitureInstance> instances) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 2);
        for (FurnitureInstance instance : instances) {
            String path = "instances." + instance.id();
            Location location = instance.location();
            yaml.set(path + ".definition", instance.definitionId().toString());
            yaml.set(path + ".world", location.getWorld().getName());
            yaml.set(path + ".x", location.getX());
            yaml.set(path + ".y", location.getY());
            yaml.set(path + ".z", location.getZ());
            yaml.set(path + ".yaw", instance.yaw());
            if (instance.renderedModel() != null) yaml.set(path + ".rendered_model", instance.renderedModel().toString());
            if (!Float.isNaN(instance.renderedYaw())) yaml.set(path + ".rendered_yaw", instance.renderedYaw());
            if (instance.renderSignature() != null) yaml.set(path + ".render_signature", instance.renderSignature());
            yaml.set(path + ".renderer", instance.renderer().name());
            List<String> entities = new ArrayList<String>();
            for (UUID id : instance.entities()) entities.add(id.toString());
            yaml.set(path + ".entities", entities);
            List<Map<String, Object>> blocks = new ArrayList<Map<String, Object>>();
            for (FurnitureBlockPosition position : instance.blocks()) {
                Map<String, Object> block = new LinkedHashMap<String, Object>();
                block.put("x", position.x());
                block.put("y", position.y());
                block.put("z", position.z());
                block.put("material", position.material().name());
                blocks.add(block);
            }
            yaml.set(path + ".blocks", blocks);
        }
        try {
            Files.createDirectories(file.getParent());
            yaml.save(file.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save furniture instances to " + file, exception);
        }
    }

    private static int integer(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
    }
}
