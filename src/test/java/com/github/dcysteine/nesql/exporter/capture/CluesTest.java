package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import cpw.mods.fml.common.Loader;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.oredict.OreDictionary;
import thaumcraft.common.lib.utils.InventoryUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reproduces wildcard-as-item failure and verifies the actual TC clue matcher. */
public final class CluesTest {
    private CluesTest() {}

    public static void run() throws Exception {
        if (!(CluesTest.class.getClassLoader() instanceof LaunchClassLoader)) {
            // Vanilla registration requires FML's real loader type. Keep its
            // static registries isolated from the rest of the source fixture.
            java.net.URL[] urls = Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
                    .map(java.io.File::new).map(java.io.File::toURI).map(uri -> {
                        try { return uri.toURL(); } catch (java.net.MalformedURLException error) { throw new IllegalStateException(error); }
                    }).toArray(java.net.URL[]::new);
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            java.io.PrintStream out = System.out, err = System.err;
            try (LaunchClassLoader loader = new LaunchClassLoader(urls)) {
                Thread.currentThread().setContextClassLoader(loader);
                try { loader.loadClass(CluesTest.class.getName()).getMethod("run").invoke(null); }
                catch (java.lang.reflect.InvocationTargetException error) {
                    if (error.getCause() instanceof Error) throw (Error) error.getCause();
                    throw (Exception) error.getCause();
                }
            } finally {
                Thread.currentThread().setContextClassLoader(previous);
                System.setOut(out); System.setErr(err);
            }
            return;
        }
        Loader.injectData("7", "99", "40", "1614", "1.7.10", "9.05", new java.io.File("."), Collections.emptyList());
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.relauncher.FMLRelaunchLog.class, null,
                cpw.mods.fml.relauncher.Side.CLIENT, "side");
        Bootstrap.func_151354_b();
        // A palette-backed item reproduces the reported failure without guessing
        // which runtime mod supplied the unidentified research trigger.
        Item palette = new Item() {
            private final String[] names = net.minecraft.item.ItemDye.field_150923_a;
            @Override public String getUnlocalizedName(ItemStack stack) { return "fixture." + names[getDamage(stack)]; }
        }.setHasSubtypes(true);
        Item.itemRegistry.addObject(30999, "nesql:clue_palette", palette);
        ItemStack wildcard = new ItemStack(palette, 1, OreDictionary.WILDCARD_VALUE);
        try { wildcard.getDisplayName(); throw new AssertionError("Expected wildcard display to fail"); }
        catch (ArrayIndexOutOfBoundsException expected) {
            require(expected.getMessage().contains("32767"), "Wrong native wildcard failure");
        }
        List<ItemStack> catalog = new ArrayList<>();
        for (int meta = 0; meta < 16; meta++) catalog.add(new ItemStack(palette, 1, meta));
        catalog.add(wildcard); // A pattern must never reach the fact/display callback.
        Set<String> colors = resolve(catalog, wildcard, wildcard);
        require(colors.size() == 16, "Wildcard clues lost variants or retained duplicate patterns");
        require(wildcard.getItemDamage() == 32767, "Clue resolution changed its research-owned pattern");

        ItemStack exact = new ItemStack(palette, 1, 5);
        Set<String> exactResult = resolve(catalog, exact);
        require(exactResult.equals(Collections.singleton(id(exact))), "Exact metadata widened to all variants");
        ItemStack tagged = exact.copy();
        NBTTagCompound data = new NBTTagCompound(); data.setString("fixture", "original"); tagged.setTagCompound(data);
        List<ItemStack> nbtCatalog = Arrays.asList(exact, tagged);
        require(resolve(nbtCatalog, tagged).equals(Collections.singleton(id(tagged))), "Native NBT requirement was ignored");
        require(tagged.getTagCompound().getString("fixture").equals("original"), "Fact callback mutated research NBT");

        ItemStack bow = new ItemStack(Items.bow, 1, 3), worn = new ItemStack(Items.bow, 1, 9);
        require(resolve(Arrays.asList(bow, worn), bow).equals(new LinkedHashSet<>(Arrays.asList(id(bow), id(worn)))),
                "Damageable clues ignored the native durability rule");

        Item first = item(31000, "nesql_clue_first"), alternate = item(31001, "nesql_clue_alternate"), later = item(31002, "nesql_clue_later");
        ItemStack seed = new ItemStack(first, 1, 0), other = new ItemStack(alternate, 1, 2), excluded = new ItemStack(later, 1, 4);
        OreDictionary.registerOre("nesqlCluePrimary", seed);
        OreDictionary.registerOre("nesqlCluePrimary", other);
        OreDictionary.registerOre("nesqlClueSecondary", seed);
        OreDictionary.registerOre("nesqlClueSecondary", excluded);
        List<ItemStack> oreCatalog = Arrays.asList(seed, other, excluded);
        Set<String> nativeMatches = new LinkedHashSet<>();
        for (ItemStack candidate : oreCatalog) if (InventoryUtils.areItemStacksEqual(seed, candidate, true, true, false)) nativeMatches.add(id(candidate));
        require(nativeMatches.contains(id(other)) && !nativeMatches.contains(id(excluded)), "Native first-ore behavior changed");
        require(resolve(oreCatalog, seed).equals(nativeMatches), "Ore clues differ from TC's native matcher");
        ItemStack oreTagged = seed.copy(); oreTagged.setTagCompound((NBTTagCompound) data.copy());
        require(InventoryUtils.areItemStacksEqual(oreTagged, other, true, true, false)
                        && resolve(oreCatalog, oreTagged).contains(id(other)),
                "The native ore branch stopped accepting its NBT-independent alternative");

        rejected(() -> resolve(Collections.emptyList(), wildcard), "No concrete catalog item", "32767");
        rejected(() -> resolve(catalog, (ItemStack) null), "Empty research item trigger", "trigger 0");
        Clues.Cursor empty = new Clues(Collections.emptyList()).open(null);
        require(empty.capture(stack -> { throw new AssertionError("Empty triggers captured an item"); }) && empty.records().size() == 0,
                "Absent triggers are not empty");
        System.out.println("Research clues: wildcard display failure, native matching, NBT, durability, first ore, copies and diagnostics passed");
    }

    private static Set<String> resolve(List<ItemStack> catalog, ItemStack... patterns) {
        Clues.Cursor cursor = new Clues(catalog).open(patterns);
        int ticks = 0;
        boolean done;
        do {
            int[] captures = {0};
            done = cursor.capture(stack -> {
                captures[0]++;
                require(Items.feather.getDamage(stack) != OreDictionary.WILDCARD_VALUE, "Wildcard reached item capture");
                stack.getDisplayName();
                String id = id(stack);
                if (stack.hasTagCompound()) stack.getTagCompound().setString("fixture", "mutated callback copy");
                stack.stackSize = 64;
                return id;
            });
            require(captures[0] <= 16, "Clue capture exceeded its per-call item budget");
            require(++ticks < 10000, "Clue cursor did not terminate");
        } while (!done);
        JsonArray records = cursor.records();
        Set<String> result = new LinkedHashSet<>();
        for (JsonElement record : records) require(result.add(record.getAsString()), "Duplicate clue id");
        return result;
    }

    private static String id(ItemStack stack) {
        return Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound()));
    }

    private static Item item(int index, String name) {
        Item item = new Item().setUnlocalizedName(name).setHasSubtypes(true);
        Item.itemRegistry.addObject(index, "nesql:" + name, item);
        return item;
    }

    private static void rejected(Runnable action, String... details) {
        try { action.run(); throw new AssertionError("Expected research_trigger"); }
        catch (Jobs.Fault expected) {
            require(expected.code.equals("research_trigger"), "Wrong clue failure: " + expected.code);
            for (String detail : details) require(expected.getMessage().contains(detail), "Missing clue diagnostic: " + detail);
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
