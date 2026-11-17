package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.RecipeCatalysts;
import codechicken.nei.recipe.ShapedRecipeHandler;
import codechicken.nei.recipe.ShapelessRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.CaptureAccounting;
import com.github.dcysteine.nesql.exporter.task.CaptureOutcome;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.relauncher.ReflectionHelper;
import gregtech.api.recipe.RecipeCategory;
import gregtech.nei.GTNEIDefaultHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.item.ItemStack;

/** Explicit enumeration adapters. An unsupported handler is an error, never an empty recipe list. */
final class Recipes {
    enum Adapter { CROPS_NH_REFERENCE, USAGE_REFERENCE, FMP_MICROBLOCK, XU_MICROBLOCK, CAL_IMPRINT, SPACE_ACQUISITION, LOOTBAGS, BANNER, EXTRAUTILS_SOUL, AE_WORLD, AE_FACADE, WITCHERY_COOKING, REFERENCE, TOOL_MATERIALS_REFERENCE, FORGE_REGISTRY_REFERENCE, NEI_INFORMATION_REFERENCE, VILLAGER_TRADES_REFERENCE, ASPECT_COMBINATIONS_REFERENCE, ITEM_ASPECTS_REFERENCE, BOTANIA_LEXICON_REFERENCE, MOB_DROPS_REFERENCE, INFERNAL_DROPS_REFERENCE, DRACONIC_REACTOR_REFERENCE, BEE_TREE_REFERENCE, GT_MINERALS_REFERENCE, BARTWORKS_ORES_REFERENCE, QUESTS_REFERENCE, UNDERGROUND_FLUIDS_REFERENCE, NATIVE_INTERACTIVE, STRUCTURE_REFERENCE, BINNIE_GENETICS, BINNIE_LUMBERMILL, BLOOD_RITUAL, SPINNING, DISTILLERY, GT, MAGIC, AE, INSCRIBER, SCRAPBOX, ENCHANTER, VAT, ASSEMBLY, BUILDCRAFT, SAG, SOUL, IC2, IC2_ADVANCED, IC2_LATHE, IC2_CROP, PROJECT_BLUE, BLAST, CANNER, SMELTING, EXTREME, FORESTRY, FORESTRY_ADDONS, MATTER_MANIPULATOR, TINKER, SPACE, CIRCUIT, REFINERY, RAIL, ROLLING, DISABLED, SOLAR, GENDUSTRY_FLUIDS, GENDUSTRY_TEMPLATE, GENDUSTRY_GENETICS, HARVESTCRAFT, QED, BOTANIA_POOL, BOTANIA_STATIC, BOTANIA_BREWERY, IMBUING, BLOOD_ORB_SHAPED, BLOOD_ORB_SHAPELESS, BLOOD_ALCHEMY, BLOOD_CALCINATOR, CREATIVECORE, BOTANIA_RUNIC, BOTANIA_FLOATING, GALAXY_ASSEMBLY, DECAYABLE, BREWING, FIREWORKS, PROJECT_RED, FUEL, CHISEL, LOGISTICS_SOLDERING, FURNACE, SHAPED, SHAPELESS }

    static Adapter adapter(ICraftingHandler handler) {
        if (CropsNHReferences.supports(handler)) return Adapter.CROPS_NH_REFERENCE;
        if (FmpMicroblockRecipes.supports(handler)) return Adapter.FMP_MICROBLOCK;
        if (XuMicroblockRecipes.supports(handler)) return Adapter.XU_MICROBLOCK;
        if (CalImprintRecipes.supports(handler)) return Adapter.CAL_IMPRINT;
        if (SpaceAcquisitionRecipes.supports(handler)) return Adapter.SPACE_ACQUISITION;
        if (AeWorldRecipes.supports(handler)) return Adapter.AE_WORLD;
        if (AeFacadeRecipes.supports(handler)) return Adapter.AE_FACADE;
        if (WitcheryCookingRecipes.supports(handler) || WitcheryCauldronRecipes.supports(handler)
                || WitcheryOvenRecipes.supports(handler)) return Adapter.WITCHERY_COOKING;
        if (WitcherySpinningRecipes.supports(handler)) return Adapter.SPINNING;
        if (SpinningRecipes.supports(handler)) return Adapter.SPINNING;
        if (DistilleryRecipes.supports(handler)) return Adapter.DISTILLERY;
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
        if (Ic2CropBreedingRecipes.supports(handler)) return Adapter.IC2_CROP;
        if (ProjectBlueRecipes.supports(handler)) return Adapter.PROJECT_BLUE;
        if (BlastRecipes.supports(handler)) return Adapter.BLAST;
        if (CannerRecipes.supports(handler)) return Adapter.CANNER;
        if (BuildcraftRecipes.supports(handler)) return Adapter.BUILDCRAFT;
        if (IntegrationRecipes.supports(handler)) return Adapter.BUILDCRAFT;
        if (RefiningRecipes.supports(handler)) return Adapter.BUILDCRAFT;
        if (SmeltingRecipes.supports(handler)) return Adapter.SMELTING;
        if (ExtremeRecipes.supports(handler)) return Adapter.EXTREME;
        if (ForestryRecipes.supports(handler) || SqueezerRecipes.supports(handler) || ForestryRemainingRecipes.supports(handler) || ForestryProcessingRecipes.supports(handler)) return Adapter.FORESTRY;
        if (ForestryAddonsReferences.supports(handler)) return Adapter.FORESTRY_ADDONS;
        if (MatterManipulatorRecipes.supports(handler)) return Adapter.MATTER_MANIPULATOR;
        if (BinnieLumbermillRecipes.supports(handler)) return Adapter.BINNIE_LUMBERMILL;
        if (BinnieGeneticsRecipes.supportsReference(handler)) return Adapter.REFERENCE;
        if (NativeToolMaterialsReferences.supports(handler)) return Adapter.TOOL_MATERIALS_REFERENCE;
        if (ForgeRegistryReferences.supports(handler)) return Adapter.FORGE_REGISTRY_REFERENCE;
        if (NativeInformationReferences.supports(handler)) return Adapter.NEI_INFORMATION_REFERENCE;
        if (NativeInteractiveReferences.supports(handler)) return Adapter.NATIVE_INTERACTIVE;
        if (InfernalDropsReferences.supports(handler)) return Adapter.INFERNAL_DROPS_REFERENCE;
        if (DraconicReactorReferences.supports(handler)) return Adapter.DRACONIC_REACTOR_REFERENCE;
        if (BartworksOreReferences.supports(handler)) return Adapter.BARTWORKS_ORES_REFERENCE;
        if (BetterQuestingReferences.supports(handler)) return Adapter.QUESTS_REFERENCE;
        if (GtMineralReferences.supports(handler)) return Adapter.GT_MINERALS_REFERENCE;
        if (UndergroundFluidReferences.supports(handler)) return Adapter.UNDERGROUND_FLUIDS_REFERENCE;
        if (BeeTreeReferences.supports(handler)) return Adapter.BEE_TREE_REFERENCE;
        if (MobDropsReferences.supports(handler)) return Adapter.MOB_DROPS_REFERENCE;
        if (BotaniaLexiconReferences.supports(handler)) return Adapter.BOTANIA_LEXICON_REFERENCE;
        if (ItemAspectReferences.supports(handler)) return Adapter.ITEM_ASPECTS_REFERENCE;
        if (AspectCombinationReferences.supports(handler)) return Adapter.ASPECT_COMBINATIONS_REFERENCE;
        if (VillagerTradeReferences.supports(handler)) return Adapter.VILLAGER_TRADES_REFERENCE;
        if (StructureReferences.supports(handler)) return Adapter.STRUCTURE_REFERENCE;
        if (BinnieGeneticsRecipes.supports(handler)) return Adapter.BINNIE_GENETICS;
        if (BloodRitualRecipes.supports(handler)) return Adapter.BLOOD_RITUAL;
        if (TinkerRecipes.supports(handler)) return Adapter.TINKER;
        if (SpaceRecipes.supports(handler)) return Adapter.SPACE;
        if (CircuitRecipes.supports(handler)) return Adapter.CIRCUIT;
        if (RefineryRecipes.supports(handler)) return Adapter.REFINERY;
        if (RailRecipes.supports(handler)) return Adapter.RAIL;
        if (RollingRecipes.supports(handler)) return Adapter.ROLLING;
        if (DisabledRecipes.supports(handler)) return Adapter.DISABLED;
        if (SolarRecipes.supports(handler)) return Adapter.SOLAR;
        if (LootBagRecipes.supports(handler)) return Adapter.LOOTBAGS;
        if (BannerRecipes.supports(handler)) return Adapter.BANNER;
        if (ExtraUtilsSoulRecipes.supports(handler)) return Adapter.EXTRAUTILS_SOUL;
        if (GendustryTemplateRecipes.supports(handler)) return Adapter.GENDUSTRY_TEMPLATE;
        if (GendustryGeneticsRecipes.supports(handler)) return Adapter.GENDUSTRY_GENETICS;
        if (HarvestCraftRecipes.supports(handler)) return Adapter.HARVESTCRAFT;
        if (GendustryFluidRecipes.supports(handler)) return Adapter.GENDUSTRY_FLUIDS;
        if (QedRecipes.supports(handler)) return Adapter.QED;
        if (BotaniaPoolRecipes.supports(handler)) return Adapter.BOTANIA_POOL;
        if (BotaniaWorldRecipes.supportsClass(handler.getClass().getName())) return handler.getClass().getName().endsWith("Brewery")?Adapter.BOTANIA_BREWERY:Adapter.BOTANIA_STATIC;
        if (ImbuingRecipes.supports(handler)) return Adapter.IMBUING;
        if (BloodOrbShapedRecipes.supports(handler)) return Adapter.BLOOD_ORB_SHAPED;
        if (BloodOrbShapelessRecipes.supports(handler)) return Adapter.BLOOD_ORB_SHAPELESS;
        if (BloodAlchemyRecipes.supports(handler)) return Adapter.BLOOD_ALCHEMY;
        if (BloodCalcinatorRecipes.supports(handler)) return Adapter.BLOOD_CALCINATOR;
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
        if (LogisticsPipesRecipes.supports(handler)) return Adapter.LOGISTICS_SOLDERING;
        if (handler.getClass() == FurnaceRecipeHandler.class) return Adapter.FURNACE;
        if (handler.getClass() == ShapedRecipeHandler.class) return Adapter.SHAPED;
        if (handler.getClass() == ShapelessRecipeHandler.class) return Adapter.SHAPELESS;
        return null;
    }

    static List<Handler> handlers() {
        return handlers(GuiCraftingRecipe.craftinghandlers, GuiCraftingRecipe.serialCraftingHandlers,
                GuiUsageRecipe.usagehandlers, GuiUsageRecipe.serialUsageHandlers);
    }

    static List<Handler> handlers(List<? extends IRecipeHandler> crafting, List<? extends IRecipeHandler> serialCrafting,
                                  List<? extends IRecipeHandler> usage, List<? extends IRecipeHandler> serialUsage) {
        Map<String, Handler> result = new LinkedHashMap<>();
        register(result, "crafting", crafting);
        register(result, "serialCrafting", serialCrafting);
        register(result, "usage", usage);
        register(result, "serialUsage", serialUsage);
        return new ArrayList<>(result.values());
    }

    private static void register(Map<String, Handler> result, String registry, List<? extends IRecipeHandler> handlers) {
        List<? extends IRecipeHandler> snapshot;
        synchronized (handlers) { snapshot = new ArrayList<>(handlers); }
        for (IRecipeHandler handler : snapshot) {
            if (handler == null) throw new Jobs.Fault("handler_identity", "Null NEI registration in " + registry);
            Handler entry = new Handler(handler, result.size());
            Handler previous = result.putIfAbsent(entry.id, entry);
            if (previous != null) {
                IRecipeHandler prior = previous.registrationInstances.get(registry);
                if (prior != null && prior != handler)
                    throw new Jobs.Fault("handler_conflict", "Different NEI handler instances share an identity in " + registry + ": " + entry.id);
                previous.verifyCompatible(entry, registry);
                // NEI can register separate crafting and usage instances of one handler.
                // Retain the crafting prototype, and expose every discovery source.
                previous.registrations.add(registry);
                previous.registrationInstances.put(registry, handler);
            } else {
                entry.registrations.add(registry);
                entry.registrationInstances.put(registry, handler);
            }
        }
    }

    static final class Handler {
        final ICraftingHandler prototype;
        final IRecipeHandler nativeHandler;
        final Set<String> registrations = new java.util.LinkedHashSet<>();
        private final Map<String, IRecipeHandler> registrationInstances = new LinkedHashMap<>();
        final JsonObject origin;
        final String id, name;
        final boolean supported;
        final Adapter adapter;
        final int order;
        private final RegistrationBindings bindings;

        Handler(IRecipeHandler handler, int order) {
            nativeHandler = handler;
            prototype = handler instanceof ICraftingHandler ? (ICraftingHandler) handler : null;
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
            adapter = NativeUsageReferences.supports(handler) ? Adapter.USAGE_REFERENCE : prototype == null ? null : adapter(prototype);
            supported = adapter != null;
            bindings = RegistrationBindings.read(handler);
        }

        private void verifyCompatible(Handler later, String registration) {
            if (nativeHandler == later.nativeHandler) return;
            if (bindings != null && !bindings.same(later.bindings))
                throw new Jobs.Fault("handler_conflict", "Different native semantic bindings share an identity across "
                        + registrations + " and " + registration + ": " + id);
        }

        private void verifyRegistrations() {
            if (bindings == null) return;
            if (!bindings.same(RegistrationBindings.read(nativeHandler)))
                throw new Jobs.Fault("environment_changed", "Primary native registration semantic source changed: " + id);
            for (IRecipeHandler instance : registrationInstances.values())
                if (!bindings.same(RegistrationBindings.read(instance)))
                    throw new Jobs.Fault("environment_changed", "Native registration semantic source changed: " + id);
        }

        private String registrationEquivalence() {
            for (IRecipeHandler instance : registrationInstances.values())
                if (instance != nativeHandler) return bindings == null ? "unverified" : "shared-native-bindings";
            return "same-instance";
        }

        JsonObject describe() {
            verifyRegistrations();
            String reason = null;
            if (!supported) {
                if (nativeHandler.getClass().getName().contains("ProfilerRecipeHandler")) {
                    reason = "Excluded non-gameplay profiling utility";
                } else {
                    reason = prototype == null ? "Native usage-only handler requires a dedicated semantic reader" : "No production recipe adapter implemented";
                }
            }
            JsonArray sources = array();
            for (String registration : registrations) sources.add(value(registration));
            return object("id", id, "name", name, "source", origin, "supported", supported, "reason", reason,
                    "registrations", sources, "registrationEquivalence", registrationEquivalence());
        }

        Cursor open(Facts facts) {
            verifyRegistrations();
            if (!supported) throw new Jobs.Fault("handler_unsupported", "No recipe adapter for " + nativeHandler.getClass().getName() + " (" + name + ")");
            if (adapter == Adapter.USAGE_REFERENCE)
                return openReferences(facts,new NativeUsageReferences(nativeHandler,origin,facts.locale()));
            if (adapter == Adapter.AE_WORLD) {
                return openRegistry(facts, new AeWorldRecipes(prototype));
            }
            if (adapter == Adapter.FORESTRY_ADDONS) {
                return openReferences(facts, new ForestryAddonsReferences(prototype, origin));
            }
            if (adapter == Adapter.NATIVE_INTERACTIVE) {
                return openReferences(facts, new NativeInteractiveReferences(prototype, origin));
            }
            if (adapter == Adapter.INFERNAL_DROPS_REFERENCE) {
                return openReferences(facts, new InfernalDropsReferences(prototype, origin));
            }
            if (adapter == Adapter.DRACONIC_REACTOR_REFERENCE) {
                return openReferences(facts, new DraconicReactorReferences(prototype, origin));
            }
            if (adapter == Adapter.UNDERGROUND_FLUIDS_REFERENCE) {
                return openReferences(facts, new UndergroundFluidReferences(prototype, origin));
            }
            if (adapter == Adapter.GT_MINERALS_REFERENCE) {
                return openReferences(facts, new GtMineralReferences(prototype, origin));
            }
            if (adapter == Adapter.BARTWORKS_ORES_REFERENCE) {
                return openReferences(facts, new BartworksOreReferences(prototype, origin));
            }
            if (adapter == Adapter.QUESTS_REFERENCE) {
                return openReferences(facts, new BetterQuestingReferences(prototype, origin));
            }
            if (adapter == Adapter.STRUCTURE_REFERENCE) {
                return openReferences(facts, new StructureReferences(prototype, origin));
            }
            if (adapter == Adapter.FUEL) {
                return openReferences(facts, new FuelRecipes(prototype, origin));
            }
            if (adapter == Adapter.FIREWORKS) {
                return openReferences(facts, new FireworkRecipes(prototype, origin));
            }
            if (adapter == Adapter.IC2_LATHE) {
                return openReferences(facts, new Ic2LatheRecipes(prototype, origin));
            }
            if (adapter == Adapter.PROJECT_BLUE) {
                return openReferences(facts, new ProjectBlueRecipes(prototype, origin));
            }
            if(adapter==Adapter.IC2_ADVANCED)return openRegistry(facts,new Ic2AdvancedRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.FMP_MICROBLOCK)return openRegistry(facts,new FmpMicroblockRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.XU_MICROBLOCK)return openRegistry(facts,new XuMicroblockRecipes((TemplateRecipeHandler)prototype));
            if (adapter == Adapter.BEE_TREE_REFERENCE) {
                return openReferences(facts, new BeeTreeReferences(prototype, origin));
            }
            if (adapter == Adapter.MOB_DROPS_REFERENCE) {
                return openReferences(facts, new MobDropsReferences(prototype, origin));
            }
            if (adapter == Adapter.BOTANIA_LEXICON_REFERENCE) {
                return openReferences(facts, new BotaniaLexiconReferences(prototype, origin));
            }
            if (adapter == Adapter.ITEM_ASPECTS_REFERENCE) {
                return openReferences(facts, new ItemAspectReferences(prototype, origin));
            }
            if (adapter == Adapter.ASPECT_COMBINATIONS_REFERENCE) {
                return openReferences(facts, new AspectCombinationReferences(prototype, origin));
            }
            if (adapter == Adapter.CROPS_NH_REFERENCE) {
                return openReferences(facts, new CropsNHReferences(prototype, origin));
            }
            if (adapter == Adapter.VILLAGER_TRADES_REFERENCE) {
                return openReferences(facts, new VillagerTradeReferences(prototype, origin));
            }
            if (adapter == Adapter.NEI_INFORMATION_REFERENCE) {
                return openReferences(facts, new NativeInformationReferences(prototype, origin));
            }
            if (adapter == Adapter.FORGE_REGISTRY_REFERENCE) {
                return openReferences(facts, new ForgeRegistryReferences(prototype, origin));
            }
            if (adapter == Adapter.GT) {
                return openRegistry(facts, new GtRecipes((GTNEIDefaultHandler) prototype));
            }
            if (adapter == Adapter.VAT) {
                return openRegistry(facts, new VatRecipes((TemplateRecipeHandler) prototype));
            }
            if (adapter == Adapter.ASSEMBLY) {
                return openRegistry(facts, new EnderAssemblyRecipes((TemplateRecipeHandler) prototype));
            }
            if (adapter == Adapter.SPACE) {
                return openRegistry(facts, new SpaceRecipes((TemplateRecipeHandler) prototype));
            }
            if (adapter == Adapter.FURNACE) {
                return openRegistry(facts, new VanillaFurnaceRecipes());
            }
            if (adapter == Adapter.REFINERY) {
                return openRegistry(facts, new RefineryRecipes((TemplateRecipeHandler) prototype));
            }
            if (adapter == Adapter.SHAPED || adapter == Adapter.SHAPELESS) {
                return openRegistry(facts, new NativeCraftingRecipes(adapter == Adapter.SHAPED));
            }
            if(adapter==Adapter.CANNER)return openRegistry(facts,new CannerRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.IC2)return openRegistry(facts,new Ic2Recipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.BLAST)return openRegistry(facts,new BlastRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.BUILDCRAFT&&RefiningRecipes.supports(prototype))return openRegistry(facts,new RefiningRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.BUILDCRAFT&&BuildcraftRecipes.supports(prototype))return openRegistry(facts,new BuildcraftRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.BUILDCRAFT&&IntegrationRecipes.supports(prototype))return openRegistry(facts,new IntegrationRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.RAIL)return openRegistry(facts,new RailRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.CAL_IMPRINT)return openRegistry(facts,new CalImprintRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.AE_FACADE)return openRegistry(facts,new AeFacadeRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.SPACE_ACQUISITION)return openRegistry(facts,new SpaceAcquisitionRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.LOOTBAGS)return openRegistry(facts,new LootBagRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.ROLLING)return openRegistry(facts,new RollingRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.EXTREME)return openRegistry(facts,new ExtremeRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.QED)return openRegistry(facts,new QedRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.GALAXY_ASSEMBLY)return openRegistry(facts,new GalaxyAssemblyRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.BANNER)return openRegistry(facts,new BannerRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.EXTRAUTILS_SOUL)return openRegistry(facts,new ExtraUtilsSoulRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.DECAYABLE)return openRegistry(facts,new DecayableRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.SOLAR)return openRegistry(facts,new SolarRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.CIRCUIT)return openRegistry(facts,new CircuitRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.CHISEL)return openRegistry(facts,new ChiselRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.DISTILLERY)return openRegistry(facts,new DistilleryRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.CREATIVECORE)return openRegistry(facts,new CreativeCoreRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.ENCHANTER)return openRegistry(facts,new EnderEnchanterRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.BINNIE_LUMBERMILL)return openRegistry(facts,new BinnieLumbermillRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.BLOOD_RITUAL)return openRegistry(facts,new BloodRitualRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.BINNIE_GENETICS)return openRegistry(facts,new BinnieGeneticsRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.REFERENCE)return openReferences(facts,new NativeGeneticsReferences((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.AE)return openRegistry(facts,new AeRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.INSCRIBER)return openRegistry(facts,new InscriberRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.BOTANIA_POOL)return openRegistry(facts,new BotaniaPoolRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.BOTANIA_RUNIC)return openRegistry(facts,new BotaniaRunicRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.BOTANIA_FLOATING)return openRegistry(facts,new BotaniaFloatingRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.GENDUSTRY_FLUIDS)return openRegistry(facts,new GendustryFluidRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.GENDUSTRY_TEMPLATE)return openRegistry(facts,new GendustryTemplateRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.GENDUSTRY_GENETICS)return openRegistry(facts,new GendustryGeneticsRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.MAGIC)return openRegistry(facts,new MagicRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.FORESTRY&&ForestryProcessingRecipes.supports(prototype))return openRegistry(facts,new ForestryProcessingRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.FORESTRY&&SqueezerRecipes.supports(prototype))return openRegistry(facts,new SqueezerRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.FORESTRY&&ForestryRemainingRecipes.supports(prototype))return openRegistry(facts,new ForestryRemainingRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.FORESTRY&&ForestryRecipes.supports(prototype))return openRegistry(facts,new ForestryRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.WITCHERY_COOKING)return openRegistry(facts,WitcheryCookingRecipes.supports(prototype)?new WitcheryCookingRecipes((TemplateRecipeHandler)prototype):WitcheryCauldronRecipes.supports(prototype)?new WitcheryCauldronRecipes((TemplateRecipeHandler)prototype):new WitcheryOvenRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.SPINNING)return openRegistry(facts,WitcherySpinningRecipes.supports(prototype)?new WitcherySpinningRecipes((TemplateRecipeHandler)prototype):new SpinningRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.SAG)return openRegistry(facts,new SagRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.SOUL)return openRegistry(facts,new SoulRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.SCRAPBOX)return openRegistry(facts,new ScrapboxRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.TINKER)return openRegistry(facts,new TinkerRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.MATTER_MANIPULATOR)return openRegistry(facts,new MatterManipulatorRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.HARVESTCRAFT)return openRegistry(facts,new HarvestCraftRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.IMBUING)return openRegistry(facts,new ImbuingRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.BLOOD_ORB_SHAPED)return openRegistry(facts,new BloodOrbShapedRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.BLOOD_ORB_SHAPELESS)return openRegistry(facts,new BloodOrbShapelessRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.PROJECT_RED)return openRegistry(facts,new ProjectRedRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.BLOOD_CALCINATOR)return openRegistry(facts,new BloodCalcinatorRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.BREWING)return openRegistry(facts,new BrewingRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.BLOOD_ALCHEMY)return openRegistry(facts,new BloodAlchemyRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.LOGISTICS_SOLDERING)return openRegistry(facts,new LogisticsPipesRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.BOTANIA_STATIC||adapter==Adapter.BOTANIA_BREWERY)return openRegistry(facts,new BotaniaWorldRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.TOOL_MATERIALS_REFERENCE)return openReferences(facts,new NativeToolMaterialsReferences((TemplateRecipeHandler)prototype,origin));
            if(adapter==Adapter.NEI_INFORMATION_REFERENCE)return openReferences(facts,new NativeInformationReferences(prototype,origin));
            if(adapter==Adapter.IC2_CROP)return openRegistry(facts,new Ic2CropBreedingRecipes((TemplateRecipeHandler)prototype));
            if(adapter==Adapter.SMELTING)return openRegistry(facts,new SmeltingRecipes((FurnaceRecipeHandler)prototype));
            if(adapter==Adapter.DISABLED)return openRegistry(facts,new DisabledRecipes((TemplateRecipeHandler)prototype));
            throw new Jobs.Fault("handler_unsupported", "No native semantic reader for: " + prototype.getClass().getName());
        }

        /** Adopt a registry after its category callbacks succeed; the native prototype is borrowed. */
        Cursor openRegistry(Facts facts, RegistryRecipes registry) {
            try {
                category(prototype, facts, registry, null);
                return new Cursor(this, facts, registry, null);
            } catch (RuntimeException | Error failure) {
                try { registry.close(); }
                catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        /** Adopt a native reader only after category callbacks and cursor construction succeed. */
        Cursor openReferences(Facts facts, NativeReferences references) {
            try {
                category(nativeHandler, facts, null, references);
                return new Cursor(this, facts, null, references);
            } catch (RuntimeException | Error failure) {
                try { references.close(); }
                catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        private void category(IRecipeHandler handler, Facts facts, RegistryRecipes registry,
                              NativeReferences references) {
                HandlerInfo info = GuiRecipeTab.getHandlerInfo(handler);
                JsonObject icon = info.getItemStack() == null ? null : object("kind", "item", "id", facts.item(info.getItemStack()));
                JsonArray machines = new JsonArray();
                Set<String> machineIds = new HashSet<>();
                for (PositionedStack catalyst : RecipeCatalysts.getRecipeCatalysts(handler)) for (ItemStack item : catalyst.items) {
                    String id = facts.item(item);
                    if (machineIds.add(id)) machines.add(object("kind", "item", "id", id));
                }
                JsonObject category = object("id", id, "source", origin, "name", facts.text(name),
                        "icon", icon, "machines", machines, "order", order);
                String program = registry == null ? null : registry.program(facts);
                if (program != null) category.addProperty("program", program);
                if (references != null) category.addProperty("reference", references.domain());
                facts.row("categories", category);
        }
    }

    static final class Cursor implements AutoCloseable {
        private final Handler source;
        private Facts facts;
        private RegistryRecipes registry;
        private NativeReferences references;
        private boolean closed;
        private final Set<Attempt> pending = Collections.newSetFromMap(new IdentityHashMap<Attempt, Boolean>());
        private final Map<String, Origin> recipes = new LinkedHashMap<>();
        private final JsonObject exclusions = new JsonObject();
        private final CaptureAccounting accounting = new CaptureAccounting();
        private int exclusionPreviewBytes, exclusionsOmitted;

        Cursor(
                Handler source,
                Facts facts,
                RegistryRecipes registry,
                NativeReferences references) {
            this.source = source;
            this.facts = facts;
            this.registry = registry;
            this.references = references;
            if (registry != null) {
                JsonObject proof = registry.handlerExclusion();
                if (proof != null)
                    exclusions.add(
                            "handler", new com.google.gson.JsonParser().parse(proof.toString()));
            }
        }

        int size() {
            requireOpen();
            return references != null
                    ? references.size()
                    : registry.size();
        }

        void verify() {
            requireOpen();
            source.verifyRegistrations();
            if (references != null) references.verify();
            else if (registry != null) registry.verify();
        }

        void finishVerification() {
            requireOpen();
            source.verifyRegistrations();
            if (references != null) references.finishVerification();
            else if (registry != null) registry.finishVerification();
        }

        boolean reference() {
            requireOpen();
            return references != null;
        }

        String recordKind() {
            return reference() ? "references" : "recipes";
        }

        JsonObject referenceCounts() {
            requireOpen();
            return references == null ? null : references.counts();
        }

        java.util.List<Structures.Machine> structures(int start, int end) {
            requireOpen();
            return references == null
                    ? java.util.Collections.emptyList()
                    : references.structures(start, end);
        }

        JsonObject exclusions() {
            JsonObject result =
                    new com.google.gson.JsonParser().parse(exclusions.toString()).getAsJsonObject();
            if (exclusionsOmitted != 0)
                result.add(
                        "preview",
                        object("omitted", exclusionsOmitted, "accounting", accounting.json()));
            return result;
        }

        CaptureOutcome capture(int index) {
            return capture(index, facts);
        }

        CaptureOutcome capture(int index, Facts facts) {
            try (Attempt attempt = attempt(index, facts)) {
                return attempt.commit();
            }
        }

        /** Staged checks validate detached JSON before reserving IDs or publishing facts. */
        Attempt attempt(int index, Facts facts) {
            requireOpen();
            Attempt attempt = new Attempt(index, facts);
            try {
                captureRow(index, facts, attempt);
                return attempt;
            } catch (java.util.concurrent.CancellationException error) {
                throw error;
            } catch (RuntimeException error) {
                Jobs.Fault failure =
                        new Jobs.Fault(
                                error instanceof Jobs.Fault
                                        ? ((Jobs.Fault) error).code
                                        : "recipe_capture",
                                "Recipe handler '"
                                        + source.name
                                        + "'; category="
                                        + source.id
                                        + "; index="
                                        + index
                                        + ": "
                                        + error);
                failure.initCause(error);
                throw failure;
            } finally {
                if (!attempt.ready) attempt.close();
            }
        }

        private void captureRow(int index, Facts facts, Attempt attempt) {
            Jobs.checkpoint();
            if (references != null) {
                references.capture(index, facts);
                if (attempt.transaction.records("references") != 1
                        || attempt.transaction.records("recipes") != 0)
                    throw new Jobs.Fault(
                            "reference_capture",
                            "Native reference entry must emit exactly one reference");
                attempt.reference = true;
                attempt.ready = true;
                return;
            }
            RecipeRow row = new RecipeRow(facts, source.origin, source.id, index);
            if (registry != null) {
                java.util.List<RecipeRow> captured = registry.captureRows(index, row);
                if (captured.isEmpty()) {
                    JsonObject proof = registry.exclusion(index);
                    attempt.exclude(proof == null ? row.exclusion : proof);
                    return;
                }
                attempt.emit(captured);
                return;
            } else {
                throw new Jobs.Fault("handler_unsupported", "Native recipe reader is missing: " + source.name);
            }
        }

        JsonObject accounting() {
            return accounting.json();
        }

        final class Attempt implements AutoCloseable {
            private final int index;
            private final Facts facts;
            private final Facts.Transaction transaction;
            private final List<Origin> origins = new ArrayList<>();
            private JsonObject proof;
            private boolean ready, reference, done;

            private Attempt(int index, Facts facts) {
                this.index = index;
                this.facts = facts;
                transaction = facts.begin();
                pending.add(this);
            }

            private void exclude(JsonObject proof) {
                this.proof = CaptureOutcome.excluded(proof).json().getAsJsonObject("proof");
                ready = true;
            }

            private void emit(List<RecipeRow> branches) {
                if (branches == null || branches.isEmpty() || branches.size() > 65536)
                    throw new Jobs.Fault(
                            "capture_empty", "Native recipe branch set is empty without a proof");
                Map<String, Origin> local = new LinkedHashMap<>();
                for (RecipeRow row : branches) {
                    if (row == null || row.facts != facts || row.exclusion != null)
                        throw new Jobs.Fault(
                                "capture_owner",
                                "Native branch does not own this facts transaction");
                    row.finish();
                    JsonObject semantic = new JsonObject();
                    row.record.entrySet().stream()
                            .filter(
                                    entry ->
                                            !entry.getKey().equals("id")
                                                    && !entry.getKey().equals("order"))
                            .forEach(entry -> semantic.add(entry.getKey(), entry.getValue()));
                    Origin origin =
                            new Origin(
                                    row.record.get("id").getAsString(),
                                    CanonicalJson.digest(semantic),
                                    index,
                                    origins.size());
                    origins.add(origin);
                    Origin prior =
                            local.containsKey(origin.id)
                                    ? local.get(origin.id)
                                    : recipes.get(origin.id);
                    if (prior != null) same(prior, origin);
                    else {
                        local.put(origin.id, origin);
                        // Content icons are independent; no native display/GUI is requested.
                        facts.row("recipes", row.record);
                    }
                }
                ready = true;
            }

            CaptureOutcome commit() {
                requireOpen();
                if (!ready || done)
                    throw new IllegalStateException("Native attempt is not pending");
                Map<String, Origin> additions = new LinkedHashMap<>();
                JsonArray duplicates = new JsonArray();
                CaptureOutcome outcome;
                if (proof != null) {
                    outcome = CaptureOutcome.excluded(proof);
                } else if (reference) outcome = CaptureOutcome.reference();
                else {
                    for (Origin origin : origins) {
                        Origin first =
                                recipes.containsKey(origin.id)
                                        ? recipes.get(origin.id)
                                        : additions.get(origin.id);
                        if (first == null) additions.put(origin.id, origin);
                        else {
                            same(first, origin);
                            duplicates.add(
                                    object(
                                            "recipe",
                                            origin.id,
                                            "firstIndex",
                                            first.index,
                                            "firstBranch",
                                            first.branch,
                                            "branch",
                                            origin.branch));
                        }
                    }
                    outcome = CaptureOutcome.recipes(additions.size(), duplicates);
                }
                if (outcome.kind == CaptureOutcome.Kind.EXCLUDED
                        || outcome.kind == CaptureOutcome.Kind.DUPLICATE) transaction.close();
                else transaction.commit();
                recipes.putAll(additions);
                if (proof != null) {
                    int bytes = CanonicalJson.bytes(proof).length;
                    if (exclusions.entrySet().size() < 16
                            && exclusionPreviewBytes + bytes <= 8192) {
                        exclusions.add(Integer.toString(index), outcome.json().get("proof"));
                        exclusionPreviewBytes += bytes;
                    } else exclusionsOmitted++;
                }
                accounting.append(index, outcome);
                done = true;
                pending.remove(this);
                return outcome;
            }

            @Override
            public void close() {
                try {
                    if (!done) transaction.close();
                } finally {
                    done = true;
                    origins.clear();
                    pending.remove(this);
                }
            }
        }

        private static final class Origin {
            final String id, signature;
            final int index, branch;

            Origin(String id, String signature, int index, int branch) {
                this.id = id;
                this.signature = signature;
                this.index = index;
                this.branch = branch;
            }
        }

        private static void same(Origin first, Origin later) {
            if (!first.signature.equals(later.signature))
                throw new Jobs.Fault(
                        "recipe_identity_conflict",
                        "Equal recipe identity would discard different native information: "
                                + later.id
                                + "; first="
                                + first.index
                                + "/"
                                + first.branch
                                + "; later="
                                + later.index
                                + "/"
                                + later.branch);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            List<Runnable> cleanups = new ArrayList<>();
            for (Attempt attempt : new ArrayList<>(pending)) cleanups.add(attempt::close);
            if (registry != null) cleanups.add(registry::close);
            if (references != null) cleanups.add(references::close);
            Throwable failure = null;
            try {
                for (Runnable cleanup : cleanups) {
                    try { cleanup.run(); }
                    catch (RuntimeException | Error error) {
                        if (failure == null) failure = error;
                        else if (failure != error) failure.addSuppressed(error);
                    }
                }
            } finally {
                pending.clear();
                recipes.clear();
                facts = null;
                registry = null;
                references = null;
            }
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            if (failure instanceof Error) throw (Error) failure;
        }

        private void requireOpen() {
            if (closed) throw new Jobs.Fault("capture_closed", "Native recipe cursor is closed: " + source.id);
        }
    }
}
