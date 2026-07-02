package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.nativeui.NativeUiExportAbi;
import com.github.dcysteine.nesql.exporter.plugin.nei.metadata.NeiUiFamilyClassifier;
import com.github.dcysteine.nesql.exporter.plugin.nei.metadata.NeiUiTemplateLayoutSpecs;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.GZIPOutputStream;

final class RawExportNeiFactWriter {
    private static final Map<String, String> MACHINE_CATALYST_RULES = loadBundledMachineCatalystRules();

    private final File repositoryDirectory;
    private final File rawDir;
    private final String schemaVersion;

    RawExportNeiFactWriter(File repositoryDirectory, File rawDir, String schemaVersion) {
        this.repositoryDirectory = repositoryDirectory;
        this.rawDir = rawDir;
        this.schemaVersion = schemaVersion;
    }

    RawNeiFactCounts write() throws IOException {
        RawNeiFactCounts counts = new RawNeiFactCounts();
        JsonObject browserLayout = readObject(new File(repositoryDirectory, "canonical/browser-layout-index.json"));
        if (browserLayout != null) {
            JsonArray groups = browserLayout.getAsJsonArray("groups");
            JsonArray order = browserLayout.getAsJsonArray("defaultEntries");
            counts.groups = writeArrayAsJsonl(groups, new File(rawDir, "facts/nei/groups.jsonl.gz"));
            counts.neiOrderEntries = writeArrayAsJsonl(order, new File(rawDir, "facts/nei/order.jsonl.gz"));
            counts.neiBrowserContract = buildNeiBrowserContract(browserLayout, groups, order);
            counts.neiRuntimePanelItems = readLong(browserLayout, "neiRuntimeItemCount", 0L);
            counts.neiExportOnlyItems = readLong(browserLayout, "exportOnlyItemCount", 0L);
            counts.neiBrowserItems = readLong(browserLayout, "itemCount", countArray(browserLayout, "items"));
            counts.neiDefaultEntries = readLong(browserLayout, "defaultEntryCount", order == null ? 0L : order.size());
            counts.neiHiddenItems = readLong(browserLayout, "hiddenItemCount", 0L);
            if (counts.neiBrowserContract != null) {
                counts.neiFallbackGroups = counts.neiBrowserContract.fallbackGroupCount;
                counts.neiNativeGroups = counts.neiBrowserContract.nativeGroupCount;
                counts.neiSyntheticGroups = counts.neiBrowserContract.syntheticGroupCount;
                counts.neiRepresentativeMismatches = counts.neiBrowserContract.representativeMismatchCount;
            }
        } else {
            createEmptyJsonl(new File(rawDir, "facts/nei/groups.jsonl.gz"));
            createEmptyJsonl(new File(rawDir, "facts/nei/order.jsonl.gz"));
        }
        counts.neiGuidFilterRules = writeGuidFilterRules(rawDir);
        counts.neiHiddenItemRules = writeHiddenItemRules(rawDir);
        HandlerMetadataCounts handlerCounts = writeHandlerMetadata(rawDir);
        counts.neiHandlers = handlerCounts.handlers;
        counts.neiHandlerLayouts = handlerCounts.layouts;
        UiFamilyCensusCounts uiFamilyCensusCounts = readUiFamilyCensusCounts(rawDir);
        counts.uiFamilyCensusHandlers = uiFamilyCensusCounts.handlers;
        counts.uiFamilyCensusFamilies = uiFamilyCensusCounts.families;
        UiTemplateCatalogCounts uiTemplateCatalogCounts = readUiTemplateCatalogCounts(rawDir);
        counts.uiTemplateCatalogHandlers = uiTemplateCatalogCounts.handlers;
        counts.uiTemplateCatalogTemplates = uiTemplateCatalogCounts.templates;
        counts.uiTemplateCatalogFamilies = uiTemplateCatalogCounts.families;
        return counts;
    }

    private NeiBrowserContract buildNeiBrowserContract(
            JsonObject browserLayout,
            JsonArray groups,
            JsonArray defaultEntries) {
        NeiBrowserContract contract = new NeiBrowserContract();
        contract.schemaVersion = NativeUiExportAbi.schema(schemaVersion, "nei-browser-contract");
        contract.generatedAt = utcNow();
        contract.neiRuntimeSnapshot = readBoolean(browserLayout, "neiRuntimeSnapshot", false);
        contract.neiRuntimePanelItemCount = readLong(browserLayout, "neiRuntimeItemCount", 0L);
        contract.exportOnlyItemCount = readLong(browserLayout, "exportOnlyItemCount", 0L);
        contract.browserItemCount = readLong(browserLayout, "itemCount", countArray(browserLayout, "items"));
        contract.groupCount = readLong(browserLayout, "groupCount", groups == null ? 0L : groups.size());
        contract.defaultEntryCount = readLong(browserLayout, "defaultEntryCount", defaultEntries == null ? 0L : defaultEntries.size());
        JsonObject source = browserLayout == null ? null : browserLayout.getAsJsonObject("source");
        if (source != null) {
            contract.orderSource = readString(source, "order", null);
            contract.groupingSource = readString(source, "grouping", null);
            contract.guidFiltersSource = readString(source, "guidFilters", null);
            contract.hiddenItemsSource = readString(source, "hiddenItems", null);
        }
        contract.guidFilterRuleCount = readLong(browserLayout, "guidFilterRuleCount", 0L);
        contract.hiddenItemRuleCount = readLong(browserLayout, "hiddenItemRuleCount", 0L);
        contract.hiddenItemCount = readLong(browserLayout, "hiddenItemCount", 0L);
        if (groups != null) {
            for (JsonElement element : groups) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject group = element.getAsJsonObject();
                String groupKey = readString(group, "groupKey", "");
                if (groupKey.startsWith("fallback:")) {
                    contract.fallbackGroupCount++;
                } else if (groupKey.startsWith("nei:")) {
                    contract.nativeGroupCount++;
                } else if (!groupKey.isEmpty()) {
                    contract.syntheticGroupCount++;
                }
                JsonArray members = group.getAsJsonArray("memberItemIds");
                contract.groupedMemberCount += members == null ? 0 : members.size();
                String representative = readString(group, "representativeItemId", null);
                if (representative == null || representative.isEmpty()) {
                    contract.missingRepresentativeCount++;
                } else if (!arrayContainsString(members, representative)) {
                    contract.representativeMismatchCount++;
                    if (contract.representativeMismatchSamples.size() < 50) {
                        BrowserContractMismatch sample = new BrowserContractMismatch();
                        sample.groupKey = groupKey;
                        sample.groupLabel = readString(group, "groupLabel", null);
                        sample.representativeItemId = representative;
                        sample.firstMemberItemIds = firstStrings(members, 5);
                        contract.representativeMismatchSamples.add(sample);
                    }
                }
            }
        }
        contract.ungroupedBrowserItemCount = Math.max(0L, contract.browserItemCount - contract.groupedMemberCount);
        contract.runtimeToBrowserDelta = contract.browserItemCount - contract.neiRuntimePanelItemCount;
        contract.status = contract.browserItemCount > 0 && contract.representativeMismatchCount == 0 ? "ok" : "warning";
        contract.summary = "NEI panel items="
                + contract.neiRuntimePanelItemCount
                + ", NeoNEI browser items="
                + contract.browserItemCount
                + ", groups="
                + contract.groupCount
                + ", fallbackGroups="
                + contract.fallbackGroupCount
                + ", hiddenItems="
                + contract.hiddenItemCount
                + ", representativeMismatches="
                + contract.representativeMismatchCount
                + ".";
        return contract;
    }

    private static long countArray(JsonObject object, String key) {
        JsonArray array = object == null ? null : object.getAsJsonArray(key);
        return array == null ? 0L : array.size();
    }

    private static long readLong(JsonObject object, String key, long fallback) {
        try {
            JsonElement element = object == null ? null : object.get(key);
            return element == null || element.isJsonNull() ? fallback : element.getAsLong();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static UiFamilyCensusCounts readUiFamilyCensusCounts(File rawDir) {
        UiFamilyCensusCounts counts = new UiFamilyCensusCounts();
        JsonObject report = readObject(new File(rawDir, "validation/ui-family-census.json"));
        JsonObject summary = report == null ? null : report.getAsJsonObject("summary");
        counts.handlers = readLong(summary, "handlerCount", 0L);
        counts.families = readLong(summary, "familyCount", 0L);
        return counts;
    }

    private static UiTemplateCatalogCounts readUiTemplateCatalogCounts(File rawDir) {
        UiTemplateCatalogCounts counts = new UiTemplateCatalogCounts();
        JsonObject report = readObject(new File(rawDir, "validation/ui-template-catalog.json"));
        JsonObject summary = report == null ? null : report.getAsJsonObject("summary");
        counts.handlers = readLong(summary, "handlerCount", 0L);
        counts.templates = readLong(summary, "templateCount", 0L);
        counts.families = readLong(summary, "familyCount", 0L);
        return counts;
    }

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        try {
            JsonElement element = object == null ? null : object.get(key);
            return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String readString(JsonObject object, String key, String fallback) {
        try {
            JsonElement element = object == null ? null : object.get(key);
            return element == null || element.isJsonNull() ? fallback : element.getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean arrayContainsString(JsonArray array, String expected) {
        if (array == null || expected == null) {
            return false;
        }
        for (JsonElement element : array) {
            if (element != null && !element.isJsonNull() && expected.equals(element.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> firstStrings(JsonArray array, int limit) {
        List<String> values = new ArrayList<String>();
        if (array == null || limit <= 0) {
            return values;
        }
        for (JsonElement element : array) {
            if (values.size() >= limit) {
                break;
            }
            if (element != null && !element.isJsonNull()) {
                values.add(element.getAsString());
            }
        }
        return values;
    }

    private long writeGuidFilterRules(File rawDir) throws IOException {
        File source = resolveNativeNeiRulePath(
                "nesql.guidFiltersCfg",
                "NESQL_GUID_FILTERS_CFG",
                "guidfilters.cfg",
                "assets/nei/cfg/guidfilters.cfg");
        JsonArray rows = new JsonArray();
        if (source != null) {
            try (BufferedReader reader = openUtf8Reader(source)) {
                String rawLine;
                int lineNumber = 0;
                while ((rawLine = reader.readLine()) != null) {
                    lineNumber++;
                    String line = stripBom(rawLine).trim();
                    if (line.length() == 0 || line.startsWith("#")) {
                        continue;
                    }
                    JsonObject row = new JsonObject();
                    row.addProperty("schemaVersion", NativeUiExportAbi.schema(schemaVersion, "nei-guidfilter-rule"));
                    row.addProperty("sourceKind", "gtnh-nei-config");
                    row.addProperty("sourceFile", source.getName());
                    row.addProperty("lineNumber", lineNumber);
                    row.addProperty("raw", line);
                    int comma = line.indexOf(',');
                    String itemExpression = comma >= 0 ? line.substring(0, comma).trim() : line;
                    String nbtPath = comma >= 0 ? line.substring(comma + 1).trim() : "";
                    row.addProperty("itemExpression", itemExpression);
                    row.addProperty("nbtPath", nbtPath.length() == 0 ? null : nbtPath);
                    row.addProperty("normalizedItemExpression", normalizeRuleToken(itemExpression));
                    row.addProperty("normalizedNbtPath", nbtPath.length() == 0 ? null : normalizeRuleToken(nbtPath));
                    rows.add(row);
                }
            }
        }
        return writeArrayAsJsonl(rows, new File(rawDir, "facts/nei/guidfilters.jsonl.gz"));
    }

    private long writeHiddenItemRules(File rawDir) throws IOException {
        File source = resolveNativeNeiRulePath(
                "nesql.hiddenItemsCfg",
                "NESQL_HIDDEN_ITEMS_CFG",
                "hiddenitems.cfg",
                null);
        JsonArray rows = new JsonArray();
        if (source != null) {
            try (BufferedReader reader = openUtf8Reader(source)) {
                String rawLine;
                int lineNumber = 0;
                while ((rawLine = reader.readLine()) != null) {
                    lineNumber++;
                    String line = stripBom(rawLine).trim();
                    if (line.length() == 0 || line.startsWith("#") || line.startsWith(";")) {
                        continue;
                    }
                    JsonObject row = new JsonObject();
                    row.addProperty("schemaVersion", NativeUiExportAbi.schema(schemaVersion, "nei-hidden-item-rule"));
                    row.addProperty("sourceKind", "gtnh-nei-config");
                    row.addProperty("sourceFile", source.getName());
                    row.addProperty("lineNumber", lineNumber);
                    row.addProperty("raw", line);
                    row.addProperty("itemExpression", line);
                    row.addProperty("normalizedItemExpression", normalizeRuleToken(line));
                    rows.add(row);
                }
            }
        }
        return writeArrayAsJsonl(rows, new File(rawDir, "facts/nei/hiddenitems.jsonl.gz"));
    }

    private HandlerMetadataCounts writeHandlerMetadata(File rawDir) throws IOException {
        JsonArray sourceEntries = loadBundledHandlerMetadata();
        JsonArray handlerRows = new JsonArray();
        JsonArray layoutRows = new JsonArray();
        Set<String> seenHandlers = new LinkedHashSet<String>();
        boolean requiresGtNeiBackgroundAsset = false;
        int ordinal = 0;
        for (JsonElement element : sourceEntries) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject source = element.getAsJsonObject();
            String handlerClass = trimToNull(readString(source, "handler", null));
            if (handlerClass == null || seenHandlers.contains(handlerClass)) {
                continue;
            }
            seenHandlers.add(handlerClass);
            String itemName = trimToNull(readString(source, "itemName", null));
            String modId = trimToNull(readString(source, "modId", null));
            String modName = trimToNull(readString(source, "modName", null));
            String displayName = handlerDisplayName(handlerClass, itemName);
            String handlerKey = stableHandlerKey(handlerClass, itemName, ordinal++);
            int width = parseInt(readString(source, "handlerWidth", null), 166);
            int height = parseInt(readString(source, "handlerHeight", null), 65);
            int maxPerPage = parseInt(readString(source, "maxRecipesPerPage", null), 1);
            int yShift = parseInt(readString(source, "yShift", null), 0);
            String imageResource = trimToEmpty(readString(source, "imageResource", null));
            String family = NeiUiFamilyClassifier.classifyHandlerFamily(handlerClass, itemName, modId);
            String layoutKind = NeiUiFamilyClassifier.inferLayoutKind(handlerClass, itemName, family);
            JsonObject nativeBackground = buildNativeBackground(source, family, layoutKind, width, height, yShift);
            requiresGtNeiBackgroundAsset = requiresGtNeiBackgroundAsset
                    || isGtModularUiBackground(nativeBackground);

            JsonObject handler = new JsonObject();
            handler.addProperty("schemaVersion", NativeUiExportAbi.schema(schemaVersion, "nei-handler"));
            handler.addProperty("handlerKey", handlerKey);
            handler.addProperty("handlerClass", handlerClass);
            handler.addProperty("displayName", displayName);
            handler.addProperty("localizedName", displayName);
            handler.addProperty("canonicalMachineFamily", family);
            handler.addProperty("modId", modId);
            handler.addProperty("modName", modName);
            handler.addProperty("catalystItemName", itemName);
            handler.addProperty("preferredMachineItemName", preferredMachineItemName(handlerClass, itemName, family));
            handler.addProperty("gtMultiblockPreferred", isLikelyGtMultiblock(handlerClass, itemName, family));
            handler.addProperty("maxRecipesPerPage", maxPerPage);
            handler.addProperty("handlerWidth", width);
            handler.addProperty("handlerHeight", height);
            handler.addProperty("yShift", yShift);
            handler.addProperty("imageResource", imageResource);
            handler.add("nativeBackground", cloneJsonObject(nativeBackground));
            handler.add("source", new JsonParser().parse(source.toString()));
            handlerRows.add(handler);

            JsonObject layout = new JsonObject();
            layout.addProperty("schemaVersion", NativeUiExportAbi.schema(schemaVersion, "nei-handler-layout"));
            layout.addProperty("handlerKey", handlerKey);
            layout.addProperty("handlerClass", handlerClass);
            layout.addProperty("canonicalMachineFamily", family);
            layout.addProperty("layoutKind", layoutKind);
            layout.addProperty("coordinateSpace", NativeUiExportAbi.COORDINATE_SPACE);
            layout.addProperty("scaleMode", NativeUiExportAbi.SCALE_MODE);
            layout.addProperty("anchor", NativeUiExportAbi.ANCHOR);
            layout.addProperty("width", width);
            layout.addProperty("height", height);
            layout.addProperty("yShift", yShift);
            layout.addProperty("maxRecipesPerPage", maxPerPage);
            layout.addProperty("imageResource", imageResource);
            addImageRegion(layout, source);
            layout.add("nativeBackground", cloneJsonObject(nativeBackground));
            layout.add("slots", NeiUiTemplateLayoutSpecs.defaultLayoutSlotsJson(layoutKind));
            layout.add("textOverlays", new JsonArray());
            layout.add("hotspots", new JsonArray());
            layout.add("viewports", new JsonArray());
            layoutRows.add(layout);
        }

        HandlerMetadataCounts counts = new HandlerMetadataCounts();
        if (requiresGtNeiBackgroundAsset) {
            materializeGtNeiBackgroundAsset(rawDir);
        }
        counts.handlers = writeArrayAsJsonl(handlerRows, new File(rawDir, NativeUiExportAbi.NEI_HANDLERS_FILE));
        counts.layouts = writeArrayAsJsonl(layoutRows, new File(rawDir, NativeUiExportAbi.NEI_HANDLER_LAYOUTS_FILE));
        return counts;
    }

    private static boolean isGtModularUiBackground(JsonObject background) {
        return background != null
                && "gt-modular-ui".equals(readString(background, "kind", ""))
                && ("captured".equals(readString(background, "status", ""))
                || "semantic".equals(readString(background, "status", "")));
    }

    private JsonObject buildNativeBackground(
            JsonObject source,
            String family,
            String layoutKind,
            int width,
            int height,
            int yShift) {
        String imageResource = trimToEmpty(readString(source, "imageResource", null));
        int imageWidth = parseInt(readString(source, "imageWidth", null), 0);
        int imageHeight = parseInt(readString(source, "imageHeight", null), 0);
        JsonObject background = new JsonObject();
        background.addProperty("schemaVersion", NativeUiExportAbi.schema(schemaVersion, "native-ui-background"));
        background.addProperty("width", width);
        background.addProperty("height", height);
        background.addProperty("yShift", yShift);
        background.addProperty("layoutKind", layoutKind);
        background.addProperty("canonicalMachineFamily", family);
        background.addProperty("coordinateSpace", NativeUiExportAbi.COORDINATE_SPACE);
        background.addProperty("scaleMode", NativeUiExportAbi.SCALE_MODE);
        background.addProperty("anchor", NativeUiExportAbi.ANCHOR);
        if (!imageResource.trim().isEmpty() && imageWidth > 0 && imageHeight > 0) {
            background.addProperty("status", NativeUiExportAbi.BACKGROUND_STATUS_CAPTURED);
            background.addProperty("kind", NativeUiExportAbi.BACKGROUND_KIND_TEXTURE_REGION);
            background.addProperty("resource", imageResource);
            JsonObject region = new JsonObject();
            region.addProperty("x", parseInt(readString(source, "imageX", null), 0));
            region.addProperty("y", parseInt(readString(source, "imageY", null), 0));
            region.addProperty("width", imageWidth);
            region.addProperty("height", imageHeight);
            background.add("region", region);
            return background;
        }
        if ("gregtech-machine".equals(family)) {
            background.addProperty("status", NativeUiExportAbi.BACKGROUND_STATUS_CAPTURED);
            background.addProperty("kind", NativeUiExportAbi.BACKGROUND_KIND_GT_MODULAR_UI);
            background.addProperty("source", "GTNEIDefaultHandler.drawUI(ModularWindow.getBackground)");
            background.addProperty("drawable", "GTUITextures.BACKGROUND_NEI_SINGLE_RECIPE");
            background.addProperty("assetRef", NativeUiExportAbi.GT_NEI_BACKGROUND_ASSET_REF);
            background.addProperty("resource", NativeUiExportAbi.GT_NEI_BACKGROUND_RESOURCE);
            background.addProperty("scaling", NativeUiExportAbi.BACKGROUND_SCALING_NINE_SLICE);
            JsonObject texture = new JsonObject();
            texture.addProperty("width", 64);
            texture.addProperty("height", 64);
            texture.addProperty("borderU", 2);
            texture.addProperty("borderV", 2);
            background.add("texture", texture);
            JsonObject offset = new JsonObject();
            offset.addProperty("x", 3);
            offset.addProperty("y", 3);
            background.add("recipeBackgroundOffset", offset);
            JsonObject size = new JsonObject();
            size.addProperty("width", Math.max(0, width - 6));
            size.addProperty("height", Math.max(0, height - yShift - 6));
            background.add("recipeBackgroundSize", size);
            background.addProperty("captureRequired", false);
            return background;
        }
        background.addProperty("status", NativeUiExportAbi.BACKGROUND_STATUS_MISSING);
        background.addProperty("kind", NativeUiExportAbi.BACKGROUND_KIND_UNKNOWN);
        background.addProperty("captureRequired", true);
        return background;
    }

    private static JsonObject cloneJsonObject(JsonObject source) {
        return new JsonParser().parse(source.toString()).getAsJsonObject();
    }

    private static void materializeGtNeiBackgroundAsset(File rawDir) throws IOException {
        File target = new File(rawDir, NativeUiExportAbi.GT_NEI_BACKGROUND_ASSET_REF.replace('/', File.separatorChar));
        File parent = target.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        File temporary = new File(parent, target.getName() + ".tmp");
        deleteIfExists(temporary);
            ResourceLocation location = new ResourceLocation(NativeUiExportAbi.GT_NEI_BACKGROUND_RESOURCE);
        try (InputStream input = net.minecraft.client.Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(location)
                .getInputStream();
             FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    output.write(buffer, 0, read);
                }
            }
        } catch (Exception e) {
            deleteIfExists(temporary);
            throw new IOException("Failed to materialize required GT NEI ModularUI background asset: " + location, e);
        }
        if (!temporary.isFile() || temporary.length() <= 0L) {
            deleteIfExists(temporary);
            throw new IOException("Materialized GT NEI ModularUI background asset is empty: " + location);
        }
        try {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void addImageRegion(JsonObject layout, JsonObject source) {
        int imageWidth = parseInt(readString(source, "imageWidth", null), 0);
        int imageHeight = parseInt(readString(source, "imageHeight", null), 0);
        if (imageWidth <= 0 || imageHeight <= 0) {
            return;
        }
        JsonObject region = new JsonObject();
        region.addProperty("x", parseInt(readString(source, "imageX", null), 0));
        region.addProperty("y", parseInt(readString(source, "imageY", null), 0));
        region.addProperty("width", imageWidth);
        region.addProperty("height", imageHeight);
        layout.add("imageRegion", region);
    }

    private static JsonArray loadBundledHandlerMetadata() throws IOException {
        InputStream stream = RawExportNeiFactWriter.class
                .getClassLoader()
                .getResourceAsStream("nesql/nei/handler-metadata.json");
        if (stream == null) {
            return new JsonArray();
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                return new JsonArray();
            }
            JsonArray entries = parsed.getAsJsonObject().getAsJsonArray("entries");
            return entries == null ? new JsonArray() : entries;
        }
    }

    private static Map<String, String> loadBundledMachineCatalystRules() {
        LinkedHashMap<String, String> rules = new LinkedHashMap<String, String>();
        InputStream stream = RawExportNeiFactWriter.class
                .getClassLoader()
                .getResourceAsStream("gtnh-semantic-rules/machine-catalyst-rules.json");
        if (stream == null) {
            return rules;
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                return rules;
            }
            JsonObject exactHandlers = parsed.getAsJsonObject().getAsJsonObject("exactHandlers");
            if (exactHandlers == null) {
                return rules;
            }
            for (Map.Entry<String, JsonElement> entry : exactHandlers.entrySet()) {
                String key = normalizeKey(entry.getKey());
                String value = entry.getValue() != null && entry.getValue().isJsonPrimitive()
                        ? trimToNull(entry.getValue().getAsString())
                        : null;
                if (key.length() > 0 && value != null) {
                    rules.put(key, value);
                }
            }
        } catch (Exception ignored) {
            return rules;
        }
        return rules;
    }

    private static String stableHandlerKey(String handlerClass, String itemName, int ordinal) {
        String base = normalizeKey(firstNonBlank(handlerClass, itemName, "handler-" + ordinal));
        return base.length() == 0 ? "handler-" + ordinal : base;
    }

    private static String handlerDisplayName(String handlerClass, String itemName) {
        String simple = handlerSimpleName(handlerClass);
        if (simple.endsWith("RecipeHandler")) {
            simple = simple.substring(0, simple.length() - "RecipeHandler".length());
        } else if (simple.endsWith("Handler")) {
            simple = simple.substring(0, simple.length() - "Handler".length());
        }
        simple = simple.replaceAll("([a-z])([A-Z])", "$1 $2").trim();
        return simple.length() == 0 ? firstNonBlank(itemName, "NEI Handler") : simple;
    }

    private static String preferredMachineItemName(String handlerClass, String itemName, String family) {
        for (String candidate : new String[] { handlerClass, itemName, family }) {
            String explicit = MACHINE_CATALYST_RULES.get(normalizeKey(candidate));
            if (explicit != null && explicit.length() > 0) {
                return explicit;
            }
        }
        return itemName;
    }

    private static boolean isLikelyGtMultiblock(String handlerClass, String itemName, String family) {
        String descriptor = (firstNonBlank(handlerClass, "") + " " + firstNonBlank(itemName, "") + " " + firstNonBlank(family, ""))
                .toLowerCase(java.util.Locale.ROOT);
        return descriptor.contains("gregtech")
                && (descriptor.contains("multiblock")
                || descriptor.contains("large")
                || descriptor.contains("mega")
                || descriptor.contains("gt.blockmachines"));
    }

    private static String handlerSimpleName(String handlerClass) {
        String value = firstNonBlank(handlerClass, "");
        int index = value.lastIndexOf('.');
        return index >= 0 ? value.substring(index + 1) : value;
    }


    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return null;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null || value.trim().length() == 0 ? fallback : Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._:-]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static File resolveNativeNeiRulePath(
            String propertyName,
            String envName,
            String fileName,
            String bundledResourceRelativePath) {
        List<File> candidates = new ArrayList<File>();
        String property = System.getProperty(propertyName);
        if (property != null && !property.trim().isEmpty()) {
            candidates.add(new File(property.trim()));
        }
        String env = System.getenv(envName);
        if (env != null && !env.trim().isEmpty()) {
            candidates.add(new File(env.trim()));
        }
        candidates.add(new File("config/NEI/" + fileName));
        candidates.add(new File("config/notenoughitems/" + fileName));
        candidates.add(new File("config/" + fileName));
        if (bundledResourceRelativePath != null && !bundledResourceRelativePath.trim().isEmpty()) {
            candidates.add(new File(bundledResourceRelativePath.trim()));
        }
        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    private static BufferedReader openUtf8Reader(File file) throws IOException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
    }

    private static String stripBom(String value) {
        return value == null ? "" : value.replaceFirst("^\\uFEFF", "");
    }

    private static String normalizeRuleToken(String value) {
        return (value == null ? "" : value).trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
    }

    private static JsonObject readObject(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            JsonElement element = new JsonParser().parse(reader);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long writeArrayAsJsonl(JsonArray array, File out) throws IOException {
        long count = 0L;
        Gson gson = new GsonBuilder().serializeNulls().create();
        File parent = out.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        try (OutputStreamWriter writer = createUtf8Writer(out)) {
            if (array != null) {
                for (JsonElement element : array) {
                    gson.toJson(element, writer);
                    writer.write('\n');
                    count++;
                }
            }
        }
        return count;
    }

    private static void writeJson(Gson gson, File out, Object value) throws IOException {
        File parent = out.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        try (FileOutputStream fos = new FileOutputStream(out);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            gson.toJson(value, writer);
        }
    }

    private static void createEmptyJsonl(File out) throws IOException {
        File parent = out.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        try (Writer ignored = createUtf8Writer(out)) {
            // Empty JSONL remains valid when a source is unavailable for this run.
        }
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create directory: " + directory.getAbsolutePath());
        }
    }

    private static void deleteIfExists(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteIfExists(child);
                }
            }
        }
        if (!file.delete() && file.exists()) {
            throw new IOException("Failed to delete file: " + file.getAbsolutePath());
        }
    }

    private static OutputStreamWriter createUtf8Writer(File out) throws IOException {
        FileOutputStream fos = new FileOutputStream(out);
        if (out.getName().endsWith(".gz")) {
            return new OutputStreamWriter(new GZIPOutputStream(fos), StandardCharsets.UTF_8);
        }
        return new OutputStreamWriter(fos, StandardCharsets.UTF_8);
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static final class HandlerMetadataCounts {
        long handlers;
        long layouts;
    }

    private static final class UiFamilyCensusCounts {
        long handlers;
        long families;
    }

    private static final class UiTemplateCatalogCounts {
        long handlers;
        long templates;
        long families;
    }

}
