package org.voxelhorizons.furniture.inventory;

import org.junit.Test;
import static org.junit.Assert.*;

/** Numeric window metadata must use vanilla smoker property slots, not fake items. */
public class FurnitureSmokerProgressTest {
    @Test public void nativeWindowDataUsesFuelAndCookTimesInVanillaOrder() {
        assertArrayEquals(new int[] {799, 800, 25, 100},
                FurnitureSmokerViewUpdater.NativeProgress.windowData(799, 800, 25, 100));
    }

    @Test public void noFuelClearsFlameButKeepsValidContainerDenominators() {
        assertArrayEquals(new int[] {0, 1, 0, 100},
                FurnitureSmokerViewUpdater.NativeProgress.windowData(0, 0, 0, 100));
    }
}
