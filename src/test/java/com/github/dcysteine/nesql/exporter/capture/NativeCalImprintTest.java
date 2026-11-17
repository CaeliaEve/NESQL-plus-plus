package com.github.dcysteine.nesql.exporter.capture;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import java.lang.reflect.Constructor;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Target CAL/enum bodies are unchanged; only unrelated GT startup is isolated. */
final class NativeCalImprintTest {
    static void run() throws Exception {
        Class<?> recipe=type("gregtech.common.recipes.CALImprintRecipe"),enums=type("bartworks.API.enums.CircuitImprint");
        require(recipe.getProtectionDomain().getCodeSource().getLocation().toString().contains("/gregtech-2.9-probability.jar!"),
            "Exact remapped GTNH 2.9 release jar required");
        Object[] values=(Object[])field(enums,null,"VALUES");
        try(NativeRegistryScope scope=new NativeRegistryScope()){
            Item parts=NativeCoreFixesTest.item("bartworks.system.material.CircuitGeneration.CircuitPartsItem","cal_imprint");
            scope.replace(type("bartworks.system.material.CircuitGeneration.CircuitPartsItem"),null,"INSTANCE",parts);
            ItemStack machine=new ItemStack(NativeCoreFixesTest.item("gregtech.common.blocks.ItemMachines","cal_machine"),1,120);
            machine.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("retained"));
            Object entities=java.lang.reflect.Array.newInstance(type("gregtech.api.interfaces.metatileentity.IMetaTileEntity"),121);
            java.lang.reflect.Array.set(entities,120,scope.allocate(type("bartworks.common.tileentities.multis.MTECircuitAssemblyLine")));
            scope.replace(type("gregtech.api.GregTechAPI"),null,"METATILEENTITIES",entities);
            scope.replace(type("bartworks.common.loaders.ItemRegistry"),null,"cal",machine);
            Object strategy=field(type("gregtech.api.objects.GTItemStack"),null,"ITEMSTACK_HASH_STRATEGY2");
            Object associations=type("it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap")
                .getConstructor(type("it.unimi.dsi.fastutil.Hash$Strategy")).newInstance(strategy);
            scope.replace(type("gregtech.api.util.GTOreDictUnificator"),null,"sItemStack2DataMap",associations);
            Map<Integer,Object> lookup=new HashMap<>();
            Set<Object> mods=Collections.newSetFromMap(new IdentityHashMap<>());
            List<ItemStack> imprints=new ArrayList<>();
            for(Object entry:values){
                Object mod=field(entry,"sourceMod");
                if(mods.add(mod)){scope.replace(type("gregtech.api.enums.Mods"),mod,"checked",true);scope.replace(type("gregtech.api.enums.Mods"),mod,"modLoaded",true);}
                int id=((Number)field(entry,"id")).intValue();ItemStack imprint=new ItemStack(parts,1,id);imprints.add(imprint);
                Object holder=field(entry,"imprint");
                scope.replace(type("gregtech.api.enums.ItemList"),holder,"mHasNotBeenSet",false);
                scope.replace(type("gregtech.api.enums.ItemList"),holder,"mStack",imprint);
                lookup.put(id,entry);
            }
            scope.replace(enums,null,"IMPRINT_LOOKUPS_BY_IDS",lookup);
            scope.replace(enums,null,"IMPRINT_LOOKUPS_BY_UNLOCALISED_NAMES",new HashMap<String,Object>());
            Object first=values[0];ItemStack imprint=imprints.get(0);
            ItemStack output=(ItemStack)invoke(recipe,null,"installImprint",new Class<?>[]{ItemStack.class,enums},machine,first);
            require(output.getTagCompound().getInteger("id_imprint")==4&&output.getTagCompound().getString("owner").equals("retained")
                &&!machine.getTagCompound().hasKey("id_imprint"),"Native enum installation lost tags or mutated its input");
            require(invoke(recipe,null,"getCircuitImprintFromCAL",new Class<?>[]{ItemStack.class},output)==first,"Native CAL enum lookup failed");
            ItemStack legacy=machine.copy();legacy.setTagInfo("Type",new ItemStack(net.minecraft.init.Items.redstone).writeToNBT(new NBTTagCompound()));
            legacy.setTagInfo("id_imprint",new net.minecraft.nbt.NBTTagInt(4));
            require(invoke(recipe,null,"getCircuitImprintFromCAL",new Class<?>[]{ItemStack.class},legacy)==null,"Legacy Type no longer has precedence");
            ItemStack legacyOutput=(ItemStack)invoke(recipe,null,"installImprint",new Class<?>[]{ItemStack.class,enums},legacy,first);
            require(legacyOutput.getTagCompound().getCompoundTag("Type").equals(legacy.getTagCompound().getCompoundTag("Type")),
                "Native installation deleted legacy Type");
            CalImprintRecipes.Source source=(CalImprintRecipes.Source)invoke(CalImprintRecipes.class,null,"source",new Class<?>[0]);
            Constructor<CalImprintRecipes> constructor=CalImprintRecipes.class.getDeclaredConstructor(CalImprintRecipes.Source.class);
            constructor.setAccessible(true);
            try(RegistryRecipes reader=constructor.newInstance(source)){
                require(reader.size()==37,"CAL enum enumeration differs from the target");
                RecipeRow row=NativeCoreFixesTest.row(machine,imprint,output);reader.capture(0,row);
                row.record.add("source",object("owner","gregtech","handler","gregtech.nei.GTNEIImprintHandler","key","cal-imprint"));
                NativeContractFixture.writeOwned("cal-imprint",row,Arrays.asList(machine,imprint,output));
                ItemStack other=imprints.get(1);other.stackSize=2;
                try{
                    reader.verify();reader.capture(0,NativeCoreFixesTest.row(machine,imprint,output));
                    expectChanged(reader::finishVerification);
                }finally{other.stackSize=1;}
                imprint.stackSize=2;
                try{expectChanged(()->reader.capture(0,NativeCoreFixesTest.row(machine,imprint,output)));}
                finally{imprint.stackSize=1;}
                reader.finishVerification();
            }
        }
        System.out.println("CAL 2.9: 37 enum definitions, native ID output, retained tags/legacy precedence, local drift and final sealing passed");
    }
    private static void expectChanged(Runnable work){
        try{work.run();throw new AssertionError("CAL native drift was ignored");}
        catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault fault){require(fault.code.equals("recipe_changed"),"Wrong CAL drift code");}
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}

    public static final class Definitions implements net.minecraft.launchwrapper.IClassTransformer {
        public byte[] transform(String name,String transformed,byte[] bytes){
            if(bytes==null)return null;
            boolean machine=name.equals("bartworks.common.tileentities.multis.MTECircuitAssemblyLine");
            boolean startup=name.equals("gregtech.api.GregTechAPI")||name.equals("bartworks.common.loaders.ItemRegistry")
                ||name.equals("bartworks.system.material.CircuitGeneration.CircuitPartsItem")
                ||name.equals("gregtech.api.util.GTUtility")||name.equals("gregtech.api.util.GTOreDictUnificator");
            if(!machine&&!startup)return bytes;
            org.objectweb.asm.tree.ClassNode node=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(bytes).accept(node,0);
            if(machine){
                node.superName="java/lang/Object";
                node.methods.removeIf(raw->{org.objectweb.asm.tree.MethodNode method=(org.objectweb.asm.tree.MethodNode)raw;
                    return (method.access&org.objectweb.asm.Opcodes.ACC_STATIC)==0||method.name.equals("<clinit>");});
                node.fields.clear();node.interfaces.clear();node.interfaces.add("gregtech/api/interfaces/metatileentity/IMetaTileEntity");
            }
            else node.methods.removeIf(raw->((org.objectweb.asm.tree.MethodNode)raw).name.equals("<clinit>"));
            org.objectweb.asm.ClassWriter out=new org.objectweb.asm.ClassWriter(0);node.accept(out);return out.toByteArray();
        }
    }
}
