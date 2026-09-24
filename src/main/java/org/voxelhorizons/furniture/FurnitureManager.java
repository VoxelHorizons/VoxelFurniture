package org.voxelhorizons.furniture;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.voxelhorizons.furniture.event.FurnitureBreakEvent;
import org.voxelhorizons.furniture.event.FurniturePlaceEvent;
import org.voxelhorizons.furniture.inventory.FurnitureInventoryHolder;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureBlockDefinition;
import org.voxelhorizons.furniture.model.FurnitureBlockPosition;
import org.voxelhorizons.furniture.model.FurnitureDefinitionParser;
import org.voxelhorizons.furniture.model.FurnitureInstance;
import org.voxelhorizons.furniture.model.FurnitureSeatDefinition;
import org.voxelhorizons.furniture.model.FurnitureStateSelector;
import org.voxelhorizons.furniture.model.FurnitureStateRule;
import org.voxelhorizons.furniture.render.EntitySupport;
import org.voxelhorizons.furniture.render.FurnitureRenderer;
import org.voxelhorizons.furniture.render.FurnitureRendererSelector;
import org.voxelhorizons.furniture.store.FurnitureStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

public final class FurnitureManager {
    private static final String RENDER_SIGNATURE_VERSION = "local-offset-v2";

    private final VoxelCore core;
    private final FurnitureDefinitionParser definitions;
    private final FurnitureRendererSelector renderers;
    private final Plugin plugin;
    private final FurnitureStore store;
    private final Map<UUID, FurnitureInstance> instances;
    private final Map<UUID, ArmorStand> seats = new LinkedHashMap<UUID, ArmorStand>();
    private final Map<UUID, Inventory> openInventories = new LinkedHashMap<UUID, Inventory>();
    private final Map<UUID, Long> pendingInventoryCloses = new LinkedHashMap<UUID, Long>();
    private final Map<UUID, UUID> entityIndex = new LinkedHashMap<UUID, UUID>();
    private final Map<BlockKey, UUID> blockIndex = new LinkedHashMap<BlockKey, UUID>();
    private final Map<BlockKey, UUID> originIndex = new LinkedHashMap<BlockKey, UUID>();
    private final Set<UUID> pendingSynchronization = new HashSet<UUID>();
    private final Set<UUID> forcedSynchronization = new HashSet<UUID>();
    private long contentRevision;
    private long inventoryCloseSequence;

    public FurnitureManager(Plugin plugin, VoxelCore core, FurnitureDefinitionParser definitions,
                            FurnitureRendererSelector renderers, FurnitureStore store) {
        this.plugin = plugin;
        this.core = core;
        this.definitions = definitions;
        this.renderers = renderers;
        this.store = store;
        this.instances = new LinkedHashMap<UUID, FurnitureInstance>(store.load());
        this.pendingSynchronization.addAll(this.instances.keySet());
        this.contentRevision = core.getContentRuntime().current().revision();
        rebuildIndex();
        plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                cleanupSeats();
                synchronizeIfContentChanged();
            }
        }, 20L, 20L);
    }

    public Optional<FurnitureDefinition> definition(ContentID id) {
        Optional<ItemDefinition> item = core.getItemManager().getDefinition(id);
        return item.isPresent() && !item.get().abstractDefinition()
                ? definitions.parse(item.get()) : Optional.<FurnitureDefinition>empty();
    }

    /**
     * Resolves the furniture properties belonging to a render/model variant. Unlike definition(),
     * abstract items are valid here because animation and blockstate models are intentionally
     * authored as abstract inherited items.
     */
    private FurnitureDefinition renderDefinition(ContentID id, FurnitureDefinition fallback) {
        Optional<ItemDefinition> item = core.getItemManager().getDefinition(id);
        if (!item.isPresent()) return fallback;
        Optional<FurnitureDefinition> parsed = definitions.parse(item.get());
        return parsed.isPresent() ? parsed.get() : fallback;
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
        cleanupOriginChunk(location);
        float yaw = snapYaw(player.getLocation().getYaw(), definition.rotationStep());
        return place(definition, location, yaw, player, player.getInventory().getItemInMainHand());
    }

    /**
     * Places system-owned furniture at an explicit yaw without requiring or impersonating a player.
     * Intended for trusted addons restoring previously recorded furniture layouts. The yaw is snapped
     * to the definition's configured rotation step and no player placement event is fired.
     */
    public Optional<FurnitureInstance> place(FurnitureDefinition definition, Location location, float yaw) {
        cleanupOriginChunk(location);
        return place(definition, location, snapYaw(yaw, definition.rotationStep()), null, null);
    }

    private Optional<FurnitureInstance> place(FurnitureDefinition definition, Location location, float yaw,
                                               Player player, ItemStack sourceItem) {
        List<FurnitureBlockPosition> collisionBlocks = resolveBlocks(definition.blocks(), location, yaw);
        BlockKey origin = BlockKey.of(location);
        if (originIndex.containsKey(origin) || blockIndex.containsKey(origin)
                || !canPlace(location.getWorld(), collisionBlocks)) return Optional.empty();
        if (player != null) {
            FurniturePlaceEvent event = new FurniturePlaceEvent(player, definition, location);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return Optional.empty();
        }
        FurnitureStateSelector.Selection state = state(definition, location, yaw);
        Integer dyeColor = core.getItemManager().getDyeColor(sourceItem).orElse(null);
        ItemStack modelItem = renderItem(state.model(), dyeColor);
        FurnitureDefinition visualDefinition = renderDefinition(state.model(), definition);
        FurnitureRenderer renderer = renderers.select(visualDefinition.renderer());
        String signature = renderSignature(visualDefinition, state, renderer.type(), collisionBlocks, modelItem);
        List<FurnitureBlockPosition> placed = new ArrayList<FurnitureBlockPosition>();
        List<UUID> entities = Collections.emptyList();
        FurnitureInstance instance = null;
        try {
            placeBlocks(location.getWorld(), collisionBlocks, placed);
            entities = renderer.spawn(location, state.yaw(), modelItem, visualDefinition);
            instance = new FurnitureInstance(UUID.randomUUID(), definition.itemId(), location, yaw,
                    renderer.type(), entities, collisionBlocks, state.model(), state.yaw(), signature,
                    Collections.<ItemStack>emptyList(), dyeColor, false);
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
        instance = sealInventory(instance);
        removeSeat(instance.id());
        renderers.select(instance.renderer()).remove(instance.entities());
        removeBlocks(instance.location().getWorld(), instance.blocks());
        instances.remove(instance.id());
        pendingSynchronization.remove(instance.id());
        forcedSynchronization.remove(instance.id());
        unindex(instance);
        refreshAround(instance.location());
        if (definition != null && player.getGameMode() != GameMode.CREATIVE) {
            instance.location().getWorld().dropItemNaturally(instance.location(), dropItem(definition, instance));
        }
        dropContents(instance);
        save();
        cleanupOriginChunk(instance.location());
        return true;
    }

    /** Recolours one placed furniture instance and rebuilds its active model immediately. */
    public boolean dye(FurnitureInstance requested, int rgb) {
        FurnitureInstance instance = requested == null ? null : instances.get(requested.id());
        if (instance == null) return false;
        FurnitureDefinition definition = definition(instance.definitionId()).orElse(null);
        if (definition == null || !core.getItemManager().isDyeable(core.getItemManager().createItem(definition.itemId())))
            return false;
        FurnitureInstance colored = new FurnitureInstance(instance.id(), instance.definitionId(), instance.location(),
                instance.yaw(), instance.renderer(), instance.entities(), instance.blocks(), instance.renderedModel(),
                instance.renderedYaw(), instance.renderSignature(), instance.inventoryContents(),
                Integer.valueOf(rgb & 0xFFFFFF), instance.useAnimationActive());
        instances.put(colored.id(), colored);
        if (!synchronizeInstance(colored, true)) { instances.put(instance.id(), instance); return false; }
        save();
        return true;
    }

    /**
     * Toggles animation.use for non-inventory furniture. When sync_neighbors is enabled,
     * the clicked furniture's new state is propagated through every horizontally adjacent,
     * same-height, opted-in animated furniture instance.
     */
    public boolean toggleUseAnimation(FurnitureInstance requested) {
        FurnitureInstance instance = requested == null ? null : instances.get(requested.id());
        if (instance == null) return false;
        FurnitureDefinition definition = definition(instance.definitionId()).orElse(null);
        if (!canToggleUseAnimation(definition)) return false;

        boolean targetState = !instance.useAnimationActive();
        if (!definition.animationSyncNeighbors()) {
            FurnitureInstance updated = withUseAnimationState(instance, targetState);
            instances.put(updated.id(), updated);
            refresh(updated);
            save();
            return true;
        }

        List<FurnitureInstance> group = synchronizedAnimationGroup(instance);
        for (FurnitureInstance member : group) {
            FurnitureInstance updated = withUseAnimationState(member, targetState);
            instances.put(updated.id(), updated);
        }
        for (FurnitureInstance member : group) {
            FurnitureInstance updated = instances.get(member.id());
            if (updated != null) refresh(updated);
        }
        save();
        return true;
    }

    private boolean canToggleUseAnimation(FurnitureDefinition definition) {
        return definition != null && !definition.hasInventory() && definition.animationUseModel() != null;
    }

    private FurnitureInstance withUseAnimationState(FurnitureInstance instance, boolean active) {
        return new FurnitureInstance(instance.id(), instance.definitionId(), instance.location(),
                instance.yaw(), instance.renderer(), instance.entities(), instance.blocks(), instance.renderedModel(),
                instance.renderedYaw(), instance.renderSignature(), instance.inventoryContents(), instance.dyeColor(),
                active);
    }

    private List<FurnitureInstance> synchronizedAnimationGroup(FurnitureInstance start) {
        List<FurnitureInstance> result = new ArrayList<FurnitureInstance>();
        Set<UUID> visited = new HashSet<UUID>();
        Deque<FurnitureInstance> pending = new ArrayDeque<FurnitureInstance>();
        pending.add(start);

        while (!pending.isEmpty()) {
            FurnitureInstance current = pending.removeFirst();
            if (!visited.add(current.id())) continue;
            FurnitureDefinition currentDefinition = definition(current.definitionId()).orElse(null);
            if (!canToggleUseAnimation(currentDefinition) || !currentDefinition.animationSyncNeighbors()) continue;
            result.add(current);

            Location location = current.location();
            int[][] offsets = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
            for (int[] offset : offsets) {
                UUID id = originIndex.get(new BlockKey(location.getWorld().getUID(),
                        location.getBlockX() + offset[0], location.getBlockY(),
                        location.getBlockZ() + offset[1]));
                FurnitureInstance neighbor = id == null ? null : instances.get(id);
                if (neighbor == null || visited.contains(neighbor.id())) continue;
                FurnitureDefinition neighborDefinition = definition(neighbor.definitionId()).orElse(null);
                if (canToggleUseAnimation(neighborDefinition) && neighborDefinition.animationSyncNeighbors()) {
                    pending.addLast(neighbor);
                }
            }
        }
        return result;
    }

    public boolean remove(UUID instanceId, boolean drop) {
        FurnitureInstance instance = instances.get(instanceId);
        if (instance == null) return false;
        instance = sealInventory(instance);
        instances.remove(instanceId);
        pendingSynchronization.remove(instance.id());
        forcedSynchronization.remove(instance.id());
        removeSeat(instance.id());
        renderers.select(instance.renderer()).remove(instance.entities());
        removeBlocks(instance.location().getWorld(), instance.blocks());
        unindex(instance);
        refreshAround(instance.location());
        if (drop) {
            FurnitureDefinition definition = definition(instance.definitionId()).orElse(null);
            if (definition != null) instance.location().getWorld().dropItemNaturally(instance.location(),
                    dropItem(definition, instance));
            dropContents(instance);
        }
        save();
        cleanupOriginChunk(instance.location());
        return true;
    }

    public boolean openInventory(Player player, FurnitureInstance requested) {
        if (player == null || requested == null) return false;
        FurnitureInstance instance = instances.get(requested.id());
        if (instance == null) return false;
        FurnitureDefinition definition = definition(instance.definitionId()).orElse(null);
        if (definition == null || !definition.hasInventory()) return false;

        Inventory inventory = openInventories.get(instance.id());
        if (inventory == null || inventory.getSize() != definition.inventorySize()) {
            FurnitureInventoryHolder holder = new FurnitureInventoryHolder(instance.id());
            inventory = Bukkit.createInventory(holder, definition.inventorySize(), inventoryTitle(definition));
            holder.bind(inventory);
            List<ItemStack> stored = instance.inventoryContents();
            for (int slot = 0; slot < stored.size() && slot < inventory.getSize(); slot++) {
                ItemStack item = stored.get(slot);
                inventory.setItem(slot, item == null ? null : item.clone());
            }
            openInventories.put(instance.id(), inventory);
            refresh(instance);
        }
        pendingInventoryCloses.remove(instance.id());
        player.openInventory(inventory);
        return true;
    }

    private String inventoryTitle(FurnitureDefinition furniture) {
        Optional<ItemDefinition> item = core.getItemManager().getDefinition(furniture.itemId());
        String title = item.isPresent() ? item.get().displayName() : null;
        if (title == null || title.trim().isEmpty()) title = furniture.itemId().toString();
        if (core.getTextPlaceholderService() != null) {
            title = core.getTextPlaceholderService().resolve(title);
        }
        return ChatColor.translateAlternateColorCodes('&', title);
    }

    public void closeInventory(final Inventory inventory) {
        if (inventory == null || !(inventory.getHolder() instanceof FurnitureInventoryHolder)) return;
        final UUID instanceId = ((FurnitureInventoryHolder) inventory.getHolder()).instanceId();
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                Inventory active = openInventories.get(instanceId);
                if (active != inventory || !inventory.getViewers().isEmpty()) return;
                FurnitureInstance instance = instances.get(instanceId);
                if (instance == null) return;
                FurnitureInstance updated = withInventoryContents(instance, inventory.getContents());
                instances.put(instanceId, updated);
                save();
                FurnitureDefinition definition = definition(updated.definitionId()).orElse(null);
                long closeDelay = definition != null && definition.animationUseModel() != null
                        ? definition.animationCloseDelay() : 0L;
                final long closeToken = ++inventoryCloseSequence;
                pendingInventoryCloses.put(instanceId, closeToken);
                if (closeDelay == 0L) finishInventoryClose(instanceId, inventory, closeToken);
                else plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
                    @Override public void run() { finishInventoryClose(instanceId, inventory, closeToken); }
                }, closeDelay);
            }
        });
    }

    private void finishInventoryClose(UUID instanceId, Inventory inventory, long closeToken) {
        Long pending = pendingInventoryCloses.get(instanceId);
        if (pending == null || pending.longValue() != closeToken) return;
        Inventory active = openInventories.get(instanceId);
        if (active != inventory || !inventory.getViewers().isEmpty()) return;
        pendingInventoryCloses.remove(instanceId);
        openInventories.remove(instanceId);
        FurnitureInstance instance = instances.get(instanceId);
        if (instance != null) refresh(instance);
    }

    private FurnitureInstance sealInventory(FurnitureInstance instance) {
        pendingInventoryCloses.remove(instance.id());
        Inventory inventory = openInventories.remove(instance.id());
        if (inventory == null) return instance;
        for (HumanEntity viewer : new ArrayList<HumanEntity>(inventory.getViewers())) viewer.closeInventory();
        FurnitureInstance updated = withInventoryContents(instance, inventory.getContents());
        instances.put(updated.id(), updated);
        return updated;
    }

    private static FurnitureInstance withInventoryContents(FurnitureInstance instance, ItemStack[] contents) {
        List<ItemStack> items = new ArrayList<ItemStack>();
        if (contents != null) {
            for (ItemStack item : contents) items.add(item == null ? null : item.clone());
        }
        return new FurnitureInstance(instance.id(), instance.definitionId(), instance.location(), instance.yaw(),
                instance.renderer(), instance.entities(), instance.blocks(), instance.renderedModel(),
                instance.renderedYaw(), instance.renderSignature(), items, instance.dyeColor(),
                instance.useAnimationActive());
    }

    private static void dropContents(FurnitureInstance instance) {
        for (ItemStack item : instance.inventoryContents()) {
            if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                instance.location().getWorld().dropItemNaturally(instance.location(), item);
            }
        }
    }

    private ItemStack renderItem(ContentID model, Integer dyeColor) {
        ItemStack item = core.getItemManager().createRenderItem(model);
        return dyeColor == null || !core.getItemManager().isDyeable(item)
                ? item : core.getItemManager().setDyeColor(item, dyeColor.intValue());
    }

    private ItemStack dropItem(FurnitureDefinition definition, FurnitureInstance instance) {
        ItemStack item = core.getItemManager().createItem(definition.dropItemId());
        return instance.dyeColor() == null || !core.getItemManager().isDyeable(item)
                ? item : core.getItemManager().setDyeColor(item, instance.dyeColor().intValue());
    }

    public boolean sit(Player player, FurnitureInstance instance) {
        if (player == null || instance == null || player.getVehicle() != null) return false;
        FurnitureDefinition definition = definition(instance.definitionId()).orElse(null);
        if (definition == null || definition.seat() == null) return false;

        ArmorStand existing = seats.get(instance.id());
        if (existing != null) {
            if (existing.isValid() && !existing.getPassengers().isEmpty()) return false;
            existing.remove();
            seats.remove(instance.id());
        }

        FurnitureSeatDefinition seat = definition.seat();
        Location location = seatLocation(instance, seat);
        ArmorStand stand = location.getWorld().spawn(location, ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setSmall(true);
        stand.setMarker(true);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.setSilent(true);
        stand.addScoreboardTag("voxelfurniture-seat");

        // Vehicles do not force a player's camera/body yaw to match the mount.
        // Align the rider before mounting so seat.yaw can correct furniture
        // models authored facing a different direction without dismounting them.
        Location playerLocation = player.getLocation();
        playerLocation.setYaw(location.getYaw());
        player.teleport(playerLocation);

        if (!stand.addPassenger(player)) {
            stand.remove();
            return false;
        }

        seats.put(instance.id(), stand);
        return true;
    }

    static Location seatLocation(FurnitureInstance instance, FurnitureSeatDefinition seat) {
        Location origin = instance.location();
        double radians = Math.toRadians(instance.yaw());
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        double x = seat.offsetX() * cosine - seat.offsetZ() * sine;
        double z = seat.offsetX() * sine + seat.offsetZ() * cosine;
        Location location = origin.clone().add(x, seat.offsetY(), z);
        location.setYaw(instance.yaw() + seat.yawOffset());
        return location;
    }

    private void cleanupSeats() {
        for (Map.Entry<UUID, ArmorStand> entry : new ArrayList<Map.Entry<UUID, ArmorStand>>(seats.entrySet())) {
            ArmorStand stand = entry.getValue();
            FurnitureInstance instance = instances.get(entry.getKey());
            FurnitureDefinition definition = instance == null ? null
                    : definition(instance.definitionId()).orElse(null);
            if (stand == null || !stand.isValid() || stand.getPassengers().isEmpty()
                    || definition == null || definition.seat() == null) {
                if (stand != null && stand.isValid()) stand.remove();
                seats.remove(entry.getKey());
            }
        }
    }

    private void removeSeat(UUID instanceId) {
        ArmorStand stand = seats.remove(instanceId);
        if (stand != null && stand.isValid()) stand.remove();
    }

    public void shutdown() {
        for (UUID id : new ArrayList<UUID>(openInventories.keySet())) {
            FurnitureInstance instance = instances.get(id);
            if (instance != null) sealInventory(instance);
        }
        save();
        for (ArmorStand stand : new ArrayList<ArmorStand>(seats.values())) {
            if (stand != null && stand.isValid()) stand.remove();
        }
        seats.clear();
    }

    private void synchronizeIfContentChanged() {
        long currentRevision = core.getContentRuntime().current().revision();
        if (currentRevision == contentRevision) return;

        try {
            validateDefinitions();
            pendingSynchronization.addAll(instances.keySet());
            forcedSynchronization.addAll(instances.keySet());
            synchronizePendingLoaded();
            contentRevision = currentRevision;
            plugin.getLogger().info("Scheduled placed furniture synchronization for VoxelCore content revision "
                    + currentRevision + "; unloaded furniture will update when its chunk loads.");
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Unable to synchronize furniture to VoxelCore content revision "
                    + currentRevision + ": " + exception.getMessage());
        }
    }

    /**
     * Startup/content-reload entry point. Orphaned renderers are removed from
     * already-loaded chunks first, then only furniture whose origin chunk is
     * loaded is rebuilt. Unloaded furniture remains pending until ChunkLoadEvent.
     */
    public void synchronizeDefinitions() {
        pendingSynchronization.addAll(instances.keySet());
        cleanupLoadedOrphans();
        synchronizePendingLoaded();
    }

    /**
     * Repairs a newly loaded chunk. Any VoxelFurniture renderer entity no
     * longer referenced by furniture.yml is an orphan from an interrupted or
     * historical replacement and is safe to remove. Pending furniture in this
     * chunk is then reconciled against the current definition.
     */
    public void onChunkLoad(final Chunk chunk) {
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override public void run() {
                if (chunk.getWorld().isChunkLoaded(chunk.getX(), chunk.getZ())) {
                    repairChunk(chunk);
                }
            }
        });
    }

    private void repairChunk(Chunk chunk) {
        int removedBefore = cleanupOrphans(chunk);
        boolean changed = false;

        for (UUID id : new ArrayList<UUID>(pendingSynchronization)) {
            FurnitureInstance instance = instances.get(id);
            if (instance == null) {
                pendingSynchronization.remove(id);
                forcedSynchronization.remove(id);
                continue;
            }
            if (!isInChunk(instance, chunk)) continue;

            boolean force = forcedSynchronization.contains(id);
            if (synchronizeInstance(instance, force)) {
                pendingSynchronization.remove(id);
                forcedSynchronization.remove(id);
                changed = true;
            }
        }

        if (changed) save();
        int removedAfter = cleanupOrphans(chunk);
        int removed = removedBefore + removedAfter;
        if (removed > 0) {
            plugin.getLogger().warning("Removed " + removed + " orphaned VoxelFurniture renderer entit"
                    + (removed == 1 ? "y" : "ies") + " from chunk " + chunk.getX() + "," + chunk.getZ()
                    + " in " + chunk.getWorld().getName() + ".");
        }
    }

    private void synchronizePendingLoaded() {
        boolean changed = false;
        for (UUID id : new ArrayList<UUID>(pendingSynchronization)) {
            FurnitureInstance instance = instances.get(id);
            if (instance == null) {
                pendingSynchronization.remove(id);
                forcedSynchronization.remove(id);
                continue;
            }
            if (!isOriginChunkLoaded(instance)) continue;

            boolean force = forcedSynchronization.contains(id);
            if (synchronizeInstance(instance, force)) {
                pendingSynchronization.remove(id);
                forcedSynchronization.remove(id);
                changed = true;
            }
        }
        if (changed) save();
    }

    private boolean synchronizeInstance(FurnitureInstance instance, boolean force) {
        FurnitureDefinition definition = definition(instance.definitionId()).orElse(null);
        if (definition == null) {
            plugin.getLogger().warning("Placed furniture " + instance.id() + " references missing definition "
                    + instance.definitionId() + "; leaving the existing instance untouched.");
            return true;
        }

        FurnitureStateSelector.Selection selected = selection(definition, instance);
        FurnitureDefinition visualDefinition = renderDefinition(selected.model(), definition);
        FurnitureRenderer replacementRenderer = renderers.select(visualDefinition.renderer());
        List<FurnitureBlockPosition> desiredBlocks =
                resolveBlocks(definition.blocks(), instance.location(), instance.yaw());
        ItemStack modelItem = renderItem(selected.model(), instance.dyeColor());
        String desiredSignature = renderSignature(
                visualDefinition, selected, replacementRenderer.type(), desiredBlocks, modelItem);

        if (!force && desiredSignature.equals(instance.renderSignature()) && rendererEntitiesPresent(instance)) {
            refreshSeat(instance, definition);
            return true;
        }

        List<UUID> replacement = Collections.emptyList();

        try {
            replacement = replacementRenderer.spawn(instance.location(), selected.yaw(), modelItem, visualDefinition);

            List<FurnitureBlockPosition> finalBlocks = instance.blocks();
            if (!sameBlocks(instance.blocks(), desiredBlocks)) {
                if (canMigrateBlocks(instance, desiredBlocks)) {
                    removeBlocks(instance.location().getWorld(), instance.blocks());
                    placeBlocks(instance.location().getWorld(), desiredBlocks,
                            new ArrayList<FurnitureBlockPosition>());
                    finalBlocks = desiredBlocks;
                } else {
                    plugin.getLogger().warning("Could not apply updated collision blocks to furniture "
                            + instance.id() + " (" + instance.definitionId()
                            + ") because the new layout is obstructed; keeping its existing collision blocks.");
                }
            }

            String appliedSignature = renderSignature(
                    visualDefinition, selected, replacementRenderer.type(), finalBlocks, modelItem);
            FurnitureInstance updated = new FurnitureInstance(instance.id(), instance.definitionId(),
                    instance.location(), instance.yaw(), replacementRenderer.type(), replacement, finalBlocks,
                    selected.model(), selected.yaw(), appliedSignature, instance.inventoryContents(), instance.dyeColor(),
                    instance.useAnimationActive());

            // Remove the renderer that furniture.yml currently owns before
            // replacing its UUIDs. If any historical renderer cannot be found
            // now, it becomes an unreferenced tagged orphan and the chunk
            // sweeper removes it as soon as that entity loads.
            renderers.select(instance.renderer()).remove(instance.entities());
            unindex(instance);
            instances.put(updated.id(), updated);
            index(updated);
            refreshSeat(updated, definition);
            return true;
        } catch (RuntimeException exception) {
            replacementRenderer.remove(replacement);
            plugin.getLogger().warning("Unable to synchronize furniture " + instance.id() + " ("
                    + instance.definitionId() + "): " + exception.getMessage());
            return false;
        }
    }

    private boolean rendererEntitiesPresent(FurnitureInstance instance) {
        int expected = instance.renderer() == org.voxelhorizons.furniture.model.FurnitureRendererType.DISPLAY ? 2 : 1;
        if (instance.entities().size() != expected) return false;
        for (UUID id : instance.entities()) {
            Entity entity = EntitySupport.find(id);
            if (entity == null || !entity.getScoreboardTags().contains("voxelfurniture")) return false;
        }
        return true;
    }

    private String renderSignature(FurnitureDefinition definition, FurnitureStateSelector.Selection selected,
                                   org.voxelhorizons.furniture.model.FurnitureRendererType renderer,
                                   List<FurnitureBlockPosition> blocks, ItemStack modelItem) {
        StringBuilder value = new StringBuilder();
        value.append(RENDER_SIGNATURE_VERSION).append('|')
                .append(renderer.name()).append('|')
                .append(selected.model()).append('|').append(selected.yaw()).append('|')
                .append(definition.width()).append('|').append(definition.height()).append('|')
                .append(definition.scaleX()).append('|').append(definition.scaleY()).append('|')
                .append(definition.scaleZ()).append('|').append(definition.viewDistance()).append('|')
                .append(definition.offsetX()).append('|').append(definition.offsetY()).append('|')
                .append(definition.offsetZ()).append('|').append(definition.offsetRotation()).append('|')
                .append(modelItem.serialize().toString());

        for (FurnitureBlockPosition block : blocks) {
            value.append('|').append(block.x()).append(',').append(block.y()).append(',').append(block.z())
                    .append(',').append(block.material().name());
        }

        FurnitureSeatDefinition seat = definition.seat();
        if (seat != null) {
            value.append("|seat:")
                    .append(seat.offsetX()).append(',').append(seat.offsetY()).append(',')
                    .append(seat.offsetZ()).append(',').append(seat.yawOffset());
        }

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) hex.append(String.format("%02x", item & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public int cleanupLoadedOrphans() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                removed += cleanupOrphans(chunk);
            }
        }
        if (removed > 0) {
            plugin.getLogger().warning("Removed " + removed
                    + " orphaned VoxelFurniture renderer entities from currently loaded chunks.");
        }
        return removed;
    }

    int cleanupOrphans(Chunk chunk) {
        Set<UUID> referenced = referencedEntityIds();
        int removed = 0;
        for (Entity entity : chunk.getEntities()) {
            if (!isOrphanRenderer(entity.getUniqueId(), entity.getScoreboardTags(), referenced)) continue;
            entity.remove();
            removed++;
        }
        return removed;
    }

    static boolean isOrphanRenderer(UUID entityId, Set<String> tags, Set<UUID> referenced) {
        return tags.contains("voxelfurniture") && !referenced.contains(entityId);
    }

    private void cleanupOriginChunk(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) return;
        cleanupOrphans(world.getChunkAt(chunkX, chunkZ));
    }

    private Set<UUID> referencedEntityIds() {
        Set<UUID> referenced = new HashSet<UUID>();
        for (FurnitureInstance instance : instances.values()) {
            referenced.addAll(instance.entities());
        }
        return referenced;
    }

    private static boolean isInChunk(FurnitureInstance instance, Chunk chunk) {
        Location location = instance.location();
        return location.getWorld() != null
                && location.getWorld().getUID().equals(chunk.getWorld().getUID())
                && (location.getBlockX() >> 4) == chunk.getX()
                && (location.getBlockZ() >> 4) == chunk.getZ();
    }

    private static boolean isOriginChunkLoaded(FurnitureInstance instance) {
        Location location = instance.location();
        World world = location.getWorld();
        return world != null && world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private void refreshSeat(FurnitureInstance instance, FurnitureDefinition definition) {
        ArmorStand stand = seats.get(instance.id());
        if (definition.seat() == null) {
            removeSeat(instance.id());
            return;
        }
        if (stand != null && stand.isValid()) {
            stand.teleport(seatLocation(instance, definition.seat()));
        }
    }

    private boolean canMigrateBlocks(FurnitureInstance instance, List<FurnitureBlockPosition> desired) {
        World world = instance.location().getWorld();
        Set<BlockKey> owned = new HashSet<BlockKey>();
        for (FurnitureBlockPosition block : instance.blocks()) {
            owned.add(new BlockKey(world.getUID(), block.x(), block.y(), block.z()));
        }

        for (FurnitureBlockPosition position : desired) {
            Block block = world.getBlockAt(position.x(), position.y(), position.z());
            BlockKey key = BlockKey.of(block);
            UUID blockOwner = blockIndex.get(key);
            UUID originOwner = originIndex.get(key);

            if (blockOwner != null && !blockOwner.equals(instance.id())) return false;
            if (originOwner != null && !originOwner.equals(instance.id())) return false;
            if (!owned.contains(key) && !isReplaceable(block.getType())) return false;
        }
        return true;
    }

    private static boolean sameBlocks(List<FurnitureBlockPosition> left, List<FurnitureBlockPosition> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            FurnitureBlockPosition a = left.get(index);
            FurnitureBlockPosition b = right.get(index);
            if (a.x() != b.x() || a.y() != b.y() || a.z() != b.z() || a.material() != b.material()) return false;
        }
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
            if (furniture.animationUseModel() != null && !renderable(furniture.animationUseModel())) {
                throw new IllegalArgumentException("Furniture " + furniture.itemId()
                        + " references unrenderable animation.use model " + furniture.animationUseModel());
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
        int perpendicularMask = 0;
        int clockwiseMask = 0;
        int counterClockwiseMask = 0;
        int oppositeMask = 0;
        int cornerMask = 0;
        int[][] offsets = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int index = 0; index < offsets.length; index++) {
            UUID id = originIndex.get(new BlockKey(location.getWorld().getUID(), location.getBlockX() + offsets[index][0],
                    location.getBlockY(), location.getBlockZ() + offsets[index][1]));
            FurnitureInstance neighbor = id == null ? null : instances.get(id);
            if (neighbor != null && neighbor.definitionId().equals(definition.itemId())) {
                mask |= 1 << index;
                int relativeTurns = Math.round((neighbor.yaw() - yaw) / 90.0f) & 3;
                if (relativeTurns == 0) alignedMask |= 1 << index;
                else if (relativeTurns == 2) oppositeMask |= 1 << index;
                else {
                    perpendicularMask |= 1 << index;
                    if (relativeTurns == 1) clockwiseMask |= 1 << index;
                    else counterClockwiseMask |= 1 << index;
                }
                if (continuesAroundCorner(definition.itemId(), neighbor.location(),
                        offsets[index][0], offsets[index][1])) {
                    cornerMask |= 1 << index;
                }
            }
        }
        return FurnitureStateSelector.select(definition, mask, alignedMask,
                perpendicularMask, clockwiseMask, counterClockwiseMask, oppositeMask, cornerMask, yaw);
    }

    /**
     * A perpendicular neighbor only joins a straight state when that neighbor
     * continues on the other axis. This distinguishes a real L junction from
     * two unrelated sideways chairs touching each other.
     */
    private boolean continuesAroundCorner(ContentID definitionId, Location neighborLocation, int dx, int dz) {
        int[][] perpendicularOffsets = dx == 0
                ? new int[][] {{-1, 0}, {1, 0}}
                : new int[][] {{0, -1}, {0, 1}};
        for (int[] offset : perpendicularOffsets) {
            UUID id = originIndex.get(new BlockKey(neighborLocation.getWorld().getUID(),
                    neighborLocation.getBlockX() + offset[0], neighborLocation.getBlockY(),
                    neighborLocation.getBlockZ() + offset[1]));
            FurnitureInstance continuation = id == null ? null : instances.get(id);
            if (continuation != null && continuation.definitionId().equals(definitionId)) return true;
        }
        return false;
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

    private FurnitureStateSelector.Selection selection(FurnitureDefinition definition,
                                                               FurnitureInstance instance) {
        FurnitureStateSelector.Selection selected = state(definition, instance.location(), instance.yaw());
        boolean inventoryAnimation = openInventories.containsKey(instance.id());
        boolean interactionAnimation = !definition.hasInventory() && instance.useAnimationActive();
        if ((inventoryAnimation || interactionAnimation) && definition.animationUseModel() != null) {
            return new FurnitureStateSelector.Selection(definition.animationUseModel(), selected.yaw());
        }
        return selected;
    }

    private void refresh(FurnitureInstance instance) {
        FurnitureDefinition definition = definition(instance.definitionId()).orElse(null);
        if (definition == null) return;
        boolean animated = definition.animationUseModel() != null
                && (openInventories.containsKey(instance.id())
                || (!definition.hasInventory() && instance.useAnimationActive()));
        if (definition.states().isEmpty() && !animated && definition.modelItemId().equals(instance.renderedModel())) return;
        FurnitureStateSelector.Selection selected = selection(definition, instance);
        FurnitureDefinition visualDefinition = renderDefinition(selected.model(), definition);
        FurnitureRenderer renderer = renderers.select(visualDefinition.renderer());
        try {
            ItemStack modelItem = renderItem(selected.model(), instance.dyeColor());
            String signature = renderSignature(visualDefinition, selected, renderer.type(), instance.blocks(), modelItem);
            if (signature.equals(instance.renderSignature()) && renderer.type() == instance.renderer()
                    && rendererEntitiesPresent(instance)) return;
            List<UUID> replacement = renderer.spawn(instance.location(), selected.yaw(), modelItem, visualDefinition);
            FurnitureInstance updated = new FurnitureInstance(instance.id(), instance.definitionId(), instance.location(),
                    instance.yaw(), renderer.type(), replacement, instance.blocks(), selected.model(), selected.yaw(),
                    signature, instance.inventoryContents(), instance.dyeColor(), instance.useAnimationActive());
            unindex(instance);
            instances.put(updated.id(), updated);
            index(updated);
            renderers.select(instance.renderer()).remove(instance.entities());
            save();
            cleanupOriginChunk(instance.location());
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

    static boolean isReplaceable(Material material) {
        return material == Material.AIR || !material.isSolid();
    }

    private boolean canPlace(World world, List<FurnitureBlockPosition> blocks) {
        for (FurnitureBlockPosition position : blocks) {
            Block block = world.getBlockAt(position.x(), position.y(), position.z());
            BlockKey key = BlockKey.of(block);
            if (!isReplaceable(block.getType()) || blockIndex.containsKey(key) || originIndex.containsKey(key)) {
                return false;
            }
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
