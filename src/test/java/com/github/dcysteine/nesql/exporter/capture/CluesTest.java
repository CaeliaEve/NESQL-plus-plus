package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
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

    public static void run() {
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

        ItemStack portal = new ItemStack(Blocks.portal, 1, OreDictionary.WILDCARD_VALUE);
        JsonArray absent = capture(Collections.emptyList(), portal, tagged);
        require(absent.size() == 2 && absent.get(0).getAsJsonObject().get("registry").getAsString().equals("minecraft:portal")
                && absent.get(0).getAsJsonObject().get("meta").getAsInt() == 32767
                && absent.get(0).getAsJsonObject().getAsJsonArray("matches").size() == 0,
                "MIRROR's portal pattern was dropped, rewritten or given an invented item");
        require(absent.get(1).getAsJsonObject().get("nbt").equals(TypedNbt.encode(tagged.getTagCompound()))
                && absent.get(1).getAsJsonObject().getAsJsonArray("matches").size() == 0,
                "A pattern outside the catalog lost its exact metadata or NBT");
        JsonArray orePattern = capture(oreCatalog, seed);
        require(orePattern.get(0).getAsJsonObject().get("ore").getAsString().equals("nesqlCluePrimary"), "Native primary ore was lost");
        rejected(() -> resolve(catalog, (ItemStack) null), "Empty research item trigger", "trigger 0");
        Clues.Cursor empty = new Clues(Collections.emptyList()).open(null);
        require(empty.capture(stack -> { throw new AssertionError("Empty triggers captured an item"); }) && empty.records().size() == 0,
                "Absent triggers are not empty");
        icons();
        pictures();
        System.out.println("Research clues: portal without examples, raw patterns, native matching, NBT, durability, first ore and copies passed");
    }

    private static void icons() {
        // Thaumic Based's Knose fragment declares eight contiguous subtypes and
        // indexes its name array directly, while TB.Knose uses metadata 32767.
        Item fragments = new Item() {
            private final String[] names = {"air", "fire", "aqua", "terra", "order", "entropy", "mixed", "tainted"};
            @Override public String getUnlocalizedName(ItemStack stack) { return "fixture." + names[getDamage(stack)]; }
            @Override public void getSubItems(Item item, net.minecraft.creativetab.CreativeTabs tab, List values) {
                for (int meta = 0; meta < names.length; meta++) values.add(new ItemStack(item, 1, meta));
            }
        }.setHasSubtypes(true);
        Item.itemRegistry.addObject(31003, "nesql:icon_fragments", fragments);
        thaumcraft.api.research.ResearchItem study = study(new ItemStack(fragments, 3, 32767));
        NBTTagCompound data = new NBTTagCompound(); data.setLong("energy", Long.MAX_VALUE);
        study.icon_item.setTagCompound(data);
        try { study.icon_item.getDisplayName(); throw new AssertionError("Expected Knose-shaped wildcard display failure"); }
        catch (ArrayIndexOutOfBoundsException expected) { require(expected.getMessage().contains("32767"), "Wrong icon failure"); }
        long before = System.currentTimeMillis();
        ItemStack frame = Magic.icon(study);
        phase(frame.getItemDamage(), before, System.currentTimeMillis(), 1000, 8);
        require(frame.getItem() == fragments && frame.stackSize == 1 && frame.getTagCompound().getLong("energy") == Long.MAX_VALUE,
                "The native icon lost its item, quantity or NBT");
        frame.getDisplayName();
        frame.getTagCompound().setLong("energy", 1);
        require(study.icon_item.getItemDamage() == 32767 && study.icon_item.stackSize == 3 && data.getLong("energy") == Long.MAX_VALUE,
                "Icon sampling mutated the research template");
        study = study(new ItemStack(fragments, 2, 7));
        require(Magic.icon(study).getItemDamage() == 7 && Magic.icon(study) != study.icon_item,
                "Fixed icons changed or reused their template");
        study = study(new ItemStack(Items.bow, 1, 32767));
        before = System.currentTimeMillis();
        frame = Magic.icon(study);
        phase(frame.getItemDamage(), before, System.currentTimeMillis(), 10, Items.bow.getMaxDamage());
        require(study.icon_item.getItemDamage() == 32767, "Durability icon sampling changed the template");
        study = new thaumcraft.api.research.ResearchItem("RESOURCE", "fixture", new thaumcraft.api.aspects.AspectList(),
                0, 0, 0, new net.minecraft.util.ResourceLocation("fixture", "icon"));
        require(Magic.icon(study) == null, "Resource-only research acquired an item icon");
        study = study(new ItemStack(Items.stick, 1, 32767));
        require(Magic.icon(study).getItemDamage() == 32767, "A native non-cycling display was rejected or rewritten");
        System.out.println("Research icons: native subtype/durability cycles, unchanged display metadata, fixed/resource icons and NBT isolation passed");
    }

    private static thaumcraft.api.research.ResearchItem study(ItemStack icon) {
        return new thaumcraft.api.research.ResearchItem("TB.Knose", "THAUMICBASES", new thaumcraft.api.aspects.AspectList(), 0, 0, 0, icon);
    }

    private static void pictures() {
        int[] names = {0};
        Item title = new Item() {
            @Override public String getItemStackDisplayName(ItemStack stack) {
                names[0]++;
                return new String[] {"slime", "kingSlime"}[getDamage(stack)];
            }
        }.setHasSubtypes(true);
        Item.itemRegistry.addObject(31004, "nesql:titleIcon", title);
        ItemStack decorative = new ItemStack(title, 1, 4099);
        try { decorative.getDisplayName(); throw new AssertionError("Expected title icon name failure"); }
        catch (ArrayIndexOutOfBoundsException error) { require(error.getMessage().contains("4099"), "Wrong title icon failure"); }
        names[0] = 0;
        Facts facts = new Facts("en_US");
        com.google.gson.JsonObject record = com.github.dcysteine.nesql.exporter.source.Json.object("icon", null, "texture", null);
        Magic.picture(Magic.icon(study(decorative)), record, facts);
        Facts.Batch batch = facts.drain();
        require(names[0] == 0 && batch.icons.isEmpty() && batch.records.isEmpty() && batch.pictures.size() == 1,
                "Decorative research art invoked item metadata or created a fabricated item fact");
        require(record.get("icon").isJsonNull() && record.get("texture").isJsonNull()
                && batch.pictures.get(0).record == record && batch.pictures.get(0).field.equals("texture")
                && batch.pictures.get(0).location.endsWith("#4099"), "Decorative icon lost its picture binding or metadata");
        require(decorative.getItemDamage() == 4099, "The decorative icon was rewritten to a named item variant");

        // Declare a previously captured catalog entry without booting a game client.
        // The ordinary-item path must reuse it without another tooltip or picture.
        ItemStack ordinary = new ItemStack(Items.stone_axe, 1, 0);
        String existing = id(ordinary);
        Set<String> captured = cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(Facts.class, facts, "items");
        captured.add(existing);
        record = com.github.dcysteine.nesql.exporter.source.Json.object("icon", null, "texture", null);
        Magic.picture(ordinary, record, facts);
        batch = facts.drain();
        require(record.get("icon").getAsString().equals(existing) && batch.pictures.isEmpty() && batch.icons.isEmpty(),
                "A known research icon lost its item link or queued a duplicate image");
        NBTTagCompound tag = new NBTTagCompound(); tag.setLong("variant", Long.MAX_VALUE); ordinary.setTagCompound(tag);
        require(facts.known(ordinary) == null, "Display lookup merged distinct NBT identities");
        chest();
        System.out.println("Research art: decorative metadata 4099 queues a picture without item text; known item links and NBT identities passed");
    }

    private static void chest() {
        // Salis CustomResearch.maybeRegister uses quantity zero and wildcard
        // metadata even for a chest with neither subtypes nor durability.
        ItemStack template = new ItemStack(Blocks.chest, 0, 32767);
        require(!template.getItem().getHasSubtypes() && !template.isItemStackDamageable(), "Native chest flags changed");
        NBTTagCompound tag = new NBTTagCompound(); tag.setLong("marker", Long.MAX_VALUE); template.setTagCompound(tag);
        ItemStack nativeFrame = InventoryUtils.cycleItemStack(template.copy());
        require(nativeFrame.getItemDamage() == 32767, "Native chest display unexpectedly rewrote wildcard metadata");
        thaumcraft.api.research.ResearchItem research = new thaumcraft.api.research.ResearchItem("salisarcana:CHESTSCAN", "BASICS",
                new thaumcraft.api.aspects.AspectList(), 0, 0, 0, template);
        ItemStack frame = Magic.icon(research);
        require(ItemStack.areItemStacksEqual(nativeFrame, frame), "CHESTSCAN's display differs from the native result");
        frame.getTagCompound().setLong("marker", 1);
        require(template.getTagCompound().getLong("marker") == Long.MAX_VALUE && template.stackSize == 0 && template.getItemDamage() == 32767,
                "CHESTSCAN sampling mutated its registered icon");
        frame = Magic.icon(research);
        Facts facts = new Facts("en_US");
        Set<String> captured = cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(Facts.class, facts, "items");
        captured.add(id(new ItemStack(Blocks.chest, 1, 0)));
        com.google.gson.JsonObject record = com.github.dcysteine.nesql.exporter.source.Json.object("icon", null, "texture", null);
        Magic.picture(frame, record, facts);
        Facts.Batch batch = facts.drain();
        require(record.get("icon").isJsonNull() && batch.icons.isEmpty() && batch.records.isEmpty() && batch.pictures.size() == 1,
                "The wildcard chest was replaced with an inventory item or failed to queue its image");
        require(batch.pictures.get(0).record == record && batch.pictures.get(0).location.equals("minecraft:chest#32767"),
                "CHESTSCAN lost its image binding or original metadata");
        System.out.println("CHESTSCAN: native zero-count wildcard chest, immutable NBT and picture-only capture passed");
    }

    private static void phase(int meta, long before, long after, int interval, int count) {
        require(meta >= 0 && meta < count && (meta - before / interval % count + count) % count <= after / interval - before / interval,
                "The icon frame did not follow the native clock phase");
    }

    private static Set<String> resolve(List<ItemStack> catalog, ItemStack... patterns) {
        Set<String> result = new LinkedHashSet<>();
        for (JsonElement record : capture(catalog, patterns)) {
            Set<String> unique = new LinkedHashSet<>();
            for (JsonElement match : record.getAsJsonObject().getAsJsonArray("matches")) {
                require(unique.add(match.getAsString()), "Duplicate match within one pattern");
                result.add(match.getAsString());
            }
        }
        return result;
    }

    private static JsonArray capture(List<ItemStack> catalog, ItemStack... patterns) {
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
        require(cursor.records().size() == patterns.length, "Declared pattern count changed");
        return cursor.records();
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
