package org.voxelhorizons.furniture;

import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FurnitureHighlightManagerTest {
    @Test
    public void detectsFrontFaceOfInteractionBox() {
        assertEquals(3.5D, FurnitureHighlightManager.intersectBox(
                new Vector(0, 0.5, 0), new Vector(0, 0, 1),
                new Vector(0, 0.5, 4), 1.0, 1.0), 1.0E-8);
    }

    @Test
    public void rejectsRayPointedAway() {
        assertEquals(-1.0D, FurnitureHighlightManager.intersectBox(
                new Vector(0, 0.5, 0), new Vector(0, 0, -1),
                new Vector(0, 0.5, 4), 1.0, 1.0), 0.0D);
    }

    @Test
    public void rejectsRayMissingHitbox() {
        assertEquals(-1.0D, FurnitureHighlightManager.intersectBox(
                new Vector(2, 0.5, 0), new Vector(0, 0, 1),
                new Vector(0, 0.5, 4), 1.0, 1.0), 0.0D);
    }
}
