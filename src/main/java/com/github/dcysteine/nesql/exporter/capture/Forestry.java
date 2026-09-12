package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Chance;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import forestry.api.apiculture.BeeManager;
import forestry.api.apiculture.EnumBeeType;
import forestry.api.apiculture.IAlleleBeeSpecies;
import forestry.api.arboriculture.EnumGermlingType;
import forestry.api.arboriculture.ITree;
import forestry.api.arboriculture.TreeManager;
import forestry.api.genetics.AlleleManager;
import forestry.api.genetics.IAllele;
import forestry.api.genetics.IAlleleArea;
import forestry.api.genetics.IAlleleBoolean;
import forestry.api.genetics.IAlleleFloat;
import forestry.api.genetics.IAlleleInteger;
import forestry.api.genetics.IAllelePlantType;
import forestry.api.genetics.IAlleleSpecies;
import forestry.api.genetics.IAlleleTolerance;
import forestry.api.genetics.IChromosomeType;
import forestry.api.genetics.IMutation;
import forestry.api.genetics.ISpeciesRoot;
import net.minecraft.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Read-only registered templates and mutations for the target Forestry API. */
final class Forestry {
    private final ISpeciesRoot root;
    private final boolean bees;
    private final IChromosomeType key;
    private final IChromosomeType[] chromosomes;
    private final Map<String, IAllele[]> templates = new TreeMap<>();
    private final List<IAllele[]> species;
    private final List<? extends IMutation> mutations;
    private final Map<String, Integer> occurrences = new java.util.HashMap<>();

    static List<Forestry> all() {
        ModContainer mod = Loader.instance().getIndexedModList().get("Forestry");
        if (mod == null || !"4.10.17".equals(mod.getVersion()) || AlleleManager.alleleRegistry == null) {
            throw new Jobs.Fault("domain_unsupported", "Genetics requires the target Forestry 4.10.17");
        }
        return Arrays.asList(new Forestry(BeeManager.beeRoot, true), new Forestry(TreeManager.treeRoot, false));
    }

    private Forestry(ISpeciesRoot root, boolean bees) {
        if (root == null) throw new Jobs.Fault("species_unavailable", "The target bee or tree registry is unavailable");
        this.root = root;
        this.bees = bees;
        key = root.getKaryotypeKey();
        chromosomes = root.getKaryotype().clone();
        if (key == null || key.getSpeciesRoot() != root || chromosomes.length == 0 || chromosomes.length > 256) {
            throw new Jobs.Fault("species_chromosomes", "Invalid karyotype for " + root.getUID());
        }
        Map<String, IAllele[]> registered = root.getGenomeTemplates();
        if (registered.isEmpty() || registered.size() > 65536) throw new Jobs.Fault("species_limit", "Invalid registered template count");
        for (IAllele[] genome : registered.values()) {
            Jobs.checkpoint();
            IAlleleSpecies allele = species(genome);
            IAllele[] previous = templates.putIfAbsent(allele.getUID(), genome.clone());
            if (previous != null && !Arrays.equals(previous, genome)) {
                throw new Jobs.Fault("species_conflict", "Different default templates share species " + allele.getUID());
            }
        }
        species = new ArrayList<>(templates.values());
        // false means do not shuffle the game's shared mutation list; it does not filter secrets.
        Collection<? extends IMutation> registeredMutations = root.getMutations(false);
        if (registeredMutations.size() > 262144) throw new Jobs.Fault("mutation_limit", "Mutation registry exceeds its budget");
        mutations = new ArrayList<>(registeredMutations);
    }

    String name() { return root.getUID(); }
    int speciesCount() { return species.size(); }
    int mutationCount() { return mutations.size(); }

    void captureSpecies(int index, Facts facts) {
        Jobs.checkpoint();
        IAllele[] template = species.get(index);
        IAlleleSpecies allele = species(template);
        JsonArray members = new JsonArray();
        JsonArray products, specialties;
        Boolean nocturnal = null, fruitCompatible = null;
        if (bees) {
            IAlleleBeeSpecies bee = (IAlleleBeeSpecies) allele;
            for (EnumBeeType form : new EnumBeeType[] {EnumBeeType.QUEEN, EnumBeeType.PRINCESS, EnumBeeType.DRONE, EnumBeeType.LARVAE}) {
                members.add(member(facts, template, form.getName(), form.ordinal()));
            }
            products = products(bee.getProductChances(), facts);
            specialties = products(bee.getSpecialtyChances(), facts);
            nocturnal = bee.isNocturnal();
        } else {
            for (EnumGermlingType form : new EnumGermlingType[] {EnumGermlingType.SAPLING, EnumGermlingType.POLLEN}) {
                members.add(member(facts, template, form.getName(), form.ordinal()));
            }
            ITree tree = (ITree) root.templateAsIndividual(template.clone());
            products = products(tree.getProduceList(), facts);
            specialties = products(tree.getSpecialtyList(), facts);
            fruitCompatible = tree.canBearFruit();
        }
        JsonObject source = origin(allele);
        facts.row("species", object("id", Identity.origin("species", source), "source", source, "kind", bees ? "bee" : "tree",
                "name", facts.text(allele.getName()), "description", facts.text(allele.getDescription()),
                "binomial", allele.getBinomial(), "authority", allele.getAuthority(),
                "temperature", allele.getTemperature().name().toLowerCase(Locale.ROOT),
                "humidity", allele.getHumidity().name().toLowerCase(Locale.ROOT),
                "dominant", allele.isDominant(), "secret", allele.isSecret(), "counted", allele.isCounted(),
                "blacklisted", AlleleManager.alleleRegistry.isBlacklisted(allele.getUID()),
                "nocturnal", nocturnal, "fruitCompatible", fruitCompatible,
                "members", members, "genes", genes(template, facts), "products", products, "specialties", specialties));
    }

    void captureMutation(int index, Facts facts) {
        Jobs.checkpoint();
        IMutation mutation = mutations.get(index);
        if (mutation == null || mutation.getRoot() != root) throw new Jobs.Fault("mutation_root", "Mutation belongs to another species root");
        IAllele[] template = mutation.getTemplate();
        String first = reference(mutation.getAllele0()), second = reference(mutation.getAllele1());
        JsonArray parents = new JsonArray();
        parents.add(value(first.compareTo(second) <= 0 ? first : second));
        parents.add(value(first.compareTo(second) <= 0 ? second : first));
        Collection<String> descriptions = mutation.getSpecialConditions();
        if (descriptions == null || descriptions.size() > 256) throw new Jobs.Fault("mutation_conditions", "Invalid mutation conditions");
        List<String> labels = new ArrayList<>();
        for (String description : descriptions) labels.add(facts.text(description));
        labels.sort(String::compareTo);
        JsonArray conditions = new JsonArray();
        labels.forEach(label -> conditions.add(value(label)));
        JsonObject record = object("handler", mutation.getClass().getName(), "parents", parents, "result", reference(species(template)),
                "chance", Chance.decimal(mutation.getBaseChance(), 100), "conditions", conditions,
                "secret", mutation.isSecret(), "genes", genes(template, facts));
        // Identical registrations are separate attempts in Forestry. Do not let row dedup erase them.
        String signature = Identity.content("mutation", record);
        record.addProperty("occurrence", occurrences.merge(signature, 1, Math::addExact) - 1);
        // Forestry has no mutation registry key; preserve every fact, including the result genome.
        record.addProperty("id", Identity.content("mutation", record));
        facts.row("mutations", record);
    }

    private JsonObject member(Facts facts, IAllele[] template, String form, int ordinal) {
        // getMemberStack(QUEEN) mates its argument. Never give it shared registry individuals.
        ItemStack stack = root.getMemberStack(root.templateAsIndividual(template.clone()), ordinal);
        return object("form", form, "item", facts.item(stack));
    }

    private JsonArray genes(IAllele[] template, Facts facts) {
        species(template);
        Map<String, JsonObject> genes = new TreeMap<>();
        for (IChromosomeType chromosome : chromosomes) {
            if (chromosome == null || chromosome.getSpeciesRoot() != root || chromosome.ordinal() < 0 || chromosome.ordinal() >= template.length) {
                throw new Jobs.Fault("species_chromosomes", "Template does not match its karyotype");
            }
            if (chromosome.ordinal() == key.ordinal()) continue;
            IAllele allele = template[chromosome.ordinal()];
            // Forestry's retired HUMIDITY chromosome is explicitly empty in default bee genomes.
            if (allele == null) continue;
            if (!chromosome.getAlleleClass().isInstance(allele)) throw new Jobs.Fault("allele_type", "Allele does not match " + chromosome.getName());
            JsonObject gene = object("key", chromosome.getName(), "allele", allele.getUID(), "name", facts.text(allele.getName()),
                    "dominant", allele.isDominant(), "value", alleleValue(allele, facts));
            if (genes.put(chromosome.getName(), gene) != null) throw new Jobs.Fault("allele_conflict", "Duplicate chromosome key");
        }
        JsonArray result = new JsonArray();
        genes.values().forEach(result::add);
        return result;
    }

    private static JsonObject alleleValue(IAllele allele, Facts facts) {
        if (allele instanceof IAlleleFloat) return Values.capture(((IAlleleFloat) allele).getValue(), facts);
        if (allele instanceof IAlleleInteger) return Values.capture(((IAlleleInteger) allele).getValue(), facts);
        if (allele instanceof IAlleleBoolean) return Values.capture(((IAlleleBoolean) allele).getValue(), facts);
        if (allele instanceof IAlleleTolerance) return Values.capture(((IAlleleTolerance) allele).getValue(), facts);
        if (allele instanceof IAlleleArea) return Values.capture(((IAlleleArea) allele).getValue(), facts);
        if (allele instanceof IAllelePlantType) return Values.capture(((IAllelePlantType) allele).getPlantTypes(), facts);
        return null; // Flower, growth and effect providers expose behavior, not an API value.
    }

    private IAlleleSpecies species(IAllele[] template) {
        if (template == null || template.length > 256 || key.ordinal() < 0 || key.ordinal() >= template.length
                || !(template[key.ordinal()] instanceof IAlleleSpecies)) {
            throw new Jobs.Fault("species_template", "Template has no species chromosome");
        }
        IAlleleSpecies allele = (IAlleleSpecies) template[key.ordinal()];
        if (allele.getRoot() != root || !key.getAlleleClass().isInstance(allele)) throw new Jobs.Fault("species_root", "Species belongs to another root");
        return allele;
    }

    private String reference(IAlleleSpecies allele) {
        if (allele == null || allele.getRoot() != root || !templates.containsKey(allele.getUID())) {
            throw new Jobs.Fault("species_reference", "Mutation references a species without a registered default template");
        }
        return Identity.origin("species", origin(allele));
    }

    private JsonObject origin(IAlleleSpecies allele) { return object("owner", "Forestry", "handler", root.getUID(), "key", allele.getUID()); }

    private static JsonArray products(Map<ItemStack, Float> products, Facts facts) {
        if (products == null || products.size() > 4096) throw new Jobs.Fault("species_products", "Invalid bee product map");
        List<JsonObject> result = new ArrayList<>();
        for (Map.Entry<ItemStack, Float> product : products.entrySet()) {
            if (product.getValue() == null) throw new Jobs.Fault("species_chance", "Bee product has no rate");
            result.add(product(product.getKey(), Chance.decimal(product.getValue(), 1), facts));
        }
        return sorted(result);
    }

    private static JsonArray products(ItemStack[] products, Facts facts) {
        if (products == null || products.length > 4096) throw new Jobs.Fault("species_products", "Invalid tree product list");
        List<JsonObject> result = new ArrayList<>();
        for (ItemStack product : products) result.add(product(product, null, facts));
        return sorted(result);
    }

    private static JsonObject product(ItemStack stack, JsonObject chance, Facts facts) {
        if (stack == null || stack.stackSize <= 0) throw new Jobs.Fault("species_product", "Species product has no positive item count");
        return object("item", facts.item(stack), "amount", Integer.toString(stack.stackSize), "chance", chance);
    }

    private static JsonArray sorted(List<JsonObject> rows) {
        Map<JsonObject, String> keys = new java.util.IdentityHashMap<>();
        for (JsonObject row : rows) keys.put(row, new String(CanonicalJson.bytes(row), StandardCharsets.UTF_8));
        rows.sort(java.util.Comparator.comparing(keys::get, CanonicalJson.KEY_ORDER));
        JsonArray result = new JsonArray();
        rows.forEach(result::add);
        return result;
    }
}
