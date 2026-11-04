package org.voxelhorizons.furniture.render;

import org.bukkit.Location;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureBlockDefinition;
import org.voxelhorizons.furniture.model.FurnitureRendererType;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FurnitureRenderTransformTest {

    @Test public void keepsOffsetOnLocalXAxisAtZeroDegrees() {
        assertOffset(0.0F, 9.25D, 63.0D, 20.0D);
    }

    @Test public void rotatesLocalXAxisOntoNegativeZAtNinetyDegrees() {
        assertOffset(90.0F, 10.0D, 63.0D, 19.25D);
    }

    @Test public void reversesLocalXAxisAtOneEightyDegrees() {
        assertOffset(180.0F, 10.75D, 63.0D, 20.0D);
    }

    @Test public void rotatesLocalXAxisOntoPositiveZAtTwoSeventyDegrees() {
        assertOffset(270.0F, 10.0D, 63.0D, 20.75D);
    }

    @Test public void appliesModelRotationWithoutRotatingOffset() {
        Location origin = new Location(null, 10.0D, 64.0D, 20.0D);
        Location result = FurnitureRenderTransform.applyLocalOffset(
                origin, 90.0F, -0.75D, -1.0D, 0.0D, 90.0F);

        assertEquals(10.0D, result.getX(), 0.000001D);
        assertEquals(63.0D, result.getY(), 0.000001D);
        assertEquals(19.25D, result.getZ(), 0.000001D);
        assertEquals(180.0F, result.getYaw(), 0.000001F);
    }

    @Test public void animationVariantModelPoseUsesItsOwnOffsetNotBaseOffset() {
        ContentID closedId = ContentID.parse("voxel:oak_dresser", "voxel");
        ContentID openId = ContentID.parse("voxel:oak_dresser_open", "voxel");
        FurnitureDefinition closed = new FurnitureDefinition(closedId, closedId, closedId,
                FurnitureRendererType.DISPLAY, 2.0F, 1.0F, 2.0F, 90.0F,
                0.5D, 0.5D, 0.0D, Collections.<FurnitureBlockDefinition>emptyList());
        FurnitureDefinition open = new FurnitureDefinition(openId, openId, closedId,
                FurnitureRendererType.DISPLAY, 2.0F, 1.0F, 2.0F, 90.0F,
                0.0D, 0.5D, 0.0D, Collections.<FurnitureBlockDefinition>emptyList());
        Location origin = new Location(null, 10.0D, 64.0D, 20.0D);
        Location closedPose = FurnitureRenderTransform.modelLocation(origin, 180.0F,
                closed, FurnitureRendererType.DISPLAY);
        Location openPose = FurnitureRenderTransform.modelLocation(origin, 180.0F,
                open, FurnitureRendererType.DISPLAY);

        // Changing the animation model's x value moves the display entity by half a block.
        assertEquals(9.5D, closedPose.getX(), 0.000001D);
        assertEquals(10.0D, openPose.getX(), 0.000001D);
        assertEquals(64.5D, closedPose.getY(), 0.000001D);
        assertEquals(64.5D, openPose.getY(), 0.000001D);
    }

    @Test public void armorStandModelPoseKeepsHistoricalVerticalAlignment() {
        ContentID id = ContentID.parse("voxel:oak_dresser", "voxel");
        FurnitureDefinition visual = new FurnitureDefinition(id, id, id,
                FurnitureRendererType.ARMOR_STAND, 2.0F, 1.0F, 2.0F, 90.0F,
                0.5D, 0.5D, 0.0D, Collections.<FurnitureBlockDefinition>emptyList());
        Location pose = FurnitureRenderTransform.modelLocation(
                new Location(null, 10.0D, 64.0D, 20.0D), 0.0F,
                visual, FurnitureRendererType.ARMOR_STAND);
        assertEquals(10.5D, pose.getX(), 0.000001D);
        assertEquals(63.05D, pose.getY(), 0.000001D);
    }

    private static void assertOffset(float yaw, double expectedX, double expectedY, double expectedZ) {
        Location origin = new Location(null, 10.0D, 64.0D, 20.0D);
        Location result = FurnitureRenderTransform.applyLocalOffset(origin, yaw, -0.75D, -1.0D, 0.0D);

        assertEquals(expectedX, result.getX(), 0.000001D);
        assertEquals(expectedY, result.getY(), 0.000001D);
        assertEquals(expectedZ, result.getZ(), 0.000001D);
        assertEquals(yaw, result.getYaw(), 0.000001F);
    }
}
