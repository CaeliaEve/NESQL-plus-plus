package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.common.base.Optional;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Property;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Ready AE2 facade definitions. Never initializes native subtype/config caches or constructs a GUI page. */
final class AeFacadeRecipes implements RegistryRecipes {
    private static final String HANDLER="appeng.integration.modules.NEIHelpers.NEIFacadeRecipeHandler";
    private static final String FACADE="appeng.items.parts.ItemFacade";
    private final TemplateRecipeHandler registration;
    private final Source source;
    private final ItemStack anchor;
    private final NativeDefinitions definitions;
    private final Map<String,PropertyBinding> properties=new HashMap<>();
    private final IdentityHashMap<Item,AeFacadeMetadata> metadata=new IdentityHashMap<>();
    private final IdentityHashMap<Object,Object[]> textureBindings=new IdentityHashMap<>();
    private boolean closed;
    static boolean supports(IRecipeHandler h){return h.getClass().getName().equals(HANDLER);}

    /** Active API identities, without invoking arbitrary definition or stack-source callbacks. */
    static Source registrationSource(IRecipeHandler h){
        if(!supports(h))throw fault("Unknown AE2 facade handler");
        MagicApi.version("AeFacadeRecipes","appliedenergistics2");
        Object api=exact(field(type("appeng.api.AEApi"),null,"HELD_API"),"appeng.core.Api");
        Object definitions=exact(field(api,"definitions"),"appeng.core.ApiDefinitions");
        Object items=exact(field(definitions,"items"),"appeng.core.api.definitions.ApiItems");
        Object parts=exact(field(definitions,"parts"),"appeng.core.api.definitions.ApiParts");
        Object facadeDefinition=exact(field(items,"facade"),"appeng.core.features.ItemDefinition");
        Object anchorDefinition=exact(field(parts,"cableAnchor"),"appeng.core.features.DamagedItemDefinition");
        Object facade=exact(present(field(facadeDefinition,"item"),"facade item"),FACADE);
        Object anchorSource=exact(present(field(anchorDefinition,"source"),"anchor source"),"appeng.core.features.ItemStackSrc");
        if(!(Boolean)field(anchorSource,"enabled"))throw notReady("anchor source is disabled");
        if(field(h,"facade")!=facade||field(h,"anchorDefinition")!=anchorDefinition)throw changed("handler and active API definitions differ");
        Object config=exact(field(type("appeng.core.FacadeConfig"),null,"instance"),"appeng.core.FacadeConfig");
        Object categories=field(config,"categories"),list=field(facade,"subTypes");
        if(list==null)throw notReady("native facade examples have not been initialized");
        if(list.getClass()!=ArrayList.class||categories==null||categories.getClass()!=TreeMap.class||((TreeMap<?,?>)categories).comparator()!=null)throw fault("Unknown native facade list/config storage");
        return new Source(api,definitions,items,parts,facadeDefinition,anchorDefinition,anchorSource,(Item)facade,config,(Map<?,?>)categories,(List<?>)list);
    }
    static final class Source{
        final Object api,definitions,items,parts,facadeDefinition,anchorDefinition,anchorSource,config;
        final Item facade;final Map<?,?> categories;final List<?> examples;
        final Object replacementPattern,anchorItem;final int anchorDamage;final boolean caseSensitive;final Locale locale;
        Source(Object api,Object definitions,Object items,Object parts,Object facadeDefinition,Object anchorDefinition,
               Object anchorSource,Item facade,Object config,Map<?,?> categories,List<?> examples){
            this.api=api;this.definitions=definitions;this.items=items;this.parts=parts;this.facadeDefinition=facadeDefinition;
            this.anchorDefinition=anchorDefinition;this.anchorSource=anchorSource;this.facade=facade;this.config=config;this.categories=categories;this.examples=examples;
            replacementPattern=field(config,"replacementPattern");caseSensitive=(Boolean)field(config,"caseSensitiveCustomCategories");locale=Locale.getDefault();
            anchorItem=field(anchorSource,"item");anchorDamage=(Integer)field(anchorSource,"damage");
            if(!(replacementPattern instanceof java.util.regex.Pattern)||!((java.util.regex.Pattern)replacementPattern).pattern().equals("[^a-zA-Z0-9]")||((java.util.regex.Pattern)replacementPattern).flags()!=0)throw fault("Unadapted native facade config name mapping");
        }
        boolean same(Source o){return api==o.api&&definitions==o.definitions&&items==o.items&&parts==o.parts&&facadeDefinition==o.facadeDefinition
            &&anchorDefinition==o.anchorDefinition&&anchorSource==o.anchorSource&&facade==o.facade&&config==o.config&&categories==o.categories&&examples==o.examples
            &&replacementPattern==o.replacementPattern&&caseSensitive==o.caseSensitive&&locale.equals(o.locale)&&anchorItem==o.anchorItem&&anchorDamage==o.anchorDamage;}
    }
    AeFacadeRecipes(TemplateRecipeHandler h){
        registration=h;source=registrationSource(h);anchor=anchor();
        if(source.examples.size()>65536)throw fault("Native facade examples exceed budget");
        definitions=new NativeDefinitions("AE2 facade",source.examples,this::current,this::definition);
    }
    public int size(){verify();return source.examples.size();}
    public void verify(){
        if(closed)throw new Jobs.Fault("capture_closed","AE2 facade reader is closed");
        if(!source.same(boundSource())||!RecipeRow.stackEvidence(anchor).equals(RecipeRow.stackEvidence(anchor())))throw changed("native source or anchor changed");
        definitions.verify();
    }
    private Source boundSource(){
        try{return registrationSource(registration);}catch(Jobs.Fault unavailable){Jobs.Fault drift=changed("active source became unavailable or unadapted");drift.initCause(unavailable);throw drift;}
    }
    private List<?> current(){if(!source.same(boundSource()))throw changed("active source changed");return source.examples;}
    public void finishVerification(){verify();definitions.finishVerification();}
    public void close(){if(closed)return;closed=true;definitions.close();properties.clear();metadata.clear();textureBindings.clear();}
    public boolean capture(int index,RecipeRow row){
        verify();return definitions.checked(index,()->{
            ItemStack example=stack(definitions.record(index)),texture=texture(example),output=output(texture);
            JsonArray cells=array();int slot=0;
            for(int cell=0;cell<9;cell++){
                if(cell%2==0&&cell!=4){cells.add(value(null));continue;}
                ItemStack input=cell==4?texture:anchor;
                row.itemInput(slot,Collections.singletonList(new RecipeRow.Ingredient(input.copy(),1,false,
                    cell==4?object("kind","facadeTexture"):object("kind","wildcard","meta",false,"nbt",true))),false);
                cells.add(value(slot++));
            }
            row.itemOutput(0,output,10000);
            row.record.add("grid",object("width",3,"height",3,"cells",cells,"mirror",false));
            row.record.add("process",object("kind","ae2Facade","nativeRecipe","appeng.recipes.game.FacadeRecipe","texture",row.facts.item(texture),"outputCount",4));
            int effective=metadata(texture.getItem()).apply(texture.getItemDamage());
            if(effective!=texture.getItemDamage())row.record.getAsJsonObject("process").addProperty("textureMetadata",effective);
            return true;
        });
    }
    private JsonObject definition(Object raw){
        ItemStack example=stack(raw);if(example.getItem()!=source.facade)throw fault("Native facade list contains a different item");
        ItemStack texture=texture(example),output=output(texture);
        Object[] binding=textureBindings.get(raw);Block block=Block.getBlockFromItem(texture.getItem());
        if(binding==null)textureBindings.put(raw,new Object[]{texture.getItem(),block});
        else if(binding[0]!=texture.getItem()||binding[1]!=block)throw changed("native texture block/item instance changed");
        return object("anchor",RecipeRow.stackEvidence(anchor()),"example",RecipeRow.stackEvidence(example),"texture",RecipeRow.stackEvidence(texture),"output",RecipeRow.stackEvidence(output),"metadata",metadata(texture.getItem()).binding());
    }
    private ItemStack anchor(){
        Object raw=field(source.anchorSource,"item");if(!(raw instanceof Item))throw fault("Invalid native cable anchor item");
        return stack(new ItemStack((Item)raw,1,(Integer)field(source.anchorSource,"damage")));
    }
    private ItemStack texture(ItemStack example){
        ItemStack texture=stack(invoke(type(FACADE),source.facade,"getTextureItem",new Class<?>[]{ItemStack.class},example.copy()));
        if(texture.hasTagCompound()||Block.getBlockFromItem(texture.getItem())==null)throw fault("Native facade texture is not an untagged block item");return texture;
    }
    /** Native canonical output, using ready boolean properties instead of the mutating Configuration.get. */
    private ItemStack output(ItemStack texture){
        Item item=texture.getItem();Block block=Block.getBlockFromItem(item);int metadata=texture.getItemDamage();
        int effective=metadata(item).apply(metadata);
        if(effective<0||effective==32767)throw fault("Native facade output metadata is not concrete");
        GameRegistry.UniqueIdentifier blockId=GameRegistry.findUniqueIdentifierFor(block),itemId=GameRegistry.findUniqueIdentifierFor(item);
        if(blockId==null||itemId==null)throw fault("Native facade texture has no registered block/item identity");
        String category=blockId.modId.replaceAll("[^a-zA-Z0-9]","");if(!source.caseSensitive)category=category.toLowerCase(source.locale);
        String name=blockId.name.replaceAll("[^a-zA-Z0-9]","")+(effective==0?"":"."+effective);
        if(!property(category,name).enabled())throw changed("a ready facade texture was disabled");
        ItemStack output=new ItemStack(source.facade,4);NBTTagCompound tags=new NBTTagCompound();
        tags.setIntArray("x",new int[]{Item.getIdFromItem(item),effective});tags.setString("modid",itemId.modId);tags.setString("itemname",itemId.name);output.setTagCompound(tags);return output;
    }
    private AeFacadeMetadata metadata(Item item){AeFacadeMetadata result=metadata.get(item);if(result==null){result=new AeFacadeMetadata(item);metadata.put(item,result);}return result;}
    private PropertyBinding property(String category,String name){
        String key=category+":"+name;Object rawCategory=source.categories.get(category);
        if(rawCategory==null)throw notReady("facade configuration category is absent: "+category);
        if(rawCategory.getClass()!=ConfigCategory.class)throw fault("Unknown native facade config category");
        Object storage=field(rawCategory,"properties");
        if(storage==null||storage.getClass()!=TreeMap.class||((TreeMap<?,?>)storage).comparator()!=null)throw fault("Unknown native facade property map");
        Object rawProperty=((Map<?,?>)storage).get(name);
        if(rawProperty==null)throw notReady("facade configuration value is absent: "+key);
        if(rawProperty.getClass()!=Property.class)throw fault("Unknown native facade config value");
        PropertyBinding binding=properties.get(key);
        if(binding==null){binding=new PropertyBinding((ConfigCategory)rawCategory,storage,(Property)rawProperty);properties.put(key,binding);}
        if(binding.category!=rawCategory||binding.storage!=storage||binding.property!=rawProperty||!binding.value.equals(((Property)rawProperty).getString()))throw changed("facade configuration property changed: "+key);
        return binding;
    }
    private static final class PropertyBinding{
        final ConfigCategory category;final Object storage;final Property property;final String value;
        PropertyBinding(ConfigCategory category,Object storage,Property property){this.category=category;this.storage=storage;this.property=property;value=property.getString();if(!property.isBooleanValue())throw fault("Native facade config requires a resolved boolean value");}
        boolean enabled(){return Boolean.parseBoolean(value);}
    }
    private static Object present(Object raw,String label){
        if(!(raw instanceof Optional<?>))throw fault("Unknown native facade optional: "+label);
        Optional<?> optional=(Optional<?>)raw;if(!optional.isPresent())throw notReady(label+" is unavailable");return optional.get();
    }
    private static Object exact(Object raw,String name){if(raw==null)throw notReady(name+" is unavailable");if(!raw.getClass().getName().equals(name))throw fault("Unadapted native facade source: "+raw.getClass().getName());return raw;}
    private static ItemStack stack(Object raw){
        if(raw==null||raw.getClass()!=ItemStack.class)throw fault("Invalid native facade stack");ItemStack item=(ItemStack)raw;
        if(item.getItem()==null||item.stackSize<=0||Item.itemRegistry.getNameForObject(item.getItem())==null)throw fault("Invalid registered facade item");
        if(ItemCallbacks.method(item.getItem(),"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class)throw fault("Unadapted facade stack metadata getter");
        if(item.getItemDamage()<0||item.getItemDamage()==32767)throw fault("Native facade requires concrete metadata");return item.copy();
    }
    private static Jobs.Fault notReady(String message){return new Jobs.Fault("handler_not_ready",message);}
    private static Jobs.Fault changed(String message){return new Jobs.Fault("recipe_changed","AE2 facade "+message);}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
