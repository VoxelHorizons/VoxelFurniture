package org.voxelhorizons.furniture;

import org.bukkit.Location;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureRendererType;
import org.voxelhorizons.furniture.render.FurnitureRenderer;
import org.voxelhorizons.furniture.render.FurnitureRendererUpdate;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/** Regression tests for click routing and entity-identity-preserving model changes. */
public class FurnitureClickAnimationTest {

    @Test public void mainHandClicksAreProcessedButOffHandCannotToggleAgain() {
        assertTrue(FurnitureListener.mainHand(EquipmentSlot.HAND));
        assertTrue(FurnitureListener.mainHand(null));
        assertFalse(FurnitureListener.mainHand(EquipmentSlot.OFF_HAND));
    }

    @Test public void explicitItemAllowDoesNotDiscardNormalBlockClicks() {
        assertFalse(FurnitureListener.skipForExplicitBlockPlacement(true, false));
        assertFalse(FurnitureListener.skipForExplicitBlockPlacement(false, true));
        assertTrue(FurnitureListener.skipForExplicitBlockPlacement(true, true));
    }

    @Test public void blockAndHitboxClicksFromSamePlayerOnlyToggleOncePerTick() {
        FurnitureInteractionGuard guard = new FurnitureInteractionGuard();
        UUID player = UUID.randomUUID();
        UUID furniture = UUID.randomUUID();
        assertTrue(guard.accept(player, furniture));
        assertFalse("hitbox event must not undo the block click", guard.accept(player, furniture));
        assertTrue("other furniture can still be clicked", guard.accept(player, UUID.randomUUID()));
        assertTrue("other players can still interact", guard.accept(UUID.randomUUID(), furniture));
        guard.reset();
        assertTrue("next server tick must permit another click", guard.accept(player, furniture));
    }

    @Test public void animationModelChangesKeepRendererAndHitboxEntityIds() {
        StubRenderer renderer = new StubRenderer(true);
        List<UUID> existing = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());
        FurnitureRendererUpdate.Result changed = FurnitureRendererUpdate.apply(
                renderer, existing, true, null, 0.0F, null, null);

        assertTrue(changed.reused());
        assertSame("Model swap must not allocate a new entity list", existing, changed.entities());
        assertEquals(1, renderer.updateCalls);
        assertEquals(0, renderer.spawnCalls);
        assertEquals(0, renderer.removeCalls);
    }

    @Test public void incompatibleStructureFallsBackToRespawnOnlyWhenRequired() {
        StubRenderer renderer = new StubRenderer(true);
        List<UUID> existing = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());
        FurnitureRendererUpdate.Result changed = FurnitureRendererUpdate.apply(
                renderer, existing, false, null, 0.0F, null, null);

        assertFalse(changed.reused());
        assertNotSame(existing, changed.entities());
        assertEquals(0, renderer.updateCalls);
        assertEquals(1, renderer.spawnCalls);
    }

    @Test public void UnsupportedInPlaceRendererFallsBackSafely() {
        StubRenderer renderer = new StubRenderer(false);
        FurnitureRendererUpdate.Result changed = FurnitureRendererUpdate.apply(
                renderer, Arrays.asList(UUID.randomUUID()), true, null, 0.0F, null, null);
        assertFalse(changed.reused());
        assertEquals(1, renderer.updateCalls);
        assertEquals(1, renderer.spawnCalls);
    }

    private static final class StubRenderer implements FurnitureRenderer {
        private final boolean canUpdate;
        private int spawnCalls;
        private int updateCalls;
        private int removeCalls;

        private StubRenderer(boolean canUpdate) { this.canUpdate = canUpdate; }
        @Override public FurnitureRendererType type() { return FurnitureRendererType.DISPLAY; }
        @Override public boolean supported() { return true; }
        @Override public List<UUID> spawn(Location location, float yaw, ItemStack item, FurnitureDefinition visual) {
            spawnCalls++;
            return Arrays.asList(UUID.randomUUID(), UUID.randomUUID());
        }
        @Override public boolean update(List<UUID> entities, Location location, float yaw,
                                         ItemStack item, FurnitureDefinition visual) {
            updateCalls++;
            return canUpdate;
        }
        @Override public void remove(List<UUID> entities) { removeCalls++; }
    }
}
