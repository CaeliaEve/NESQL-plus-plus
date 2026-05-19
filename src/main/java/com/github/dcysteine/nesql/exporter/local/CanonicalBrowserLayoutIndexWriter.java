package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalExportMapper;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalItem;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.sql.base.item.Item;
import com.github.dcysteine.nesql.sql.base.item.ItemGroup;
import com.github.dcysteine.nesql.sql.base.item.ItemStack;
import com.github.dcysteine.nesql.sql.forge.OreDictionary;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.persistence.EntityManager;
import net.minecraft.util.EnumChatFormatting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Writes the NeoNEI item browser layout contract at export time.
 */
public class CanonicalBrowserLayoutIndexWriter {
    private static final String OUTPUT_DIRECTORY = "canonical";
    private static final String OUTPUT_FILE = "browser-layout-index.json";

    private final EntityManager entityManager;
    private final File exportDirectory;
    private final ModBasedItemDtoAssembler dtoAssembler = new ModBasedItemDtoAssembler();

    public CanonicalBrowserLayoutIndexWriter(EntityManager entityManager, File exportDirectory) {
        this.entityManager = entityManager;
        this.exportDirectory = exportDirectory;
    }

    public void export() throws IOException {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Writing NESQL++ browser layout index...");

        File canonicalDir = new File(exportDirectory, OUTPUT_DIRECTORY);
        if (!canonicalDir.exists()) {
            canonicalDir.mkdirs();
        }

        ModBasedItemDataset dataset = ModBasedItemDatasetLoader.load(entityManager);
        Map<String, Set<String>> oreDictionaryNamesByItemId = loadOreDictionaryNamesByItemId();
        List<BrowserItemCandidate> candidates = new ArrayList<>();
        int sourceOrder = 0;
        for (Item item : dataset.items) {
            ModBasedItemExporter.ItemDTO dto = dtoAssembler.toDto(item);
            BrowserItemCandidate candidate = new BrowserItemCandidate();
            candidate.itemId = dto.itemId;
            candidate.modId = dto.modId;
            candidate.internalName = dto.internalName;
            candidate.localizedName = dto.localizedName;
            candidate.damage = dto.damage;
            candidate.tooltip = dto.tooltip;
            candidate.nbt = dto.nbt;
            candidate.sourceOrder = sourceOrder++;
            candidate.oreDictionaryNames = oreDictionaryNamesByItemId.getOrDefault(dto.itemId, new LinkedHashSet<String>());
            candidates.add(candidate);
        }

        candidates.sort(CanonicalBrowserLayoutIndexWriter::compareBrowserOrder);
        for (int i = 0; i < candidates.size(); i++) {
            candidates.get(i).browserOrder = i;
        }

        Map<String, BrowserAssignment> curatedAssignments = buildCollapsibleAssignments(candidates);
        Set<String> blockedItemIds = new HashSet<>();
        for (BrowserAssignment assignment : curatedAssignments.values()) {
            if (assignment.groupKey != null && assignment.groupSize > 1) {
                blockedItemIds.add(assignment.itemId);
            }
        }
        Map<String, SyntheticAssignment> syntheticAssignments = buildSyntheticAssignments(candidates, blockedItemIds);
        Map<String, BrowserAssignment> assignments = mergeAssignments(candidates, curatedAssignments, syntheticAssignments);

        BrowserLayoutIndex index = new BrowserLayoutIndex();
        index.generatedAt = System.currentTimeMillis();
        index.source.order = "nesqlpp-browser-order:v1";
        index.source.grouping = "collapsibleitems+variant-fallback:v1";

        Map<String, BrowserGroup> groups = new LinkedHashMap<>();
        Set<String> seenDefaultGroups = new HashSet<>();
        int entryOrder = 0;
        for (BrowserItemCandidate candidate : candidates) {
            BrowserAssignment assignment = assignments.get(candidate.itemId);
            if (assignment == null) {
                continue;
            }

            BrowserLayoutItem item = new BrowserLayoutItem();
            item.itemId = candidate.itemId;
            item.browserOrder = candidate.browserOrder;
            item.groupKey = assignment.groupKey;
            item.groupLabel = assignment.groupLabel;
            item.groupSize = assignment.groupSize;
            item.groupSortOrder = assignment.groupSortOrder;
            item.representativeItemId = assignment.representativeItemId;
            item.expandedOrder = candidate.browserOrder;
            index.items.add(item);

            if (assignment.groupKey == null || assignment.groupSize <= 1) {
                index.defaultEntries.add(BrowserDefaultEntry.item(entryOrder++, candidate.itemId));
                continue;
            }

            BrowserGroup group = groups.computeIfAbsent(assignment.groupKey, key -> {
                BrowserGroup created = new BrowserGroup();
                created.groupKey = key;
                created.groupLabel = assignment.groupLabel;
                created.groupSize = assignment.groupSize;
                created.groupSortOrder = assignment.groupSortOrder;
                created.representativeItemId =
                        assignment.representativeItemId != null ? assignment.representativeItemId : candidate.itemId;
                return created;
            });
            group.memberItemIds.add(candidate.itemId);

            if (seenDefaultGroups.add(assignment.groupKey)) {
                index.defaultEntries.add(BrowserDefaultEntry.group(
                        entryOrder++,
                        group.representativeItemId,
                        assignment.groupKey,
                        assignment.groupLabel,
                        assignment.groupSize));
            }
        }

        index.groups.addAll(groups.values());
        index.itemCount = index.items.size();
        index.groupCount = index.groups.size();
        index.defaultEntryCount = index.defaultEntries.size();

        File outputFile = new File(canonicalDir, OUTPUT_FILE);
        Gson gson = new GsonBuilder().serializeNulls().create();
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            gson.toJson(index, writer);
        }

        Logger.chatMessage(EnumChatFormatting.GREEN + "NESQL++ browser layout index written:");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "  " + outputFile.getAbsolutePath());
        Logger.chatMessage(EnumChatFormatting.GRAY
                + "  items=" + index.itemCount
                + ", groups=" + index.groupCount
                + ", defaultEntries=" + index.defaultEntryCount);
    }

    private Map<String, Set<String>> loadOreDictionaryNamesByItemId() {
        Map<String, Set<String>> namesByItemId = new HashMap<>();
        try {
            List<OreDictionary> oreDictionaries = entityManager
                    .createQuery("SELECT o FROM OreDictionary o", OreDictionary.class)
                    .getResultList();
            for (OreDictionary oreDictionary : oreDictionaries) {
                ItemGroup group = oreDictionary.getItemGroup();
                if (group == null || group.getItemStacks() == null) {
                    continue;
                }
                String name = oreDictionary.getName();
                for (ItemStack stack : group.getItemStacks()) {
                    if (stack == null || stack.getItem() == null) {
                        continue;
                    }
                    CanonicalItem canonical = CanonicalExportMapper.mapItem(stack.getItem());
                    namesByItemId.computeIfAbsent(canonical.itemId, ignored -> new LinkedHashSet<>()).add(name);
                }
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to map ore dictionary names for browser layout; continuing without ore groups", e);
        }
        return namesByItemId;
    }

    private static Map<String, BrowserAssignment> buildCollapsibleAssignments(List<BrowserItemCandidate> candidates) {
        List<GroupRule> rules = loadCollapsibleRules();
        Map<String, BrowserAssignment> assignments = new LinkedHashMap<>();
        Map<String, GroupRule> matchedRuleByItemId = new HashMap<>();
        Map<String, Integer> groupSizes = new HashMap<>();

        for (BrowserItemCandidate candidate : candidates) {
            GroupRule matched = null;
            for (GroupRule rule : rules) {
                if (rule.matches(candidate)) {
                    matched = rule;
                    break;
                }
            }
            if (matched != null) {
                matchedRuleByItemId.put(candidate.itemId, matched);
                groupSizes.put(matched.key, groupSizes.getOrDefault(matched.key, 0) + 1);
            }
        }

        Map<String, Integer> groupSortOrder = new HashMap<>();
        int nextSortOrder = 0;
        for (BrowserItemCandidate candidate : candidates) {
            GroupRule matched = matchedRuleByItemId.get(candidate.itemId);
            BrowserAssignment assignment = new BrowserAssignment();
            assignment.itemId = candidate.itemId;
            if (matched == null) {
                assignment.groupSize = 1;
                assignment.groupSortOrder = nextSortOrder++;
                assignment.representativeItemId = candidate.itemId;
            } else {
                if (!groupSortOrder.containsKey(matched.key)) {
                    groupSortOrder.put(matched.key, nextSortOrder++);
                }
                assignment.groupKey = matched.key;
                assignment.groupLabel = matched.displayName;
                assignment.groupSize = groupSizes.getOrDefault(matched.key, 1);
                assignment.groupSortOrder = groupSortOrder.getOrDefault(matched.key, 0);
                assignment.representativeItemId = candidate.itemId;
            }
            assignments.put(candidate.itemId, assignment);
        }
        return assignments;
    }

    private static Map<String, SyntheticAssignment> buildSyntheticAssignments(
            List<BrowserItemCandidate> candidates,
            Set<String> blockedItemIds) {
        Map<String, List<BrowserItemCandidate>> internalFamilies = new LinkedHashMap<>();
        Map<String, List<BrowserItemCandidate>> exactFamilies = new LinkedHashMap<>();
        for (BrowserItemCandidate candidate : candidates) {
            String internalKey = normalize(candidate.modId) + "::" + normalize(candidate.internalName);
            String exactKey = internalKey + "::" + normalize(candidate.localizedName);
            if (!internalKey.equals("::")) {
                internalFamilies.computeIfAbsent(internalKey, ignored -> new ArrayList<>()).add(candidate);
            }
            if (!exactKey.equals("::::")) {
                exactFamilies.computeIfAbsent(exactKey, ignored -> new ArrayList<>()).add(candidate);
            }
        }

        Map<String, SyntheticAssignment> assignments = new HashMap<>();
        for (Map.Entry<String, List<BrowserItemCandidate>> entry : internalFamilies.entrySet()) {
            List<BrowserItemCandidate> family = entry.getValue();
            if (!canGroupFamily(family, blockedItemIds)) {
                continue;
            }
            Map<String, Integer> localizedCounts = new HashMap<>();
            for (BrowserItemCandidate candidate : family) {
                String label = trim(candidate.localizedName);
                if (!label.isEmpty()) {
                    localizedCounts.put(label, localizedCounts.getOrDefault(label, 0) + 1);
                }
            }
            String dominantLabel = null;
            int dominantCount = 0;
            for (Map.Entry<String, Integer> count : localizedCounts.entrySet()) {
                if (count.getValue() > dominantCount
                        || (count.getValue() == dominantCount
                        && (dominantLabel == null || count.getKey().compareTo(dominantLabel) < 0))) {
                    dominantLabel = count.getKey();
                    dominantCount = count.getValue();
                }
            }
            if (dominantLabel == null || dominantCount < 2 || ((double) dominantCount / (double) family.size()) < 0.6D) {
                continue;
            }
            Set<String> canonicalLabels = new HashSet<>();
            boolean hasPristineCanonical = false;
            for (BrowserItemCandidate candidate : family) {
                if (!hasVariantPayload(candidate.itemId)) {
                    String label = trim(candidate.localizedName);
                    if (!label.isEmpty()) {
                        canonicalLabels.add(label);
                    }
                    if (candidate.damage == 0 && dominantLabel.equals(label)) {
                        hasPristineCanonical = true;
                    }
                }
            }
            if (canonicalLabels.size() > 1 || !hasPristineCanonical) {
                continue;
            }
            assignSyntheticFamily(assignments, "internal::" + entry.getKey(), family, dominantLabel);
        }

        for (Map.Entry<String, List<BrowserItemCandidate>> entry : exactFamilies.entrySet()) {
            List<BrowserItemCandidate> remaining = new ArrayList<>();
            for (BrowserItemCandidate candidate : entry.getValue()) {
                if (!assignments.containsKey(candidate.itemId)) {
                    remaining.add(candidate);
                }
            }
            assignSyntheticFamily(assignments, "exact::" + entry.getKey(), remaining, null);
        }
        return assignments;
    }

    private static boolean canGroupFamily(List<BrowserItemCandidate> family, Set<String> blockedItemIds) {
        if (family.size() <= 1) {
            return false;
        }
        for (BrowserItemCandidate candidate : family) {
            if (blockedItemIds.contains(candidate.itemId)) {
                return false;
            }
        }
        return true;
    }

    private static void assignSyntheticFamily(
            Map<String, SyntheticAssignment> assignments,
            String familyKey,
            List<BrowserItemCandidate> family,
            String labelSeed) {
        if (family.size() <= 1) {
            return;
        }
        BrowserItemCandidate representative = family.stream()
                .min(CanonicalBrowserLayoutIndexWriter::compareRepresentativePriority)
                .orElse(null);
        if (representative == null) {
            return;
        }
        String groupKey = "variant:" + md5(familyKey);
        String groupLabel = labelSeed != null ? labelSeed : representative.localizedName;
        for (BrowserItemCandidate candidate : family) {
            SyntheticAssignment assignment = new SyntheticAssignment();
            assignment.itemId = candidate.itemId;
            assignment.groupKey = groupKey;
            assignment.groupLabel = groupLabel;
            assignment.groupSize = family.size();
            assignment.representativeItemId = representative.itemId;
            assignments.put(candidate.itemId, assignment);
        }
    }

    private static Map<String, BrowserAssignment> mergeAssignments(
            List<BrowserItemCandidate> candidates,
            Map<String, BrowserAssignment> primaryAssignments,
            Map<String, SyntheticAssignment> secondaryAssignments) {
        Map<String, BrowserAssignment> merged = new LinkedHashMap<>();
        Map<String, Integer> groupSortOrder = new HashMap<>();
        int nextSortOrder = 0;
        for (BrowserItemCandidate candidate : candidates) {
            BrowserAssignment primary = primaryAssignments.get(candidate.itemId);
            boolean primaryIsGrouped = primary != null && primary.groupKey != null && primary.groupSize > 1;
            SyntheticAssignment secondary = primaryIsGrouped ? null : secondaryAssignments.get(candidate.itemId);
            String groupKey = primaryIsGrouped ? primary.groupKey : secondary != null ? secondary.groupKey : null;
            String groupLabel = primaryIsGrouped ? primary.groupLabel : secondary != null ? secondary.groupLabel : null;
            int groupSize = primaryIsGrouped ? primary.groupSize : secondary != null ? secondary.groupSize : 1;
            String representativeItemId = primaryIsGrouped
                    ? primary.representativeItemId
                    : secondary != null ? secondary.representativeItemId : candidate.itemId;

            int resolvedSortOrder;
            if (groupKey != null && groupSize > 1) {
                if (!groupSortOrder.containsKey(groupKey)) {
                    groupSortOrder.put(groupKey, nextSortOrder++);
                }
                resolvedSortOrder = groupSortOrder.getOrDefault(groupKey, 0);
            } else {
                resolvedSortOrder = nextSortOrder++;
            }

            BrowserAssignment assignment = new BrowserAssignment();
            assignment.itemId = candidate.itemId;
            assignment.groupKey = groupKey != null && groupSize > 1 ? groupKey : null;
            assignment.groupLabel = groupKey != null && groupSize > 1 ? groupLabel : null;
            assignment.groupSize = groupKey != null && groupSize > 1 ? groupSize : 1;
            assignment.groupSortOrder = resolvedSortOrder;
            assignment.representativeItemId = representativeItemId;
            merged.put(candidate.itemId, assignment);
        }
        return merged;
    }

    private static List<GroupRule> loadCollapsibleRules() {
        File configFile = resolveCollapsibleConfigPath();
        List<GroupRule> rules = new ArrayList<>();
        if (configFile == null || !configFile.exists()) {
            Logger.MOD.warn("collapsibleitems.cfg not found; browser layout will use variant groups only");
            return rules;
        }

        GroupSettings pendingSettings = new GroupSettings();
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String rawLine;
            while ((rawLine = reader.readLine()) != null) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("; ")) {
                    pendingSettings = parseSettings(line);
                    continue;
                }
                CandidateMatcher matcher = compileFilterExpression(line);
                if (matcher == null) {
                    pendingSettings = new GroupSettings();
                    continue;
                }
                GroupRule rule = new GroupRule();
                rule.key = "gtnh-collapsible:" + md5(line);
                rule.displayName = pendingSettings.displayName;
                rule.matcher = matcher;
                rules.add(rule);
                pendingSettings = new GroupSettings();
            }
            Logger.MOD.info("Loaded {} collapsible item browser rules from {}", rules.size(), configFile.getAbsolutePath());
        } catch (Exception e) {
            Logger.MOD.warn("Failed to load collapsible item rules from {}; continuing without them", configFile.getAbsolutePath(), e);
        }
        return rules;
    }

    private static File resolveCollapsibleConfigPath() {
        List<File> candidates = new ArrayList<>();
        String property = System.getProperty("nesql.collapsibleItemsCfg");
        if (property != null && !property.trim().isEmpty()) {
            candidates.add(new File(property.trim()));
        }
        String env = System.getenv("NESQL_COLLAPSIBLE_ITEMS_CFG");
        if (env != null && !env.trim().isEmpty()) {
            candidates.add(new File(env.trim()));
        }
        candidates.add(new File("config/NEI/collapsibleitems.cfg"));
        candidates.add(new File("config/notenoughitems/collapsibleitems.cfg"));
        candidates.add(new File("config/collapsibleitems.cfg"));
        for (File candidate : candidates) {
            if (candidate.exists()) {
                return candidate;
            }
        }
        return null;
    }

    private static GroupSettings parseSettings(String line) {
        GroupSettings settings = new GroupSettings();
        java.util.regex.Matcher matcher = Pattern
                .compile("\\\"displayName\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                .matcher(line.substring(2).trim());
        if (matcher.find()) {
            settings.displayName = matcher.group(1).trim();
        }
        return settings;
    }

    private interface CandidateMatcher {
        boolean matches(BrowserItemCandidate candidate);
    }

    private static CandidateMatcher compileFilterExpression(String filterText) {
        List<CandidateMatcher> partMatchers = new ArrayList<>();
        for (String part : filterText.split("\\s*\\|\\s*")) {
            String trimmedPart = part.trim();
            if (trimmedPart.isEmpty()) {
                continue;
            }
            List<CandidateMatcher> tokenMatchers = new ArrayList<>();
            for (String token : trimmedPart.split("\\s+")) {
                CandidateMatcher matcher = compileToken(token);
                if (matcher != null) {
                    tokenMatchers.add(matcher);
                }
            }
            if (!tokenMatchers.isEmpty()) {
                partMatchers.add(candidate -> {
                    for (CandidateMatcher tokenMatcher : tokenMatchers) {
                        if (!tokenMatcher.matches(candidate)) {
                            return false;
                        }
                    }
                    return true;
                });
            }
        }
        if (partMatchers.isEmpty()) {
            return null;
        }
        return candidate -> {
            for (CandidateMatcher partMatcher : partMatchers) {
                if (partMatcher.matches(candidate)) {
                    return true;
                }
            }
            return false;
        };
    }

    private static CandidateMatcher compileToken(String token) {
        List<CandidateMatcher> positive = new ArrayList<>();
        List<CandidateMatcher> negative = new ArrayList<>();
        for (String rawRule : token.split(",")) {
            String rule = rawRule.trim();
            if (rule.isEmpty()) {
                continue;
            }
            boolean negated = rule.startsWith("!");
            CandidateMatcher matcher = compileSingleRule(negated ? rule.substring(1) : rule);
            if (matcher == null) {
                continue;
            }
            if (negated) {
                negative.add(matcher);
            } else {
                positive.add(matcher);
            }
        }
        if (positive.isEmpty() && negative.isEmpty()) {
            return null;
        }
        return candidate -> {
            boolean positiveMatch = positive.isEmpty();
            for (CandidateMatcher matcher : positive) {
                if (matcher.matches(candidate)) {
                    positiveMatch = true;
                    break;
                }
            }
            if (!positiveMatch) {
                return false;
            }
            for (CandidateMatcher matcher : negative) {
                if (matcher.matches(candidate)) {
                    return false;
                }
            }
            return true;
        };
    }

    private static CandidateMatcher compileSingleRule(String rule) {
        if (rule.startsWith("$")) {
            java.util.function.Predicate<String> textMatcher = buildTextMatcher(rule.substring(1));
            return textMatcher == null ? null : candidate -> {
                for (String name : candidate.oreDictionaryNames) {
                    if (textMatcher.test(name)) {
                        return true;
                    }
                }
                return false;
            };
        }
        if (rule.startsWith("tag.")) {
            String rawValue = "";
            int index = rule.indexOf('=');
            if (index >= 0 && index + 1 < rule.length()) {
                rawValue = rule.substring(index + 1);
            }
            java.util.function.Predicate<String> textMatcher = buildTextMatcher(rawValue);
            return textMatcher == null ? null : candidate -> textMatcher.test(candidate.nbt);
        }
        if (rule.matches("^\\d+(?:-\\d+)?$")) {
            String[] parts = rule.split("-", 2);
            int start = parseInt(parts[0], 0);
            int end = parts.length > 1 ? parseInt(parts[1], start) : start;
            return candidate -> candidate.damage >= start && candidate.damage <= end;
        }
        if (rule.startsWith("<") && rule.endsWith(">")) {
            String strict = rule.substring(1, rule.length() - 1);
            java.util.regex.Matcher matcher = Pattern.compile("^([^:]+):([^:]+)(?::(\\d+))?$").matcher(strict);
            if (!matcher.matches()) {
                return null;
            }
            String modId = normalize(matcher.group(1));
            String internalName = normalize(matcher.group(2));
            Integer damage = matcher.group(3) != null ? parseInt(matcher.group(3), 0) : null;
            return candidate -> normalize(candidate.modId).equals(modId)
                    && normalize(candidate.internalName).equals(internalName)
                    && (damage == null || damage == 32767 || candidate.damage == damage);
        }
        java.util.function.Predicate<String> textMatcher = buildTextMatcher(rule);
        return textMatcher == null ? null : candidate -> textMatcher.test(candidate.modId + ":" + candidate.internalName);
    }

    private static java.util.function.Predicate<String> buildTextMatcher(String searchText) {
        String normalized = trim(searchText);
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() >= 3 && normalized.startsWith("r/") && normalized.endsWith("/")) {
            try {
                Pattern pattern = Pattern.compile(
                        normalized.substring(2, normalized.length() - 1),
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                return value -> pattern.matcher(value == null ? "" : value).find();
            } catch (Exception ignored) {
                return null;
            }
        }
        String needle = normalize(normalized);
        return value -> normalize(value).contains(needle);
    }

    private static int compareBrowserOrder(BrowserItemCandidate left, BrowserItemCandidate right) {
        int leftMinecraft = normalize(left.modId).equals("minecraft") ? 0 : 1;
        int rightMinecraft = normalize(right.modId).equals("minecraft") ? 0 : 1;
        if (leftMinecraft != rightMinecraft) {
            return leftMinecraft - rightMinecraft;
        }
        int modCompare = normalize(left.modId).compareTo(normalize(right.modId));
        if (modCompare != 0) {
            return modCompare;
        }
        int tooltipCompare = compareNullable(parseTooltipNumericId(left.tooltip), parseTooltipNumericId(right.tooltip));
        if (tooltipCompare != 0) {
            return tooltipCompare;
        }
        int sourceCompare = Integer.compare(left.sourceOrder, right.sourceOrder);
        if (sourceCompare != 0) {
            return sourceCompare;
        }
        int internalCompare = normalize(left.internalName).compareTo(normalize(right.internalName));
        if (internalCompare != 0) {
            return internalCompare;
        }
        int damageCompare = Integer.compare(left.damage, right.damage);
        if (damageCompare != 0) {
            return damageCompare;
        }
        int labelCompare = normalize(left.localizedName).compareTo(normalize(right.localizedName));
        if (labelCompare != 0) {
            return labelCompare;
        }
        return normalize(left.itemId).compareTo(normalize(right.itemId));
    }

    private static int compareRepresentativePriority(BrowserItemCandidate left, BrowserItemCandidate right) {
        boolean leftHasVariant = hasVariantPayload(left.itemId);
        boolean rightHasVariant = hasVariantPayload(right.itemId);
        if (leftHasVariant != rightHasVariant) {
            return leftHasVariant ? 1 : -1;
        }
        boolean leftPristine = left.damage == 0;
        boolean rightPristine = right.damage == 0;
        if (leftPristine != rightPristine) {
            return leftPristine ? -1 : 1;
        }
        int damageCompare = Integer.compare(left.damage, right.damage);
        if (damageCompare != 0) {
            return damageCompare;
        }
        int sourceCompare = Integer.compare(left.sourceOrder, right.sourceOrder);
        if (sourceCompare != 0) {
            return sourceCompare;
        }
        return left.itemId.compareTo(right.itemId);
    }

    private static boolean hasVariantPayload(String itemId) {
        return itemId != null && itemId.split("~").length > 4;
    }

    private static Integer parseTooltipNumericId(String tooltip) {
        java.util.regex.Matcher matcher = Pattern.compile("#(\\d+)(?:/\\d+)?").matcher(tooltip == null ? "" : tooltip);
        if (!matcher.find()) {
            return null;
        }
        return parseInt(matcher.group(1), 0);
    }

    private static int compareNullable(Integer left, Integer right) {
        if (left == null && right == null) return 0;
        if (left == null) return 1;
        if (right == null) return -1;
        return left.compareTo(right);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String normalize(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static final class BrowserLayoutIndex {
        String schemaVersion = "nesqlpp/browser-layout-index/v1";
        long generatedAt;
        BrowserLayoutSource source = new BrowserLayoutSource();
        int itemCount;
        int groupCount;
        int defaultEntryCount;
        List<BrowserLayoutItem> items = new ArrayList<>();
        List<BrowserGroup> groups = new ArrayList<>();
        List<BrowserDefaultEntry> defaultEntries = new ArrayList<>();
    }

    private static final class BrowserLayoutSource {
        String order;
        String grouping;
    }

    private static final class BrowserItemCandidate {
        String itemId;
        String modId;
        String internalName;
        String localizedName;
        int damage;
        String tooltip;
        String nbt;
        int sourceOrder;
        int browserOrder;
        Set<String> oreDictionaryNames = new LinkedHashSet<>();
    }

    private static final class BrowserLayoutItem {
        String itemId;
        int browserOrder;
        String groupKey;
        String groupLabel;
        int groupSize;
        int groupSortOrder;
        String representativeItemId;
        int expandedOrder;
    }

    private static final class BrowserGroup {
        String groupKey;
        String groupLabel;
        int groupSize;
        int groupSortOrder;
        String representativeItemId;
        List<String> memberItemIds = new ArrayList<>();
    }

    private static final class BrowserDefaultEntry {
        int entryOrder;
        String entryKind;
        String itemId;
        String groupKey;
        String groupLabel;
        int groupSize;

        static BrowserDefaultEntry item(int entryOrder, String itemId) {
            BrowserDefaultEntry entry = new BrowserDefaultEntry();
            entry.entryOrder = entryOrder;
            entry.entryKind = "item";
            entry.itemId = itemId;
            entry.groupSize = 1;
            return entry;
        }

        static BrowserDefaultEntry group(int entryOrder, String itemId, String groupKey, String groupLabel, int groupSize) {
            BrowserDefaultEntry entry = new BrowserDefaultEntry();
            entry.entryOrder = entryOrder;
            entry.entryKind = "group-collapsed";
            entry.itemId = itemId;
            entry.groupKey = groupKey;
            entry.groupLabel = groupLabel;
            entry.groupSize = groupSize;
            return entry;
        }
    }

    private static final class BrowserAssignment {
        String itemId;
        String groupKey;
        String groupLabel;
        int groupSize;
        int groupSortOrder;
        String representativeItemId;
    }

    private static final class SyntheticAssignment {
        String itemId;
        String groupKey;
        String groupLabel;
        int groupSize;
        String representativeItemId;
    }

    private static final class GroupRule {
        String key;
        String displayName;
        CandidateMatcher matcher;

        boolean matches(BrowserItemCandidate candidate) {
            return matcher != null && matcher.matches(candidate);
        }
    }

    private static final class GroupSettings {
        String displayName;
    }
}
