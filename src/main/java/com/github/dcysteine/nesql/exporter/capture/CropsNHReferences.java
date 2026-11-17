package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import cpw.mods.fml.common.Loader;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native registration facts, never the four fake machine RecipeMaps or GUI caches. */
final class CropsNHReferences extends ReaderSnapshot implements NativeReferences {
    private static final String BASE = "com.gtnewhorizon.cropsnh.";
    private static final String HANDLERS = BASE + "compatibility.NEI.";
    private static final Map<String, String> SECTIONS;
    static {
        Map<String, String> sections = new LinkedHashMap<>();
        sections.put(HANDLERS + "NEICropsNHCropHandler", "crops");
        sections.put(HANDLERS + "NEICropsNHAlternateSeedHandler", "alternateSeeds");
        sections.put(HANDLERS + "NEICropsNHCropstickBreedingHandler", "mutations");
        sections.put(HANDLERS + "NEICropsNHMutationPoolHandler", "mutationPools");
        SECTIONS = Collections.unmodifiableMap(sections);
    }
    private Object[] roots;
    private JsonObject definition;
    private final String section, domain, shape;
    private final List<Entry> entries = new ArrayList<>();
    private final List<JsonObject> records = new ArrayList<>();
    private final Map<String, ItemStack> specimens = new LinkedHashMap<>();

    static boolean supportsClass(String name) { return SECTIONS.containsKey(name); }
    static boolean supports(ICraftingHandler handler) {
        return handler != null && supportsClass(handler.getClass().getName());
    }
    static Object[] registrationAnchors() {
        Object crops = field(type(BASE + "farming.registries.CropRegistry"), null, "instance");
        Object mutations = field(type(BASE + "farming.registries.MutationRegistry"), null, "instance");
        Object alternatives = field(crops, "alternateSeedList");
        return new Object[] {crops, field(crops, "cropRegistry"), field(crops, "registrationOrder"),
                alternatives, field(alternatives, "map"), field(alternatives, "wildcards"),
                mutations, field(mutations, "mutationMap"), field(mutations, "mutationPools"),
                field(mutations, "deterministicMutations"),
                field(type(BASE + "init.CropsNHItems"), null, "genericSeed")};
    }

    CropsNHReferences(ICraftingHandler handler, JsonObject source) {
        if (!supports(handler) || handler.getClass() != type(handler.getClass().getName()))
            throw unsupported("Unknown CropsNH reference handler");
        version("CropsNHReferences", "cropsnh");
        version("CropsNHReferences", "gtnhlib");
        version("CropsNHReferences", "gregtech");
        section = SECTIONS.get(handler.getClass().getName());
        roots = registrationAnchors();
        for (Object root : roots) if (root == null)
            throw new Jobs.Fault("handler_not_ready", "CropsNH registry is unavailable");
        shape = shape();
        entries.addAll(enumerate(true));
        definition = object("kind", "cropsNhDefinition", "source", source,
                "nativeVersion", Loader.instance().getIndexedModList().get("cropsnh").getVersion(),
                "semantics", "cropsnhRegistriesV1", "section", section,
                "entries", entries.size(), "consuming", false);
        JsonArray data = array(); data.add(definition);
        for (Entry entry : entries) data.add(entry.state.data);
        domain = Identity.content("reference", object("kind", "cropsnh", "records", data));
        for (int index = 0; index < data.size(); index++) {
            JsonObject row = object("domain", domain, "order", index, "data", data.get(index));
            row.addProperty("id", Identity.content("refrow", row));
            if (CanonicalJson.bytes(row).length > 1024 * 1024)
                throw new Jobs.Fault("reference_limit", "CropsNH reference leaf exceeds 1 MiB");
            records.add(row);
        }
        finishVerification();
    }

    public int size() { checkOpen(); return records.size(); }
    public String domain() { checkOpen(); return domain; }
    public JsonObject counts() { checkOpen(); return object("entries", entries.size(), "items", specimens.size()); }
    public void verify() {
        checkOpen();
        Object[] current = registrationAnchors();
        for (int index = 0; index < roots.length; index++) if (roots[index] != current[index]) throw changed();
        if (!shape.equals(shape())) throw changed();
    }
    public void finishVerification() {
        verify();
        List<Entry> current = enumerate(false);
        if (current.size() != entries.size()) throw changed();
        for (int index = 0; index < entries.size(); index++) {
            Entry before = entries.get(index), after = current.get(index);
            if (before.source != after.source || before.item != after.item || !Objects.equals(before.meta, after.meta)
                    || !before.location.equals(after.location))
                throw changed();
            same(before.state, after.state);
        }
        verify();
    }
    public void capture(int index, Facts facts) {
        verify();
        Entry entry = index == 0 ? null : entries.get(index - 1);
        if (entry != null) same(entry.state, read(entry, false));
        try {
            if (entry != null) for (String id : entry.state.items)
                if (!id.equals(facts.item(specimens.get(id).copy()))) throw changed();
            facts.row("references", copy(records.get(index)));
        } catch (RuntimeException | Error failure) {
            try { verify(); if (entry != null) same(entry.state, read(entry, false)); }
            catch (RuntimeException | Error drift) { if (failure != drift) failure.addSuppressed(drift); }
            throw failure;
        }
        verify();
        if (entry != null) same(entry.state, read(entry, false));
    }
    private String shape() {
        return ((Map<?, ?>) roots[1]).size() + ":" + ((List<?>) roots[2]).size()
                + ":" + ((Map<?, ?>) roots[4]).size() + ":" + ((Map<?, ?>) roots[5]).size()
                + ":" + ((Map<?, ?>) roots[8]).size() + ":" + ((Map<?, ?>) roots[9]).size();
    }
    private List<Entry> enumerate(boolean own) {
        List<Entry> result = new ArrayList<>();
        switch (section) {
            case "crops":
                for (Object crop : (List<?>) roots[2]) result.add(new Entry(crop, null, null));
                if (result.size() != ((Map<?, ?>) roots[1]).size()) throw changed();
                break;
            case "alternateSeeds":
                for (Map.Entry<?, ?> item : ((Map<?, ?>) roots[4]).entrySet())
                    for (Map.Entry<?, ?> meta : ((Map<?, ?>) item.getValue()).entrySet())
                        result.add(new Entry(meta.getValue(), (Item) item.getKey(), (Integer) meta.getKey()));
                for (Map.Entry<?, ?> item : ((Map<?, ?>) roots[5]).entrySet())
                    result.add(new Entry(item.getValue(), (Item) item.getKey(), null));
                break;
            case "mutations":
                collectMutations(roots[7], result, Collections.newSetFromMap(new IdentityHashMap<>()), 0, "");
                break;
            case "mutationPools":
                for (Object pool : ((Map<?, ?>) roots[8]).values()) result.add(new Entry(pool, null, null));
                break;
            default: throw unsupported("Unknown CropsNH section");
        }
        if (result.size() > 65536) throw new Jobs.Fault("reference_limit", "CropsNH registry entry budget exceeded");
        for (Entry entry : result) { Jobs.checkpoint(); entry.state = read(entry, own); }
        if (!section.equals("crops"))
            result.sort(Comparator.comparing((Entry entry) -> entry.state.signature).thenComparing(entry -> entry.location));
        return result;
    }
    private void collectMutations(Object node, List<Entry> result, Set<Object> seen, int depth, String path) {
        Jobs.checkpoint();
        if (depth > 4 || !seen.add(node) || seen.size() > 262144
                || node.getClass() != type(BASE + "farming.mutation.MutationMap"))
            throw unsupported("Unadapted CropsNH mutation tree");
        int ordinal = 0;
        for (Object mutation : (List<?>) field(node, "list")) {
            if (result.size() == 65536) throw new Jobs.Fault("reference_limit", "CropsNH mutation budget exceeded");
            result.add(new Entry(mutation, null, null, path + ":" + ordinal++));
        }
        for (Map.Entry<?, ?> child : ((Map<?, ?>) field(node, "map")).entrySet()) {
            String next = CanonicalJson.digest(object("path", path, "crop", registeredCrop(child.getKey())));
            collectMutations(child.getValue(), result, seen, depth + 1, next);
        }
    }
    private State read(Entry entry, boolean own) {
        Jobs.checkpoint();
        State state = new State(); state.anchors.add(entry.source);
        switch (section) {
            case "crops": state.data = crop(entry.source, state, own); break;
            case "alternateSeeds":
                Object selected = entry.meta == null ? ((Map<?, ?>) roots[5]).get(entry.item)
                        : ((Map<?, ?>) ((Map<?, ?>) roots[4]).get(entry.item)).get(entry.meta);
                if (selected != entry.source) throw changed();
                state.anchors.add(entry.item);
                state.data = object("kind", "cropsNhAlternateSeed", "registry", registry(entry.item),
                        "meta", entry.meta, "crop", cropKey(entry.source, state, own)); break;
            case "mutations":
                exact(entry.source, "farming.mutation.CropMutation");
                JsonArray parents = array();
                for (String key : new String[] {"parent1", "parent2", "parent3", "parent4"}) {
                    Object parent = field(entry.source, key);
                    if (parent != null) parents.add(cropKey(parent, state, own));
                }
                state.data = object("kind", "cropsNhMutation", "output", cropKey(field(entry.source, "output"), state, own),
                        "parents", parents, "requirements", requirements(field(entry.source, "requirements"), state, own),
                        "machineEut", field(entry.source, "breedingMachineEUt"),
                        "machineDuration", field(entry.source, "breedingMachineDuration")); break;
            case "mutationPools":
                exact(entry.source, "farming.mutation.MutationPool");
                JsonArray members = array();
                for (Object member : collection(field(entry.source, "members"), state))
                    members.add(cropKey(member, state, own));
                state.data = object("kind", "cropsNhMutationPool", "pool", field(entry.source, "name"),
                        "members", sorted(members)); break;
            default: throw unsupported("Unknown CropsNH section");
        }
        state.signature = CanonicalJson.digest(state.data);
        return state;
    }
    private JsonObject crop(Object crop, State state, boolean own) {
        registeredCrop(crop);
        JsonArray drops = array(), biomes = array();
        Map<?, ?> table = (Map<?, ?>) field(crop, "dropTable"); state.anchors.add(table);
        if (table.size() > 65536) throw unsupported("CropsNH drop budget exceeded");
        for (Map.Entry<?, ?> drop : table.entrySet())
            drops.add(object("stack", stack((ItemStack) drop.getKey(), state, own, false), "weight", drop.getValue()));
        for (Object biome : collection(field(crop, "likedBiomes"), state)) biomes.add(value(((Enum<?>) biome).name()));
        return object("kind", "cropsNhCrop", "crop", cropKey(crop, state, own),
                "tier", getter(crop, "getTier"), "growthDuration", getter(crop, "getGrowthDuration"),
                "minSeedBedTier", getter(crop, "getMinSeedBedTier"),
                "machineBreedingTier", getter(crop, "getMachineBreedingRecipeTier"),
                "hidden", getter(crop, "hideFromNEI"),
                "dropChanceBits", bits(getter(crop, "getDropChance")),
                "crossingThresholdBits", bits(getter(crop, "getCrossingThreshold")),
                "breedingThresholdBits", bits(getter(crop, "getBreedingThreshold")),
                "likedBiomes", sorted(biomes),
                "soil", soil(getter(crop, "getSoilTypes"), state, 0, new int[] {4096}),
                "requirements", requirements(field(crop, "growthRequirements"), state, own),
                "drops", sorted(drops), "alternateSeeds", stacks(field(crop, "alternateSeeds"), state, own),
                "duplicationCatalysts", stacks(field(crop, "duplicationCatalysts"), state, own));
    }
    private String registeredCrop(Object crop) {
        if (crop == null || !type(BASE + "api.CropCard").isInstance(crop)
                || !crop.getClass().getName().startsWith(BASE + "crops."))
            throw unsupported("CropsNH crop callback requires a dedicated adapter");
        String id = (String) field(crop, "id");
        if (((Map<?, ?>) roots[1]).get(id) != crop) throw changed();
        return id;
    }
    private JsonObject cropKey(Object crop, State state, boolean own) {
        String id = registeredCrop(crop); state.anchors.add(crop);
        ItemStack seed = new ItemStack((Item) roots[10], 1, 0);
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("crop", id); seed.setTagCompound(tag);
        return object("crop", id, "numericId", field(crop, "numericId"), "seed", specimen(seed, state, own));
    }
    private JsonArray requirements(Object source, State state, boolean own) {
        JsonArray result = array();
        for (Object requirement : collection(source, state)) {
            state.anchors.add(requirement);
            String name = requirement.getClass().getName();
            if (name.equals(BASE + "farming.requirements.growth.MinLightLevelGrowthRequirement"))
                result.add(object("kind", "minLight", "level", field(requirement, "minLightLevel")));
            else if (name.equals(BASE + "farming.requirements.growth.MaxLightLevelGrowthRequirement"))
                result.add(object("kind", "maxLight", "level", field(requirement, "maxLightLevel")));
            else if (name.equals(BASE + "farming.requirements.growth.MachineOnlyGrowthRequirement")
                    || name.equals(BASE + "farming.requirements.breeding.MachineOnlyBreedingRequirement"))
                result.add(object("kind", "machineOnly"));
            else if (name.equals(BASE + "farming.requirements.breeding.WorldOnlyBreedingRequirement"))
                result.add(object("kind", "worldOnly"));
            else if (name.equals(BASE + "farming.requirements.growth.MigratedCropGrowthRequirement"))
                result.add(object("kind", "migrated"));
            else if (name.equals(BASE + "farming.requirements.SubSoilRequirement")) {
                JsonArray materials = array(), ores = array();
                for (Object material : collection(field(requirement, "materials"), state))
                    materials.add(value(field(material, "mName")));
                for (Object ore : collection(field(requirement, "oreDictionaries"), state)) ores.add(value(ore));
                result.add(object("kind", "subSoil", "name", field(requirement, "materialDescription"),
                        "materials", sorted(materials), "ores", sorted(ores),
                        "blocks", blocks(field(requirement, "blocks"), state)));
            } else if (name.equals(BASE + "farming.requirements.breeding.MachineBreedingCatalystRequirement")) {
                JsonArray ores = array(), items = array();
                Map<?, ?> oreMap = (Map<?, ?>) field(requirement, "oreDictionaries");
                Map<?, ?> itemMap = (Map<?, ?>) field(requirement, "items");
                state.anchors.add(oreMap); state.anchors.add(itemMap);
                if (oreMap.size() > 65536 || itemMap.size() > 65536) throw unsupported("CropsNH catalyst budget exceeded");
                for (Map.Entry<?, ?> ore : oreMap.entrySet())
                    ores.add(object("ore", ore.getKey(), "amount", ore.getValue().toString()));
                Object wildcard = field(type("com.gtnewhorizon.gtnhlib.util.map.ItemStackMap"), null, "WILDCARD_TAG");
                for (Map.Entry<?, ?> item : itemMap.entrySet()) {
                    ItemStack key = ((ItemStack) item.getKey()).copy(); key.stackSize = (Integer) item.getValue();
                    items.add(stack(key, state, own, ((ItemStack) item.getKey()).getTagCompound() == wildcard));
                }
                result.add(object("kind", "machineCatalyst", "ores", sorted(ores), "items", sorted(items)));
            } else throw unsupported("Unadapted CropsNH requirement: " + name);
        }
        if (result.size() > 256) throw unsupported("CropsNH requirement budget exceeded");
        return result;
    }
    private JsonElement soil(Object source, State state, int depth, int[] remaining) {
        if (source == null) return JsonNull.INSTANCE;
        if (depth >= 16 || --remaining[0] < 0) throw unsupported("CropsNH soil tree exceeds its budget");
        state.anchors.add(source);
        if (source.getClass() == type(BASE + "farming.registries.SoilList"))
            return object("kind", "blocks", "name", field(source, "name"), "blocks", blocks(field(source, "validSoils"), state));
        exact(source, "farming.registries.CompoundSoilList");
        JsonArray children = array();
        for (Object child : collection(field(source, "soils"), state))
            children.add(soil(child, state, depth + 1, remaining));
        return object("kind", "anyOf", "name", field(source, "name"), "soils", children);
    }
    private JsonArray blocks(Object source, State state) {
        exact(source, "utility.MetaSet"); state.anchors.add(source);
        Map<?, ?> values = (Map<?, ?>) field(source, "map"); state.anchors.add(values);
        JsonArray result = array();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            Block block = (Block) entry.getKey(); state.anchors.add(block);
            String registry = Block.blockRegistry.getNameForObject(block);
            if (registry == null || Block.blockRegistry.getObject(registry) != block)
                throw unsupported("Unregistered CropsNH soil block");
            if (entry.getValue() == null) result.add(object("registry", registry, "meta", null));
            else for (Object meta : collection(entry.getValue(), state))
                result.add(object("registry", registry, "meta", meta));
            if (result.size() > 65536) throw unsupported("CropsNH soil block budget exceeded");
        }
        return sorted(result);
    }
    private JsonArray stacks(Object source, State state, boolean own) {
        JsonArray result = array();
        for (Object value : collection(source, state)) result.add(stack((ItemStack) value, state, own, false));
        return result;
    }
    private JsonObject stack(ItemStack source, State state, boolean own, boolean nbtWildcard) {
        if (source == null || source.getClass() != ItemStack.class || source.stackSize <= 0)
            throw unsupported("Invalid CropsNH stack template");
        int meta = (Integer) field(source, "itemDamage");
        boolean wildcard = meta == -1 || meta == 32767 || nbtWildcard;
        return object("registry", registry(source.getItem()), "meta", meta,
                "nbt", nbtWildcard ? JsonNull.INSTANCE : TypedNbt.encode(source.getTagCompound()),
                "nbtWildcard", nbtWildcard, "amount", Integer.toString(source.stackSize),
                "item", wildcard ? null : specimen(source, state, own));
    }
    private String specimen(ItemStack source, State state, boolean own) {
        String id = Identity.item(registry(source.getItem()), (Integer) field(source, "itemDamage"),
                TypedNbt.encode(source.getTagCompound()));
        state.items.add(id); state.anchors.add(source.getItem());
        if (own) { ItemStack copy = source.copy(); copy.stackSize = 1; specimens.put(id, copy); }
        return id;
    }
    private static String registry(Item item) {
        String id = Item.itemRegistry.getNameForObject(item);
        if (id == null || Item.itemRegistry.getObject(id) != item) throw unsupported("Unregistered CropsNH item");
        return id;
    }
    private static Collection<?> collection(Object source, State state) {
        if (!(source instanceof Collection) || ((Collection<?>) source).size() > 65536)
            throw unsupported("Unadapted or oversized CropsNH collection");
        state.anchors.add(source); return (Collection<?>) source;
    }
    private static Object getter(Object source, String name) {
        return invoke(source.getClass(), source, name, new Class<?>[0]);
    }
    private static JsonPrimitive bits(Object value) {
        float number = (Float) value;
        if (Float.isNaN(number) || Float.isInfinite(number)) throw unsupported("Nonfinite CropsNH native float");
        return new JsonPrimitive(Integer.toUnsignedLong(Float.floatToRawIntBits(number)));
    }
    private static void exact(Object source, String name) {
        if (source == null || source.getClass() != type(BASE + name)) throw unsupported("Unadapted CropsNH source: " + name);
    }
    private static JsonArray sorted(JsonArray source) {
        List<Map.Entry<String, JsonElement>> rows = new ArrayList<>();
        for (JsonElement row : source) rows.add(new AbstractMap.SimpleImmutableEntry<>(CanonicalJson.digest(row), row));
        rows.sort(Map.Entry.comparingByKey());
        JsonArray result = array(); for (Map.Entry<String, JsonElement> row : rows) result.add(row.getValue()); return result;
    }
    private static void same(State before, State after) {
        if (!before.signature.equals(after.signature) || before.anchors.size() != after.anchors.size()) throw changed();
        for (int index = 0; index < before.anchors.size(); index++)
            if (before.anchors.get(index) != after.anchors.get(index)) throw changed();
    }
    private static JsonObject copy(JsonObject row) { return new JsonParser().parse(row.toString()).getAsJsonObject(); }
    private static Jobs.Fault unsupported(String message) { return new Jobs.Fault("handler_unsupported", message); }
    private static Jobs.Fault changed() { return new Jobs.Fault("environment_changed", "CropsNH registration facts changed"); }
    private static final class Entry {
        final Object source; final Item item; final Integer meta; final String location; State state;
        Entry(Object source, Item item, Integer meta) { this(source, item, meta, ""); }
        Entry(Object source, Item item, Integer meta, String location) {
            this.source = source; this.item = item; this.meta = meta; this.location = location;
        }
    }
    private static final class State {
        JsonObject data; String signature;
        final List<Object> anchors = new ArrayList<>();
        final Set<String> items = new LinkedHashSet<>();
    }
    @Override protected void release() { roots = null; definition = null; entries.clear(); records.clear(); specimens.clear(); }
}
