package org.voxelhorizons.furniture;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;
import org.voxelhorizons.furniture.inventory.FurnitureSmokerProcessor;

import static org.junit.Assert.*;

public class FurnitureSmokerProcessorTest {
    @Test public void acceptsCookedResultOnlyWhenItFits() {
        assertTrue(FurnitureSmokerProcessor.canAccept(null, new ItemStack(Material.COOKED_BEEF)));
        assertTrue(FurnitureSmokerProcessor.canAccept(
                new ItemStack(Material.COOKED_BEEF, 63), new ItemStack(Material.COOKED_BEEF)));
        assertFalse(FurnitureSmokerProcessor.canAccept(
                new ItemStack(Material.COOKED_BEEF, 64), new ItemStack(Material.COOKED_BEEF)));
        assertFalse(FurnitureSmokerProcessor.canAccept(
                new ItemStack(Material.STONE), new ItemStack(Material.COOKED_BEEF)));
    }

    @Test public void smokerBurnsFuelAndRejectsNonFuel() {
        assertEquals(800, FurnitureSmokerProcessor.fuelTicks(new ItemStack(Material.COAL)));
        assertEquals(50, FurnitureSmokerProcessor.fuelTicks(new ItemStack(Material.STICK)));
        assertEquals(0, FurnitureSmokerProcessor.fuelTicks(new ItemStack(Material.STONE)));
        assertEquals(0, FurnitureSmokerProcessor.fuelTicks(null));
    }
}
