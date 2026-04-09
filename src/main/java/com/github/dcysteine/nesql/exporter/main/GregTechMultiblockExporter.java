package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import cpw.mods.fml.relauncher.FMLInjectionData;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Exports first-pass GregTech multiblock blueprints for the web 3D viewer.
 *
 * <p>This intentionally starts with curated canonical controllers so the frontend can consume
 * exported voxel structures instead of relying on backend-only manual overrides.</p>
 */
public final class GregTechMultiblockExporter {
    private static final String REPOSITORY_PATH_FORMAT_STRING = "nesql" + File.separator + "%s";
    private static final String OUTPUT_RELATIVE_PATH =
            "multiblocks" + File.separator + "gregtech-multiblocks.json.gz";

    private final String repositoryName;
    private final File repositoryDirectory;

    public GregTechMultiblockExporter() {
        this(ConfigOptions.REPOSITORY_NAME.get());
    }

    public GregTechMultiblockExporter(String repositoryName) {
        this.repositoryName = repositoryName;
        this.repositoryDirectory =
                new File(
                        (File) FMLInjectionData.data()[6],
                        String.format(REPOSITORY_PATH_FORMAT_STRING, repositoryName));
    }

    public void exportReportException() {
        try {
            export();
        } catch (Exception e) {
            Logger.MOD.error("GregTech multiblock export failed", e);
            Logger.chatMessage(EnumChatFormatting.RED + "GregTech multiblock export failed: " + e.getMessage());
        }
    }

    public void export() throws Exception {
        Logger.MOD.info("============================================================");
        Logger.MOD.info("=== NESQL GregTech Multiblock Export STARTED ===");
        Logger.MOD.info("============================================================");
        Logger.MOD.info("Repository: {}", repositoryDirectory.getAbsolutePath());

        Logger.chatMessage(EnumChatFormatting.AQUA + "Starting GregTech multiblock export...");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "Repository: " + repositoryName);

        if (!repositoryDirectory.exists() && !repositoryDirectory.mkdirs()) {
            throw new IllegalStateException("Failed to create repository directory: " + repositoryDirectory);
        }

        File multiblockDir = new File(repositoryDirectory, "multiblocks");
        if (!multiblockDir.exists() && !multiblockDir.mkdirs()) {
            throw new IllegalStateException("Failed to create multiblocks directory: " + multiblockDir);
        }

        ExportPayload payload = new ExportPayload();
        payload.generatedAtEpochMs = System.currentTimeMillis();
        payload.blueprints.addAll(buildCuratedBlueprints());
        payload.totalMetaTileEntitiesScanned = payload.blueprints.size();
        payload.totalMultiblocksExported = payload.blueprints.size();
        payload.failedControllers = 0;

        File output = new File(repositoryDirectory, OUTPUT_RELATIVE_PATH);
        writeGzipJson(output, payload);

        Logger.MOD.info("GregTech multiblock export complete: {}", output.getAbsolutePath());
        Logger.MOD.info("Exported blueprints: {}", payload.blueprints.size());
        Logger.chatMessage(EnumChatFormatting.GREEN + "GregTech multiblock export complete!");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "Output: " + output.getAbsolutePath());
        Logger.chatMessage(EnumChatFormatting.YELLOW + "Blueprints exported: " + payload.blueprints.size());
    }

    private static void writeGzipJson(File output, ExportPayload payload) throws Exception {
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        try (FileOutputStream fos = new FileOutputStream(output);
             GZIPOutputStream gzip = new GZIPOutputStream(fos);
             OutputStreamWriter writer = new OutputStreamWriter(gzip, StandardCharsets.UTF_8)) {
            gson.toJson(payload, writer);
        }
    }

    private List<MultiblockBlueprint> buildCuratedBlueprints() {
        List<MultiblockBlueprint> blueprints = new ArrayList<>();
        blueprints.add(buildBrickedBlastFurnace());
        blueprints.add(buildElectricBlastFurnace());
        blueprints.add(buildVacuumFreezer());
        blueprints.add(buildDistillationTower());
        blueprints.add(buildProcessingArray());
        blueprints.add(buildAssemblyLineDescriptor());
        blueprints.add(buildCleanroomDescriptor());
        blueprints.add(buildImplosionCompressor());
        blueprints.add(buildMultiSmelter());
        blueprints.add(buildPyrolyseOven());
        blueprints.add(buildIndustrialExtractor());
        blueprints.add(buildOilCracker());
        blueprints.add(buildHeatExchanger());
        for (MultiblockBlueprint blueprint : blueprints) {
            enrichFromStructureDefinition(blueprint);
            enrichFromRuntimeTooltip(blueprint);
            enrichOptionalFeatures(blueprint);
        }
        return blueprints;
    }

    private MultiblockBlueprint buildBrickedBlastFurnace() {
        MultiblockBlueprint blueprint = baseBlueprint(
                140,
                "gregtech.common.tileentities.machines.multi.GT_MetaTileEntity_BrickedBlastFurnace",
                "i~gregtech~gt.blockmachines~140",
                "Bricked Blast Furnace");
        blueprint.dimensions = new Dimensions(3, 4, 3, "Size: 3x4x3 (WxHxL)");
        blueprint.structureSource = "builtin_registry";
        blueprint.structureInformation.add("Size: 3x4x3 (WxHxL)");
        blueprint.structureInformation.add("Controller: Front center (middle layer)");
        blueprint.structureInformation.add("Main body: Bricked Casing shell");
        blueprint.structureInformation.add("Inner cavity: center column (3 air blocks)");
        blueprint.structureHints.add("Top center must stay empty.");
        blueprint.voxelBlueprint = new VoxelBlueprint();
        blueprint.voxelBlueprint.size = new Dimensions(3, 4, 3, null);
        blueprint.voxelBlueprint.legend = new LinkedHashMap<>();
        blueprint.voxelBlueprint.legend.put("X",
                legend("Controller", "#f59e0b", "/images/item/gregtech/gt.blockmachines~140.png", "i~gregtech~gt.blockmachines~140"));
        blueprint.voxelBlueprint.legend.put("C",
                legend("Bricked Casing", "#ef4444", "/images/item/etfuturum/blast_furnace~0.png", "i~etfuturum~blast_furnace~0"));
        blueprint.voxelBlueprint.legend.put("A",
                legend("Air", "#0ea5e9", null, null));
        blueprint.voxelBlueprint.layers = new ArrayList<>();
        blueprint.voxelBlueprint.layers.add(layer("CCC", "CCC", "CCC"));
        blueprint.voxelBlueprint.layers.add(layer("CXC", "CAC", "CCC"));
        blueprint.voxelBlueprint.layers.add(layer("CCC", "CAC", "CCC"));
        blueprint.voxelBlueprint.layers.add(layer("CCC", "CAC", "CCC"));
        return blueprint;
    }

    private MultiblockBlueprint buildElectricBlastFurnace() {
        MultiblockBlueprint blueprint = baseBlueprint(
                1000,
                "gregtech.common.tileentities.machines.multi.GT_MetaTileEntity_ElectricBlastFurnace",
                "i~gregtech~gt.blockmachines~1000",
                "Electric Blast Furnace");
        blueprint.dimensions = new Dimensions(3, 4, 3, "Size: 3x4x3 (WxHxL)");
        blueprint.structureSource = "builtin_registry";
        blueprint.supports = new OptionalFeatures();
        blueprint.supports.inputSeparation = true;
        blueprint.supports.batchMode = true;
        blueprint.structureInformation.add("Size: 3x4x3 (WxHxL)");
        blueprint.structureInformation.add("Controller: Front center");
        blueprint.structureInformation.add("Bottom layer: Heatproof casing shell");
        blueprint.structureInformation.add("Middle two layers: Heating Coil ring (center empty)");
        blueprint.structureInformation.add("Top center: Muffler Hatch slot");
        blueprint.structureHints.add("Input/Output buses and hatches can replace valid casing blocks.");
        blueprint.voxelBlueprint = new VoxelBlueprint();
        blueprint.voxelBlueprint.size = new Dimensions(3, 4, 3, null);
        blueprint.voxelBlueprint.legend = new LinkedHashMap<>();
        blueprint.voxelBlueprint.legend.put("X",
                legend("Controller", "#f59e0b", "/images/item/gregtech/gt.blockmachines~1000.png", "i~gregtech~gt.blockmachines~1000"));
        blueprint.voxelBlueprint.legend.put("C",
                legend("Heatproof Casing", "#22c55e", "/images/item/gregtech/gt.blockcasings~11.png", "i~gregtech~gt.blockcasings~11"));
        blueprint.voxelBlueprint.legend.put("H",
                legend("Heating Coil", "#f97316", "/images/item/gregtech/gt.blockcasings5~0.png", "i~gregtech~gt.blockcasings5~0"));
        blueprint.voxelBlueprint.legend.put("M",
                legend("Muffler Hatch Slot", "#60a5fa", null, null));
        blueprint.voxelBlueprint.legend.put("A",
                legend("Air", "#0ea5e9", null, null));
        blueprint.voxelBlueprint.layers = new ArrayList<>();
        blueprint.voxelBlueprint.layers.add(layer("CXC", "CCC", "CCC"));
        blueprint.voxelBlueprint.layers.add(layer("HHH", "HAH", "HHH"));
        blueprint.voxelBlueprint.layers.add(layer("HHH", "HAH", "HHH"));
        blueprint.voxelBlueprint.layers.add(layer("CCC", "CMC", "CCC"));
        return blueprint;
    }

    private MultiblockBlueprint buildVacuumFreezer() {
        MultiblockBlueprint blueprint = buildSimpleCubicShellBlueprint(
                1002,
                "gregtech.common.tileentities.machines.multi.MTEVacuumFreezer",
                "i~gregtech~gt.blockmachines~1002",
                "Vacuum Freezer",
                "Frost Proof Machine Casing",
                "#60a5fa",
                "/images/item/gregtech/gt.blockcasings2~1.png",
                "i~gregtech~gt.blockcasings2~1");
        blueprint.supports = new OptionalFeatures();
        blueprint.supports.batchMode = true;
        blueprint.supports.voidProtection = true;
        blueprint.structureInformation.add("Size: 3x3x3 (WxHxL)");
        blueprint.structureInformation.add("Controller: Front center");
        blueprint.structureInformation.add("Main body: Frost Proof Machine Casing shell");
        blueprint.structureInformation.add("Inner cavity: 1 air block");
        blueprint.structureHints.add("Energy hatch, maintenance hatch, input/output hatches, and buses can replace any valid casing.");
        return blueprint;
    }

    private MultiblockBlueprint buildProcessingArray() {
        MultiblockBlueprint blueprint = buildSimpleCubicShellBlueprint(
                1199,
                "gregtech.common.tileentities.machines.multi.MTEProcessingArray",
                "i~gregtech~gt.blockmachines~1199",
                "Processing Array",
                "Robust Tungstensteel Machine Casing",
                "#8b5cf6",
                "/images/item/gregtech/gt.blockcasings4~0.png",
                "i~gregtech~gt.blockcasings4~0");
        blueprint.supports = new OptionalFeatures();
        blueprint.supports.inputSeparation = true;
        blueprint.supports.batchMode = true;
        blueprint.supports.recipeLocking = true;
        blueprint.supports.voidProtection = true;
        blueprint.structureInformation.add("Size: 3x3x3 (WxHxL)");
        blueprint.structureInformation.add("Controller: Front center");
        blueprint.structureInformation.add("Main body: Robust Tungstensteel Machine Casing shell");
        blueprint.structureInformation.add("Inner cavity: 1 air block");
        blueprint.structureHints.add("All hatches and buses may replace any valid casing.");
        blueprint.structureHints.add("Insert up to 64 identical single-block machines to batch that recipe map.");
        return blueprint;
    }

    private MultiblockBlueprint buildDistillationTower() {
        MultiblockBlueprint blueprint = baseBlueprint(
                1126,
                "gregtech.common.tileentities.machines.multi.MTEDistillationTower",
                "i~gregtech~gt.blockmachines~1126",
                "Distillation Tower");
        blueprint.dimensions = new Dimensions(3, 3, 3, "Footprint: 3x3, height 3-12 (minimum blueprint shown)");
        blueprint.structureSource = "builtin_registry";
        blueprint.structureInformation.add("Footprint: 3x3, height 3-12");
        blueprint.structureInformation.add("Controller: Front bottom");
        blueprint.structureInformation.add("Main body: Clean Stainless Steel Machine Casing tower");
        blueprint.structureInformation.add("Each extra tower level repeats the middle 3x3 layer.");
        blueprint.structureInformation.add("At least one output hatch is required on every non-bottom layer.");
        blueprint.structureHints.add("The exported voxel blueprint shows the minimum valid 3-layer tower.");
        blueprint.structureHints.add("Add more middle layers to increase height; the top cap stays a full 3x3 casing layer.");
        blueprint.structureHints.add("Fluid outputs must be placed at the layer matching the NEI output slot height.");
        blueprint.voxelBlueprint = new VoxelBlueprint();
        blueprint.voxelBlueprint.size = new Dimensions(3, 3, 3, null);
        blueprint.voxelBlueprint.legend = new LinkedHashMap<>();
        blueprint.voxelBlueprint.legend.put(
                "X",
                legend(
                        "Controller",
                        "#f59e0b",
                        toItemTextureUrl("i~gregtech~gt.blockmachines~1126"),
                        "i~gregtech~gt.blockmachines~1126"));
        blueprint.voxelBlueprint.legend.put(
                "B",
                legend(
                        "Bottom Casing / Hatch Slot",
                        "#94a3b8",
                        "/images/item/gregtech/gt.blockcasings4~1.png",
                        "i~gregtech~gt.blockcasings4~1"));
        blueprint.voxelBlueprint.legend.put(
                "L",
                legend(
                        "Tower Casing",
                        "#cbd5e1",
                        "/images/item/gregtech/gt.blockcasings4~1.png",
                        "i~gregtech~gt.blockcasings4~1"));
        blueprint.voxelBlueprint.legend.put(
                "O",
                legend(
                        "Output Hatch Column",
                        "#38bdf8",
                        "/images/item/gregtech/gt.blockcasings4~1.png",
                        "i~gregtech~gt.blockcasings4~1"));
        blueprint.voxelBlueprint.legend.put(
                "T",
                legend(
                        "Top Casing",
                        "#e2e8f0",
                        "/images/item/gregtech/gt.blockcasings4~1.png",
                        "i~gregtech~gt.blockcasings4~1"));
        blueprint.voxelBlueprint.layers = new ArrayList<>();
        blueprint.voxelBlueprint.layers.add(layer("BXB", "BBB", "BBB"));
        blueprint.voxelBlueprint.layers.add(layer("LLL", "LOL", "LLL"));
        blueprint.voxelBlueprint.layers.add(layer("TTT", "TTT", "TTT"));
        blueprint.segmentBlueprint = segmentBlueprint(
                "y",
                3,
                12,
                segment("base", 3, 1, 3, layer("BXB", "BBB", "BBB")),
                segment("repeat", 3, 1, 3, layer("LLL", "LOL", "LLL")),
                segment("top", 3, 1, 3, layer("TTT", "TTT", "TTT")));
        return blueprint;
    }

    private MultiblockBlueprint buildAssemblyLineDescriptor() {
        MultiblockBlueprint blueprint = baseBlueprint(
                1170,
                "gregtech.common.tileentities.machines.multi.MTEAssemblyLine",
                "i~gregtech~gt.blockmachines~1170",
                "Assembly Line");
        blueprint.dimensions = new Dimensions(3, 4, 3, "Footprint: 3x4, length 2-16 slices (3-slice example shown)");
        blueprint.structureSource = "builtin_registry";
        blueprint.supports = new OptionalFeatures();
        blueprint.supports.voidProtection = true;
        blueprint.supports.batchMode = true;
        blueprint.structureInformation.add("Footprint: 3x4, length 2-16 slices");
        blueprint.structureInformation.add("Controller: Replaces the grate position in the first slice.");
        blueprint.structureInformation.add("Each extra slice extends the line by one recipe item slot.");
        blueprint.structureInformation.add("Layer 1: Solid Steel casing with input bus lane (last slice may host output bus).");
        blueprint.structureInformation.add("Layer 2: Glass / Assembly Line Casing / Glass.");
        blueprint.structureInformation.add("Layer 3: Grate / Assembler Machine Casing / Grate.");
        blueprint.structureInformation.add("Layer 4: Air / Solid Steel casing-energy slot / Air.");
        blueprint.structureHints.add("The exported voxel blueprint shows a 3-slice example: first + middle + last.");
        blueprint.structureHints.add("Input buses occupy the lane center on bottom layer; the last slice may swap to an output bus.");
        blueprint.structureHints.add("Data Access Hatch is optional and sits next to the controller in the first slice.");
        blueprint.segmentBlueprint = segmentBlueprint(
                "z",
                2,
                16,
                segment("first", 3, 4, 1, layer("BIB"), layer("DLD"), layer("XMG"), layer("AEA")),
                segment("repeat", 3, 4, 1, layer("BIB"), layer("DLD"), layer("GMG"), layer("AEA")),
                segment("last", 3, 4, 1, layer("BOB"), layer("DLD"), layer("GMG"), layer("AEA")));
        blueprint.voxelBlueprint = new VoxelBlueprint();
        blueprint.voxelBlueprint.size = new Dimensions(3, 4, 3, null);
        blueprint.voxelBlueprint.legend = new LinkedHashMap<>();
        blueprint.voxelBlueprint.legend.put(
                "A",
                legend("Air", "#0ea5e9", null, null));
        blueprint.voxelBlueprint.legend.put(
                "E",
                legend(
                        "Energy Hatch / Solid Steel Casing",
                        "#facc15",
                        "/images/item/gregtech/gt.blockcasings2~0.png",
                        "i~gregtech~gt.blockcasings2~0"));
        blueprint.voxelBlueprint.legend.put(
                "B",
                legend(
                        "Solid Steel Casing / Hatch Slot",
                        "#94a3b8",
                        "/images/item/gregtech/gt.blockcasings2~0.png",
                        "i~gregtech~gt.blockcasings2~0"));
        blueprint.voxelBlueprint.legend.put(
                "I",
                legend("Input Bus Lane", "#22c55e", null, null));
        blueprint.voxelBlueprint.legend.put(
                "O",
                legend("Output Bus Lane", "#38bdf8", null, null));
        blueprint.voxelBlueprint.legend.put(
                "D",
                legend("Glass", "#7dd3fc", null, null));
        blueprint.voxelBlueprint.legend.put(
                "L",
                legend(
                        "Assembly Line Casing",
                        "#a78bfa",
                        "/images/item/gregtech/gt.blockcasings2~9.png",
                        "i~gregtech~gt.blockcasings2~9"));
        blueprint.voxelBlueprint.legend.put(
                "G",
                legend(
                        "Grate Machine Casing",
                        "#64748b",
                        "/images/item/gregtech/gt.blockcasings3~10.png",
                        "i~gregtech~gt.blockcasings3~10"));
        blueprint.voxelBlueprint.legend.put(
                "M",
                legend(
                        "Assembler Machine Casing",
                        "#f97316",
                        "/images/item/gregtech/gt.blockcasings2~5.png",
                        "i~gregtech~gt.blockcasings2~5"));
        blueprint.voxelBlueprint.legend.put(
                "X",
                legend(
                        "Controller",
                        "#f59e0b",
                        toItemTextureUrl("i~gregtech~gt.blockmachines~1170"),
                        "i~gregtech~gt.blockmachines~1170"));
        blueprint.voxelBlueprint.layers = new ArrayList<>();
        blueprint.voxelBlueprint.layers.add(layer("BIB", "BIB", "BOB"));
        blueprint.voxelBlueprint.layers.add(layer("DLD", "DLD", "DLD"));
        blueprint.voxelBlueprint.layers.add(layer("XMG", "GMG", "GMG"));
        blueprint.voxelBlueprint.layers.add(layer("AEA", "AEA", "AEA"));
        return blueprint;
    }

    private MultiblockBlueprint buildCleanroomDescriptor() {
        MultiblockBlueprint blueprint = baseBlueprint(
                1172,
                "gregtech.common.tileentities.machines.multi.MTECleanroom",
                "i~gregtech~gt.blockmachines~1172",
                "Cleanroom");
        blueprint.dimensions = new Dimensions(3, 4, 3, "Footprint: 3x3 to 15x15, height 4-15 (minimum room shown)");
        blueprint.structureSource = "builtin_registry";
        blueprint.structureInformation.add("Footprint: 3x3 to 15x15, height 4-15");
        blueprint.structureInformation.add("Controller: Top center");
        blueprint.structureInformation.add("Top layer interior: Filter Machine Casing");
        blueprint.structureInformation.add("Walls and floor: Plascrete shell");
        blueprint.structureInformation.add("Interior: hollow cleanroom space");
        blueprint.structureHints.add("The exported voxel blueprint shows the minimum valid room.");
        blueprint.structureHints.add("Energy hatch, maintenance hatch, and one door may replace valid plascrete blocks.");
        blueprint.structureHints.add("Expansion grows width and length symmetrically while preserving the top-center controller.");
        blueprint.voxelBlueprint = new VoxelBlueprint();
        blueprint.voxelBlueprint.size = new Dimensions(3, 4, 3, null);
        blueprint.voxelBlueprint.legend = new LinkedHashMap<>();
        blueprint.voxelBlueprint.legend.put(
                "X",
                legend(
                        "Controller",
                        "#f59e0b",
                        toItemTextureUrl("i~gregtech~gt.blockmachines~1172"),
                        "i~gregtech~gt.blockmachines~1172"));
        blueprint.voxelBlueprint.legend.put(
                "P",
                legend(
                        "Plascrete Shell",
                        "#9ca3af",
                        null,
                        null));
        blueprint.voxelBlueprint.legend.put(
                "F",
                legend(
                        "Filter Machine Casing",
                        "#06b6d4",
                        "/images/item/gregtech/gt.blockcasings3~11.png",
                        "i~gregtech~gt.blockcasings3~11"));
        blueprint.voxelBlueprint.legend.put(
                "A",
                legend("Air", "#0ea5e9", null, null));
        blueprint.voxelBlueprint.layers = new ArrayList<>();
        blueprint.voxelBlueprint.layers.add(layer("PPP", "PPP", "PPP"));
        blueprint.voxelBlueprint.layers.add(layer("PAP", "PAP", "PPP"));
        blueprint.voxelBlueprint.layers.add(layer("PAP", "PAP", "PPP"));
        blueprint.voxelBlueprint.layers.add(layer("PPP", "FXF", "PPP"));
        return blueprint;
    }

    private MultiblockBlueprint buildImplosionCompressor() {
        MultiblockBlueprint blueprint = buildSimpleCubicShellBlueprint(
                1001,
                "gregtech.common.tileentities.machines.multi.MTEImplosionCompressor",
                "i~gregtech~gt.blockmachines~1001",
                "Implosion Compressor",
                "Solid Steel Machine Casing",
                "#94a3b8",
                "/images/item/gregtech/gt.blockcasings2~0.png",
                "i~gregtech~gt.blockcasings2~0");
        blueprint.supports = new OptionalFeatures();
        blueprint.supports.voidProtection = true;
        blueprint.structureInformation.add("Size: 3x3x3 (WxHxL)");
        blueprint.structureInformation.add("Controller: Front center");
        blueprint.structureInformation.add("Main body: Solid Steel Machine Casing shell");
        blueprint.structureInformation.add("Inner cavity: 1 air block");
        blueprint.structureHints.add("Explosion Warning Signs may replace valid casing blocks.");
        blueprint.structureHints.add("Energy hatch, maintenance hatch, muffler hatch, and buses may replace any valid casing.");
        return blueprint;
    }

    private MultiblockBlueprint buildMultiSmelter() {
        MultiblockBlueprint blueprint = baseBlueprint(
                1003,
                "gregtech.common.tileentities.machines.multi.MTEMultiFurnace",
                "i~gregtech~gt.blockmachines~1003",
                "Multi Smelter");
        blueprint.dimensions = new Dimensions(3, 3, 3, "Size: 3x3x3 (WxHxL)");
        blueprint.structureSource = "builtin_registry";
        blueprint.supports = new OptionalFeatures();
        blueprint.supports.batchMode = true;
        blueprint.structureInformation.add("Size: 3x3x3 (WxHxL)");
        blueprint.structureInformation.add("Controller: Front bottom");
        blueprint.structureInformation.add("Bottom shell: Heat Proof Machine Casing");
        blueprint.structureInformation.add("Middle layer: Heating coil ring with hollow center");
        blueprint.structureInformation.add("Top center: Muffler Hatch slot");
        blueprint.structureHints.add("Energy hatch, maintenance hatch, input bus, and output bus may replace any bottom casing.");
        blueprint.voxelBlueprint = new VoxelBlueprint();
        blueprint.voxelBlueprint.size = new Dimensions(3, 3, 3, null);
        blueprint.voxelBlueprint.legend = new LinkedHashMap<>();
        blueprint.voxelBlueprint.legend.put(
                "X",
                legend(
                        "Controller",
                        "#f59e0b",
                        toItemTextureUrl("i~gregtech~gt.blockmachines~1003"),
                        "i~gregtech~gt.blockmachines~1003"));
        blueprint.voxelBlueprint.legend.put(
                "B",
                legend(
                        "Bottom Casing / Hatch Slot",
                        "#94a3b8",
                        "/images/item/gregtech/gt.blockcasings1~11.png",
                        "i~gregtech~gt.blockcasings1~11"));
        blueprint.voxelBlueprint.legend.put(
                "C",
                legend("Heating Coil", "#f97316", null, null));
        blueprint.voxelBlueprint.legend.put(
                "M",
                legend("Muffler Hatch Slot", "#60a5fa", null, null));
        blueprint.voxelBlueprint.legend.put(
                "T",
                legend(
                        "Top Heat Proof Casing",
                        "#cbd5e1",
                        "/images/item/gregtech/gt.blockcasings1~11.png",
                        "i~gregtech~gt.blockcasings1~11"));
        blueprint.voxelBlueprint.legend.put("A", legend("Air", "#0ea5e9", null, null));
        blueprint.voxelBlueprint.layers = new ArrayList<>();
        blueprint.voxelBlueprint.layers.add(layer("BXB", "BBB", "BBB"));
        blueprint.voxelBlueprint.layers.add(layer("CCC", "CAC", "CCC"));
        blueprint.voxelBlueprint.layers.add(layer("TTT", "TMT", "TTT"));
        return blueprint;
    }

    private MultiblockBlueprint buildPyrolyseOven() {
        MultiblockBlueprint blueprint = baseBlueprint(
                1159,
                "gregtech.common.tileentities.machines.multi.MTEPyrolyseOven",
                "i~gregtech~gt.blockmachines~1159",
                "Pyrolyse Oven");
        blueprint.dimensions = new Dimensions(5, 4, 5, "Size: 5x4x5 (WxHxL)");
        blueprint.structureSource = "builtin_registry";
        blueprint.structureInformation.add("Size: 5x4x5 (WxHxL)");
        blueprint.structureInformation.add("Controller: Front center");
        blueprint.structureInformation.add("Outer shell: Pyrolyse Oven Casing");
        blueprint.structureInformation.add("Bottom center 3x1x3: Heating Coils");
        blueprint.structureInformation.add("Top center 3x1x3: input and muffler area");
        blueprint.structureHints.add("Energy hatch, maintenance hatch, output bus, and output hatch may replace bottom-layer casing.");
        blueprint.structureHints.add("Input bus, input hatch, and muffler hatch may replace the top-center 3x1x3 area.");
        blueprint.voxelBlueprint = new VoxelBlueprint();
        blueprint.voxelBlueprint.size = new Dimensions(5, 4, 5, null);
        blueprint.voxelBlueprint.legend = new LinkedHashMap<>();
        blueprint.voxelBlueprint.legend.put(
                "X",
                legend(
                        "Controller",
                        "#f59e0b",
                        toItemTextureUrl("i~gregtech~gt.blockmachines~1159"),
                        "i~gregtech~gt.blockmachines~1159"));
        blueprint.voxelBlueprint.legend.put(
                "B",
                legend(
                        "Bottom Casing / Hatch Slot",
                        "#94a3b8",
                        "/images/item/gregtech/gt.blockcasingsNH~2.png",
                        "i~gregtech~gt.blockcasingsNH~2"));
        blueprint.voxelBlueprint.legend.put(
                "C",
                legend("Heating Coil", "#f97316", null, null));
        blueprint.voxelBlueprint.legend.put(
                "P",
                legend(
                        "Pyrolyse Oven Casing",
                        "#cbd5e1",
                        "/images/item/gregtech/gt.blockcasingsNH~2.png",
                        "i~gregtech~gt.blockcasingsNH~2"));
        blueprint.voxelBlueprint.legend.put(
                "T",
                legend("Top Center Utility Area", "#60a5fa", null, null));
        blueprint.voxelBlueprint.legend.put("A", legend("Air", "#0ea5e9", null, null));
        blueprint.voxelBlueprint.layers = new ArrayList<>();
        blueprint.voxelBlueprint.layers.add(layer("BBXBB", "BCCCB", "BCCCB", "BCCCB", "BBBBB"));
        blueprint.voxelBlueprint.layers.add(layer("PPPPP", "PAAAP", "PAAAP", "PAAAP", "PPPPP"));
        blueprint.voxelBlueprint.layers.add(layer("PPPPP", "PAAAP", "PAAAP", "PAAAP", "PPPPP"));
        blueprint.voxelBlueprint.layers.add(layer("PPPPP", "PTTTP", "PTTTP", "PTTTP", "PPPPP"));
        return blueprint;
    }

    private MultiblockBlueprint buildIndustrialExtractor() {
        MultiblockBlueprint blueprint = baseBlueprint(
                3010,
                "gregtech.common.tileentities.machines.multi.MTEIndustrialExtractor",
                "i~gregtech~gt.blockmachines~3010",
                "Industrial Extractor");
        blueprint.dimensions = new Dimensions(5, 4, 5, "Size: 5x4x5 (WxHxL)");
        blueprint.structureSource = "builtin_registry";
        blueprint.structureInformation.add("Size: 5x4x5 (WxHxL)");
        blueprint.structureInformation.add("Controller: Front center");
        blueprint.structureInformation.add("Outer shell: Stainless Steel Machine Casing");
        blueprint.structureInformation.add("Core: Item Pipe casing spine with EV+ glass lanes");
        blueprint.structureHints.add("The machine gains 8 parallels per tier of the Item Pipe casing.");
        blueprint.structureHints.add("Energy hatch, maintenance hatch, input bus, output bus, input hatch, and output hatch may replace any stainless casing.");
        blueprint.voxelBlueprint = new VoxelBlueprint();
        blueprint.voxelBlueprint.size = new Dimensions(5, 4, 5, null);
        blueprint.voxelBlueprint.legend = new LinkedHashMap<>();
        blueprint.voxelBlueprint.legend.put(
                "X",
                legend(
                        "Controller",
                        "#f59e0b",
                        toItemTextureUrl("i~gregtech~gt.blockmachines~3010"),
                        "i~gregtech~gt.blockmachines~3010"));
        blueprint.voxelBlueprint.legend.put(
                "C",
                legend(
                        "Stainless Steel Casing",
                        "#94a3b8",
                        "/images/item/gregtech/gt.blockcasings4~1.png",
                        "i~gregtech~gt.blockcasings4~1"));
        blueprint.voxelBlueprint.legend.put(
                "B",
                legend("Item Pipe Casing", "#f97316", null, null));
        blueprint.voxelBlueprint.legend.put(
                "A",
                legend("EV+ Glass", "#7dd3fc", null, null));
        blueprint.voxelBlueprint.layers = new ArrayList<>();
        blueprint.voxelBlueprint.layers.add(layer("CCCCC", "C   C", "C   C", "C   C", "CCXCC"));
        blueprint.voxelBlueprint.layers.add(layer("CCCCC", " BBB ", " AAA ", " BBB ", "CCCCC"));
        blueprint.voxelBlueprint.layers.add(layer("CCCCC", " BBB ", " ABA ", " BBB ", "CCCCC"));
        blueprint.voxelBlueprint.layers.add(layer("CCCCC", " BBB ", " AAA ", " BBB ", "CCCCC"));
        return blueprint;
    }

    private MultiblockBlueprint buildOilCracker() {
        MultiblockBlueprint blueprint = baseBlueprint(
                1160,
                "gregtech.common.tileentities.machines.multi.MTEOilCracker",
                "i~gregtech~gt.blockmachines~1160",
                "Oil Cracker");
        blueprint.dimensions = new Dimensions(5, 3, 3, "Size: 5x3x3 (WxHxL)");
        blueprint.structureSource = "builtin_registry";
        blueprint.structureInformation.add("Size: 5x3x3 (WxHxL)");
        blueprint.structureInformation.add("Controller: Front center");
        blueprint.structureInformation.add("Main body: Clean Stainless Steel casing frame");
        blueprint.structureInformation.add("Both sides: 2 rings of 8 heating coils around the center");
        blueprint.structureHints.add("Input and output hatches must be placed on opposite left/right sides.");
        blueprint.structureHints.add("Cracking fluid hatch belongs on the middle ring casing.");
        blueprint.structureHints.add("Programmed circuit automation may occupy a middle ring casing.");
        blueprint.voxelBlueprint = new VoxelBlueprint();
        blueprint.voxelBlueprint.size = new Dimensions(5, 3, 3, null);
        blueprint.voxelBlueprint.legend = new LinkedHashMap<>();
        blueprint.voxelBlueprint.legend.put(
                "X",
                legend(
                        "Controller",
                        "#f59e0b",
                        toItemTextureUrl("i~gregtech~gt.blockmachines~1160"),
                        "i~gregtech~gt.blockmachines~1160"));
        blueprint.voxelBlueprint.legend.put(
                "L",
                legend(
                        "Left Side Casing / Hatch Side",
                        "#94a3b8",
                        "/images/item/gregtech/gt.blockcasings4~1.png",
                        "i~gregtech~gt.blockcasings4~1"));
        blueprint.voxelBlueprint.legend.put(
                "R",
                legend(
                        "Right Side Casing / Hatch Side",
                        "#94a3b8",
                        "/images/item/gregtech/gt.blockcasings4~1.png",
                        "i~gregtech~gt.blockcasings4~1"));
        blueprint.voxelBlueprint.legend.put(
                "C",
                legend("Heating Coil Ring", "#f97316", null, null));
        blueprint.voxelBlueprint.legend.put(
                "M",
                legend(
                        "Middle Ring Casing",
                        "#cbd5e1",
                        "/images/item/gregtech/gt.blockcasings4~1.png",
                        "i~gregtech~gt.blockcasings4~1"));
        blueprint.voxelBlueprint.legend.put("A", legend("Air", "#0ea5e9", null, null));
        blueprint.voxelBlueprint.layers = new ArrayList<>();
        blueprint.voxelBlueprint.layers.add(layer("LCMCR", "LCMCR", "LCMCR"));
        blueprint.voxelBlueprint.layers.add(layer("LCXCR", "LAAAR", "LCMCR"));
        blueprint.voxelBlueprint.layers.add(layer("LCMCR", "LCMCR", "LCMCR"));
        return blueprint;
    }

    private MultiblockBlueprint buildHeatExchanger() {
        MultiblockBlueprint blueprint = baseBlueprint(
                31079,
                "gregtech.common.tileentities.machines.multi.MTEHeatExchanger",
                "i~gregtech~gt.blockmachines~31079",
                "Heat Exchanger");
        blueprint.dimensions = new Dimensions(3, 4, 3, "Size: 3x4x3 (WxHxL)");
        blueprint.structureSource = "builtin_registry";
        blueprint.structureInformation.add("Size: 3x4x3 (WxHxL)");
        blueprint.structureInformation.add("Controller: Front bottom");
        blueprint.structureInformation.add("Main body: Stable Titanium Machine Casing shell");
        blueprint.structureInformation.add("Center 2 blocks: Titanium Pipe Casing column");
        blueprint.structureHints.add("Hot fluid input goes to the bottom center.");
        blueprint.structureHints.add("Cold fluid output goes to the top center.");
        blueprint.structureHints.add("Distilled water and steam/superheated steam hatches may replace valid casing blocks.");
        blueprint.voxelBlueprint = new VoxelBlueprint();
        blueprint.voxelBlueprint.size = new Dimensions(3, 4, 3, null);
        blueprint.voxelBlueprint.legend = new LinkedHashMap<>();
        blueprint.voxelBlueprint.legend.put(
                "X",
                legend(
                        "Controller",
                        "#f59e0b",
                        toItemTextureUrl("i~gregtech~gt.blockmachines~31079"),
                        "i~gregtech~gt.blockmachines~31079"));
        blueprint.voxelBlueprint.legend.put(
                "C",
                legend(
                        "Stable Titanium Casing",
                        "#94a3b8",
                        null,
                        null));
        blueprint.voxelBlueprint.legend.put(
                "P",
                legend(
                        "Titanium Pipe Casing",
                        "#f97316",
                        null,
                        null));
        blueprint.voxelBlueprint.legend.put(
                "H",
                legend("Hot Fluid Input", "#ef4444", null, null));
        blueprint.voxelBlueprint.layers = new ArrayList<>();
        blueprint.voxelBlueprint.layers.add(layer("CXC", "CHC", "CCC"));
        blueprint.voxelBlueprint.layers.add(layer("CCC", "CPC", "CCC"));
        blueprint.voxelBlueprint.layers.add(layer("CCC", "CPC", "CCC"));
        blueprint.voxelBlueprint.layers.add(layer("CCC", "CCC", "CCC"));
        return blueprint;
    }

    private MultiblockBlueprint buildSimpleCubicShellBlueprint(
            int metaTileId,
            String className,
            String controllerItemId,
            String controllerLocalizedName,
            String casingLabel,
            String casingColor,
            String casingTextureUrl,
            String casingBlockId) {
        MultiblockBlueprint blueprint =
                baseBlueprint(metaTileId, className, controllerItemId, controllerLocalizedName);
        blueprint.dimensions = new Dimensions(3, 3, 3, "Size: 3x3x3 (WxHxL)");
        blueprint.structureSource = "builtin_registry";
        blueprint.voxelBlueprint = new VoxelBlueprint();
        blueprint.voxelBlueprint.size = new Dimensions(3, 3, 3, null);
        blueprint.voxelBlueprint.legend = new LinkedHashMap<>();
        blueprint.voxelBlueprint.legend.put(
                "X",
                legend(
                        "Controller",
                        "#f59e0b",
                        toItemTextureUrl(controllerItemId),
                        controllerItemId));
        blueprint.voxelBlueprint.legend.put(
                "C",
                legend(casingLabel, casingColor, casingTextureUrl, casingBlockId));
        blueprint.voxelBlueprint.legend.put("A", legend("Air", "#0ea5e9", null, null));
        blueprint.voxelBlueprint.layers = new ArrayList<>();
        blueprint.voxelBlueprint.layers.add(layer("CCC", "CCC", "CCC"));
        blueprint.voxelBlueprint.layers.add(layer("CXC", "CAC", "CCC"));
        blueprint.voxelBlueprint.layers.add(layer("CCC", "CCC", "CCC"));
        return blueprint;
    }

    private String toItemTextureUrl(String itemId) {
        String[] parts = itemId.split("~");
        if (parts.length < 4) {
            return null;
        }
        return "/images/item/" + parts[1] + "/" + parts[2] + "~" + parts[3] + ".png";
    }

    private SegmentSequenceBlueprint segmentBlueprint(
            String axis,
            int minimumSegments,
            int maximumSegments,
            SegmentBlueprint first,
            SegmentBlueprint repeat,
            SegmentBlueprint last) {
        SegmentSequenceBlueprint blueprint = new SegmentSequenceBlueprint();
        blueprint.axis = axis;
        blueprint.minimumSegments = minimumSegments;
        blueprint.maximumSegments = maximumSegments;
        blueprint.first = first;
        blueprint.repeat = repeat;
        blueprint.last = last;
        return blueprint;
    }

    private SegmentBlueprint segment(String label, int x, int y, int z, String[]... layers) {
        SegmentBlueprint segment = new SegmentBlueprint();
        segment.label = label;
        segment.size = new Dimensions(x, y, z, null);
        segment.layers = new ArrayList<>();
        for (String[] layer : layers) {
            segment.layers.add(layer);
        }
        return segment;
    }

    private void enrichFromRuntimeTooltip(MultiblockBlueprint blueprint) {
        try {
            Object machine = instantiateMachine(blueprint.className);
            if (machine == null) {
                return;
            }
            Method createTooltip = findMethod(machine.getClass(), "createTooltip");
            if (createTooltip == null) {
                return;
            }
            createTooltip.setAccessible(true);
            Object tooltip = createTooltip.invoke(machine);
            if (tooltip == null) {
                return;
            }
            mergeLines(blueprint.information, invokeStringArrayGetter(tooltip, "getInformation"));
            mergeLines(
                    blueprint.structureInformation,
                    invokeStringArrayGetter(tooltip, "getStructureInformation"));
            mergeLines(blueprint.structureHints, invokeStringArrayGetter(tooltip, "getStructureHint"));
        } catch (Exception e) {
            Logger.MOD.warn("Failed to extract runtime tooltip for {}: {}", blueprint.className, e.getMessage());
        }
    }

    private void enrichFromStructureDefinition(MultiblockBlueprint blueprint) {
        try {
            Object machine = instantiateMachine(blueprint.className);
            if (machine == null) {
                return;
            }
            Method getStructureDefinition = findMethod(machine.getClass(), "getStructureDefinition");
            if (getStructureDefinition == null) {
                return;
            }
            getStructureDefinition.setAccessible(true);
            Object structureDefinition = getStructureDefinition.invoke(machine);
            if (structureDefinition == null) {
                return;
            }
            Method getShapes = structureDefinition.getClass().getMethod("getShapes");
            Object shapesValue = getShapes.invoke(structureDefinition);
            if (!(shapesValue instanceof Map)) {
                return;
            }
            Map<?, ?> shapes = (Map<?, ?>) shapesValue;
            if (shapes.isEmpty()) {
                return;
            }
            if (blueprint.structurePieces == null) {
                blueprint.structurePieces = new LinkedHashMap<>();
            }
            for (Map.Entry<?, ?> entry : shapes.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                String pieceName = String.valueOf(entry.getKey());
                String rawShape = String.valueOf(entry.getValue());
                if (!rawShape.trim().isEmpty()) {
                    blueprint.structurePieces.put(pieceName, rawShape);
                }
            }
            if (!blueprint.structurePieces.isEmpty()) {
                if (blueprint.structureSource == null || blueprint.structureSource.trim().isEmpty()) {
                    blueprint.structureSource = "runtime_structure_definition";
                } else if (!blueprint.structureSource.contains("runtime_structure_definition")) {
                    blueprint.structureSource = blueprint.structureSource + "+runtime_structure_definition";
                }
            }
        } catch (Exception e) {
            Logger.MOD.warn(
                    "Failed to extract runtime structure definition for {}: {}",
                    blueprint.className,
                    e.getMessage());
        }
    }

    private void enrichOptionalFeatures(MultiblockBlueprint blueprint) {
        try {
            Object machine = instantiateMachine(blueprint.className);
            if (machine == null) {
                return;
            }
            boolean inputSeparation = invokeBoolean(machine, "supportsInputSeparation");
            boolean batchMode = invokeBoolean(machine, "supportsBatchMode");
            boolean recipeLocking = invokeBoolean(machine, "supportsSingleRecipeLocking");
            boolean voidProtection = invokeBoolean(machine, "supportsVoidProtection");
            if (!(inputSeparation || batchMode || recipeLocking || voidProtection)) {
                return;
            }
            if (blueprint.supports == null) {
                blueprint.supports = new OptionalFeatures();
            }
            if (inputSeparation) {
                blueprint.supports.inputSeparation = true;
            }
            if (batchMode) {
                blueprint.supports.batchMode = true;
            }
            if (recipeLocking) {
                blueprint.supports.recipeLocking = true;
            }
            if (voidProtection) {
                blueprint.supports.voidProtection = true;
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to extract optional features for {}: {}", blueprint.className, e.getMessage());
        }
    }

    private Object instantiateMachine(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Constructor<?> constructor = clazz.getConstructor(String.class);
            return constructor.newInstance("nesqlpp-tooltip-probe");
        } catch (Exception ignored) {
            return null;
        }
    }

    private Method findMethod(Class<?> clazz, String methodName) {
        Class<?> cursor = clazz;
        while (cursor != null) {
            try {
                return cursor.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        return null;
    }

    private String[] invokeStringArrayGetter(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        Object value = method.invoke(target);
        if (value instanceof String[]) {
            return (String[]) value;
        }
        return new String[0];
    }

    private boolean invokeBoolean(Object target, String methodName) {
        try {
            Method method = findMethod(target.getClass(), methodName);
            if (method == null) {
                return false;
            }
            method.setAccessible(true);
            Object value = method.invoke(target);
            return value instanceof Boolean && (Boolean) value;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void mergeLines(List<String> target, String[] source) {
        if (source == null) {
            return;
        }
        Arrays.stream(source)
                .filter(line -> line != null && !line.trim().isEmpty())
                .forEach(
                        line -> {
                            if (!target.contains(line)) {
                                target.add(line);
                            }
                        });
    }

    private MultiblockBlueprint baseBlueprint(
            int metaTileId,
            String className,
            String controllerItemId,
            String controllerLocalizedName) {
        MultiblockBlueprint blueprint = new MultiblockBlueprint();
        blueprint.metaTileId = metaTileId;
        blueprint.className = className;
        blueprint.controllerItemId = controllerItemId;
        blueprint.controllerLocalizedName = controllerLocalizedName;
        blueprint.information = new ArrayList<>();
        blueprint.structureInformation = new ArrayList<>();
        blueprint.structureHints = new ArrayList<>();
        return blueprint;
    }

    private VoxelLegendEntry legend(String label, String color, String textureUrl, String blockId) {
        VoxelLegendEntry entry = new VoxelLegendEntry();
        entry.label = label;
        entry.color = color;
        entry.textureUrl = textureUrl;
        entry.blockId = blockId;
        return entry;
    }

    private String[] layer(String... rows) {
        return rows;
    }

    private static final class ExportPayload {
        long generatedAtEpochMs;
        int totalMetaTileEntitiesScanned;
        int totalMultiblocksExported;
        int failedControllers;
        List<MultiblockBlueprint> blueprints = new ArrayList<>();
    }

    private static final class MultiblockBlueprint {
        int metaTileId;
        String className;
        String controllerItemId;
        String controllerLocalizedName;
        String structureSource;
        OptionalFeatures supports;
        Dimensions dimensions;
        List<String> information;
        List<String> structureInformation;
        List<String> structureHints;
        Map<String, String> structurePieces;
        VoxelBlueprint voxelBlueprint;
        SegmentSequenceBlueprint segmentBlueprint;
    }

    private static final class OptionalFeatures {
        Boolean inputSeparation;
        Boolean batchMode;
        Boolean recipeLocking;
        Boolean voidProtection;
    }

    private static final class Dimensions {
        int x;
        int y;
        int z;
        String raw;

        Dimensions(int x, int y, int z, String raw) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.raw = raw;
        }
    }

    private static final class VoxelBlueprint {
        Dimensions size;
        List<String[]> layers;
        Map<String, VoxelLegendEntry> legend;
    }

    private static final class VoxelLegendEntry {
        String label;
        String color;
        String textureUrl;
        String blockId;
    }

    private static final class SegmentSequenceBlueprint {
        String axis;
        int minimumSegments;
        int maximumSegments;
        SegmentBlueprint first;
        SegmentBlueprint repeat;
        SegmentBlueprint last;
    }

    private static final class SegmentBlueprint {
        String label;
        Dimensions size;
        List<String[]> layers;
    }
}
