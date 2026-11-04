package org.voxelhorizons.furniture.inventory;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Actual fuel/input/output regression test; previously only fuel values were tested. */
public class FurnitureSmokerCookingTest {
    @Test public void rawFoodAndCoalConsumeFuelAndProduceCookedOutput() {
        final ItemStack[] slots = new ItemStack[] {
                new ItemStack(Material.valueOf("RAW_BEEF"), 2),
                new ItemStack(Material.COAL, 2),
                null
        };
        final Inventory inventory = fakeInventory(slots);
        FurnitureSmokerProcessor processor = new FurnitureSmokerProcessor(
                input -> new TestRecipe(new ItemStack(Material.COOKED_BEEF), 1));

        assertFalse(processor.isBurning());
        assertTrue("Smoker must complete one-tick cooking recipe", processor.tick(inventory));
        assertTrue("Coal must ignite the furnace", processor.isBurning());
        assertEquals(800, processor.fuelDuration());
        assertEquals(800, processor.remainingBurnTicks());
        assertEquals(1, slots[0].getAmount());
        assertEquals(1, slots[1].getAmount());
        assertNotNull("Smoker must produce a real output item", slots[2]);
        assertEquals(Material.COOKED_BEEF, slots[2].getType());
        assertEquals(1, slots[2].getAmount());
    }

    @Test public void unsupportedInputNeverConsumesFuel() {
        final ItemStack[] slots = new ItemStack[] {
                new ItemStack(Material.STONE, 1),
                new ItemStack(Material.COAL, 3),
                null
        };
        final AtomicInteger queries = new AtomicInteger();
        FurnitureSmokerProcessor processor = new FurnitureSmokerProcessor(input -> {
            queries.incrementAndGet();
            return null;
        });

        assertFalse(processor.tick(fakeInventory(slots)));
        assertEquals(1, queries.get());
        assertEquals(3, slots[1].getAmount());
        assertFalse(processor.isBurning());
    }

    @Test public void legacyRecipeIngredientMatchingUsesDeclaredInput() {
        ItemStack raw = new ItemStack(Material.valueOf("RAW_BEEF"));
        Recipe cooking = new TestRecipe(new ItemStack(Material.COOKED_BEEF), 1);
        assertTrue(FurnitureSmokerProcessor.matches(cooking, raw));
        assertFalse(FurnitureSmokerProcessor.matches(cooking, new ItemStack(Material.STONE)));
    }

    private static Inventory fakeInventory(final ItemStack[] slots) {
        return (Inventory) Proxy.newProxyInstance(
                Inventory.class.getClassLoader(), new Class<?>[]{Inventory.class},
                (proxy, method, args) -> {
                    if ("getSize".equals(method.getName())) return slots.length;
                    if ("getItem".equals(method.getName())) return slots[(Integer) args[0]];
                    if ("setItem".equals(method.getName())) {
                        slots[(Integer) args[0]] = (ItemStack) args[1];
                        return null;
                    }
                    return null;
                });
    }

    public static final class TestRecipe implements Recipe {
        private final ItemStack product;
        private final int duration;

        public TestRecipe(ItemStack product, int duration) {
            this.product = product;
            this.duration = duration;
        }

        @Override public ItemStack getResult() { return product; }
        public int getCookingTime() { return duration; }
        public ItemStack getInput() { return new ItemStack(Material.valueOf("RAW_BEEF")); }
    }
}
