package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.*;
import forestry.api.core.EnumHumidity;
import forestry.api.core.EnumTemperature;
import forestry.api.genetics.*;
import forestry.api.lepidopterology.ButterflyManager;
import forestry.api.lepidopterology.IButterflyRoot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Real pinned roots produce the member stacks and NBT; providers supply an isolated registry. */
public final class NativeGeneticsTest {
    private static final Map<String, IAllele[]> defaults = new HashMap<>();
    public static IAllele[] nativeDefault(String root) { return defaults.get(root).clone(); }
    static void run() throws Exception {
        Constructor<Forestry> constructor;
        try { constructor = Forestry.class.getDeclaredConstructor(ISpeciesRoot.class, String.class); }
        catch (NoSuchMethodException missing) { throw new AssertionError("Butterfly and flower roots have no species/mutation capture", missing); }
        constructor.setAccessible(true);
        Map<String, ISpeciesRoot> roots = new HashMap<>();
        Map<String, IAllele> alleles = new HashMap<>();
        AlleleManager.alleleRegistry = proxy(IAlleleRegistry.class, (method, args) -> {
            switch (method.getName()) {
                case "getSpeciesRoot": return roots.get(args[0]);
                case "getAllele": return alleles.get(args[0]);
                case "isBlacklisted": return false;
                default: throw new AssertionError("Unexpected allele registry operation " + method);
            }
        });
        for (String kind : Arrays.asList("butterfly", "flower")) {
            String rootClass = kind.equals("butterfly") ? "forestry.lepidopterology.genetics.ButterflyHelper" : "binnie.botany.genetics.FlowerHelper";
            ISpeciesRoot root = (ISpeciesRoot) Class.forName(rootClass).newInstance();
            roots.put(root.getUID(), root);
            if (kind.equals("butterfly")) ButterflyManager.butterflyRoot = (IButterflyRoot) root;
            else Class.forName("binnie.botany.core.BotanyCore").getField("speciesRoot").set(null, root);
            installItems(kind);
            IChromosomeType[] chromosomes = root.getKaryotype();
            IAllele[] template = new IAllele[chromosomes.length];
            for (IChromosomeType chromosome : chromosomes) {
                String uid = "fixture." + kind + "." + chromosome.getName();
                template[chromosome.ordinal()] = allele(chromosome.getAlleleClass(), uid, root, 7);
                alleles.put(uid, template[chromosome.ordinal()]);
            }
            IAllele[] original = template.clone();
            defaults.put(rootClass, template.clone());
            root.getGenomeTemplates().put(template[0].getUID(), template);
            IAllele[] result = template.clone();
            int modified = kind.equals("butterfly") ? 3 : 1;
            result[modified] = allele(chromosomes[modified].getAlleleClass(), "fixture.result." + kind, root, 11);
            Class<?> mutationClass = Class.forName(kind.equals("butterfly") ? "forestry.api.lepidopterology.IButterflyMutation" : "binnie.botany.api.IFlowerMutation");
            IMutation mutation = (IMutation) proxy(mutationClass, (method, args) -> {
                switch (method.getName()) {
                    case "getRoot": return root;
                    case "getTemplate": return result;
                    case "getAllele0": case "getAllele1": return template[0];
                    case "getBaseChance": return 7.5f;
                    case "getSpecialConditions": return Collections.singletonList("Native " + kind + " condition");
                    case "isSecret": return true;
                    default: throw new AssertionError("Unexpected mutation operation " + method);
                }
            });
            root.getMutations(false).clear();
            root.registerMutation(mutation); root.registerMutation(mutation);
            Facts facts = new Facts("en_US");
            List<String> memberIds = new ArrayList<>();
            for (int ordinal = 0; ordinal < 3; ordinal++) {
                ItemStack stack = root.getMemberStack(root.templateAsIndividual(template.clone()), ordinal);
                String id = Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), stack.getItemDamage(), TypedNbt.encode(stack.getTagCompound()));
                facts.restore("items", object("id", id)); memberIds.add(id);
            }
            Forestry capture = constructor.newInstance(root, kind);
            require(capture.speciesCount() == 1 && capture.mutationCount() == 2, "Root registrations were not retained");
            capture.captureSpecies(0, facts);
            capture.captureMutation(0, facts); capture.captureMutation(1, facts);
            Facts.Batch batch = facts.drain();
            JsonObject species = null; List<JsonObject> mutations = new ArrayList<>();
            for (Facts.Record row : batch.records) {
                if (row.kind.equals("species")) species = row.value;
                if (row.kind.equals("mutations")) mutations.add(row.value);
            }
            require(species != null && species.get("kind").getAsString().equals(kind), "Wrong species kind");
            require(species.getAsJsonObject("source").get("owner").getAsString().equals(kind.equals("flower") ? "Botany" : "Forestry"), "Wrong root owner");
            require(species.getAsJsonArray("products").size() == 0 && species.getAsJsonArray("specialties").size() == 0, "Breeding became bee production");
            require(species.getAsJsonArray("members").size() == 3, "Native member forms were omitted");
            Set<String> actual = new HashSet<>();
            for (JsonElement member : species.getAsJsonArray("members")) actual.add(member.getAsJsonObject().get("item").getAsString());
            require(actual.equals(new HashSet<>(memberIds)), "Native member NBT or type ordinal changed");
            require(mutations.size() == 2 && !mutations.get(0).get("id").equals(mutations.get(1).get("id")), "Duplicate native attempts were erased");
            require(mutations.get(0).getAsJsonObject("chance").equals(object("numerator", "3", "denominator", "40")), "Native base chance changed");
            require(mutations.get(1).get("occurrence").getAsInt() == 1 && mutations.get(0).get("secret").getAsBoolean(), "Mutation metadata changed");
            require(mutations.get(0).getAsJsonArray("conditions").size() == 1, "Native special condition was lost");
            String changedGene = chromosomes[modified].getName();
            JsonObject defaultGene = gene(species.getAsJsonArray("genes"), changedGene);
            JsonObject resultGene = gene(mutations.get(0).getAsJsonArray("genes"), changedGene);
            require(defaultGene.get("allele").getAsString().equals(template[modified].getUID())
                    && resultGene.get("allele").getAsString().equals(result[modified].getUID()), "Mutation result genome was replaced by species defaults");
            require(!defaultGene.get("value").equals(resultGene.get("value")), "Native chromosome scalar values were lost");
            require(Arrays.equals(original, template), "Member or mutation capture mutated the shared default template");
            if (kind.equals("butterfly")) require(species.get("nocturnal").getAsBoolean() && species.get("flower").isJsonNull(), "Butterfly traits were replaced by tree traits");
            else require(species.getAsJsonObject("flower").equals(object("acidity", "neutral", "moisture", "normal", "type", 12)), "Botany climate/type was not preserved");
        }
        System.out.println("Native genetics roots: member forms/NBT, chromosomes, Botany traits and duplicate mutation attempts passed");
    }

    private static IAllele allele(Class<?> type, String uid, ISpeciesRoot root, int number) {
        return (IAllele) proxy(type, (method, args) -> {
            switch (method.getName()) {
                case "getUID": return uid;
                case "getName": case "getDescription": case "getBinomial": case "getAuthority": return uid;
                case "getRoot": return root;
                case "getTemperature": return EnumTemperature.NORMAL;
                case "getHumidity": return EnumHumidity.NORMAL;
                case "getPH": return enumValue(method.getReturnType(), "NEUTRAL");
                case "getMoisture": return enumValue(method.getReturnType(), "NORMAL");
                case "getType": return proxy(method.getReturnType(), (nested, ignored) -> 12);
                case "isDominant": case "isCounted": case "isNocturnal": return true;
                case "isSecret": return false;
                case "getValue":
                    if (method.getReturnType() == float.class) return 0.3f;
                    if (method.getReturnType() == int.class) return number;
                    if (method.getReturnType() == boolean.class) return true;
                    if (method.getReturnType() == int[].class) return new int[]{3, 4, 5};
                    return EnumTolerance.NONE;
                default: throw new AssertionError("Unexpected allele operation " + method);
            }
        });
    }

    private static void installItems(String kind) throws Exception {
        if (kind.equals("butterfly")) {
            Class<?> registryType = Class.forName("forestry.lepidopterology.items.ItemRegistryLepidopterology");
            Object registry = allocate(registryType);
            Class.forName("forestry.plugins.PluginLepidopterology").getField("items").set(null, registry);
            for (String name : Arrays.asList("butterflyGE", "serumGE", "caterpillarGE")) {
                Field field = registryType.getField(name); field.setAccessible(true);
                Class<?> form = Class.forName("forestry.api.lepidopterology.EnumFlutterType");
                String symbol = name.equals("butterflyGE") ? "BUTTERFLY" : name.equals("serumGE") ? "SERUM" : "CATERPILLAR";
                Item item = (Item) field.getType().getConstructor(form).newInstance(enumValue(form, symbol));
                Item.itemRegistry.addObject(5000 + Arrays.asList("butterflyGE", "serumGE", "caterpillarGE").indexOf(name), "fixture:" + name, item);
                field.set(registry, item);
            }
        } else {
            Class<?> botany = Class.forName("binnie.botany.Botany");
            for (String name : Arrays.asList("flowerItem", "seed", "pollen")) {
                Field field = botany.getField(name); Item item = (Item) field.getType().newInstance();
                Item.itemRegistry.addObject(5010 + Arrays.asList("flowerItem", "seed", "pollen").indexOf(name), "fixture:" + name, item);
                field.set(null, item);
            }
        }
    }
    private interface Call { Object call(Method method, Object[] args) throws Exception; }
    private static JsonObject gene(JsonArray genes, String key) {
        for (JsonElement row : genes) if (row.getAsJsonObject().get("key").getAsString().equals(key)) return row.getAsJsonObject();
        throw new AssertionError("Missing native chromosome " + key);
    }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, Call call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("toString")) return type.getName();
            return call.call(method, args);
        });
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) private static Object enumValue(Class<?> type, String value) { return Enum.valueOf((Class) type, value); }
    private static Object allocate(Class<?> type) throws Exception { Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); return ((sun.misc.Unsafe) field.get(null)).allocateInstance(type); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    /** Substitute only bootstrap default registries; native genomes, members, NBT and mutations are untouched. */
    public static final class Defaults implements net.minecraft.launchwrapper.IClassTransformer {
        @Override public byte[] transform(String name, String transformed, byte[] bytes) {
            if (!name.equals("forestry.lepidopterology.genetics.ButterflyHelper") && !name.equals("binnie.botany.genetics.FlowerHelper")) return bytes;
            org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(bytes).accept(node, 0);
            for (Object raw : node.methods) {
                org.objectweb.asm.tree.MethodNode method = (org.objectweb.asm.tree.MethodNode) raw;
                if (!method.name.equals("getDefaultTemplate")) continue;
                method.instructions.clear(); method.tryCatchBlocks.clear();
                if (method.localVariables != null) method.localVariables.clear();
                method.instructions.add(new org.objectweb.asm.tree.LdcInsnNode(name));
                method.instructions.add(new org.objectweb.asm.tree.MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                        NativeGeneticsTest.class.getName().replace('.', '/'), "nativeDefault", "(Ljava/lang/String;)[Lforestry/api/genetics/IAllele;", false));
                method.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.ARETURN));
            }
            org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
            node.accept(writer); return writer.toByteArray();
        }
    }
}
