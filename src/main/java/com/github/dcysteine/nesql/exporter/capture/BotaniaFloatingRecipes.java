package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.ItemList;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native floating conversion for a String-or-absent type on the physically last special flower. */
final class BotaniaFloatingRecipes implements RegistryRecipes {
    private static final String HANDLER = "vazkii.botania.client.integration.nei.recipe.RecipeHandlerFloatingFlowers";
    private static final String RECIPE = "vazkii.botania.common.crafting.recipe.SpecialFloatingFlowerRecipe";
    private static final String BLOCKS = "vazkii.botania.common.block.ModBlocks";
    private static final String[] KEYS = {"floatingFlower", "specialFlower", "floatingSpecialFlower"};
    private final TemplateRecipeHandler handler;
    private final List<?> registry;
    private final List<ItemStack> catalog;
    private final List<?> registered;
    private final List<IRecipe> recipes = new ArrayList<>();
    private final Block[] blocks = new Block[3];
    private final Item[] items = new Item[3];
    private final ItemStack floating, special, output;
    private final String signature;
    private boolean live;

    static boolean supports(ICraftingHandler handler) { return handler.getClass().getName().equals(HANDLER); }
    BotaniaFloatingRecipes(TemplateRecipeHandler handler) {
        this(handler, registry(), ItemList.items); live = true;
    }
    private static List<?> registry() {
        version("Botania", "1.12.28-GTNH"); return CraftingManager.getInstance().getRecipeList();
    }
    BotaniaFloatingRecipes(TemplateRecipeHandler handler, List<?> registry, List<ItemStack> catalog) {
        if (!supports(handler) || registry == null || catalog == null || registry.size() > 262144 || catalog.size() > 262144)
            throw fault("Invalid floating-flower registry or item catalog");
        this.handler = handler; this.registry = registry; this.catalog = catalog;
        this.registered = new ArrayList<>(registry);
        Class<?> recipeType = type(RECIPE);
        for (Object source : registry) {
            Jobs.checkpoint();
            if (!(source instanceof IRecipe)) throw fault("Invalid ordinary crafting registry entry");
            if (!recipeType.isInstance(source)) continue;
            if (source.getClass() != recipeType) throw fault("Unadapted floating-flower recipe override");
            recipes.add((IRecipe) source);
            if (recipes.size() > 4096) throw fault("Floating-flower branches exceed their budget");
        }
        if (recipes.isEmpty()) throw fault("The native floating-flower recipe is not registered");
        for (int i = 0; i < 3; i++) {
            blocks[i] = (Block) field(type(BLOCKS), null, KEYS[i]);
            if (blocks[i] == null || (items[i] = Item.getItemFromBlock(blocks[i])) == null) throw fault("Missing native floating-flower item");
        }
        if (items[0] == items[1] || items[0] == items[2] || items[1] == items[2]) throw fault("Native floating-flower item roles are not distinct");
        ItemStack[] examples = examples(); floating = unit(examples[0]); special = unit(examples[1]);
        noContainers(); output = result(recipes.get(0), floating, special);
        if (output.getItem() != items[2] || output.stackSize != 1 || Items.feather.getDamage(output) != 0
                || !output.hasTagCompound() || output.getTagCompound().func_150296_c().size() != 1 || !output.getTagCompound().hasKey("type", 8))
            throw fault("Native floating-flower output changed");
        signature = signature(examples);
    }
    public int size() { return recipes.size() * 36; }
    public void verify() {
        if (registry.size() != registered.size() || live && (CraftingManager.getInstance().getRecipeList() != registry || ItemList.items != catalog)) changed();
        for (int i = 0; i < registered.size(); i++) { Jobs.checkpoint(); if (registry.get(i) != registered.get(i)) changed(); }
        for (int i = 0; i < 3; i++) if (field(type(BLOCKS), null, KEYS[i]) != blocks[i] || Item.getItemFromBlock(blocks[i]) != items[i]) changed();
        try {
            noContainers();
            if (!signature.equals(signature(examples()))) changed();
        } catch (Jobs.Fault error) { if (error.code.equals("recipe_changed")) throw error; changed(); }
    }
    public boolean capture(int index, RecipeRow row) {
        if (index < 0 || index >= size()) throw fault("Floating-flower branch is outside the registry");
        verify();
        int branch = index % 36, total = 2;
        while (branch >= total - 1) { branch -= total - 1; total++; }
        int count = branch + 1, firstSpecial = total - count;
        JsonArray specialSlots = new JsonArray();
        PositionedStack floatingDisplay = new PositionedStack(floating.copy(), 25, 6, false);
        PositionedStack specialDisplay = new PositionedStack(special.copy(), 43, 6, false);
        for (int slot = 0; slot < total; slot++) {
            ItemStack example = slot < firstSpecial ? floating : special;
            JsonObject rule = slot == total - 1 ? object("kind", "string_tag", "key", "type") : object("kind", "wildcard", "meta", true, "nbt", true);
            PositionedStack display = slot == 0 ? floatingDisplay : slot == total - 1 ? specialDisplay : null;
            row.itemInput(display, slot, Collections.singletonList(new RecipeRow.Ingredient(example.copy(), 1, false, rule)), false);
            if (slot >= firstSpecial) specialSlots.add(value(slot));
        }
        row.record.add("process", object("kind", "floatingFlowers", "special", specialSlots));
        ItemStack observed = result(recipes.get(index / 36), floating, special);
        if (!stack(output).equals(stack(observed))) changed();
        PositionedStack resultDisplay = new PositionedStack(observed.copy(), 119, 24, false);
        row.itemOutput(resultDisplay, 0, observed, 10000);
        String outputId = row.outputs.get(0).getAsJsonObject().get("id").getAsString();
        row.outputs.get(0).getAsJsonObject().add("change", object("input", total - 1,
                "action", object("kind", "floatingFlower", "base", outputId), "samples", array(object("id", outputId, "amount", "1"))));
        row.property("botania:floatingScope", "Supported native input states", "Two to nine occupied crafting cells containing both native flower item types; the physically last special flower has a String or absent type tag. Earlier special flowers may carry arbitrary NBT. A non-String type on the last special flower remains unadapted because native NBT stringification depends on tag type and compound storage order. Display stacks are examples, not NBT restrictions.");
        row.property("botania:craftingBoundary", "Native crafting boundary", "SpecialFloatingFlowerRecipe with ordinary SlotCrafting default unit consumption and no container items; this recipe definition does not simulate global CraftingManager priority or player crafting event callbacks. Special inputs bind in ascending physical slot order. Output uses only the selected type string in fresh NBT, count one, metadata zero.");
        Object cache = TinkerRecipes.construct(type(HANDLER + "$CachedFloatingFlowerRecipe"),
                new Class<?>[]{handler.getClass(), ItemStack.class, ItemStack.class, ItemStack.class}, handler, floating.copy(), special.copy(), observed.copy());
        handler.arecipes.clear(); handler.arecipes.add((TemplateRecipeHandler.CachedRecipe) cache);
        return true;
    }
    private ItemStack[] examples() {
        if (catalog.size() > 262144) throw fault("Floating-flower catalog exceeds its budget");
        ItemStack[] found = new ItemStack[2];
        for (ItemStack candidate : catalog) {
            Jobs.checkpoint();
            if (candidate == null || candidate.getItem() == null || candidate.stackSize < 1 || Items.feather.getDamage(candidate) == 32767) continue;
            if (found[0] == null && candidate.getItem() == items[0]) found[0] = candidate;
            if (found[1] == null && candidate.getItem() == items[1] && candidate.hasTagCompound() && candidate.getTagCompound().hasKey("type", 8)) found[1] = candidate;
            if (found[0] != null && found[1] != null) return found;
        }
        throw fault("Floating flowers need native concrete catalog examples with a String type");
    }
    private void noContainers() {
        for (int i = 0; i < 2; i++) {
            ItemStack input = i == 0 ? floating : special;
            if (ItemCallbacks.container(input, true) != null) throw fault("Floating flowers have an unadapted crafting container state");
        }
    }
    private String signature(ItemStack[] examples) {
        return CanonicalJson.digest(object("floating", stack(examples[0]), "special", stack(examples[1]), "output", stack(result(recipes.get(0), unit(examples[0]), unit(examples[1])))));
    }
    private static ItemStack result(IRecipe recipe, ItemStack floating, ItemStack special) {
        InventoryCrafting inventory = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer player) { return false; }
        }, 3, 3);
        inventory.setInventorySlotContents(0, floating.copy()); inventory.setInventorySlotContents(1, special.copy());
        if (!recipe.matches(inventory, null)) throw fault("Native floating-flower example does not match");
        ItemStack output = recipe.getCraftingResult(inventory);
        if (output == null || output.getItem() == null) throw fault("Native floating-flower example has no result");
        return output.copy();
    }
    private static ItemStack unit(ItemStack input) { ItemStack copy = input.copy(); copy.stackSize = 1; return copy; }
    private static JsonObject stack(ItemStack input) {
        return object("registry", Item.itemRegistry.getNameForObject(input.getItem()), "meta", Items.feather.getDamage(input), "count", input.stackSize, "nbt", TypedNbt.encode(input.getTagCompound()));
    }
    private static void changed() { throw new Jobs.Fault("recipe_changed", "Native floating-flower registry, items or display examples changed"); }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
