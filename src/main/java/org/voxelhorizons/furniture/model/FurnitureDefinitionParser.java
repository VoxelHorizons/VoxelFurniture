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
            "renderer", "model_item", "drop", "hitbox", "scale", "view_distance", "rotation_step", "placement", "seat", "offset", "blocks", "blockstates", "animation", "idle_animation", "display_parts", "interaction", "inventory"
    ));
    private static final Set<String> HITBOX_KEYS = new HashSet<String>(Arrays.asList("width", "height", "offset"));
    private static final Set<String> HITBOX_OFFSET_KEYS = new HashSet<String>(Arrays.asList("x", "y", "z"));
    private static final Set<String> SCALE_KEYS = new HashSet<String>(Arrays.asList("x", "y", "z"));
    private static final Set<String> OFFSET_KEYS = new HashSet<String>(Arrays.asList("x", "y", "z", "rotation"));
    private static final Set<String> SEAT_KEYS = new HashSet<String>(Arrays.asList("x", "y", "z", "yaw"));
    private static final Set<String> ANIMATION_KEYS = new HashSet<String>(Arrays.asList("use", "close_delay", "sync_neighbors"));
    private static final Set<String> INVENTORY_KEYS = new HashSet<String>(Arrays.asList("size"));
    private static final Set<String> IDLE_KEYS = new HashSet<String>(Arrays.asList("bob", "spin"));
    private static final Set<String> BOB_KEYS = new HashSet<String>(Arrays.asList("amplitude", "period_ticks"));
    private static final Set<String> SPIN_KEYS = new HashSet<String>(Arrays.asList("degrees_per_tick"));
    private static final Set<String> PART_KEYS = new HashSet<String>(Arrays.asList(
            "type", "model_item", "text", "billboard", "offset", "scale", "bob", "spin"));
    private static final Set<String> PART_OFFSET_KEYS = new HashSet<String>(Arrays.asList("x", "y", "z"));
    private static final Set<String> INTERACTION_KEYS = new HashSet<String>(Arrays.asList("commands"));
    private static final Set<String> COMMAND_KEYS = new HashSet<String>(Arrays.asList("command", "executor"));
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
        double hitboxOffsetX = 0.0D;
        double hitboxOffsetY = 0.0D;
        double hitboxOffsetZ = 0.0D;
        if (map.containsKey("hitbox")) {
            Map<?, ?> hitbox = nested(item, map.get("hitbox"), "hitbox");
            rejectUnknown(item, hitbox, HITBOX_KEYS, "hitbox");
            width = positive(item, hitbox.get("width"), width, "hitbox.width");
            height = positive(item, hitbox.get("height"), height, "hitbox.height");
            if (hitbox.containsKey("offset")) {
                Map<?, ?> hitboxOffset = nested(item, hitbox.get("offset"), "hitbox.offset");
                rejectUnknown(item, hitboxOffset, HITBOX_OFFSET_KEYS, "hitbox.offset");
                hitboxOffsetX = number(item, hitboxOffset.get("x"), hitboxOffsetX, "hitbox.offset.x");
                hitboxOffsetY = number(item, hitboxOffset.get("y"), hitboxOffsetY, "hitbox.offset.y");
                hitboxOffsetZ = number(item, hitboxOffset.get("z"), hitboxOffsetZ, "hitbox.offset.z");
            }
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
        int animationCloseDelay = 10;
        boolean animationSyncNeighbors = false;
        if (map.containsKey("animation")) {
            Map<?, ?> animation = nested(item, map.get("animation"), "animation");
            rejectUnknown(item, animation, ANIMATION_KEYS, "animation");
            if (!animation.containsKey("use")) throw invalid(item, "animation requires use");
            animationUseModel = contentId(item, animation.get("use"), item.id(), "animation.use");
            animationCloseDelay = integer(item, animation.get("close_delay"), animationCloseDelay,
                    "animation.close_delay");
            if (animationCloseDelay < 0) throw invalid(item, "animation.close_delay cannot be negative");
            Object syncNeighbors = animation.get("sync_neighbors");
            if (!(syncNeighbors == null || syncNeighbors instanceof Boolean))
                throw invalid(item, "animation.sync_neighbors must be a boolean");
            animationSyncNeighbors = Boolean.TRUE.equals(syncNeighbors);
        }

        FurnitureIdleAnimationDefinition idleAnimation = null;
        if (map.containsKey("idle_animation")) {
            Map<?, ?> idle = nested(item, map.get("idle_animation"), "idle_animation");
            rejectUnknown(item, idle, IDLE_KEYS, "idle_animation");
            double bobAmplitude = 0.0D;
            int bobPeriodTicks = 40;
            float spinDegreesPerTick = 0.0F;
            if (idle.containsKey("bob")) {
                Map<?, ?> bob = nested(item, idle.get("bob"), "idle_animation.bob");
                rejectUnknown(item, bob, BOB_KEYS, "idle_animation.bob");
                bobAmplitude = number(item, bob.get("amplitude"), 0.0D, "idle_animation.bob.amplitude");
                bobPeriodTicks = integer(item, bob.get("period_ticks"), 40, "idle_animation.bob.period_ticks");
                if (bobPeriodTicks <= 0) throw invalid(item, "idle_animation.bob.period_ticks must be greater than zero");
            }
            if (idle.containsKey("spin")) {
                Map<?, ?> spin = nested(item, idle.get("spin"), "idle_animation.spin");
                rejectUnknown(item, spin, SPIN_KEYS, "idle_animation.spin");
                spinDegreesPerTick = (float) number(item, spin.get("degrees_per_tick"), 0.0D,
                        "idle_animation.spin.degrees_per_tick");
            }
            idleAnimation = new FurnitureIdleAnimationDefinition(bobAmplitude, bobPeriodTicks, spinDegreesPerTick);
        }

        List<FurnitureDisplayPartDefinition> displayParts = Collections.emptyList();
        if (map.containsKey("display_parts")) {
            Map<?, ?> parts = nested(item, map.get("display_parts"), "display_parts");
            List<FurnitureDisplayPartDefinition> parsed = new ArrayList<FurnitureDisplayPartDefinition>();
            for (Map.Entry<?, ?> entry : parts.entrySet()) {
                String id = String.valueOf(entry.getKey());
                if (!id.matches("[a-z0-9_-]+")) throw invalid(item, "display_parts ids use lowercase letters, numbers, _ and -");
                Map<?, ?> part = nested(item, entry.getValue(), "display_parts." + id);
                rejectUnknown(item, part, PART_KEYS, "display_parts." + id);
                String typeName = part.get("type") == null ? "text" : String.valueOf(part.get("type")).toLowerCase(java.util.Locale.ROOT);
                FurnitureDisplayPartDefinition.Type type;
                if ("text".equals(typeName)) type = FurnitureDisplayPartDefinition.Type.TEXT;
                else if ("item".equals(typeName)) type = FurnitureDisplayPartDefinition.Type.ITEM;
                else throw invalid(item, "display_parts." + id + ".type must be text or item");
                ContentID partModel = null;
                String text = null;
                if (type == FurnitureDisplayPartDefinition.Type.TEXT) {
                    if (!(part.get("text") instanceof String)) throw invalid(item, "display_parts." + id + ".text is required");
                    text = (String) part.get("text");
                } else {
                    if (!(part.get("model_item") instanceof String)) throw invalid(item, "display_parts." + id + ".model_item is required");
                    partModel = contentId(item, part.get("model_item"), item.id(), "display_parts." + id + ".model_item");
                }
                String billboard = part.get("billboard") == null ? "FIXED" : String.valueOf(part.get("billboard")).toUpperCase(java.util.Locale.ROOT);
                if (!Arrays.asList("FIXED", "VERTICAL", "HORIZONTAL", "CENTER").contains(billboard))
                    throw invalid(item, "display_parts." + id + ".billboard must be FIXED, VERTICAL, HORIZONTAL or CENTER");
                double px = 0.0D, py = 0.0D, pz = 0.0D;
                if (part.containsKey("offset")) {
                    Map<?, ?> offset = nested(item, part.get("offset"), "display_parts." + id + ".offset");
                    rejectUnknown(item, offset, PART_OFFSET_KEYS, "display_parts." + id + ".offset");
                    px = number(item, offset.get("x"), 0.0D, "display_parts." + id + ".offset.x");
                    py = number(item, offset.get("y"), 0.0D, "display_parts." + id + ".offset.y");
                    pz = number(item, offset.get("z"), 0.0D, "display_parts." + id + ".offset.z");
                }
                float partScale = positive(item, part.get("scale"), 1.0F, "display_parts." + id + ".scale");
                Object bob = part.get("bob"), spin = part.get("spin");
                if (!(bob == null || bob instanceof Boolean)) throw invalid(item, "display_parts." + id + ".bob must be a boolean");
                if (!(spin == null || spin instanceof Boolean)) throw invalid(item, "display_parts." + id + ".spin must be a boolean");
                parsed.add(new FurnitureDisplayPartDefinition(id, type, partModel, text, billboard, px, py, pz,
                        partScale, !Boolean.FALSE.equals(bob), Boolean.TRUE.equals(spin)));
            }
            displayParts = parsed;
        }

        List<FurnitureInteractionCommand> interactionCommands = Collections.emptyList();
        if (map.containsKey("interaction")) {
            Map<?, ?> interaction = nested(item, map.get("interaction"), "interaction");
            rejectUnknown(item, interaction, INTERACTION_KEYS, "interaction");
            Object commandsRaw = interaction.get("commands");
            if (commandsRaw != null) {
                if (!(commandsRaw instanceof Collection))
                    throw invalid(item, "interaction.commands must be a list");
                List<FurnitureInteractionCommand> parsedCommands = new ArrayList<FurnitureInteractionCommand>();
                int index = 0;
                for (Object commandRaw : (Collection<?>) commandsRaw) {
                    String command;
                    FurnitureInteractionCommand.Executor executor = FurnitureInteractionCommand.Executor.PLAYER;
                    if (commandRaw instanceof String) {
                        command = ((String) commandRaw).trim();
                    } else {
                        Map<?, ?> commandMap = nested(item, commandRaw,
                                "interaction.commands[" + index + "]");
                        rejectUnknown(item, commandMap, COMMAND_KEYS,
                                "interaction.commands[" + index + "]");
                        Object value = commandMap.get("command");
                        if (!(value instanceof String))
                            throw invalid(item, "interaction.commands[" + index + "].command is required");
                        command = ((String) value).trim();
                        try {
                            executor = FurnitureInteractionCommand.Executor.parse(commandMap.get("executor"));
                        } catch (IllegalArgumentException exception) {
                            throw invalid(item, "interaction.commands[" + index + "]." + exception.getMessage());
                        }
                    }
                    if (command.startsWith("/")) command = command.substring(1).trim();
                    if (command.isEmpty())
                        throw invalid(item, "interaction.commands[" + index + "] cannot be empty");
                    parsedCommands.add(new FurnitureInteractionCommand(command, executor));
                    index++;
                }
                interactionCommands = parsedCommands;
            }
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
        float offsetRotation = 0.0F;
        if (map.containsKey("offset")) {
            Map<?, ?> offset = nested(item, map.get("offset"), "offset");
            rejectUnknown(item, offset, OFFSET_KEYS, "offset");
            x = number(item, offset.get("x"), x, "offset.x");
            y = number(item, offset.get("y"), y, "offset.y");
            z = number(item, offset.get("z"), z, "offset.z");
            offsetRotation = (float) number(item, offset.get("rotation"), offsetRotation, "offset.rotation");
        }
        List<FurnitureBlockDefinition> blocks = map.containsKey("blocks")
                ? blocks(item, map.get("blocks")) : Collections.<FurnitureBlockDefinition>emptyList();
        List<FurnitureStateRule> states = map.containsKey("blockstates")
                ? states(item, map.get("blockstates")) : Collections.<FurnitureStateRule>emptyList();
        if (!states.isEmpty() && rotationStep != 90.0f) {
            throw invalid(item, "blockstates require rotation_step: 90");
        }
        return Optional.of(new FurnitureDefinition(item.id(), modelItem, drop, renderer, width, height,
                hitboxOffsetX, hitboxOffsetY, hitboxOffsetZ,
                scale[0], scale[1], scale[2], viewDistance, rotationStep, placement, seat,
                x, y, z, offsetRotation, blocks, states, animationUseModel, animationCloseDelay,
                animationSyncNeighbors, idleAnimation, displayParts, interactionCommands, inventorySize));
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
