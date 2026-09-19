package org.voxelhorizons.furniture;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.furniture.event.FurnitureBreakEvent;
import org.voxelhorizons.furniture.event.FurniturePlaceEvent;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureBlockDefinition;
import org.voxelhorizons.furniture.model.FurnitureBlockPosition;
import org.voxelhorizons.furniture.model.FurnitureDefinitionParser;
import org.voxelhorizons.furniture.model.FurnitureInstance;
import org.voxelhorizons.furniture.model.FurnitureStateSelector;
import org.voxelhorizons.furniture.model.FurnitureStateRule;
import org.voxelhorizons.furniture.render.FurnitureRenderer;
import org.voxelhorizons.furniture.render.FurnitureRendererSelector;
import org.voxelhorizons.furniture.store.FurnitureStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

public final class FurnitureManager {
    private final VoxelCore core;
    private final FurnitureDefinitionParser definitions;
    private final FurnitureRendererSelector renderers;
    private final FurnitureStore store;
    private final Map<UUID, FurnitureInstance> instances;
    private final Map<UUID, UUID> entityIndex = new LinkedHashMap<UUID, UUID>();
    private final Map<BlockKey, UUID> blockIndex = new LinkedHashMap<BlockKey, UUID>();
    private final Map<BlockKey, UUID> originIndex = new LinkedHashMap<BlockKey, UUID>();

    public FurnitureManager(VoxelCore core, FurnitureDefinitionParser definitions,
                            FurnitureRendererSelector renderers, FurnitureStore store) {
        this.core = core;
        this.definitions = definitions;
        this.renderers = renderers;
        this.store = store;
        this.instances = new LinkedHashMap<UUID, FurnitureInstance>(store.load());
        rebuildIndex();
    }

    public Optional<FurnitureDefinition> definition(ContentID id) {
        Optional<ItemDefinition> item = core.getItemManager().getDefinition(id);
        return item.isPresent() && !item.get().abstractDefinition()
                ? definitions.parse(item.get()) : Optional.<FurnitureDefinition>empty();
    }

    public Map<ContentID, FurnitureDefinition> definitions() {
        Map<ContentID, FurnitureDefinition> result = new LinkedHashMap<ContentID, FurnitureDefinition>();
        for (ItemDefinition item : core.getItemRegistry().entries().values()) {
            if (item.abstractDefinition()) continue;
            Optional<FurnitureDefinition> definition = definitions.parse(item);
            if (definition.isPresent()) result.put(item.id(), definition.get());
        }
        return Collections.unmodifiableMap(result);
    }

    public Optional<FurnitureInstance> byEntity(UUID entityId) {
        UUID instanceId = entityIndex.get(entityId);
        return Optional.ofNullable(instanceId == null ? null : instances.get(instanceId));
    }

    public Optional<FurnitureInstance> byBlock(Block block) {
        UUID instanceId = blockIndex.get(BlockKey.of(block));
        return Optional.ofNullable(instanceId == null ? null : instances.get(instanceId));
    }

    public Collection<FurnitureInstance> instances() {
        return Collections.unmodifiableCollection(new ArrayList<FurnitureInstance>(instances.values()));
    }

    public Optional<FurnitureInstance> place(Player player, FurnitureDefinition definition, Location location) {
        float yaw = snapYaw(player.getLocation().getYaw(), definition.rotationStep());
        List<FurnitureBlockPosition> collisionBlocks = resolveBlocks(definition.blocks(), location, yaw);
        BlockKey origin = BlockKey.of(location);
        if (originIndex.containsKey(origin) || blockIndex.containsKey(origin)
                || !canPlace(location.getWorld(), collisionBlocks)) return Optional.empty();
        FurniturePlaceEvent event = new FurniturePlaceEvent(player, definition, location);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return Optional.empty();
        FurnitureStateSelector.Selection state = state(definition, location, yaw);
        ItemStack modelItem = core.getItemManager().createRenderItem(state.model());
        FurnitureRenderer renderer = renderers.select(definition.renderer());
        List<FurnitureBlockPosition> placed = new ArrayList<FurnitureBlockPosition>();
        List<UUID> entities = Collections.emptyList();
        FurnitureInstance instance = null;
        try {
            placeBlocks(location.getWorld(), collisionBlocks, placed);
            entities = renderer.spawn(location, state.yaw(), modelItem, definition);
            instance = new FurnitureInstance(UUID.randomUUID(), definition.itemId(), location, yaw,
                    renderer.type(), entities, collisionBlocks, state.model(), state.yaw());
            instances.put(instance.id(), instance);
            index(instance);
            save();
            refreshAround(location);
            return Optional.of(instance);
        } catch (RuntimeException exception) {
            if (instance != null) {
                instances.remove(instance.id());
                unindex(instance);
            }
            renderer.remove(entities);
            removeBlocks(location.getWorld(), placed);
            throw exception;
        }
    }

    public boolean breakFurniture(Player player, FurnitureInstance instance) {
        FurnitureBreakEvent event = new FurnitureBreakEvent(player, instance);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        FurnitureDefinition definition = definition(instance.definitionId()).orElse(null);
        renderers.select(instance.renderer()).remove(instance.entities());
        removeBlocks(instance.location().getWorld(), instance.blocks());
        instances.remove(instance.id());
        unindex(instance);
        refreshAround(instance.location());
        if (definition != null && player.getGameMode() != GameMode.CREATIVE) {
            instance.location().getWorld().dropItemNaturally(instance.location(),
                    core.getItemManager().createItem(definition.dropItemId()));
        }
        save();
        return true;
    }

    public boolean remove(UUID instanceId, boolean drop) {
        FurnitureInstance instance = instances.remove(instanceId);
        if (instance == null) return false;
        renderers.select(instance.renderer()).remove(instance.entities());
        removeBlocks(instance.location().getWorld(), instance.blocks());
        unindex(instance);
        refreshAround(instance.location());
        if (drop) {
            FurnitureDefinition definition = definition(instance.definitionId()).orElse(null);
            if (definition != null) instance.location().getWorld().dropItemNaturally(instance.location(),
                    core.getItemManager().createItem(definition.dropItemId()));
        }
        save();
        return true;
    }

    public void validateDefinitions() {
        Map<ContentID, FurnitureDefinition> parsed = definitions();
        for (FurnitureDefinition furniture : parsed.values()) {
            if (!renderable(furniture.modelItemId())) {
                throw new IllegalArgumentException("Furniture " + furniture.itemId() + " references unknown model_item "
                        + furniture.modelItemId());
            }
            if (!core.getItemManager().hasItem(furniture.dropItemId())
                    || core.getItemManager().getDefinition(furniture.dropItemId()).get().abstractDefinition()) {
                throw new IllegalArgumentException("Furniture " + furniture.itemId() + " references unknown drop "
                        + furniture.dropItemId());
            }
            renderers.select(furniture.renderer());
            for (FurnitureStateRule rule : furniture.states()) {
                if (!renderable(rule.model())) throw new IllegalArgumentException("Furniture " + furniture.itemId()
                        + " references unrenderable blockstate model " + rule.model());
            }
        }
    }

    private boolean renderable(ContentID id) {
        Optional<ItemDefinition> item = core.getItemManager().getDefinition(id);
        return item.isPresent() && item.get().material() != null && item.get().render() != null
                && item.get().render().model() != null;
    }

    /** Refreshes loaded furniture after content reload or restart. */
    public void refreshStates() {
        for (FurnitureInstance instance : new ArrayList<FurnitureInstance>(instances.values())) refresh(instance);
    }

    private FurnitureStateSelector.Selection state(FurnitureDefinition definition, Location location, float yaw) {
        int mask = 0;
        int alignedMask = 0;
        int[][] offsets = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int index = 0; index < offsets.length; index++) {
            UUID id = originIndex.get(new BlockKey(location.getWorld().getUID(), location.getBlockX() + offsets[index][0],
                    location.getBlockY(), location.getBlockZ() + offsets[index][1]));
            FurnitureInstance neighbor = id == null ? null : instances.get(id);
            if (neighbor != null && neighbor.definitionId().equals(definition.itemId())) {
                mask |= 1 << index;
                if (neighbor.yaw() == yaw) alignedMask |= 1 << index;
            }
        }
        return FurnitureStateSelector.select(definition, mask, alignedMask, yaw);
    }

    private void refreshAround(Location location) {
        int[][] offsets = {{0, 0}, {0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] offset : offsets) {
            UUID id = originIndex.get(new BlockKey(location.getWorld().getUID(), location.getBlockX() + offset[0],
                    location.getBlockY(), location.getBlockZ() + offset[1]));
            FurnitureInstance instance = id == null ? null : instances.get(id);
            if (instance != null) refresh(instance);
        }
    }

    private void refresh(FurnitureInstance instance) {
        FurnitureDefinition definition = definition(instance.definitionId()).orElse(null);
        if (definition == null || definition.states().isEmpty()) return;
        FurnitureStateSelector.Selection selected = state(definition, instance.location(), instance.yaw());
        if (selected.model().equals(instance.renderedModel()) && selected.yaw() == instance.renderedYaw()) return;
        FurnitureRenderer renderer = renderers.select(instance.renderer());
        try {
            List<UUID> replacement = renderer.spawn(instance.location(), selected.yaw(),
                    core.getItemManager().createRenderItem(selected.model()), definition);
            FurnitureInstance updated = new FurnitureInstance(instance.id(), instance.definitionId(), instance.location(),
                    instance.yaw(), instance.renderer(), replacement, instance.blocks(), selected.model(), selected.yaw());
            unindex(instance);
            instances.put(updated.id(), updated);
            index(updated);
            renderer.remove(instance.entities());
            save();
        } catch (RuntimeException exception) {
            Bukkit.getLogger().warning("Unable to update furniture state " + instance.id() + ": " + exception.getMessage());
        }
    }

    public static float snapYaw(float yaw, float step) {
        float normalized = yaw % 360.0F;
        if (normalized < 0.0F) normalized += 360.0F;
        float snapped = Math.round(normalized / step) * step;
        return snapped >= 360.0F ? snapped - 360.0F : snapped;
    }

    private void rebuildIndex() {
        entityIndex.clear();
        blockIndex.clear();
        originIndex.clear();
        for (FurnitureInstance instance : instances.values()) index(instance);
    }

    static List<FurnitureBlockPosition> resolveBlocks(List<FurnitureBlockDefinition> definitions,
                                                       Location origin, float yaw) {
        List<FurnitureBlockPosition> result = new ArrayList<FurnitureBlockPosition>();
        Set<String> occupied = new HashSet<String>();
        double radians = Math.toRadians(yaw);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        for (FurnitureBlockDefinition definition : definitions) {
            int rotatedX = (int) Math.round(definition.x() * cosine - definition.z() * sine);
            int rotatedZ = (int) Math.round(definition.x() * sine + definition.z() * cosine);
            FurnitureBlockPosition position = new FurnitureBlockPosition(origin.getBlockX() + rotatedX,
                    origin.getBlockY() + definition.y(), origin.getBlockZ() + rotatedZ, definition.material());
            String key = position.x() + ":" + position.y() + ":" + position.z();
            if (!occupied.add(key)) {
                throw new IllegalArgumentException("Furniture collision blocks overlap after rotation at " + key);
            }
            result.add(position);
        }
        return result;
    }

    private boolean canPlace(World world, List<FurnitureBlockPosition> blocks) {
        for (FurnitureBlockPosition position : blocks) {
            Block block = world.getBlockAt(position.x(), position.y(), position.z());
            BlockKey key = BlockKey.of(block);
            if (block.getType() != Material.AIR || blockIndex.containsKey(key) || originIndex.containsKey(key)) return false;
        }
        return true;
    }

    private static void placeBlocks(World world, List<FurnitureBlockPosition> blocks,
                                    List<FurnitureBlockPosition> placed) {
        for (FurnitureBlockPosition position : blocks) {
            world.getBlockAt(position.x(), position.y(), position.z()).setType(position.material());
            placed.add(position);
        }
    }

    private static void removeBlocks(World world, List<FurnitureBlockPosition> blocks) {
        for (FurnitureBlockPosition position : blocks) {
            Block block = world.getBlockAt(position.x(), position.y(), position.z());
            if (block.getType() == position.material()) block.setType(Material.AIR);
        }
    }

    private void index(FurnitureInstance instance) {
        for (UUID entity : instance.entities()) entityIndex.put(entity, instance.id());
        Location location = instance.location();
        World world = location.getWorld();
        originIndex.put(BlockKey.of(location), instance.id());
        for (FurnitureBlockPosition block : instance.blocks()) {
            blockIndex.put(new BlockKey(world.getUID(), block.x(), block.y(), block.z()), instance.id());
        }
    }

    private void unindex(FurnitureInstance instance) {
        for (UUID entity : instance.entities()) entityIndex.remove(entity);
        Location location = instance.location();
        World world = location.getWorld();
        originIndex.remove(BlockKey.of(location), instance.id());
        for (FurnitureBlockPosition block : instance.blocks()) {
            blockIndex.remove(new BlockKey(world.getUID(), block.x(), block.y(), block.z()));
        }
    }

    private static final class BlockKey {
        private final UUID world;
        private final int x;
        private final int y;
        private final int z;

        private BlockKey(UUID world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }

        static BlockKey of(Location location) {
            return new BlockKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(),
                    location.getBlockZ());
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BlockKey)) return false;
            BlockKey key = (BlockKey) other;
            return x == key.x && y == key.y && z == key.z && world.equals(key.world);
        }

        @Override public int hashCode() {
            int result = world.hashCode();
            result = 31 * result + x;
            result = 31 * result + y;
            return 31 * result + z;
        }
    }

    private void save() { store.save(instances.values()); }
}
