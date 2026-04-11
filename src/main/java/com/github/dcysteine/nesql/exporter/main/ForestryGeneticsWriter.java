package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.util.IdPrefixUtil;
import com.github.dcysteine.nesql.exporter.util.IdUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import forestry.api.apiculture.BeeManager;
import forestry.api.apiculture.EnumBeeType;
import forestry.api.apiculture.IBee;
import forestry.api.apiculture.IBeeMutation;
import forestry.api.apiculture.IAlleleBeeSpecies;
import forestry.api.arboriculture.EnumGermlingType;
import forestry.api.arboriculture.IAlleleTreeSpecies;
import forestry.api.arboriculture.ITree;
import forestry.api.arboriculture.ITreeMutation;
import forestry.api.arboriculture.TreeManager;
import forestry.api.genetics.IAlleleSpecies;
import forestry.api.genetics.IMutation;
import net.minecraft.item.ItemStack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exports Forestry bee/tree genetics and mutation chains based on ForestryMC + NEIAddons semantics.
 */
final class ForestryGeneticsWriter {

    private final File repositoryDirectory;

    ForestryGeneticsWriter(File repositoryDirectory) {
        this.repositoryDirectory = repositoryDirectory;
    }

    void export() throws Exception {
        File canonicalDir = new File(repositoryDirectory, "canonical");
        if (!canonicalDir.exists() && !canonicalDir.mkdirs()) {
            throw new IllegalStateException("Failed to create canonical directory: " + canonicalDir);
        }

        File output = new File(canonicalDir, "forestry-genetics.json");
        Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
        GeneticsDocument document = buildDocument();

        try (FileOutputStream fos = new FileOutputStream(output);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            gson.toJson(document, writer);
        }

        Logger.MOD.info("Forestry genetics written: {}", output.getAbsolutePath());
    }

    private GeneticsDocument buildDocument() {
        GeneticsDocument document = new GeneticsDocument();
        document.generatedFrom = "ForestryMC + NEIAddons Forestry";
        document.bees = buildBeeSection();
        document.trees = buildTreeSection();
        return document;
    }

    private SpeciesSection buildBeeSection() {
        SpeciesSection section = new SpeciesSection();
        section.species = new ArrayList<>();
        section.mutations = new ArrayList<>();

        if (BeeManager.beeRoot != null) {
            for (IBee bee : BeeManager.beeRoot.getIndividualTemplates()) {
                if (bee != null && bee.getGenome() != null && bee.getGenome().getPrimary() != null) {
                    section.species.add(buildBeeSpecies(bee));
                }
            }
            for (IBeeMutation mutation : BeeManager.beeRoot.getMutations(false)) {
                section.mutations.add(buildMutation(mutation));
            }
        }

        return section;
    }

    private SpeciesSection buildTreeSection() {
        SpeciesSection section = new SpeciesSection();
        section.species = new ArrayList<>();
        section.mutations = new ArrayList<>();

        if (TreeManager.treeRoot != null) {
            for (ITree tree : TreeManager.treeRoot.getIndividualTemplates()) {
                if (tree != null && tree.getGenome() != null && tree.getGenome().getPrimary() != null) {
                    section.species.add(buildTreeSpecies(tree));
                }
            }
            for (ITreeMutation mutation : TreeManager.treeRoot.getMutations(false)) {
                section.mutations.add(buildMutation(mutation));
            }
        }

        return section;
    }

    private SpeciesDTO buildBeeSpecies(IBee bee) {
        SpeciesDTO dto = new SpeciesDTO();
        IAlleleBeeSpecies species = bee.getGenome().getPrimary();
        dto.uid = species.getUID();
        dto.name = species.getName();
        dto.memberItemId = mapStack(BeeManager.beeRoot.getMemberStack(bee, EnumBeeType.QUEEN.ordinal()));
        dto.products = mapDrops(species.getProductChances());
        dto.specialties = mapDrops(species.getSpecialtyChances());
        return dto;
    }

    private SpeciesDTO buildTreeSpecies(ITree tree) {
        SpeciesDTO dto = new SpeciesDTO();
        IAlleleTreeSpecies species = tree.getGenome().getPrimary();
        dto.uid = species.getUID();
        dto.name = species.getName();
        dto.memberItemId = mapStack(TreeManager.treeRoot.getMemberStack(tree, EnumGermlingType.SAPLING.ordinal()));
        dto.products = mapStackArray(tree.getProduceList());
        dto.specialties = mapStackArray(tree.getSpecialtyList());
        return dto;
    }

    private MutationDTO buildMutation(IMutation mutation) {
        MutationDTO dto = new MutationDTO();
        dto.parent1Uid = mutation.getAllele0().getUID();
        dto.parent1Name = mutation.getAllele0().getName();
        dto.parent2Uid = mutation.getAllele1().getUID();
        dto.parent2Name = mutation.getAllele1().getName();

        IAlleleSpecies result = (IAlleleSpecies) mutation.getTemplate()[0];
        dto.resultUid = result.getUID();
        dto.resultName = result.getName();
        dto.chance = mutation.getBaseChance();

        try {
            java.util.Collection<String> requirements = mutation.getSpecialConditions();
            if (requirements != null && !requirements.isEmpty()) {
                dto.requirements = new ArrayList<>(requirements);
            }
        } catch (Throwable ignored) {
        }

        return dto;
    }

    private List<DropDTO> mapDrops(Map<ItemStack, Float> drops) {
        List<DropDTO> list = new ArrayList<>();
        if (drops == null) return list;
        for (Map.Entry<ItemStack, Float> entry : drops.entrySet()) {
            if (entry.getKey() == null || entry.getKey().getItem() == null) continue;
            DropDTO dto = new DropDTO();
            dto.itemId = mapStack(entry.getKey());
            dto.localizedName = entry.getKey().getDisplayName();
            dto.chance = entry.getValue();
            list.add(dto);
        }
        return list;
    }

    private List<DropDTO> mapStackArray(ItemStack[] stacks) {
        List<DropDTO> list = new ArrayList<>();
        if (stacks == null) return list;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getItem() == null) continue;
            DropDTO dto = new DropDTO();
            dto.itemId = mapStack(stack);
            dto.localizedName = stack.getDisplayName();
            dto.chance = 1.0f;
            list.add(dto);
        }
        return list;
    }

    private String mapStack(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        return IdPrefixUtil.ITEM.applyPrefix(IdUtil.itemId(stack));
    }

    private static final class GeneticsDocument {
        String generatedFrom;
        SpeciesSection bees;
        SpeciesSection trees;
    }

    private static final class SpeciesSection {
        List<SpeciesDTO> species;
        List<MutationDTO> mutations;
    }

    private static final class SpeciesDTO {
        String uid;
        String name;
        String memberItemId;
        List<DropDTO> products;
        List<DropDTO> specialties;
    }

    private static final class MutationDTO {
        String parent1Uid;
        String parent1Name;
        String parent2Uid;
        String parent2Name;
        String resultUid;
        String resultName;
        float chance;
        List<String> requirements;
    }

    private static final class DropDTO {
        String itemId;
        String localizedName;
        float chance;
    }
}
