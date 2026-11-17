package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;

import java.util.ArrayList;
import java.util.List;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native AE2 world actions, without constructing NEI display selections. */
final class AeWorldRecipes implements RegistryRecipes {
    private static final String HANDLER = "appeng.integration.modules.NEIHelpers.NEIWorldCraftingHandler";
    private static final String[] MATERIALS = {
            "certusQuartzCrystalCharged", "logicProcessorPress", "calcProcessorPress", "engProcessorPress",
            "fluixCrystal", "qESingularity", "purifiedCertusQuartzCrystal",
            "purifiedNetherQuartzCrystal", "purifiedFluixCrystal"
    };
    private final ICraftingHandler handler;
    private final List<Page> pages;
    private final String signature;
    private final AeWorldSource source;
    private boolean closed;

    static boolean supports(ICraftingHandler value) { return HANDLER.equals(value.getClass().getName()); }

    AeWorldRecipes(ICraftingHandler handler) {
        MagicApi.version("AeWorldRecipes","appliedenergistics2");
        if (!supports(handler)) throw fault("Unknown AE2 world handler");
        this.handler = handler;
        source = AeWorldSource.read(handler);
        pages = read();
        signature = digest(pages);
        checked(source::finishVerification);
    }

    public int size() { verify(); return pages.size(); }
    public void verify() {
        if(closed)throw new Jobs.Fault("capture_closed","AE2 world reader is closed");
        try{
            source.verifyRoots();
        }catch(Jobs.Fault failure){
            if(failure.code.equals("recipe_changed"))throw failure;
            Jobs.Fault drift=new Jobs.Fault("recipe_changed","AE2 world source became unavailable or unadapted");drift.initCause(failure);throw drift;
        }
    }
    public void finishVerification(){
        verify();
        checked(()->{
            source.finishVerification();
            if(!signature.equals(digest(read())))throw new Jobs.Fault("recipe_changed","AE2 world native pages or text changed");
            source.finishVerification();
        });
    }
    public void close(){if(closed)return;closed=true;pages.clear();source.close();}

    public boolean capture(int index, RecipeRow row) {
        verify();Page page=pages.get(index);verifyPage(page);boolean result;
        try{result=captureRow(index,row);}catch(RuntimeException|Error failure){
            try{verifyPage(page);}catch(RuntimeException|Error drift){if(drift!=failure)failure.addSuppressed(drift);}throw failure;
        }verifyPage(page);return result;
    }
    private void verifyPage(Page page){
        verify();
        checked(()->{
            if(!page.detail.equals(description(page.material)))throw new Jobs.Fault("recipe_changed","AE2 world localized description changed");
            source.verifyDefinition(page.material);
            if(page.material.equals("fluixCrystal"))source.verifyDefinition("certusQuartzCrystalCharged");
            else if(page.material.equals("qESingularity")){source.verifyDefinition("singularity");source.verifyOres();}
            else if(page.material.startsWith("purified"))source.verifyDefinition("crystalSeed");
            for(java.util.Map.Entry<Item,String> entry:page.registries.entrySet())
                if(!java.util.Objects.equals(entry.getValue(),Item.itemRegistry.getNameForObject(entry.getKey())))
                    throw new Jobs.Fault("recipe_changed","AE2 world item registry binding changed");
            source.verifyRoots();
        });
    }
    private static void checked(Runnable action){
        try{action.run();}catch(Jobs.Fault failure){
            if(failure.code.equals("recipe_changed"))throw failure;
            Jobs.Fault drift=new Jobs.Fault("recipe_changed","AE2 world source became unavailable or unadapted");drift.initCause(failure);throw drift;
        }
    }
    private boolean captureRow(int index, RecipeRow row) {
        Page page = pages.get(index);
        for(List<RecipeRow.Ingredient> candidates:page.sources)if(candidates.isEmpty())
            return row.exclude(object("reason","native_ore_input_has_no_members","nativeRecipe","appeng.entity.EntitySingularity","ores",array("dustEnder","dustEnderPearl")));
        for (int slot = 0; slot < page.sources.size(); slot++)
            row.itemInput(slot, page.sources.get(slot), false);
        String sample = page.material.equals("fluixCrystal") || page.material.equals("qESingularity") ? "2" : "1";
        row.itemOutput(0, page.stack.copy(), 10000,
                Amounts.potential("ae2:worldAcquisition", "worldConditionsAndSourceAction", sample, Long.parseLong(sample)));
        JsonArray sources = array();
        JsonArray sourceIdentities = array();
        JsonObject nativeDefinitions = object();
        nativeDefinitions.add("result", identity(page.stack));
        for (int slot = 0; slot < row.inputs.size(); slot++) {
            JsonObject input = row.inputs.get(slot).getAsJsonObject();
            JsonArray ids = array();
            JsonArray identities = array();
            java.util.Map<String, JsonObject> nativeIdentities = new java.util.HashMap<>();
            for (RecipeRow.Ingredient candidate : page.sources.get(slot))
                nativeIdentities.put(CanonicalJson.digest(object("id", row.facts.item(candidate.item),
                        "rule", candidate.rule)), identity(candidate.item));
            for (com.google.gson.JsonElement element : input.getAsJsonArray("choices")) {
                JsonObject choice = element.getAsJsonObject();
                ids.add(choice.get("id"));
                // RecipeRow removes identical complete alternatives. Keep native snapshot
                // duplicates for drift checks, but bind only the alternatives actually emitted.
                JsonObject nativeIdentity = nativeIdentities.get(CanonicalJson.digest(object(
                        "id", choice.get("id"), "rule", choice.get("rule"))));
                if (nativeIdentity == null) throw fault("AE2 world input lost its sealed native identity");
                identities.add(nativeIdentity);
            }
            sources.add(ids);
            sourceIdentities.add(identities);
            for (int i = 0; i < identities.size(); i++)
                nativeDefinitions.add("source" + slot + "_" + i, identities.get(i));
        }
        row.note("ae2:world/"+page.material,
                "appeng.core.localization.GuiText#"+String.join(",",descriptionKeys(page.material)),page.detail);
        row.record.add("process", object("kind", "ae2WorldAcquisition", "material", page.material,
                "action", action(page.material),
                "result", row.facts.item(page.stack), "resultIdentity", identity(page.stack),
                "sources", sources, "sourceIdentities", sourceIdentities,
                "nativeDefinitions", nativeDefinitions));
        return true;
    }

    private List<Page> read() {
        List<Page> result=new ArrayList<>();
        for(String name:MATERIALS){
            Jobs.checkpoint();
            String feature=AeWorldSource.featureFor(name);
            if(!source.feature(feature))continue;
            ItemStack stack=source.stack(name);if(stack==null)continue;
            result.add(new Page(name,valid(stack),description(name),sourceInputs(name)));
        }
        return result;
    }
    private static String description(String name){
        List<String> parts=new ArrayList<>();for(String key:descriptionKeys(name))parts.add(text(key));
        return String.join("\n\n",parts);
    }
    private static String[] descriptionKeys(String name){
        if(name.equals("certusQuartzCrystalCharged"))return new String[]{"ChargedQuartz","ChargedQuartzFind"};
        return new String[]{name.endsWith("ProcessorPress")?"inWorldCraftingPresses":name.equals("fluixCrystal")?"inWorldFluix":
            name.equals("qESingularity")?"inWorldSingularity":name.equals("purifiedCertusQuartzCrystal")?"inWorldPurificationCertus":
            name.equals("purifiedNetherQuartzCrystal")?"inWorldPurificationNether":"inWorldPurificationFluix"};
    }
    private static String text(String key){
        Class<?> owner=type("appeng.core.localization.GuiText");Object value=field(owner,null,key);
        if(value==null||value.getClass()!=owner)throw fault("Unadapted native AE2 description enum");
        return (String)invoke(type("appeng.core.localization.Localization"),value,"getLocal",new Class<?>[0]);
    }

    private static JsonObject action(String material) {
        if (material.equals("certusQuartzCrystalCharged")) return object("kind", "chargedOre");
        if (material.endsWith("ProcessorPress")) return object("kind", "meteoriteLoot");
        if (material.equals("fluixCrystal")) return object("kind", "fluix", "liquidTicks", 61);
        if (material.equals("qESingularity")) return object("kind", "singularity", "explosion", true, "freshFrequency", true);
        int start = material.equals("purifiedCertusQuartzCrystal") ? 0 :
                material.equals("purifiedNetherQuartzCrystal") ? 600 : 1200;
        // EntityGrowingCrystal accepts any Forge liquid material (the native
        // callback checks Material.isLiquid), not water specifically. Keep
        // that condition typed so the Compiler cannot narrow the action.
        return object("kind", "growth", "progressStart", start, "progressEnd", start + 600, "liquidMaterial", "liquid");
    }
    private List<List<RecipeRow.Ingredient>> sourceInputs(String material) {
        List<List<RecipeRow.Ingredient>> result = new ArrayList<>();
        if (material.equals("fluixCrystal")) {
            source(result, material("certusQuartzCrystalCharged"), object("kind", "wildcard", "meta", false, "nbt", true));
            source(result, new ItemStack(Items.redstone), object("kind", "wildcard", "meta", false, "nbt", true));
            source(result, new ItemStack(Items.quartz), object("kind", "wildcard", "meta", false, "nbt", true));
        } else if (material.equals("qESingularity")) {
            source(result, material("singularity"), object("kind", "wildcard", "meta", false, "nbt", true));
            List<RecipeRow.Ingredient> choices = new ArrayList<>();
            for(AeWorldSource.Ore ore:source.ores)for(ItemStack raw:ore.templates){
                ItemStack template=raw.copy();template.stackSize=1;
                if(template.getItemDamage()<0)throw fault("Negative AE2 ore metadata requires a native adapter");
                if(template.getItemDamage()==32767){
                    if(!codechicken.nei.ItemList.loadFinished||codechicken.nei.ItemList.items==null)
                        throw new Jobs.Fault("handler_not_ready","AE2 wildcard ore examples are unavailable");
                    if(ItemCallbacks.method(template.getItem(),"setDamage","setDamage",ItemStack.class,int.class).getDeclaringClass()!=Item.class)
                        throw fault("Unadapted AE2 wildcard metadata setter");
                }
                for(ItemStack sample:source.metadata(template)){
                    if(choices.size()==65536)throw fault("AE2 singularity ore examples exceed budget");
                    choices.add(new RecipeRow.Ingredient(sample,1,false,object("kind","ae2EnderDust","ore",ore.name,"metadata",template.getItemDamage())));
                }
            }
            result.add(choices);
        } else if (material.startsWith("purified")) {
            ItemStack input=source.stack("crystalSeed");
            if(input==null)throw new Jobs.Fault("handler_not_ready","AE2 crystal seed definition is disabled or absent");
            if(ItemCallbacks.method(input.getItem(),"setDamage","setDamage",ItemStack.class,int.class).getDeclaringClass()!=Item.class)
                throw fault("Unadapted AE2 seed metadata setter");
            input.setItemDamage(material.equals("purifiedCertusQuartzCrystal") ? 0 :
                    material.equals("purifiedNetherQuartzCrystal") ? 600 : 1200);
            int start = input.getItemDamage();
            source(result, input, object("kind", "ae2CrystalSeed", "start", start, "end", start + 600));
        }
        return result;
    }
    private ItemStack material(String name){
        ItemStack stack=source.stack(name);if(stack==null)throw new Jobs.Fault("handler_not_ready","AE2 world source is absent: "+name);return stack;
    }
    private static void source(List<List<RecipeRow.Ingredient>> sources, ItemStack item, JsonObject rule) {
        item = item.copy();
        item.stackSize = 1;
        sources.add(java.util.Collections.singletonList(new RecipeRow.Ingredient(item, 1, false, rule)));
    }
    private static String digest(List<Page> pages) {
        JsonArray array = array();
        for (Page page : pages) {
            JsonArray sources = array();
            for (List<RecipeRow.Ingredient> candidates : page.sources) {
                JsonArray choices = array();
                for (RecipeRow.Ingredient choice : candidates)
                    choices.add(object("stack", raw(choice.item), "rule", choice.rule));
                sources.add(choices);
            }
            array.add(object("material", page.material, "stack", raw(page.stack), "detail", page.detail, "sources", sources));
        }
        return CanonicalJson.digest(array);
    }
    private static JsonObject raw(ItemStack item) { return object("item", Item.itemRegistry.getNameForObject(item.getItem()),
            "meta", item.getItemDamage(), "nbt", TypedNbt.encode(item.getTagCompound()), "count", item.stackSize); }
    private static JsonObject identity(ItemStack item) { return object("registry", Item.itemRegistry.getNameForObject(item.getItem()),
            "meta", item.getItemDamage(), "nbt", TypedNbt.encode(item.getTagCompound())); }
    private static ItemStack valid(ItemStack item){return AeWorldSource.valid(item);}
    private static final class Page {
        final String material; final ItemStack stack; final String detail;
        final List<List<RecipeRow.Ingredient>> sources;
        final java.util.Map<Item,String> registries=new java.util.IdentityHashMap<>();
        Page(String material, ItemStack stack, String detail, List<List<RecipeRow.Ingredient>> sources) {
            this.material = material; this.stack = stack; this.detail = detail; this.sources = sources;
            bind(stack);
            for(List<RecipeRow.Ingredient> choices:sources)for(RecipeRow.Ingredient choice:choices)bind(choice.item);
        }
        private void bind(ItemStack value){registries.put(value.getItem(),Item.itemRegistry.getNameForObject(value.getItem()));}
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
