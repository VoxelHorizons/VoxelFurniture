package org.voxelhorizons.furniture.model;

import java.util.Locale;

public enum FurnitureRendererType {
    AUTO,
    DISPLAY,
    ARMOR_STAND;

    public static FurnitureRendererType parse(Object raw, FurnitureRendererType fallback) {
        if (raw == null) return fallback;
        if (!(raw instanceof String)) throw new IllegalArgumentException("furniture.renderer must be a string");
        try {
            return valueOf(((String) raw).trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown furniture renderer '" + raw + "'");
        }
    }
}
