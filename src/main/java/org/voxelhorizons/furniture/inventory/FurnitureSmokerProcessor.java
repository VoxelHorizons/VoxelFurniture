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
    /** Indirection permits testing actual cooking without a running Bukkit server. */
    public interface RecipeFinder {
        Recipe find(ItemStack input);
    }

    private final RecipeFinder recipeFinder;
    private int burnTicks;
    private int fuelDuration;
    private int cookingTicks;
    private int cookingDuration = 100;
    private ItemStack previousInput;
    private Recipe cachedRecipe;
    private int missingRecipeCooldown;

    public FurnitureSmokerProcessor() {
        this(new RecipeFinder() {
            @Override public Recipe find(ItemStack input) {
                return smokingRecipe(input);
            }
        });
    }

    public FurnitureSmokerProcessor(RecipeFinder finder) {
        if (finder == null) throw new IllegalArgumentException("Recipe finder cannot be null");
        this.recipeFinder = finder;
    }

    /**
     * Match the vanilla lit-state semantics: fuel currently burning, even if
     * there is no cookable ingredient or the output slot has become blocked.
     */
    public boolean isBurning() { return burnTicks > 0; }
    public int remainingBurnTicks() { return burnTicks; }
    public int fuelDuration() { return Math.max(1, fuelDuration); }
    public int cookingTicks() { return cookingTicks; }
    public int cookingDuration() { return Math.max(1, cookingDuration); }

    /** Call once per server tick. Returns true only when the inventory contents changed. */
    public boolean tick(Inventory inventory) {
        if (inventory == null || inventory.getSize() != 3) return false;
        if (burnTicks > 0) burnTicks--;

        ItemStack input = inventory.getItem(0);
        if (empty(input)) {
            cookingTicks = 0;
            previousInput = null;
            cachedRecipe = null;
            missingRecipeCooldown = 0;
            return false;
        }

        // Re-evaluate whenever the ingredient changes. Retry missing recipes
        // once per second to pick up data-pack registrations, not every tick.
        if (previousInput == null || !previousInput.isSimilar(input)) {
            previousInput = input.clone();
            cachedRecipe = null;
            missingRecipeCooldown = 0;
            cookingTicks = 0;
        }
        if (cachedRecipe == null && missingRecipeCooldown-- <= 0) {
            cachedRecipe = recipeFinder.find(input);
            missingRecipeCooldown = 20;
        }

        Recipe recipe = cachedRecipe;
        cookingDuration = recipe == null ? 100 : cookingTime(recipe);
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
            fuelDuration = ticks;
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
        if (cookingTicks >= cookingDuration) {
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

    /**
     * Match ingredients through the public Bukkit RecipeChoice API, not the
     * implementation class (which may be inaccessible on a running server).
     * This plugin compiles against 1.12, so both APIs are accessed reflectively.
     */
    static boolean matches(Recipe recipe, ItemStack input) {
        try {
            Object choice = recipe.getClass().getMethod("getInputChoice").invoke(recipe);
            if (choice != null) {
                Class<?> choiceApi = Class.forName("org.bukkit.inventory.RecipeChoice");
                Object match = choiceApi.getMethod("test", ItemStack.class).invoke(choice, input);
                return Boolean.TRUE.equals(match);
            }
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // RecipeChoice did not exist in 1.12. Fall back to ingredient type.
        } catch (IllegalAccessException | InvocationTargetException exception) {
            // Do not silently fail every recipe on servers using implementation
            // wrappers that restrict reflective access. Try getInput instead.
        }
        try {
            ItemStack ingredient = (ItemStack) recipe.getClass().getMethod("getInput").invoke(recipe);
            return ingredient != null && ingredient.getType() == input.getType();
        } catch (ReflectiveOperationException ignored) {
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
