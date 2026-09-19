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

    private static FurnitureDefinition table() {
        return new FurnitureDefinition(id("table"), id("table"), id("table"), FurnitureRendererType.AUTO,
                1, 1, 1, 90, 0, 0, 0, Collections.emptyList(), Arrays.asList(
                new FurnitureStateRule(1, 0, id("end"), 0, true),
                new FurnitureStateRule(3, 0, id("corner"), 0, true)));
    }

    private static ContentID id(String name) { return ContentID.parse("voxel:" + name, "voxel"); }
}
