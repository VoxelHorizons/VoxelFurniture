package org.voxelhorizons.furniture.model;

import org.voxelhorizons.content.ContentID;

public final class FurnitureStateSelector {
    private FurnitureStateSelector() { }

    public static Selection select(FurnitureDefinition definition, int neighbors, float baseYaw) {
        return select(definition, neighbors, neighbors, 0, 0, 0, 0, baseYaw);
    }

    /** Aligned neighbors have the same snapped placement yaw as this furniture. */
    public static Selection select(FurnitureDefinition definition, int neighbors, int alignedNeighbors, float baseYaw) {
        return select(definition, neighbors, alignedNeighbors, 0, 0, 0, 0, baseYaw);
    }

    public static Selection select(FurnitureDefinition definition, int neighbors, int alignedNeighbors,
                                   int perpendicularNeighbors, int clockwiseNeighbors, int counterClockwiseNeighbors,
                                   int oppositeNeighbors, float baseYaw) {
        FurnitureStateRule chosen = null;
        int turns = -1;
        for (FurnitureStateRule rule : definition.states()) {
            int sourceNeighbors;
            switch (rule.neighborFacing()) {
                case SAME: sourceNeighbors = alignedNeighbors; break;
                case PERPENDICULAR: sourceNeighbors = perpendicularNeighbors; break;
                case CLOCKWISE: sourceNeighbors = clockwiseNeighbors; break;
                case COUNTERCLOCKWISE: sourceNeighbors = counterClockwiseNeighbors; break;
                case OPPOSITE: sourceNeighbors = oppositeNeighbors; break;
                default: sourceNeighbors = neighbors;
            }
            int localNeighbors = rule.relative()
                    ? FurnitureStateRule.rotate(sourceNeighbors, (4 - (Math.round(baseYaw / 90.0f) & 3)) & 3)
                    : sourceNeighbors;
            int match = rule.match(localNeighbors);
            if (match < 0) continue;

            int specificity = rule.specificity();
            int chosenSpecificity = chosen == null ? -1 : chosen.specificity();

            // Prefer the most specific rule first. For equal specificity,
            // prefer the rule that matched with fewer quarter-turn rotations.
            // This lets an explicit corner pattern beat an earlier generic
            // auto-rotating corner rule that only matches after rotation.
            if (chosen == null
                    || specificity > chosenSpecificity
                    || (specificity == chosenSpecificity && match < turns)) {
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
