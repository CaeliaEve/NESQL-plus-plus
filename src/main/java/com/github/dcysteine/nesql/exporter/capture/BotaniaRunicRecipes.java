package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Botania's packed, stable runic altar inventory, including rune refunds and dropped livingrock. */
final class BotaniaRunicRecipes implements RegistryRecipes {
    private static final String HANDLER = "vazkii.botania.client.integration.nei.recipe.RecipeHandlerRunicAltar";
    private static final String RECIPE = "vazkii.botania.api.recipe.RecipeRuneAltar";
    private static final String API = "vazkii.botania.api.BotaniaAPI";
    private static final String BLOCKS = "vazkii.botania.common.block.ModBlocks";
    private static final String ITEMS = "vazkii.botania.common.item.ModItems";
    private final TemplateRecipeHandler handler;
    private final List<?> registry;
    private final List<Entry> entries = new ArrayList<>(), visible = new ArrayList<>();
    private final Block rock, altar;
    private final Item rune;
    private boolean nativeRegistry;

    static boolean supports(ICraftingHandler handler) { return handler.getClass().getName().equals(HANDLER); }
    BotaniaRunicRecipes(TemplateRecipeHandler handler) { this(handler, registry()); nativeRegistry = true; }
    private static List<?> registry() {
        version("Botania", "1.12.28-GTNH");
        return (List<?>) field(type(API), null, "runeAltarRecipes");
    }
    BotaniaRunicRecipes(TemplateRecipeHandler handler, List<?> registry) {
        if (!supports(handler) || registry == null || registry.size() > 4096) throw fault("Invalid runic altar registry");
        this.handler = handler; this.registry = registry;
        rock = (Block) field(type(BLOCKS), null, "livingrock");
        altar = (Block) field(type(BLOCKS), null, "runeAltar");
        rune = (Item) field(type(ITEMS), null, "rune");
        if (rock == null || altar == null || rune == null || Item.getItemFromBlock(rock) == null) throw fault("Runic altar native item identities are unavailable");
        Work work = new Work();
        for (Object raw : registry) {
            Jobs.checkpoint(); Entry entry = new Entry(raw, work); entries.add(entry);
            // The native NEI enumeration hides skull outputs; the registry matcher still sees them.
            if (entry.output.getItem() != Items.skull) visible.add(entry);
        }
        for (int i = 0; i < entries.size(); i++) for (int j = 0; j < i; j++) {
            work.step(); Entry a = entries.get(i), b = entries.get(j);
            if (a.inputs.size() == b.inputs.size() && allocationOverlap(a.inputs, b.inputs, 0, 0, work))
                throw fault("Runic altar recipes have overlapping native first-match predicates");
        }
    }
    public int size() { return visible.size(); }
    public void verify() {
        if (nativeRegistry && field(type(API), null, "runeAltarRecipes") != registry || registry.size() != entries.size()) changed();
        if (field(type(BLOCKS), null, "livingrock") != rock || field(type(BLOCKS), null, "runeAltar") != altar || field(type(ITEMS), null, "rune") != rune) changed();
        Work work = new Work();
        for (int i = 0; i < entries.size(); i++) {
            Jobs.checkpoint(); Entry entry = entries.get(i);
            if (registry.get(i) != entry.source || !entry.signature.equals(new Entry(entry.source, work).signature)) changed();
        }
    }
    public boolean capture(int index, RecipeRow row) {
        Entry entry = visible.get(index);
        if (!entry.signature.equals(new Entry(entry.source, new Work()).signature)) changed();
        if (!entry.reachable()) return false;
        List<List<RecipeRow.Ingredient>> inputs = new ArrayList<>();
        Object[] projectionInputs = new Object[entry.inputs.size()];
        for (int i = 0; i < entry.inputs.size(); i++) {
            List<RecipeRow.Ingredient> choices = new ArrayList<>();
            for (Pattern pattern : entry.inputs.get(i).patterns) {
                ItemStack sample = pattern.example();
                choices.add(new RecipeRow.Ingredient(sample, 1, sample.getItem() == rune, pattern.rule()));
            }
            inputs.add(choices); projectionInputs[i] = choices.get(0).item.copy();
        }
        Object projection = TinkerRecipes.construct(type(RECIPE), new Class<?>[]{ItemStack.class, int.class, Object[].class}, entry.output.copy(), entry.mana, projectionInputs);
        TemplateRecipeHandler.CachedRecipe cache = (TemplateRecipeHandler.CachedRecipe) TinkerRecipes.construct(type(HANDLER + "$CachedRunicAltarRecipe"),
                new Class<?>[]{handler.getClass(), type(RECIPE)}, handler, projection);
        @SuppressWarnings("unchecked") List<PositionedStack> positions = (List<PositionedStack>) field(cache, "inputs");
        if (positions.size() != inputs.size() + 1) throw new Jobs.Fault("slot_changed", "Runic altar native cache changed its layout");
        for (int i = 0; i < inputs.size(); i++) {
            PositionedStack position = positions.get(i);
            PositionedStack display = new PositionedStack(inputs.get(i).stream().map(choice -> choice.item.copy()).toArray(ItemStack[]::new), position.relx, position.rely, false);
            positions.set(i, display); row.itemInput(display, i, inputs.get(i), false);
        }
        // Completion uses an entity in the altar AABB, rather than an altar inventory slot.
        row.itemInput(null, 16, Collections.singletonList(new RecipeRow.Ingredient(new ItemStack(rock), 1, false,
                object("kind", "wildcard", "meta", true, "nbt", true))), false);
        row.itemOutput(cache.getResult(), 0, entry.output.copy(), 10000);
        row.property("botania:mana", "Mana consumed", entry.mana);
        row.property("botania:cooldown", "Post-craft altar cooldown in ticks", 60);
        row.property("botania:allocation", "Native altar input slots", "Inputs occupy the first altar slots in any order, one item per slot, with all remaining slots empty");
        row.property("botania:completion", "Native execution conditions", "Stable inputs and no cached recipe override; initialized positive mana target and sufficient mana; server-side wand trigger by a noncreative player or no player; one live livingrock item entity inside the altar bounds");
        handler.arecipes.clear(); handler.arecipes.add(cache);
        return true;
    }
    static List<PositionedStack> ornaments(TemplateRecipeHandler handler, int index) {
        @SuppressWarnings("unchecked") List<PositionedStack> inputs = (List<PositionedStack>) field(handler.arecipes.get(index), "inputs");
        if (inputs.isEmpty()) throw new Jobs.Fault("slot_changed", "Runic altar cache lacks its native center decoration");
        PositionedStack center = inputs.get(inputs.size() - 1);
        if (center.relx != 73 || center.rely != 55) throw new Jobs.Fault("slot_changed", "Runic altar decoration moved");
        return Collections.singletonList(center);
    }
    static void draw(TemplateRecipeHandler handler, int index) {
        handler.drawBackground(index);
        for (PositionedStack ornament : ornaments(handler, index))
            codechicken.nei.guihook.GuiContainerManager.drawItem(ornament.relx, ornament.rely, ornament.item);
        handler.drawForeground(index);
    }
    private static boolean allocationOverlap(List<Group> a, List<Group> b, int at, int used, Work work) {
        work.step(); if (at == a.size()) return true;
        for (int i = 0; i < b.size(); i++) if ((used & 1 << i) == 0 && a.get(at).overlaps(b.get(i), work)
                && allocationOverlap(a, b, at + 1, used | 1 << i, work)) return true;
        return false;
    }
    private static final class Work {
        int remaining = 1048576;
        void step() {
            if (--remaining < 0) throw fault("Runic altar matching proof exceeds its comparison budget");
            if ((remaining & 1023) == 0) Jobs.checkpoint();
        }
    }
    private static final class Entry {
        final Object source; final List<Group> inputs = new ArrayList<>();
        final ItemStack output; final int mana; final String signature;
        Entry(Object source, Work work) {
            this.source = source;
            if (source == null || !source.getClass().getName().equals(RECIPE)) throw fault("Unadapted runic altar recipe callback");
            List<?> raw = (List<?>) field(source, "inputs");
            if (raw == null || raw.size() > 16) throw fault("Runic altar requirements exceed its physical inventory");
            JsonArray values = new JsonArray();
            for (Object input : raw) {
                Group group = new Group(input, work);
                for (Group previous : inputs) if (group.overlaps(previous, work) && !group.key.equals(previous.key))
                    throw fault("Runic altar partially overlapping requirements depend on native greedy input order");
                inputs.add(group); values.add(value(group.signature));
            }
            ItemStack result = (ItemStack) field(source, "output");
            if (result == null || result.getItem() == null || result.stackSize <= 0) throw fault("Invalid runic altar output");
            output = result.copy(); mana = (Integer) field(source, "mana");
            signature = CanonicalJson.digest(object("inputs", values, "output", stackValue(output), "mana", mana));
        }
        boolean reachable() {
            if (mana <= 0) return false;
            for (Group group : inputs) if (group.patterns.isEmpty()) return false;
            return true;
        }
    }
    private static final class Group {
        final List<Pattern> patterns = new ArrayList<>(); final String key, signature;
        Group(Object source, Work work) {
            List<?> raw;
            if (source instanceof String) raw = OreDictionary.getOres((String) source);
            else if (source instanceof ItemStack) raw = Collections.singletonList(source);
            else throw fault("Unadapted runic altar input kind");
            if (raw.size() > 65536) throw fault("Runic altar ore candidates exceed their budget");
            JsonArray fingerprint = new JsonArray();
            for (Object value : raw) {
                work.step();
                if (!(value instanceof ItemStack) || ((ItemStack) value).getItem() == null) throw fault("Invalid runic altar candidate");
                ItemStack stack = (ItemStack) value;
                if (ItemCallbacks.method(stack.getItem(), "getDamage", "getDamage", ItemStack.class).getDeclaringClass() != Item.class)
                    throw fault("Runic altar input overrides native metadata matching");
                boolean wildcard = source instanceof String && stack.getItemDamage() == 32767;
                if (wildcard && ItemCallbacks.method(stack.getItem(), "setDamage", "setDamage", ItemStack.class, int.class).getDeclaringClass() != Item.class)
                    throw fault("Runic altar ore candidate overrides wildcard metadata substitution");
                if (!wildcard && stack.getItemDamage() == 32767) throw fault("Literal runic altar wildcard metadata lacks a concrete native display");
                patterns.add(new Pattern(stack, wildcard)); fingerprint.add(stackValue(stack));
            }
            signature = CanonicalJson.digest(object("kind", source instanceof String ? source : "stack", "values", fingerprint));
            for (int i = patterns.size() - 1; i >= 0; i--) {
                Pattern candidate = patterns.get(i); boolean redundant = false;
                for (int j = 0; j < patterns.size(); j++) {
                    work.step();
                    if (i != j && patterns.get(j).covers(candidate) && (j < i || !candidate.covers(patterns.get(j)))) { redundant = true; break; }
                }
                if (redundant) patterns.remove(i);
            }
            List<String> keys = new ArrayList<>(); for (Pattern pattern : patterns) keys.add(CanonicalJson.digest(object("item", stackValue(pattern.item), "wildcard", pattern.wildcard)));
            Collections.sort(keys); JsonArray normalized = new JsonArray(); for (String pattern : keys) normalized.add(value(pattern));
            key = CanonicalJson.digest(normalized);
        }
        boolean overlaps(Group other, Work work) {
            for (Pattern a : patterns) for (Pattern b : other.patterns) { work.step(); if (a.overlaps(b)) return true; }
            return false;
        }
    }
    private static final class Pattern {
        final ItemStack item; final boolean wildcard;
        Pattern(ItemStack stack, boolean wildcard) { item = stack.copy(); item.stackSize = 1; item.setTagCompound(null); this.wildcard = wildcard; }
        boolean covers(Pattern other) { return item.getItem() == other.item.getItem() && (wildcard || !other.wildcard && item.getItemDamage() == other.item.getItemDamage()); }
        boolean overlaps(Pattern other) { return item.getItem() == other.item.getItem() && (wildcard || other.wildcard || item.getItemDamage() == other.item.getItemDamage()); }
        JsonObject rule() { return object("kind", "wildcard", "meta", wildcard, "nbt", true); }
        ItemStack example() {
            if (!wildcard) return item.copy();
            ItemStack[] choices = new PositionedStack(item.copy(), 0, 0, true).items;
            if (choices == null || choices.length == 0 || choices.length > 65536) throw fault("Runic altar wildcard lacks bounded display candidates");
            for (ItemStack choice : choices) if (choice != null && choice.getItem() == item.getItem() && choice.getItemDamage() != 32767) {
                ItemStack owned = choice.copy(); owned.stackSize = 1; owned.setTagCompound(null); return owned;
            }
            throw fault("Runic altar wildcard lacks a concrete native display");
        }
    }
    private static JsonObject stackValue(ItemStack item) { return object("item", Item.itemRegistry.getNameForObject(item.getItem()), "meta", Items.feather.getDamage(item), "amount", item.stackSize, "nbt", TypedNbt.encode(item.getTagCompound())); }
    private static void changed() { throw new Jobs.Fault("recipe_changed", "Botania runic altar registry or native identities changed during capture"); }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
