package com.github.dcysteine.nesql.exporter.canonical;

import com.github.dcysteine.nesql.sql.base.fluid.Fluid;
import com.github.dcysteine.nesql.sql.base.fluid.FluidGroup;
import com.github.dcysteine.nesql.sql.base.fluid.FluidStack;
import com.github.dcysteine.nesql.sql.base.fluid.FluidStackWithProbability;
import com.github.dcysteine.nesql.sql.base.item.Item;
import com.github.dcysteine.nesql.sql.base.item.ItemGroup;
import com.github.dcysteine.nesql.sql.base.item.ItemStack;
import com.github.dcysteine.nesql.sql.base.item.ItemStackWithProbability;
import com.github.dcysteine.nesql.sql.base.recipe.Dimension;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import com.github.dcysteine.nesql.sql.gregtech.GregTechRecipe;
import com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry;
import com.github.dcysteine.nesql.exporter.semantic.SemanticItemIdentity;
import com.github.dcysteine.nesql.exporter.semantic.SemanticItemIdentityMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical mapper utilities for NESQL++.
 *
 * <p>The immediate purpose of this class is to establish one field-level mapping
 * surface that later replaces the duplicated DTO assembly logic currently spread
 * across Local*Exporter and ModBased*Exporter.</p>
 */
public final class CanonicalExportMapper {

    private CanonicalExportMapper() {}

    public static CanonicalItem mapItem(Item item) {
        CanonicalItem canonical = new CanonicalItem();
        canonical.itemId = item.getId();
        canonical.modId = item.getModId();
        canonical.internalName = item.getInternalName();
        canonical.localizedName = item.getLocalizedName();
        canonical.unlocalizedName = item.getUnlocalizedName();
        canonical.damage = item.getItemDamage();
        canonical.maxStackSize = item.getMaxStackSize();
        canonical.maxDamage = item.getMaxDamage();
        canonical.nbtDescriptor = item.hasNbt() ? item.getNbt() : null;
        canonical.tooltip = item.getTooltip();
        canonical.searchTerms = buildItemSearchTerms(item);
        canonical.toolClasses = item.getToolClasses();
        canonical.renderAssetRef = buildRenderAssetRef("item", canonical.itemId);
        return canonical;
    }

    public static CanonicalFluid mapFluid(Fluid fluid) {
        CanonicalFluid canonical = new CanonicalFluid();
        canonical.fluidId = fluid.getId();
        canonical.modId = fluid.getModId();
        canonical.internalName = fluid.getInternalName();
        canonical.localizedName = fluid.getLocalizedName();
        canonical.temperature = fluid.getTemperature();
        canonical.renderAssetRef = buildRenderAssetRef("fluid", canonical.fluidId);
        return canonical;
    }

    public static CanonicalMachineDescriptor mapRecipeType(RecipeType recipeType) {
        CanonicalMachineDescriptor machine = new CanonicalMachineDescriptor();
        machine.machineId = recipeType.getId();
        machine.category = recipeType.getCategory();
        machine.machineType = recipeType.getType();
        machine.displayName = recipeType.getCategory() + " - " + recipeType.getType();
        machine.iconRef = recipeType.getIcon() != null ? buildRenderAssetRef("item", recipeType.getIcon().getId()) : null;
        machine.iconInfoRaw = recipeType.getIconInfo();
        machine.layoutClass = inferLayoutClass(recipeType);
        machine.shapeless = recipeType.isShapeless();
        VoltageInfo voltageInfo = parseVoltageInfo(recipeType.getIconInfo());
        machine.parsedVoltageTier = voltageInfo.voltageTier;
        machine.parsedVoltage = voltageInfo.voltage;
        machine.itemInputWidth = getWidth(recipeType.getItemInputDimension());
        machine.itemInputHeight = getHeight(recipeType.getItemInputDimension());
        machine.itemOutputWidth = getWidth(recipeType.getItemOutputDimension());
        machine.itemOutputHeight = getHeight(recipeType.getItemOutputDimension());
        machine.fluidInputWidth = getWidth(recipeType.getFluidInputDimension());
        machine.fluidInputHeight = getHeight(recipeType.getFluidInputDimension());
        machine.fluidOutputWidth = getWidth(recipeType.getFluidOutputDimension());
        machine.fluidOutputHeight = getHeight(recipeType.getFluidOutputDimension());
        machine.supportsFluids = machine.fluidInputWidth != null || machine.fluidOutputWidth != null;
        machine.supportsSpecialItems = false;
        return machine;
    }

    public static CanonicalRecipe mapRecipe(Recipe recipe, GregTechRecipe gregTechRecipe) {
        CanonicalRecipe canonical = new CanonicalRecipe();
        canonical.recipeId = recipe.getId();
        canonical.family = inferFamily(recipe, gregTechRecipe);
        canonical.sourcePlugin = canonical.family;
        canonical.sourceMod = recipe.getRecipeType() != null ? recipe.getRecipeType().getCategory() : "unknown";
        canonical.machine = recipe.getRecipeType() != null ? mapRecipeType(recipe.getRecipeType()) : null;
        canonical.layout = buildLayout(recipe);
        canonical.itemInputs = mapItemInputs(recipe.getItemInputs());
        canonical.itemOutputs = mapItemOutputs(recipe.getItemOutputs());
        canonical.fluidInputs = mapFluidInputs(recipe.getFluidInputs());
        canonical.fluidOutputs = mapFluidOutputs(recipe.getFluidOutputs());
        canonical.probabilities = buildProbabilityMap(recipe);
        canonical.metadata = buildMetadata(recipe, gregTechRecipe);
        canonical.renderHints = buildRenderHints(recipe);
        canonical.extensions = buildExtensions(recipe, gregTechRecipe);

        if (canonical.machine != null && gregTechRecipe != null) {
            canonical.machine.supportsSpecialItems =
                    gregTechRecipe.getSpecialItems() != null && !gregTechRecipe.getSpecialItems().isEmpty();
        }

        return canonical;
    }

    private static String buildItemSearchTerms(Item item) {
        StringBuilder builder = new StringBuilder();
        appendToken(builder, item.getInternalName());
        appendToken(builder, item.getLocalizedName());
        appendToken(builder, item.getModId());
        appendToken(builder, item.getTooltip());
        return builder.toString().trim();
    }

    private static void appendToken(StringBuilder builder, String value) {
        if (value == null || value.isEmpty()) return;
        if (builder.length() > 0) builder.append(' ');
        builder.append(value.replace('\n', ' '));
    }

    public static String normalizeImageFileName(String imageFilePath) {
        if (imageFilePath == null) return null;
        String[] prefixes = {
                "item" + File.separator,
                "fluid" + File.separator,
                "item/",
                "fluid/"
        };
        for (String prefix : prefixes) {
            if (imageFilePath.startsWith(prefix)) {
                return imageFilePath.substring(prefix.length());
            }
        }
        return imageFilePath;
    }

    private static String buildRenderAssetRef(String family, String id) {
        return "nesqlpp:" + family + "/" + id;
    }

    private static Integer getWidth(Dimension dimension) {
        return dimension == null ? null : dimension.getWidth();
    }

    private static Integer getHeight(Dimension dimension) {
        return dimension == null ? null : dimension.getHeight();
    }

    private static String inferLayoutClass(RecipeType recipeType) {
        if (recipeType.getItemInputDimension() != null) {
          int width = recipeType.getItemInputDimension().getWidth();
          int height = recipeType.getItemInputDimension().getHeight();
          if (width == 3 && height == 3) return "grid-3x3";
          if (width > 3 || height > 3) return "oversized-grid";
        }
        if (recipeType.getFluidInputDimension() != null || recipeType.getFluidOutputDimension() != null) {
          return "machine-fluid";
        }
        return "machine-generic";
    }

    private static String inferFamily(Recipe recipe, GregTechRecipe gregTechRecipe) {
        if (gregTechRecipe != null) return "gregtech";
        if (recipe.getRecipeType() == null) return "unknown";

        String category = recipe.getRecipeType().getCategory().toLowerCase();
        String type = recipe.getRecipeType().getType().toLowerCase();

        if (category.contains("botania")) return "botania";
        if (category.contains("thaumcraft") || type.contains("infusion") || type.contains("arcane")) return "thaumcraft";
        if (category.contains("blood")) return "blood_magic";
        if (category.contains("minecraft")) return "minecraft";
        if (category.contains("forge")) return "forge";
        if (category.contains("avaritia")) return "avaritia";
        return category;
    }

    private static Map<String, Object> buildLayout(Recipe recipe) {
        Map<String, Object> layout = new LinkedHashMap<>();
        RecipeType recipeType = recipe.getRecipeType();
        Map<String, Object> specialMetadata = readSpecialMetadata(recipe.getId());

        layout.put("contractVersion", 1);
        if (recipeType != null) {
            layout.put("layoutClass", inferLayoutClass(recipeType));
            if (recipeType.getItemInputDimension() != null) {
                layout.put("itemInputWidth", recipeType.getItemInputDimension().getWidth());
                layout.put("itemInputHeight", recipeType.getItemInputDimension().getHeight());
            }
            if (recipeType.getItemOutputDimension() != null) {
                layout.put("itemOutputWidth", recipeType.getItemOutputDimension().getWidth());
                layout.put("itemOutputHeight", recipeType.getItemOutputDimension().getHeight());
            }
            if (recipeType.getFluidInputDimension() != null) {
                layout.put("fluidInputWidth", recipeType.getFluidInputDimension().getWidth());
                layout.put("fluidInputHeight", recipeType.getFluidInputDimension().getHeight());
            }
            if (recipeType.getFluidOutputDimension() != null) {
                layout.put("fluidOutputWidth", recipeType.getFluidOutputDimension().getWidth());
                layout.put("fluidOutputHeight", recipeType.getFluidOutputDimension().getHeight());
            }
            layout.put("shapeless", recipeType.isShapeless());

            Map<String, Object> fallbackGrid = buildFallbackGrid(recipeType);
            if (!fallbackGrid.isEmpty()) {
                layout.put("fallbackGrid", fallbackGrid);
            }
        } else {
            layout.put("shapeless", false);
        }

        Map<String, Object> canvas = buildCanvas(specialMetadata);
        if (!canvas.isEmpty()) {
            layout.put("canvas", canvas);
        }

        Map<String, Object> legacyHints = buildLegacyHints(specialMetadata);
        if (!legacyHints.isEmpty()) {
            layout.put("legacyHints", legacyHints);
        }

        List<Map<String, Object>> itemSlots = buildItemSlots(specialMetadata);
        if (!itemSlots.isEmpty()) {
            layout.put("itemSlots", itemSlots);
        }

        List<Map<String, Object>> fluidSlots = buildFluidSlots(recipe, recipeType);
        if (!fluidSlots.isEmpty()) {
            layout.put("fluidSlots", fluidSlots);
        }

        Map<String, Object> bindings = buildBindings(specialMetadata);
        if (!bindings.isEmpty()) {
            layout.put("bindings", bindings);
        }
        return layout;
    }

    private static List<Map<String, Object>> mapItemInputs(Map<Integer, ItemGroup> itemInputs) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (itemInputs == null) return result;
        for (Map.Entry<Integer, ItemGroup> entry : sortedEntries(itemInputs)) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("slotIndex", entry.getKey());
            group.put("groupId", entry.getValue().getId());
            List<Map<String, Object>> variants = new ArrayList<>();
            for (ItemStack stack : entry.getValue().getItemStacks()) {
                Map<String, Object> variant = new LinkedHashMap<>();
                variant.put("itemId", stack.getItem().getId());
                addSemanticItemRefs(variant, stack.getItem());
                variant.put("stackSize", stack.getStackSize());
                variants.add(variant);
            }
            group.put("variants", variants);
            group.put("oreDictionary", entry.getValue().getItemStacks().size() > 1);
            result.add(group);
        }
        return result;
    }

    private static List<Map<String, Object>> mapItemOutputs(Map<Integer, ItemStackWithProbability> itemOutputs) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (itemOutputs == null) return result;
        for (Map.Entry<Integer, ItemStackWithProbability> entry : sortedEntries(itemOutputs)) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("slotIndex", entry.getKey());
            output.put("itemId", entry.getValue().getItem().getId());
            addSemanticItemRefs(output, entry.getValue().getItem());
            output.put("stackSize", entry.getValue().getStackSize());
            output.put("probability", entry.getValue().getProbability());
            result.add(output);
        }
        return result;
    }

    private static void addSemanticItemRefs(Map<String, Object> target, Item item) {
        if (target == null || item == null) {
            return;
        }
        SemanticItemIdentity identity = SemanticItemIdentityMapper.map(item);
        target.put("publicItemId", identity.publicItemId);
        target.put("variantId", identity.variantId);
        target.put("payloadHash", identity.payloadHash);
        target.put("semanticFamily", identity.family);
        target.put("semanticClassification", identity.classification);
    }

    private static List<Map<String, Object>> mapFluidInputs(Map<Integer, FluidGroup> fluidInputs) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (fluidInputs == null) return result;
        for (Map.Entry<Integer, FluidGroup> entry : sortedEntries(fluidInputs)) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("slotIndex", entry.getKey());
            List<Map<String, Object>> variants = new ArrayList<>();
            for (FluidStack stack : entry.getValue().getFluidStacks()) {
                Map<String, Object> variant = new LinkedHashMap<>();
                variant.put("fluidId", stack.getFluid().getId());
                variant.put("amount", stack.getAmount());
                variants.add(variant);
            }
            group.put("variants", variants);
            result.add(group);
        }
        return result;
    }

    private static List<Map<String, Object>> mapFluidOutputs(Map<Integer, FluidStackWithProbability> fluidOutputs) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (fluidOutputs == null) return result;
        for (Map.Entry<Integer, FluidStackWithProbability> entry : sortedEntries(fluidOutputs)) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("slotIndex", entry.getKey());
            output.put("fluidId", entry.getValue().getFluid().getId());
            output.put("amount", entry.getValue().getAmount());
            output.put("probability", entry.getValue().getProbability());
            result.add(output);
        }
        return result;
    }

    private static Map<String, Object> buildProbabilityMap(Recipe recipe) {
        Map<String, Object> probability = new LinkedHashMap<>();
        probability.put("hasItemOutputProbability", recipe.getItemOutputs() != null && recipe.getItemOutputs().values().stream().anyMatch(stack -> stack.getProbability() != 1.0));
        probability.put("hasFluidOutputProbability", recipe.getFluidOutputs() != null && recipe.getFluidOutputs().values().stream().anyMatch(stack -> stack.getProbability() != 1.0));
        return probability;
    }

    private static Map<String, Object> buildMetadata(Recipe recipe, GregTechRecipe gregTechRecipe) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (gregTechRecipe != null) {
            metadata.put("voltageTier", gregTechRecipe.getVoltageTier());
            metadata.put("voltage", gregTechRecipe.getVoltage());
            metadata.put("amperage", gregTechRecipe.getAmperage());
            metadata.put("duration", gregTechRecipe.getDuration());
            metadata.put("totalEU", (long) gregTechRecipe.getVoltage() * gregTechRecipe.getDuration() * gregTechRecipe.getAmperage());
            metadata.put("requiresCleanroom", gregTechRecipe.isRequiresCleanroom());
            metadata.put("requiresLowGravity", gregTechRecipe.isRequiresLowGravity());
            metadata.put("additionalInfo", gregTechRecipe.getAdditionalInfo());
        } else if (recipe.getRecipeType() != null) {
            VoltageInfo voltageInfo = parseVoltageInfo(recipe.getRecipeType().getIconInfo());
            if (voltageInfo.voltageTier != null) {
                metadata.put("voltageTier", voltageInfo.voltageTier);
            }
            if (voltageInfo.voltage != null) {
                metadata.put("voltage", voltageInfo.voltage);
            }
        }

        SpecialRecipeMetadataRegistry.SpecialRecipeMetadata specialMetadata =
                SpecialRecipeMetadataRegistry.getMetadata(recipe.getId());
        if (specialMetadata != null && specialMetadata.getData() != null) {
            metadata.putAll(specialMetadata.getData());
            metadata.put("specialRecipeType", specialMetadata.getRecipeType());
        }
        return metadata;
    }

    private static Map<String, Object> buildRenderHints(Recipe recipe) {
        Map<String, Object> renderHints = new LinkedHashMap<>();
        if (recipe.getRecipeType() != null && recipe.getRecipeType().getIcon() != null) {
            renderHints.put("machineIconAssetRef", buildRenderAssetRef("item", recipe.getRecipeType().getIcon().getId()));
        }
        renderHints.put("renderMode", "render-asset-ref");
        return renderHints;
    }

    private static Map<String, Object> buildExtensions(Recipe recipe, GregTechRecipe gregTechRecipe) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        if (gregTechRecipe != null) {
            Map<String, Object> gregtech = new LinkedHashMap<>();
            List<String> specialItemIds = new ArrayList<>();
            if (gregTechRecipe.getSpecialItems() != null) {
                for (Item item : gregTechRecipe.getSpecialItems()) {
                    specialItemIds.add(item.getId());
                }
            }
            gregtech.put("specialItemIds", specialItemIds);
            gregtech.put("modOwners", gregTechRecipe.getModOwners());
            extensions.put("gregtech", gregtech);
        }
        return extensions;
    }

    private static Map<String, Object> readSpecialMetadata(String recipeId) {
        SpecialRecipeMetadataRegistry.SpecialRecipeMetadata specialMetadata =
                SpecialRecipeMetadataRegistry.getMetadata(recipeId);
        if (specialMetadata == null || specialMetadata.getData() == null) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(specialMetadata.getData());
    }

    private static Map<String, Object> buildFallbackGrid(RecipeType recipeType) {
        Map<String, Object> fallbackGrid = new LinkedHashMap<>();
        putDimension(fallbackGrid, "itemInput", recipeType == null ? null : recipeType.getItemInputDimension());
        putDimension(fallbackGrid, "itemOutput", recipeType == null ? null : recipeType.getItemOutputDimension());
        putDimension(fallbackGrid, "fluidInput", recipeType == null ? null : recipeType.getFluidInputDimension());
        putDimension(fallbackGrid, "fluidOutput", recipeType == null ? null : recipeType.getFluidOutputDimension());
        if (recipeType != null) {
            fallbackGrid.put("shapeless", recipeType.isShapeless());
        }
        return fallbackGrid;
    }

    private static void putDimension(Map<String, Object> target, String key, Dimension dimension) {
        if (dimension == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("width", dimension.getWidth());
        data.put("height", dimension.getHeight());
        target.put(key, data);
    }

    private static Map<String, Object> buildCanvas(Map<String, Object> metadata) {
        Map<String, Object> canvas = new LinkedHashMap<>();
        Integer width = asPositiveInteger(metadata.get("handlerWidth"));
        Integer height = asPositiveInteger(metadata.get("handlerHeight"));
        Integer yShift = asInteger(metadata.get("yShift"));
        String imageResource = asNonBlankString(metadata.get("imageResource"));

        if (width != null) canvas.put("width", width);
        if (height != null) canvas.put("height", height);
        if (yShift != null) canvas.put("yShift", yShift);
        if (imageResource != null) canvas.put("backgroundResource", imageResource);
        if (!canvas.isEmpty()) {
            canvas.put("coordinateSpace", "nei_pixels");
            canvas.put("align", "top-left");
        }
        return canvas;
    }

    private static Map<String, Object> buildLegacyHints(Map<String, Object> metadata) {
        Map<String, Object> legacyHints = new LinkedHashMap<>();
        copyIfPresent(legacyHints, "handler", metadata.get("handler"));
        copyIfPresent(legacyHints, "handlerId", metadata.get("handlerId"));
        copyIfPresent(legacyHints, "handlerClass", metadata.get("handlerClass"));
        copyIfPresent(legacyHints, "modName", metadata.get("modName"));
        copyIfPresent(legacyHints, "modId", metadata.get("modId"));
        copyIfPresent(legacyHints, "handlerIcon", metadata.get("handlerIcon"));
        copyIfPresent(legacyHints, "handlerWidth", metadata.get("handlerWidth"));
        copyIfPresent(legacyHints, "handlerHeight", metadata.get("handlerHeight"));
        copyIfPresent(legacyHints, "maxRecipesPerPage", metadata.get("maxRecipesPerPage"));
        copyIfPresent(legacyHints, "yShift", metadata.get("yShift"));
        copyIfPresent(legacyHints, "imageResource", metadata.get("imageResource"));
        copyIfPresent(legacyHints, "itemNotes", metadata.get("itemNotes"));
        return legacyHints;
    }

    private static List<Map<String, Object>> buildItemSlots(Map<String, Object> metadata) {
        List<Map<String, Object>> itemSlots = new ArrayList<>();
        appendPositionedSlots(itemSlots, metadata.get("inputSlotLayout"), "input");
        appendPositionedSlots(itemSlots, metadata.get("outputSlotLayout"), "output");
        appendPositionedSlots(itemSlots, metadata.get("otherSlotLayout"), "other");
        return itemSlots;
    }

    private static void appendPositionedSlots(
            List<Map<String, Object>> target, Object rawValue, String defaultRole) {
        if (!(rawValue instanceof List<?>)) {
            return;
        }
        for (Object entry : (List<?>) rawValue) {
            if (!(entry instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> slot = new LinkedHashMap<>();
            Map<?, ?> rawMap = (Map<?, ?>) entry;
            slot.put("key", defaultRole + "-" + target.size());
            slot.put("role", asNonBlankString(rawMap.get("role")) != null ? asNonBlankString(rawMap.get("role")) : defaultRole);
            copyIfPresent(slot, "slotIndex", rawMap.get("slotIndex"));
            copyIfPresent(slot, "x", rawMap.get("x"));
            copyIfPresent(slot, "y", rawMap.get("y"));
            copyIfPresent(slot, "w", rawMap.get("w"));
            copyIfPresent(slot, "h", rawMap.get("h"));
            copyIfPresent(slot, "column", rawMap.get("column"));
            copyIfPresent(slot, "row", rawMap.get("row"));
            copyIfPresent(slot, "itemId", rawMap.get("itemId"));
            copyIfPresent(slot, "coordinateSpace", rawMap.get("coordinateSpace"));
            copyIfPresent(slot, "source", rawMap.get("source"));
            target.add(slot);
        }
    }

    private static List<Map<String, Object>> buildFluidSlots(Recipe recipe, RecipeType recipeType) {
        List<Map<String, Object>> fluidSlots = new ArrayList<>();
        appendSyntheticFluidSlots(
                fluidSlots,
                recipe.getFluidInputs(),
                "input",
                recipeType == null ? null : recipeType.getFluidInputDimension());
        appendSyntheticFluidSlots(
                fluidSlots,
                recipe.getFluidOutputs(),
                "output",
                recipeType == null ? null : recipeType.getFluidOutputDimension());
        return fluidSlots;
    }

    private static void appendSyntheticFluidSlots(
            List<Map<String, Object>> target,
            Map<Integer, ?> entries,
            String role,
            Dimension dimension) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        int width = dimension != null && dimension.getWidth() > 0 ? dimension.getWidth() : entries.size();
        for (Map.Entry<Integer, ?> entry : sortedEntries(entries)) {
            int slotIndex = entry.getKey();
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("key", "fluid-" + role + "-" + slotIndex);
            slot.put("role", role);
            slot.put("slotIndex", slotIndex);
            slot.put("column", width > 0 ? slotIndex % width : slotIndex);
            slot.put("row", width > 0 ? slotIndex / width : 0);
            slot.put("coordinateSpace", "grid");
            slot.put("source", "synthetic_grid");
            target.add(slot);
        }
    }

    private static Map<String, Object> buildBindings(Map<String, Object> metadata) {
        Map<String, Object> bindings = new LinkedHashMap<>();
        copyIfPresent(bindings, "aspects", metadata.get("aspects"));
        copyIfPresent(bindings, "research", metadata.get("research"));
        copyIfPresent(bindings, "instability", metadata.get("instability"));
        copyIfPresent(bindings, "centralItemId", metadata.get("centralItemId"));
        copyIfPresent(bindings, "centerInputSlotIndex", metadata.get("centerInputSlotIndex"));
        copyIfPresent(bindings, "componentSlotOrder", metadata.get("componentSlotOrder"));
        copyIfPresent(bindings, "manaCost", metadata.get("manaCost"));
        copyIfPresent(bindings, "ticks", metadata.get("ticks"));
        copyIfPresent(bindings, "bloodCost", metadata.get("bloodCost"));
        copyIfPresent(bindings, "lpCost", metadata.get("lpCost"));
        copyIfPresent(bindings, "consumptionRate", metadata.get("consumptionRate"));
        copyIfPresent(bindings, "drainRate", metadata.get("drainRate"));
        copyIfPresent(bindings, "tartaricCost", metadata.get("tartaricCost"));
        copyIfPresent(bindings, "tier", metadata.get("tier"));
        copyIfPresent(bindings, "isWeakActivation", metadata.get("isWeakActivation"));
        return bindings;
    }

    private static void copyIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String && ((String) value).trim().isEmpty()) {
            return;
        }
        target.put(key, value);
    }

    private static Integer asInteger(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    private static Integer asPositiveInteger(Object value) {
        Integer converted = asInteger(value);
        return converted != null && converted > 0 ? converted : null;
    }

    private static String asNonBlankString(Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        String text = ((String) value).trim();
        return text.isEmpty() ? null : text;
    }

    private static <T> List<Map.Entry<Integer, T>> sortedEntries(Map<Integer, T> map) {
        List<Map.Entry<Integer, T>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Comparator.comparingInt(Map.Entry::getKey));
        return entries;
    }

    private static VoltageInfo parseVoltageInfo(String text) {
        VoltageInfo info = new VoltageInfo();
        if (text == null || text.isEmpty()) {
            return info;
        }

        java.util.regex.Pattern pattern =
                java.util.regex.Pattern.compile("([A-Za-z]+)\\s*\\(\\s*(\\d+)\\s*EU/t\\s*\\)");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            info.voltageTier = matcher.group(1);
            info.voltage = Integer.parseInt(matcher.group(2));
        }
        return info;
    }

    private static final class VoltageInfo {
        private String voltageTier;
        private Integer voltage;
    }
}
