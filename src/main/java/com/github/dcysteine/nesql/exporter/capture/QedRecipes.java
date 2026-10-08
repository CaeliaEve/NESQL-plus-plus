package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.ShapedRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** ExtraUtilities 1.2.12 QED: ordered native crafting lookup, ender flux and direct slot consumption. */
final class QedRecipes implements RegistryRecipes {
    private static final String HANDLER = "com.rwtema.extrautils.nei.EnderConstructorHandler";
    private static final String REGISTRY = "com.rwtema.extrautils.tileentity.enderconstructor.EnderConstructorRecipesHandler";
    private final ShapedRecipeHandler handler;
    private final List<?> registry;
    private final List<Entry> all = new ArrayList<>(), visible = new ArrayList<>();
    private boolean nativeRegistry;

    static boolean supports(ICraftingHandler handler) { return handler.getClass().getName().equals(HANDLER); }
    QedRecipes(TemplateRecipeHandler handler) { this(handler, registry()); nativeRegistry = true; }
    private static List<?> registry() {
        version("ExtraUtilities", "1.2.12");
        return (List<?>) field(type(REGISTRY), null, "recipes");
    }
    QedRecipes(TemplateRecipeHandler handler, List<?> registry) {
        if (!supports(handler) || !(handler instanceof ShapedRecipeHandler) || registry == null || registry.size() > 262144)
            throw fault("Invalid QED handler or registry budget");
        this.handler = (ShapedRecipeHandler) handler; this.registry = registry;
        for (Object recipe : registry) {
            Jobs.checkpoint(); Entry entry = new Entry(recipe); all.add(entry);
            // This is the native NEI loader's enumeration rule, not a gameplay omission.
            if (entry.shaped) visible.add(entry);
        }
    }
    public int size() { return visible.size(); }
    public void verify() {
        if (nativeRegistry && field(type(REGISTRY), null, "recipes") != registry || registry.size() != all.size()) changed();
        for (int i = 0; i < all.size(); i++) {
            Jobs.checkpoint(); Entry entry = all.get(i);
            if (registry.get(i) != entry.source || !entry.signature.equals(new Entry(registry.get(i)).signature)) changed();
        }
    }
    public boolean capture(int index, RecipeRow row) {
        Entry entry = visible.get(index);
        if (!entry.signature.equals(new Entry(entry.source).signature)) changed();
        if (entry.impossible) return false;
        if (entry.copyNbt) throw fault("QED shaped recipe copies offered NBT; a fixed output is not valid");
        JsonArray earlier = new JsonArray(); int priorChoices = 0;
        for (Entry prior : all) {
            if (prior == entry) break;
            Jobs.checkpoint();
            if (prior.impossible) continue;
            for (List<ItemStack> group : prior.raw) if (group != null) priorChoices += group.size();
            if (priorChoices > 1048576) throw fault("QED first-match selectors exceed their budget");
            earlier.add(prior.selector(row.facts));
        }
        row.record.add("process", object("kind", "qed", "enderFlux", "20000", "earlier", earlier));
        row.record.add("grid", entry.grid());
        List<List<RecipeRow.Ingredient>> inputs = new ArrayList<>();
        Object[] projection = new Object[entry.raw.length];
        for (int cell = 0; cell < entry.raw.length; cell++) {
            List<ItemStack> templates = entry.raw[cell]; if (templates == null) continue;
            List<RecipeRow.Ingredient> choices = new ArrayList<>();
            for (ItemStack template : templates) {
                Jobs.checkpoint(); boolean wildcard = template.getItemDamage() == 32767;
                ItemStack[] display = samples(template);
                if (choices.size() + display.length > 65536) throw fault("QED alternatives exceed their budget");
                for (ItemStack sample : display) choices.add(new RecipeRow.Ingredient(sample, 1, false, rule(wildcard)));
            }
            inputs.add(choices); projection[cell] = choices.stream().map(choice -> choice.item.copy()).toArray(ItemStack[]::new);
        }
        ShapedRecipeHandler.CachedShapedRecipe cache = handler.new CachedShapedRecipe(entry.width, entry.height, projection, entry.output.copy());
        int slot = 0;
        for (int cell = 0; cell < entry.raw.length; cell++) {
            if (entry.raw[cell] == null) continue;
            int x = 25 + cell % entry.width * 18, y = 6 + cell / entry.width * 18;
            PositionedStack display = null;
            for (PositionedStack candidate : cache.ingredients) if (candidate.relx == x && candidate.rely == y) {
                if (display != null) throw new Jobs.Fault("slot_conflict", "QED cache repeats an input cell");
                display = candidate;
            }
            if (display == null) throw new Jobs.Fault("slot_missing", "QED cache omitted a native input cell");
            // TileEnderConstructor decrements every occupied slot; it never calls a container callback.
            row.itemInput(display, slot, inputs.get(slot), false); slot++;
        }
        row.itemOutput(cache.getResult(), 0, entry.output.copy(), 10000);
        handler.arecipes.clear(); handler.arecipes.add(cache);
        return true;
    }
    static int[][] progressBars() { return new int[][]{{85, 24, 176, 0, 22, 15, 48, 0}}; }
    static String progressTexture() { return "extrautils:textures/guiQED_NEI.png"; }
    static JsonArray decorations(Facts facts, String location) {
        JsonObject clip = object("kind", "clip", "asset", null, "x", 85, "y", 24, "width", 22, "height", 15,
                "z", 0, "track", facts.track(Ui.progress(22, 15, 48, 0)));
        // The native handler inherits vanilla getGuiTexture(), but draws the QED widget atlas explicitly.
        facts.picture(new Facts.Picture(clip, "asset", location + "/progress/qed", 22, 15, () -> {
            codechicken.lib.gui.GuiDraw.changeTexture(progressTexture());
            org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
            codechicken.lib.gui.GuiDraw.drawTexturedModalRect(0, 0, 176, 0, 22, 15);
        }));
        JsonArray elements = new JsonArray(); elements.add(clip); return elements;
    }
    private static JsonObject rule(boolean wildcard) { return object("kind", "wildcard", "meta", wildcard, "nbt", true); }
    private static ItemStack[] samples(ItemStack source) {
        ItemStack template = source.copy(); template.stackSize = 1; template.setTagCompound(null);
        ItemStack[] result = source.getItemDamage() == 32767 ? new PositionedStack(template, 0, 0, true).items : new ItemStack[]{template};
        if (result == null || result.length == 0 || result.length > 65536) throw fault("QED wildcard has no bounded native display expansion");
        for (ItemStack sample : result) if (sample == null || sample.getItem() != source.getItem() || sample.getItemDamage() == 32767)
            throw fault("QED native display substituted an input or retained a wildcard sentinel");
        return result;
    }
    private static final class Entry {
        final Object source;
        final boolean shaped, mirror, copyNbt, impossible;
        final int width, height;
        final List<ItemStack>[] raw;
        final ItemStack output;
        final String signature;
        @SuppressWarnings("unchecked") Entry(Object source) {
            this.source = source;
            if (source == null) throw fault("Null QED recipe");
            Class<?> c = source.getClass();
            if (c != ShapedRecipes.class && c != ShapedOreRecipe.class && c != ShapelessRecipes.class && c != ShapelessOreRecipe.class)
                throw fault("Unadapted QED executable recipe: " + c.getName());
            shaped = c == ShapedRecipes.class || c == ShapedOreRecipe.class;
            boolean ore = c == ShapedOreRecipe.class || c == ShapelessOreRecipe.class;
            width = c == ShapedRecipes.class ? ((ShapedRecipes) source).recipeWidth : shaped ? (Integer) field(source, "width") : 0;
            height = c == ShapedRecipes.class ? ((ShapedRecipes) source).recipeHeight : shaped ? (Integer) field(source, "height") : 0;
            mirror = shaped && (!ore || (Boolean) field(source, "mirrored"));
            copyNbt = c == ShapedRecipes.class && (Boolean) ReflectionHelper.getPrivateValue(ShapedRecipes.class, (ShapedRecipes) source, "field_92101_f");
            Object input = c == ShapedRecipes.class ? ((ShapedRecipes) source).recipeItems : c == ShapedOreRecipe.class ? ((ShapedOreRecipe) source).getInput()
                    : c == ShapelessRecipes.class ? ((ShapelessRecipes) source).recipeItems : ((ShapelessOreRecipe) source).getInput();
            Object[] cells = input instanceof List<?> ? ((List<?>) input).toArray() : ((Object[]) input).clone();
            if (cells.length == 0 || cells.length > 9 || shaped && (width < 1 || width > 3 || height < 1 || height > 3 || cells.length != width * height))
                throw fault("Invalid QED grid");
            raw = (List<ItemStack>[]) new List<?>[cells.length];
            JsonArray fingerprint = new JsonArray(); int count = 0, occupied = 0; boolean empty = false;
            for (int cell = 0; cell < cells.length; cell++) {
                if (cells[cell] == null) {
                    if (!shaped) throw fault("Null shapeless QED ingredient"); fingerprint.add(value(null)); continue;
                }
                occupied++; List<?> choices = ore && cells[cell] instanceof ArrayList<?> ? (List<?>) cells[cell] : Collections.singletonList(cells[cell]);
                raw[cell] = new ArrayList<>(); empty |= choices.isEmpty(); JsonArray group = new JsonArray();
                for (Object choice : choices) {
                    if (++count > 65536 || !(choice instanceof ItemStack) || ((ItemStack) choice).getItem() == null) throw fault("Invalid QED ingredient");
                    ItemStack stack = (ItemStack) choice;
                    if (ItemCallbacks.method(stack.getItem(), "getDamage", "getDamage", ItemStack.class).getDeclaringClass() != Item.class)
                        throw fault("QED input overrides native metadata matching");
                    raw[cell].add(stack.copy()); group.add(stackValue(stack));
                }
                fingerprint.add(group);
            }
            if (occupied == 0) throw fault("QED recipe has no occupied cells"); impossible = empty;
            ItemStack result = ((IRecipe) source).getRecipeOutput();
            if (result == null || result.getItem() == null || result.stackSize <= 0) throw fault("QED recipe has no positive fixed output");
            output = result.copy();
            signature = CanonicalJson.digest(object("class", c.getName(), "width", width, "height", height, "mirror", mirror,
                    "copyNbt", copyNbt, "inputs", fingerprint, "output", stackValue(output)));
        }
        JsonObject grid() {
            JsonArray cells = new JsonArray(); int slot = 0;
            for (List<ItemStack> group : raw) cells.add(value(group == null ? null : slot++));
            return object("width", width, "height", height, "cells", cells, "mirror", mirror);
        }
        JsonObject selector(Facts facts) {
            JsonArray inputs = new JsonArray();
            for (List<ItemStack> group : raw) if (group != null) {
                JsonArray choices = new JsonArray(); Set<String> seen = new HashSet<>();
                for (ItemStack stack : group) {
                    JsonObject choice = object("id", facts.item(samples(stack)[0]), "rule", rule(stack.getItemDamage() == 32767));
                    if (seen.add(CanonicalJson.digest(choice))) choices.add(choice);
                }
                inputs.add(choices);
            }
            return object("grid", shaped ? grid() : null, "inputs", inputs);
        }
    }
    private static JsonObject stackValue(ItemStack item) {
        return object("item", Item.itemRegistry.getNameForObject(item.getItem()), "meta", Items.feather.getDamage(item),
                "amount", item.stackSize, "nbt", TypedNbt.encode(item.getTagCompound()));
    }
    private static void changed() { throw new Jobs.Fault("recipe_changed", "QED native registry changed during capture"); }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
