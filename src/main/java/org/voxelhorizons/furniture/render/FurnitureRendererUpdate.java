package org.voxelhorizons.furniture.render;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.furniture.model.FurnitureDefinition;

import java.util.List;
import java.util.UUID;

/** Reuse an existing renderer whenever the selected visual state is structurally compatible. */
public final class FurnitureRendererUpdate {
    private FurnitureRendererUpdate() {}

    public static final class Result {
        private final List<UUID> entities;
        private final boolean reused;

        private Result(List<UUID> entities, boolean reused) {
            this.entities = entities;
            this.reused = reused;
        }

        public List<UUID> entities() { return entities; }
        public boolean reused() { return reused; }
    }

    public static Result apply(FurnitureRenderer renderer, List<UUID> existing, boolean compatible,
                               Location origin, float yaw, ItemStack model, FurnitureDefinition visual) {
        if (compatible && renderer.update(existing, origin, yaw, model, visual)) {
            return new Result(existing, true);
        }
        return new Result(renderer.spawn(origin, yaw, model, visual), false);
    }
}
