package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import thaumcraft.api.aspects.AspectList;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Executes the installed Automagy implementation in an isolated offline registry. */
public final class NativeFilterTest {
    private NativeFilterTest() {}
    public static void main(String[] args) throws Exception { GameTest.run(); }

    public static void run() throws Exception {
        Class<?> paperType = Class.forName("tuhljin.automagy.items.ItemEnchantedPaper");
        Item paper = (Item) paperType.getConstructor(String.class).newInstance("testFilter");
        Class<?> recipeType = Class.forName("tuhljin.automagy.lib.recipe.PreserveFilterRecipe");
        Method nativeCraft = recipeType.getMethod("getCraftingResult", IInventory.class, ItemStack.class);
        JsonArray fixtures = new JsonArray();
        for (int scenario = 0; scenario < 4; scenario++) {
            ItemStack base = new ItemStack(Items.paper, 3, 7);
            NBTTagCompound baseTag = new NBTTagCompound(); baseTag.setString("owner", "base");
            NBTTagCompound stale = new NBTTagCompound(); stale.setBoolean("ignoreMetadata", true);
            baseTag.setTag("FilterOptions", stale); base.setTagCompound(baseTag);
            ItemStack config = new ItemStack(paper, 1, 1);
            if (scenario > 0) {
                NBTTagCompound tags = new NBTTagCompound(); tags.setString("owner", "must-not-copy");
                NBTTagCompound options = new NBTTagCompound();
                options.setInteger("ignoreNBT", 257); options.setBoolean("useItemCount", false);
                options.setString("nameFilter", " \tIron\u00a0 \r");
                options.setString("unknown", "drop"); tags.setTag("FilterOptions", options);
                NBTTagList list = new NBTTagList();
                NBTTagCompound entry = new NBTTagCompound();
                entry.setByte("Slot", (byte) 3); entry.setShort("id", (short) Item.getIdFromItem(Items.apple));
                entry.setInteger("Count", 300); entry.setShort("Damage", (short) -4);
                entry.setString("unknown", "drop"); list.appendTag(entry);
                tags.setTag("ContainedItems", list); config.setTagCompound(tags);
            }
            ItemStack meta = scenario >= 2 ? new ItemStack(paper, 1, 2) : null;
            if (scenario == 3) config.getTagCompound().getCompoundTag("FilterOptions").setInteger("ignoreNBT", 256);
            InventoryBasic inventory = new InventoryBasic("test", false, 2);
            inventory.setInventorySlotContents(0, config.copy());
            if (meta != null) inventory.setInventorySlotContents(1, meta.copy());
            ItemStack expected = (ItemStack) nativeCraft.invoke(null, inventory, base.copy());
            Object filter = paperType.getMethod("getFilterInventory", ItemStack.class).invoke(null, config.copy());
            NBTTagCompound normalized = new NBTTagCompound();
            filter.getClass().getMethod("writeCustomNBT", NBTTagCompound.class).invoke(filter, normalized);
            NBTTagCompound configBefore = config.hasTagCompound() ? (NBTTagCompound) config.getTagCompound().copy() : null;
            if (!Products.filterConfiguration(config).equals(TypedNbt.encode(normalized).getAsJsonObject().getAsJsonObject("value"))) {
                throw new AssertionError("Captured configuration differs from native inventory serialization");
            }
            if (!java.util.Objects.equals(configBefore, config.getTagCompound())) throw new AssertionError("Configuration capture mutated a source stack");
            List<MagicRecipes.Candidate> configChoices = Collections.singletonList(new MagicRecipes.Candidate(config, object("kind", "exact")));
            int[] roles = Products.filterRoles(meta == null ? Collections.singletonList(configChoices) : Arrays.asList(configChoices,
                    Collections.singletonList(new MagicRecipes.Candidate(meta, object("kind", "exact")))));
            if (roles[0] != 0 || roles[1] != (meta == null ? -1 : 1)) throw new AssertionError("Incorrect optional filter roles");
            fixtures.add(object("base", TypedNbt.encode(base.getTagCompound()), "config", TypedNbt.encode(config.getTagCompound()),
                    "normalized", TypedNbt.encode(normalized), "expected", TypedNbt.encode(expected.getTagCompound()),
                    "meta", expected.getItemDamage(), "hasMetadata", meta != null));
            if (expected.stackSize != 3 || expected.getItem() != Items.paper || expected.getItemDamage() != (meta == null ? 7 : 2)
                    || !"base".equals(expected.getTagCompound().getString("owner"))) throw new AssertionError("Native filter facts changed");
        }
        java.nio.file.Path fixture = java.nio.file.Paths.get(System.getProperty("nesql.nativeFilterFixture"));
        java.nio.file.Files.createDirectories(fixture.getParent());
        java.nio.file.Files.write(fixture, (fixtures.toString() + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Actual production invocation boundary: a recipe with one filter has no metadata slot.
        ItemStack config = new ItemStack(paper, 1, 1), base = new ItemStack(Items.paper, 3, 7);
        Object recipe = recipeType.getConstructor(String.class, ItemStack.class, AspectList.class, Object[].class)
                .newInstance("", base, new AspectList(), new Object[] {config});
        List<List<MagicRecipes.Candidate>> inputs = Collections.singletonList(Collections.singletonList(
                new MagicRecipes.Candidate(config, object("kind", "exact"))));
        Method capture = Products.class.getDeclaredMethod("nativeFilter", Object.class, ItemStack.class, ItemStack.class,
                ItemStack.class, List.class, int.class, int.class);
        capture.setAccessible(true);
        ItemStack actual;
        try { actual = (ItemStack) capture.invoke(null, recipe, base, config, null, inputs, 0, -1); }
        catch (java.lang.reflect.InvocationTargetException error) {
            throw new AssertionError("A native PreserveFilter recipe with no second filter must retain base metadata", error.getCause());
        }
        if (actual.getItemDamage() != 7 || actual.stackSize != 3) throw new AssertionError("Lost base output metadata/count");
        MagicRecipes.Candidate ordinary = new MagicRecipes.Candidate(new ItemStack(Items.paper), object("kind", "exact"));
        if (Products.filterRoles(Collections.singletonList(Collections.singletonList(ordinary)))[0] != -1) {
            throw new AssertionError("Recipe without filter must keep its fixed native output");
        }
        try {
            Products.filterRoles(Collections.singletonList(Arrays.asList(inputs.get(0).get(0), ordinary)));
            throw new AssertionError("Mixed filter and ordinary alternatives cannot share one role");
        } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
            if (!expected.getMessage().contains("different inventory roles")) throw expected;
        }
        System.out.println("Native Automagy filter fixtures and optional metadata boundary passed");
    }
}
