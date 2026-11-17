package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import forestry.api.apiculture.*;
import forestry.api.arboriculture.*;
import forestry.api.genetics.*;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Species production/breeding information. No consuming recipe, cached GUI or simulated world. */
final class ForestryAddonsReferences implements NativeReferences {
    private static final String PREFIX = "net.bdew.neiaddons.";
    private static final String UTILS = PREFIX + "forestry.GeneticsUtils";
    private static final Map<String, String> ROOTS = new LinkedHashMap<>();

    static {
        ROOTS.put(PREFIX + "forestry.bees.BeeProduceHandler", "rootBees");
        ROOTS.put(PREFIX + "forestry.trees.TreeProduceHandler", "rootTrees");
        ROOTS.put(PREFIX + "forestry.bees.BeeBreedingHandler", "rootBees");
        ROOTS.put(PREFIX + "forestry.trees.TreeBreedingHandler", "rootTrees");
        ROOTS.put(PREFIX + "forestry.butterflies.ButterflyBreedingHandler", "rootButterflies");
        ROOTS.put(PREFIX + "botany.flowers.FlowerBreedingHandler", "rootFlowers");
    }

    private final ICraftingHandler handler;
    private final ISpeciesRoot root;
    private final JsonObject source;
    private final JsonArray rows;
    private final Map<String, ItemStack> specimens = new TreeMap<>();
    private final String signature, domain;
    private final int nativeEntries;
    private final boolean produce, secrets;

    static boolean supports(ICraftingHandler handler) {
        return handler != null && ROOTS.containsKey(handler.getClass().getName());
    }

    ForestryAddonsReferences(ICraftingHandler handler, JsonObject source) {
        if (!supports(handler)) throw fault("Unknown NEIAddons domain handler");
        MagicApi.version("ForestryAddonsReferences","NEIAddons");
        this.handler = handler;
        this.source = copy(source);
        Object raw = field(handler, "speciesRoot");
        if (!(raw instanceof ISpeciesRoot))
            throw new Jobs.Fault("handler_not_ready", "NEIAddons species root is not initialized");
        root = (ISpeciesRoot) raw;
        produce = handler.getClass().getName().contains("Produce");
        secrets = !produce && showSecret();
        rows = current(specimens);
        domain = rows.get(0).getAsJsonObject().get("domain").getAsString();
        signature = CanonicalJson.digest(rows);
        nativeEntries =
                rows.get(0)
                        .getAsJsonObject()
                        .getAsJsonObject("data")
                        .getAsJsonObject("counts")
                        .get("nativeEntries")
                        .getAsInt();
    }

    private Collection<?> registered() {
        Object raw = invoke(handler.getClass(), handler, "getAllSpecies", new Class<?>[0]);
        if (!(raw instanceof Collection))
            throw new Jobs.Fault(
                    "handler_not_ready", "Native NEIAddons species list is unavailable");
        return (Collection<?>) raw;
    }

    private JsonArray current(Map<String, ItemStack> items) {
        if (field(handler, "speciesRoot") != root) throw changed("Native species root changed");
        Collection<?> species = produce ? registered() : Collections.emptyList();
        return snapshot(root, source, species, !produce && showSecret(), items);
    }

    public int size() {
        return rows.size();
    }

    public String domain() {
        return domain;
    }

    public JsonObject counts() {
        return copy(
                rows.get(0).getAsJsonObject().getAsJsonObject("data").getAsJsonObject("counts"));
    }

    public void verify() {
        if (field(handler, "speciesRoot") != root
                || !ROOTS.get(handler.getClass().getName()).equals(root.getUID())
                || !produce && showSecret() != secrets)
            throw changed("NEIAddons root or secret policy changed");
        Collection<?> nativeRows = produce ? registered() : root.getMutations(false);
        if (nativeRows == null || nativeRows.size() != nativeEntries)
            throw changed("Native NEIAddons registry size changed");
    }

    public void finishVerification() {
        verify();
        if (!signature.equals(CanonicalJson.digest(current(new TreeMap<>()))))
            throw changed(
                    "Native NEIAddons species, specimens, products, mutation genotype, conditions or rates changed");
    }

    public void capture(int index, Facts facts) {
        Jobs.checkpoint();
        JsonObject row = copy(rows.get(index).getAsJsonObject()),
                data = row.getAsJsonObject("data");
        if (data.get("kind").getAsString().equals("forestryAddonsSpecies"))
            for (JsonElement form : data.getAsJsonArray("specimens"))
                fact(facts, form.getAsJsonObject().get("item").getAsString());
        if (data.get("kind").getAsString().equals("forestryAddonsOutput"))
            fact(facts, data.get("item").getAsString());
        facts.row("references", row);
    }

    private void fact(Facts facts, String id) {
        ItemStack nativeItem = specimens.get(id);
        if (nativeItem == null || !facts.item(nativeItem.copy()).equals(id))
            throw changed("Native NEIAddons item identity changed");
    }

    @Override
    public void close() {
        specimens.clear();
    }

    static JsonArray snapshot(
            ISpeciesRoot root,
            JsonObject source,
            Collection<?> registered,
            boolean showSecret,
            Map<String, ItemStack> specimens) {
        Jobs.checkpoint();
        String handler = source.get("handler").getAsString(), expected = ROOTS.get(handler);
        if (root == null || expected == null || !expected.equals(root.getUID()))
            throw new Jobs.Fault(
                    "handler_not_ready", "Native NEIAddons root does not match its handler");
        boolean produce = handler.contains("Produce");
        JsonObject memberTypes = memberTypes(root, produce);
        if (registered == null || registered.size() > 65536)
            throw fault("Native species list is unavailable or oversized");
        LinkedHashMap<String, IAlleleSpecies> species = new LinkedHashMap<>();
        JsonArray mutations = array();
        int eligible = 0, nativeEntries;
        long[] budget = {0};
        if (produce) {
            for (Object raw : registered) {
                if (!(raw instanceof IAlleleSpecies))
                    throw fault("Native species list contains an unknown entry");
                species((IAlleleSpecies) raw, root, species);
            }
            if (species.size() != registered.size())
                throw fault("Native species enumeration repeats a UID");
            nativeEntries = registered.size();
        } else {
            Collection<? extends IMutation> registry = root.getMutations(false);
            if (registry == null || registry.size() > 262144)
                throw new Jobs.Fault(
                        "handler_not_ready",
                        "Native mutation registry is unavailable or oversized");
            nativeEntries = registry.size();
            int ordinal = 0;
            for (IMutation mutation : new ArrayList<>(registry)) {
                Jobs.checkpoint();
                if (mutation == null || mutation.getRoot() != root)
                    throw fault("Native mutation has another or missing root");
                String first = species(mutation.getAllele0(), root, species),
                        second = species(mutation.getAllele1(), root, species);
                IAllele[] template = mutation.getTemplate();
                JsonArray genome = template(template);
                if (!(template[0] instanceof IAlleleSpecies))
                    throw fault("Mutation has no offspring species");
                String result = species((IAlleleSpecies) template[0], root, species);
                Collection<String> raw = mutation.getSpecialConditions();
                JsonArray conditions = raw == null ? null : array();
                if (raw != null) {
                    if (raw.size() > 256) throw fault("Native mutation conditions exceed budget");
                    for (String text : raw) conditions.add(value(text(text)));
                }
                boolean secret = mutation.isSecret(), listed = !secret || showSecret;
                if (listed) eligible++;
                append(
                        mutations,
                        object(
                                "kind",
                                "forestryAddonsMutation",
                                "ordinal",
                                ordinal++,
                                "owner",
                                mutation.getClass().getName(),
                                "parents",
                                array(first, second),
                                "result",
                                result,
                                "template",
                                genome,
                                "base",
                                scalar(mutation.getBaseChance()),
                                "secret",
                                secret,
                                "eligible",
                                listed,
                                "conditions",
                                conditions),
                        budget);
            }
        }
        JsonArray speciesRows = array(), outputs = array();
        int ordinal = 0;
        for (Map.Entry<String, IAlleleSpecies> entry : species.entrySet()) {
            Jobs.checkpoint();
            IAlleleSpecies s = entry.getValue();
            JsonArray template = template(root.getTemplate(entry.getKey()));
            if (!entry.getKey().equals(template.get(0).getAsString()))
                throw fault("Native specimen template does not match species UID");
            JsonArray forms = array();
            for (String role :
                    produce
                            ? new String[] {"Producer"}
                            : new String[] {"Parent1", "Parent2", "Offspring"})
                forms.add(member(s, role, specimens));
            String provider = null, jubilance = null;
            List<Output> produced = new ArrayList<>();
            if (produce) {
                if (s instanceof IAlleleBeeSpecies) {
                    add(
                            produced,
                            ((IAlleleBeeSpecies) s).getProductChances(),
                            "product",
                            specimens);
                    add(
                            produced,
                            ((IAlleleBeeSpecies) s).getSpecialtyChances(),
                            "specialty",
                            specimens);
                    if (s instanceof IAlleleBeeSpeciesCustom) {
                        IJubilanceProvider p = ((IAlleleBeeSpeciesCustom) s).getJubilanceProvider();
                        if (p != null) {
                            provider = p.getClass().getName();
                            jubilance = p.getDescription();
                            if (jubilance != null) text(jubilance);
                        }
                    }
                } else if (s instanceof IAlleleTreeSpecies && root instanceof ITreeRoot) {
                    ITree tree = (ITree) ForestryMembers.create(root, root.getTemplate(entry.getKey()));
                    if (tree == null) throw fault("Native tree individual is unavailable");
                    add(produced, tree.getProduceList(), "product", specimens);
                    add(produced, tree.getSpecialtyList(), "specialty", specimens);
                } else throw fault("Unadapted producer species callback");
            }
            produced.sort(
                    Comparator.comparing((Output o) -> o.role)
                            .thenComparing(o -> o.id)
                            .thenComparingInt(o -> o.amount)
                            .thenComparing(o -> o.rate.get("bits").getAsString()));
            int outputOrdinal = 0;
            for (Output output : produced)
                append(
                        outputs,
                        object(
                                "kind",
                                "forestryAddonsOutput",
                                "species",
                                entry.getKey(),
                                "ordinal",
                                outputOrdinal++,
                                "role",
                                output.role,
                                "item",
                                output.id,
                                "amount",
                                Integer.toString(output.amount),
                                "rate",
                                output.rate),
                        budget);
            append(
                    speciesRows,
                    object(
                            "kind",
                            "forestryAddonsSpecies",
                            "ordinal",
                            ordinal++,
                            "species",
                            entry.getKey(),
                            "name",
                            text(s.getName()),
                            "template",
                            template,
                            "specimens",
                            forms,
                            "listed",
                            produce ? !produced.isEmpty() : true,
                            "outputs",
                            produced.size(),
                            "jubilanceProvider",
                            provider,
                            "jubilance",
                            jubilance),
                    budget);
        }
        JsonArray data =
                array(
                        object(
                                "kind",
                                "forestryAddonsDefinition",
                                "source",
                                source,
                                "root",
                                expected,
                                "mode",
                                produce ? "produce" : "breeding",
                                "ready",
                                true,
                                "consuming",
                                false,
                                "showSecret",
                                produce ? null : showSecret,
                                "probability",
                                "nominalSpeciesValues",
                                "outputOrder",
                                "stableRoleItemAmountFloatBits",
                                "specimens",
                                "nativeAnalyzedTemplates",
                                "memberTypes",
                                memberTypes,
                                "counts",
                                object(
                                        "nativeEntries",
                                        nativeEntries,
                                        "species",
                                        species.size(),
                                        "outputs",
                                        outputs.size(),
                                        "mutations",
                                        mutations.size(),
                                        "eligible",
                                        eligible)));
        if (!memberTypes.equals(memberTypes(root, produce)))
            throw changed("Native member-role bindings changed during snapshot");
        for (JsonElement value : speciesRows) data.add(value);
        for (JsonElement value : outputs) data.add(value);
        for (JsonElement value : mutations) data.add(value);
        String domain =
                Identity.content("reference", object("kind", "forestryAddons", "records", data));
        JsonArray rows = array();
        long bytes = 0;
        for (int index = 0; index < data.size(); index++) {
            JsonObject row = object("domain", domain, "order", index, "data", data.get(index));
            row.addProperty("id", Identity.content("refrow", row));
            int size = CanonicalJson.bytes(row).length;
            if (size > 1024 * 1024 || (bytes += size) > 64L * 1024 * 1024)
                throw fault("Native NEIAddons reference snapshot exceeds its budget");
            rows.add(row);
        }
        return rows;
    }

    private static String species(
            IAlleleSpecies species, ISpeciesRoot root, Map<String, IAlleleSpecies> values) {
        if (species == null || species.getRoot() != root)
            throw fault("Species belongs to another or missing root");
        String uid = uid(species);
        IAlleleSpecies prior = values.putIfAbsent(uid, species);
        if (prior != null && !Objects.equals(prior.getName(), species.getName()))
            throw fault("Native species UID has conflicting names");
        if (values.size() > 65536) throw fault("Native species budget exceeded");
        return uid;
    }

    private static JsonArray template(IAllele[] values) {
        if (values == null || values.length == 0 || values.length > 256 || values[0] == null)
            throw fault("Native genotype template is unavailable or oversized");
        JsonArray result = array();
        for (IAllele allele : values) result.add(value(allele == null ? null : uid(allele)));
        return result;
    }

    private static JsonObject member(
            IAlleleSpecies species, String role, Map<String, ItemStack> specimens) {
        Class<?> position = type(UTILS + "$RecipePosition");
        Object choice = Enum.valueOf((Class) position, role);
        ItemStack value =
                (ItemStack)
                        invoke(
                                type(UTILS),
                                null,
                                "stackFromSpecies",
                                new Class<?>[] {IAlleleSpecies.class, position},
                                species,
                                choice);
        int nativeType = 0;
        ISpeciesRoot root = species.getRoot();
        if (root instanceof IBeeRoot || root instanceof ITreeRoot) {
            Object raw =
                    field(
                            type(UTILS),
                            null,
                            root instanceof IBeeRoot ? "beePositionToType" : "treePositionToType");
            if (!(raw instanceof Map) || !(((Map<?, ?>) raw).get(choice) instanceof Integer))
                throw fault("Native member-role map is unavailable");
            nativeType = (Integer) ((Map<?, ?>) raw).get(choice);
        }
        if (nativeType < 0 || nativeType > 255)
            throw fault("Native member type exceeds its domain");
        String id = item(value, specimens);
        return object(
                "role",
                role.toLowerCase(Locale.ROOT),
                "nativeType",
                nativeType,
                "item",
                id,
                "amount",
                Integer.toString(value.stackSize));
    }

    private static JsonObject memberTypes(ISpeciesRoot root, boolean produce) {
        JsonObject result = object();
        Class<?> position = type(UTILS + "$RecipePosition");
        for (String role :
                produce
                        ? new String[] {"Producer"}
                        : new String[] {"Parent1", "Parent2", "Offspring"}) {
            Object choice = Enum.valueOf((Class) position, role);
            int nativeType = 0;
            if (root instanceof IBeeRoot || root instanceof ITreeRoot) {
                Object raw =
                        field(
                                type(UTILS),
                                null,
                                root instanceof IBeeRoot
                                        ? "beePositionToType"
                                        : "treePositionToType");
                if (!(raw instanceof Map) || !(((Map<?, ?>) raw).get(choice) instanceof Integer))
                    throw fault("Native member-role map is unavailable");
                nativeType = (Integer) ((Map<?, ?>) raw).get(choice);
            }
            if (nativeType < 0 || nativeType > 255)
                throw fault("Native member type exceeds its domain");
            result.addProperty(role.toLowerCase(Locale.ROOT), nativeType);
        }
        return result;
    }

    private static void append(JsonArray rows, JsonObject value, long[] bytes) {
        int size = CanonicalJson.bytes(value).length;
        if (rows.size() >= 262144 || size > 1024 * 1024 || (bytes[0] += size) > 64L * 1024 * 1024)
            throw fault("Native NEIAddons snapshot exceeds its record/byte budget");
        rows.add(value);
    }

    private static void add(
            List<Output> out,
            Map<ItemStack, Float> values,
            String role,
            Map<String, ItemStack> specimens) {
        if (values == null || values.size() > 65536)
            throw fault("Native species " + role + " map is unavailable or oversized");
        for (Map.Entry<ItemStack, Float> entry : values.entrySet()) {
            if (entry.getValue() == null) throw fault("Native species rate is null");
            if (out.size() >= 65536) throw fault("Native output list exceeds its budget");
            out.add(
                    new Output(
                            role,
                            item(entry.getKey(), specimens),
                            entry.getKey().stackSize,
                            scalar(entry.getValue())));
        }
    }

    private static void add(
            List<Output> out, ItemStack[] values, String role, Map<String, ItemStack> specimens) {
        if (values == null || values.length > 65536)
            throw fault("Native tree " + role + " list is unavailable or oversized");
        for (ItemStack value : values) {
            if (out.size() >= 65536) throw fault("Native output list exceeds its budget");
            out.add(new Output(role, item(value, specimens), value.stackSize, scalar(1.0f)));
        }
    }

    private static String item(ItemStack value, Map<String, ItemStack> specimens) {
        if (value == null || value.getItem() == null || value.stackSize <= 0)
            throw fault("Native species member/output stack is invalid");
        String registry = Item.itemRegistry.getNameForObject(value.getItem());
        if (registry == null || Item.itemRegistry.getObject(registry) != value.getItem())
            throw fault("Native species item is unregistered");
        JsonElement nbt = TypedNbt.encode(value.getTagCompound());
        if (CanonicalJson.bytes(nbt).length > 1024 * 1024)
            throw fault("Native item NBT exceeds its byte budget");
        String id = Identity.item(registry, net.minecraft.init.Items.feather.getDamage(value), nbt);
        if (specimens.size() >= 262144 && !specimens.containsKey(id))
            throw fault("Native item specimen budget exceeded");
        specimens.putIfAbsent(id, value.copy());
        return id;
    }

    private static JsonObject scalar(float value) {
        if (!Float.isFinite(value) || value < 0)
            throw fault("Native species rate is negative or non-finite");
        return object(
                "bits",
                String.format(Locale.ROOT, "%08x", Float.floatToRawIntBits(value)),
                "decimal",
                Float.toString(value));
    }

    private static String uid(IAllele allele) {
        String uid = allele.getUID();
        if (uid == null
                || uid.isEmpty()
                || !java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(uid)
                || uid.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 256)
            throw fault("Native allele UID is unavailable or oversized");
        return uid;
    }

    private static String text(String text) {
        if (text == null
                || text.isEmpty()
                || !java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(text)
                || text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536)
            throw fault("Native description/name is unavailable or oversized");
        return text;
    }

    private static boolean showSecret() {
        return (Boolean) field(type(PREFIX + "forestry.AddonForestry"), null, "showSecret");
    }

    private static JsonObject copy(JsonObject value) {
        return new JsonParser().parse(value.toString()).getAsJsonObject();
    }

    private static Jobs.Fault fault(String message) {
        return new Jobs.Fault("reference_unsupported", message);
    }

    private static Jobs.Fault changed(String message) {
        return new Jobs.Fault("environment_changed", message);
    }

    private static final class Output {
        final String role, id;
        final int amount;
        final JsonObject rate;

        Output(String role, String id, int amount, JsonObject rate) {
            this.role = role;
            this.id = id;
            this.amount = amount;
            this.rate = rate;
        }
    }
}
