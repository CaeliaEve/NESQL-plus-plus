package com.github.dcysteine.nesql.exporter.capture;

import cpw.mods.fml.common.eventhandler.*;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.*;
import java.lang.reflect.*;
import java.util.*;
import com.google.gson.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** Real ExtraUtilities callback bytecode, with only its mod-startup registry omitted. */
final class NativeCastingEventsTest {
    @SuppressWarnings("unchecked") static void run() throws Exception {
        NativeCoreFixesTest.version("ExtraUtilities","1.2.12");
        Class<?> config=type("com.rwtema.extrautils.ExtraUtils"), recipeType=type("tconstruct.library.crafting.CastingRecipe");
        config.getField("tcon_unstable_material_id").setInt(null,314); config.getField("tcon_bedrock_material_id").setInt(null,315);
        Map<Integer,Object> materials=(Map<Integer,Object>)field(type("tconstruct.library.TConstructRegistry"),null,"toolMaterials");
        materials.put(314,null);materials.put(315,null);
        Item part=NativeCoreFixesTest.item("tconstruct.tools.items.ToolPart","casting_part"); part.setHasSubtypes(true);
        Object target=type("com.rwtema.extrautils.modintegration.TConEvents").newInstance();
        Class<?> eventType=type("tconstruct.library.event.SmelteryCastedEvent$CastingTable");
        Constructor<?> constructor=recipeType.getConstructor(ItemStack.class,FluidStack.class,ItemStack.class,boolean.class,int.class,
                type("tconstruct.library.client.FluidRenderProperties"),boolean.class);
        int bus=(Integer)field(EventBus.class,MinecraftForge.EVENT_BUS,"busID");
        List<ASMEventHandler> listeners=new ArrayList<>();
        for(String callback:new String[]{"addUnstableTimer","addBedrockiumPartSlowness"})
            listeners.add(new ASMEventHandler(target,target.getClass().getMethod(callback,eventType),null));
        Method dispatch=TinkerCasting.class.getDeclaredMethod("dispatch",Event.class);dispatch.setAccessible(true);
        try {
            for(int meta:new int[]{0,314,315}) {
                ItemStack result=meta==0?new ItemStack(Items.diamond):new ItemStack(part,1,meta);
                Object recipe=constructor.newInstance(result,new FluidStack(FluidRegistry.LAVA,144),new ItemStack(Items.paper),false,80,null,false);
                Event event=(Event)eventType.getConstructor(recipeType,ItemStack.class).newInstance(recipe,result.copy());
                for(ASMEventHandler listener:listeners)event.getListenerList().register(bus,EventPriority.NORMAL,listener);
                boolean unstable=(Boolean)dispatch.invoke(null,event);
                require(unstable==(meta==314),"Unstable timer affected the wrong output");
                ItemStack captured=(ItemStack)field(event,"output");
                if(meta==315) {
                    Event nativeEvent=(Event)eventType.getConstructor(recipeType,ItemStack.class).newInstance(recipe,result.copy());
                    target.getClass().getMethod("addBedrockiumPartSlowness",eventType).invoke(target,nativeEvent);
                    require(ItemStack.areItemStackTagsEqual(captured,(ItemStack)field(nativeEvent,"output")),"Bedrockium weight differs from native callback");
                    require(captured.getTagCompound().getTagList("AttributeModifiers",10).tagCount()==1,"Bedrockium weight disappeared");
                }
                if(meta==314) require(captured.getTagCompound()!=null && !captured.getTagCompound().hasKey("XUDeadline"),
                        "Exporter froze a world timestamp into a fixed output identity");
                require(result.getTagCompound()==null,"Event mutated the registry output");
                for(ASMEventHandler listener:listeners)ListenerList.unregisterAll(bus,listener);
            }
        } finally { for(ASMEventHandler listener:listeners)ListenerList.unregisterAll(bus,listener); }
        System.out.println("Core regression: ExtraUtilities inert, bedrockium and unstable casting effects passed");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
