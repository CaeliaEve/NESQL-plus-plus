package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Audited BuildCraft 7.1.44 integration transformations, evaluated only on owned stacks.
 * This is a native observation boundary, not enumeration of the recipe's full input domain.
 * The eventual shared rule must preserve all ordered expansion slots and preview mutations.
 */
final class IntegrationRules {
    private static final String TRANSPORT="buildcraft.transport.", ROBOTICS="buildcraft.robotics.";
    private final Object recipe;
    final String kind;
    private final JsonObject context;
    private final String fingerprint;
    private final JsonObject environment;

    IntegrationRules(Object recipe) {
        this.recipe=recipe;
        String name=recipe==null?"null":recipe.getClass().getName();
        switch(name) {
            case TRANSPORT+"recipes.GateExpansionRecipe":kind="gate";break;
            case TRANSPORT+"recipes.AdvancedFacadeRecipe":kind="facade";break;
            case ROBOTICS+"RobotIntegrationRecipe":kind="robot";break;
            default:throw fault("Unadapted integration recipe: "+name);
        }
        environment=environment();context=snapshot();fingerprint=CanonicalJson.digest(context);
        // The immutable environment can contain thousands of block identities. Hash
        // only mutable recipe/board/chipset state for each observation.
        environment.entrySet().forEach(entry->context.add(entry.getKey(),entry.getValue()));
    }

    JsonObject context() { return new JsonParser().parse(context.toString()).getAsJsonObject(); }

    Observation observe(ItemStack offered,List<ItemStack> slots,boolean preview) {
        if(slots==null||slots.size()>8)throw fault("Integration has exactly eight physical expansion slots");
        if(!fingerprint.equals(CanonicalJson.digest(snapshot())))throw new Jobs.Fault("recipe_changed","Integration registry changed after rule capture");
        familyCheck(offered);
        for(ItemStack stack:slots)if(stack!=null)familyCheck(stack);
        // Copy the complete graph once: even an aliased stack in two slots retains native
        // alias semantics, without allowing craft(false) to touch a borrowed object.
        IdentityHashMap<ItemStack,ItemStack> owned=new IdentityHashMap<>();
        ItemStack input=copy(offered,owned); List<ItemStack> physical=new ArrayList<>(), compact=new ArrayList<>();
        for(ItemStack stack:slots) {
            Jobs.checkpoint(); ItemStack value=stack==null?null:copy(stack,owned);
            physical.add(value);if(value!=null)compact.add(value);
        }
        while(physical.size()<8)physical.add(null);
        if(!(Boolean)invoke(recipe.getClass(),recipe,"isValidInput",new Class<?>[]{ItemStack.class},input))throw fault("Invalid integration primary item");
        // The native machine never calls craft when every expansion slot is empty.
        if(compact.isEmpty())return new Observation(input,physical,null);
        try {
            ItemStack output=(ItemStack)invoke(recipe.getClass(),recipe,"craft",new Class<?>[]{ItemStack.class,List.class,boolean.class},input,compact,preview);
            return new Observation(input,physical,output);
        } catch(Jobs.Fault failure) { throw failure; }
        catch(RuntimeException failure) {
            Jobs.Fault wrapped=new Jobs.Fault("recipe_capture","Integration "+kind+" "+(preview?"preview":"completion")+" failed on owned inputs: "+failure);
            wrapped.initCause(failure);throw wrapped;
        }
    }

    private JsonObject snapshot() {
        JsonObject value=object("kind",kind,"energy",field(recipe,"energyCost"),"maximum",field(recipe,"maxExpansionCount"));
        if(kind.equals("gate")) {
            Object red=field(type("buildcraft.silicon.ItemRedstoneChipset$Chipset"),null,"RED");
            ItemStack stack=(ItemStack)invoke(red.getClass(),red,"getStack",new Class<?>[0]);
            value.add("red",stack(stack));JsonArray expansions=new JsonArray();
            Object raw=field(recipe,"recipes");
            if(!(raw instanceof Map))throw fault("Missing native gate expansion map");
            Map<?,?> map=(Map<?,?>)raw;
            if(map.size()>4096)throw fault("Gate expansion registry exceeds budget");
            for(Map.Entry<?,?> entry:map.entrySet()) {
                Object expansion=entry.getKey();
                inherited(expansion,"getUniqueIdentifier",TRANSPORT+"gates.GateExpansionBuildcraft");
                String tag=(String)field(expansion,"tag");
                if(tag==null||tag.length()>65524)throw fault("Invalid native gate expansion id");
                if(!(entry.getValue() instanceof ItemStack))throw fault("Invalid gate chipset entry");
                expansions.add(object("id","buildcraft:"+tag,"chip",stack((ItemStack)entry.getValue())));
            }
            value.add("expansions",expansions);
        } else if(kind.equals("facade")) {
            value.addProperty("facade",registered((Item)field(type("buildcraft.BuildCraftTransport"),null,"facadeItem")));
            value.addProperty("wire",registered((Item)field(type("buildcraft.BuildCraftTransport"),null,"pipeWire")));
            value.addProperty("plug",registered((Item)field(type("buildcraft.BuildCraftTransport"),null,"plugItem")));
        } else {
            value.addProperty("robot",registered((Item)field(type("buildcraft.BuildCraftRobotics"),null,"robotItem")));
            Object registry=field(type("buildcraft.api.boards.RedstoneBoardRegistry"),null,"instance");
            if(registry==null||!registry.getClass().getName().equals(ROBOTICS+"ImplRedstoneBoardRegistry"))throw fault("Unadapted robot board registry");
            value.addProperty("empty",board(field(registry,"emptyRobotBoardNBT")));
            Object raw=field(registry,"boards");
            if(!(raw instanceof Map)||((Map<?,?>)raw).size()>4096)throw fault("Invalid robot board registry");
            TreeMap<String,String> ids=new TreeMap<>();
            for(Map.Entry<?,?> entry:((Map<?,?>)raw).entrySet()) {
                if(!(entry.getKey() instanceof String)||((String)entry.getKey()).length()>65535)throw fault("Invalid board lookup key");
                ids.put((String)entry.getKey(),board(field(entry.getValue(),"boardNBT")));
            }
            value.add("boards",object());for(Map.Entry<String,String> entry:ids.entrySet())value.getAsJsonObject("boards").addProperty(entry.getKey(),entry.getValue());
        }
        return value;
    }

    private JsonObject environment() {
        JsonObject result=object();
        String primary=kind.equals("gate")?TRANSPORT+"gates.ItemGate":kind.equals("facade")?TRANSPORT+"ItemFacade":ROBOTICS+"ItemRobot";
        result.add("primary",family(primary));
        if(kind.equals("facade")) {
            result.add("wires",family(TRANSPORT+"ItemPipeWire"));
            JsonObject blocks=object();
            for(Object value:net.minecraft.block.Block.blockRegistry) {
                net.minecraft.block.Block block=(net.minecraft.block.Block)value;
                blocks.addProperty(Integer.toString(net.minecraft.block.Block.getIdFromBlock(block)),net.minecraft.block.Block.blockRegistry.getNameForObject(block));
            }
            result.add("blocks",blocks);
        }
        return result;
    }
    private static JsonArray family(String className) {
        Class<?> type=type(className);TreeSet<String> names=new TreeSet<>();
        for(Object item:Item.itemRegistry)if(type.isInstance(item))names.add(registered((Item)item));
        JsonArray values=new JsonArray();for(String name:names)values.add(value(name));return values;
    }
    private void familyCheck(ItemStack stack) {
        if(stack==null||stack.getItem()==null)throw fault("Invalid integration stack");
        String primary=kind.equals("gate")?TRANSPORT+"gates.ItemGate":kind.equals("facade")?TRANSPORT+"ItemFacade":ROBOTICS+"ItemRobot";
        familyCheck(stack.getItem(),"primary",primary);
        if(kind.equals("facade"))familyCheck(stack.getItem(),"wires",TRANSPORT+"ItemPipeWire");
    }
    private void familyCheck(Item item,String key,String className) {
        String name=registered(item);boolean captured=false;
        for(JsonElement entry:environment.getAsJsonArray(key))if(entry.getAsString().equals(name)){captured=true;break;}
        if(captured!=type(className).isInstance(item))throw new Jobs.Fault("recipe_changed","Integration item family changed: "+name);
    }

    private static String board(Object board) {
        if(board==null)throw fault("No empty native robot board");
        inherited(board,"createBoard","buildcraft.api.boards.RedstoneBoardNBT",net.minecraft.nbt.NBTTagCompound.class);
        String owner;
        try {owner=board.getClass().getMethod("getID").getDeclaringClass().getName();}
        catch(NoSuchMethodException e){throw fault("Missing board identifier method");}
        String id;
        if(owner.equals(ROBOTICS+"boards.BCBoardNBT"))id=(String)field(board,"id");
        else if(owner.equals(ROBOTICS+"boards.RedstoneBoardRobotEmptyNBT"))id="buildcraft:boardRobotEmpty";
        else throw fault("Unadapted board identity callback: "+owner);
        if(id==null||id.length()>65535)throw fault("Invalid robot board id");
        return id;
    }
    private static void inherited(Object object,String method,String owner,Class<?>... args) {
        if(object==null)throw fault("Missing native callback owner");
        try {if(!object.getClass().getMethod(method,args).getDeclaringClass().getName().equals(owner))throw fault("Unadapted integration callback: "+object.getClass().getName()+"."+method);}
        catch(NoSuchMethodException e){throw fault("Missing integration callback: "+method);}
    }
    private static ItemStack copy(ItemStack value,IdentityHashMap<ItemStack,ItemStack> owned) {
        if(value==null||value.getItem()==null||value.stackSize<=0)throw fault("Invalid integration input stack");
        inherited(value.getItem(),"getDamage",Item.class.getName(),ItemStack.class);
        inherited(value.getItem(),"getHasSubtypes",Item.class.getName());
        TypedNbt.encode(value.getTagCompound());
        return owned.computeIfAbsent(value,ItemStack::copy);
    }
    private static JsonObject stack(ItemStack value) {
        if(value==null||value.getItem()==null)throw fault("Missing gate chipset");
        inherited(value.getItem(),"getDamage",Item.class.getName(),ItemStack.class);
        inherited(value.getItem(),"getHasSubtypes",Item.class.getName());
        return object("registry",registered(value.getItem()),"meta",Items.feather.getDamage(value),"amount",value.stackSize,"subtypes",value.getHasSubtypes(),"nbt",TypedNbt.encode(value.getTagCompound()));
    }
    private static String registered(Item item) {
        String name=Item.itemRegistry.getNameForObject(item);
        if(item==null||name==null||Item.itemRegistry.getObject(name)!=item)throw fault("Integration references an unregistered item");
        return name;
    }
    static final class Observation {
        final ItemStack input, output;
        final List<ItemStack> expansions;
        Observation(ItemStack input,List<ItemStack> expansions,ItemStack output){this.input=input;this.expansions=Collections.unmodifiableList(expansions);this.output=output;}
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
