package org.voxelhorizons.furniture.inventory;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Server-side processing for virtual SMOKER inventories. A custom Bukkit inventory is only a
 * container; it has no vanilla block entity to perform recipes or consume fuel on its own.
 * State is per furniture instance, and the owning manager persists inventory changes.
 */
public final class FurnitureSmokerProcessor {
    private int burnTicks;
    private int cookingTicks;
    private ItemStack previousInput;
    private Recipe cachedRecipe;

    /** Call once per server tick. Returns true only when the inventory contents changed. */
    public boolean tick(Inventory inventory) {
        if (inventory == null || inventory.getSize() != 3) return false;
        if (burnTicks > 0) burnTicks--;

        ItemStack input = inventory.getItem(0);
        if (empty(input)) {
            cookingTicks = 0;
            previousInput = null;
            cachedRecipe = null;
            return false;
        }

        if (previousInput == null || !previousInput.isSimilar(input)) {
            previousInput = input.clone();
            cachedRecipe = smokingRecipe(input);
            cookingTicks = 0;
        }

        Recipe recipe = cachedRecipe;
        if (recipe == null || !canAccept(inventory.getItem(2), recipe.getResult())) {
            cookingTicks = 0;
            return false;
        }

        boolean changed = false;
        if (burnTicks <= 0) {
            ItemStack fuel = inventory.getItem(1);
            int ticks = fuelTicks(fuel);
            if (ticks <= 0) return false;
            burnTicks = ticks;
            if (fuel.getAmount() == 1) {
                inventory.setItem(1, "LAVA_BUCKET".equals(fuel.getType().name())
                        ? new ItemStack(Material.valueOf("BUCKET")) : null);
            } else {
                ItemStack reduced = fuel.clone();
                reduced.setAmount(reduced.getAmount() - 1);
                inventory.setItem(1, reduced);
            }
            changed = true;
        }

        cookingTicks++;
        if (cookingTicks >= cookingTime(recipe)) {
            ItemStack product = recipe.getResult().clone();
            ItemStack oldOutput = inventory.getItem(2);
            if (!empty(oldOutput)) {
                product.setAmount(oldOutput.getAmount() + product.getAmount());
            }
            inventory.setItem(2, product);
            if (input.getAmount() == 1) inventory.setItem(0, null);
            else {
                ItemStack reduced = input.clone();
                reduced.setAmount(reduced.getAmount() - 1);
                inventory.setItem(0, reduced);
            }
            cookingTicks = 0;
            changed = true;
        }
        return changed;
    }

    /** A result must either fit an empty result slot or stack with the existing result. */
    public static boolean canAccept(ItemStack current, ItemStack product) {
        if (empty(product)) return false;
        return empty(current) || current.isSimilar(product)
                && current.getAmount() + product.getAmount()
                   <= Math.min(current.getMaxStackSize(), product.getMaxStackSize());
    }

    public static int fuelTicks(ItemStack item) {
        if (empty(item)) return 0;
        String id = item.getType().name();
        if ("LAVA_BUCKET".equals(id)) return 10000;
        if ("COAL_BLOCK".equals(id)) return 8000;
        if ("DRIED_KELP_BLOCK".equals(id)) return 2000;
        if ("BLAZE_ROD".equals(id)) return 1200;
        if ("COAL".equals(id) || "CHARCOAL".equals(id)) return 800;
        if ("STICK".equals(id)) return 50;
        if ("BAMBOO".equals(id)) return 25;
        if ("BAMBOO_BLOCK".equals(id)) return 150;
        if ("BOWL".equals(id) || "SCAFFOLDING".equals(id)) return 100;
        if (id.endsWith("_LOG") || id.endsWith("_WOOD") || id.endsWith("_STEM")
                || id.endsWith("_HYPHAE") || id.endsWith("_PLANKS")
                || id.endsWith("_FENCE") || id.endsWith("_FENCE_GATE")
                || id.endsWith("_SLAB") || id.endsWith("_STAIRS") || id.endsWith("_TRAPDOOR")
                || id.endsWith("_DOOR") || id.endsWith("_SIGN")) return 150;
        return 0;
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    private static Recipe smokingRecipe(ItemStack input) {
        // Bukkit.getRecipesFor(input) looks for recipes producing input, NOT
        // recipes that can cook it. Iterate the registered recipes instead.
        java.util.Iterator<Recipe> recipes = Bukkit.recipeIterator();
        while (recipes.hasNext()) {
            Recipe candidate = recipes.next();
            if (isSmokingRecipe(candidate) && matches(candidate, input)) return candidate;
        }
        return null;
    }

    private static boolean isSmokingRecipe(Recipe recipe) {
        try {
            return Class.forName("org.bukkit.inventory.SmokingRecipe").isInstance(recipe);
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static boolean matches(Recipe recipe, ItemStack input) {
        try {
            Object choice = recipe.getClass().getMethod("getInputChoice").invoke(recipe);
            return choice != null && Boolean.TRUE.equals(
                    choice.getClass().getMethod("test", ItemStack.class).invoke(choice, input));
        } catch (NoSuchMethodException exception) {
            try {
                ItemStack ingredient = (ItemStack) recipe.getClass().getMethod("getInput").invoke(recipe);
                return ingredient != null && ingredient.getType() == input.getType();
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return false;
        }
    }

    private static int cookingTime(Recipe recipe) {
        try {
            Object value = recipe.getClass().getMethod("getCookingTime").invoke(recipe);
            if (value instanceof Number) return Math.max(1, ((Number) value).intValue());
        } catch (ReflectiveOperationException ignored) {
            // Minecraft smoker recipes conventionally require 100 ticks.
        }
        return 100;
    }
}
