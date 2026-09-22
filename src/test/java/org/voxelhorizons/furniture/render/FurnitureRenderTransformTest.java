package org.voxelhorizons.furniture.render;

import org.bukkit.Location;
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

    private static void assertOffset(float yaw, double expectedX, double expectedY, double expectedZ) {
        Location origin = new Location(null, 10.0D, 64.0D, 20.0D);
        Location result = FurnitureRenderTransform.applyLocalOffset(origin, yaw, -0.75D, -1.0D, 0.0D);

        assertEquals(expectedX, result.getX(), 0.000001D);
        assertEquals(expectedY, result.getY(), 0.000001D);
        assertEquals(expectedZ, result.getZ(), 0.000001D);
        assertEquals(yaw, result.getYaw(), 0.000001F);
    }
}
