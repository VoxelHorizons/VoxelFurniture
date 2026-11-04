package org.voxelhorizons.furniture.model;

import org.voxelhorizons.content.item.ItemDefinition;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Validates furniture price metadata independently of any installed economy.
 * The original OptionalDouble API remains available for legacy scalar prices;
 * named multi-currency prices are consumed directly by VoxelFurnitureShop.
 */
public final class FurnitureValues {
    private FurnitureValues() {}

    public static OptionalDouble fromItem(ItemDefinition item) {
        if (item == null) return OptionalDouble.empty();
        Object furniture = item.properties().get("furniture");
        if (!(furniture instanceof Map)) return OptionalDouble.empty();
        return parse((Map<?, ?>) furniture);
    }

    /** Both value and worth are accepted for compatibility with existing content. */
    public static OptionalDouble parse(Map<?, ?> furniture) {
        if (furniture == null) return OptionalDouble.empty();
        if (furniture.containsKey("value") && furniture.containsKey("worth")) {
            throw new IllegalArgumentException("Furniture must define either value or worth, not both");
        }
        Object raw = furniture.containsKey("value") ? furniture.get("value") : furniture.get("worth");
        if (raw == null) return OptionalDouble.empty();
        if (raw instanceof Map) {
            validateCurrencies((Map<?, ?>) raw);
            // A map has no single unambiguous price. Preserve the OptionalDouble
            // contract instead of arbitrarily selecting the first currency.
            return OptionalDouble.empty();
        }
        if (!(raw instanceof Number)) throw new IllegalArgumentException(
                "Furniture value/worth must be numeric or a currency-to-price mapping");
        double value = ((Number) raw).doubleValue();
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException("Furniture value/worth must be finite and nonnegative");
        }
        return OptionalDouble.of(value);
    }

    private static void validateCurrencies(Map<?, ?> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Furniture value/worth currency mapping must not be empty");
        }
        Set<String> normalized = new HashSet<String>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                throw new IllegalArgumentException("Furniture currency ID must be a string");
            }
            String currency = ((String) entry.getKey()).trim();
            if (currency.isEmpty() || currency.length() > 128) {
                throw new IllegalArgumentException("Furniture currency ID must be 1 to 128 characters");
            }
            for (int i = 0; i < currency.length(); i++) {
                if (Character.isISOControl(currency.charAt(i))) {
                    throw new IllegalArgumentException("Furniture currency ID contains control characters");
                }
            }
            if (!normalized.add(currency.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate furniture currency ID: " + currency);
            }
            Object amount = entry.getValue();
            if (!(amount instanceof Number)) {
                throw new IllegalArgumentException("Furniture price for " + currency + " must be numeric");
            }
            final BigDecimal value;
            try {
                value = new BigDecimal(amount.toString());
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("Furniture price for " + currency + " must be finite", invalid);
            }
            if (value.signum() < 0) {
                throw new IllegalArgumentException("Furniture price for " + currency + " must be nonnegative");
            }
        }
    }
}
