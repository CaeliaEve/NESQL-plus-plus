package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.util.IdPrefixUtil;
import com.github.dcysteine.nesql.exporter.util.IdUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.util.GTModHandler;
import net.minecraft.item.ItemStack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Exports GT circuit/board progression based on GTNH's NEI custom diagram definitions.
 */
final class GregTechCircuitProgressionWriter {

    private final File repositoryDirectory;

    GregTechCircuitProgressionWriter(File repositoryDirectory) {
        this.repositoryDirectory = repositoryDirectory;
    }

    void export() throws Exception {
        File canonicalDir = new File(repositoryDirectory, "canonical");
        if (!canonicalDir.exists() && !canonicalDir.mkdirs()) {
            throw new IllegalStateException("Failed to create canonical directory: " + canonicalDir);
        }

        File output = new File(canonicalDir, "gregtech-circuit-progression.json");
        Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
        ProgressionDocument document = buildDocument();

        try (FileOutputStream fos = new FileOutputStream(output);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            gson.toJson(document, writer);
        }

        Logger.MOD.info("GregTech circuit progression written: {}", output.getAbsolutePath());
    }

    private ProgressionDocument buildDocument() {
        ProgressionDocument document = new ProgressionDocument();
        document.generatedFrom = "GTNH nei-custom-diagram gregtech.circuits";
        document.circuitLines = new ArrayList<>();
        document.individualCircuits = new ArrayList<>();
        document.circuitParts = new ArrayList<>();

        addCircuitLine(document, 0,
                list(),
                list(
                        GTModHandler.getModItem("dreamcraft", "CircuitULV", 1L),
                        GTModHandler.getModItem("dreamcraft", "CircuitLV", 1L),
                        GTModHandler.getModItem("dreamcraft", "CircuitMV", 1L),
                        GTModHandler.getModItem("dreamcraft", "CircuitHV", 1L),
                        GTModHandler.getModItem("dreamcraft", "CircuitEV", 1L),
                        GTModHandler.getModItem("dreamcraft", "CircuitIV", 1L),
                        GTModHandler.getModItem("dreamcraft", "CircuitLuV", 1L),
                        GTModHandler.getModItem("dreamcraft", "CircuitZPM", 1L),
                        GTModHandler.getModItem("dreamcraft", "CircuitUV", 1L),
                        GTModHandler.getModItem("dreamcraft", "CircuitUHV", 1L),
                        GTModHandler.getModItem("dreamcraft", "CircuitUEV", 1L),
                        GTModHandler.getModItem("dreamcraft", "CircuitUIV", 1L),
                        GTModHandler.getModItem("dreamcraft", "CircuitUMV", 1L),
                        GTModHandler.getModItem("dreamcraft", "CircuitUXV", 1L),
                        GTModHandler.getModItem("dreamcraft", "CircuitMAX", 1L)));

        addCircuitLine(document, 0,
                list(ItemList.Circuit_Board_Coated_Basic.get(1), ItemList.Circuit_Board_Phenolic_Good.get(1)),
                list(ItemList.Circuit_Primitive.get(1), GTModHandler.getIC2Item("electronicCircuit", 1L), ItemList.Circuit_Good.get(1)));
        addCircuitLine(document, 1,
                list(ItemList.Circuit_Board_Coated_Basic.get(1), ItemList.Circuit_Board_Phenolic_Good.get(1)),
                list(ItemList.Circuit_Basic.get(1), ItemList.Circuit_Integrated_Good.get(1), GTModHandler.getIC2Item("advancedCircuit", 1L)));
        addCircuitLine(document, 2,
                list(ItemList.Circuit_Board_Plastic_Advanced.get(1)),
                list(ItemList.Circuit_Processor.get(1), ItemList.Circuit_Advanced.get(1), ItemList.Circuit_Data.get(1), ItemList.Circuit_Elite.get(1)));
        addCircuitLine(document, 3,
                list(ItemList.Circuit_Board_Epoxy_Advanced.get(1)),
                list(ItemList.Circuit_Nanoprocessor.get(1), ItemList.Circuit_Nanocomputer.get(1), ItemList.Circuit_Elitenanocomputer.get(1), ItemList.Circuit_Master.get(1)));
        addCircuitLine(document, 4,
                list(ItemList.Circuit_Board_Fiberglass_Advanced.get(1)),
                list(ItemList.Circuit_Quantumprocessor.get(1), ItemList.Circuit_Quantumcomputer.get(1), ItemList.Circuit_Masterquantumcomputer.get(1), ItemList.Circuit_Quantummainframe.get(1)));
        addCircuitLine(document, 5,
                list(ItemList.Circuit_Board_Multifiberglass_Elite.get(1)),
                list(ItemList.Circuit_Crystalprocessor.get(1), ItemList.Circuit_Crystalcomputer.get(1), ItemList.Circuit_Ultimatecrystalcomputer.get(1), ItemList.Circuit_Crystalmainframe.get(1)));
        addCircuitLine(document, 6,
                list(ItemList.Circuit_Board_Wetware_Extreme.get(1)),
                list(ItemList.Circuit_Neuroprocessor.get(1), ItemList.Circuit_Wetwarecomputer.get(1), ItemList.Circuit_Wetwaresupercomputer.get(1), ItemList.Circuit_Wetwaremainframe.get(1)));
        addCircuitLine(document, 7,
                list(ItemList.Circuit_Board_Bio_Ultra.get(1)),
                list(ItemList.Circuit_Bioprocessor.get(1), ItemList.Circuit_Biowarecomputer.get(1), ItemList.Circuit_Biowaresupercomputer.get(1), ItemList.Circuit_Biomainframe.get(1)));
        addCircuitLine(document, 8,
                list(ItemList.Circuit_Board_Optical.get(1)),
                list(ItemList.Circuit_OpticalProcessor.get(1), ItemList.Circuit_OpticalAssembly.get(1), ItemList.Circuit_OpticalComputer.get(1), ItemList.Circuit_OpticalMainframe.get(1)));
        addCircuitLine(document, 9, list(), list(ItemList.Circuit_ExoticProcessor.get(1), ItemList.Circuit_ExoticAssembly.get(1), ItemList.Circuit_ExoticComputer.get(1), ItemList.Circuit_ExoticMainframe.get(1)));
        addCircuitLine(document, 10, list(), list(ItemList.Circuit_CosmicProcessor.get(1), ItemList.Circuit_CosmicAssembly.get(1), ItemList.Circuit_CosmicComputer.get(1), ItemList.Circuit_CosmicMainframe.get(1)));
        addCircuitLine(document, 11, list(), list(ItemList.Circuit_TranscendentProcessor.get(1), ItemList.Circuit_TranscendentAssembly.get(1), ItemList.Circuit_TranscendentComputer.get(1), ItemList.Circuit_TranscendentMainframe.get(1)));

        document.individualCircuits.add(buildSingleCircuit(list(ItemList.Circuit_Board_Phenolic_Good.get(1)), ItemList.NandChip.get(1), 0));
        document.individualCircuits.add(buildSingleCircuit(list(ItemList.Circuit_Board_Plastic_Advanced.get(1)), ItemList.Circuit_Microprocessor.get(1), 1));

        document.circuitParts.add(buildPartGroup("resistors", list(ItemList.Circuit_Parts_Resistor.get(1), ItemList.Circuit_Parts_ResistorSMD.get(1), ItemList.Circuit_Parts_ResistorASMD.get(1), ItemList.Circuit_Parts_ResistorXSMD.get(1))));
        document.circuitParts.add(buildPartGroup("diodes", list(ItemList.Circuit_Parts_Diode.get(1), ItemList.Circuit_Parts_DiodeSMD.get(1), ItemList.Circuit_Parts_DiodeASMD.get(1), ItemList.Circuit_Parts_DiodeXSMD.get(1))));
        document.circuitParts.add(buildPartGroup("transistors", list(ItemList.Circuit_Parts_Transistor.get(1), ItemList.Circuit_Parts_TransistorSMD.get(1), ItemList.Circuit_Parts_TransistorASMD.get(1), ItemList.Circuit_Parts_TransistorXSMD.get(1))));
        document.circuitParts.add(buildPartGroup("capacitors", list(ItemList.Circuit_Parts_Capacitor.get(1), ItemList.Circuit_Parts_CapacitorSMD.get(1), ItemList.Circuit_Parts_CapacitorASMD.get(1), ItemList.Circuit_Parts_CapacitorXSMD.get(1))));
        document.circuitParts.add(buildPartGroup("inductors", list(ItemList.Circuit_Parts_Coil.get(1), ItemList.Circuit_Parts_InductorSMD.get(1), ItemList.Circuit_Parts_InductorASMD.get(1), ItemList.Circuit_Parts_InductorXSMD.get(1))));

        return document;
    }

    private void addCircuitLine(ProgressionDocument document, int startTier, List<ItemStack> boards, List<ItemStack> circuits) {
        CircuitLineDTO dto = new CircuitLineDTO();
        dto.startTier = startTier;
        dto.boards = mapStacks(boards, startTier, false);
        dto.circuits = mapStacks(circuits, startTier, true);
        document.circuitLines.add(dto);
    }

    private SingleCircuitDTO buildSingleCircuit(List<ItemStack> boards, ItemStack circuit, int tier) {
        SingleCircuitDTO dto = new SingleCircuitDTO();
        dto.tier = tier;
        dto.boards = mapStacks(boards, tier, false);
        dto.circuit = mapStack(circuit, tier, true);
        return dto;
    }

    private CircuitPartGroupDTO buildPartGroup(String key, List<ItemStack> parts) {
        CircuitPartGroupDTO dto = new CircuitPartGroupDTO();
        dto.key = key;
        dto.parts = mapStacks(parts, null, false);
        return dto;
    }

    private List<ItemRefDTO> mapStacks(List<ItemStack> stacks, Integer startTier, boolean withTierProgression) {
        List<ItemRefDTO> refs = new ArrayList<>();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack == null || stack.getItem() == null) {
                continue;
            }
            Integer tier = withTierProgression && startTier != null ? Integer.valueOf(startTier + i) : startTier;
            refs.add(mapStack(stack, tier, withTierProgression));
        }
        return refs;
    }

    private ItemRefDTO mapStack(ItemStack stack, Integer tier, boolean includeTierName) {
        ItemRefDTO dto = new ItemRefDTO();
        String rawId = IdUtil.itemId(stack);
        dto.itemId = IdPrefixUtil.ITEM.applyPrefix(rawId);
        dto.localizedName = stack.getDisplayName();
        if (tier != null) {
            dto.tier = tier;
            if (includeTierName && tier >= 0 && tier < GTValues.VN.length) {
                dto.tierName = GTValues.VN[tier];
            }
        }
        return dto;
    }

    @SafeVarargs
    private final List<ItemStack> list(ItemStack... stacks) {
        return Arrays.asList(stacks);
    }

    private static final class ProgressionDocument {
        String generatedFrom;
        List<CircuitLineDTO> circuitLines;
        List<SingleCircuitDTO> individualCircuits;
        List<CircuitPartGroupDTO> circuitParts;
    }

    private static final class CircuitLineDTO {
        int startTier;
        List<ItemRefDTO> boards;
        List<ItemRefDTO> circuits;
    }

    private static final class SingleCircuitDTO {
        int tier;
        List<ItemRefDTO> boards;
        ItemRefDTO circuit;
    }

    private static final class CircuitPartGroupDTO {
        String key;
        List<ItemRefDTO> parts;
    }

    private static final class ItemRefDTO {
        String itemId;
        String localizedName;
        Integer tier;
        String tierName;
    }
}
