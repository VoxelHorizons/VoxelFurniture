package org.voxelhorizons.furniture;

import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
    @Test
    public void targetsVisibleOffsetModelEvenAboveTheInteractionBox() {
        Vector eye = new Vector(0, 2.2D, 0);
        Vector direction = new Vector(0, 0, 1);
        Vector hitboxCenter = new Vector(0, 0.5D, 4);
        Vector visualCenter = new Vector(0, 1.5D, 4);

        // The 1x1 Interaction entity is below the crosshair.
        assertEquals(-1.0D, FurnitureHighlightManager.intersectBox(
                eye, direction, hitboxCenter, 1.0D, 1.0D), 0.0D);

        // The scaled and offset ItemDisplay is still aimed at.
        assertTrue(FurnitureHighlightManager.intersectFurniture(
                eye, direction, hitboxCenter, 1.0D, 1.0D,
                visualCenter, 1.5D, 1.5D, 1.5D) > 0.0D);
    }

    @Test
    public void retainsInteractionTargetingWhenModelIsElsewhere() {
        double hit = FurnitureHighlightManager.intersectFurniture(
                new Vector(0, 0.5D, 0), new Vector(0, 0, 1),
                new Vector(0, 0.5D, 4), 1.0D, 1.0D,
                new Vector(10, 10, 4), 1.0D, 1.0D, 1.0D);
        assertEquals(3.5D, hit, 1.0E-8D);
    }

    @Test
    public void missesBothInteractionAndModelWhenNotAimingAtThem() {
        assertEquals(-1.0D, FurnitureHighlightManager.intersectFurniture(
                new Vector(3, 1.5D, 0), new Vector(0, 0, 1),
                new Vector(0, 0.5D, 4), 1.0D, 1.0D,
                new Vector(0, 1.5D, 4), 1.5D, 1.5D, 1.5D), 0.0D);
    }


    @Test
    public void conditionFlagsControlWhetherPerViewerSelectionIsNeeded() {
        // ALWAYS stays on real display and needs no per-player overlay.
        org.voxelhorizons.furniture.model.FurnitureHighlightDefinition always =
                new org.voxelhorizons.furniture.model.FurnitureHighlightDefinition(
                        false, 0xFFFFFF, 16.0D, false, false);
        org.voxelhorizons.furniture.model.FurnitureHighlightDefinition hover =
                new org.voxelhorizons.furniture.model.FurnitureHighlightDefinition(
                        true, 0xFFFFFF, 16.0D, false, false);
        org.voxelhorizons.furniture.model.FurnitureHighlightDefinition proximity =
                new org.voxelhorizons.furniture.model.FurnitureHighlightDefinition(
                        false, 0xFFFFFF, 16.0D, true, false);
        org.voxelhorizons.furniture.model.FurnitureHighlightDefinition both =
                new org.voxelhorizons.furniture.model.FurnitureHighlightDefinition(
                        true, 0xFFFFFF, 16.0D, true, false);

        org.junit.Assert.assertFalse(FurnitureHighlightManager.requiresViewerCheck(always));
        assertTrue(FurnitureHighlightManager.requiresViewerCheck(hover));
        assertTrue(FurnitureHighlightManager.requiresViewerCheck(proximity));
        assertTrue(FurnitureHighlightManager.requiresViewerCheck(both));
        org.junit.Assert.assertFalse(FurnitureHighlightManager.requiresViewerCheck(null));
    }
}
