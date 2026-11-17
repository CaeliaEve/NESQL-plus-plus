package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.capture.NativeDiagramContents.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Small actual-generator API closure; inputs are owned local registries, never a game/export. */
final class NativeGtDiagramGeneratorsTest {
    private static final JsonArray domains = array();
    private static final Map<String, JsonObject> itemFacts = new TreeMap<>(),
            fluidFacts = new TreeMap<>();

    static void run() throws Exception {
        try (java.io.InputStream nativeLanguage = NativeGtDiagramGeneratorsTest.class.getClassLoader()
                .getResourceAsStream("assets/neicustomdiagram/lang/en_US.lang")) {
            if (nativeLanguage == null) throw new AssertionError("Pinned native English diagram resources are required");
            net.minecraft.util.StringTranslate.inject(nativeLanguage);
        }
        if (net.minecraft.launchwrapper.Launch.blackboard == null)
            net.minecraft.launchwrapper.Launch.blackboard = new HashMap<>();
        net.minecraft.launchwrapper.Launch.blackboard.put("fml.deobfuscatedEnvironment", true);
        cpw.mods.fml.common.ModMetadata metadata = new cpw.mods.fml.common.ModMetadata();
        metadata.modId = "gregtech";
        metadata.name = "Owned GT native API fixture";
        metadata.version = "5.09.51.482";
        Object controller =
                new cpw.mods.fml.common.LoadController(cpw.mods.fml.common.Loader.instance());
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
                cpw.mods.fml.common.Loader.class,
                cpw.mods.fml.common.Loader.instance(),
                (cpw.mods.fml.common.LoadController) controller,
                "modController");
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
                (Class) controller.getClass(),
                controller,
                new cpw.mods.fml.common.DummyModContainer(metadata),
                "activeContainer");
        for (Object option :
                collection(
                        invoke(
                                type(
                                        "com.github.dcysteine.neicustomdiagram.main.config.ConfigOptions"),
                                null,
                                "getAllOptions",
                                new Class<?>[0]))) call(option, "initialize");
        type("gregtech.GTMod")
                .getField("proxy")
                .set(null, type("gregtech.common.GTProxy").newInstance());
        try {
            type("nesql.native.fixture.RequiredMissingApi");
            throw new AssertionError("Missing API accepted");
        } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault failure) {
            if (!(failure.getCause() instanceof ClassNotFoundException))
                throw new AssertionError("Original native linkage cause was lost", failure);
        }
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
                (Class) type("gregtech.api.util.GTConfig"),
                null,
                new net.minecraftforge.common.config.Configuration(
                        new java.io.File(
                                "build/native-tests/diagram-config/gregtech-enchantments.cfg")),
                "sConfigFileIDs");
        type("gregtech.api.enchants.EnchantmentRadioactivity").newInstance();
        Class<?> tools = type("gregtech.common.items.MetaGeneratedTool01");
        Object nativeTools = tools.newInstance();
        Object aluminium = field(type("gregtech.api.enums.Materials"), null, "Aluminium");
        Object wood = field(type("gregtech.api.enums.Materials"), null, "Wood");
        Object hammer =
                Enum.valueOf((Class) type("gregtech.common.items.IDMetaTool01"), "HARDHAMMER");
        int hammerId = (Integer) field(hammer, "ID");
        ItemStack stack =
                (ItemStack)
                        invoke(
                                tools,
                                nativeTools,
                                "getToolWithStats",
                                new Class<?>[] {
                                    int.class,
                                    int.class,
                                    type("gregtech.api.enums.Materials"),
                                    type("gregtech.api.enums.Materials"),
                                    long[].class
                                },
                                hammerId,
                                1,
                                aluminium,
                                wood,
                                null);
        if (stack == null
                || stack.getTagCompound() == null
                || field(tools, null, "INSTANCE") != nativeTools
                || invoke(
                                type("gregtech.api.items.MetaGeneratedTool"),
                                null,
                                "getPrimaryMaterial",
                                new Class<?>[] {ItemStack.class},
                                stack)
                        != aluminium)
            throw new AssertionError(
                    "Genuine native tool registry, stats and primary-material NBT are required");
        System.out.println(
                "Native GT diagram tool bootstrap: actual MetaGeneratedTool01 constructor, native hammer stats and material NBT passed; generator coverage still pending");
        nativeGenerators(aluminium, wood, stack);
        JsonArray items = array(), fluids = array();
        for (JsonObject item : itemFacts.values()) items.add(item);
        for (JsonObject fluid : fluidFacts.values()) fluids.add(fluid);
        Files.write(
                Paths.get("build/native-tests/gt-diagram-generators-contract.json"),
                CanonicalJson.bytes(
                        object(
                                "domains",
                                domains,
                                "items",
                                items,
                                "fluids",
                                fluids)));
    }

    private static void nativeGenerators(Object aluminium, Object wood, ItemStack tool)
            throws Exception {
        Class<?> materials = type("gregtech.api.enums.Materials"),
                prefixes = type("gregtech.api.enums.OrePrefixes"),
                items = type("gregtech.api.enums.ItemList");
        Object water = field(materials, null, "Water"), emerald = field(materials, null, "Emerald");
        Map<String, Object> nativeMaterials =
                (Map<String, Object>) field(materials, null, "MATERIALS_MAP");
        Map<String, Object> saved = new LinkedHashMap<>(nativeMaterials);
        nativeMaterials.clear();
        for (Object material : Arrays.asList(aluminium, wood, water, emerald))
            nativeMaterials.put((String) field(material, "mName"), material);
        try {
            itemList(items, "Cell_Empty", new ItemStack(Items.bucket));
            itemList(items, "Display_Fluid", new ItemStack(Items.feather));
            itemList(items, "Machine_Multi_BlastFurnace", new ItemStack(Items.brewing_stand));
            String[] coils = {
                "Cupronickel",
                "Kanthal",
                "Nichrome",
                "TungstenSteel",
                "HSSG",
                "Naquadah",
                "NaquadahAlloy",
                "ElectrumFlux",
                "AwakenedDraconium",
                "HSSS",
                "Trinium",
                "Infinity",
                "Hypogen",
                "Eternal"
            };
            for (int index = 0; index < coils.length; index++)
                itemList(
                        items, "Casing_Coil_" + coils[index], new ItemStack(Items.paper, 1, index));
            ItemStack part = new ItemStack(Items.iron_ingot),
                    cell = new ItemStack(Items.water_bucket);
            net.minecraftforge.fluids.FluidContainerRegistry.registerFluidContainer(
                    new net.minecraftforge.fluids.FluidStack(
                            net.minecraftforge.fluids.FluidRegistry.WATER, 1000),
                    cell,
                    new ItemStack(Items.bucket));
            invoke(
                    type("gregtech.api.util.GTUtility"),
                    null,
                    "addFluidContainerData",
                    new Class<?>[] {
                        net.minecraftforge.fluids.FluidContainerRegistry.FluidContainerData.class
                    },
                    new net.minecraftforge.fluids.FluidContainerRegistry.FluidContainerData(
                            new net.minecraftforge.fluids.FluidStack(
                                    net.minecraftforge.fluids.FluidRegistry.WATER, 1000),
                            cell,
                            new ItemStack(Items.bucket)));
            Class<?> unificator = type("gregtech.api.util.GTOreDictUnificator");
            invoke(
                    unificator,
                    null,
                    "set",
                    new Class<?>[] {prefixes, materials, ItemStack.class},
                    field(prefixes, null, "ingot"),
                    aluminium,
                    part);
            invoke(
                    unificator,
                    null,
                    "addAssociation",
                    new Class<?>[] {prefixes, materials, ItemStack.class, boolean.class},
                    field(prefixes, null, "cell"),
                    water,
                    cell,
                    false);
            codechicken.nei.ItemList.items = Arrays.asList(part, cell, tool);
            Object generator =
                    type("com.github.dcysteine.neicustomdiagram.generators.gregtech5.materialparts.GregTechMaterialParts")
                            .getConstructor(String.class)
                            .newInstance(NativeGtDiagramReferences.PREFIX + "materialparts");
            Object group = call(generator, "generate");
            JsonObject source =
                    object(
                            "owner",
                            "neicustomdiagram",
                            "handler",
                            group.getClass().getName(),
                            "key",
                            NativeGtDiagramReferences.PREFIX + "materialparts");
            try (NativeGtDiagramReferences reader =
                    new NativeGtDiagramReferences(
                            (codechicken.nei.recipe.ICraftingHandler) group, source)) {
                reader.finishVerification();
                JsonArray records = (JsonArray) field(field(reader, "snapshot"), "records");
                boolean fluidQuery = false;
                for (JsonElement record : records) {
                    JsonObject data = record.getAsJsonObject().getAsJsonObject("data");
                    if (data.get("kind").getAsString().equals("neiDiagramLookup")
                            && data.getAsJsonObject("original")
                                    .get("kind")
                                    .getAsString()
                                    .equals("fluid")
                            && data.getAsJsonObject("original")
                                    .get("registry")
                                    .getAsString()
                                    .equals("water")) fluidQuery = true;
                }
                if (!fluidQuery)
                    throw new AssertionError(
                            "Actual material fluid-to-filled-cell query path missing");
                if (records.toString().contains("neiDiagramLookup")
                        && reader.counts().get("diagrams").getAsInt() == 4)
                    System.out.println(
                            "Native material-parts generate/getDiagram callback and finite controlled-material closure passed");
                else throw new AssertionError("Original material callback/finite closure missing");
                record(reader);
            }
            net.minecraft.item.crafting.CraftingManager.getInstance()
                    .getRecipeList()
                    .add(
                            new net.minecraft.item.crafting.ShapelessRecipes(
                                    tool.copy(),
                                    Collections.singletonList(new ItemStack(Items.paper))));
            Object toolGenerator =
                    type("com.github.dcysteine.neicustomdiagram.generators.gregtech5.materialtools.GregTechMaterialTools")
                            .getConstructor(String.class)
                            .newInstance(NativeGtDiagramReferences.PREFIX + "materialtools");
            Object toolGroup = call(toolGenerator, "generate");
            JsonObject toolSource =
                    object(
                            "owner",
                            "neicustomdiagram",
                            "handler",
                            toolGroup.getClass().getName(),
                            "key",
                            NativeGtDiagramReferences.PREFIX + "materialtools");
            try (NativeGtDiagramReferences reader =
                    new NativeGtDiagramReferences(
                            (codechicken.nei.recipe.ICraftingHandler) toolGroup, toolSource)) {
                reader.finishVerification();
                if (reader.counts().get("diagrams").getAsInt() != 4)
                    throw new AssertionError("Tool material finite closure differs");
                JsonArray records = (JsonArray) field(field(reader, "snapshot"), "records");
                NativeDiagramContents contents=(NativeDiagramContents)field(field(reader,"snapshot"),"contents");
                boolean toolQuery = false;
                for (JsonElement record : records) {
                    JsonObject data = record.getAsJsonObject().getAsJsonObject("data");
                    if (data.get("kind").getAsString().equals("neiDiagramLookup")
                            &&data.getAsJsonObject("original").get("kind").getAsString().equals("itemFact")){
                        ItemStack offered=contents.items.get(data.getAsJsonObject("original").get("fact").getAsString());
                        if(offered!=null&&offered.hasTagCompound()&&offered.getTagCompound().getCompoundTag("GT.ToolStats").hasKey("PrimaryMaterial"))toolQuery=true;
                    }
                }
                if (!toolQuery)
                    throw new AssertionError("Native tool primary-material NBT path missing");
                System.out.println(
                        "Native material-tools generate/getDiagram callback, actual tool NBT, ore/cell query and controlled finite-material closure passed");
                record(reader);
            }
            for (Object item : items.getEnumConstants()) {
                String name = ((Enum<?>) item).name();
                if (name.startsWith("Circuit_")
                        || name.startsWith("Machine_HV_")
                        || name.equals("Machine_Multi_Assemblyline")
                        || name.equals("Machine_Multi_Cleanroom")
                        || name.equals("NandChip")
                        || name.equals("NandChipArray"))
                    itemList(
                            items,
                            name,
                            new ItemStack(Items.paper, 1, 400 + ((Enum<?>) item).ordinal()));
            }
            type("ic2.core.Ic2Items")
                    .getField("electronicCircuit")
                    .set(null, new ItemStack(Items.redstone));
            type("ic2.core.Ic2Items")
                    .getField("advancedCircuit")
                    .set(null, new ItemStack(Items.repeater));
            type("gregtech.api.GregTechAPI").getField("sPreloadStarted").setBoolean(null, true);
            Object circuitGenerator =
                    type("com.github.dcysteine.neicustomdiagram.generators.gregtech5.circuits.GregTechCircuits")
                            .getConstructor(String.class)
                            .newInstance(NativeGtDiagramReferences.PREFIX + "circuits");
            Object circuitGroup = call(circuitGenerator, "generate");
            JsonObject circuitSource =
                    object(
                            "owner",
                            "neicustomdiagram",
                            "handler",
                            circuitGroup.getClass().getName(),
                            "key",
                            NativeGtDiagramReferences.PREFIX + "circuits");
            try (NativeGtDiagramReferences reader =
                    new NativeGtDiagramReferences(
                            (codechicken.nei.recipe.ICraftingHandler) circuitGroup,
                            circuitSource)) {
                reader.finishVerification();
                JsonArray records = (JsonArray) field(field(reader, "snapshot"), "records");
                JsonArray action =
                        records.get(records.size() - 1)
                                .getAsJsonObject()
                                .getAsJsonObject("data")
                                .getAsJsonArray("diagrams");
                if (action.size() != 1
                        || reader.counts().get("diagrams").getAsInt() <= action.size())
                    throw new AssertionError(
                            "Original circuit overview was replaced by all recipe diagrams");
                System.out.println(
                        "Native circuits generate, recipe diagrams and distinct original overview supplier passed");
                record(reader);
            }
            ItemStack lens = new ItemStack(Items.glass_bottle, 1, 63);
            invoke(
                    unificator,
                    null,
                    "set",
                    new Class<?>[] {prefixes, materials, ItemStack.class},
                    field(prefixes, null, "lens"),
                    emerald,
                    lens);
            net.minecraftforge.oredict.OreDictionary.registerOre("lensEmerald", lens);
            net.minecraftforge.oredict.OreDictionary.registerOre("craftingLensGreen", lens);
            gregtech.api.util.GTRecipe laser =
                    new gregtech.api.util.GTRecipe(
                            false,
                            new ItemStack[] {new ItemStack(Items.paper, 3), lens.copy()},
                            new ItemStack[] {new ItemStack(Items.diamond, 2)},
                            null,
                            null,
                            new int[] {10000},
                            null,
                            null,
                            new net.minecraftforge.fluids.FluidStack[0],
                            new net.minecraftforge.fluids.FluidStack[0],
                            40,
                            16,
                            0);
            Object laserMap =
                    field(type("gregtech.api.recipe.RecipeMaps"), null, "laserEngraverRecipes");
            invoke(
                    laserMap.getClass(),
                    laserMap,
                    "addRecipe",
                    new Class<?>[] {gregtech.api.util.GTRecipe.class},
                    laser);
            codechicken.nei.ItemList.items =
                    Arrays.asList(
                            part,
                            cell,
                            tool,
                            lens,
                            new ItemStack(Items.paper),
                            new ItemStack(Items.diamond));
            Object lensGenerator =
                    type("com.github.dcysteine.neicustomdiagram.generators.gregtech5.lenses.GregTechLenses")
                            .getConstructor(String.class)
                            .newInstance(NativeGtDiagramReferences.PREFIX + "lenses");
            Object lensGroup = call(lensGenerator, "generate");
            JsonObject lensSource =
                    object(
                            "owner",
                            "neicustomdiagram",
                            "handler",
                            lensGroup.getClass().getName(),
                            "key",
                            NativeGtDiagramReferences.PREFIX + "lenses");
            try (NativeGtDiagramReferences reader =
                    new NativeGtDiagramReferences(
                            (codechicken.nei.recipe.ICraftingHandler) lensGroup, lensSource)) {
                reader.finishVerification();
                if (reader.counts().get("diagrams").getAsInt() == 0)
                    throw new AssertionError("Native laser/lens fixture was not registered");
                JsonArray records = (JsonArray) field(field(reader, "snapshot"), "records");
                if (!records.toString().contains("lensRecipe"))
                    throw new AssertionError("Native lens recipe relationship missing");
                System.out.println(
                        "Native lenses generate, laser recipe/component bindings and factory relationships passed");
                record(reader);
            }
            net.minecraft.block.Block oreBlock =
                    (net.minecraft.block.Block)
                            type("gregtech.common.blocks.BlockOres").newInstance();
            ItemStack rawOre =
                    new ItemStack(oreBlock, 1, (Integer) field(aluminium, "mMetaItemSubID"));
            ItemStack crushed = new ItemStack(Items.glowstone_dust, 2);
            net.minecraftforge.oredict.OreDictionary.registerOre("oreAluminium", rawOre);
            invoke(
                    unificator,
                    null,
                    "addAssociation",
                    new Class<?>[] {prefixes, materials, ItemStack.class, boolean.class},
                    field(prefixes, null, "ore"),
                    aluminium,
                    rawOre,
                    false);
            invoke(
                    unificator,
                    null,
                    "set",
                    new Class<?>[] {prefixes, materials, ItemStack.class},
                    field(prefixes, null, "crushed"),
                    aluminium,
                    crushed);
            gregtech.api.util.GTRecipe macerate =
                    new gregtech.api.util.GTRecipe(
                            false,
                            new ItemStack[] {rawOre.copy()},
                            new ItemStack[] {crushed.copy()},
                            null,
                            null,
                            new int[] {10000},
                            null,
                            null,
                            new net.minecraftforge.fluids.FluidStack[0],
                            new net.minecraftforge.fluids.FluidStack[0],
                            40,
                            16,
                            0);
            Object maceratorMap =
                    field(type("gregtech.api.recipe.RecipeMaps"), null, "maceratorRecipes");
            invoke(
                    maceratorMap.getClass(),
                    maceratorMap,
                    "addRecipe",
                    new Class<?>[] {gregtech.api.util.GTRecipe.class},
                    macerate);
            codechicken.nei.ItemList.items =
                    Arrays.asList(
                            part,
                            cell,
                            tool,
                            lens,
                            rawOre,
                            crushed,
                            new ItemStack(Items.paper),
                            new ItemStack(Items.diamond));
            for (String name : Arrays.asList("Mercury", "SodiumPersulfate")) {
                net.minecraftforge.fluids.Fluid fluid =
                        new net.minecraftforge.fluids.Fluid(
                                "fixture.diagram." + name.toLowerCase(Locale.ROOT));
                net.minecraftforge.fluids.FluidRegistry.registerFluid(fluid);
                materials.getField("mFluid").set(field(materials, null, name), fluid);
            }
            invoke(
                    unificator,
                    null,
                    "set",
                    new Class<?>[] {prefixes, materials, ItemStack.class},
                    field(prefixes, null, "dust"),
                    field(materials, null, "Stone"),
                    new ItemStack(Items.flint));
            Object oreGenerator =
                    type("com.github.dcysteine.neicustomdiagram.generators.gregtech5.oreprocessing.GregTechOreProcessing")
                            .getConstructor(String.class)
                            .newInstance(NativeGtDiagramReferences.PREFIX + "oreprocessing");
            Object oreGroup = call(oreGenerator, "generate");
            JsonObject oreSource =
                    object(
                            "owner",
                            "neicustomdiagram",
                            "handler",
                            oreGroup.getClass().getName(),
                            "key",
                            NativeGtDiagramReferences.PREFIX + "oreprocessing");
            try (NativeGtDiagramReferences reader =
                    new NativeGtDiagramReferences(
                            (codechicken.nei.recipe.ICraftingHandler) oreGroup, oreSource)) {
                reader.finishVerification();
                if (reader.counts().get("diagrams").getAsInt() == 0)
                    throw new AssertionError(
                            "Native ore generator missed the genuine registered ore block");
                JsonArray records = (JsonArray) field(field(reader, "snapshot"), "records");
                if (!records.toString().contains("oreStage"))
                    throw new AssertionError("Native ore maceration relationship missing");
                System.out.println(
                        "Native oreprocessing generate, genuine BlockOres, macerator registry and factory stage relationships passed");
                record(reader);
            }
        } finally {
            nativeMaterials.clear();
            nativeMaterials.putAll(saved);
        }
    }

    private static void itemList(Class<?> items, String name, ItemStack stack) {
        Object item = Enum.valueOf((Class) items, name);
        invoke(items, item, "set", new Class<?>[] {ItemStack.class}, stack);
    }

    private static void record(NativeGtDiagramReferences reader) {
        Object snapshot = field(reader, "snapshot");
        JsonObject definition=((JsonArray)field(snapshot,"records")).get(0).getAsJsonObject().getAsJsonObject("data");
        if(definition.get("name").getAsString().startsWith("neicustomdiagram."))
            throw new AssertionError("Native group name was not translated from actual installed English resources");
        domains.add((JsonArray) field(snapshot, "records"));
        NativeDiagramContents contents = (NativeDiagramContents) field(snapshot, "contents");
        for (Map.Entry<String, ItemStack> entry : contents.items.entrySet()) {
            ItemStack stack = entry.getValue();
            itemFacts.put(
                    entry.getKey(),
                    object(
                            "id",
                            entry.getKey(),
                            "registry",
                            net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()),
                            "meta",
                            Items.feather.getDamage(stack),
                            "nbt",
                            TypedNbt.encode(stack.getTagCompound()),
                            "tags",
                            array(),
                            "durability",
                            stack.getMaxDamage()));
        }
        for (Map.Entry<String, net.minecraftforge.fluids.FluidStack> entry :
                contents.fluids.entrySet()) {
            net.minecraftforge.fluids.FluidStack stack = entry.getValue();
            net.minecraftforge.fluids.Fluid fluid = stack.getFluid();
            fluidFacts.put(
                    entry.getKey(),
                    object(
                            "id",
                            entry.getKey(),
                            "registry",
                            fluid.getName(),
                            "nbt",
                            TypedNbt.encode(stack.tag),
                            "temperature",
                            fluid.getTemperature(stack),
                            "density",
                            fluid.getDensity(stack),
                            "viscosity",
                            fluid.getViscosity(stack),
                            "luminosity",
                            fluid.getLuminosity(stack),
                            "gaseous",
                            fluid.isGaseous(stack)));
        }
    }
}
