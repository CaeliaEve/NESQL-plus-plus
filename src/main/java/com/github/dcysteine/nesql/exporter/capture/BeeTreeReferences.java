package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Chance;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import forestry.api.apiculture.IBeeRoot;
import forestry.api.apiculture.IBeeMutation;
import forestry.api.genetics.IAllele;
import forestry.api.genetics.IAlleleSpecies;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native mutation graph, not the truncated NEI tree's apparent consuming slots. */
final class BeeTreeReferences implements NativeReferences {
    static final String HANDLER = "hellfirepvp.beebetteratbees.client.gui.BBABGuiRecipeTreeHandler";
    private static final String CONFIG = "hellfirepvp.beebetteratbees.common.ModConfig";
    private final JsonObject source;
    private final IBeeRoot root;
    private final JsonArray rows;
    private final Map<String,ItemStack> specimens = new TreeMap<>();
    private final String domain, signature;
    static boolean supports(ICraftingHandler value) { return value != null && HANDLER.equals(value.getClass().getName()); }
    BeeTreeReferences(ICraftingHandler handler, JsonObject source) {
        if (!supports(handler)) throw fault("Unknown native bee tree handler");
        Object raw = field(type(HANDLER), null, "speciesRoot");
        if (!(raw instanceof IBeeRoot)) throw new Jobs.Fault("handler_not_ready", "BBAB bee root has not been initialized");
        this.source = copy(source); root = (IBeeRoot)raw;
        rows = current(specimens); domain = rows.get(0).getAsJsonObject().get("domain").getAsString();
        signature = CanonicalJson.digest(rows);
    }
    private JsonArray current(Map<String,ItemStack> items) {
        if (field(type(HANDLER), null, "speciesRoot") != root) throw new Jobs.Fault("environment_changed", "BBAB root changed");
        return snapshot(root, source, flag("shouldShowSecretRecipes"), flag("showDuplicateTrees"), items);
    }
    public int size() { return rows.size(); }
    public String domain() { return domain; }
    public JsonObject counts() { return copy(rows.get(0).getAsJsonObject().getAsJsonObject("data").getAsJsonObject("counts")); }
    public void verify() {
        if (!signature.equals(CanonicalJson.digest(current(new HashMap<>()))))
            throw new Jobs.Fault("environment_changed", "BBAB native mutation graph, templates or configuration changed");
    }
    public void capture(int index, Facts facts) {
        Jobs.checkpoint();
        JsonObject row = copy(rows.get(index).getAsJsonObject()), data = row.getAsJsonObject("data");
        if (data.get("kind").getAsString().equals("beeTreeSpecies")) for (String form : new String[]{"princess","drone"}) {
            String id = data.get(form).getAsString(); ItemStack stack = specimens.get(id);
            if (stack == null || !id.equals(facts.item(stack.copy()))) throw fault("Native analyzed bee specimen identity changed");
        }
        facts.row("references", row);
    }
    static JsonArray snapshot(IBeeRoot root, JsonObject source, boolean showSecret, boolean showDuplicateTrees, Map<String,ItemStack> specimens) {
        if (root == null || !"rootBees".equals(root.getUID())) throw new Jobs.Fault("handler_not_ready", "BBAB requires the initialized native bee root");
        Collection<? extends IBeeMutation> registered = root.getMutations(false);
        if (registered == null || registered.size() > 262144) throw fault("Invalid native BBAB mutation list");
        List<? extends IBeeMutation> mutations = new ArrayList<>(registered);
        Map<String,IAlleleSpecies> species = new TreeMap<>(); JsonArray routes = array(); int eligible = 0;
        for (int index = 0; index < mutations.size(); index++) {
            Jobs.checkpoint(); IBeeMutation mutation = mutations.get(index);
            if (mutation == null || mutation.getRoot() != root) throw fault("BBAB mutation belongs to another root");
            IAllele[] template = mutation.getTemplate();
            if (template == null || template.length == 0 || template.length > 256) throw fault("BBAB mutation template is unavailable");
            String first = species(mutation.getAllele0(), root, species), second = species(mutation.getAllele1(), root, species);
            String result = species(template[0], root, species);
            JsonArray genome = array(); for (IAllele allele : template) genome.add(value(allele == null ? null : uid(allele)));
            Collection<String> descriptions = mutation.getSpecialConditions(); // Never inherit the GUI's swallowed callback failures.
            if (descriptions == null || descriptions.size() > 256) throw fault("BBAB conditions are unavailable");
            JsonArray conditions = array();
            for (String text : descriptions) {
                if (text == null || text.isEmpty() || text.length() > 65536) throw fault("Invalid BBAB condition description");
                conditions.add(value(text));
            }
            float chance = mutation.getBaseChance();
            if (!Float.isFinite(chance) || chance < 0 || chance > 100) throw fault("Invalid native bee mutation base chance");
            boolean secret = mutation.isSecret(), listed = !secret || showSecret; if (listed) eligible++;
            routes.add(object("kind","beeTreeMutation","ordinal",index,"handler",mutation.getClass().getName(),
                    "parents",array(first,second),"result",result,"template",genome,"chance",Chance.decimal(chance,100),
                    "conditions",conditions,"secret",secret,"eligible",listed));
        }
        JsonArray data = array(object("kind","beeTreeDefinition","source",source,"root","rootBees","ready",true,
                "consuming",false,"showSecret",showSecret,"showDuplicateTrees",showDuplicateTrees,
                "ancestorSelection","firstRegisteredMutation","ancestorSecrets","nativeUnfiltered",
                "specimens","nativeAnalyzedTemplates","nativeTreeDepth",4,"nativeVisibleParentDepth",3,
                "counts",object("species",species.size(),"mutations",mutations.size(),"eligible",eligible)));
        for (Map.Entry<String,IAlleleSpecies> entry : species.entrySet()) {
            String name = entry.getValue().getName();
            if (name == null || name.isEmpty() || name.length() > 65536) throw fault("BBAB species name is unavailable");
            data.add(object("kind","beeTreeSpecies","species",entry.getKey(),"name",name,
                    "princess",member(entry.getValue(),1,specimens),"drone",member(entry.getValue(),0,specimens)));
        }
        for (JsonElement route : routes) data.add(route);
        String domain = Identity.content("reference",object("kind","bbabBeeTree","records",data)); JsonArray rows = array();
        for (int index = 0; index < data.size(); index++) {
            JsonObject row = object("domain",domain,"order",index,"data",data.get(index));
            row.addProperty("id",Identity.content("refrow",row));
            if (CanonicalJson.bytes(row).length > 1048576) throw fault("BBAB reference leaf exceeds budget"); rows.add(row);
        }
        return rows;
    }
    private static String species(IAllele allele, IBeeRoot root, Map<String,IAlleleSpecies> species) {
        if (!(allele instanceof IAlleleSpecies) || ((IAlleleSpecies)allele).getRoot() != root) throw fault("Invalid BBAB species allele");
        String uid = uid(allele); IAlleleSpecies previous = species.putIfAbsent(uid,(IAlleleSpecies)allele);
        if (species.size() > 65536) throw fault("Native BBAB species budget exceeded");
        if (previous != null && previous != allele) throw fault("Conflicting native bee species UID"); return uid;
    }
    private static String uid(IAllele allele) {
        String uid = allele.getUID(); if (uid == null || uid.isEmpty() || uid.length() > 256) throw fault("Invalid bee allele UID"); return uid;
    }
    private static String member(IAlleleSpecies allele, int form, Map<String,ItemStack> specimens) {
        // Actual BBAB helper owns analyze() and the target root's default genome selection.
        Object raw = invoke(type(HANDLER),null,"createStack",new Class<?>[]{IAlleleSpecies.class,int.class},allele,form);
        if (!(raw instanceof ItemStack)) throw fault("BBAB native analyzed member is unavailable"); ItemStack stack = (ItemStack)raw;
        String registry = stack.getItem() == null ? null : Item.itemRegistry.getNameForObject(stack.getItem());
        if (registry == null || Item.itemRegistry.getObject(registry) != stack.getItem() || stack.stackSize != 1) throw fault("Invalid native bee specimen");
        String id = Identity.item(registry,stack.getItemDamage(),TypedNbt.encode(stack.getTagCompound())); specimens.put(id,stack.copy()); return id;
    }
    private static boolean flag(String name) { Object raw = field(type(CONFIG),null,name); if (!(raw instanceof Boolean)) throw fault("Invalid BBAB flag"); return (Boolean)raw; }
    private static JsonObject copy(JsonObject row) { return new JsonParser().parse(row.toString()).getAsJsonObject(); }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("reference_unsupported",message); }
    @Override public void close() { specimens.clear(); }
}
