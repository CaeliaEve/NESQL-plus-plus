package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.ItemList;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;

/** Real AmunRa label failure and Railcraft matching, using the existing offline bootstrap. */
final class NativeRailWildcardTest {
    static void run() throws Exception {
        Class<?> blockType = Class.forName("de.katzenpapst.amunra.block.BlockBasicMeta");
        Block block = (Block) blockType.getConstructor(String.class, Material.class).newInstance("wood_test", Material.wood);
        Class<?> subType = Class.forName("de.katzenpapst.amunra.block.SubBlock");
        for (int meta : new int[]{3, 7}) blockType.getMethod("addSubBlock", int.class, subType).invoke(block, meta,
                subType.getConstructor(String.class, String.class).newInstance("wood" + meta, "test:wood"));
        Item item = (Item) Class.forName("de.katzenpapst.amunra.item.ItemBlockMulti").getConstructor(Block.class).newInstance(block);
        Item.itemRegistry.addObject(31000, "nesqltest:amunra_wood", item);
        ItemStack template = new ItemStack(item, 19, 32767);
        try { template.getDisplayName(); throw new AssertionError("Native wildcard label no longer fails"); }
        catch (ArrayIndexOutOfBoundsException expected) { System.out.println("Confirmed AmunRa native wildcard name failure"); }
        ItemStack candidate = new ItemStack(item, 12, 7);
        candidate.setTagInfo("catalogOnly", new net.minecraft.nbt.NBTTagInt(99));
        List<ItemStack> previous = ItemList.items;
        try {
            derived(item, candidate);
            ItemList.items = new ArrayList<>(Arrays.asList(template.copy(), new ItemStack(Items.coal), candidate));
            for (boolean nbt : new boolean[]{false, true}) {
                if (nbt) template.setTagInfo("grade", new net.minecraft.nbt.NBTTagInt(5));
                Class<?> managerType = Class.forName("mods.railcraft.common.util.crafting.CokeOvenCraftingManager");
                Object manager = managerType.newInstance();
                ItemStack output = new ItemStack(Items.coal, 2, 1);
                managerType.getMethod("addRecipe", ItemStack.class, boolean.class, boolean.class, ItemStack.class, FluidStack.class, int.class)
                        .invoke(manager, template, true, nbt, output, new FluidStack(FluidRegistry.WATER, 250), 51);
                TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("tonius.neiintegration.mods.railcraft.RecipeHandlerCokeOven").newInstance();
                RailRecipes adapter = new RailRecipes(handler, (List<?>) managerType.getMethod("getRecipes").invoke(manager));
                ItemStack exemplar = template.copy(); exemplar.setItemDamage(7);
                // Seed identity records only: this test exercises selection/matching, not a game tooltip renderer.
                RecipeRow row = NativeCoreFixesTest.row(template, exemplar, output);
                require(adapter.capture(0, row), "Wildcard recipe was dropped");
                JsonObject choice = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
                require(choice.get("id").getAsString().equals(id(exemplar)), "Wildcard sentinel reached item facts instead of a concrete NEI exemplar");
                JsonObject rule = choice.getAsJsonObject("rule");
                require(rule.get("meta").getAsBoolean(), "Concrete display narrowed wildcard matching");
                require(nbt ? !rule.get("nbt").getAsBoolean() : rule.get("kind").getAsString().equals("tags"), "NBT/synthetic conditions changed");
                for (int meta : new int[]{0, 3, 7, 32767}) {
                    ItemStack offered = template.copy(); offered.setItemDamage(meta); offered.stackSize = 1;
                    require(managerType.getMethod("getRecipe", ItemStack.class).invoke(manager, offered) != null,
                            "Native manager disagrees with the exported wildcard rule");
                    if (nbt) { offered.setTagCompound(null); require(managerType.getMethod("getRecipe", ItemStack.class).invoke(manager, offered) == null,
                            "Native required NBT was lost"); }
                }
                require(row.record.get("duration").getAsInt() == 100 && choice.get("amount").getAsInt() == 1,
                        "Input quantity or native 50-tick processing interval changed");
                require(exemplar.getDisplayName().contains("wood7") && template.getItemDamage() == 32767
                        && template.stackSize == 19 && candidate.stackSize == 12 && exemplar.hasTagCompound() == nbt,
                        "Native display resolution or template ownership changed");
                require(!exemplar.hasTagCompound() || !exemplar.getTagCompound().hasKey("catalogOnly"), "NEI example NBT leaked into matching");
                ItemList.items = new ArrayList<>(Collections.singletonList(template.copy()));
                try {
                    new RailRecipes(handler, (List<?>) managerType.getMethod("getRecipes").invoke(manager))
                            .capture(0, NativeCoreFixesTest.row(template, output));
                    throw new AssertionError("Missing concrete wildcard example was silently accepted");
                } catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"), "Wrong missing-example failure"); }
                ItemList.items = new ArrayList<>(Arrays.asList(template.copy(), candidate));
            }
        } finally { ItemList.items = previous; }
        System.out.println("Native Railcraft/AmunRa: concrete display, wildcard/NBT semantics, absent-example rejection and source ownership passed");
    }
    private static void derived(Item item, ItemStack candidate) throws Exception {
        // Concrete native input creates a second, literal-32767 branch. Replacing
        // its display must neither turn it into meta 7 nor broaden it to all wood.
        ItemList.items = new ArrayList<>(Collections.singletonList(candidate));
        for (boolean nbt : new boolean[]{false, true}) {
            Class<?> type = Class.forName("mods.railcraft.common.util.crafting.CokeOvenCraftingManager");
            Object manager = type.newInstance();
            Map<String, ItemStack> known = new HashMap<>();
            for (int meta : new int[]{0, 3, 7, 32767}) for (int tag = 0; tag < 3; tag++) {
                ItemStack offered = new ItemStack(item, 1, meta);
                if (tag > 0) offered.setTagInfo("grade", new net.minecraft.nbt.NBTTagInt(5));
                if (tag == 2) offered.setTagInfo("synthetic", new net.minecraft.nbt.NBTTagByte((byte)0));
                known.put(id(offered), offered);
            }
            for (int meta : new int[]{3, 7, 32767}) {
                ItemStack input = new ItemStack(item, 9, meta), output = new ItemStack(Items.coal, meta == 3 ? 2 : 1, meta == 3 ? 1 : 0);
                if (nbt) input.setTagInfo("grade", new net.minecraft.nbt.NBTTagInt(5));
                type.getMethod("addRecipe", ItemStack.class, boolean.class, boolean.class, ItemStack.class, FluidStack.class, int.class)
                        .invoke(manager, input, true, nbt, output, new FluidStack(FluidRegistry.WATER, 250), 51);
                known.put(id(output), output);
            }
            TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("tonius.neiintegration.mods.railcraft.RecipeHandlerCokeOven").newInstance();
            RailRecipes adapter = new RailRecipes(handler, (List<?>) type.getMethod("getRecipes").invoke(manager));
            List<RecipeRow> rows = new ArrayList<>();
            for (int index = 0; index < adapter.size(); index++) {
                RecipeRow row = NativeCoreFixesTest.row(known.values().toArray(new ItemStack[0]));
                if (!adapter.capture(index, row)) continue;
                rows.add(row);
                for (com.google.gson.JsonElement raw : row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices")) {
                    ItemStack display = known.get(raw.getAsJsonObject().get("id").getAsString());
                    require(display != null && display.getItemDamage() != 32767,
                            "Derived literal-wildcard branch reached facts with sentinel metadata");
                    require(display.getDisplayName().contains("wood"), "Concrete native label failed");
                }
            }
            require(rows.size() == 3, "Priority handling dropped an unshadowed native recipe");
            for (ItemStack offered : known.values()) {
                if (offered.getItem() != item) continue;
                Object nativeRecipe = type.getMethod("getRecipe", ItemStack.class).invoke(manager, offered);
                if (offered.hasTagCompound() && offered.getTagCompound().hasKey("synthetic")) nativeRecipe = null;
                String expected = nativeRecipe == null ? null : id((ItemStack) nativeRecipe.getClass().getMethod("getOutput").invoke(nativeRecipe));
                List<String> actual = new ArrayList<>();
                for (RecipeRow row : rows) {
                    boolean accepts = false;
                    for (com.google.gson.JsonElement raw : row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices")) {
                        JsonObject choice = raw.getAsJsonObject();
                        accepts |= NativeRailTest.matches(choice.getAsJsonObject("rule"), known.get(choice.get("id").getAsString()), offered, known);
                    }
                    if (accepts) actual.add(row.outputs.get(0).getAsJsonObject().get("id").getAsString());
                }
                require(expected == null ? actual.isEmpty() : actual.equals(Collections.singletonList(expected)),
                        "Derived branch changed native priority/meta/NBT for meta=" + offered.getItemDamage());
            }
        }
        require(candidate.getItemDamage() == 7 && candidate.stackSize == 12 && candidate.getTagCompound().hasKey("catalogOnly"), "Catalog sample mutated");
    }
    private static String id(ItemStack stack) { return Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound())); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
