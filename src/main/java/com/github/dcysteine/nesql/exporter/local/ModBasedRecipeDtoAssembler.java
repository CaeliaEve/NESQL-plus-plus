package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalExportMapper;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalFluid;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalItem;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalMachineDescriptor;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalRecipe;
import com.github.dcysteine.nesql.sql.base.item.Item;
import com.github.dcysteine.nesql.sql.base.item.ItemStackWithProbability;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import jakarta.persistence.EntityManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ModBasedRecipeDtoAssembler {

    private final EntityManager entityManager;

    ModBasedRecipeDtoAssembler(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    ModBasedRecipeExporter.RecipeDTO toDto(
            Recipe recipe,
            String recipeType,
            Map<Recipe, com.github.dcysteine.nesql.sql.gregtech.GregTechRecipe> gtRecipeMap) {
        ModBasedRecipeExporter.RecipeDTO dto = new ModBasedRecipeExporter.RecipeDTO();
        dto.id = recipe.getId();
        com.github.dcysteine.nesql.sql.gregtech.GregTechRecipe canonicalGtRecipe = gtRecipeMap.get(recipe);
        CanonicalRecipe canonicalRecipe = CanonicalExportMapper.mapRecipe(recipe, canonicalGtRecipe);
        dto.recipeType = recipeType;
        dto.machineInfo = buildMachineInfo(recipe, canonicalRecipe);
        populateItemSlots(dto, recipe);
        populateFluidSlots(dto, recipe);
        dto.additionalData = canonicalRecipe.metadata.isEmpty() ? null : new java.util.LinkedHashMap<>(canonicalRecipe.metadata);
        dto.metadata = buildMetadataFromCanonical(canonicalRecipe, null);
        return dto;
    }

    private ModBasedRecipeExporter.MachineInfoDTO buildMachineInfo(Recipe recipe, CanonicalRecipe canonicalRecipe) {
        if (canonicalRecipe.machine == null) {
            return null;
        }

        ModBasedRecipeExporter.MachineInfoDTO dto = new ModBasedRecipeExporter.MachineInfoDTO();
        CanonicalMachineDescriptor machine = canonicalRecipe.machine;
        dto.machineId = machine.machineId;
        dto.category = machine.category;
        dto.machineType = machine.machineType;
        dto.iconInfo = machine.iconInfoRaw;
        dto.shapeless = Boolean.TRUE.equals(machine.shapeless);
        dto.parsedVoltageTier = machine.parsedVoltageTier;
        dto.parsedVoltage = machine.parsedVoltage;

        if (recipe.getRecipeType() != null && recipe.getRecipeType().getIcon() != null) {
            dto.machineIcon = convertItem(recipe.getRecipeType().getIcon());
        }

        return dto;
    }

    private void populateItemSlots(ModBasedRecipeExporter.RecipeDTO dto, Recipe recipe) {
        if (recipe.getItemOutputs() != null) {
            for (Map.Entry<Integer, ItemStackWithProbability> entry : recipe.getItemOutputs().entrySet()) {
                dto.outputs.add(convertItemStack(entry.getValue()));
            }
        }

        if (recipe.getItemInputs() != null) {
            for (Map.Entry<Integer, com.github.dcysteine.nesql.sql.base.item.ItemGroup> entry : recipe.getItemInputs().entrySet()) {
                dto.inputs.add(convertItemGroup(entry.getKey(), entry.getValue()));
            }
        }
    }

    private void populateFluidSlots(ModBasedRecipeExporter.RecipeDTO dto, Recipe recipe) {
        if (recipe.getFluidInputs() != null) {
            for (Map.Entry<Integer, com.github.dcysteine.nesql.sql.base.fluid.FluidGroup> entry : recipe.getFluidInputs().entrySet()) {
                dto.fluidInputs.add(convertFluidGroup(entry.getKey(), entry.getValue()));
            }
        }

        if (recipe.getFluidOutputs() != null) {
            for (Map.Entry<Integer, com.github.dcysteine.nesql.sql.base.fluid.FluidStackWithProbability> entry : recipe.getFluidOutputs().entrySet()) {
                dto.fluidOutputs.add(convertFluidStack(entry.getValue()));
            }
        }
    }

    private ModBasedRecipeExporter.RecipeMetadataDTO buildMetadataFromCanonical(
            CanonicalRecipe canonicalRecipe,
            ModBasedRecipeExporter.RecipeMetadataDTO existingMetadata) {
        ModBasedRecipeExporter.RecipeMetadataDTO metadata =
                existingMetadata != null ? existingMetadata : new ModBasedRecipeExporter.RecipeMetadataDTO();
        if (canonicalRecipe.metadata.isEmpty()) {
            return metadata == existingMetadata ? metadata : null;
        }

        metadata.voltageTier = asString(canonicalRecipe.metadata.get("voltageTier"), metadata.voltageTier);
        metadata.voltage = asInteger(canonicalRecipe.metadata.get("voltage"), metadata.voltage);
        metadata.amperage = asInteger(canonicalRecipe.metadata.get("amperage"), metadata.amperage);
        metadata.duration = asInteger(canonicalRecipe.metadata.get("duration"), metadata.duration);
        metadata.totalEU = asLong(canonicalRecipe.metadata.get("totalEU"), metadata.totalEU);
        metadata.requiresCleanroom = asBoolean(canonicalRecipe.metadata.get("requiresCleanroom"), metadata.requiresCleanroom);
        metadata.requiresLowGravity = asBoolean(canonicalRecipe.metadata.get("requiresLowGravity"), metadata.requiresLowGravity);
        metadata.additionalInfo = asString(canonicalRecipe.metadata.get("additionalInfo"), metadata.additionalInfo);
        metadata.aspects = asStringIntegerMap(canonicalRecipe.metadata.get("aspects"));
        metadata.specialRecipeType = asString(canonicalRecipe.metadata.get("specialRecipeType"), metadata.specialRecipeType);
        metadata.research = asString(canonicalRecipe.metadata.get("research"), metadata.research);
        metadata.centralItemId = asString(canonicalRecipe.metadata.get("centralItemId"), metadata.centralItemId);
        metadata.centerInputSlotIndex = asInteger(canonicalRecipe.metadata.get("centerInputSlotIndex"), metadata.centerInputSlotIndex);
        metadata.instability = asInteger(canonicalRecipe.metadata.get("instability"), metadata.instability);
        metadata.componentSlotOrder = asIntegerList(canonicalRecipe.metadata.get("componentSlotOrder"));
        attachGregTechSpecialItems(metadata, canonicalRecipe);
        return metadata;
    }

    private void attachGregTechSpecialItems(
            ModBasedRecipeExporter.RecipeMetadataDTO metadata,
            CanonicalRecipe canonicalRecipe) {
        Object gregtechExtension = canonicalRecipe.extensions.get("gregtech");
        if (!(gregtechExtension instanceof Map<?, ?>)) {
            return;
        }

        Object specialItemIds = ((Map<?, ?>) gregtechExtension).get("specialItemIds");
        if (!(specialItemIds instanceof List<?>)) {
            return;
        }

        metadata.specialItems = new ArrayList<>();
        for (Object specialItemId : (List<?>) specialItemIds) {
            if (specialItemId instanceof String) {
                Item specialItem = entityManager.find(Item.class, (String) specialItemId);
                if (specialItem != null) {
                    metadata.specialItems.add(convertItem(specialItem));
                }
            }
        }

        if (metadata.specialItems.isEmpty()) {
            metadata.specialItems = null;
        }
    }

    private ModBasedRecipeExporter.ItemStackDTO convertItemStack(ItemStackWithProbability stack) {
        ModBasedRecipeExporter.ItemStackDTO dto = new ModBasedRecipeExporter.ItemStackDTO();
        dto.item = convertItem(stack.getItem());
        dto.stackSize = stack.getStackSize();
        dto.probability = stack.getProbability();
        return dto;
    }

    private ModBasedRecipeExporter.ItemDTO convertItem(Item item) {
        CanonicalItem canonical = CanonicalExportMapper.mapItem(item);
        ModBasedRecipeExporter.ItemDTO dto = new ModBasedRecipeExporter.ItemDTO();
        dto.itemId = canonical.itemId;
        dto.modId = canonical.modId;
        dto.localizedName = canonical.localizedName;
        dto.renderAssetRef = canonical.renderAssetRef;
        dto.imageFileName = CanonicalExportMapper.normalizeImageFileName(item.getImageFilePath());
        return dto;
    }

    private ModBasedRecipeExporter.ItemGroupDTO convertItemGroup(
            int slotIndex,
            com.github.dcysteine.nesql.sql.base.item.ItemGroup group) {
        ModBasedRecipeExporter.ItemGroupDTO dto = new ModBasedRecipeExporter.ItemGroupDTO();
        dto.slotIndex = slotIndex;
        dto.items = new ArrayList<>();

        for (com.github.dcysteine.nesql.sql.base.item.ItemStack stack : group.getItemStacks()) {
            ModBasedRecipeExporter.ItemStackDTO itemStackDto = new ModBasedRecipeExporter.ItemStackDTO();
            itemStackDto.item = convertItem(stack.getItem());
            itemStackDto.stackSize = stack.getStackSize();
            itemStackDto.probability = 1.0;
            dto.items.add(itemStackDto);
        }

        if (dto.items.size() > 1) {
            dto.isOreDictionary = true;
            String groupId = group.getId();
            if (groupId != null && groupId.contains(":")) {
                String[] parts = groupId.split(":");
                dto.oreDictName = parts.length >= 2 ? parts[1] : "unknown";
            } else {
                dto.oreDictName = "unknown";
            }
        } else {
            dto.isOreDictionary = false;
            dto.oreDictName = null;
        }

        return dto;
    }

    private ModBasedRecipeExporter.FluidStackDTO convertFluidStack(
            com.github.dcysteine.nesql.sql.base.fluid.FluidStackWithProbability stack) {
        ModBasedRecipeExporter.FluidStackDTO dto = new ModBasedRecipeExporter.FluidStackDTO();
        dto.fluid = convertFluid(stack.getFluid());
        dto.amount = stack.getAmount();
        dto.probability = stack.getProbability();
        return dto;
    }

    private ModBasedRecipeExporter.FluidGroupDTO convertFluidGroup(
            int slotIndex,
            com.github.dcysteine.nesql.sql.base.fluid.FluidGroup group) {
        ModBasedRecipeExporter.FluidGroupDTO dto = new ModBasedRecipeExporter.FluidGroupDTO();
        dto.slotIndex = slotIndex;
        dto.fluids = new ArrayList<>();

        for (com.github.dcysteine.nesql.sql.base.fluid.FluidStack stack : group.getFluidStacks()) {
            ModBasedRecipeExporter.FluidStackDTO stackDto = new ModBasedRecipeExporter.FluidStackDTO();
            stackDto.fluid = convertFluid(stack.getFluid());
            stackDto.amount = stack.getAmount();
            stackDto.probability = 1.0;
            dto.fluids.add(stackDto);
        }

        return dto;
    }

    private ModBasedRecipeExporter.FluidDTO convertFluid(com.github.dcysteine.nesql.sql.base.fluid.Fluid fluid) {
        CanonicalFluid canonical = CanonicalExportMapper.mapFluid(fluid);
        ModBasedRecipeExporter.FluidDTO dto = new ModBasedRecipeExporter.FluidDTO();
        dto.fluidId = canonical.fluidId;
        dto.modId = canonical.modId;
        dto.internalName = canonical.internalName;
        dto.localizedName = canonical.localizedName;
        dto.renderAssetRef = canonical.renderAssetRef;
        dto.temperature = canonical.temperature != null ? canonical.temperature : 0;
        return dto;
    }

    private String asString(Object value, String fallback) {
        return value instanceof String ? (String) value : fallback;
    }

    private Integer asInteger(Object value, Integer fallback) {
        return value instanceof Number ? Integer.valueOf(((Number) value).intValue()) : fallback;
    }

    private Long asLong(Object value, Long fallback) {
        return value instanceof Number ? Long.valueOf(((Number) value).longValue()) : fallback;
    }

    private Boolean asBoolean(Object value, Boolean fallback) {
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> asStringIntegerMap(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Integer> converted = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (entry.getKey() instanceof String && entry.getValue() instanceof Number) {
                converted.put((String) entry.getKey(), ((Number) entry.getValue()).intValue());
            }
        }
        return converted.isEmpty() ? null : converted;
    }

    private List<Integer> asIntegerList(Object value) {
        if (!(value instanceof List<?>)) {
            return null;
        }
        List<Integer> converted = new ArrayList<>();
        for (Object element : (List<?>) value) {
            if (element instanceof Number) {
                converted.add(((Number) element).intValue());
            }
        }
        return converted.isEmpty() ? null : converted;
    }
}
