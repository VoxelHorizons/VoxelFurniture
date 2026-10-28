package org.voxelhorizons.furniture;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureHighlightDefinition;
import org.voxelhorizons.furniture.model.FurnitureInstance;
import org.voxelhorizons.furniture.model.FurnitureRendererType;
import org.voxelhorizons.furniture.render.EntitySupport;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Player-specific hover outlines using only the platform entity visibility API.
 *
 * The real display is hidden ONLY from the targeting player, and an identical,
 * glowing, hidden-by-default temporary display is shown in its place. This avoids
 * global entity metadata, NMS, packet libraries and scoreboard interference.
 * Interaction hitboxes remain untouched.
 */
public final class FurnitureHighlightManager {
    private final Plugin plugin;
    private final FurnitureManager furniture;
    private final Map<UUID, Active> active = new HashMap<UUID, Active>();
    private boolean warned;

    public FurnitureHighlightManager(Plugin plugin, FurnitureManager furniture) {
        this.plugin = plugin;
        this.furniture = furniture;
    }

    /** Run on the server thread once per tick, after furniture movement. */
    public void tick() {
        Set<UUID> online = new HashSet<UUID>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
            try {
                Candidate target = findTarget(player);
                Active previous = active.get(player.getUniqueId());
                if (target == null) {
                    if (previous != null) clear(player.getUniqueId(), player);
                    continue;
                }
                if (previous != null && previous.matches(target)) {
                    previous.follow();
                    continue;
                }
                if (previous != null) clear(player.getUniqueId(), player);
                Active next = create(player, target);
                if (next != null) active.put(player.getUniqueId(), next);
            } catch (RuntimeException failure) {
                clear(player.getUniqueId(), player);
                if (!warned) {
                    warned = true;
                    plugin.getLogger().warning("Unable to render native furniture hover outline: " + failure);
                }
            }
        }
        for (UUID viewerId : new ArrayList<UUID>(active.keySet())) {
            if (!online.contains(viewerId)) clear(viewerId, null);
        }
    }

    public void close() {
        for (UUID id : new ArrayList<UUID>(active.keySet())) clear(id, Bukkit.getPlayer(id));
    }

    private Candidate findTarget(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Candidate nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;

        for (FurnitureInstance instance : furniture.instances()) {
            if (instance.renderer() != FurnitureRendererType.DISPLAY
                    || instance.entities().size() < 2
                    || !eye.getWorld().equals(instance.location().getWorld())) continue;
            FurnitureDefinition definition = furniture.definition(instance.definitionId()).orElse(null);
            if (definition == null) continue;
            FurnitureHighlightDefinition highlight = definition.highlight();
            if (highlight == null || !highlight.hover()) continue;

            double range = Math.min(highlight.range(), definition.viewDistance());
            double maxOffset = Math.abs(definition.hitboxOffsetX()) + Math.abs(definition.hitboxOffsetY())
                    + Math.abs(definition.hitboxOffsetZ());
            double roughRange = range + maxOffset + 2.0D;
            if (eye.distanceSquared(instance.location()) > roughRange * roughRange) continue;

            Entity interaction = EntitySupport.find(instance.entities().get(1));
            Entity main = EntitySupport.find(instance.entities().get(0));
            if (interaction == null || main == null || !interaction.isValid() || !main.isValid()) continue;

            if (eye.distanceSquared(interaction.getLocation()) > (range + 2.0D) * (range + 2.0D)) continue;
            double distance = intersectBox(eye.toVector(), direction, interaction.getLocation().toVector(),
                    definition.width(), definition.height());
            if (distance < 0.0D || distance > range || distance >= nearestDistance) continue;
            nearestDistance = distance;
            nearest = new Candidate(instance, highlight, distance);
        }
        if (nearest == null) return null;
        if (nearest.highlight.lineOfSight() && blocked(eye, direction, nearest.distance)) return null;
        return nearest;
    }

    /** Slab intersection against the real interaction entity's axis-aligned hitbox. */
    static double intersectBox(Vector eye, Vector direction, Vector center, double width, double height) {
        double[] origin = {eye.getX(), eye.getY(), eye.getZ()};
        double[] ray = {direction.getX(), direction.getY(), direction.getZ()};
        double[] middle = {center.getX(), center.getY(), center.getZ()};
        double[] extents = {width / 2.0D, height / 2.0D, width / 2.0D};
        double near = 0.0D, far = Double.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            double min = middle[axis] - extents[axis], max = middle[axis] + extents[axis];
            if (Math.abs(ray[axis]) < 1.0E-9D) {
                if (origin[axis] < min || origin[axis] > max) return -1.0D;
                continue;
            }
            double a = (min - origin[axis]) / ray[axis], b = (max - origin[axis]) / ray[axis];
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
            if (far < near) return -1.0D;
        }
        return near;
    }

    private static boolean blocked(Location eye, Vector direction, double distance) {
        try {
            // Precise vanilla block-shape test (not entity ray-tracing, since
            // ItemDisplay has no collidable hitbox). Avoids loading remote chunks.
            Method ray = World.class.getMethod("rayTraceBlocks", Location.class, Vector.class, Double.TYPE);
            Object result = ray.invoke(eye.getWorld(), eye, direction, Math.max(0.0D, distance - 0.02D));
            return result != null;
        } catch (ReflectiveOperationException failure) {
            // Fail closed: never reveal furniture through walls on unsupported APIs.
            return true;
        }
    }

    private Active create(Player player, Candidate candidate) {
        FurnitureInstance instance = candidate.instance;
        List<Entity> source = new ArrayList<Entity>();
        List<Entity> replicas = new ArrayList<Entity>();
        List<UUID> relevantIds = new ArrayList<UUID>();

        int index = 0;
        for (UUID id : instance.entities()) {
            if (index++ == 1) continue; // The Interaction entity must NEVER be outlined.
            if (!candidate.highlight.includeParts() && index > 1) break;
            Entity entity = EntitySupport.find(id);
            if (entity == null || !entity.isValid()) return null;
            // Glow on text displays is client-dependent; leave original text visible.
            if (!"ITEM_DISPLAY".equals(entity.getType().name())) continue;
            relevantIds.add(id);
            source.add(entity);
        }
        if (source.isEmpty()) return null;

        try {
            for (Entity entity : source) {
                Entity replica = duplicate(entity, candidate.highlight.rgb());
                replicas.add(replica);
            }
            // Never hide the originals until all replacement displays exist.
            for (Entity replica : replicas) show(player, replica);
            for (Entity entity : source) hide(player, entity);
            return new Active(instance.id(), relevantIds, candidate.highlight.rgb(),
                    candidate.highlight.includeParts(), source, replicas);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            for (Entity entity : source) {
                try { show(player, entity); } catch (ReflectiveOperationException ignored) { }
            }
            for (Entity replica : replicas) replica.remove();
            if (!warned) {
                warned = true;
                plugin.getLogger().warning("Unable to create hover display: " + failure);
            }
            return null;
        }
    }

    private static Entity duplicate(Entity original, int rgb) throws ReflectiveOperationException {
        Class<?> itemDisplayType = Class.forName("org.bukkit.entity.ItemDisplay");
        Method spawn = World.class.getMethod("spawn", Location.class, Class.class, Consumer.class);
        Consumer<Entity> initialize = entity -> {
            try {
                entity.getClass().getMethod("setVisibleByDefault", Boolean.TYPE).invoke(entity, false);
                entity.getClass().getMethod("setPersistent", Boolean.TYPE).invoke(entity, false); // Never save overlays.
                entity.setGravity(false);
                entity.setInvulnerable(true);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot create hidden viewer-only display", exception);
            }
        };
        Entity copy;
        try {
            copy = (Entity) spawn.invoke(original.getWorld(), original.getLocation(), itemDisplayType, initialize);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException("Could not spawn temporary display", failure.getCause());
        }

        try {
            copy.addScoreboardTag("voxelfurniture-highlight-overlay"); // Not a persisted furniture entity.
            Object item = original.getClass().getMethod("getItemStack").invoke(original);
            copy.getClass().getMethod("setItemStack", org.bukkit.inventory.ItemStack.class).invoke(copy, item);
            Class<?> transformType = Class.forName("org.bukkit.entity.ItemDisplay$ItemDisplayTransform");
            Object transform = original.getClass().getMethod("getItemDisplayTransform").invoke(original);
            copy.getClass().getMethod("setItemDisplayTransform", transformType).invoke(copy, transform);
            // Display exposes getTransformation(), not getTransformationMatrix().
            // Preserve the original model's scale, rotation and translation.
            Class<?> transformationType = Class.forName("org.bukkit.util.Transformation");
            Object transformation = original.getClass().getMethod("getTransformation").invoke(original);
            copy.getClass().getMethod("setTransformation", transformationType).invoke(copy, transformation);
            Class<?> billboardType = Class.forName("org.bukkit.entity.Display$Billboard");
            Object billboard = original.getClass().getMethod("getBillboard").invoke(original);
            copy.getClass().getMethod("setBillboard", billboardType).invoke(copy, billboard);
            Object range = original.getClass().getMethod("getViewRange").invoke(original);
            copy.getClass().getMethod("setViewRange", Float.TYPE).invoke(copy, range);
            copy.getClass().getMethod("setTeleportDuration", Integer.TYPE).invoke(copy, 1);
            copy.setGlowing(true);
            Class<?> colorType = Class.forName("org.bukkit.Color");
            Object color = colorType.getMethod("fromRGB", Integer.TYPE).invoke(null, rgb);
            copy.getClass().getMethod("setGlowColorOverride", colorType).invoke(copy, color);
            return copy;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            copy.remove();
            throw failure;
        }
    }

    private void show(Player player, Entity entity) throws ReflectiveOperationException {
        Player.class.getMethod("showEntity", Plugin.class, Entity.class).invoke(player, plugin, entity);
    }

    private void hide(Player player, Entity entity) throws ReflectiveOperationException {
        Player.class.getMethod("hideEntity", Plugin.class, Entity.class).invoke(player, plugin, entity);
    }

    private void clear(UUID playerId, Player viewer) {
        Active previous = active.remove(playerId);
        if (previous == null) return;
        if (viewer != null && viewer.isOnline()) {
            for (Entity original : previous.source) {
                if (original.isValid()) {
                    try { show(viewer, original); } catch (ReflectiveOperationException ignored) { }
                }
            }
        }
        for (Entity replica : previous.replicas) {
            if (replica.isValid()) replica.remove();
        }
    }

    private static final class Candidate {
        final FurnitureInstance instance;
        final FurnitureHighlightDefinition highlight;
        final double distance;

        Candidate(FurnitureInstance instance, FurnitureHighlightDefinition highlight, double distance) {
            this.instance = instance;
            this.highlight = highlight;
            this.distance = distance;
        }
    }

    private static final class Active {
        final UUID furnitureId;
        final List<UUID> sourceIds;
        final int rgb;
        final boolean includeParts;
        final List<Entity> source;
        final List<Entity> replicas;

        Active(UUID furnitureId, List<UUID> sourceIds, int rgb, boolean includeParts,
               List<Entity> source, List<Entity> replicas) {
            this.furnitureId = furnitureId;
            this.sourceIds = Collections.unmodifiableList(new ArrayList<UUID>(sourceIds));
            this.rgb = rgb;
            this.includeParts = includeParts;
            this.source = source;
            this.replicas = replicas;
        }

        boolean matches(Candidate candidate) {
            if (!furnitureId.equals(candidate.instance.id()) || rgb != candidate.highlight.rgb()
                    || includeParts != candidate.highlight.includeParts()) return false;
            List<UUID> currentIds = new ArrayList<UUID>();
            int index = 0;
            for (UUID id : candidate.instance.entities()) {
                if (index++ == 1) continue;
                if (!includeParts && index > 1) break;
                Entity entity = EntitySupport.find(id);
                if (entity == null || !entity.isValid()) return false;
                if ("ITEM_DISPLAY".equals(entity.getType().name())) currentIds.add(id);
            }
            return currentIds.equals(sourceIds);
        }

        void follow() {
            for (int index = 0; index < source.size(); index++) {
                Entity original = source.get(index);
                Entity replica = replicas.get(index);
                if (original.isValid() && replica.isValid()) {
                    Location location = original.getLocation();
                    if (!replica.getLocation().equals(location)) replica.teleport(location);
                }
            }
        }
    }
}
