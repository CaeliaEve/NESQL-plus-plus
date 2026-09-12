package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.RecipeCatalysts;
import codechicken.nei.recipe.ShapedRecipeHandler;
import codechicken.nei.recipe.ShapelessRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.relauncher.ReflectionHelper;
import gregtech.api.recipe.RecipeCategory;
import gregtech.nei.GTNEIDefaultHandler;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Explicit enumeration adapters. An unsupported handler is an error, never an empty recipe list. */
final class Recipes {
    static List<Handler> handlers() {
        Map<String, Handler> result = new LinkedHashMap<>();
        List<ICraftingHandler> registered = new ArrayList<>(GuiCraftingRecipe.craftinghandlers);
        registered.addAll(GuiCraftingRecipe.serialCraftingHandlers);
        for (ICraftingHandler handler : registered) {
            Handler entry = new Handler(handler, result.size());
            Handler previous = result.putIfAbsent(entry.id, entry);
            if (previous != null && previous.prototype != handler) {
                throw new Jobs.Fault("handler_conflict", "Different NEI handler instances share an identity: " + entry.id);
            }
        }
        return new ArrayList<>(result.values());
    }

    static final class Handler {
        final ICraftingHandler prototype;
        final JsonObject origin;
        final String id, name;
        final boolean supported;
        final int order;

        Handler(ICraftingHandler handler, int order) {
            prototype = handler;
            this.order = order;
            name = handler.getRecipeName();
            HandlerInfo info = GuiRecipeTab.getHandlerInfo(handler);
            String owner = info.getModId();
            String key = handler.getHandlerId();
            if (handler instanceof GTNEIDefaultHandler) {
                RecipeCategory category = ReflectionHelper.getPrivateValue(GTNEIDefaultHandler.class, (GTNEIDefaultHandler) handler, "recipeCategory");
                owner = category.ownerMod.getModId();
                key = category.recipeMap.unlocalizedName + "/" + category.unlocalizedName;
            }
            if (owner == null || owner.isEmpty()) owner = "NEI";
            if (key == null || key.isEmpty()) throw new Jobs.Fault("handler_identity", "NEI handler has no identity: " + handler.getClass().getName());
            origin = object("owner", owner, "handler", handler.getClass().getName(), "key", key);
            id = Identity.origin("category", origin);
            supported = GtRecipes.supports(handler) || MagicRecipes.supports(handler)
                    || handler.getClass() == ShapedRecipeHandler.class || handler.getClass() == ShapelessRecipeHandler.class
                    || handler.getClass() == FurnaceRecipeHandler.class;
        }

        JsonObject describe() {
            return object("id", id, "name", name, "source", origin, "supported", supported,
                    "reason", supported ? null : "No explicit enumeration and semantics adapter");
        }

        Cursor open(Facts facts, boolean views) {
            if (!supported) throw new Jobs.Fault("handler_unsupported", "No recipe adapter for " + prototype.getClass().getName() + " (" + name + ")");
            TemplateRecipeHandler handler = ((TemplateRecipeHandler) prototype).newInstance();
            if (handler == prototype || handler.getClass() != prototype.getClass() || !new Handler(handler, order).id.equals(id)) {
                throw new Jobs.Fault("handler_changed", "NEI handler factory changed the handler identity: " + id);
            }
            GtRecipes gt = null;
            MagicRecipes magic = null;
            if (handler instanceof GTNEIDefaultHandler) {
                GTNEIDefaultHandler machine = (GTNEIDefaultHandler) handler;
                handler.arecipes.addAll(machine.getCache());
                gt = new GtRecipes(machine, views, id);
            } else if (MagicRecipes.supports(handler)) magic = new MagicRecipes(handler);
            else handler.loadCraftingRecipes(handler instanceof FurnaceRecipeHandler ? "smelting" : "crafting");
            HandlerInfo info = GuiRecipeTab.getHandlerInfo(handler);
            JsonObject icon = info.getItemStack() == null ? null : object("kind", "item", "id", facts.item(info.getItemStack()));
            JsonArray machines = new JsonArray();
            Set<String> machineIds = new HashSet<>();
            for (PositionedStack catalyst : RecipeCatalysts.getRecipeCatalysts(handler)) for (ItemStack item : catalyst.items) {
                String id = facts.item(item);
                if (machineIds.add(id)) machines.add(object("kind", "item", "id", id));
            }
            facts.row("categories", object("id", id, "source", origin, "name", facts.text(name),
                    "icon", icon, "machines", machines, "view", null, "order", order));
            return new Cursor(this, handler, facts, views, gt, magic);
        }
    }

    static final class Cursor implements AutoCloseable {
        private final Handler source;
        private final TemplateRecipeHandler handler;
        private final Facts facts;
        private final boolean views;
        private final GtRecipes gt;
        private final MagicRecipes magic;
        private final JsonArray decorations;
        private final Set<String> recipes = new HashSet<>();

        Cursor(Handler source, TemplateRecipeHandler handler, Facts facts, boolean views, GtRecipes gt, MagicRecipes magic) {
            this.source = source; this.handler = handler; this.facts = facts; this.views = views; this.gt = gt; this.magic = magic;
            decorations = new JsonArray();
        }

        int size() { return magic == null ? handler.numRecipes() : magic.size(); }

        void capture(int index) {
            Jobs.checkpoint();
            RecipeRow row = new RecipeRow(facts, source.origin, source.id, index);
            if (gt != null) gt.capture((GTNEIDefaultHandler.CachedDefaultRecipe) handler.arecipes.get(index), row);
            else if (magic != null) { if (!magic.capture(index, row)) return; }
            else {
                boolean crafting = !(handler instanceof FurnaceRecipeHandler);
                int slot = 0;
                for (PositionedStack input : handler.getIngredientStacks(index)) {
                    row.itemInput(input, slot++, 1, false, crafting, object("kind", "wildcard", "meta", false, "nbt", true));
                }
                PositionedStack output = handler.getResultStack(index);
                if (output == null || output.item == null) throw new Jobs.Fault("output_missing", "NEI recipe has no result: " + source.name);
                row.itemOutput(output, 0, output.item, 10000);
                if (crafting) row.property("minecraft:shapeless", "Shapeless", handler instanceof ShapelessRecipeHandler);
                else row.record.addProperty("duration", "200");
                // Furnace.getOtherStacks() is the fuel display, not a recipe output.
            }
            row.finish();
            if (!recipes.add(row.record.get("id").getAsString())) return;
            if (views) {
                if (handler.getClass() == FurnaceRecipeHandler.class && decorations.size() == 0) {
                    for (com.google.gson.JsonElement element : Ui.furnace(facts, (FurnaceRecipeHandler) handler, source.id)) decorations.add(element);
                }
                for (com.google.gson.JsonElement element : decorations) row.elements.add(element);
                HandlerInfo info = GuiRecipeTab.getHandlerInfo(handler);
                int at = magic == null ? index : 0;
                int width = info.getWidth(), height = handler.getRecipeHeight(at);
                if (gt != null) { width = gt.ui.width(width); height = gt.ui.height(height); }
                for (com.google.gson.JsonElement element : row.elements) {
                    JsonObject position = element.getAsJsonObject();
                    if (position.has("width")) width = Math.max(width, position.get("x").getAsInt() + position.get("width").getAsInt());
                    if (position.has("height")) height = Math.max(height, position.get("y").getAsInt() + position.get("height").getAsInt());
                }
                if (width <= 0 || height <= 0 || width > 2048 || height > 2048) throw new Jobs.Fault("view_limit", "Invalid NEI recipe dimensions");
                gregtech.api.util.GTRecipe nativeRecipe = gt == null ? null : ((GTNEIDefaultHandler.CachedDefaultRecipe) handler.arecipes.get(index)).mRecipe;
                if (gt != null) gt.ui.add(facts, row, width, height, nativeRecipe);
                facts.scene(new Facts.Scene(row.record, row.elements, width, height, gt == null ? 0 : gt.ui.foreground(), source.id, () -> {
                    if (gt == null) { handler.drawBackground(at); if (decorations.size() == 0) handler.drawForeground(at); }
                    else gt.ui.context(nativeRecipe, () -> handler.drawForeground(at));
                }));
            }
            facts.row("recipes", row.record);
        }

        @Override public void close() { try { if (gt != null) gt.close(); } finally { handler.arecipes.clear(); } }
    }
}
