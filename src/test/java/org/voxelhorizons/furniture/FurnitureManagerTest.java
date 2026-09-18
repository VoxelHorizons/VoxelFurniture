package org.voxelhorizons.furniture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FurnitureManagerTest {
    @Test public void snapsPositiveYaw() {
        assertEquals(90.0F, FurnitureManager.snapYaw(70.0F, 45.0F), 0.001F);
    }

    @Test public void wrapsNegativeYaw() {
        assertEquals(315.0F, FurnitureManager.snapYaw(-40.0F, 45.0F), 0.001F);
    }

    @Test public void wrapsFullRotation() {
        assertEquals(0.0F, FurnitureManager.snapYaw(359.0F, 45.0F), 0.001F);
    }
}
