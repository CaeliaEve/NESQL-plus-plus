package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.*;
import java.util.function.Supplier;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** IC2 heat/air state machine, not an electric one-input machine. */
final class BlastRecipes implements RegistryRecipes {
    private static final String HANDLER="ic2.neiIntegration.core.recipehandler.BlastFurnaceRecipeHandler";
    private static final String MACHINE="ic2.core.block.machine.tileentity.TileEntityBlastFurnace";
    private final Object manager;
    private final Map<?,?> registry;
    private final Supplier<Object> currentManager;
    private final Supplier<Configuration> currentConfiguration;
    private final Configuration configuration;
    private final SelectorCache cache;
    private final NativeDefinitions definitions;
    private final List<Map.Entry<?,?>> sources=new ArrayList<>();
    private final int heat;
    private final ItemStack air,cell;
    private boolean closed;
    static boolean supports(IRecipeHandler handler){return handler.getClass().getName().equals(HANDLER);}
    private static Object manager(){return field(type("ic2.api.recipe.Recipes"),null,"blastfurance");}
    static Ic2Recipes.NativeSource registrationSource(IRecipeHandler handler){
        MagicApi.version("BlastRecipes","IC2");
        Object manager=manager();Map<?,?> storage=storage(manager);
        if(!supports(handler))throw fault("Unknown IC2 blast handler");
        Object view=invoke(handler.getClass(),handler,"getRecipeList",new Class<?>[0]);
        if(view==null||!view.getClass().getName().equals("ic2.core.BasicMachineRecipeManager$1")||field(view,"this$0")!=manager)
            throw fault("IC2 blast handler exposes a different native source");
        return new Ic2Recipes.NativeSource(manager,storage);
    }
    BlastRecipes(TemplateRecipeHandler handler){this(handler,registrationSource(handler).manager,configuration(),BlastRecipes::manager,BlastRecipes::configuration);}
    BlastRecipes(TemplateRecipeHandler handler,Object manager,int heat,ItemStack air,ItemStack cell){
        this(handler,manager,new Configuration(heat,air,cell),()->manager,()->new Configuration(heat,air,cell));
    }
    private BlastRecipes(TemplateRecipeHandler handler,Object manager,Configuration configuration,Supplier<Object> currentManager,Supplier<Configuration> currentConfiguration){
        if(!supports(handler))throw fault("Unknown IC2 blast handler");
        this.registry=storage(manager);this.manager=manager;this.configuration=configuration;
        this.currentManager=currentManager;this.currentConfiguration=currentConfiguration;
        this.heat=configuration.heat;this.air=stack(configuration.air);this.cell=stack(configuration.cell);
        if(this.air.getTagCompound()!=null&&!this.air.getTagCompound().hasNoTags())throw fault("Tagged IC2 air wrapper requires native tag-hash matching");
        this.air.setTagCompound(null);
        cache=new SelectorCache(manager);
        if(registry.size()>262144)throw new Jobs.Fault("recipe_limit","IC2 blast registry budget exceeded");
        for(Map.Entry<?,?> source:registry.entrySet()){
            Jobs.checkpoint();
            sources.add(new AbstractMap.SimpleImmutableEntry<>(source));
        }
        definitions=new NativeDefinitions("IC2 blast",sources,this::currentVisible,raw->definition((Map.Entry<?,?>)raw));
    }
    private static Map<?,?> storage(Object manager){
        if(manager==null)throw new Jobs.Fault("handler_not_ready","IC2 blast manager is unavailable");
        if(!manager.getClass().getName().equals("ic2.core.BasicMachineRecipeManager"))throw fault("Unknown IC2 blast registry");
        Object storage=field(manager,"recipes");
        if(storage==null)throw new Jobs.Fault("handler_not_ready","IC2 blast storage is unavailable");
        if(storage.getClass()!=HashMap.class)throw fault("Unknown IC2 blast storage map");
        return (Map<?,?>)storage;
    }
    public int size(){verify();return sources.size();}
    public void verify(){if(closed)throw new Jobs.Fault("capture_closed","IC2 blast reader is closed");definitions.verify();}
    private List<?> currentVisible(){
        if(currentManager.get()!=manager||field(manager,"recipes")!=registry||registry.size()!=sources.size())throw changed("manager, storage or count changed");
        if(!configuration.same(currentConfiguration.get()))throw changed("heat or air/cell configuration changed");
        cache.verify();return sources;
    }
    public void finishVerification(){
        verify();int index=0;
        for(Map.Entry<?,?> source:registry.entrySet()){
            Jobs.checkpoint();Map.Entry<?,?> original=sources.get(index++);
            if(source.getKey()!=original.getKey()||source.getValue()!=original.getValue())throw changed("native order or entry instance changed");
        }
        cache.finishVerification();definitions.finishVerification();
    }
    public void close(){if(closed)return;closed=true;sources.clear();cache.close();definitions.close();}
    public boolean capture(int index,RecipeRow row){
        verify();return definitions.checked(index,()->{captureRow(sources.get(index),row);return true;});
    }
    private JsonObject definition(Map.Entry<?,?> source){
        if(registry.get(source.getKey())!=source.getValue())throw changed("native recipe output instance changed");
        JsonArray inputs=array(),outputs=array();
        for(RecipeRow.Ingredient input:Ic2Recipes.ingredients(source.getKey())){
            select(source,input.item);
            ItemStack returned=ItemCallbacks.container(input.item,input.rule.get("meta").getAsBoolean());
            inputs.add(object("stack",RecipeRow.stackEvidence(input.item),"amount",Long.toString(input.amount),"rule",input.rule,
                "container",returned==null?null:RecipeRow.stackEvidence(returned)));
        }
        Object output=source.getValue();List<?> products=products(output);
        for(Object product:products){Jobs.checkpoint();outputs.add(RecipeRow.stackEvidence(stack(product)));}
        return object("inputs",inputs,"outputs",outputs,"metadata",com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(
            (net.minecraft.nbt.NBTTagCompound)field(output,"metadata")));
    }
    private void select(Map.Entry<?,?> source,ItemStack input){
        if(type("ic2.core.item.ItemUpgradeModule").isInstance(input.getItem()))throw fault("Native blast input slot rejects upgrade items");
        cache.verify(input);
        // Pinned getRecipe only reads recipeCache (wildcard first, then exact meta).
        // The empty audited uncacheable list prevents any predicate callback fallback.
        Object selected=invoke(manager.getClass(),manager,"getRecipe",new Class<?>[]{ItemStack.class},input.copy());
        if(selected==null||field(selected,"a")!=source.getKey()||field(selected,"b")!=source.getValue())
            throw new Jobs.Fault("slot_changed","IC2 blast cache selects another recipe for the displayed input");
    }
    private void captureRow(Map.Entry<?,?> source,RecipeRow row){
        List<RecipeRow.Ingredient> inputs=Ic2Recipes.ingredients(source.getKey());
        JsonArray containers=new JsonArray(),primary=new JsonArray(),airContainers=new JsonArray();
        for(RecipeRow.Ingredient input:inputs){
            primary.add(container(input.item,input.rule.get("meta").getAsBoolean(),row.facts));
            select(source,input.item);
        }
        Object value=source.getValue();
        List<?> raw=products(value);
        List<ItemStack> outputs=new ArrayList<>();for(int i=0;i<Math.min(2,raw.size());i++)outputs.add(stack(raw.get(i)));
        // Native work() only reads these two results and ignores recipe metadata.
        row.itemInput(0,inputs,false);
        boolean ignoreMeta=configuration.airIgnoreMeta;
        airContainers.add(container(air,ignoreMeta,row.facts)); containers.add(primary); containers.add(airContainers);
        row.itemInput(1,Collections.singletonList(new RecipeRow.Ingredient(air.copy(),1,false,object("kind","untagged","meta",ignoreMeta))),false);
        for(JsonElement input:row.inputs)for(JsonElement c:input.getAsJsonObject().getAsJsonArray("choices"))c.getAsJsonObject().add("consume",object("kind","staged"));
        row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonArray("returns")
            .add(object("kind","item","id",row.facts.item(cell),"amount",Integer.toString(cell.stackSize)));
        row.itemOutput(0,outputs.get(0),10000);
        if(outputs.size()>1)row.itemOutput(1,outputs.get(1),10000,
            object("kind","potential","stat","ic2:slagSpace","nominal",Integer.toString(outputs.get(1).stackSize)));
        row.record.add("process",object("kind","ic2Blast","heat",heat,"containers",containers));
    }
    private static List<?> products(Object value){
        if(value==null||!value.getClass().getName().equals("ic2.api.recipe.RecipeOutput"))throw fault("Unknown IC2 blast result");
        Object raw=field(value,"items");
        if(!(raw instanceof List<?>)||((List<?>)raw).isEmpty()||((List<?>)raw).size()>4096)throw fault("Invalid IC2 blast results");
        return (List<?>)raw;
    }
    private static ItemStack stack(Object value){if(!(value instanceof ItemStack)||((ItemStack)value).getItem()==null||((ItemStack)value).stackSize<=0)throw fault("Invalid IC2 blast item");return ((ItemStack)value).copy();}
    private static JsonElement container(ItemStack stack,boolean ignoreMeta,Facts facts){
        ItemStack result=ItemCallbacks.container(stack,ignoreMeta);
        return result==null?com.google.gson.JsonNull.INSTANCE:object("id",facts.item(result),"amount",Integer.toString(result.stackSize));
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
    private static Jobs.Fault changed(String message){return new Jobs.Fault("recipe_changed","IC2 blast "+message);}

    private static Configuration configuration(){return new Configuration((Integer)field(type(MACHINE),null,"maxHeat"),
        (ItemStack)field(type("ic2.core.Ic2Items"),null,"airCell"),(ItemStack)field(type("ic2.core.Ic2Items"),null,"cell"));}
    private static final class Configuration{
        final int heat;final ItemStack air,cell;final JsonObject airFacts,cellFacts,airContainer;final boolean airIgnoreMeta;
        Configuration(int heat,ItemStack air,ItemStack cell){
            this.heat=heat;this.air=air;this.cell=cell;
            airFacts=RecipeRow.stackEvidence(stack(air));cellFacts=RecipeRow.stackEvidence(stack(cell));
            Item item=air.getItem();
            if(ItemCallbacks.method(item,"getHasSubtypes","func_77614_k").getDeclaringClass()!=Item.class
                    ||ItemCallbacks.method(item,"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class
                    ||ItemCallbacks.method(item,"getMaxDamage","getMaxDamage",ItemStack.class).getDeclaringClass()!=Item.class
                    ||ItemCallbacks.method(item,"getMaxDamage","func_77612_l").getDeclaringClass()!=Item.class)
                throw fault("IC2 air wrapper requires an audited metadata policy: "+item.getClass().getName());
            airIgnoreMeta=!air.getHasSubtypes()&&!air.isItemStackDamageable();
            ItemStack returned=ItemCallbacks.container(air,airIgnoreMeta);
            airContainer=returned==null?null:RecipeRow.stackEvidence(returned);
        }
        boolean same(Configuration current){return heat==current.heat&&air==current.air&&cell==current.cell&&airIgnoreMeta==current.airIgnoreMeta
            &&airFacts.equals(current.airFacts)&&cellFacts.equals(current.cellFacts)&&Objects.equals(airContainer,current.airContainer);}
    }

    /** Bind the actual selection table; never rebuild a native manager or subscribe it to events. */
    private static final class SelectorCache implements AutoCloseable{
        final Object manager,uncacheable;final Map<?,?> source;final Object subscribed;
        final IdentityHashMap<Item,CacheMap> maps=new IdentityHashMap<>();
        SelectorCache(Object manager){
            this.manager=manager;Object value=field(manager,"recipeCache");
            if(value==null||value.getClass()!=IdentityHashMap.class)throw fault("Unknown IC2 blast selector cache");
            source=(Map<?,?>)value;uncacheable=field(manager,"uncacheableRecipes");subscribed=field(manager,"oreRegisterEventSubscribed");
            if(uncacheable==null||uncacheable.getClass()!=ArrayList.class||!((List<?>)uncacheable).isEmpty())throw fault("Unadapted IC2 blast selector callback");
            if(source.size()>262144)throw new Jobs.Fault("recipe_limit","IC2 blast selector items exceed budget");
            long entries=0;
            for(Map.Entry<?,?> entry:source.entrySet()){
                Jobs.checkpoint();if(!(entry.getKey() instanceof Item)||!(entry.getValue() instanceof Map<?,?>)||entry.getValue().getClass()!=HashMap.class)
                    throw fault("Unknown IC2 blast selector entry");
                Map<?,?> map=(Map<?,?>)entry.getValue();entries+=map.size();
                if(entries>262144)throw new Jobs.Fault("recipe_limit","IC2 blast selector cache exceeds budget");
                maps.put((Item)entry.getKey(),new CacheMap(map));
            }
        }
        void verify(){
            if(field(manager,"recipeCache")!=source||source.size()!=maps.size()||field(manager,"uncacheableRecipes")!=uncacheable
                    ||!((List<?>)uncacheable).isEmpty()||!subscribed.equals(field(manager,"oreRegisterEventSubscribed")))
                throw changed("native selector cache or callback table changed");
        }
        void verify(ItemStack input){
            verify();CacheMap original=maps.get(input.getItem());Object current=source.get(input.getItem());
            if(original==null){if(current!=null)throw changed("native selector item changed");return;}
            if(current!=original.map)throw changed("native selector metadata map changed");
            original.verify(32767);original.verify(input.getItemDamage());
        }
        void finishVerification(){
            verify();for(Map.Entry<Item,CacheMap> entry:maps.entrySet()){
                Jobs.checkpoint();if(source.get(entry.getKey())!=entry.getValue().map)throw changed("native selector metadata map changed");
                entry.getValue().verifyAll();
            }
        }
        public void close(){maps.clear();}
    }
    private static final class CacheMap{
        final Map<?,?> map;final Map<Integer,Object[]> entries=new HashMap<>();
        CacheMap(Map<?,?> map){
            this.map=map;
            for(Map.Entry<?,?> entry:map.entrySet()){
                Jobs.checkpoint();
                Object tuple=entry.getValue();
                if(!(entry.getKey() instanceof Integer)||tuple==null||!tuple.getClass().getName().equals("ic2.core.util.Tuple$T2"))throw fault("Unknown IC2 blast cached tuple");
                entries.put((Integer)entry.getKey(),new Object[]{tuple,field(tuple,"a"),field(tuple,"b")});
            }
        }
        void verify(int meta){
            if(map.size()!=entries.size())throw changed("native selector metadata count changed");
            Object[] original=entries.get(meta);Object current=map.get(meta);
            if(original==null){if(current!=null)throw changed("native selector metadata changed");return;}
            if(current!=original[0]||field(current,"a")!=original[1]||field(current,"b")!=original[2])throw changed("native selector tuple changed");
        }
        void verifyAll(){for(int meta:entries.keySet()){Jobs.checkpoint();verify(meta);}}
    }
}
