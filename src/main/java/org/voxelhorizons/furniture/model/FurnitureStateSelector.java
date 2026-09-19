package org.voxelhorizons.furniture.model;

import org.voxelhorizons.content.ContentID;

public final class FurnitureStateSelector {
    private FurnitureStateSelector() { }

    public static Selection select(FurnitureDefinition definition, int neighbors, float baseYaw) {
        return select(definition, neighbors, neighbors, baseYaw);
    }

    /** Aligned neighbors have the same snapped placement yaw as this furniture. */
    public static Selection select(FurnitureDefinition definition, int neighbors, int alignedNeighbors, float baseYaw) {
        FurnitureStateRule chosen = null;
        int turns = -1;
        for (FurnitureStateRule rule : definition.states()) {
            int localNeighbors = rule.relative()
                    ? FurnitureStateRule.rotate(alignedNeighbors, (4 - (Math.round(baseYaw / 90.0f) & 3)) & 3)
                    : neighbors;
            int match = rule.match(localNeighbors);
            if (match >= 0 && (chosen == null || rule.specificity() > chosen.specificity())) {
                chosen = rule;
                turns = match;
            }
        }
        if (chosen == null) return new Selection(definition.modelItemId(), baseYaw);
        float yaw = (turns * 90.0F + chosen.yawOffset() + (chosen.relative() ? baseYaw : 0.0F)) % 360.0F;
        return new Selection(chosen.model(), yaw < 0 ? yaw + 360.0F : yaw);
    }

    public static final class Selection {
        private final ContentID model;
        private final float yaw;

        public Selection(ContentID model, float yaw) { this.model = model; this.yaw = yaw; }
        public ContentID model() { return model; }
        public float yaw() { return yaw; }
    }
}
