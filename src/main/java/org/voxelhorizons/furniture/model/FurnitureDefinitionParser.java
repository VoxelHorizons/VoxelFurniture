package org.voxelhorizons.furniture.model;

import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FurnitureDefinitionParser {
    private static final Set<String> KEYS = new HashSet<String>(Arrays.asList(
            "renderer", "model_item", "drop", "hitbox", "scale", "view_distance", "rotation_step", "placement", "seat", "offset", "blocks", "blockstates", "animation", "inventory"
    ));
    private static final Set<String> HITBOX_KEYS = new HashSet<String>(Arrays.asList("width", "height"));
    private static final Set<String> SCALE_KEYS = new HashSet<String>(Arrays.asList("x", "y", "z"));
    private static final Set<String> OFFSET_KEYS = new HashSet<String>(Arrays.asList("x", "y", "z"));
    private static final Set<String> SEAT_KEYS = new HashSet<String>(Arrays.asList("x", "y", "z", "yaw"));
    private static final Set<String> ANIMATION_KEYS = new HashSet<String>(Arrays.asList("use"));
    private static final Set<String> INVENTORY_KEYS = new HashSet<String>(Arrays.asList("size"));
    private static final Set<String> BLOCK_KEYS = new HashSet<String>(Arrays.asList("x", "y", "z", "material"));
    private static final Set<String> STATE_KEYS = new HashSet<String>(Arrays.asList(
            "neighbors", "absent", "model_item", "rotation", "rotate", "relative", "aligned_only", "neighbor_facing"));

    private final FurnitureRendererType defaultRenderer;
    private final float defaultRotationStep;

    public FurnitureDefinitionParser(FurnitureRendererType defaultRenderer, float defaultRotationStep) {
        this.defaultRenderer = defaultRenderer;
        this.defaultRotationStep = defaultRotationStep;
    }

    public Optional<FurnitureDefinition> parse(ItemDefinition item) {
        Object raw = item.properties().get("furniture");
        if (raw == null) return Optional.empty();
        if (!(raw instanceof Map)) throw invalid(item, "properties.furniture must be a mapping");
        Map<?, ?> map = (Map<?, ?>) raw;
        rejectUnknown(item, map, KEYS, "properties.furniture");

        FurnitureRendererType renderer = FurnitureRendererType.parse(map.get("renderer"), defaultRenderer);
        ContentID modelItem = contentId(item, map.get("model_item"), item.id(), "model_item");
        ContentID drop = contentId(item, map.get("drop"), item.id(), "drop");
        float[] scale = scale(item, map.get("scale"));
        float viewDistance = positive(item, map.get("view_distance"), 64.0f, "view_distance");
        float rotationStep = positive(item, map.get("rotation_step"), defaultRotationStep, "rotation_step");
        if (rotationStep > 360.0f) throw invalid(item, "rotation_step cannot exceed 360");
        FurniturePlacement placement;
        try {
            placement = FurniturePlacement.parse(map.get("placement"));
        } catch (IllegalArgumentException exception) {
            throw invalid(item, exception.getMessage());
        }

        float width = 1.0f;
        float height = 1.0f;
        if (map.containsKey("hitbox")) {
            Map<?, ?> hitbox = nested(item, map.get("hitbox"), "hitbox");
            rejectUnknown(item, hitbox, HITBOX_KEYS, "hitbox");
            width = positive(item, hitbox.get("width"), width, "hitbox.width");
            height = positive(item, hitbox.get("height"), height, "hitbox.height");
        }

        FurnitureSeatDefinition seat = null;
        if (map.containsKey("seat")) {
            Map<?, ?> seatMap = nested(item, map.get("seat"), "seat");
            rejectUnknown(item, seatMap, SEAT_KEYS, "seat");
            double seatX = number(item, seatMap.get("x"), 0.0D, "seat.x");
            double seatY = number(item, seatMap.get("y"), -1.1D, "seat.y");
            double seatZ = number(item, seatMap.get("z"), 0.0D, "seat.z");
            float seatYaw = (float) number(item, seatMap.get("yaw"), 0.0D, "seat.yaw");
            seat = new FurnitureSeatDefinition(seatX, seatY, seatZ, seatYaw);
        }

        ContentID animationUseModel = null;
        if (map.containsKey("animation")) {
            Map<?, ?> animation = nested(item, map.get("animation"), "animation");
            rejectUnknown(item, animation, ANIMATION_KEYS, "animation");
            if (!animation.containsKey("use")) throw invalid(item, "animation requires use");
            animationUseModel = contentId(item, animation.get("use"), item.id(), "animation.use");
        }

        int inventorySize = 0;
        if (map.containsKey("inventory")) {
            Map<?, ?> inventory = nested(item, map.get("inventory"), "inventory");
            rejectUnknown(item, inventory, INVENTORY_KEYS, "inventory");
            if (!inventory.containsKey("size")) throw invalid(item, "inventory requires size");
            inventorySize = integer(item, inventory.get("size"), 0, "inventory.size");
            if (inventorySize < 9 || inventorySize > 54 || inventorySize % 9 != 0) {
                throw invalid(item, "inventory.size must be a multiple of 9 between 9 and 54");
            }
        }

        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        if (map.containsKey("offset")) {
            Map<?, ?> offset = nested(item, map.get("offset"), "offset");
            rejectUnknown(item, offset, OFFSET_KEYS, "offset");
            x = number(item, offset.get("x"), x, "offset.x");
            y = number(item, offset.get("y"), y, "offset.y");
            z = number(item, offset.get("z"), z, "offset.z");
        }
        List<FurnitureBlockDefinition> blocks = map.containsKey("blocks")
                ? blocks(item, map.get("blocks")) : Collections.<FurnitureBlockDefinition>emptyList();
        List<FurnitureStateRule> states = map.containsKey("blockstates")
                ? states(item, map.get("blockstates")) : Collections.<FurnitureStateRule>emptyList();
        if (!states.isEmpty() && rotationStep != 90.0f) {
            throw invalid(item, "blockstates require rotation_step: 90");
        }
        return Optional.of(new FurnitureDefinition(item.id(), modelItem, drop, renderer, width, height,
                scale[0], scale[1], scale[2], viewDistance, rotationStep, placement, seat,
                x, y, z, blocks, states, animationUseModel, inventorySize));
    }

    static float[] scale(ItemDefinition item, Object raw) {
        if (raw == null) return new float[] {1.0F, 1.0F, 1.0F};

        if (raw instanceof Number) {
            float uniform = positive(item, raw, 1.0F, "scale");
            return new float[] {uniform, uniform, uniform};
        }

        if (!(raw instanceof Map)) {
            throw invalid(item, "scale must be numeric or a mapping with x, y and z");
        }

        Map<?, ?> map = (Map<?, ?>) raw;
        rejectUnknown(item, map, SCALE_KEYS, "scale");
        return new float[] {
                positive(item, map.get("x"), 1.0F, "scale.x"),
                positive(item, map.get("y"), 1.0F, "scale.y"),
                positive(item, map.get("z"), 1.0F, "scale.z")
        };
    }

    private static List<FurnitureStateRule> states(ItemDefinition item, Object raw) {
        if (!(raw instanceof Collection)) throw invalid(item, "blockstates must be a list");
        List<FurnitureStateRule> result = new ArrayList<FurnitureStateRule>();
        for (Object entry : (Collection<?>) raw) {
            Map<?, ?> state = nested(item, entry, "blockstates entry");
            rejectUnknown(item, state, STATE_KEYS, "blockstates entry");
            if (!state.containsKey("model_item")) throw invalid(item, "blockstates entry requires model_item");
            int required = directions(item, state.get("neighbors"), "neighbors");
            int absent = directions(item, state.get("absent"), "absent");
            float offset = (float) number(item, state.get("rotation"), 0, "blockstates.rotation");
            if (!(state.get("rotate") == null || state.get("rotate") instanceof Boolean))
                throw invalid(item, "blockstates.rotate must be a boolean");
            if (!(state.get("relative") == null || state.get("relative") instanceof Boolean))
                throw invalid(item, "blockstates.relative must be a boolean");
            if (!(state.get("aligned_only") == null || state.get("aligned_only") instanceof Boolean))
                throw invalid(item, "blockstates.aligned_only must be a boolean");
            if (state.containsKey("aligned_only") && state.containsKey("neighbor_facing"))
                throw invalid(item, "blockstates entry cannot combine aligned_only and neighbor_facing");
            boolean rotate = !Boolean.FALSE.equals(state.get("rotate"));
            boolean relative = Boolean.TRUE.equals(state.get("relative"));
            boolean alignedOnly = !Boolean.FALSE.equals(state.get("aligned_only"));
            FurnitureStateRule.NeighborFacing neighborFacing;
            try {
                neighborFacing = FurnitureStateRule.NeighborFacing.parse(state.get("neighbor_facing"), alignedOnly);
            } catch (IllegalArgumentException exception) {
                throw invalid(item, "blockstates." + exception.getMessage());
            }
            result.add(new FurnitureStateRule(required, absent,
                    contentId(item, state.get("model_item"), item.id(), "blockstates.model_item"), offset,
                    rotate, relative, neighborFacing));
        }
        return result;
    }

    private static int directions(ItemDefinition item, Object raw, String field) {
        if (raw == null) return 0;
        if (!(raw instanceof Collection)) throw invalid(item, "blockstates." + field + " must be a list");
        int mask = 0;
        for (Object value : (Collection<?>) raw) {
            int bit = Arrays.asList("north", "east", "south", "west").indexOf(value);
            if (bit < 0) throw invalid(item, "blockstates." + field + " has invalid direction " + value);
            mask |= 1 << bit;
        }
        return mask;
    }

    private static List<FurnitureBlockDefinition> blocks(ItemDefinition item, Object raw) {
        Collection<?> entries;
        if (raw instanceof Collection) entries = (Collection<?>) raw;
        else if (raw instanceof Map) entries = ((Map<?, ?>) raw).values();
        else throw invalid(item, "blocks must be a list or numbered mapping");

        List<FurnitureBlockDefinition> result = new ArrayList<FurnitureBlockDefinition>();
        for (Object entry : entries) {
            Map<?, ?> block = nested(item, entry, "blocks entry");
            rejectUnknown(item, block, BLOCK_KEYS, "blocks entry");
            int x = integer(item, block.get("x"), 0, "blocks.x");
            int y = integer(item, block.get("y"), 0, "blocks.y");
            int z = integer(item, block.get("z"), 0, "blocks.z");
            Material material = material(item, block.get("material"));
            result.add(new FurnitureBlockDefinition(x, y, z, material));
        }
        return result;
    }

    private static int integer(ItemDefinition item, Object raw, int fallback, String field) {
        double value = number(item, raw, fallback, field);
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw invalid(item, field + " must be a whole block coordinate");
        }
        return (int) value;
    }

    private static Material material(ItemDefinition item, Object raw) {
        String name = raw == null ? "BARRIER" : String.valueOf(raw);
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isBlock() || material == Material.AIR) {
            throw invalid(item, "blocks.material must be a non-air block material, got '" + name + "'");
        }
        return material;
    }

    private static ContentID contentId(ItemDefinition item, Object raw, ContentID fallback, String field) {
        if (raw == null) return fallback;
        if (!(raw instanceof String)) throw invalid(item, field + " must be a content id string");
        return ContentID.parse((String) raw, item.id().namespace());
    }

    private static float positive(ItemDefinition item, Object raw, float fallback, String field) {
        double value = number(item, raw, fallback, field);
        if (!Double.isFinite(value) || value <= 0.0D) throw invalid(item, field + " must be greater than zero");
        return (float) value;
    }

    private static double number(ItemDefinition item, Object raw, double fallback, String field) {
        if (raw == null) return fallback;
        if (!(raw instanceof Number)) throw invalid(item, field + " must be numeric");
        double value = ((Number) raw).doubleValue();
        if (!Double.isFinite(value)) throw invalid(item, field + " must be finite");
        return value;
    }

    private static Map<?, ?> nested(ItemDefinition item, Object raw, String field) {
        if (!(raw instanceof Map)) throw invalid(item, field + " must be a mapping");
        return (Map<?, ?>) raw;
    }

    private static void rejectUnknown(ItemDefinition item, Map<?, ?> map, Set<String> keys, String field) {
        for (Object key : map.keySet()) {
            if (!(key instanceof String) || !keys.contains(key)) {
                throw invalid(item, "Unsupported key '" + key + "' in " + field);
            }
        }
    }

    private static IllegalArgumentException invalid(ItemDefinition item, String message) {
        return new IllegalArgumentException("Invalid furniture for " + item.id() + ": " + message);
    }
}
