package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;

/** Exact native storage and food callbacks that differed from the development runtime. */
final class NativeRuntimeBoundariesTest {
    static void run() throws Exception {
        java.io.File home=new java.io.File("build/native-tests/runtime-boundary-profile").getAbsoluteFile();home.mkdirs();
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.relauncher.FMLInjectionData.class,null,home,"minecraftHome");
        net.minecraft.launchwrapper.Launch.minecraftHome=home;
        List<Throwable> failures=new ArrayList<>();
        try { nbtStorage(); } catch(Exception|AssertionError failure){failures.add(failure);}
        try { smokerFood(); } catch(Exception|AssertionError failure){failures.add(failure);}
        try { rawContentMetadata(); } catch(Exception|AssertionError failure){failures.add(failure);}
        try { srgItemMembers(); } catch(Exception|AssertionError failure){failures.add(failure);}
        if(!failures.isEmpty()){
            AssertionError failure=new AssertionError("Native runtime boundaries failed: "+failures);
            for(Throwable cause:failures)failure.addSuppressed(cause);
            throw failure;
        }
        System.out.println("Native runtime boundaries: patched NBT, owned values/drift, food healing and unknown callback rejection passed");
    }
    private static void nbtStorage() throws Exception {
        try(NativeRegistryScope scope=new NativeRegistryScope()){
            NativeCoreFixesTest.version("hodgepodge","2.6.112");
            NBTTagCompound tag=new NBTTagCompound(),child=new NBTTagCompound();
            child.setLong("count",9007199254740993L);tag.setTag("child",child);
            Map<?,?> original=(Map<?,?>)field(tag,"tagMap");
            Object optimized=type("it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap").getConstructor(Map.class).newInstance(original);
            scope.replace(NBTTagCompound.class,tag,"tagMap",optimized);
            NativeStackAudit.nbt(tag);
            try(NativeDefinitionGraph graph=new NativeDefinitionGraph(()->new Object[]{tag},Collections.emptyMap())){
                NBTTagCompound copy=(NBTTagCompound)tag.copy();
                require(copy.getCompoundTag("child").getLong("count")==9007199254740993L&&field(tag,"tagMap")==optimized,"Patched NBT data or ownership changed");
                child.setLong("count",7);
                try{graph.verify();throw new AssertionError("Nested patched NBT drift accepted");}
                catch(Jobs.Fault expected){require(expected.code.equals("environment_changed"),expected.toString());}
            }
            final int[] calls={0};
            Map<String,NBTBase> foreign=new HashMap<String,NBTBase>(){public Set<Map.Entry<String,NBTBase>> entrySet(){calls[0]++;throw new AssertionError("Foreign NBT storage invoked");}};
            scope.replace(NBTTagCompound.class,tag,"tagMap",foreign);
            try{NativeStackAudit.nbt(tag);throw new AssertionError("Foreign NBT map admitted");}
            catch(Jobs.Fault expected){require(calls[0]==0,"Foreign NBT callback ran before admission");}
        }
    }
    private static void smokerFood() throws Exception {
        try(NativeRegistryScope scope=new NativeRegistryScope()){
            Class<?> config=type("ganymedes01.etfuturum.configuration.configs.ConfigFunctions");
            scope.replace(config,null,"enableAutoAddSmoker",true);
            SmeltingRecipes reader=scope.allocate(SmeltingRecipes.class);
            scope.replace(SmeltingRecipes.class,reader,"smoker",true);
            scope.replace(SmeltingRecipes.class,reader,"foodDependencies",new LinkedHashMap<String,Object>());
            Method canAdd=SmeltingRecipes.class.getDeclaredMethod("canAdd",ItemStack.class,ItemStack.class);canAdd.setAccessible(true);
            require((Boolean)canAdd.invoke(reader,new ItemStack(Items.beef),new ItemStack(Items.cooked_beef)),"Native healing food rejected by smoker");
            require((Boolean)canAdd.invoke(reader,new ItemStack(Items.fish),new ItemStack(Items.cooked_fished,1,0)),"Native cooked cod rejected");
            require((Boolean)canAdd.invoke(reader,new ItemStack(Items.fish),new ItemStack(Items.cooked_fished,1,1)),"Native cooked salmon rejected");
            final int[] calls={0};
            ItemFood unknown=new ItemFood(1,.1f,false){@Override public int func_150905_g(ItemStack input){calls[0]++;return 1;}};
            try{canAdd.invoke(reader,new ItemStack(Items.beef),new ItemStack(unknown));throw new AssertionError("Unknown healing callback admitted");}
            catch(InvocationTargetException failure){require(failure.getCause() instanceof Jobs.Fault&&calls[0]==0,"Unknown healing callback was executed");}
        }
    }
    private static void rawContentMetadata() {
        final int[] calls={0};
        Item item=new Item(){@Override public int getDamage(ItemStack stack){calls[0]++;throw new AssertionError("Raw content invoked a semantic metadata callback");}};
        ItemStack offered=new ItemStack(item,7,19);offered.setTagCompound(new NBTTagCompound());offered.getTagCompound().setLong("native",9007199254740993L);
        try(NativeItemExamples examples=new NativeItemExamples()){
            ItemStack[] variants=examples.permutations(offered);
            require(variants.length==1&&variants[0]!=offered&&Items.feather.getDamage(variants[0])==19&&variants[0].stackSize==7,"Raw content metadata or quantity changed");
            require(calls[0]==0&&variants[0].getTagCompound().getLong("native")==9007199254740993L,"Raw content lost NBT or invoked a callback");
            variants[0].getTagCompound().setLong("native",1);
            require(offered.getTagCompound().getLong("native")==9007199254740993L,"Native content source was mutated");
        }
        try{NativeStackAudit.audit(offered);throw new AssertionError("Raw-content support relaxed predicate callback admission");}
        catch(Jobs.Fault expected){require(calls[0]==0,"Unknown predicate callback executed");}
    }
    private static void srgItemMembers() throws Exception {
        org.objectweb.asm.ClassWriter out=new org.objectweb.asm.ClassWriter(0);String name="net/minecraft/item/Item";
        out.visit(org.objectweb.asm.Opcodes.V1_8,org.objectweb.asm.Opcodes.ACC_PUBLIC,name,null,"java/lang/Object",null);
        out.visitField(org.objectweb.asm.Opcodes.ACC_PUBLIC,"field_77700_c","Ljava/lang/Object;",null,null).visitEnd();
        org.objectweb.asm.MethodVisitor init=out.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC,"<init>","()V",null,null);init.visitCode();init.visitVarInsn(25,0);init.visitMethodInsn(183,"java/lang/Object","<init>","()V",false);init.visitInsn(177);init.visitMaxs(1,1);init.visitEnd();
        for(String method:new String[]{"func_150896_i","func_77630_h","func_150892_m"}){
            boolean potion=method.equals("func_150896_i");
            org.objectweb.asm.MethodVisitor body=out.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC,method,"(Lnet/minecraft/item/ItemStack;)"+(potion?"Ljava/lang/String;":"Z"),null,null);body.visitCode();if(potion)body.visitLdcInsn("native-potion");else body.visitInsn(4);body.visitInsn(potion?176:172);body.visitMaxs(1,2);body.visitEnd();
        }
        out.visitEnd();byte[] bytes=out.toByteArray();
        Class<?> owner=new ClassLoader(NativeRuntimeBoundariesTest.class.getClassLoader()){Class<?> load(){return defineClass("net.minecraft.item.Item",bytes,0,bytes.length);}}.load();
        Object item=owner.newInstance(),marker=new Object();owner.getField("field_77700_c").set(item,marker);
        require(field(owner,item,"containerItem")==marker,"SRG container field was not resolved");
        require("native-potion".equals(invoke(owner,item,"getPotionEffect",new Class<?>[]{ItemStack.class},new ItemStack(Items.apple))),"SRG potion effect was not resolved");
        require(Boolean.TRUE.equals(invoke(owner,item,"isPotionIngredient",new Class<?>[]{ItemStack.class},new ItemStack(Items.apple))),"SRG potion ingredient predicate was not resolved");
        require(Boolean.TRUE.equals(invoke(owner,item,"doesContainerItemLeaveCraftingGrid",new Class<?>[]{ItemStack.class},new ItemStack(Items.apple))),"SRG container leave behavior was not resolved");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
