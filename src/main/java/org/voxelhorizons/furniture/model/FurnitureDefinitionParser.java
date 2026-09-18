package org.voxelhorizons.furniture.model;

import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.content.item.ItemDefinition;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FurnitureDefinitionParser {
    private static final Set<String> KEYS = new HashSet<String>(Arrays.asList(
            "renderer", "model_item", "drop", "hitbox", "scale", "rotation_step", "offset"
    ));
    private static final Set<String> HITBOX_KEYS = new HashSet<String>(Arrays.asList("width", "height"));
    private static final Set<String> OFFSET_KEYS = new HashSet<String>(Arrays.asList("x", "y", "z"));

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
        float scale = positive(item, map.get("scale"), 1.0f, "scale");
        float rotationStep = positive(item, map.get("rotation_step"), defaultRotationStep, "rotation_step");
        if (rotationStep > 360.0f) throw invalid(item, "rotation_step cannot exceed 360");

        float width = 1.0f;
        float height = 1.0f;
        if (map.containsKey("hitbox")) {
            Map<?, ?> hitbox = nested(item, map.get("hitbox"), "hitbox");
            rejectUnknown(item, hitbox, HITBOX_KEYS, "hitbox");
            width = positive(item, hitbox.get("width"), width, "hitbox.width");
            height = positive(item, hitbox.get("height"), height, "hitbox.height");
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
        return Optional.of(new FurnitureDefinition(item.id(), modelItem, drop, renderer, width, height, scale,
                rotationStep, x, y, z));
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
