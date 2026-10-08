package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Botania 1.12.28 mana infusion, including the catalyst-dependent first matching recipe. */
final class BotaniaPoolRecipes implements RegistryRecipes {
    private static final String HANDLER = "vazkii.botania.client.integration.nei.recipe.RecipeHandlerManaPool";
    private static final String RECIPE = "vazkii.botania.api.recipe.RecipeManaInfusion";
    private static final String API = "vazkii.botania.api.BotaniaAPI";
    private static final String[] STATES = {"none", "alchemy", "conjuration"};
    private final TemplateRecipeHandler handler;
    private final List<?> registry;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Variant> variants = new ArrayList<>();
    private boolean nativeRegistry;
    private Object alchemyBlock, conjurationBlock;

    static boolean supports(ICraftingHandler handler) { return handler.getClass().getName().equals(HANDLER); }
    BotaniaPoolRecipes(TemplateRecipeHandler handler) {
        this(handler, registry()); nativeRegistry = true;
        alchemyBlock = field(type("vazkii.botania.common.block.ModBlocks"), null, "alchemyCatalyst");
        conjurationBlock = field(type("vazkii.botania.common.block.ModBlocks"), null, "conjurationCatalyst");
    }
    private static List<?> registry() {
        version("Botania", "1.12.28-GTNH");
        Object alchemy = field(type("vazkii.botania.common.block.ModBlocks"), null, "alchemyCatalyst");
        Object conjuration = field(type("vazkii.botania.common.block.ModBlocks"), null, "conjurationCatalyst");
        if (alchemy == null || conjuration == null || alchemy == conjuration) throw fault("Mana pool catalyst identities are invalid");
        return (List<?>) field(type(API), null, "manaInfusionRecipes");
    }
    BotaniaPoolRecipes(TemplateRecipeHandler handler, List<?> registry) {
        if (!supports(handler) || registry == null || registry.size() > 65536) throw fault("Invalid mana infusion registry");
        this.handler = handler; this.registry = registry;
        Work work = new Work();
        for (Object raw : registry) {
            Jobs.checkpoint(); work.step(); Entry entry = new Entry(raw, work); entries.add(entry);
            if (entry.alchemy && entry.conjuration) variants.add(new Variant(entry, -1));
            else for (int state = 0; state < 3; state++) if (entry.enabled(state)) variants.add(new Variant(entry, state));
        }
    }
    public int size() { return variants.size(); }
    public void verify() {
        if (nativeRegistry && field(type(API), null, "manaInfusionRecipes") != registry || registry.size() != entries.size()) changed();
        if (nativeRegistry && (field(type("vazkii.botania.common.block.ModBlocks"), null, "alchemyCatalyst") != alchemyBlock
                || field(type("vazkii.botania.common.block.ModBlocks"), null, "conjurationCatalyst") != conjurationBlock)) changed();
        Work work = new Work();
        for (int i = 0; i < entries.size(); i++) {
            Jobs.checkpoint(); Entry entry = entries.get(i);
            if (registry.get(i) != entry.source || !entry.signature.equals(new Entry(registry.get(i), work).signature)) changed();
        }
    }
    public boolean capture(int index, RecipeRow row) {
        Variant variant = variants.get(index); Entry entry = variant.entry;
        Work work = new Work();
        if (!entry.signature.equals(new Entry(entry.source, work).signature)) changed();
        if (variant.state < 0 || entry.patterns.isEmpty()) return false;
        List<Pattern> earlier = new ArrayList<>();
        for (Entry prior : entries) {
            work.step();
            if (prior == entry) break;
            if (prior.enabled(variant.state)) earlier.addAll(prior.patterns);
            if (earlier.size() > 65536) throw fault("Mana pool first-match budget exceeded");
        }
        List<RecipeRow.Ingredient> choices = new ArrayList<>();
        for (Pattern base : entry.patterns) {
            Jobs.checkpoint(); boolean covered = false;
            for (Pattern prior : earlier) { work.step(); if (prior.covers(base)) { covered = true; break; } }
            if (covered) continue;
            JsonArray excluded = new JsonArray(); Set<String> seen = new HashSet<>();
            for (Pattern prior : earlier) {
                work.step(); if (!prior.overlaps(base)) continue;
                JsonObject filter = object("id", row.facts.item(prior.example()), "rule", prior.rule());
                if (seen.add(CanonicalJson.digest(filter))) excluded.add(filter);
                if (excluded.size() > 4096) throw fault("Mana infusion priority exceeds the exclusion budget");
            }
            JsonObject predicate = base.rule();
            if (excluded.size() != 0) predicate = object("kind", "except", "base", predicate, "exclude", excluded);
            choices.add(new RecipeRow.Ingredient(base.example(), 1, false, predicate));
        }
        if (choices.isEmpty()) return false;
        PositionedStack display = new PositionedStack(choices.stream().map(choice -> choice.item.copy()).toArray(ItemStack[]::new), 42, 37, false);
        row.itemInput(display, 0, choices, false);
        row.itemOutput(new PositionedStack(entry.output.copy(), 101, 37, false), 0, entry.output.copy(), 10000);
        row.property("botania:mana", "Mana consumed", entry.mana);
        row.property("botania:catalyst", "Block below the pool", STATES[variant.state]);
        row.property("botania:collision", "Native availability", "Live dropped item; catalyst state initialized; item age outside 101..129; current pool mana at least the listed cost");
        // The native cache is retained for its own text, mana bar and block rendering; only owned copies enter it.
        Object projection = TinkerRecipes.construct(type(RECIPE), new Class<?>[]{ItemStack.class, Object.class, int.class}, entry.output.copy(), choices.get(0).item.copy(), entry.mana);
        invoke(type(RECIPE), projection, "setAlchemy", new Class<?>[]{boolean.class}, variant.state == 1);
        invoke(type(RECIPE), projection, "setConjuration", new Class<?>[]{boolean.class}, variant.state == 2);
        TemplateRecipeHandler.CachedRecipe cache = (TemplateRecipeHandler.CachedRecipe) TinkerRecipes.construct(type(HANDLER + "$CachedManaPoolRecipe"),
                new Class<?>[]{handler.getClass(), type(RECIPE)}, handler, projection);
        @SuppressWarnings("unchecked") List<PositionedStack> inputs = (List<PositionedStack>) field(cache, "inputs");
        if (inputs.size() != 2) throw new Jobs.Fault("slot_changed", "Mana pool cache changed its native layout");
        inputs.set(1, display);
        handler.arecipes.clear(); handler.arecipes.add(cache);
        return true;
    }
    static List<PositionedStack> ornaments(TemplateRecipeHandler handler, int index) {
        TemplateRecipeHandler.CachedRecipe cache = handler.arecipes.get(index);
        @SuppressWarnings("unchecked") List<PositionedStack> inputs = (List<PositionedStack>) field(cache, "inputs");
        if (inputs.size() != 2) throw new Jobs.Fault("slot_changed", "Mana pool cache changed its native layout");
        List<PositionedStack> result = new ArrayList<>();
        result.add(inputs.get(0)); result.addAll(cache.getOtherStacks());
        return result;
    }
    static void draw(TemplateRecipeHandler handler, int index) {
        scene(() -> {
            handler.drawBackground(index);
            for (PositionedStack ornament : ornaments(handler, index))
                codechicken.nei.guihook.GuiContainerManager.drawItem(ornament.relx, ornament.rely, ornament.item);
            handler.drawForeground(index);
        });
    }
    static void scene(Runnable draw) {
        Class<?> renderer = type("vazkii.botania.client.render.tile.RenderTilePool");
        boolean previous = (Boolean) field(renderer, null, "forceMana");
        try { draw.run(); }
        finally {
            try { renderer.getField("forceMana").setBoolean(null, previous); }
            catch (ReflectiveOperationException error) { throw new Jobs.Fault("native_cleanup", "Could not restore Botania pool renderer: " + error); }
        }
    }
    private static final class Variant {
        final Entry entry; final int state;
        Variant(Entry entry, int state) { this.entry = entry; this.state = state; }
    }
    private static final class Work {
        int remaining = 1048576;
        void step() {
            if (--remaining < 0) throw fault("Mana pool first-match proof exceeds its comparison budget");
            if ((remaining & 1023) == 0) Jobs.checkpoint();
        }
    }
    private static final class Entry {
        final Object source;
        final boolean alchemy, conjuration;
        final int mana;
        final ItemStack output;
        final List<Pattern> patterns = new ArrayList<>();
        final String signature;
        Entry(Object source, Work work) {
            this.source = source;
            if (source == null || !source.getClass().getName().equals(RECIPE)) throw fault("Unadapted mana infusion recipe callback");
            alchemy = (Boolean) field(source, "isAlchemy"); conjuration = (Boolean) field(source, "isConjuration");
            mana = (Integer) field(source, "mana");
            if (mana < 0) throw fault("Negative mana recipe is a stateful pool refill");
            ItemStack result = (ItemStack) field(source, "output");
            if (result == null || result.getItem() == null || result.stackSize <= 0) throw fault("Invalid mana infusion output");
            output = result.copy();
            Object input = field(source, "input");
            List<?> candidates;
            if (input instanceof ItemStack) candidates = Collections.singletonList(input);
            else if (input instanceof String) candidates = OreDictionary.getOres((String) input);
            else throw fault("Unadapted mana infusion input");
            if (candidates.size() > 65536) throw fault("Mana infusion candidate budget exceeded");
            JsonArray fingerprint = new JsonArray();
            for (Object raw : candidates) {
                work.step();
                if (!(raw instanceof ItemStack) || ((ItemStack) raw).getItem() == null) throw fault("Invalid mana infusion candidate");
                ItemStack item = (ItemStack) raw;
                if (type("vazkii.botania.api.item.IManaDissolvable").isInstance(item.getItem()))
                    throw fault("Mana infusion input has an unadapted pre-recipe dissolve callback: " + item.getItem().getClass().getName());
                if (ItemCallbacks.method(item.getItem(), "getDamage", "getDamage", ItemStack.class).getDeclaringClass() != Item.class)
                    throw fault("Mana infusion input overrides metadata matching");
                if (item.getItemDamage() == 32767 && ItemCallbacks.method(item.getItem(), "setDamage", "setDamage", ItemStack.class, int.class).getDeclaringClass() != Item.class)
                    throw fault("Mana infusion input overrides wildcard metadata substitution");
                patterns.add(new Pattern(item)); fingerprint.add(stackValue(item));
            }
            signature = CanonicalJson.digest(object("inputKind", input instanceof String ? input : "stack", "inputs", fingerprint,
                    "output", stackValue(output), "alchemy", alchemy, "conjuration", conjuration, "mana", mana));
        }
        boolean enabled(int state) { return (!alchemy || state == 1) && (!conjuration || state == 2); }
    }
    private static final class Pattern {
        final ItemStack template; final boolean wildcard;
        Pattern(ItemStack item) { template = item.copy(); template.stackSize = 1; template.setTagCompound(null); wildcard = template.getItemDamage() == 32767; }
        boolean covers(Pattern other) { return template.getItem() == other.template.getItem() && (wildcard || !other.wildcard && template.getItemDamage() == other.template.getItemDamage()); }
        boolean overlaps(Pattern other) { return template.getItem() == other.template.getItem() && (wildcard || other.wildcard || template.getItemDamage() == other.template.getItemDamage()); }
        JsonObject rule() { return object("kind", "wildcard", "meta", wildcard, "nbt", true); }
        ItemStack example() {
            if (!wildcard) return template.copy();
            ItemStack[] samples = new PositionedStack(template.copy(), 0, 0, true).items;
            if (samples == null || samples.length == 0 || samples.length > 65536) throw fault("Mana infusion wildcard has no bounded native display sample");
            for (ItemStack sample : samples) if (sample != null && sample.getItem() == template.getItem() && sample.getItemDamage() != 32767) {
                ItemStack owned = sample.copy(); owned.stackSize = 1; owned.setTagCompound(null); return owned;
            }
            throw fault("Mana infusion wildcard has no concrete native display sample");
        }
    }
    private static JsonObject stackValue(ItemStack item) { return object("item", Item.itemRegistry.getNameForObject(item.getItem()), "meta", Items.feather.getDamage(item), "amount", item.stackSize, "nbt", TypedNbt.encode(item.getTagCompound())); }
    private static void changed() { throw new Jobs.Fault("recipe_changed", "Botania mana infusion registry changed during capture"); }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
