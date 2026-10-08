package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagInt;
import net.minecraftforge.oredict.OreDictionary;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Checks catalyst-dependent precedence and native NBT/count semantics against the pinned API. */
final class NativeBotaniaPoolTest {
    static void run() throws Exception {
        Class<?> adapter;
        try { adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.BotaniaPoolRecipes"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Mana infusion has no catalyst-aware native capture", missing); }
        Class<?> recipe = Class.forName("vazkii.botania.api.recipe.RecipeManaInfusion");
        Class<?> blocks = Class.forName("vazkii.botania.common.block.ModBlocks");
        blocks.getField("pool").set(null, Blocks.stone);
        blocks.getField("alchemyCatalyst").set(null, Blocks.gold_block);
        blocks.getField("conjurationCatalyst").set(null, Blocks.iron_block);
        ItemStack iron = new ItemStack(Items.iron_ingot, 19), gold = new ItemStack(Items.gold_ingot, 11);
        ItemStack diamond = new ItemStack(Items.diamond, 3); diamond.setTagInfo("native", new NBTTagInt(7));
        ItemStack emerald = new ItemStack(Items.emerald, 2), apple = new ItemStack(Items.apple);
        OreDictionary.registerOre("nesqlPoolFixture", iron); OreDictionary.registerOre("nesqlPoolFixture", gold);
        Constructor<?> nativeCtor = recipe.getConstructor(ItemStack.class, Object.class, int.class);
        Object alchemy = nativeCtor.newInstance(diamond, iron, 50);
        recipe.getMethod("setAlchemy", boolean.class).invoke(alchemy, true);
        Object common = nativeCtor.newInstance(emerald, "nesqlPoolFixture", 75);
        Object shadowed = nativeCtor.newInstance(apple, iron, 1);
        List<Object> registry = new ArrayList<>(Arrays.asList(alchemy, common, shadowed));
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("vazkii.botania.client.integration.nei.recipe.RecipeHandlerManaPool").newInstance();
        Constructor<?> ctor = adapter.getDeclaredConstructor(TemplateRecipeHandler.class, List.class); ctor.setAccessible(true);
        RegistryRecipes capture = (RegistryRecipes) ctor.newInstance(handler, registry);
        require(capture.size() == 7, "Unflagged recipes must retain each distinct catalyst state");
        Map<String, ItemStack> known = new HashMap<>();
        for (ItemStack item : Arrays.asList(iron, gold, diamond, emerald, apple)) known.put(id(item), item);
        JsonArray records = new JsonArray();
        List<RecipeRow> rows = new ArrayList<>();
        for (int i = 0; i < capture.size(); i++) {
            Facts facts = new Facts("en_US");
            @SuppressWarnings("unchecked") Set<String> items = (Set<String>) MagicApi.field(facts, "items"); items.addAll(known.keySet());
            RecipeRow row = new RecipeRow(facts, object("owner", "Botania", "handler", handler.getClass().getName(), "key", "pool"), "category_test", i);
            if (!capture.capture(i, row)) continue;
            rows.add(row); row.finish(); records.add(row.record);
            require(row.inputs.size() == 1 && row.outputs.size() == 1, "Machine and catalyst blocks became consumed ingredients");
            JsonObject input = row.inputs.get(0).getAsJsonObject();
            for (JsonElement raw : input.getAsJsonArray("choices")) {
                JsonObject choice = raw.getAsJsonObject();
                require(choice.get("amount").getAsString().equals("1") && choice.getAsJsonArray("returns").size() == 0,
                        "Mana pool used template count or invoked crafting container returns");
            }
            require(handler.arecipes.size() == 1 && handler.arecipes.get(0).getResult().item.stackSize > 0,
                    "Native cache projection was not installed");
        }
        require(rows.size() == 4, "First-match native shadowing changed");
        for (String state : Arrays.asList("none", "alchemy", "conjuration")) for (ItemStack item : Arrays.asList(iron, gold)) {
            ItemStack offered = item.copy(); offered.stackSize = 1; offered.setTagInfo("unrelated", new NBTTagInt(22));
            Object winner = null;
            for (Object raw : registry) {
                if ((Boolean) recipe.getMethod("isAlchemy").invoke(raw) && !state.equals("alchemy")) continue;
                if ((Boolean) recipe.getMethod("isConjuration").invoke(raw) && !state.equals("conjuration")) continue;
                if ((Boolean) recipe.getMethod("matches", ItemStack.class).invoke(raw, offered)) { winner = raw; break; }
            }
            List<String> outputs = new ArrayList<>();
            for (RecipeRow row : rows) {
                if (!row.properties.getAsJsonObject("botania:catalyst").getAsJsonObject("value").get("text").getAsString()
                        .equals(Identity.content("text", object("locale", "en_US", "text", state)))) continue;
                for (JsonElement raw : row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices")) {
                    JsonObject choice = raw.getAsJsonObject();
                    if (NativeRailTest.matches(choice.getAsJsonObject("rule"), known.get(choice.get("id").getAsString()), offered, known)) {
                        outputs.add(row.outputs.get(0).getAsJsonObject().get("id").getAsString()); break;
                    }
                }
            }
            require(winner != null && outputs.equals(Collections.singletonList(id((ItemStack) recipe.getMethod("getOutput").invoke(winner)))),
                    "Captured catalyst/input predicates differ from native first-match selection");
        }
        require(rows.get(0).outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3")
                && property(rows.get(0), "botania:mana").equals("50"), "Native output quantity or mana cost changed");
        require(diamond.stackSize == 3 && diamond.getTagCompound().getInteger("native") == 7 && iron.stackSize == 19,
                "Owned capture mutated native stacks");
        capture.verify(); diamond.stackSize = 4;
        try { capture.verify(); throw new AssertionError("Mutated recipe output did not invalidate capture"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_changed"), "Wrong registry drift failure"); }
        diamond.stackSize = 3;
        try { ctor.newInstance(handler, Collections.singletonList(new Object())); throw new AssertionError("Unadapted native callback accepted"); }
        catch (InvocationTargetException expected) { require(expected.getCause() instanceof Jobs.Fault, "Wrong native callback guard"); }
        Class<?> renderer = Class.forName("vazkii.botania.client.render.tile.RenderTilePool");
        Field forceMana = renderer.getField("forceMana"); forceMana.setBoolean(null, false);
        Method scene = adapter.getDeclaredMethod("scene", Runnable.class); scene.setAccessible(true);
        try {
            scene.invoke(null, (Runnable) () -> {
                try { forceMana.setBoolean(null, true); } catch (IllegalAccessException error) { throw new AssertionError(error); }
                throw new IllegalStateException("native draw failed");
            });
            throw new AssertionError("Mana pool scene swallowed native failure");
        } catch (InvocationTargetException expected) { require(expected.getCause() instanceof IllegalStateException, "Mana pool scene changed native error"); }
        require(!forceMana.getBoolean(null), "Mana pool rendering leaked forceMana after failure");
        Method ornaments;
        try { ornaments = adapter.getDeclaredMethod("ornaments", TemplateRecipeHandler.class, int.class); }
        catch (NoSuchMethodException missing) { throw new AssertionError("Mana pool view dropped its machine/catalyst item decorations", missing); }
        ornaments.setAccessible(true);
        @SuppressWarnings("unchecked") List<codechicken.nei.PositionedStack> decorations = (List<codechicken.nei.PositionedStack>) ornaments.invoke(null, handler, 0);
        require(decorations.size() == 2 && decorations.get(0).relx == 71 && decorations.get(1).relx == 10,
                "Mana pool or active catalyst decoration lost its native position");
        Item many = new Item().setHasSubtypes(true); Item.itemRegistry.addObject(31034, "fixture:pool_many", many);
        for (int meta = 0; meta < 1500; meta++) OreDictionary.registerOre("nesqlPoolPriorityBudget", new ItemStack(many, 1, meta));
        RegistryRecipes expensive = (RegistryRecipes) ctor.newInstance(handler, Arrays.asList(
                nativeCtor.newInstance(emerald, "nesqlPoolPriorityBudget", 5), nativeCtor.newInstance(apple, "nesqlPoolPriorityBudget", 10)));
        RecipeRow budgetRow = new RecipeRow(new Facts("en_US"), object("owner", "Botania", "handler", handler.getClass().getName(), "key", "pool"), "category_test", 3);
        try { expensive.capture(3, budgetRow); throw new AssertionError("Mana pool accepted an unbounded first-match comparison workload"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"), "Wrong mana pool proof-budget failure"); }
        Item clamp = new ClampDamageItem(); Item.itemRegistry.addObject(31035, "fixture:pool_clamp", clamp);
        Object callback = nativeCtor.newInstance(emerald, new ItemStack(clamp, 1, 32767), 5);
        ItemStack offered = new ItemStack(clamp); Items.feather.setDamage(offered, 1);
        require(!(Boolean) recipe.getMethod("matches", ItemStack.class).invoke(callback, offered), "Native wildcard setter callback fixture no longer narrows matching");
        try { ctor.newInstance(handler, Collections.singletonList(callback)); throw new AssertionError("Mana pool flattened an overridden wildcard metadata setter"); }
        catch (InvocationTargetException expected) { require(expected.getCause() instanceof Jobs.Fault && ((Jobs.Fault) expected.getCause()).code.equals("recipe_unsupported"), "Wrong mana pool wildcard callback guard"); }
        java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/botania-pool-records.json"), CanonicalJson.bytes(records));
        System.out.println("Native Botania pool: catalyst states, native first-match selection, ignored NBT, unit consumption, output NBT/count and drift guards passed");
    }
    private static String property(RecipeRow row, String key) { return row.properties.getAsJsonObject(key).getAsJsonObject("value").get("value").getAsString(); }
    static final class ClampDamageItem extends Item {
        @Override public void setDamage(ItemStack stack, int damage) { super.setDamage(stack, damage == 32767 ? damage : 0); }
    }
    private static String id(ItemStack stack) { return Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound())); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
