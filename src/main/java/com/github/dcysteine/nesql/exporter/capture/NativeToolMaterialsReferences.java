package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.ItemList;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.item.*;
import net.minecraft.init.Items;
import net.minecraftforge.oredict.OreDictionary;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Pinned native material catalogue. Raw part templates never become consuming ingredients. */
final class NativeToolMaterialsReferences implements NativeReferences {
    private static final String HANDLER="tconstruct.plugins.nei.RecipeHandlerToolMaterials";
    private final JsonObject source;
    private final List<JsonObject> rows=new ArrayList<>();
    private final Map<Integer,List<ItemStack>> parts=new LinkedHashMap<>();
    private final JsonArray snapshot;
    private final String signature,domain;
    static boolean supports(ICraftingHandler handler){return handler!=null&&HANDLER.equals(handler.getClass().getName());}
    NativeToolMaterialsReferences(TemplateRecipeHandler handler,JsonObject source){
        if(!supports(handler))throw fault("Unknown tool material handler");
        MagicApi.version("NativeToolMaterialsReferences","TConstruct");
        this.source=copy(source);
        snapshot=snapshot(parts);signature=CanonicalJson.digest(snapshot);
        if(parts.isEmpty())throw fault("No native material pages available");
        List<JsonObject> data=new ArrayList<>();
        int count=parts.values().stream().mapToInt(List::size).sum();
        data.add(object("kind","toolMaterialsDefinition","source",this.source,"version","1.13.57-GTNH",
            "craftMetalTools",field(type("tconstruct.util.config.PHConstruct"),null,"craftMetalTools"),
            "materials",parts.size(),"parts",count,"nativePages",parts.size()*2,"consuming",false));
        for(JsonElement value:snapshot){JsonObject page=value.getAsJsonObject();int id=page.get("materialId").getAsInt();
            data.add(object("kind","toolMaterial","material",page.get("material")));
            List<ItemStack> members=parts.get(id);
            for(int i=0;i<members.size();i++){
                ItemStack template=members.get(i),chosen=display(template);
                JsonObject part=object("kind","toolMaterialPart","materialId",id,"index",i,"template",raw(template),"display",displayIdentity(chosen));
                JsonObject binding=NativeToolMaterialDisplay.binding(template,chosen);if(binding!=null)part.add("displayBinding",binding);
                data.add(part);
            }
        }
        JsonArray all=array();for(JsonObject row:data)all.add(row);
        domain=Identity.content("reference",object("kind","tconstructToolMaterials","records",all));
        for(int i=0;i<data.size();i++){JsonObject row=object("id","","domain",domain,"order",i,"data",data.get(i));
            row.addProperty("id",Identity.content("refrow",row));
            if(CanonicalJson.bytes(row).length>1024*1024)throw fault("Tool material leaf exceeds 1 MiB");rows.add(row);}
    }
    public int size(){return rows.size();}
    public String domain(){return domain;}
    public JsonObject counts(){return object("materials",parts.size(),"parts",rows.size()-parts.size()-1,"nativePages",parts.size()*2);}
    public void verify(){
        Jobs.checkpoint();
        boolean expected=rows.get(0).getAsJsonObject("data").get("craftMetalTools").getAsBoolean();
        if(expected!=(Boolean)field(type("tconstruct.util.config.PHConstruct"),null,"craftMetalTools"))
            throw new Jobs.Fault("environment_changed","Native material configuration changed");
    }
    public void finishVerification(){
        verify();
        if(!signature.equals(CanonicalJson.digest(snapshot(new LinkedHashMap<>()))))
            throw new Jobs.Fault("environment_changed","TConstruct material registry, parts, config or localized display changed");
        for(JsonObject row:rows)if(row.getAsJsonObject("data").get("kind").getAsString().equals("toolMaterialPart")){
            JsonObject data=row.getAsJsonObject("data");ItemStack part=parts.get(data.get("materialId").getAsInt()).get(data.get("index").getAsInt());
            verifyDisplay(part,data.get("display"));}
    }
    public void capture(int index,Facts facts){Jobs.checkpoint();JsonObject row=copy(rows.get(index)),data=row.getAsJsonObject("data");
        if(data.get("kind").getAsString().equals("toolMaterialPart")){
            ItemStack part=parts.get(data.get("materialId").getAsInt()).get(data.get("index").getAsInt());
            ItemStack display=verifyDisplay(part,data.get("display"));
            if(display!=null&&!facts.item(display.copy()).equals(data.get("display").getAsString()))
                throw new Jobs.Fault("environment_changed","Tool material specimen identity changed");
        }
        facts.row("references",row);
    }
    @SuppressWarnings("unchecked") private JsonArray snapshot(Map<Integer,List<ItemStack>> owned){
        Class<?> registry=type("tconstruct.library.TConstructRegistry");
        Map<Integer,Object> materials=(Map<Integer,Object>)field(registry,null,"toolMaterials");
        if(materials.size()>4096)throw fault("Too many native tool materials");
        Object builder=field(type("tconstruct.library.crafting.PatternBuilder"),null,"instance");
        List<?> keys=(List<?>)field(builder,"materials");Map<?,?> sets=(Map<?,?>)field(builder,"materialSets");
        Map<List,ItemStack> mappings=(Map<List,ItemStack>)field(registry,null,"patternPartMapping");
        boolean craft=(Boolean)field(type("tconstruct.util.config.PHConstruct"),null,"craftMetalTools");
        List<?> casts=craft?Collections.emptyList():(List<?>)invoke(invoke(registry,null,"getTableCasting",new Class<?>[]{}).getClass(),
            invoke(registry,null,"getTableCasting",new Class<?>[]{}),"getCastingRecipes",new Class<?>[]{});
        if(keys.size()>65536||mappings.size()>262144||casts.size()>262144)throw fault("Native material registry exceeds budget");
        JsonArray result=array();int totalParts=0;
        for(Map.Entry<Integer,Object> entry:materials.entrySet()){
            Jobs.checkpoint();int id=entry.getKey();Object material=entry.getValue();List<ItemStack> list=new ArrayList<>();
            for(Object key:keys){Object set=sets.get(field(key,"key"));if(set==null)throw fault("Missing native material set");
                if((Integer)field(set,"materialID")==id)list.add(new ItemStack((Item)field(key,"item"),1,(Integer)field(key,"damage")));}
            for(Map.Entry<List,ItemStack> mapping:mappings.entrySet())if((Integer)mapping.getKey().get(2)==id)list.add(mapping.getValue().copy());
            for(Object recipe:casts){ItemStack part=(ItemStack)invoke(recipe.getClass(),recipe,"getResult",new Class<?>[]{});
                if(type("tconstruct.library.util.IToolPart").isInstance(part.getItem())&&
                    (Integer)invoke(part.getItem().getClass(),part.getItem(),"getMaterialID",new Class<?>[]{ItemStack.class},part.copy())==id)list.add(part.copy());}
            if(list.isEmpty())continue;
            if((totalParts+=list.size())>262144)throw fault("Native material parts exceed total budget");
            Object bow=invoke(registry,null,"getBowMaterial",new Class<?>[]{int.class},id),arrow=invoke(registry,null,"getArrowMaterial",new Class<?>[]{int.class},id);
            JsonObject stats=object("materialId",id,"materialName",field(material,"materialName"),"localizationString",field(material,"localizationString"),
                "localizedName",invoke(material.getClass(),material,"localizedName",new Class<?>[]{}),"prefixName",invoke(material.getClass(),material,"prefixName",new Class<?>[]{}),
                "ability",invoke(material.getClass(),material,"ability",new Class<?>[]{}),"harvestLevel",field(material,"harvestLevel"),
                "durability",field(material,"durability"),"miningSpeed",field(material,"miningspeed"),"attack",field(material,"attack"),
                "handleModifier",bits(field(material,"handleModifier")),"reinforced",field(material,"reinforced"),"stonebound",bits(field(material,"stonebound")),
                "tipStyle",field(material,"tipStyle"),"primaryColor",field(material,"primaryColor"),"parts",list.size(),
                "bow",bow==null?null:object("drawSpeed",field(bow,"drawspeed"),"flightSpeedMax",bits(field(bow,"flightSpeedMax"))),
                "arrow",arrow==null?null:object("mass",bits(field(arrow,"mass")),"breakChance",bits(field(arrow,"breakChance"))));
            JsonArray rawParts=array();for(ItemStack part:list)rawParts.add(raw(part));
            result.add(object("materialId",id,"material",stats,"parts",rawParts,"craftMetalTools",craft));owned.put(id,list);
        }
        return result;
    }
    private static String bits(Object value){return String.format(Locale.ROOT,"%08x",Float.floatToRawIntBits((Float)value));}
    private static JsonObject raw(ItemStack stack){String registry=Item.itemRegistry.getNameForObject(stack.getItem());
        if(registry==null||Item.itemRegistry.getObject(registry)!=stack.getItem())throw fault("Unregistered tool material part");
        return object("registry",registry,"meta",stack.getItemDamage(),"amount",Integer.toString(stack.stackSize),"nbt",TypedNbt.encode(stack.getTagCompound()));}
    private static ItemStack display(ItemStack part){
        ItemStack nativeVariant=NativeToolMaterialDisplay.resolve(part);if(nativeVariant!=null)return nativeVariant;
        if(part.getItemDamage()!=32767)return part.copy();
        if(ItemList.itemMap!=null)for(ItemStack candidate:ItemList.itemMap.get(part.getItem()))
            if(candidate.getItemDamage()!=32767&&OreDictionary.itemMatches(part.copy(),candidate.copy(),false)){
                ItemStack copy=candidate.copy();copy.setTagCompound(part.getTagCompound()==null?null:(net.minecraft.nbt.NBTTagCompound)part.getTagCompound().copy());return copy;}
        return null;
    }
    private static String displayIdentity(ItemStack display){return display==null?null:Identity.item(Item.itemRegistry.getNameForObject(display.getItem()),display.getItemDamage(),TypedNbt.encode(display.getTagCompound()));}
    private static ItemStack verifyDisplay(ItemStack part,JsonElement expected){
        ItemStack display;
        try{display=display(part);}catch(Jobs.Fault cause){Jobs.Fault changed=new Jobs.Fault("environment_changed","Tool material display source changed");changed.initCause(cause);throw changed;}
        if(!value(displayIdentity(display)).equals(expected))throw new Jobs.Fault("environment_changed","Tool material display examples changed");
        return display;
    }
    private static JsonObject copy(JsonObject row){return new JsonParser().parse(row.toString()).getAsJsonObject();}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("tool_materials",message);}
    @Override public void close(){parts.clear();rows.clear();}
}
