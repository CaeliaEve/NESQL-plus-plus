package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.util.IdPrefixUtil;
import com.github.dcysteine.nesql.exporter.util.IdUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports GT material-part progression based on GTNH's NEI custom diagram material parts layouts.
 */
final class GregTechMaterialPartsWriter {

    private static final OrePrefixes[] GEAR_PREFIXES = {OrePrefixes.gearGt, OrePrefixes.gearGtSmall};
    private static final OrePrefixes[] PLATE_PREFIXES = {
            OrePrefixes.plate,
            OrePrefixes.foil,
            OrePrefixes.plateDense,
            OrePrefixes.plateDouble,
            OrePrefixes.plateTriple,
            OrePrefixes.plateQuadruple,
            OrePrefixes.plateQuintuple,
            OrePrefixes.plateSuperdense
    };
    private static final OrePrefixes[] WIRE_PREFIXES = {
            OrePrefixes.wireFine,
            OrePrefixes.wireGt01,
            OrePrefixes.wireGt02,
            OrePrefixes.wireGt04,
            OrePrefixes.wireGt08,
            OrePrefixes.wireGt12,
            OrePrefixes.wireGt16,
            OrePrefixes.cableGt01,
            OrePrefixes.cableGt02,
            OrePrefixes.cableGt04,
            OrePrefixes.cableGt08,
            OrePrefixes.cableGt12,
            OrePrefixes.cableGt16
    };
    private static final OrePrefixes[] ROD_PREFIXES = {
            OrePrefixes.stick,
            OrePrefixes.stickLong,
            OrePrefixes.bolt,
            OrePrefixes.screw,
            OrePrefixes.spring,
            OrePrefixes.springSmall,
            OrePrefixes.ring,
            OrePrefixes.round,
            OrePrefixes.rotor
    };
    private static final OrePrefixes[] STRUCTURAL_PREFIXES = {
            OrePrefixes.frameGt,
            OrePrefixes.itemCasing,
            OrePrefixes.bars
    };
    private static final OrePrefixes[] PIPE_PREFIXES = {
            OrePrefixes.pipeTiny,
            OrePrefixes.pipeSmall,
            OrePrefixes.pipeMedium,
            OrePrefixes.pipeLarge,
            OrePrefixes.pipeHuge,
            OrePrefixes.pipeQuadruple,
            OrePrefixes.pipeNonuple,
            OrePrefixes.pipeRestrictiveTiny,
            OrePrefixes.pipeRestrictiveSmall,
            OrePrefixes.pipeRestrictiveMedium,
            OrePrefixes.pipeRestrictiveLarge,
            OrePrefixes.pipeRestrictiveHuge
    };
    private static final OrePrefixes[] GEM_PREFIXES = {
            OrePrefixes.gem,
            OrePrefixes.gemChipped,
            OrePrefixes.gemFlawed,
            OrePrefixes.gemFlawless,
            OrePrefixes.gemExquisite,
            OrePrefixes.lens
    };
    private static final OrePrefixes[] DUST_PREFIXES = {
            OrePrefixes.dust,
            OrePrefixes.dustSmall,
            OrePrefixes.dustTiny,
            OrePrefixes.ingot,
            OrePrefixes.ingotHot,
            OrePrefixes.nugget,
            OrePrefixes.block,
            OrePrefixes.nanite
    };

    private final File repositoryDirectory;

    GregTechMaterialPartsWriter(File repositoryDirectory) {
        this.repositoryDirectory = repositoryDirectory;
    }

    void export() throws Exception {
        File canonicalDir = new File(repositoryDirectory, "canonical");
        if (!canonicalDir.exists() && !canonicalDir.mkdirs()) {
            throw new IllegalStateException("Failed to create canonical directory: " + canonicalDir);
        }

        File output = new File(canonicalDir, "gregtech-material-parts.json");
        Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
        MaterialPartsDocument document = buildDocument();

        try (FileOutputStream fos = new FileOutputStream(output);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            gson.toJson(document, writer);
        }

        Logger.MOD.info("GregTech material parts written: {}", output.getAbsolutePath());
    }

    private MaterialPartsDocument buildDocument() {
        MaterialPartsDocument document = new MaterialPartsDocument();
        document.generatedFrom = "GTNH nei-custom-diagram gregtech.materialparts";
        document.materials = new ArrayList<>();

        for (Materials material : Materials.getAll()) {
            MaterialPartsDTO dto = new MaterialPartsDTO();
            dto.materialName = material.mDefaultLocalName;
            dto.materialId = material.mName;
            dto.sections = new LinkedHashMap<>();

            addSection(dto.sections, "gems", material, GEM_PREFIXES);
            addSection(dto.sections, "dust_ingot", material, DUST_PREFIXES);
            addSection(dto.sections, "plates", material, PLATE_PREFIXES);
            addSection(dto.sections, "rods_bolts_springs", material, ROD_PREFIXES);
            addSection(dto.sections, "gears", material, GEAR_PREFIXES);
            addSection(dto.sections, "wires_cables", material, WIRE_PREFIXES);
            addSection(dto.sections, "structural", material, STRUCTURAL_PREFIXES);
            addSection(dto.sections, "pipes", material, PIPE_PREFIXES);

            dto.fluids = buildFluidEntries(material);

            if (!dto.sections.isEmpty() || !dto.fluids.isEmpty()) {
                document.materials.add(dto);
            }
        }

        return document;
    }

    private void addSection(Map<String, List<ItemRefDTO>> sections, String key, Materials material, OrePrefixes[] prefixes) {
        List<ItemRefDTO> entries = new ArrayList<>();
        for (OrePrefixes prefix : prefixes) {
            ItemStack stack = GTOreDictUnificator.get(prefix, material, 1L);
            if (stack == null || stack.getItem() == null) {
                continue;
            }
            entries.add(mapStack(stack, prefix.name()));
        }
        if (!entries.isEmpty()) {
            sections.put(key, entries);
        }
    }

    private List<FluidRefDTO> buildFluidEntries(Materials material) {
        List<FluidRefDTO> fluids = new ArrayList<>();
        addFluid(fluids, "fluid", material.getFluid(1000));
        addFluid(fluids, "gas", material.getGas(1000));
        addFluid(fluids, "solid", material.getSolid(1000));
        addFluid(fluids, "molten", material.getMolten(1000));
        addFluid(fluids, "plasma", material.getPlasma(1000));
        addFluid(fluids, "lightlyHydroCracked", material.getLightlyHydroCracked(1000));
        addFluid(fluids, "moderatelyHydroCracked", material.getModeratelyHydroCracked(1000));
        addFluid(fluids, "severelyHydroCracked", material.getSeverelyHydroCracked(1000));
        addFluid(fluids, "lightlySteamCracked", material.getLightlySteamCracked(1000));
        addFluid(fluids, "moderatelySteamCracked", material.getModeratelySteamCracked(1000));
        addFluid(fluids, "severelySteamCracked", material.getSeverelySteamCracked(1000));
        return fluids;
    }

    private void addFluid(List<FluidRefDTO> fluids, String key, FluidStack stack) {
        if (stack == null || stack.getFluid() == null) {
            return;
        }
        FluidRefDTO dto = new FluidRefDTO();
        dto.kind = key;
        dto.fluidId = IdPrefixUtil.FLUID.applyPrefix(IdUtil.fluidId(stack));
        dto.localizedName = stack.getFluid().getLocalizedName(stack);
        fluids.add(dto);
    }

    private ItemRefDTO mapStack(ItemStack stack, String prefix) {
        ItemRefDTO dto = new ItemRefDTO();
        dto.prefix = prefix;
        dto.itemId = IdPrefixUtil.ITEM.applyPrefix(IdUtil.itemId(stack));
        dto.localizedName = stack.getDisplayName();
        return dto;
    }

    private static final class MaterialPartsDocument {
        String generatedFrom;
        List<MaterialPartsDTO> materials;
    }

    private static final class MaterialPartsDTO {
        String materialName;
        String materialId;
        Map<String, List<ItemRefDTO>> sections;
        List<FluidRefDTO> fluids;
    }

    private static final class ItemRefDTO {
        String prefix;
        String itemId;
        String localizedName;
    }

    private static final class FluidRefDTO {
        String kind;
        String fluidId;
        String localizedName;
    }
}
