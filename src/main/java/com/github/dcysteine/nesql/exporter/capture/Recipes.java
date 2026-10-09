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
    enum Adapter { GT, MAGIC, AE, INSCRIBER, SCRAPBOX, ENCHANTER, VAT, ASSEMBLY, BUILDCRAFT, SAG, SOUL, IC2, IC2_ADVANCED, IC2_LATHE, PROJECT_BLUE, BLAST, CANNER, SMELTING, EXTREME, FORESTRY, TINKER, SPACE, CIRCUIT, REFINERY, RAIL, ROLLING, DISABLED, SOLAR, GENDUSTRY_FLUIDS, QED, BOTANIA_POOL, BOTANIA_STATIC, BOTANIA_BREWERY, IMBUING, BLOOD_ORB_SHAPED, BLOOD_ORB_SHAPELESS, CREATIVECORE, BOTANIA_RUNIC, BOTANIA_FLOATING, GALAXY_ASSEMBLY, DECAYABLE, BREWING, FIREWORKS, PROJECT_RED, FUEL, CHISEL, FURNACE, SHAPED, SHAPELESS }

    static Adapter adapter(ICraftingHandler handler) {
        if (GtRecipes.supports(handler)) return Adapter.GT;
        if (MagicRecipes.supports(handler)) return Adapter.MAGIC;
        if (AeRecipes.supports(handler)) return Adapter.AE;
        if (InscriberRecipes.supports(handler)) return Adapter.INSCRIBER;
        if (ScrapboxRecipes.supports(handler)) return Adapter.SCRAPBOX;
        if (EnderEnchanterRecipes.supports(handler)) return Adapter.ENCHANTER;
        if (VatRecipes.supports(handler)) return Adapter.VAT;
        if (EnderAssemblyRecipes.supports(handler)) return Adapter.ASSEMBLY;
        if (SagRecipes.supports(handler)) return Adapter.SAG;
        if (SoulRecipes.supports(handler)) return Adapter.SOUL;
        if (Ic2Recipes.supports(handler)) return Adapter.IC2;
        if (Ic2AdvancedRecipes.supports(handler)) return Adapter.IC2_ADVANCED;
        if (Ic2LatheRecipes.supports(handler)) return Adapter.IC2_LATHE;
        if (ProjectBlueRecipes.supports(handler)) return Adapter.PROJECT_BLUE;
        if (BlastRecipes.supports(handler)) return Adapter.BLAST;
        if (CannerRecipes.supports(handler)) return Adapter.CANNER;
        if (BuildcraftRecipes.supports(handler)) return Adapter.BUILDCRAFT;
        if (IntegrationRecipes.supports(handler)) return Adapter.BUILDCRAFT;
        if (RefiningRecipes.supports(handler)) return Adapter.BUILDCRAFT;
        if (SmeltingRecipes.supports(handler)) return Adapter.SMELTING;
        if (ExtremeRecipes.supports(handler)) return Adapter.EXTREME;
        if (ForestryRecipes.supports(handler) || SqueezerRecipes.supports(handler)) return Adapter.FORESTRY;
        if (TinkerRecipes.supports(handler)) return Adapter.TINKER;
        if (SpaceRecipes.supports(handler)) return Adapter.SPACE;
        if (CircuitRecipes.supports(handler)) return Adapter.CIRCUIT;
        if (RefineryRecipes.supports(handler)) return Adapter.REFINERY;
        if (RailRecipes.supports(handler)) return Adapter.RAIL;
        if (RollingRecipes.supports(handler)) return Adapter.ROLLING;
        if (DisabledRecipes.supports(handler)) return Adapter.DISABLED;
        if (SolarRecipes.supports(handler)) return Adapter.SOLAR;
        if (GendustryFluidRecipes.supports(handler)) return Adapter.GENDUSTRY_FLUIDS;
        if (QedRecipes.supports(handler)) return Adapter.QED;
        if (BotaniaPoolRecipes.supports(handler)) return Adapter.BOTANIA_POOL;
        if (BotaniaStaticRecipes.supports(handler)) return Adapter.BOTANIA_STATIC;
        if (BotaniaBreweryRecipes.supports(handler)) return Adapter.BOTANIA_BREWERY;
        if (ImbuingRecipes.supports(handler)) return Adapter.IMBUING;
        if (BloodOrbShapedRecipes.supports(handler)) return Adapter.BLOOD_ORB_SHAPED;
        if (BloodOrbShapelessRecipes.supports(handler)) return Adapter.BLOOD_ORB_SHAPELESS;
        if (CreativeCoreRecipes.supports(handler)) return Adapter.CREATIVECORE;
        if (BotaniaRunicRecipes.supports(handler)) return Adapter.BOTANIA_RUNIC;
        if (BotaniaFloatingRecipes.supports(handler)) return Adapter.BOTANIA_FLOATING;
        if (GalaxyAssemblyRecipes.supports(handler)) return Adapter.GALAXY_ASSEMBLY;
        if (DecayableRecipes.supports(handler)) return Adapter.DECAYABLE;
        if (BrewingRecipes.supports(handler)) return Adapter.BREWING;
        if (FireworkRecipes.supports(handler)) return Adapter.FIREWORKS;
        if (ProjectRedRecipes.supports(handler)) return Adapter.PROJECT_RED;
        if (FuelRecipes.supports(handler)) return Adapter.FUEL;
        if (ChiselRecipes.supports(handler)) return Adapter.CHISEL;
        if (handler.getClass() == FurnaceRecipeHandler.class) return Adapter.FURNACE;
        if (handler.getClass() == ShapedRecipeHandler.class) return Adapter.SHAPED;
        if (handler.getClass() == ShapelessRecipeHandler.class) return Adapter.SHAPELESS;
        return null;
    }

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
        final Adapter adapter;
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
            adapter = adapter(handler);
            supported = adapter != null;
        }

        JsonObject describe() {
            String reason = null;
            if (!supported) {
                if (prototype.getClass().getName().contains("ProfilerRecipeHandler")) {
                    reason = "Excluded non-gameplay profiling utility";
                } else {
                    reason = "No production recipe adapter implemented";
                }
            }
            return object("id", id, "name", name, "source", origin, "supported", supported, "reason", reason);
        }

        Cursor open(Facts facts, boolean views) {
            if (!supported) throw new Jobs.Fault("handler_unsupported", "No recipe adapter for " + prototype.getClass().getName() + " (" + name + ")");
            TemplateRecipeHandler handler = ((TemplateRecipeHandler) prototype).newInstance();
            if (handler == prototype || handler.getClass() != prototype.getClass() || !new Handler(handler, order).id.equals(id)) {
                throw new Jobs.Fault("handler_changed", "NEI handler factory changed the handler identity: " + id);
            }
            GtRecipes gt = null;
            MagicRecipes magic = null;
            AeRecipes ae = null;
            RegistryRecipes registry = null;
            Crafting crafting = null;
            try {
                if (adapter == Adapter.GT) {
                    GTNEIDefaultHandler machine = (GTNEIDefaultHandler) handler;
                    handler.arecipes.addAll(machine.getCache());
                    gt = new GtRecipes(machine, views, id);
                } else if (adapter == Adapter.MAGIC) {
                    magic = new MagicRecipes(handler);
                } else if (adapter == Adapter.AE) {
                    ae = new AeRecipes(handler);
                } else if (adapter == Adapter.IC2) {
                    registry = new Ic2Recipes(handler);
                } else if (adapter == Adapter.IC2_ADVANCED) {
                    registry = new Ic2AdvancedRecipes(handler);
                } else if (adapter == Adapter.IC2_LATHE) {
                    registry = new Ic2LatheRecipes(handler);
                } else if (adapter == Adapter.PROJECT_BLUE) {
                    registry = new ProjectBlueRecipes(handler);
                } else if (adapter == Adapter.CANNER) {
                    registry = new CannerRecipes(handler);
                } else if (adapter == Adapter.BLAST) {
                    registry = new BlastRecipes(handler);
                } else if (adapter == Adapter.BUILDCRAFT) {
                    registry = IntegrationRecipes.supports(handler) ? new IntegrationRecipes(handler)
                        : RefiningRecipes.supports(handler) ? new RefiningRecipes(handler) : new BuildcraftRecipes(handler);
                } else if (adapter == Adapter.INSCRIBER) {
                    registry = new InscriberRecipes(handler);
                } else if (adapter == Adapter.SCRAPBOX) {
                    registry = new ScrapboxRecipes(handler);
                } else if (adapter == Adapter.ENCHANTER) {
                    registry = new EnderEnchanterRecipes(handler);
                } else if (adapter == Adapter.VAT) {
                    registry = new VatRecipes(handler);
                } else if (adapter == Adapter.ASSEMBLY) {
                    registry = new EnderAssemblyRecipes(handler);
                } else if (adapter == Adapter.SAG) {
                    registry = new SagRecipes(handler);
                } else if (adapter == Adapter.SOUL) {
                    registry = new SoulRecipes(handler);
                } else if (adapter == Adapter.SMELTING) {
                    registry = new SmeltingRecipes((FurnaceRecipeHandler) handler);
                } else if (adapter == Adapter.EXTREME) {
                    registry = new ExtremeRecipes(handler);
                } else if (adapter == Adapter.FORESTRY) {
                    registry = SqueezerRecipes.supports(handler) ? new SqueezerRecipes(handler) : new ForestryRecipes(handler);
                } else if (adapter == Adapter.TINKER) {
                    registry = new TinkerRecipes(handler);
                } else if (adapter == Adapter.SPACE) {
                    registry = new SpaceRecipes(handler);
                } else if (adapter == Adapter.CIRCUIT) {
                    registry = new CircuitRecipes(handler);
                } else if (adapter == Adapter.REFINERY) {
                    registry = new RefineryRecipes(handler);
                } else if (adapter == Adapter.RAIL) {
                    registry = new RailRecipes(handler);
                } else if (adapter == Adapter.ROLLING) {
                    registry = new RollingRecipes(handler);
                } else if (adapter == Adapter.DISABLED) {
                    registry = new DisabledRecipes(handler);
                } else if (adapter == Adapter.SOLAR) {
                    registry = new SolarRecipes(handler);
                } else if (adapter == Adapter.GENDUSTRY_FLUIDS) {
                    registry = new GendustryFluidRecipes(handler);
                } else if (adapter == Adapter.QED) {
                    registry = new QedRecipes(handler);
                } else if (adapter == Adapter.BOTANIA_POOL) {
                    registry = new BotaniaPoolRecipes(handler);
                } else if (adapter == Adapter.BOTANIA_STATIC) {
                    registry = new BotaniaStaticRecipes(handler);
                } else if (adapter == Adapter.BOTANIA_BREWERY) {
                    registry = new BotaniaBreweryRecipes(handler);
                } else if (adapter == Adapter.IMBUING) {
                    registry = new ImbuingRecipes(handler);
                } else if (adapter == Adapter.BLOOD_ORB_SHAPED) {
                    registry = new BloodOrbShapedRecipes(handler);
                } else if (adapter == Adapter.BLOOD_ORB_SHAPELESS) {
                    registry = new BloodOrbShapelessRecipes(handler);
                } else if (adapter == Adapter.CREATIVECORE) {
                    registry = new CreativeCoreRecipes(handler);
                } else if (adapter == Adapter.BOTANIA_RUNIC) {
                    registry = new BotaniaRunicRecipes(handler);
                } else if (adapter == Adapter.BOTANIA_FLOATING) {
                    registry = new BotaniaFloatingRecipes(handler);
                } else if (adapter == Adapter.GALAXY_ASSEMBLY) {
                    registry = new GalaxyAssemblyRecipes(handler);
                } else if (adapter == Adapter.DECAYABLE) {
                    registry = new DecayableRecipes(handler);
                } else if (adapter == Adapter.BREWING) {
                    registry = new BrewingRecipes(handler);
                } else if (adapter == Adapter.FIREWORKS) {
                    registry = new FireworkRecipes(handler);
                } else if (adapter == Adapter.PROJECT_RED) {
                    registry = new ProjectRedRecipes(handler);
                } else if (adapter == Adapter.FUEL) {
                    registry = new FuelRecipes(handler);
                } else if (adapter == Adapter.CHISEL) {
                    registry = new ChiselRecipes(handler);
                } else if (adapter == Adapter.FURNACE) {
                    handler.loadCraftingRecipes("smelting");
                } else if (adapter == Adapter.SHAPED) {
                    crafting = new Crafting((ShapedRecipeHandler) handler, net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList());
                } else if (adapter == Adapter.SHAPELESS) {
                    handler.loadCraftingRecipes("crafting");
                } else {
                    throw new Jobs.Fault("handler_unsupported", "No recipe loader for: " + handler.getClass().getName());
                }
                HandlerInfo info = GuiRecipeTab.getHandlerInfo(handler);
                JsonObject icon = info.getItemStack() == null ? null : object("kind", "item", "id", facts.item(info.getItemStack()));
                JsonArray machines = new JsonArray();
                Set<String> machineIds = new HashSet<>();
                for (PositionedStack catalyst : RecipeCatalysts.getRecipeCatalysts(handler)) for (ItemStack item : catalyst.items) {
                    String id = facts.item(item);
                    if (machineIds.add(id)) machines.add(object("kind", "item", "id", id));
                }
                JsonObject category = object("id", id, "source", origin, "name", facts.text(name),
                        "icon", icon, "machines", machines, "view", null, "order", order);
                String program = registry == null ? null : registry.program(facts);
                if (program != null) category.addProperty("program", program);
                facts.row("categories", category);
                return new Cursor(this, handler, facts, views, gt, magic, ae, registry, crafting);
            } catch (RuntimeException | Error failure) {
                try { if (gt != null) gt.close(); }
                catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                finally { handler.arecipes.clear(); }
                throw failure;
            }
        }
    }

    static final class Cursor implements AutoCloseable {
        private final Handler source;
        private final TemplateRecipeHandler handler;
        private final Facts facts;
        private final boolean views;
        private final GtRecipes gt;
        private final MagicRecipes magic;
        private final AeRecipes ae;
        private final RegistryRecipes registry;
        private final Crafting crafting;
        private final JsonArray decorations;
        private final Set<String> recipes = new HashSet<>();
        private final JsonObject exclusions = new JsonObject();

        Cursor(Handler source, TemplateRecipeHandler handler, Facts facts, boolean views, GtRecipes gt, MagicRecipes magic, AeRecipes ae, RegistryRecipes registry, Crafting crafting) {
            this.source = source; this.handler = handler; this.facts = facts; this.views = views; this.gt = gt; this.magic = magic;
            this.ae = ae;
            this.registry = registry;
            this.crafting = crafting;
            decorations = new JsonArray();
            if (registry != null) {
                JsonObject proof = registry.handlerExclusion();
                if (proof != null) exclusions.add("handler", new com.google.gson.JsonParser().parse(proof.toString()));
            }
        }

        int size() { return registry != null ? registry.size() : ae != null ? ae.size() : magic == null ? handler.numRecipes() : magic.size(); }
        void verify() { if (registry != null) registry.verify(); }

        JsonObject exclusions() { return new com.google.gson.JsonParser().parse(exclusions.toString()).getAsJsonObject(); }

        void capture(int index) {
            capture(index, facts);
        }

        void capture(int index, Facts facts) {
            try { captureRow(index, facts); }
            catch (java.util.concurrent.CancellationException error) { throw error; }
            catch (RuntimeException error) {
                Jobs.Fault failure = new Jobs.Fault(error instanceof Jobs.Fault ? ((Jobs.Fault) error).code : "recipe_capture",
                        "Recipe handler '" + source.name + "'; category=" + source.id + "; index=" + index
                                + (magic != null ? "; native=" + magic.sourceType(index) : crafting != null ? "; native=" + crafting.sourceType(index) : "") + ": " + error);
                failure.initCause(error);
                throw failure;
            }
        }

        private void captureRow(int index, Facts facts) {
            Jobs.checkpoint();
            RecipeRow row = new RecipeRow(facts, source.origin, source.id, index);
            if (gt != null) {
                GTNEIDefaultHandler.CachedDefaultRecipe cached = (GTNEIDefaultHandler.CachedDefaultRecipe) handler.arecipes.get(index);
                JsonObject proof = Replaced.inspect(cached.mRecipe, source.origin.get("key").getAsString());
                if (proof != null) {
                    if (exclusions.entrySet().size() >= 128 && !exclusions.has(Integer.toString(index)))
                        throw new Jobs.Fault("recipe_exclusion_limit", "Too many obsolete-input exclusions in " + source.id);
                    exclusions.add(Integer.toString(index), proof);
                    return;
                }
                for (RecipeRow branch : gt.capture(cached, row)) emit(index, branch, facts);
                return;
            }
            else if (magic != null) { if (!magic.capture(index, row)) return; }
            else if (ae != null) { ae.capture(index, row); }
            else if (registry != null) {
                if (!registry.capture(index, row)) {
                    JsonObject proof = registry.exclusion(index);
                    if (proof != null) {
                        if (exclusions.entrySet().size() - (exclusions.has("handler") ? 1 : 0) >= 4096 && !exclusions.has(Integer.toString(index)))
                            throw new Jobs.Fault("recipe_exclusion_limit", "Registry exclusion proof budget exceeded in " + source.id);
                        exclusions.add(Integer.toString(index), proof);
                    }
                    return;
                }
            }
            else if (crafting != null && crafting.capture(index, row)) { /* Native special result captured. */ }
            else {
                boolean crafting = !(handler instanceof FurnaceRecipeHandler);
                int slot = 0;
                for (PositionedStack input : handler.getIngredientStacks(index)) {
                    int inSize = (input != null && input.item != null && input.item.stackSize > 0) ? input.item.stackSize : 1;
                    row.itemInput(input, slot++, inSize, false, crafting, object("kind", "wildcard", "meta", false, "nbt", true));
                }
                PositionedStack output = handler.getResultStack(index);
                if (output != null && output.item != null) {
                    row.itemOutput(output, 0, output.item, 10000);
                }
                int outSlot = (output != null && output.item != null) ? 1 : 0;
                List<PositionedStack> others = handler.getOtherStacks(index);
                if (others != null && !(handler instanceof FurnaceRecipeHandler)) {
                    for (PositionedStack other : others) {
                        if (other != null && other.item != null) {
                            row.itemOutput(other, outSlot++, other.item, 10000);
                        }
                    }
                }
                if (outSlot == 0) {
                    throw new Jobs.Fault("output_missing", "NEI recipe has no result: " + source.name);
                }
                if (crafting && handler instanceof ShapelessRecipeHandler) row.property("minecraft:shapeless", "Shapeless", true);
                else if (!crafting) row.record.addProperty("duration", "200");
                // Furnace.getOtherStacks() is the fuel display, not a recipe output.
            }
            emit(index, row, facts);
        }

        private void emit(int index, RecipeRow row, Facts facts) {
            row.finish();
            if (!recipes.add(row.record.get("id").getAsString())) return;
            if (!views) dimensions(index, row);
            if (views) {
                if ((handler.getClass() == FurnaceRecipeHandler.class || registry instanceof SmeltingRecipes) && decorations.size() == 0) {
                    for (com.google.gson.JsonElement element : Ui.furnace(facts, (FurnaceRecipeHandler) handler, source.id)) decorations.add(element);
                } else if (registry instanceof Ic2Recipes && decorations.size() == 0) {
                    for (com.google.gson.JsonElement element : Ui.neiProgress(facts, handler, source.id, Ic2Recipes.progressBars(handler))) decorations.add(element);
                } else if (registry instanceof CannerRecipes && decorations.size() == 0) {
                    for (com.google.gson.JsonElement element : Ui.neiProgress(facts, handler, source.id, CannerRecipes.progressBars(handler))) decorations.add(element);
                } else if (registry instanceof BlastRecipes && decorations.size() == 0) {
                    for (com.google.gson.JsonElement element : Ui.neiProgress(facts, handler, source.id, BlastRecipes.progressBars())) decorations.add(element);
                } else if (registry instanceof BuildcraftRecipes && decorations.size() == 0) {
                    for (com.google.gson.JsonElement element : Ui.neiProgress(facts, handler, source.id, BuildcraftRecipes.progressBars())) decorations.add(element);
                } else if (registry instanceof ForestryRecipes && decorations.size() == 0) {
                    for (com.google.gson.JsonElement element : Ui.neiProgress(facts, handler, source.id, new int[][] {ForestryRecipes.progressBar(handler)})) decorations.add(element);
                } else if (registry instanceof CircuitRecipes && decorations.size() == 0) {
                    for (com.google.gson.JsonElement element : CircuitRecipes.decorations(facts, handler, source.id)) decorations.add(element);
                } else if (registry instanceof RefineryRecipes && decorations.size() == 0) {
                    for (com.google.gson.JsonElement element : RefineryRecipes.decorations(facts, handler, source.id)) decorations.add(element);
                } else if (registry instanceof RollingRecipes && decorations.size() == 0) {
                    for (com.google.gson.JsonElement element : Ui.neiProgress(facts, handler, source.id, RollingRecipes.progressBars())) decorations.add(element);
                } else if (registry instanceof QedRecipes && decorations.size() == 0) {
                    for (com.google.gson.JsonElement element : QedRecipes.decorations(facts, source.id)) decorations.add(element);
                } else if (registry instanceof ImbuingRecipes && decorations.size() == 0) {
                    for (com.google.gson.JsonElement element : Ui.neiProgress(facts, handler, source.id, ImbuingRecipes.progressBars())) decorations.add(element);
                } else if (registry instanceof GalaxyAssemblyRecipes && decorations.size() == 0) {
                    for (com.google.gson.JsonElement element : GalaxyAssemblyRecipes.decorations(facts, source.id)) decorations.add(element);
                } else if (registry instanceof RailRecipes && decorations.size() == 0) {
                    for (com.google.gson.JsonElement element : Ui.neiProgress(facts, handler, source.id, RailRecipes.progressBars(handler))) decorations.add(element);
                } else if (registry instanceof SolarRecipes && decorations.size() == 0) {
                    for (com.google.gson.JsonElement element : Ui.neiProgress(facts, handler, source.id, SolarRecipes.progressBars())) decorations.add(element);
                } else if (registry instanceof EnderAssemblyRecipes && decorations.size() == 0) {
                    for (com.google.gson.JsonElement element : Ui.neiProgress(facts, handler, source.id, ((EnderAssemblyRecipes)registry).progressBars())) decorations.add(element);
                } else if (registry instanceof SagRecipes && decorations.size() == 0) {
                    for (com.google.gson.JsonElement element : Ui.neiProgress(facts, handler, source.id, ((SagRecipes)registry).progressBars())) decorations.add(element);
                } else if (registry instanceof SoulRecipes && decorations.size() == 0) {
                    for (com.google.gson.JsonElement element : Ui.neiProgress(facts, handler, source.id, ((SoulRecipes)registry).progressBars())) decorations.add(element);
                }
                for (com.google.gson.JsonElement element : decorations) row.elements.add(element);
                if(registry instanceof SqueezerRecipes)for(com.google.gson.JsonElement element:((SqueezerRecipes)registry).decorations(facts))row.elements.add(element);
                int at = magic == null && ae == null && registry == null ? index : 0;
                int[] dimensions = dimensions(index, row);
                int width = dimensions[0], height = dimensions[1];
                gregtech.api.util.GTRecipe nativeRecipe = gt == null ? null : ((GTNEIDefaultHandler.CachedDefaultRecipe) handler.arecipes.get(index)).mRecipe;
                if (gt != null) gt.ui.add(facts, row, width, height, nativeRecipe);
                facts.scene(new Facts.Scene(row.record, row.elements, width, height, gt == null ? 0 : gt.ui.foreground(), source.id, () -> {
                    if (registry instanceof Ic2Recipes) Ic2Recipes.scene(handler, () -> Ic2Recipes.draw(handler, at));
                    else if (registry instanceof CannerRecipes) CannerRecipes.scene(handler, () -> CannerRecipes.draw(handler, at));
                    else if (registry instanceof BlastRecipes) BlastRecipes.draw(handler, at);
                    else if (registry instanceof BuildcraftRecipes) BuildcraftRecipes.draw(handler, at);
                    else if (registry instanceof IntegrationRecipes) IntegrationRecipes.draw(handler,row.record.getAsJsonObject("process").getAsJsonObject("rule").get("energy").getAsInt());
                    else if (registry instanceof RefiningRecipes) RefiningRecipes.draw(handler, at);
                    else if (registry instanceof ForestryRecipes) ForestryRecipes.draw(handler, at);
                    else if (registry instanceof SqueezerRecipes) SqueezerRecipes.draw(handler, at);
                    else if (registry instanceof CircuitRecipes) CircuitRecipes.scene(handler, () -> handler.drawBackground(at));
                    else if (registry instanceof RefineryRecipes) RefineryRecipes.draw(handler);
                    else if (registry instanceof RailRecipes) RailRecipes.draw(handler);
                    else if (registry instanceof SolarRecipes) SolarRecipes.draw(handler);
                    else if (registry instanceof VatRecipes) ((VatRecipes)registry).draw(index);
                    else if (registry instanceof EnderAssemblyRecipes) ((EnderAssemblyRecipes)registry).draw(index);
                    else if (registry instanceof SagRecipes) ((SagRecipes)registry).draw(index);
                    else if (registry instanceof SoulRecipes) ((SoulRecipes)registry).draw(index);
                    else if (registry instanceof BotaniaPoolRecipes) BotaniaPoolRecipes.draw(handler, at);
                    else if (registry instanceof BotaniaRunicRecipes) BotaniaRunicRecipes.draw(handler, at);
                    else if (registry instanceof GalaxyAssemblyRecipes) GalaxyAssemblyRecipes.draw(handler, at);
                    else if (registry instanceof EnderEnchanterRecipes) EnderEnchanterRecipes.scene(handler,row.record.getAsJsonObject("process").get("level").getAsInt(),
                            () -> {handler.drawBackground(at);handler.drawForeground(at);});
                    else if (gt == null) { handler.drawBackground(at); if (decorations.size() == 0) handler.drawForeground(at); }
                    else gt.foreground(at, row);
                }));
            }
            facts.row("recipes", row.record);
        }

        private int[] dimensions(int index, RecipeRow row) {
            HandlerInfo info = GuiRecipeTab.getHandlerInfo(handler);
            int at = magic == null && ae == null && registry == null ? index : 0;
            int width = info.getWidth(), height = handler.getRecipeHeight(at);
            // NEIRecipeWidget treats nonpositive per-recipe height as unspecified.
            // Fluid-only layouts have no item slots from which to infer the background bounds.
            if (height <= 0) height = info.getHeight();
            if (registry instanceof CannerRecipes) { width = Math.max(width, 140); height = Math.max(height, CannerRecipes.height(handler)); }
            if (registry instanceof BuildcraftRecipes) { width = Math.max(width,166); height = Math.max(height,96); }
            if (registry instanceof IntegrationRecipes) { width = Math.max(width,166); height = Math.max(height,85); }
            if (registry instanceof RefiningRecipes) { width = Math.max(width,166); height = Math.max(height,65); }
            if (gt != null && gt.ui != null) { width = gt.ui.width(width); height = gt.ui.height(height); }
            for (com.google.gson.JsonElement element : row.elements) {
                JsonObject position = element.getAsJsonObject();
                if (position.has("width")) width = Math.max(width, position.get("x").getAsInt() + position.get("width").getAsInt());
                if (position.has("height")) height = Math.max(height, position.get("y").getAsInt() + position.get("height").getAsInt());
            }
            if (width <= 0 || height <= 0 || width > 2048 || height > 2048) throw new Jobs.Fault("view_limit",
                    "Invalid NEI recipe dimensions: " + width + "x" + height + "; configured=" + info.getWidth() + "x" + info.getHeight());
            return new int[] {width, height};
        }

        @Override public void close() { try { if (gt != null) gt.close(); } finally { handler.arecipes.clear(); } }
    }
}
