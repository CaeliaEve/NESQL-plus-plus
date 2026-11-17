package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.nbt.NBTTagCompound;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** Actual pinned registry capture; no orbit, machine operation or GL is simulated. */
final class NativeSpaceAcquisitionTest {
    static void run() throws Exception {
        @SuppressWarnings("unchecked") Map<String,cpw.mods.fml.common.ModContainer> mods=(Map<String,cpw.mods.fml.common.ModContainer>)field(cpw.mods.fml.common.Loader.instance(),"namedMods");
        Map<String,cpw.mods.fml.common.ModContainer> previous=new HashMap<>(mods);
        java.lang.reflect.Field language=null;for(java.lang.reflect.Field candidate:net.minecraft.client.resources.I18n.class.getDeclaredFields())if(candidate.getType()==net.minecraft.client.resources.Locale.class)language=candidate;
        require(language!=null,"Native locale field missing");language.setAccessible(true);Object previousLanguage=language.get(null);language.set(null,new net.minecraft.client.resources.Locale());
        try {
        NativeCoreFixesTest.version("gregtech","MC1710");
        NativeCoreFixesTest.version("gregtech_nh","5.09.51.482");
        for(boolean pump:new boolean[]{true,false}) {
            String name="gtnhintergalactic.nei."+(pump?"SpacePumpModuleRecipeHandler":"GasSiphonRecipeHandler");
            TemplateRecipeHandler handler=(TemplateRecipeHandler)allocate(type(name));
            java.lang.reflect.Field recipes=TemplateRecipeHandler.class.getDeclaredField("arecipes");recipes.setAccessible(true);recipes.set(handler,new ArrayList<>());
            TemplateRecipeHandler.CachedRecipe borrowed=handler.new CachedRecipe(){@Override public codechicken.nei.PositionedStack getResult(){return null;}};
            handler.arecipes.add(borrowed);
            require(Recipes.adapter(handler)!=null,"Space acquisition has no native semantic adapter: "+name);
            @SuppressWarnings("unchecked") Map<Object,Object> registry=(Map<Object,Object>)field(type("gtnhintergalactic.recipe."+(pump?"SpacePumpingRecipes":"GasSiphonRecipes")),null,"RECIPES");
            Map<Object,Object> previousRegistry=new HashMap<>(registry);
            try {
            registry.clear();FluidStack fluid=new FluidStack(FluidRegistry.WATER,15000);
            fluid.tag=new NBTTagCompound();fluid.tag.setString("nativeFixture","retained");
            Object key;Map<Integer,FluidStack> depths=null;
            if(pump){key=org.apache.commons.lang3.tuple.Pair.of(3,2);registry.put(key,fluid);}
            else{key="planet.jupiter";depths=new HashMap<>();depths.put(2,fluid);registry.put(key,depths);}
            RegistryRecipes adapter=(RegistryRecipes)type("com.github.dcysteine.nesql.exporter.capture.SpaceAcquisitionRecipes")
                .getDeclaredConstructor(TemplateRecipeHandler.class).newInstance(handler);
            require(adapter.size()==1,"Native space table entry was omitted");
            RecipeRow row=NativeCoreFixesTest.row();adapter.capture(0,row);
            require(handler.arecipes.size()==1&&handler.arecipes.get(0)==borrowed,"Space acquisition wrote borrowed NEI cache");
            JsonObject output=row.outputs.get(0).getAsJsonObject(),process=row.record.getAsJsonObject("process");
            require(row.inputs.size()==0&&output.get("kind").getAsString().equals("fluid")&&output.get("amount").isJsonNull(),
                "Native display item became a guaranteed fluid or synthetic ingredient");
            require(process.get("baseAmount").getAsString().equals("15000")&&process.get("operation").getAsString().equals(pump?"pump":"siphon"),
                "Native acquisition selector/nominal amount lost");
            require(process.get("toolDebit").getAsString().equals("none")&&process.get("durationLaw").getAsString().equals(pump?"20TimesMaximumClampedBatch":"20Ticks"),
                "Native reusable equipment or duration changed");
            require(fluid.amount==15000&&fluid.tag.getString("nativeFixture").equals("retained"),"Capture mutated native fluid");
            com.google.gson.JsonObject origin=com.github.dcysteine.nesql.exporter.source.Json.object("owner","gregtech","handler",name,"key",pump?"space-pump":"gas-siphon");
            row.record.add("source",origin);row.record.addProperty("category",com.github.dcysteine.nesql.exporter.source.Identity.origin("category",origin));
            NativeContractFixture.writeOwned(pump?"space-pump-semantic":"gas-siphon-semantic",row,Collections.emptyList(),fluid);
            adapter.finishVerification();
            RegistrationBindings binding=RegistrationBindings.read(handler);
            require(binding.same(RegistrationBindings.read((TemplateRecipeHandler)allocate(type(name)))),"Independent space registrations have different sources");
            @SuppressWarnings("unchecked") Map<Object,Object> owner=pump?registry:(Map<Object,Object>)(Map<?,?>)depths;
            Object selector=pump?key:Integer.valueOf(2);
            owner.put(selector,fluid.copy());
            expect("recipe_changed",adapter::finishVerification);owner.put(selector,fluid);
            fluid.amount=123;expect("recipe_changed",()->adapter.capture(0,NativeCoreFixesTest.row()));fluid.amount=15000;
            fluid.tag.setString("nativeFixture","changed");expect("recipe_changed",adapter::finishVerification);fluid.tag.setString("nativeFixture","retained");
            if(!pump){registry.put(key,new HashMap<>(depths));expect("recipe_changed",adapter::verify);registry.put(key,depths);}
            adapter.finishVerification();adapter.close();adapter.close();expect("capture_closed",adapter::size);
            require(owner.get(selector)==fluid&&handler.arecipes.size()==1&&handler.arecipes.get(0)==borrowed,"Closing space reader changed borrowed data");
            try(Recipes.Cursor cursor=new Recipes.Handler(handler,0).open(NativeCoreFixesTest.row().facts)){
                require(cursor.size()==1,"Production space cursor lost entries");cursor.capture(0);cursor.finishVerification();
            }
            registry.clear();
            try(RegistryRecipes empty=new SpaceAcquisitionRecipes(handler)){require(empty.size()==0,"Ready-empty space table rejected");empty.finishVerification();}
            if(pump){
                UnknownPair unknown=new UnknownPair();registry.put(unknown,fluid);unknown.calls=0;
                expect("recipe_unsupported",()->new SpaceAcquisitionRecipes(handler));require(unknown.calls==0,"Unknown pump Pair callback executed");registry.clear();
                org.apache.commons.lang3.tuple.MutablePair<Integer,Integer> mutable=org.apache.commons.lang3.tuple.MutablePair.of(3,2);registry.put(mutable,fluid);
                try(RegistryRecipes changed=new SpaceAcquisitionRecipes(handler)){mutable.setLeft(4);expect("recipe_changed",changed::finishVerification);}
            }
            } finally {registry.clear();registry.putAll(previousRegistry);}
        }
        } finally {mods.clear();mods.putAll(previous);language.set(null,previousLanguage);}
        System.out.println("Native space registry: selectors, fluid/NBT preservation, conditional yield, non-consuming tooling and registry drift passed");
    }
    private static void expect(String code,Runnable action){try{action.run();throw new AssertionError("Expected "+code);}catch(Jobs.Fault expected){require(expected.code.equals(code),"Wrong failure: "+expected.code);}}
    private static final class UnknownPair extends org.apache.commons.lang3.tuple.Pair<Integer,Integer>{
        int calls;public int hashCode(){return 17;}public Integer getLeft(){calls++;throw new AssertionError("Unknown left callback");}public Integer getRight(){calls++;throw new AssertionError("Unknown right callback");}public Integer setValue(Integer value){throw new AssertionError("Unknown mutation");}
    }
    private static Object allocate(Class<?> type)throws Exception{java.lang.reflect.Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);return ((sun.misc.Unsafe)f.get(null)).allocateInstance(type);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
