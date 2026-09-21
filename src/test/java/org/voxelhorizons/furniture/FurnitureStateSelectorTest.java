package org.voxelhorizons.furniture;

import org.junit.Test;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureRendererType;
import org.voxelhorizons.furniture.model.FurnitureStateRule;
import org.voxelhorizons.furniture.model.FurnitureStateSelector;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class FurnitureStateSelectorTest {
    @Test public void rotatesEndsTowardsNeighbors() {
        FurnitureDefinition table = table();
        assertEquals(id("end"), FurnitureStateSelector.select(table, 1, 180).model());
        assertEquals(0.0f, FurnitureStateSelector.select(table, 1, 180).yaw(), 0.01f);
        assertEquals(90.0f, FurnitureStateSelector.select(table, 2, 180).yaw(), 0.01f);
    }

    @Test public void prefersMostSpecificRuleAndFallsBackWhenIsolated() {
        assertEquals(id("corner"), FurnitureStateSelector.select(table(), 3, 0).model());
        assertEquals(id("table"), FurnitureStateSelector.select(table(), 0, 270).model());
        assertEquals(270.0f, FurnitureStateSelector.select(table(), 0, 270).yaw(), 0.01f);
    }

    @Test public void relativeLeftAndRightEndsFollowChairFacingAndIgnorePerpendicularChairs() {
        FurnitureDefinition chair = new FurnitureDefinition(id("chair"), id("chair"), id("chair"),
                FurnitureRendererType.AUTO, 1, 1, 1, 90, 0, 0, 0, Collections.emptyList(), Arrays.asList(
                new FurnitureStateRule(2, 13, id("left"), 0, false, true),
                new FurnitureStateRule(8, 7, id("right"), 0, false, true),
                new FurnitureStateRule(10, 5, id("middle"), 0, false, true)));
        assertEquals(id("left"), FurnitureStateSelector.select(chair, 2, 2, 0).model());
        assertEquals(id("right"), FurnitureStateSelector.select(chair, 8, 8, 0).model());
        assertEquals(id("middle"), FurnitureStateSelector.select(chair, 10, 10, 0).model());
        assertEquals(id("left"), FurnitureStateSelector.select(chair, 4, 4, 90).model());
        assertEquals(90.0f, FurnitureStateSelector.select(chair, 4, 4, 90).yaw(), 0.01f);
        assertEquals(id("right"), FurnitureStateSelector.select(chair, 1, 1, 90).model());
        assertEquals(id("chair"), FurnitureStateSelector.select(chair, 2, 0, 0).model());
    }

    @Test public void exactCornerRuleBeatsEarlierRotatedCornerRuleAtEqualSpecificity() {
        FurnitureDefinition chair = new FurnitureDefinition(id("chair"), id("chair"), id("chair"),
                FurnitureRendererType.AUTO, 1, 1, 1, 90, 0, 0, 0, Collections.emptyList(), Arrays.asList(
                // This first rule can rotate and therefore also matches north+east after two turns.
                new FurnitureStateRule(12, 3, id("rotating"), 0, true, true, false),
                // Explicit north+east rule should win because it matches without rotation.
                new FurnitureStateRule(3, 12, id("exact"), 0, false, true, false)));

        assertEquals(id("exact"), FurnitureStateSelector.select(chair, 3, 0, 0).model());
    }

    @Test public void relativeRulesCanCountPerpendicularNeighborsWhenAlignedOnlyIsDisabled() {
        FurnitureDefinition chair = new FurnitureDefinition(id("chair"), id("chair"), id("chair"),
                FurnitureRendererType.AUTO, 1, 1, 1, 90, 0, 0, 0, Collections.emptyList(), Arrays.asList(
                new FurnitureStateRule(10, 5, id("middle"), 0, false, true, false),
                new FurnitureStateRule(12, 3, id("inner"), 0, false, true, false),
                new FurnitureStateRule(9, 6, id("outer"), 0, false, true, false)));

        // Both west/east neighbours exist, but only the east one shares this chair's yaw.
        assertEquals(id("middle"), FurnitureStateSelector.select(chair, 10, 2, 0).model());

        // Local west+south and west+north can now be distinguished instead of
        // being collapsed into one auto-rotated world-space corner.
        assertEquals(id("inner"), FurnitureStateSelector.select(chair, 12, 8, 0).model());
        assertEquals(id("outer"), FurnitureStateSelector.select(chair, 9, 8, 0).model());
    }

    @Test public void perpendicularRulesIgnoreSameFacingNeighbors() {
        FurnitureDefinition chair = new FurnitureDefinition(id("chair"), id("chair"), id("chair"),
                FurnitureRendererType.AUTO, 1, 1, 1, 90, 0, 0, 0, Collections.emptyList(), Arrays.asList(
                new FurnitureStateRule(1, 14, id("corner"), 0, false, true,
                        FurnitureStateRule.NeighborFacing.PERPENDICULAR)));

        assertEquals(id("chair"), FurnitureStateSelector.select(chair, 1, 1, 0, 0, 0).model());
        assertEquals(id("corner"), FurnitureStateSelector.select(chair, 1, 0, 1, 0, 0).model());
    }

    private static FurnitureDefinition table() {
        return new FurnitureDefinition(id("table"), id("table"), id("table"), FurnitureRendererType.AUTO,
                1, 1, 1, 90, 0, 0, 0, Collections.emptyList(), Arrays.asList(
                new FurnitureStateRule(1, 0, id("end"), 0, true),
                new FurnitureStateRule(3, 0, id("corner"), 0, true)));
    }

    private static ContentID id(String name) { return ContentID.parse("voxel:" + name, "voxel"); }
}
