package org.voxelhorizons.furniture;

import com.sk89q.worldguard.protection.flags.StateFlag;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Security regression for WorldGuard's testBuild(flag) semantics:
 * custom ALLOW alone must never authorize structural furniture changes
 * where ordinary BUILD is denied.
 *
 * These tests isolate policy decisions; a real WorldGuard/Paper instance
 * must still verify region priorities, event cancellation and bypass.
 */
public class FurnitureProtectionPolicyTest {
    @Test
    public void customAllowDoesNotBypassVanillaBuildDeny() {
        assertFalse(FurnitureProtection.WorldGuardHook.structuralAllowed(false, StateFlag.State.ALLOW));
        assertFalse(FurnitureProtection.WorldGuardHook.structuralAllowed(false, StateFlag.State.DENY));
        assertFalse(FurnitureProtection.WorldGuardHook.structuralAllowed(false, null));
    }

    @Test
    public void dedicatedDenyStillBlocksWhenBuildIsAllowed() {
        assertFalse(FurnitureProtection.WorldGuardHook.structuralAllowed(true, StateFlag.State.DENY));
    }

    @Test
    public void buildPermittedAndCustomAllowOrUnsetPermitStructure() {
        assertTrue(FurnitureProtection.WorldGuardHook.structuralAllowed(true, StateFlag.State.ALLOW));
        assertTrue(FurnitureProtection.WorldGuardHook.structuralAllowed(true, null));
    }

    @Test
    public void inventoryRuleFallsBackToInteractionOnlyWhenUnset() {
        assertSame(StateFlag.State.ALLOW, FurnitureProtection.WorldGuardHook.inventoryFallback(null, StateFlag.State.ALLOW));
        assertSame(StateFlag.State.DENY, FurnitureProtection.WorldGuardHook.inventoryFallback(null, StateFlag.State.DENY));
        assertSame(StateFlag.State.DENY, FurnitureProtection.WorldGuardHook.inventoryFallback(StateFlag.State.DENY, StateFlag.State.ALLOW));
        assertSame(StateFlag.State.ALLOW, FurnitureProtection.WorldGuardHook.inventoryFallback(StateFlag.State.ALLOW, StateFlag.State.DENY));
        assertNull(FurnitureProtection.WorldGuardHook.inventoryFallback(null, null));
    }
}
