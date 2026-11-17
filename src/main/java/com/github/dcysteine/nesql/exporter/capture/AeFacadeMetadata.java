package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.item.Item;
import net.minecraft.block.Block;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Finite reviewed integer mappings, not a bytecode interpreter or a callback allowlist. */
final class AeFacadeMetadata {
    private static final JsonObject POLICIES=policies();
    private final Item item;
    private final String operation;
    private final int operand;
    private final String owner;
    private final Object auxiliary;
    private final JsonObject typePolicy;
    private final Class<?> enumType;

    AeFacadeMetadata(Item item){
        this.item=item;
        Method method=ItemCallbacks.method(item,"getMetadata","func_77647_b",int.class);
        Class<?> declaring=method.getDeclaringClass();owner=declaring.getName();
        JsonElement raw=POLICIES.get(owner);
        if(raw==null)throw fault("Unadapted facade metadata owner: "+owner);
        JsonObject policy=raw.getAsJsonObject();operation=policy.get("operation").getAsString();operand=policy.get("operand").getAsInt();
        // Vanilla is bound by the pinned Minecraft version and exact declaring class;
        // resource names for these classes differ between the production and dev loaders.
        if(!owner.startsWith("net.minecraft.item."))verifyResource(declaring,policy.getAsJsonArray("classes"));
        JsonObject types=null;Class<?> enumOwner=null;Object array=null;
        if(operation.equals("etTypes")){
            Block block=Block.getBlockFromItem(item);
            try{
                Class<?> getter=block.getClass().getMethod("getTypes").getDeclaringClass();
                JsonElement rawTypes=POLICIES.getAsJsonObject("@types").get(getter.getName());
                if(rawTypes==null)throw fault("Unadapted native facade block types getter: "+getter.getName());
                types=rawTypes.getAsJsonObject();verifyResource(getter,types.getAsJsonArray("classes"));
            }catch(NoSuchMethodException failure){throw fault("Missing native facade block types getter");}
            array=typesArray(types);
        }else if(operation.equals("ironChest")){
            enumOwner=MagicApi.type("cpw.mods.ironchest.IronChestType");
            verifyResource(enumOwner,POLICIES.getAsJsonObject("@dependencies").getAsJsonArray(enumOwner.getName()));
            array=MagicApi.field(enumOwner,null,"$VALUES");
            if(array==null||!array.getClass().isArray()||java.lang.reflect.Array.getLength(array)>256)throw fault("Invalid native chest metadata enum");
        }else if(operation.equals("etShulker")){
            Method base=cpw.mods.fml.relauncher.ReflectionHelper.findMethod(Block.class,null,new String[]{"damageDropped","func_149692_a"},int.class);
            try{if(Block.getBlockFromItem(item).getClass().getMethod(base.getName(),int.class).getDeclaringClass()!=Block.class)throw fault("Unadapted native shulker block metadata callback");}
            catch(NoSuchMethodException failure){throw fault("Missing native shulker block metadata callback");}
        }
        typePolicy=types;enumType=enumOwner;auxiliary=array;
    }
    int apply(int metadata){
        switch(operation){
            case "identity":return metadata;
            case "constant":return operand;
            case "and":return metadata&operand;
            case "or":return metadata|operand;
            case "remainder":return metadata%operand;
            case "shiftLeft":return metadata<<operand;
            case "shiftRight":return metadata>>operand;
            case "aeSubtypes":return subtype()?metadata:0;
            case "xuBlockMetadata":return (Boolean)MagicApi.field(MagicApi.type(owner),item,"hasBlockMetadata")?metadata:0;
            case "etTypes":return metadata%typesArray().length;
            case "ironChest":{
                Object[] values=chestTypes();return metadata<values.length&&(Integer)MagicApi.field(enumType,values[metadata],"size")>0?metadata:0;
            }
            case "etShulker":return 0;
            case "coral":return metadata<8?metadata+8:metadata;
            case "laserTable":return metadata<6?metadata:0;
            case "scaffold":return metadata==0?0:1;
            case "amethystCluster":return metadata<6?0:6;
            case "enclosed":return metadata==4?0:metadata==0?4:metadata;
            case "magnet":return metadata==0?0:2;
            default:throw fault("Unknown reviewed facade metadata operation: "+operation);
        }
    }
    JsonObject binding(){
        JsonArray types=null,sizes=null;
        if(operation.equals("etTypes")){types=array();for(String name:typesArray())types.add(value(name));}
        if(operation.equals("ironChest")){sizes=array();for(Object entry:chestTypes())sizes.add(value((Integer)MagicApi.field(enumType,entry,"size")));}
        return object("owner",owner,"operation",operation,"operand",operand,"subtypes",operation.equals("aeSubtypes")?subtype():null,
                "blockMetadata",operation.equals("xuBlockMetadata")?MagicApi.field(MagicApi.type(owner),item,"hasBlockMetadata"):null,"types",types,"chestSizes",sizes);
    }
    private String[] typesArray(){Object current=typesArray(typePolicy);if(current!=auxiliary)throw changed("native facade types array was replaced");return (String[])current;}
    private String[] typesArray(JsonObject policy){
        Object current=MagicApi.field(MagicApi.type(policy.get("fieldOwner").getAsString()),policy.get("static").getAsBoolean()?null:Block.getBlockFromItem(item),policy.get("field").getAsString());
        if(current==null)throw new Jobs.Fault("handler_not_ready","Native facade block types are unavailable");
        if(current.getClass()!=String[].class||((String[])current).length==0||((String[])current).length>4096)throw fault("Invalid native facade block types array");return (String[])current;
    }
    private Object[] chestTypes(){Object current=MagicApi.field(enumType,null,"$VALUES");if(current!=auxiliary)throw changed("native chest metadata enum was replaced");return (Object[])current;}
    private boolean subtype(){
        try{
            java.lang.reflect.Field field=cpw.mods.fml.relauncher.ReflectionHelper.findField(Item.class,"hasSubtypes","field_77787_bX");
            field.setAccessible(true);return field.getBoolean(item);
        }catch(ReflectiveOperationException failure){throw fault("Missing pinned AE2 item subtype field");}
    }
    private static JsonObject policies(){
        JsonObject policies=new JsonParser().parse(new String(resource(AeFacadeMetadata.class,"/ae-facade-metadata.json",1048576),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        if(policies.entrySet().size()>4096)throw fault("Facade metadata policy budget exceeded");return policies;
    }
    private static void verifyResource(Class<?> declaring,JsonArray hashes){
        if(hashes==null)throw fault("Missing reviewed native metadata dependency: "+declaring.getName());
        String digest=CanonicalJson.digest(resource(declaring,"/"+declaring.getName().replace('.','/')+".class",8388608));
        for(JsonElement hash:hashes)if(hash.getAsString().equals(digest))return;
        throw fault("Facade metadata resource differs from the reviewed installed artifact: "+declaring.getName());
    }
    private static byte[] resource(Class<?> owner,String name,int limit){
        try(InputStream stream=owner.getResourceAsStream(name)){
            if(stream==null)throw fault("Missing reviewed facade metadata resource: "+name);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
            while((count=stream.read(buffer))>=0){Jobs.checkpoint();if(bytes.size()+count>limit)throw fault("Facade metadata resource exceeds budget");bytes.write(buffer,0,count);}return bytes.toByteArray();
        }catch(IOException failure){Jobs.Fault fault=fault("Cannot read facade metadata resource: "+name);fault.initCause(failure);throw fault;}
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
    private static Jobs.Fault changed(String message){return new Jobs.Fault("recipe_changed",message);}
}
