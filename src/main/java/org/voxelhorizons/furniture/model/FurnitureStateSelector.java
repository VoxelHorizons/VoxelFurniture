package org.voxelhorizons.furniture.model;

import org.voxelhorizons.content.ContentID;

public final class FurnitureStateSelector {
    private FurnitureStateSelector() { }

    public static Selection select(FurnitureDefinition definition, int neighbors, float baseYaw) {
        FurnitureStateRule chosen = null;
        int turns = -1;
        for (FurnitureStateRule rule : definition.states()) {
            int match = rule.match(neighbors);
            if (match >= 0 && (chosen == null || rule.specificity() > chosen.specificity())) {
                chosen = rule;
                turns = match;
            }
        }
        return chosen == null ? new Selection(definition.modelItemId(), baseYaw)
                : new Selection(chosen.model(), (turns * 90.0F + chosen.yawOffset()) % 360.0F);
    }

    public static final class Selection {
        private final ContentID model;
        private final float yaw;

        public Selection(ContentID model, float yaw) { this.model = model; this.yaw = yaw; }
        public ContentID model() { return model; }
        public float yaw() { return yaw; }
    }
}
