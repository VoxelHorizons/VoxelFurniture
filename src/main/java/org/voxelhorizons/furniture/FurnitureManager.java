package org.voxelhorizons.furniture;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.furniture.event.FurnitureBreakEvent;
import org.voxelhorizons.furniture.event.FurniturePlaceEvent;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureDefinitionParser;
import org.voxelhorizons.furniture.model.FurnitureInstance;
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

public final class FurnitureManager {
    private final VoxelCore core;
    private final FurnitureDefinitionParser definitions;
    private final FurnitureRendererSelector renderers;
    private final FurnitureStore store;
    private final Map<UUID, FurnitureInstance> instances;
    private final Map<UUID, UUID> entityIndex = new LinkedHashMap<UUID, UUID>();

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
        return item.isPresent() ? definitions.parse(item.get()) : Optional.<FurnitureDefinition>empty();
    }

    public Map<ContentID, FurnitureDefinition> definitions() {
        Map<ContentID, FurnitureDefinition> result = new LinkedHashMap<ContentID, FurnitureDefinition>();
        for (ItemDefinition item : core.getItemRegistry().entries().values()) {
            Optional<FurnitureDefinition> definition = definitions.parse(item);
            if (definition.isPresent()) result.put(item.id(), definition.get());
        }
        return Collections.unmodifiableMap(result);
    }

    public Optional<FurnitureInstance> byEntity(UUID entityId) {
        UUID instanceId = entityIndex.get(entityId);
        return Optional.ofNullable(instanceId == null ? null : instances.get(instanceId));
    }

    public Collection<FurnitureInstance> instances() {
        return Collections.unmodifiableCollection(new ArrayList<FurnitureInstance>(instances.values()));
    }

    public Optional<FurnitureInstance> place(Player player, FurnitureDefinition definition, Location location) {
        FurniturePlaceEvent event = new FurniturePlaceEvent(player, definition, location);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return Optional.empty();

        float yaw = snapYaw(player.getLocation().getYaw(), definition.rotationStep());
        ItemStack modelItem = core.getItemManager().createItem(definition.modelItemId());
        FurnitureRenderer renderer = renderers.select(definition.renderer());
        List<UUID> entities = renderer.spawn(location, yaw, modelItem, definition);
        FurnitureInstance instance = new FurnitureInstance(UUID.randomUUID(), definition.itemId(), location, yaw,
                renderer.type(), entities);
        instances.put(instance.id(), instance);
        for (UUID entity : entities) entityIndex.put(entity, instance.id());
        save();
        return Optional.of(instance);
    }

    public boolean breakFurniture(Player player, FurnitureInstance instance) {
        FurnitureBreakEvent event = new FurnitureBreakEvent(player, instance);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        FurnitureDefinition definition = definition(instance.definitionId()).orElse(null);
        renderers.select(instance.renderer()).remove(instance.entities());
        instances.remove(instance.id());
        for (UUID entity : instance.entities()) entityIndex.remove(entity);
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
        for (UUID entity : instance.entities()) entityIndex.remove(entity);
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
            if (!core.getItemManager().hasItem(furniture.modelItemId())) {
                throw new IllegalArgumentException("Furniture " + furniture.itemId() + " references unknown model_item "
                        + furniture.modelItemId());
            }
            if (!core.getItemManager().hasItem(furniture.dropItemId())) {
                throw new IllegalArgumentException("Furniture " + furniture.itemId() + " references unknown drop "
                        + furniture.dropItemId());
            }
            renderers.select(furniture.renderer());
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
        for (FurnitureInstance instance : instances.values()) {
            for (UUID entity : instance.entities()) entityIndex.put(entity, instance.id());
        }
    }

    private void save() { store.save(instances.values()); }
}
