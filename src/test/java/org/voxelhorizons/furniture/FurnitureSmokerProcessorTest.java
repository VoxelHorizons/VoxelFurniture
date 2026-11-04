package org.voxelhorizons.furniture;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import org.voxelhorizons.furniture.inventory.FurnitureSmokerProcessor;

import static org.junit.Assert.*;

public class FurnitureSmokerProcessorTest {
    @Test public void acceptsCookedResultOnlyWhenItFits() {
        assertTrue(FurnitureSmokerProcessor.canAccept(null, new ItemStack(Material.COOKED_BEEF)));
        assertFalse(FurnitureSmokerProcessor.canAccept(null, null));
        // ItemStack#isSimilar uses Bukkit's ItemFactory; non-null comparisons are
        // exercised on the live server rather than in serverless unit tests.
    }

    @Test public void burnerTurnsOffWhenItsLastFuelTickExpiresWithoutItemChanges() throws Exception {
        FurnitureSmokerProcessor processor = new FurnitureSmokerProcessor();
        java.lang.reflect.Field burn = FurnitureSmokerProcessor.class.getDeclaredField("burnTicks");
        burn.setAccessible(true);
        burn.setInt(processor, 2);

        org.bukkit.inventory.Inventory empty = (org.bukkit.inventory.Inventory)
                java.lang.reflect.Proxy.newProxyInstance(
                        org.bukkit.inventory.Inventory.class.getClassLoader(),
                        new Class<?>[] {org.bukkit.inventory.Inventory.class},
                        (proxy, method, arguments) -> {
                            if ("getSize".equals(method.getName())) return 3;
                            if ("getItem".equals(method.getName())) return null;
                            return null;
                        });

        assertTrue(processor.isBurning());
        assertFalse(processor.tick(empty));
        assertTrue(processor.isBurning());
        assertFalse("Exhausting fuel need not change inventory contents", processor.tick(empty));
        assertFalse(processor.isBurning());
    }

    @Test public void smokerBurnsFuelAndRejectsNonFuel() {
        assertEquals(800, FurnitureSmokerProcessor.fuelTicks(new ItemStack(Material.COAL)));
        assertEquals(50, FurnitureSmokerProcessor.fuelTicks(new ItemStack(Material.STICK)));
        assertEquals(0, FurnitureSmokerProcessor.fuelTicks(new ItemStack(Material.STONE)));
        assertEquals(0, FurnitureSmokerProcessor.fuelTicks(null));
    }
}
