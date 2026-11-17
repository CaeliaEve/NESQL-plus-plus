package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Small conformance suite against the actual, locally remapped IC2 jar. No game is launched. */
public final class NativeMachinesTest {
    public static void main(String[] args) throws Exception { GameTest.run(); }
    public static void run() throws Exception {
        String family = System.getProperty("nesql.nativeFamily", "machines");
        if (family.equals("gt-probability")) { NativeGtProbabilityTest.run(); return; }
        if (family.equals("targeted-return")) { NativeTargetedReturnTest.run(); return; }
        if (family.equals("targeted-containers")) { NativeTargetedReturnTest.containers(); return; }
        if (family.equals("targeted-wireless")) { NativeTargetedSemanticsTest.wireless(); return; }
        if (family.equals("targeted-random-infusion")) { NativeTargetedSemanticsTest.randomCosts(); return; }
        if (family.equals("ari-routing")) { NativeAriRoutingTest.run(); return; }
        if (family.equals("returned-failures")) { NativeReturnedFailuresTest.run(); return; }
        if (family.equals("returned-state")) { NativeReturnedStateTest.run(); return; }
        if (family.equals("returned-oc")) { NativeReturnedOcTest.run(); return; }
        if (family.equals("returned-smelting")) { NativeReturnedSmeltingTest.run(); return; }
        if (family.equals("returned-structures")) { NativeReturnedStructureTest.run(); return; }
        if (family.equals("returned-diagram")) { NativeReturnedDiagramTest.run(); return; }
        if (family.startsWith("observed-callbacks-")) { NativeObservedCallbacksTest.run(); return; }
        if (family.equals("gt-construction")) { NativeGtConstructionTest.run(); return; }
        if (family.equals("botania-structure")) { NativeBotaniaStructureTest.run(); return; }
        if (family.equals("magicbees-pool-law")) { NativeMagicBeesPoolLawTest.run(); return; }
        if (family.equals("creativecore")) { NativeCreativeCoreTest.run(); return; }
        if (family.equals("botania-special")) { NativeBotaniaSpecialTest.run(); return; }
        if (family.startsWith("forestry-container-")) { NativeSqueezerFamiliesTest.run(); return; }
        if (family.equals("genetics-budgets")) { NativeGeneticsBudgetsTest.run(); return; }
        if (family.equals("binnie-witness")) { BinnieWitnessTest.run(); return; }
        if (family.equals("runtime-boundaries")) { NativeRuntimeBoundariesTest.run(); return; }
        if (family.equals("harvest-storage")) { NativeHarvestStorageTest.run(); return; }
        if (family.equals("infusion-storage")) { NativeInfusionStorageTest.run(); return; }
        if (family.equals("binnie-zero")) { NativeBinnieZeroTest.run(); return; }
        if (family.equals("fireworks-fixed")) { FireworkReferencesTest.fixed(); return; }
        if (family.equals("fireworks-vanilla")) { FireworkReferencesTest.run(); return; }
        if (family.equals("usage-queries")) { NativeUsageTest.run(); return; }
        if (family.equals("ore-storage")) { NativeOreStorageTest.run(); return; }
        if (family.equals("ore-alternatives")) { NativeOreAlternativesTest.run(); return; }
        if (family.equals("ae-world-ore")) { NativeAeRemainingTest.worldOreStorageOnly(); return; }
        if (family.equals("native-crafting")) { NativeCraftingTest.run(); return; }
        if (family.equals("native-crafting-forestry")) { NativeForestryCraftingTest.run(); return; }
        if (family.equals("matter-manipulator")) { NativeMatterManipulatorTest.run(); return; }
        if (family.equals("projectblue-materials")) { NativeProjectBlueTest.materialsOnly(); return; }
        if (family.equals("projectblue")) { NativeProjectBlueTest.run(); return; }
        if (family.equals("ic2-advanced")) { NativeIc2AdvancedTest.run(); return; }
        if (family.equals("ic2-advanced-geometry")) { NativeIc2AdvancedTest.geometryOnly(); return; }
        if (family.equals("forge-registries")) { NativeForgeRegistryTest.run(); return; }
        if (family.equals("fuel-containers")) { NativeFuelContainersTest.run(); return; }
        if (family.equals("gt-crafting-declarations")) { NativeGtCraftingTest.run(); return; }
        if (family.equals("vanilla-furnace")) { NativeVanillaFurnaceTest.run(); return; }
        if (family.equals("enderstorage-state")) { EnderStorageSnapshotTest.run(); return; }
        if (family.equals("nei-diagrams") || family.equals("nei-diagram-generators")) { NativeDiagramTest.run(); return; }
        if (family.equals("nei-information")) { InformationReferencesTest.run(); return; }
        if (family.equals("lathe")) { LatheReferencesTest.run(); return; }
        if (family.equals("forestry-addons")) { ForestryAddonsTest.run(); return; }
        if (family.equals("betterquesting")) { BetterQuestingTest.run(); return; }
        if (family.equals("bartworks-ores")) { BartworksOreTest.run(); return; }
        if (family.equals("gt-minerals")) { GtMineralTest.run(); return; }
        if (family.equals("underground-fluids")) { UndergroundFluidTest.run(); return; }
        if (family.equals("bee-tree")) { BeeTreeTest.run(); return; }
        if (family.equals("draconic-reactor")) { DraconicReactorTest.run(); return; }
        if (family.equals("infernal-drops")) { InfernalDropsTest.run(); return; }
        if (family.equals("mob-drops")) { MobDropsTest.run(); return; }
        if (family.equals("item-aspects")) { ItemAspectReferencesTest.run(); return; }
        if (family.equals("botania-lexicon")) { BotaniaLexiconTest.run(); return; }
        if (family.equals("aspect-combinations")) { AspectCombinationsTest.run(); return; }
        if (family.equals("semantic-recipes")) { SemanticRecipeTest.run(); return; }
        if (family.equals("gt-semantic")) { NativeGtSemanticTest.run(); return; }
        if (family.equals("crop-prediction")) { CropPredictionTest.run(); return; }
        if (family.equals("villager-trades")) { VillagerTradesTest.run(); return; }
        if (family.equals("handler-discovery")) { HandlerDiscoveryTest.run(); return; }
        if (family.equals("microblocks")) { NativeMicroblockTest.run(); return; }
        if (family.equals("cal-imprint")) { NativeCalImprintTest.run(); return; }
        if (family.equals("space-acquisition")) { NativeSpaceAcquisitionTest.run(); return; }
        if (family.equals("banner")) { NativeBannerTest.run(); return; }
        if (family.equals("lootbags")) { NativeLootBagTest.run(); return; }
        if (family.equals("extrautils-soul")) { NativeExtraUtilsSoulTest.run(); return; }
        if (family.equals("ae-remaining")) { NativeAeRemainingTest.run(); return; }
        if (family.equals("witchery-cooking")) { NativeWitcheryCookingTest.run(); return; }
        if (family.equals("gendustry-native")) { NativeGendustryTest.run(); return; }
        if (family.equals("gendustry-conditions")) { NativeGendustryConditionsTest.run(); return; }
        if (family.equals("binnie-genetics")) { NativeBinnieGeneticsTest.run(); return; }
        if (family.equals("forestry-processing")) { NativeForestryProcessingTest.run(); return; }
        if (family.equals("harvestcraft")) { NativeHarvestCraftTest.run(); return; }
        if (family.equals("registry-views")) { RegistryViewTest.run(); return; }
        if (family.equals("tool-materials")) { NativeToolMaterialsTest.run(); return; }
        if (family.startsWith("forestry-remaining-")) { NativeForestryRemainingTest.run(); return; }
        if (family.equals("bloodritual")) { NativeBloodRitualTest.run(); return; }
        if (family.equals("binnie-lumbermill")) { NativeBinnieLumbermillTest.run(); return; }
        if (family.equals("chisel")) { NativeChiselTest.run(); return; }
        if (family.equals("logistics-soldering")) { NativeLogisticsPipesTest.run(); return; }
        if (family.equals("blood-alchemy")) { NativeBloodAlchemyTest.run(); return; }
        if (family.equals("blood-calcinator")) { NativeBloodCalcinatorTest.run(); return; }
        if (family.equals("witchery-spinning")) { NativeWitcherySpinningTest.run(); return; }
        if (family.equals("spinning")) { NativeSpinningTest.run(); return; }
        if (family.equals("distillery")) { NativeDistilleryTest.run(); return; }
        if (family.equals("genetics-roots")) { NativeGeneticsTest.run(); return; }
        if (family.equals("ore-groups")) { NativeOreGroupsTest.run(); return; }
        if (family.equals("galaxy-assembly")) { NativeGalaxyAssemblyTest.run(); return; }
        if (family.equals("qed")) { NativeQedTest.run(); return; }
        if (family.equals("botania-pool")) { NativeBotaniaPoolTest.run(); return; }
        if (family.equals("botania-runic")) { NativeBotaniaRunicTest.run(); return; }
        if (family.equals("botania-floating")) { NativeBotaniaFloatingTest.run(); return; }
        if (family.equals("imbuing")) { NativeImbuingTest.run(); return; }
        if (family.equals("imbuing-json")) { NativeImbuingSerializationTest.run(); return; }
        if (family.equals("rail-wildcard")) { NativeRailWildcardTest.run(); return; }
        if (family.equals("dimensions")) { NativeDimensionsTest.run(); return; }
        if (family.startsWith("fluid-icons")) { NativeFluidIconsTest.run(); return; }
        if (family.equals("core-fixes-casting")) { NativeCastingEventsTest.run(); return; }
        if (family.startsWith("core-fixes")) { NativeCoreFixesTest.run(); return; }
        if (family.equals("forestry-callbacks")) { NativeSqueezerCallbacksTest.run(); return; }
        if (family.equals("forestry-work")) { NativeSqueezerWorkTest.run(); return; }
        if (family.equals("canner")) { NativeCannerTest.run(); return; }
        if (family.equals("blast")) { NativeBlastTest.run(); return; }
        if (family.equals("integration")) { NativeIntegrationTest.run(); return; }
        if (family.equals("compression")) { NativeCompressionTest.run(); return; }
        if (family.equals("buildcraft")) { NativeBuildcraftTest.run(); NativeRefiningTest.run(); return; }
        if (family.equals("soul")) { NativeSoulTest.run(); return; }
        if (family.equals("ender-machines")) { NativeEnderMachinesTest.run(); return; }
        if (family.equals("vat")) { NativeVatTest.run(); return; }
        if (family.equals("enchanter")) { NativeEnchanterTest.run(); return; }
        if (family.equals("scrapbox")) { NativeScrapboxTest.run(); return; }
        if (family.equals("inscriber")) { NativeInscriberTest.run(); return; }
        if (family.equals("wireless")) { NativeAeTest.run(); return; }
        if (family.equals("replaced")) { NativeReplacedTest.run(); return; }
        if (family.equals("runic")) { NativeRunicTest.run(); return; }
        if (family.equals("infusion-tools")) { NativeInfusionToolTest.run(); return; }
        if (family.equals("harmony")) { NativeHarmonyTest.run(); return; }
        if (family.equals("solar")) { NativeSolarTest.run(); return; }
        if (family.equals("railcraft")) { NativeRailTest.run(); return; }
        if (family.equals("extreme")) { extreme(); return; }
        if (family.equals("space")) { NativeSpaceTest.run(); return; }
        if (family.equals("circuits")) { NativeCircuitsTest.run(); return; }
        if (!family.equals("machines")&&!family.equals("ic2-machines")) { NativeFactoriesTest.run(family); return; }
        String prefix = "ic2.neiIntegration.core.recipehandler.";
        require(Recipes.adapter((TemplateRecipeHandler) Class.forName(prefix + "BlockCutterRecipeHandler").newInstance()) != null,
                "Block cutter has no native adapter");
        for (String kind : new String[] {"Macerator", "Extractor", "Compressor", "MetalFormerRecipeHandlerCutting",
                "MetalFormerRecipeHandlerRolling", "MetalFormerRecipeHandlerExtruding", "Centrifuge", "OreWashing", "BlockCutter"}) {
            String name = kind.contains("RecipeHandler") ? kind : kind + "RecipeHandler";
            TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName(prefix + name).newInstance();
            require(Recipes.adapter(handler) != null, "Native IC2 handler has no adapter: " + kind);
        }
        // Blast-furnace inputs include air; it must not inherit a plain one-input adapter.
        require(Recipes.adapter((TemplateRecipeHandler) Class.forName(prefix + "BlastFurnaceRecipeHandler").newInstance()) != Recipes.Adapter.IC2,
                "Blast furnace was incorrectly treated as a plain item machine");
        Class<?> inputType = Class.forName("ic2.api.recipe.RecipeInputItemStack");
        Class<?> oreType = Class.forName("ic2.api.recipe.RecipeInputOreDict");
        Class<?> outputType = Class.forName("ic2.api.recipe.RecipeOutput");
        ItemStack iron = new ItemStack(Items.iron_ingot, 17), gold = new ItemStack(Items.gold_ingot, 9);
        ItemStack output = new ItemStack(Items.diamond, 3), byproduct = new ItemStack(Items.coal, 2);
        Object input = inputType.getConstructor(ItemStack.class, int.class).newInstance(iron, 4);
        ItemStack tagged = iron.copy(); tagged.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("arbitrary"));
        require((Boolean) inputType.getMethod("matches", ItemStack.class).invoke(input, tagged), "Native IC2 must ignore input NBT");
        NBTTagCompound metadata = new NBTTagCompound(); metadata.setInteger("minHeat", 1500);
        Object products = outputType.getConstructor(NBTTagCompound.class, ItemStack[].class)
                .newInstance(metadata, new ItemStack[] {output, byproduct});
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName(prefix + "CentrifugeRecipeHandler").newInstance();
        TemplateRecipeHandler.CachedRecipe borrowed=handler.new CachedRecipe(){@Override public codechicken.nei.PositionedStack getResult(){return null;}};
        handler.arecipes.add(borrowed);
        RecipeRow row = row(iron, gold, output, byproduct);
        capture(handler, input, products, row);
        require(handler.arecipes.size()==1&&handler.arecipes.get(0)==borrowed,"IC2 semantic capture mutated borrowed NEI cache");
        require(row.inputs.size() == 1 && row.outputs.size() == 2, "Native multi-output recipe lost slots");
        com.google.gson.JsonObject choice = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
        require(choice.get("amount").getAsString().equals("4") && choice.getAsJsonObject("rule").get("nbt").getAsBoolean(), "IC2 amount or NBT rule changed");
        require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3")
                && row.outputs.get(1).getAsJsonObject().get("amount").getAsString().equals("2"), "Output counts collapsed");
        require(row.record.get("duration").getAsString().equals("500") && row.record.get("energy").getAsString().equals("48")
                && row.properties.has("ic2:minHeat"), "Centrifuge requirements disappeared");
        OreDictionary.registerOre("nesqlMachineInputs", iron); OreDictionary.registerOre("nesqlMachineInputs", gold);
        Object ore = oreType.getConstructor(String.class, int.class).newInstance("nesqlMachineInputs", 6);
        RecipeRow alternatives = row(iron, gold, output, byproduct);
        capture(handler, ore, products, alternatives);
        require(alternatives.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").size() == 2, "Ore alternatives collapsed");
        require(iron.stackSize == 17 && gold.stackSize == 9 && output.stackSize == 3 && metadata.getInteger("minHeat") == 1500,
                "Native layout mutated source stacks or metadata");
        Object unknown = Proxy.newProxyInstance(inputType.getClassLoader(), new Class<?>[] {Class.forName("ic2.api.recipe.IRecipeInput")},
                (proxy, method, args) -> { throw new AssertionError("Unknown predicate should never execute"); });
        try { capture(handler, unknown, products, row(iron, output)); throw new AssertionError("Unknown native predicate was accepted"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"), "Wrong failure for unknown predicate"); }
        UnknownMetadata unknownMetadata=new UnknownMetadata();
        Item.itemRegistry.addObject(31001,"nesql_fixture:ic2_metadata_probe",unknownMetadata);
        ItemStack probe=new ItemStack(unknownMetadata);
        Object unsafeInput=inputType.getConstructor(ItemStack.class,int.class).newInstance(probe,1);
        unknownMetadata.armed=true;
        try {Ic2Recipes.ingredients(unsafeInput);throw new AssertionError("Unknown IC2 ingredient metadata callback was accepted");}
        catch(Jobs.Fault failure){require(failure.code.equals("recipe_unsupported"),failure.toString());}
        finally {unknownMetadata.armed=false;}
        require(unknownMetadata.calls==0,"IC2 called unknown metadata before rejecting it");
        OreDictionary.registerOre("nesqlUnsafeMetadata",probe);
        Object unsafeOre=oreType.getConstructor(String.class,int.class).newInstance("nesqlUnsafeMetadata",1);
        Field forcedMeta=oreType.getDeclaredField("meta");forcedMeta.setAccessible(true);forcedMeta.set(unsafeOre,Integer.valueOf(0));
        unknownMetadata.armed=true;
        try {Ic2Recipes.ingredients(unsafeOre);throw new AssertionError("Unknown filtered ore metadata callback was accepted");}
        catch(Jobs.Fault failure){require(failure.code.equals("recipe_unsupported"),failure.toString());}
        finally {unknownMetadata.armed=false;}
        require(unknownMetadata.calls==0,"IC2 ore expansion called unknown metadata before rejecting it");
        TemplateRecipeHandler washing = (TemplateRecipeHandler) Class.forName(prefix + "OreWashingRecipeHandler").newInstance();
        NBTTagCompound water = new NBTTagCompound(); water.setInteger("amount", 1000);
        Object washingOutput = outputType.getConstructor(NBTTagCompound.class, ItemStack[].class).newInstance(water, new ItemStack[] {output, byproduct});
        RecipeRow washed = row(iron, output, byproduct); capture(washing, input, washingOutput, washed);
        com.google.gson.JsonObject fluidChoice = washed.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
        require(washed.inputs.get(1).getAsJsonObject().get("kind").getAsString().equals("fluid")
                && fluidChoice.get("id").getAsString().equals(Identity.fluid("water", null))
                && fluidChoice.get("amount").getAsString().equals("1000") && fluidChoice.getAsJsonObject("rule").get("nbt").getAsBoolean(),
                "Ore washing lost native water consumption or required water NBT");
        require(washed.record.get("duration").getAsString().equals("500") && washed.record.get("energy").getAsString().equals("16"), "Ore washing lost native power/time");
        require(water.getInteger("amount") == 1000, "Ore washing mutated metadata");
        water.setInteger("amount", 0);
        RecipeRow dry = row(iron, output, byproduct); capture(washing, input, washingOutput, dry);
        require(dry.inputs.size() == 1, "Zero-water recipe gained a fake keep-one fluid input");
        cutter(input, output, byproduct);
        machineLifecycle(handler,input,products,metadata,iron,output,byproduct);
        actualMachineSource(iron,output,byproduct);
        System.out.println("Native IC2: nine handlers, native matching/counts, alternatives, multi-output, water and blade requirements, immutable projection, unknown predicate rejection passed");
        if(family.equals("ic2-machines"))return;
        furnaces();
        extreme();
    }
    private static final class UnknownMetadata extends Item {
        boolean armed;int calls;
        @Override public int getDamage(ItemStack stack) {
            if(armed){calls++;throw new AssertionError("Unknown IC2 ingredient metadata callback executed");}
            return super.getDamage(stack);
        }
    }
    private static void actualMachineSource(ItemStack... stacks)throws Exception {
        cpw.mods.fml.common.Loader loader=cpw.mods.fml.common.Loader.instance();
        Map<String,cpw.mods.fml.common.ModContainer> prior=cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(cpw.mods.fml.common.Loader.class,loader,"namedMods");
        Map<String,cpw.mods.fml.common.ModContainer> configured=new HashMap<>(prior);
        cpw.mods.fml.common.ModMetadata metadata=new cpw.mods.fml.common.ModMetadata();metadata.modId="IC2";metadata.version="2.2.828-experimental";
        configured.put("IC2",new cpw.mods.fml.common.DummyModContainer(metadata));
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.common.Loader.class,loader,configured,"namedMods");
        try {
            for(String[] mode:new String[][]{{"MaceratorRecipeHandler","macerator"},{"ExtractorRecipeHandler","extractor"},
                    {"CompressorRecipeHandler","compressor"},{"MetalFormerRecipeHandlerCutting","metalformerCutting"},
                    {"MetalFormerRecipeHandlerRolling","metalformerRolling"},{"MetalFormerRecipeHandlerExtruding","metalformerExtruding"},
                    {"CentrifugeRecipeHandler","centrifuge"},{"OreWashingRecipeHandler","oreWashing"},{"BlockCutterRecipeHandler","blockcutter"}}) {
                Field api=Class.forName("ic2.api.recipe.Recipes").getField(mode[1]);Object previous=api.get(null);
                Object manager=Class.forName("ic2.core.BasicMachineRecipeManager").newInstance();
                Object nativeInput=Class.forName("ic2.api.recipe.RecipeInputItemStack").getConstructor(ItemStack.class,int.class).newInstance(stacks[0].copy(),1);
                NBTTagCompound requirement=new NBTTagCompound();
                if(mode[1].equals("centrifuge"))requirement.setInteger("minHeat",1500);
                if(mode[1].equals("oreWashing"))requirement.setInteger("amount",1000);
                if(mode[1].equals("blockcutter"))requirement.setInteger("hardness",6);
                manager.getClass().getMethod("addRecipe",Class.forName("ic2.api.recipe.IRecipeInput"),NBTTagCompound.class,ItemStack[].class)
                        .invoke(manager,nativeInput,requirement,new ItemStack[]{stacks[1].copy()});
                api.set(null,manager);
                try {
                    TemplateRecipeHandler handler=(TemplateRecipeHandler)Class.forName("ic2.neiIntegration.core.recipehandler."+mode[0]).newInstance();
                    RegistrationBindings first=RegistrationBindings.read(handler);
                    require(first.same(RegistrationBindings.read((TemplateRecipeHandler)handler.getClass().newInstance())),"IC2 registrations lost shared native source: "+mode[0]);
                    RegistryRecipes reader=new Ic2Recipes(handler);
                    try {
                        require(reader.size()==1&&reader.capture(0,row(stacks)),"Real IC2 native source failed capture: "+mode[0]);reader.finishVerification();
                        api.set(null,manager.getClass().newInstance());
                        require(!first.same(RegistrationBindings.read(handler)),"Replaced IC2 manager stayed registration-equivalent");
                        try{reader.verify();throw new AssertionError("IC2 manager replacement escaped verification");}
                        catch(Jobs.Fault failure){require(failure.code.equals("recipe_changed"),failure.toString());}
                        api.set(null,manager);reader.finishVerification();
                    } finally {reader.close();}
                } finally {api.set(null,previous);}
            }
        } finally {cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.common.Loader.class,loader,prior,"namedMods");}
    }
    private static void machineLifecycle(TemplateRecipeHandler handler,Object input,Object output,NBTTagCompound metadata,ItemStack... stacks)throws Exception {
        Map<Object,Object> source=new LinkedHashMap<>();source.put(input,output);
        Constructor<?> constructor=Class.forName("com.github.dcysteine.nesql.exporter.capture.Ic2Recipes").getDeclaredConstructor(TemplateRecipeHandler.class,Map.class);
        constructor.setAccessible(true);
        RegistryRecipes reader=(RegistryRecipes)constructor.newInstance(handler,source);
        try {
            require(reader.size()==1&&reader.capture(0,row(stacks)),"IC2 native map record was not captured");
            reader.finishVerification();metadata.setInteger("minHeat",1501);
            try {reader.finishVerification();throw new AssertionError("IC2 native metadata drift escaped sealing");}
            catch(Jobs.Fault failure){require(failure.code.equals("recipe_changed"),failure.toString());}
            finally {metadata.setInteger("minHeat",1500);}
            reader.finishVerification();source.clear();
            try {reader.verify();throw new AssertionError("IC2 registry count drift escaped verification");}
            catch(Jobs.Fault failure){require(failure.code.equals("recipe_changed"),failure.toString());}
            source.put(input,output);reader.finishVerification();
        } finally {reader.close();reader.close();}
        require(source.size()==1,"IC2 close cleared a borrowed registry");
        try {reader.size();throw new AssertionError("Closed IC2 reader remained usable");}
        catch(Jobs.Fault failure){require(failure.code.equals("capture_closed"),failure.toString());}
        RegistryRecipes empty=(RegistryRecipes)constructor.newInstance(handler,new LinkedHashMap<>());
        try {require(empty.size()==0,"Ready-empty IC2 map was not retained");empty.finishVerification();}finally{empty.close();}
    }
    private static void cutter(Object input, ItemStack output, ItemStack byproduct) throws Exception {
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("ic2.neiIntegration.core.recipehandler.BlockCutterRecipeHandler").newInstance();
        Class<?> outputType = Class.forName("ic2.api.recipe.RecipeOutput");
        ItemStack ingredient = new ItemStack(Items.iron_ingot);
        for (int hardness : new int[] {-1, 0, 3, 6, 9}) {
            NBTTagCompound metadata = new NBTTagCompound(); metadata.setInteger("hardness", hardness);
            Object products = outputType.getConstructor(NBTTagCompound.class, ItemStack[].class).newInstance(metadata, new ItemStack[] {output, byproduct});
            RecipeRow row = row(ingredient, output, byproduct);
            capture(handler, input, products, row);
            require(row.record.get("duration").getAsInt() == 900 && row.record.get("energy").getAsInt() == 48,
                    "Block cutter base time/power changed");
            require(row.inputs.size() == 1 && row.outputs.size() == 2
                    && row.properties.getAsJsonObject("ic2:bladeHardness").getAsJsonObject("value").get("value").getAsInt() == hardness,
                    "Block cutter blade attachment became consumption or lost its native threshold");
            require(metadata.getInteger("hardness") == hardness, "Block cutter display changed source metadata");
        }
        Object absent = outputType.getConstructor(NBTTagCompound.class, ItemStack[].class).newInstance(null, new ItemStack[] {output});
        try { capture(handler, input, absent, row(ingredient, output)); throw new AssertionError("Metadata-free block cutter recipe accepted"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"), "Wrong missing cutter metadata failure"); }
        NBTTagCompound empty = new NBTTagCompound();
        Object zero = outputType.getConstructor(NBTTagCompound.class, ItemStack[].class).newInstance(empty, new ItemStack[] {output});
        RecipeRow defaulted = row(ingredient, output); capture(handler, input, zero, defaulted);
        require(defaulted.properties.getAsJsonObject("ic2:bladeHardness").getAsJsonObject("value").get("value").getAsInt() == 0,
                "Native default-zero hardness changed");
        Class<?> machineType = Class.forName("ic2.core.block.machine.tileentity.TileEntityBlockCutter");
        machineType.getMethod("init").invoke(null);
        Object registry = Class.forName("ic2.api.recipe.Recipes").getField("blockcutter").get(null);
        Method add = registry.getClass().getMethod("addRecipe", Class.forName("ic2.api.recipe.IRecipeInput"), NBTTagCompound.class, ItemStack[].class);
        NBTTagCompound hardness = new NBTTagCompound(); hardness.setInteger("hardness", 6);
        add.invoke(registry, input, hardness, new ItemStack[] {output});
        Object machine = machineType.newInstance();
        Object inputSlot = MagicApi.field(machine, "inputSlot"), cutterSlot = MagicApi.field(machine, "cutterSlot");
        inputSlot.getClass().getMethod("put", ItemStack.class).invoke(inputSlot, new ItemStack(Items.iron_ingot, 4));
        require(machineType.getMethod("getOutput").invoke(machine) == null, "Native cutter works without a blade");
        // Own a native blade without ItemIC2's global item/network registration. Keep its real getter.
        Class<?> bladeType = Class.forName("ic2.core.item.resources.ItemBlockCuttingBlade");
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        Item blade = (Item) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(bladeType);
        Field delegate = Item.class.getField("delegate"); delegate.setAccessible(true);
        delegate.set(blade, new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(blade, Item.class));
        Field strength = bladeType.getDeclaredField("hardness"); strength.setAccessible(true);
        cutterSlot.getClass().getMethod("put", ItemStack.class).invoke(cutterSlot, new ItemStack(blade));
        for (int rating : new int[] {3, 6, 9}) {
            strength.setInt(blade, rating);
            require((machineType.getMethod("getOutput").invoke(machine) != null) == (rating >= 6), "Native blade threshold disagrees with exported requirement");
        }
    }
    private static void extreme() throws Exception {
        String prefix = "fox.spiteful.avaritia.";
        for (String kind : new String[] {"Shaped", "Shapeless"}) {
            TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName(prefix + "compat.nei.Extreme" + kind + "RecipeHandler").newInstance();
            require(Recipes.adapter(handler) != null, "Avaritia " + kind + " has no native adapter");
            ItemStack tagged = new ItemStack(Items.apple, 9); tagged.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("required"));
            ItemStack paper = new ItemStack(Items.paper, 6), output = new ItemStack(Items.diamond, 4);
            Class<?> type = Class.forName(prefix + "crafting.Extreme" + kind + "Recipe");
            net.minecraft.item.crafting.IRecipe recipe = (net.minecraft.item.crafting.IRecipe) (kind.equals("Shaped")
                    ? type.getConstructor(int.class, int.class, ItemStack[].class, ItemStack.class).newInstance(2, 2, new ItemStack[] {tagged, null, paper, tagged}, output)
                    : type.getConstructor(ItemStack.class, List.class).newInstance(output, Arrays.asList(tagged, paper, tagged)));
            net.minecraft.inventory.InventoryCrafting inventory = new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container() {
                public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) { return true; }
            }, 9, 9);
            inventory.setInventorySlotContents(0, new ItemStack(Items.apple)); inventory.setInventorySlotContents(kind.equals("Shaped") ? 9 : 1, paper.copy());
            inventory.setInventorySlotContents(kind.equals("Shaped") ? 10 : 2, tagged.copy());
            require(!recipe.matches(inventory, null), "Native tagged recipe accepted missing NBT");
            inventory.setInventorySlotContents(0, tagged.copy()); require(recipe.matches(inventory, null), "Native recipe fixture does not match");
            Method capture = Class.forName("com.github.dcysteine.nesql.exporter.capture.ExtremeRecipes")
                    .getDeclaredMethod("capture", net.minecraft.item.crafting.IRecipe.class, TemplateRecipeHandler.class, RecipeRow.class);
            capture.setAccessible(true); RecipeRow row = row(tagged, paper, output);
            TemplateRecipeHandler.CachedRecipe sentinel=handler.new CachedRecipe(){
                public codechicken.nei.PositionedStack getResult(){return null;}
                public List<codechicken.nei.PositionedStack> getIngredients(){return Collections.emptyList();}
            };
            handler.arecipes.add(sentinel);
            require((Boolean) capture.invoke(null, recipe, handler, row), "Valid native recipe was excluded");
            require(handler.arecipes.size()==1&&handler.arecipes.get(0)==sentinel,"Extreme semantic capture changed the borrowed native GUI cache");
            require(row.inputs.size() == 3 && row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("4"), "Avaritia lost cells or output count");
            com.google.gson.JsonObject choice = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
            require(choice.get("amount").getAsString().equals("1") && choice.getAsJsonObject("rule").get("kind").getAsString().equals("exact"), "Avaritia must preserve required NBT with an exact rule and consume one item");
            if (kind.equals("Shaped")) require(row.record.getAsJsonObject("grid").getAsJsonArray("cells").get(1).isJsonNull()
                    && row.record.getAsJsonObject("grid").get("mirror").getAsBoolean(), "Avaritia grid holes/mirroring lost");
            require(tagged.stackSize == 9 && paper.stackSize == 6, "Avaritia mutated native source quantities");
            extremeProduction(handler,recipe,tagged,paper,output);
            if (kind.equals("Shaped")) {
                Class<?> ore = Class.forName(prefix + "crafting.ExtremeShapedOreRecipe");
                Object mixed = ore.getConstructor(ItemStack.class, Object[].class, int.class, int.class)
                        .newInstance(output, new Object[] {new ArrayList<>(Arrays.asList(tagged, paper)), tagged, null, paper}, 2, 2);
                ore.getMethod("setMirrored", boolean.class).invoke(mixed, false);
                RecipeRow mixedRow = row(tagged, paper, output); capture.invoke(null, mixed, handler, mixedRow);
                require(mixedRow.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").size() == 2
                        && mixedRow.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").get("nbt").getAsBoolean()
                        && mixedRow.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").get("kind").getAsString().equals("exact")
                        && !mixedRow.record.getAsJsonObject("grid").get("mirror").getAsBoolean(), "Extreme ore groups must ignore tags while direct stacks keep required NBT");
                Object empty = ore.getConstructor(ItemStack.class, Object[].class, int.class, int.class)
                        .newInstance(output, new Object[] {new ArrayList<>()}, 1, 1);
                require(!(Boolean) capture.invoke(null, empty, handler, row(output)), "Impossible ore predicate was exported as a recipe");
            } else {
                net.minecraftforge.oredict.ShapelessOreRecipe ore = new net.minecraftforge.oredict.ShapelessOreRecipe(output, tagged, paper);
                RecipeRow mixedRow = row(tagged, paper, output); capture.invoke(null, ore, handler, mixedRow);
                require(mixedRow.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").get("nbt").getAsBoolean(), "Forge shapeless input unexpectedly requires NBT");
            }
        }
        System.out.println("Native Avaritia: native matching, source/member/fact bindings, readiness, metadata/container boundaries, cancellation and owned cleanup passed");
    }
    private static void extremeProduction(TemplateRecipeHandler handler,net.minecraft.item.crafting.IRecipe recipe,ItemStack... specimens)throws Exception{
        Class<?> managerType=Class.forName("fox.spiteful.avaritia.crafting.ExtremeCraftingManager");
        Object manager=MagicApi.field(managerType,null,"instance");
        Field registry=managerType.getDeclaredField("recipes");registry.setAccessible(true);Object oldRegistry=registry.get(manager);
        cpw.mods.fml.common.Loader loader=cpw.mods.fml.common.Loader.instance();
        Map<String,cpw.mods.fml.common.ModContainer> mods=(Map<String,cpw.mods.fml.common.ModContainer>)MagicApi.field(loader,"namedMods");
        cpw.mods.fml.common.ModContainer oldMod=mods.get("Avaritia");
        Field locale=null;for(Field field:net.minecraft.client.resources.I18n.class.getDeclaredFields())if(field.getType()==net.minecraft.client.resources.Locale.class)locale=field;
        require(locale!=null,"Native client locale field is missing");locale.setAccessible(true);Object oldLocale=locale.get(null);
        try{
            NativeCoreFixesTest.version("Avaritia","1.77");registry.set(manager,new ArrayList<>(Collections.singletonList(recipe)));
            locale.set(null,new net.minecraft.client.resources.Locale());
            try(Recipes.Cursor cursor=new Recipes.Handler(handler,0).open(row(specimens).facts)){
                require(cursor.size()==1,"Extreme production cursor lost native recipe");
                require(MagicApi.field(cursor,"handler")==null,"Extreme production still owns a GUI projection handler");
                cursor.capture(0);cursor.finishVerification();
            }
            require(handler.arecipes.size()==1,"Extreme production close changed borrowed GUI cache");
            try(ExtremeRecipes reader=new ExtremeRecipes(handler)) {
                RecipeRow captured=row(specimens);reader.capture(0,captured);
                require(captured.record.has("process")&&captured.record.getAsJsonObject("process").get("kind").getAsString().equals("extremeCrafting"),
                        "Extreme capture lost native selector and repair priority");
                extremeFixture("native-extreme-"+(handler.getClass().getName().endsWith("ExtremeShapedRecipeHandler")?"shaped":"shapeless"),captured,handler,Arrays.asList(specimens));
            }
            try(ExtremeRecipes reader=new ExtremeRecipes(handler)) {
                ItemStack nativeOutput=recipe.getRecipeOutput();int amount=nativeOutput.stackSize;
                try {
                    nativeOutput.stackSize++;
                    try { reader.capture(0,row(specimens));throw new AssertionError("Extreme reader accepted changed native output"); }
                    catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
                        require(expected.code.equals("recipe_changed"),"Extreme output drift lost its classification");
                    }
                } finally {nativeOutput.stackSize=amount;}
                Object input=MagicApi.field(recipe,"recipeItems");
                ItemStack first=(ItemStack)(input instanceof List<?>?((List<?>)input).get(0):((Object[])input)[0]);
                int inputAmount=first.stackSize;
                try {first.stackSize++;extremeFault("recipe_changed",()->reader.capture(0,row(specimens)));}
                finally {first.stackSize=inputAmount;}
                NBTTagCompound oldTag=first.getTagCompound();
                try {first.setTagCompound((NBTTagCompound)oldTag.copy());extremeFault("recipe_changed",reader::finishVerification);}
                finally {first.setTagCompound(oldTag);}
                String owner=oldTag.getString("owner");
                try {oldTag.setString("owner","changed");extremeFault("recipe_changed",reader::finishVerification);}
                finally {oldTag.setString("owner",owner);}
                try {
                    if(input instanceof List<?>)((List)input).set(0,first.copy());else ((Object[])input)[0]=first.copy();
                    extremeFault("recipe_changed",()->reader.capture(0,row(specimens)));
                } finally {if(input instanceof List<?>)((List)input).set(0,first);else ((Object[])input)[0]=first;}
                Field inputField=recipe.getClass().getDeclaredField("recipeItems");inputField.setAccessible(true);
                try {
                    inputField.set(recipe,input instanceof List<?>?new ArrayList<>((List<?>)input):((Object[])input).clone());
                    extremeFault("recipe_changed",reader::finishVerification);
                } finally {inputField.set(recipe,input);}
                Field outputField=recipe.getClass().getDeclaredField("recipeOutput");outputField.setAccessible(true);
                try {outputField.set(recipe,nativeOutput.copy());extremeFault("recipe_changed",reader::finishVerification);}
                finally {outputField.set(recipe,nativeOutput);}
                Object active=registry.get(manager);
                RegistrationBindings binding=RegistrationBindings.read(handler);
                try {
                    registry.set(manager,new ArrayList<>(Collections.singletonList(recipe)));
                    require(!binding.same(RegistrationBindings.read(handler)),"Extreme registration accepted a replacement source root");
                    extremeFault("recipe_changed",reader::size);
                    registry.set(manager,null);extremeFault("recipe_changed",reader::size);
                } finally {registry.set(manager,active);}
                Thread.currentThread().interrupt();
                try {reader.size();throw new AssertionError("Extreme reader ignored cancellation");}
                catch(java.util.concurrent.CancellationException expected) { }
                finally {Thread.interrupted();}
                reader.finishVerification();reader.close();reader.close();
                extremeFault("capture_closed",reader::size);
            }
            registry.set(manager,new ArrayList<>());
            try(ExtremeRecipes reader=new ExtremeRecipes(handler)){require(reader.size()==0,"Native ready-empty extreme table was rejected");reader.finishVerification();}
            registry.set(manager,null);
            extremeFault("handler_not_ready",()->new ExtremeRecipes(handler));
            registry.set(manager,new ArrayList<>(Collections.singletonList(recipe)));
            extremeBoundaries(handler,recipe,registry,manager,specimens);
            if(handler.getClass().getName().endsWith("ExtremeShapedRecipeHandler"))extremePriority(handler,recipe,registry,manager,specimens);
        }finally{
            registry.set(manager,oldRegistry);locale.set(null,oldLocale);
            if(oldMod==null)mods.remove("Avaritia");else mods.put("Avaritia",oldMod);
        }
    }
    private static void extremeFault(String code,Runnable action) {
        try {action.run();throw new AssertionError("Extreme reader accepted an invalid boundary: "+code);}
        catch(Jobs.Fault expected){require(expected.code.equals(code),"Wrong extreme boundary code: "+expected.code+" expected "+code);}
    }
    private static void extremeFixture(String name,RecipeRow captured,TemplateRecipeHandler handler,Collection<ItemStack> specimens)throws Exception {
        com.google.gson.JsonObject origin=object("owner","Avaritia","handler",handler.getClass().getName(),"key",name);
        captured.record.add("source",origin);captured.record.addProperty("category",Identity.origin("category",origin));
        NativeContractFixture.writeOwned(name,captured,specimens);
    }
    private static void extremePriority(TemplateRecipeHandler handler,net.minecraft.item.crafting.IRecipe original,
            Field registry,Object manager,ItemStack...specimens)throws Exception {
        Class<?> shapeless=Class.forName("fox.spiteful.avaritia.crafting.ExtremeShapelessRecipe");
        Object prior=shapeless.getConstructor(ItemStack.class,List.class).newInstance(new ItemStack(Items.emerald),Arrays.asList(specimens[0],specimens[1],specimens[0]));
        ItemStack unique=new ItemStack(Items.emerald);
        Object first=shapeless.getConstructor(ItemStack.class,List.class).newInstance(new ItemStack(Items.gold_ingot),Collections.singletonList(unique));
        registry.set(manager,new ArrayList<>(Arrays.asList(first,prior,original)));
        List<ItemStack> known=new ArrayList<>(Arrays.asList(specimens));known.add(unique);
        try(ExtremeRecipes reader=new ExtremeRecipes(handler)) {
            RecipeRow captured=row(known.toArray(new ItemStack[0]));reader.capture(0,captured);reader.finishVerification();
            require(captured.record.getAsJsonObject("process").get("nativeIndex").getAsInt()==2
                    &&captured.record.getAsJsonObject("process").getAsJsonArray("earlier").size()==2,
                    "Extreme shaped reader lost a preceding shapeless selector");
            extremeFixture("native-extreme-priority",captured,handler,known);
            Object owned=manager.getClass().newInstance();registry.set(owned,new ArrayList<>((List<?>)registry.get(manager)));
            net.minecraft.inventory.InventoryCrafting offered=new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container(){public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player){return false;}},9,9);
            offered.setInventorySlotContents(0,specimens[0].copy());offered.setInventorySlotContents(9,specimens[1].copy());offered.setInventorySlotContents(10,specimens[0].copy());
            ItemStack actual=(ItemStack)manager.getClass().getMethod("findMatchingRecipe",net.minecraft.inventory.InventoryCrafting.class,net.minecraft.world.World.class).invoke(owned,offered,null);
            require(actual.getItem()==Items.emerald,"Native extreme manager no longer selects its earlier matching shapeless definition");
        }
        Class<?> shaped=Class.forName("fox.spiteful.avaritia.crafting.ExtremeShapedRecipe");
        ItemStack left=new ItemStack(Items.iron_pickaxe,1,20),right=new ItemStack(Items.iron_pickaxe,1,30),out=new ItemStack(Items.diamond);
        Object repair=shaped.getConstructor(int.class,int.class,ItemStack[].class,ItemStack.class).newInstance(2,1,new ItemStack[]{left,right},out);
        registry.set(manager,new ArrayList<>(Collections.singletonList(repair)));
        try(ExtremeRecipes reader=new ExtremeRecipes(handler)) {
            RecipeRow captured=row(left,right,out);reader.capture(0,captured);reader.finishVerification();
            com.google.gson.JsonObject state=captured.record.getAsJsonObject("process").getAsJsonArray("repair").get(0).getAsJsonObject();
            require(state.get("enabled").getAsBoolean()&&state.get("maximum").getAsInt()==Items.iron_pickaxe.getMaxDamage(),"Extreme repair priority was lost");
            extremeFixture("native-extreme-repair",captured,handler,Arrays.asList(left,right,out));
            Field canRepair=Item.class.getDeclaredField("canRepair");canRepair.setAccessible(true);boolean enabled=canRepair.getBoolean(Items.iron_pickaxe);
            try {canRepair.setBoolean(Items.iron_pickaxe,!enabled);extremeFault("recipe_changed",reader::finishVerification);}
            finally {canRepair.setBoolean(Items.iron_pickaxe,enabled);}
            Object owned=manager.getClass().newInstance();registry.set(owned,new ArrayList<>(Collections.singletonList(repair)));
            net.minecraft.inventory.InventoryCrafting offered=new net.minecraft.inventory.InventoryCrafting(new net.minecraft.inventory.Container(){public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player){return false;}},9,9);
            offered.setInventorySlotContents(0,left.copy());offered.setInventorySlotContents(1,right.copy());
            Method match=manager.getClass().getMethod("findMatchingRecipe",net.minecraft.inventory.InventoryCrafting.class,net.minecraft.world.World.class);
            ItemStack actual=(ItemStack)match.invoke(owned,offered,null);
            require(actual.getItem()==Items.iron_pickaxe&&actual.getTagCompound()==null,"Native repair did not override its registered result");
            int max=Items.iron_pickaxe.getMaxDamage();
            require(actual.getItemDamage()==Math.max(0,max-((max-20)+(max-30)+max*5/100)),"Native repair formula differs from the declaration");
            ItemStack stacked=left.copy();stacked.stackSize=2;offered.setInventorySlotContents(0,stacked);
            actual=(ItemStack)match.invoke(owned,offered,null);require(actual.getItem()==Items.diamond,"Non-unit offered stock incorrectly entered native repair");
        }
        registry.set(manager,new ArrayList<>(Collections.singletonList(original)));
    }
    private static void extremeBoundaries(TemplateRecipeHandler handler,net.minecraft.item.crafting.IRecipe original,
            Field registry,Object manager,ItemStack...specimens)throws Exception {
        Class<?> shaped=Class.forName("fox.spiteful.avaritia.crafting.ExtremeShapedRecipe");
        java.util.function.Function<ItemStack,Object> recipe=input->{
            try{return shaped.getConstructor(int.class,int.class,ItemStack[].class,ItemStack.class)
                    .newInstance(1,1,new ItemStack[]{input},new ItemStack(Items.diamond));}
            catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
        };
        // These foreign callbacks must be rejected before they are executed.
        ExtremeMetadataProbe metadata=new ExtremeMetadataProbe();ExtremeContainerProbe container=new ExtremeContainerProbe();
        for(Item item:new Item[]{metadata,container}) {
            registry.set(manager,new ArrayList<>(Collections.singletonList(recipe.apply(new ItemStack(item)))));
            extremeFault("recipe_unsupported",()->new ExtremeRecipes(handler));
        }
        require(metadata.calls==0&&container.calls==0,"Extreme reader executed an unknown item callback");
        ExtremeRepairProbe repairProbe=new ExtremeRepairProbe();
        Object foreignRepair=shaped.getConstructor(int.class,int.class,ItemStack[].class,ItemStack.class)
                .newInstance(2,1,new ItemStack[]{new ItemStack(repairProbe),new ItemStack(repairProbe)},new ItemStack(Items.diamond));
        registry.set(manager,new ArrayList<>(Collections.singletonList(foreignRepair)));
        extremeFault("recipe_unsupported",()->new ExtremeRecipes(handler));
        require(repairProbe.calls==0,"Extreme reader executed an unknown repair callback");
        ExtremeRecipeProbe customRecipe=new ExtremeRecipeProbe();
        registry.set(manager,new ArrayList<>(Collections.singletonList(customRecipe)));
        extremeFault("recipe_unsupported",()->new ExtremeRecipes(handler));
        require(customRecipe.calls==0,"Extreme reader executed an unknown recipe callback");
        Object valid=recipe.apply(new ItemStack(Items.paper));
        ExtremeListProbe customTable=new ExtremeListProbe();customTable.add(valid);registry.set(manager,customTable);
        extremeFault("recipe_unsupported",()->new ExtremeRecipes(handler));
        require(customTable.calls==0,"Extreme reader executed a foreign source list callback");
        boolean oldReady=codechicken.nei.ItemList.loadFinished;
        com.google.common.collect.ListMultimap<Item,ItemStack> oldMap=codechicken.nei.ItemList.itemMap;
        try {
            ItemStack wildcard=new ItemStack(Items.apple,7,32767);
            wildcard.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("native-template"));
            registry.set(manager,new ArrayList<>(Collections.singletonList(recipe.apply(wildcard))));
            codechicken.nei.ItemList.itemMap=com.google.common.collect.ArrayListMultimap.create();
            codechicken.nei.ItemList.loadFinished=false;
            extremeFault("handler_not_ready",()->new ExtremeRecipes(handler));
            codechicken.nei.ItemList.loadFinished=true;
            ItemStack variant=new ItemStack(Items.apple,4,3);
            variant.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("display-only"));
            codechicken.nei.ItemList.itemMap.put(Items.apple,variant);
            ItemStack semantic=variant.copy();semantic.setTagCompound((NBTTagCompound)wildcard.getTagCompound().copy());
            try(ExtremeRecipes reader=new ExtremeRecipes(handler)) {
                // The shapeless handler intentionally has no visible row in this table;
                // sealing must nevertheless bind the other family's selectors.
                require(reader.size()==(handler.getClass().getName().endsWith("ExtremeShapedRecipeHandler")?1:0),"Extreme family selection changed");
                if(reader.size()==1) {
                    RecipeRow row=row(semantic,new ItemStack(Items.diamond));reader.capture(0,row);
                    com.google.gson.JsonObject choice=row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
                    require(choice.getAsJsonObject("rule").get("meta").getAsBoolean()
                            &&!choice.getAsJsonObject("rule").get("nbt").getAsBoolean(),"Extreme wildcard lost required source NBT");
                }
                try {variant.stackSize++;extremeFault("recipe_changed",reader::finishVerification);}
                finally {variant.stackSize--;}
                codechicken.nei.ItemList.loadFinished=false;
                extremeFault("recipe_changed",reader::finishVerification);codechicken.nei.ItemList.loadFinished=true;
                ItemStack prior=codechicken.nei.ItemList.itemMap.get(Items.apple).set(0,variant.copy());
                extremeFault("recipe_changed",reader::finishVerification);codechicken.nei.ItemList.itemMap.get(Items.apple).set(0,prior);
                com.google.common.collect.ListMultimap<Item,ItemStack> active=codechicken.nei.ItemList.itemMap;
                codechicken.nei.ItemList.itemMap=com.google.common.collect.ArrayListMultimap.create(active);
                extremeFault("recipe_changed",reader::finishVerification);codechicken.nei.ItemList.itemMap=active;
                reader.finishVerification();
            }
            codechicken.nei.ItemList.itemMap.clear();
            try(ExtremeRecipes reader=new ExtremeRecipes(handler)){reader.finishVerification();}
            require(wildcard.stackSize==7&&wildcard.getItemDamage()==32767&&variant.stackSize==4,
                    "Extreme enumeration mutated borrowed native stacks");
        } finally {codechicken.nei.ItemList.loadFinished=oldReady;codechicken.nei.ItemList.itemMap=oldMap;}
        registry.set(manager,new ArrayList<>(Collections.singletonList(recipe.apply(new ItemStack(Items.milk_bucket)))));
        Item oldContainer=Items.milk_bucket.getContainerItem();
        try(ExtremeRecipes reader=new ExtremeRecipes(handler)) {
            Items.milk_bucket.setContainerItem(Items.bowl);
            extremeFault("recipe_changed",reader::finishVerification);
        } finally {Items.milk_bucket.setContainerItem(oldContainer);}
        registry.set(manager,new ArrayList<>(Arrays.asList(original,recipe.apply(new ItemStack(Items.paper)))));
        try(ExtremeRecipes reader=new ExtremeRecipes(handler)) {
            Collections.swap((List<?>)registry.get(manager),0,1);
            extremeFault("recipe_changed",reader::finishVerification);
        }
        registry.set(manager,new ArrayList<>(Collections.singletonList(original)));
    }
    public static final class ExtremeMetadataProbe extends Item {
        int calls;
        @Override public int getDamage(ItemStack stack){calls++;return super.getDamage(stack);}
    }
    public static final class ExtremeContainerProbe extends Item {
        int calls;
        @Override public boolean hasContainerItem(ItemStack stack){calls++;return false;}
    }
    public static final class ExtremeRepairProbe extends Item {
        int calls;
        @Override public boolean isRepairable(){calls++;return false;}
    }
    public static final class ExtremeRecipeProbe extends net.minecraftforge.oredict.ShapelessOreRecipe {
        int calls;
        ExtremeRecipeProbe(){super(new ItemStack(Items.diamond),new ItemStack(Items.paper));}
        @Override public ItemStack getRecipeOutput(){calls++;return super.getRecipeOutput();}
    }
    public static final class ExtremeListProbe extends ArrayList<Object> {
        int calls;
        @Override public Object[] toArray(){calls++;return super.toArray();}
        @Override public int size(){calls++;return super.size();}
    }
    @SuppressWarnings("unchecked")
    private static void furnaces() throws Exception {
        java.io.File root = new java.io.File("build/native-tests/etfuturum-config").getAbsoluteFile();
        root.mkdirs();
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.relauncher.FMLInjectionData.class, null, root, "minecraftHome");
        net.minecraft.launchwrapper.Launch.minecraftHome = root;
        for (String kind : new String[] {"Smoker", "BlastFurnace"}) {
            TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("ganymedes01.etfuturum.compat.nei." + kind + "RecipeHandler").newInstance();
            require(Recipes.adapter(handler) != null, "Et Futurum " + kind + " has no adapter");
            Object registry = Class.forName("ganymedes01.etfuturum.recipes." + kind + "Recipes").getMethod("smelting").invoke(null);
            Method add = registry.getClass().getMethod("addRecipe", ItemStack.class, ItemStack.class, float.class);
            ItemStack apple = new ItemStack(Items.apple, 11), carrot = new ItemStack(Items.carrot, 7);
            ItemStack result = new ItemStack(Items.diamond, 3);
            add.invoke(registry, apple, result, 0.5f); add.invoke(registry, carrot, result, 0.5f);
            registry.getClass().getMethod("removeRecipe", ItemStack.class).invoke(registry, carrot);
            Class<?> adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.SmeltingRecipes");
            Method enumerate = adapter.getDeclaredMethod("enumerate", Object.class, Map.class); enumerate.setAccessible(true);
            Map<ItemStack, ItemStack> inherited = new LinkedHashMap<>(); inherited.put(apple, new ItemStack(Items.bread));
            // Exercise the native filter directly so missing test-only bootstrap dependencies retain their cause.
            registry.getClass().getMethod("canAdd", ItemStack.class, ItemStack.class).invoke(registry, apple, inherited.get(apple));
            List<?> recipes = (List<?>) enumerate.invoke(null, registry, inherited);
            require(recipes.size() == 1, "Et Futurum override/blacklist produced extra recipes");
            RecipeRow row = row(apple, result);
            Method capture = adapter.getDeclaredMethod("capture", codechicken.nei.recipe.FurnaceRecipeHandler.class, Map.Entry.class, Object.class, RecipeRow.class);
            capture.setAccessible(true); capture.invoke(null, handler, recipes.get(0), registry, row);
            require(row.inputs.size() == 1 && row.outputs.size() == 1 && row.record.get("duration").getAsString().equals("100"), "Et Futurum captured fuel as output or lost native duration");
            require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3"), "Et Futurum override result/count was lost");
            require(apple.stackSize == 11 && result.stackSize == 3, "Smelting layout mutated registered recipe stacks");
            ((Map<?, ?>) registry.getClass().getField("smeltingList").get(registry)).clear();
            // Native wildcard lookup precedes exact entries; a specific blacklist still wins.
            ItemStack any = new ItemStack(Items.paper, 1, OreDictionary.WILDCARD_VALUE);
            ItemStack first = new ItemStack(Items.paper, 1, 0), second = new ItemStack(Items.paper, 1, 1);
            List<ItemStack> previous = new ArrayList<>(codechicken.nei.ItemList.itemMap.get(Items.paper));
            try {
                codechicken.nei.ItemList.itemMap.replaceValues(Items.paper, Arrays.asList(first, second));
                add.invoke(registry, any, result, 0.5f);
                add.invoke(registry, second, new ItemStack(Items.gold_ingot), 0.5f);
                registry.getClass().getMethod("removeRecipe", ItemStack.class).invoke(registry, first);
                List<Map.Entry<ItemStack, ItemStack>> expanded = (List<Map.Entry<ItemStack, ItemStack>>) enumerate.invoke(null, registry, Collections.emptyMap());
                require(expanded.size() == 1 && expanded.get(0).getKey().getItemDamage() == 1,
                        "Wildcard smelting retained a blacklisted subtype or duplicated a native key");
                ItemStack nativeOutput = (ItemStack) registry.getClass().getMethod("getSmeltingResult", ItemStack.class).invoke(registry, second.copy());
                require(ItemStack.areItemStacksEqual(nativeOutput, expanded.get(0).getValue()), "Smelting disagrees with native wildcard precedence");
                RecipeRow exact = row(second, result); capture.invoke(null, handler, expanded.get(0), registry, exact);
                require(!exact.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").get("meta").getAsBoolean(),
                        "Filtered smelting candidate must not regain wildcard metadata matching");
            } finally {
                codechicken.nei.ItemList.itemMap.replaceValues(Items.paper, previous);
                ((Map<?, ?>) registry.getClass().getField("smeltingList").get(registry)).clear();
            }
        }
        System.out.println("Native Et Futurum: overrides, blacklist, exact output counts, duration, immutable layout passed");
    }
    private static void capture(TemplateRecipeHandler handler, Object input, Object output, RecipeRow row) throws Exception {
        Method method = Class.forName("com.github.dcysteine.nesql.exporter.capture.Ic2Recipes")
                .getDeclaredMethod("capture", TemplateRecipeHandler.class, Object.class, Object.class, RecipeRow.class);
        method.setAccessible(true);
        try { method.invoke(null, handler, input, output, row); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Error) throw (Error) failure.getCause();
            throw (Exception) failure.getCause();
        }
    }
    @SuppressWarnings("unchecked")
    private static RecipeRow row(ItemStack... stacks) {
        Facts facts = new Facts("en_US");
        Set<String> known = (Set<String>) MagicApi.field(facts, "items");
        for (ItemStack stack : stacks) known.add(Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), stack.getItemDamage(), TypedNbt.encode(stack.getTagCompound())));
        return new RecipeRow(facts, object("owner", "IC2", "handler", "native", "key", "native"), "category_test", 0);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
