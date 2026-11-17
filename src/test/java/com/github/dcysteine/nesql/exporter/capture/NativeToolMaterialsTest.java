package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.google.gson.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagString;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Real pinned TConstruct handler/cache, sparse registry IDs and tagged pattern parts. */
final class NativeToolMaterialsTest {
    @SuppressWarnings("unchecked") static void run() throws Exception {
        NativeCoreFixesTest.version("TConstruct","1.13.57-GTNH");
        Class<?> registry=type("tconstruct.library.TConstructRegistry");
        Map<Integer,Object> materials=(Map<Integer,Object>)field(registry,null,"toolMaterials");
        materials.clear();
        Object material=type("tconstruct.library.tools.ToolMaterial")
            .getConstructor(String.class,int.class,int.class,int.class,int.class,float.class,int.class,float.class,String.class,int.class)
            .newInstance("Nondefault",3,777,923,7,1.37f,4,-0.23f,"fixture",0x123456);
        materials.put(31,material);materials.put(4093,material);
        Map<List,ItemStack> mappings=(Map<List,ItemStack>)field(registry,null,"patternPartMapping");mappings.clear();
        ItemStack part=new ItemStack(Items.diamond,3,0);part.setTagInfo("nativeOwner",new NBTTagString("retained"));
        mappings.put(Arrays.asList(Items.paper,0,31),part);
        field(registry,null,"bowMaterials");field(registry,null,"arrowMaterials");
        ((Map)field(registry,null,"bowMaterials")).clear();((Map)field(registry,null,"arrowMaterials")).clear();
        Object arrow=type("tconstruct.library.tools.ArrowMaterial").getConstructor(float.class,float.class).newInstance(1.7f,0.27f);
        ((Map)field(registry,null,"arrowMaterials")).put(31,arrow);
        type("tconstruct.util.config.PHConstruct").getField("craftMetalTools").setBoolean(null,true);
        TemplateRecipeHandler handler=(TemplateRecipeHandler)type("tconstruct.plugins.nei.RecipeHandlerToolMaterials").newInstance();
        handler.loadCraftingRecipes("tconstruct.tools.materials");
        require(handler.numRecipes()==2,"Native handler omitted its empty projectile page or included a material without parts");
        Class<?> adapter;
        try {adapter=Class.forName("com.github.dcysteine.nesql.exporter.capture.NativeToolMaterialsReferences");}
        catch(ClassNotFoundException missing){throw new AssertionError("Typed native tool material domain is absent",missing);}
        NativeReferences cursor=(NativeReferences)adapter.getDeclaredConstructor(TemplateRecipeHandler.class,JsonObject.class)
            .newInstance(handler,object("owner","TConstruct","handler",handler.getClass().getName(),"key",handler.getHandlerId()));
        JsonArray rows=array();Facts facts=new Facts("en_US");
        facts.restore("items",object("id",com.github.dcysteine.nesql.exporter.source.Identity.item("minecraft:diamond",0,com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(part.getTagCompound()))));
        for(int i=0;i<cursor.size();i++){cursor.capture(i,facts);for(Facts.Record row:facts.drain().records)if(row.kind.equals("references"))rows.add(row.value);}
        JsonObject definition=rows.get(0).getAsJsonObject().getAsJsonObject("data");
        require(definition.get("kind").getAsString().equals("toolMaterialsDefinition"),"Native typed definition missing");
        JsonObject record=rows.get(1).getAsJsonObject().getAsJsonObject("data").getAsJsonObject("material");
        require(record.get("materialId").getAsInt()==31,"Sparse material ID replaced by page arithmetic");
        require(record.get("bow").isJsonNull()&&!record.get("arrow").isJsonNull(),"Optional projectile stats lost");
        require(record.get("durability").getAsInt()==777&&record.get("miningSpeed").getAsInt()==923,"Native stats altered");
        JsonObject member=rows.get(2).getAsJsonObject().getAsJsonObject("data");
        require(member.getAsJsonObject("template").get("amount").getAsString().equals("3")&&member.toString().contains("retained"),"Native part count/NBT lost");
        require(rows.size()==3,"Expected one definition/material/part leaf without consuming recipes");
        cursor.finishVerification();part.setTagInfo("nativeOwner",new NBTTagString("drift"));
        try {cursor.finishVerification();throw new AssertionError("Native in-place NBT drift accepted");}catch(Jobs.Fault expected){require(expected.code.equals("environment_changed"),"Incorrect drift failure");}
        part.setTagInfo("nativeOwner",new NBTTagString("retained"));cursor.finishVerification();
        NativeContractFixture.writeReference("tconstruct-tool-materials",object("records",rows),Arrays.asList(part));
        cursor.close();
        inheritanceToolCases();
        tgregDisplay();
        System.out.println("Native TConstruct materials: sparse IDs, absent-part material, optional projectile stats, signed native values, part count/NBT and drift passed");
    }
    private static void inheritanceToolCases() throws Exception {
        Class<?> binding=Class.forName(InfusionInheritance.class.getName()+"$ToolBinding");
        java.lang.reflect.Constructor<?> create=binding.getDeclaredConstructor(net.minecraft.item.Item.class);create.setAccessible(true);
        java.lang.reflect.Method applies=binding.getDeclaredMethod("applies",ItemStack.class);applies.setAccessible(true);
        net.minecraft.item.Item pickaxe=(net.minecraft.item.Item)type("tconstruct.items.tools.Pickaxe").newInstance();
        Object reader=create.newInstance(pickaxe);
        ItemStack stack=new ItemStack(pickaxe);
        require((Boolean)applies.invoke(reader,stack)&&!pickaxe.getToolClasses(stack).isEmpty(),"Tool classes incorrectly required harvest-level NBT");
        stack.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        require((Boolean)applies.invoke(reader,stack)==!pickaxe.getToolClasses(stack).isEmpty(),"Tagged harvest-tool classification differs from native");
        net.minecraft.nbt.NBTTagCompound tags=new net.minecraft.nbt.NBTTagCompound();tags.setBoolean("Broken",true);stack.getTagCompound().setTag("InfiTool",tags);
        require((Boolean)applies.invoke(reader,stack)&&!pickaxe.getToolClasses(stack).isEmpty(),"Broken flag incorrectly removed the native tool class");
        for(String name:Arrays.asList("ItemThaumiumPickaxe","ItemElementalAxe","ItemPrimalCrusher")){
            net.minecraft.item.Item item=(net.minecraft.item.Item)type("thaumcraft.common.items.equipment."+name)
                .getConstructor(net.minecraft.item.Item.ToolMaterial.class).newInstance(net.minecraft.item.Item.ToolMaterial.IRON);
            Object known=create.newInstance(item);ItemStack offered=new ItemStack(item);
            require((Boolean)applies.invoke(known,offered)==!item.getToolClasses(offered).isEmpty(),"Native constant tool classes were not adapted: "+name);
        }
        net.minecraft.item.Item plain=new net.minecraft.item.Item();plain.setHarvestLevel("fixture",2);
        Object inherited=create.newInstance(plain);
        require((Boolean)applies.invoke(inherited,new ItemStack(plain)),"Base Item tool class was discarded");
        net.minecraft.item.Item spade=new net.minecraft.item.ItemSpade(net.minecraft.item.Item.ToolMaterial.IRON);
        Object vanilla=create.newInstance(spade);
        require((Boolean)applies.invoke(vanilla,new ItemStack(spade))&&!spade.getToolClasses(new ItemStack(spade)).isEmpty(),
                "Forge ItemTool's own class was replaced by the Item map");
        plain.setHarvestLevel("fixture",3);
        java.lang.reflect.Method verify=binding.getDeclaredMethod("verify");verify.setAccessible(true);
        try{verify.invoke(inherited);throw new AssertionError("Native tool-class source drift was accepted");}
        catch(java.lang.reflect.InvocationTargetException failure){require(failure.getCause() instanceof Jobs.Fault,"Wrong tool-class drift rejection");}
        final int[] calls={0};
        net.minecraft.item.Item unknown=new net.minecraft.item.Item(){public Set<String> getToolClasses(ItemStack offered){calls[0]++;throw new AssertionError("Unknown tool callback");}};
        try{create.newInstance(unknown);throw new AssertionError("Unknown tool callback admitted");}
        catch(java.lang.reflect.InvocationTargetException failure){require(failure.getCause() instanceof Jobs.Fault&&calls[0]==0,"Unknown tool callback was invoked");}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}

    private static void tgregDisplay() throws Exception {
        try(NativeRegistryScope scope=new NativeRegistryScope()){
            NativeCoreFixesTest.version("TGregworks","1.7.10-GTNH-1.0.28");
            Class<?> registry=type("tconstruct.library.TConstructRegistry");
            net.minecraft.item.Item item=scope.allocate(type("vexatos.tgregworks.item.ItemTGregPart").asSubclass(net.minecraft.item.Item.class));
            java.lang.reflect.Field delegate=net.minecraft.item.Item.class.getField("delegate");
            delegate.setAccessible(true);
            delegate.set(item,new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(item,net.minecraft.item.Item.class));
            net.minecraft.item.Item.itemRegistry.addObject(31352,"TGregworks:tGregToolPartChunk",item);
            Map registryNames=(Map)field(net.minecraft.item.Item.itemRegistry,"registryObjects");
            registryNames.remove(net.minecraft.item.Item.itemRegistry.getNameForObject(item));registryNames.put("TGregworks:tGregToolPartChunk",item);
            ItemStack raw=new ItemStack(item,1,31),observed=raw.copy();observed.setTagInfo("material",new NBTTagString("Iron"));
            com.google.common.collect.ArrayListMultimap<net.minecraft.item.Item,ItemStack> panel=com.google.common.collect.ArrayListMultimap.create();panel.put(item,observed);
            scope.replace(codechicken.nei.ItemList.class,null,"itemMap",panel);scope.replace(codechicken.nei.ItemList.class,null,"loadFinished",true);
            scope.replace(registry,null,"patternPartMapping",new LinkedHashMap<>(Collections.singletonMap(Arrays.asList(Items.paper,0,31),raw)));
            TemplateRecipeHandler handler=(TemplateRecipeHandler)type("tconstruct.plugins.nei.RecipeHandlerToolMaterials").newInstance();
            try(NativeReferences cursor=new NativeToolMaterialsReferences(handler,object("owner","TConstruct","handler",handler.getClass().getName(),"key",handler.getHandlerId()))){
                Facts facts=new Facts("en_US");String expected=com.github.dcysteine.nesql.exporter.source.Identity.item("TGregworks:tGregToolPartChunk",31,com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(observed.getTagCompound()));
                facts.restore("items",object("id",expected));
                facts.restore("items",object("id",com.github.dcysteine.nesql.exporter.source.Identity.item("TGregworks:tGregToolPartChunk",31,null)));
                cursor.capture(2,facts);
                JsonObject data=facts.drain().records.stream().filter(row->row.kind.equals("references")).findFirst().get().value.getAsJsonObject("data");
                require(data.get("display").getAsString().equals(expected)&&data.getAsJsonObject("template").get("nbt").isJsonNull(),"TGreg raw template replaced or uninitialized template used for tooltip");
                require(data.getAsJsonObject("displayBinding").get("material").getAsString().equals("Iron")&&raw.getTagCompound()==null,"Native display binding or raw ownership lost");
                cursor.finishVerification();
                NativeContractFixture.writeReference("tconstruct-tgreg-display",object("records",collect(cursor,facts)),Arrays.asList(observed));
                observed.getTagCompound().setString("material","Gold");
                try{cursor.finishVerification();throw new AssertionError("Native TGreg display drift accepted");}catch(Jobs.Fault expectedChange){require(expectedChange.code.equals("environment_changed"),expectedChange.toString());}
            }
        }
    }
    private static JsonArray collect(NativeReferences cursor,Facts facts){JsonArray rows=array();for(int i=0;i<cursor.size();i++){cursor.capture(i,facts);for(Facts.Record row:facts.drain().records)if(row.kind.equals("references"))rows.add(row.value);}return rows;}
    /** Skip only mod bootstrap; the original part methods remain intact. */
    public static final class PartBootstrap implements net.minecraft.launchwrapper.IClassTransformer {
        public byte[] transform(String name,String transformed,byte[] bytes){
            if(!"vexatos.tgregworks.item.ItemTGregPart".equals(transformed))return bytes;
            org.objectweb.asm.ClassReader reader=new org.objectweb.asm.ClassReader(bytes);org.objectweb.asm.ClassWriter writer=new org.objectweb.asm.ClassWriter(reader,0);
            reader.accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM5,writer){
                @Override public org.objectweb.asm.MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions){return name.equals("<clinit>")?null:super.visitMethod(access,name,desc,sig,exceptions);}
            },0);return writer.toByteArray();
        }
    }
}
