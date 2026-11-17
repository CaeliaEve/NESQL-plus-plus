package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.oredict.OreDictionary;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.field;

/** Same small storage contract against original Forge and the installed Hodgepodge transformer. */
public final class NativeOreStorageTest {
    private NativeOreStorageTest() {}
    public static final class Hodgepodge implements IClassTransformer {
        public byte[] transform(String name,String transformedName,byte[] bytes) {
            if(bytes==null||!transformedName.equals("net.minecraftforge.oredict.OreDictionary"))return bytes;
            try {
                Class<?> type=Class.forName("com.mitchej123.hodgepodge.asm.transformers.mc.SpeedupOreDictionaryTransformer");
                Object transformer=type.newInstance();
                for(String member:new String[]{"itemStackClass","itemClass"}){
                    Field f=type.getDeclaredField(member);f.setAccessible(true);
                    f.set(transformer,member.equals("itemClass")?"net/minecraft/item/Item":"net/minecraft/item/ItemStack");
                }
                // Exact installed transformer body; only the isolated class namespace is selected.
                Method method=type.getDeclaredMethod("transformOreDictionary",byte[].class);method.setAccessible(true);
                return (byte[])method.invoke(transformer,(Object)bytes);
            }catch(Exception failure){throw new AssertionError("Installed Hodgepodge transformation failed",failure);}
        }
    }
    public static void run() throws Exception {
        boolean optimized=Boolean.getBoolean("nesql.nativeOreOptimized");
        Object names=field(OreDictionary.class,null,"nameToId"),stacks=field(OreDictionary.class,null,"idToStack");
        Object lookup=field(OreDictionary.class,null,"stackToId");
        if(optimized){
            if(!names.getClass().getName().equals("it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap")
                    ||!lookup.getClass().getName().equals("it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap")
                    ||stacks.getClass()!=ArrayList.class)throw new AssertionError("Fixture did not execute the real collection rewrite");
        }
        String key="fixtureOreStorage";
        OreDictionary.registerOre(key,new ItemStack(Items.ender_pearl,2));
        OreDictionary.registerOre(key,new ItemStack(Items.apple,3));
        List<?> members=NativeOreTable.members(key);
        if(members.size()!=2||((ItemStack)members.get(0)).getItem()!=Items.ender_pearl)throw new AssertionError("Native order was lost");
        int count=((Map<?,?>)names).size();
        if(!NativeOreTable.members("fixtureMissingOreStorage").isEmpty()||((Map<?,?>)names).size()!=count)
            throw new AssertionError("Reading an absent ore registered a new name");
        List<ItemStack> copies=NativeOreTable.copies(key);
        List<ItemStack> nativeView=OreDictionary.getOres(key);
        try(NativeDefinitionGraph view=new NativeDefinitionGraph(()->new Object[]{nativeView},Collections.emptyMap())){
            view.verify();
        }
        Class<?> graphType=Class.forName("com.github.dcysteine.nesql.exporter.capture.ForestryRecipeBindings$Graph");
        Constructor<?> graphCtor=graphType.getDeclaredConstructor();graphCtor.setAccessible(true);
        Method visit=graphType.getDeclaredMethod("visit",Object.class);visit.setAccessible(true);
        Hashtable<String,Object> nativeOutputs=new Hashtable<>();nativeOutputs.put("members",nativeView);
        visit.invoke(graphCtor.newInstance(),nativeOutputs);
        copies.get(0).stackSize=99;
        if(((ItemStack)members.get(0)).stackSize!=2)throw new AssertionError("Owned copies changed native ore facts");
        int id=((Map<String,Integer>)names).get(key);
        if(!contains(OreDictionary.getOreIDs(new ItemStack(Items.ender_pearl)),id))throw new AssertionError("Transformed native reverse lookup disagrees");
        BotaniaBorrowed borrowed=new BotaniaBorrowed(new BotaniaBorrowed.Budget());
        if(borrowed.ore(key)!=members)throw new AssertionError("Borrowed ore view lost the original member list");
        Method baked=CreativeCoreMatching.class.getDeclaredMethod("bakedOres");baked.setAccessible(true);
        Map<Integer,Set<String>> predicates=(Map<Integer,Set<String>>)baked.invoke(null);
        int selector=net.minecraft.item.Item.getIdFromItem(Items.ender_pearl)|(1<<16);
        if(!predicates.get(selector).contains(key))throw new AssertionError("Owned ore matching lost the native primitive reverse lookup");
        try(NativeDefinitionGraph graph=new NativeDefinitionGraph(()->new Object[]{names,lookup},Collections.emptyMap())){
            graph.verify();
            ((ItemStack)members.get(0)).stackSize=4;
            // Reverse lookup does not include stack quantities; use the actual member root for value drift.
            try(NativeDefinitionGraph values=new NativeDefinitionGraph(()->new Object[]{members},Collections.emptyMap())){
                ((ItemStack)members.get(0)).stackSize=5;
                expect("environment_changed",values::verify);
            }finally{((ItemStack)members.get(0)).stackSize=2;}
            if(optimized){
                Method defaults=names.getClass().getMethod("defaultReturnValue",int.class);
                defaults.invoke(names,7);
                try{expect("environment_changed",graph::verify);}finally{defaults.invoke(names,-1);}
            }
            graph.verify();
        }
        Field f=OreDictionary.class.getDeclaredField("nameToId");f.setAccessible(true);
        EvilMap evil=new EvilMap();
        Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);
        sun.misc.Unsafe memory=(sun.misc.Unsafe)access.get(null);
        Object base=memory.staticFieldBase(f);long offset=memory.staticFieldOffset(f);
        memory.putObject(base,offset,evil);
        try{expect("recipe_unsupported",()->NativeOreTable.members(key));if(evil.calls!=0)throw new AssertionError("Unknown collection callback executed");}
        finally{memory.putObject(base,offset,names);}
        System.out.println("Ore storage: original/installed-transformer order, absent names, copies, reverse lookup, drift and unknown callbacks passed; optimized="+optimized);
    }
    private static boolean contains(int[] values,int expected){for(int value:values)if(value==expected)return true;return false;}
    private static void expect(String code,Runnable action){try{action.run();throw new AssertionError("Expected "+code);}catch(Jobs.Fault failure){if(!failure.code.equals(code))throw failure;}}
    private static final class EvilMap extends HashMap<Object,Object>{
        int calls;
        public Object get(Object key){calls++;throw new AssertionError("Foreign get");}
        public int size(){calls++;throw new AssertionError("Foreign size");}
        public Set<Map.Entry<Object,Object>> entrySet(){calls++;throw new AssertionError("Foreign entries");}
    }
}
