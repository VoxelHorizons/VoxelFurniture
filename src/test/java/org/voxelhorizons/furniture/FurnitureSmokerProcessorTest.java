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

    @Test public void smokerBurnsFuelAndRejectsNonFuel() {
        assertEquals(800, FurnitureSmokerProcessor.fuelTicks(new ItemStack(Material.COAL)));
        assertEquals(50, FurnitureSmokerProcessor.fuelTicks(new ItemStack(Material.STICK)));
        assertEquals(0, FurnitureSmokerProcessor.fuelTicks(new ItemStack(Material.STONE)));
        assertEquals(0, FurnitureSmokerProcessor.fuelTicks(null));
    }
}
