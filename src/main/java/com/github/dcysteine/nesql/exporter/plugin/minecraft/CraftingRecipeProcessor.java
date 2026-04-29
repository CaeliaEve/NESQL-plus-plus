package com.github.dcysteine.nesql.exporter.plugin.minecraft;

import codechicken.nei.NEIServerUtils;
import cpw.mods.fml.common.registry.GameRegistry;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.minecraft.MinecraftRecipeTypeHandler;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.exporter.util.ItemUtil;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.lang.reflect.Method;

public class CraftingRecipeProcessor extends PluginHelper {
    private static final String BLOOD_ORB_INTERFACE_NAME =
            "WayofTime.alchemicalWizardry.api.items.interfaces.IBloodOrb";

    private final RecipeType shapedCrafting;
    private final RecipeType shapelessCrafting;
    private Class<?> bloodOrbInterface;
    private Method bloodOrbLevelMethod;
    private ItemStack[] cachedBloodOrbCandidates;

    public CraftingRecipeProcessor(
            PluginExporter exporter, MinecraftRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.shapedCrafting =
                recipeTypeHandler.getRecipeType(
                        MinecraftRecipeTypeHandler.MinecraftRecipeType.SHAPED_CRAFTING);
        this.shapelessCrafting =
                recipeTypeHandler.getRecipeType(
                        MinecraftRecipeTypeHandler.MinecraftRecipeType.SHAPELESS_CRAFTING);
    }

    public void process() {
        int total = CraftingManager.getInstance().getRecipeList().size();
        logger.info("Processing {} crafting recipes...", total);

        @SuppressWarnings("unchecked")
        List<IRecipe> recipes = CraftingManager.getInstance().getRecipeList();
        int count = 0;
        for (IRecipe recipe : recipes) {
            count++;

            if (recipe.getRecipeOutput() == null) {
                logger.warn("Skipping crafting recipe with null output: " + recipe);
                continue;
            }

            if (recipe instanceof ShapedRecipes) {
                processShapedRecipe((ShapedRecipes) recipe);
            } else if (recipe instanceof ShapedOreRecipe) {
                processShapedOreRecipe((ShapedOreRecipe) recipe);
            } else if (recipe instanceof ShapelessRecipes) {
                processShapelessRecipe((ShapelessRecipes) recipe);
            } else if (recipe instanceof ShapelessOreRecipe) {
                processShapelessOreRecipe((ShapelessOreRecipe) recipe);
            } else if (processCustomRecipe(recipe)) {
                // handled by reflective fallback
            } else {
                logger.warn("Unhandled crafting recipe: " + recipe);
            }

            if (Logger.intermittentLog(count)) {
                logger.info("Processed crafting recipe {} of {}", count, total);
                logger.info(
                        "Most recent recipe: {}", recipe.getRecipeOutput().getDisplayName());
            }
        }

        exporterState.flushEntityManager();
        logger.info("Finished processing crafting recipes!");
    }

    private void processShapedRecipe(ShapedRecipes recipe) {
        RecipeBuilder builder = new RecipeBuilder(exporter, shapedCrafting);
        for (Object itemInput : recipe.recipeItems) {
            if (itemInput == null) {
                builder.skipItemInput();
                continue;
            }

            handleItemInput(builder, itemInput);
        }
        builder.addItemOutput(recipe.getRecipeOutput()).build();
    }

    private void processShapedOreRecipe(ShapedOreRecipe recipe) {
        RecipeBuilder builder = new RecipeBuilder(exporter, shapedCrafting);
        for (Object itemInput : recipe.getInput()) {
            if (itemInput == null) {
                builder.skipItemInput();
                continue;
            }

            handleItemInput(builder, itemInput);
        }
        builder.addItemOutput(recipe.getRecipeOutput()).build();
    }

    private void processShapelessRecipe(ShapelessRecipes recipe) {
        // Apparently this actually happens? At least, according to a comment in NEI source.
        if (recipe.recipeItems == null) {
            logger.warn("Crafting recipe with null inputs: " + recipe);
            return;
        }

        RecipeBuilder builder = new RecipeBuilder(exporter, shapelessCrafting);
        for (Object itemInput : recipe.recipeItems) {
            handleItemInput(builder, itemInput);
        }
        builder.addItemOutput(recipe.getRecipeOutput()).build();
    }

    private void processShapelessOreRecipe(ShapelessOreRecipe recipe) {
        RecipeBuilder builder = new RecipeBuilder(exporter, shapelessCrafting);
        for (Object itemInput : recipe.getInput()) {
            handleItemInput(builder, itemInput);
        }
        builder.addItemOutput(recipe.getRecipeOutput()).build();
    }

    private boolean processCustomRecipe(IRecipe recipe) {
        if (recipe == null) {
            return false;
        }

        if (looksLikeShapedRecipe(recipe)) {
            Object[] inputs = extractObjectArray(recipe, "getInput", "getIngredients");
            if (inputs == null) {
                return false;
            }
            RecipeBuilder builder = new RecipeBuilder(exporter, shapedCrafting);
            for (Object itemInput : inputs) {
                if (itemInput == null) {
                    builder.skipItemInput();
                    continue;
                }
                handleItemInput(builder, itemInput);
            }
            builder.addItemOutput(recipe.getRecipeOutput()).build();
            return true;
        }

        if (looksLikeShapelessRecipe(recipe)) {
            List<Object> inputs = extractObjectList(recipe, "getInput", "getIngredients");
            if (inputs == null) {
                Object[] arrayInputs = extractObjectArray(recipe, "getInput", "getIngredients");
                if (arrayInputs != null) {
                    inputs = Arrays.asList(arrayInputs);
                }
            }
            if (inputs == null) {
                return false;
            }
            RecipeBuilder builder = new RecipeBuilder(exporter, shapelessCrafting);
            for (Object itemInput : inputs) {
                handleItemInput(builder, itemInput);
            }
            builder.addItemOutput(recipe.getRecipeOutput()).build();
            return true;
        }

        return false;
    }

    private void handleItemInput(RecipeBuilder builder, Object itemInput) {
        if (itemInput instanceof Integer) {
            ItemStack[] candidates = resolveBloodOrbCandidates((Integer) itemInput);
            if (candidates.length == 0) {
                builder.skipItemInput();
                return;
            }
            builder.addItemGroupInput(candidates);
            return;
        }

        if (itemInput instanceof Collection) {
            ItemStack[] normalizedCollection = normalizeCollectionInput((Collection<?>) itemInput);
            if (normalizedCollection.length == 0) {
                builder.skipItemInput();
                return;
            }
            builder.addItemGroupInput(normalizedCollection);
            return;
        }

        ItemStack[] itemStacks = extractNormalizedRecipeItems(itemInput);
        if (itemStacks == null || itemStacks.length == 0) {
            builder.skipItemInput();
            return;
        }

        // For some reason, a bunch of crafting recipes have stack size > 1, even though crafting
        // recipes only ever consume one item from each slot. This is probably a bug in the recipes.
        // We'll fix this by manually setting stack sizes to 1.
        ItemStack[] fixedItemStacks = new ItemStack[itemStacks.length];
        boolean foundBadStackSize = false;
        for (int i = 0; i < itemStacks.length; i++) {
            ItemStack itemStack = itemStacks[i];

            if (itemStack.stackSize != 1) {
                foundBadStackSize = true;
                fixedItemStacks[i] = itemStack.copy();
                fixedItemStacks[i].stackSize = 1;
            } else {
                fixedItemStacks[i] = itemStack;
            }
        }

        if (foundBadStackSize) {
            logger.warn("Crafting recipe with bad stack size: " + Arrays.toString(itemStacks));
        }

        builder.addItemGroupInput(fixedItemStacks);
    }

    private ItemStack[] normalizeCollectionInput(Collection<?> itemInputs) {
        List<ItemStack> normalized = new ArrayList<>();
        for (Object candidate : itemInputs) {
            ItemStack[] extracted = extractNormalizedRecipeItems(candidate);
            if (extracted == null || extracted.length == 0) {
                continue;
            }
            normalized.addAll(Arrays.asList(extracted));
        }
        return normalized.toArray(new ItemStack[0]);
    }

    private ItemStack[] extractNormalizedRecipeItems(Object itemInput) {
        if (itemInput == null) {
            return new ItemStack[0];
        }

        try {
            ItemStack[] extracted = NEIServerUtils.extractRecipeItems(itemInput);
            if (extracted != null && extracted.length > 0) {
                return extracted;
            }
        } catch (ClassCastException ignored) {
            // Fall through to custom normalization for mods that expose Block / definition objects.
        }

        ItemStack[] normalizedDirect = normalizeDirectInput(itemInput);
        if (normalizedDirect.length > 0) {
            return normalizedDirect;
        }

        logger.warn(
                "Unsupported custom crafting input type {} -> {}",
                itemInput.getClass().getName(),
                itemInput);
        return new ItemStack[0];
    }

    private ItemStack[] normalizeDirectInput(Object itemInput) {
        if (itemInput instanceof ItemStack) {
            return new ItemStack[] { ((ItemStack) itemInput).copy() };
        }

        if (itemInput instanceof ItemStack[]) {
            ItemStack[] stacks = (ItemStack[]) itemInput;
            ItemStack[] copies = new ItemStack[stacks.length];
            for (int i = 0; i < stacks.length; i++) {
                copies[i] = stacks[i] == null ? null : stacks[i].copy();
            }
            return filterNullStacks(copies);
        }

        if (itemInput instanceof Item) {
            return new ItemStack[] { new ItemStack((Item) itemInput, 1) };
        }

        if (itemInput instanceof Block) {
            Optional<ItemStack> stack = ItemUtil.getItemStack((Block) itemInput);
            return stack.isPresent() ? new ItemStack[] { stack.get() } : new ItemStack[0];
        }

        if (itemInput instanceof String) {
            ItemStack resolved = resolveRegistryStringInput((String) itemInput);
            return resolved == null ? new ItemStack[0] : new ItemStack[] { resolved };
        }

        ItemStack reflected = resolveDefinitionLikeStack(itemInput);
        return reflected == null ? new ItemStack[0] : new ItemStack[] { reflected };
    }

    private ItemStack[] filterNullStacks(ItemStack[] stacks) {
        List<ItemStack> filtered = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack != null) {
                filtered.add(stack);
            }
        }
        return filtered.toArray(new ItemStack[0]);
    }

    private ItemStack resolveRegistryStringInput(String descriptor) {
        if (descriptor == null) {
            return null;
        }

        String normalized = descriptor.trim();
        if (normalized.isEmpty() || "_".equals(normalized)) {
            return null;
        }

        String[] parts = normalized.split(":");
        if (parts.length < 2) {
            return null;
        }

        String modId = parts[0];
        String name = parts[1];
        int damage = 0;
        if (parts.length >= 3) {
            try {
                damage = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {
                damage = 0;
            }
        }

        Item item = GameRegistry.findItem(modId, name);
        if (item != null) {
            return new ItemStack(item, 1, damage);
        }

        Block block = GameRegistry.findBlock(modId, name);
        if (block != null) {
            return new ItemStack(block, 1, damage);
        }

        return null;
    }

    private ItemStack resolveDefinitionLikeStack(Object itemInput) {
        ItemStack viaMaybeStack = invokeItemStackFactory(itemInput, "maybeStack");
        if (viaMaybeStack != null) {
            return viaMaybeStack;
        }

        ItemStack viaStack = invokeItemStackFactory(itemInput, "stack");
        if (viaStack != null) {
            return viaStack;
        }

        Block viaMaybeBlock = invokeOptionalBlockFactory(itemInput, "maybeBlock");
        if (viaMaybeBlock != null) {
            return ItemUtil.getItemStack(viaMaybeBlock).orElse(null);
        }

        return null;
    }

    private ItemStack invokeItemStackFactory(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName, int.class);
            Object result = method.invoke(target, 1);
            if (result instanceof ItemStack) {
                return ((ItemStack) result).copy();
            }
            if (result instanceof Optional) {
                Object value = ((Optional<?>) result).orElse(null);
                if (value instanceof ItemStack) {
                    return ((ItemStack) value).copy();
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private Block invokeOptionalBlockFactory(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object result = method.invoke(target);
            if (result instanceof Optional) {
                Object value = ((Optional<?>) result).orElse(null);
                if (value instanceof Block) {
                    return (Block) value;
                }
            }
            if (result instanceof Block) {
                return (Block) result;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private boolean looksLikeShapedRecipe(IRecipe recipe) {
        String simpleName = recipe.getClass().getSimpleName();
        return simpleName.contains("Shaped")
                || hasMethod(recipe.getClass(), "getWidth")
                || hasField(recipe.getClass(), "width");
    }

    private boolean looksLikeShapelessRecipe(IRecipe recipe) {
        String simpleName = recipe.getClass().getSimpleName();
        return simpleName.contains("Shapeless");
    }

    private Object[] extractObjectArray(IRecipe recipe, String... methodNames) {
        for (String methodName : methodNames) {
            Object value = invokeNoArg(recipe, methodName);
            if (value instanceof Object[]) {
                return (Object[]) value;
            }
        }
        return null;
    }

    private List<Object> extractObjectList(IRecipe recipe, String... methodNames) {
        for (String methodName : methodNames) {
            Object value = invokeNoArg(recipe, methodName);
            if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) value;
                return list;
            }
            if (value instanceof Collection) {
                return new ArrayList<>((Collection<?>) value);
            }
        }
        return null;
    }

    private Object invokeNoArg(IRecipe recipe, String methodName) {
        try {
            Method method = recipe.getClass().getMethod(methodName);
            return method.invoke(recipe);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private boolean hasMethod(Class<?> type, String methodName) {
        try {
            type.getMethod(methodName);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private boolean hasField(Class<?> type, String fieldName) {
        try {
            type.getField(fieldName);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private ItemStack[] resolveBloodOrbCandidates(int minimumLevel) {
        if (!ensureBloodOrbReflection()) {
            return new ItemStack[0];
        }

        if (cachedBloodOrbCandidates == null) {
            List<ItemStack> candidates = new ArrayList<>();
            for (Object registryEntry : net.minecraft.item.Item.itemRegistry) {
                if (!(registryEntry instanceof net.minecraft.item.Item)) {
                    continue;
                }
                net.minecraft.item.Item item = (net.minecraft.item.Item) registryEntry;
                Integer orbLevel = readBloodOrbLevel(item);
                if (orbLevel == null) {
                    continue;
                }
                candidates.add(new ItemStack(item));
            }
            cachedBloodOrbCandidates = candidates.toArray(new ItemStack[0]);
        }

        List<ItemStack> eligible = new ArrayList<>();
        for (ItemStack candidate : cachedBloodOrbCandidates) {
            Integer orbLevel = readBloodOrbLevel(candidate.getItem());
            if (orbLevel != null && orbLevel >= minimumLevel) {
                eligible.add(candidate.copy());
            }
        }
        return eligible.toArray(new ItemStack[0]);
    }

    private boolean ensureBloodOrbReflection() {
        if (bloodOrbInterface != null && bloodOrbLevelMethod != null) {
            return true;
        }

        try {
            bloodOrbInterface = Class.forName(BLOOD_ORB_INTERFACE_NAME);
            bloodOrbLevelMethod = bloodOrbInterface.getMethod("getOrbLevel");
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private Integer readBloodOrbLevel(net.minecraft.item.Item item) {
        if (item == null || !ensureBloodOrbReflection() || !bloodOrbInterface.isInstance(item)) {
            return null;
        }

        try {
            Object level = bloodOrbLevelMethod.invoke(item);
            return level instanceof Number ? ((Number) level).intValue() : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
