package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.*;
import forestry.api.apiculture.*;
import forestry.api.arboriculture.*;
import forestry.api.genetics.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Installed NEIAddons specimen/merge callbacks, with explicit controlled Forestry API providers.
 */
final class ForestryAddonsTest {
    static void run() throws Exception {
        Class<?> nativeUtils = Class.forName("net.bdew.neiaddons.forestry.GeneticsUtils");
        require(
                nativeUtils
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toString()
                        .contains("/native-tests/neiaddons.jar!"),
                "Installed NEIAddons test jar required");
        Class<?> adapter =
                Class.forName(
                        "com.github.dcysteine.nesql.exporter.capture.ForestryAddonsReferences");
        Method snapshot =
                adapter.getDeclaredMethod(
                        "snapshot",
                        ISpeciesRoot.class,
                        JsonObject.class,
                        Collection.class,
                        boolean.class,
                        Map.class);
        snapshot.setAccessible(true);
        JsonArray domains = array(), facts = array();
        Set<String> itemIds = new HashSet<>();
        for (String handler :
                new String[] {
                    "forestry.bees.BeeProduceHandler",
                    "forestry.trees.TreeProduceHandler",
                    "forestry.bees.BeeBreedingHandler",
                    "forestry.trees.TreeBreedingHandler",
                    "forestry.butterflies.ButterflyBreedingHandler",
                    "botany.flowers.FlowerBreedingHandler"
                }) {
            Fixture fixture = new Fixture(handler);
            JsonObject source =
                    object(
                            "owner",
                            "NEIAddons",
                            "handler",
                            "net.bdew.neiaddons." + handler,
                            "key",
                            handler);
            Map<String, ItemStack> specimens = new TreeMap<>();
            JsonArray rows =
                    (JsonArray)
                            snapshot.invoke(
                                    null, fixture.root, source, fixture.species, false, specimens);
            JsonObject definition = data(rows, "forestryAddonsDefinition").get(0).getAsJsonObject();
            require(
                    !definition.get("consuming").getAsBoolean()
                            && definition.get("ready").getAsBoolean(),
                    "Reference became a consuming recipe or unready success");
            List<JsonElement> outputs = data(rows, "forestryAddonsOutput"),
                    mutations = data(rows, "forestryAddonsMutation");
            if (handler.contains("Produce")) {
                require(
                        outputs.size() == 6,
                        "Lost raw product/specialty entries or empty-output species");
                require(
                        outputs.stream()
                                        .filter(
                                                row ->
                                                        row.getAsJsonObject()
                                                                .get("role")
                                                                .getAsString()
                                                                .equals("specialty"))
                                        .count()
                                == 2,
                        "Native specialty was omitted");
                if (handler.contains("bees")) {
                    JsonObject low =
                            outputs.stream()
                                    .map(JsonElement::getAsJsonObject)
                                    .filter(
                                            row ->
                                                    row.getAsJsonObject("rate")
                                                            .get("decimal")
                                                            .getAsString()
                                                            .equals("1.5E-4"))
                                    .findFirst()
                                    .get();
                    require(
                            low.getAsJsonObject("rate")
                                    .get("bits")
                                    .getAsString()
                                    .equals(
                                            String.format(
                                                    Locale.ROOT,
                                                    "%08x",
                                                    Float.floatToRawIntBits(0.00015f))),
                            "Fractional rate rounded to basis points");
                    require(
                            data(rows, "forestryAddonsSpecies")
                                    .get(0)
                                    .getAsJsonObject()
                                    .get("jubilance")
                                    .getAsString()
                                    .equals("§eNative fixture jubilance"),
                            "Specialty condition description missing");
                }
                Set<Integer> amounts = new HashSet<>();
                for (JsonElement value : outputs)
                    if (value.getAsJsonObject().get("role").getAsString().equals("product"))
                        amounts.add(value.getAsJsonObject().get("amount").getAsInt());
                require(
                        amounts.contains(2) && amounts.contains(5),
                        "GUI merge quantities were substituted for native species quantities");
            } else {
                require(
                        mutations.size() == 3,
                        "Duplicate registration or hidden mutation disappeared");
                require(
                        !mutations.get(1).getAsJsonObject().get("eligible").getAsBoolean(),
                        "Secret native mutation became visible");
                require(
                        mutations.get(0).getAsJsonObject().getAsJsonArray("template").size() == 2,
                        "Offspring genotype omitted");
                require(
                        mutations
                                .get(0)
                                .getAsJsonObject()
                                .getAsJsonObject("base")
                                .get("decimal")
                                .getAsString()
                                .equals("0.015"),
                        "Fractional mutation chance rounded");
                JsonArray visible =
                        (JsonArray)
                                snapshot.invoke(
                                        null,
                                        fixture.root,
                                        source,
                                        fixture.species,
                                        true,
                                        new TreeMap<>());
                require(
                        !CanonicalJson.digest(rows).equals(CanonicalJson.digest(visible)),
                        "Visibility omitted from drift proof");
            }
            for (JsonElement value : data(rows, "forestryAddonsSpecies")) {
                JsonObject species = value.getAsJsonObject();
                for (JsonElement form : species.getAsJsonArray("specimens")) {
                    ItemStack stack =
                            specimens.get(form.getAsJsonObject().get("item").getAsString());
                    require(
                            stack != null && stack.getTagCompound().getBoolean("isAnalyzed"),
                            "Native analyzed specimen policy lost");
                }
            }
            String before = CanonicalJson.digest(rows);
            fixture.quantity[0]++;
            require(
                    !before.equals(
                            CanonicalJson.digest(
                                    (JsonArray)
                                            snapshot.invoke(
                                                    null,
                                                    fixture.root,
                                                    source,
                                                    fixture.species,
                                                    false,
                                                    new TreeMap<>()))),
                    "Item/quantity/genotype omitted from source proof");
            fixture.quantity[0]--;
            fixture.fail[0] = true;
            try {
                snapshot.invoke(
                        null, fixture.root, source, fixture.species, false, new TreeMap<>());
                throw new AssertionError("Native callback failure was swallowed");
            } catch (InvocationTargetException expected) {
                require(
                        expected.getCause() instanceof IllegalStateException,
                        "Unexpected callback failure");
            }
            fixture.fail[0] = false;
            for (Map.Entry<String, ItemStack> entry : specimens.entrySet())
                if (itemIds.add(entry.getKey())) {
                    ItemStack stack = entry.getValue();
                    facts.add(
                            object(
                                    "id",
                                    entry.getKey(),
                                    "registry",
                                    net.minecraft.item.Item.itemRegistry.getNameForObject(
                                            stack.getItem()),
                                    "meta",
                                    stack.getItemDamage(),
                                    "nbt",
                                    TypedNbt.encode(stack.getTagCompound()),
                                    "tags",
                                    array()));
                }
            domains.add(rows);
            fixture.mutations.clear();
            fixture.species.clear();
            JsonArray empty =
                    (JsonArray)
                            snapshot.invoke(
                                    null,
                                    fixture.root,
                                    source,
                                    fixture.species,
                                    false,
                                    new TreeMap<>());
            require(empty.size() == 1, "Initialized empty registry misclassified");
        }
        Files.write(
                Paths.get("build/native-tests/forestry-addons-contract.json"),
                CanonicalJson.bytes(object("domains", domains, "items", facts)));
        System.out.println(
                "NEIAddons: six native domains, original quantities/rates, specialty/jubilance, all parents/genotypes, secrets/duplicates, drift and callback errors passed");
    }

    private static List<JsonElement> data(JsonArray rows, String kind) {
        List<JsonElement> values = new ArrayList<>();
        for (JsonElement row : rows) {
            JsonObject value = row.getAsJsonObject().getAsJsonObject("data");
            if (value.get("kind").getAsString().equals(kind)) values.add(value);
        }
        return values;
    }

    private static final class Fixture {
        final ISpeciesRoot root;
        final List<IAlleleSpecies> species = new ArrayList<>();
        final List<IMutation> mutations = new ArrayList<>();
        final Map<String, IAllele[]> templates = new HashMap<>();
        final int[] quantity = {2};
        final boolean[] fail = {false};

        Fixture(String handler) {
            boolean bee = handler.contains("bees"), tree = handler.contains("trees");
            String uid =
                    bee
                            ? "rootBees"
                            : tree
                                    ? "rootTrees"
                                    : handler.contains("butterflies")
                                            ? "rootButterflies"
                                            : "rootFlowers";
            root =
                    (ISpeciesRoot)
                            proxy(
                                    bee
                                            ? IBeeRoot.class
                                            : tree ? ITreeRoot.class : ISpeciesRoot.class,
                                    (method, args) -> {
                                        switch (method.getName()) {
                                            case "getUID":
                                                return uid;
                                            case "getMutations":
                                                require(
                                                        !(Boolean) args[0],
                                                        "Native mutation order shuffled");
                                                return mutations;
                                            case "getTemplate":
                                                return templates.get(args[0]);
                                            case "templateAsIndividual":
                                                IAllele[] genome = (IAllele[]) args[0];
                                                boolean[] analyzed = {false};
                                                return proxy(
                                                        tree ? ITree.class : IIndividual.class,
                                                        (operation, values) -> {
                                                            switch (operation.getName()) {
                                                                case "analyze":
                                                                    analyzed[0] = true;
                                                                    return true;
                                                                case "getProduceList":
                                                                    if (fail[0])
                                                                        throw new IllegalStateException(
                                                                                "fixture production callback");
                                                                    return new ItemStack[] {
                                                                        new ItemStack(
                                                                                Items.dye,
                                                                                quantity[0],
                                                                                4),
                                                                        new ItemStack(
                                                                                Items.dye, 5, 4)
                                                                    };
                                                                case "getSpecialtyList":
                                                                    return new ItemStack[] {
                                                                        new ItemStack(
                                                                                Items.gold_ingot)
                                                                    };
                                                                case "writeToNBT":
                                                                    NBTTagCompound nbt =
                                                                            (NBTTagCompound)
                                                                                    values[0];
                                                                    nbt.setString(
                                                                            "fixtureSpecies",
                                                                            genome[0].getUID());
                                                                    nbt.setBoolean(
                                                                            "isAnalyzed",
                                                                            analyzed[0]);
                                                                    return nbt;
                                                                default:
                                                                    throw new AssertionError(
                                                                            "Unexpected individual callback "
                                                                                    + operation);
                                                            }
                                                        });
                                            case "getMemberStack":
                                                NBTTagCompound nbt = new NBTTagCompound();
                                                ((IIndividual) args[0]).writeToNBT(nbt);
                                                ItemStack stack =
                                                        new ItemStack(
                                                                Items.paper,
                                                                quantity[0],
                                                                (Integer) args[1]);
                                                stack.setTagCompound(nbt);
                                                return stack;
                                            default:
                                                throw new AssertionError(
                                                        "Unexpected root callback " + method);
                                        }
                                    });
            for (String key : new String[] {"fixture.a", "fixture.b"}) {
                IAlleleSpecies allele =
                        (IAlleleSpecies)
                                proxy(
                                        bee
                                                ? IAlleleBeeSpeciesCustom.class
                                                : tree
                                                        ? IAlleleTreeSpecies.class
                                                        : IAlleleSpecies.class,
                                        (method, args) -> {
                                            switch (method.getName()) {
                                                case "getUID":
                                                    return key;
                                                case "getName":
                                                    return "Native " + key;
                                                case "getRoot":
                                                    return root;
                                                case "getJubilanceProvider":
                                                    return proxy(
                                                            IJubilanceProvider.class,
                                                            (operation, values) -> {
                                                                if (operation
                                                                        .getName()
                                                                        .equals("getDescription"))
                                                                    return "§eNative fixture jubilance";
                                                                throw new AssertionError(operation);
                                                            });
                                                case "getProductChances":
                                                    if (fail[0])
                                                        throw new IllegalStateException(
                                                                "fixture production callback");
                                                    Map<ItemStack, Float> drops =
                                                            new LinkedHashMap<>();
                                                    drops.put(
                                                            new ItemStack(
                                                                    Items.dye, quantity[0], 4),
                                                            0.00015f);
                                                    drops.put(
                                                            new ItemStack(Items.dye, 5, 4),
                                                            0.00015f);
                                                    return drops;
                                                case "getSpecialtyChances":
                                                    return Collections.singletonMap(
                                                            new ItemStack(Items.gold_ingot), 0.25f);
                                                default:
                                                    throw new AssertionError(
                                                            "Unexpected species callback "
                                                                    + method);
                                            }
                                        });
                species.add(allele);
                templates.put(key, new IAllele[] {allele, null});
            }
            for (int i = 0; i < 3; i++) {
                final int ordinal = i;
                mutations.add(
                        (IMutation)
                                proxy(
                                        IMutation.class,
                                        (method, args) -> {
                                            switch (method.getName()) {
                                                case "getRoot":
                                                    return root;
                                                case "getAllele0":
                                                    return species.get(0);
                                                case "getAllele1":
                                                    return species.get(1);
                                                case "getTemplate":
                                                    return new IAllele[] {
                                                        species.get(1), species.get(0)
                                                    };
                                                case "getBaseChance":
                                                    return 0.015f;
                                                case "isSecret":
                                                    return ordinal == 1;
                                                case "getSpecialConditions":
                                                    if (fail[0])
                                                        throw new IllegalStateException(
                                                                "fixture mutation callback");
                                                    return Arrays.asList(
                                                            "§eNative fixture climate condition");
                                                default:
                                                    throw new AssertionError(
                                                            "Unexpected mutation callback "
                                                                    + method);
                                            }
                                        }));
            }
        }
    }

    private interface Callback {
        Object call(Method method, Object[] args);
    }

    private static Object proxy(Class<?> type, Callback callback) {
        return Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (target, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        if (method.getName().equals("equals")) return target == args[0];
                        if (method.getName().equals("hashCode"))
                            return System.identityHashCode(target);
                        if (method.getName().equals("toString")) return type.getName() + " fixture";
                    }
                    return callback.call(method, args);
                });
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
