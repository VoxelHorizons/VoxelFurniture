package org.voxelhorizons.furniture.model;

import org.voxelhorizons.content.item.ItemDefinition;
import java.util.Map;
import java.util.OptionalDouble;

/** Single source of price metadata for furniture, independent of Vault's balance API. */
public final class FurnitureValues {
    private FurnitureValues() {}

    public static OptionalDouble fromItem(ItemDefinition item) {
        if (item == null) return OptionalDouble.empty();
        Object furniture = item.properties().get("furniture");
        if (!(furniture instanceof Map)) return OptionalDouble.empty();
        return parse((Map<?, ?>) furniture);
    }

    /** Both value and worth are accepted for compatibility with content author terminology. */
    public static OptionalDouble parse(Map<?, ?> furniture) {
        if (furniture == null) return OptionalDouble.empty();
        if (furniture.containsKey("value") && furniture.containsKey("worth")) {
            throw new IllegalArgumentException("Furniture must define either value or worth, not both");
        }
        Object raw = furniture.containsKey("value") ? furniture.get("value") : furniture.get("worth");
        if (raw == null) return OptionalDouble.empty();
        if (!(raw instanceof Number)) throw new IllegalArgumentException("Furniture value/worth must be numeric");
        double value = ((Number) raw).doubleValue();
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException("Furniture value/worth must be finite and nonnegative");
        }
        return OptionalDouble.of(value);
    }
}
