package org.voxelhorizons.furniture;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Safety policy regression: only Bedrock storage pickup changes. */
public class BedrockFurnitureSafetyTest {
    @Test
    public void ordinaryBedrockStorageAttackDoesNotRemoveFurniture() {
        assertTrue(BedrockFurnitureSafety.blocksStoragePickup(true, true, true, false));
    }

    @Test
    public void deliberateSneakingAttackAllowsBedrockStoragePickup() {
        assertFalse(BedrockFurnitureSafety.blocksStoragePickup(true, true, true, true));
    }

    @Test
    public void javaStoragePickupRemainsUnchanged() {
        assertFalse(BedrockFurnitureSafety.blocksStoragePickup(true, false, true, false));
    }

    @Test
    public void nonStorageBedrockFurniturePickupRemainsUnchanged() {
        assertFalse(BedrockFurnitureSafety.blocksStoragePickup(true, true, false, false));
    }

    @Test
    public void safetyCanBeDisabledForDiagnosingClientBehaviour() {
        assertFalse(BedrockFurnitureSafety.blocksStoragePickup(false, true, true, false));
    }
}
